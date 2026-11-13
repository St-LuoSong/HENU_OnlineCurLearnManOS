package com.onlinecurlearnmanos.file.preview;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.file.config.FileStorageProperties;
import com.onlinecurlearnmanos.file.dto.FilePreviewResponse;
import com.onlinecurlearnmanos.file.dto.SheetPreview;
import com.onlinecurlearnmanos.file.entity.FileAsset;
import com.onlinecurlearnmanos.file.service.FileAssetService;
import com.onlinecurlearnmanos.file.storage.FileStorage;
import com.onlinecurlearnmanos.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在线预览：pdf 走内联响应，txt / md 输出安全文本与 Markdown HTML，
 * xls / xlsx / docx 由 Apache POI 解析并限制行数与单元格数量。
 * 解析结果按 SHA-256 做小容量 LRU 缓存，同一份内容不重复解析。
 */
@Service
@RequiredArgsConstructor
public class FilePreviewService {

    private static final Logger log = LoggerFactory.getLogger(FilePreviewService.class);
    private static final int PREVIEW_CACHE_SIZE = 64;
    private static final int MAX_PREVIEW_SHEETS = 10;

    private final FileAssetService fileAssetService;
    private final FileStorage fileStorage;
    private final FileStorageProperties properties;
    private final MarkdownRenderer markdownRenderer;

    private final Map<String, FilePreviewResponse> cache =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, FilePreviewResponse> eldest) {
                    return size() > PREVIEW_CACHE_SIZE;
                }
            });

    public FilePreviewResponse preview(Long fileId, UserPrincipal principal) {
        FileAsset asset = fileAssetService.requireReadable(fileId, principal);
        String extension = asset.getExtension();
        if ("pdf".equals(extension)) {
            return new FilePreviewResponse(
                    asset.getId(),
                    asset.getOriginalName(),
                    extension,
                    FilePreviewResponse.KIND_PDF,
                    "/api/v1/files/" + asset.getId() + "/content",
                    null,
                    null,
                    List.of(),
                    0,
                    false
            );
        }
        String cacheKey = asset.getSha256() + ":" + extension;
        FilePreviewResponse cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        byte[] content = readContent(asset);
        FilePreviewResponse response = switch (extension) {
            case "txt" -> textPreview(asset, decodeUtf8(content));
            case "md" -> markdownPreview(asset, decodeUtf8(content));
            case "xlsx" -> sheetPreview(asset, content, true);
            case "xls" -> sheetPreview(asset, content, false);
            case "docx" -> docxPreview(asset, content);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "该文件类型不支持在线预览");
        };
        cache.put(cacheKey, response);
        return response;
    }

    private byte[] readContent(FileAsset asset) {
        try (InputStream stream = fileStorage.openStream(asset.getStoredKey())) {
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件内容不存在或已被清理");
        }
    }

    private String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文本文件必须使用 UTF-8 编码");
        }
    }

    private FilePreviewResponse textPreview(FileAsset asset, String text) {
        boolean truncated = text.length() > properties.getPreviewMaxChars();
        String limited = truncated ? text.substring(0, properties.getPreviewMaxChars()) : text;
        return new FilePreviewResponse(
                asset.getId(),
                asset.getOriginalName(),
                asset.getExtension(),
                FilePreviewResponse.KIND_TEXT,
                null,
                limited,
                null,
                List.of(),
                0,
                truncated
        );
    }

    private FilePreviewResponse markdownPreview(FileAsset asset, String markdown) {
        boolean truncated = markdown.length() > properties.getPreviewMaxChars();
        String limited = truncated
                ? markdown.substring(0, properties.getPreviewMaxChars())
                : markdown;
        return new FilePreviewResponse(
                asset.getId(),
                asset.getOriginalName(),
                asset.getExtension(),
                FilePreviewResponse.KIND_HTML,
                null,
                null,
                markdownRenderer.render(limited),
                List.of(),
                0,
                truncated
        );
    }

    private FilePreviewResponse sheetPreview(FileAsset asset, byte[] content, boolean xlsx) {
        try (Workbook workbook = openWorkbook(content, xlsx)) {
            DataFormatter formatter = new DataFormatter();
            int maxRows = properties.getPreviewMaxRows();
            int remainingCells = properties.getPreviewMaxCells();
            List<SheetPreview> sheets = new ArrayList<>();
            boolean truncated = false;
            int totalSheets = workbook.getNumberOfSheets();
            for (int index = 0; index < totalSheets; index++) {
                if (sheets.size() >= MAX_PREVIEW_SHEETS) {
                    truncated = true;
                    break;
                }
                Sheet sheet = workbook.getSheetAt(index);
                List<List<String>> rows = new ArrayList<>();
                boolean sheetTruncated = false;
                int firstRow = sheet.getFirstRowNum();
                int lastRow = Math.min(sheet.getLastRowNum(), firstRow + maxRows - 1);
                for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    List<String> cells = new ArrayList<>();
                    if (row != null) {
                        short lastCell = row.getLastCellNum();
                        for (int cellIndex = 0; cellIndex < lastCell; cellIndex++) {
                            if (remainingCells <= 0) {
                                sheetTruncated = true;
                                break;
                            }
                            Cell cell = row.getCell(cellIndex);
                            cells.add(cell == null ? "" : formatter.formatCellValue(cell));
                            remainingCells--;
                        }
                    }
                    rows.add(cells);
                    if (sheetTruncated) {
                        break;
                    }
                }
                if (sheet.getLastRowNum() > lastRow) {
                    sheetTruncated = true;
                }
                truncated = truncated || sheetTruncated;
                sheets.add(new SheetPreview(sheet.getSheetName(), rows, sheetTruncated));
            }
            return new FilePreviewResponse(
                    asset.getId(),
                    asset.getOriginalName(),
                    asset.getExtension(),
                    FilePreviewResponse.KIND_SHEET,
                    null,
                    null,
                    null,
                    List.copyOf(sheets),
                    totalSheets,
                    truncated
            );
        } catch (IOException | RuntimeException exception) {
            log.debug("Failed to preview spreadsheet {}", asset.getId(), exception);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "表格内容无法解析，请下载后查看");
        }
    }

    private Workbook openWorkbook(byte[] content, boolean xlsx) throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream(content);
        return xlsx ? new XSSFWorkbook(stream) : new HSSFWorkbook(stream);
    }

    private FilePreviewResponse docxPreview(FileAsset asset, byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            StringBuilder html = new StringBuilder();
            int limit = properties.getPreviewMaxChars();
            boolean truncated = false;
            html.append("<div class=\"docx-preview\">");
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text == null || text.isBlank()) {
                    continue;
                }
                if (html.length() > limit) {
                    truncated = true;
                    break;
                }
                html.append("<p>").append(escape(text)).append("</p>");
            }
            if (!truncated) {
                for (XWPFTable table : document.getTables()) {
                    if (html.length() > limit) {
                        truncated = true;
                        break;
                    }
                    html.append("<table class=\"docx-table\">");
                    for (XWPFTableRow row : table.getRows()) {
                        html.append("<tr>");
                        for (XWPFTableCell cell : row.getTableCells()) {
                            html.append("<td>").append(escape(cell.getText())).append("</td>");
                        }
                        html.append("</tr>");
                    }
                    html.append("</table>");
                }
            }
            html.append("</div>");
            return new FilePreviewResponse(
                    asset.getId(),
                    asset.getOriginalName(),
                    asset.getExtension(),
                    FilePreviewResponse.KIND_HTML,
                    null,
                    null,
                    html.toString(),
                    List.of(),
                    0,
                    truncated
            );
        } catch (IOException | RuntimeException exception) {
            log.debug("Failed to preview document {}", asset.getId(), exception);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文档内容无法解析，请下载后查看");
        }
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '&' -> builder.append("&amp;");
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                case '"' -> builder.append("&quot;");
                case '\'' -> builder.append("&#39;");
                default -> builder.append(current);
            }
        }
        return builder.toString();
    }
}

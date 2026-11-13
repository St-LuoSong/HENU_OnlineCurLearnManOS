package com.onlinecurlearnmanos.file.preview;

import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.file.config.FileStorageProperties;
import com.onlinecurlearnmanos.file.dto.FilePreviewResponse;
import com.onlinecurlearnmanos.file.entity.FileAsset;
import com.onlinecurlearnmanos.file.service.FileAssetService;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.onlinecurlearnmanos.support.InMemoryFileStorage;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 在线预览回归：pdf 内联、txt / md 安全渲染、xls / xlsx 表格限流、docx 段落与表格清洗。
 */
class FilePreviewTest {

    private static final Long FILE_ID = 7L;
    private static final String STORED_KEY = "0123456789abcdef0123456789abcdef.txt";

    private final InMemoryFileStorage storage = new InMemoryFileStorage();
    private final FileAssetService fileAssetService = mock(FileAssetService.class);
    private final UserPrincipal principal =
            new UserPrincipal(31L, "2024001", "n/a", "张三", null, 1, List.of("STUDENT"));

    private FileStorageProperties properties;
    private FilePreviewService previewService;

    @BeforeEach
    void setUp() {
        properties = new FileStorageProperties(
                "./target/test-files",
                20 * 1024 * 1024,
                "xls,xlsx,docx,pdf,md,txt",
                5 * 1024 * 1024,
                "jpg,jpeg,png,webp,gif",
                20_000_000L,
                10,
                50,
                1000
        );
        previewService = new FilePreviewService(
                fileAssetService,
                storage,
                properties,
                new MarkdownRenderer()
        );
    }

    @Test
    void pdfPreviewReturnsInlineUrl() {
        stubAsset("paper.pdf", "pdf", "abc123", "%PDF-1.7".getBytes(StandardCharsets.UTF_8));

        FilePreviewResponse response = previewService.preview(FILE_ID, principal);

        assertThat(response.kind()).isEqualTo(FilePreviewResponse.KIND_PDF);
        assertThat(response.contentUrl()).isEqualTo("/api/v1/files/7/content");
    }

    @Test
    void textPreviewReturnsPlainTextAndTruncationFlag() {
        stubAsset("notes.txt", "txt", "abc123", "第一行\n第二行".getBytes(StandardCharsets.UTF_8));

        FilePreviewResponse response = previewService.preview(FILE_ID, principal);

        assertThat(response.kind()).isEqualTo(FilePreviewResponse.KIND_TEXT);
        assertThat(response.text()).contains("第二行");
        assertThat(response.truncated()).isFalse();

        previewService = new FilePreviewService(
                fileAssetService,
                storage,
                new FileStorageProperties(
                        "./target/test-files",
                        1024,
                        "txt",
                        1024,
                        "png",
                        1_000_000L,
                        10,
                        10,
                        4
                ),
                new MarkdownRenderer()
        );
        FilePreviewResponse truncated = previewService.preview(FILE_ID, principal);
        assertThat(truncated.truncated()).isTrue();
        assertThat(truncated.text()).hasSize(4);
    }

    @Test
    void textPreviewRejectsNonUtf8Content() {
        stubAsset("notes.txt", "txt", "abc123", new byte[]{(byte) 0xC3, (byte) 0x28});

        assertThatThrownBy(() -> previewService.preview(FILE_ID, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void markdownPreviewEscapesHtmlAndKeepsSafeFormatting() {
        String markdown = "# 标题\n\n**重点**内容\n\n<script>alert(1)</script>\n\n- 条目";
        stubAsset("guide.md", "md", "abc123", markdown.getBytes(StandardCharsets.UTF_8));

        FilePreviewResponse response = previewService.preview(FILE_ID, principal);

        assertThat(response.kind()).isEqualTo(FilePreviewResponse.KIND_HTML);
        assertThat(response.html()).contains("<h1>标题</h1>");
        assertThat(response.html()).contains("<strong>重点</strong>");
        assertThat(response.html()).contains("<li>条目</li>");
        assertThat(response.html()).contains("&lt;script&gt;");
        assertThat(response.html()).doesNotContain("<script>");
    }

    @Test
    void markdownPreviewDropsJavascriptLinks() {
        String markdown = "[点我](javascript:alert(1)) 与 [官网](https://example.com)";
        stubAsset("links.md", "md", "abc123", markdown.getBytes(StandardCharsets.UTF_8));

        FilePreviewResponse response = previewService.preview(FILE_ID, principal);

        assertThat(response.html()).doesNotContain("javascript:");
        assertThat(response.html()).contains("href=\"https://example.com\"");
        assertThat(response.html()).contains("rel=\"noopener noreferrer\"");
    }

    @Test
    void xlsxPreviewReturnsLimitedSheetRows() throws IOException {
        byte[] content;
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("成绩");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("姓名");
            header.createCell(1).setCellValue("分数");
            Row body = sheet.createRow(1);
            body.createCell(0).setCellValue("张三");
            body.createCell(1).setCellValue(90);
            workbook.write(out);
            content = out.toByteArray();
        }
        stubAsset("scores.xlsx", "xlsx", "abc123", content);

        FilePreviewResponse response = previewService.preview(FILE_ID, principal);

        assertThat(response.kind()).isEqualTo(FilePreviewResponse.KIND_SHEET);
        assertThat(response.totalSheets()).isEqualTo(1);
        assertThat(response.sheets()).hasSize(1);
        assertThat(response.sheets().get(0).name()).isEqualTo("成绩");
        assertThat(response.sheets().get(0).rows().get(0)).containsExactly("姓名", "分数");
        assertThat(response.sheets().get(0).rows().get(1)).containsExactly("张三", "90");
    }

    @Test
    void xlsPreviewSupportsLegacyWorkbook() throws IOException {
        byte[] content;
        try (Workbook workbook = new HSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("旧表格");
            sheet.createRow(0).createCell(0).setCellValue("值");
            workbook.write(out);
            content = out.toByteArray();
        }
        stubAsset("legacy.xls", "xls", "abc123", content);

        FilePreviewResponse response = previewService.preview(FILE_ID, principal);

        assertThat(response.kind()).isEqualTo(FilePreviewResponse.KIND_SHEET);
        assertThat(response.sheets().get(0).name()).isEqualTo("旧表格");
    }

    @Test
    void brokenSpreadsheetIsRejectedInsteadOfThrowing500() {
        stubAsset("broken.xlsx", "xlsx", "abc123", "not a workbook".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> previewService.preview(FILE_ID, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法解析");
    }

    @Test
    void docxPreviewEscapesParagraphAndKeepsTable() throws IOException {
        byte[] content;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("实验报告 <script>alert(1)</script>");
            XWPFTable table = document.createTable(1, 1);
            table.getRow(0).getCell(0).setText("得分 90");
            document.write(out);
            content = out.toByteArray();
        }
        stubAsset("report.docx", "docx", "abc123", content);

        FilePreviewResponse response = previewService.preview(FILE_ID, principal);

        assertThat(response.kind()).isEqualTo(FilePreviewResponse.KIND_HTML);
        assertThat(response.html()).contains("实验报告");
        assertThat(response.html()).contains("&lt;script&gt;");
        assertThat(response.html()).doesNotContain("<script>");
        assertThat(response.html()).contains("<td>得分 90</td>");
    }

    @Test
    void previewResultIsCachedBySha256() {
        stubAsset("notes.txt", "txt", "same-hash", "缓存内容".getBytes(StandardCharsets.UTF_8));

        FilePreviewResponse first = previewService.preview(FILE_ID, principal);
        FilePreviewResponse second = previewService.preview(FILE_ID, principal);

        assertThat(second).isSameAs(first);
    }

    @Test
    void unsupportedExtensionCannotBePreviewed() {
        stubAsset("archive.zip", "zip", "abc123", "PK".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> previewService.preview(FILE_ID, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持在线预览");
    }

    private void stubAsset(String name, String extension, String sha256, byte[] content) {
        String storedKey = STORED_KEY.substring(0, 32) + "." + extension;
        storage.store(storedKey, content);
        FileAsset asset = new FileAsset();
        asset.setId(FILE_ID);
        asset.setExtension(extension);
        asset.setOriginalName(name);
        asset.setStoredKey(storedKey);
        asset.setSha256(sha256);
        asset.setSizeBytes((long) content.length);
        asset.setStatus(FileAsset.STATUS_ACTIVE);
        given(fileAssetService.requireReadable(FILE_ID, principal)).willReturn(asset);
    }
}

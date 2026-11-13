package com.onlinecurlearnmanos.file.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.file.config.FileStorageProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 上传文件校验：扩展名白名单、声明 MIME、文件魔数、大小与 SHA-256。
 * 任何一项不通过都直接拒绝，绝不把未经验证的内容写入磁盘。
 */
@Component
public class FileValidator {

    private static final int MAX_NAME_LENGTH = 255;
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] OLE2_MAGIC = {
            (byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1
    };
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] GIF_MAGIC = {'G', 'I', 'F', '8'};
    private static final Map<String, String> IMAGE_MIME_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "webp", "image/webp"
    );
    private static final Map<String, Set<String>> MIME_BY_EXTENSION = Map.of(
            "pdf", Set.of("application/pdf"),
            "txt", Set.of("text/plain"),
            "md", Set.of("text/markdown", "text/plain", "text/x-markdown"),
            "docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ),
            "xlsx", Set.of(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ),
            "xls", Set.of("application/vnd.ms-excel")
    );

    private final FileStorageProperties properties;

    public FileValidator(FileStorageProperties properties) {
        this.properties = properties;
    }

    public ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要上传的文件");
        }
        String originalName = normalizeName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        if (!properties.isAllowedExtension(extension)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "只支持上传 " + String.join(" / ", properties.getAllowedExtensions())
            );
        }
        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "文件不能超过 " + (properties.getMaxSizeBytes() / 1024 / 1024) + " MB"
            );
        }
        byte[] content = readBytes(file);
        if (content.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件内容为空");
        }
        if (content.length > properties.getMaxSizeBytes()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "文件不能超过 " + (properties.getMaxSizeBytes() / 1024 / 1024) + " MB"
            );
        }
        verifyMagicNumber(extension, content);
        String mimeType = resolveMimeType(extension, file.getContentType());
        return new ValidatedFile(
                originalName,
                extension,
                mimeType,
                content.length,
                sha256(content),
                content
        );
    }

    /**
     * 去掉任何路径成分并使用 {@link StringUtils#cleanPath} 归一化，只保留纯文件名。
     */
    /**
     * 图片上传校验：独立的扩展名白名单、体积上限与像素上限，并逐字节校验文件头。
     * 尺寸来自文件头解析，不做整图解码，避免「小体积、大画布」的解压炸弹。
     */
    public ValidatedFile validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要上传的图片");
        }
        String originalName = normalizeName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        if (!properties.isAllowedImageExtension(extension)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "只支持上传 " + String.join(" / ", properties.getImageAllowedExtensions()) + " 格式的图片"
            );
        }
        long maxSize = properties.getImageMaxSizeBytes();
        String sizeMessage = "图片不能超过 " + Math.max(1, maxSize / 1024 / 1024) + " MB";
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, sizeMessage);
        }
        byte[] content = readBytes(file);
        if (content.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片内容为空");
        }
        if (content.length > maxSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, sizeMessage);
        }
        verifyImageMagic(extension, content);
        ImageDimensionReader.Dimensions dimensions = ImageDimensionReader.read(extension, content);
        if (dimensions.pixels() > properties.getImageMaxPixels()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "图片分辨率过大（" + dimensions.width() + "×" + dimensions.height()
                            + "），请压缩后再上传"
            );
        }
        String mimeType = resolveImageMimeType(extension, file.getContentType());
        return new ValidatedFile(
                originalName,
                extension,
                mimeType,
                content.length,
                sha256(content),
                content
        );
    }

    private void verifyImageMagic(String extension, byte[] content) {
        switch (extension) {
            case "jpg", "jpeg" -> requireMagic(content, JPEG_MAGIC, "图片内容不是有效的 JPEG");
            case "png" -> requireMagic(content, PNG_MAGIC, "图片内容不是有效的 PNG");
            case "gif" -> requireMagic(content, GIF_MAGIC, "图片内容不是有效的 GIF");
            case "webp" -> requireWebpMagic(content);
            default -> throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "不支持的图片格式：" + extension
            );
        }
    }

    private void requireWebpMagic(byte[] content) {
        if (content.length < 12
                || content[0] != 'R' || content[1] != 'I' || content[2] != 'F' || content[3] != 'F'
                || content[8] != 'W' || content[9] != 'E' || content[10] != 'B' || content[11] != 'P') {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片内容不是有效的 WEBP");
        }
    }

    /** 声明 MIME 必须与扩展名一致；浏览器漏报时回落到扩展名对应的标准 MIME。 */
    private String resolveImageMimeType(String extension, String declared) {
        String expected = IMAGE_MIME_BY_EXTENSION.getOrDefault(extension, "application/octet-stream");
        String normalized = declared == null
                ? ""
                : declared.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(normalized)) {
            normalized = "image/jpeg";
        }
        if (normalized.isEmpty()
                || "application/octet-stream".equals(normalized)
                || expected.equals(normalized)) {
            return expected;
        }
        throw new BusinessException(
                ErrorCode.BAD_REQUEST,
                "图片类型与扩展名不一致：" + extension
        );
    }

    private String normalizeName(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名不能为空");
        }
        String cleaned = StringUtils.cleanPath(rawName.trim());
        if (cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名不合法");
        }
        int separator = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        if (separator >= 0) {
            cleaned = cleaned.substring(separator + 1);
        }
        if (!StringUtils.hasText(cleaned)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名不合法");
        }
        if (cleaned.length() > MAX_NAME_LENGTH) {
            cleaned = cleaned.substring(cleaned.length() - MAX_NAME_LENGTH);
        }
        return cleaned;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件缺少扩展名");
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件读取失败");
        }
    }

    private void verifyMagicNumber(String extension, byte[] content) {
        switch (extension) {
            case "pdf" -> requireMagic(content, PDF_MAGIC, "文件内容不是有效的 PDF");
            case "xls" -> requireMagic(content, OLE2_MAGIC, "文件内容不是有效的 Excel 97-2003 文档");
            case "docx" -> requireZipWithEntry(
                    content,
                    "word/document.xml",
                    "文件内容不是有效的 Word 文档"
            );
            case "xlsx" -> requireZipWithEntry(
                    content,
                    "xl/workbook.xml",
                    "文件内容不是有效的 Excel 工作簿"
            );
            default -> requirePlainText(content);
        }
    }

    private void requireMagic(byte[] content, byte[] magic, String message) {
        if (content.length < magic.length) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        for (int index = 0; index < magic.length; index++) {
            if (content[index] != magic[index]) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, message);
            }
        }
    }

    /**
     * docx / xlsx 都是 zip 容器，仅校验 zip 头不够，必须确认包含对应的内部条目。
     */
    private void requireZipWithEntry(byte[] content, String requiredEntry, String message) {
        if (content.length < 4
                || content[0] != 'P'
                || content[1] != 'K'
                || content[2] != 3
                || content[3] != 4) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (requiredEntry.equals(entry.getName())) {
                    return;
                }
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private void requirePlainText(byte[] content) {
        for (byte value : content) {
            if (value == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文本文件不能包含二进制内容");
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
        } catch (CharacterCodingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文本文件必须使用 UTF-8 编码");
        }
    }

    private String resolveMimeType(String extension, String declared) {
        Set<String> expected = MIME_BY_EXTENSION.getOrDefault(extension, Set.of());
        String normalized = declared == null
                ? ""
                : declared.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()
                || "application/octet-stream".equals(normalized)
                || expected.contains(normalized)) {
            return expected.isEmpty() ? normalized : expected.iterator().next();
        }
        throw new BusinessException(
                ErrorCode.BAD_REQUEST,
                "文件类型与扩展名不一致：" + extension
        );
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}

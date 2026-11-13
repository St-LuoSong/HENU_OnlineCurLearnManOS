package com.onlinecurlearnmanos.file.service;

import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.file.config.FileStorageProperties;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 上传校验回归：扩展名、MIME、魔数、大小、编码与文件名清洗都必须在落盘前拦住。
 */
class FileValidationTest {

    private FileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileValidator(properties(1024 * 1024));
    }

    private FileStorageProperties properties(long maxSizeBytes) {
        return new FileStorageProperties(
                "./target/test-files",
                maxSizeBytes,
                "xls,xlsx,docx,pdf,md,txt",
                5 * 1024 * 1024,
                "jpg,jpeg,png,webp,gif",
                20_000_000L,
                200,
            2000,
                200000
        );
    }

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    @Test
    void rejectsUnsupportedExtension() {
        MockMultipartFile upload = file(
                "app.exe",
                "application/octet-stream",
                "MZ binary".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> validator.validate(upload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只支持上传");
    }

    @Test
    void rejectsFileWithoutExtension() {
        MockMultipartFile upload = file("README", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validate(upload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少扩展名");
    }

    @Test
    void rejectsPdfWithWrongMagicNumber() {
        MockMultipartFile upload = file(
                "fake.pdf",
                "application/pdf",
                "not really a pdf".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> validator.validate(upload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是有效的 PDF");
    }

    @Test
    void rejectsContentTypeThatContradictsExtension() {
        MockMultipartFile upload = file("scan.pdf", "image/png", pdfBytes());

        assertThatThrownBy(() -> validator.validate(upload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile upload = file("empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> validator.validate(upload))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsFileAboveConfiguredLimit() {
        FileValidator smallLimitValidator = new FileValidator(properties(8));
        MockMultipartFile upload = file(
                "notes.txt",
                "text/plain",
                "0123456789".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> smallLimitValidator.validate(upload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过");
    }

    @Test
    void rejectsNonUtf8TextAndBinaryText() {
        MockMultipartFile invalidUtf8 = file(
                "notes.txt",
                "text/plain",
                new byte[]{(byte) 0xC3, (byte) 0x28}
        );
        MockMultipartFile withNulByte = file(
                "notes.txt",
                "text/plain",
                new byte[]{'a', 0, 'b'}
        );

        assertThatThrownBy(() -> validator.validate(invalidUtf8))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UTF-8");
        assertThatThrownBy(() -> validator.validate(withNulByte))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("二进制");
    }

    @Test
    void rejectsDocxZipWithoutWordDocumentEntry() throws IOException {
        MockMultipartFile upload = file("report.docx", docxMime(), zipOf("hello.txt", "hi"));

        assertThatThrownBy(() -> validator.validate(upload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是有效的 Word 文档");
    }

    @Test
    void acceptsValidPdfAndComputesSha256() throws Exception {
        byte[] content = pdfBytes();

        ValidatedFile validated = validator.validate(file("paper.pdf", "application/pdf", content));

        assertThat(validated.extension()).isEqualTo("pdf");
        assertThat(validated.mimeType()).isEqualTo("application/pdf");
        assertThat(validated.sizeBytes()).isEqualTo(content.length);
        assertThat(validated.sha256()).isEqualTo(sha256(content));
    }

    @Test
    void acceptsRealXlsxWorkbook() throws IOException {
        byte[] content;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("成绩").createRow(0).createCell(0).setCellValue("张三");
            workbook.write(out);
            content = out.toByteArray();
        }

        ValidatedFile validated = validator.validate(file("scores.xlsx", xlsxMime(), content));

        assertThat(validated.extension()).isEqualTo("xlsx");
        assertThat(validated.mimeType()).isEqualTo(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
    }

    @Test
    void stripsDirectoryPartsFromUploadedFileName() {
        MockMultipartFile upload = file(
                "C:\\Users\\docs\\notes.txt",
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        );

        ValidatedFile validated = validator.validate(upload);

        assertThat(validated.originalName()).isEqualTo("notes.txt");
    }

    @Test
    void rejectsFileNameWithParentTraversal() {
        MockMultipartFile upload = file(
                "..\\..\\notes.txt",
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> validator.validate(upload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件名不合法");
    }

    private String docxMime() {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    }

    private String xlsxMime() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    private byte[] pdfBytes() {
        return "%PDF-1.7\n1 0 obj\n<<>>\nendobj\ntrailer\n%%EOF".getBytes(StandardCharsets.UTF_8);
    }

    private byte[] zipOf(String entryName, String content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}

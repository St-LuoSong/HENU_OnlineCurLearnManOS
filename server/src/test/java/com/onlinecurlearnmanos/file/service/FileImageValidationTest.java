package com.onlinecurlearnmanos.file.service;

import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.file.config.FileStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 图片上传校验（第四轮审计 16-C）：图片走独立白名单，必须同时通过魔数、声明 MIME 与像素上限。
 * 尺寸只从文件头解析，不解码整图，因此「小体积、大画布」的解压炸弹在落盘前就被拦住。
 */
class FileImageValidationTest {

    private FileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileValidator(properties(1024 * 1024, 5 * 1024 * 1024, 20_000_000L));
    }

    private FileStorageProperties properties(long maxSize, long imageMaxSize, long imageMaxPixels) {
        return new FileStorageProperties(
                "./target/test-files",
                maxSize,
                "xls,xlsx,docx,pdf,md,txt",
                imageMaxSize,
                "jpg,jpeg,png,webp,gif",
                imageMaxPixels,
                200,
                2000,
                200000
        );
    }

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    @Test
    void acceptsPngAndGifAndWebp() {
        assertThat(validator.validateImage(file("cover.png", "image/png", png(800, 600))).mimeType())
                .isEqualTo("image/png");
        assertThat(validator.validateImage(file("cover.gif", "image/gif", gif(120, 90))).extension())
                .isEqualTo("gif");
        assertThat(validator.validateImage(file("cover.webp", "image/webp", webp(1024, 768))).mimeType())
                .isEqualTo("image/webp");
    }

    @Test
    void acceptsJpegDeclaredAsGenericOctetStream() {
        ValidatedFile validated = validator.validateImage(
                file("photo.jpeg", "application/octet-stream", jpeg(1920, 1080))
        );

        assertThat(validated.mimeType()).isEqualTo("image/jpeg");
        assertThat(validated.extension()).isEqualTo("jpeg");
    }

    @Test
    void parsesRealDimensionsFromFileHeader() {
        assertThat(ImageDimensionReader.read("png", png(800, 600)))
                .isEqualTo(new ImageDimensionReader.Dimensions(800, 600));
        assertThat(ImageDimensionReader.read("gif", gif(320, 240)))
                .isEqualTo(new ImageDimensionReader.Dimensions(320, 240));
        assertThat(ImageDimensionReader.read("jpg", jpeg(2560, 1440)))
                .isEqualTo(new ImageDimensionReader.Dimensions(2560, 1440));
        assertThat(ImageDimensionReader.read("webp", webp(1024, 768)))
                .isEqualTo(new ImageDimensionReader.Dimensions(1024, 768));
    }

    @Test
    void rejectsExtensionOutsideImageWhitelist() {
        assertThatThrownBy(() -> validator.validateImage(file("icon.bmp", "image/bmp", png(10, 10))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只支持上传");
    }

    @Test
    void rejectsDisguisedImageContent() {
        MockMultipartFile upload = file(
                "fake.png",
                "image/png",
                "not an image at all".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> validator.validateImage(upload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是有效的 PNG");
    }

    @Test
    void rejectsImageWhoseDeclaredMimeContradictsExtension() {
        assertThatThrownBy(() -> validator.validateImage(file("cover.png", "image/gif", png(10, 10))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    void rejectsImageAboveSizeLimit() {
        FileValidator smallLimit = new FileValidator(properties(1024, 16, 20_000_000L));

        assertThatThrownBy(() -> smallLimit.validateImage(file("cover.png", "image/png", png(10, 10))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过");
    }

    @Test
    void rejectsImageAbovePixelLimit() {
        // 8000×8000 = 6400 万像素，超过默认 2000 万上限，但体积只有几十字节。
        assertThatThrownBy(() -> validator.validateImage(file("huge.png", "image/png", png(8000, 8000))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分辨率过大");
    }

    @Test
    void rejectsTruncatedHeaderAsUnreadable() {
        byte[] truncated = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', 'X'};

        assertThatThrownBy(() -> validator.validateImage(file("clip.webp", "image/webp", truncated)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法识别");
    }

    private byte[] png(int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        out.writeBytes(new byte[]{0, 0, 0, 13});
        out.writeBytes("IHDR".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(int32be(width));
        out.writeBytes(int32be(height));
        out.writeBytes(new byte[]{8, 6, 0, 0, 0});
        return out.toByteArray();
    }

    private byte[] gif(int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("GIF89a".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(u16le(width));
        out.writeBytes(u16le(height));
        out.writeBytes(new byte[]{0, 0, 0, 0});
        return out.toByteArray();
    }

    private byte[] jpeg(int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xD8});
        // APP0 段：标记 + 长度 16 + 14 字节负载
        out.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xE0, 0x00, 0x10});
        out.writeBytes(new byte[14]);
        // SOF0 段：标记 + 长度 17 + 精度 + 高度 + 宽度
        out.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xC0, 0x00, 0x11, 0x08});
        out.writeBytes(u16be(height));
        out.writeBytes(u16be(width));
        out.writeBytes(new byte[]{0x03, 0x01, 0x11, 0x00});
        return out.toByteArray();
    }

    private byte[] webp(int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(u32le(22));
        out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes("VP8X".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(u32le(10));
        out.write(0x00);
        out.writeBytes(new byte[3]);
        out.writeBytes(u24le(width - 1));
        out.writeBytes(u24le(height - 1));
        return out.toByteArray();
    }

    private byte[] int32be(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }

    private byte[] u16be(int value) {
        return new byte[]{(byte) (value >>> 8), (byte) value};
    }

    private byte[] u16le(int value) {
        return new byte[]{(byte) value, (byte) (value >>> 8)};
    }

    private byte[] u24le(int value) {
        return new byte[]{(byte) value, (byte) (value >>> 8), (byte) (value >>> 16)};
    }

    private byte[] u32le(int value) {
        return new byte[]{
                (byte) value,
                (byte) (value >>> 8),
                (byte) (value >>> 16),
                (byte) (value >>> 24)
        };
    }
}


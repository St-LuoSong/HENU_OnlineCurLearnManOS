package com.onlinecurlearnmanos.file.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;

import java.util.Locale;

/**
 * 从图片文件头解析真实宽高。
 *
 * <p>刻意不做整图解码：解码一张很小的 PNG / WEBP 也可能展开成巨大的位图，
 * 而像素上限必须在使用任何内存之前生效。这里只读取文件头里的尺寸字段，
 * 任何越界或未知结构都直接判定为「无法识别」。</p>
 */
public final class ImageDimensionReader {

    private ImageDimensionReader() {
    }

    public record Dimensions(int width, int height) {

        public long pixels() {
            return (long) width * height;
        }
    }

    public static Dimensions read(String extension, byte[] content) {
        if (extension == null || content == null) {
            throw invalid();
        }
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "png" -> png(content);
            case "gif" -> gif(content);
            case "jpg", "jpeg" -> jpeg(content);
            case "webp" -> webp(content);
            default -> throw invalid();
        };
    }

    private static Dimensions png(byte[] content) {
        require(content, 24);
        return checked(int32be(content, 16), int32be(content, 20));
    }

    private static Dimensions gif(byte[] content) {
        require(content, 10);
        return checked(u16le(content, 6), u16le(content, 8));
    }

    /**
     * JPEG 需要跳过可变长段，直到遇到 SOF0-SOF15（不含 DHT/JPG 扩展段）才能拿到尺寸。
     */
    private static Dimensions jpeg(byte[] content) {
        require(content, 4);
        int index = 2;
        while (index + 3 < content.length) {
            if ((content[index] & 0xFF) != 0xFF) {
                index++;
                continue;
            }
            int marker = content[index + 1] & 0xFF;
            if (marker == 0xFF) {
                index++;
                continue;
            }
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                index += 2;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) {
                break;
            }
            int length = u16be(content, index + 2);
            if (length < 2) {
                break;
            }
            if (isStartOfFrame(marker)) {
                if (index + 9 > content.length) {
                    break;
                }
                int height = u16be(content, index + 5);
                int width = u16be(content, index + 7);
                return checked(width, height);
            }
            index += 2 + length;
        }
        throw invalid();
    }

    private static boolean isStartOfFrame(int marker) {
        return (marker >= 0xC0 && marker <= 0xC3)
                || (marker >= 0xC5 && marker <= 0xC7)
                || (marker >= 0xC9 && marker <= 0xCB)
                || (marker >= 0xCD && marker <= 0xCF);
    }

    /** WEBP 有三种容器：有损 VP8、无损 VP8L 与扩展 VP8X，尺寸字段位置各不相同。 */
    private static Dimensions webp(byte[] content) {
        require(content, 30);
        String fourcc = new String(content, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return switch (fourcc) {
            case "VP8 " -> checked(u16le(content, 23) & 0x3FFF, u16le(content, 25) & 0x3FFF);
            case "VP8L" -> {
                int first = content[21] & 0xFF;
                int second = content[22] & 0xFF;
                int third = content[23] & 0xFF;
                int fourth = content[24] & 0xFF;
                int width = 1 + (((second & 0x3F) << 8) | first);
                int height = 1 + (((fourth & 0x0F) << 10) | (third << 2) | ((second & 0xC0) >> 6));
                yield checked(width, height);
            }
            case "VP8X" -> checked(1 + u24le(content, 24), 1 + u24le(content, 27));
            default -> throw invalid();
        };
    }

    private static Dimensions checked(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw invalid();
        }
        return new Dimensions(width, height);
    }

    private static void require(byte[] content, int length) {
        if (content.length < length) {
            throw invalid();
        }
    }

    private static int int32be(byte[] content, int offset) {
        return ((content[offset] & 0xFF) << 24)
                | ((content[offset + 1] & 0xFF) << 16)
                | ((content[offset + 2] & 0xFF) << 8)
                | (content[offset + 3] & 0xFF);
    }

    private static int u16be(byte[] content, int offset) {
        if (offset + 2 > content.length) {
            throw invalid();
        }
        return ((content[offset] & 0xFF) << 8) | (content[offset + 1] & 0xFF);
    }

    private static int u16le(byte[] content, int offset) {
        if (offset + 2 > content.length) {
            throw invalid();
        }
        return (content[offset] & 0xFF) | ((content[offset + 1] & 0xFF) << 8);
    }

    private static int u24le(byte[] content, int offset) {
        if (offset + 3 > content.length) {
            throw invalid();
        }
        return (content[offset] & 0xFF)
                | ((content[offset + 1] & 0xFF) << 8)
                | ((content[offset + 2] & 0xFF) << 16);
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.BAD_REQUEST, "图片内容无法识别，请重新导出后再上传");
    }
}


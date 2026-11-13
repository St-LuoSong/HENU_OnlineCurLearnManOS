package com.onlinecurlearnmanos.file.storage;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 存储键生成与校验。键只包含随机十六进制与白名单扩展名，
 * 永不使用上传时提交的文件名，从根上消除路径穿越与覆盖攻击。
 */
public final class StoredKeys {

    private static final Pattern SAFE_KEY = Pattern.compile("^[a-z0-9]{32}\\.[a-z0-9]{1,8}$");

    private StoredKeys() {
    }

    public static String generate(String extension) {
        String normalized = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return UUID.randomUUID().toString().replace("-", "") + "." + normalized;
    }

    public static boolean isSafe(String storedKey) {
        return storedKey != null && SAFE_KEY.matcher(storedKey).matches();
    }
}

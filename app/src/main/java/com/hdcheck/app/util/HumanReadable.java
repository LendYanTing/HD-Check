package com.hdcheck.app.util;

/**
 * 人类可读的字节数格式化。1024 进制。
 */
public final class HumanReadable {

    private static final String[] UNITS = {"B", "KiB", "MiB", "GiB", "TiB"};

    private HumanReadable() {}

    public static String bytes(long bytes) {
        if (bytes < 0) bytes = 0;
        double value = bytes;
        int unitIdx = 0;
        while (value >= 1024.0 && unitIdx < UNITS.length - 1) {
            value /= 1024.0;
            unitIdx++;
        }
        if (unitIdx == 0) {
            return String.format("%.0f %s", value, UNITS[unitIdx]);
        } else {
            return String.format("%.1f %s", value, UNITS[unitIdx]);
        }
    }

    /**
     * 解析 UFS 寿命估算值 (0x00 ~ 0x0B)
     */
    public static String parseLifeEstimation(String rawHex) {
        if (rawHex == null || rawHex.isEmpty()) return "不支持";
        String hex = rawHex.trim().replace("0x", "").replace("0X", "");
        if (hex.isEmpty()) return "不支持";

        int dec;
        try {
            dec = Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return "未知 (" + rawHex + ")";
        }

        switch (dec) {
            case 0:  return "未定义";
            case 1:  return "0% – 10%";
            case 2:  return "10% – 20%";
            case 3:  return "20% – 30%";
            case 4:  return "30% – 40%";
            case 5:  return "40% – 50%";
            case 6:  return "50% – 60%";
            case 7:  return "60% – 70%";
            case 8:  return "70% – 80%";
            case 9:  return "80% – 90%";
            case 10: return "90% – 100%";
            case 11: return "已超过设计寿命";
            default: return "未知 (0x" + hex + ")";
        }
    }
}

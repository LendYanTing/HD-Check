package com.hdcheck.app.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.HashMap;
import java.util.Map;

/**
 * UID → 应用名 解析器。
 *
 * 两层策略：
 * 1. 从 /data/system/packages.list(Uid→包名) + PackageManager(包名→应用名)
 * 2. 回退：uid_NNNNN
 */
public final class PackageResolver {

    /** uid → 包名 映射（从 packages.list 解析） */
    private final Map<Integer, String> uidToPackage = new HashMap<>();
    /** 包名 → 应用名 缓存 */
    private final Map<String, String> nameCache = new HashMap<>();
    private final Context context;

    public PackageResolver(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 解析 packages.list 原始文本，填充 UID → 包名 映射。
     * 格式：com.example.app 10086 0 /data/user/0/com.example.app ...
     */
    public void loadPackageList(String rawText) {
        uidToPackage.clear();
        if (rawText == null || rawText.isEmpty()) return;

        for (String line : rawText.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            if (parts.length < 2) continue;

            String pkgName = parts[0];
            try {
                int uid = Integer.parseInt(parts[1]);
                uidToPackage.put(uid, pkgName);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /**
     * 获取 UID 对应的显示名称。
     * 优先应用名 → 回退包名 → 回退 uid_NNNNN
     */
    public String resolveLabel(int uid) {
        String pkg = uidToPackage.get(uid);
        if (pkg == null) return "uid_" + uid;

        // 查缓存
        String cached = nameCache.get(pkg);
        if (cached != null) return cached;

        // 查 PackageManager
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            String name = pm.getApplicationLabel(ai).toString();
            nameCache.put(pkg, name);
            return name;
        } catch (PackageManager.NameNotFoundException e) {
            // 包未安装或已卸载——使用包名本身
            nameCache.put(pkg, pkg);
            return pkg;
        }
    }

    /**
     * 预加载一批 UID 的显示名（批量缓存，避免重复查 PackageManager）。
     */
    public void preloadLabels(Iterable<Integer> uids) {
        for (int uid : uids) {
            resolveLabel(uid); // 触发缓存
        }
    }

    /** 清空全部缓存 */
    public void clear() {
        uidToPackage.clear();
        nameCache.clear();
    }
}

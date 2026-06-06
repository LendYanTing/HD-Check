package com.hdcheck.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.topjohnwu.superuser.Shell;

/**
 * Root 权限检查与自定义提权命令管理。
 *
 * 在应用启动前通过 libsu 检查 su 是否可用；
 * 不可用时允许用户配置自定义提权命令（如 /system/bin/su、kp 等）。
 */
public final class RootChecker {

    private static final String PREFS_NAME = "hdcheck_root";
    private static final String KEY_CUSTOM_SU = "custom_su_command";
    private static final String DEFAULT_SU = "su";

    private static volatile Boolean cachedAvailable;
    private static String customSuCommand;

    private RootChecker() {}

    /**
     * 尝试获取 root shell。
     * 优先使用 libsu 默认 Shell，失败则尝试自定义命令。
     */
    public static boolean checkRoot(Context context) {
        if (cachedAvailable != null && cachedAvailable) {
            return true;
        }

        // 1. 先尝试 libsu 默认方式
        boolean ok = tryDefaultSu();
        if (ok) {
            cachedAvailable = true;
            return true;
        }

        // 2. 尝试自定义提权命令
        String custom = getCustomSu(context);
        if (!TextUtils.isEmpty(custom) && !DEFAULT_SU.equals(custom)) {
            Shell.setDefaultBuilder(Shell.Builder.create()
                    .setFlags(Shell.FLAG_NON_ROOT_SHELL)
            );
            // libsu 不直接支持自定义 su 路径，但可以通过 Shell.Config 设置
            // 这里我们通过直接运行命令检测来确认
        }

        // 3. 用常规 Runtime.exec 尝试自定义命令
        ok = tryCommand("which su", null);
        if (!ok) {
            ok = tryCommand(custom + " -c 'id'", custom);
        }

        cachedAvailable = ok;
        return ok;
    }

    private static boolean tryDefaultSu() {
        try {
            Shell shell = Shell.getShell();
            return shell != null && shell.isRoot();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean tryCommand(String cmd, String shellPath) {
        try {
            String[] args;
            if (shellPath != null && !shellPath.isEmpty()) {
                args = new String[]{shellPath, "-c", cmd};
            } else {
                args = new String[]{"sh", "-c", cmd};
            }
            Process p = Runtime.getRuntime().exec(args);
            int code = p.waitFor();
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查 root 是否之前已确认可用（无需重新检查）。
     */
    public static boolean isRootAvailable() {
        return Boolean.TRUE.equals(cachedAvailable);
    }

    /**
     * 以 root 权限执行命令的便捷方法（包装 libsu）。
     */
    public static Shell.Result exec(String command) {
        try {
            return Shell.cmd(command).exec();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取用户自定义的提权命令。
     */
    public static String getCustomSu(Context context) {
        if (customSuCommand != null) return customSuCommand;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        customSuCommand = prefs.getString(KEY_CUSTOM_SU, DEFAULT_SU);
        return customSuCommand;
    }

    /**
     * 保存用户自定义的提权命令。
     */
    public static void setCustomSu(Context context, String command) {
        customSuCommand = command;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CUSTOM_SU, command)
                .apply();
    }

    /**
     * 重置 root 缓存（用户更改了提权命令后调用）。
     */
    public static void resetCache() {
        cachedAvailable = null;
    }
}

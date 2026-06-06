package com.hdcheck.app.service;

import android.util.Log;

import com.topjohnwu.superuser.Shell;

import java.util.ArrayList;
import java.util.List;

/**
 * Root shell 命令执行的薄封装。
 *
 * 确保 libsu Shell 在使用前已正确初始化，并提供带日志的便捷方法。
 */
final class RootShell {

    private static final String TAG = "HDCheck.RootShell";

    /** 缓存 Shell 初始化状态 */
    private static volatile boolean shellInitialized = false;
    private static volatile boolean shellAvailable = false;

    private RootShell() {}

    /**
     * 确保 libsu Shell 已初始化。首次调用时通过 Shell.getShell() 触发连接。
     */
    static synchronized void ensureShell() {
        if (shellInitialized) return;
        shellInitialized = true;
        try {
            Shell shell = Shell.getShell();
            shellAvailable = (shell != null && shell.isRoot());
            Log.i(TAG, "Shell initialized: root=" + shellAvailable);
        } catch (Exception e) {
            shellAvailable = false;
            Log.e(TAG, "Shell initialization failed", e);
        }
    }

    /** 返回缓存的 shell 可用状态 */
    static boolean isAvailable() {
        ensureShell();
        return shellAvailable;
    }

    /**
     * 执行一条 root shell 命令，返回 Shell.Result。
     * 注意：参数中的路径会被 shell-quote 保护，避免空格/特殊字符问题。
     */
    static Shell.Result exec(String command) {
        ensureShell();
        try {
            Log.d(TAG, "exec: " + command);
            Shell.Result r = Shell.cmd(command).exec();
            if (r != null) {
                Log.d(TAG, "exec success, exit=" + r.getCode()
                        + ", outLines=" + r.getOut().size()
                        + ", errLines=" + r.getErr().size());
            } else {
                Log.w(TAG, "exec returned null: " + command);
            }
            return r;
        } catch (Exception e) {
            Log.e(TAG, "exec exception: " + command, e);
            return null;
        }
    }

    /** 列出 /sys/block/ 下的所有块设备名 */
    static String[] listBlockDevices() {
        ensureShell();
        Shell.Result r = exec("ls /sys/block/");
        if (r == null || !r.isSuccess()) {
            Log.w(TAG, "listBlockDevices: shell failed");
            return new String[0];
        }
        List<String> out = r.getOut();
        List<String> result = new ArrayList<>();
        for (String line : out) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        Log.d(TAG, "listBlockDevices: " + result);
        return result.toArray(new String[0]);
    }

    /**
     * 读取文件内容（返回第一行，去除首尾空白）。
     * 修复：不再对 List 调用 toString()，避免 [value] 括号包裹问题。
     */
    static String readFile(String path) {
        ensureShell();
        Shell.Result r = exec("cat " + path + " 2>/dev/null");
        if (r == null || !r.isSuccess()) {
            Log.w(TAG, "readFile failed: path=" + path
                    + " success=" + (r != null && r.isSuccess()));
            return "";
        }
        List<String> out = r.getOut();
        if (out.isEmpty()) {
            Log.d(TAG, "readFile empty: " + path);
            return "";
        }
        // 取第一行，而非 List.toString()
        String result = out.get(0).trim();
        Log.d(TAG, "readFile: " + path + " => '" + result + "'");
        return result;
    }

    /**
     * 读取文件的全部内容（多行合并）。
     */
    static String readFileFull(String path) {
        ensureShell();
        Shell.Result r = exec("cat " + path + " 2>/dev/null");
        if (r == null || !r.isSuccess()) {
            Log.w(TAG, "readFileFull failed: " + path);
            return "";
        }
        List<String> out = r.getOut();
        if (out.isEmpty()) {
            Log.d(TAG, "readFileFull empty: " + path);
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < out.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(out.get(i));
        }
        String result = sb.toString().trim();
        Log.d(TAG, "readFileFull: " + path + " (" + out.size() + " lines, " + result.length() + " chars)");
        return result;
    }

    /**
     * 查找 health_descriptor 目录。
     * 修复：不再对 List 调用 toString()，避免返回 [path] 带括号。
     */
    static String findHealthDescriptor() {
        ensureShell();
        Shell.Result r = exec("find /sys -name health_descriptor -type d 2>/dev/null | head -n1");
        if (r == null || !r.isSuccess()) {
            Log.w(TAG, "findHealthDescriptor: shell failed");
            return "";
        }
        List<String> out = r.getOut();
        if (out.isEmpty()) {
            Log.d(TAG, "findHealthDescriptor: no result");
            return "";
        }
        String result = out.get(0).trim();
        Log.d(TAG, "findHealthDescriptor: '" + result + "'");
        return result;
    }
}

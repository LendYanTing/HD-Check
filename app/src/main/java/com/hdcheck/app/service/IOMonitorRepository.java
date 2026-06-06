package com.hdcheck.app.service;

import android.util.Log;

import com.hdcheck.app.model.AppRow;
import com.hdcheck.app.model.UidIoStat;
import com.hdcheck.app.util.PackageResolver;
import com.topjohnwu.superuser.Shell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * I/O 监控 Repository。
 *
 * 负责通过 root 读取 /proc/uid_io/stats 和 /data/system/packages.list，
 * 计算 delta，维护累计值，返回可用于 UI 的 AppRow 列表。
 */
public class IOMonitorRepository {

    private static final String TAG = "HDCheck.IOMonitor";
    private static final String CMD_STATS    = "cat /proc/uid_io/stats";
    private static final String CMD_PACKAGES = "cat /data/system/packages.list";

    private final Map<Integer, UidIoStat> prevStatsMap = new HashMap<>();
    /** 累计读/写/IO，key=uid, value=[accumRead, accumWrite, accumIo] */
    private final Map<Integer, long[]> accumMap = new HashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final PackageResolver packageResolver;

    public IOMonitorRepository(PackageResolver packageResolver) {
        this.packageResolver = packageResolver;
    }

    // ─── 解析 ───────────────────────────────────────────────────────

    /**
     * 解析 /proc/uid_io/stats 原始文本为 UidIoStat 列表。
     */
    private List<UidIoStat> parseStats(String raw) {
        List<UidIoStat> list = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            Log.w(TAG, "parseStats: empty input");
            return list;
        }

        int lineCount = 0;
        int skipped = 0;
        for (String line : raw.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String[] fields = line.split("\\s+");
            if (fields.length < 7) {
                skipped++;
                continue;
            }

            try {
                int uid = Integer.parseInt(fields[0]);
                UidIoStat s = new UidIoStat(uid);
                s.fgReadBytes  = Long.parseLong(fields[1]);
                s.fgWriteBytes = Long.parseLong(fields[2]);
                s.fgFsync      = Long.parseLong(fields[3]);
                s.bgReadBytes  = Long.parseLong(fields[4]);
                s.bgWriteBytes = Long.parseLong(fields[5]);
                s.bgFsync      = Long.parseLong(fields[6]);
                list.add(s);
                lineCount++;
            } catch (NumberFormatException ignored) {
                skipped++;
            }
        }
        Log.d(TAG, "parseStats: parsed=" + lineCount + " skipped=" + skipped);
        return list;
    }

    // ─── 单次采样 ────────────────────────────────────────────────────

    /**
     * 执行一次采样（读取 stats + packages.list），返回 AppRow 列表。
     *
     * @param intervalMs 距离上次采样的间隔（毫秒），用于计算速度
     * @param isFirst    是否首次采样（首次无 prev，不计算 delta）
     */
    public List<AppRow> sample(long intervalMs, boolean isFirst) {
        Log.d(TAG, "sample: intervalMs=" + intervalMs + " isFirst=" + isFirst
                + " shellAvailable=" + RootShell.isAvailable());

        // 读取 I/O 统计
        Shell.Result statsResult = RootShell.exec(CMD_STATS);
        if (statsResult == null || !statsResult.isSuccess()) {
            Log.w(TAG, "sample: stats shell failed, result="
                    + (statsResult == null ? "null" : "code=" + statsResult.getCode()));
            return new ArrayList<>();
        }

        // 使用 readFileFull 的正确方法读取多行内容
        String rawStats = RootShell.readFileFull("/proc/uid_io/stats");
        List<UidIoStat> currStats = parseStats(rawStats);

        if (currStats.isEmpty()) {
            Log.w(TAG, "sample: no stats parsed from " + rawStats.length() + " chars of input");
            return new ArrayList<>();
        }
        Log.d(TAG, "sample: got " + currStats.size() + " UID entries");

        // 读取包名映射（每次采样都刷新，因为可能有新安装/卸载的应用）
        Shell.Result pkgResult = RootShell.exec(CMD_PACKAGES);
        if (pkgResult != null && pkgResult.isSuccess()) {
            String rawPkgs = RootShell.readFileFull("/data/system/packages.list");
            packageResolver.loadPackageList(rawPkgs);
            Log.d(TAG, "sample: packages loaded");
        } else {
            Log.w(TAG, "sample: packages shell failed");
        }

        // 计算 delta
        Map<Integer, UidIoStat> currMap = new HashMap<>();
        for (UidIoStat s : currStats) {
            currMap.put(s.uid, s);
        }

        List<AppRow> rows = new ArrayList<>();
        double factor = (intervalMs > 0) ? (intervalMs / 1000.0) : 0;

        for (UidIoStat curr : currStats) {
            UidIoStat prev = prevStatsMap.get(curr.uid);
            UidIoStat delta;
            if (isFirst || prev == null) {
                delta = new UidIoStat(curr.uid);  // 全零
            } else {
                delta = UidIoStat.delta(prev, curr);
            }

            long speedRead  = (long) ((delta.fgReadBytes + delta.bgReadBytes) / Math.max(factor, 1.0));
            long speedWrite = (long) ((delta.fgWriteBytes + delta.bgWriteBytes) / Math.max(factor, 1.0));
            long speedIo    = speedRead + speedWrite;

            // 更新累计（首次采样不累加增量为0的数据，避免巨量初始值）
            long[] accum = accumMap.computeIfAbsent(curr.uid, k -> new long[3]);
            if (!isFirst) {
                accum[0] += Math.max(speedRead, 0);
                accum[1] += Math.max(speedWrite, 0);
                accum[2] += Math.max(speedIo, 0);
            }

            String label = packageResolver.resolveLabel(curr.uid);
            AppRow row = new AppRow(curr.uid, label);
            row.speedRead  = speedRead;
            row.speedWrite = speedWrite;
            row.speedIo    = speedIo;
            row.accumRead  = accum[0];
            row.accumWrite = accum[1];
            row.accumIo    = accum[2];
            rows.add(row);
        }

        // 保存当前快照
        prevStatsMap.clear();
        prevStatsMap.putAll(currMap);

        // 排序由 ViewModel 根据用户选择处理
        Log.d(TAG, "sample: " + rows.size() + " rows ready, isFirst=" + isFirst);

        return rows;
    }

    // ─── 生命周期 ────────────────────────────────────────────────────

    public boolean isRunning() {
        return running.get();
    }

    public void setRunning(boolean v) {
        running.set(v);
    }

    /**
     * 重置所有累计值和上次采样记录（重新开始监控）。
     */
    public void reset() {
        prevStatsMap.clear();
        accumMap.clear();
        Log.d(TAG, "reset: state cleared");
    }
}

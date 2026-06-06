package com.hdcheck.app.service;

import android.util.Log;

import com.hdcheck.app.model.DiskInfo;

/**
 * 硬盘健康检查 Repository。
 *
 * 对应 ufs_check.sh 的逻辑：
 * 1. 查找主块设备（sda / sdb / mmcblk0）
 * 2. 从 /sys/block/$dev/stat 读取扇区读写计数（使用 hw_sector_size）
 * 3. 查找 health_descriptor，读取寿命信息
 */
public class DiskHealthRepository {

    private static final String TAG = "HDCheck.DiskHealth";

    /**
     * 执行完整检查，返回 DiskInfo。
     */
    public DiskInfo check() {
        Log.i(TAG, "check: starting disk health check");
        DiskInfo info = new DiskInfo();

        // 1. 查找块设备
        String dev = findBlockDevice();
        if (dev.isEmpty()) {
            info.error = "未找到块设备";
            Log.w(TAG, "check: no block device found");
            return info;
        }
        info.blockDevice = dev;
        Log.i(TAG, "check: block device = " + dev);

        // 2. 读取扇区大小 —— 多路径回退
        int sectorSize = 512;
        String[] sectorSizePaths = {
            "/sys/block/" + dev + "/queue/hw_sector_size",
            "/sys/block/" + dev + "/queue/logical_block_size",
            "/sys/block/" + dev + "/queue/physical_block_size",
        };
        for (String p : sectorSizePaths) {
            String val = RootShell.readFile(p);
            if (!val.isEmpty()) {
                try {
                    sectorSize = Integer.parseInt(val);
                    Log.i(TAG, "check: sectorSize=" + sectorSize + " from " + p);
                    break;
                } catch (NumberFormatException e) {
                    Log.w(TAG, "check: failed to parse sectorSize from " + p + " val='" + val + "'");
                }
            }
        }
        if (sectorSize == 512) {
            Log.w(TAG, "check: using default sectorSize=512 (hw_sector_size not readable)");
        }
        info.sectorSize = sectorSize;

        // 3. 解析 stat —— 使用 readFileFull 处理多行情况
        String statContent = RootShell.readFileFull("/sys/block/" + dev + "/stat");
        if (!statContent.isEmpty()) {
            // stat 是单行，取第一行
            String firstLine = statContent.split("\n")[0];
            parseStat(firstLine, info);
            Log.d(TAG, "check: stat parse => readSectors="
                    + (info.totalReadBytes / Math.max(info.sectorSize, 1))
                    + " writeSectors="
                    + (info.totalWriteBytes / Math.max(info.sectorSize, 1)));
        } else {
            Log.w(TAG, "check: stat file empty for " + dev);
        }

        // 4. 查找 health_descriptor（修复后不带括号）
        String hpPath = RootShell.findHealthDescriptor();
        if (!hpPath.isEmpty()) {
            Log.i(TAG, "check: health_descriptor = " + hpPath);
            info.healthDescPath = hpPath;
            info.lifeEstimationA = RootShell.readFile(hpPath + "/life_time_estimation_a");
            info.lifeEstimationB = RootShell.readFile(hpPath + "/life_time_estimation_b");
            info.preEOLInfo = RootShell.readFile(hpPath + "/bPreEOLInfo");
            Log.i(TAG, "check: lifeEstimationA='" + info.lifeEstimationA
                    + "' lifeEstimationB='" + info.lifeEstimationB
                    + "' preEOL='" + info.preEOLInfo + "'");
        } else {
            Log.w(TAG, "check: no health_descriptor found");
        }

        Log.i(TAG, "check: complete, totalRead=" + info.totalReadBytes
                + " totalWrite=" + info.totalWriteBytes);
        return info;
    }

    /**
     * 查找主块设备，排除 loop / ram。
     */
    private String findBlockDevice() {
        for (String dev : new String[]{"sda", "sdb", "mmcblk0"}) {
            String stat = RootShell.readFile("/sys/block/" + dev + "/stat");
            if (!stat.isEmpty()) {
                Log.d(TAG, "findBlockDevice: found " + dev);
                return dev;
            }
        }

        // 兜底：遍历 /sys/block/
        String[] devices = RootShell.listBlockDevices();
        for (String d : devices) {
            d = d.trim();
            if (d.startsWith("loop") || d.startsWith("ram")) continue;
            String stat = RootShell.readFile("/sys/block/" + d + "/stat");
            if (!stat.isEmpty()) {
                Log.d(TAG, "findBlockDevice: found " + d + " (fallback)");
                return d;
            }
        }
        Log.w(TAG, "findBlockDevice: none found");
        return "";
    }

    /**
     * 解析 /sys/block/$dev/stat：
     * 第 3 列 = 读扇区数（0-indexed: field[2]），第 7 列 = 写扇区数（field[6]）
     */
    private void parseStat(String statLine, DiskInfo info) {
        String[] fields = statLine.split("\\s+");
        if (fields.length < 7) {
            Log.w(TAG, "parseStat: insufficient fields (" + fields.length + ")");
            return;
        }

        try {
            long readSectors  = Long.parseLong(fields[2]);
            long writeSectors = Long.parseLong(fields[6]);
            info.totalReadBytes  = readSectors  * info.sectorSize;
            info.totalWriteBytes = writeSectors * info.sectorSize;
        } catch (NumberFormatException e) {
            Log.w(TAG, "parseStat: number parse error", e);
        }
    }
}

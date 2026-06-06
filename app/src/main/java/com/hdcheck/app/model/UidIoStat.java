package com.hdcheck.app.model;

/**
 * 单个 UID 的 I/O 统计快照。
 * 对应 /proc/uid_io/stats 每行 7 列：
 * uid fg_read_bytes fg_write_bytes fg_fsync bg_read_bytes bg_write_bytes bg_fsync
 */
public class UidIoStat {
    public final int uid;
    public long fgReadBytes;
    public long fgWriteBytes;
    public long fgFsync;
    public long bgReadBytes;
    public long bgWriteBytes;
    public long bgFsync;

    public UidIoStat(int uid) {
        this.uid = uid;
    }

    /** 总 I/O 量（读+写，不含 fsync） */
    public long totalIo() {
        return fgReadBytes + fgWriteBytes + bgReadBytes + bgWriteBytes;
    }

    /** 计算两次采样之间的差值（delta），返回新的 UidIoStat */
    public static UidIoStat delta(UidIoStat prev, UidIoStat curr) {
        if (prev == null || prev.uid != curr.uid) {
            return copy(curr);
        }
        UidIoStat d = new UidIoStat(curr.uid);
        d.fgReadBytes  = saturatingSub(curr.fgReadBytes,  prev.fgReadBytes);
        d.fgWriteBytes = saturatingSub(curr.fgWriteBytes, prev.fgWriteBytes);
        d.fgFsync      = saturatingSub(curr.fgFsync,      prev.fgFsync);
        d.bgReadBytes  = saturatingSub(curr.bgReadBytes,  prev.bgReadBytes);
        d.bgWriteBytes = saturatingSub(curr.bgWriteBytes, prev.bgWriteBytes);
        d.bgFsync      = saturatingSub(curr.bgFsync,      prev.bgFsync);
        return d;
    }

    public static UidIoStat copy(UidIoStat src) {
        UidIoStat c = new UidIoStat(src.uid);
        c.fgReadBytes  = src.fgReadBytes;
        c.fgWriteBytes = src.fgWriteBytes;
        c.fgFsync      = src.fgFsync;
        c.bgReadBytes  = src.bgReadBytes;
        c.bgWriteBytes = src.bgWriteBytes;
        c.bgFsync      = src.bgFsync;
        return c;
    }

    private static long saturatingSub(long a, long b) {
        return a >= b ? a - b : 0;
    }
}

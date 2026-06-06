package com.hdcheck.app.model;

/**
 * RecyclerView 展示用的一行数据。
 */
public class AppRow {
    public String label;       // 应用名 / uid_NNNNN
    public int uid;
    /** 当前速率：本轮 delta 的总 IO 量 */
    public long speedRead;
    public long speedWrite;
    public long speedIo;
    /** 累计量：从开始监控以来的总和（速度 × 间隔累加） */
    public long accumRead;
    public long accumWrite;
    public long accumIo;

    public AppRow(int uid, String label) {
        this.uid = uid;
        this.label = label;
    }
}

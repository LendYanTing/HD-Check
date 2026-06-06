package com.hdcheck.app.model;

/**
 * 硬盘健康信息。
 */
public class DiskInfo {
    /** 块设备名，如 "sda" */
    public String blockDevice;
    /** 扇区大小 */
    public int sectorSize = 512;
    /** 总读取字节 */
    public long totalReadBytes;
    /** 总写入字节 */
    public long totalWriteBytes;
    /** 健康描述符路径 */
    public String healthDescPath;
    /** 平均磨损寿命原始值 */
    public String lifeEstimationA;
    /** 最差磨损寿命原始值 */
    public String lifeEstimationB;
    /** 预寿命警告 */
    public String preEOLInfo;
    /** 错误信息 */
    public String error;
}

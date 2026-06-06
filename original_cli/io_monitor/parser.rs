//! 解析 `/proc/uid_io/stats` 文件。
//!
//! 格式（每行 7 列，空格分隔）：
//! ```text
//! uid fg_read_bytes fg_write_bytes fg_fsync bg_read_bytes bg_write_bytes bg_fsync
//! ```

use std::fs::File;
use std::io::{BufRead, BufReader};
use std::path::Path;

/// 单个 UID 的 I/O 统计
#[derive(Debug, Clone, Default)]
pub struct UidIoStat {
    pub uid: u32,
    pub fg_read_bytes: u64,
    pub fg_write_bytes: u64,
    pub fg_fsync: u64,
    pub bg_read_bytes: u64,
    pub bg_write_bytes: u64,
    pub bg_fsync: u64,
}

impl UidIoStat {
    /// 总 I/O 量（读 + 写，不含 fsync），用于排序
    pub fn total_io(&self) -> u64 {
        self.fg_read_bytes
            .saturating_add(self.fg_write_bytes)
            .saturating_add(self.bg_read_bytes)
            .saturating_add(self.bg_write_bytes)
    }
}

/// 解析 `/proc/uid_io/stats`，返回所有 UID 的统计
pub fn parse_uid_io_stats(path: &Path) -> anyhow::Result<Vec<UidIoStat>> {
    let file = File::open(path)?;
    let reader = BufReader::new(file);
    let mut stats = Vec::new();

    for line in reader.lines() {
        let line = line?;
        let line = line.trim();
        if line.is_empty() {
            continue;
        }

        let fields: Vec<&str> = line.split_whitespace().collect();
        if fields.len() < 7 {
            continue; // 跳过格式不正确的行
        }

        let uid: u32 = fields[0].parse()?;
        let stat = UidIoStat {
            uid,
            fg_read_bytes: fields[1].parse()?,
            fg_write_bytes: fields[2].parse()?,
            fg_fsync: fields[3].parse()?,
            bg_read_bytes: fields[4].parse()?,
            bg_write_bytes: fields[5].parse()?,
            bg_fsync: fields[6].parse()?,
        };
        stats.push(stat);
    }

    Ok(stats)
}

/// 比较两次采样的差值，计算出增量（delta），用于展示"实时"速率
pub fn delta_stats(prev: &[UidIoStat], curr: &[UidIoStat]) -> Vec<UidIoStat> {
    // 构建 prev 的 uid → stat 映射
    use std::collections::HashMap;
    let prev_map: HashMap<u32, &UidIoStat> = prev.iter().map(|s| (s.uid, s)).collect();

    curr.iter()
        .map(|c| {
            if let Some(p) = prev_map.get(&c.uid) {
                UidIoStat {
                    uid: c.uid,
                    fg_read_bytes: c.fg_read_bytes.saturating_sub(p.fg_read_bytes),
                    fg_write_bytes: c.fg_write_bytes.saturating_sub(p.fg_write_bytes),
                    fg_fsync: c.fg_fsync.saturating_sub(p.fg_fsync),
                    bg_read_bytes: c.bg_read_bytes.saturating_sub(p.bg_read_bytes),
                    bg_write_bytes: c.bg_write_bytes.saturating_sub(p.bg_write_bytes),
                    bg_fsync: c.bg_fsync.saturating_sub(p.bg_fsync),
                }
            } else {
                c.clone() // 新出现的 UID，直接使用当前值
            }
        })
        .collect()
}

#[cfg(test)]
mod tests {
    #[test]
    fn test_parse_sample_line() {
        let line = "10086 12345 67890 10 111 222 0";
        let fields: Vec<&str> = line.split_whitespace().collect();
        assert_eq!(fields.len(), 7);
        let uid: u32 = fields[0].parse().unwrap();
        assert_eq!(uid, 10086);
    }
}

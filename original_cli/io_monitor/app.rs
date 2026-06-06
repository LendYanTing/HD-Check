//! 应用状态与排序逻辑。

use std::collections::HashMap;

use crate::packages::uid_label;
use crate::parser::UidIoStat;

/// 排序模式
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SortMode {
    /// 按总 I/O 量降序（默认）
    IoDesc,
    /// 按包名 / UID 标签字母升序
    NameAsc,
}

impl SortMode {
    /// 切换到下一个排序模式
    pub fn next(self) -> Self {
        match self {
            SortMode::IoDesc => SortMode::NameAsc,
            SortMode::NameAsc => SortMode::IoDesc,
        }
    }

    pub fn label(self) -> &'static str {
        match self {
            SortMode::IoDesc => "I/O (降序)",
            SortMode::NameAsc => "名称 (升序)",
        }
    }
}

/// 一条展示用的行数据
#[derive(Debug, Clone)]
#[allow(dead_code)]
pub struct Row {
    pub label: String,
    pub uid: u32,
    pub fg_read: u64,
    pub fg_write: u64,
    pub bg_read: u64,
    pub bg_write: u64,
    pub total_io: u64,
}

/// 应用全局状态
pub struct App {
    /// 当前展示的行数据
    pub rows: Vec<Row>,
    /// 排序模式
    pub sort_mode: SortMode,
    /// UID → 包名映射（首次加载后持存）
    pub pkg_map: HashMap<u32, String>,
    /// 上次采样的原始数据，用于计算增量
    pub prev_stats: Vec<UidIoStat>,
}

impl App {
    pub fn new() -> Self {
        Self {
            rows: Vec::new(),
            sort_mode: SortMode::IoDesc,
            pkg_map: HashMap::new(),
            prev_stats: Vec::new(),
        }
    }

    /// 用新的 stats 数据更新应用状态
    pub fn update(&mut self, curr_stats: &[UidIoStat]) {
        // 计算增量
        let delta = crate::parser::delta_stats(&self.prev_stats, curr_stats);

        // 构建行数据
        self.rows = delta
            .iter()
            .map(|s| {
                let label = uid_label(s.uid, &self.pkg_map);
                Row {
                    label,
                    uid: s.uid,
                    fg_read: s.fg_read_bytes,
                    fg_write: s.fg_write_bytes,
                    bg_read: s.bg_read_bytes,
                    bg_write: s.bg_write_bytes,
                    total_io: s.total_io(),
                }
            })
            .collect();

        // 排序
        self.sort_rows();

        // 保存当前采样供下次计算增量
        self.prev_stats = curr_stats.to_vec();
    }

    /// 根据当前排序模式对行排序
    fn sort_rows(&mut self) {
        match self.sort_mode {
            SortMode::IoDesc => {
                self.rows.sort_by(|a, b| b.total_io.cmp(&a.total_io));
            }
            SortMode::NameAsc => {
                self.rows
                    .sort_by(|a, b| a.label.to_lowercase().cmp(&b.label.to_lowercase()));
            }
        }
    }

    /// 切换排序模式并重新排序
    pub fn toggle_sort(&mut self) {
        self.sort_mode = self.sort_mode.next();
        self.sort_rows();
    }
}

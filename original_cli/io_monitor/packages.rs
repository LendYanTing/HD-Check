//! UID → 包名映射。
//!
//! 在已 root 的 Android 设备上，从 `/data/system/packages.list` 读取映射。
//!
//! 格式（空格分隔）：
//! ```text
//! com.example.app 10086 0 /data/user/0/com.example.app platform:privapp:targetSdkVersion=34 ...
//! ```

use std::collections::HashMap;
use std::fs::File;
use std::io::{BufRead, BufReader};
use std::path::Path;

/// 从 `/data/system/packages.list` 构建 UID → 包名 映射
pub fn load_package_map(path: &Path) -> anyhow::Result<HashMap<u32, String>> {
    let file = File::open(path)?;
    let reader = BufReader::new(file);
    let mut map = HashMap::new();

    for line in reader.lines() {
        let line = line?;
        let line = line.trim();
        if line.is_empty() {
            continue;
        }

        let parts: Vec<&str> = line.split_whitespace().collect();
        if parts.len() < 2 {
            continue;
        }

        let pkg_name = parts[0].to_string();
        if let Ok(uid) = parts[1].parse::<u32>() {
            map.insert(uid, pkg_name);
        }
    }

    Ok(map)
}

/// 为给定的 UID 生成可读标签：优先包名，其次显示 UID 数字
pub fn uid_label(uid: u32, pkg_map: &HashMap<u32, String>) -> String {
    pkg_map
        .get(&uid)
        .cloned()
        .unwrap_or_else(|| format!("uid_{}", uid))
}

#[cfg(test)]
mod tests {
    #[test]
    fn test_parse_packages_line() {
        let line = "com.tencent.mm 10123 0 /data/user/0/com.tencent.mm platform:privapp:targetSdkVersion=33 none";
        let parts: Vec<&str> = line.split_whitespace().collect();
        assert_eq!(parts[0], "com.tencent.mm");
        assert_eq!(parts[1], "10123");
    }
}

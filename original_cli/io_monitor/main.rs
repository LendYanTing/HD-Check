//! Android UID I/O 监控 TUI 应用入口。
//!
//! 在已 root 的 Android 设备上运行，实时展示 `/proc/uid_io/stats` 中
//! 每个 UID（应用）的读写 I/O 占用情况。

mod app;
mod packages;
mod parser;
mod ui;

use std::io;
use std::path::Path;
use std::time::Duration;

use crossterm::{
    event::{self, Event, KeyCode, KeyEventKind},
    execute,
    terminal::{disable_raw_mode, enable_raw_mode, EnterAlternateScreen, LeaveAlternateScreen},
};
use ratatui::backend::{Backend, CrosstermBackend};
use ratatui::Terminal;

use crate::app::App;

const STATS_PATH: &str = "/proc/uid_io/stats";
const PACKAGES_PATH: &str = "/data/system/packages.list";
const REFRESH_INTERVAL: Duration = Duration::from_millis(1000);

fn main() -> anyhow::Result<()> {
    // 初始化终端
    enable_raw_mode()?;
    let mut stdout = io::stdout();
    execute!(stdout, EnterAlternateScreen)?;
    let backend = CrosstermBackend::new(stdout);
    let mut terminal = Terminal::new(backend)?;

    let result = run_app(&mut terminal);

    // 恢复终端
    disable_raw_mode()?;
    execute!(terminal.backend_mut(), LeaveAlternateScreen)?;
    terminal.show_cursor()?;

    result
}

fn run_app<B: Backend>(terminal: &mut Terminal<B>) -> anyhow::Result<()> {
    let mut app = App::new();

    // 尝试加载包名映射（静默失败，没映射就用 uid_NNNNN）
    if let Ok(pkg_map) = packages::load_package_map(Path::new(PACKAGES_PATH)) {
        app.pkg_map = pkg_map;
    }

    // 首次加载 I/O 数据，建立基线
    if let Ok(stats) = parser::parse_uid_io_stats(Path::new(STATS_PATH)) {
        app.update(&stats);
    }

    loop {
        // 渲染
        terminal.draw(|f| ui::render(f, &app))?;

        // 等待事件或刷新超时
        if event::poll(REFRESH_INTERVAL)? {
            if let Event::Key(key) = event::read()? {
                // 只处理按下事件，忽略释放和重复
                if key.kind != KeyEventKind::Press {
                    continue;
                }
                match key.code {
                    KeyCode::Char('q') | KeyCode::Esc => return Ok(()),
                    KeyCode::Char('s') => app.toggle_sort(),
                    _ => {}
                }
            }
        }

        // 每次循环都刷新 I/O 数据
        if let Ok(stats) = parser::parse_uid_io_stats(Path::new(STATS_PATH)) {
            app.update(&stats);
        }
    }
}

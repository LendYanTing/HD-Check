//! TUI 渲染模块 —— 使用 ratatui + crossterm。

use ratatui::{
    layout::{Constraint, Layout, Rect},
    style::{Color, Modifier, Style},
    text::Span,
    widgets::{Block, Borders, Cell, Paragraph, Row as TuiRow, Table, TableState},
    Frame,
};

use crate::app::{App, Row};

/// 将字节数格式化为人类可读的字符串
fn human_bytes(bytes: u64) -> String {
    const UNITS: &[&str] = &["B", "K", "M", "G", "T"];
    let mut value = bytes as f64;
    let mut unit_idx = 0;

    while value >= 1024.0 && unit_idx < UNITS.len() - 1 {
        value /= 1024.0;
        unit_idx += 1;
    }

    if unit_idx == 0 {
        format!("{:.0}{}", value, UNITS[unit_idx])
    } else {
        format!("{:.1}{}", value, UNITS[unit_idx])
    }
}

pub fn render(f: &mut Frame, app: &App) {
    let area = f.area();

    // 垂直布局：标题栏 + 表格 + 状态栏
    let chunks = Layout::vertical([
        Constraint::Length(2), // 标题
        Constraint::Min(0),    // 表格（占满剩余）
        Constraint::Length(2), // 状态栏
    ])
    .split(area);

    render_title(f, chunks[0]);
    render_table(f, chunks[1], app);
    render_status_bar(f, chunks[2], app);
}

fn render_title(f: &mut Frame, area: Rect) {
    let title = Paragraph::new(Span::styled(
        " UID I/O Monitor — /proc/uid_io/stats ",
        Style::default()
            .fg(Color::Cyan)
            .add_modifier(Modifier::BOLD),
    ))
    .block(Block::default().borders(Borders::NONE));
    f.render_widget(title, area);
}

fn render_table(f: &mut Frame, area: Rect, app: &App) {
    // 表头
    let header_cells = ["应用", "FG 读", "FG 写", "BG 读", "BG 写", "总 I/O"]
        .iter()
        .map(|h| Cell::from(Span::styled(*h, Style::default().fg(Color::Yellow))));

    let header = TuiRow::new(header_cells)
        .style(Style::default().add_modifier(Modifier::BOLD))
        .height(1);

    // 数据行
    let rows: Vec<TuiRow> = app.rows.iter().map(|row| build_row(row)).collect();

    // 列宽约束
    let widths = [
        Constraint::Percentage(35), // 应用名
        Constraint::Percentage(13), // FG 读
        Constraint::Percentage(13), // FG 写
        Constraint::Percentage(13), // BG 读
        Constraint::Percentage(13), // BG 写
        Constraint::Percentage(13), // 总 I/O
    ];

    let table = Table::new(rows, widths)
        .header(header)
        .block(
            Block::default()
                .borders(Borders::ALL)
                .title(" 实时 I/O 统计 ")
                .style(Style::default().fg(Color::White)),
        )
        .row_highlight_style(
            Style::default()
                .bg(Color::DarkGray)
                .add_modifier(Modifier::BOLD),
        )
        .column_spacing(2);

    f.render_stateful_widget(table, area, &mut TableState::default());
}

fn build_row(row: &Row) -> TuiRow<'static> {
    // 根据 I/O 量级染色
    let io_style = if row.total_io > 10_000_000 {
        Style::default().fg(Color::Red)
    } else if row.total_io > 1_000_000 {
        Style::default().fg(Color::Yellow)
    } else {
        Style::default().fg(Color::Green)
    };

    TuiRow::new(vec![
        Cell::from(Span::styled(
            row.label.clone(),
            Style::default().fg(Color::White),
        )),
        Cell::from(human_bytes(row.fg_read)),
        Cell::from(human_bytes(row.fg_write)),
        Cell::from(human_bytes(row.bg_read)),
        Cell::from(human_bytes(row.bg_write)),
        Cell::from(Span::styled(human_bytes(row.total_io), io_style)),
    ])
}

fn render_status_bar(f: &mut Frame, area: Rect, app: &App) {
    let sort_label = app.sort_mode.label();

    let help = format!(
        " s: 切换排序 | q: 退出 | {} | 共 {} 个应用 ",
        sort_label,
        app.rows.len()
    );

    let status = Paragraph::new(Span::styled(help, Style::default().fg(Color::DarkGray)))
        .block(Block::default().borders(Borders::NONE));

    f.render_widget(status, area);
}

"""Tkinter desktop UI: paste/open a log, see per-table SQL counts, filter and
drill into individual statements."""

from __future__ import annotations

import tkinter as tk
from tkinter import filedialog, messagebox, ttk

from charset_normalizer import from_bytes

from .logparser import extract_sql_blocks
from .model import AnalysisResult
from .sql_analyzer import CommentType, SqlRecord, SqlType, analyze as analyze_sql

_SQL_TYPES = [SqlType.SELECT, SqlType.INSERT, SqlType.UPDATE, SqlType.DELETE, SqlType.MERGE, SqlType.OTHER]

_COMMENT_COLORS = {
    CommentType.HINT: "#008000",
    CommentType.STATEMENT_ID: "#787878",
    CommentType.BLOCK: "#966428",
    CommentType.LINE: "#966428",
}


def _read_text_auto(path: str) -> str | None:
    """Reads a log file whose encoding is unknown ahead of time.

    A naive "try utf-8, fall back to cp949 on UnicodeDecodeError" approach
    can silently mis-decode: cp949-encoded Korean bytes occasionally form a
    byte sequence that is *also* valid (but garbage) UTF-8, so the utf-8
    attempt succeeds without raising and the text comes out corrupted
    instead of failing loudly. charset_normalizer scores candidate
    encodings by how coherent the resulting text actually is, which catches
    that case.
    """
    with open(path, "rb") as f:
        raw = f.read()
    if raw.startswith(b"\xef\xbb\xbf"):
        return raw.decode("utf-8-sig")
    best = from_bytes(raw).best()
    return str(best) if best is not None else None


class MainWindow(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title("DBLogAnalyzer - iBatis/Oracle SQL 로그 분석기")
        self.geometry("1200x800")

        self.result: AnalysisResult | None = None
        self.selected_table: str | None = None
        self.type_filters: dict[SqlType, tk.BooleanVar] = {}
        self.search_var = tk.StringVar()

        self._build_input_panel()
        self._build_result_panel()
        self._build_status_bar()

    # ---------- input panel ----------

    def _build_input_panel(self) -> None:
        frame = ttk.LabelFrame(self, text="로그 붙여넣기")
        frame.pack(fill="x", padx=4, pady=4)

        self.log_input = tk.Text(frame, height=10, wrap="none")
        self.log_input.pack(fill="both", expand=True, padx=4, pady=4)

        button_bar = ttk.Frame(frame)
        button_bar.pack(fill="x", padx=4, pady=(0, 4))
        ttk.Button(button_bar, text="파일 열기...", command=self._open_file).pack(side="left")
        ttk.Button(button_bar, text="분석 실행", command=self._run_analysis).pack(side="left", padx=(6, 0))

    def _open_file(self) -> None:
        path = filedialog.askopenfilename()
        if not path:
            return
        try:
            content = _read_text_auto(path)
        except OSError as e:
            messagebox.showerror("오류", f"파일을 읽을 수 없습니다: {e}")
            return
        if content is None:
            messagebox.showerror("오류", "파일 인코딩을 인식할 수 없습니다.")
            return
        self.log_input.delete("1.0", tk.END)
        self.log_input.insert("1.0", content)
        self.status_label.config(text=f"파일을 불러왔습니다: {path}")

    # ---------- result panel ----------

    def _build_result_panel(self) -> None:
        paned = ttk.PanedWindow(self, orient="horizontal")
        paned.pack(fill="both", expand=True, padx=4, pady=4)

        left = ttk.LabelFrame(paned, text="테이블별 SQL 사용 현황 (행 클릭 시 필터)")
        paned.add(left, weight=1)

        columns = ("table", "SELECT", "INSERT", "UPDATE", "DELETE", "MERGE", "total")
        headers = ("테이블", "SELECT", "INSERT", "UPDATE", "DELETE", "MERGE", "합계")
        self.table_stats_view = ttk.Treeview(left, columns=columns, show="headings")
        for col, header in zip(columns, headers):
            self.table_stats_view.heading(col, text=header)
            self.table_stats_view.column(col, width=90 if col != "table" else 160)
        self.table_stats_view.pack(fill="both", expand=True, padx=4, pady=4)
        self.table_stats_view.bind("<<TreeviewSelect>>", self._on_table_selected)

        right = ttk.LabelFrame(paned, text="SQL 목록 (더블클릭 시 상세보기)")
        paned.add(right, weight=2)

        filter_bar = ttk.Frame(right)
        filter_bar.pack(fill="x", padx=4, pady=4)
        ttk.Label(filter_bar, text="SQL 종류:").pack(side="left")
        for sql_type in _SQL_TYPES:
            var = tk.BooleanVar(value=True)
            self.type_filters[sql_type] = var
            ttk.Checkbutton(filter_bar, text=sql_type.name, variable=var,
                             command=self._refresh_sql_list).pack(side="left", padx=2)
        ttk.Button(filter_bar, text="테이블 필터 해제", command=self._clear_table_filter).pack(side="left", padx=(10, 0))

        search_bar = ttk.Frame(right)
        search_bar.pack(fill="x", padx=4, pady=(0, 4))
        ttk.Label(search_bar, text="컬럼/값 검색:").pack(side="left")
        search_entry = ttk.Entry(search_bar, textvariable=self.search_var)
        search_entry.pack(side="left", fill="x", expand=True, padx=(4, 4))
        self.search_var.trace_add("write", lambda *_: self._refresh_sql_list())
        ttk.Button(search_bar, text="지우기", command=lambda: self.search_var.set("")).pack(side="left")

        list_columns = ("seq", "timestamp", "type", "tables", "summary", "parsed")
        list_headers = ("#", "시각", "타입", "테이블", "SQL 요약", "파싱")
        self.sql_list_view = ttk.Treeview(right, columns=list_columns, show="headings")
        for col, header in zip(list_columns, list_headers):
            self.sql_list_view.heading(col, text=header)
        self.sql_list_view.column("seq", width=40)
        self.sql_list_view.column("timestamp", width=140)
        self.sql_list_view.column("type", width=70)
        self.sql_list_view.column("tables", width=200)
        self.sql_list_view.column("summary", width=400)
        self.sql_list_view.column("parsed", width=50)
        self.sql_list_view.pack(fill="both", expand=True, padx=4, pady=4)
        self.sql_list_view.bind("<Double-1>", self._open_selected_detail)

        self._sql_records_by_row: dict[str, SqlRecord] = {}

    def _build_status_bar(self) -> None:
        self.status_label = ttk.Label(self, text="로그를 불러오거나 붙여넣은 뒤 [분석 실행]을 누르세요.")
        self.status_label.pack(fill="x", side="bottom", padx=4, pady=2)

    # ---------- actions ----------

    def _run_analysis(self) -> None:
        text = self.log_input.get("1.0", tk.END)
        if not text.strip():
            messagebox.showwarning("안내", "분석할 로그 내용이 없습니다.")
            return

        blocks = extract_sql_blocks(text)
        records = [analyze_sql(b) for b in blocks]
        self.result = AnalysisResult.of(records)
        self.selected_table = None
        self.table_stats_view.selection_remove(self.table_stats_view.selection())

        self._populate_table_stats()
        self._refresh_sql_list()

        self.status_label.config(
            text=f"SQL {len(records)}건 분석 완료 (테이블 {len(self.result.table_stats)}개, "
                 f"파싱 실패 {self.result.parse_failure_count()}건)"
        )

    def _populate_table_stats(self) -> None:
        self.table_stats_view.delete(*self.table_stats_view.get_children())
        if not self.result:
            return
        stats = sorted(self.result.table_stats.values(), key=lambda s: s.total(), reverse=True)
        for stat in stats:
            values = (stat.table, stat.count(SqlType.SELECT), stat.count(SqlType.INSERT),
                      stat.count(SqlType.UPDATE), stat.count(SqlType.DELETE), stat.count(SqlType.MERGE),
                      stat.total())
            self.table_stats_view.insert("", "end", iid=stat.table, values=values)

    def _on_table_selected(self, _event) -> None:
        selection = self.table_stats_view.selection()
        self.selected_table = selection[0] if selection else None
        self._refresh_sql_list()

    def _clear_table_filter(self) -> None:
        self.selected_table = None
        self.table_stats_view.selection_remove(self.table_stats_view.selection())
        self._refresh_sql_list()

    def _refresh_sql_list(self) -> None:
        self.sql_list_view.delete(*self.sql_list_view.get_children())
        self._sql_records_by_row.clear()
        if not self.result:
            return

        enabled_types = {t for t, var in self.type_filters.items() if var.get()}
        search_term = self.search_var.get().strip().lower()
        for record in self.result.records:
            if record.type not in enabled_types:
                continue
            if self.selected_table is not None and self.selected_table not in record.tables:
                continue
            if search_term and not any(
                search_term in cv.column.lower() or search_term in cv.value.lower()
                for cv in record.column_values
            ):
                continue
            row_id = str(record.sequence)
            summary = " ".join(record.raw_sql.split())
            if len(summary) > 120:
                summary = summary[:120] + "..."
            values = (record.sequence, record.timestamp, record.type.name, ", ".join(record.tables),
                      summary, "OK" if record.parsed_ok else "실패")
            self.sql_list_view.insert("", "end", iid=row_id, values=values)
            self._sql_records_by_row[row_id] = record

    def _open_selected_detail(self, _event) -> None:
        selection = self.sql_list_view.selection()
        if not selection:
            return
        record = self._sql_records_by_row.get(selection[0])
        if record:
            SqlDetailWindow(self, record)


class SqlDetailWindow(tk.Toplevel):
    def __init__(self, master: tk.Misc, record: SqlRecord) -> None:
        super().__init__(master)
        self.title(f"SQL 상세 - #{record.sequence} {record.type.name}")
        self.geometry("900x700")

        tag = record.statement_id_tag()
        info = f"시각: {record.timestamp}   스레드: {record.thread}"
        if tag:
            info += f"   구문ID: {tag}"
        if not record.parsed_ok:
            info += f"   [파싱 실패: {record.parse_error}]"
        ttk.Label(self, text=info).pack(fill="x", padx=4, pady=4)

        paned = ttk.PanedWindow(self, orient="vertical")
        paned.pack(fill="both", expand=True, padx=4, pady=4)

        text_frame = ttk.Frame(paned)
        paned.add(text_frame, weight=3)
        text = tk.Text(text_frame, wrap="none", font=("Courier New", 11))
        text.pack(fill="both", expand=True)
        text.insert("1.0", record.raw_sql)
        for comment_type, color in _COMMENT_COLORS.items():
            text.tag_configure(comment_type.name, foreground=color)
        for comment in record.comments:
            start = f"1.0+{comment.start}c"
            end = f"1.0+{comment.end}c"
            text.tag_add(comment.type.name, start, end)
        text.configure(state="disabled")

        table_frame = ttk.Frame(paned)
        paned.add(table_frame, weight=2)
        columns = ("clause", "table", "column", "operator", "value")
        headers = ("절", "테이블/별칭", "컬럼", "연산자", "값")
        cv_view = ttk.Treeview(table_frame, columns=columns, show="headings")
        for col, header in zip(columns, headers):
            cv_view.heading(col, text=header)
        cv_view.pack(fill="both", expand=True)
        for cv in record.column_values:
            cv_view.insert("", "end", values=(cv.clause, cv.table, cv.column, cv.operator, cv.value))


def main() -> None:
    MainWindow().mainloop()

"""Splits raw batch-job log text into individual SQL blocks.

The observed log format prefixes every log entry with
"yyyy-MM-dd HH:mm:ss,SSS LEVEL thread logger - message", and a SQL
statement is logged as a "SQL:" marker line followed by the fully
resolved SQL text (bind values already inlined) spanning an arbitrary
number of un-prefixed lines, until the next prefixed log line appears.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

_LOG_PREFIX = re.compile(
    r"^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3})\s+(\S+)\s+(\S+)\s+(\S+)\s+-\s?(.*)$"
)
_SQL_MARKER = re.compile(r"^SQL\s*:\s*(.*)$", re.IGNORECASE)


@dataclass(frozen=True)
class RawSqlBlock:
    sequence: int
    timestamp: str
    thread: str
    raw_sql: str


def extract_sql_blocks(log_text: str) -> list[RawSqlBlock]:
    blocks: list[RawSqlBlock] = []
    lines = re.split(r"\r\n|\r|\n", log_text)

    current_timestamp: str | None = None
    current_thread: str | None = None
    current_sql_lines: list[str] | None = None
    sequence = 0

    def flush() -> None:
        nonlocal current_sql_lines, sequence
        if current_sql_lines is not None:
            sequence += 1
            blocks.append(
                RawSqlBlock(sequence, current_timestamp, current_thread, "\n".join(current_sql_lines).strip())
            )
            current_sql_lines = None

    for line in lines:
        m = _LOG_PREFIX.match(line)
        if m:
            flush()
            message = m.group(5)
            sm = _SQL_MARKER.match(message.strip())
            if sm:
                current_timestamp = m.group(1)
                current_thread = m.group(3)
                current_sql_lines = []
                inline = sm.group(1)
                if inline.strip():
                    current_sql_lines.append(inline)
        elif current_sql_lines is not None:
            current_sql_lines.append(line)

    flush()
    return blocks

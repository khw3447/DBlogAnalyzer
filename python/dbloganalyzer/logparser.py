"""Splits raw batch-job log text into individual SQL blocks.

Two log line prefix formats are recognized, tried in this order:

- Legacy: ``yyyy-MM-dd HH:mm:ss,SSS LEVEL thread logger - message``
- Bracketed: ``[yyyy-MM-dd HH:mm:ss][txnId][programCode][LEVEL]message``
  (no millis, no dash; seen in KB05023xxx-style transaction logs)

In both formats, a SQL statement is logged as a "SQL:" marker message
followed by the fully resolved SQL text (bind values already inlined)
spanning an arbitrary number of un-prefixed lines, until the next
prefixed log line (in either format) appears.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

_LOG_PREFIX_LEGACY = re.compile(
    r"^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3})\s+(\S+)\s+(\S+)\s+(\S+)\s+-\s?(.*)$"
)
_LOG_PREFIX_BRACKETED = re.compile(
    r"^\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\s*]\s*\[([^]]*)]\s*\[([^]]*)]\s*\[\s*(\w+)\s*]\s*(.*)$"
)
_SQL_MARKER = re.compile(r"^SQL\s*:\s*(.*)$", re.IGNORECASE)


@dataclass(frozen=True)
class RawSqlBlock:
    sequence: int
    timestamp: str
    thread: str
    guid: str
    raw_sql: str


def _match_prefix(line: str) -> tuple[str, str, str, str] | None:
    """Returns (timestamp, thread, guid, message) for a recognized prefix line, else None."""
    m = _LOG_PREFIX_LEGACY.match(line)
    if m:
        # The legacy format has no per-transaction id, only a thread name.
        return m.group(1), m.group(3), "", m.group(5)
    m = _LOG_PREFIX_BRACKETED.match(line)
    if m:
        # group(2) is the long per-transaction id (거래일련번호, shown in the UI as
        # "GUID"), group(3) the calling program/screen code (거래코드) - the latter is
        # the closer analogue of "thread" for display purposes.
        return m.group(1), m.group(3), m.group(2), m.group(5)
    return None


def extract_sql_blocks(log_text: str) -> list[RawSqlBlock]:
    blocks: list[RawSqlBlock] = []
    lines = re.split(r"\r\n|\r|\n", log_text)

    current_timestamp: str | None = None
    current_thread: str | None = None
    current_guid: str | None = None
    current_sql_lines: list[str] | None = None
    sequence = 0

    def flush() -> None:
        nonlocal current_sql_lines, sequence
        if current_sql_lines is not None:
            sequence += 1
            blocks.append(
                RawSqlBlock(sequence, current_timestamp, current_thread, current_guid,
                            "\n".join(current_sql_lines).strip())
            )
            current_sql_lines = None

    for line in lines:
        prefix = _match_prefix(line)
        if prefix:
            flush()
            timestamp, thread, guid, message = prefix
            sm = _SQL_MARKER.match(message.strip())
            if sm:
                current_timestamp = timestamp
                current_thread = thread
                current_guid = guid
                current_sql_lines = []
                inline = sm.group(1)
                if inline.strip():
                    current_sql_lines.append(inline)
        elif current_sql_lines is not None:
            current_sql_lines.append(line)

    flush()
    return blocks

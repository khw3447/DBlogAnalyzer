"""Scans a raw SQL string and separates its three comment flavors:

- Oracle optimizer hints: ``/*+ ... */`` - the '+' right after the opening
  ``/*`` is the distinguishing marker.
- iBatis mapped-statement-id tags: a plain block comment placed immediately
  after the SQL verb, e.g. ``SELECT /* TSKECD4C2_getTSKECD4C2ForSelResult_0001 */``.
- Everything else: ordinary block comments and ``-- `` line comments
  (commonly used in this codebase's logs to annotate each VALUES entry
  with its column name).

Comments are located with a single character-by-character scan that tracks
whether the cursor is inside a quoted string literal, so a ``--`` or ``/*``
that only happens to appear inside a literal value is never mistaken for a
real comment.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum, auto


class CommentType(Enum):
    HINT = auto()
    STATEMENT_ID = auto()
    BLOCK = auto()
    LINE = auto()


@dataclass(frozen=True)
class CommentSpan:
    start: int
    end: int
    type: CommentType
    text: str


@dataclass(frozen=True)
class AnalyzedComments:
    original: str
    stripped: str
    comments: list[CommentSpan]


_LEADING_VERB = re.compile(r"^(SELECT|INSERT|UPDATE|DELETE|MERGE)\b", re.IGNORECASE)


def analyze(sql: str) -> AnalyzedComments:
    comments: list[CommentSpan] = []
    out: list[str] = []

    i = 0
    n = len(sql)
    in_string = False

    while i < n:
        c = sql[i]

        if in_string:
            out.append(c)
            if c == "'":
                if i + 1 < n and sql[i + 1] == "'":
                    out.append("'")
                    i += 2
                    continue
                in_string = False
            i += 1
            continue

        if c == "'":
            in_string = True
            out.append(c)
            i += 1
            continue

        if c == "-" and i + 1 < n and sql[i + 1] == "-":
            end = sql.find("\n", i)
            end = n if end == -1 else end
            comments.append(CommentSpan(i, end, CommentType.LINE, sql[i:end]))
            out.append(_blanked(sql, i, end))
            i = end
            continue

        if c == "/" and i + 1 < n and sql[i + 1] == "*":
            end = sql.find("*/", i + 2)
            end = n if end == -1 else end + 2
            is_hint = i + 2 < n and sql[i + 2] == "+"
            comments.append(CommentSpan(i, end, CommentType.HINT if is_hint else CommentType.BLOCK, sql[i:end]))
            out.append(_blanked(sql, i, end))
            i = end
            continue

        out.append(c)
        i += 1

    comments = _reclassify_statement_id_comment(sql, comments)
    return AnalyzedComments(sql, "".join(out), comments)


def _blanked(sql: str, start: int, end: int) -> str:
    return "".join("\n" if sql[k] == "\n" else " " for k in range(start, end))


def _reclassify_statement_id_comment(sql: str, comments: list[CommentSpan]) -> list[CommentSpan]:
    trimmed = sql.lstrip()
    lead_offset = len(sql) - len(trimmed)
    m = _LEADING_VERB.match(trimmed)
    if not m:
        return comments

    after_verb = lead_offset + m.end()
    result = list(comments)
    for idx, span in enumerate(result):
        if span.type != CommentType.BLOCK or span.start < after_verb:
            continue
        between = sql[after_verb:span.start]
        if between.strip() == "":
            result[idx] = CommentSpan(span.start, span.end, CommentType.STATEMENT_ID, span.text)
        break
    return result

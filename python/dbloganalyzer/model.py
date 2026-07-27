"""Aggregates analyzed SQL records into per-table usage rollups."""

from __future__ import annotations

from dataclasses import dataclass, field

from .sql_analyzer import SqlRecord, SqlType


@dataclass
class TableStat:
    table: str
    counts: dict[SqlType, int] = field(default_factory=dict)

    def increment(self, sql_type: SqlType) -> None:
        self.counts[sql_type] = self.counts.get(sql_type, 0) + 1

    def count(self, sql_type: SqlType) -> int:
        return self.counts.get(sql_type, 0)

    def total(self) -> int:
        return sum(self.counts.values())


@dataclass
class AnalysisResult:
    records: list[SqlRecord]
    table_stats: dict[str, TableStat]

    @staticmethod
    def of(records: list[SqlRecord]) -> "AnalysisResult":
        stats: dict[str, TableStat] = {}
        for record in records:
            for table in record.tables:
                stats.setdefault(table, TableStat(table)).increment(record.type)
        return AnalysisResult(records, stats)

    def parse_failure_count(self) -> int:
        return sum(1 for r in self.records if not r.parsed_ok)

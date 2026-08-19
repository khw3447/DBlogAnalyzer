from pathlib import Path

from dbloganalyzer.logparser import extract_sql_blocks

FIXTURE = (Path(__file__).parent / "fixtures" / "sample-log.txt").read_text(encoding="utf-8")
BRACKET_FIXTURE = (Path(__file__).parent / "fixtures" / "sample-log-bracket.txt").read_text(encoding="utf-8")


def test_extracts_all_five_sql_blocks_from_sample_log():
    blocks = extract_sql_blocks(FIXTURE)

    assert len(blocks) == 5
    assert blocks[0].raw_sql.startswith("SELECT")
    assert blocks[1].raw_sql.startswith("UPDATE")
    assert blocks[2].raw_sql.startswith("INSERT")
    assert blocks[3].raw_sql.startswith("DELETE")
    assert blocks[4].raw_sql.startswith("MERGE")


def test_stops_capturing_at_next_log_prefixed_line():
    blocks = extract_sql_blocks(FIXTURE)

    assert "ORDER BY" in blocks[0].raw_sql
    assert "iBatis ConfigurationLoader" not in blocks[1].raw_sql


def test_captures_timestamp_and_thread():
    blocks = extract_sql_blocks(FIXTURE)

    assert blocks[0].timestamp == "2026-07-24 10:56:08,061"
    assert blocks[0].thread == "mkec495b-1"
    assert blocks[0].guid == ""


def test_extracts_sql_blocks_from_bracketed_log_format():
    blocks = extract_sql_blocks(BRACKET_FIXTURE)

    assert len(blocks) == 3
    assert blocks[0].raw_sql.startswith("SELECT")
    assert blocks[1].raw_sql.startswith("UPDATE")
    assert blocks[2].raw_sql.startswith("insert")


def test_bracketed_format_captures_timestamp_and_program_code_as_thread():
    blocks = extract_sql_blocks(BRACKET_FIXTURE)

    assert blocks[0].timestamp == "2026-07-23 18:03:57"
    assert blocks[0].thread == "KEC0649142"


def test_bracketed_format_captures_transaction_serial_number_as_guid():
    blocks = extract_sql_blocks(BRACKET_FIXTURE)

    assert blocks[0].guid == "KB0502310200120260723180357000093002"


def test_bracketed_format_does_not_swallow_non_sql_lines_between_blocks():
    blocks = extract_sql_blocks(BRACKET_FIXTURE)

    assert "TransactionCoordinator" not in blocks[1].raw_sql
    assert "TheK_ERROR_FORMAT" not in blocks[2].raw_sql

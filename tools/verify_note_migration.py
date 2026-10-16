"""Run the actual Room 7/8 -> 9 SQL against SQLite and compare exported schema.

Uses only the Python standard library; no Android device is needed.
"""
import json
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / "app/schemas/com.reater.app.data.local.AppDatabase"
source = (ROOT / "app/src/main/java/com/reater/app/data/local/AppDatabase.kt").read_text(encoding="utf-8")
def migration(name):
    block = source.split(f"val {name}:", 1)[1].split("\n        }", 1)[0]
    return re.findall(r'connection.execSql\("([^"\n]+)"\)', block)

statements = migration("MIGRATION_7_8") + migration("MIGRATION_8_9")
assert len(statements) == 3, "Migration SQL changed; update the verification harness"

db = sqlite3.connect(":memory:")
old = json.loads((SCHEMAS / "7.json").read_text(encoding="utf-8"))["database"]
for entity in old["entities"]:
    if entity["tableName"] in ("user_edits", "saved_collections"):
        db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
columns = list(db.execute("PRAGMA table_info(user_edits)"))
values = {row[1]: ("" if row[2] == "TEXT" else 0) for row in columns if row[3]}
values.update(itemId=1, manualNote="我的人工筆記：保留繁體中文", manualSummary="舊版 AI 摘要")
db.execute(f"INSERT INTO user_edits ({','.join(values)}) VALUES ({','.join('?' for _ in values)})", list(values.values()))
for statement in statements:
    db.execute(statement)
note, summary, legacy = db.execute("SELECT manualNote, aiSummary, manualSummary FROM user_edits").fetchone()
assert note == values["manualNote"]
assert summary == legacy == values["manualSummary"]

new = json.loads((SCHEMAS / "9.json").read_text(encoding="utf-8"))["database"]
new_entity = next(e for e in new["entities"] if e["tableName"] == "user_edits")
fresh = sqlite3.connect(":memory:")
fresh.execute(new_entity["createSql"].replace("${TABLE_NAME}", "user_edits"))
def table_shape(connection):
    # ALTER TABLE appends columns; Room compares names/types/defaults, not order.
    return sorted(tuple(row[1:]) for row in connection.execute("PRAGMA table_info(user_edits)"))

assert table_shape(db) == table_shape(fresh), "Migrated and fresh schemas differ"
legacy8 = sqlite3.connect(":memory:")
schema8 = json.loads((SCHEMAS / "8.json").read_text(encoding="utf-8"))["database"]
for entity in schema8["entities"]:
    if entity["tableName"] in ("user_edits", "saved_collections"):
        legacy8.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
legacy8.execute("INSERT INTO saved_collections VALUES (1, '停用規則', 'life', '{}', 0, 0, 0)")
for statement in migration("MIGRATION_8_9"):
    legacy8.execute(statement)
assert legacy8.execute("SELECT isEnabled FROM saved_collections").fetchone()[0] == 0
assert table_shape(legacy8) == table_shape(fresh)
print("PASS: Room 7/8 -> 9 preserves notes, summaries and disabled rules; migrated user_edits equals fresh schema.")

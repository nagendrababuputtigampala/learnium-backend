#!/usr/bin/env python3
"""Export the as-built PostgreSQL schema as a Mermaid ER diagram, using metadata only."""
import argparse
import json
from pathlib import Path
import re
import subprocess

SQL = """
SELECT json_build_object(
 'migrations', (SELECT json_agg(version ORDER BY installed_rank)
                FROM learnium.flyway_schema_history WHERE success AND version IS NOT NULL),
 'columns', (SELECT json_agg(json_build_object('table', c.relname, 'name', a.attname,
   'type', format_type(a.atttypid,a.atttypmod), 'required', a.attnotnull,
   'pk', EXISTS(SELECT 1 FROM pg_constraint p WHERE p.conrelid=c.oid AND p.contype='p'
                AND a.attnum=ANY(p.conkey)),
   'fk', EXISTS(SELECT 1 FROM pg_constraint p WHERE p.conrelid=c.oid AND p.contype='f'
                AND a.attnum=ANY(p.conkey))) ORDER BY c.relname,a.attnum)
  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
  JOIN pg_attribute a ON a.attrelid=c.oid
  WHERE n.nspname='learnium' AND c.relkind='r' AND c.relname<>'flyway_schema_history'
    AND a.attnum>0 AND NOT a.attisdropped),
 'fks', (SELECT json_agg(json_build_object('name', f.conname, 'child', c.relname,
   'parent', p.relname, 'required', (SELECT bool_and(a.attnotnull) FROM pg_attribute a
           WHERE a.attrelid=c.oid AND a.attnum=ANY(f.conkey)),
   'unique', EXISTS(SELECT 1 FROM pg_constraint u WHERE u.conrelid=c.oid
                    AND u.contype IN ('p','u') AND u.conkey <@ f.conkey),
   'columns', (SELECT string_agg(a.attname,', ' ORDER BY k.ord)
       FROM unnest(f.conkey) WITH ORDINALITY k(num,ord)
       JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum=k.num),
   'references', (SELECT string_agg(a.attname,', ' ORDER BY k.ord)
       FROM unnest(f.confkey) WITH ORDINALITY k(num,ord)
       JOIN pg_attribute a ON a.attrelid=p.oid AND a.attnum=k.num)) ORDER BY c.relname,f.conname)
  FROM pg_constraint f JOIN pg_class c ON c.oid=f.conrelid
  JOIN pg_class p ON p.oid=f.confrelid JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname='learnium' AND f.contype='f'));
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--container", default="learnium-backend-postgres-1")
    parser.add_argument("--output", type=Path, default=Path("ER-Diagram.md"))
    args = parser.parse_args()
    result = subprocess.run(["docker", "exec", args.container, "psql", "-X", "-U", "learnium",
                             "-d", "learnium", "-At", "-c", SQL],
                            check=True, text=True, stdout=subprocess.PIPE)
    schema = json.loads(result.stdout)
    lines = ["# Learnium — as-built entity relationships", "",
             "Source: PostgreSQL catalogue metadata from the migrated `learnium` schema.",
             "Flyway versions: " + ", ".join(schema["migrations"]) + ".", "",
             "Regenerate with `python3 scripts/export_er_diagram.py --container CONTAINER`.", "",
             "Nullable references and unique child keys determine the diagram cardinalities.",
             "Composite tenancy constraints are listed explicitly below.", "", "```mermaid", "erDiagram"]
    tables = sorted({column["table"] for column in schema["columns"]})
    for table in tables:
        lines.append("    " + table + " {")
        for column in (c for c in schema["columns"] if c["table"] == table):
            kind = re.sub(r"[^A-Za-z0-9_]", "_", column["type"])
            keys = ",".join(key for key, present in [("PK", column["pk"]), ("FK", column["fk"])] if present)
            lines.append("        " + kind + " " + column["name"] + (" " + keys if keys else ""))
        lines.append("    }")
    for fk in schema["fks"]:
        parent = "||" if fk["required"] else "|o"
        child = "o|" if fk["unique"] else "o{"
        lines.append(f'    {fk["parent"]} {parent}--{child} {fk["child"]} : {fk["name"]}')
    lines += ["```", "", "## Foreign-key constraints", "",
              "| Child table | Columns | Parent table | Referenced columns |", "|---|---|---|---|"]
    for fk in schema["fks"]:
        lines.append(f'| {fk["child"]} | {fk["columns"]} | {fk["parent"]} | {fk["references"]} |')
    args.output.write_text("\n".join(lines) + "\n")
    print(f"Exported {len(tables)} tables and {len(schema['fks'])} foreign keys to {args.output}")


if __name__ == "__main__":
    main()

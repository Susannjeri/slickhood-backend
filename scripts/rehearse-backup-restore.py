#!/usr/bin/env python3
"""Restore a SlickHood gzip backup into a new loopback-only rehearsal database."""
from argparse import ArgumentParser
from pathlib import Path
from urllib.parse import urlparse
import gzip
import os
import re
import subprocess


def mysql_args(host: str, port: int, user: str, database: str | None = None) -> list[str]:
    args = ["mysql", "--host", host, "--port", str(port), "--user", user, "--batch", "--skip-column-names"]
    if database:
        args.append(database)
    return args


def main() -> int:
    parser = ArgumentParser()
    parser.add_argument("--backup", required=True, type=Path)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", required=True)
    parser.add_argument("--database", required=True)
    args = parser.parse_args()
    if args.host not in {"127.0.0.1", "localhost", "::1"}:
        raise SystemExit("Restore rehearsal accepts loopback MySQL only.")
    if not re.fullmatch(r"slickhood_restore_rehearsal_[a-zA-Z0-9_]+", args.database):
        raise SystemExit("Database must use the slickhood_restore_rehearsal_ prefix.")
    if not args.backup.is_file() or args.backup.suffix != ".gz":
        raise SystemExit("A readable .gz backup is required.")
    env = dict(os.environ)
    if not env.get("MYSQL_PWD"):
        raise SystemExit("Stage the disposable database password in MYSQL_PWD; never pass it on the command line.")
    exists = subprocess.run(mysql_args(args.host, args.port, args.user), input=(
        "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='" + args.database + "';\n"),
        text=True, capture_output=True, env=env, check=True).stdout.strip()
    if exists != "0":
        raise SystemExit("The rehearsal database already exists; this script will not overwrite it.")
    subprocess.run(mysql_args(args.host, args.port, args.user), input=f"CREATE DATABASE `{args.database}`;\n",
                   text=True, env=env, check=True)
    with gzip.open(args.backup, "rb") as source:
        restored = subprocess.run(mysql_args(args.host, args.port, args.user, args.database), stdin=source, env=env)
    if restored.returncode:
        raise SystemExit("Restore failed; the rehearsal database was retained for inspection.")
    table_count = subprocess.run(mysql_args(args.host, args.port, args.user, args.database),
        input="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();\n",
        text=True, capture_output=True, env=env, check=True).stdout.strip()
    if not table_count.isdigit() or int(table_count) == 0:
        raise SystemExit("Restore completed but produced no tables.")
    subprocess.run(["mysqlcheck", "--host", args.host, "--port", str(args.port), "--user", args.user,
                    "--check", args.database], env=env, check=True)
    print("RESTORE_REHEARSAL_OK", args.database, "tables=" + table_count)
    print("The rehearsal database was retained for acceptance checks and requires explicit cleanup.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

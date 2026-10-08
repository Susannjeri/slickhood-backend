#!/usr/bin/env python3
"""Remove only the known failed V114 Flyway history entry.

V114 originally used MariaDB-only ``IF NOT EXISTS`` DDL and could not execute
on production MySQL. The schema change never started, but MySQL retained a
failed Flyway history row which prevents a corrected migration from running.
This release-scoped repair deliberately refuses to touch successful or
unexpected migration records.
"""
import importlib.util
import os
from pathlib import Path
import re
import subprocess
import sys


VERSION = "114"
DESCRIPTION = "soko rider identity number"


def main():
    if os.geteuid() != 0:
        raise RuntimeError("Root is required for the production migration repair")

    spec = importlib.util.spec_from_file_location(
        "preflight", Path(__file__).with_name("production-preflight.py")
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    checks = module.Preflight()
    config = module.load_configuration(checks)
    if checks.failures:
        raise RuntimeError("Protected configuration check failed")

    jdbc = module.resolve(config, "spring.datasource.url", "spring.datasource.jdbc-url")
    username = module.resolve(config, "spring.datasource.username")
    password = module.resolve(config, "spring.datasource.password")
    match = re.match(r"jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)", jdbc or "")
    if not match or not username or not password:
        raise RuntimeError("Primary datasource configuration unavailable")

    host, port, database = match.group(1), match.group(2) or "3306", match.group(3)
    env = dict(os.environ, MYSQL_PWD=password)
    command = [
        "mysql", "--batch", "--skip-column-names", "--host", host,
        "--port", port, "--user", username, database,
    ]
    query = (
        "SELECT installed_rank, description, success FROM flyway_schema_history "
        f"WHERE version='{VERSION}'"
    )
    result = subprocess.run(
        command + ["--execute", query], check=True, capture_output=True,
        text=True, env=env, timeout=30,
    )
    rows = [line.split("\t") for line in result.stdout.splitlines() if line.strip()]
    if not rows:
        print("MIGRATION_REPAIR_NOT_REQUIRED", VERSION, flush=True)
        return
    if len(rows) != 1 or len(rows[0]) != 3:
        raise RuntimeError("Unexpected V114 Flyway history records; refusing repair")
    installed_rank, description, success = rows[0]
    normalized_description = " ".join(description.replace("_", " ").lower().split())
    normalized_success = success.strip().lower()
    failed_values = {"0", "false", "\x00", "b'0'", "b'\\x00'"}
    if normalized_description != DESCRIPTION or normalized_success not in failed_values:
        raise RuntimeError(
            "V114 is not the expected failed migration; refusing repair "
            f"(records={len(rows)}, description={normalized_description!r}, "
            f"success={normalized_success!r})"
        )

    delete = (
        "DELETE FROM flyway_schema_history "
        f"WHERE installed_rank={int(installed_rank)} AND version='{VERSION}' "
        f"AND description='{DESCRIPTION}' AND success=0"
    )
    subprocess.run(
        command + ["--execute", delete], check=True, stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL, env=env, timeout=30,
    )
    print("MIGRATION_REPAIR_COMPLETE", VERSION, flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(
            "MIGRATION_REPAIR_FAILED",
            str(error) if isinstance(error, RuntimeError) else "Operation failed",
            flush=True,
        )
        sys.exit(1)

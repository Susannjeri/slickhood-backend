#!/usr/bin/env python3
"""Fail CI when code crosses the modular-monolith boundaries accepted in ADR-001."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "java"
IMPORT = re.compile(r"^import\s+([\w.]+);", re.MULTILINE)


def imports(path: Path) -> set[str]:
    return set(IMPORT.findall(path.read_text(encoding="utf-8")))


def main() -> int:
    violations: list[str] = []
    for path in ROOT.rglob("*.java"):
        relative = path.relative_to(ROOT).as_posix()
        for imported in imports(path):
            if "/controller/" in f"/{relative}" and imported.startswith("org.pms.silverocean.database") \
                    and imported.rsplit(".", 1)[-1].endswith("Repo"):
                violations.append(f"{relative}: controllers must use application services, not {imported}")
            if "/service/payment/" in f"/{relative}" and imported.startswith("org.pms.silverocean.service.subscription") \
                    and ".contract." not in imported:
                violations.append(f"{relative}: payment may use only subscription public contracts, not {imported}")
            if "/service/" in f"/{relative}" and imported.startswith("org.pms.silverocean.controller") \
                    and ".wrappers." not in imported:
                violations.append(f"{relative}: services must not depend on web controllers: {imported}")
    if violations:
        print("MODULE_BOUNDARY_FAILURE")
        print("\n".join(sorted(violations)))
        return 1
    print("MODULE_BOUNDARY_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Synchronize packaged draft Help Desk bodies from the governed Markdown manual."""

from __future__ import annotations

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANUAL = ROOT / "docs" / "SLICKHOOD_USER_MANUAL.md"
BUNDLE = ROOT / "src" / "main" / "resources" / "helpdesk" / "user-manual.json"


def main() -> int:
    written = MANUAL.read_text(encoding="utf-8").replace("\r\n", "\n")
    bundle = json.loads(BUNDLE.read_text(encoding="utf-8-sig"))
    chapters = bundle.get("chapters", [])
    if len(chapters) != 30:
        raise RuntimeError(f"Expected 30 chapters, found {len(chapters)}")
    for chapter in chapters:
        article = chapter["article"]
        title = article["title"]
        match = re.search(
            rf"(?ms)^## {re.escape(title)}\n\nAudience: [^\n]+\.\n\n(?P<body>.*?)(?=\n## |\Z)",
            written,
        )
        if not match:
            raise RuntimeError(f"Manual chapter not found: {title}")
        article["body"] = match.group("body").rstrip()
    BUNDLE.write_text(json.dumps(bundle, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Dependency-free authenticated load runner for an isolated SlickHood environment.

Production hosts are deliberately refused. Supply a synthetic bearer token and
safe read endpoints; write journeys remain in the acceptance suite.
"""
from argparse import ArgumentParser
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path
from statistics import mean
from time import monotonic, sleep
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen
import json
import os


@dataclass(frozen=True)
class Phase:
    name: str
    concurrency: int
    seconds: int


PROFILES = {
    "normal": [Phase("normal", 5, 60)],
    "peak": [Phase("peak", 25, 60)],
    "soak": [Phase("soak", 5, 900)],
    "recovery": [Phase("peak", 25, 60), Phase("recovery", 5, 60)],
}


def request_once(base_url: str, endpoint: str, token: str, workspace: str | None):
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
    if workspace:
        headers["X-Slickhood-Workspace"] = workspace
    started = monotonic()
    try:
        with urlopen(Request(urljoin(base_url.rstrip("/") + "/", endpoint.lstrip("/")), headers=headers), timeout=15) as response:
            response.read(4096)
            status = response.status
    except HTTPError as error:
        status = error.code
    except (URLError, TimeoutError, OSError):
        status = 0
    return (monotonic() - started) * 1000, status


def percentile(values: list[float], percentage: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int((len(ordered) - 1) * percentage))]


def run_phase(base_url: str, endpoints: list[str], token: str, workspace: str | None, phase: Phase):
    deadline = monotonic() + phase.seconds
    latencies: list[float] = []
    statuses: dict[int, int] = {}

    def worker(worker_id: int):
        rows = []
        index = worker_id
        while monotonic() < deadline:
            rows.append(request_once(base_url, endpoints[index % len(endpoints)], token, workspace))
            index += 1
        return rows

    started = monotonic()
    with ThreadPoolExecutor(max_workers=phase.concurrency) as executor:
        for future in as_completed(executor.submit(worker, index) for index in range(phase.concurrency)):
            for latency, status in future.result():
                latencies.append(latency)
                statuses[status] = statuses.get(status, 0) + 1
    elapsed = monotonic() - started
    failures = sum(count for status, count in statuses.items() if status < 200 or status >= 400)
    return {
        "phase": phase.name, "concurrency": phase.concurrency, "elapsedSeconds": round(elapsed, 3),
        "requests": len(latencies), "requestsPerSecond": round(len(latencies) / elapsed, 2),
        "meanMs": round(mean(latencies), 2) if latencies else 0,
        "p50Ms": round(percentile(latencies, .50), 2), "p95Ms": round(percentile(latencies, .95), 2),
        "p99Ms": round(percentile(latencies, .99), 2), "failures": failures, "statuses": statuses,
    }


def main() -> int:
    parser = ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--profile", choices=PROFILES, default="normal")
    parser.add_argument("--endpoint", action="append", dest="endpoints", required=True)
    parser.add_argument("--workspace")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    host = (urlparse(args.base_url).hostname or "").lower()
    if host == "slickhood.com" or host.endswith(".slickhood.com"):
        raise SystemExit("Refusing load testing against a SlickHood production hostname; use an isolated equivalent environment.")
    token = os.environ.get("SLICKHOOD_SYNTHETIC_BEARER_TOKEN", "").strip()
    if not token:
        raise SystemExit("Set SLICKHOOD_SYNTHETIC_BEARER_TOKEN for a non-customer synthetic account.")
    report = {"baseUrl": args.base_url, "profile": args.profile, "phases": []}
    for phase in PROFILES[args.profile]:
        report["phases"].append(run_phase(args.base_url, args.endpoints, token, args.workspace, phase))
        sleep(2)
    rendered = json.dumps(report, indent=2, sort_keys=True)
    if args.output:
        args.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)
    return 1 if any(row["failures"] for row in report["phases"]) else 0


if __name__ == "__main__":
    raise SystemExit(main())

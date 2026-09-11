#!/usr/bin/env python3
"""One-shot, secret-safe production dependency preflight for the SlickHood host.

Run as root immediately before a backend deployment. The script reads the same
root-owned configuration used by pms.service, but reports only check names and
statuses. It never prints configured values, credentials, response bodies or
provider URLs containing query parameters.
"""

from __future__ import annotations

import argparse
import binascii
import imaplib
import json
import os
import re
import smtplib
import ssl
import stat
import struct
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zlib
from pathlib import Path


DEFAULT_FILES = (
    Path("/etc/slickhood/application.properties"),
    Path("/etc/slickhood/pms-antivirus.env"),
    Path("/etc/slickhood/pms-ocr.env"),
    Path("/etc/slickhood/pms-release.env"),
    Path("/etc/slickhood/pms-storage.env"),
    Path("/etc/slickhood/secrets/s3-credentials.env"),
    Path("/etc/slickhood/secrets/alpha-vantage.env"),
    Path("/etc/slickhood/secrets/insurance-imap.env"),
    Path("/home/silverocean/backend/config/application.properties"),
)
PLACEHOLDER = re.compile(r"^\$\{([^}:]+)(?::([^}]*))?}$")


class Preflight:
    def __init__(self) -> None:
        self.failures: list[str] = []

    def pass_(self, name: str) -> None:
        print(f"PASS  {name}")

    def fail(self, name: str, reason: str) -> None:
        self.failures.append(name)
        print(f"FAIL  {name}: {reason}")

    def skip(self, name: str, reason: str) -> None:
        print(f"SKIP  {name}: {reason}")

    def finish(self) -> int:
        if self.failures:
            print(f"BLOCKED: {len(self.failures)} production preflight check(s) failed.")
            return 1
        print("READY: all production preflight checks passed.")
        return 0


def parse_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key.strip()] = value
    return values


def load_configuration(preflight: Preflight) -> dict[str, str]:
    merged: dict[str, str] = dict(os.environ)
    for path in DEFAULT_FILES:
        if not path.exists():
            preflight.fail(f"configuration file {path.name}", "missing")
            continue
        info = path.stat()
        mode = stat.S_IMODE(info.st_mode)
        if mode not in (0o600, 0o640):
            preflight.fail(f"configuration permissions {path.name}", "must be 0600 or 0640")
        elif info.st_uid != 0:
            preflight.fail(f"configuration owner {path.name}", "must be root")
        else:
            preflight.pass_(f"configuration protection {path.name}")
        try:
            merged.update(parse_file(path))
        except (OSError, UnicodeError):
            preflight.fail(f"configuration readability {path.name}", "could not be read")
    return merged


def resolve(config: dict[str, str], *keys: str, default: str = "") -> str:
    for key in keys:
        value = config.get(key)
        if value is None or not value.strip():
            continue
        value = value.strip()
        match = PLACEHOLDER.match(value)
        if match:
            value = config.get(match.group(1), match.group(2) or "")
        if value.strip():
            return value.strip()
    return default


def required(preflight: Preflight, config: dict[str, str], name: str, *keys: str) -> str:
    value = resolve(config, *keys)
    if value:
        preflight.pass_(f"configured {name}")
    else:
        preflight.fail(f"configured {name}", "missing")
    return value


def run(command: list[str], *, env: dict[str, str] | None = None, timeout: int = 20) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(command, capture_output=True, text=True, env=env, timeout=timeout, check=False)
    except FileNotFoundError:
        return subprocess.CompletedProcess(command, 127, "", "executable not available")
    except subprocess.TimeoutExpired:
        return subprocess.CompletedProcess(command, 124, "", "command timed out")


def check_service(preflight: Preflight, unit: str) -> None:
    result = run(["systemctl", "is-active", "--quiet", unit])
    if result.returncode == 0:
        preflight.pass_(f"service {unit}")
    else:
        preflight.fail(f"service {unit}", "not active")


def request_status(url: str, *, method: str = "GET", headers: dict[str, str] | None = None,
                   body: bytes | None = None, timeout: int = 12) -> tuple[int, bytes, dict[str, str]]:
    request_headers = dict(headers or {})
    # Some provider edges reject urllib's implicit Python user agent before
    # evaluating otherwise valid credentials. Keep this identifier stable and
    # deliberately free of host, release or secret data.
    request_headers.setdefault("User-Agent", "slickhood-production-preflight")
    request = urllib.request.Request(url, data=body, method=method, headers=request_headers)
    try:
        with urllib.request.urlopen(request, timeout=timeout, context=ssl.create_default_context()) as response:
            return response.status, response.read(1_048_576), dict(response.headers.items())
    except urllib.error.HTTPError as error:
        return error.code, error.read(1_048_576), dict(error.headers.items())


def check_readiness(preflight: Preflight, readiness_url: str, expected_scope: set[str]) -> None:
    try:
        status, body, _ = request_status(readiness_url)
        payload = json.loads(body)
        component = payload.get("components", {}).get("productionReadiness", {})
        details = component.get("details", {})
        missing = details.get("missingOrUnsafeConfiguration", [])
        scope = {item.strip() for item in details.get("scope", "").split(",") if item.strip()}
        if status == 200 and payload.get("status") == "UP" and not missing and expected_scope <= scope:
            preflight.pass_("backend production readiness")
        else:
            preflight.fail("backend production readiness", "endpoint is not UP, complete or safely configured")
    except (OSError, ValueError, urllib.error.URLError):
        preflight.fail("backend production readiness", "unreachable or invalid response")


def check_cors(preflight: Preflight, public_origin: str) -> None:
    try:
        status, _, headers = request_status(
            f"{public_origin}/api/auth/login",
            method="OPTIONS",
            headers={
                "Origin": public_origin,
                "Access-Control-Request-Method": "POST",
                "Access-Control-Request-Headers": "content-type",
            },
        )
        allowed = headers.get("Access-Control-Allow-Origin", "")
        # A same-origin request is not CORS, so Spring may correctly omit ACAO.
        same_origin_ok = status in (200, 204) and allowed in ("", public_origin)
        hostile_status, _, hostile_headers = request_status(
            f"{public_origin}/api/auth/login",
            method="OPTIONS",
            headers={
                "Origin": "https://untrusted.invalid",
                "Access-Control-Request-Method": "POST",
                "Access-Control-Request-Headers": "content-type",
            },
        )
        hostile_denied = hostile_status in (400, 401, 403) and not hostile_headers.get("Access-Control-Allow-Origin")
        if same_origin_ok and hostile_denied:
            preflight.pass_("HTTPS same-origin and CORS rejection contract")
        else:
            preflight.fail("HTTPS same-origin and CORS rejection contract", "production origin failed or an untrusted origin was allowed")
    except (OSError, urllib.error.URLError):
        preflight.fail("HTTPS same-origin and CORS rejection contract", "preflight request failed")


def check_provider_http(preflight: Preflight, name: str, url: str, headers: dict[str, str],
                        accepted: set[int]) -> tuple[int, bytes] | None:
    try:
        status, body, _ = request_status(url, headers=headers)
        if status in accepted:
            preflight.pass_(f"{name} authentication and reachability")
            return status, body
        preflight.fail(f"{name} authentication and reachability", f"provider returned HTTP {status}")
    except (OSError, urllib.error.URLError):
        preflight.fail(f"{name} authentication and reachability", "connection failed")
    return None


def classify_alpha_payload(payload: object) -> str:
    """Classify Alpha Vantage without logging its provider response or API key."""
    if not isinstance(payload, dict):
        return "invalid"
    quote = payload.get("Global Quote")
    if isinstance(quote, dict) and quote:
        return "quote"
    message = str(payload.get("Information") or payload.get("Note") or "").lower()
    if any(marker in message for marker in ("invalid api key", "invalid key", "not activated")):
        return "invalid_key"
    if any(marker in message for marker in ("rate limit", "call frequency", "requests per")):
        return "rate_limit"
    return "invalid"


def check_mail(preflight: Preflight, config: dict[str, str]) -> None:
    host = required(preflight, config, "SMTP host", "spring.mail.host", "MAIL_HOST")
    username = required(preflight, config, "SMTP username", "spring.mail.username", "MAIL_USERNAME")
    password = required(preflight, config, "SMTP password", "spring.mail.password", "MAIL_PASSWORD")
    port_text = resolve(config, "spring.mail.port", "MAIL_PORT", default="465")
    if not all((host, username, password)):
        return
    try:
        port = int(port_text)
        if resolve(config, "MAIL_SSL", "spring.mail.properties.mail.smtp.ssl.enable", default="false").lower() == "true":
            client: smtplib.SMTP = smtplib.SMTP_SSL(host, port, timeout=12, context=ssl.create_default_context())
        else:
            client = smtplib.SMTP(host, port, timeout=12)
            client.starttls(context=ssl.create_default_context())
        try:
            client.login(username, password)
        finally:
            client.quit()
        preflight.pass_("SMTP authentication and TLS")
    except (OSError, ValueError, smtplib.SMTPException):
        preflight.fail("SMTP authentication and TLS", "login or TLS negotiation failed")


def check_imap(preflight: Preflight, config: dict[str, str]) -> None:
    host = required(preflight, config, "Insurance IMAP host", "app.insurance.imap.host", "INSURANCE_IMAP_HOST")
    username = required(preflight, config, "Insurance IMAP username", "app.insurance.imap.username", "INSURANCE_IMAP_USERNAME")
    password = required(preflight, config, "Insurance IMAP password", "app.insurance.imap.password", "INSURANCE_IMAP_PASSWORD")
    port_text = resolve(config, "app.insurance.imap.port", "INSURANCE_IMAP_PORT", default="993")
    if not all((host, username, password)):
        return
    try:
        client = imaplib.IMAP4_SSL(host, int(port_text), ssl_context=ssl.create_default_context(), timeout=12)
        try:
            status, _ = client.login(username, password)
            if status != "OK":
                raise imaplib.IMAP4.error("login rejected")
        finally:
            client.logout()
        preflight.pass_("Insurance IMAP authentication and TLS")
    except (OSError, ValueError, imaplib.IMAP4.error):
        preflight.fail("Insurance IMAP authentication and TLS", "login or TLS negotiation failed")


def check_external_apis(preflight: Preflight, config: dict[str, str]) -> None:
    openai_key = required(preflight, config, "OpenAI key", "helpdesk.ai.api-key", "OPENAI_API_KEY")
    openai_base = resolve(config, "helpdesk.ai.base-url", "HELPDESK_AI_BASE_URL", default="https://api.openai.com/v1").rstrip("/")
    if openai_key:
        check_provider_http(preflight, "OpenAI", f"{openai_base}/models", {"Authorization": f"Bearer {openai_key}"}, {200})
        moderation_body = json.dumps({"input": "SlickHood production connectivity check"}).encode()
        try:
            status, _, _ = request_status(
                f"{openai_base}/moderations",
                method="POST",
                headers={
                    "Authorization": f"Bearer {openai_key}",
                    "Content-Type": "application/json",
                },
                body=moderation_body,
            )
            if status == 200:
                preflight.pass_("OpenAI moderation authentication and reachability")
            else:
                preflight.fail("OpenAI moderation authentication and reachability", f"provider returned HTTP {status}")
        except (OSError, urllib.error.URLError):
            preflight.fail("OpenAI moderation authentication and reachability", "connection failed")

    alpha_key = required(preflight, config, "Alpha Vantage key", "wealth.market.alpha-vantage.api-key", "ALPHA_VANTAGE_API_KEY")
    alpha_base = resolve(config, "wealth.market.alpha-vantage.base-url", "ALPHA_VANTAGE_BASE_URL", default="https://www.alphavantage.co/query")
    if alpha_key:
        separator = "&" if "?" in alpha_base else "?"
        query = urllib.parse.urlencode({"function": "GLOBAL_QUOTE", "symbol": "IBM", "apikey": alpha_key})
        result = check_provider_http(preflight, "Alpha Vantage", f"{alpha_base}{separator}{query}", {}, {200})
        if result:
            try:
                classification = classify_alpha_payload(json.loads(result[1]))
                if classification == "quote":
                    preflight.pass_("Alpha Vantage credential validity")
                elif classification == "rate_limit":
                    # The free provider quota can be consumed by the required
                    # before/after deployment checks themselves. Reachability
                    # already passed, so a recognized throttle is operational
                    # degradation rather than evidence of a bad credential.
                    preflight.skip("Alpha Vantage credential validity", "provider rate limit is temporarily exhausted")
                elif classification == "invalid_key":
                    preflight.fail("Alpha Vantage credential validity", "provider rejected the configured key")
                else:
                    preflight.fail("Alpha Vantage credential validity", "provider did not return a live quote")
            except ValueError:
                preflight.fail("Alpha Vantage credential validity", "provider returned invalid JSON")

    paystack_enabled = resolve(config, "payment.paystack.enabled", "PAYSTACK_ENABLED", default="false").lower() == "true"
    if paystack_enabled:
        paystack_key = required(preflight, config, "Paystack key", "payment.paystack.secret-key", "PAYSTACK_SECRET_KEY")
        paystack_base = resolve(config, "payment.paystack.api-url", "PAYSTACK_API_URL", default="https://api.paystack.co").rstrip("/")
        if paystack_key:
            # Use an authenticated account endpoint for the deployment gate. The
            # public bank-directory endpoint can return 403 for otherwise valid
            # Kenyan sandbox integrations, so it is not a reliable credential
            # or egress check. No transaction data is logged by this preflight.
            result = check_provider_http(preflight, "Paystack", f"{paystack_base}/transaction/totals",
                                         {"Authorization": f"Bearer {paystack_key}"}, {200})
            if result:
                try:
                    if json.loads(result[1]).get("status") is True:
                        preflight.pass_("Paystack credential validity")
                    else:
                        preflight.fail("Paystack credential validity", "provider rejected the request")
                except ValueError:
                    preflight.fail("Paystack credential validity", "provider returned invalid JSON")
    else:
        preflight.skip("Paystack authentication and reachability", "provider disabled")


def check_clamav(preflight: Preflight) -> None:
    check_service(preflight, "clamd@scan.service")
    check_service(preflight, "clamav-freshclam.service")
    result = run(["clamdscan", "--ping=3"], timeout=12)
    if result.returncode == 0 and "PONG" in result.stdout:
        preflight.pass_("ClamAV daemon response")
    else:
        preflight.fail("ClamAV daemon response", "clamd did not return PONG")
    definitions = [path for pattern in ("daily.cvd", "daily.cld") for path in Path("/var/lib/clamav").glob(pattern)]
    newest = max((path.stat().st_mtime for path in definitions), default=0)
    if newest and time.time() - newest <= 172_800:
        preflight.pass_("ClamAV definitions freshness")
    else:
        preflight.fail("ClamAV definitions freshness", "daily definitions are older than 48 hours")


def check_database(preflight: Preflight, config: dict[str, str], expected_version: int) -> None:
    jdbc = required(preflight, config, "primary datasource URL", "spring.datasource.url", "SPRING_DATASOURCE_URL")
    username = required(preflight, config, "primary datasource username", "spring.datasource.username", "DATABASE_USERNAME", "SPRING_DATASOURCE_USERNAME")
    password = required(preflight, config, "primary datasource password", "spring.datasource.password", "DATABASE_PASSWORD", "SPRING_DATASOURCE_PASSWORD")
    match = re.match(r"jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)", jdbc)
    if not match or not username or not password:
        preflight.fail("Flyway migration state", "datasource configuration is incomplete or unsupported")
        return
    host, port, database = match.group(1), match.group(2) or "3306", match.group(3)
    mysql_env = dict(os.environ)
    mysql_env["MYSQL_PWD"] = password
    query = (
        "SELECT CONCAT(COALESCE(SUM(CASE WHEN success=0 THEN 1 ELSE 0 END),0),'|',"
        "COALESCE(MAX(CAST(version AS UNSIGNED)),0)) FROM flyway_schema_history"
    )
    result = run(["mysql", "--batch", "--skip-column-names", "--host", host, "--port", port,
                  "--user", username, database, "--execute", query], env=mysql_env, timeout=20)
    if result.returncode != 0:
        preflight.fail("Flyway migration state", "database query failed")
        return
    try:
        failed_text, version_text = result.stdout.strip().split("|", 1)
        failed, version = int(failed_text), int(version_text)
    except ValueError:
        preflight.fail("Flyway migration state", "unexpected database result")
        return
    if failed == 0 and version >= expected_version:
        preflight.pass_(f"Flyway migration state through V{expected_version}")
    else:
        preflight.fail("Flyway migration state", "failed records exist or required version is absent")


def create_preflight_png() -> bytes:
    """Return a valid, non-sensitive PNG large enough for Textract validation."""
    width, height = 640, 160
    rows = b"".join(b"\x00" + (b"\xff\xff\xff" * width) for _ in range(height))

    def chunk(kind: bytes, payload: bytes) -> bytes:
        checksum = binascii.crc32(kind + payload) & 0xFFFFFFFF
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", checksum)

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(rows, level=9))
        + chunk(b"IEND", b"")
    )


def check_aws_data_plane(preflight: Preflight, config: dict[str, str], bucket: str, region: str) -> None:
    if not bucket or not region:
        return
    aws_path = next((str(path) for path in (Path("/usr/local/bin/aws"), Path("/usr/bin/aws")) if path.is_file()), "")
    if not aws_path:
        preflight.fail("AWS CLI", "install AWS CLI v2 for S3/Textract data-plane verification")
        return
    version = run([aws_path, "--version"])
    if version.returncode == 0 and (version.stdout + version.stderr).startswith("aws-cli/2."):
        preflight.pass_("AWS CLI v2")
    else:
        preflight.fail("AWS CLI v2", "install or upgrade to AWS CLI v2")
    child_env = dict(os.environ)
    access_key = resolve(config, "garage.s3.access.key", "GARAGE_S3_ACCESS_KEY")
    secret_key = resolve(config, "garage.s3.secret.key", "GARAGE_S3_SECRET_KEY")
    use_default = resolve(config, "garage.s3.use-default-credentials", "GARAGE_S3_USE_DEFAULT_CREDENTIALS", default="false").lower() == "true"
    if access_key and secret_key and not use_default:
        child_env["AWS_ACCESS_KEY_ID"] = access_key
        child_env["AWS_SECRET_ACCESS_KEY"] = secret_key
    elif not use_default:
        preflight.fail("AWS credential source", "configure one static pair or the workload default chain")
        return
    child_env["AWS_REGION"] = region
    child_env["AWS_DEFAULT_REGION"] = region
    child_env["AWS_PAGER"] = ""
    identity = run([aws_path, "sts", "get-caller-identity", "--output", "json"], env=child_env)
    if identity.returncode == 0:
        preflight.pass_("AWS credential validity")
    else:
        preflight.fail("AWS credential validity", "STS GetCallerIdentity failed")
    head = run([aws_path, "s3api", "head-bucket", "--bucket", bucket, "--region", region], env=child_env)
    if head.returncode == 0:
        preflight.pass_("S3 bucket authentication and reachability")
    else:
        preflight.fail("S3 bucket authentication and reachability", "HeadBucket failed")

    key = f"preflight/textract-{uuid.uuid4().hex}.png"
    uploaded = False
    try:
        with tempfile.NamedTemporaryFile(prefix="slickhood-textract-", suffix=".png") as fixture:
            fixture.write(create_preflight_png())
            fixture.flush()
            put = run([
                aws_path, "s3api", "put-object", "--bucket", bucket, "--key", key,
                "--body", fixture.name, "--content-type", "image/png", "--region", region,
            ], env=child_env, timeout=30)
            uploaded = put.returncode == 0
        if not uploaded:
            preflight.fail("S3 temporary preflight object", "PutObject failed")
            return
        preflight.pass_("S3 temporary preflight object")
        document = json.dumps({"S3Object": {"Bucket": bucket, "Name": key}}, separators=(",", ":"))
        detect = run([
            aws_path, "textract", "detect-document-text", "--region", region,
            "--document", document,
        ], env=child_env, timeout=30)
        if detect.returncode == 0:
            preflight.pass_("Textract DetectDocumentText permission and reachability")
        else:
            preflight.fail("Textract DetectDocumentText permission and reachability", "AWS request failed")
    finally:
        if uploaded:
            deleted = run([
                aws_path, "s3api", "delete-object", "--bucket", bucket, "--key", key, "--region", region,
            ], env=child_env, timeout=30)
            if deleted.returncode == 0:
                preflight.pass_("S3 temporary preflight cleanup")
            else:
                preflight.fail("S3 temporary preflight cleanup", "DeleteObject failed")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--readiness-url", required=True)
    parser.add_argument("--public-origin", default="https://app.slickhood.com")
    parser.add_argument("--expected-flyway-version", type=int, default=71)
    parser.add_argument("--expected-bucket", required=True)
    parser.add_argument("--expected-region", required=True)
    args = parser.parse_args()

    preflight = Preflight()
    if os.geteuid() != 0:
        preflight.fail("execution identity", "run as root so protected configuration can be inspected")
        return preflight.finish()
    if not args.public_origin.startswith("https://") or not args.readiness_url.startswith("https://"):
        preflight.fail("HTTPS endpoints", "public origin and readiness URL must use HTTPS")
        return preflight.finish()

    config = load_configuration(preflight)
    check_service(preflight, "pms.service")
    check_service(preflight, "pm2-silverocean.service")
    check_clamav(preflight)
    check_readiness(preflight, args.readiness_url,
                    {"wealth", "insurance", "affiliate", "services", "soko", "helpdesk"})
    check_cors(preflight, args.public_origin.rstrip("/"))
    check_database(preflight, config, args.expected_flyway_version)

    bucket = required(preflight, config, "S3 bucket", "garage.s3.bucket", "GARAGE_S3_BUCKET")
    region = required(preflight, config, "S3 region", "garage.s3.region", "GARAGE_S3_REGION")
    if bucket and bucket != args.expected_bucket:
        preflight.fail("S3 bucket identity", "configured bucket does not match the approved production bucket")
    else:
        preflight.pass_("S3 bucket identity")
    if region and region != args.expected_region:
        preflight.fail("S3 region identity", "configured region does not match the approved production region")
    else:
        preflight.pass_("S3 region identity")
    check_aws_data_plane(preflight, config, bucket, region)
    check_mail(preflight, config)
    check_imap(preflight, config)
    check_external_apis(preflight, config)
    return preflight.finish()


if __name__ == "__main__":
    sys.exit(main())

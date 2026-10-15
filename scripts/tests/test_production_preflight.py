import contextlib
import importlib.util
import io
import sys
import unittest
from pathlib import Path
from unittest import mock
from urllib.parse import urlsplit


MODULE_PATH = Path(__file__).resolve().parents[1] / "production-preflight.py"
SPEC = importlib.util.spec_from_file_location("slickhood_production_preflight", MODULE_PATH)
PREFLIGHT = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = PREFLIGHT
SPEC.loader.exec_module(PREFLIGHT)


class PublicMarketplaceCorsPreflightTest(unittest.TestCase):
    def setUp(self):
        self.calls = []

    def successful_contract(self, url, *, method="GET", headers=None, body=None, timeout=12):
        del body, timeout
        headers = dict(headers or {})
        self.calls.append((url, method, headers))
        origin = headers.get("Origin", "")
        path = urlsplit(url).path
        is_read = path in ("/api/soko/catalog", "/api/sp/directory")
        is_protected = path in ("/api/soko/order/checkout", "/api/sp/booking/create")
        if is_read and origin in PREFLIGHT.PUBLIC_WEBSITE_ORIGINS:
            return (204 if method == "OPTIONS" else 200), b"", {
                "access-control-allow-origin": origin,
            }
        if (is_read and origin == "https://untrusted.invalid") or is_protected:
            return 403, b"", {}
        raise AssertionError(f"Unexpected request: {method} {url} from {origin}")

    def run_check(self, request_status):
        checks = PREFLIGHT.Preflight()
        with mock.patch.object(PREFLIGHT, "request_status", side_effect=request_status), \
                contextlib.redirect_stdout(io.StringIO()):
            PREFLIGHT.check_public_marketplace_cors(checks, "https://app.slickhood.com")
        return checks

    def test_official_origins_can_only_read_anonymous_catalogues(self):
        checks = self.run_check(self.successful_contract)

        self.assertEqual([], checks.failures)
        self.assertEqual(16, len(self.calls))
        for _, _, headers in self.calls:
            self.assertNotIn("Authorization", headers)
        protected = [call for call in self.calls if urlsplit(call[0]).path in {
            "/api/soko/order/checkout", "/api/sp/booking/create"
        }]
        self.assertEqual(4, len(protected))
        self.assertTrue(all(method == "OPTIONS" for _, method, _ in protected))

    def test_missing_catalogue_allow_origin_blocks_release(self):
        def missing_allow_origin(url, **kwargs):
            status, body, headers = self.successful_contract(url, **kwargs)
            request_headers = kwargs.get("headers") or {}
            if (urlsplit(url).path == "/api/soko/catalog"
                    and kwargs.get("method", "GET") == "GET"
                    and request_headers.get("Origin") == "https://www.slickhood.com"):
                headers = {}
            return status, body, headers

        checks = self.run_check(missing_allow_origin)

        self.assertIn(
            "public Soko catalogue browser access from https://www.slickhood.com",
            checks.failures,
        )

    def test_public_preflight_for_protected_mutation_blocks_release(self):
        def exposed_checkout(url, **kwargs):
            status, body, headers = self.successful_contract(url, **kwargs)
            request_headers = kwargs.get("headers") or {}
            if (urlsplit(url).path == "/api/soko/order/checkout"
                    and request_headers.get("Origin") == "https://slickhood.com"):
                return 204, body, {"Access-Control-Allow-Origin": "https://slickhood.com"}
            return status, body, headers

        checks = self.run_check(exposed_checkout)

        self.assertIn(
            "Soko checkout remains app-only from https://slickhood.com",
            checks.failures,
        )

    def test_untrusted_catalogue_origin_blocks_release_if_allowed(self):
        def exposed_catalogue(url, **kwargs):
            status, body, headers = self.successful_contract(url, **kwargs)
            request_headers = kwargs.get("headers") or {}
            if (urlsplit(url).path == "/api/sp/directory"
                    and request_headers.get("Origin") == "https://untrusted.invalid"):
                return 200, body, {"Access-Control-Allow-Origin": "https://untrusted.invalid"}
            return status, body, headers

        checks = self.run_check(exposed_catalogue)

        self.assertIn(
            "public Services directory rejects untrusted browser origins",
            checks.failures,
        )


if __name__ == "__main__":
    unittest.main()

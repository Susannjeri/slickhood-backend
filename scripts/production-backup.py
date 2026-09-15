#!/usr/bin/env python3
"""Protected host-local backups before forward-only production migrations.

Never export customer data or configuration values to the deployment runner.
Only archive paths, sizes and checksums are reported. Database rollback is a
separate, explicitly authorised recovery operation, not an automatic downgrade.
"""
import gzip
import hashlib
import importlib.util
import os
from pathlib import Path
import re
import subprocess
import sys
from datetime import datetime, timezone


def main():
    if os.geteuid() != 0:
        raise RuntimeError('Root is required for protected backup creation')
    os.umask(0o077)
    spec = importlib.util.spec_from_file_location('preflight', Path(__file__).with_name('production-preflight.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    checks = module.Preflight()
    config = module.load_configuration(checks)
    if checks.failures:
        raise RuntimeError('Protected configuration check failed')
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    destination = Path('/home/silverocean/backups') / ('pending-release-' + stamp)
    destination.mkdir(mode=0o700)
    for label, prefix in [('primary', 'spring.datasource'), ('audit', 'audit.datasource.mysql')]:
        jdbc = module.resolve(config, prefix + '.url', prefix + '.jdbc-url')
        username = module.resolve(config, prefix + '.username')
        password = module.resolve(config, prefix + '.password')
        match = re.match(r'jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)', jdbc)
        if not match or not username or not password:
            raise RuntimeError(label + ' datasource configuration unavailable')
        host, port, database = match.group(1), match.group(2) or '3306', match.group(3)
        env = dict(os.environ, MYSQL_PWD=password)
        target = destination / (label + '.sql.gz')
        result = subprocess.Popen([
            'mysqldump', '--single-transaction', '--routines', '--triggers', '--events',
            '--hex-blob', '--set-gtid-purged=OFF', '--host', host, '--port', port,
            '--user', username, database,
        ], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, env=env)
        with gzip.open(target, 'wb') as output:
            while chunk := result.stdout.read(1024 * 1024):
                output.write(chunk)
        result.wait(timeout=60)
        if result.returncode:
            raise RuntimeError(label + ' backup failed; incomplete archive retained for investigation')
        content_size = 0
        with gzip.open(target, 'rb') as source:
            while chunk := source.read(1024 * 1024):
                content_size += len(chunk)
        if content_size < 100:
            raise RuntimeError(label + ' backup is unexpectedly empty')
        checksum = hashlib.sha256()
        with target.open('rb') as source:
            while chunk := source.read(1024 * 1024):
                checksum.update(chunk)
        digest = checksum.hexdigest()
        target.with_suffix(target.suffix + '.sha256').write_text(digest + '  ' + target.name + '\n')
        print('BACKUP_VERIFIED', label, str(target), target.stat().st_size, digest, flush=True)
    print('BACKUP_COMPLETE', destination, flush=True)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('BACKUP_FAILED', type(error).__name__, str(error) if isinstance(error, RuntimeError)
              else 'Operation failed; no configuration values displayed', flush=True)
        sys.exit(1)

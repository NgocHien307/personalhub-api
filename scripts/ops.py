"""Portable Docker Compose backup/recovery; Python 3.11+, no extra packages."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import time
from datetime import datetime, timezone
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]


def run(args, *, data=None, env=None):
    # Capture output: neither Docker environment nor private SQL rows reach logs.
    result = subprocess.run(args, input=data, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, cwd=ROOT, env=env)
    if result.returncode:
        raise RuntimeError(f"{args[0]} operation failed (exit {result.returncode}); check the service locally")
    return result.stdout


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    path.chmod(0o600)


def validated_archive(value):
    if Path(value).is_symlink():
        raise ValueError('Backup must be a regular file')
    path = Path(value).resolve(strict=True)
    manifest = json.loads(path.with_suffix('.dump.manifest.json').read_text(encoding='utf-8'))
    checksum = path.with_suffix('.dump.sha256').read_text(encoding='ascii').split()[0]
    if path.is_symlink() or path.suffix != '.dump' or digest(path) != checksum or checksum != manifest['sha256']:
        raise ValueError('Backup checksum mismatch or invalid archive; restore refused')
    return path, manifest


class Database:
    def __init__(self, container, database='personalhub'):
        self.container, self.database = container, database

    def exec(self, *args, data=None):
        return run(['docker', 'exec', '-i', self.container, *args], data=data)

    def sql(self, query):
        return self.exec('psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', 'personalhub',
                         '-d', self.database, data=query.encode()).decode().strip()

    def snapshot(self):
        tables = json.loads(self.sql("SELECT coalesce(json_agg(tablename ORDER BY tablename),'[]') "
                                     "FROM pg_tables WHERE schemaname='public';"))
        if 'flyway_schema_history' not in tables:
            raise ValueError('Not a migrated PersonalHub database')
        queries = []
        for name in tables:
            identifier = '"' + name.replace('"', '""') + '"'
            literal = "'" + name.replace("'", "''") + "'"
            queries.append(f"SELECT {literal} AS name, count(*) AS rows, "
                           "md5(coalesce(string_agg(md5(row_to_json(t)::text), '' "
                           f"ORDER BY md5(row_to_json(t)::text)),'')) AS fingerprint FROM public.{identifier} t")
        return json.loads(self.sql("BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY; "
                                    "SET LOCAL TIME ZONE 'UTC'; SELECT json_agg(t ORDER BY name) FROM (" +
                                    ' UNION ALL '.join(queries) + ') t; COMMIT;'))


def source_database(args):
    command = ['docker', 'compose', '--env-file', str(Path(args.env_file).resolve())]
    if args.project_name:
        command += ['--project-name', args.project_name]
    command += ['-f', 'compose.yaml']
    if args.production:
        command += ['-f', 'compose.production.yaml']
    ids = run(command + ['ps', '-q', 'db']).decode().split()
    if len(ids) != 1:
        raise RuntimeError('Expected exactly one running db service')
    name = run(['docker', 'exec', ids[0], 'printenv', 'POSTGRES_DB']).decode().strip()
    return Database(ids[0], name)


def backup(args):
    source = source_database(args)
    before = source.snapshot()
    directory = Path(args.output_dir).resolve()
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    name = 'personalhub-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + uuid4().hex[:8] + '.dump'
    output = directory / name
    partial = output.with_suffix('.dump.partial')
    remote = '/tmp/' + name
    try:
        source.exec('pg_dump', '-U', 'personalhub', '-d', source.database, '--format=custom',
                    '--no-owner', '--no-privileges', '--file=' + remote)
        run(['docker', 'cp', source.container + ':' + remote, str(partial)])
        partial.chmod(0o600)
        after = source.snapshot()
        stable = before == after
        partial.rename(output)
        checksum = digest(output)
        output.with_suffix('.dump.sha256').write_text(checksum + '  ' + name + '\n', encoding='ascii')
        write_json(output.with_suffix('.dump.manifest.json'), {
            'createdAtUtc': datetime.now(timezone.utc).isoformat(), 'format': 'pg_dump custom',
            'database': source.database, 'sha256': checksum, 'bytes': output.stat().st_size,
            'sourceStableDuringBackup': stable, 'tables': before if stable else None,
        })
        # A busy source still has a consistent pg_dump, but no exact source-comparison claim.
        return {'archive': str(output), 'sha256': checksum, 'sourceStableDuringBackup': stable}
    finally:
        source.exec('rm', '-f', remote)
        partial.unlink(missing_ok=True)


def load_archive(target, archive):
    remote = '/tmp/personalhub-restore-' + uuid4().hex + '.dump'
    try:
        run(['docker', 'cp', str(archive), target.container + ':' + remote])
        target.exec('pg_restore', '-U', 'personalhub', '-d', target.database, '--exit-on-error',
                    '--single-transaction', '--no-owner', '--no-privileges', remote)
    finally:
        target.exec('rm', '-f', remote)


def compare_restore(target, manifest):
    tables = target.snapshot()
    matches = manifest['tables'] == tables if manifest.get('tables') is not None else None
    if matches is False:
        raise RuntimeError('Restored table fingerprints differ from backup manifest')
    return {'restore': 'PASS', 'sourceFingerprintsMatch': matches,
            'tables': {item['name']: item['rows'] for item in tables}}


def restore_check(args):
    archive, manifest = validated_archive(args.archive)
    name = 'personalhub-restore-check-' + uuid4().hex[:12]
    created = False
    try:
        # No network, published port, host mount or production volume. Local socket only.
        run(['docker', 'run', '-d', '--name', name, '--network=none',
             '-e', 'POSTGRES_USER=personalhub', '-e', 'POSTGRES_DB=personalhub_restore',
             '-e', 'POSTGRES_HOST_AUTH_METHOD=trust', 'postgres:17-alpine'])
        created = True
        target = Database(name, 'personalhub_restore')
        for _ in range(60):
            try:
                target.exec('pg_isready', '-h', '127.0.0.1', '-U', 'personalhub', '-d', target.database)
                break
            except RuntimeError:
                time.sleep(0.5)
        else:
            raise RuntimeError('Isolated restore database not ready')
        load_archive(target, archive)
        return compare_restore(target, manifest)
    finally:
        if created:
            run(['docker', 'rm', '-f', '-v', name])


def restore_new(args):
    archive, manifest = validated_archive(args.archive)
    # Restrict this operation to a fresh recovery name, never the active database.
    if not re.fullmatch(r'personalhub_restore_[a-z0-9_]{1,48}', args.database):
        raise ValueError('Use a new database named personalhub_restore_<suffix>')
    source = source_database(args)
    existing = source.sql("SELECT count(*) FROM pg_database WHERE datname='" + args.database + "';")
    if existing != '0' or args.database == source.database:
        raise ValueError('Database already exists; restore refused')
    source.exec('createdb', '-U', 'personalhub', '--template=template0', args.database)
    target = Database(source.container, args.database)
    load_archive(target, archive)
    return {**compare_restore(target, manifest), 'database': args.database,
            'activeDatabaseChanged': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--production', action='store_true')
    parser.add_argument('--project-name')
    parser.add_argument('--env-file', default=str(ROOT / '.env'))
    commands = parser.add_subparsers(dest='command', required=True)
    create = commands.add_parser('backup')
    create.add_argument('--output-dir', default=str(ROOT / 'backups'))
    for name in ('restore-check', 'restore-new'):
        command = commands.add_parser(name)
        command.add_argument('archive')
        if name == 'restore-new':
            command.add_argument('--database', required=True)
    args = parser.parse_args()
    try:
        result = {'backup': backup, 'restore-check': restore_check, 'restore-new': restore_new}[args.command](args)
        print(json.dumps(result, indent=2))
    except (RuntimeError, ValueError, OSError, KeyError) as error:
        # No command arguments, subprocess output or environment included.
        print('Operation failed: ' + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())

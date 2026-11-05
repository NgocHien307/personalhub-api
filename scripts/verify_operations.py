"""Exercise backup, checksum rejection and non-destructive recovery on disposable data."""
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
from uuid import uuid4

from ops import ROOT, Database, backup, restore_check, restore_new, run, validated_archive


def main():
    project = 'personalhub-ops-test-' + uuid4().hex[:8]
    with tempfile.TemporaryDirectory(prefix='personalhub-ops-') as temporary:
        directory = Path(temporary)
        env = directory / '.env'
        env.write_text('POSTGRES_PASSWORD=ops-disposable-only-password\n'
                       'PERSONALHUB_API_KEY=ops-disposable-only-api-key\nGITHUB_USERNAME=octocat\nSITE_ADDRESS=localhost\n')
        command = ['docker', 'compose', '--env-file', str(env), '-p', project,
                   '-f', 'compose.yaml', '-f', 'compose.production.yaml']
        args = SimpleNamespace(env_file=str(env), project_name=project, production=True,
                               output_dir=str(directory / 'backups'))
        try:
            run(command + ['up', '-d', '--wait', 'db'])
            container = run(command + ['ps', '-q', 'db']).decode().strip()
            db = Database(container)
            migrations = sorted((ROOT / 'src/main/resources/db/migration').glob('V*.sql'))
            for migration in migrations:
                db.sql(migration.read_text(encoding='utf-8'))
            db.sql("CREATE TABLE flyway_schema_history (installed_rank int PRIMARY KEY, version text); "
                   "INSERT INTO flyway_schema_history VALUES (1,'1'),(2,'2'); "
                   "INSERT INTO github_repositories VALUES (1,'test','sample','test/sample',"
                   "'https://github.com/test/sample','main',false,now()); "
                   "INSERT INTO github_commits VALUES (1,'test-sha','test','Fixture only',now());")
            baseline = db.snapshot()
            result = backup(args)
            args.archive = result['archive']
            restored = restore_check(args)
            assert restored['sourceFingerprintsMatch'] is True
            archive = Path(args.archive)
            original = archive.read_bytes()
            archive.write_bytes(original + b'corruption')
            try:
                validated_archive(archive)
                raise AssertionError('Corrupt backup accepted')
            except ValueError:
                pass
            finally:
                archive.write_bytes(original)
            for forbidden in ('personalhub', 'postgres', 'personalhub_restore_bad;DROP DATABASE personalhub'):
                args.database = forbidden
                try:
                    restore_new(args)
                    raise AssertionError('Unsafe restore database accepted')
                except ValueError:
                    pass
            args.database = 'personalhub_restore_test'
            assert restore_new(args)['sourceFingerprintsMatch'] is True
            try:
                restore_new(args)
                raise AssertionError('Existing database overwritten')
            except ValueError:
                pass
            assert db.snapshot() == baseline
            print(json.dumps({'backupAndIsolatedRestore': 'PASS', 'checksumRejection': 'PASS',
                              'activeDatabaseGuard': 'PASS', 'freshDatabaseRecovery': 'PASS',
                              'existingDatabaseGuard': 'PASS', 'sourceUnchanged': True}))
        finally:
            # This name is generated above; this project has disposable data only.
            run(command + ['down', '--volumes', '--remove-orphans'])


if __name__ == '__main__':
    main()

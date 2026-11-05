"""Validate the production stack over trusted local HTTPS, using an isolated restored DB.

Optional --sync calls real upstream APIs against the isolated database only.
The local .env is read by Compose; credentials and private API payloads are never printed.
"""
import argparse
from datetime import datetime, timezone
import http.client
import json
import os
from pathlib import Path
import re
import socket
import ssl
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from uuid import uuid4

from ops import ROOT, Database, compare_restore, load_archive, run, validated_archive, write_json


def free_port():
    with socket.socket() as channel:
        channel.bind(('127.0.0.1', 0))
        return channel.getsockname()[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive')
    parser.add_argument('--sync', action='store_true', help='Opt in to real GitHub/Notion sync')
    parser.add_argument('--expected-commit', help='Require this SHA after GitHub sync')
    args = parser.parse_args()
    archive, manifest = validated_archive(args.archive)
    project = 'personalhub-https-check-' + uuid4().hex[:8]
    environment = dict(os.environ, SITE_ADDRESS='localhost', PUBLIC_BIND_ADDRESS='127.0.0.1',
                       PUBLIC_HTTP_PORT=str(free_port()), PUBLIC_HTTPS_PORT=str(free_port()),
                       POSTGRES_DB='personalhub_https_test', SYNC_ENABLED='false', NOTION_SYNC_ENABLED='false')
    command = ['docker', 'compose', '-p', project, '-f', 'compose.yaml', '-f', 'compose.production.yaml']

    def compose(*parts):
        return run(command + list(parts), env=environment)

    config = json.loads(compose('config', '--format', 'json'))
    assert not config['services']['db'].get('ports'), 'Database must not publish ports'
    assert not config['services']['api'].get('ports'), 'API must not publish ports'
    key = config['services']['api']['environment']['PERSONALHUB_API_KEY']
    base = 'https://localhost:' + environment['PUBLIC_HTTPS_PORT']
    report = {'checkedAtUtc': datetime.now(timezone.utc).isoformat(),
              'scope': 'local HTTPS, isolated database; not a public deployment',
              'databaseAndApiPortsPublished': False, 'certificateVerification': True,
              'realUpstreamSyncRequested': args.sync}
    try:
        compose('up', '-d', '--wait', 'db')
        database = Database(compose('ps', '-q', 'db').decode().strip(), 'personalhub_https_test')
        load_archive(database, archive)
        report['restoredData'] = compare_restore(database, manifest)
        compose('up', '-d', '--build', 'api', 'caddy')
        with tempfile.TemporaryDirectory(prefix='personalhub-tls-') as temporary:
            certificate = Path(temporary) / 'root.crt'
            for _ in range(60):
                try:
                    compose('cp', 'caddy:/data/caddy/pki/authorities/local/root.crt', str(certificate))
                    break
                except RuntimeError:
                    time.sleep(1)
            else:
                raise RuntimeError('Local HTTPS certificate was not created')
            context = ssl.create_default_context(cafile=str(certificate))
            connection = http.client.HTTPConnection('localhost', int(environment['PUBLIC_HTTP_PORT']))
            try:
                connection.request('GET', '/dashboard')
                redirect = connection.getresponse()
                assert redirect.status == 308 and redirect.getheader('Location').startswith('https://localhost/')
                report['httpToHttpsRedirect'] = 'PASS'
            finally:
                connection.close()

            def request(path, *, authenticated=True, method='GET'):
                headers = {'X-Request-ID': str(uuid4())}
                if authenticated:
                    headers['X-API-Key'] = key
                req = Request(base + path, headers=headers, method=method)
                try:
                    with urlopen(req, context=context, timeout=180) as response:
                        return response.status, response.headers, response.read()
                except HTTPError as error:
                    return error.code, error.headers, error.read()

            for _ in range(90):
                try:
                    code, _, body = request('/actuator/health', authenticated=False)
                    if code == 200 and json.loads(body)['status'] == 'UP':
                        break
                except (URLError, OSError):
                    pass
                time.sleep(1)
            else:
                raise RuntimeError('HTTPS application did not become healthy')
            code, headers, body = request('/dashboard', authenticated=False)
            assert code == 200 and b'app.mjs' in body
            assert "default-src 'none'" in headers['Content-Security-Policy']
            code, _, _ = request('/dashboard/app.mjs', authenticated=False)
            assert code == 200
            code, headers, body = request('/api/v1/dashboard/overview', authenticated=False)
            assert code == 401 and json.loads(body)['requestId'] == headers['X-Request-ID']
            code, _, body = request('/api/v1/dashboard/overview')
            assert code == 200
            report['httpsHealth'] = 'UP'
            report['dashboardShellAndAsset'] = 'PASS'
            report['apiAuthenticationAndRequestId'] = 'PASS'
            if args.sync:
                def sync_github():
                    code, _, body = request('/api/v1/sync/github', method='POST')
                    assert code == 202
                    run_id = json.loads(body)['id']
                    for _ in range(120):
                        code, _, body = request('/api/v1/sync/runs/' + run_id)
                        job = json.loads(body)
                        if job['status'] in ('SUCCESS', 'FAILED'):
                            break
                        time.sleep(1)
                    assert job['status'] == 'SUCCESS', 'GitHub sync failed'
                    return job

                job = sync_github()
                report['githubSync'] = {'status': job['status'], 'repositoriesProcessed': job['repositoriesProcessed']}
                before_commits = database.sql('SELECT count(*) FROM github_commits;')
                sync_github()
                assert database.sql('SELECT count(*) FROM github_commits;') == before_commits
                report['githubRepeatSyncNoDuplicates'] = True
                code, _, body = request('/api/v1/notion/sync?full=true', method='POST')
                notion = json.loads(body)
                assert code == 200 and notion['status'] == 'SUCCESS', 'Notion sync failed'
                report['notionSync'] = {'status': notion['status'], 'tasksProcessed': notion['tasksProcessed']}
                before_tasks = database.sql('SELECT count(*) FROM notion_tasks;')
                code, _, body = request('/api/v1/notion/sync?full=true', method='POST')
                assert code == 200 and json.loads(body)['status'] == 'SUCCESS'
                assert database.sql('SELECT count(*) FROM notion_tasks;') == before_tasks
                report['notionRepeatSyncNoDuplicates'] = True
                report['notionDatePresent'] = int(database.sql('SELECT count(*) FROM notion_tasks WHERE due_start IS NOT NULL;')) > 0
                if args.expected_commit:
                    if not re.fullmatch(r'[0-9a-f]{40}', args.expected_commit):
                        raise ValueError('Expected commit must be a full lowercase SHA')
                    assert database.sql("SELECT count(*) FROM github_commits WHERE sha='" + args.expected_commit + "';") == '1'
                    report['publishedCommitImported'] = True
            code, _, body = request('/api/v1/dashboard/overview')
            assert code == 200
            overview = json.loads(body)
            report['overview'] = {'repositories': overview['github']['trackedRepositories'],
                                  'activeTasks': overview['notion']['activeTasks']}
            report['result'] = 'PASS'
            output = ROOT / 'target/deployment-verification.json'
            write_json(output, report)
            print(json.dumps(report, indent=2))
    finally:
        # Project is generated here; only these disposable volumes are removed.
        compose('down', '--volumes', '--remove-orphans')


if __name__ == '__main__':
    main()

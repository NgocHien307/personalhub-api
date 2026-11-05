from pathlib import Path
import argparse
import hashlib
import re
import zipfile


def package_source(root, filename):
    root = Path(root).resolve()
    if Path(filename).name != filename or not filename.endswith('.zip'):
        raise ValueError('Expected a ZIP filename')
    files = [root / name for name in (
        '.dockerignore', '.env.example', '.gitignore', 'Dockerfile', 'README.md', 'compose.yaml',
        'compose.production.yaml', 'pom.xml',
        'postman/PersonalHub.postman_collection.json', 'postman/PersonalHub.local.example.postman_environment.json')]
    for folder in ('src', 'docs', '.github/workflows', 'scripts', 'deploy'):
        files.extend(path for path in (root / folder).rglob('*')
                     if path.is_file() and '__pycache__' not in path.parts)
    files = sorted(set(files), key=lambda path: path.relative_to(root).as_posix())
    private_values = []
    local_env = root / '.env'
    for line in local_env.read_text(encoding='utf-8-sig').splitlines() if local_env.exists() else []:
        match = re.match(r'^\s*(?:export\s+)?(GITHUB_TOKEN|NOTION_TOKEN|PERSONALHUB_API_KEY|POSTGRES_PASSWORD)\s*=\s*(.*?)\s*$', line)
        if match:
            value = match[2]
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            if value:
                private_values.append(value.encode())
    patterns = [re.compile(rb'gh[pousr]_[A-Za-z0-9]{30,}'), re.compile(rb'github_pat_[A-Za-z0-9_]{30,}'),
                re.compile(rb'ntn_[A-Za-z0-9]{25,}'), re.compile(rb'-----BEGIN [A-Z ]*PRIVATE KEY-----')]
    for path in files:
        relative = path.relative_to(root)
        if path.is_symlink() or any(part in ('.git', '.postman', 'target', 'dist', 'backups', '__pycache__') for part in relative.parts):
            raise RuntimeError('Unexpected package path: ' + str(relative))
        if path.name.startswith('.env') and path.name != '.env.example':
            raise RuntimeError('Environment file refused: ' + str(relative))
        if path.suffix in ('.dump', '.pem', '.key', '.p12', '.pfx', '.jks', '.keystore'):
            raise RuntimeError('Private file refused: ' + str(relative))
        data = path.read_bytes()
        if any(value in data for value in private_values) or any(pattern.search(data) for pattern in patterns):
            raise RuntimeError('Possible credential in package file: ' + str(relative))
    output = root / 'dist' / filename
    output.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in files:
            archive.write(path, path.relative_to(root).as_posix())
    with zipfile.ZipFile(output) as archive:
        if len(archive.namelist()) != len(files) or archive.testzip() is not None:
            raise RuntimeError('ZIP integrity failure')
        for path in files:
            name = path.relative_to(root).as_posix()
            if hashlib.sha256(archive.read(name)).digest() != hashlib.sha256(path.read_bytes()).digest():
                raise RuntimeError('ZIP content mismatch: ' + name)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    output.with_suffix('.zip.sha256').write_text(digest + '  ' + output.name + '\n', encoding='ascii')
    return {'archive': str(output), 'files': len(files), 'bytes': output.stat().st_size,
            'sha256': digest, 'contentHashesMatch': True, 'localSecretMatches': 0}


if __name__ == '__main__':
    import json
    parser = argparse.ArgumentParser(description='Package source from an explicit allowlist; reject local secrets.')
    parser.add_argument('--filename', default='personalhub-api-0.1.0-source.zip')
    options = parser.parse_args()
    print(json.dumps(package_source(Path(__file__).resolve().parents[1], options.filename), indent=2))

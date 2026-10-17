"""Repair only the existing, user-approved temporary collector (not its expiry/scope).

Run on the VPS with --apply. No application payload, credentials or raw IPs are
printed or added to the existing metadata-only diagnostics. No backend restart.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import time
import uuid

BASE = Path('/var/lib/pusula-connection-diag-20261014')
UNIT = 'pusula-connection-diag-20261014'
UNIT_PATH = Path('/etc/systemd/system') / (UNIT + '.service')
RUNTIME = Path('/run') / UNIT
CONF = Path('/etc/nginx/conf.d/90-pusula-connection-diag-20261014.conf')
SITE = Path('/etc/nginx/sites-available/api.pusulaiklimlendirme.com')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def call(*args):
    result = subprocess.run(args, capture_output=True, text=True, timeout=40)
    if result.returncode:
        raise RuntimeError('Command failed: ' + args[0])
    return result.stdout.strip()


def repaired_unit(original):
    if original.count(b'[Service]\n') != 1 or b'RuntimeDirectory=' in original:
        raise RuntimeError('Unexpected service definition; inspect before changing')
    if b'ReadWritePaths=' + BASE.as_posix().encode() + b' ' + RUNTIME.as_posix().encode() not in original:
        raise RuntimeError('Unexpected write paths')
    return original.replace(b'[Service]\n',
        b'[Service]\nRuntimeDirectory=pusula-connection-diag-20261014\nRuntimeDirectoryMode=0750\n', 1)


def main(apply):
    if os.geteuid() != 0:
        raise RuntimeError('Run on the VPS as root')
    for path in (BASE, RUNTIME, UNIT_PATH, CONF, SITE):
        if path.is_symlink():
            raise RuntimeError('Symlink target refused')
    spec = importlib.util.spec_from_file_location('existing_diag', BASE / 'diag.py')
    diag = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(diag)
    manifest_path = BASE / 'manifest.json'
    original_manifest = manifest_path.read_bytes()
    settings = json.loads(original_manifest)
    if settings.get('disabled') or time.time() >= settings['expires_epoch']:
        raise RuntimeError('The approved recording window has expired or is disabled')
    original = UNIT_PATH.read_bytes()
    if sha(original) != settings['unit_sha256'][UNIT_PATH.name]:
        raise RuntimeError('Service ownership hash changed')
    if sha(CONF.read_bytes()) != settings['conf_sha256']:
        raise RuntimeError('Diagnostic configuration ownership hash changed')
    patched = repaired_unit(original)
    before = {name: call('systemctl', 'show', name, '--property=MainPID', '--value')
              for name in ('pusula-backend', 'nginx')}
    site_hash, conf_hash = sha(SITE.read_bytes()), sha(CONF.read_bytes())
    if not apply:
        print(json.dumps({'validated': True, 'changes': ['RuntimeDirectory', 'RuntimeDirectoryMode'],
                          'expiryUnchanged': settings['expiresAt'], 'backendRestart': False}))
        return
    backup = BASE / ('runtime-repair-backup-' + uuid.uuid4().hex)
    backup.mkdir(mode=0o700)
    diag.write(backup / UNIT_PATH.name, original)
    diag.write(backup / 'manifest.json', original_manifest)
    diag.write(UNIT_PATH, patched, 0o644)
    settings['unit_sha256'][UNIT_PATH.name] = sha(patched)
    settings['runtimeDirectoryRepairAt'] = diag.utc()
    diag.save(manifest_path, settings)
    try:
        call('systemd-analyze', 'verify', str(UNIT_PATH))
        call('systemctl', 'daemon-reload')
        call('systemctl', 'restart', UNIT + '.service')
        if not (RUNTIME / 'nginx.sock').is_socket():
            raise RuntimeError('Collector socket was not restored')
        # An actual stop removes the systemd-owned ephemeral directory. Starting
        # again must recreate it, exercising the same missing-directory condition.
        call('systemctl', 'stop', UNIT + '.service')
        if RUNTIME.exists():
            raise RuntimeError('Runtime directory was not removed on stop')
        call('systemctl', 'start', UNIT + '.service')
        if not (RUNTIME / 'nginx.sock').is_socket():
            raise RuntimeError('Cold-start socket was not created')
        call('nginx', '-t')
        call('systemctl', 'reload', 'nginx')
        probes = []
        for build in ('55', '57'):
            identifier = str(uuid.uuid4())
            status = call('curl', '--silent', '--show-error', '--max-time', '12',
                '--output', '/dev/null', '--write-out', '%{http_code}',
                '--resolve', 'api.pusulaiklimlendirme.com:443:168.231.104.133',
                '--user-agent', 'PusulaService/' + build + ' runtime-repair-probe',
                '--header', 'X-Pusula-Diagnostic-ID: ' + identifier,
                'https://api.pusulaiklimlendirme.com/api/users/technicians')
            if status != '401':
                raise RuntimeError('Unexpected unauthenticated probe result')
            probes.append(identifier)
        time.sleep(3)
        matched = set()
        kinds = set()
        with (BASE / 'events.jsonl').open() as stream:
            for line in stream:
                event = json.loads(line)
                if event.get('request_id') in probes and event.get('status') == 401:
                    matched.add(event['request_id'])
                if event.get('at', '') >= settings['runtimeDirectoryRepairAt']:
                    kinds.add(event.get('kind'))
        if matched != set(probes):
            raise RuntimeError('HTTP correlation was not restored')
        after = {name: call('systemctl', 'show', name, '--property=MainPID', '--value') for name in before}
        assert before == after, 'Backend/nginx master PIDs changed'
        assert site_hash == sha(SITE.read_bytes()) and conf_hash == sha(CONF.read_bytes())
        assert call('systemctl', 'is-active', UNIT + '-cleanup.timer') == 'active'
        assert all(sha((Path('/etc/systemd/system') / name).read_bytes()) == value
                   for name, value in settings['unit_sha256'].items())
        diag.cleanup()  # Before expiry, this must remain a no-op.
        receipt = {'atUTC': diag.utc(), 'repaired': True, 'coldStartFromMissingDirectoryPassed': True,
            'httpProbesMatched': len(matched), 'tcpMetadataObserved': 'tcp' in kinds, 'probeIds': probes,
            'backendAndNginxMasterPidsUnchanged': True, 'nginxConfigurationUnchanged': True,
            'expiryUnchangedUTC': settings['expiresAt'], 'maxDiskBytesUnchanged': settings['max_bytes'],
            'rawIpStored': False, 'applicationPayloadStored': False, 'cleanupOwnershipHashesVerified': True,
            'cleanupTimerActive': True, 'appReviewBuild54Modified': False, 'backup': str(backup)}
        diag.save(BASE / 'runtime-repair-receipt.json', receipt)
        print(json.dumps(receipt))
    except Exception:
        # Keep the repaired unit on a validation failure: reverting to the known
        # broken definition would discard diagnostic availability. Evidence and
        # the original unit are retained; an operator must inspect the result.
        print(json.dumps({'repairValidationFailed': True, 'backup': str(backup)}))
        raise


def verify_external_probe(identifier):
    """Complete TCP verification from a real off-VPS HTTPS request, not loopback."""
    uuid.UUID(identifier)
    spec = importlib.util.spec_from_file_location('existing_diag', BASE / 'diag.py')
    diag = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(diag)
    settings = json.loads((BASE / 'manifest.json').read_bytes())
    assert time.time() < settings['expires_epoch'] and not settings.get('disabled')
    assert b'RuntimeDirectory=pusula-connection-diag-20261014\n' in UNIT_PATH.read_bytes()
    assert call('systemctl', 'is-active', UNIT + '.service') == 'active'
    assert call('systemctl', 'is-active', UNIT + '-cleanup.timer') == 'active'
    assert all(sha((Path('/etc/systemd/system') / name).read_bytes()) == value
               for name, value in settings['unit_sha256'].items())
    with (BASE / 'events.jsonl').open() as stream:
        events = [json.loads(line) for line in stream]
    matches = [e for e in events if e.get('request_id') == identifier and e.get('status') == 401]
    assert len(matches) == 1, 'External HTTP probe missing'
    http = matches[0]
    packets = [e for e in events if e.get('kind') == 'tcp' and e.get('peer') == http['peer']
               and e.get('peer_port') == http['peer_port'] and e.get('collector') == http['collector']]
    assert any(e['direction'] == 'in' and 'S' in e['flags'] for e in packets)
    assert any(e['direction'] == 'out' and 'S' in e['flags'] for e in packets)
    assert any(e['direction'] == 'in' and '.' in e['flags'] for e in packets)
    receipt = {'atUTC': diag.utc(), 'repaired': True, 'externalHttpAndTcpCorrelated': True,
        'externalProbeId': identifier, 'matchedTcpMetadataRecords': len(packets),
        'expiryUnchangedUTC': settings['expiresAt'], 'maxDiskBytesUnchanged': settings['max_bytes'],
        'rawIpStored': False, 'applicationPayloadStored': False, 'cleanupOwnershipHashesVerified': True,
        'cleanupTimerActive': True, 'appReviewBuild54Modified': False}
    diag.cleanup()
    diag.save(BASE / 'runtime-repair-external-verification.json', receipt)
    print(json.dumps(receipt))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--verify-external-probe')
    args = parser.parse_args()
    if args.verify_external_probe:
        verify_external_probe(args.verify_external_probe)
    else:
        main(args.apply)

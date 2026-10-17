"""Fail closed before uploading: inspect the actual distribution-signed IPA."""
import json
import os
import pathlib
import plistlib
import subprocess
import tempfile
import zipfile

root = pathlib.Path(os.environ['RUNNER_TEMP']).resolve() / 'pusula-ios-release'
expected_build = os.environ['BUILD_NUMBER']
ipa = root / 'export/PusulaService.ipa'
archive = root / 'PusulaService.xcarchive'

def command(*args):
    return subprocess.check_output(args, stderr=subprocess.STDOUT)

def stdout(*args):
    return subprocess.run(args, check=True, capture_output=True).stdout

def entitlement_plist(raw):
    # Modern codesign can prefix the XML with an entitlement-blob header.
    # Parse only a complete XML plist; never infer a missing entitlement.
    start = raw.find(b'<?xml')
    if start < 0:
        start = raw.find(b'<plist')
    end = raw.find(b'</plist>', max(start, 0))
    if start < 0 or end < 0:
        raise RuntimeError('codesign did not return a complete XML entitlement plist')
    return plistlib.loads(raw[start:end + len(b'</plist>')])

with tempfile.TemporaryDirectory(prefix='verify-', dir=root) as temporary:
    with zipfile.ZipFile(ipa) as package:
        for name in package.namelist():
            target = (pathlib.Path(temporary) / name).resolve()
            if not target.is_relative_to(pathlib.Path(temporary).resolve()):
                raise RuntimeError('Unsafe IPA entry')
        package.extractall(temporary)
    apps = list((pathlib.Path(temporary) / 'Payload').glob('*.app'))
    assert len(apps) == 1, 'Expected exactly one application'
    app = apps[0]
    info = plistlib.loads((app / 'Info.plist').read_bytes())
    assert info['CFBundleIdentifier'] == 'com.pusula.service', 'Wrong bundle ID'
    assert info['CFBundleShortVersionString'] == '1.0.1', 'Wrong marketing version'
    assert info['CFBundleVersion'] == expected_build, 'Wrong build number'
    command('codesign', '--verify', '--deep', '--strict', str(app))
    signature = command('codesign', '-dv', '--verbose=4', str(app)).decode()
    assert 'TeamIdentifier=DDV3HCZRP6' in signature, 'Wrong signing team'
    assert 'Authority=Apple Distribution' in signature, 'Not distribution signed'
    entitlements = entitlement_plist(stdout('codesign', '-d', '--entitlements', ':-', '--xml', str(app)))
    assert entitlements.get('get-task-allow') is False, 'Debug entitlement must be false'
    assert entitlements.get('aps-environment') == 'production', 'Push must use production'
    assert entitlements.get('com.apple.developer.applesignin') == ['Default'], 'Apple sign-in missing'
    assert entitlements.get('com.apple.developer.team-identifier') == 'DDV3HCZRP6'
    profile = plistlib.loads(command('security', 'cms', '-D', '-i', str(app / 'embedded.mobileprovision')))
    assert profile['TeamIdentifier'] == ['DDV3HCZRP6']
    assert 'ProvisionedDevices' not in profile, 'App Store profiles cannot list devices'
    assert not profile.get('ProvisionsAllDevices'), 'Enterprise distribution is not allowed'
    assert profile['Entitlements']['application-identifier'] == 'DDV3HCZRP6.com.pusula.service'
    assert profile['Entitlements'].get('beta-reports-active') is True
    executable = app / info['CFBundleExecutable']
    uuids = command('dwarfdump', '--uuid', str(executable)).decode()
    dsym = archive / 'dSYMs' / f'{app.name}.dSYM'
    assert dsym.exists(), 'Archive must retain app dSYMs'
    symbol_uuids = command('dwarfdump', '--uuid', str(dsym)).decode()
    binary_ids = [line.split()[1] for line in uuids.splitlines() if line.startswith('UUID:')]
    assert binary_ids and all(uuid in symbol_uuids for uuid in binary_ids), 'dSYM UUID mismatch'
    strings = command('strings', str(executable)).decode(errors='replace')
    assert 'https://api.pusulaiklimlendirme.com' in strings, 'Production API missing'
    assert not any(value in strings for value in ['PUSULA_SOCIAL_AUTH_FIXTURE', 'PUSULA_TICKET_STRESS_COUNT', 'PUSULA_TICKET_DETAIL_LOAD_FIXTURE', 'PUSULA_CUSTOMER_REFRESH_FIXTURE', 'PUSULA_CONNECTION_DIAGNOSTICS_FIXTURE', 'PUSULA_PHOTO_BATCH_TEST', 'PUSULA_GUIDE_TEST', 'PUSULA_SUPPORT_FIXTURE']), 'DEBUG UI test hook present in Release'
    assert (app / 'GoogleSans.ttf').exists(), 'Social sign-in font missing'
    report = {'bundle': info['CFBundleIdentifier'], 'version': '1.0.1', 'build': expected_build,
              'team': 'DDV3HCZRP6', 'signature': 'Apple Distribution', 'push': 'production',
              'appleSignIn': True, 'dSYM_UUIDs': binary_ids}
    (root / 'export-verification.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))

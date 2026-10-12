const { test } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { APP, buildNumber, privateKey, jwt, query, encryptionKey, decryptBackup } = require('./ios-release.cjs');
const read = name => fs.readFileSync(path.join(__dirname, '..', name), 'utf8');
const workflow = read('.github/workflows/ios-testflight.yml');
const { privateKey: key, publicKey } = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const pem = key.export({ format: 'pem', type: 'pkcs8' });

test('release identity excludes the retired iOS app', () => {
  assert.equal(APP.id, '6801256557');
  assert.equal(APP.bundle, 'com.pusula.service');
  assert.equal(APP.team, 'DDV3HCZRP6');
  assert.doesNotMatch(workflow, /com\.pusula\.service\.ios|XM36DTLK9N|6764022995/);
});
test('manual main-only release; read-only GitHub permissions; no overlapping upload', () => {
  assert.match(workflow, /workflow_dispatch:/);
  assert.doesNotMatch(workflow, /^  (push|pull_request|schedule|workflow_run):/m);
  assert.match(workflow, /if: github\.ref == 'refs\/heads\/main'/);
  assert.match(workflow, /contents: read/);
  assert.match(workflow, /actions: read/);
  assert.doesNotMatch(workflow, /write/);
  assert.match(workflow, /cancel-in-progress: false/);
  assert.match(workflow, /default: preflight/);
  assert.match(workflow, /persist-credentials: false/);
});
test('only reviewed, pinned checkout/artifact actions execute with release access', () => {
  const actions = [...workflow.matchAll(/uses: ([^\s#]+)/g)].map(m => m[1]);
  assert.equal(actions.length, 2);
  for (const action of actions) assert.match(action, /^actions\/(checkout|upload-artifact)@[a-f0-9]{40}$/);
});
test('input build numbers reject shell fragments, reuse and invalid Apple versions', () => {
  assert.equal(buildNumber('54'), '54');
  assert.equal(buildNumber('999'), '999');
  for (const value of ['53', '0', '-1', '54.1', '054', '54\n', '54; echo nope', '', undefined])
    assert.throws(() => buildNumber(value));
});
test('key material supports PEM, escaped PEM and base64 without changing key identity', () => {
  const exportDER = value => privateKey(value).export({ format: 'der', type: 'pkcs8' });
  assert.deepEqual(exportDER(pem), exportDER(Buffer.from(pem).toString('base64')));
  assert.deepEqual(exportDER(pem), exportDER(pem.replace(/\n/g, '\\n')));
  assert.throws(() => privateKey(''));
  const rsa = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 }).privateKey;
  assert.throws(() => privateKey(rsa.export({ format: 'pem', type: 'pkcs8' })));
});
test('Apple JWT is short-lived ES256 with a valid IEEE P1363 signature', () => {
  const token = jwt(key, 'TT657H627S', APP.issuer, 1000);
  const [header, payload, signature] = token.split('.');
  assert.deepEqual(JSON.parse(Buffer.from(header, 'base64url')), { alg: 'ES256', kid: 'TT657H627S', typ: 'JWT' });
  assert.deepEqual(JSON.parse(Buffer.from(payload, 'base64url')), { iss: APP.issuer, iat: 1000, exp: 1600, aud: 'appstoreconnect-v1' });
  assert.equal(Buffer.from(signature, 'base64url').length, 64);
  assert.equal(crypto.verify('sha256', Buffer.from(`${header}.${payload}`),
    { key: publicKey, dsaEncoding: 'ieee-p1363' }, Buffer.from(signature, 'base64url')), true);
  assert.throws(() => jwt(key, 'TT657H627S', 'wrong-team'));
});
test('Apple query parameters are encoded instead of interpolating input', () => {
  const url = new URL(query('/v1/builds', { 'filter[app]': APP.id, 'filter[version]': '54' }), 'https://api.appstoreconnect.apple.com');
  assert.equal(url.searchParams.get('filter[app]'), APP.id);
  assert.equal(url.searchParams.get('filter[version]'), '54');
});
test('artifact upload is encrypted only and private keys are excluded from the tar', () => {
  assert.match(workflow, /path: \$\{\{ runner\.temp \}\}\/pusula-ios-release\/backup\.pusula\.enc/);
  assert.doesNotMatch(workflow, /path:.*(\.ipa|\.xcarchive|private_keys|\.p8)/);
  const helper = read('scripts/ios-release.cjs');
  assert.match(helper, /aes-256-gcm/);
  assert.match(helper, /fs\.unlinkSync\(tarPath\)/);
  assert.match(helper, /crypto\.randomBytes\(32\)/);
});
test('encrypted backups can be recovered and reject modified or wrong-key data', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'pusula-backup-test-'));
  try {
    const keyPath = path.join(dir, 'test.key'); fs.writeFileSync(keyPath, pem);
    const salt = crypto.randomBytes(32), iv = crypto.randomBytes(12);
    const plain = Buffer.from('synthetic archive and dSYM data');
    const cipher = crypto.createCipheriv('aes-256-gcm', encryptionKey(key, salt), iv);
    const ciphertext = Buffer.concat([cipher.update(plain), cipher.final()]);
    const encrypted = Buffer.concat([Buffer.from('PUSULA01'), salt, iv, ciphertext, cipher.getAuthTag()]);
    const input = path.join(dir, 'backup.enc'), output = path.join(dir, 'recovered.tar.gz');
    fs.writeFileSync(input, encrypted);
    await decryptBackup(input, output, keyPath);
    assert.deepEqual(fs.readFileSync(output), plain);
    await assert.rejects(decryptBackup(input, output, keyPath)); // never overwrite existing output
    encrypted[55] ^= 1; fs.writeFileSync(input, encrypted);
    const corrupt = path.join(dir, 'corrupt.tar.gz');
    await assert.rejects(decryptBackup(input, corrupt, keyPath));
    assert.equal(fs.existsSync(corrupt), false);
    const otherKey = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' }).privateKey;
    fs.writeFileSync(keyPath, otherKey.export({ format: 'pem', type: 'pkcs8' }));
    await assert.rejects(decryptBackup(input, corrupt, keyPath));
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});
test('processing verification cannot mutate review, groups, app metadata or certificates', () => {
  const helper = read('scripts/ios-release.cjs');
  assert.doesNotMatch(helper, /method:\s*['"](?:POST|PATCH|DELETE)['"]/);
  assert.doesNotMatch(workflow, /submit_for_review|distribute_external|betaGroups|appStoreVersions/);
  assert.match(workflow, /python3 scripts\/verify-ios-export\.py/);
  assert.ok(workflow.indexOf('python3 scripts/verify-ios-export.py') < workflow.indexOf('xcrun altool --upload-app'));
});

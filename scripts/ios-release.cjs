// Manual TestFlight release support. Never submit a version for App Review here.
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { pipeline } = require('node:stream/promises');

const APP = Object.freeze({ id: '6801256557', bundle: 'com.pusula.service',
  team: 'DDV3HCZRP6', issuer: '3b14669e-aa75-4aa0-a56b-5729810d726f', version: '1.0.1' });
const RELEASE_DIR = 'pusula-ios-release';
function releaseDir() {
  if (!process.env.RUNNER_TEMP) throw new Error('RUNNER_TEMP is required');
  return path.join(path.resolve(process.env.RUNNER_TEMP), RELEASE_DIR);
}
function buildNumber(value) {
  if (!/^[1-9]\d{0,8}$/.test(value || '') || Number(value) < 54)
    throw new Error('A numeric, unused build number >= 54 is required');
  return value;
}
function privateKey(value) {
  if (!value) throw new Error('The existing App Store Connect private-key secret is missing');
  const pem = value.includes('-----BEGIN') ? value.replace(/\\n/g, '\n') : Buffer.from(value, 'base64').toString('utf8');
  const key = crypto.createPrivateKey(pem);
  if (key.asymmetricKeyType !== 'ec' || key.asymmetricKeyDetails.namedCurve !== 'prime256v1')
    throw new Error('App Store Connect requires an EC P-256 key');
  return key;
}
function jwt(key, keyId, issuer, now = Math.floor(Date.now() / 1000)) {
  if (!/^[A-Z0-9]{10}$/.test(keyId || '') || issuer !== APP.issuer)
    throw new Error('The API key must belong to the expected Apple team');
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const message = `${encode({ alg: 'ES256', kid: keyId, typ: 'JWT' })}.${encode({ iss: issuer, iat: now, exp: now + 600, aud: 'appstoreconnect-v1' })}`;
  return `${message}.${crypto.sign('sha256', Buffer.from(message), { key, dsaEncoding: 'ieee-p1363' }).toString('base64url')}`;
}
function appleClient() {
  const key = privateKey(process.env.APP_STORE_CONNECT_PRIVATE_KEY);
  const keyId = process.env.APP_STORE_CONNECT_KEY_ID;
  const issuer = process.env.APP_STORE_CONNECT_ISSUER_ID;
  return { key, keyId, issuer, get: async endpoint => {
    // Only allow read-only requests against Apple's documented API host.
    const url = new URL(endpoint, 'https://api.appstoreconnect.apple.com');
    if (url.origin !== 'https://api.appstoreconnect.apple.com') throw new Error('Unexpected Apple API host');
    const response = await fetch(url, { headers: { Authorization: `Bearer ${jwt(key, keyId, issuer)}` },
      signal: AbortSignal.timeout(60000) });
    const body = await response.json();
    if (!response.ok) {
      const codes = (body.errors || []).map(e => String(e.code).replace(/[^A-Z0-9_.-]/g, '')).join(',');
      throw new Error(`Apple API HTTP ${response.status} (${codes || 'unknown'})`);
    }
    return body;
  } };
}
function query(endpoint, params) {
  return `${endpoint}?${new URLSearchParams(params)}`;
}
async function preflight() {
  const number = buildNumber(process.env.BUILD_NUMBER);
  const client = appleClient();
  const app = (await client.get(`/v1/apps/${APP.id}`)).data;
  if (app.id !== APP.id || app.attributes.bundleId !== APP.bundle) throw new Error('Wrong App Store Connect application');
  const builds = (await client.get(query('/v1/builds', { 'filter[app]': APP.id, sort: '-uploadedDate', limit: '200' }))).data;
  if (builds.some(b => b.attributes.version === number)) throw new Error(`Build ${number} is already uploaded`);
  const highest = Math.max(53, ...builds.map(b => Number(b.attributes.version)).filter(Number.isInteger));
  if (Number(number) <= highest) throw new Error(`Requested build must be higher than the existing maximum (${highest})`);
  console.log(`Apple API authenticated. App: ${APP.bundle}; highest recent build: ${highest}; requested: ${APP.version} (${number}).`);
  const bundleIds = (await client.get(query('/v1/bundleIds', { 'filter[identifier]': APP.bundle,
    'fields[bundleIds]': 'identifier,seedId,platform' }))).data.filter(b =>
    b.attributes.identifier === APP.bundle && ['IOS', 'UNIVERSAL'].includes(b.attributes.platform));
  console.log(`Current bundle metadata: ${JSON.stringify(bundleIds.map(b => ({ id: b.id,
    identifier: b.attributes.identifier, prefix: b.attributes.seedId, platform: b.attributes.platform })))}`);
  if (bundleIds.length !== 1 || bundleIds[0].attributes.seedId !== APP.team) throw new Error('Unexpected bundle identifier/team');
  const capabilities = (await client.get(`/v1/bundleIds/${bundleIds[0].id}/bundleIdCapabilities`)).data;
  const types = new Set(capabilities.map(c => c.attributes.capabilityType));
  for (const type of ['IN_APP_PURCHASE', 'PUSH_NOTIFICATIONS', 'APPLE_ID_AUTH'])
    if (!types.has(type)) throw new Error(`Missing required capability: ${type}`);
  const certs = (await client.get('/v1/certificates?limit=200')).data;
  console.log('Available certificate metadata (private keys are not returned by this API):');
  for (const cert of certs) console.log(`  ${cert.attributes.certificateType}; expires ${cert.attributes.expirationDate}`);
  const profiles = (await client.get('/v1/profiles?limit=200&include=bundleId')).data;
  const matching = profiles.filter(p => p.relationships?.bundleId?.data?.id === bundleIds[0].id);
  console.log(`Manually visible profiles for the current bundle ID: ${matching.length}. Old-app profiles will not be used.`);
  const dir = releaseDir();
  fs.mkdirSync(path.join(dir, 'private_keys'), { recursive: true, mode: 0o700 });
  const keyPath = path.join(dir, 'private_keys', `AuthKey_${client.keyId}.p8`);
  fs.writeFileSync(keyPath, client.key.export({ type: 'pkcs8', format: 'pem' }), { mode: 0o600 });
  fs.writeFileSync(path.join(dir, 'manifest.json'), JSON.stringify({ ...APP, build: number,
    source: process.env.GITHUB_SHA, run: process.env.GITHUB_RUN_ID, createdAt: new Date().toISOString() }, null, 2));
  const exportOptions = { method: 'app-store-connect', destination: 'export', signingStyle: 'automatic',
    teamID: APP.team, manageAppVersionAndBuildNumber: false, uploadSymbols: true };
  fs.writeFileSync(path.join(dir, 'ExportOptions.plist'), JSON.stringify(exportOptions));
  execFileSync('plutil', ['-convert', 'xml1', path.join(dir, 'ExportOptions.plist')]);
  if (process.env.GITHUB_OUTPUT) fs.appendFileSync(process.env.GITHUB_OUTPUT,
    `key_path=${keyPath}\nrelease_dir=${dir}\n`);
}
async function gate() {
  if (process.env.GITHUB_REF !== 'refs/heads/main' || process.env.GITHUB_EVENT_NAME !== 'workflow_dispatch')
    throw new Error('Releases must be manually dispatched from main');
  const sha = process.env.GITHUB_SHA;
  if (!/^[0-9a-f]{40}$/.test(sha || '')) throw new Error('Invalid source SHA');
  const repo = process.env.GITHUB_REPOSITORY;
  if (repo !== 'emirrkls/Pusula-SaaS-Ecosystem') throw new Error('Unexpected repository');
  const response = await fetch(`https://api.github.com/repos/${repo}/actions/workflows/ios-validation.yml/runs?branch=main&status=success&per_page=30`,
    { headers: { Authorization: `Bearer ${process.env.GH_TOKEN}`, Accept: 'application/vnd.github+json' }, signal: AbortSignal.timeout(30000) });
  if (!response.ok) throw new Error(`Could not verify the iOS UI gate: HTTP ${response.status}`);
  const { workflow_runs: runs } = await response.json();
  for (const run of runs) {
    if (!/^[0-9a-f]{40}$/.test(run.head_sha)) continue;
    try { execFileSync('git', ['merge-base', '--is-ancestor', run.head_sha, sha], { stdio: 'pipe' }); } catch { continue; }
    const changed = execFileSync('git', ['diff', '--name-only', run.head_sha, sha, '--', 'frontend-appstore',
      'scripts/test-ios-auth-contract.cjs', 'scripts/test-ios-release-contract.cjs', '.github/workflows/ios-validation.yml'], { encoding: 'utf8' }).trim();
    if (!changed) {
      console.log(`iPhone/iPad UI gate verified: ${run.html_url}; tested source ${run.head_sha}.`);
      return;
    }
  }
  throw new Error('Current iOS source has no passing compile/iPhone/iPad UI gate. Run iOS Compile Gate first.');
}
async function waitForProcessing() {
  const number = buildNumber(process.env.BUILD_NUMBER);
  const client = appleClient();
  for (let attempt = 0; attempt < 40; attempt++) {
    const builds = (await client.get(query('/v1/builds', { 'filter[app]': APP.id, 'filter[version]': number,
      include: 'preReleaseVersion', limit: '10' })));
    const build = builds.data.find(b => b.attributes.version === number);
    if (build) {
      const version = builds.included?.find(v => v.id === build.relationships.preReleaseVersion?.data?.id);
      if (version?.attributes.version !== APP.version) throw new Error('Apple processed an unexpected marketing version');
      const state = build.attributes.processingState;
      console.log(`Apple processing ${APP.version} (${number}): ${state}`);
      if (state === 'VALID') {
        const result = { ...APP, build: number, appleBuildId: build.id, processing: state,
          source: process.env.GITHUB_SHA, run: process.env.GITHUB_RUN_ID };
        fs.writeFileSync(path.join(releaseDir(), 'upload-result.json'), JSON.stringify(result, null, 2));
        if (process.env.GITHUB_STEP_SUMMARY) fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY,
          `\nApple processing completed: **${APP.version} (${number})**, bundle \`${APP.bundle}\`.\n\nSource: \`${process.env.GITHUB_SHA}\`. No App Review submission or publication was performed.\n`);
        return;
      }
      if (['FAILED', 'INVALID'].includes(state)) throw new Error(`Apple rejected processing: ${state}`);
    } else console.log(`Waiting for uploaded build ${number} to appear (${attempt + 1}/40).`);
    await new Promise(resolve => setTimeout(resolve, 30000));
  }
  throw new Error('Upload processing is not yet confirmed. Check App Store Connect before retrying; do not blindly re-upload.');
}
function encryptionKey(key, salt) {
  return Buffer.from(crypto.hkdfSync('sha256', key.export({ type: 'pkcs8', format: 'der' }), salt,
    Buffer.from('Pusula iOS archive backup v1'), 32));
}
async function encryptBackup() {
  const dir = releaseDir();
  if (!fs.existsSync(path.join(dir, 'PusulaService.xcarchive'))) return;
  const key = privateKey(process.env.APP_STORE_CONNECT_PRIVATE_KEY);
  const files = ['PusulaService.xcarchive', 'manifest.json'];
  for (const name of ['export', 'archive.log', 'export.log', 'upload.log', 'upload-result.json', 'export-verification.json'])
    if (fs.existsSync(path.join(dir, name))) files.push(name);
  const tarPath = path.join(dir, 'backup.tar.gz');
  execFileSync('tar', ['-czf', tarPath, '-C', dir, ...files]);
  const salt = crypto.randomBytes(32), iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', encryptionKey(key, salt), iv);
  const output = path.join(dir, 'backup.pusula.enc');
  // Layout: magic(8) | HKDF salt(32) | GCM IV(12) | ciphertext | GCM tag(16).
  fs.writeFileSync(output, Buffer.concat([Buffer.from('PUSULA01'), salt, iv]), { mode: 0o600 });
  await pipeline(fs.createReadStream(tarPath), cipher, fs.createWriteStream(output, { flags: 'a' }));
  fs.appendFileSync(output, cipher.getAuthTag());
  fs.unlinkSync(tarPath);
  console.log('Archive, dSYMs and diagnostics encrypted with AES-256-GCM. No signing key or keychain is included.');
}
async function decryptBackup(input, output, keyPath) {
  if (!input || !output || !keyPath || fs.existsSync(output)) throw new Error('Supply input, NEW output tar.gz path, and the original ASC private-key path');
  const stat = fs.statSync(input);
  if (stat.size < 68) throw new Error('Invalid archive backup');
  const fd = fs.openSync(input, 'r');
  const header = Buffer.alloc(52), tag = Buffer.alloc(16);
  try { fs.readSync(fd, header, 0, 52, 0); fs.readSync(fd, tag, 0, 16, stat.size - 16); } finally { fs.closeSync(fd); }
  if (header.subarray(0, 8).toString() !== 'PUSULA01') throw new Error('Invalid archive header');
  const key = privateKey(fs.readFileSync(keyPath, 'utf8'));
  const decipher = crypto.createDecipheriv('aes-256-gcm', encryptionKey(key, header.subarray(8, 40)), header.subarray(40, 52));
  decipher.setAuthTag(tag);
  try {
    await pipeline(fs.createReadStream(input, { start: 52, end: stat.size - 17 }), decipher,
      fs.createWriteStream(output, { flags: 'wx', mode: 0o600 }));
  } catch (error) { if (fs.existsSync(output)) fs.unlinkSync(output); throw error; }
}
if (require.main === module) {
  const commands = { preflight, gate, wait: waitForProcessing, encrypt: encryptBackup,
    decrypt: () => decryptBackup(...process.argv.slice(3)) };
  Promise.resolve().then(() => {
    const command = commands[process.argv[2]];
    if (!command) throw new Error('Choose preflight, gate, wait, encrypt, or decrypt');
    return command();
  }).catch(error => { console.error(error.message); process.exitCode = 1; });
}
module.exports = { APP, buildNumber, privateKey, jwt, query, encryptionKey, decryptBackup };

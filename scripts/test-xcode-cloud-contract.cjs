const test = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const script = path.resolve(__dirname, '../frontend-appstore/ci_scripts/ci_pre_xcodebuild.sh');
const shell = process.platform === 'win32'
  ? path.join(process.env.ProgramFiles || 'C:/Program Files', 'Git/bin/bash.exe')
  : '/bin/sh';

function run(action) {
  const env = { ...process.env, CI_XCODEBUILD_ACTION: action };
  delete env.CI_PRIMARY_REPOSITORY_PATH;
  return spawnSync(shell, [script], { env, encoding: 'utf8', timeout: 10000 });
}

test('Cloud preparation script has valid shell syntax', () => {
  const result = spawnSync(shell, ['-n', script], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
});

test('artifact-only simulator workers proceed to the compiled UI tests without a checkout', () => {
  const result = run('test-without-building');
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /running compiled device tests/);
});

for (const action of ['build-for-testing', 'archive', '']) {
  test(`source action ${action || 'unknown'} fails closed without a source checkout`, () => {
    const result = run(action);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /Xcode Cloud repository path is required/);
  });
}

test('source actions retain full Swift policy and timezone gates', () => {
  const source = fs.readFileSync(script, 'utf8');
  assert.match(source, /swift test --package-path "\$package_path"/);
  assert.match(source, /UTC Europe\/Istanbul America\/Los_Angeles/);
  assert.match(source, /BusinessDateParserTests\|TicketListTests/);
});

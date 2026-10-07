import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const html = fs.readFileSync(new URL('../dist/privacy/index.html', import.meta.url), 'utf8');
test('privacy is prerendered and covers the actual app lifecycle', () => {
  for (const text of ['7 Ekim 2026', 'Google ve Apple ile giriş', 'Hesabımı nasıl silerim?',
    'Hesabı silmek Apple aboneliğini otomatik iptal etmez.', 'saklama', 'fotoğraf',
    'şifreli', 'WhatsApp', '30 gün', 'silme kuyruğuyla', 'ortak cari']) assert.ok(html.includes(text), text);
  assert.ok(html.includes('https://apps.apple.com/account/subscriptions/'));
  assert.ok(html.includes('mailto:pusulaiklimlendirme.didim@gmail.com'));
  assert.ok(!html.includes('30 Nisan 2026'));
});

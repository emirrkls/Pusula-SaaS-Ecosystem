const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const canonical = fs.readFileSync(path.join(root, 'guides/mobile-guide.json'));
const targets = [
  'frontend-appstore/PusulaService/Core/Guide/app-guide.json',
  'frontend-playstore/PusulaService/app/src/main/assets/app-guide.json'
];
for (const target of targets) {
  const absolute = path.join(root, target);
  if (process.argv.includes('--write')) fs.writeFileSync(absolute, canonical);
  else if (!fs.existsSync(absolute) || !fs.readFileSync(absolute).equals(canonical)) {
    throw new Error('Guide catalog is out of sync: ' + target + '. Run node scripts/sync-mobile-guide.cjs --write.');
  }
}
console.log('Mobile guide catalogs match.');

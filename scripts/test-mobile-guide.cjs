const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const read = f => fs.readFileSync(path.join(root, f), 'utf8');
const catalog = JSON.parse(read('guides/mobile-guide.json'));
const ios = 'frontend-appstore/PusulaService/';
const android = 'frontend-playstore/PusulaService/app/src/main/';
function files(dir) { return fs.readdirSync(path.join(root,dir),{withFileTypes:true}).flatMap(e=>e.isDirectory()?files(dir+'/'+e.name):[dir+'/'+e.name]); }
test('native catalogs are byte-identical to canonical source', () => execFileSync(process.execPath, ['scripts/sync-mobile-guide.cjs', '--check'], {cwd:root}));
test('complete catalog, valid roles, categories, locations and short lessons', () => {
  assert.equal(catalog.schemaVersion,1); assert.equal(catalog.categories.length,10); assert.equal(catalog.topics.length,56);
  assert.equal(new Set(catalog.topics.map(t=>t.id)).size,56);
  for(const t of catalog.topics) {
    assert.ok(catalog.categories.some(c=>c.id===t.category),t.id);
    assert.ok(t.steps.length>=2 && t.steps.length<=6,t.id);
    assert.ok(t.roles.length && t.roles.every(r=>['COMPANY_ADMIN','SUPER_ADMIN','TECHNICIAN'].includes(r)));
    for(const p of t.platforms) { assert.ok(t.locations[p],t.id); assert.ok(t.sources[p]?.length,t.id); }
    for(const step of t.steps) assert.ok(step.title && step.body);
  }
});
test('every public mobile feature screen is mapped or explicitly a container', () => {
  const containers = {
    ios: ['ContentView.swift','LoginView.swift','RegisterView.swift','GuideCenterView.swift','ReadLoadFailureView.swift','SupportAttachmentPicker.swift','PhotoPicker.swift','CameraPicker.swift','SignatureCanvas.swift','PDFKitView.swift','SupportEntryView.swift'],
    android: ['LoginScreen.kt','RegisterScreen.kt','GuideCenterScreen.kt','SupportEntry.kt','SupportModels.kt','SupportRepository.kt','SupportViewModel.kt','SupportCenterScreen.kt']
  };
  const roots = {ios:ios+'Views',android:android+'java/com/pusula/service/ui'};
  for(const platform of ['ios','android']) {
    const covered = new Set(catalog.topics.flatMap(t=>t.sources?.[platform]||[]));
    const candidates = files(roots[platform]).filter(f=>platform==='ios'?f.endsWith('View.swift'):f.endsWith('Screen.kt'));
    const missing = candidates.map(f=>path.basename(f)).filter(f=>!covered.has(f)&&!containers[platform].includes(f));
    assert.deepEqual(missing,[],platform+' guide coverage missing');
  }
});
test('both clients use the actual app icon bytes', () => {
  const icon=fs.readFileSync(path.join(root,ios+'Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png'));
  for(const f of [ios+'Assets.xcassets/AppBrandIcon.imageset/AppBrandIcon.png',android+'res/drawable-nodpi/pusula_app_icon.png']) assert.ok(fs.readFileSync(path.join(root,f)).equals(icon));
  assert.match(read(ios+'Core/PusulaDesignSystem.swift'),/Image\("AppBrandIcon"\)/);
  assert.match(read(android+'AndroidManifest.xml'),/android:icon="@drawable\/pusula_app_icon"/);
});
test('guide is wired into actual account menus and has no remote action API', () => {
  for(const f of ['Views/Admin/SettingsView.swift','Views/Shared/ProfileView.swift']) assert.match(read(ios+f),/NavigationLink \{ GuideCenterDestination\(\) \}/);
  for(const f of ['settings/SettingsScreen.kt','profile/ProfileScreen.kt']) assert.match(read(android+'java/com/pusula/service/ui/'+f),/ApplicationGuideEntry\(session\)/);
  for(const f of [ios+'Views/Shared/GuideCenterView.swift',android+'java/com/pusula/service/ui/guide/GuideCenterScreen.kt']) assert.doesNotMatch(read(f),/URLSession|ApiService|Retrofit|StoreKitManager|launchUrl|startActivity|sendMessage/);
});
test('fixture only runs in DEBUG simulator and cannot sync production onboarding', () => {
  const fixture=read(ios+'ScreenshotSupport/GuideFlowFixture.swift');
  assert.match(fixture,/#if DEBUG/);assert.match(fixture,/#if targetEnvironment\(simulator\)/);
  assert.match(fixture,/AppStoreScreenshotMode.isEnabled/);assert.match(fixture,/syncCompletion: \{ _ in \}/);
  assert.doesNotMatch(fixture,/SessionManager.shared.configure|KeychainHelper|AuthService/);
});
test('old Continue footer is fixed and coach layout no longer estimates card height', () => {
  const source=read(ios+'Core/Onboarding.swift');
  assert.match(source,/safeAreaInset\(edge: .bottom\)/);
  assert.match(source,/if cursor.advance\(\) \{ onboarding.completeIntro\(\) \}/);
  assert.doesNotMatch(source,/estimatedHalfHeight|TabView\(selection: \$page\)/);
  assert.match(source,/activeSteps = steps\(for: role\).filter/);
});
test('iOS lessons use destination navigation and large-text tests never swipe the fixed footer', () => {
  const screen=read(ios+'Views/Shared/GuideCenterView.swift');
  assert.match(screen,/navigationDestination\(isPresented: \$isLessonPresented\)/);
  assert.doesNotMatch(screen,/\.sheet\(item: \$selectedTopic\)/);
  assert.match(screen,/private func open\(_ topic: GuideTopic\)[\s\S]*PusulaKeyboard.dismiss\(\)/);
  assert.match(screen,/\.contentShape\(Rectangle\(\)\)/);
  assert.match(screen,/@FocusState private var searchFocused/);
  assert.match(screen,/\.accessibilityIdentifier\("guide.search"\)/);
  assert.match(screen,/searchFocused = false/);
  assert.doesNotMatch(screen,/\.searchable\(/);
  assert.match(screen,/matches.filter \{ \$0.titleMatches\(search\) \}/);
  const ui=read('frontend-appstore/PusulaServiceScreenshots/GuideFlowTests.swift');
  assert.match(ui,/if needsReveal/);
  assert.match(ui,/for iteration in 0\.\.<3/);
  assert.match(ui,/next.frame.maxY, screen.maxY/);
  assert.match(ui,/Guide failure accessibility hierarchy/);
  assert.match(ui,/app.textFields\["guide.search"\]/);
  assert.match(ui,/app.scrollViews\["guide.catalog"\].swipeUp\(\)/);
});
test('Android guide uses the system-inset-aware sheet and a separately fixed footer', () => {
  const screen=read(android+'java/com/pusula/service/ui/guide/GuideCenterScreen.kt');
  assert.match(screen,/ModalBottomSheet/);
  assert.match(screen,/skipPartiallyExpanded = true/);
  assert.match(screen,/WindowInsets.safeDrawing/);
  assert.match(screen,/bottomBar =/);
  assert.match(read(android.replace('src/main/','src/androidTest/')+'java/com/pusula/service/guide/GuideFlowInstrumentedTest.kt'),/next.bottom <= screen.bottom/);
});

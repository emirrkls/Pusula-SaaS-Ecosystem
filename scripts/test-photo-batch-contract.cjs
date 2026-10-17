const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const read = f => fs.readFileSync(path.join(__dirname, '..', f), 'utf8');
const ios = 'frontend-appstore/PusulaService/';
const android = 'frontend-playstore/PusulaService/app/src/main/java/com/pusula/service/';

test('native galleries use a bounded multi-image picker and preserve the camera route', () => {
  const swift = read(ios + 'Views/Technician/ServicePhotoView.swift');
  assert.match(swift, /selectedLibraryItems: \[PhotosPickerItem\]/);
  assert.match(swift, /maxSelectionCount: 10, selectionBehavior: .ordered, matching: .images/);
  assert.match(swift, /beginUpload\(\[.camera\(image\)\]\)/);
  const kotlin = read(android + 'ui/technician/ServicePhotoScreen.kt');
  assert.match(kotlin, /PickMultipleVisualMedia\(10\)/);
  assert.match(kotlin, /ActivityResultContracts.TakePicture\(\)/);
  assert.match(kotlin, /ImageUploadHelper.prepareForUpload\(appContext, selected\)/);
});
test('both native uploaders use the existing server idempotency contract', () => {
  assert.match(read(ios + 'Services/TicketService.swift'), /reliable-mutations\/tickets\/.*photos/);
  const api = read(android + 'data/remote/ApiService.kt');
  assert.match(api, /@POST\("\/api\/reliable-mutations\/tickets\/\{id\}\/photos"\)/);
  assert.match(api, /@Header\("Idempotency-Key"\) key: String/);
  const vm = read(android + 'ui/technician/TicketViewModel.kt');
  assert.match(vm, /for \(id in pending\)/);
  assert.match(vm, /repository.uploadServicePhoto\(ticketId, id, requireNotNull\(owner.token\), batch.type, batch.note, part\)/);
  assert.match(vm, /photoBatch = photoBatch\?\.prepare\(id, prepared\)/);
  assert.match(vm, /photoBatch = photoBatch\?\.succeed\(id\)/);
  assert.match(vm, /if \(generation == photoGeneration\)/);
});
test('failed batches retain intent, cannot silently leave, and pin account credentials', () => {
  const swift = read(ios + 'Views/Technician/ServicePhotoView.swift');
  assert.match(swift, /interactiveDismissDisabled\(isUploading \|\| hasPendingPhotos\)/);
  assert.match(swift, /photoOwnerIsCurrent\(owner\)/);
  assert.match(swift, /currentPhotoOwner == owner/);
  assert.match(swift, /requestId: id, authToken: owner.token/);
  assert.match(swift, /kCGImageSourceThumbnailMaxPixelSize: 2_048/);
  assert.match(swift, /\.alert\(showLeaveWarning \?/);
  assert.match(swift, /Button\("Burada Kal", role: \.cancel\)/);
  assert.doesNotMatch(swift, /confirmationDialog\("Yüklenemeyen/);
  const screen = read(android + 'ui/technician/ServicePhotoScreen.kt');
  assert.match(screen, /BackHandler\(onBack = leave\)/);
  assert.match(screen, /Bekleyenleri Bırak ve Çık/);
  const auth = read(android + 'data/remote/AuthInterceptor.kt');
  assert.match(auth, /if \(pinned != null && pinned != token\?\.let/);
  assert.match(auth, /if \(pinned == null && !token.isNullOrBlank\(\)\)/);
});

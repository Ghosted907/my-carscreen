import assert from "node:assert/strict";
import { test } from "node:test";
import {
  canInstallFromDist,
  compareVersion,
  fetchVerifiedApk,
  isAllowedPostInstall,
  isHostedReleaseApk,
  isSignatureMismatch,
  parsePackageDump,
  sha256Hex,
  type DistApp,
} from "./dist-apps.ts";

const release: DistApp = {
  id: "t1launcher",
  name: "T1",
  packageName: "com.ghosted907.t1launcher",
  versionCode: 6,
  versionName: "1.5.0",
  apk: "https://raw.githubusercontent.com/Ghosted907/my-carscreen/dist/t1launcher-release.apk",
  sha256: "ab".repeat(32),
  size: 4,
  external: false,
  postInstall: [],
  notes: "",
};

test("dumpsys versionCode and system flag", () => {
  const dump = `
    pkgFlags=[ SYSTEM HAS_CODE ]
    versionCode=6 minSdk=30 targetSdk=30
    versionName=1.5.0
  `;
  const parsed = parsePackageDump(dump);
  assert.equal(parsed.installed, true);
  assert.equal(parsed.versionCode, 6);
  assert.equal(parsed.versionName, "1.5.0");
  assert.equal(parsed.system, true);
  assert.equal(compareVersion(parsed, 7), "system");
});

test("missing package and newer remote version", () => {
  assert.equal(parsePackageDump("Unable to find package: com.ghosted907.t1launcher").installed, false);
  const installed = parsePackageDump("versionCode=5 minSdk=30\nversionName=1.4.0\nflags=[ HAS_CODE ]");
  assert.equal(compareVersion(installed, 6), "older");
  assert.equal(compareVersion(installed, 5), "current");
  assert.equal(compareVersion(installed, 4), "newer");
});

test("only hosted release APKs and own packages can be installed", () => {
  assert.equal(canInstallFromDist(release), true);
  assert.equal(canInstallFromDist({ ...release, external: true }), false);
  assert.equal(canInstallFromDist({ ...release, packageName: "com.android.settings" }), false);
  assert.equal(isHostedReleaseApk("https://example.com/app.apk"), false);
});

test("post-install allowlist rejects vehicle writes and other packages", () => {
  const pkg = "com.ghosted907.t1launcher";
  assert.equal(
    isAllowedPostInstall("appops set com.ghosted907.t1launcher SYSTEM_ALERT_WINDOW allow", pkg),
    true,
  );
  assert.equal(isAllowedPostInstall("settings put global enable_freeform_support 1", pkg), true);
  assert.equal(isAllowedPostInstall("settings put system cluster_theme 1", pkg), false);
  assert.equal(isAllowedPostInstall("pm uninstall --user 0 com.android.settings", pkg), false);
  assert.equal(
    isAllowedPostInstall("appops set com.android.settings SYSTEM_ALERT_WINDOW allow", pkg),
    false,
  );
});

test("signature mismatch is recognized and a bad hash is refused", async () => {
  assert.equal(isSignatureMismatch("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]"), true);
  assert.equal(isSignatureMismatch("network down"), false);
  const bytes = new Uint8Array([1, 2, 3, 4]);
  const digest = await sha256Hex(bytes);
  const original = globalThis.fetch;
  globalThis.fetch = async () => new Response(bytes, { status: 200 });
  try {
    await assert.rejects(
      () => fetchVerifiedApk({ ...release, sha256: "0".repeat(64), size: 4 }),
      /ما تطابق apps.json/,
    );
    const file = await fetchVerifiedApk({ ...release, sha256: digest, size: 4 });
    assert.deepEqual(file, bytes);
  } finally {
    globalThis.fetch = original;
  }
});

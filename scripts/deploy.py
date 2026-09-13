"""Publish generated dist/ to the dedicated repo branch in GitHub Actions."""
import gzip
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import hashlib

import index_pb2 as pb

root = Path(__file__).resolve().parents[1]
repository = os.environ["GITHUB_REPOSITORY"]
ios_upstream_only = os.environ.get("IOS_UPSTREAM_ONLY") == "true"
if not os.environ.get("GH_TOKEN"):
    raise SystemExit("GH_TOKEN is required; run this script in GitHub Actions")

with tempfile.TemporaryDirectory() as temp:
    target = Path(temp)

    def git(*args, check=True):
        return subprocess.run(["git", *args], cwd=target, check=check, capture_output=True, text=True)

    git("init", "--initial-branch=repo")
    git("remote", "add", "origin", f"https://github.com/{repository}.git")
    git("config", "credential.helper", '!f() { echo username=x-access-token; echo "password=$GH_TOKEN"; }; f')
    git("config", "user.name", "github-actions[bot]")
    git("config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com")
    refs = git("ls-remote", "--heads", "origin", "repo").stdout
    if refs.strip():
        git("fetch", "--depth=1", "origin", "repo")
        git("checkout", "-B", "repo", "FETCH_HEAD")
        previous = pb.Index.FromString(gzip.decompress((target / "index.pb").read_bytes()))
        current = pb.Index.FromString(gzip.decompress((root / "dist/index.pb").read_bytes()))
        legacy = json.loads((root / "dist/index.min.json").read_text(encoding="utf-8"))
        repo_metadata = json.loads((root / "dist/repo.json").read_text(encoding="utf-8"))
        if len(legacy) != 1 or legacy[0]["pkg"] != "eu.kanade.tachiyomi.extension.ko.toonkor":
            raise SystemExit("Generated legacy index is not a Toonkor-only repository")
        if len(previous.extensionList.extensions) != 1 or previous.extensionList.extensions[0].packageName != "eu.kanade.tachiyomi.extension.ko.toonkor":
            raise SystemExit("Existing repo branch is not a Toonkor-only repository")
        if ios_upstream_only:
            if (target / "index.pb").read_bytes() != (root / "dist/index.pb").read_bytes():
                raise SystemExit("Android index.pb must remain byte-for-byte unchanged")
            record = json.loads((root / "prebuilt/upstream.json").read_text(encoding="utf-8"))
            expected_apk = Path(record["extension"]["resources"]["apkUrl"]).name
            ios_apk = root / "dist/apk" / expected_apk
            digest = hashlib.sha256(ios_apk.read_bytes()).hexdigest()
            if digest != record["apkSha256"]:
                raise SystemExit("iOS APK does not match the original Keiyoushi APK")
            if legacy[0]["version"] != record["extension"]["versionName"] or legacy[0]["apk"] != expected_apk:
                raise SystemExit("iOS index does not point to the original Keiyoushi APK")
            if repo_metadata.get("meta", {}).get("signingKeyFingerprint") != record["signingKey"]:
                raise SystemExit("iOS repository signing fingerprint is not the Keiyoushi fingerprint")
        else:
            current_ext = current.extensionList.extensions[0]
            if (legacy[0]["version"] != current_ext.versionName or
                    legacy[0]["apk"] != Path(current_ext.resources.apkUrl).name):
                raise SystemExit("Legacy index does not match the Mihon index")
            if repo_metadata.get("meta", {}).get("signingKeyFingerprint") != current.signingKey:
                raise SystemExit("Repository metadata signing fingerprint does not match the Mihon index")
            if previous.signingKey != current.signingKey and os.environ.get("ALLOW_SIGNER_CHANGE") != "true":
                raise SystemExit("Signing key changed. For the one-time personal-key transition, run manually with allow_signer_change enabled")
            if current.extensionList.extensions[0].versionCode < previous.extensionList.extensions[0].versionCode:
                raise SystemExit("Refusing to publish an older version")
            if previous.signingKey == current.signingKey and current.extensionList.extensions[0].versionCode == previous.extensionList.extensions[0].versionCode:
                old_apks = list((target / "apk").glob("*.apk"))
                new_apks = list((root / "dist/apk").glob("*.apk"))
                if len(old_apks) != 1 or old_apks[0].read_bytes() != new_apks[0].read_bytes():
                    raise SystemExit("APK changed without a versionCode increase")
        allowed = {
            "index.pb", "index.json", "index.min.json", "repo.json", "LICENSE",
            "icon/toonkor.png", "icon/eu.kanade.tachiyomi.extension.ko.toonkor.png",
        }
        for name in git("ls-files").stdout.splitlines():
            if name not in allowed and not (name.startswith("apk/") and name.endswith(".apk") and name.count("/") == 1):
                raise SystemExit(f"Unexpected file on repo branch: {name}")
        for old in (target / "apk").glob("*.apk"):
            old.unlink()
    shutil.copytree(root / "dist", target, dirs_exist_ok=True)
    git("add", "--all")
    if git("status", "--porcelain").stdout.strip():
        git("commit", "-m", "Publish Toonkor")
        git("push", "origin", "HEAD:repo")
    print(f"https://raw.githubusercontent.com/{repository}/repo/index.pb")
    print(f"https://raw.githubusercontent.com/{repository}/repo/index.min.json")

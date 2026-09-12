"""Publish exactly one Toonkor APK with Mihon and Tachimanga indexes."""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

import index_pb2 as pb

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "eu.kanade.tachiyomi.extension.ko.toonkor"


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(tool, *args):
    result = subprocess.run([str(tool), *map(str, args)], check=True, capture_output=True, text=True)
    return result.stdout


def certificate(apksigner, apk):
    output = run(apksigner, "verify", "--print-certs", apk)
    fingerprints = set(re.findall(r"certificate SHA-256 digest: ([0-9a-fA-F]{64})", output))
    if len(fingerprints) != 1:
        raise ValueError("Expected one signing certificate")
    return fingerprints.pop().lower()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY"))
    parser.add_argument("--mode", choices=["upstream", "built"], default="upstream")
    parser.add_argument("--apksigner", type=Path)
    parser.add_argument("--aapt", type=Path)
    args = parser.parse_args()
    if not args.repository or not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repository):
        parser.error("Pass --repository OWNER/REPO, or run in GitHub Actions")
    if args.mode == "upstream":
        record = json.loads((ROOT / "prebuilt/upstream.json").read_text(encoding="utf-8"))
        ext = record["extension"]
        apk = ROOT / "prebuilt" / ext["resources"]["apkUrl"].rsplit("/", 1)[1]
        if sha256(apk) != record["apkSha256"]:
            raise ValueError("Original APK SHA-256 does not match upstream snapshot")
        signing_key = record["signingKey"]
        if args.apksigner and certificate(args.apksigner, apk) != signing_key:
            raise ValueError("Original signing certificate mismatch")
    else:
        if not args.apksigner or not args.aapt:
            parser.error("built mode requires --apksigner and --aapt")
        build = ROOT / "src/ko/toonkor/build"
        info = json.loads((build / "keiyoushi-source-info.json").read_text(encoding="utf-8"))
        apks = list((build / "outputs/apk/release").glob("*.apk"))
        if len(apks) != 1:
            raise ValueError("Run a clean release build: expected exactly one APK")
        apk = apks[0]
        signing_key = certificate(args.apksigner, apk)
        if "Android Debug" in run(args.apksigner, "verify", "--print-certs", apk):
            raise ValueError("Refusing to publish a debug-signed release. Configure signingkey.jks")
        badging = run(args.aapt, "dump", "badging", apk)
        actual = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
        expected = (info["packageName"], str(info["versionCode"]), info["versionName"])
        if not actual or actual.groups() != expected:
            raise ValueError("APK package/version does not match generated source metadata")
        ext = {
            "name": info["name"], "packageName": info["packageName"],
            "extensionLib": info["extensionLib"], "versionCode": info["versionCode"],
            "versionName": info["versionName"], "contentWarning": info["contentWarning"],
            "sources": [{"id": s["id"], "name": s["name"], "language": s["lang"],
                         "homeUrl": s["baseUrl"], "mirrorUrls": s.get("mirrorUrls", [])}
                        for s in info["sources"]],
        }
    if ext["packageName"] != PACKAGE or len(ext["sources"]) != 1:
        raise ValueError("This repository only publishes Toonkor")
    base = f"https://raw.githubusercontent.com/{args.repository}/repo"
    index = pb.Index(name="Toonkor Personal", badgeLabel="TK", signingKey=signing_key,
                     contact=pb.Contact(website=f"https://github.com/{args.repository}"))
    warning = ext["contentWarning"]
    entry = index.extensionList.extensions.add(
        name=ext["name"], packageName=PACKAGE, extensionLib=ext["extensionLib"],
        versionCode=int(ext["versionCode"]), versionName=ext["versionName"],
        contentWarning=pb.ContentWarning.Value(warning) if isinstance(warning, str) else warning,
        resources=pb.Resources(apkUrl=f"{base}/apk/{apk.name}", iconUrl=f"{base}/icon/toonkor.png"),
    )
    for source in ext["sources"]:
        entry.sources.add(id=int(source["id"]), name=source["name"], language=source["language"],
                          homeUrl=source["homeUrl"], mirrorUrls=source.get("mirrorUrls", []))
    payload = gzip.compress(index.SerializeToString(deterministic=True), mtime=0)
    decoded = pb.Index.FromString(gzip.decompress(payload))
    if decoded != index or len(decoded.extensionList.extensions) != 1:
        raise ValueError("Index round-trip validation failed")
    source = ext["sources"][0]
    warning_value = pb.ContentWarning.Value(warning) if isinstance(warning, str) else int(warning)
    legacy_index = [{
        "name": f"Tachiyomi: {ext['name']}",
        "pkg": PACKAGE,
        "apk": apk.name,
        "lang": source["language"],
        "code": int(ext["versionName"].rsplit(".", 1)[1]),
        "version": ext["versionName"],
        "nsfw": int(warning_value != pb.ContentWarning.Value("CONTENT_WARNING_SAFE")),
        "sources": [{
            "name": source["name"],
            "lang": source["language"],
            "id": str(source["id"]),
            "baseUrl": source["homeUrl"],
        }],
    }]
    legacy_payload = json.dumps(legacy_index, ensure_ascii=False, separators=(",", ":"))
    if json.loads(legacy_payload) != legacy_index or len(legacy_index) != 1:
        raise ValueError("Legacy index round-trip validation failed")
    out = ROOT / "dist"
    (out / "apk").mkdir(parents=True, exist_ok=True)
    (out / "icon").mkdir(exist_ok=True)
    for old in (out / "apk").glob("*.apk"):
        old.unlink()
    shutil.copy2(apk, out / "apk" / apk.name)
    shutil.copy2(ROOT / "src/ko/toonkor/res/mipmap-xhdpi/ic_launcher.png", out / "icon/toonkor.png")
    (out / "index.pb").write_bytes(payload)
    (out / "index.min.json").write_text(legacy_payload + "\n", encoding="utf-8")
    # Keep the source license with redistributed binaries.
    shutil.copy2(ROOT / "LICENSE", out / "LICENSE")
    print(f"Toonkor {entry.versionName}; APK SHA-256 {sha256(apk)}")
    print(f"Signing certificate SHA-256: {signing_key}")
    print(f"Mihon URL: {base}/index.pb")
    print(f"Tachimanga URL: {base}/index.min.json")


if __name__ == "__main__":
    main()

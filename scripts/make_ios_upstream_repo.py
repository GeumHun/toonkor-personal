"""Overlay the original Keiyoushi APK onto the existing Android repository."""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import shutil

import index_pb2 as pb

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "eu.kanade.tachiyomi.extension.ko.toonkor"


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY"))
    parser.add_argument("--existing", type=Path, required=True)
    args = parser.parse_args()
    if not args.repository or not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repository):
        parser.error("Pass --repository OWNER/REPO, or run in GitHub Actions")

    record = json.loads((ROOT / "prebuilt/upstream.json").read_text(encoding="utf-8"))
    ext = record["extension"]
    apk = ROOT / "prebuilt" / Path(ext["resources"]["apkUrl"]).name
    if sha256(apk) != record["apkSha256"]:
        raise ValueError("Original APK SHA-256 does not match upstream snapshot")
    if ext["packageName"] != PACKAGE or len(ext["sources"]) != 1:
        raise ValueError("The upstream snapshot must contain exactly one Toonkor source")

    existing_index_path = args.existing / "index.pb"
    existing_index = pb.Index.FromString(gzip.decompress(existing_index_path.read_bytes()))
    if len(existing_index.extensionList.extensions) != 1:
        raise ValueError("The Android index must contain exactly one extension")
    android_extension = existing_index.extensionList.extensions[0]
    if android_extension.packageName != PACKAGE:
        raise ValueError("The Android index is not a Toonkor-only repository")
    android_apk = args.existing / "apk" / Path(android_extension.resources.apkUrl).name
    if not android_apk.is_file():
        raise ValueError("The APK referenced by the Android index is missing")

    out = ROOT / "dist"
    if out.exists():
        if out.resolve().parent != ROOT.resolve():
            raise ValueError("Refusing to replace a dist directory outside the repository")
        shutil.rmtree(out)
    shutil.copytree(args.existing, out, ignore=shutil.ignore_patterns(".git"))
    shutil.copy2(apk, out / "apk" / apk.name)

    source = ext["sources"][0]
    legacy_index = [{
        "name": f"Tachiyomi: {ext['name']}",
        "pkg": PACKAGE,
        "apk": apk.name,
        "lang": source["language"],
        "code": int(ext["versionName"].rsplit(".", 1)[1]),
        "version": ext["versionName"],
        "nsfw": int(ext["contentWarning"] != "CONTENT_WARNING_SAFE"),
        "sources": [{
            "name": source["name"],
            "lang": source["language"],
            "id": str(source["id"]),
            "baseUrl": source["homeUrl"],
            "versionId": 1,
            "hasCloudflare": 0,
        }],
    }]
    base = f"https://raw.githubusercontent.com/{args.repository}/repo"
    repo_metadata = {
        "index_v2": f"{base}/index.pb",
        "meta": {
            "name": "Toonkor Personal",
            "shortName": "TK",
            "website": f"https://github.com/{args.repository}",
            "signingKeyFingerprint": record["signingKey"],
        },
    }
    compact = json.dumps(legacy_index, ensure_ascii=False, separators=(",", ":"))
    (out / "index.min.json").write_text(compact + "\n", encoding="utf-8")
    (out / "index.json").write_text(
        json.dumps(legacy_index, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    (out / "repo.json").write_text(
        json.dumps(repo_metadata, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    if existing_index_path.read_bytes() != (out / "index.pb").read_bytes():
        raise ValueError("Android index.pb changed while preparing the iOS overlay")
    print(f"Android index preserved: Toonkor {android_extension.versionName}")
    print(f"iOS APK: {apk.name}; SHA-256 {sha256(apk)}")
    print(f"iOS signing certificate SHA-256: {record['signingKey']}")


if __name__ == "__main__":
    main()

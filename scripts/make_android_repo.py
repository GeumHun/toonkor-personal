"""Add the original DC Manga APK to the existing Mihon repository."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import shutil

import index_pb2 as pb

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "eu.kanade.tachiyomi.extension.ko.dcmanga"
TOONKOR = "eu.kanade.tachiyomi.extension.ko.toonkor"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--existing", type=Path, required=True)
    parser.add_argument("--repository", default="GeumHun/toonkor-personal")
    args = parser.parse_args()

    existing = args.existing.resolve()
    record = json.loads((ROOT / "prebuilt/dcmanga.json").read_text(encoding="utf-8"))
    apk = ROOT / "prebuilt" / record["apkFile"]
    if sha256(apk) != record["apkSha256"]:
        raise SystemExit("DC Manga APK SHA-256 differs from the uploaded original")

    old_payload = (existing / "index.pb").read_bytes()
    index = pb.Index.FromString(gzip.decompress(old_payload))
    old_toonkor = next((item for item in index.extensionList.extensions if item.packageName == TOONKOR), None)
    if old_toonkor is None:
        raise SystemExit("Existing Mihon index does not contain Toonkor")
    old_toonkor_bytes = old_toonkor.SerializeToString(deterministic=True)

    retained = [item for item in index.extensionList.extensions if item.packageName != PACKAGE]
    index.extensionList.ClearField("extensions")
    for item in retained:
        index.extensionList.extensions.add().CopyFrom(item)

    ext = record["extension"]
    base = f"https://raw.githubusercontent.com/{args.repository}/repo"
    entry = index.extensionList.extensions.add(
        name=ext["name"],
        packageName=ext["packageName"],
        extensionLib=ext["extensionLib"],
        versionCode=int(ext["versionCode"]),
        versionName=ext["versionName"],
        contentWarning=pb.ContentWarning.Value(ext["contentWarning"]),
        resources=pb.Resources(
            apkUrl=f"{base}/apk/{record['apkFile']}",
            iconUrl=f"{base}/icon/dcmanga.png",
        ),
    )
    for source in ext["sources"]:
        entry.sources.add(
            id=int(source["id"]),
            name=source["name"],
            language=source["language"],
            homeUrl=source["homeUrl"],
        )

    out = ROOT / "dist"
    if out.exists():
        shutil.rmtree(out)
    shutil.copytree(existing, out, ignore=shutil.ignore_patterns(".git"))
    (out / "apk").mkdir(exist_ok=True)
    (out / "icon").mkdir(exist_ok=True)
    for old in (out / "apk").glob("tachiyomi-ko.dcmanga-v*.apk"):
        old.unlink()
    shutil.copy2(apk, out / "apk" / record["apkFile"])
    shutil.copy2(ROOT / "prebuilt/dcmanga.png", out / "icon/dcmanga.png")
    shutil.copy2(ROOT / "prebuilt/dcmanga.png", out / "icon" / f"{PACKAGE}.png")

    payload = gzip.compress(index.SerializeToString(deterministic=True), mtime=0)
    decoded = pb.Index.FromString(gzip.decompress(payload))
    new_toonkor = next(item for item in decoded.extensionList.extensions if item.packageName == TOONKOR)
    if new_toonkor.SerializeToString(deterministic=True) != old_toonkor_bytes:
        raise SystemExit("Toonkor metadata changed while adding DC Manga")
    if {item.packageName for item in decoded.extensionList.extensions} != {TOONKOR, PACKAGE}:
        raise SystemExit("Mihon index must contain exactly Toonkor and DC Manga")
    (out / "index.pb").write_bytes(payload)

    # index.json is the readable legacy companion. Keep the existing Toonkor record intact.
    readable = json.loads((existing / "index.json").read_text(encoding="utf-8"))
    readable = [item for item in readable if item.get("pkg") != PACKAGE]
    readable.append({
        "name": "Tachiyomi: DC Manga",
        "pkg": PACKAGE,
        "apk": record["apkFile"],
        "lang": "ko",
        "code": int(ext["versionCode"]),
        "version": ext["versionName"],
        "nsfw": 1,
        "sources": [{
            "name": source["name"], "lang": source["language"], "id": source["id"],
            "baseUrl": source["homeUrl"], "versionId": 1, "hasCloudflare": 0,
        } for source in ext["sources"]],
    })
    (out / "index.json").write_text(
        json.dumps(readable, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    for protected in ("index.min.json", "repo.json"):
        if (out / protected).read_bytes() != (existing / protected).read_bytes():
            raise SystemExit(f"Protected iOS file changed: {protected}")
    if sha256(out / "apk" / record["apkFile"]) != record["apkSha256"]:
        raise SystemExit("Published DC Manga APK is not byte-identical to the uploaded original")
    print(f"DC Manga {entry.versionName}; APK SHA-256 {record['apkSha256']}")
    print(f"Mihon URL: {base}/index.pb")


if __name__ == "__main__":
    main()

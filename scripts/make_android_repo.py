"""Build the curated Android-only Mihon repository overlay."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import shutil

import index_pb2 as pb

ROOT = Path(__file__).resolve().parents[1]
TOONKOR = "eu.kanade.tachiyomi.extension.ko.toonkor"
GOODTOON = "eu.kanade.tachiyomi.extension.ko.goodtoonwebtoontest"
REMOVED_PACKAGES = {
    "eu.kanade.tachiyomi.extension.ko.dcmanga",
    "eu.kanade.tachiyomi.extension.ko.jjaptoon",
    "eu.kanade.tachiyomi.extension.ko.dcnaverwebtoon",
    "eu.kanade.tachiyomi.extension.ko.sbxhnovelmihontest",
    "eu.kanade.tachiyomi.extension.ko.wfwfv1",
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_goodtoon_metadata(path: Path) -> dict:
    metadata = json.loads(path.read_text(encoding="utf-8"))
    if metadata["packageName"] != GOODTOON:
        raise SystemExit("Built Goodtoon package name is invalid")
    if metadata["versionName"] != "1.4.4" or metadata["versionCode"] != 104004:
        raise SystemExit("Built Goodtoon version is invalid")
    if metadata["extensionLib"] != "1.4" or metadata["contentWarning"] != 3:
        raise SystemExit("Built Goodtoon extension metadata is invalid")
    if metadata["name"] != "Goodtoon 웹툰" or len(metadata["sources"]) != 1:
        raise SystemExit("Built Goodtoon source metadata is invalid")
    source = metadata["sources"][0]
    expected_source = {
        "id": 760550510744678728,
        "name": "Goodtoon 웹툰 (Android)",
        "lang": "ko",
        "baseUrl": "https://www.goodtoon004.com",
    }
    if any(source.get(key) != value for key, value in expected_source.items()):
        raise SystemExit("Built Goodtoon source identity is invalid")
    return metadata


def replace_goodtoon_entry(entry: pb.Extension, metadata: dict, repository: str, apk_name: str) -> None:
    entry.name = metadata["name"]
    entry.packageName = metadata["packageName"]
    entry.extensionLib = metadata["extensionLib"]
    entry.versionCode = metadata["versionCode"]
    entry.versionName = metadata["versionName"]
    entry.contentWarning = metadata["contentWarning"]
    entry.resources.apkUrl = f"https://raw.githubusercontent.com/{repository}/repo/apk/{apk_name}"
    entry.resources.iconUrl = f"https://raw.githubusercontent.com/{repository}/repo/icon/goodtoonwebtoontest.png"
    entry.sources.clear()
    source = entry.sources.add()
    source.id = metadata["sources"][0]["id"]
    source.name = metadata["sources"][0]["name"]
    source.language = metadata["sources"][0]["lang"]
    source.homeUrl = metadata["sources"][0]["baseUrl"]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--existing", type=Path, required=True)
    parser.add_argument("--goodtoon-apk", type=Path, required=True)
    parser.add_argument("--goodtoon-source-info", type=Path, required=True)
    parser.add_argument("--goodtoon-icon", type=Path, required=True)
    parser.add_argument("--repository", default="GeumHun/toonkor-personal")
    args = parser.parse_args()
    existing = args.existing.resolve()
    goodtoon_apk = args.goodtoon_apk.resolve()
    goodtoon_icon = args.goodtoon_icon.resolve()
    if not goodtoon_apk.is_file() or not goodtoon_icon.is_file():
        raise SystemExit("Built Goodtoon APK or icon is missing")
    goodtoon = load_goodtoon_metadata(args.goodtoon_source_info.resolve())

    manifest = json.loads((ROOT / "prebuilt/dc-individual.json").read_text(encoding="utf-8"))
    records = manifest["records"]
    individual_packages = {record["extension"]["packageName"] for record in records}
    expected_packages = {TOONKOR, *individual_packages}
    if len(records) != 10 or len(individual_packages) != 10:
        raise SystemExit("Manifest must contain exactly 10 curated individual APK packages")
    if len(expected_packages) != 11 or expected_packages & REMOVED_PACKAGES or GOODTOON not in individual_packages:
        raise SystemExit("Curated package selection is invalid")

    for record in records:
        apk = ROOT / "prebuilt" / "dc-individual" / record["apkFile"]
        if sha256(apk) != record["apkSha256"]:
            raise SystemExit(f"Original APK SHA-256 mismatch: {record['apkFile']}")

    index = pb.Index.FromString(gzip.decompress((existing / "index.pb").read_bytes()))
    original_entries = {item.packageName: item.SerializeToString(deterministic=True) for item in index.extensionList.extensions}
    if not expected_packages <= set(original_entries):
        raise SystemExit("Existing Mihon index is missing a retained extension")

    retained = [item for item in index.extensionList.extensions if item.packageName in expected_packages]
    index.extensionList.ClearField("extensions")
    for item in retained:
        index.extensionList.extensions.add().CopyFrom(item)
    goodtoon_entry = next(item for item in index.extensionList.extensions if item.packageName == GOODTOON)
    replace_goodtoon_entry(goodtoon_entry, goodtoon, args.repository, goodtoon_apk.name)

    out = ROOT / "dist"
    if out.exists():
        shutil.rmtree(out)
    shutil.copytree(existing, out, ignore=shutil.ignore_patterns(".git"))
    (out / "apk").mkdir(exist_ok=True)
    (out / "icon").mkdir(exist_ok=True)
    for package in REMOVED_PACKAGES | {GOODTOON}:
        short_name = package.rsplit(".", 1)[-1]
        for apk in (out / "apk").glob(f"tachiyomi-ko.{short_name}-v*.apk"):
            apk.unlink()
    for record in records:
        package = record["extension"]["packageName"]
        if package == GOODTOON:
            continue
        apk = ROOT / "prebuilt" / "dc-individual" / record["apkFile"]
        published = out / "apk" / record["apkFile"]
        shutil.copy2(apk, published)
        if sha256(published) != record["apkSha256"]:
            raise SystemExit(f"Published APK differs from original: {record['apkFile']}")
    shutil.copy2(goodtoon_apk, out / "apk" / goodtoon_apk.name)
    shutil.copy2(goodtoon_icon, out / "icon" / "goodtoonwebtoontest.png")

    payload = gzip.compress(index.SerializeToString(deterministic=True), mtime=0)
    decoded = pb.Index.FromString(gzip.decompress(payload))
    decoded_by_package = {item.packageName: item for item in decoded.extensionList.extensions}
    if set(decoded_by_package) != expected_packages:
        raise SystemExit("Mihon index package set is incomplete or contains an unexpected package")
    for package in expected_packages - {GOODTOON}:
        if decoded_by_package[package].SerializeToString(deterministic=True) != original_entries[package]:
            raise SystemExit(f"Retained index entry changed: {package}")
    if decoded_by_package[GOODTOON].versionCode != 104004 or decoded_by_package[GOODTOON].sources[0].id != 760550510744678728:
        raise SystemExit("Reconstructed Goodtoon index entry is invalid")
    (out / "index.pb").write_bytes(payload)

    readable = json.loads((existing / "index.json").read_text(encoding="utf-8"))
    readable = [item for item in readable if item.get("pkg") in expected_packages and item.get("pkg") != GOODTOON]
    readable.append({
        "name": "Tachiyomi: Goodtoon 웹툰",
        "pkg": GOODTOON,
        "apk": goodtoon_apk.name,
        "lang": "ko",
        "code": 4,
        "version": "1.4.4",
        "nsfw": 1,
        "sources": [{
            "name": "Goodtoon 웹툰 (Android)",
            "lang": "ko",
            "id": "760550510744678728",
            "baseUrl": "https://www.goodtoon004.com",
            "versionId": 1,
            "hasCloudflare": 0,
        }],
    })
    if {item.get("pkg") for item in readable} != expected_packages:
        raise SystemExit("Readable index package set is incomplete or unexpected")
    (out / "index.json").write_text(json.dumps(readable, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    for protected in ("index.min.json", "repo.json"):
        if (out / protected).read_bytes() != (existing / protected).read_bytes():
            raise SystemExit(f"Protected file changed: {protected}")
    print(f"Published {len(expected_packages)} retained packages; reconstructed Goodtoon {goodtoon_apk.name}")
    print(f"Mihon URL: https://raw.githubusercontent.com/{args.repository}/repo/index.pb")


if __name__ == "__main__":
    main()

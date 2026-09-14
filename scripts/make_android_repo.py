"""Add original DC Manga bundle and individual APKs to the Mihon repository."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import shutil

import index_pb2 as pb

ROOT = Path(__file__).resolve().parents[1]
DCMANGA = "eu.kanade.tachiyomi.extension.ko.dcmanga"
TOONKOR = "eu.kanade.tachiyomi.extension.ko.toonkor"
TOKKI_SIGNAL = "eu.kanade.tachiyomi.extension.ko.tokkisignal"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def add_entry(index: pb.Index, record: dict, repository: str) -> None:
    ext = record["extension"]
    short_name = ext["packageName"].rsplit(".", 1)[-1]
    base = f"https://raw.githubusercontent.com/{repository}/repo"
    entry = index.extensionList.extensions.add(
        name=ext["name"],
        packageName=ext["packageName"],
        extensionLib=ext["extensionLib"],
        versionCode=int(ext["versionCode"]),
        versionName=ext["versionName"],
        contentWarning=pb.ContentWarning.Value(ext["contentWarning"]),
        resources=pb.Resources(
            apkUrl=f"{base}/apk/{record['apkFile']}",
            iconUrl=f"{base}/icon/{short_name}.png",
        ),
    )
    for source in ext["sources"]:
        entry.sources.add(
            id=int(source["id"]),
            name=source["name"],
            language=source["language"],
            homeUrl=source["homeUrl"],
        )


def legacy_record(record: dict) -> dict:
    ext = record["extension"]
    return {
        "name": ext.get("legacyName", f"Tachiyomi: {ext['name']}"),
        "pkg": ext["packageName"],
        "apk": record["apkFile"],
        "lang": "ko",
        "code": int(ext["versionCode"]),
        "version": ext["versionName"],
        "nsfw": 1,
        "sources": [{
            "name": source["name"],
            "lang": source["language"],
            "id": source["id"],
            "baseUrl": source["homeUrl"],
            "versionId": int(source.get("versionId", 1)),
            "hasCloudflare": int(source.get("hasCloudflare", 0)),
        } for source in ext["sources"]],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--existing", type=Path, required=True)
    parser.add_argument("--repository", default="GeumHun/toonkor-personal")
    args = parser.parse_args()
    existing = args.existing.resolve()

    bundle = json.loads((ROOT / "prebuilt/dcmanga.json").read_text(encoding="utf-8"))
    manifest = json.loads((ROOT / "prebuilt/dc-individual.json").read_text(encoding="utf-8"))
    individual = manifest["records"]
    records = [bundle, *individual]
    selected_packages = {record["extension"]["packageName"] for record in records}
    individual_packages = {record["extension"]["packageName"] for record in individual}
    if len(individual) != 14 or len(individual_packages) != 14:
        raise SystemExit("Manifest must contain exactly 14 individual APK packages")
    if sum(len(record["extension"]["sources"]) for record in individual) != 15:
        raise SystemExit("The 14 individual APKs must expose exactly 15 sources")
    if TOKKI_SIGNAL in selected_packages:
        raise SystemExit("Tokki Signal must not be published")

    for record in records:
        apk_root = ROOT / "prebuilt"
        apk = apk_root / record["apkFile"] if record is bundle else apk_root / "dc-individual" / record["apkFile"]
        if sha256(apk) != record["apkSha256"]:
            raise SystemExit(f"Original APK SHA-256 mismatch: {record['apkFile']}")

    index = pb.Index.FromString(gzip.decompress((existing / "index.pb").read_bytes()))
    original_entries = {
        item.packageName: item.SerializeToString(deterministic=True)
        for item in index.extensionList.extensions
    }
    if TOONKOR not in original_entries or DCMANGA not in original_entries:
        raise SystemExit("Existing Mihon index must contain Toonkor and DC Manga")
    original_packages = set(original_entries)

    retained = [item for item in index.extensionList.extensions if item.packageName not in selected_packages]
    index.extensionList.ClearField("extensions")
    for item in retained:
        index.extensionList.extensions.add().CopyFrom(item)
    for record in records:
        add_entry(index, record, args.repository)

    out = ROOT / "dist"
    if out.exists():
        shutil.rmtree(out)
    shutil.copytree(existing, out, ignore=shutil.ignore_patterns(".git"))
    (out / "apk").mkdir(exist_ok=True)
    (out / "icon").mkdir(exist_ok=True)

    for record in records:
        ext = record["extension"]
        short_name = ext["packageName"].rsplit(".", 1)[-1]
        for old in (out / "apk").glob(f"tachiyomi-ko.{short_name}-v*.apk"):
            old.unlink()
        source_apk = ROOT / "prebuilt" / record["apkFile"] if record is bundle else ROOT / "prebuilt/dc-individual" / record["apkFile"]
        shutil.copy2(source_apk, out / "apk" / record["apkFile"])
        source_icon = ROOT / "prebuilt/dcmanga.png" if record is bundle else ROOT / "prebuilt/dc-individual-icons" / record["iconFile"]
        shutil.copy2(source_icon, out / "icon" / f"{short_name}.png")
        shutil.copy2(source_icon, out / "icon" / f"{ext['packageName']}.png")

    payload = gzip.compress(index.SerializeToString(deterministic=True), mtime=0)
    decoded = pb.Index.FromString(gzip.decompress(payload))
    decoded_by_package = {item.packageName: item for item in decoded.extensionList.extensions}
    expected_packages = original_packages | individual_packages
    if set(decoded_by_package) != expected_packages:
        raise SystemExit("Mihon index package set is incomplete or contains an unexpected package")
    for package, serialized in original_entries.items():
        if package in individual_packages:
            continue
        if decoded_by_package[package].SerializeToString(deterministic=True) != serialized:
            raise SystemExit(f"Existing index entry changed: {package}")
    if TOKKI_SIGNAL in decoded_by_package:
        raise SystemExit("Tokki Signal was unexpectedly added")
    (out / "index.pb").write_bytes(payload)

    readable = json.loads((existing / "index.json").read_text(encoding="utf-8"))
    readable = [item for item in readable if item.get("pkg") not in selected_packages]
    readable.extend(legacy_record(record) for record in records)
    (out / "index.json").write_text(
        json.dumps(readable, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    for protected in ("index.min.json", "repo.json"):
        if (out / protected).read_bytes() != (existing / protected).read_bytes():
            raise SystemExit(f"Protected iOS file changed: {protected}")
    for record in records:
        published = out / "apk" / record["apkFile"]
        if sha256(published) != record["apkSha256"]:
            raise SystemExit(f"Published APK differs from original: {record['apkFile']}")

    print(f"Published {len(individual)} individual APKs with 15 sources; preserved {len(original_packages)} existing packages")
    print(f"Mihon URL: https://raw.githubusercontent.com/{args.repository}/repo/index.pb")


if __name__ == "__main__":
    main()

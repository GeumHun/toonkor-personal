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
REMOVED_PACKAGES = {
    "eu.kanade.tachiyomi.extension.ko.dcmanga",
    "eu.kanade.tachiyomi.extension.ko.jjaptoon",
    "eu.kanade.tachiyomi.extension.ko.dcnaverwebtoon",
    "eu.kanade.tachiyomi.extension.ko.sbxhnovelmihontest",
    "eu.kanade.tachiyomi.extension.ko.wfwfv1",
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--existing", type=Path, required=True)
    parser.add_argument("--repository", default="GeumHun/toonkor-personal")
    args = parser.parse_args()
    existing = args.existing.resolve()

    manifest = json.loads((ROOT / "prebuilt/dc-individual.json").read_text(encoding="utf-8"))
    records = manifest["records"]
    individual_packages = {record["extension"]["packageName"] for record in records}
    expected_packages = {TOONKOR, *individual_packages}
    if len(records) != 10 or len(individual_packages) != 10:
        raise SystemExit("Manifest must contain exactly 10 curated individual APK packages")
    if len(expected_packages) != 11 or expected_packages & REMOVED_PACKAGES:
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

    out = ROOT / "dist"
    if out.exists():
        shutil.rmtree(out)
    shutil.copytree(existing, out, ignore=shutil.ignore_patterns(".git"))
    (out / "apk").mkdir(exist_ok=True)
    for package in REMOVED_PACKAGES:
        short_name = package.rsplit(".", 1)[-1]
        for apk in (out / "apk").glob(f"tachiyomi-ko.{short_name}-v*.apk"):
            apk.unlink()
    for record in records:
        apk = ROOT / "prebuilt" / "dc-individual" / record["apkFile"]
        published = out / "apk" / record["apkFile"]
        shutil.copy2(apk, published)
        if sha256(published) != record["apkSha256"]:
            raise SystemExit(f"Published APK differs from original: {record['apkFile']}")

    payload = gzip.compress(index.SerializeToString(deterministic=True), mtime=0)
    decoded = pb.Index.FromString(gzip.decompress(payload))
    decoded_by_package = {item.packageName: item for item in decoded.extensionList.extensions}
    if set(decoded_by_package) != expected_packages:
        raise SystemExit("Mihon index package set is incomplete or contains an unexpected package")
    for package in expected_packages:
        if decoded_by_package[package].SerializeToString(deterministic=True) != original_entries[package]:
            raise SystemExit(f"Retained index entry changed: {package}")
    (out / "index.pb").write_bytes(payload)

    readable = json.loads((existing / "index.json").read_text(encoding="utf-8"))
    readable = [item for item in readable if item.get("pkg") in expected_packages]
    if {item.get("pkg") for item in readable} != expected_packages:
        raise SystemExit("Readable index package set is incomplete or unexpected")
    (out / "index.json").write_text(json.dumps(readable, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    for protected in ("index.min.json", "repo.json"):
        if (out / protected).read_bytes() != (existing / protected).read_bytes():
            raise SystemExit(f"Protected file changed: {protected}")
    print(f"Published {len(expected_packages)} retained packages; removed {len(REMOVED_PACKAGES)} packages")
    print(f"Mihon URL: https://raw.githubusercontent.com/{args.repository}/repo/index.pb")


if __name__ == "__main__":
    main()

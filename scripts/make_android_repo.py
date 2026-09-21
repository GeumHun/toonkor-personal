"""Build the Toonkor and Goodtoon Android Mihon repository."""
import argparse, gzip, json, shutil
from pathlib import Path
import index_pb2 as pb

ROOT = Path(__file__).resolve().parents[1]
TOONKOR = "eu.kanade.tachiyomi.extension.ko.toonkor"
GOODTOON = "eu.kanade.tachiyomi.extension.ko.goodtoonwebtoontest"
PACKAGES = {TOONKOR, GOODTOON}
GOODTOON_CODE = 104008
GOODTOON_VERSION = "1.4.8"
GOODTOON_ICON = "goodtoonwebtoontest-gdt-v1.4.8.png"


def fail(message):
    raise SystemExit(message)


def load_goodtoon(path):
    meta = json.loads(path.read_text(encoding="utf-8"))
    expected = {
        "packageName": GOODTOON,
        "versionCode": GOODTOON_CODE,
        "versionName": GOODTOON_VERSION,
        "extensionLib": "1.4",
        "contentWarning": 3,
        "name": "Goodtoon 웹툰",
    }
    if any(meta.get(key) != value for key, value in expected.items()) or len(meta.get("sources", [])) != 1:
        fail("Built Goodtoon metadata is invalid")
    source = meta["sources"][0]
    expected_source = {"id": 760550510744678728, "name": "Goodtoon 웹툰 (Android)", "lang": "ko", "baseUrl": "https://www.goodtoon004.com"}
    if any(source.get(key) != value for key, value in expected_source.items()):
        fail("Built Goodtoon source identity is invalid")
    return meta


def update_goodtoon(entry, meta, repository, apk_name):
    entry.name = meta["name"]
    entry.packageName = meta["packageName"]
    entry.extensionLib = meta["extensionLib"]
    entry.versionCode = meta["versionCode"]
    entry.versionName = meta["versionName"]
    entry.contentWarning = meta["contentWarning"]
    entry.resources.apkUrl = f"https://raw.githubusercontent.com/{repository}/repo/apk/{apk_name}"
    entry.resources.iconUrl = f"https://raw.githubusercontent.com/{repository}/repo/icon/{GOODTOON_ICON}"
    entry.sources.clear()
    source = entry.sources.add()
    source.id = meta["sources"][0]["id"]
    source.name = meta["sources"][0]["name"]
    source.language = meta["sources"][0]["lang"]
    source.homeUrl = meta["sources"][0]["baseUrl"]


def copy(source, destination):
    if not source.is_file():
        fail(f"Required asset is missing: {source}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--existing", type=Path, required=True)
    parser.add_argument("--goodtoon-apk", type=Path, required=True)
    parser.add_argument("--goodtoon-source-info", type=Path, required=True)
    parser.add_argument("--goodtoon-icon", type=Path, required=True)
    parser.add_argument("--repository", default="GeumHun/toonkor-personal")
    args = parser.parse_args()
    existing = args.existing.resolve()
    apk = args.goodtoon_apk.resolve()
    icon = args.goodtoon_icon.resolve()
    if not apk.is_file() or not icon.is_file():
        fail("Built Goodtoon APK or icon is missing")
    goodtoon = load_goodtoon(args.goodtoon_source_info.resolve())

    index = pb.Index.FromString(gzip.decompress((existing / "index.pb").read_bytes()))
    original = {entry.packageName: entry.SerializeToString(deterministic=True) for entry in index.extensionList.extensions}
    if not PACKAGES <= set(original):
        fail("Existing index is missing Toonkor or Goodtoon")
    retained = [entry for entry in index.extensionList.extensions if entry.packageName in PACKAGES]
    index.extensionList.ClearField("extensions")
    for entry in retained:
        index.extensionList.extensions.add().CopyFrom(entry)
    by_package = {entry.packageName: entry for entry in index.extensionList.extensions}
    toonkor = by_package[TOONKOR]
    update_goodtoon(by_package[GOODTOON], goodtoon, args.repository, apk.name)
    toonkor_apk = Path(toonkor.resources.apkUrl).name
    toonkor_icon = Path(toonkor.resources.iconUrl).name
    if not toonkor_apk or not toonkor_icon:
        fail("Retained Toonkor resources are invalid")

    out = ROOT / "dist"
    if out.exists():
        shutil.rmtree(out)
    shutil.copytree(existing, out, ignore=shutil.ignore_patterns(".git"))
    shutil.rmtree(out / "apk", ignore_errors=True)
    shutil.rmtree(out / "icon", ignore_errors=True)
    copy(existing / "apk" / toonkor_apk, out / "apk" / toonkor_apk)
    copy(existing / "icon" / toonkor_icon, out / "icon" / toonkor_icon)
    copy(apk, out / "apk" / apk.name)
    copy(icon, out / "icon" / GOODTOON_ICON)

    payload = gzip.compress(index.SerializeToString(deterministic=True), mtime=0)
    decoded = pb.Index.FromString(gzip.decompress(payload))
    entries = {entry.packageName: entry for entry in decoded.extensionList.extensions}
    if set(entries) != PACKAGES:
        fail("Mihon index contains an unexpected package")
    if entries[TOONKOR].SerializeToString(deterministic=True) != original[TOONKOR]:
        fail("Retained Toonkor entry changed")
    if entries[GOODTOON].versionCode != GOODTOON_CODE or entries[GOODTOON].sources[0].id != 760550510744678728:
        fail("Reconstructed Goodtoon entry is invalid")
    (out / "index.pb").write_bytes(payload)

    readable = json.loads((existing / "index.json").read_text(encoding="utf-8"))
    readable = [entry for entry in readable if entry.get("pkg") == TOONKOR]
    readable.append({"name": "Tachiyomi: Goodtoon 웹툰", "pkg": GOODTOON, "apk": apk.name, "lang": "ko", "code": 8, "version": GOODTOON_VERSION, "nsfw": 1, "sources": [{"name": "Goodtoon 웹툰 (Android)", "lang": "ko", "id": "760550510744678728", "baseUrl": "https://www.goodtoon004.com", "versionId": 1, "hasCloudflare": 0}]})
    if {entry.get("pkg") for entry in readable} != PACKAGES:
        fail("Readable index contains an unexpected package")
    (out / "index.json").write_text(json.dumps(readable, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for name in ("index.min.json", "repo.json"):
        if (out / name).read_bytes() != (existing / name).read_bytes():
            fail(f"Protected file changed: {name}")
    print(f"Published two packages: Toonkor and Goodtoon {apk.name}")


if __name__ == "__main__":
    main()
"""Build the curated Android Mihon repository."""
import argparse, gzip, json, shutil
from pathlib import Path
import index_pb2 as pb

ROOT = Path(__file__).resolve().parents[1]
TOONKOR = "eu.kanade.tachiyomi.extension.ko.toonkor"
GOODTOON = "eu.kanade.tachiyomi.extension.ko.goodtoonwebtoontest"
TOON11 = "eu.kanade.tachiyomi.extension.ko.toon11"
PACKAGES = {TOONKOR, GOODTOON, TOON11}
TOONKOR_CODE = 104017
TOONKOR_VERSION = "1.4.17"
TOONKOR_ICON = "toonkor-gh-v1.4.17.png"
GOODTOON_CODE = 104009
GOODTOON_VERSION = "1.4.9"
GOODTOON_ICON = "goodtoon-gh-v1.4.9.png"
TOON11_CODE = 104029
TOON11_VERSION = "1.4.29"
TOON11_ICON = "toon11-gh-v1.4.29.png"


def fail(message):
    raise SystemExit(message)


def load_toonkor(path):
    meta = json.loads(path.read_text(encoding="utf-8"))
    expected = {"packageName": TOONKOR, "versionCode": TOONKOR_CODE, "versionName": TOONKOR_VERSION, "extensionLib": "1.4", "contentWarning": 2, "name": "Toonkor"}
    if any(meta.get(key) != value for key, value in expected.items()) or len(meta.get("sources", [])) != 1:
        fail("Built Toonkor metadata is invalid")
    source = meta["sources"][0]
    expected_source = {"id": 6596496791271983268, "name": "Toonkor", "lang": "ko", "baseUrl": "https://tkor154.com"}
    if any(source.get(key) != value for key, value in expected_source.items()):
        fail("Built Toonkor source identity is invalid")
    return meta


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


def load_toon11(path):
    meta = json.loads(path.read_text(encoding="utf-8"))
    expected = {"packageName": TOON11, "versionCode": TOON11_CODE, "versionName": TOON11_VERSION, "extensionLib": "1.4", "contentWarning": 2, "name": "11toon"}
    if any(meta.get(key) != value for key, value in expected.items()) or len(meta.get("sources", [])) != 1:
        fail("Built 11toon metadata is invalid")
    source = meta["sources"][0]
    expected_source = {"id": 8796296375202334266, "name": "11toon 만화", "lang": "ko", "baseUrl": "https://11toon.com"}
    if any(source.get(key) != value for key, value in expected_source.items()):
        fail("Built 11toon source identity is invalid")
    return meta


def update_entry(entry, meta, repository, apk_name, icon_name):
    entry.name = meta["name"]
    entry.packageName = meta["packageName"]
    entry.extensionLib = meta["extensionLib"]
    entry.versionCode = meta["versionCode"]
    entry.versionName = meta["versionName"]
    entry.contentWarning = meta["contentWarning"]
    entry.resources.apkUrl = f"https://raw.githubusercontent.com/{repository}/repo/apk/{apk_name}"
    entry.resources.iconUrl = f"https://raw.githubusercontent.com/{repository}/repo/icon/{icon_name}"
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
    parser.add_argument("--toonkor-apk", type=Path, required=True)
    parser.add_argument("--toonkor-source-info", type=Path, required=True)
    parser.add_argument("--toonkor-icon", type=Path, required=True)
    parser.add_argument("--goodtoon-apk", type=Path, required=True)
    parser.add_argument("--goodtoon-source-info", type=Path, required=True)
    parser.add_argument("--goodtoon-icon", type=Path, required=True)
    parser.add_argument("--toon11-apk", type=Path, required=True)
    parser.add_argument("--toon11-source-info", type=Path, required=True)
    parser.add_argument("--toon11-icon", type=Path, required=True)
    parser.add_argument("--repository", default="GeumHun/toonkor-personal")
    args = parser.parse_args()
    existing = args.existing.resolve()
    toonkor_apk = args.toonkor_apk.resolve()
    toonkor_icon = args.toonkor_icon.resolve()
    goodtoon_apk = args.goodtoon_apk.resolve()
    goodtoon_icon = args.goodtoon_icon.resolve()
    toon11_apk = args.toon11_apk.resolve()
    toon11_icon = args.toon11_icon.resolve()
    if any(not asset.is_file() for asset in (toonkor_apk, toonkor_icon, goodtoon_apk, goodtoon_icon, toon11_apk, toon11_icon)):
        fail("A built APK or icon is missing")
    toonkor = load_toonkor(args.toonkor_source_info.resolve())
    goodtoon = load_goodtoon(args.goodtoon_source_info.resolve())
    toon11 = load_toon11(args.toon11_source_info.resolve())

    index = pb.Index.FromString(gzip.decompress((existing / "index.pb").read_bytes()))
    original = {entry.packageName: entry.SerializeToString(deterministic=True) for entry in index.extensionList.extensions}
    if not {TOONKOR, GOODTOON} <= set(original):
        fail("Existing index is missing Toonkor or Goodtoon")
    retained = [entry for entry in index.extensionList.extensions if entry.packageName in {TOONKOR, GOODTOON}]
    index.extensionList.ClearField("extensions")
    for entry in retained:
        index.extensionList.extensions.add().CopyFrom(entry)
    by_package = {entry.packageName: entry for entry in index.extensionList.extensions}
    update_entry(by_package[TOONKOR], toonkor, args.repository, toonkor_apk.name, TOONKOR_ICON)
    update_entry(by_package[GOODTOON], goodtoon, args.repository, goodtoon_apk.name, GOODTOON_ICON)
    toon11_entry = index.extensionList.extensions.add()
    update_entry(toon11_entry, toon11, args.repository, toon11_apk.name, TOON11_ICON)

    out = ROOT / "dist"
    if out.exists():
        shutil.rmtree(out)
    shutil.copytree(existing, out, ignore=shutil.ignore_patterns(".git"))
    shutil.rmtree(out / "apk", ignore_errors=True)
    shutil.rmtree(out / "icon", ignore_errors=True)
    copy(toonkor_apk, out / "apk" / toonkor_apk.name)
    copy(toonkor_icon, out / "icon" / TOONKOR_ICON)
    copy(goodtoon_apk, out / "apk" / goodtoon_apk.name)
    copy(goodtoon_icon, out / "icon" / GOODTOON_ICON)
    copy(toon11_apk, out / "apk" / toon11_apk.name)
    copy(toon11_icon, out / "icon" / TOON11_ICON)

    payload = gzip.compress(index.SerializeToString(deterministic=True), mtime=0)
    decoded = pb.Index.FromString(gzip.decompress(payload))
    entries = {entry.packageName: entry for entry in decoded.extensionList.extensions}
    if set(entries) != PACKAGES:
        fail("Mihon index contains an unexpected package")
    if entries[TOONKOR].versionCode != TOONKOR_CODE or entries[TOONKOR].sources[0].id != 6596496791271983268:
        fail("Reconstructed Toonkor entry is invalid")
    if entries[GOODTOON].versionCode != GOODTOON_CODE or entries[GOODTOON].sources[0].id != 760550510744678728:
        fail("Reconstructed Goodtoon entry is invalid")
    if entries[TOON11].versionCode != TOON11_CODE or entries[TOON11].sources[0].id != 8796296375202334266:
        fail("Reconstructed 11toon entry is invalid")
    (out / "index.pb").write_bytes(payload)

    readable = json.loads((existing / "index.json").read_text(encoding="utf-8"))
    readable = [entry for entry in readable if entry.get("pkg") not in PACKAGES]
    readable.append({"name": "Tachiyomi: Toonkor", "pkg": TOONKOR, "apk": toonkor_apk.name, "lang": "ko", "code": 17, "version": TOONKOR_VERSION, "nsfw": 1, "sources": [{"name": "Toonkor", "lang": "ko", "id": "6596496791271983268", "baseUrl": "https://tkor154.com", "versionId": 1, "hasCloudflare": 0}]})
    readable.append({"name": "Tachiyomi: Goodtoon 웹툰", "pkg": GOODTOON, "apk": goodtoon_apk.name, "lang": "ko", "code": 9, "version": GOODTOON_VERSION, "nsfw": 1, "sources": [{"name": "Goodtoon 웹툰 (Android)", "lang": "ko", "id": "760550510744678728", "baseUrl": "https://www.goodtoon004.com", "versionId": 1, "hasCloudflare": 0}]})
    readable.append({"name": "Tachiyomi: 11toon", "pkg": TOON11, "apk": toon11_apk.name, "lang": "ko", "code": 29, "version": TOON11_VERSION, "nsfw": 1, "sources": [{"name": "11toon 만화", "lang": "ko", "id": "8796296375202334266", "baseUrl": "https://11toon.com", "versionId": 1, "hasCloudflare": 0}]})
    if {entry.get("pkg") for entry in readable} != PACKAGES:
        fail("Readable index contains an unexpected package")
    (out / "index.json").write_text(json.dumps(readable, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for name in ("index.min.json", "repo.json"):
        if (out / name).read_bytes() != (existing / name).read_bytes():
            fail(f"Protected file changed: {name}")
    print(f"Published Toonkor {toonkor_apk.name}, Goodtoon {goodtoon_apk.name}, and 11toon {toon11_apk.name}")


if __name__ == "__main__":
    main()

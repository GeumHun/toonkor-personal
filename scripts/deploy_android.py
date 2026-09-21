"""Publish the Toonkor and Goodtoon Android Mihon repository."""
import gzip, hashlib, os, shutil, subprocess, tempfile
from pathlib import Path
import index_pb2 as pb

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = os.environ["GITHUB_REPOSITORY"]
TOONKOR = "eu.kanade.tachiyomi.extension.ko.toonkor"
GOODTOON = "eu.kanade.tachiyomi.extension.ko.goodtoonwebtoontest"
PACKAGES = {TOONKOR, GOODTOON}
if not os.environ.get("GH_TOKEN"):
    raise SystemExit("GH_TOKEN is required; run this script in GitHub Actions")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


with tempfile.TemporaryDirectory() as temp:
    target = Path(temp)
    def git(*args):
        return subprocess.run(["git", *args], cwd=target, check=True, capture_output=True, text=True)
    git("init", "--initial-branch=repo")
    git("remote", "add", "origin", f"https://github.com/{REPOSITORY}.git")
    git("config", "credential.helper", '!f() { echo username=x-access-token; echo "password=$GH_TOKEN"; }; f')
    git("config", "user.name", "github-actions[bot]")
    git("config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com")
    git("fetch", "--depth=1", "origin", "repo")
    git("checkout", "-B", "repo", "FETCH_HEAD")

    old = pb.Index.FromString(gzip.decompress((target / "index.pb").read_bytes()))
    new = pb.Index.FromString(gzip.decompress((ROOT / "dist/index.pb").read_bytes()))
    old_by_package = {entry.packageName: entry for entry in old.extensionList.extensions}
    new_by_package = {entry.packageName: entry for entry in new.extensionList.extensions}
    if old.signingKey != new.signingKey:
        raise SystemExit("Repository signing key changed")
    if not PACKAGES <= set(old_by_package) or set(new_by_package) != PACKAGES:
        raise SystemExit("Repository package selection is invalid")
    toonkor = new_by_package[TOONKOR]
    if toonkor.versionCode != 104015 or toonkor.versionName != "1.4.15" or len(toonkor.sources) != 1 or toonkor.sources[0].id != 6596496791271983268:
        raise SystemExit("Updated Toonkor metadata is invalid")
    goodtoon = new_by_package[GOODTOON]
    if goodtoon.versionCode != 104008 or goodtoon.versionName != "1.4.8" or len(goodtoon.sources) != 1 or goodtoon.sources[0].id != 760550510744678728:
        raise SystemExit("Reconstructed Goodtoon metadata is invalid")
    for entry, label in ((toonkor, "Toonkor"), (goodtoon, "Goodtoon")):
        apk = ROOT / "dist" / "apk" / Path(entry.resources.apkUrl).name
        icon = ROOT / "dist" / "icon" / Path(entry.resources.iconUrl).name
        if not apk.is_file() or not digest(apk) or not icon.is_file() or not digest(icon):
            raise SystemExit(f"{label} APK or icon is missing")
    for name in ("index.min.json", "repo.json"):
        if (target / name).read_bytes() != (ROOT / "dist" / name).read_bytes():
            raise SystemExit(f"Protected file changed: {name}")

    for child in target.iterdir():
        if child.name != ".git":
            shutil.rmtree(child) if child.is_dir() else child.unlink()
    shutil.copytree(ROOT / "dist", target, dirs_exist_ok=True)
    git("add", "--all")
    if git("status", "--porcelain").stdout.strip():
        git("commit", "-m", "Publish Toonkor and Goodtoon for Mihon")
        git("push", "origin", "HEAD:repo")
    print(f"https://raw.githubusercontent.com/{REPOSITORY}/repo/index.pb")
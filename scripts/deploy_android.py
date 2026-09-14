"""Publish an Android-only repository overlay to the repo branch."""
import gzip
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

import index_pb2 as pb

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = os.environ["GITHUB_REPOSITORY"]
DCMANGA = "eu.kanade.tachiyomi.extension.ko.dcmanga"
TOONKOR = "eu.kanade.tachiyomi.extension.ko.toonkor"

if not os.environ.get("GH_TOKEN"):
    raise SystemExit("GH_TOKEN is required; run this script in GitHub Actions")


def digest(path: Path) -> str:
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

    old_index = pb.Index.FromString(gzip.decompress((target / "index.pb").read_bytes()))
    new_index = pb.Index.FromString(gzip.decompress((ROOT / "dist/index.pb").read_bytes()))
    old_toonkor = next(item for item in old_index.extensionList.extensions if item.packageName == TOONKOR)
    new_toonkor = next(item for item in new_index.extensionList.extensions if item.packageName == TOONKOR)
    if old_index.signingKey != new_index.signingKey:
        raise SystemExit("Repository signing key changed")
    if old_toonkor != new_toonkor:
        raise SystemExit("Existing Toonkor entry changed")
    if {item.packageName for item in new_index.extensionList.extensions} != {TOONKOR, DCMANGA}:
        raise SystemExit("Mihon index must contain exactly Toonkor and DC Manga")
    for protected in ("index.min.json", "repo.json"):
        if (target / protected).read_bytes() != (ROOT / "dist" / protected).read_bytes():
            raise SystemExit(f"Protected iOS file changed: {protected}")

    shutil.copytree(ROOT / "dist", target, dirs_exist_ok=True)
    record_apk = ROOT / "dist/apk/tachiyomi-ko.dcmanga-v1.4.6.apk"
    if digest(record_apk) != "8caea537ba54845fc80dc72435ee04752ace6109704f0000f4ed0d9343da94cd":
        raise SystemExit("DC Manga APK SHA-256 mismatch")
    git("add", "--all")
    status = git("status", "--porcelain").stdout.strip()
    if status:
        git("commit", "-m", "Publish DC Manga for Mihon")
        git("push", "origin", "HEAD:repo")
    print(f"https://raw.githubusercontent.com/{REPOSITORY}/repo/index.pb")

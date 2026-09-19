"""Publish the curated Android-only repository overlay to the repo branch."""
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

import index_pb2 as pb

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = os.environ["GITHUB_REPOSITORY"]
TOONKOR = "eu.kanade.tachiyomi.extension.ko.toonkor"
REMOVED_PACKAGES = {
    "eu.kanade.tachiyomi.extension.ko.dcmanga",
    "eu.kanade.tachiyomi.extension.ko.jjaptoon",
    "eu.kanade.tachiyomi.extension.ko.dcnaverwebtoon",
    "eu.kanade.tachiyomi.extension.ko.sbxhnovelmihontest",
    "eu.kanade.tachiyomi.extension.ko.wfwfv1",
}
if not os.environ.get("GH_TOKEN"):
    raise SystemExit("GH_TOKEN is required; run this script in GitHub Actions")


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


manifest = json.loads((ROOT / "prebuilt/dc-individual.json").read_text(encoding="utf-8"))
records = manifest["records"]
individual_packages = {record["extension"]["packageName"] for record in records}
expected_packages = {TOONKOR, *individual_packages}
if len(records) != 10 or len(individual_packages) != 10:
    raise SystemExit("Expected exactly 10 curated individual DC APKs")

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
    old_by_package = {item.packageName: item for item in old_index.extensionList.extensions}
    new_by_package = {item.packageName: item for item in new_index.extensionList.extensions}
    if old_index.signingKey != new_index.signingKey:
        raise SystemExit("Repository signing key changed")
    if not expected_packages <= set(old_by_package):
        raise SystemExit("Current repository is missing a retained extension")
    if set(new_by_package) != expected_packages:
        raise SystemExit("New Mihon index package set is incomplete or unexpected")
    if set(old_by_package) - set(new_by_package) != REMOVED_PACKAGES:
        raise SystemExit("Only the requested extension packages may be removed")
    for package in expected_packages:
        if new_by_package[package] != old_by_package[package]:
            raise SystemExit(f"Retained extension entry changed: {package}")
    for protected in ("index.min.json", "repo.json"):
        if (target / protected).read_bytes() != (ROOT / "dist" / protected).read_bytes():
            raise SystemExit(f"Protected file changed: {protected}")
    for record in records:
        apk = ROOT / "dist/apk" / record["apkFile"]
        if digest(apk) != record["apkSha256"]:
            raise SystemExit(f"Published APK SHA-256 mismatch: {record['apkFile']}")
    shutil.copytree(ROOT / "dist", target, dirs_exist_ok=True)
    git("add", "--all")
    status = git("status", "--porcelain").stdout.strip()
    if status:
        git("commit", "-m", "Publish curated DC extensions for Mihon")
        git("push", "origin", "HEAD:repo")
    print(f"https://raw.githubusercontent.com/{REPOSITORY}/repo/index.pb")

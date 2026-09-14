"""Publish an Android-only repository overlay to the repo branch."""
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
DCMANGA = "eu.kanade.tachiyomi.extension.ko.dcmanga"
TOONKOR = "eu.kanade.tachiyomi.extension.ko.toonkor"
TOKKI_SIGNAL = "eu.kanade.tachiyomi.extension.ko.tokkisignal"

if not os.environ.get("GH_TOKEN"):
    raise SystemExit("GH_TOKEN is required; run this script in GitHub Actions")


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


manifest = json.loads((ROOT / "prebuilt/dc-individual.json").read_text(encoding="utf-8"))
records = manifest["records"]
individual_packages = {record["extension"]["packageName"] for record in records}
if len(records) != 14 or len(individual_packages) != 14:
    raise SystemExit("Expected exactly 14 individual DC APKs")

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
    if TOONKOR not in old_by_package or DCMANGA not in old_by_package:
        raise SystemExit("Current repository must contain Toonkor and DC Manga")
    for package, old_entry in old_by_package.items():
        if package not in individual_packages and new_by_package.get(package) != old_entry:
            raise SystemExit(f"Existing extension entry changed: {package}")
    if set(new_by_package) != set(old_by_package) | individual_packages:
        raise SystemExit("New Mihon index package set is incomplete or unexpected")
    if TOKKI_SIGNAL in new_by_package:
        raise SystemExit("Tokki Signal must not be published")
    for protected in ("index.min.json", "repo.json"):
        if (target / protected).read_bytes() != (ROOT / "dist" / protected).read_bytes():
            raise SystemExit(f"Protected iOS file changed: {protected}")
    for record in records:
        apk = ROOT / "dist/apk" / record["apkFile"]
        if digest(apk) != record["apkSha256"]:
            raise SystemExit(f"Published APK SHA-256 mismatch: {record['apkFile']}")

    shutil.copytree(ROOT / "dist", target, dirs_exist_ok=True)
    git("add", "--all")
    status = git("status", "--porcelain").stdout.strip()
    if status:
        git("commit", "-m", "Publish DC individual extensions for Mihon")
        git("push", "origin", "HEAD:repo")
    print(f"https://raw.githubusercontent.com/{REPOSITORY}/repo/index.pb")

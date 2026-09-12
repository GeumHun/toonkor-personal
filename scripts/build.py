"""Build only Toonkor, with upstream formatting checks and release lint."""
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
if not (root / "signingkey.jks").is_file():
    raise SystemExit("Create signingkey.jks first (see README). No debug-key publication.")
for name in ("ALIAS", "KEY_STORE_PASSWORD", "KEY_PASSWORD"):
    if not os.environ.get(name):
        raise SystemExit(f"Missing environment variable: {name}")
env = dict(os.environ, CI="true")
wrapper = [str(root / "gradlew.bat")] if os.name == "nt" else ["bash", str(root / "gradlew")]
subprocess.run(wrapper + [":src:ko:toonkor:clean", ":src:ko:toonkor:assembleRelease",
                         ":src:ko:toonkor:lintRelease", "--no-daemon"],
               cwd=root, env=env, check=True)

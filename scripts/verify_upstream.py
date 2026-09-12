"""Check that every retained upstream file still matches its Git blob hash."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
record = json.loads((root / "UPSTREAM.json").read_text(encoding="utf-8"))
failed = []
for name, expected in record["unchangedGitBlobs"].items():
    data = (root / name).read_bytes()
    # Match Git's text normalization, including .bat files checked out with CRLF.
    if name.endswith(".bat"):
        data = data.replace(b"\r\n", b"\n")
    actual = hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest()
    if actual != expected:
        failed.append(name)
if failed:
    raise SystemExit("Changed since upstream snapshot:\n" + "\n".join(failed))
print(f"Verified {len(record['unchangedGitBlobs'])} unchanged upstream files")

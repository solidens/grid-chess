#!/usr/bin/env python3
"""
Fetch the five Maia networks and export them to ONNX for the Android app.

Maia ships lc0's protobuf weight format (.pb.gz), which ONNX Runtime cannot
read. lc0's own `leela2onnx` mode is the conversion tool, and it emits exactly
the graph the app expects:

    Input:  INPUT_CLASSICAL_112_PLANE  named /input/planes   (1, 112, 8, 8)
    Output: POLICY_CONVOLUTION         named /output/policy  (1, 1858)
            VALUE_WDL                  named /output/wdl     (1, 3)

which is what LeelaEncoder.kt builds and PolicyIndex.kt reads back.

Usage:

    brew install lc0          # or build it from LeelaChessZero/lc0
    python3 tools/export_maia.py

Nets land in app/src/main/assets/models/ and are gitignored: they are GPL-3
artefacts from CSSLab/maia-chess, fetched rather than vendored.
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

RATINGS = (1100, 1300, 1500, 1700, 1900)
WEIGHTS_URL = "https://github.com/CSSLab/maia-chess/raw/master/maia_weights/maia-{r}.pb.gz"

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "tools" / "weights"
OUT = ROOT / "app" / "src" / "main" / "assets" / "models"


def download(rating: int) -> Path:
    CACHE.mkdir(parents=True, exist_ok=True)
    dest = CACHE / f"maia-{rating}.pb.gz"
    if dest.exists() and dest.stat().st_size > 0:
        print(f"  cached   {dest.name}")
        return dest
    print(f"  fetch    {WEIGHTS_URL.format(r=rating)}")
    urllib.request.urlretrieve(WEIGHTS_URL.format(r=rating), dest)
    return dest


def convert(lc0: str, src: Path, dest: Path, dtype: str) -> None:
    result = subprocess.run(
        [
            lc0, "leela2onnx",
            f"--input={src}",
            f"--output={dest}",
            f"--onnx-data-type={dtype}",
            "--onnx-opset=17",
        ],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0 or not dest.exists():
        sys.stderr.write(result.stdout + result.stderr)
        raise SystemExit(f"leela2onnx failed for {src.name}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--dtype",
        default="f32",
        choices=("f32", "f16"),
        help="f32 is what ONNX Runtime's CPU backend executes natively; f16 halves "
             "the asset size but makes the runtime insert casts (default: f32)",
    )
    parser.add_argument("--force", action="store_true", help="re-export nets that already exist")
    args = parser.parse_args()

    lc0 = shutil.which("lc0")
    if lc0 is None:
        raise SystemExit("lc0 not on PATH. Install it with: brew install lc0")

    OUT.mkdir(parents=True, exist_ok=True)
    for rating in RATINGS:
        print(f"maia-{rating}")
        dest = OUT / f"maia-{rating}.onnx"
        if dest.exists() and dest.stat().st_size > 0 and not args.force:
            print(f"  exists   {dest.name}")
            continue
        convert(lc0, download(rating), dest, args.dtype)
        print(f"  wrote    {dest.name}  ({dest.stat().st_size / 1e6:.1f} MB)")

    total = sum(f.stat().st_size for f in OUT.glob("*.onnx"))
    print(f"\nFive nets in {OUT}  ({total / 1e6:.1f} MB total)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Generate index.json + index.min.json from built extension APKs.

Reads each APK's manifest via `aapt2 dump xmltree` (aapt2 ships with the Android SDK build-tools;
set ANDROID_HOME or put aapt2 on PATH). Produces the array shape the app's MangaExtensionApi
deserializer expects: {name, pkg, apk, lang, code, version, nsfw, sources[]}.

LIMITATION: the per-source `sources[]` array (id/lang/name/baseUrl) cannot be derived from the
manifest alone — it requires loading the APK's dex and instantiating the Source classes (the
"Inspector" step in the keiyoushi/yuzono model). This script emits sources: [] as a placeholder;
wire an Inspector before relying on pre-install source listings. See SETUP.md.

Usage: python3 tools/create_repo.py <apk_dir> <index.json> <index.min.json>
"""
import json
import os
import re
import shutil
import subprocess
import sys


def find_aapt2() -> str:
    exe = "aapt2.exe" if os.name == "nt" else "aapt2"
    on_path = shutil.which("aapt2") or shutil.which(exe)
    if on_path:
        return on_path
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk:
        bt = os.path.join(sdk, "build-tools")
        if os.path.isdir(bt):
            for ver in sorted(os.listdir(bt), reverse=True):
                cand = os.path.join(bt, ver, exe)
                if os.path.isfile(cand):
                    return cand
    sys.exit("aapt2 not found. Install Android build-tools and set ANDROID_HOME, or add aapt2 to PATH.")


def manifest_tree(aapt2: str, apk: str) -> str:
    return subprocess.run(
        [aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk],
        capture_output=True, text=True, check=True,
    ).stdout


def attr(tree: str, name: str) -> str | None:
    # aapt2 xmltree lines look like:  A: android:versionName(0x...)="1.5.1" (Raw: "1.5.1")
    m = re.search(rf'{re.escape(name)}\([^)]*\)=(?:"([^"]*)"|\(type [^)]*\)0x([0-9a-fA-F]+))', tree)
    if not m:
        return None
    return m.group(1) if m.group(1) is not None else str(int(m.group(2), 16))


def meta_value(tree: str, key: str) -> str | None:
    # Find a <meta-data> element whose android:name == key, then read its android:value in the same block.
    blocks = re.split(r'\n(?=\s*E: )', tree)
    for b in blocks:
        if "meta-data" in b and attr(b, "android:name") == key:
            return attr(b, "android:value")
    return None


def main() -> None:
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    apk_dir, index_path, index_min_path = sys.argv[1:4]
    aapt2 = find_aapt2()

    entries = []
    for fn in sorted(os.listdir(apk_dir)):
        if not fn.endswith(".apk"):
            continue
        apk = os.path.join(apk_dir, fn)
        tree = manifest_tree(aapt2, apk)
        pkg = attr(tree, "package")
        ext_class = meta_value(tree, "tachiyomi.extension.class")
        nsfw = meta_value(tree, "tachiyomi.extension.nsfw")
        entries.append({
            "name": (attr(tree, "android:label") or pkg or fn).removeprefix("Tachiyomi: "),
            "pkg": pkg,
            "apk": fn,
            "lang": fn.split(".")[0].split("-")[0] if "-" in fn else "all",
            "code": int(attr(tree, "android:versionCode") or 0),
            "version": attr(tree, "android:versionName") or "0",
            "nsfw": int(nsfw) if nsfw and nsfw.isdigit() else 0,
            "sources": [],  # TODO: fill via an Inspector step (loads dex → id/lang/name/baseUrl)
            "_extClass": ext_class,  # diagnostic; harmless extra field
        })

    with open(index_path, "w", encoding="utf-8") as f:
        json.dump(entries, f, indent=2, ensure_ascii=False)
    with open(index_min_path, "w", encoding="utf-8") as f:
        json.dump(entries, f, separators=(",", ":"), ensure_ascii=False)
    print(f"Wrote {len(entries)} entries to {index_path} and {index_min_path}")


if __name__ == "__main__":
    main()

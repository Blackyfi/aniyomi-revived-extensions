#!/usr/bin/env python3
"""Generate index.json + index.min.json from built extension APKs.

Reads each APK's manifest via `aapt2 dump xmltree` (aapt2 ships with the Android SDK build-tools;
set ANDROID_HOME or put aapt2 on PATH). Produces the array shape the app's MangaExtensionApi
deserializer expects: {name, pkg, apk, lang, code, version, nsfw, sources[]}.

The per-source `sources[]` array (id/lang/name/baseUrl) is filled by the Inspector step
(tools/inspector.py). Rather than load the APK dex and instantiate Source classes on a device
(the keiyoushi/yuzono model — impossible here: no device, and the sources are ConfigurableSource
with injekt/Android-backed constructors), the Inspector statically reads the module's Kotlin and
recomputes each source id with the EXACT source-api formula (HttpSource.kt:57,87-91). For a
SourceFactory it emits one source per language variant. Pass --src-root <dir> (the repo root that
contains src/<parent>/<module>) to enable; without it, sources[] stays empty.

Usage: python3 tools/create_repo.py <apk_dir> <index.json> <index.min.json> [--src-root <dir>]
"""
import json
import os
import re
import shutil
import subprocess
import sys

import inspector as _inspector


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
    # aapt2 xmltree attribute lines take several forms:
    #   A: android:versionName(0x0101021c)="1.5.2" (Raw: "1.5.2")   -> quoted string
    #   A: android:versionCode(0x0101021b)=2                         -> bare int (no (type)/0x)
    #   A: android:required(0x0101028e)=(type 0x12)0x0               -> typed hex
    #   A: package="eu.kanade...."                                   -> no (0x..) resource id
    # The attr name is matched at a word boundary so the `android:` short form still matches the
    # fully-qualified `http://schemas.android.com/apk/res/android:<name>` rendering.
    m = re.search(
        rf'{re.escape(name)}(?:\([^)]*\))?='
        rf'(?:"([^"]*)"|\(type [^)]*\)0x([0-9a-fA-F]+)|(-?\d+))',
        tree,
    )
    if not m:
        return None
    if m.group(1) is not None:      # quoted string
        return m.group(1)
    if m.group(2) is not None:      # typed hex
        return str(int(m.group(2), 16))
    return m.group(3)               # bare integer


def meta_value(tree: str, key: str) -> str | None:
    # Find a <meta-data> element whose android:name == key, then read its android:value in the same block.
    blocks = re.split(r'\n(?=\s*E: )', tree)
    for b in blocks:
        if "meta-data" in b and attr(b, "android:name") == key:
            return attr(b, "android:value")
    return None


_PKG_PREFIX = "eu.kanade.tachiyomi.extension."


def module_dir_for(src_root: str, pkg: str | None) -> str | None:
    """Map an extension package to its module source dir under <src_root>/src.

    pkg = eu.kanade.tachiyomi.extension.<parent>.<module>  (common.gradle:53)
        -> <src_root>/src/<parent>/<module>
    """
    if not src_root or not pkg or not pkg.startswith(_PKG_PREFIX):
        return None
    tail = pkg[len(_PKG_PREFIX):].split(".")
    if len(tail) < 2:
        return None
    parent, module = tail[0], tail[1]
    cand = os.path.join(src_root, "src", parent, module)
    return cand if os.path.isdir(cand) else None


def main() -> None:
    args = sys.argv[1:]
    src_root = None
    if "--src-root" in args:
        i = args.index("--src-root")
        src_root = args[i + 1]
        del args[i:i + 2]
    if len(args) != 3:
        sys.exit(__doc__)
    apk_dir, index_path, index_min_path = args
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

        # Top-level lang = the source's <parent> dir (src/<parent>/<module>), encoded in the
        # package (common.gradle:53). Falls back to the APK-filename heuristic for non-standard pkgs.
        pkg_parent = (
            pkg[len(_PKG_PREFIX):].split(".")[0]
            if pkg and pkg.startswith(_PKG_PREFIX) and "." in pkg[len(_PKG_PREFIX):]
            else None
        )

        # Inspector: fill sources[] (id/lang/name/baseUrl) by static analysis of the module.
        sources = []
        mod_dir = module_dir_for(src_root, pkg)
        if mod_dir:
            sources = _inspector.inspect_module(mod_dir, ext_class)

        entries.append({
            "name": (attr(tree, "android:label") or pkg or fn).removeprefix("Tachiyomi: "),
            "pkg": pkg,
            "apk": fn,
            "lang": pkg_parent or (fn.split(".")[0].split("-")[0] if "-" in fn else "all"),
            "code": int(attr(tree, "android:versionCode") or 0),
            "version": attr(tree, "android:versionName") or "0",
            "nsfw": int(nsfw) if nsfw and nsfw.isdigit() else 0,
            "sources": sources,
            "_extClass": ext_class,  # diagnostic; harmless extra field
        })

    with open(index_path, "w", encoding="utf-8") as f:
        json.dump(entries, f, indent=2, ensure_ascii=False)
    with open(index_min_path, "w", encoding="utf-8") as f:
        json.dump(entries, f, separators=(",", ":"), ensure_ascii=False)
    print(f"Wrote {len(entries)} entries to {index_path} and {index_min_path}")


if __name__ == "__main__":
    main()

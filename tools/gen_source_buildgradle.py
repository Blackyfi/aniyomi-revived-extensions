#!/usr/bin/env python3
"""Generate EXT-convention build.gradle for vendored keiyoushi source modules.

For each src/<lang>/<name> that still carries keiyoushi's kei.plugins build.gradle,
rewrite it to the EXT convention (ext{} + apply from common.gradle + deps). The
compile step is the validator; this just does the mechanical transform.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src")

# keiyoushi build.gradle field patterns
def field(text, name, quoted=True):
    if quoted:
        m = re.search(rf"{name}\s*=\s*['\"](.*?)['\"]", text)
    else:
        m = re.search(rf"{name}\s*=\s*([\w.]+)", text)
    return m.group(1) if m else None

def uses_core(moddir):
    for dp, _, files in os.walk(os.path.join(moddir, "src")):
        for f in files:
            if f.endswith(".kt"):
                try:
                    with open(os.path.join(dp, f), encoding="utf-8", errors="ignore") as fh:
                        if "keiyoushi.utils" in fh.read():
                            return True
                except OSError:
                    pass
    return False

generated, skipped, themed, standalone = 0, 0, 0, 0
for lang in sorted(os.listdir(SRC)):
    langdir = os.path.join(SRC, lang)
    if not os.path.isdir(langdir):
        continue
    for name in sorted(os.listdir(langdir)):
        moddir = os.path.join(langdir, name)
        bg = os.path.join(moddir, "build.gradle")
        if not os.path.isfile(bg):
            continue
        with open(bg, encoding="utf-8", errors="ignore") as fh:
            text = fh.read()
        # Only rewrite keiyoushi-style builds (skip ones already converted).
        if "apply from:" in text and "common.gradle" in text:
            skipped += 1
            continue
        ext_name = field(text, "extName")
        ext_class = field(text, "extClass")
        if not ext_name or not ext_class:
            skipped += 1
            continue
        vc = field(text, "extVersionCode", quoted=False) or field(text, "overrideVersionCode", quoted=False) or "1"
        is_nsfw = (field(text, "isNsfw", quoted=False) or "false").strip()
        theme = field(text, "themePkg")

        deps = []
        if theme:
            deps.append(f"lib-multisrc:{theme}")
            themed_flag = True
        else:
            themed_flag = False
        # project deps declared in the original dependencies block
        for ref in re.findall(r"project\(\s*['\"]:([^'\"]+)['\"]\s*\)", text):
            deps.append(ref)
        if uses_core(moddir):
            deps.append("core")
        # dedupe, keep order
        seen = set()
        proj_deps = []
        for d in deps:
            if d not in seen:
                seen.add(d)
                proj_deps.append(d)
        # external maven deps from the original (carry verbatim with their config)
        ext_deps = re.findall(r"(implementation|compileOnly|api)\(\s*['\"]([\w.\-]+:[\w.\-]+:[\w.\-]+)['\"]\s*\)", text)

        lines = ["ext {", f"    extName = '{ext_name}'", f"    extClass = '{ext_class}'",
                 f"    extVersionCode = {vc}", f"    isNsfw = {is_nsfw}", "}", "",
                 'apply from: "$rootDir/common.gradle"', ""]
        dep_lines = [f"    implementation project(':{d}')" for d in proj_deps]
        for cfg, coord in ext_deps:
            cfg2 = "compileOnly" if cfg == "compileOnly" else "implementation"
            dep_lines.append(f'    {cfg2} "{coord}"')
        if dep_lines:
            lines.append("dependencies {")
            lines.extend(dep_lines)
            lines.append("}")
            lines.append("")
        with open(bg, "w", encoding="utf-8") as fh:
            fh.write("\n".join(lines))
        generated += 1
        themed += 1 if themed_flag else 0
        standalone += 0 if themed_flag else 1

print(f"generated={generated} (themed={themed} standalone={standalone}) skipped={skipped}")

#!/usr/bin/env python3
"""Inject external Maven deps into source build.gradle based on .kt imports.

keiyoushi declares some deps via version-catalog aliases the source generator couldn't resolve
(protobuf, quickjs, metadata-extractor). Detect their use by import and add the literal coordinate
as `implementation` (compile + bundle, since the host doesn't provide these).
"""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src")

# import-substring -> dependency line
RULES = {
    "kotlinx.serialization.protobuf": 'implementation "org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.9.0"',
    "app.cash.quickjs": 'implementation "app.cash.quickjs:quickjs-android:0.9.2"',
    "import com.drew": 'implementation "com.drewnoakes:metadata-extractor:2.18.0"',
}

def imports_blob(moddir):
    blob = []
    for dp, _, files in os.walk(os.path.join(moddir, "src")):
        for f in files:
            if f.endswith(".kt"):
                try:
                    with open(os.path.join(dp, f), encoding="utf-8", errors="ignore") as fh:
                        blob.append(fh.read())
                except OSError:
                    pass
    return "\n".join(blob)

changed = 0
for lang in sorted(os.listdir(SRC)):
    ld = os.path.join(SRC, lang)
    if not os.path.isdir(ld):
        continue
    for name in sorted(os.listdir(ld)):
        moddir = os.path.join(ld, name)
        bg = os.path.join(moddir, "build.gradle")
        if not os.path.isfile(bg):
            continue
        blob = imports_blob(moddir)
        needed = [dep for key, dep in RULES.items() if key in blob]
        if not needed:
            continue
        with open(bg, encoding="utf-8") as fh:
            text = fh.read()
        needed = [d for d in needed if d not in text]
        if not needed:
            continue
        add = "\n".join("    " + d for d in needed)
        if "dependencies {" in text:
            text = text.replace("dependencies {", "dependencies {\n" + add, 1)
        else:
            text = text.rstrip() + "\n\ndependencies {\n" + add + "\n}\n"
        with open(bg, "w", encoding="utf-8") as fh:
            fh.write(text)
        changed += 1

print(f"injected external deps into {changed} source build.gradle files")

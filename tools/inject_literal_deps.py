#!/usr/bin/env python3
"""Carry literal external Maven coords from each keiyoushi ORIGINAL build.gradle into the EXT one.

The source generator only matched `config("group:art:ver")` (paren) deps; keiyoushi mostly uses the
Groovy `config 'group:art:ver'` (no-paren) form, so those were dropped. Re-read the originals and
inject any missing external coordinate (skip project()/catalog refs and the baseline libs already in
common.gradle).
"""
import os
import re

EXT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KEI = os.path.join(os.path.dirname(EXT), "keiyoushi-extensions-source")
ESRC = os.path.join(EXT, "src")

# already provided by common.gradle / themes — don't re-add
SKIP = ("okhttp", "jsoup", "kotlinx-serialization", "kotlinx-coroutines", "androidx.preference", "rxjava")
COORD = re.compile(r"\b(implementation|compileOnly|api)\b\s*\(?\s*['\"]([\w.\-]+:[\w.\-]+:[\w.\-]+)['\"]")

changed = 0
for lang in sorted(os.listdir(ESRC)):
    ld = os.path.join(ESRC, lang)
    if not os.path.isdir(ld):
        continue
    for name in sorted(os.listdir(ld)):
        korig = os.path.join(KEI, "src", lang, name, "build.gradle")
        ebg = os.path.join(ESRC, lang, name, "build.gradle")
        if not (os.path.isfile(korig) and os.path.isfile(ebg)):
            continue
        with open(korig, encoding="utf-8", errors="ignore") as fh:
            otext = fh.read()
        coords = [(c, coord) for c, coord in COORD.findall(otext) if not any(s in coord for s in SKIP)]
        if not coords:
            continue
        with open(ebg, encoding="utf-8") as fh:
            etext = fh.read()
        add = []
        for cfg, coord in coords:
            artifact = coord.rsplit(":", 1)[0]
            if artifact in etext:  # already present (any version)
                continue
            cfg2 = "compileOnly" if cfg == "compileOnly" else "implementation"
            add.append(f'    {cfg2} "{coord}"')
        if not add:
            continue
        block = "\n".join(add)
        if "dependencies {" in etext:
            etext = etext.replace("dependencies {", "dependencies {\n" + block, 1)
        else:
            etext = etext.rstrip() + "\n\ndependencies {\n" + block + "\n}\n"
        with open(ebg, "w", encoding="utf-8") as fh:
            fh.write(etext)
        changed += 1

print(f"injected literal deps into {changed} source build.gradle files")

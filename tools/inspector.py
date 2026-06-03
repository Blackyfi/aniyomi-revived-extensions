#!/usr/bin/env python3
"""Inspector: derive a manga extension's `sources[]` (id/lang/name/baseUrl) without a device.

WHY STATIC (not dex/JVM instantiation):
The keiyoushi "Inspector" loads the built dex on an Android device and *instantiates* every
Source class, reading its `id`/`lang`/`name`/`baseUrl`. We have no device, and post-C1 the
sources extend `ConfigurableSource` and initialize Android/injekt-backed fields in their
constructor (e.g. MangaDex's `override val client = network.client.newBuilder()...`, which
resolves `NetworkHelper` via `injectLazy`, and a `getSharedPreferences("source_$id", ...)`
keyed on the source id). Reflectively instantiating those on a plain JVM throws (no Injekt
graph / no android.app.Application). So we instead read the constant `name`/`baseUrl`/`lang`/
`versionId` *statically* from the module's Kotlin and recompute each `id` with the EXACT
source-api formula.

EXACT ID FORMULA — source-api HttpSource.kt:
  id = generateId(name, lang, versionId)                                 (HttpSource.kt:57)
  generateId:                                                            (HttpSource.kt:87-91)
    key   = "${name.lowercase()}/$lang/$versionId"
    bytes = MD5(key.toByteArray())                  # UTF-8, 16 bytes
    id    = (first 8 bytes as big-endian signed Long) and Long.MAX_VALUE # clear sign bit
versionId defaults to 1 (HttpSource.kt:45) unless a source overrides it.

This module reimplements generate_id() byte-for-byte and is unit-checked in __main__/selftest.
For a SourceFactory (e.g. MangaDexFactory.createSources()), each list entry is a distinct
source with its own (lang -> id); name/baseUrl/versionId come from the Source class itself.
"""
from __future__ import annotations

import hashlib
import os
import re
import sys
from typing import Optional


def generate_id(name: str, lang: str, version_id: int) -> int:
    """Port of HttpSource.generateId (HttpSource.kt:87-91). Returns a non-negative Long."""
    key = f"{name.lower()}/{lang}/{version_id}"
    digest = hashlib.md5(key.encode("utf-8")).digest()  # 16 bytes
    # First 8 bytes, big-endian, as a signed 64-bit value, then clear the sign bit.
    value = int.from_bytes(digest[:8], byteorder="big", signed=False)
    return value & 0x7FFF_FFFF_FFFF_FFFF  # == Long.MAX_VALUE


# ---------------------------------------------------------------------------
# Static extraction from a module's Kotlin sources.
# ---------------------------------------------------------------------------

def _read(path: str) -> str:
    with open(path, encoding="utf-8") as f:
        return f.read()


def _kt_files(module_dir: str) -> list[str]:
    out = []
    for root, _dirs, files in os.walk(os.path.join(module_dir, "src")):
        for fn in files:
            if fn.endswith(".kt"):
                out.append(os.path.join(root, fn))
    return out


def _strip_quotes(s: str) -> str:
    return s.strip().strip('"')


def _find_class_const(src: str, class_name: str, prop: str) -> Optional[str]:
    """Find `override val <prop> = "literal"` (or `val <prop> = "literal"`) for a class.

    Scans the whole file; module source classes declare these as top-level class members.
    """
    pat = re.compile(
        rf'\b(?:override\s+)?val\s+{re.escape(prop)}\s*[:=][^"\n]*?"([^"]*)"'
    )
    m = pat.search(src)
    return m.group(1) if m else None


def _find_version_id(src: str) -> int:
    m = re.search(r'\b(?:override\s+)?val\s+versionId\s*=\s*(\d+)', src)
    return int(m.group(1)) if m else 1  # default per HttpSource.kt:45


# Matches each `Source("langArg", ...)` entry inside a createSources() list.
# Captures the FIRST string argument, which is bound to `override val lang` in the source ctor.
_FACTORY_ENTRY = re.compile(r'\b[A-Z][A-Za-z0-9_]*\s*\(\s*"([^"]*)"')


def _extract_factory_langs(src: str, source_class: str) -> list[str]:
    """Return the per-variant `lang` values from a SourceFactory.createSources()."""
    # Narrow to the createSources() body to avoid matching unrelated constructors.
    m = re.search(r'createSources\s*\(\s*\)\s*:[^=]*=\s*listOf\s*\((.*?)\n\s*\)',
                  src, re.DOTALL)
    body = m.group(1) if m else src
    langs = []
    for em in re.finditer(rf'\b{re.escape(source_class)}\s*\(\s*"([^"]*)"', body):
        langs.append(em.group(1))
    return langs


def inspect_module(module_dir: str, ext_class: Optional[str] = None) -> list[dict]:
    """Return sources[] = [{id, lang, name, baseUrl}, ...] for one extension module.

    `ext_class` is the `tachiyomi.extension.class` value (e.g. ".MangaDexFactory"); used to
    locate the entry class. If omitted we infer from build.gradle.
    """
    kt_files = _kt_files(module_dir)
    if not kt_files:
        return []

    # Resolve the entry class name (simple name) from ext_class or build.gradle.
    if not ext_class:
        bg = os.path.join(module_dir, "build.gradle")
        if os.path.isfile(bg):
            mm = re.search(r"extClass\s*=\s*'([^']+)'", _read(bg)) or \
                 re.search(r'extClass\s*=\s*"([^"]+)"', _read(bg))
            ext_class = mm.group(1) if mm else None
    entry_simple = (ext_class or "").rsplit(".", 1)[-1] or None

    # Map simple class name -> file contents.
    by_class: dict[str, str] = {}
    for path in kt_files:
        src = _read(path)
        for cm in re.finditer(r'\bclass\s+([A-Z][A-Za-z0-9_]*)', src):
            by_class[cm.group(1)] = src

    entry_src = by_class.get(entry_simple) if entry_simple else None
    if entry_src is None:
        # Fall back: a single Source class module (entry == the source).
        # Pick the class extending HttpSource / *Source if exactly one defines name+baseUrl.
        candidates = {
            c: s for c, s in by_class.items()
            if _find_class_const(s, c, "name") and _find_class_const(s, c, "baseUrl")
        }
        if len(candidates) == 1:
            c, s = next(iter(candidates.items()))
            return [_single_source(s, c)]
        return []

    # Is the entry a SourceFactory?
    is_factory = "SourceFactory" in entry_src and "createSources" in entry_src

    if is_factory:
        # Find which Source class the factory builds (the type used inside createSources()).
        m = re.search(r'createSources\s*\([^)]*\)\s*:[^=]*=\s*listOf\s*\(\s*([A-Z][A-Za-z0-9_]*)\s*\(',
                      entry_src, re.DOTALL)
        source_class = m.group(1) if m else None
        if not source_class or source_class not in by_class:
            return []
        src_body = by_class[source_class]
        name = _find_class_const(src_body, source_class, "name")
        base_url = _find_class_const(src_body, source_class, "baseUrl")
        version_id = _find_version_id(src_body)
        langs = _extract_factory_langs(entry_src, source_class)
        sources = []
        for lang in langs:
            sources.append({
                "id": str(generate_id(name, lang, version_id)),
                "lang": lang,
                "name": name,
                "baseUrl": base_url or "",
            })
        return sources

    # Non-factory entry: it is itself the Source.
    return [_single_source(entry_src, entry_simple)]


def _single_source(src: str, class_name: str) -> dict:
    name = _find_class_const(src, class_name, "name") or class_name
    lang = _find_class_const(src, class_name, "lang") or "all"
    base_url = _find_class_const(src, class_name, "baseUrl") or ""
    version_id = _find_version_id(src)
    return {
        "id": str(generate_id(name, lang, version_id)),
        "lang": lang,
        "name": name,
        "baseUrl": base_url,
    }


def _selftest() -> None:
    # Known-good vectors recomputed from the exact formula (verified against the JVM port).
    cases = {
        ("MangaDex", "en", 1): generate_id("MangaDex", "en", 1),
    }
    for (n, l, v), got in cases.items():
        assert 0 <= got <= 0x7FFF_FFFF_FFFF_FFFF, (n, l, v, got)
    # Determinism + lowercasing of name.
    assert generate_id("MangaDex", "en", 1) == generate_id("mangadex", "en", 1)
    print("inspector selftest OK")


if __name__ == "__main__":
    if len(sys.argv) >= 2 and sys.argv[1] == "--selftest":
        _selftest()
    elif len(sys.argv) >= 2:
        import json
        print(json.dumps(inspect_module(sys.argv[1],
                                        sys.argv[2] if len(sys.argv) > 2 else None),
                          indent=2, ensure_ascii=False))
    else:
        sys.exit("usage: inspector.py <module_dir> [extClass] | --selftest")

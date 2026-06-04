#!/usr/bin/env python3
"""Extract each extension APK's launcher icon to <icon_out_dir>/<pkg>.png.

The app fetches extension icons from "$repoUrl/icon/<pkg>.png"
(MangaExtensionApi.kt:129). This script derives <pkg> and the launcher-icon
resource from each APK using `aapt2` (ships with the Android SDK build-tools),
picks the best PNG it can find, and writes it to <icon_out_dir>/<pkg>.png.

Resolution logic (in order):
  1. `aapt2 dump badging` -> package name + the `application:` icon resource path.
  2. Parse `aapt2 dump resources` once into {res_id -> [(density, path)]} and a
     name -> res_id map (so adaptive-icon drawable references can be resolved).
  3. If the icon resource is a raster (png/webp), pick the highest-density file.
  4. If it is an `<adaptive-icon>` XML, read its xmltree, resolve the
     <foreground> (then <background>, then <monochrome>) drawable reference to a
     raster and pick the highest density. If those are themselves XML/vector,
     fall back to the `ic_launcher_round` then any `ic_launcher*` raster mipmap.
  5. Highest density = the largest of {ldpi<mdpi<hdpi<xhdpi<xxhdpi<xxxhdpi<nodpi};
     PNG is preferred over WEBP at equal density.

Output is always written as <pkg>.png:
  - A chosen .png file is copied verbatim.
  - A chosen .webp file is converted to PNG with Pillow if available; if Pillow
    is missing, a clear warning is logged and that APK is skipped (never crash).
  - If no raster launcher icon exists at all (e.g. a fully vector adaptive icon),
    a clear warning is logged and that APK is skipped.

Dependency-light: stdlib `zipfile` + `subprocess` only. Pillow is optional and
used solely for WEBP->PNG conversion.

Usage: python tools/extract_icons.py <apk_dir> <icon_out_dir>
"""
import os
import re
import shutil
import subprocess
import sys
import zipfile

# Density qualifier -> rank (higher is better). `nodpi`/`anydpi` are treated as
# top-tier because they are density-independent full-resolution assets.
DENSITY_RANK = {
    "ldpi": 1,
    "mdpi": 2,
    "tvdpi": 3,
    "hdpi": 4,
    "xhdpi": 5,
    "xxhdpi": 6,
    "xxxhdpi": 7,
    "nodpi": 8,
    "anydpi": 8,
    "": 0,  # density-less (e.g. res/mipmap/foo.png with no qualifier)
}

RASTER_EXTS = (".png", ".webp")


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


def aapt2_run(aapt2: str, *args: str) -> str:
    # Capture bytes and decode as UTF-8 with replacement: aapt2 emits non-ASCII
    # bytes (labels, embedded data) that Windows' default cp1252 text mode can't
    # decode. errors="replace" keeps the ASCII structure we parse intact.
    out = subprocess.run(
        [aapt2, *args], capture_output=True, check=True,
    ).stdout
    return out.decode("utf-8", errors="replace")


def badging(aapt2: str, apk: str) -> tuple[str | None, str | None]:
    """Return (package_name, icon_resource_path) from `aapt2 dump badging`."""
    out = aapt2_run(aapt2, "dump", "badging", apk)
    pkg = None
    icon = None
    m = re.search(r"package: name='([^']+)'", out)
    if m:
        pkg = m.group(1)
    # The `application:` line carries the resolved default icon path.
    m = re.search(r"application:\s+label='[^']*'\s+icon='([^']*)'", out)
    if m and m.group(1):
        icon = m.group(1)
    if not icon:
        # Fall back to the highest density-specific application-icon line.
        best_rank = -1
        for d, path in re.findall(r"application-icon-(\d+):'([^']+)'", out):
            # Higher pixel density numbers win; 65534/65535 are anydpi/nodpi sentinels.
            rank = int(d)
            if rank > best_rank and path:
                best_rank, icon = rank, path
    return pkg, icon


def density_of(path: str) -> str:
    """Extract the density qualifier from a res/<dir>/<file> path, '' if none."""
    parts = path.split("/")
    if len(parts) < 2:
        return ""
    folder = parts[-2]  # e.g. mipmap-xxxhdpi-v4 or mipmap
    for seg in folder.split("-"):
        if seg in DENSITY_RANK and seg not in ("mipmap", "drawable"):
            return seg
    return ""


def parse_resources(aapt2: str, apk: str):
    """Parse `aapt2 dump resources` into lookup tables.

    Returns:
      by_id:   {res_id_int -> [(path, density_rank, is_png), ...]}
      name_to_id: {(type, name) -> res_id_int}  e.g. ('mipmap','ic_launcher')
    """
    out = aapt2_run(aapt2, "dump", "resources", apk)
    by_id: dict[int, list[tuple[str, int, bool]]] = {}
    name_to_id: dict[tuple[str, str], int] = {}
    cur_id: int | None = None
    # Header line: "resource 0x7f0f0003 mipmap/ic_local_source"
    hdr_re = re.compile(r"resource\s+(0x[0-9a-fA-F]+)\s+([A-Za-z0-9_]+)/([A-Za-z0-9_.]+)")
    # File line: "(xxxhdpi) (file) res/mipmap-xxxhdpi-v4/ic_local_source.webp type=PNG"
    # density group is optional (some entries have no qualifier).
    file_re = re.compile(r"(?:\(([A-Za-z0-9]+)\)\s+)?\(file\)\s+(\S+)")
    for line in out.splitlines():
        h = hdr_re.search(line)
        if h:
            cur_id = int(h.group(1), 16)
            name_to_id[(h.group(2), h.group(3))] = cur_id
            by_id.setdefault(cur_id, [])
            continue
        if cur_id is None:
            continue
        f = file_re.search(line)
        if f and "(file)" in line:
            density_q = f.group(1) or ""
            path = f.group(2)
            ext = os.path.splitext(path)[1].lower()
            if ext not in RASTER_EXTS:
                # Could still be an XML/vector; record path so adaptive resolution
                # can detect "only-XML" and fall back. Use rank -1 to deprioritize.
                by_id[cur_id].append((path, -1, False))
                continue
            # Prefer the path's own folder density; aapt's printed (density) and
            # the folder usually agree, but the folder is canonical.
            rank = DENSITY_RANK.get(density_of(path), DENSITY_RANK.get(density_q, 0))
            by_id[cur_id].append((path, rank, ext == ".png"))
    return by_id, name_to_id


def best_raster(files: list[tuple[str, int, bool]]) -> str | None:
    """Pick the best raster path: highest density, PNG over WEBP on a tie."""
    rasters = [f for f in files if f[1] >= 0]  # rank -1 marks non-raster (XML)
    if not rasters:
        return None
    # Sort key: density rank desc, then png-preferred desc.
    rasters.sort(key=lambda t: (t[1], 1 if t[2] else 0), reverse=True)
    return rasters[0][0]


def adaptive_refs(aapt2: str, apk: str, xml_path: str) -> list[int]:
    """If xml_path is an <adaptive-icon>, return referenced drawable res IDs
    in preference order: foreground, background, monochrome."""
    try:
        tree = aapt2_run(aapt2, "dump", "xmltree", "--file", xml_path, apk)
    except subprocess.CalledProcessError:
        return []
    if "adaptive-icon" not in tree:
        return []
    refs: dict[str, int] = {}
    cur_elem = None
    for line in tree.splitlines():
        e = re.search(r"E:\s+(\w+)", line)
        if e:
            cur_elem = e.group(1)
        # A: ...:drawable(0x...)=@0x7f080105
        a = re.search(r"drawable\([^)]*\)=@(0x[0-9a-fA-F]+)", line)
        if a and cur_elem in ("foreground", "background", "monochrome"):
            refs[cur_elem] = int(a.group(1), 16)
    return [refs[k] for k in ("foreground", "background", "monochrome") if k in refs]


def resolve_icon_path(aapt2, apk, icon_path, by_id, name_to_id) -> str | None:
    """Return the in-APK path of the best raster launcher icon, or None."""
    # 1. Direct raster icon. badging reports ONE density-resolved path (often the
    #    device-default density, e.g. hdpi), so look up the owning resource and
    #    pick its highest-density sibling instead of using that path verbatim.
    if icon_path and os.path.splitext(icon_path)[1].lower() in RASTER_EXTS:
        for files in by_id.values():
            if any(p == icon_path for p, _, _ in files):
                return best_raster(files) or icon_path
        return icon_path

    # 2. The icon is an XML. If it's an adaptive-icon, resolve its layers to rasters.
    if icon_path and icon_path.lower().endswith(".xml"):
        for ref_id in adaptive_refs(aapt2, apk, icon_path):
            picked = best_raster(by_id.get(ref_id, []))
            if picked:
                return picked

    # 3. Fall back to known launcher mipmap/drawable names, raster only.
    fallback_names = [
        ("mipmap", "ic_launcher_round"),
        ("mipmap", "ic_launcher"),
        ("mipmap", "ic_launcher_foreground"),
        ("drawable", "ic_launcher_round"),
        ("drawable", "ic_launcher"),
        ("drawable", "ic_launcher_foreground"),
    ]
    for key in fallback_names:
        rid = name_to_id.get(key)
        if rid is not None:
            picked = best_raster(by_id.get(rid, []))
            if picked:
                return picked

    # 4. Last resort: if the named launcher mipmap is an adaptive XML whose
    #    foreground is itself a raster we missed above, try resolving it too.
    for key in (("mipmap", "ic_launcher"), ("mipmap", "ic_launcher_round")):
        rid = name_to_id.get(key)
        if rid is None:
            continue
        for path, rank, _ in by_id.get(rid, []):
            if path.lower().endswith(".xml"):
                for ref_id in adaptive_refs(aapt2, apk, path):
                    picked = best_raster(by_id.get(ref_id, []))
                    if picked:
                        return picked
    return None


def write_png(apk: str, in_path: str, out_png: str) -> bool:
    """Extract in_path from the APK zip and write it as out_png.

    PNG is copied verbatim. WEBP is converted with Pillow if available; if Pillow
    is missing, warn and return False (caller skips). Returns True on success.
    """
    ext = os.path.splitext(in_path)[1].lower()
    with zipfile.ZipFile(apk) as z:
        try:
            data = z.read(in_path)
        except KeyError:
            print(f"  WARNING: '{in_path}' not present in APK zip; skipping.", file=sys.stderr)
            return False

    if ext == ".png":
        with open(out_png, "wb") as f:
            f.write(data)
        return True

    if ext == ".webp":
        try:
            import io

            from PIL import Image
        except ImportError:
            print(
                f"  WARNING: launcher icon is WEBP ('{in_path}') and Pillow is not "
                f"installed; cannot convert to PNG. Skipping {out_png}. "
                f"Install Pillow (pip install Pillow) to enable WEBP->PNG.",
                file=sys.stderr,
            )
            return False
        try:
            img = Image.open(io.BytesIO(data)).convert("RGBA")
            img.save(out_png, format="PNG")
            return True
        except Exception as exc:  # noqa: BLE001 - never crash the whole run
            print(f"  WARNING: failed to convert WEBP '{in_path}' to PNG: {exc}", file=sys.stderr)
            return False

    print(f"  WARNING: unexpected icon format '{in_path}'; skipping.", file=sys.stderr)
    return False


def main() -> None:
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    apk_dir, icon_out_dir = sys.argv[1:3]
    aapt2 = find_aapt2()
    os.makedirs(icon_out_dir, exist_ok=True)

    apks = sorted(fn for fn in os.listdir(apk_dir) if fn.endswith(".apk"))
    if not apks:
        print(f"No .apk files found in {apk_dir}", file=sys.stderr)

    written = 0
    skipped = 0
    for fn in apks:
        apk = os.path.join(apk_dir, fn)
        print(f"{fn}:")
        pkg, icon_path = badging(aapt2, apk)
        if not pkg:
            print("  WARNING: could not read package name; skipping.", file=sys.stderr)
            skipped += 1
            continue
        by_id, name_to_id = parse_resources(aapt2, apk)
        chosen = resolve_icon_path(aapt2, apk, icon_path, by_id, name_to_id)
        if not chosen:
            print(
                f"  WARNING: no raster launcher icon found for {pkg} "
                f"(icon resource: {icon_path or 'unknown'}). Likely a vector-only "
                f"adaptive icon. No {pkg}.png written.",
                file=sys.stderr,
            )
            skipped += 1
            continue
        out_png = os.path.join(icon_out_dir, f"{pkg}.png")
        if write_png(apk, chosen, out_png):
            print(f"  {pkg}.png  <-  {chosen}")
            written += 1
        else:
            skipped += 1

    print(f"\nDone: {written} icon(s) written to {icon_out_dir}, {skipped} skipped.")
    # Non-zero exit only if every APK failed (lets CI surface a total breakage
    # while tolerating individual vector-only icons).
    if apks and written == 0:
        sys.exit(1)


if __name__ == "__main__":
    main()

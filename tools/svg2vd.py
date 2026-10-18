#!/usr/bin/env python3
"""Convert pixelarticons SVGs (MIT, https://pixelarticons.com) to Android VectorDrawable XML.

Usage: python3 tools/svg2vd.py <svg_dir> <out_dir> name1 name2 ...
"""
import re, sys, os

def convert(svg_path, out_path, name):
    src = open(svg_path, encoding="utf-8").read()
    m = re.search(r'viewBox="([\d\s.\-]+)"', src)
    vb = m.group(1).split() if m else ["0", "0", "24", "24"]
    vw, vh = vb[2], vb[3]
    paths = re.findall(r'<path\b([^>]*)/?>', src)
    nodes = []
    for attrs in paths:
        d = re.search(r'\bd="([^"]+)"', attrs)
        if not d:
            continue
        evenodd = 'evenodd' in attrs
        fill = '#FF17181A'
        ft = '\n        android:fillType="evenOdd"' if evenodd else ''
        nodes.append(
            f'    <path\n        android:fillColor="{fill}"{ft}\n'
            f'        android:pathData="{d.group(1)}" />'
        )
    xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="24dp"\n    android:height="24dp"\n'
        f'    android:viewportWidth="{vw}"\n    android:viewportHeight="{vh}">\n'
        + "\n".join(nodes) + "\n</vector>\n"
    )
    open(out_path, "w", encoding="utf-8").write(xml)

if __name__ == "__main__":
    svg_dir, out_dir = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    for name in sys.argv[3:]:
        src = os.path.join(svg_dir, name + ".svg")
        if not os.path.exists(src):
            print("MISSING:", name)
            continue
        convert(src, os.path.join(out_dir, f"ic_pi_{name.replace('-', '_')}.xml"), name)
        print("ok:", name)

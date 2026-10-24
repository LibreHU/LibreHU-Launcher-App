#!/usr/bin/env python3
"""
Retro font of the launcher (app/src/main/res/font/librehu_seg14.ttf), made from DSEG14 Classic Bold
(https://github.com/keshikan/DSEG, SIL OFL 1.1, the font of LibreHU-Disc's retro display):

- accented and other Latin letters drawn with their bare letter (é → E, ç → C…): a segment display has no accents,
  the text reads in capitals without them, as on a car stereo's display (DSEG's lower case is already drawn as
  capitals);
- a few punctuation marks with the nearest one the font has (« » “ ” → ", ’ → ', – — → -, [ ] { } → ( ), …);
- renamed "LibreHU Seg14": "DSEG" is a Reserved Font Name, a modified version may not use it (OFL §3).

Usage: python3 tools/make_retro_font.py DSEG14Classic-Bold.ttf app/src/main/res/font/librehu_seg14.ttf
(needs fontTools).
"""
import sys
import unicodedata

from fontTools.ttLib import TTFont

PUNCTUATION = {
    "«": '"', "»": '"', "“": '"', "”": '"', "„": '"',
    "‘": "'", "’": "'", "ʼ": "'", "´": "'",
    "–": "-", "—": "-", "‐": "-", "‑": "-", "−": "-",
    "[": "(", "{": "(", "]": ")", "}": ")",
    "…": ".", "·": ".", "•": ".",
    ";": ":", "×": "X", "÷": "/",
    " ": " ", " ": " ", " ": " ",
    "²": "2", "³": "3", "¹": "1",
    "Æ": "A", "æ": "A", "Œ": "O", "œ": "O", "ß": "S", "Ø": "O", "ø": "O", "Đ": "D", "đ": "D", "Ł": "L", "ł": "L",
}

NAME = "LibreHU Seg14"


def main(src: str, dst: str) -> None:
    font = TTFont(src)
    cmap = font.getBestCmap()
    added = {}
    for code in range(0x00A0, 0x0250):
        ch = chr(code)
        if code in cmap:
            continue
        base = PUNCTUATION.get(ch) or unicodedata.normalize("NFD", ch)[0]
        if base != ch and ord(base) in cmap:
            added[code] = cmap[ord(base)]
    for ch, base in PUNCTUATION.items():
        if ord(ch) not in cmap and ord(base) in cmap:
            added[ord(ch)] = cmap[ord(base)]
    for table in font["cmap"].tables:
        if table.isUnicode():
            table.cmap.update(added)
    for record in font["name"].names:
        if record.nameID in (1, 16):
            record.string = NAME
        elif record.nameID == 3:
            record.string = f"{NAME} Bold"
        elif record.nameID == 4:
            record.string = f"{NAME} Bold"
        elif record.nameID == 6:
            record.string = NAME.replace(" ", "") + "-Bold"
        elif record.nameID == 0:
            record.string = record.toUnicode() + "\nModified for LibreHU Launcher: bare letters for accented ones, renamed."
    font.save(dst)
    print(f"{dst}: {len(added)} characters added")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

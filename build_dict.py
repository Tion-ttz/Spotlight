"""
生成 assets/offline_dictionary.json。

数据来源：
1. dict_source.txt —— 手工整理的常用词（带例句、词性），优先使用；
2. data/ecdict-core.json —— ECDICT 核心 3000 词（MIT License，
   https://github.com/skywind3000/ECDICT），补充音标与中文释义。

输出格式（与 App 端 OfflineDictionary 保持一致）：
{"format":1,"name":"...","count":N,"entries":[[word, phonetic, pos, 中文释义, 例句], ...]}
"""

import json
import os
import re

BASE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(BASE, "app", "src", "main", "assets")
CURATED = os.path.join(ASSETS, "dict_source.txt")
ECDICT = os.path.join(BASE, "data", "ecdict-core.json")
OUT = os.path.join(ASSETS, "offline_dictionary.json")

POS_RE = re.compile(r"^\s*(n|v|vt|vi|adj|adv|ad|a|prep|conj|pron|num|art|int|aux|abbr)\s*\.\s*", re.I)
TAG_RE = re.compile(r"\[[^\]]{1,6}\]")
SPACE_RE = re.compile(r"\s+")


def clean_gloss(raw: str) -> str:
    text = raw.replace("\\r", " ").replace("\\n", "；").replace("\r", " ").replace("\n", "；")
    text = TAG_RE.sub("", text)
    text = text.replace("；；", "；").replace(";;", "；")
    text = SPACE_RE.sub(" ", text)
    text = text.strip(" ；;,")
    if len(text) > 120:
        text = text[:120].rstrip("；; ,") + "…"
    return text


def split_pos(raw: str) -> tuple[str, str]:
    m = POS_RE.match(raw)
    if not m:
        return "", clean_gloss(raw)
    tag = m.group(1).lower()
    tag = {"vt": "v.", "vi": "v."}.get(tag, tag + ".")
    return tag, clean_gloss(raw[m.end():])


def load_curated() -> dict[str, list[str]]:
    entries: dict[str, list[str]] = {}
    if not os.path.exists(CURATED):
        return entries
    with open(CURATED, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip():
                continue
            parts = line.split(";;")
            while len(parts) < 5:
                parts.append("")
            word, phon, pos, zh, ex = [p.strip() for p in parts[:5]]
            if not word:
                continue
            if ex and any("\u4e00" <= c <= "\u9fff" for c in ex):
                ex = ""
            entries[word.lower()] = [word, phon, pos, zh, ex]
    return entries


def load_ecdict() -> list[list[str]]:
    if not os.path.exists(ECDICT):
        print("警告：找不到 ECDICT 数据，只使用手工词表")
        return []
    with open(ECDICT, encoding="utf-8") as f:
        data = json.load(f)
    result = []
    for row in data:
        if len(row) < 4:
            continue
        word, phon, _en, zh = row[0], row[1], row[2], row[3]
        if not word or not zh:
            continue
        pos, meaning = split_pos(zh)
        if not meaning:
            continue
        result.append([word.strip(), phon.strip(), pos, meaning, ""])
    return result


def main() -> None:
    curated = load_curated()
    merged: dict[str, list[str]] = dict(curated)

    added = 0
    for word, phon, pos, meaning, ex in load_ecdict():
        key = word.lower()
        if key in merged:
            # 手工词条优先，但缺音标时用 ECDICT 补上
            if not merged[key][1] and phon:
                merged[key][1] = phon
            continue
        merged[key] = [word, phon, pos, meaning, ex]
        added += 1

    entries = sorted(merged.values(), key=lambda e: e[0].lower())
    data = {
        "format": 1,
        "name": "Spotlight 离线词典（ECDICT 核心 + 手工词表）",
        "count": len(entries),
        "entries": entries,
    }
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")

    with open(OUT, encoding="utf-8") as f:
        back = json.load(f)
    print("curated:", len(curated), "ecdict added:", added, "total:", back["count"])
    print("size KB:", round(os.path.getsize(OUT) / 1024, 1))
    print("sample:", back["entries"][100])


if __name__ == "__main__":
    main()

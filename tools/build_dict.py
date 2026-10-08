#!/usr/bin/env python3
"""
把小麥注音（McBopomofo, MIT）的原始資料轉成本專案的系統詞庫。

用法：
    git clone --depth 1 https://github.com/openvanilla/McBopomofo.git
    python3 tools/build_dict.py McBopomofo/Source/Data app/src/main/assets/zhuyin_dict.tsv

輸出格式（UTF-8，每行一筆，依 count 由大到小排序）：
    詞<TAB>讀音（每字一個音節，以空白分隔）<TAB>count

規則：
- 單字：來自 BPMFBase.txt；次數來自 phrase.occ。
  多音字依 heterophony1/2/3.list 分配權重（主要讀音 1、次要 0.1、第三 0.01、其他 0.001），
  不在名單中的多音字平均分配。
- 詞：來自 BPMFMappings.txt；次數來自 phrase.occ（找不到則為 0）。
- 只使用 Python 標準函式庫，不執行 McBopomofo 的任何程式。
"""
import sys
from collections import defaultdict
from pathlib import Path

TONES = set("ˊˇˋ˙")


def is_syllable(s: str) -> bool:
    return (
        bool(s)
        and all(("ㄅ" <= ch <= "ㄩ") or ch in TONES for ch in s)
        and "ㄅ" <= s[0] <= "ㄩ"  # 至少以一個注音符號開頭（排除單獨的聲調符號）
    )


def load_occ(path: Path) -> dict:
    occ = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if len(parts) == 2 and parts[1].isdigit():
            occ[parts[0]] = int(parts[1])
    return occ


def load_het(data: Path) -> dict:
    rank = {}
    for level, name in ((1, "heterophony1.list"), (2, "heterophony2.list"), (3, "heterophony3.list")):
        for line in (data / name).read_text(encoding="utf-8").splitlines():
            parts = line.split()
            if len(parts) == 2:
                rank[(parts[0], parts[1])] = level
    return rank


def main(data_dir: str, out_path: str) -> None:
    data = Path(data_dir)
    occ = load_occ(data / "phrase.occ")
    het = load_het(data)
    het_chars = {c for (c, _) in het}

    # 單字
    readings = defaultdict(list)
    for line in (data / "BPMFBase.txt").read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if len(parts) < 2:
            continue
        ch, rd = parts[0], parts[1]
        if len(ch) != 1 or not is_syllable(rd) or ("ㄅ" <= ch <= "ㄩ"):
            continue
        if rd not in readings[ch]:
            readings[ch].append(rd)

    rows = {}
    weight = {1: 1.0, 2: 0.1, 3: 0.01}
    for ch, rds in readings.items():
        base = occ.get(ch, 0)
        for rd in rds:
            if len(rds) == 1:
                c = base
            elif ch in het_chars:
                c = base * weight.get(het.get((ch, rd), 0), 0.001)
            else:
                c = base / len(rds)
            rows[(ch, rd)] = int(round(c))

    # 詞
    for line in (data / "BPMFMappings.txt").read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if len(parts) < 3:
            continue
        word, syls = parts[0], parts[1:]
        if len(word) != len(syls) or not all(is_syllable(s) for s in syls):
            continue
        rows[(word, " ".join(syls))] = occ.get(word, 0)

    ordered = sorted(rows.items(), key=lambda kv: (-kv[1], kv[0][0]))
    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8", newline="\n") as f:
        f.write("# Source: McBopomofo (MIT License) https://github.com/openvanilla/McBopomofo\n")
        for (word, rd), c in ordered:
            f.write(f"{word}\t{rd}\t{c}\n")
    print(f"wrote {len(ordered)} entries to {out}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])

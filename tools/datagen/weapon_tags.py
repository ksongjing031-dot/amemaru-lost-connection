"""職業の武器のタグ（tags/items/weapons/<職業>.json）を、装備の定義（equipment/*.json の jobs）に合わせる。

11月25日：「ウィザード系の武器でウィザードにしても使えないものがある」のご指示で見つけた不具合の直し。
2階・4階・5階の武器（古樹の枝杖・祈り手の鈴・守護者シリーズの武器）を作ったとき、定義の jobs には職業を書いたが、
スキルを使うときに見るタグに足し忘れていた（タグにない武器を持つと「職業の武器を持っていない」になる）。
タグを手で書くのをやめ、装備の定義から足す。今あるタグの並びは残し、足りないものを後ろに足す（消さない）。

使い方：python3 tools/datagen/weapon_tags.py         （足りないものを足す）
        python3 tools/datagen/weapon_tags.py --check （足りないものがあれば一覧を出して 1 で終わる）
階や装備を書き出すスクリプトは、最後に sync() を呼ぶ。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "src/main/resources/data/amemaru"
EQUIPMENT = DATA / "amemaru/equipment"
TAGS = DATA / "tags/items/weapons"
JOBS = DATA / "amemaru/jobs"


def wanted() -> dict[str, list[str]]:
    """職業 → その職業が持てる武器（装備の定義の jobs から）。"""
    result: dict[str, list[str]] = {}
    for path in sorted(EQUIPMENT.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("slot") != "weapon":
            continue
        for job in data.get("jobs", []):
            job_name = job.split(":", 1)[1] if ":" in job else job
            result.setdefault(job_name, []).append("amemaru:" + path.stem)
    return result


def missing() -> dict[str, list[str]]:
    """職業 → タグに足りない武器。"""
    result: dict[str, list[str]] = {}
    for job, items in wanted().items():
        path = TAGS / (job + ".json")
        have = set(json.loads(path.read_text(encoding="utf-8"))["values"]) if path.exists() else set()
        lack = [item for item in items if item not in have]
        if lack:
            result[job] = lack
    return result


def sync() -> int:
    """足りない武器をタグに足す。足した数を返す。"""
    added = 0
    for job, lack in missing().items():
        path = TAGS / (job + ".json")
        data = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": False, "values": []}
        data["values"].extend(lack)
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        added += len(lack)
    return added


def main() -> int:
    if "--check" in sys.argv[1:]:
        lack = missing()
        for job, items in sorted(lack.items()):
            print(f"問題：tags/items/weapons/{job}.json に {', '.join(items)} がありません（定義では {job} の武器）")
        return 1 if lack else 0
    print("職業の武器のタグに足したもの：", sync())
    return 0


if __name__ == "__main__":
    sys.exit(main())

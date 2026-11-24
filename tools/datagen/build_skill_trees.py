"""職業ごとのスキルツリー（星座のような節のつながり）を書き出す（11月24日のご指示「スキルツリー少し改変したいです　ステータスも
スキルツリーで一緒に振る形にして、すべてのスキルが取れるではなくビルドによって変化するように」）。

使い方: python3 tools/datagen/build_skill_trees.py [--render 出力フォルダ]

書き出すもの：
- data/amemaru/amemaru/skill_trees/<職業>.json（節・つながり・位置。サーバーとクライアントが読む）
- --render を付けると、ツリーの見本の絵（<職業>.png）も描く

形（【補完】）：
- まん中に職業の核（最初から取れている）。左上と右上へ、2つの流派の枝がのびる。流派の最初のスキル（無料）が核のとなり
- スキル（data/amemaru/amemaru/skills の row・column）を、段（row）ごとに外の輪へ置く。前提のスキル（requires）とつなぐ
- スキルとスキルの間に、ステータスの小さな節（体力・攻撃・防御・魔力・MP・会心・素早さ）を数珠のように置く。1つ1ポイント
- 流派の2段目と4段目の外側に、大きな節（%で上がる・名前つき）。1段目・3段目・5段目では、2つの流派を橋の節でつなぐ
  （橋のまん中も大きな節）。流派をまたぐビルドは、橋を渡って作る
- 奥義は流派の先端（上位職になってから）
節を取るには、となりの節が取れていること（核から道がつながっていること）。ポイントはレベルが上がるごとに2（5の倍数のレベルではさらに1）。
"""
import glob
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
DATA = os.path.join(ROOT, "src/main/resources/data/amemaru/amemaru")
OUT = os.path.join(DATA, "skill_trees")

JOBS = ["knight", "wizard", "healer", "archer", "rogue", "monk"]
# 段の輪の半径（ツリーの単位。画面の倍率1で1ピクセル）と、奥義・先の大きな節の半径
ROW_RADIUS = [95, 175, 255, 335, 415, 495]
ULTIMATE_RADIUS = 590
# 流派の向き（数学の角度。0が右・90が上）。左の流派・右の流派
SCHOOL_ANGLE = [138.0, 42.0]
# 流派の中の列（外側から：足したスキル・主なアクティブ・2つ目のアクティブ・パッシブ）と、列の間の角度
LANES = [3, 0, 1, 2]
LANE_STEP = 17.0
# 横につなぐ段（流派の中の列どうし）と、2つの流派を橋でつなぐ段
RUNG_ROWS = [1, 3, 5]
BRIDGE_ROWS = [2, 4]
# 数珠の節の間（これより長いつながりには、節をはさむ）
BEAD_STEP = 52.0
# ステータスの節の値（内側・中ほど・外側。tools/balance/estimate.py の NODE と同じ）
STAT_VALUES = {"hp": (20, 24, 28), "mp": (10, 12, 14), "attack": (2, 2, 3), "magic": (2, 2, 3), "defense": (2, 2, 3),
               "speed": (1, 1, 2), "crit": (0.8, 1.0, 1.2)}
# 列ごとの数珠の並び（順にくり返す）。3・0・1・2 は列、rung は横のつなぎ、bridge は流派の間の橋、core は核のまわり
BEADS = {
    "knight": {"guardian": {3: ["hp", "defense"], 0: ["defense", "hp"], 1: ["hp", "attack"], 2: ["defense", "mp"], "rung": ["hp"]},
               "greatsword": {3: ["attack", "crit"], 0: ["attack", "hp"], 1: ["crit", "attack"], 2: ["hp", "defense"], "rung": ["attack"]},
               "bridge": ["hp", "defense", "attack"], "core": ["hp"]},
    "wizard": {"starlight": {3: ["magic", "crit"], 0: ["magic", "mp"], 1: ["crit", "magic"], 2: ["mp", "hp"], "rung": ["magic"]},
               "frost": {3: ["magic", "defense"], 0: ["magic", "mp"], 1: ["mp", "magic"], 2: ["hp", "defense"], "rung": ["mp"]},
               "bridge": ["mp", "hp", "magic"], "core": ["mp"]},
    "healer": {"healing": {3: ["magic", "mp"], 0: ["magic", "mp"], 1: ["mp", "hp"], 2: ["hp", "defense"], "rung": ["mp"]},
               "blessing": {3: ["magic", "crit"], 0: ["defense", "magic"], 1: ["magic", "hp"], 2: ["defense", "mp"], "rung": ["hp"]},
               "bridge": ["mp", "hp", "magic"], "core": ["mp"]},
    "archer": {"sniping": {3: ["attack", "crit"], 0: ["attack", "crit"], 1: ["crit", "attack"], 2: ["hp", "mp"], "rung": ["attack"]},
               "rapid": {3: ["speed", "attack"], 0: ["attack", "speed"], 1: ["attack", "crit"], 2: ["hp", "defense"], "rung": ["speed"]},
               "bridge": ["attack", "hp", "crit"], "core": ["attack"]},
    "rogue": {"shadow": {3: ["speed", "attack"], 0: ["attack", "crit"], 1: ["crit", "speed"], 2: ["hp", "defense"], "rung": ["crit"]},
              "venom": {3: ["attack", "mp"], 0: ["attack", "hp"], 1: ["attack", "crit"], 2: ["hp", "mp"], "rung": ["attack"]},
              "bridge": ["speed", "attack", "hp"], "core": ["attack"]},
    "monk": {"hard": {3: ["attack", "crit"], 0: ["attack", "hp"], 1: ["crit", "attack"], 2: ["hp", "defense"], "rung": ["attack"]},
             "soft": {3: ["speed", "attack"], 0: ["hp", "attack"], 1: ["defense", "speed"], 2: ["hp", "mp"], "rung": ["hp"]},
             "bridge": ["hp", "attack", "speed"], "core": ["hp"]},
}
# 大きな節（名前と、上がるもの）。flat は数、percent は%。流派の先（5段目の外側の列・2つ目の列）と、橋のまん中（2段目・4段目）
NOTABLES = {
    "knight": {
        "guardian": [("城塞の心得", {}, {"defense": 8, "hp": 4}), ("揺るがぬ礎", {"hp": 60}, {"defense": 6})],
        "greatsword": [("剛力", {}, {"attack": 6}), ("断ち切る型", {"crit": 3, "crit_damage": 10}, {})],
        "bridge": [("騎士の均衡", {}, {"hp": 4, "attack": 3}), ("誓いの重み", {"defense": 6}, {"hp": 5})],
    },
    "wizard": {
        "starlight": [("星読み", {}, {"magic": 6}), ("流星の眼", {"crit": 3, "crit_damage": 10}, {})],
        "frost": [("凍てる泉", {}, {"mp": 10, "magic": 3}), ("霜の衣", {"defense": 6}, {"hp": 6})],
        "bridge": [("魔力の巡り", {}, {"mp": 8, "magic": 2}), ("深い詠唱", {"cooldown_reduction": 4}, {"magic": 3})],
    },
    "healer": {
        "healing": [("慈しみの手", {"heal_power": 8}, {}), ("満ちる祈り", {}, {"mp": 10, "magic": 3})],
        "blessing": [("加護の衣", {}, {"defense": 8}), ("澄んだ鈴", {"cooldown_reduction": 4}, {"magic": 3})],
        "bridge": [("祈りの均衡", {}, {"hp": 4, "mp": 5}), ("癒し手の芯", {"heal_power": 5}, {"hp": 3})],
    },
    "archer": {
        "sniping": [("鷹の構え", {"crit": 3}, {"attack": 3}), ("一矢の重み", {"crit_damage": 15}, {})],
        "rapid": [("疾風の指", {"speed": 4}, {"attack": 3}), ("矢継ぎ", {"cooldown_reduction": 4}, {"attack": 3})],
        "bridge": [("射手の均衡", {}, {"attack": 4, "hp": 3}), ("張りつめた弦", {"crit": 2}, {"attack": 4})],
    },
    "rogue": {
        "shadow": [("闇の足", {"speed": 4, "crit": 2}, {}), ("急所の心得", {"crit_damage": 15}, {})],
        "venom": [("毒の手練", {}, {"attack": 6}), ("しぶとさ", {"hp": 60}, {"defense": 5})],
        "bridge": [("影と毒", {}, {"attack": 4, "hp": 3}), ("忍ぶ刃", {"crit": 3, "speed": 2}, {})],
    },
    "monk": {
        "hard": [("剛の拳", {}, {"attack": 6}), ("鉄の芯", {"crit": 2}, {"hp": 6})],
        "soft": [("柔の体", {}, {"hp": 6, "defense": 4}), ("流れる気", {"speed": 3, "cooldown_reduction": 3}, {})],
        "bridge": [("剛柔一体", {}, {"attack": 3, "hp": 4}), ("練気", {"defense": 5}, {"attack": 3, "mp": 5})],
    },
}


def load_skills(job):
    out = []
    for path in sorted(glob.glob(os.path.join(DATA, "skills", job, "*.json"))):
        with open(path, encoding="utf-8") as f:
            d = json.load(f)
        d["id"] = job + "/" + os.path.basename(path)[:-5]
        out.append(d)
    return out


def polar(radius, degrees):
    a = math.radians(degrees)
    return radius * math.cos(a), -radius * math.sin(a)


class Tree:
    def __init__(self, job):
        self.job = job
        self.nodes = []
        self.ids = {}
        self.links = set()
        self.bead_index = {}

    def add(self, node):
        if node["id"] in self.ids:
            raise ValueError("同じ節が2つ：" + node["id"])
        node["x"] = round(node["x"], 1)
        node["y"] = round(node["y"], 1)
        self.ids[node["id"]] = node
        self.nodes.append(node)
        return node

    def link(self, a, b):
        self.links.add(tuple(sorted((a, b))))

    def bead_stat(self, group):
        seq = group if isinstance(group, list) else BEADS[self.job][group]
        key = id(seq)
        i = self.bead_index.get(key, 0)
        self.bead_index[key] = i + 1
        return seq[i % len(seq)]

    def chain(self, a, b, group, band, min_beads=0):
        """a と b を、ステータスの節の数珠でつなぐ。"""
        na, nb = self.ids[a], self.ids[b]
        dist = math.hypot(nb["x"] - na["x"], nb["y"] - na["y"])
        count = max(min_beads, int(round(dist / BEAD_STEP)) - 1)
        prev = a
        for k in range(1, count + 1):
            t = k / (count + 1)
            stat = self.bead_stat(group)
            value = STAT_VALUES[stat][band]
            node = self.add({"id": "b%d" % (len(self.nodes)), "kind": "stat", "x": na["x"] + (nb["x"] - na["x"]) * t,
                             "y": na["y"] + (nb["y"] - na["y"]) * t, "stats": {stat: value}})
            self.link(prev, node["id"])
            prev = node["id"]
        self.link(prev, b)


def band_of(row):
    return 0 if row <= 1 else 1 if row <= 3 else 2


def lane_angle(si, lane):
    """その流派のその列の角度（外側の列ほど、まん中の線から離れる）。"""
    center = SCHOOL_ANGLE[si]
    outward = -1.0 if center < 90 else 1.0
    offset = {3: 1.5, 0: 0.5, 1: -0.5, 2: -1.5}[lane]
    return center + outward * offset * LANE_STEP


def notable(tree, job, group, k, node_id, x, y):
    name, flat, percent = NOTABLES[job][group][k]
    node = {"id": node_id, "kind": "notable", "name": name, "x": x, "y": y}
    if flat:
        node["stats"] = flat
    if percent:
        node["percent"] = percent
    return tree.add(node)


def build(job):
    with open(os.path.join(DATA, "jobs", job + ".json"), encoding="utf-8") as f:
        jd = json.load(f)
    schools = [s["id"] for s in jd["schools"]]
    starting = set(jd["starting_skills"])
    skills = load_skills(job)
    tree = Tree(job)
    tree.add({"id": "core", "kind": "core", "x": 0.0, "y": 0.0})
    grid = {}
    for si, school in enumerate(schools):
        group = [s for s in skills if s["school"] == school]
        cells = {}
        for s in group:
            if not s.get("ultimate"):
                cells[(s["row"], s.get("column", 0))] = s
        # 列ごとに、最初と最後のスキルの段（その間の空きは、ステータスの節で埋める）
        for lane in LANES:
            rows = [r for (r, c) in cells if c == lane]
            if not rows:
                continue
            last = max(rows)
            if lane in (3, 1):
                last = 5  # 外側の列・2つ目の列の先には、大きな節を置く
            for row in range(min(rows), last + 1):
                x, y = polar(ROW_RADIUS[row], lane_angle(si, lane))
                s = cells.get((row, lane))
                if s is not None:
                    node = tree.add({"id": "s:" + s["id"], "kind": "skill", "skill": "amemaru:" + s["id"], "x": x, "y": y,
                                     "start": s["id"] in starting})
                elif row == 5:
                    node = notable(tree, job, school, 0 if lane == 3 else 1, "n:%s:%d" % (school, lane), x, y)
                else:
                    stat = tree.bead_stat(BEADS[job][school][lane])
                    node = tree.add({"id": "g:%s:%d:%d" % (school, row, lane), "kind": "stat", "x": x, "y": y,
                                     "stats": {stat: STAT_VALUES[stat][band_of(row)]}})
                grid[(si, row, lane)] = node["id"]
        # 列のつながり（下の段から上の段へ）
        for lane in LANES:
            rows = sorted(r for (s_i, r, l) in grid if s_i == si and l == lane)
            for a, b in zip(rows, rows[1:]):
                tree.chain(grid[(si, a, lane)], grid[(si, b, lane)], BEADS[job][school][lane], band_of(b), min_beads=1)
        # 核と最初のスキル
        tree.chain("core", grid[(si, 0, 0)], BEADS[job]["core"], 0, min_beads=1)
        # 横のつなぎ：決まった段と、列の始まりの段（列が核へつながるように）
        starts = {}
        for lane in LANES:
            rows = [r for (s_i, r, l) in grid if s_i == si and l == lane]
            if rows:
                starts[lane] = min(rows)
        for row in range(6):
            present = [lane for lane in LANES if (si, row, lane) in grid]
            for a, b in zip(present, present[1:]):
                if row in RUNG_ROWS or starts.get(a) == row or starts.get(b) == row:
                    tree.chain(grid[(si, row, a)], grid[(si, row, b)], BEADS[job][school]["rung"], band_of(row))
        # 奥義：主な列の先
        for s in group:
            if s.get("ultimate"):
                x, y = polar(ULTIMATE_RADIUS, lane_angle(si, 0))
                tree.add({"id": "s:" + s["id"], "kind": "skill", "skill": "amemaru:" + s["id"], "x": x, "y": y, "start": False})
                tree.chain(grid[(si, 5, 0)], "s:" + s["id"], BEADS[job][school][0], 2, min_beads=1)
    # 橋：2段目・4段目で、2つの流派のパッシブの列を、まん中の大きな節でつなぐ
    for k, row in enumerate(BRIDGE_ROWS):
        x, y = polar(ROW_RADIUS[row], 90.0)
        node = notable(tree, job, "bridge", k, "n:bridge:%d" % row, x, y)
        for si in range(2):
            tree.chain(grid[(si, row, 2)], node["id"], BEADS[job]["bridge"], band_of(row), min_beads=1)
    check(tree, skills)
    return {"_comment": "スキルツリー（tools/datagen/build_skill_trees.py が書き出す。手で直さない）。節（core：職業の核・skill：スキル・"
                        "stat：ステータスの小さな節・notable：大きな節）と、つながり（links）。x・y はツリーの単位（右・下が正）",
            "job": "amemaru:" + job, "nodes": tree.nodes, "links": sorted([list(l) for l in tree.links])}, tree


def check(tree, skills):
    errors = []
    for s in skills:
        if "s:" + s["id"] not in tree.ids:
            errors.append("スキルの節がない：" + s["id"])
    # 近すぎる節
    nodes = tree.nodes
    for i in range(len(nodes)):
        for j in range(i + 1, len(nodes)):
            a, b = nodes[i], nodes[j]
            d = math.hypot(a["x"] - b["x"], a["y"] - b["y"])
            limit = 34 if a["kind"] != "stat" and b["kind"] != "stat" else 22
            if d < limit:
                errors.append("節が近すぎる（%.0f）：%s と %s" % (d, a["id"], b["id"]))
    # 核からすべての節へ行けるか
    adj = {}
    for a, b in tree.links:
        adj.setdefault(a, []).append(b)
        adj.setdefault(b, []).append(a)
    seen = {"core"}
    stack = ["core"]
    while stack:
        n = stack.pop()
        for m in adj.get(n, []):
            if m not in seen:
                seen.add(m)
                stack.append(m)
    for n in nodes:
        if n["id"] not in seen:
            errors.append("核から行けない節：" + n["id"])
    if errors:
        if os.environ.get("TREE_LENIENT"):
            print("\n".join(errors[:5]), "…（%d）" % len(errors))
            return
        raise SystemExit("\n".join(errors[:30]))


def cost_summary(data):
    stats = sum(1 for n in data["nodes"] if n["kind"] in ("stat", "notable"))
    skills = []
    for n in data["nodes"]:
        if n["kind"] == "skill":
            with open(os.path.join(DATA, "skills", n["skill"].split(":")[1] + ".json"), encoding="utf-8") as f:
                d = json.load(f)
            skills.append(sum(d["rank_costs"]))
    return stats, len(skills), sum(skills)


def render(job, data, out_dir):
    from PIL import Image, ImageDraw
    pad = 60
    xs = [n["x"] for n in data["nodes"]]
    ys = [n["y"] for n in data["nodes"]]
    x0, y0 = min(xs) - pad, min(ys) - pad
    w, h = int(max(xs) - x0 + pad), int(max(ys) - y0 + pad)
    img = Image.new("RGB", (w, h), (14, 18, 30))
    d = ImageDraw.Draw(img)
    pos = {n["id"]: (n["x"] - x0, n["y"] - y0) for n in data["nodes"]}
    for a, b in data["links"]:
        d.line([pos[a], pos[b]], fill=(70, 90, 130), width=2)
    colors = {"hp": (230, 90, 90), "mp": (90, 150, 240), "attack": (240, 150, 70), "magic": (190, 120, 250),
              "defense": (200, 200, 120), "speed": (120, 230, 160), "crit": (250, 220, 90)}
    for n in data["nodes"]:
        x, y = pos[n["id"]]
        if n["kind"] == "core":
            d.ellipse([x - 22, y - 22, x + 22, y + 22], fill=(240, 210, 120), outline=(255, 255, 255), width=2)
        elif n["kind"] == "skill":
            r = 16
            fill = (60, 70, 100) if not n.get("start") else (110, 90, 40)
            d.rectangle([x - r, y - r, x + r, y + r], fill=fill, outline=(230, 190, 90), width=2)
        elif n["kind"] == "notable":
            d.ellipse([x - 13, y - 13, x + 13, y + 13], fill=(90, 60, 110), outline=(240, 200, 255), width=2)
        else:
            stat = next(iter(n["stats"]))
            d.ellipse([x - 7, y - 7, x + 7, y + 7], fill=colors.get(stat, (200, 200, 200)), outline=(20, 20, 20))
    path = os.path.join(out_dir, job + ".png")
    img.save(path)
    return path


def main():
    render_dir = None
    if "--render" in sys.argv:
        render_dir = sys.argv[sys.argv.index("--render") + 1]
        os.makedirs(render_dir, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    for job in JOBS:
        data, tree = build(job)
        with open(os.path.join(OUT, job + ".json"), "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        stats, n_skills, skill_cost = cost_summary(data)
        print("%s：節 %d（ステータス %d・スキル %d）・つながり %d・すべて取るのに %d ポイント（ステータス %d・スキル %d）" % (
            job, len(data["nodes"]), stats, n_skills, len(data["links"]), stats + skill_cost, stats, skill_cost))
        if render_dir:
            render(job, data, render_dir)


if __name__ == "__main__":
    main()

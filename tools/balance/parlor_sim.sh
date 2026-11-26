#!/bin/bash
# 地下遊技場の機種の数を確かめる（tools/balance/ParlorSim.java）。Mod の casino/parlor の台の動きを、Minecraft なしで動かす。
#   bash tools/balance/parlor_sim.sh [機種のID ...]
# Gson の jar（Minecraft と同じ 2.10）は、Gradle に入っているものを使う。GSON=… で場所を変えられる。
set -e
cd "$(dirname "$0")/../.."
GSON="${GSON:-$(ls /opt/gradle-*/lib/gson-2.10*.jar 2>/dev/null | head -n 1)}"
OUT="$(mktemp -d)"
javac -encoding UTF-8 -proc:none -cp "$GSON" -d "$OUT" \
  src/main/java/jp/amemaru/lostconnection/core/data/DefinitionException.java \
  src/main/java/jp/amemaru/lostconnection/casino/parlor/ParlorJson.java \
  src/main/java/jp/amemaru/lostconnection/casino/parlor/SlotSpec.java \
  src/main/java/jp/amemaru/lostconnection/casino/parlor/ReelControl.java \
  src/main/java/jp/amemaru/lostconnection/casino/parlor/SlotEngine.java \
  src/main/java/jp/amemaru/lostconnection/casino/parlor/PachinkoSpec.java \
  src/main/java/jp/amemaru/lostconnection/casino/parlor/PachinkoEngine.java \
  tools/balance/ParlorSim.java
java -Dstdout.encoding=UTF-8 -cp "$OUT:$GSON" ParlorSim src/main/resources/data/amemaru/amemaru/casino_machines "$@"

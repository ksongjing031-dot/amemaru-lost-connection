# ゲームの中で直した所（取り込んだもの）

`/amemaru admin city export` で書き出した直しを、`python3 -I tools/mapgen/import_edits.py <フォルダ>` で取り込むと、
ここに `<構造物>.json` ができます。`build_*.py` が構造物ファイルを書くとき、ここの直しを重ねます（`mapgen/edits.py`）。
形は `docs/ingame_check.md` の「建物を直す」と `mapgen/edits.py` を見てください。

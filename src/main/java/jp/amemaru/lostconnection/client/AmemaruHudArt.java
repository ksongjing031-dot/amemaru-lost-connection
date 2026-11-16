package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.resources.ResourceLocation;

/**
 * HUDと画面で使う絵（{@code assets/amemaru/textures/gui/hud.png}、256×256）の、どこに何があるか。
 *
 * <p>絵は {@code tools/hudgen/make_hud.py} で作っている。描き直すときは、同じ位置・同じ大きさで描けば
 * コードを変えずに差し替えられる（Phase 1の手順書の8章）。</p>
 */
public final class AmemaruHudArt {
    public static final ResourceLocation TEXTURE = AmemaruMod.id("textures/gui/hud.png");
    public static final int TEX_SIZE = 256;

    // ---- HPとMPのバー（80×12。HPは左がとがり、MPは右がとがる）
    public static final int BAR_W = 80;
    public static final int BAR_H = 12;
    /** バーの中身の幅（左右の枠と面取りを除く）。中身は絵の x=2 から始まる。 */
    public static final int BAR_INNER = 76;
    public static final int V_HP_FRAME = 0;
    public static final int V_MP_FRAME = 12;
    public static final int V_HP_FILL = 24;
    public static final int V_HP_FILL_LOW = 36;
    public static final int V_MP_FILL = 48;
    public static final int V_HP_GHOST = 60;
    public static final int V_MP_GHOST = 72;
    /** 枠だけの白い形（低HPのとき、赤く染めて点滅させる）。 */
    public static final int U_HP_GLOW = 0;
    public static final int U_MP_GLOW = 80;
    public static final int V_GLOW = 84;

    // ---- 経験値のバー（182×5）
    public static final int EXP_W = 182;
    public static final int EXP_H = 5;
    /** 中身の幅。中身は絵の x=1 から始まる。 */
    public static final int EXP_INNER = 180;
    public static final int V_EXP_FRAME = 96;
    public static final int V_EXP_FILL = 101;
    public static final int V_EXP_FLASH = 106;

    // ---- 中央の結晶（24×28。上の8行は冠、その下が六角）
    public static final int GEM_W = 24;
    public static final int GEM_H = 28;
    public static final int GEM_HEX_TOP = 8;
    public static final int U_GEM = 96;
    public static final int U_GEM_LIT = 120;
    public static final int U_GEM_FLASH = 144;
    /** 結晶のまわりの光（28×32。結晶より上下左右に2ピクセル大きい）。 */
    public static final int U_GEM_GLOW = 96;
    public static final int V_GEM_GLOW = 28;
    public static final int GEM_GLOW_W = 28;
    public static final int GEM_GLOW_H = 32;

    // ---- ホットバー（182×22）・選んでいるスロットの枠（24×24）・オフハンドのスロット（22×22）
    public static final int U_HOTBAR = 0;
    public static final int V_HOTBAR = 112;
    public static final int HOTBAR_W = 182;
    public static final int HOTBAR_H = 22;
    public static final int U_SELECTION = 184;
    public static final int V_SELECTION = 112;
    public static final int SELECTION_SIZE = 24;
    public static final int U_OFFHAND = 208;
    public static final int V_OFFHAND = 112;
    public static final int OFFHAND_SIZE = 22;

    // ---- パネルの角の小さな結晶（9×9）
    public static final int U_DIAMOND = 168;
    public static final int V_DIAMOND = 0;
    public static final int DIAMOND_SIZE = 9;

    private AmemaruHudArt() {
    }
}

package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.resources.ResourceLocation;

/**
 * スキルのHUDとスキルツリーの画面で使う絵（{@code assets/amemaru/textures/gui/skill_hud.png}、256×256）の、どこに何があるか。
 *
 * <p>絵は {@code tools/artgen/make_phase2_art.py} で作っている。同じ位置・同じ大きさで描き直せば、コードを変えずに差し替えられる
 * （Phase 2の手順書の8章）。</p>
 */
public final class SkillHudArt {
    public static final ResourceLocation TEXTURE = AmemaruMod.id("textures/gui/skill_hud.png");
    public static final int TEX_SIZE = 256;

    // ---- スキル枠（六角。24×26）
    public static final int HEX_W = 24;
    public static final int HEX_H = 26;
    public static final int V_HEX = 0;
    /** スキルが入っている枠。 */
    public static final int U_HEX = 0;
    /** 空の枠。 */
    public static final int U_HEX_EMPTY = 24;
    /** クールダウンの影（中だけ。上から残りの割合だけ描く）。 */
    public static final int U_HEX_SHADE = 48;
    /** 詠唱中の金のふち。 */
    public static final int U_HEX_CAST = 72;
    /** MPやゲージが足りないときの青い影。 */
    public static final int U_HEX_DIM = 96;
    /** 使えるようになった瞬間の白い光。 */
    public static final int U_HEX_FLASH = 120;
    /** 枠のまわりの光（28×30）。 */
    public static final int U_HEX_GLOW = 144;
    public static final int HEX_GLOW_W = 28;
    public static final int HEX_GLOW_H = 30;

    // ---- 専用ゲージ（87×6）
    public static final int GAUGE_W = 87;
    public static final int GAUGE_H = 6;
    public static final int V_GAUGE_FRAME = 32;
    public static final int V_GAUGE_FILL = 38;
    /** 中身は絵の x=1 から、幅85。 */
    public static final int GAUGE_INNER = 85;

    // ---- 詠唱のバー（102×8）
    public static final int CAST_W = 102;
    public static final int CAST_H = 8;
    public static final int V_CAST_FRAME = 48;
    public static final int V_CAST_FILL = 56;
    /** 中身は絵の x=1 から、幅100。 */
    public static final int CAST_INNER = 100;

    // ---- スキルツリーの節（36×36）
    public static final int NODE = 36;
    public static final int V_NODE = 96;
    public static final int U_NODE_LOCKED = 0;
    public static final int U_NODE_AVAILABLE = 36;
    public static final int U_NODE_LEARNED = 72;
    public static final int U_NODE_MAX = 108;
    public static final int U_NODE_ULTIMATE = 144;
    /** 選んでいる節の光（40×40）。 */
    public static final int U_NODE_SELECT = 184;
    public static final int NODE_SELECT = 40;

    // ---- 段階の印（5×5）
    public static final int PIP = 5;
    public static final int V_PIP = 136;
    public static final int U_PIP_EMPTY = 0;
    public static final int U_PIP_FILLED = 5;
    public static final int U_PIP_UPPER = 10;
    public static final int U_PIP_UPPER_FILLED = 15;

    private SkillHudArt() {
    }
}

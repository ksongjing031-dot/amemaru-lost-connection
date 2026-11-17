package jp.amemaru.lostconnection.client.fx;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.resources.ResourceLocation;

/**
 * 演出の形に貼るテクスチャ（{@code textures/fx/}。仮素材は tools/artgen/make_fx_art.py で作る）。
 * 同じ名前・同じ大きさの画像を置けば、コードを変えずに差し替えられる。
 */
public final class FxTextures {
    /** やわらかな光の玉（64x64）。 */
    public static final ResourceLocation GLOW = tex("glow");
    /** 細い光の輪（128x128）。波紋に使う。 */
    public static final ResourceLocation RING = tex("ring");
    /** 下が明るく上へ消える縦のグラデーション（64x64）。波紋の壁・光の柱。 */
    public static final ResourceLocation WALL = tex("wall");
    /** 光の帯（64x16。v の真ん中が明るい）。糸・軌跡。 */
    public static final ResourceLocation BEAM = tex("beam");
    /** 芯の細い光の帯（64x16）。稲妻。 */
    public static final ResourceLocation LIGHTNING = tex("lightning");
    /** 鎖の輪（64x16。横にくり返す）。縛鎖。 */
    public static final ResourceLocation CHAIN = tex("chain");
    /** 光の大剣（64x256。上が柄頭、下が切っ先）。天断ち。 */
    public static final ResourceLocation GREATSWORD = tex("greatsword");
    /** 斬撃の弧（128x32。v=0 が外側の刃先、u=1 が振り終わり）。 */
    public static final ResourceLocation SLASH = tex("slash");
    /** 魔法陣（128x128）。 */
    public static final ResourceLocation SIGIL_ARCANE = tex("sigil_arcane");
    public static final ResourceLocation SIGIL_FROST = tex("sigil_frost");
    public static final ResourceLocation SIGIL_STAR = tex("sigil_star");
    public static final ResourceLocation SIGIL_OATH = tex("sigil_oath");
    public static final ResourceLocation SIGIL_THUNDER = tex("sigil_thunder");
    /** 六角の網目（64x64。横にくり返す）。障壁・城壁のドーム。 */
    public static final ResourceLocation DOME = tex("dome");
    /** 氷の面（32x32）。凍結の氷・氷の棘。 */
    public static final ResourceLocation ICE = tex("ice");
    /** 4つのとがりの星の光（64x64）。 */
    public static final ResourceLocation STAR = tex("star");
    /** 雪の結晶の印（32x32）。凍えの数。 */
    public static final ResourceLocation SNOWFLAKE = tex("snowflake");
    /** ひびの入った盾の印（32x32）。防御崩し。 */
    public static final ResourceLocation CRACK = tex("crack");
    /** 盾の紋（64x64）。受け流し・盾結び。 */
    public static final ResourceLocation SHIELD = tex("shield");

    private FxTextures() {
    }

    private static ResourceLocation tex(String name) {
        return AmemaruMod.id("textures/fx/" + name + ".png");
    }
}

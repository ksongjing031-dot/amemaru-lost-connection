package jp.amemaru.lostconnection.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 見た目の設定（プレイヤーごと。{@code config/amemaru-client.toml}）。
 *
 * <p>Phase 1ではHUDの設定だけを置く。スキルの操作や配信向けの設定は、使い始めるPhaseで足す。</p>
 */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue HUD_SCALE;
    public static final ForgeConfigSpec.BooleanValue HUD_SHOW_NUMBERS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("HUD（HP・MP・経験値のバー）").push("hud");
        HUD_SCALE = b
                .comment("HUDの大きさ（0.5〜2.0）")
                .defineInRange("scale", 1.0, 0.5, 2.0);
        HUD_SHOW_NUMBERS = b
                .comment("HPとMPのバーに数字を出す")
                .define("showNumbers", true);
        b.pop();

        SPEC = b.build();
    }

    private ClientConfig() {
    }

    /** 設定がまだ読み込まれていないときは初期値を返す。 */
    public static double hudScale() {
        try {
            return HUD_SCALE.get();
        } catch (IllegalStateException notLoaded) {
            return 1.0;
        }
    }

    /** 設定がまだ読み込まれていないときは初期値を返す。 */
    public static boolean showNumbers() {
        try {
            return HUD_SHOW_NUMBERS.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }
}

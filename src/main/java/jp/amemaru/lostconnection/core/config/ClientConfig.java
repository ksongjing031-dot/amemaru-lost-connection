package jp.amemaru.lostconnection.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 見た目の設定（プレイヤーごと。{@code config/amemaru-client.toml}）。
 *
 * <p>Phase 1でHUD、Phase 2でスキルの操作と表示の設定を置いた。配信向けの設定は、使い始めるPhaseで足す。</p>
 */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    /** スキルの操作の方式（Phase 0-B 7章）。 */
    public enum SkillControl {
        /** スキル枠6つを、それぞれのキーに直接割り当てる（初期値）。 */
        KEYS,
        /** 左Altを押している間だけ、ホットバーの1〜6がスキルになる。 */
        HOTBAR_ALT
    }

    public static final ForgeConfigSpec.DoubleValue HUD_SCALE;
    public static final ForgeConfigSpec.BooleanValue HUD_SHOW_NUMBERS;
    public static final ForgeConfigSpec.EnumValue<SkillControl> SKILL_CONTROL;
    public static final ForgeConfigSpec.BooleanValue SHOW_DAMAGE_NUMBERS;
    public static final ForgeConfigSpec.BooleanValue SCREEN_SHAKE;
    public static final ForgeConfigSpec.BooleanValue TOOLBAR_KEYS_RELEASED;
    public static final ForgeConfigSpec.IntValue SKILL_KEYS_VERSION;
    public static final ForgeConfigSpec.BooleanValue CURIOS_KEY_RELEASED;
    public static final ForgeConfigSpec.BooleanValue GEAR_SPARKLES;
    public static final ForgeConfigSpec.BooleanValue BOSS_MUSIC;
    public static final ForgeConfigSpec.BooleanValue BOSS_EFFECTS;

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

        b.comment("スキルの操作と表示").push("skills");
        SKILL_CONTROL = b
                .comment("スキルの操作。KEYS：キーに直接（初期値 R・G・V・Z・X・C）／HOTBAR_ALT：左Altを押している間だけホットバーの1〜6がスキルになる")
                .defineEnum("control", SkillControl.KEYS);
        SHOW_DAMAGE_NUMBERS = b
                .comment("敵や味方の頭の上に、ダメージや回復の数字を出す")
                .define("showDamageNumbers", true);
        SCREEN_SHAKE = b
                .comment("大きな技（流星群・星落としなど）で画面を揺らす")
                .define("screenShake", true);
        TOOLBAR_KEYS_RELEASED = b
                .comment("（自動で書きかわる値）バニラの「ツールバーの保存・読み込み」のキー（X・C）を、スキル用に外したか。",
                        "false に戻すと、次の起動でもう一度外す")
                .define("toolbarKeysReleased", false);
        SKILL_KEYS_VERSION = b
                .comment("（自動で書きかわる値）スキルのキーの初期値の版。試作版の初期値（R・Z・X・C・B・Y）のままの人を、",
                        "R・G・V・Z・X・C に移したら 2 になる")
                .defineInRange("skillKeysVersion", 0, 0, 100);
        CURIOS_KEY_RELEASED = b
                .comment("（自動で書きかわる値）Curios（アクセサリーの枠）の画面を開くキー（G）を、スキルのキーと重ならないように外したか。",
                        "アクセサリーの枠は、ステータス画面（K）の「アクセサリー」か、持ち物の画面のボタンから開く")
                .define("curiosKeyReleased", false);
        GEAR_SPARKLES = b
                .comment("エピック以上の装備と、+7以上に強化した装備を持っている人の手元に、光の粒を出すか")
                .define("gearSparkles", true);
        b.pop();

        // 11月25日：ボス戦の曲と演出
        b.comment("ボス戦").push("boss");
        BOSS_MUSIC = b
                .comment("ボス戦の間、ボスごとの曲を流す（音量は「音楽」の音量。いつもの曲は止まる）")
                .define("music", true);
        BOSS_EFFECTS = b
                .comment("ボス戦の演出（はじまりの名前・段階が変わったときの光・ギミックの残りの赤いふちと時を刻む音・",
                        "HPが少ないときの心臓の音・大きく打たれたときの赤いふち）。画面の揺れは skills.screenShake にも従う")
                .define("effects", true);
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
    public static SkillControl skillControl() {
        try {
            return SKILL_CONTROL.get();
        } catch (IllegalStateException notLoaded) {
            return SkillControl.KEYS;
        }
    }

    /** 設定がまだ読み込まれていないときは初期値を返す。 */
    public static boolean showDamageNumbers() {
        try {
            return SHOW_DAMAGE_NUMBERS.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** 設定がまだ読み込まれていないときは初期値を返す。 */
    public static boolean screenShake() {
        try {
            return SCREEN_SHAKE.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** 設定がまだ読み込まれていないときは初期値を返す。 */
    public static boolean bossMusic() {
        try {
            return BOSS_MUSIC.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** 設定がまだ読み込まれていないときは初期値を返す。 */
    public static boolean bossEffects() {
        try {
            return BOSS_EFFECTS.get();
        } catch (IllegalStateException notLoaded) {
            return true;
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

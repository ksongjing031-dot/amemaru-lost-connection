package jp.amemaru.lostconnection.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * サーバーの設定（ワールドごと。{@code serverconfig/amemaru-server.toml}）。
 *
 * <p>倍率・料金・上限は、コードを変えずにここで変えられる（Phase 0-B 7章）。
 * Phase 1では、成長・HPとMP・ワールドの設定だけを置く。ほかの区分は、使い始めるPhaseで足す。</p>
 */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;

    // 成長
    public static final ForgeConfigSpec.DoubleValue EXP_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue RESTORE_ON_LEVEL_UP;

    // HPとMPの自然回復
    public static final ForgeConfigSpec.DoubleValue HP_REGEN_IN_COMBAT;
    public static final ForgeConfigSpec.DoubleValue HP_REGEN_OUT_OF_COMBAT;
    public static final ForgeConfigSpec.DoubleValue MP_REGEN_IN_COMBAT;
    public static final ForgeConfigSpec.DoubleValue MP_REGEN_OUT_OF_COMBAT;
    public static final ForgeConfigSpec.IntValue OUT_OF_COMBAT_SECONDS;

    // ワールド
    public static final ForgeConfigSpec.BooleanValue APPLY_GAME_RULES;
    public static final ForgeConfigSpec.BooleanValue KEEP_FOOD_FULL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("成長").push("growth");
        EXP_MULTIPLIER = b
                .comment("経験値の倍率。敵などから得る経験値にかける（管理者のコマンドで与える経験値にはかけない）")
                .defineInRange("expMultiplier", 1.0, 0.0, 100.0);
        RESTORE_ON_LEVEL_UP = b
                .comment("レベルが上がったときに、HPとMPを全快にする")
                .define("restoreOnLevelUp", true);
        b.pop();

        b.comment("HPとMPの自然回復。値は「1秒あたり、最大値の何%を回復するか」").push("vitals");
        HP_REGEN_IN_COMBAT = b
                .comment("戦闘中のHPの回復（%/秒）")
                .defineInRange("hpRegenInCombat", 0.0, 0.0, 100.0);
        HP_REGEN_OUT_OF_COMBAT = b
                .comment("戦闘していないときのHPの回復（%/秒）")
                .defineInRange("hpRegenOutOfCombat", 2.0, 0.0, 100.0);
        MP_REGEN_IN_COMBAT = b
                .comment("戦闘中のMPの回復（%/秒）")
                .defineInRange("mpRegenInCombat", 1.0, 0.0, 100.0);
        MP_REGEN_OUT_OF_COMBAT = b
                .comment("戦闘していないときのMPの回復（%/秒）")
                .defineInRange("mpRegenOutOfCombat", 3.0, 0.0, 100.0);
        OUT_OF_COMBAT_SECONDS = b
                .comment("最後に攻撃した・攻撃を受けたときから、何秒たつと「戦闘していない」扱いになるか")
                .defineInRange("outOfCombatSeconds", 8, 0, 600);
        b.pop();

        b.comment("ワールド").push("world");
        APPLY_GAME_RULES = b
                .comment("サーバーの起動時に、このModに合わせたゲームルールにする。",
                        "（自然回復・敵の自然発生・火の広がり・モブによる破壊・ファントム・襲撃隊・行商人を止め、死んでも持ち物を失わないようにする）")
                .define("applyGameRules", true);
        KEEP_FOOD_FULL = b
                .comment("満腹度が減らないようにする")
                .define("keepFoodFull", true);
        b.pop();

        SPEC = b.build();
    }

    private ServerConfig() {
    }
}

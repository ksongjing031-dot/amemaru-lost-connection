package jp.amemaru.lostconnection.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * サーバーの設定（ワールドごと。{@code serverconfig/amemaru-server.toml}）。
 *
 * <p>倍率・料金・上限は、コードを変えずにここで変えられる（Phase 0-B 7章）。
 * Phase 1で成長・HPとMP・ワールド、Phase 2で戦闘・スキル・職業の設定を置いた。ほかの区分は、使い始めるPhaseで足す。</p>
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

    // 戦闘
    public static final ForgeConfigSpec.DoubleValue BASIC_ATTACK_COEFFICIENT;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_VARIANCE;
    public static final ForgeConfigSpec.DoubleValue VANILLA_MOB_HP_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue VANILLA_MOB_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue VANILLA_ARMOR_TO_DEFENSE;
    public static final ForgeConfigSpec.BooleanValue ALLOW_PVP;
    public static final ForgeConfigSpec.DoubleValue LINK_WINDOW_SECONDS;
    public static final ForgeConfigSpec.DoubleValue LINK_BONUS;
    public static final ForgeConfigSpec.DoubleValue LINK3_BONUS;
    public static final ForgeConfigSpec.DoubleValue SUPPORT_BONUS;
    public static final ForgeConfigSpec.IntValue TRAINING_DUMMY_HP;

    // スキル
    public static final ForgeConfigSpec.BooleanValue REQUIRE_JOB_WEAPON;
    public static final ForgeConfigSpec.IntValue SKILL_REQUESTS_PER_SECOND;
    public static final ForgeConfigSpec.DoubleValue COOLDOWN_REDUCTION_CAP;

    // 職業
    public static final ForgeConfigSpec.IntValue JOB_CHANGE_FREE_UNTIL_DAY;
    public static final ForgeConfigSpec.IntValue JOB_CHANGE_TOKENS;
    public static final ForgeConfigSpec.DoubleValue OATH_CRYSTAL_REACH;

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

        b.comment("戦闘（ダメージの計算と連携技）").push("combat");
        BASIC_ATTACK_COEFFICIENT = b
                .comment("近接攻撃（左クリック）のダメージが、攻撃のステータスの何倍か")
                .defineInRange("basicAttackCoefficient", 1.0, 0.0, 100.0);
        DAMAGE_VARIANCE = b
                .comment("ダメージのばらつき（%）。5なら、95%〜105%の間でばらつく")
                .defineInRange("damageVariance", 5.0, 0.0, 50.0);
        VANILLA_MOB_HP_MULTIPLIER = b
                .comment("このModの敵ではない魔物（バニラのゾンビなど）のHPを、バニラの体力の何倍として扱うか",
                        "（このModの敵はPhase 3で足す。それまでの動作確認用）")
                .defineInRange("vanillaMobHpMultiplier", 5.0, 0.1, 1000.0);
        VANILLA_MOB_DAMAGE_MULTIPLIER = b
                .comment("このModの敵ではない魔物の攻撃のダメージを、バニラの値の何倍として扱うか（その後に防御で減る）")
                .defineInRange("vanillaMobDamageMultiplier", 8.0, 0.0, 1000.0);
        VANILLA_ARMOR_TO_DEFENSE = b
                .comment("このModの敵ではない魔物の防具の値を、防御のステータスの何倍として扱うか")
                .defineInRange("vanillaArmorToDefense", 2.0, 0.0, 100.0);
        ALLOW_PVP = b
                .comment("プレイヤーどうしで攻撃できるようにする（初期値は false：協力のゲームのため）")
                .define("allowPvp", false);
        LINK_WINDOW_SECONDS = b
                .comment("連携技：「起点」の後、何秒以内に別の人の「追撃」が当たれば「連携！」になるか（「支援」の効き目の時間も同じ）")
                .defineInRange("linkWindowSeconds", 4.0, 0.5, 30.0);
        LINK_BONUS = b
                .comment("連携技：「連携！」のときの追撃のダメージの上がり幅（%）")
                .defineInRange("linkBonus", 30.0, 0.0, 500.0);
        LINK3_BONUS = b
                .comment("連携技：3人以上がつないだ「三連携」のときの上がり幅（%）")
                .defineInRange("link3Bonus", 50.0, 0.0, 500.0);
        SUPPORT_BONUS = b
                .comment("連携技：「支援」を受けた味方が、時間内に使ったスキルの効果の上がり幅（%）")
                .defineInRange("supportBonus", 20.0, 0.0, 500.0);
        TRAINING_DUMMY_HP = b
                .comment("訓練人形のHP（0になると、すぐに満タンに戻る）")
                .defineInRange("trainingDummyHp", 2000, 1, 10000000);
        b.pop();

        b.comment("スキル").push("skills");
        REQUIRE_JOB_WEAPON = b
                .comment("スキルを使うとき、職業の武器（見習いの剣・杖など）を手に持っている必要があるか")
                .define("requireJobWeapon", true);
        SKILL_REQUESTS_PER_SECOND = b
                .comment("1人が1秒に送れるスキルの要求の上限（超えた分は捨てて、ログに残す）")
                .defineInRange("skillRequestsPerSecond", 10, 1, 100);
        COOLDOWN_REDUCTION_CAP = b
                .comment("クールダウン短縮のステータスの上限（%）")
                .defineInRange("cooldownReductionCap", 50.0, 0.0, 90.0);
        b.pop();

        b.comment("職業").push("jobs");
        JOB_CHANGE_FREE_UNTIL_DAY = b
                .comment("戦闘職を無料で何度でも変えられる最後の進行日（お試し期間。Phase 0-A 19章：3日目まで）")
                .defineInRange("jobChangeFreeUntilDay", 3, 0, 365);
        JOB_CHANGE_TOKENS = b
                .comment("お試し期間の後に戦闘職を変えるとき、使う「転職の証」の数",
                        "（ノットの費用は、ノットの仕組みを作るPhase 6で足す）")
                .defineInRange("jobChangeTokens", 1, 0, 64);
        OATH_CRYSTAL_REACH = b
                .comment("職業を選ぶとき、誓約の結晶から何ブロック以内にいる必要があるか")
                .defineInRange("oathCrystalReach", 8.0, 2.0, 64.0);
        b.pop();

        SPEC = b.build();
    }

    private ServerConfig() {
    }
}

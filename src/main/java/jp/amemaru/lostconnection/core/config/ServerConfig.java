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
    // ---- Phase 5：物語・塔
    public static final ForgeConfigSpec.BooleanValue OPENING_ON_FIRST_LOGIN;
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

    // ---- 難易度・ドロップ・装備（Phase 3。Phase 0-B 7章）
    public static final ForgeConfigSpec.DoubleValue ENEMY_HP_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue ENEMY_ATTACK_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue DROP_RATE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue RARE_DROP_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue RARE_ANNOUNCE_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue PITY_STEP;
    public static final ForgeConfigSpec.DoubleValue LOOT_RANGE;
    public static final ForgeConfigSpec.IntValue FRUIT_CAP;
    public static final ForgeConfigSpec.IntValue CLASP_UNLOCK_DAY;
    public static final ForgeConfigSpec.DoubleValue WORKSHOP_REACH;
    public static final ForgeConfigSpec.DoubleValue ENEMY_LEASH_RANGE;

    // ---- ダウンと復活・パーティ（Phase 4。Phase 0-B 7章「ダウンと復活」）
    public static final ForgeConfigSpec.IntValue DOWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue REVIVE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue HEALER_REVIVE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue REVIVE_RANGE;
    public static final ForgeConfigSpec.DoubleValue REVIVE_HP_PERCENT;
    public static final ForgeConfigSpec.DoubleValue REVIVE_INVULNERABLE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue DEATH_MONEY_PERCENT;
    public static final ForgeConfigSpec.IntValue WEAKNESS_SECONDS;
    public static final ForgeConfigSpec.DoubleValue WEAKNESS_PERCENT;
    public static final ForgeConfigSpec.DoubleValue CHURCH_SCATTER;
    public static final ForgeConfigSpec.IntValue PARTY_MAX_MEMBERS;
    public static final ForgeConfigSpec.DoubleValue PARTY_EXP_RANGE;
    public static final ForgeConfigSpec.DoubleValue PARTY_EXP_BONUS;
    public static final ForgeConfigSpec.IntValue PARTY_INVITE_SECONDS;

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

        b.comment("難易度（Phase 3：このModの敵）").push("difficulty");
        ENEMY_HP_MULTIPLIER = b
                .comment("このModの敵のHPにかける倍率")
                .defineInRange("enemyHpMultiplier", 1.0, 0.01, 1000.0);
        ENEMY_ATTACK_MULTIPLIER = b
                .comment("このModの敵の攻撃にかける倍率")
                .defineInRange("enemyAttackMultiplier", 1.0, 0.0, 1000.0);
        ENEMY_LEASH_RANGE = b
                .comment("このModの敵が、戦い始めた所から何ブロック離れて、5秒戦わなかったら（打たれも打ちもしない）追うのをやめて戻るか（打たれずに戻れたらHPが満タンになる。戻る間も攻撃は当たる）")
                .defineInRange("enemyLeashRange", 28.0, 8.0, 256.0);
        b.pop();

        b.comment("ドロップ（Phase 3。ドロップは、敵に攻撃を当てた人が一人ずつ個別に抽選する）").push("drops");
        DROP_RATE_MULTIPLIER = b
                .comment("ふつうのドロップ（ドロップ表）の倍率。1.5なら、ドロップ表を1回引いたうえで、50%の確率でもう1回引く")
                .defineInRange("dropRateMultiplier", 1.0, 0.0, 100.0);
        RARE_DROP_MULTIPLIER = b
                .comment("レアドロップの確率にかける倍率（敵ごとの確率は、データパックの敵の定義で決める）")
                .defineInRange("rareDropMultiplier", 1.0, 0.0, 1000.0);
        RARE_ANNOUNCE_THRESHOLD = b
                .comment("この確率（%）以下のレアドロップが出たら、サーバー全体に知らせる")
                .defineInRange("rareAnnounceThreshold", 3.0, 0.0, 100.0);
        PITY_STEP = b
                .comment("天井：レアが出なかったたびに、もとの確率に足す割合（%。10なら4%→4.4%→4.8%…）。天井のある敵（ボス）だけ")
                .defineInRange("pityStep", 10.0, 0.0, 1000.0);
        LOOT_RANGE = b
                .comment("敵が倒れたとき、この距離（ブロック）より遠くにいる人は、ドロップと経験値を受け取れない")
                .defineInRange("lootRange", 48.0, 4.0, 512.0);
        b.pop();

        b.comment("ダウンと復活（Phase 4。Phase 0-A 20章：ダウン30秒・しゃがんで3秒で蘇生・時間切れで教会へ、所持金の5%と60秒の衰弱）")
                .push("down");
        DOWN_SECONDS = b
                .comment("HPが0になってから、教会へ戻るまでの秒数（この間、近くの仲間がしゃがんで起こせる）")
                .defineInRange("downSeconds", 30, 5, 600);
        REVIVE_SECONDS = b
                .comment("仲間を起こすのに、そばでしゃがみ続ける秒数")
                .defineInRange("reviveSeconds", 3.0, 0.5, 60.0);
        HEALER_REVIVE_SECONDS = b
                .comment("ヒーラーが仲間を起こす秒数")
                .defineInRange("healerReviveSeconds", 1.5, 0.5, 60.0);
        REVIVE_RANGE = b
                .comment("起こせる距離（ブロック。倒れた人の足もとから、しゃがんでいる人の足もとまで）")
                .defineInRange("reviveRange", 2.5, 1.0, 8.0);
        REVIVE_HP_PERCENT = b
                .comment("起き上がったときのHP（最大HPの%）")
                .defineInRange("reviveHpPercent", 30.0, 1.0, 100.0);
        REVIVE_INVULNERABLE_SECONDS = b
                .comment("起き上がってから、ダメージを受けない秒数（すぐにまた倒れないように）")
                .defineInRange("reviveInvulnerableSeconds", 2.0, 0.0, 30.0);
        DEATH_MONEY_PERCENT = b
                .comment("教会へ戻るとき（呼び戻しの儀の代価として）失う所持金の割合（%）。経験値とアイテムは失わない")
                .defineInRange("deathMoneyPercent", 5.0, 0.0, 100.0);
        WEAKNESS_SECONDS = b
                .comment("教会へ戻った後の「衰弱」の秒数（0なら衰弱しない）")
                .defineInRange("weaknessSeconds", 60, 0, 3600);
        WEAKNESS_PERCENT = b
                .comment("「衰弱」の間に下がるステータスの割合（%。HP・MP・攻撃・魔力・防御・素早さ・会心）")
                .defineInRange("weaknessPercent", 10.0, 0.0, 90.0);
        CHURCH_SCATTER = b
                .comment("教会の呼び戻しの円で、戻る場所をばらす半径（ブロック。同時に戻った人が重ならないように）")
                .defineInRange("churchScatter", 3.0, 0.0, 16.0);
        b.pop();

        b.comment("パーティ（Phase 4。Phase 0-A 20章：経験値は近くのメンバーで均等に分け、合計が「1＋0.2×（人数−1）」倍）")
                .push("party");
        PARTY_MAX_MEMBERS = b
                .comment("パーティの人数の上限")
                .defineInRange("maxMembers", 6, 2, 6);
        PARTY_EXP_RANGE = b
                .comment("敵が倒れたとき、この距離（ブロック）以内のパーティのメンバーが、経験値とドロップを受け取る")
                .defineInRange("expShareRange", 32.0, 4.0, 256.0);
        PARTY_EXP_BONUS = b
                .comment("パーティの経験値のボーナス：1人増えるごとに、合計がこの割合だけ増える（0.2なら3人で1.4倍を3人で分ける）")
                .defineInRange("expBonusPerMember", 0.2, 0.0, 2.0);
        PARTY_INVITE_SECONDS = b
                .comment("パーティの招待が、何秒で消えるか")
                .defineInRange("inviteSeconds", 60, 10, 3600);
        b.pop();

        b.comment("装備とアクセサリー（Phase 3）").push("equipment");
        FRUIT_CAP = b
                .comment("結晶果を、ステータスごとに何個まで食べられるか（Phase 0-A 20章：上限+15）")
                .defineInRange("fruitCap", 15, 0, 1000);
        CLASP_UNLOCK_DAY = b
                .comment("アクセサリーの6つ目の枠「留め具」が開く進行日（Phase 0-A 21章：11日目）")
                .defineInRange("claspUnlockDay", 11, 1, 365);
        WORKSHOP_REACH = b
                .comment("工房の作業台から何ブロック以内で、制作・強化・修理ができるか")
                .defineInRange("workshopReach", 8.0, 2.0, 64.0);
        b.pop();

        b.comment("物語と塔（Phase 5）").push("story");
        OPENING_ON_FIRST_LOGIN = b
                .comment("初めてログインした人に、召喚から始まるオープニング（大結晶のそばで目覚め、リリアと出会う）を流す。",
                        "オープニングは一人ひとりの画面で、その人の準備ができてから流す（ラグで入るのが遅れても弊害がないように）")
                .define("openingOnFirstLogin", true);
        b.pop();

        SPEC = b.build();
    }

    private ServerConfig() {
    }
}

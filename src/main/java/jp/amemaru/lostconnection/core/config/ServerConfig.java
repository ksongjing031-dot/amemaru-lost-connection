package jp.amemaru.lostconnection.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    public static final ForgeConfigSpec.BooleanValue AUTO_BUILD_CITY;
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

    // ---- 経済（Phase 6。Phase 0-B 7章）
    public static final ForgeConfigSpec.BooleanValue TRANSFER_ENABLED;
    public static final ForgeConfigSpec.LongValue TRANSFER_MAX;
    public static final ForgeConfigSpec.DoubleValue TRANSFER_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue SHOP_REACH;
    public static final ForgeConfigSpec.LongValue STORAGE_CONTRACT_PRICE;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends Number>> STORAGE_PAGE_PRICES;
    public static final ForgeConfigSpec.IntValue STORAGE_MAX_PAGES;
    public static final ForgeConfigSpec.DoubleValue STORAGE_REACH;
    public static final ForgeConfigSpec.IntValue STORAGE_RECEIVED_MAX;
    public static final ForgeConfigSpec.IntValue MARKET_OPEN_DAY;
    public static final ForgeConfigSpec.IntValue MARKET_PRICE_MIN_PERCENT;
    public static final ForgeConfigSpec.IntValue MARKET_PRICE_MAX_PERCENT;
    public static final ForgeConfigSpec.DoubleValue MARKET_LISTING_FEE_PERCENT;
    public static final ForgeConfigSpec.DoubleValue MARKET_SALE_FEE_PERCENT;
    public static final ForgeConfigSpec.DoubleValue MARKET_STALL_FEE_FACTOR;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends Integer>> MARKET_FREE_LISTING_DAYS;
    public static final ForgeConfigSpec.IntValue MARKET_LISTING_LIMIT;
    public static final ForgeConfigSpec.IntValue MARKET_STALL_LISTING_LIMIT;
    public static final ForgeConfigSpec.IntValue MARKET_LISTING_DAYS;
    public static final ForgeConfigSpec.LongValue MARKET_STALL_RENT;
    public static final ForgeConfigSpec.IntValue MARKET_STALL_DAYS;
    public static final ForgeConfigSpec.IntValue MARKET_STALL_NAME_MAX;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> MARKET_NG_WORDS;
    public static final ForgeConfigSpec.BooleanValue MARKET_PRICE_DRIFT;
    public static final ForgeConfigSpec.DoubleValue MARKET_DRIFT_MAX_PERCENT;
    public static final ForgeConfigSpec.IntValue MARKET_DRIFT_MIN_TRADES;
    public static final ForgeConfigSpec.DoubleValue MARKET_REACH;

    // ---- ギルド（Phase 6 その3）
    public static final ForgeConfigSpec.IntValue GUILD_PARTICIPANTS;
    public static final ForgeConfigSpec.LongValue GUILD_STALL_DONATION_GOAL;
    public static final ForgeConfigSpec.BooleanValue GUILD_SHOW_TITLES;
    // Phase 6 その4：厳選と決闘
    public static final ForgeConfigSpec.DoubleValue REFINE_COST_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue COMMISSION_EXPIRE_DAYS;
    public static final ForgeConfigSpec.IntValue COMMISSION_MAX_ATTEMPTS;
    public static final ForgeConfigSpec.IntValue COMMISSION_MAX_OPEN;
    public static final ForgeConfigSpec.BooleanValue DUEL_ENABLED;
    public static final ForgeConfigSpec.IntValue DUEL_SECONDS;
    public static final ForgeConfigSpec.IntValue DUEL_CHALLENGE_SECONDS;
    public static final ForgeConfigSpec.IntValue DUEL_ARENAS;
    public static final ForgeConfigSpec.BooleanValue DUEL_ANNOUNCE;
    public static final ForgeConfigSpec.BooleanValue DUEL_ALLOW_POTIONS;
    // Phase 7 その1：コネクション量と輝き段階
    public static final ForgeConfigSpec.BooleanValue CONNECTION_ENABLED;
    public static final ForgeConfigSpec.DoubleValue CONNECTION_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue CONNECTION_DAILY_TOTAL;
    public static final ForgeConfigSpec.IntValue CONNECTION_DAILY_CATEGORY;
    public static final ForgeConfigSpec.IntValue CONNECTION_BASE_PLAYERS;
    public static final ForgeConfigSpec.DoubleValue CONNECTION_SCALE_MIN;
    public static final ForgeConfigSpec.DoubleValue CONNECTION_SCALE_MAX;
    public static final ForgeConfigSpec.IntValue CONNECTION_ACTIVE_DAYS;
    public static final ForgeConfigSpec.IntValue CONNECTION_RESET_HOUR;
    public static final ForgeConfigSpec.IntValue FIRST_MEET_STEP;
    public static final ForgeConfigSpec.IntValue FIRST_MEET_MAX;
    public static final ForgeConfigSpec.IntValue FIRST_MEET_DAILY;
    public static final ForgeConfigSpec.IntValue LEAD_LEVEL_GAP;
    public static final ForgeConfigSpec.BooleanValue PARTNER_ENABLED;
    public static final ForgeConfigSpec.IntValue PARTNER_GOAL;
    public static final ForgeConfigSpec.IntValue PARTNER_KNOTS;
    public static final ForgeConfigSpec.BooleanValue FISHING_ENABLED;

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
        AUTO_BUILD_CITY = b
                .comment("新しいワールドを初めて開いたとき、だれかが入る前にコネリアの街を建てておく（11月23日）。",
                        "このModの平地のワールド（ワールドの種類「コネリアの街」）だけ。前から遊んでいるワールドには建てない",
                        "（/amemaru admin city build で建てられる）。一度決めたら、街を壊しても勝手に建て直さない")
                .define("autoBuildCity", true);
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

        b.comment("経済（Phase 6）").push("economy");
        TRANSFER_ENABLED = b
                .comment("プレイヤー同士で、ノットを受け渡しできるようにする（11月22日に決定。ステータスの画面の「渡す」か、しゃがんで相手を右クリック）")
                .define("transferEnabled", true);
        TRANSFER_MAX = b
                .comment("1回に渡せるノットの上限（0なら上限なし。持っている分まで）")
                .defineInRange("transferMax", 0L, 0L, Long.MAX_VALUE);
        TRANSFER_COOLDOWN_SECONDS = b
                .comment("受け渡しのあと、次に渡せるまでの秒数（押しすぎを防ぐ）")
                .defineInRange("transferCooldownSeconds", 1.0, 0.0, 60.0);
        SHOP_REACH = b
                .comment("店のカウンターの呼び鈴から何ブロック以内で、買う・売るができるか")
                .defineInRange("shopReach", 8.0, 2.0, 64.0);
        b.pop();

        b.comment("個人倉庫（Phase 6。Phase 0-A 20章：契約は買い切り、1ページ＝27枠）").push("storage");
        STORAGE_CONTRACT_PRICE = b
                .comment("契約の値段（1ページ目）")
                .defineInRange("contractPrice", 500L, 0L, Long.MAX_VALUE);
        STORAGE_PAGE_PRICES = b
                .comment("2ページ目から順に、ページを増やす値段（Phase 0-A 20章：5,000／20,000／60,000／150,000）")
                .defineList("pagePrices", java.util.List.<Number>of(5_000L, 20_000L, 60_000L, 150_000L), o -> o instanceof Number n && n.longValue() >= 0);
        STORAGE_MAX_PAGES = b
                .comment("ページ数の上限（Phase 0-A 20章：5ページ＝135枠）。増やす値段の数＋1までしか増やせない")
                .defineInRange("maxPages", 5, 1, 20);
        STORAGE_REACH = b
                .comment("倉庫の窓口（封印箱・カウンター）から何ブロック以内で使えるか")
                .defineInRange("reach", 6.0, 2.0, 32.0);
        STORAGE_RECEIVED_MAX = b
                .comment("受け取り箱に入る品の数（市場で買った品などが届く）。いっぱいのときは、届く品を受け付けない")
                .defineInRange("receivedMax", 270, 27, 2700);
        b.pop();

        b.comment("フリーマーケット（Phase 6。Phase 0-A 20章。【確定】は値段の幅をconfigで変えられること、相場の変動をオン・オフできること）")
                .push("market");
        MARKET_OPEN_DAY = b
                .comment("フリーマーケットが開く進行日（Phase 0-A 11章：2日目に開場）。この日より前は、出品も購入もできない")
                .defineInRange("openDay", 2, 1, 20);
        MARKET_PRICE_MIN_PERCENT = b
                .comment("出品の値段の下限（1個あたり、相場の何%か。Phase 0-A 20章：50%）")
                .defineInRange("priceMinPercent", 50, 1, 100);
        MARKET_PRICE_MAX_PERCENT = b
                .comment("出品の値段の上限（1個あたり、相場の何%か。Phase 0-A 20章：200%）")
                .defineInRange("priceMaxPercent", 200, 100, 10_000);
        MARKET_LISTING_FEE_PERCENT = b
                .comment("出品の手数料（値段の合計の何%か。出品したときに払い、取り消しても戻らない。Phase 0-A 20章：2%）")
                .defineInRange("listingFeePercent", 2.0, 0.0, 50.0);
        MARKET_SALE_FEE_PERCENT = b
                .comment("売れたときの手数料（値段の合計の何%か。売上から引く。Phase 0-A 20章：5%）")
                .defineInRange("saleFeePercent", 5.0, 0.0, 50.0);
        MARKET_STALL_FEE_FACTOR = b
                .comment("露店で直接買ったときの、売れたときの手数料の倍率（Phase 0-A 20章：半分）")
                .defineInRange("stallFeeFactor", 0.5, 0.0, 1.0);
        MARKET_FREE_LISTING_DAYS = b
                .comment("出品の手数料がかからない進行日（Phase 0-A 11章：2日目の露店開き）")
                .defineList("freeListingDays", java.util.List.of(2), o -> o instanceof Integer);
        MARKET_LISTING_LIMIT = b
                .comment("1人が出品できる数（Phase 0-A 20章：20件）")
                .defineInRange("listingLimit", 20, 1, 500);
        MARKET_STALL_LISTING_LIMIT = b
                .comment("露店を借りている人が出品できる数（Phase 0-A 20章：30件）")
                .defineInRange("stallListingLimit", 30, 1, 500);
        MARKET_LISTING_DAYS = b
                .comment("出品が並ぶ日数（進行日）。出品した日から数えて、この日数が過ぎた朝に下がり、品は倉庫の受け取り箱へ戻る（0なら下げない）【補完】")
                .defineInRange("listingDays", 3, 0, 60);
        MARKET_STALL_RENT = b
                .comment("露店の区画を借りる値段（1回分）【補完】")
                .defineInRange("stallRent", 1_000L, 0L, Long.MAX_VALUE);
        MARKET_STALL_DAYS = b
                .comment("露店を1回で借りられる日数（進行日。借りた日を含む）。延ばせるのは、残りがこの日数より少ないとき【補完】")
                .defineInRange("stallDays", 3, 1, 30);
        MARKET_STALL_NAME_MAX = b
                .comment("露店の店名の長さの上限（文字）")
                .defineInRange("stallNameMax", 12, 2, 32);
        MARKET_NG_WORDS = b
                .comment("露店の店名で伏せる言葉（Phase 0-B 不正対策：NGワードは＊で伏せる。管理者は /amemaru admin market stall hide でも隠せる）")
                .defineList("ngWords", java.util.List.of(), o -> o instanceof String);
        MARKET_PRICE_DRIFT = b
                .comment("相場の変動（Phase 0-A 20章【確定】：オン・オフを切り替えられる）。日が変わるとき、その日の取引の中央値に向けて相場を動かす")
                .define("priceDrift", true);
        MARKET_DRIFT_MAX_PERCENT = b
                .comment("相場が1日に動ける幅（%。Phase 0-A 20章：±10%）。上限まで動いた品は、管理者に知らせる")
                .defineInRange("driftMaxPercent", 10.0, 0.0, 100.0);
        MARKET_DRIFT_MIN_TRADES = b
                .comment("相場を動かすのに要る、1日の取引の数（Phase 0-A 20章：5件未満の品は動かさない）")
                .defineInRange("driftMinTrades", 5, 1, 1000);
        MARKET_REACH = b
                .comment("市場監督の小屋・露店から何ブロック以内で、借りる・露店で買うができるか")
                .defineInRange("reach", 8.0, 2.0, 64.0);
        b.pop();

        b.comment("ギルドとクエスト・主要NPC・古代記録・称号（Phase 6 その3）").push("guild");
        GUILD_PARTICIPANTS = b
                .comment("サーバーの参加人数。寄付の目標を、この人数に合わせて増減する（Phase 0-A 7章：目標は25人基準。13章の人数補正と同じ）")
                .defineInRange("participants", 25, 1, 500);
        GUILD_STALL_DONATION_GOAL = b
                .comment("フリーマーケットの露店の区画を12から20に増やす寄付の目標（25人基準のノット）。フィン・ロッソが寄付を募り、",
                        "目標に届いた次の朝（次の進行日）に増える（Phase 0-A 7章。11月20日に決定）。目標の額は【補完】")
                .defineInRange("stallDonationGoal", 20_000L, 1L, Long.MAX_VALUE);
        GUILD_SHOW_TITLES = b
                .comment("表示している称号を、頭の上・チャット・Tabの一覧の名前の前に出す（Phase 0-A 22章【確定】）")
                .define("showTitles", true);
        b.pop();

        b.comment("アクセサリーの厳選と、装飾師への厳選の依頼（Phase 6 その4。Phase 0-A 21章）").push("refine");
        REFINE_COST_MULTIPLIER = b
                .comment("厳選の費用の倍率（Phase 0-B 7章：1.0）。振り直し・固定・強化の素材とノットの両方にかける（素材は切り上げ）。",
                        "費用の表そのものは data/amemaru/amemaru/accessory_rules/default.json の refine")
                .defineInRange("costMultiplier", 1.0, 0.0, 100.0);
        COMMISSION_EXPIRE_DAYS = b
                .comment("依頼台の依頼が、誰も受けないまま何日（進行日）で取り下げになるか。預けた品・素材・ノットは持ち主に戻る【補完】")
                .defineInRange("commissionExpireDays", 3, 1, 365);
        COMMISSION_MAX_ATTEMPTS = b
                .comment("依頼で決められる「回数の上限」の最大（振り直しの回数）【補完】")
                .defineInRange("commissionMaxAttempts", 50, 1, 1000);
        COMMISSION_MAX_OPEN = b
                .comment("1人が同時に出せる依頼の数【補完】")
                .defineInRange("commissionMaxOpen", 3, 1, 50);
        b.pop();

        b.comment("決闘（Phase 6 その4。11月22日に決定：ほかのプレイヤーに決闘を申し込み、相手が受けると1対1で戦う。",
                "街の中では戦わず、決闘場で戦う。ほかのプレイヤーは観戦できる。決闘の間は、武器や防具などの耐久は減らない）").push("duel");
        DUEL_ENABLED = b
                .comment("決闘を申し込めるようにする")
                .define("enabled", true);
        DUEL_SECONDS = b
                .comment("決闘の時間（秒）。時間切れのときは、HPの割合が多い方の勝ち（同じなら引き分け）【補完】")
                .defineInRange("seconds", 180, 30, 3600);
        DUEL_CHALLENGE_SECONDS = b
                .comment("申し込みが、何秒で消えるか")
                .defineInRange("challengeSeconds", 60, 10, 3600);
        DUEL_ARENAS = b
                .comment("同時に使える決闘場の数（塔の空間の、どの階とも離れた所に建てる）【補完】")
                .defineInRange("arenas", 4, 1, 16);
        DUEL_ANNOUNCE = b
                .comment("決闘が始まったことを、サーバー全体に知らせる（［観戦する］のボタンつき）")
                .define("announce", true);
        DUEL_ALLOW_POTIONS = b
                .comment("決闘の間に、回復薬・マナ水を使えるようにする（初期は使えない。腕前の勝負にするため）【補完】")
                .define("allowPotions", false);
        b.pop();

        b.comment("コネクション量と輝き段階（Phase 7 その1。Phase 0-A 13章・Phase 0-B 7章）。増える行動と量はデータパックの connection_actions/、",
                "段階のしきい値と効果は connection_rules/default.json").push("connection");
        CONNECTION_ENABLED = b
                .comment("コネクション量（CP）を数える")
                .define("enabled", true);
        CONNECTION_MULTIPLIER = b
                .comment("増えるCPの倍率（Phase 0-B 7章：1.0）")
                .defineInRange("multiplier", 1.0, 0.0, 10.0);
        CONNECTION_DAILY_TOTAL = b
                .comment("1人1日に増やせるCPの合計の上限（Phase 0-A 13章：600）")
                .defineInRange("dailyTotal", 600, 1, 100_000);
        CONNECTION_DAILY_CATEGORY = b
                .comment("1人1日に増やせるCPの、分野ごとの上限（Phase 0-A 13章：150。7分野＝戦闘・生産・商売・交流・娯楽・街・物語）")
                .defineInRange("dailyCategory", 150, 1, 100_000);
        CONNECTION_BASE_PLAYERS = b
                .comment("輝き段階のしきい値の基準の人数（Phase 0-A 13章：25人）。しきい値は「直近の平均アクティブ人数 ÷ この人数」倍になる")
                .defineInRange("basePlayers", 25, 1, 1000);
        CONNECTION_SCALE_MIN = b
                .comment("人数補正の倍率の下限（Phase 0-A 13章：0.4）")
                .defineInRange("scaleMin", 0.4, 0.01, 10.0);
        CONNECTION_SCALE_MAX = b
                .comment("人数補正の倍率の上限（Phase 0-A 13章：2.0）")
                .defineInRange("scaleMax", 2.0, 0.01, 10.0);
        CONNECTION_ACTIVE_DAYS = b
                .comment("平均アクティブ人数を数える日数（Phase 0-A 13章：直近3日。今日を含む）")
                .defineInRange("activeDays", 3, 1, 14);
        CONNECTION_RESET_HOUR = b
                .comment("1日の上限（CP・結び札など）を切り替える時刻（0〜23時。Phase 0-B 4.1：進行日ではなく、毎日決まった時刻。4時）")
                .defineInRange("resetHour", 4, 0, 23);
        FIRST_MEET_STEP = b
                .comment("はじめましてボーナス：今のパーティにいる「今日初めて組んだ人」1人ごとに、経験値とドロップの判定が上がる割合（%。",
                        "Phase 0-A 15章：その人数×10%）")
                .defineInRange("firstMeetStep", 10, 0, 100);
        FIRST_MEET_MAX = b
                .comment("はじめましてボーナスの上限（%。Phase 0-A 15章：+30%）")
                .defineInRange("firstMeetMax", 30, 0, 300);
        FIRST_MEET_DAILY = b
                .comment("はじめましてボーナスに数える「初めて組んだ人」の、1日の人数（Phase 0-A 15章：1日5人まで）")
                .defineInRange("firstMeetDaily", 5, 0, 100);
        LEAD_LEVEL_GAP = b
                .comment("先導者：サーバーの中央値より、このレベル以上低い人と組むと、先導する側に報酬（Phase 0-A 15章：10レベル）。",
                        "途中参加者（来てから3日のうち）と組んだときも同じ")
                .defineInRange("leadLevelGap", 10, 1, 200);
        PARTNER_ENABLED = b
                .comment("今日の相棒（Phase 0-A 15章：毎日ログインしたときに、まだ組んだことのない人を1人「今日の相棒」として示す）")
                .define("partnerEnabled", true);
        PARTNER_GOAL = b
                .comment("今日の相棒の依頼：相棒と同じパーティで倒す敵の数【補完】")
                .defineInRange("partnerGoal", 10, 1, 1000);
        PARTNER_KNOTS = b
                .comment("今日の相棒の依頼を終えたときの、ひとりぶんのお礼（ノット）【補完】。コネクションは connection_actions/today_partner.json")
                .defineInRange("partnerKnots", 150, 0, 1_000_000);
        b.pop();

        b.comment("釣り（Phase 7 その2。Phase 0-A 15章）。魚の表と数は、データパックの fish/ と fishing_rules/default.json").push("fishing");
        FISHING_ENABLED = b
                .comment("釣りのミニゲーム（糸の張り）と魚図鑑を使う（オフにすると、バニラの釣りのまま）")
                .define("enabled", true);
        b.pop();

        SPEC = b.build();
    }

    // ---------------------------------------------------------------- GameTest で値を変える

    /**
     * GameTest で変えた値（11月23日）。{@code ConfigValue.set} は config のファイルを書き、Forge のファイルの見張りが別のスレッドで
     * それを読み直すと、読み直しが書く前の中身で値を戻してしまうことがある（run 161 の marketListBuyCollect で、取引所の開く日が
     * テストの途中で戻った）。テストはここで値を変え、ファイルは書かない。
     */
    private static final Map<ForgeConfigSpec.ConfigValue<?>, Object> TEST_OVERRIDES = new ConcurrentHashMap<>();

    /** 値（GameTest で変えていれば、その値）。テストで変える設定は、これで読む。 */
    @SuppressWarnings("unchecked")
    public static <T> T value(ForgeConfigSpec.ConfigValue<T> config) {
        Object override = TEST_OVERRIDES.get(config);
        return override != null ? (T) override : config.get();
    }

    /** GameTest 用：値を変える（null で、もとの config の値に戻す）。 */
    public static <T> void overrideForTest(ForgeConfigSpec.ConfigValue<T> config, @Nullable T value) {
        if (value == null) {
            TEST_OVERRIDES.remove(config);
        } else {
            TEST_OVERRIDES.put(config, value);
        }
    }

    private ServerConfig() {
    }
}

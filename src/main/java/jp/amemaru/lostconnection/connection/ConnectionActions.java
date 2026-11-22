package jp.amemaru.lostconnection.connection;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.resources.ResourceLocation;

/**
 * CPが増える行動のID（{@code connection_actions/<名前>.json}。Phase 0-A 13章の表の順）。知らせる側は、このIDで
 * {@link ConnectionService#award} を呼ぶ。数はデータで変えられる。
 */
public final class ConnectionActions {
    // 戦闘
    public static final ResourceLocation PARTY_KILL = AmemaruMod.id("party_kill");
    public static final ResourceLocation FLOOR_BOSS = AmemaruMod.id("floor_boss");
    public static final ResourceLocation CHAPTER_BOSS = AmemaruMod.id("chapter_boss");
    public static final ResourceLocation LINK_COMBO = AmemaruMod.id("link_combo");
    // 生産
    public static final ResourceLocation CRAFT_FOR_OTHER = AmemaruMod.id("craft_for_other");
    public static final ResourceLocation REPAIR_FOR_OTHER = AmemaruMod.id("repair_for_other");
    public static final ResourceLocation REFINE_COMMISSION = AmemaruMod.id("refine_commission");
    // 商売
    public static final ResourceLocation MARKET_TRADE = AmemaruMod.id("market_trade");
    public static final ResourceLocation STALL_VISITORS = AmemaruMod.id("stall_visitors");
    // 交流
    public static final ResourceLocation REVIVE = AmemaruMod.id("revive");
    public static final ResourceLocation FIRST_PARTY = AmemaruMod.id("first_party");
    public static final ResourceLocation THANKS_SEND = AmemaruMod.id("thanks_send");
    public static final ResourceLocation THANKS_RECEIVE = AmemaruMod.id("thanks_receive");
    public static final ResourceLocation NEWCOMER_PARTY = AmemaruMod.id("newcomer_party");
    /** 今日の相棒の依頼を終えた（15章。13章の表にはないので、量は【補完】）。 */
    public static final ResourceLocation TODAY_PARTNER = AmemaruMod.id("today_partner");
    // 娯楽（釣りと釣り大会はその2、カジノとカジノナイトはその3で数え始める）
    public static final ResourceLocation FISH_NEAR_OTHERS = AmemaruMod.id("fish_near_others");
    public static final ResourceLocation FISH_DEX = AmemaruMod.id("fish_dex");
    public static final ResourceLocation CASINO_TABLE = AmemaruMod.id("casino_table");
    public static final ResourceLocation EVENT_JOIN = AmemaruMod.id("event_join");
    // 街（共同事業の寄付はその4、襲撃は Phase 8 で数え始める）
    public static final ResourceLocation DONATION = AmemaruMod.id("donation");
    public static final ResourceLocation RAID_SUPPORT = AmemaruMod.id("raid_support");
    public static final ResourceLocation NPC_REQUEST = AmemaruMod.id("npc_request");
    // 物語
    public static final ResourceLocation STORY_QUEST = AmemaruMod.id("story_quest");
    public static final ResourceLocation RECORD_FOUND = AmemaruMod.id("record_found");
    public static final ResourceLocation DAILY_QUEST = AmemaruMod.id("daily_quest");

    private ConnectionActions() {
    }
}

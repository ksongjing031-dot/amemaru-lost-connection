package jp.amemaru.lostconnection.guild;

import java.util.List;

/**
 * 称号の条件に使う数の名前（Phase 0-A 22章の通常称号の条件）。
 *
 * <p>倒した数・作った数・起こした数・組んだことのある人の数・古代記録の割合は、それぞれの記録から数える
 * （{@link TitleService#counterValue}）。ここに書いた残りの数は {@link GuildData} に足していく。</p>
 */
public final class GuildCounters {
    /** 倒した敵の数（図鑑の記録から数える）。 */
    public static final String KILLS = "kills";
    /** 連携技を成立させた回数（連携に加わった全員に数える）【補完】。 */
    public static final String LINK_COMBOS = "link_combos";
    /** 装備を作った数（工房の記録から数える）。 */
    public static final String CRAFTED = "crafted";
    /** 厳選の回数（Phase 6 その4で数える）。 */
    public static final String REROLLS = "rerolls";
    /** 他人のための制作・修理・厳選の合計（協力依頼。Phase 7で数える）。 */
    public static final String HELPED = "helped";
    /** フリマで取引を成立させた回数（売り手・買い手それぞれ）。 */
    public static final String TRADES = "trades";
    /** 自分の露店に来た人（1日に1人1回）。 */
    public static final String STALL_VISITORS = "stall_visitors";
    /** 仲間を起こした回数（ダウンの記録から数える）。 */
    public static final String REVIVES = "revives";
    /** 一緒にパーティを組んだことのある人の数（縁帳から数える）。 */
    public static final String PARTNERS = "partners";
    /** 結び札をもらった数（Phase 7で数える）。 */
    public static final String THANKS_RECEIVED = "thanks_received";
    /** 途中参加者・後輩とパーティを組んだ回数（先導者。Phase 7で数える）。 */
    public static final String LEADS = "leads";
    /** 魚図鑑の割合（%。Phase 7で数える）。 */
    public static final String FISH_PERCENT = "fish_percent";
    /** 古代記録を集めた割合（%。記録から数える）。 */
    public static final String RECORDS_PERCENT = "records_percent";
    /** 称号を表示しないまま倒した章ボスの数（名もなき英雄。Phase 8で数える）。 */
    public static final String HIDDEN_CHAPTER_BOSSES = "hidden_chapter_bosses";
    /** 終えたクエストの数。 */
    public static final String QUESTS = "quests";

    /** 条件に書ける数の名前（定義を読むときに確かめる）。 */
    public static final List<String> ALL = List.of(KILLS, LINK_COMBOS, CRAFTED, REROLLS, HELPED, TRADES, STALL_VISITORS, REVIVES,
            PARTNERS, THANKS_RECEIVED, LEADS, FISH_PERCENT, RECORDS_PERCENT, HIDDEN_CHAPTER_BOSSES, QUESTS);

    private GuildCounters() {
    }
}

package jp.amemaru.lostconnection.accessory;

import jp.amemaru.lostconnection.player.StatType;

import javax.annotation.Nullable;
import java.util.List;

/**
 * アクセサリーの枠（Phase 0-A 21章）。枠の入れ物は Curios API（Phase 0-B 1章）で、枠の名前は他のModと
 * 混ざらないように {@code amemaru_} をつける（{@code data/amemaru/curios/slots/}）。
 *
 * <table>
 *   <tr><th>枠</th><th>数</th><th>メインステータス</th><th>体に表示</th></tr>
 *   <tr><td>指輪</td><td>2</td><td>攻撃または魔力（作るときの素材で決まる。「闘環」「智環」）</td><td>しない（小さすぎるため）</td></tr>
 *   <tr><td>首飾り</td><td>1</td><td>HP</td><td>する（胸元）</td></tr>
 *   <tr><td>腕輪</td><td>1</td><td>防御</td><td>する（手首）</td></tr>
 *   <tr><td>お守り</td><td>1</td><td>ランダム（MP・素早さ・会心・回復量・ロスト耐性のどれか）</td><td>する（腰に下げる）</td></tr>
 *   <tr><td>留め具（11日目に解放）</td><td>1</td><td>会心ダメージまたはクールダウン短縮</td><td>する（マントや胸の留め具）</td></tr>
 * </table>
 *
 * <p>順番は保存（表示のオン・オフのビット）と通信で使うため、変えないこと。</p>
 */
public enum AccessorySlot {
    RING("ring", 2, false, List.of(StatType.ATTACK, StatType.MAGIC)),
    NECKLACE("necklace", 1, true, List.of(StatType.HP)),
    BRACELET("bracelet", 1, true, List.of(StatType.DEFENSE)),
    CHARM("charm", 1, true, List.of(StatType.MP, StatType.SPEED, StatType.CRIT, StatType.HEAL_POWER, StatType.LOST_RESIST)),
    CLASP("clasp", 1, true, List.of(StatType.CRIT_DAMAGE, StatType.COOLDOWN_REDUCTION));

    private static final AccessorySlot[] VALUES = values();

    private final String id;
    private final int size;
    private final boolean shownOnBody;
    private final List<StatType> mainStats;

    AccessorySlot(String id, int size, boolean shownOnBody, List<StatType> mainStats) {
        this.id = id;
        this.size = size;
        this.shownOnBody = shownOnBody;
        this.mainStats = mainStats;
    }

    public String id() {
        return id;
    }

    /** Curios の枠の名前。 */
    public String curiosId() {
        return "amemaru_" + id;
    }

    /** 枠の数（留め具は、解放されるまで0）。 */
    public int size() {
        return size;
    }

    /** 体に表示する枠か（表示のオン・オフを選べる）。 */
    public boolean shownOnBody() {
        return shownOnBody;
    }

    /** メインステータスになれる種類。 */
    public List<StatType> mainStats() {
        return mainStats;
    }

    public String translationKey() {
        return "accessory_slot.amemaru." + id;
    }

    @Nullable
    public static AccessorySlot byId(String id) {
        for (AccessorySlot slot : VALUES) {
            if (slot.id.equals(id)) {
                return slot;
            }
        }
        return null;
    }

    @Nullable
    public static AccessorySlot byCuriosId(String curiosId) {
        for (AccessorySlot slot : VALUES) {
            if (slot.curiosId().equals(curiosId)) {
                return slot;
            }
        }
        return null;
    }

    public static AccessorySlot byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : RING;
    }

    public static AccessorySlot[] all() {
        return VALUES.clone();
    }
}

package jp.amemaru.lostconnection.item;

import javax.annotation.Nullable;

/**
 * 武器の種類（アイテムの種類と一緒に、Modに同梱した {@code amemaru/item_types.json} で決める）。
 *
 * <p>攻撃の速さ（バニラの攻撃の間隔）と、両手で持つかは種類で決まる。ダメージはバニラの値ではなく、
 * 攻撃のステータスで決まる（{@code CombatService}）。Phase 6 その2で、残りの職業の武器（鈴杖・弓・二刀の短剣・籠手）を足した。</p>
 * <p>弓は、左クリックで矢を射る（近接攻撃はしない。{@code BasicShotService}）。二刀の短剣と籠手は、両手で持つ（盾は持てない）。</p>
 */
public enum WeaponKind {
    /** 片手剣（ナイト）。 */
    SWORD("sword", false, -2.4),
    /** 両手剣（ナイト）。 */
    GREATSWORD("greatsword", true, -3.0),
    /** 杖（ウィザード）。 */
    STAFF("staff", false, -2.8),
    /** 鈴杖（ヒーラー）。 */
    BELL_STAFF("bell_staff", false, -2.8),
    /** 弓（アーチャー）。左クリックで矢を射る。 */
    BOW("bow", true, -2.7),
    /** 二刀の短剣（ローグ）。速い。 */
    DAGGERS("daggers", true, -1.6),
    /** 籠手（モンク）。いちばん速い。 */
    FIST("fist", true, -1.4);

    private final String id;
    private final boolean twoHanded;
    private final double attackSpeed;

    WeaponKind(String id, boolean twoHanded, double attackSpeed) {
        this.id = id;
        this.twoHanded = twoHanded;
        this.attackSpeed = attackSpeed;
    }

    public String id() {
        return id;
    }

    public boolean twoHanded() {
        return twoHanded;
    }

    /** バニラの攻撃の速さに足す値。 */
    public double attackSpeed() {
        return attackSpeed;
    }

    /** 言語ファイルのキー（「片手剣」など）。 */
    public String translationKey() {
        return "weapon_kind.amemaru." + id;
    }

    @Nullable
    public static WeaponKind byId(String id) {
        for (WeaponKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }
}

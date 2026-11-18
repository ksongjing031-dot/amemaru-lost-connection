package jp.amemaru.lostconnection.equipment;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * 装備とアクセサリーのレアリティ（6段階。Phase 0-A 20章）。
 *
 * <table>
 *   <tr><th>段階</th><th>文字の色</th><th>見た目の目安</th></tr>
 *   <tr><td>コモン</td><td>白</td><td>素朴</td></tr>
 *   <tr><td>アンコモン</td><td>緑</td><td>装飾が少し増える</td></tr>
 *   <tr><td>レア</td><td>青</td><td>光る部分が入る</td></tr>
 *   <tr><td>エピック</td><td>紫</td><td>光る部分と小さなパーティクル</td></tr>
 *   <tr><td>レジェンダリー</td><td>橙</td><td>揺れる・回る部品と、常に出るパーティクル</td></tr>
 *   <tr><td>ユニーク</td><td>虹色（文字の色が変わり続ける）</td><td>専用モデルと専用エフェクト（Phase 8）</td></tr>
 * </table>
 *
 * <p>ステータスにかかる倍率は {@code gear_rules/default.json} で決める（データパックで変えられる）。
 * 順番は保存と通信で使うため、変えないこと。</p>
 */
public enum Rarity {
    COMMON("common", 0xF2F2F2),
    UNCOMMON("uncommon", 0x6BE36B),
    RARE("rare", 0x5AA9FF),
    EPIC("epic", 0xC07CFF),
    LEGENDARY("legendary", 0xFFA53A),
    UNIQUE("unique", 0xFF7AD9);

    private static final Rarity[] VALUES = values();

    private final String id;
    private final int color;

    Rarity(String id, int color) {
        this.id = id;
        this.color = color;
    }

    /** JSONと保存データで使う名前。 */
    public String id() {
        return id;
    }

    /** 言語ファイルのキー（「レア」など）。 */
    public String translationKey() {
        return "rarity.amemaru." + id;
    }

    /** 文字の色（ユニークは時間で変わる。{@link #colorAt(long)}）。 */
    public int color() {
        return color;
    }

    /** その時刻の文字の色。ユニークは虹色に変わり続ける。 */
    public int colorAt(long millis) {
        if (this != UNIQUE) {
            return color;
        }
        float hue = (millis % 4000L) / 4000.0F;
        return Mth.hsvToRgb(hue, 0.55F, 1.0F);
    }

    /** 名前などに使う文字の飾り。 */
    public Style style() {
        return Style.EMPTY.withColor(TextColor.fromRgb(colorAt(System.currentTimeMillis())));
    }

    /** 文字にレアリティの色をつける。 */
    public MutableComponent paint(MutableComponent text) {
        return text.withStyle(style());
    }

    /** 「レア」などの名前（色つき）。 */
    public MutableComponent displayName() {
        return paint(Component.translatable(translationKey()));
    }

    /** 光る部分がある段階か（レア以上）。 */
    public boolean glows() {
        return ordinal() >= RARE.ordinal();
    }

    /** 持っている間、小さなパーティクルが出る段階か（エピック以上）。 */
    public boolean sparkles() {
        return ordinal() >= EPIC.ordinal();
    }

    /** パーティクルが常に出る段階か（レジェンダリー以上）。 */
    public boolean alwaysSparkles() {
        return ordinal() >= LEGENDARY.ordinal();
    }

    /** 番号から探す。範囲の外ならコモン。 */
    public static Rarity byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : COMMON;
    }

    /** 名前から探す。見つからなければ null。 */
    @Nullable
    public static Rarity byId(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        for (Rarity rarity : VALUES) {
            if (rarity.id.equals(key)) {
                return rarity;
            }
        }
        return null;
    }

    /** すべての段階（低い順）。 */
    public static Rarity[] all() {
        return VALUES.clone();
    }
}

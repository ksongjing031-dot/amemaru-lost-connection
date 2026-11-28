package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.fx.FxTextures;
import jp.amemaru.lostconnection.client.fx.FxWorld;
import jp.amemaru.lostconnection.client.fx.FxWorld.Anchor;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.network.s2c.BossImpactS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * ボスの技が当たった瞬間の演出（11月27日のご指示「ボスの攻撃エフェクトがほぼ存在しない、攻撃範囲が見えるだけなので、ボス特有の
 * 攻撃時にプレイヤーがスキルを使った時のようなエフェクトを追加する」【補完】）。
 *
 * <p>予兆と同じ形に、ボスごとの色と材質で出す（プレイヤーのスキルと同じ {@code FxWorld} の部品）：</p>
 * <ul>
 *   <li>扇（薙ぎ払い・爪など）：扇いっぱいを、2本の斬撃が走る</li>
 *   <li>線（突進・光線・水の大砲・氷柱など）：地割れが走り、材質によって、とげ（土・木・石・氷・鏡）か、光の帯（水・音）が立つ</li>
 *   <li>円（踏みつけ・着地・叩きつけなど）：衝撃波と、とげか光の柱。中心に光</li>
 *   <li>形のない技：ボスのまわりに、光と小さな衝撃波</li>
 * </ul>
 * 大きな一撃（半径・長さが6ブロック以上、固有のギミックをしくじったとき）は、近くの人の画面を少し揺らす（設定で止められる）。
 */
public final class BossImpactFx {
    /** 材質：とげを出すか、光の柱・帯を出すか。 */
    private enum Material { EARTH, WOOD, WATER, SOUND, STONE, ICE, PRISM }

    /** ボスごとの色（主・強い・明るい）と材質。 */
    private record Theme(int main, int accent, int light, Material material) {
        boolean spikes() {
            return material == Material.EARTH || material == Material.WOOD || material == Material.STONE || material == Material.ICE
                    || material == Material.PRISM;
        }
    }

    private static final Theme DEFAULT = new Theme(0xC9B79C, 0xF0D9A8, 0xFFF6E0, Material.STONE);
    private static final Map<String, Theme> THEMES = Map.of(
            "veldhorn", new Theme(0x8B6B3E, 0xE2B65A, 0xF6E3B0, Material.EARTH),
            "old_ward", new Theme(0x5E7F3A, 0x8FE07A, 0xD8FFC8, Material.WOOD),
            "hydram", new Theme(0x2E7FB8, 0x5FC6F2, 0xDFF6FF, Material.WATER),
            "cantare", new Theme(0xB58CFF, 0xF2C94C, 0xFFF6D8, Material.SOUND),
            "ancient_guardian", new Theme(0x8E8A80, 0x4FCC9E, 0xD3FFEE, Material.STONE),
            "ordo", new Theme(0x7FC8F0, 0xBFEFFF, 0xFFFFFF, Material.ICE),
            "reflecta", new Theme(0x9A7CF0, 0xC9A8FF, 0xF0F4FF, Material.PRISM),
            "mune", new Theme(0x2E6E9E, 0x7FDDE8, 0xA8F7FF, Material.WATER));

    /** 固有のギミックをしくじったときの、大きな一撃（衝撃を大きく）。 */
    private static final java.util.Set<String> BIG = java.util.Set.of("veldhorn:earth_roar", "hydram:flood", "cantare:echo",
            "ancient_guardian:judge", "ancient_guardian:sever", "ordo:gaze", "mune:wave");

    private BossImpactFx() {
    }

    public static void play(BossImpactS2C message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Theme theme = THEMES.getOrDefault(message.boss(), DEFAULT);
        boolean big = BIG.contains(message.boss() + ":" + message.skill());
        Vec3 at = message.at();
        float size = Math.max(1.0F, message.size());
        if ("nova".equals(message.skill())) {
            // 11月28日：全員を狙う大技。部屋いっぱいに衝撃波が2重に広がる（打たれた人の所には、別に小さな一撃が出る）
            nova(at, size, theme);
            return;
        }
        switch (message.shape()) {
            case AmemaruMob.TELEGRAPH_CONE -> cone(at, message.yaw(), size, message.arg(), theme);
            case AmemaruMob.TELEGRAPH_LINE -> line(at, message.yaw(), size, message.arg(), theme, big);
            case AmemaruMob.TELEGRAPH_CIRCLE -> circle(at, size, theme, big);
            default -> burst(message.entityId(), at, size, theme, big);
        }
        if (big || size >= 6.0F) {
            FxWorld.shake(at, big ? 14 : 8, big ? 0.9F : 0.45F);
        }
    }

    /** 全員を狙う大技（11月28日）：真ん中に光、部屋いっぱいに衝撃波が2重に広がり、画面が揺れる。 */
    private static void nova(Vec3 at, float radius, Theme theme) {
        FxWorld.add(new FxWorld.Flare(Anchor.at(at.add(0.0, 1.2, 0.0)), 6.0F, theme.light(), 16, FxTextures.GLOW, 0.0F, 3));
        FxWorld.add(new FxWorld.Shockwave(at, 1.0F, radius, 30, theme.accent(), 2.8F, 0.75F));
        FxWorld.later(5, () -> FxWorld.add(new FxWorld.Shockwave(at, 1.0F, radius * 0.85F, 26, theme.main(), 1.8F, 0.6F)));
        FxWorld.shake(at, 16, 1.0F);
    }

    /** 扇：扇いっぱいを、2本の斬撃が走る（外は強い色、内は明るい色）。 */
    private static void cone(Vec3 at, float yaw, float radius, float angle, Theme theme) {
        float half = (angle <= 0.0F ? 120.0F : angle) / 2.0F;
        Vec3 center = at.add(0.0, 1.0, 0.0);
        FxWorld.add(new FxWorld.Slash(Anchor.at(center), yaw, 6.0F, radius * 0.95F, 1.4F, -half, half, 10, 4, theme.accent()));
        FxWorld.add(new FxWorld.Slash(Anchor.at(center.add(0.0, 0.35, 0.0)), yaw, -8.0F, radius * 0.7F, 0.9F, -half * 0.9F, half * 0.9F,
                9, 3, theme.light()));
        if (theme.spikes()) {
            // 扇のふちに、小さなとげが並ぶ
            double rad = Math.toRadians(yaw);
            for (int i = 0; i <= 4; i++) {
                double a = rad + Math.toRadians(-half + half * 2.0 * i / 4.0);
                Vec3 spot = at.add(-Math.sin(a) * radius * 0.85, 0.0, Math.cos(a) * radius * 0.85);
                FxWorld.add(new FxWorld.Spikes(spot, 0.6F, 3, 1.1F, theme.main(), 18, (long) (spot.x * 31 + spot.z * 17)));
            }
        } else {
            FxWorld.add(new FxWorld.Shockwave(at, 0.6F, radius, 12, theme.accent(), 0.6F, 0.55F));
        }
    }

    /** 線：地割れが走り、とげか光の帯が立つ。 */
    private static void line(Vec3 at, float yaw, float length, float width, Theme theme, boolean big) {
        double rad = Math.toRadians(yaw);
        Vec3 dir = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
        Vec3 to = at.add(dir.scale(length));
        float w = Math.max(1.2F, width <= 0.0F ? 2.0F : width);
        FxWorld.add(new FxWorld.GroundCut(at, to, w, theme.main(), big ? 40 : 26, 4));
        if (theme.spikes()) {
            int steps = Math.max(2, Math.round(length / 2.0F));
            for (int i = 1; i <= steps; i++) {
                Vec3 spot = at.add(dir.scale(length * i / (double) steps));
                int delay = i;
                FxWorld.later(delay, () -> FxWorld.add(new FxWorld.Spikes(spot, w * 0.45F, 4, big ? 2.2F : 1.5F, theme.main(), 20,
                        (long) (spot.x * 13 + spot.z * 7))));
            }
        } else {
            FxWorld.add(new FxWorld.Beam(Anchor.at(at.add(0.0, 1.0, 0.0)), Anchor.at(to.add(0.0, 1.0, 0.0)), w * 0.6F, FxTextures.BEAM,
                    theme.accent(), 14, 2.0F, 0.6F));
        }
        FxWorld.add(new FxWorld.Shockwave(to, 0.4F, w * 1.4F, 12, theme.light(), 0.8F, 0.7F));
    }

    /** 円：衝撃波と、とげか光の柱。中心に光。 */
    private static void circle(Vec3 at, float radius, Theme theme, boolean big) {
        FxWorld.add(new FxWorld.Shockwave(at, 0.5F, radius, big ? 26 : 16, theme.accent(), big ? 2.4F : 1.2F, 0.85F));
        FxWorld.add(new FxWorld.Flare(Anchor.at(at.add(0.0, 0.8, 0.0)), Math.min(4.0F, radius * 0.6F), theme.light(), 12, FxTextures.GLOW,
                0.0F, 2));
        if (theme.spikes()) {
            int count = Math.min(48, 8 + Math.round(radius * 3.0F));
            FxWorld.add(new FxWorld.Spikes(at, radius * 0.8F, count, big ? 2.6F : 1.6F, theme.main(), big ? 36 : 22,
                    (long) (at.x * 7 + at.z * 3)));
        } else {
            FxWorld.add(new FxWorld.Pillar(Anchor.at(at), Math.max(0.6F, radius * 0.45F), big ? 10.0F : 6.0F, theme.accent(),
                    big ? 26 : 18));
            if (theme.material() == Material.SOUND) {
                // 音：少し遅れて、もう2つの輪が広がる
                FxWorld.later(4, () -> FxWorld.add(new FxWorld.Shockwave(at, 0.5F, radius * 1.15F, 14, theme.main(), 0.6F, 0.7F)));
                FxWorld.later(8, () -> FxWorld.add(new FxWorld.Shockwave(at, 0.5F, radius * 1.3F, 14, theme.light(), 0.4F, 0.6F)));
            }
        }
        if (big) {
            FxWorld.add(new FxWorld.Shockwave(at, 1.0F, radius * 1.25F, 34, theme.main(), 3.2F, 0.6F));
        }
    }

    /** 形のない技：ボスのまわりに、光と小さな衝撃波。 */
    private static void burst(int entityId, Vec3 at, float size, Theme theme, boolean big) {
        FxWorld.add(new FxWorld.Flare(Anchor.center(entityId, Vec3.ZERO), big ? 4.0F : 2.4F, theme.light(), 14, FxTextures.GLOW, 0.0F, 3));
        FxWorld.add(new FxWorld.Shockwave(at, 0.5F, Math.max(4.0F, size), big ? 24 : 14, theme.accent(), big ? 2.0F : 0.8F, 0.75F));
    }
}

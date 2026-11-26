package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.fx.FxTextures;
import jp.amemaru.lostconnection.client.fx.FxWorld;
import jp.amemaru.lostconnection.client.fx.FxWorld.Anchor;
import jp.amemaru.lostconnection.network.s2c.AwakenFxS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 覚醒の演出（11月27日【補完】。{@link AwakenFxS2C}）。
 *
 * <ul>
 *   <li>覚醒した：足もとに職業の色の魔法陣が2重に回り、光の柱が昇り、まわりから光が集まる。胸の高さで光が弾ける</li>
 *   <li>「もう一度」：当たった相手に、職業の色の衝撃波と光（少し揺れる）</li>
 * </ul>
 */
public final class AwakenFx {
    private AwakenFx() {
    }

    public static void play(AwakenFxS2C message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(message.entityId());
        if (entity == null) {
            return;
        }
        int color = message.color() & 0xFFFFFF;
        int light = lighter(color);
        Vec3 feet = entity.position();
        if (message.kind() == AwakenFxS2C.Kind.AWAKEN) {
            Anchor ground = Anchor.entity(entity.getId(), new Vec3(0.0, 0.06, 0.0));
            FxWorld.add(new FxWorld.Sigil(ground, 2.6F, FxTextures.SIGIL_STAR, color, 60, 0.07F, 8, 0.95F));
            FxWorld.add(new FxWorld.Sigil(Anchor.entity(entity.getId(), new Vec3(0.0, 0.1, 0.0)), 1.5F, FxTextures.SIGIL_ARCANE, light, 54,
                    -0.12F, 6, 0.85F));
            FxWorld.add(new FxWorld.Pillar(Anchor.at(feet), 1.1F, 9.0F, light, 34));
            FxWorld.add(new FxWorld.Gather(Anchor.entity(entity.getId(), new Vec3(0.0, 1.0, 0.0)), 3.2F, color, 24, 18));
            FxWorld.later(18, () -> {
                FxWorld.add(new FxWorld.Shockwave(feet, 0.4F, 4.5F, 18, color, 0.6F, 0.8F));
                FxWorld.add(new FxWorld.Flare(Anchor.entity(entity.getId(), new Vec3(0.0, 1.1, 0.0)), 2.4F, light, 18, FxTextures.GLOW,
                        0.0F, 3));
            });
            return;
        }
        Vec3 center = entity.getBoundingBox().getCenter();
        float size = (float) Math.max(1.2, entity.getBbWidth() * 1.6);
        FxWorld.add(new FxWorld.Shockwave(new Vec3(center.x, entity.getY() + 0.1, center.z), 0.3F, size + 1.6F, 12, color, 0.55F, 0.7F));
        FxWorld.add(new FxWorld.Flare(Anchor.at(center), size, light, 10, FxTextures.GLOW, 0.0F, 2));
        FxWorld.add(new FxWorld.Flare(Anchor.at(center), size * 0.6F, 0xFFFFFF, 6, FxTextures.STAR, 0.4F, 1));
    }

    private static int lighter(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = r + (255 - r) * 3 / 5;
        g = g + (255 - g) * 3 / 5;
        b = b + (255 - b) * 3 / 5;
        return (r << 16) | (g << 8) | b;
    }
}

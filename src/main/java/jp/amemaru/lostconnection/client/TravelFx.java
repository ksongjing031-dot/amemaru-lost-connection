package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.fx.FxTextures;
import jp.amemaru.lostconnection.client.fx.FxWorld;
import jp.amemaru.lostconnection.client.fx.FxWorld.Anchor;
import jp.amemaru.lostconnection.network.s2c.TravelFxS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * 塔を進む・戻るときの魔法陣（11月27日【補完】。{@link TravelFxS2C}）。
 *
 * <ul>
 *   <li>発つ所：足もとに青い魔法陣（星灯の紋）が回り、上へ昇る光の柱と、まわりから集まる光の筋</li>
 *   <li>着く所：金色の魔法陣が広がって回り、足もとから光の輪が広がる。胸の高さに光</li>
 * </ul>
 */
public final class TravelFx {
    private static final int DEPART = 0x8FD3FF;
    private static final int DEPART_LIGHT = 0xD8F2FF;
    private static final int ARRIVE = 0xFFD27A;
    private static final int ARRIVE_LIGHT = 0xFFF6E0;

    private TravelFx() {
    }

    public static void play(TravelFxS2C message) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Vec3 at = message.at();
        Anchor ground = Anchor.at(at.add(0.0, 0.06, 0.0));
        if (message.arrive()) {
            FxWorld.add(new FxWorld.Sigil(ground, 2.0F, FxTextures.SIGIL_STAR, ARRIVE, 36, -0.06F, 5, 0.95F));
            FxWorld.add(new FxWorld.Sigil(Anchor.at(at.add(0.0, 0.1, 0.0)), 1.2F, FxTextures.SIGIL_ARCANE, ARRIVE_LIGHT, 30, 0.1F, 3,
                    0.8F));
            FxWorld.add(new FxWorld.Shockwave(at, 0.3F, 3.0F, 16, ARRIVE, 0.5F, 0.7F));
            FxWorld.add(new FxWorld.Flare(Anchor.at(at.add(0.0, 1.1, 0.0)), 1.8F, ARRIVE_LIGHT, 14, FxTextures.GLOW, 0.0F, 2));
        } else {
            FxWorld.add(new FxWorld.Sigil(ground, 1.8F, FxTextures.SIGIL_ARCANE, DEPART, 30, 0.08F, 6, 0.95F));
            FxWorld.add(new FxWorld.Pillar(Anchor.at(at), 0.9F, 7.0F, DEPART_LIGHT, 22));
            FxWorld.add(new FxWorld.Gather(Anchor.at(at.add(0.0, 1.0, 0.0)), 2.4F, DEPART, 16, 12));
        }
    }
}

package jp.amemaru.lostconnection.client.render;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.boss.Mune;
import jp.amemaru.lostconnection.enemy.lake.SilentFish;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.core.object.Color;

/**
 * 8階の、氷の下を泳ぐもの（無音の大魚・湖主ムーネ）の見た目（11月25日）。氷の下にいる間は、氷ごしに見える暗い影として描き
 * （半透明の濃い紺）、氷の上へ出ると、ふつうの色で描く（Phase 0-A 9章の8階「氷の下に巨大な影」）。
 */
public class LakeShadowRenderer<T extends AmemaruMob> extends AmemaruMobRenderer<T> {
    /** 半透明の濃い紺（ARGB）。 */
    private static final Color SHADOW = new Color((190 << 24) | (34 << 16) | (44 << 8) | 64);

    public LakeShadowRenderer(EntityRendererProvider.Context context, String name, float shadow) {
        super(context, name, shadow, true);
    }

    public static LakeShadowRenderer<SilentFish> fish(EntityRendererProvider.Context context) {
        return new LakeShadowRenderer<>(context, "silent_fish", 0.0F);
    }

    public static LakeShadowRenderer<Mune> mune(EntityRendererProvider.Context context) {
        return new LakeShadowRenderer<>(context, "mune", 0.0F);
    }

    private static boolean under(AmemaruMob mob) {
        if (mob instanceof SilentFish fish) {
            return fish.submerged();
        }
        return mob instanceof Mune mune && mune.submerged();
    }

    @Override
    public Color getRenderColor(T animatable, float partialTick, int packedLight) {
        return under(animatable) ? SHADOW : super.getRenderColor(animatable, partialTick, packedLight);
    }
}

package jp.amemaru.lostconnection.client.render;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLibのモデルのファイルの置き場を、Phase 0-B 12章の表のとおりに決める。
 *
 * <ul>
 *   <li>モデル：{@code assets/amemaru/geo/<種類>/<名前>.geo.json}</li>
 *   <li>絵：{@code assets/amemaru/textures/<種類>/<名前>.png}（光る部分は {@code <名前>_glowmask.png}）</li>
 *   <li>アニメーション：{@code assets/amemaru/animations/<種類>/<名前>.animation.json}</li>
 * </ul>
 *
 * <p>種類は item（動く武器）・armor（防具）・accessory（体に表示するアクセサリー）・entity（敵）。</p>
 */
public class AssetGeoModel<T extends GeoAnimatable> extends GeoModel<T> {
    private final ResourceLocation model;
    private final ResourceLocation texture;
    private final ResourceLocation animation;

    public AssetGeoModel(String kind, String name) {
        this.model = AmemaruMod.id("geo/" + kind + "/" + name + ".geo.json");
        this.texture = AmemaruMod.id("textures/" + kind + "/" + name + ".png");
        this.animation = AmemaruMod.id("animations/" + kind + "/" + name + ".animation.json");
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return model;
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return texture;
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return animation;
    }

    /** 敵が決める、見せる・隠すボーン（Phase 8 その2：3階のボスの水の外套。{@link jp.amemaru.lostconnection.enemy.AmemaruMob#boneToggles}）。 */
    @Override
    public void setCustomAnimations(T animatable, long instanceId, software.bernie.geckolib.core.animation.AnimationState<T> animationState) {
        if (animatable instanceof jp.amemaru.lostconnection.enemy.AmemaruMob mob) {
            for (java.util.Map.Entry<String, Boolean> entry : mob.boneToggles().entrySet()) {
                software.bernie.geckolib.core.animatable.model.CoreGeoBone bone = getAnimationProcessor().getBone(entry.getKey());
                if (bone != null) {
                    bone.setHidden(!entry.getValue());
                }
            }
        }
    }
}

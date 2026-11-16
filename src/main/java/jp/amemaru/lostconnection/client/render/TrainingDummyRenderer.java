package jp.amemaru.lostconnection.client.render;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.entity.TrainingDummy;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 訓練人形の見た目。人の形のモデル（ゾンビと同じ骨組み）に、藁と結晶の人形の絵を貼る【仮素材】。
 * 絵は {@code textures/entity/training_dummy.png}（64×64、プレイヤーのスキンと同じ並び）。
 */
public class TrainingDummyRenderer extends HumanoidMobRenderer<TrainingDummy, HumanoidModel<TrainingDummy>> {
    private static final ResourceLocation TEXTURE = AmemaruMod.id("textures/entity/training_dummy.png");

    public TrainingDummyRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(TrainingDummy entity) {
        return TEXTURE;
    }
}

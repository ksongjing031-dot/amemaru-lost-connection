package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import jp.amemaru.lostconnection.enemy.village.StatueEcho;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * 氷像の残響（6階）を、凍った村人の氷像と同じ形に描く（11月25日）。眠っている間は像の格好（{@link VillageStatueRenderer} の格好の
 * どれか。ID で決まる）のまま、目覚めると歩き・腕を振る（プレイヤーと同じ形のモデルの動き）。見た目の絵は、氷の色で顔の白く抜けた
 * 村人（{@code textures/entity/village/<名前>_statue.png}）。
 */
public class StatueEchoRenderer extends LivingEntityRenderer<StatueEcho, PlayerModel<StatueEcho>> {
    private static final String[] DORMANT_POSES = {"talk", "reach", "carry", "wave", "point", "listen", "shovel"};
    private final PlayerModel<StatueEcho> wide;
    private final PlayerModel<StatueEcho> slim;

    public StatueEchoRenderer(EntityRendererProvider.Context context) {
        super(context, new EchoModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wide = getModel();
        this.slim = new EchoModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public ResourceLocation getTextureLocation(StatueEcho entity) {
        return VillageStatueRenderer.texture(entity.skin());
    }

    @Override
    public void render(StatueEcho entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        String skin = entity.skin();
        model = skin.equals("woman") || skin.equals("buyer") || skin.equals("child_girl") ? slim : wide;
        poseStack.pushPose();
        if (skin.startsWith("child")) {
            poseStack.scale(0.8F, 0.8F, 0.8F);
        }
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
        poseStack.popPose();
    }

    @Override
    protected void scale(StatueEcho entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Nullable
    @Override
    protected RenderType getRenderType(StatueEcho entity, boolean visible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(getTextureLocation(entity));
    }

    @Override
    protected boolean shouldShowName(StatueEcho entity) {
        return false;
    }

    /** 眠っている間は、像の格好で止める。 */
    private static final class EchoModel extends PlayerModel<StatueEcho> {
        EchoModel(ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void setupAnim(StatueEcho entity, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(entity, limbSwing, limbAmount, age, headYaw, headPitch);
            if (!entity.dormant()) {
                return;
            }
            VillageStatueRenderer.Pose pose = VillageStatueRenderer.pose(
                    DORMANT_POSES[Math.floorMod(entity.getUUID().hashCode() >> 3, DORMANT_POSES.length)]);
            set(head, pose.head());
            set(hat, pose.head());
            set(body, pose.body());
            set(jacket, pose.body());
            set(rightArm, pose.rightArm());
            set(rightSleeve, pose.rightArm());
            set(leftArm, pose.leftArm());
            set(leftSleeve, pose.leftArm());
            set(rightLeg, pose.rightLeg());
            set(rightPants, pose.rightLeg());
            set(leftLeg, pose.leftLeg());
            set(leftPants, pose.leftLeg());
        }

        private static void set(ModelPart part, float[] deg) {
            part.xRot = (float) Math.toRadians(deg[0]);
            part.yRot = (float) Math.toRadians(deg[1]);
            part.zRot = (float) Math.toRadians(deg[2]);
        }
    }
}

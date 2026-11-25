package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.tower.village.VillageBlocks;
import jp.amemaru.lostconnection.tower.village.VillageStatueBlockEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 6階の凍った村人の氷像を、人の形に描く（11月25日）。プレイヤーと同じ形のモデルに、村人の見た目（{@code textures/entity/village/
 * <名前>_statue.png}。顔は白く抜けている＝ロストの侵食）を貼り、格好（{@link Pose}）のとおりに手足を曲げ、氷の色で半分すけて描く。
 * 溶けた氷像（{@code thawed}）は描かない。
 */
public class VillageStatueRenderer implements BlockEntityRenderer<VillageStatueBlockEntity> {
    /** 体の細い見た目（腕が3ピクセル）。 */
    private static final Set<String> SLIM = Set.of("woman", "mother", "buyer", "child_girl");
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    private final PlayerModel<LivingEntity> wide;
    private final PlayerModel<LivingEntity> slim;

    /** 格好（角度は度。x・y・z）。{@code sit}：腰かけている（脚を前へ、体を下げる）。 */
    public record Pose(float[] head, float[] body, float[] rightArm, float[] leftArm, float[] rightLeg, float[] leftLeg, boolean sit) {
    }

    private static float[] r(float x, float y, float z) {
        return new float[]{x, y, z};
    }

    private static final float[] Z = {0, 0, 0};
    private static final Map<String, Pose> POSES = new HashMap<>();

    static {
        POSES.put("stand", new Pose(Z, Z, r(0, 0, 4), r(0, 0, -4), Z, Z, false));
        // 市場の値切り：右手で品を差し出し、左の手のひらを上げて「まけて」
        POSES.put("haggle", new Pose(r(-5, -15, 0), r(0, -10, 0), r(-45, 10, 0), r(-75, 0, -25), r(-8, 0, 0), r(8, 0, 0), false));
        // 母と子：母は振り向きかけている
        POSES.put("turn", new Pose(r(5, 55, 0), r(0, 30, 0), r(10, 0, 4), r(-25, 0, -8), r(0, 0, 0), r(-10, 0, 0), false));
        // 手を伸ばす（母の手を取ろうとする子ども）
        POSES.put("reach", new Pose(r(-15, 0, 0), r(8, 0, 0), r(-80, -5, 0), r(10, 0, -4), r(-15, 0, 0), r(20, 0, 0), false));
        // 鐘の綱を引く若者
        POSES.put("pull", new Pose(r(-25, 0, 0), Z, r(-170, 0, 8), r(-165, 0, -8), r(-6, 0, 0), r(6, 0, 0), false));
        // 村長の呼びかけ（右手を高く、左手で杖）
        POSES.put("call", new Pose(r(-10, 10, 0), Z, r(-130, 0, 15), r(-25, 0, -6), r(-5, 0, 0), r(5, 0, 0), false));
        POSES.put("point", new Pose(r(0, 10, 0), Z, r(-90, 10, 0), r(5, 0, -4), r(-5, 0, 0), r(5, 0, 0), false));
        POSES.put("carry", new Pose(r(10, 0, 0), r(5, 0, 0), r(-50, 15, 0), r(-50, -15, 0), r(-12, 0, 0), r(12, 0, 0), false));
        POSES.put("talk", new Pose(r(0, 20, 0), Z, r(-35, 20, 0), r(5, 0, -4), Z, Z, false));
        POSES.put("wave", new Pose(r(-5, 0, 0), Z, r(-160, 0, 20), r(0, 0, -4), Z, Z, false));
        POSES.put("shovel", new Pose(r(15, 0, 0), r(18, 0, 0), r(-35, 20, 0), r(-30, -20, 0), r(-25, 0, 0), r(10, 0, 0), false));
        POSES.put("snowball", new Pose(r(-5, -10, 0), r(0, -15, 0), r(-150, 0, 15), r(-45, 0, -10), r(-20, 0, 0), r(20, 0, 0), false));
        POSES.put("sweep", new Pose(r(20, 0, 0), r(10, 20, 0), r(-30, 30, 0), r(-35, 25, 0), r(-10, 0, 0), r(10, 0, 0), false));
        POSES.put("reach_up", new Pose(r(-30, 0, 0), Z, r(-165, 0, 10), r(-160, 0, -10), Z, Z, false));
        POSES.put("listen", new Pose(r(12, 0, 0), Z, r(0, 0, 3), r(0, 0, -3), Z, Z, false));
        POSES.put("pray", new Pose(r(25, 0, 0), r(5, 0, 0), r(-60, -25, 0), r(-60, 25, 0), Z, Z, false));
        POSES.put("sit_write", new Pose(r(25, 0, 0), r(10, 0, 0), r(-50, 0, 0), r(-40, 10, 0), r(-90, 8, 0), r(-90, -8, 0), true));
        POSES.put("fish", new Pose(r(20, 0, 0), r(5, 0, 0), r(-60, 5, 0), r(-55, -5, 0), r(-90, 10, 0), r(-90, -10, 0), true));
    }

    public VillageStatueRenderer(BlockEntityRendererProvider.Context context) {
        this.wide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    public static ResourceLocation texture(String skin) {
        return TEXTURES.computeIfAbsent(skin, s -> AmemaruMod.id("textures/entity/village/" + s + "_statue.png"));
    }

    public static Pose pose(String name) {
        Pose pose = POSES.get(name);
        return pose == null ? POSES.get("stand") : pose;
    }

    @Override
    public void render(VillageStatueBlockEntity statue, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (statue.getBlockState().hasProperty(VillageBlocks.THAWED) && statue.getBlockState().getValue(VillageBlocks.THAWED)) {
            return;
        }
        PlayerModel<LivingEntity> model = SLIM.contains(statue.skin()) ? slim : wide;
        Pose pose = pose(statue.pose());
        reset(model);
        apply(model.head, pose.head());
        apply(model.hat, pose.head());
        apply(model.body, pose.body());
        apply(model.jacket, pose.body());
        apply(model.rightArm, pose.rightArm());
        apply(model.rightSleeve, pose.rightArm());
        apply(model.leftArm, pose.leftArm());
        apply(model.leftSleeve, pose.leftArm());
        apply(model.rightLeg, pose.rightLeg());
        apply(model.rightPants, pose.rightLeg());
        apply(model.leftLeg, pose.leftLeg());
        apply(model.leftPants, pose.leftLeg());
        poseStack.pushPose();
        poseStack.translate(0.5, pose.sit() ? -0.55 : 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - statue.yaw()));
        float s = statue.scale() * 0.9375F;
        poseStack.scale(-s, -s, s);
        poseStack.translate(0.0, -1.501, 0.0);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(texture(statue.skin())));
        // 氷の色（少し青く、少しすける）。記憶の氷像は、ほんの少し明るい
        float glow = statue.isMemory() ? 1.0F : 0.9F;
        int lit = net.minecraft.client.renderer.LightTexture.pack(Math.max(net.minecraft.client.renderer.LightTexture.block(light), 6),
                net.minecraft.client.renderer.LightTexture.sky(light));
        model.renderToBuffer(poseStack, vc, lit, OverlayTexture.NO_OVERLAY, 0.78F * glow, 0.90F * glow, 1.0F,
                statue.isMemory() ? 0.9F : 0.82F);
        poseStack.popPose();
    }

    private static void reset(PlayerModel<LivingEntity> model) {
        model.young = false;
        model.crouching = false;
        model.riding = false;
        for (ModelPart part : new ModelPart[]{model.head, model.hat, model.body, model.jacket, model.rightArm, model.rightSleeve, model.leftArm,
                model.leftSleeve, model.rightLeg, model.rightPants, model.leftLeg, model.leftPants}) {
            part.xRot = 0.0F;
            part.yRot = 0.0F;
            part.zRot = 0.0F;
            part.visible = true;
        }
    }

    private static void apply(ModelPart part, float[] deg) {
        part.xRot = (float) Math.toRadians(deg[0]);
        part.yRot = (float) Math.toRadians(deg[1]);
        part.zRot = (float) Math.toRadians(deg[2]);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}

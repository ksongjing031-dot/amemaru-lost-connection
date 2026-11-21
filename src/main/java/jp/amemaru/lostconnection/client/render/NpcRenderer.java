package jp.amemaru.lostconnection.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.npc.NpcDef;
import jp.amemaru.lostconnection.npc.NpcDefs;
import jp.amemaru.lostconnection.npc.NpcEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * NPCの見た目。定義に立体のモデル（{@code model}）があれば GeckoLib のモデルで描く（11月22日のご指示「キャラクターに凹凸をつけて
 * 立体的にする」。{@link NpcGeoModel}）。なければ、プレイヤーと同じ形のスキン（Phase 0-B 12章。腕の細いスキンと太いスキン）。
 * 頭の上に、名前と肩書き（「冒険者ギルドの受付」など）を出す。表情（{@link NpcEntity#expression()}）で顔の絵を差し替える。
 */
public class NpcRenderer extends EntityRenderer<NpcEntity> {
    private static final ResourceLocation FALLBACK = AmemaruMod.id("textures/entity/npc/lilia.png");

    private final Inner wide;
    private final Inner slim;
    private final Geo geo;

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context);
        wide = new Inner(context, false);
        slim = new Inner(context, true);
        geo = new Geo(context);
        shadowRadius = 0.5F;
    }

    private Inner inner(NpcEntity entity) {
        NpcDef def = NpcDefs.client(entity.npcId());
        return def != null && def.slim() ? slim : wide;
    }

    @Override
    public void render(NpcEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        NpcDef def = NpcDefs.client(entity.npcId());
        if (def != null && def.model() != null) {
            float s = def.modelScale();
            poseStack.pushPose();
            poseStack.scale(s, s, s);
            geo.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
            poseStack.popPose();
            return;
        }
        inner(entity).render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }

    /** 名前を出すか（役者と、遠いNPCには出さない）。 */
    static boolean showName(NpcEntity entity) {
        return entity.getId() >= 0 && NpcDefs.client(entity.npcId()) != null
                && entity.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()) < 20 * 20;
    }

    @Override
    public ResourceLocation getTextureLocation(NpcEntity entity) {
        return texture(entity);
    }

    static ResourceLocation texture(NpcEntity entity) {
        NpcDef def = NpcDefs.client(entity.npcId());
        if (def == null) {
            return FALLBACK;
        }
        return def.skinFor(entity.expression());
    }

    /** 立体のモデルの描き手（GeckoLib）。名前と肩書きは、スキンのときと同じに出す。 */
    private static final class Geo extends software.bernie.geckolib.renderer.GeoEntityRenderer<NpcEntity> {
        Geo(EntityRendererProvider.Context context) {
            super(context, new NpcGeoModel());
            shadowRadius = 0.5F;
        }

        @Override
        public boolean shouldShowName(NpcEntity entity) {
            return showName(entity);
        }

        @Override
        protected void renderNameTag(NpcEntity entity, Component name, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
            NpcDef def = NpcDefs.client(entity.npcId());
            if (def == null) {
                return;
            }
            // モデルは大きさを変えて描いているので、名前は元の大きさに戻してから出す
            float s = 1.0F / Math.max(0.25F, def.modelScale());
            poseStack.pushPose();
            poseStack.scale(s, s, s);
            super.renderNameTag(entity, Component.literal(def.name()).withStyle(ChatFormatting.WHITE), poseStack, buffer, packedLight);
            if (!def.title().isEmpty()) {
                poseStack.translate(0.0, 0.26, 0.0);
                super.renderNameTag(entity, Component.literal(def.title()).withStyle(ChatFormatting.AQUA), poseStack, buffer, packedLight);
            }
            poseStack.popPose();
        }
    }

    /** 形（腕の太さ）ごとの描き手。 */
    private static final class Inner extends HumanoidMobRenderer<NpcEntity, PlayerModel<NpcEntity>> {
        Inner(EntityRendererProvider.Context context, boolean slim) {
            super(context, new PlayerModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), 0.5F);
        }

        @Override
        public ResourceLocation getTextureLocation(NpcEntity entity) {
            return texture(entity);
        }

        @Override
        protected boolean shouldShowName(NpcEntity entity) {
            // 役者（クライアントだけのNPC）と、遠いNPCには名前を出さない
            return showName(entity);
        }

        @Override
        protected void renderNameTag(NpcEntity entity, Component name, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
            NpcDef def = NpcDefs.client(entity.npcId());
            if (def == null) {
                return;
            }
            super.renderNameTag(entity, Component.literal(def.name()).withStyle(ChatFormatting.WHITE), poseStack, buffer, packedLight);
            if (!def.title().isEmpty()) {
                poseStack.pushPose();
                poseStack.translate(0.0, 0.26, 0.0);
                super.renderNameTag(entity, Component.literal(def.title()).withStyle(ChatFormatting.AQUA), poseStack, buffer, packedLight);
                poseStack.popPose();
            }
        }
    }
}

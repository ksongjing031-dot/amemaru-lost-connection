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
 * NPCの見た目（Phase 0-B 12章「NPC：プレイヤーと同じ形のスキン」）。腕の細いスキンと太いスキンの2つの形を持ち、NPCの定義で選ぶ。
 * 頭の上に、名前と肩書き（「冒険者ギルドの受付」など）を出す。表情（{@link NpcEntity#expression()}）で顔の絵を差し替える。
 */
public class NpcRenderer extends EntityRenderer<NpcEntity> {
    private static final ResourceLocation FALLBACK = AmemaruMod.id("textures/entity/npc/lilia.png");

    private final Inner wide;
    private final Inner slim;

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context);
        wide = new Inner(context, false);
        slim = new Inner(context, true);
        shadowRadius = 0.5F;
    }

    private Inner inner(NpcEntity entity) {
        NpcDef def = NpcDefs.client(entity.npcId());
        return def != null && def.slim() ? slim : wide;
    }

    @Override
    public void render(NpcEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        inner(entity).render(entity, yaw, partialTick, poseStack, buffer, packedLight);
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
            return entity.getId() >= 0 && NpcDefs.client(entity.npcId()) != null
                    && entity.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()) < 20 * 20;
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

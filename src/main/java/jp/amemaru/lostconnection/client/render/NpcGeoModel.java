package jp.amemaru.lostconnection.client.render;

import jp.amemaru.lostconnection.npc.NpcDef;
import jp.amemaru.lostconnection.npc.NpcDefs;
import jp.amemaru.lostconnection.npc.NpcEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * 街のNPCの立体のモデル（11月22日）。NPCの定義の {@code model}（{@code amemaru:npc/lilia} など）から、
 * {@code geo/entity/npc/lilia.geo.json}・{@code animations/entity/npc/lilia.animation.json} を選ぶ。絵は表情の差分
 * （{@link NpcDef#skinFor}）。頭は、見ている人の方を向く（首のかしげはアニメーションのまま）。
 */
public class NpcGeoModel extends GeoModel<NpcEntity> {
    private static final ResourceLocation FALLBACK = jp.amemaru.lostconnection.AmemaruMod.id("npc/lilia");

    private static ResourceLocation model(NpcEntity entity) {
        NpcDef def = NpcDefs.client(entity.npcId());
        return def != null && def.model() != null ? def.model() : FALLBACK;
    }

    @Override
    public ResourceLocation getModelResource(NpcEntity entity) {
        ResourceLocation m = model(entity);
        return ResourceLocation.fromNamespaceAndPath(m.getNamespace(), "geo/entity/" + m.getPath() + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(NpcEntity entity) {
        return NpcRenderer.texture(entity);
    }

    @Override
    public ResourceLocation getAnimationResource(NpcEntity entity) {
        ResourceLocation m = model(entity);
        return ResourceLocation.fromNamespaceAndPath(m.getNamespace(), "animations/entity/" + m.getPath() + ".animation.json");
    }

    @Override
    public void setCustomAnimations(NpcEntity animatable, long instanceId, AnimationState<NpcEntity> animationState) {
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head != null && data != null) {
            head.setRotX(data.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(data.netHeadYaw() * Mth.DEG_TO_RAD);
        }
    }
}

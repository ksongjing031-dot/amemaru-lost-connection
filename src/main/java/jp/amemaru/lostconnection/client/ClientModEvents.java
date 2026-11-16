package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.client.render.LampBoltRenderer;
import jp.amemaru.lostconnection.client.render.TrainingDummyRenderer;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.core.registry.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * クライアントの登録（Modのイベントバス）：キー・HUD・エンティティの見た目・盾の構え・プレイヤーのモーション。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PlayerAnimations.init();
            // 見習いの盾を構えたときの見た目（バニラの盾と同じ「blocking」の切りかえ）
            ItemProperties.register(ModItems.APPRENTICE_SHIELD.get(), ResourceLocation.withDefaultNamespace("blocking"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
        });
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_STATUS);
        event.register(KeyBindings.OPEN_SKILL_TREE);
        for (KeyMapping key : KeyBindings.SKILLS) {
            event.register(key);
        }
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        // ホットバーのすぐ後に描く（ハートや空気の泡より先に描き、高さの計算を渡すため）
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "status", AmemaruHud.STATUS);
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "skills", SkillHud.OVERLAY);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TRAINING_DUMMY.get(), TrainingDummyRenderer::new);
        event.registerEntityRenderer(ModEntities.LAMP_BOLT.get(), LampBoltRenderer::new);
    }
}

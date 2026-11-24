package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.client.fx.FxParticle;
import jp.amemaru.lostconnection.accessory.AccessoryItem;
import jp.amemaru.lostconnection.client.render.AccessoryCurioRenderer;
import jp.amemaru.lostconnection.client.render.AmemaruMobRenderer;
import jp.amemaru.lostconnection.client.render.CrystalNeedleRenderer;
import jp.amemaru.lostconnection.client.render.LampBoltRenderer;
import jp.amemaru.lostconnection.client.render.LootItemRenderer;
import jp.amemaru.lostconnection.client.render.OptionalGlowLayer;
import jp.amemaru.lostconnection.client.screen.AccessoryScreen;
import jp.amemaru.lostconnection.client.screen.WorkshopScreen;
import jp.amemaru.lostconnection.equipment.GearItems;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.item.ItemManifest;
import jp.amemaru.lostconnection.workshop.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import jp.amemaru.lostconnection.client.render.TrainingDummyRenderer;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.core.registry.ModParticles;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * クライアントの登録（Modのイベントバス）：キー・HUD・エンティティの見た目・盾の構え・プレイヤーのモーション・演出用パーティクル。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PlayerAnimations.init();
            ClientStory.init();
            // このModの盾を構えたときの見た目（バニラの盾と同じ「blocking」の切りかえ）
            for (RegistryObject<Item> shield : ModItems.MANIFEST.category(ItemManifest.Category.SHIELD)) {
                ItemProperties.register(shield.get(), ResourceLocation.withDefaultNamespace("blocking"),
                        (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
            }
            // 装備のレアリティ（モデルの作り手が、レアリティごとに見た目を変えたいときに使う：0〜1の6段）
            for (ItemManifest.Category category : new ItemManifest.Category[]{ItemManifest.Category.WEAPON,
                    ItemManifest.Category.SHIELD, ItemManifest.Category.ARMOR}) {
                for (RegistryObject<Item> item : ModItems.MANIFEST.category(category)) {
                    ItemProperties.register(item.get(), AmemaruMod.id("rarity"), (stack, level, entity, seed) -> {
                        Rarity rarity = GearItems.rarity(stack);
                        return rarity == null ? 0.0F : rarity.ordinal() / 5.0F;
                    });
                }
            }
            // 画面
            MenuScreens.register(ModMenus.WORKSHOP.get(), WorkshopScreen::new);
            MenuScreens.register(ModMenus.ACCESSORY.get(), AccessoryScreen::new);
            MenuScreens.register(ModMenus.STORAGE.get(), jp.amemaru.lostconnection.client.screen.StorageScreen::new);
            // 体に表示するアクセサリー（Curios の描画）
            for (RegistryObject<Item> item : ModItems.MANIFEST.category(ItemManifest.Category.ACCESSORY)) {
                if (item.get() instanceof AccessoryItem accessory && accessory.visibleOnBody()) {
                    CuriosRendererRegistry.register(accessory, () -> new AccessoryCurioRenderer(accessory));
                }
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_STATUS);
        event.register(KeyBindings.OPEN_SKILL_TREE);
        event.register(KeyBindings.OPEN_PARTY);
        event.register(KeyBindings.RETIRE);
        event.register(KeyBindings.LOCK_ON);
        for (KeyMapping key : KeyBindings.SKILLS) {
            event.register(key);
        }
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        // ホットバーのすぐ後に描く（ハートや空気の泡より先に描き、高さの計算を渡すため）
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "status", AmemaruHud.STATUS);
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "skills", SkillHud.OVERLAY);
        // Phase 4：パーティのメンバー（左上）と、ダウン（自分が倒れた・仲間を起こしている）
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "party", PartyHud.OVERLAY);
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "down", DownHud.OVERLAY);
        // 11月24日：ロックした敵の名前・距離・HP（照準のすぐ下）
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "lock_on", TargetLock.OVERLAY);
        // Phase 3：レアドロップ・工房などの特別な演出の帯
        event.registerAboveAll("special_moment", SpecialMomentFx.OVERLAY);
        // Phase 5：暗転・章題・字幕・古代文字・知らせ・カウントダウン・はじめの手引き
        event.registerAboveAll("story", StoryHud.OVERLAY);
        // Phase 6 その3：受けているクエスト（右上。はじめの手引きの下。手引きの高さを使うので、そのあとに描く）
        event.registerAboveAll("guild_quests", GuildHud.OVERLAY);
        // Phase 6 その4：決闘の残り時間と二人のHP
        event.registerAboveAll("duel", DuelHud.OVERLAY);
        // Phase 7 その2：釣りの糸の張り（画面の真ん中の下）と、釣り大会（上の真ん中）
        event.registerAboveAll("fishing", ClientFishing.OVERLAY);
    }

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.GLOW.get(), sprites -> new FxParticle.Provider(sprites, FxParticle.Style.GLOW));
        event.registerSpriteSet(ModParticles.SPARK.get(), sprites -> new FxParticle.Provider(sprites, FxParticle.Style.SPARK));
        event.registerSpriteSet(ModParticles.SHARD.get(), sprites -> new FxParticle.Provider(sprites, FxParticle.Style.SHARD));
        event.registerSpriteSet(ModParticles.STAR.get(), sprites -> new FxParticle.Provider(sprites, FxParticle.Style.STAR));
        event.registerSpriteSet(ModParticles.RUNE.get(), sprites -> new FxParticle.Provider(sprites, FxParticle.Style.RUNE));
        event.registerSpriteSet(ModParticles.SNOW.get(), sprites -> new FxParticle.Provider(sprites, FxParticle.Style.SNOW));
        event.registerSpriteSet(ModParticles.FEATHER.get(), sprites -> new FxParticle.Provider(sprites, FxParticle.Style.FEATHER));
        event.registerSpriteSet(ModParticles.PETAL.get(), sprites -> new FxParticle.Provider(sprites, FxParticle.Style.PETAL));
        event.registerSpriteSet(ModParticles.SMOKE.get(), sprites -> new FxParticle.Provider(sprites, FxParticle.Style.SMOKE));
        event.registerSpriteSet(ModParticles.BUBBLE.get(), sprites -> new FxParticle.Provider(sprites, FxParticle.Style.BUBBLE));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TRAINING_DUMMY.get(), TrainingDummyRenderer::new);
        event.registerEntityRenderer(ModEntities.LAMP_BOLT.get(), LampBoltRenderer::new);
        // Phase 3：1階の敵・結晶蜂の針・ドロップ
        event.registerEntityRenderer(ModEntities.TSUNOMOFU.get(), context -> new AmemaruMobRenderer<>(context, "tsunomofu", 0.5F));
        event.registerEntityRenderer(ModEntities.ISHIKURE_SOLDIER.get(),
                context -> new AmemaruMobRenderer<>(context, "ishikure_soldier", 0.6F));
        event.registerEntityRenderer(ModEntities.CRYSTAL_BEE.get(), context -> new AmemaruMobRenderer<>(context, "crystal_bee", 0.3F));
        event.registerEntityRenderer(ModEntities.KAKEKIBA_WOLF.get(), context -> new AmemaruMobRenderer<>(context, "kakekiba_wolf", 0.5F));
        event.registerEntityRenderer(ModEntities.CRYSTAL_NEEDLE.get(), CrystalNeedleRenderer::new);
        event.registerEntityRenderer(ModEntities.LOOT_ITEM.get(), LootItemRenderer::new);
        // Phase 5：NPC・1階のボス・塔の仕掛け
        event.registerEntityRenderer(ModEntities.NPC.get(), jp.amemaru.lostconnection.client.render.NpcRenderer::new);
        event.registerEntityRenderer(ModEntities.VELDHORN.get(), context -> new AmemaruMobRenderer<>(context, "veldhorn", 1.6F));
        // Phase 8 その1：2階の敵
        event.registerEntityRenderer(ModEntities.MAYOI_BI.get(), context -> new AmemaruMobRenderer<>(context, "mayoi_bi", 0.2F));
        event.registerEntityRenderer(ModEntities.JUHIHEI.get(), context -> new AmemaruMobRenderer<>(context, "juhihei", 0.7F));
        event.registerEntityRenderer(ModEntities.POKODAKE.get(), context -> new AmemaruMobRenderer<>(context, "pokodake", 0.5F));
        event.registerEntityRenderer(ModEntities.HAKAGE_KITSUNE.get(), context -> new AmemaruMobRenderer<>(context, "hakage_kitsune", 0.4F));
        event.registerEntityRenderer(ModEntities.OLD_WARD.get(), context -> new AmemaruMobRenderer<>(context, "old_ward", 1.8F));
        event.registerEntityRenderer(ModEntities.OLD_WARD_KNOT.get(), context -> new AmemaruMobRenderer<>(context, "old_ward_knot", 0.0F));
        // Phase 8 その2：3階の敵
        event.registerEntityRenderer(ModEntities.SUIMON_NEZUMI.get(), context -> new AmemaruMobRenderer<>(context, "suimon_nezumi", 0.35F));
        // 11月24日：4階の敵とボス
        event.registerEntityRenderer(ModEntities.SILENT_STATUE.get(), context -> new AmemaruMobRenderer<>(context, "silent_statue", 0.6F));
        event.registerEntityRenderer(ModEntities.PRAYER_ECHO.get(), context -> new AmemaruMobRenderer<>(context, "prayer_echo", 0.0F, true));
        event.registerEntityRenderer(ModEntities.CRYSTAL_BEETLE.get(), context -> new AmemaruMobRenderer<>(context, "crystal_beetle", 0.6F));
        event.registerEntityRenderer(ModEntities.BROKEN_BEAST.get(), context -> new AmemaruMobRenderer<>(context, "broken_beast", 0.6F));
        event.registerEntityRenderer(ModEntities.CANTARE.get(), context -> new AmemaruMobRenderer<>(context, "cantare", 0.0F));
        // 11月24日：5階の敵とボス
        event.registerEntityRenderer(ModEntities.WATCHER.get(), context -> new AmemaruMobRenderer<>(context, "watcher", 0.0F));
        event.registerEntityRenderer(ModEntities.SENTINEL.get(), context -> new AmemaruMobRenderer<>(context, "sentinel", 0.6F));
        event.registerEntityRenderer(ModEntities.REPAIR_DRONE.get(), context -> new AmemaruMobRenderer<>(context, "repair_drone", 0.0F));
        event.registerEntityRenderer(ModEntities.ANCIENT_GUARDIAN.get(), context -> new AmemaruMobRenderer<>(context, "ancient_guardian", 1.4F));
        event.registerEntityRenderer(ModEntities.YODOMI_SLIME.get(), context -> new AmemaruMobRenderer<>(context, "yodomi_slime", 0.6F));
        event.registerEntityRenderer(ModEntities.SOUJIKIHEI.get(), context -> new AmemaruMobRenderer<>(context, "soujikihei", 0.7F));
        event.registerEntityRenderer(ModEntities.KIBA_NAMAZU.get(), context -> new AmemaruMobRenderer<>(context, "kiba_namazu", 0.6F));
        event.registerEntityRenderer(ModEntities.HYDRAM.get(), context -> new AmemaruMobRenderer<>(context, "hydram", 2.0F));
        event.registerEntityRenderer(ModEntities.GROUND_MARKER.get(), jp.amemaru.lostconnection.client.render.GroundMarkerRenderer::new);
        event.registerBlockEntityRenderer(jp.amemaru.lostconnection.core.registry.ModBlockEntities.TOWER_PILLAR.get(),
                jp.amemaru.lostconnection.client.render.TowerPillarRenderer::new);
        event.registerBlockEntityRenderer(jp.amemaru.lostconnection.core.registry.ModBlockEntities.FLOOR_SEAL.get(),
                jp.amemaru.lostconnection.client.render.FloorSealRenderer::new);
    }

    /**
     * 11月23日に決定：両手で使う武器（籠手・二刀の短剣）を、空いているもう片方の手にも描く層を、プレイヤーの見た目
     * （ふつうの腕・細い腕）に足す。
     */
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            net.minecraft.client.renderer.entity.player.PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new jp.amemaru.lostconnection.client.render.TwinHandLayer(renderer,
                        event.getContext().getItemInHandRenderer()));
            }
        }
    }

    /** 持ち物のアイコンの飾り（★5のアクセサリーのきらめき・強化+7と+10の星）。 */
    /** リソースパックを読み直したら、光る部分の絵があるかを確かめ直す。 */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> OptionalGlowLayer.clearCache());
    }

    @SubscribeEvent
    public static void onRegisterDecorations(RegisterItemDecorationsEvent event) {
        for (RegistryObject<Item> item : ModItems.MANIFEST.category(ItemManifest.Category.ACCESSORY)) {
            event.register(item.get(), GearDecorators.ACCESSORY);
        }
        for (ItemManifest.Category category : new ItemManifest.Category[]{ItemManifest.Category.WEAPON,
                ItemManifest.Category.SHIELD, ItemManifest.Category.ARMOR}) {
            for (RegistryObject<Item> item : ModItems.MANIFEST.category(category)) {
                event.register(item.get(), GearDecorators.GEAR);
            }
        }
    }
}

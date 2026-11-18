package jp.amemaru.lostconnection;

import com.mojang.logging.LogUtils;
import jp.amemaru.lostconnection.core.config.ClientConfig;
import jp.amemaru.lostconnection.core.registry.ModBlocks;
import jp.amemaru.lostconnection.core.registry.ModCreativeTabs;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.core.registry.ModParticles;
import jp.amemaru.lostconnection.core.registry.ModSounds;
import jp.amemaru.lostconnection.loot.ModLootFunctions;
import jp.amemaru.lostconnection.workshop.ModMenus;
import jp.amemaru.lostconnection.workshop.ModRecipes;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.network.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Modの入り口。
 *
 * <p>設定ファイルと通信の登録だけをここで行う。イベントの受け取りは、各パッケージの
 * {@code @Mod.EventBusSubscriber} が付いたクラスが、Forgeによって自動で登録される。</p>
 */
@Mod(AmemaruMod.MOD_ID)
public final class AmemaruMod {
    public static final String MOD_ID = "amemaru";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Forge 47.4系では、読み込みの情報（context）をコンストラクタで受け取る。 */
    public AmemaruMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        // サーバーの設定はワールドごと（serverconfig/amemaru-server.toml）
        context.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, MOD_ID + "-server.toml");
        // 見た目の設定はプレイヤーごと（config/amemaru-client.toml）
        context.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, MOD_ID + "-client.toml");

        modBus.addListener(this::onCommonSetup);

        // 登録（ブロック・アイテム・エンティティ・状態・音・パーティクル・クリエイティブのタブ）
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        // Phase 3：ドロップ表の関数・工房のレシピ・画面の中身
        ModLootFunctions.FUNCTIONS.register(modBus);
        ModRecipes.TYPES.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        ModMenus.MENUS.register(modBus);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    /** {@code amemaru:<path>} の名前を作る。 */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}

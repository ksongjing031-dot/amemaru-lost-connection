package jp.amemaru.lostconnection;

import com.mojang.logging.LogUtils;
import jp.amemaru.lostconnection.core.config.ClientConfig;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.network.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
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

    public AmemaruMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext context = ModLoadingContext.get();
        // サーバーの設定はワールドごと（serverconfig/amemaru-server.toml）
        context.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, MOD_ID + "-server.toml");
        // 見た目の設定はプレイヤーごと（config/amemaru-client.toml）
        context.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, MOD_ID + "-client.toml");

        modBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    /** {@code amemaru:<path>} の名前を作る。 */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}

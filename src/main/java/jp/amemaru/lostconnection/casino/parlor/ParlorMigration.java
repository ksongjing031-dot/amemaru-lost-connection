package jp.amemaru.lostconnection.casino.parlor;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.admin.CityCommands;
import jp.amemaru.lostconnection.story.StoryState;
import jp.amemaru.lostconnection.town.TownData;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 前の版のワールドを、11月27日のカジノの作り直しに合わせる（サーバーを起動した最初の tick に1回）。
 *
 * <ul>
 *   <li>共同事業⑥「魚レース」→「カジノの新台入替」：寄付と完成の記録・物語の印・街に重ねた形（2階の宴の間）を引きつぐ</li>
 *   <li>地下遊技場（casino_parlor）：前の版で街を建てたワールドには、まだないので置く（1階の休み場の所に階段の口ができる）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class ParlorMigration {
    private static boolean pending;

    private ParlorMigration() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        pending = true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!pending || event.phase != TickEvent.Phase.END || event.getServer() == null) {
            return;
        }
        pending = false;
        migrate(event.getServer());
    }

    static void migrate(MinecraftServer server) {
        try {
            if (TownData.get(server).renameProject(AmemaruMod.id("fishrace"), AmemaruMod.id("new_machines"))) {
                AmemaruMod.LOGGER.info("[amemaru][parlor] project fishrace was renamed to new_machines");
            }
            StoryState story = StoryState.get(server);
            if (story.remove(AmemaruMod.id("project/fishrace"))) {
                story.add(AmemaruMod.id("project/new_machines"));
                AmemaruMod.LOGGER.info("[amemaru][parlor] flag project/fishrace was renamed to project/new_machines");
            }
            if (CityCommands.renameOverlay(server.overworld(), "casino_fishrace", "casino_new_machines")) {
                AmemaruMod.LOGGER.info("[amemaru][parlor] the grown form casino_fishrace was replaced by casino_new_machines");
            }
            CityCommands.placeMissingBase(server.overworld(), "casino_parlor");
        } catch (RuntimeException e) {
            AmemaruMod.LOGGER.error("[amemaru][parlor] migration failed", e);
        }
    }
}

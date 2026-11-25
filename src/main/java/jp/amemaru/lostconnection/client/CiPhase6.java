package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.client.screen.MarketScreen;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.economy.MarketData;
import jp.amemaru.lostconnection.economy.MarketService;
import jp.amemaru.lostconnection.economy.ShopService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 自動の確認の写真（Phase 6）の段取り：雑貨屋の画面を開く・回復薬を買う・取引所に見本の出品と露店を置く・取引所を開く・タブを変える。
 * その2（職業とスキル）：スキルを使う・弓で射る・スキルツリーを開いてスキルを選ぶ・職業選択の画面を開く・正面から見る。
 */
final class CiPhase6 {
    /** 雑貨屋（マルタ・ポム）の呼び鈴：商店街の北（街の真ん中から (-18, 0, 22)）の中の (27, 3, 6) */
    private static final BlockPos GENERAL_BELL = new BlockPos(9, 3, 28);

    private CiPhase6() {
    }

    /** その種類の行なら true（first のときだけ動かす）。 */
    static boolean action(Minecraft minecraft, String[] shot, boolean first) {
        String kind = shot[0];
        if (!List.of("p6_shop", "p6_shop_buy", "p6_market_seed", "p6_market", "p6_market_tab", "p6_use", "p6_shoot", "p6_tree",
                "p6_jobs", "p6_front").contains(kind)) {
            return false;
        }
        if (!first) {
            return true;
        }
        switch (kind) {
            case "p6_shop" -> CiPhase3.onServer(minecraft, player -> MarketService.origin(player.server).ifPresent(origin ->
                    ShopService.open(player, origin.offset(GENERAL_BELL), AmemaruMod.id("general"))));
            case "p6_shop_buy" -> CiPhase3.onServer(minecraft, player -> MarketService.origin(player.server).ifPresent(origin ->
                    ShopService.buy(player, origin.offset(GENERAL_BELL), AmemaruMod.id("general"), 0, 2)));
            case "p6_market_seed" -> CiPhase3.onServer(minecraft, CiPhase6::seed);
            case "p6_market" -> CiPhase3.onServer(minecraft, player -> MarketService.open(player, MarketService.Mode.valueOf(shot[3]),
                    shot.length > 4 ? Integer.parseInt(shot[4]) : 0));
            case "p6_market_tab" -> {
                if (minecraft.screen instanceof MarketScreen screen) {
                    screen.ciTab(Integer.parseInt(shot[3]));
                }
            }
            case "p6_use" -> {
                if (minecraft.player != null) {
                    SkillInput.use(minecraft, minecraft.player, Integer.parseInt(shot[3]));
                }
            }
            case "p6_shoot" -> jp.amemaru.lostconnection.network.ModNetwork.sendToServer(
                    new jp.amemaru.lostconnection.network.c2s.BasicShotC2S());
            case "p6_tree" -> {
                jp.amemaru.lostconnection.client.screen.SkillTreeScreen tree = new jp.amemaru.lostconnection.client.screen.SkillTreeScreen();
                minecraft.setScreen(tree);
                for (jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C.SkillInfo skill
                        : ClientDefinitions.get().skillsOf(ClientPlayerState.get().jobIndex())) {
                    if (skill.id().getPath().equals(shot[3])) {
                        tree.select(skill.index());
                    }
                }
            }
            case "p6_jobs" -> CiPhase3.onServer(minecraft, player ->
                    jp.amemaru.lostconnection.job.JobService.openJobScreen(player, player.blockPosition()));
            case "p6_front" -> minecraft.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            default -> {
            }
        }
        return true;
    }

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("amemaru-ci:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static ItemStack item(String name, int count) {
        return new ItemStack(ModItems.MANIFEST.get(name).get(), count);
    }

    /** 見本の出品（3人）と、借りられた露店（区画2「蒼の小物屋」）と、最近の取引。 */
    private static void seed(ServerPlayer player) {
        MinecraftServer server = player.server;
        MarketData data = MarketData.get(server);
        int day = MarketService.today(server);
        UUID aoi = id("aoi");
        UUID ren = id("ren");
        UUID kotone = id("kotone");
        data.add(kotone, "Kotone", item("tsunomofu_fluff", 20), 11, day, day + 3, 1);
        data.add(ren, "Ren", item("crystal_shard", 3), 120, day, day + 3, 2);
        data.add(kotone, "Kotone", item("healing_potion_small", 5), 90, day, day + 3, 3);
        data.add(ren, "Ren", item("soft_horn", 1), 620, day, day + 3, 4);
        data.add(aoi, "Aoi", item("moss_iron", 12), 38, day, day + 3, 5);
        data.add(aoi, "Aoi", item("ancient_stone_sword", 1), 540, day, day + 3, 6);
        data.add(aoi, "Aoi", item("waystone_charm", 1), 500, day, day + 3, 7);
        data.setStall(2, new MarketData.StallRent(aoi, "Aoi", "蒼の小物屋", day + 2));
        long base = 40;
        for (long price : new long[]{36, 42, 40}) {
            data.recordTrade("amemaru:moss_iron", new MarketData.Trade(day, price, 4, base));
        }
        MarketService.broadcastStalls(server);
    }
}

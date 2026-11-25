package jp.amemaru.lostconnection.guild;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.GuildS2C;
import jp.amemaru.lostconnection.npc.NpcDef;
import jp.amemaru.lostconnection.npc.NpcDefs;
import jp.amemaru.lostconnection.npc.NpcEntity;
import jp.amemaru.lostconnection.npc.NpcService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.tower.TowerService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Optional;

/**
 * ギルドのイベント（Phase 6 その3）：入ったとき・抜けたとき・名前に称号を付ける・1秒ごとのクエストの見直し・
 * 決まった日から現れるNPC（ノア・エルネスト）・依頼の掲示板を右クリック。
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class GuildEvents {
    private static int lastDay = -1;
    private static boolean backfilled;

    private GuildEvents() {
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        backfillLimited(player.server);
        GuildService.sendCatalog(player);
        GuildHooks.applyPending(player, data);
        TitleService.claimPending(player, data);
        QuestService.ensureDaily(player, data);
        TitleService.check(player);
        GuildService.syncSelf(player, data);
        // ほかの人が表示している称号を、入った人に。入った人の称号を、ほかの人に
        ModNetwork.sendTo(player, new GuildS2C("shown", TitleService.shownTag(player.server.getPlayerList().getPlayers())));
        TitleService.refreshShown(player);
    }

    /**
     * その3より前の版で起きていた「最初」（フリマの最初の取引・1階の初撃破）の限定称号を、その人たちに渡す
     * （サーバーを起こしてから一度だけ。いない人には、次に入ったときに渡す）。
     */
    private static void backfillLimited(MinecraftServer server) {
        if (backfilled) {
            return;
        }
        backfilled = true;
        jp.amemaru.lostconnection.economy.MarketData market = jp.amemaru.lostconnection.economy.MarketData.get(server);
        if (market.firstSeller() != null && market.firstBuyer() != null) {
            TitleService.onFirst(server, "first_market_trade", List.of(market.firstSeller(), market.firstBuyer()),
                    List.of(nameOf(server, market.firstSeller()), nameOf(server, market.firstBuyer())));
        }
        ProgressionData progress = ProgressionData.get(server);
        for (int floor = 1; floor <= 20; floor++) {
            ProgressionData.FirstKill kill = progress.firstKill(floor);
            if (kill == null) {
                continue;
            }
            List<java.util.UUID> uuids = new java.util.ArrayList<>();
            List<String> names = new java.util.ArrayList<>();
            for (String name : kill.members()) {
                if (server.getProfileCache() == null) {
                    break;
                }
                server.getProfileCache().get(name).ifPresent(profile -> {
                    uuids.add(profile.getId());
                    names.add(profile.getName());
                });
            }
            if (!uuids.isEmpty()) {
                TitleService.onFirst(server, "first_boss_floor" + floor, uuids, names);
            }
        }
    }

    private static String nameOf(MinecraftServer server, java.util.UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        if (server.getProfileCache() != null) {
            return server.getProfileCache().get(uuid).map(com.mojang.authlib.GameProfile::getName).orElse("?");
        }
        return "?";
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PartyBoard.onLogout(event.getEntity().getUUID());
    }

    /** チャット・頭の上（サーバーから送る名前）の前に、表示している称号を付ける（Phase 0-A 22章【確定】）。 */
    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ServerConfig.GUILD_SHOW_TITLES.get()) {
            return;
        }
        MutableComponent prefix = TitleService.prefix(player);
        if (prefix != null) {
            event.setDisplayname(Component.empty().append(prefix).append(event.getDisplayname()));
        }
    }

    /** Tabの一覧の名前の前に、表示している称号を付ける。 */
    @SubscribeEvent
    public static void onTabListName(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ServerConfig.GUILD_SHOW_TITLES.get()) {
            return;
        }
        MutableComponent prefix = TitleService.prefix(player);
        if (prefix != null) {
            Component name = event.getDisplayName() == null ? player.getName() : event.getDisplayName();
            event.setDisplayName(Component.empty().append(prefix).append(name));
        }
    }

    /** 依頼の掲示板（ギルドの1階）を右クリック：北の面は依頼、南の面はパーティ募集。 */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || player.level() != player.server.overworld()) {
            return;
        }
        String tab = GuildService.boardTab(player.server, event.getPos(), player.position());
        if (tab == null || player.isShiftKeyDown()) {
            return;
        }
        GuildService.openGuild(player, tab);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        long tick = server.getTickCount();
        if (tick % 20 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PlayerDataCapability.get(player).ifPresent(data -> QuestService.tick(player, data));
            }
            int day = ProgressionData.get(server).day();
            if (day != lastDay) {
                if (lastDay >= 0) {
                    StallDonation.onDay(server, day);
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        PlayerDataCapability.get(player).ifPresent(data -> GuildService.syncSelf(player, data));
                    }
                } else {
                    StallDonation.onDay(server, day);
                }
                lastDay = day;
            }
        }
        if (tick % 100 == 37) {
            updateAppearances(server);
            PartyBoard.prune(server);
        }
    }

    /** 決まった日・物語フラグで現れるNPC（{@code appears}）を、置いたり消したりする。 */
    public static void updateAppearances(MinecraftServer server) {
        Optional<BlockPos> origin = TowerService.cityOrigin(server);
        if (origin.isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        for (NpcDef def : NpcDefs.all().values()) {
            if (def.appears() == null || def.home() == null || !"conelia".equals(def.home().place())) {
                continue;
            }
            BlockPos at = origin.get().offset((int) Math.floor(def.home().pos().x), (int) Math.floor(def.home().pos().y),
                    (int) Math.floor(def.home().pos().z));
            if (!level.isLoaded(at)) {
                continue;
            }
            boolean should = def.appears().testServer(server);
            List<NpcEntity> found = level.getEntitiesOfClass(NpcEntity.class, new AABB(at).inflate(24.0),
                    npc -> def.id().equals(npc.npcId()));
            if (should && found.isEmpty()) {
                NpcService.spawn(level, origin.get(), def);
            } else if (!should && !found.isEmpty()) {
                for (NpcEntity npc : found) {
                    npc.discard();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PartyBoard.clear();
        lastDay = -1;
        backfilled = false;
    }
}

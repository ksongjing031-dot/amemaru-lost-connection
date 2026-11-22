package jp.amemaru.lostconnection.progression;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.AnnouncementS2C;
import jp.amemaru.lostconnection.network.s2c.WorldStateS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.TowerHall;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 管理者が進める進行（Phase 0-B 9章・10章、Phase 0-A 11章「進行の操作」）。
 *
 * <ul>
 *   <li>進行日を進めると、その日の予定（{@link DaySchedule}）の階を開き、全員に知らせる</li>
 *   <li>階層を個別に開く・閉じる。開くと、塔の入口の「塔の灯り」のその階のろうそくが灯る（{@link TowerHall}）</li>
 *   <li>予告をつけると、全員の画面に「◯分後に◯◯」とカウントダウンを出し、時間になったら行う（予告は保存するので、
 *       サーバーが止まっても数え続ける）</li>
 *   <li>進行日・章・開いた階・予告は {@link WorldStateS2C} で全員に送る（ログインしたときと、変わったとき）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class ProgressionService {
    private static int tickCounter;

    private ProgressionService() {
    }

    // ---------------------------------------------------------------- 起動・ログイン・毎秒

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ProgressionData data = ProgressionData.get(server);
        if (!data.floorsInitialized()) {
            // はじめて（または Phase 4 までのワールド）：今の日までの予定の階を開く
            for (int floor : DaySchedule.floorsUntil(data.day())) {
                data.unlock(floor);
            }
            data.markFloorsInitialized();
            AmemaruMod.LOGGER.info("[amemaru] Progression initialized: day {}, floors {}", data.day(), data.unlockedFloors());
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModNetwork.sendTo(player, WorldStateS2C.of(player.server));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tickCounter % 20 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        ProgressionData data = ProgressionData.get(server);
        if (data.scheduled().isEmpty()) {
            return;
        }
        List<ProgressionData.Scheduled> due = data.takeDue(System.currentTimeMillis());
        for (ProgressionData.Scheduled s : due) {
            switch (s.kind()) {
                case UNLOCK_FLOOR -> unlockFloor(server, s.value(), true);
                case DAY -> setDay(server, s.value());
                case ANNOUNCE -> broadcast(server, Component.literal(s.text()), AnnouncementS2C.Kind.EVENT);
            }
        }
        if (!due.isEmpty()) {
            broadcastWorldState(server);
        }
    }

    // ---------------------------------------------------------------- 進行日

    /**
     * 進行日を決める。進めたときは、飛ばした日も含めて、その日までの予定の階を開く（戻したときは開いた階はそのまま）。
     * 章（レベルと熟練度の上限）と、アクセサリーの留め具の枠（11日目）を全員に合わせ直す（Phase 3）。
     */
    public static int setDay(MinecraftServer server, int day) {
        ProgressionData data = ProgressionData.get(server);
        int before = data.day();
        data.setDay(day);
        int now = data.day();
        for (int d = before + 1; d <= now; d++) {
            DaySchedule.Day plan = DaySchedule.day(d);
            for (int floor : plan.unlockFloors()) {
                unlockFloor(server, floor, false);
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            jp.amemaru.lostconnection.accessory.AccessoryService.updateClaspSlot(player);
            PlayerDataCapability.get(player).ifPresent(playerData ->
                    playerData.markDirty(PlayerData.SYNC_ADVENTURE | PlayerData.SYNC_GROWTH));
        }
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.amemaru.day.changed", now).withStyle(ChatFormatting.GOLD), false);
        if (now > before) {
            String announce = DaySchedule.day(now).announce();
            broadcast(server, announce.isBlank() ? Component.translatable("message.amemaru.day.banner", now) : Component.literal(announce),
                    AnnouncementS2C.Kind.DAY);
        }
        // Phase 6：フリーマーケットの相場の変動・出品と露店の期限
        jp.amemaru.lostconnection.economy.MarketService.onDayChanged(server, before, now);
        // Phase 7 その4：共同事業の受付の始まりと、完成していた共同事業の施設が変わる（次の朝）
        jp.amemaru.lostconnection.town.ProjectService.onDayChanged(server, before, now);
        broadcastWorldState(server);
        TowerHall.update(server);
        return now;
    }

    // ---------------------------------------------------------------- 階層

    /** 階を開く。{@code announce} なら全員に知らせる。もう開いていれば false。 */
    public static boolean unlockFloor(MinecraftServer server, int floor, boolean announce) {
        ProgressionData data = ProgressionData.get(server);
        boolean changed = data.unlock(floor);
        if (changed) {
            FloorDef def = FloorDefs.byNumber(floor);
            Component name = def != null ? Component.literal(def.title()) : Component.translatable("message.amemaru.floor.number", floor);
            if (announce) {
                broadcast(server, Component.translatable("message.amemaru.floor.unlocked", name), AnnouncementS2C.Kind.FLOOR);
            } else {
                server.getPlayerList().broadcastSystemMessage(Component.translatable("message.amemaru.floor.unlocked_chat", name)
                        .withStyle(ChatFormatting.AQUA), false);
            }
            AmemaruMod.LOGGER.info("[amemaru] Floor {} unlocked", floor);
            broadcastWorldState(server);
            TowerHall.update(server);
        }
        return changed;
    }

    /** 階を閉じる。開いていなければ false。 */
    public static boolean lockFloor(MinecraftServer server, int floor) {
        boolean changed = ProgressionData.get(server).lock(floor);
        if (changed) {
            AmemaruMod.LOGGER.info("[amemaru] Floor {} locked", floor);
            broadcastWorldState(server);
            TowerHall.update(server);
        }
        return changed;
    }

    // ---------------------------------------------------------------- 予告

    /** 予告をつける（{@code minutes} 分後に行う）。全員の画面にカウントダウンを出す。 */
    public static ProgressionData.Scheduled schedule(MinecraftServer server, ProgressionData.ScheduleKind kind, int value, String text,
                                                     double minutes) {
        long at = System.currentTimeMillis() + Math.round(minutes * 60_000.0);
        ProgressionData.Scheduled s = ProgressionData.get(server).schedule(kind, value, text, at);
        Component what = describe(s);
        broadcast(server, Component.translatable("message.amemaru.schedule.announced", formatMinutes(minutes), what),
                AnnouncementS2C.Kind.SCHEDULE);
        broadcastWorldState(server);
        TowerHall.update(server);
        return s;
    }

    /** 予告の中身（「1階がひらく」「2日目になる」「（知らせの文）」）。 */
    public static Component describe(ProgressionData.Scheduled s) {
        return switch (s.kind()) {
            case UNLOCK_FLOOR -> {
                FloorDef def = FloorDefs.byNumber(s.value());
                yield Component.translatable("message.amemaru.schedule.floor",
                        def != null ? Component.literal(def.title()) : Component.translatable("message.amemaru.floor.number", s.value()));
            }
            case DAY -> Component.translatable("message.amemaru.schedule.day", s.value());
            case ANNOUNCE -> Component.literal(s.text());
        };
    }

    private static String formatMinutes(double minutes) {
        if (Math.abs(minutes - Math.rint(minutes)) < 1.0e-6) {
            return Long.toString(Math.round(minutes));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", minutes);
    }

    // ---------------------------------------------------------------- 知らせ

    /** 全員の画面の真ん中に知らせを出し、チャットにも残す。 */
    public static void broadcast(MinecraftServer server, Component message, AnnouncementS2C.Kind kind) {
        server.getPlayerList().broadcastSystemMessage(Component.literal("【").append(Component.translatable(kind.labelKey()))
                .append("】").withStyle(ChatFormatting.GOLD).append(message.copy().withStyle(ChatFormatting.WHITE)), false);
        ModNetwork.sendToAll(new AnnouncementS2C(kind, message.getString()));
    }

    /** 進行日・章・開いた階・予告を全員に送る。 */
    public static void broadcastWorldState(MinecraftServer server) {
        ModNetwork.sendToAll(WorldStateS2C.of(server));
    }

    /** 結晶の加護による、その階のボスへの与ダメージの倍率（+5% ずつ）。 */
    public static double blessingMultiplier(MinecraftServer server, int floor) {
        return 1.0 + 0.05 * ProgressionData.get(server).blessing(floor);
    }
}

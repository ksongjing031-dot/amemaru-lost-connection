package jp.amemaru.lostconnection.admin;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import jp.amemaru.lostconnection.npc.NpcDef;
import jp.amemaru.lostconnection.npc.NpcDefs;
import jp.amemaru.lostconnection.npc.NpcService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.progression.ProgressionService;
import jp.amemaru.lostconnection.story.GuideService;
import jp.amemaru.lostconnection.story.OpeningService;
import jp.amemaru.lostconnection.story.StoryEventDef;
import jp.amemaru.lostconnection.story.StoryEventManager;
import jp.amemaru.lostconnection.story.StoryState;
import jp.amemaru.lostconnection.tower.BossRoom;
import jp.amemaru.lostconnection.tower.FloorBuild;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorRuntime;
import jp.amemaru.lostconnection.tower.TowerData;
import jp.amemaru.lostconnection.tower.TowerService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Phase 5の管理者のコマンド（Phase 0-B 10章「主なコマンド」）：
 * <ul>
 *   <li>{@code map build floor <階>}・{@code floor list|unlock|lock|status|tp}・{@code boss respawn <階>}・{@code blessing}：塔の階</li>
 *   <li>{@code announce <分> <文>}・{@code schedule list|cancel}：予告とカウントダウン（{@code floor unlock <階> in <分>}・
 *       {@code day next in <分>} も予告になる）</li>
 *   <li>{@code story trigger|replay|list|opening|wake|firstkill}・{@code flag}：物語</li>
 *   <li>{@code npc place|list|remove}：街のNPC</li>
 *   <li>{@code pillars reset|complete <階>}：石柱の起動（確かめ用）</li>
 * </ul>
 */
public final class StoryCommands {
    private static final SuggestionProvider<CommandSourceStack> EVENTS = (context, builder) -> {
        for (StoryEventDef def : StoryEventManager.all()) {
            builder.suggest(def.id().toString());
        }
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> FLOORS = (context, builder) -> {
        for (FloorDef def : FloorDefs.all()) {
            builder.suggest(def.number());
        }
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> SPOTS = (context, builder) -> {
        for (String spot : List.of("entrance", "boss", "center", "pillar1", "pillar2", "pillar3", "top")) {
            builder.suggest(spot);
        }
        return builder.buildFuture();
    };

    private StoryCommands() {
    }

    // ---------------------------------------------------------------- map

    static LiteralArgumentBuilder<CommandSourceStack> mapCommand() {
        return Commands.literal("map")
                .then(Commands.literal("build")
                        .then(Commands.literal("floor")
                                .then(Commands.argument("floor", IntegerArgumentType.integer(1, 99)).suggests(FLOORS)
                                        .executes(ctx -> FloorBuild.build(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor"))))));
    }

    // ---------------------------------------------------------------- floor

    static LiteralArgumentBuilder<CommandSourceStack> floorCommand() {
        return Commands.literal("floor")
                .then(Commands.literal("list").executes(ctx -> listFloors(ctx.getSource())))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("floor", IntegerArgumentType.integer(1, 99)).suggests(FLOORS)
                                .executes(ctx -> unlock(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor")))
                                .then(Commands.literal("in")
                                        .then(Commands.argument("minutes", DoubleArgumentType.doubleArg(0.1, 1440.0))
                                                .executes(ctx -> scheduleUnlock(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor"),
                                                        DoubleArgumentType.getDouble(ctx, "minutes")))))))
                .then(Commands.literal("lock")
                        .then(Commands.argument("floor", IntegerArgumentType.integer(1, 99)).suggests(FLOORS)
                                .executes(ctx -> lock(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor")))))
                .then(Commands.literal("status")
                        .then(Commands.argument("floor", IntegerArgumentType.integer(1, 99)).suggests(FLOORS)
                                .executes(ctx -> status(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor")))))
                .then(Commands.literal("tp")
                        .then(Commands.argument("floor", IntegerArgumentType.integer(1, 99)).suggests(FLOORS)
                                .executes(ctx -> tp(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor"), "entrance",
                                        List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("spot", StringArgumentType.word()).suggests(SPOTS)
                                        .executes(ctx -> tp(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor"),
                                                StringArgumentType.getString(ctx, "spot"), List.of(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> tp(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor"),
                                                        StringArgumentType.getString(ctx, "spot"), EntityArgument.getPlayers(ctx, "targets")))))))
                .then(Commands.literal("return")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> {
                                    for (ServerPlayer player : EntityArgument.getPlayers(ctx, "targets")) {
                                        TowerService.returnToGate(player);
                                    }
                                    return 1;
                                })));
    }

    private static int listFloors(CommandSourceStack source) {
        ProgressionData progress = ProgressionData.get(source.getServer());
        TowerData tower = TowerData.get(source.getServer());
        source.sendSuccess(() -> Component.translatable("commands.amemaru.floor.list_header", progress.day(), progress.chapter()), false);
        for (int n = 1; n <= FloorDefs.FLOOR_COUNT; n++) {
            FloorDef def = FloorDefs.byNumber(n);
            ProgressionData.FirstKill kill = progress.firstKill(n);
            int floor = n;
            String line = n + "階" + (def != null ? "　" + def.name() : "（定義なし）")
                    + "：" + (progress.isUnlocked(n) ? "ひらいている" : "とじている")
                    + "・" + (tower.isBuilt(n) ? "建てた" : "建てていない")
                    + (progress.blessing(n) > 0 ? "・加護+" + (progress.blessing(n) * 5) + "%" : "")
                    + (kill != null ? "・初撃破：" + String.join("・", kill.members()) : "");
            if (def != null || progress.isUnlocked(n)) {
                source.sendSuccess(() -> Component.literal(line).withStyle(progress.isUnlocked(floor) ? ChatFormatting.AQUA : ChatFormatting.GRAY),
                        false);
            }
        }
        return 1;
    }

    private static int unlock(CommandSourceStack source, int floor) {
        boolean changed = ProgressionService.unlockFloor(source.getServer(), floor, true);
        AdminCommands.log(source, "floor unlock " + floor);
        source.sendSuccess(() -> Component.translatable(changed ? "commands.amemaru.floor.unlocked" : "commands.amemaru.floor.already_open",
                floor), true);
        return changed ? 1 : 0;
    }

    private static int scheduleUnlock(CommandSourceStack source, int floor, double minutes) {
        ProgressionData.Scheduled s = ProgressionService.schedule(source.getServer(), ProgressionData.ScheduleKind.UNLOCK_FLOOR, floor, "",
                minutes);
        AdminCommands.log(source, "floor unlock " + floor + " in " + minutes + " (#" + s.id() + ")");
        source.sendSuccess(() -> Component.translatable("commands.amemaru.schedule.added", s.id()), true);
        return s.id();
    }

    private static int lock(CommandSourceStack source, int floor) {
        boolean changed = ProgressionService.lockFloor(source.getServer(), floor);
        AdminCommands.log(source, "floor lock " + floor);
        source.sendSuccess(() -> Component.translatable(changed ? "commands.amemaru.floor.locked" : "commands.amemaru.floor.already_closed",
                floor), true);
        return changed ? 1 : 0;
    }

    private static int status(CommandSourceStack source, int floor) {
        FloorRuntime runtime = TowerService.runtime(floor);
        if (runtime == null) {
            source.sendFailure(Component.translatable("commands.amemaru.map.no_floor", floor));
            return 0;
        }
        BossRoom room = runtime.bossRoom();
        String text = floor + "階：湧いた敵 " + runtime.spawner().count() + "・ボスの部屋 " + (room == null ? "なし" : room.describe());
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int tp(CommandSourceStack source, int floor, String spot, Collection<ServerPlayer> players) {
        FloorDef def = FloorDefs.byNumber(floor);
        ServerLevel tower = FloorDefs.towerLevel(source.getServer());
        if (def == null || tower == null) {
            source.sendFailure(Component.translatable("commands.amemaru.map.no_floor", floor));
            return 0;
        }
        Vec3 at;
        float yaw = def.entrance().yaw();
        float pitch = 0.0F;
        switch (spot) {
            case "boss" -> {
                if (def.bossRoom() == null) {
                    source.sendFailure(Component.translatable("commands.amemaru.floor.no_boss", floor));
                    return 0;
                }
                FloorDef.BossRoomDef room = def.bossRoom();
                Vec3 seal = Vec3.atCenterOf(room.sealMin()).add(Vec3.atCenterOf(room.sealMax())).scale(0.5);
                Vec3 outward = seal.subtract(room.arenaCenter()).multiply(1, 0, 1).normalize();
                at = def.world(new Vec3(seal.x, room.sealMin().getY(), seal.z).add(outward.scale(4.0)));
                yaw = (float) Math.toDegrees(Math.atan2(outward.x, -outward.z));
            }
            case "center" -> at = def.world(new Vec3(def.sizeX() / 2.0, def.entrance().pos().y + 1, def.sizeZ() / 2.0));
            case "top" -> {
                at = def.world(new Vec3(def.sizeX() / 2.0, def.sizeY() + 90, def.sizeZ() / 2.0));
                pitch = 90.0F;
            }
            default -> {
                if (spot.startsWith("pillar")) {
                    int index;
                    try {
                        index = Integer.parseInt(spot.substring("pillar".length())) - 1;
                    } catch (NumberFormatException e) {
                        index = -1;
                    }
                    if (index < 0 || index >= def.pillars().size()) {
                        source.sendFailure(Component.translatable("commands.amemaru.floor.no_spot", spot));
                        return 0;
                    }
                    BlockPos p = def.world(def.pillars().get(index).pos());
                    at = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 3.5);
                    yaw = 180.0F;
                } else {
                    at = def.world(def.entrance().pos());
                }
            }
        }
        for (ServerPlayer player : players) {
            player.teleportTo(tower, at.x, at.y, at.z, yaw, pitch);
        }
        return players.size();
    }

    // ---------------------------------------------------------------- boss・blessing・announce・schedule

    static LiteralArgumentBuilder<CommandSourceStack> bossCommand() {
        return Commands.literal("boss")
                .then(Commands.literal("respawn")
                        .then(Commands.argument("floor", IntegerArgumentType.integer(1, 99)).suggests(FLOORS)
                                .executes(ctx -> {
                                    int floor = IntegerArgumentType.getInteger(ctx, "floor");
                                    FloorRuntime runtime = TowerService.runtime(floor);
                                    ServerLevel tower = FloorDefs.towerLevel(ctx.getSource().getServer());
                                    if (runtime == null || runtime.bossRoom() == null || tower == null) {
                                        ctx.getSource().sendFailure(Component.translatable("commands.amemaru.floor.no_boss", floor));
                                        return 0;
                                    }
                                    runtime.bossRoom().forceRespawn(tower);
                                    AdminCommands.log(ctx.getSource(), "boss respawn " + floor);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.amemaru.boss.respawn", floor), true);
                                    return 1;
                                })));
    }

    static LiteralArgumentBuilder<CommandSourceStack> blessingCommand() {
        return Commands.literal("blessing")
                .then(Commands.argument("floor", IntegerArgumentType.integer(1, 99)).suggests(FLOORS)
                        .executes(ctx -> showBlessing(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor"))))
                .then(Commands.literal("up")
                        .then(Commands.argument("floor", IntegerArgumentType.integer(1, 99)).suggests(FLOORS)
                                .executes(ctx -> setBlessing(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor"),
                                        ProgressionData.get(ctx.getSource().getServer()).blessing(IntegerArgumentType.getInteger(ctx, "floor")) + 1))))
                .then(Commands.literal("set")
                        .then(Commands.argument("floor", IntegerArgumentType.integer(1, 99)).suggests(FLOORS)
                                .then(Commands.argument("steps", IntegerArgumentType.integer(0, ProgressionData.MAX_BLESSING))
                                        .executes(ctx -> setBlessing(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor"),
                                                IntegerArgumentType.getInteger(ctx, "steps"))))));
    }

    private static int showBlessing(CommandSourceStack source, int floor) {
        int steps = ProgressionData.get(source.getServer()).blessing(floor);
        source.sendSuccess(() -> Component.translatable("commands.amemaru.blessing.info", floor, steps, steps * 5), false);
        return steps;
    }

    private static int setBlessing(CommandSourceStack source, int floor, int steps) {
        ProgressionData data = ProgressionData.get(source.getServer());
        data.setBlessing(floor, steps);
        int now = data.blessing(floor);
        AdminCommands.log(source, "blessing " + floor + " = " + now);
        FloorDef def = FloorDefs.byNumber(floor);
        ProgressionService.broadcast(source.getServer(), Component.translatable("message.amemaru.blessing.changed",
                def != null ? Component.literal(def.title()) : Component.translatable("message.amemaru.floor.number", floor), now * 5),
                jp.amemaru.lostconnection.network.s2c.AnnouncementS2C.Kind.EVENT);
        source.sendSuccess(() -> Component.translatable("commands.amemaru.blessing.info", floor, now, now * 5), true);
        return now;
    }

    static LiteralArgumentBuilder<CommandSourceStack> announceCommand() {
        return Commands.literal("announce")
                .then(Commands.argument("minutes", DoubleArgumentType.doubleArg(0.1, 1440.0))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ProgressionData.Scheduled s = ProgressionService.schedule(ctx.getSource().getServer(),
                                            ProgressionData.ScheduleKind.ANNOUNCE, 0, StringArgumentType.getString(ctx, "text"),
                                            DoubleArgumentType.getDouble(ctx, "minutes"));
                                    AdminCommands.log(ctx.getSource(), "announce #" + s.id() + " " + s.text());
                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.amemaru.schedule.added", s.id()), true);
                                    return s.id();
                                })));
    }

    static LiteralArgumentBuilder<CommandSourceStack> scheduleCommand() {
        return Commands.literal("schedule")
                .then(Commands.literal("list").executes(ctx -> {
                    ProgressionData data = ProgressionData.get(ctx.getSource().getServer());
                    long now = System.currentTimeMillis();
                    if (data.scheduled().isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("commands.amemaru.schedule.none"), false);
                    }
                    for (ProgressionData.Scheduled s : data.scheduled()) {
                        long seconds = Math.max(0, (s.atMillis() - now) / 1000);
                        ctx.getSource().sendSuccess(() -> Component.literal("#" + s.id() + "　")
                                .append(ProgressionService.describe(s)).append("　あと " + seconds / 60 + "分" + seconds % 60 + "秒"), false);
                    }
                    return data.scheduled().size();
                }))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int id = IntegerArgumentType.getInteger(ctx, "id");
                                    boolean removed = ProgressionData.get(ctx.getSource().getServer()).cancel(id);
                                    if (removed) {
                                        ProgressionService.broadcastWorldState(ctx.getSource().getServer());
                                        jp.amemaru.lostconnection.tower.TowerHall.update(ctx.getSource().getServer());
                                    }
                                    AdminCommands.log(ctx.getSource(), "schedule cancel " + id);
                                    ctx.getSource().sendSuccess(() -> Component.translatable(removed ? "commands.amemaru.schedule.cancelled"
                                            : "commands.amemaru.schedule.unknown", id), true);
                                    return removed ? 1 : 0;
                                })));
    }

    // ---------------------------------------------------------------- story

    static LiteralArgumentBuilder<CommandSourceStack> storyCommand() {
        return Commands.literal("story")
                .then(Commands.literal("list").executes(ctx -> {
                    StoryState state = StoryState.get(ctx.getSource().getServer());
                    for (StoryEventDef def : StoryEventManager.all()) {
                        ctx.getSource().sendSuccess(() -> Component.literal(def.id() + "　" + def.title() + "（" + def.triggerType()
                                + (state.happened(def.id()) ? "・起きた" : "") + "）"), false);
                    }
                    return StoryEventManager.all().size();
                }))
                .then(Commands.literal("trigger")
                        .then(Commands.argument("event", ResourceLocationArgument.id()).suggests(EVENTS)
                                .executes(ctx -> trigger(ctx.getSource(), ResourceLocationArgument.getId(ctx, "event"),
                                        List.of(ctx.getSource().getPlayerOrException()), false))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> trigger(ctx.getSource(), ResourceLocationArgument.getId(ctx, "event"),
                                                EntityArgument.getPlayers(ctx, "targets"), false)))))
                .then(Commands.literal("replay")
                        .then(Commands.argument("event", ResourceLocationArgument.id()).suggests(EVENTS)
                                .executes(ctx -> trigger(ctx.getSource(), ResourceLocationArgument.getId(ctx, "event"),
                                        List.of(ctx.getSource().getPlayerOrException()), true))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> trigger(ctx.getSource(), ResourceLocationArgument.getId(ctx, "event"),
                                                EntityArgument.getPlayers(ctx, "targets"), true)))))
                .then(Commands.literal("opening")
                        .then(Commands.literal("start").then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> opening(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), "start"))))
                        .then(Commands.literal("skip").then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> opening(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), "skip"))))
                        .then(Commands.literal("reset").then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> opening(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), "reset"))))
                        .then(Commands.literal("status").then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> opening(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), "status")))))
                .then(Commands.literal("wake")
                        .then(Commands.literal("set").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            StoryState.get(ctx.getSource().getServer()).setWake(player.position(), player.getYRot());
                            AdminCommands.log(ctx.getSource(), "story wake set " + player.blockPosition().toShortString());
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.amemaru.story.wake_set"), true);
                            return 1;
                        }))
                        .then(Commands.literal("clear").executes(ctx -> {
                            StoryState.get(ctx.getSource().getServer()).setWake(null, 0.0F);
                            AdminCommands.log(ctx.getSource(), "story wake clear");
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.amemaru.story.wake_cleared"), true);
                            return 1;
                        }))
                        .then(Commands.literal("tp").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            OpeningService.WakeSpot spot = OpeningService.wakeSpot(player);
                            player.teleportTo(spot.level(), spot.pos().x, spot.pos().y, spot.pos().z, spot.yaw(), 0.0F);
                            return 1;
                        })))
                .then(Commands.literal("firstkill")
                        .then(Commands.literal("clear")
                                .then(Commands.argument("floor", IntegerArgumentType.integer(1, 99)).suggests(FLOORS)
                                        .executes(ctx -> {
                                            int floor = IntegerArgumentType.getInteger(ctx, "floor");
                                            boolean removed = ProgressionData.get(ctx.getSource().getServer()).clearFirstKill(floor);
                                            for (StoryEventDef def : StoryEventManager.all()) {
                                                if ("floor_first_kill".equals(def.triggerType()) && def.triggerFloor() == floor) {
                                                    StoryState.get(ctx.getSource().getServer()).forget(def.id());
                                                }
                                            }
                                            AdminCommands.log(ctx.getSource(), "story firstkill clear " + floor);
                                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.amemaru.story.firstkill_cleared",
                                                    floor), true);
                                            return removed ? 1 : 0;
                                        }))));
    }

    private static int trigger(CommandSourceStack source, ResourceLocation id, Collection<ServerPlayer> players, boolean replay) {
        if (StoryEventManager.get(id) == null) {
            source.sendFailure(Component.translatable("commands.amemaru.story.unknown", id.toString()));
            return 0;
        }
        int count = StoryEventManager.adminTrigger(id, players, replay);
        AdminCommands.log(source, "story " + (replay ? "replay " : "trigger ") + id + " x" + count);
        source.sendSuccess(() -> Component.translatable("commands.amemaru.story.played", id.toString(), count), true);
        return count;
    }

    private static int opening(CommandSourceStack source, Collection<ServerPlayer> players, String action) {
        for (ServerPlayer player : players) {
            switch (action) {
                case "start" -> {
                    OpeningService.reset(player);
                    OpeningService.begin(player);
                }
                case "skip" -> OpeningService.skip(player);
                case "reset" -> OpeningService.reset(player);
                default -> {
                    PlayerData data = PlayerDataCapability.get(player).orElse(null);
                    int stage = data == null ? -1 : data.getStory().opening();
                    Component stageText = Component.translatable("commands.amemaru.story.opening.stage." + Math.max(0, Math.min(2, stage)));
                    Component pendingText = OpeningService.isPending(player)
                            ? Component.translatable("commands.amemaru.story.opening.pending") : Component.empty();
                    source.sendSuccess(() -> Component.translatable("commands.amemaru.story.opening_status", player.getDisplayName(), stageText,
                            pendingText), false);
                }
            }
        }
        if (!"status".equals(action)) {
            AdminCommands.log(source, "story opening " + action + " x" + players.size());
            source.sendSuccess(() -> Component.translatable("commands.amemaru.story.opening." + action, players.size()), true);
        }
        return players.size();
    }

    // ---------------------------------------------------------------- flag

    static LiteralArgumentBuilder<CommandSourceStack> flagCommand() {
        return Commands.literal("flag")
                .then(Commands.literal("add")
                        .then(Commands.argument("flag", ResourceLocationArgument.id())
                                .executes(ctx -> flag(ctx.getSource(), ResourceLocationArgument.getId(ctx, "flag"), null, true))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> flag(ctx.getSource(), ResourceLocationArgument.getId(ctx, "flag"),
                                                EntityArgument.getPlayers(ctx, "targets"), true)))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("flag", ResourceLocationArgument.id())
                                .executes(ctx -> flag(ctx.getSource(), ResourceLocationArgument.getId(ctx, "flag"), null, false))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> flag(ctx.getSource(), ResourceLocationArgument.getId(ctx, "flag"),
                                                EntityArgument.getPlayers(ctx, "targets"), false)))))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            StoryState state = StoryState.get(ctx.getSource().getServer());
                            MutableComponent text = Component.translatable("commands.amemaru.flag.server");
                            for (ResourceLocation id : state.flags()) {
                                text.append(" " + id);
                            }
                            ctx.getSource().sendSuccess(() -> text, false);
                            return state.flags().size();
                        })
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> {
                                    for (ServerPlayer player : EntityArgument.getPlayers(ctx, "targets")) {
                                        PlayerData data = PlayerDataCapability.get(player).orElse(null);
                                        if (data == null) {
                                            continue;
                                        }
                                        MutableComponent text = Component.translatable("commands.amemaru.flag.player", player.getDisplayName());
                                        for (ResourceLocation id : data.getStory().flags()) {
                                            text.append(" " + id);
                                        }
                                        ctx.getSource().sendSuccess(() -> text, false);
                                    }
                                    return 1;
                                })));
    }

    private static int flag(CommandSourceStack source, ResourceLocation flag, Collection<ServerPlayer> players, boolean add) {
        if (players == null) {
            StoryState state = StoryState.get(source.getServer());
            boolean changed = add ? state.add(flag) : state.remove(flag);
            AdminCommands.log(source, "flag " + (add ? "add " : "remove ") + flag + " (server)");
            source.sendSuccess(() -> Component.translatable(changed ? "commands.amemaru.flag.changed" : "commands.amemaru.flag.same",
                    flag.toString()), true);
            return changed ? 1 : 0;
        }
        int count = 0;
        for (ServerPlayer player : players) {
            PlayerData data = PlayerDataCapability.get(player).orElse(null);
            if (data != null && (add ? data.getStory().add(flag) : data.getStory().remove(flag))) {
                count++;
                GuideService.refresh(player, data);
            }
        }
        int changed = count;
        AdminCommands.log(source, "flag " + (add ? "add " : "remove ") + flag + " x" + changed);
        source.sendSuccess(() -> Component.translatable("commands.amemaru.flag.players", flag.toString(), changed), true);
        return changed;
    }

    // ---------------------------------------------------------------- npc

    static LiteralArgumentBuilder<CommandSourceStack> npcCommand() {
        return Commands.literal("npc")
                .then(Commands.literal("place").executes(ctx -> {
                    ServerLevel level = ctx.getSource().getServer().overworld();
                    Optional<BlockPos> origin = CityCommands.builtOrigin(level);
                    if (origin.isEmpty()) {
                        ctx.getSource().sendFailure(Component.translatable("commands.amemaru.npc.no_city"));
                        return 0;
                    }
                    int count = NpcService.spawnAll(level, origin.get());
                    AdminCommands.log(ctx.getSource(), "npc place x" + count);
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.amemaru.npc.placed", count), true);
                    return count;
                }))
                .then(Commands.literal("list").executes(ctx -> {
                    for (NpcDef def : NpcDefs.all().values()) {
                        String home = def.home() == null ? "—" : def.home().place() + " " + def.home().pos();
                        ctx.getSource().sendSuccess(() -> Component.literal(def.id() + "　" + def.name() + "（" + def.title() + "）　" + home), false);
                    }
                    return NpcDefs.all().size();
                }));
    }

    // ---------------------------------------------------------------- pillars

    static LiteralArgumentBuilder<CommandSourceStack> pillarsCommand() {
        return Commands.literal("pillars")
                .then(Commands.literal("reset")
                        .then(Commands.argument("floor", IntegerArgumentType.integer(1, 99)).suggests(FLOORS)
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> pillars(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor"),
                                                EntityArgument.getPlayers(ctx, "targets"), false)))))
                .then(Commands.literal("complete")
                        .then(Commands.argument("floor", IntegerArgumentType.integer(1, 99)).suggests(FLOORS)
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> pillars(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor"),
                                                EntityArgument.getPlayers(ctx, "targets"), true)))));
    }

    private static int pillars(CommandSourceStack source, int floor, Collection<ServerPlayer> players, boolean complete) {
        FloorDef def = FloorDefs.byNumber(floor);
        int count = def == null ? 0 : def.pillars().size();
        for (ServerPlayer player : players) {
            PlayerDataCapability.get(player).ifPresent(data -> {
                if (complete) {
                    for (int i = 0; i < count; i++) {
                        data.getStory().activatePillar(floor, i);
                    }
                } else {
                    data.getStory().resetPillars(floor);
                }
                GuideService.refresh(player, data);
            });
        }
        AdminCommands.log(source, "pillars " + (complete ? "complete " : "reset ") + floor + " x" + players.size());
        source.sendSuccess(() -> Component.translatable(complete ? "commands.amemaru.pillars.completed" : "commands.amemaru.pillars.reset",
                floor, players.size()), true);
        return players.size();
    }
}

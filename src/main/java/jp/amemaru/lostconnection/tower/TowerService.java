package jp.amemaru.lostconnection.tower;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.admin.CityCommands;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.FloorSelectS2C;
import jp.amemaru.lostconnection.network.s2c.StoryTextS2C;
import jp.amemaru.lostconnection.party.Party;
import jp.amemaru.lostconnection.party.PartyManager;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.story.GuideService;
import jp.amemaru.lostconnection.story.StoryEventManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 塔（Phase 0-B 2章「塔の1〜20階：独自のディメンション amemaru:tower」、Phase 0-A 7章「塔の門：解放済みの階層を選んで入る」）。
 *
 * <ul>
 *   <li>塔の門：街の塔の入口の広間の「転移の円」に立つと、行く階を選ぶ画面が開く。開いていて建ててある階にだけ行ける。
 *       同じパーティのメンバーで円の上にいる人は、一緒に行く</li>
 *   <li>階の入口の円に立つと、塔の門（街）へ戻る。ボスの部屋の奥の円は、戦いが終わると使える</li>
 *   <li>石柱を起動する（本人と、同じパーティのメンバーに数える）。触れるたびに古代文字の一行が出る</li>
 *   <li>人のいる階だけ、敵の湧く所とボスの部屋を動かす（{@link FloorRuntime}）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class TowerService {
    /** 転移の円の真ん中（中央広場の真ん中から。tools/mapgen/build_tower.py の platform：台の上 y 6、円は x 0・z −168）。 */
    public static final Vec3 GATE_CIRCLE = new Vec3(0.5, 7.0, -167.5);
    public static final double GATE_RADIUS = 2.6;
    /** 階から戻ったときに立つ所（転移の台の前の広間の床）。 */
    public static final Vec3 GATE_RETURN = new Vec3(0.5, 4.0, -159.5);
    /** 入口の円・出口の円に立ってから移るまで。 */
    private static final int RETURN_TICKS = 40;

    private static final Map<Integer, FloorRuntime> RUNTIMES = new HashMap<>();
    private static int runtimeGeneration = -1;
    private static final Map<UUID, Boolean> ON_GATE = new HashMap<>();
    private static final Map<UUID, Long> GATE_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Integer> RETURN_CHARGE = new HashMap<>();

    private TowerService() {
    }

    // ---------------------------------------------------------------- 階の動き

    /** その階の動き（定義を読み直したら作り直す）。 */
    @Nullable
    public static FloorRuntime runtime(int floor) {
        int generation = FloorDefs.loader().generation();
        if (generation != runtimeGeneration) {
            RUNTIMES.clear();
            runtimeGeneration = generation;
        }
        FloorDef def = FloorDefs.byNumber(floor);
        if (def == null) {
            return null;
        }
        return RUNTIMES.computeIfAbsent(floor, n -> new FloorRuntime(def));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 5 == 0) {
            checkGate(server);
        }
        ServerLevel tower = FloorDefs.towerLevel(server);
        if (tower == null) {
            return;
        }
        Map<Integer, List<ServerPlayer>> byFloor = new HashMap<>();
        for (ServerPlayer player : tower.players()) {
            FloorDef def = FloorDefs.floorAt(tower, player.position());
            if (def != null) {
                byFloor.computeIfAbsent(def.number(), k -> new ArrayList<>()).add(player);
            }
        }
        for (Map.Entry<Integer, List<ServerPlayer>> entry : byFloor.entrySet()) {
            FloorRuntime runtime = runtime(entry.getKey());
            if (runtime != null) {
                runtime.tick(tower, entry.getValue(), now);
            }
        }
        if (now % 5 == 0) {
            checkReturnCircles(tower, byFloor);
        }
        if (now % 10 == 0) {
            checkTablets(byFloor);
        }
    }

    // ---------------------------------------------------------------- 古代記録の石板（Phase 0-A 9章「道標の石板・一」）

    /** 石板を読んだ人（ログインの間だけ覚える。近づいたときに一度だけ出す）。 */
    private static final Map<UUID, java.util.Set<Integer>> TABLET_SEEN = new HashMap<>();

    private static void checkTablets(Map<Integer, List<ServerPlayer>> byFloor) {
        for (Map.Entry<Integer, List<ServerPlayer>> entry : byFloor.entrySet()) {
            FloorDef def = FloorDefs.byNumber(entry.getKey());
            if (def == null || def.tablet() == null) {
                continue;
            }
            Vec3 at = def.world(Vec3.atCenterOf(def.tablet().pos()));
            for (ServerPlayer player : entry.getValue()) {
                if (player.position().distanceToSqr(at) <= 4.5 * 4.5
                        && TABLET_SEEN.computeIfAbsent(player.getUUID(), k -> new java.util.HashSet<>()).add(def.number())) {
                    readTablet(player, def);
                }
            }
        }
    }

    /** 石板の文字を出す（古代文字から、大結晶の仲立ちで読める字に変わる）。 */
    public static void readTablet(ServerPlayer player, FloorDef def) {
        FloorDef.Tablet tablet = def.tablet();
        if (tablet == null) {
            return;
        }
        ModNetwork.sendTo(player, new StoryTextS2C(StoryTextS2C.Kind.ANCIENT, tablet.title(), tablet.text(), 160));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                1.0F, 0.6F);
    }

    /** 石板を右クリックしたとき（何度でも読める）。 */
    @SubscribeEvent
    public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        if (!player.level().dimension().equals(FloorDefs.TOWER)) {
            return;
        }
        FloorDef def = FloorDefs.floorAt(player.level(), player.position());
        if (def == null || def.tablet() == null) {
            return;
        }
        BlockPos tablet = def.world(def.tablet().pos());
        if (event.getPos().distSqr(tablet) <= 2.0 * 2.0) {
            readTablet(player, def);
        }
    }

    // ---------------------------------------------------------------- 塔の門（街）

    /** 中央広場の真ん中（街を建てていなければ空）。 */
    public static Optional<BlockPos> cityOrigin(MinecraftServer server) {
        return CityCommands.builtOrigin(server.overworld());
    }

    private static void checkGate(MinecraftServer server) {
        Optional<BlockPos> origin = cityOrigin(server);
        if (origin.isEmpty()) {
            return;
        }
        Vec3 center = Vec3.atLowerCornerOf(origin.get()).add(GATE_CIRCLE);
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : server.overworld().players()) {
            double dx = player.getX() - center.x;
            double dz = player.getZ() - center.z;
            double dy = player.getY() - center.y;
            boolean on = dx * dx + dz * dz <= GATE_RADIUS * GATE_RADIUS && dy > -1.0 && dy < 2.0 && !player.isSpectator();
            boolean was = ON_GATE.getOrDefault(player.getUUID(), false);
            ON_GATE.put(player.getUUID(), on);
            if (on && !was && now >= GATE_COOLDOWN.getOrDefault(player.getUUID(), 0L)) {
                GuideService.event(player, "tower_gate");
                openFloorSelect(player, false);
                ((ServerLevel) player.level()).sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.2, player.getZ(),
                        16, 0.6, 0.1, 0.6, 0.02);
            }
        }
    }

    /** 転移の円の上（か、その近く）にいるか。 */
    private static boolean nearGate(ServerPlayer player, double range) {
        if (player.level().dimension() != Level.OVERWORLD) {
            return false;
        }
        Optional<BlockPos> origin = cityOrigin(player.server);
        if (origin.isEmpty()) {
            return false;
        }
        Vec3 center = Vec3.atLowerCornerOf(origin.get()).add(GATE_CIRCLE);
        return player.position().distanceToSqr(center) <= range * range;
    }

    /** 行く階を選ぶ画面を開く（{@code fromNpc} は門番との会話から）。 */
    public static void openFloorSelect(ServerPlayer player, boolean fromNpc) {
        MinecraftServer server = player.server;
        ProgressionData progress = ProgressionData.get(server);
        TowerData tower = TowerData.get(server);
        ProgressionData.Scheduled next = progress.nextFloorUnlock();
        long now = System.currentTimeMillis();
        List<FloorSelectS2C.Entry> entries = new ArrayList<>();
        for (int n = 1; n <= FloorDefs.FLOOR_COUNT; n++) {
            FloorDef def = FloorDefs.byNumber(n);
            boolean unlocked = progress.isUnlocked(n);
            FloorSelectS2C.State state;
            if (unlocked && def != null && tower.isBuilt(n)) {
                state = FloorSelectS2C.State.OPEN;
            } else if (unlocked) {
                state = FloorSelectS2C.State.NOT_BUILT;
            } else if (next != null && next.value() == n) {
                state = FloorSelectS2C.State.NEXT;
            } else {
                state = FloorSelectS2C.State.LOCKED;
            }
            ProgressionData.FirstKill kill = progress.firstKill(n);
            int chapter = def != null ? def.chapter() : (n - 1) / 5 + 1;
            entries.add(new FloorSelectS2C.Entry(n, chapter, unlocked && def != null ? def.name() : "", state,
                    def != null ? def.recommendedMin() : 0, def != null ? def.recommendedMax() : 0,
                    kill == null ? "" : String.join("・", kill.members()),
                    state == FloorSelectS2C.State.NEXT && next != null ? next.atMillis() - now : 0L));
        }
        ModNetwork.sendTo(player, new FloorSelectS2C(progress.day(), List.copyOf(entries)));
    }

    /** 画面で階を選んだ（{@code FloorSelectC2S}）。 */
    public static void onFloorSelected(ServerPlayer player, int floor) {
        if (!nearGate(player, 14.0)) {
            player.displayClientMessage(Component.translatable("message.amemaru.tower.not_at_gate"), true);
            return;
        }
        FloorDef def = FloorDefs.byNumber(floor);
        ProgressionData progress = ProgressionData.get(player.server);
        if (def == null || !progress.isUnlocked(floor)) {
            player.displayClientMessage(Component.translatable("message.amemaru.tower.locked").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!TowerData.get(player.server).isBuilt(floor) || FloorDefs.towerLevel(player.server) == null) {
            player.displayClientMessage(Component.translatable("message.amemaru.tower.not_built").withStyle(ChatFormatting.RED), true);
            return;
        }
        // 同じパーティで、転移の円の上にいるメンバーも一緒に
        List<ServerPlayer> travelers = new ArrayList<>();
        travelers.add(player);
        Party party = PartyManager.get(player.server).partyOf(player.getUUID());
        if (party != null) {
            for (UUID member : party.members()) {
                ServerPlayer other = player.server.getPlayerList().getPlayer(member);
                if (other != null && other != player && nearGate(other, GATE_RADIUS + 0.5)) {
                    travelers.add(other);
                }
            }
        }
        for (ServerPlayer traveler : travelers) {
            enterFloor(traveler, def);
        }
    }

    /** 階の入口へ移る。初めてなら、章題と階の名前を出す。 */
    public static void enterFloor(ServerPlayer player, FloorDef def) {
        ServerLevel tower = FloorDefs.towerLevel(player.server);
        if (tower == null) {
            return;
        }
        Vec3 at = def.world(def.entrance().pos());
        player.teleportTo(tower, at.x, at.y, at.z, def.entrance().yaw(), 0.0F);
        tower.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
        tower.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 30, 0.6, 1.0, 0.6, 0.05);
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        boolean first = data != null && data.getStory().markVisited(def.number());
        String upper = first && !def.chapterTitle().isEmpty() ? def.chapterTitle()
                : Component.translatable("message.amemaru.tower.floor_label").getString();
        ModNetwork.sendTo(player, new StoryTextS2C(StoryTextS2C.Kind.TITLE, upper, def.title(), first ? 100 : 60));
        if (data != null) {
            GuideService.refresh(player, data);
        }
        if (first) {
            StoryEventManager.trigger(player.server, "floor_enter", def.number(), List.of(player));
        }
        AmemaruMod.LOGGER.info("[amemaru] {} entered floor {}", player.getGameProfile().getName(), def.number());
    }

    /** 街の塔の門へ戻る。 */
    public static void returnToGate(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        Optional<BlockPos> origin = cityOrigin(player.server);
        Vec3 at;
        float yaw = 0.0F;
        if (origin.isPresent()) {
            at = Vec3.atLowerCornerOf(origin.get()).add(GATE_RETURN);
        } else {
            BlockPos spawn = overworld.getSharedSpawnPos();
            at = Vec3.atBottomCenterOf(spawn);
            yaw = overworld.getSharedSpawnAngle();
        }
        GATE_COOLDOWN.put(player.getUUID(), overworld.getGameTime() + 60);
        player.teleportTo(overworld, at.x, at.y, at.z, yaw, 0.0F);
        overworld.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    private static void checkReturnCircles(ServerLevel tower, Map<Integer, List<ServerPlayer>> byFloor) {
        for (Map.Entry<Integer, List<ServerPlayer>> entry : byFloor.entrySet()) {
            FloorDef def = FloorDefs.byNumber(entry.getKey());
            FloorRuntime runtime = runtime(entry.getKey());
            if (def == null) {
                continue;
            }
            Vec3 origin = def.originVec();
            for (ServerPlayer player : entry.getValue()) {
                boolean inEntrance = def.returnCircle().contains(player.position(), origin);
                boolean inExit = def.bossRoom() != null && runtime != null && runtime.bossRoom() != null
                        && runtime.bossRoom().exitOpen() && def.bossRoom().exit().contains(player.position(), origin);
                if ((inEntrance || inExit) && !player.isSpectator()) {
                    int charge = RETURN_CHARGE.merge(player.getUUID(), 5, Integer::sum);
                    if (charge >= RETURN_TICKS) {
                        RETURN_CHARGE.remove(player.getUUID());
                        returnToGate(player);
                    } else {
                        player.displayClientMessage(Component.translatable("message.amemaru.tower.returning",
                                Math.max(1, (RETURN_TICKS - charge + 19) / 20)).withStyle(ChatFormatting.AQUA), true);
                        tower.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.1, player.getZ(), 4, 0.5, 0.0, 0.5, 0.02);
                    }
                } else {
                    RETURN_CHARGE.remove(player.getUUID());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        ON_GATE.remove(id);
        GATE_COOLDOWN.remove(id);
        RETURN_CHARGE.remove(id);
        TABLET_SEEN.remove(id);
    }

    // ---------------------------------------------------------------- 石柱

    /** その人が、その階の石柱をすべて起動したか（封印を通れるか）。 */
    public static boolean pillarsComplete(ServerPlayer player, int floor) {
        FloorDef def = FloorDefs.byNumber(floor);
        if (def == null || def.pillars().isEmpty()) {
            return true;
        }
        int all = (1 << def.pillars().size()) - 1;
        return PlayerDataCapability.get(player).map(data -> (data.getStory().pillars(floor) & all) == all).orElse(false);
    }

    /**
     * 石柱を起動する。本人と、同じパーティのメンバーに数える【補完】。触れた人には、その石柱の古代文字の一行を出す
     * （もう起動していても、もう一度読める）。
     */
    public static void activatePillar(ServerPlayer player, int floor, int index, BlockPos pos) {
        FloorDef def = FloorDefs.byNumber(floor);
        if (def == null || index < 0 || index >= def.pillars().size()) {
            return;
        }
        FloorDef.PillarDef pillar = def.pillars().get(index);
        ServerLevel level = (ServerLevel) player.level();
        ModNetwork.sendTo(player, new StoryTextS2C(StoryTextS2C.Kind.ANCIENT, Component.translatable("message.amemaru.pillar.label",
                index + 1).getString(), pillar.line(), 120));
        List<ServerPlayer> credited = new ArrayList<>();
        credited.add(player);
        Party party = PartyManager.get(player.server).partyOf(player.getUUID());
        if (party != null) {
            for (UUID member : party.members()) {
                ServerPlayer other = player.server.getPlayerList().getPlayer(member);
                if (other != null && other != player) {
                    credited.add(other);
                }
            }
        }
        boolean any = false;
        for (ServerPlayer target : credited) {
            PlayerData data = PlayerDataCapability.get(target).orElse(null);
            if (data == null || !data.getStory().activatePillar(floor, index)) {
                continue;
            }
            any = true;
            int count = Integer.bitCount(data.getStory().pillars(floor) & ((1 << def.pillars().size()) - 1));
            int total = def.pillars().size();
            if (target != player) {
                target.sendSystemMessage(Component.translatable("message.amemaru.pillar.by_party", player.getGameProfile().getName(),
                        index + 1, count, total).withStyle(ChatFormatting.AQUA));
                if (target.level() == level && def.contains(target.position())) {
                    ModNetwork.sendTo(target, new StoryTextS2C(StoryTextS2C.Kind.ANCIENT, Component.translatable("message.amemaru.pillar.label",
                            index + 1).getString(), pillar.line(), 120));
                }
            } else {
                target.sendSystemMessage(Component.translatable("message.amemaru.pillar.activated", index + 1, count, total)
                        .withStyle(ChatFormatting.AQUA));
            }
            if (count >= total) {
                target.sendSystemMessage(Component.literal(def.pillarsDoneMessage().isEmpty()
                        ? Component.translatable("message.amemaru.pillar.done").getString() : def.pillarsDoneMessage())
                        .withStyle(ChatFormatting.GOLD));
                target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                        0.8F, 1.2F);
            }
            GuideService.refresh(target, data);
        }
        if (any) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.4F, 0.9F);
            level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.4, 1.2, 0.4, 0.06);
        } else {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }
}

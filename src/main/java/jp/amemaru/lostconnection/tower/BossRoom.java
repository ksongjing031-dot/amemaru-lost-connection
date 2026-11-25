package jp.amemaru.lostconnection.tower;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.enemy.boss.AmemaruBoss;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.AnnouncementS2C;
import jp.amemaru.lostconnection.network.s2c.StoryTextS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.progression.ProgressionService;
import jp.amemaru.lostconnection.story.GuideService;
import jp.amemaru.lostconnection.story.StoryEventManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ボスの部屋（11月21日のご指示「ボスはボス部屋として作ってください」）。部屋は入口の封印（{@link FloorSealBlock}）で区切られ、
 * 戦いは部屋の中だけで行う。
 *
 * <ol>
 *   <li>待つ：部屋のまわりに人が来ると、ボスが部屋の真ん中に現れる（倒されたあとは {@code respawn_seconds} 秒後）</li>
 *   <li>現れている：石柱をすべて起動した人は、封印を通って部屋に入れる。ボスに近づく（{@code start_radius}）か攻撃すると、戦いが始まる</li>
 *   <li>戦い：封印が閉じて、だれも出入りできない。そのとき部屋の中にいた人が参加者。ボスのHPは参加者の人数で増える。
 *       参加者がみな部屋からいなくなる（ダウンの時間切れで教会へ戻る・抜ける）と、戦いははじめからになり、封印が開く</li>
 *   <li>倒した：初撃破なら記録して全員に知らせ、物語の演出を流す。封印が開き、部屋の奥の出口の円から街へ戻れる</li>
 * </ol>
 * 同じ部屋で同時に戦えるのは1組（封印が閉じている間、ほかの人は待つ）【補完】。
 */
public final class BossRoom {
    public enum State { WAITING, IDLE, FIGHT, VICTORY }

    private final FloorDef floor;
    private final FloorDef.BossRoomDef def;
    private State state = State.WAITING;
    @Nullable
    private UUID bossId;
    private final Set<UUID> participants = new LinkedHashSet<>();
    private long respawnAt;
    private long stateSince;
    private boolean sealed;
    /** この戦いで、蘇生の手がかり（倒れた仲間はしゃがんで起こせる）を出したか（ヘイト・回避・蘇生のチュートリアル）。 */
    private boolean reviveTipShown;

    public BossRoom(FloorDef floor, FloorDef.BossRoomDef def) {
        this.floor = floor;
        this.def = def;
    }

    public FloorDef floor() {
        return floor;
    }

    public FloorDef.BossRoomDef def() {
        return def;
    }

    public State state() {
        return state;
    }

    public boolean fighting() {
        return state == State.FIGHT;
    }

    /** このボスが、部屋が今出しているボスか（ほかのボスは古いもので、自分で消える）。 */
    public boolean owns(AmemaruBoss boss) {
        return bossId != null && bossId.equals(boss.getUUID());
    }

    /** 部屋の真ん中（世界の座標）。 */
    public Vec3 arenaCenter() {
        return floor.world(def.arenaCenter());
    }

    public boolean inside(Vec3 pos) {
        return def.inside(pos, floor.originVec());
    }

    /** 戦いの間の「部屋の中」（門の通路まで。{@link FloorDef.BossRoomDef#insideFight}）。 */
    public boolean insideFight(Vec3 pos) {
        return def.insideFight(pos, floor.originVec());
    }

    /** 戦いの参加者（いまサーバーにいる人）。 */
    public List<ServerPlayer> participants() {
        List<ServerPlayer> list = new ArrayList<>();
        AmemaruBoss boss = boss();
        if (boss == null || boss.getServer() == null) {
            return list;
        }
        for (UUID id : participants) {
            ServerPlayer player = boss.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                list.add(player);
            }
        }
        return list;
    }

    @Nullable
    private ServerLevel level;

    @Nullable
    public AmemaruBoss boss() {
        if (level == null || bossId == null) {
            return null;
        }
        Entity entity = level.getEntity(bossId);
        return entity instanceof AmemaruBoss boss && boss.isAlive() ? boss : null;
    }

    public static boolean isDown(ServerPlayer player) {
        return PlayerDataCapability.get(player).map(data -> data.down().isDown()).orElse(false);
    }

    // ---------------------------------------------------------------- 毎 tick（その階に人がいる間）

    public void tick(ServerLevel level, List<ServerPlayer> onFloor) {
        this.level = level;
        long now = level.getGameTime();
        switch (state) {
            case WAITING, VICTORY -> {
                if (state == State.VICTORY && now - stateSince > 20 * 5 && sealed) {
                    setSealed(level, false);
                }
                if (now >= respawnAt && anyoneNear(onFloor, 72.0)) {
                    spawnBoss(level);
                }
            }
            case IDLE -> {
                AmemaruBoss boss = boss();
                if (boss == null) {
                    // 区画が閉じてボスが消えた：また出す
                    state = State.WAITING;
                    respawnAt = now;
                    return;
                }
                if (sealed) {
                    setSealed(level, false);
                }
                boss.clearParticipants();
                // 部屋の真ん中から離れていたら戻す
                Vec3 spawn = floor.world(def.spawn().pos());
                if (boss.position().distanceToSqr(spawn) > 4.0 && boss.getNavigation().isDone()) {
                    boss.getNavigation().moveTo(spawn.x, spawn.y, spawn.z, 0.8);
                }
                if (shouldStart(boss, onFloor)) {
                    startFight(level, boss, onFloor);
                }
            }
            case FIGHT -> tickFight(level, onFloor, now);
        }
    }

    private boolean anyoneNear(List<ServerPlayer> players, double range) {
        Vec3 center = arenaCenter();
        for (ServerPlayer player : players) {
            if (player.position().distanceToSqr(center) < range * range) {
                return true;
            }
        }
        return false;
    }

    private void spawnBoss(ServerLevel level) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(def.boss());
        if (type == null) {
            AmemaruMod.LOGGER.error("[amemaru] Boss {} for floor {} is not registered", def.boss(), floor.number());
            respawnAt = level.getGameTime() + 20 * 30;
            return;
        }
        Entity entity = type.create(level);
        if (!(entity instanceof AmemaruBoss boss)) {
            AmemaruMod.LOGGER.error("[amemaru] {} is not a boss", def.boss());
            respawnAt = level.getGameTime() + 20 * 30;
            return;
        }
        Vec3 spawn = floor.world(def.spawn().pos());
        // 部屋の近くに、前に出したボスが残っていれば消す（増えないように）
        for (AmemaruBoss old : level.getEntitiesOfClass(AmemaruBoss.class, new net.minecraft.world.phys.AABB(spawn, spawn).inflate(80.0),
                other -> other.room() == this)) {
            AmemaruMod.LOGGER.info("[amemaru] Removed an old boss of floor {} before spawning a new one", floor.number());
            old.discard();
        }
        boss.moveTo(spawn.x, spawn.y, spawn.z, def.spawn().yaw(), 0.0F);
        boss.setYHeadRot(def.spawn().yaw());
        boss.setHome(BlockPos.containing(spawn));
        boss.setRoom(this);
        level.addFreshEntity(boss);
        bossId = boss.getUUID();
        participants.clear();
        setState(State.IDLE, level);
        setSealed(level, false);
        level.sendParticles(ParticleTypes.END_ROD, spawn.x, spawn.y + 1.5, spawn.z, 60, 1.2, 1.2, 1.2, 0.05);
    }

    private boolean shouldStart(AmemaruBoss boss, List<ServerPlayer> onFloor) {
        if (!boss.contributions().isEmpty()) {
            return true;
        }
        for (ServerPlayer player : onFloor) {
            if (eligible(player) && inside(player.position()) && player.distanceTo(boss) <= def.startRadius()) {
                return true;
            }
        }
        return false;
    }

    private static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    private void startFight(ServerLevel level, AmemaruBoss boss, List<ServerPlayer> onFloor) {
        reviveTipShown = false;
        participants.clear();
        List<ServerPlayer> inside = new ArrayList<>();
        for (ServerPlayer player : onFloor) {
            if (eligible(player) && inside(player.position())) {
                participants.add(player.getUUID());
                inside.add(player);
            }
        }
        if (inside.isEmpty()) {
            boss.resetFight();
            return;
        }
        double multiplier = 1.0 + def.hpPerExtraPlayer() * Math.max(0, inside.size() - 1);
        boss.setHpMultiplier(multiplier);
        boss.setEnemyHp(boss.getEnemyMaxHp());
        setState(State.FIGHT, level);
        setSealed(level, true);
        boss.setParticipants(inside);
        for (ServerPlayer player : inside) {
            player.sendSystemMessage(Component.translatable("message.amemaru.boss.start", Component.literal(bossName(boss)), inside.size())
                    .withStyle(ChatFormatting.RED));
        }
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.6F);
        boss.onFightStarted(level, inside);
        AmemaruMod.LOGGER.info("[amemaru] Boss fight started on floor {} ({} player(s), hp x{})", floor.number(), inside.size(), multiplier);
    }

    private void tickFight(ServerLevel level, List<ServerPlayer> onFloor, long now) {
        AmemaruBoss boss = boss();
        if (boss == null) {
            // 倒した知らせが来ずにボスが消えた（区画が閉じた・管理者が消した）：はじめから
            wipe(level, null);
            return;
        }
        // 部屋からいなくなった人（教会へ戻った・抜けた・ほかの階へ）を外す。技で門の通路まで押し出された人は、まだ部屋にいる
        List<ServerPlayer> present = new ArrayList<>();
        for (UUID id : new ArrayList<>(participants)) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null || player.level() != level || !insideFight(player.position()) || !eligible(player)) {
                participants.remove(id);
            } else {
                present.add(player);
            }
        }
        boss.setParticipants(present);
        // 蘇生のチュートリアル：はじめて誰かが倒れたら、ほかの人に起こし方を出す（Phase 0-A 9章「ヘイト・回避・蘇生のチュートリアルを兼ねる」）
        if (!reviveTipShown) {
            for (ServerPlayer player : present) {
                if (isDown(player)) {
                    reviveTipShown = true;
                    String text = Component.translatable("boss.amemaru.tip.revive", player.getGameProfile().getName()).getString();
                    for (ServerPlayer other : present) {
                        if (other != player) {
                            ModNetwork.sendTo(other, new StoryTextS2C(StoryTextS2C.Kind.TIP, "", text, 120));
                        }
                    }
                    break;
                }
            }
        }
        boolean anyStanding = false;
        for (ServerPlayer player : present) {
            if (!isDown(player)) {
                anyStanding = true;
                break;
            }
        }
        if (present.isEmpty()) {
            wipe(level, boss);
            return;
        }
        if (!anyStanding && now - stateSince > 20 * 3) {
            // 全員がダウン：起こし合えない。ダウンの時間切れで教会へ戻るのを待つ（戻ったら上で外れる）
            return;
        }
        if (!sealed) {
            setSealed(level, true);
        }
        // 部屋の外へ押し出されたボスを戻す
        if (!inside(boss.position())) {
            Vec3 spawn = floor.world(def.spawn().pos());
            boss.teleportTo(spawn.x, spawn.y, spawn.z);
        }
    }

    /** 戦いをはじめからにする（参加者がみないなくなった）。 */
    private void wipe(ServerLevel level, @Nullable AmemaruBoss boss) {
        participants.clear();
        if (boss != null) {
            boss.resetFight();
            boss.setHpMultiplier(1.0);
            boss.setEnemyHp(boss.getEnemyMaxHp());
            boss.clearParticipants();
            Vec3 spawn = floor.world(def.spawn().pos());
            boss.teleportTo(spawn.x, spawn.y, spawn.z);
            setState(State.IDLE, level);
        } else {
            bossId = null;
            respawnAt = level.getGameTime() + 20 * 5;
            setState(State.WAITING, level);
        }
        setSealed(level, false);
        AmemaruMod.LOGGER.info("[amemaru] Boss fight on floor {} was reset", floor.number());
    }

    /** ボスが倒れた（{@link AmemaruBoss#die} から）。 */
    public void onBossDefeated(AmemaruBoss boss) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        // ボスはもう倒れている（isAlive が false）ので participants() は空になる。部屋が覚えている参加者から集める
        List<ServerPlayer> winners = new ArrayList<>();
        for (UUID id : participants) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                winners.add(player);
            }
        }
        if (winners.isEmpty()) {
            for (UUID id : boss.contributions().keySet()) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                if (player != null) {
                    winners.add(player);
                }
            }
        }
        long now = level.getGameTime();
        respawnAt = now + 20L * def.respawnSeconds();
        bossId = null;
        participants.clear();
        setState(State.VICTORY, level);
        String name = bossName(boss);
        List<String> names = new ArrayList<>();
        for (ServerPlayer player : winners) {
            names.add(player.getGameProfile().getName());
            PlayerData data = PlayerDataCapability.get(player).orElse(null);
            if (data != null) {
                data.getStory().addBossKill(floor.number());
                GuideService.refresh(player, data);
            }
            ModNetwork.sendTo(player, new StoryTextS2C(StoryTextS2C.Kind.TITLE, Component.translatable("message.amemaru.boss.defeated_sub").getString(),
                    name, 80));
            player.sendSystemMessage(Component.translatable("message.amemaru.boss.defeated", Component.literal(name))
                    .withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.translatable("message.amemaru.boss.exit").withStyle(ChatFormatting.AQUA));
        }
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        ProgressionData progress = ProgressionData.get(level.getServer());
        if (names.isEmpty()) {
            // 戦った人がいない（コマンドで消した・部屋の外から倒れた）。初撃破は、倒した人がいるときだけ残して知らせる
            AmemaruMod.LOGGER.info("[amemaru] Boss of floor {} fell with no one taking part; no first kill recorded", floor.number());
            return;
        }
        boolean first = progress.recordFirstKill(floor.number(), System.currentTimeMillis(), names);
        if (first) {
            ProgressionService.broadcast(level.getServer(), Component.translatable("message.amemaru.boss.first_kill",
                    String.join("・", names), Component.literal(floor.title()), Component.literal(name)), AnnouncementS2C.Kind.BOSS);
            AmemaruMod.LOGGER.info("[amemaru] First kill of floor {} by {}", floor.number(), names);
            StoryEventManager.trigger(level.getServer(), "floor_first_kill", floor.number(), winners);
        }
        // Phase 6 その3：クエスト・称号・冒険日誌（初撃破なら限定称号「最初の一歩」など）
        jp.amemaru.lostconnection.guild.GuildHooks.onBossDefeated(winners, floor.number(),
                boss.def() == null ? AmemaruMod.id("unknown") : boss.def().id(), name, floor.title(),
                first);
    }

    private static String bossName(AmemaruBoss boss) {
        return boss.def() == null ? boss.getName().getString() : boss.def().name();
    }

    private void setState(State next, ServerLevel level) {
        state = next;
        stateSince = level.getGameTime();
    }

    /** 封印を閉じる・開く。 */
    private void setSealed(ServerLevel level, boolean value) {
        sealed = value;
        BlockPos min = floor.world(def.sealMin());
        BlockPos max = floor.world(def.sealMax());
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FloorSealBlock && state.getValue(FloorSealBlock.SEALED) != value) {
                level.setBlock(pos, state.setValue(FloorSealBlock.SEALED, value), Block.UPDATE_ALL);
            }
        }
    }

    /** 出口の円が使えるか（戦いの間は使えない）。 */
    public boolean exitOpen() {
        return state != State.FIGHT;
    }

    /** 管理者：ボスを出し直す（{@code /amemaru admin boss respawn <階>}）。 */
    public void forceRespawn(ServerLevel level) {
        AmemaruBoss boss = boss();
        if (boss != null) {
            boss.discard();
        }
        bossId = null;
        participants.clear();
        respawnAt = level.getGameTime();
        setState(State.WAITING, level);
        setSealed(level, false);
        this.level = level;
    }

    /** 階を建て直したとき：何もいない状態に戻す。 */
    public void reset(ServerLevel level) {
        forceRespawn(level);
    }

    /** 管理者の確かめ用：様子の一行。 */
    public String describe() {
        AmemaruBoss boss = boss();
        return state + (boss != null ? " hp=" + Math.round(boss.getEnemyHp()) + "/" + Math.round(boss.getEnemyMaxHp()) + " phase=" + boss.phase() : "")
                + " participants=" + participants.size() + " sealed=" + sealed;
    }
}

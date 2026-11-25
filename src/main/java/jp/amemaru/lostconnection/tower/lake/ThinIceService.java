package jp.amemaru.lostconnection.tower.lake;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.combat.PlayerColdService;
import jp.amemaru.lostconnection.core.registry.ModBlocks;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 8階「沈黙の湖」の氷（11月25日。Phase 0-A 9章の8階「氷の割れ目（同じ場所に集まりすぎると割れる）」）【補完】。
 *
 * <ul>
 *   <li>薄氷の上で、{@link #CROWD_RANGE} ブロックのうちに {@link #CROWD} 人以上が乗っていると、足もとの薄氷にひびが入る
 *       （0.5秒ごとに1段。3段で、次にまだ集まっていれば割れる）。ひとりなら割れない。離れれば、ひびは少しずつ消える</li>
 *   <li>割れた所（と、無音の大魚・ボスが割った所）は、しばらく水の穴になる。穴は {@link #HOLE_SECONDS} 秒で凍り直す
 *       （穴の中に誰かがいる間は凍らない）。サーバーが止まっても、次に起きたときに戻す</li>
 *   <li>水に落ちた人は、{@code lake.rescue_seconds} 秒で、最後に立っていた所へ引き上げられる（凍えが2つ）。氷の下に閉じこめられない</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class ThinIceService {
    public static final double CROWD_RANGE = 3.0;
    public static final int CROWD = 2;
    public static final int HOLE_SECONDS = 30;

    private static final Map<BlockPos, Long> CROWDED = new HashMap<>();
    private static final Map<UUID, Vec3> SAFE = new HashMap<>();
    private static final Map<UUID, Integer> WET = new HashMap<>();

    private ThinIceService() {
    }

    // ================================================================ 氷の穴（保存。凍り直すまで）

    private record Hole(BlockState ice, BlockState above, long restoreAt) {
    }

    public static final class Data extends SavedData {
        private static final String NAME = "amemaru_lake_holes";
        private final Map<BlockPos, Hole> holes = new LinkedHashMap<>();

        public static Data load(CompoundTag tag, MinecraftServer server) {
            Data data = new Data();
            var blocks = server.overworld().holderLookup(Registries.BLOCK);
            for (Tag t : tag.getList("holes", Tag.TAG_COMPOUND)) {
                CompoundTag h = (CompoundTag) t;
                data.holes.put(NbtUtils.readBlockPos(h.getCompound("pos")), new Hole(NbtUtils.readBlockState(blocks, h.getCompound("ice")),
                        NbtUtils.readBlockState(blocks, h.getCompound("above")), h.getLong("at")));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            holes.forEach((pos, hole) -> {
                CompoundTag h = new CompoundTag();
                h.put("pos", NbtUtils.writeBlockPos(pos));
                h.put("ice", NbtUtils.writeBlockState(hole.ice()));
                h.put("above", NbtUtils.writeBlockState(hole.above()));
                h.putLong("at", hole.restoreAt());
                list.add(h);
            });
            tag.put("holes", list);
            return tag;
        }

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(t -> load(t, server), Data::new, NAME);
        }

        public int size() {
            return holes.size();
        }
    }

    /** 今ある氷の穴の数（自動テスト用）。 */
    public static int holes(MinecraftServer server) {
        return Data.get(server).size();
    }

    /** 割れて、穴にできる氷か（湖の氷・薄氷）。 */
    public static boolean breakable(BlockState state) {
        return state.is(ModBlocks.LAKE_ICE.get()) || state.is(ModBlocks.THIN_ICE.get());
    }

    public static boolean thin(BlockState state) {
        return state.is(ModBlocks.THIN_ICE.get());
    }

    /**
     * 氷を割って、しばらく水の穴にする（無音の大魚・ボス・集まりすぎた薄氷）。{@code center} の高さの氷だけ。割った数。
     * {@code ticks} で凍り直す（中に誰かいる間は、待つ）。
     */
    public static int breakIce(ServerLevel level, Vec3 center, double radius, int ticks) {
        Data data = Data.get(level.getServer());
        long at = level.getGameTime() + Math.max(20, ticks);
        int y = (int) Math.floor(center.y);
        int r = (int) Math.ceil(radius);
        int n = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                BlockPos pos = BlockPos.containing(center.x + dx, y, center.z + dz);
                if (!level.isLoaded(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                Hole old = data.holes.get(pos);
                if (old != null) {
                    data.holes.put(pos, new Hole(old.ice(), old.above(), Math.max(old.restoreAt(), at)));
                    continue;
                }
                if (!breakable(state)) {
                    continue;
                }
                BlockState above = level.getBlockState(pos.above());
                BlockState keep = above.getBlock() instanceof SnowLayerBlock ? above : Blocks.AIR.defaultBlockState();
                data.holes.put(pos.immutable(), new Hole(thin(state) ? state.setValue(LakeBlocks.CRACK, 0) : state, keep, at));
                if (!keep.isAir()) {
                    level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
                }
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
                CROWDED.remove(pos);
                n++;
            }
        }
        if (n > 0) {
            data.setDirty();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), center.x, y + 1.0, center.z,
                    30 + n * 3, radius * 0.5, 0.3, radius * 0.5, 0.2);
            level.sendParticles(ParticleTypes.SPLASH, center.x, y + 1.0, center.z, 40, radius * 0.5, 0.2, radius * 0.5, 0.1);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.6F, 0.6F);
        }
        return n;
    }

    /** まわりの薄氷にひびを入れる（ボスの尾打ちなど）。3段をこえる所は割る。 */
    public static void crackAround(ServerLevel level, Vec3 center, double radius, int amount) {
        int y = (int) Math.floor(center.y);
        int r = (int) Math.ceil(radius);
        List<BlockPos> burst = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                BlockPos pos = BlockPos.containing(center.x + dx, y, center.z + dz);
                BlockState state = level.getBlockState(pos);
                if (!thin(state)) {
                    continue;
                }
                int crack = state.getValue(LakeBlocks.CRACK) + amount;
                if (crack > 3) {
                    burst.add(pos.immutable());
                } else {
                    level.setBlock(pos, state.setValue(LakeBlocks.CRACK, crack), 3);
                    CROWDED.put(pos.immutable(), level.getGameTime());
                }
            }
        }
        for (BlockPos pos : burst) {
            breakIce(level, Vec3.atCenterOf(pos).add(0, -0.5, 0), 0.5, HOLE_SECONDS * 20);
        }
    }

    /** 凍り直す時が来た穴を、もとの氷に戻す（中に生き物がいる間は待つ）。{@code force} なら、すぐにすべて戻す。 */
    public static void refreeze(ServerLevel level, boolean force) {
        Data data = Data.get(level.getServer());
        long now = level.getGameTime();
        boolean changed = false;
        Iterator<Map.Entry<BlockPos, Hole>> it = data.holes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Hole> e = it.next();
            BlockPos pos = e.getKey();
            Hole hole = e.getValue();
            if (!level.isLoaded(pos) || (!force && now < hole.restoreAt())) {
                continue;
            }
            AABB column = new AABB(pos.getX(), pos.getY() - 3, pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
            if (!force && !level.getEntitiesOfClass(LivingEntity.class, column).isEmpty()) {
                continue;
            }
            BlockState now0 = level.getBlockState(pos);
            if (now0.is(Blocks.WATER) || now0.isAir()) {
                level.setBlock(pos, hole.ice(), 3);
                if (!hole.above().isAir() && level.getBlockState(pos.above()).isAir()) {
                    level.setBlock(pos.above(), hole.above(), 3);
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 4, 0.3, 0.05, 0.3, 0.0);
            }
            it.remove();
            changed = true;
        }
        if (changed) {
            data.setDirty();
        }
    }

    // ================================================================ 毎 tick：集まりすぎた薄氷・ひびの戻り・水に落ちた人

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick % 10 != 5) {
            return;
        }
        ServerLevel tower = FloorDefs.towerLevel(server);
        if (tower == null) {
            return;
        }
        long now = tower.getGameTime();
        if (tick % 20 == 5) {
            refreeze(tower, false);
        }
        Map<Integer, List<ServerPlayer>> byFloor = new HashMap<>();
        for (ServerPlayer player : tower.players()) {
            FloorDef def = FloorDefs.floorAt(tower, player.position());
            if (def != null && def.features().lake() != null) {
                byFloor.computeIfAbsent(def.number(), k -> new ArrayList<>()).add(player);
            }
        }
        for (Map.Entry<Integer, List<ServerPlayer>> entry : byFloor.entrySet()) {
            FloorDef def = FloorDefs.byNumber(entry.getKey());
            List<ServerPlayer> players = entry.getValue();
            tickCrowd(tower, players, now);
            for (ServerPlayer player : players) {
                tickRescue(player, def.features().lake(), def);
            }
        }
        healCracks(tower, now);
    }

    /** 薄氷の上の人どうしが集まっていれば、足もとにひびを入れ、3段の所はさらに集まっていれば割る（自動テストからも）。 */
    public static void tickCrowd(ServerLevel level, List<ServerPlayer> players, long now) {
        List<ServerPlayer> onThin = new ArrayList<>();
        for (ServerPlayer p : players) {
            if (p.isAlive() && !p.isSpectator() && thinUnder(level, p) != null) {
                onThin.add(p);
            }
        }
        Set<BlockPos> touched = new HashSet<>();
        for (ServerPlayer p : onThin) {
            int near = 0;
            for (ServerPlayer q : onThin) {
                double dx = q.getX() - p.getX();
                double dz = q.getZ() - p.getZ();
                if (dx * dx + dz * dz <= CROWD_RANGE * CROWD_RANGE && Math.abs(q.getY() - p.getY()) < 1.5) {
                    near++;
                }
            }
            if (near < CROWD) {
                continue;
            }
            // 同じ tick のうちに、先の人の所で割れて、足もとがもう水になっていることがある
            BlockPos under = thinUnder(level, p);
            if (under == null) {
                continue;
            }
            boolean burst = false;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dz * dz > 5) {
                        continue;
                    }
                    BlockPos pos = under.offset(dx, 0, dz);
                    if (!touched.add(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (!thin(state)) {
                        continue;
                    }
                    int crack = state.getValue(LakeBlocks.CRACK);
                    CROWDED.put(pos.immutable(), now);
                    if (crack >= 3) {
                        burst = true;
                    } else {
                        level.setBlock(pos, state.setValue(LakeBlocks.CRACK, crack + 1), 3);
                    }
                }
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), p.getX(), under.getY() + 1.02,
                    p.getZ(), 6, 0.8, 0.0, 0.8, 0.0);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 0.8F, 0.6F);
            if (burst) {
                breakIce(level, new Vec3(p.getX(), under.getY() + 0.5, p.getZ()), 2.3, HOLE_SECONDS * 20);
                for (ServerPlayer q : onThin) {
                    if (q.distanceToSqr(p) < 16.0) {
                        q.displayClientMessage(Component.translatable("message.amemaru.lake.ice_broke").withStyle(ChatFormatting.AQUA), true);
                    }
                }
            } else {
                p.displayClientMessage(Component.translatable("message.amemaru.lake.ice_cracking").withStyle(ChatFormatting.YELLOW), true);
            }
        }
    }

    /** 足もとの薄氷（なければ null）。 */
    @Nullable
    public static BlockPos thinUnder(ServerLevel level, ServerPlayer player) {
        BlockPos pos = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
        return thin(level.getBlockState(pos)) ? pos : null;
    }

    private static void healCracks(ServerLevel level, long now) {
        Iterator<Map.Entry<BlockPos, Long>> it = CROWDED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> e = it.next();
            if (now - e.getValue() < 60) {
                continue;
            }
            BlockPos pos = e.getKey();
            if (!level.isLoaded(pos)) {
                it.remove();
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!thin(state) || state.getValue(LakeBlocks.CRACK) == 0) {
                it.remove();
                continue;
            }
            if ((now / 10) % 4 == 0) {
                int crack = state.getValue(LakeBlocks.CRACK) - 1;
                level.setBlock(pos, state.setValue(LakeBlocks.CRACK, crack), 3);
                if (crack == 0) {
                    it.remove();
                }
            }
        }
    }

    /** 水に落ちた人を、しばらくしたら最後に立っていた所へ引き上げる（氷の下に閉じこめられない）。 */
    private static void tickRescue(ServerPlayer player, FloorFeatures.Lake lake, FloorDef def) {
        rescue(player, lake, def, player.isInWater());
    }

    /**
     * 0.5秒ごとの見張り（自動テストからも。水の中かを {@code inWater} で渡す）：水の外で地面に立っていれば、そこを覚える。水の中に
     * {@code lake.rescue_seconds} 秒いたら、覚えた所（なければ階の入口）へ引き上げる。引き上げたら true。
     */
    public static boolean rescue(ServerPlayer player, FloorFeatures.Lake lake, FloorDef def, boolean inWater) {
        UUID id = player.getUUID();
        if (player.isSpectator() || player.isCreative()) {
            WET.remove(id);
            return false;
        }
        if (!inWater) {
            WET.remove(id);
            if (player.onGround() && thinUnder(player.serverLevel(), player) == null) {
                SAFE.put(id, player.position());
            }
            return false;
        }
        int wet = WET.getOrDefault(id, 0) + 10;
        WET.put(id, wet);
        if (wet < lake.rescueSeconds() * 20) {
            return false;
        }
        WET.remove(id);
        Vec3 back = SAFE.get(id);
        if (back == null) {
            back = def.world(def.entrance().pos());
        }
        player.teleportTo(back.x, back.y + 0.1, back.z);
        player.resetFallDistance();
        PlayerColdService.chill(player, 2, 100);
        player.displayClientMessage(Component.translatable("message.amemaru.lake.rescued").withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    /** 最後に立っていた所（自動テスト用。なければ null）。 */
    @Nullable
    public static Vec3 safeSpot(ServerPlayer player) {
        return SAFE.get(player.getUUID());
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SAFE.remove(event.getEntity().getUUID());
        WET.remove(event.getEntity().getUUID());
    }
}

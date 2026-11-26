package jp.amemaru.lostconnection.tower.forest;

import jp.amemaru.lostconnection.party.PartyService;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 中継の灯（Phase 8 その1。11月23日の決定「後半はかなりの時間がかかるようにしてもよい」への手当て【補完】）。
 *
 * <ul>
 *   <li>広い階の途中の「中継の灯」に触れると、その人の灯になる（近くの同じパーティの人も）。進行日が変わっても消えない</li>
 *   <li>入口の「道しるべの石」に触れると、その階で自分がともした中継の灯のうち、いちばん先の灯へ行ける。帰還の羽根で帰っても、
 *       進み具合は消えない</li>
 * </ul>
 */
public final class WaypointService {
    public static final double PARTY_RANGE = 24.0;

    private WaypointService() {
    }

    /** ともした中継の灯（{@code amemaru_waypoints.dat}。人ごとに「階:番号」）。 */
    public static final class Data extends SavedData {
        private static final String NAME = "amemaru_waypoints";
        private final Map<UUID, Set<String>> lit = new HashMap<>();

        public static Data load(CompoundTag tag) {
            Data data = new Data();
            CompoundTag people = tag.getCompound("lit");
            for (String key : people.getAllKeys()) {
                Set<String> set = new LinkedHashSet<>();
                for (Tag t : people.getList(key, Tag.TAG_STRING)) {
                    set.add(t.getAsString());
                }
                try {
                    data.lit.put(UUID.fromString(key), set);
                } catch (IllegalArgumentException ignored) {
                    // 壊れた行は読み飛ばす
                }
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            CompoundTag people = new CompoundTag();
            for (Map.Entry<UUID, Set<String>> entry : lit.entrySet()) {
                ListTag list = new ListTag();
                for (String key : entry.getValue()) {
                    list.add(StringTag.valueOf(key));
                }
                people.put(entry.getKey().toString(), list);
            }
            tag.put("lit", people);
            return tag;
        }

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, NAME);
        }

        public boolean has(UUID who, int floor, int index) {
            Set<String> set = lit.get(who);
            return set != null && set.contains(floor + ":" + index);
        }

        public boolean add(UUID who, int floor, int index) {
            boolean added = lit.computeIfAbsent(who, k -> new LinkedHashSet<>()).add(floor + ":" + index);
            if (added) {
                setDirty();
            }
            return added;
        }

        public void clear(UUID who) {
            if (lit.remove(who) != null) {
                setDirty();
            }
        }
    }

    /** 中継の灯に触れる。 */
    public static void light(ServerPlayer player, int floor, int index, BlockPos pos) {
        FloorDef def = FloorDefs.byNumber(floor);
        if (def == null || index < 0 || index >= def.features().waypoints().size()) {
            return;
        }
        FloorFeatures.Waypoint point = def.features().waypoints().get(index);
        Data data = Data.get(player.server);
        boolean first = data.add(player.getUUID(), floor, index);
        // 近くの同じパーティの人にも
        List<ServerPlayer> mates = new ArrayList<>();
        for (ServerPlayer mate : PartyService.onlineMembers(player)) {
            if (mate != player && mate.level() == player.level() && mate.distanceTo(player) <= PARTY_RANGE
                    && data.add(mate.getUUID(), floor, index)) {
                mates.add(mate);
            }
        }
        ServerLevel level = player.serverLevel();
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.3F);
        level.sendParticles(ParticleTypes.GLOW, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 20, 0.5, 0.6, 0.5, 0.05);
        player.displayClientMessage(Component.translatable(first ? "message.amemaru.waypoint.lit" : "message.amemaru.waypoint.already",
                point.name()).withStyle(ChatFormatting.AQUA), false);
        for (ServerPlayer mate : mates) {
            mate.displayClientMessage(Component.translatable("message.amemaru.waypoint.lit_by", player.getDisplayName(), point.name())
                    .withStyle(ChatFormatting.AQUA), false);
        }
    }

    /** 入口の道しるべの石に触れる：いちばん先の、ともした中継の灯へ。 */
    public static void travel(ServerPlayer player, int floor, BlockPos pos) {
        FloorDef def = FloorDefs.byNumber(floor);
        FloorFeatures.Waypoint best = def == null ? null : furthest(player, def);
        if (best == null) {
            player.displayClientMessage(Component.translatable("message.amemaru.waypoint.none").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 at = def.originVec().add(best.arrive().pos());
        // 11月27日：バニラの光の粒とエンダーマンの音の代わりに、発つ所と着く所に魔法陣
        jp.amemaru.lostconnection.network.s2c.TravelFxS2C.depart(level, player.position());
        player.teleportTo(level, at.x, at.y, at.z, best.arrive().yaw(), 0.0F);
        jp.amemaru.lostconnection.network.s2c.TravelFxS2C.arrive(level, at);
        player.displayClientMessage(Component.translatable("message.amemaru.waypoint.travel", best.name()).withStyle(ChatFormatting.AQUA), true);
    }

    /**
     * 着く所が水の中か（Phase 8 その2：3階の沈んだ区画の灯は、水門が閉じて水が戻ると水の底になる。そこへは送らない）。
     */
    private static boolean submerged(@Nullable ServerLevel tower, FloorDef def, FloorFeatures.Waypoint point) {
        if (tower == null) {
            return false;
        }
        BlockPos head = BlockPos.containing(def.originVec().add(point.arrive().pos()).add(0.0, 1.2, 0.0));
        return tower.isLoaded(head) && tower.getFluidState(head).is(net.minecraft.tags.FluidTags.WATER);
    }

    /** その人がともした、いちばん先（番号のいちばん大きい）中継の灯（水の底になった灯はのぞく）。 */
    @Nullable
    public static FloorFeatures.Waypoint furthest(ServerPlayer player, FloorDef def) {
        Data data = Data.get(player.server);
        FloorFeatures.Waypoint best = null;
        ServerLevel tower = FloorDefs.towerLevel(player.server);
        for (FloorFeatures.Waypoint point : def.features().waypoints()) {
            if (data.has(player.getUUID(), def.number(), point.index()) && !submerged(tower, def, point)) {
                best = point;
            }
        }
        return best;
    }
}

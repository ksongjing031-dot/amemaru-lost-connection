package jp.amemaru.lostconnection.item;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 持ち歩く灯り（11月23日の直し。ろうそくと灯りの店の「手提げランタン」と「灯りの小瓶」）。
 *
 * <ul>
 *   <li>手提げランタン：手（どちらでも）に持っている間、頭の高さのマスに見えない灯り（明るさ14）を置き、動くと付け替える。
 *       持つのをやめる・ログアウトする・世界を移ると消す</li>
 *   <li>灯りの小瓶：使った所と、そのまわり4か所（3ブロック先）に、明るさ15の灯りを90秒置く</li>
 * </ul>
 *
 * <p>置くのは空気のマスだけ（水や物のある所には置かない）。消すときは、まだ見えない灯りのままのときだけ消す。
 * サーバーを止めるときは、置いた灯りを全部消す。街の直しの書き出し（{@link jp.amemaru.lostconnection.admin.CityEdits}）は、
 * 見えない灯りを直しに数えない。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class LightService {
    public static final int LANTERN_LEVEL = 14;
    public static final int VIAL_LEVEL = 15;
    public static final int VIAL_SECONDS = 90;

    private record Spot(ResourceKey<Level> level, BlockPos pos) {
    }

    private record Temporary(Spot spot, long until) {
    }

    private static final Map<UUID, Spot> LANTERNS = new HashMap<>();
    private static final List<Temporary> TEMPORARY = new ArrayList<>();

    private LightService() {
    }

    /** 手提げランタンを手に持っているか。 */
    public static boolean holdsLantern(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof HandLanternItem || player.getOffhandItem().getItem() instanceof HandLanternItem;
    }

    /** 灯りの小瓶：その場とまわりに、しばらく灯りを置く。置けた数。 */
    public static int placeVial(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos center = BlockPos.containing(player.getEyePosition());
        long until = level.getGameTime() + VIAL_SECONDS * 20L;
        int placed = 0;
        BlockPos[] spots = {center, center.offset(3, 0, 0), center.offset(-3, 0, 0), center.offset(0, 0, 3), center.offset(0, 0, -3)};
        for (BlockPos pos : spots) {
            BlockPos at = free(level, pos);
            if (at != null) {
                level.setBlock(at, light(VIAL_LEVEL), 3);
                TEMPORARY.add(new Temporary(new Spot(level.dimension(), at), until));
                placed++;
            }
        }
        return placed;
    }

    /** そのマスか、ひとつ下のマスが空気なら、そのマス（なければ null）。 */
    private static BlockPos free(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        if (level.getBlockState(pos).isAir()) {
            return pos;
        }
        BlockPos below = pos.below();
        return level.getBlockState(below).isAir() ? below : null;
    }

    private static BlockState light(int level) {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level);
    }

    private static void remove(MinecraftServer server, Spot spot) {
        ServerLevel level = server.getLevel(spot.level());
        if (level != null && level.isLoaded(spot.pos()) && level.getBlockState(spot.pos()).is(Blocks.LIGHT)) {
            level.setBlock(spot.pos(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 4 != 0) {
            return;
        }
        // 手提げランタン
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Spot old = LANTERNS.get(player.getUUID());
            if (!holdsLantern(player) || player.isSpectator()) {
                if (old != null) {
                    remove(server, old);
                    LANTERNS.remove(player.getUUID());
                }
                continue;
            }
            ServerLevel level = player.serverLevel();
            BlockPos want = BlockPos.containing(player.getEyePosition());
            if (old != null && old.level().equals(level.dimension()) && (old.pos().equals(want) || old.pos().equals(want.below()))
                    && level.getBlockState(old.pos()).is(Blocks.LIGHT)) {
                continue;   // 同じ所（頭のマスか、その下のマス）なら、そのまま
            }
            if (old != null) {
                remove(server, old);
                LANTERNS.remove(player.getUUID());
            }
            BlockPos at = free(level, want);
            if (at != null) {
                level.setBlock(at, light(LANTERN_LEVEL), 3);
                LANTERNS.put(player.getUUID(), new Spot(level.dimension(), at));
            }
        }
        // 灯りの小瓶
        if (!TEMPORARY.isEmpty()) {
            Iterator<Temporary> it = TEMPORARY.iterator();
            while (it.hasNext()) {
                Temporary t = it.next();
                ServerLevel level = server.getLevel(t.spot().level());
                if (level == null || level.getGameTime() >= t.until()) {
                    remove(server, t.spot());
                    it.remove();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Spot old = LANTERNS.remove(player.getUUID());
            if (old != null) {
                remove(player.server, old);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Spot spot : LANTERNS.values()) {
            remove(event.getServer(), spot);
        }
        for (Temporary t : TEMPORARY) {
            remove(event.getServer(), t.spot());
        }
        LANTERNS.clear();
        TEMPORARY.clear();
    }

    /** 自動テスト用：今置いている灯りの数（ランタンと小瓶）。 */
    public static int activeCount() {
        return LANTERNS.size() + TEMPORARY.size();
    }
}

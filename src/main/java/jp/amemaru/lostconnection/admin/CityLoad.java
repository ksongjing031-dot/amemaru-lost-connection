package jp.amemaru.lostconnection.admin;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * 街の範囲の区画と「もの」が読み込まれてから、建てる・書き出す（11月21日）。
 *
 * <p>区画はすぐ読み込めるが、「もの」（額縁・防具立て）は少し遅れて読み込まれる。読み込まれる前に建て直すと古い「もの」を消せずに
 * 重なり、書き出すと「もの」がなくなったように見える。そこで、範囲を読み込んだままにして、全部の区画の「もの」が読み込まれたら続ける
 * （遠くにいても使える。待つのは長くて30秒）。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class CityLoad {
    /** 範囲を読み込んだままにする札（30秒。建てたあとの光の計算の間も読み込んだままにする） */
    static final TicketType<ChunkPos> TICKET = TicketType.create("amemaru_city", Comparator.comparingLong(ChunkPos::toLong), 20 * 30);
    private static final int MAX_WAIT_TICKS = 20 * 30;
    private static final List<Job> JOBS = new ArrayList<>();

    private static final class Job {
        final ServerLevel level;
        final List<BlockPos> origins;
        final CommandSourceStack source;
        final Runnable run;
        int waited;

        Job(ServerLevel level, List<BlockPos> origins, CommandSourceStack source, Runnable run) {
            this.level = level;
            this.origins = origins;
            this.source = source;
            this.run = run;
        }
    }

    private CityLoad() {
    }

    /** origins（中央広場の真ん中）の街の範囲を読み込み、「もの」まで読み込まれていればすぐ、まだなら読み込まれてから run する */
    static void whenLoaded(CommandSourceStack source, ServerLevel level, List<BlockPos> origins, Runnable run) {
        for (BlockPos origin : origins) {
            hold(level, origin);
        }
        if (loaded(level, origins)) {
            run.run();
            return;
        }
        source.sendSuccess(() -> Component.translatable("commands.amemaru.city.loading"), false);
        JOBS.add(new Job(level, List.copyOf(origins), source, run));
    }

    /** 街の範囲を、しばらく読み込んだままにする */
    static void hold(ServerLevel level, BlockPos origin) {
        int x0 = origin.getX() + CityCommands.MIN_X;
        int x1 = origin.getX() + CityCommands.MAX_X;
        int z0 = origin.getZ() + CityCommands.MIN_Z;
        int z1 = origin.getZ() + CityCommands.MAX_Z;
        ChunkPos middle = new ChunkPos(new BlockPos((x0 + x1) / 2, origin.getY(), (z0 + z1) / 2));
        level.getChunkSource().addRegionTicket(TICKET, middle, Math.max((x1 - x0) / 32, (z1 - z0) / 32) + 3, middle);
    }

    private static boolean loaded(ServerLevel level, List<BlockPos> origins) {
        for (BlockPos origin : origins) {
            for (int cx = (origin.getX() + CityCommands.MIN_X) >> 4; cx <= (origin.getX() + CityCommands.MAX_X) >> 4; cx++) {
                for (int cz = (origin.getZ() + CityCommands.MIN_Z) >> 4; cz <= (origin.getZ() + CityCommands.MAX_Z) >> 4; cz++) {
                    if (!level.areEntitiesLoaded(ChunkPos.asLong(cx, cz))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty()) {
            return;
        }
        List<Job> ready = new ArrayList<>();
        for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
            Job job = it.next();
            if (loaded(job.level, job.origins)) {
                it.remove();
                ready.add(job);
            } else if (++job.waited > MAX_WAIT_TICKS) {
                it.remove();
                job.source.sendFailure(Component.translatable("commands.amemaru.city.load_timeout"));
            } else if (job.waited % 200 == 0) {
                for (BlockPos origin : job.origins) {
                    hold(job.level, origin);
                }
            }
        }
        for (Job job : ready) {
            try {
                job.run.run();
            } catch (RuntimeException e) {
                AmemaruMod.LOGGER.error("[amemaru] city command failed", e);
                job.source.sendFailure(Component.translatable("commands.amemaru.city.failed", String.valueOf(e.getMessage())));
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        JOBS.clear();
    }
}

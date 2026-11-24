package jp.amemaru.lostconnection.tower;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * 塔の階を建てる（{@code /amemaru admin map build floor <階>}。Phase 0-B 12章「マップの作り方」：構造物ファイル（48×48×48 まで）に分けて、
 * コマンドで決まった座標に建てる。何度でも同じ形に建て直せる）。
 *
 * <p>構造物は {@code data/amemaru/structures/map/floorN/p<東西の番号>_<南北の番号>.nbt}（階の定義の {@code structure} の後ろ）。
 * 範囲の区画と「もの」が読み込まれてから建てる（長くて30秒）。建て直すときは、範囲の中の「もの」（プレイヤーを除く）とブロックを
 * 片づけてから置く（構造物は空気を書かないので、ファイルが小さい）。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class FloorBuild {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("amemaru_floor", Comparator.comparingLong(ChunkPos::toLong), 20 * 30);
    private static final int MAX_WAIT_TICKS = 20 * 30;
    /** 片づける高さ（階の原点の下と、範囲の上）。 */
    private static final int CLEAR_BELOW = 8;
    private static final int CLEAR_ABOVE = 24;
    /** 建て終えた回数（CIの写真が、建て終わるのを待つのに使う。同じJVMの中のサーバー）。 */
    public static volatile int builds;

    private record Job(ServerLevel level, FloorDef def, CommandSourceStack source, int[] waited) {
    }

    private static final List<Job> JOBS = new ArrayList<>();

    private FloorBuild() {
    }

    /** 建てる（読み込まれていなければ、読み込まれてから）。 */
    public static int build(CommandSourceStack source, int floor) {
        FloorDef def = FloorDefs.byNumber(floor);
        if (def == null) {
            source.sendFailure(Component.translatable("commands.amemaru.map.no_floor", floor));
            return 0;
        }
        ServerLevel level = FloorDefs.towerLevel(source.getServer());
        if (level == null) {
            source.sendFailure(Component.translatable("commands.amemaru.map.no_tower"));
            return 0;
        }
        hold(level, def);
        if (loaded(level, def)) {
            place(level, def, source);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.amemaru.map.loading", def.title()), false);
            JOBS.add(new Job(level, def, source, new int[1]));
        }
        return 1;
    }

    /** その階を、読み込みを待って建てる順番待ちがあるか（自動テストが、待ちきれずに落ちた順番を出し直すのに使う）。 */
    public static boolean pending(int floor) {
        for (Job job : JOBS) {
            if (job.def().number() == floor) {
                return true;
            }
        }
        return false;
    }

    private static void hold(ServerLevel level, FloorDef def) {
        ChunkPos middle = new ChunkPos(new BlockPos(def.origin().getX() + def.sizeX() / 2, def.origin().getY(),
                def.origin().getZ() + def.sizeZ() / 2));
        int radius = Math.max(def.sizeX(), def.sizeZ()) / 32 + 3;
        level.getChunkSource().addRegionTicket(TICKET, middle, radius, middle);
    }

    private static boolean loaded(ServerLevel level, FloorDef def) {
        for (int cx = def.origin().getX() >> 4; cx <= (def.origin().getX() + def.sizeX() - 1) >> 4; cx++) {
            for (int cz = def.origin().getZ() >> 4; cz <= (def.origin().getZ() + def.sizeZ() - 1) >> 4; cz++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(cx, cz))) {
                    return false;
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
            if (loaded(job.level(), job.def())) {
                it.remove();
                ready.add(job);
            } else if (++job.waited()[0] > MAX_WAIT_TICKS) {
                it.remove();
                job.source().sendFailure(Component.translatable("commands.amemaru.city.load_timeout"));
            } else if (job.waited()[0] % 200 == 0) {
                hold(job.level(), job.def());
            }
        }
        for (Job job : ready) {
            try {
                place(job.level(), job.def(), job.source());
            } catch (RuntimeException e) {
                AmemaruMod.LOGGER.error("[amemaru] floor build failed", e);
                job.source().sendFailure(Component.translatable("commands.amemaru.city.failed", String.valueOf(e.getMessage())));
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        JOBS.clear();
    }

    private static void place(ServerLevel level, FloorDef def, CommandSourceStack source) {
        long started = System.currentTimeMillis();
        StructureTemplateManager manager = level.getStructureManager();
        BlockPos origin = def.origin();
        // 範囲の区画を読み込む
        for (int cx = origin.getX() >> 4; cx <= (origin.getX() + def.sizeX() - 1) >> 4; cx++) {
            for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + def.sizeZ() - 1) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        // 「もの」を消す（湧いた敵・ボス・落ちたアイテム。プレイヤーは残す）
        AABB box = new AABB(origin.getX(), origin.getY() - CLEAR_BELOW - 16, origin.getZ(), origin.getX() + def.sizeX(),
                origin.getY() + def.sizeY() + CLEAR_ABOVE + 16, origin.getZ() + def.sizeZ());
        for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            entity.discard();
        }
        FloorRuntime runtime = TowerService.runtime(def.number());
        if (runtime != null) {
            runtime.reset(level);
        }
        // 建て直すとき：ブロックを片づける
        int cleared = 0;
        if (TowerData.get(level.getServer()).isBuilt(def.number())) {
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int y0 = Math.max(level.getMinBuildHeight(), origin.getY() - CLEAR_BELOW);
            int y1 = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + def.sizeY() + CLEAR_ABOVE);
            for (int x = origin.getX(); x < origin.getX() + def.sizeX(); x++) {
                for (int z = origin.getZ(); z < origin.getZ() + def.sizeZ(); z++) {
                    for (int y = y0; y <= y1; y++) {
                        pos.set(x, y, z);
                        BlockState now = level.getBlockState(pos);
                        if (!now.isAir()) {
                            if (now.hasBlockEntity()) {
                                Clearable.tryClear(level.getBlockEntity(pos));
                            }
                            level.setBlock(pos, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                            cleared++;
                        }
                    }
                }
            }
        }
        // 構造物を置く
        StructurePlaceSettings settings = new StructurePlaceSettings();
        int pieces = 0;
        int missing = 0;
        for (int px = 0; px * def.pieceSize() < def.sizeX(); px++) {
            for (int pz = 0; pz * def.pieceSize() < def.sizeZ(); pz++) {
                ResourceLocation id = ResourceLocation.tryParse(def.structurePrefix() + "p" + px + "_" + pz);
                if (id == null) {
                    continue;
                }
                Optional<StructureTemplate> template = manager.get(id);
                if (template.isEmpty()) {
                    missing++;
                    continue;
                }
                BlockPos at = origin.offset(px * def.pieceSize(), 0, pz * def.pieceSize());
                template.get().placeInWorld(level, at, at, settings, level.getRandom(), Block.UPDATE_CLIENTS);
                pieces++;
            }
        }
        TowerData.get(level.getServer()).markBuilt(def.number());
        // Phase 8 その2：建て直すと水は満ちた形に戻るので、次に人が来たとき開いた水門の状態に合わせ直す
        jp.amemaru.lostconnection.tower.waterway.FloodgateService.forgetSync(level.getServer(), def.number());
        builds++;
        long millis = System.currentTimeMillis() - started;
        int placed = pieces;
        int lacking = missing;
        jp.amemaru.lostconnection.admin.AdminCommands.log(source, "map build floor " + def.number() + " (" + placed + " pieces, " + cleared
                + " cleared, " + millis + " ms)");
        source.sendSuccess(() -> Component.translatable("commands.amemaru.map.built", def.title(), placed, lacking), true);
        AmemaruMod.LOGGER.info("[amemaru] Built floor {}: {} piece(s), {} missing, {} cleared, {} ms", def.number(), placed, lacking, cleared,
                millis);
    }
}

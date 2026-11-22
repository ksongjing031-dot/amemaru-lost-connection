package jp.amemaru.lostconnection.social;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.tower.FloorDefs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * 決闘場（Phase 6 その4。11月22日に決定：「街の中では戦わず（街の中はPvP不可【確定】）、簡単な決闘場で戦う。ほかのプレイヤーは
 * 観戦できる」）。
 *
 * <p>塔の空間（{@code amemaru:tower}）の、どの階とも離れた所に、同じ形の決闘場を並べて建てる【補完】。一つは、
 * 19×19の戦う場（まわりに見えない壁と天井）と、そのまわりの3段の観客席（外側に壁）。戦う人は東と西の印の上から始め、
 * 観客は北か南の観客席に立つ。初めて使うときに建てる（サーバーを起動し直したら、もう一度建て直す）。</p>
 */
public final class DuelArena {
    /** 1つ目の決闘場の中心の床（塔の空間。どの階からも遠い所）。 */
    public static final BlockPos ORIGIN = new BlockPos(-6000, 100, -6000);
    /** 決闘場どうしの間隔（東へ並べる）。 */
    public static final int SPACING = 64;
    /** 戦う場の半分の幅（-RING〜RING）。 */
    public static final int RING = 9;
    /** 観客席の外側の壁（-OUTER〜OUTER）。 */
    public static final int OUTER = 17;
    /** 戦う場から出たとみなす距離（中心から）。 */
    public static final double LEAVE_DISTANCE = 14.0;

    private static final Set<Integer> BUILT = new HashSet<>();

    private DuelArena() {
    }

    public static BlockPos center(int index) {
        return ORIGIN.offset(index * SPACING, 0, 0);
    }

    public static ServerLevel level(MinecraftServer server) {
        ServerLevel tower = server.getLevel(FloorDefs.TOWER);
        return tower == null ? server.overworld() : tower;
    }

    /** 戦う人の立つ所（0：西、1：東）と向き。 */
    public static Vec3 fighterSpot(int index, int side) {
        BlockPos c = center(index);
        return new Vec3(c.getX() + 0.5 + (side == 0 ? -6 : 6), c.getY() + 1.0, c.getZ() + 0.5);
    }

    public static float fighterYaw(int side) {
        return side == 0 ? -90.0F : 90.0F;
    }

    /** 観客の立つ所（北か南の観客席の2段目。何人目かで少しずらす）。 */
    public static Vec3 spectatorSpot(int index, int number) {
        BlockPos c = center(index);
        boolean north = number % 2 == 0;
        int offset = ((number / 2) % 7 - 3) * 2;
        return new Vec3(c.getX() + 0.5 + offset, c.getY() + 3.0, c.getZ() + 0.5 + (north ? -14 : 14));
    }

    public static float spectatorYaw(int number) {
        return number % 2 == 0 ? 0.0F : 180.0F;
    }

    /** 戦う場の中か（出たら負け）。 */
    public static boolean insideRing(int index, Vec3 pos) {
        BlockPos c = center(index);
        double dx = pos.x - (c.getX() + 0.5);
        double dz = pos.z - (c.getZ() + 0.5);
        return Math.abs(dx) <= LEAVE_DISTANCE && Math.abs(dz) <= LEAVE_DISTANCE && pos.y > c.getY() - 3 && pos.y < c.getY() + 20;
    }

    /** まだ建てていなければ建てる。 */
    public static void ensureBuilt(MinecraftServer server, int index) {
        if (BUILT.contains(index)) {
            return;
        }
        build(level(server), center(index));
        BUILT.add(index);
        AmemaruMod.LOGGER.info("[amemaru][duel] Built arena {} at {}", index, center(index));
    }

    public static void forgetBuilt() {
        BUILT.clear();
    }

    /** 決闘場を建てる（同じ所に建て直しても同じ形になる）。 */
    static void build(ServerLevel level, BlockPos c) {
        // チャンクを読み込む（何もない空間なので、すぐにできる）
        for (int cx = (c.getX() - OUTER - 1) >> 4; cx <= (c.getX() + OUTER + 1) >> 4; cx++) {
            for (int cz = (c.getZ() - OUTER - 1) >> 4; cz <= (c.getZ() + OUTER + 1) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        int flags = Block.UPDATE_CLIENTS;
        for (int x = -OUTER; x <= OUTER; x++) {
            for (int z = -OUTER; z <= OUTER; z++) {
                int ring = Math.max(Math.abs(x), Math.abs(z));
                // 空気で片づける（建て直しのため）
                for (int y = 1; y <= 10; y++) {
                    level.setBlock(c.offset(x, y, z), Blocks.AIR.defaultBlockState(), flags);
                }
                level.setBlock(c.offset(x, -1, z), Blocks.DEEPSLATE_TILES.defaultBlockState(), flags);
                BlockState floor;
                if (ring <= RING) {
                    // 戦う場：滑らかな石。真ん中は模様、端は暗い縁取り
                    if (ring == 0) {
                        floor = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
                    } else if (ring <= 2) {
                        floor = Blocks.POLISHED_ANDESITE.defaultBlockState();
                    } else if (ring == RING) {
                        floor = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                    } else if ((x + z) % 6 == 0 && Math.abs(x) % 6 == 0) {
                        floor = Blocks.SEA_LANTERN.defaultBlockState();
                    } else {
                        floor = Blocks.SMOOTH_STONE.defaultBlockState();
                    }
                } else {
                    floor = Blocks.STONE_BRICKS.defaultBlockState();
                }
                level.setBlock(c.offset(x, 0, z), floor, flags);
                if (ring == RING + 1) {
                    // 戦う場のふち：低い壁と、その上の見えない壁
                    level.setBlock(c.offset(x, 1, z), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), flags);
                    for (int y = 2; y <= 8; y++) {
                        level.setBlock(c.offset(x, y, z), Blocks.BARRIER.defaultBlockState(), flags);
                    }
                } else if (ring <= RING) {
                    // 戦う場の天井（見えない）
                    level.setBlock(c.offset(x, 9, z), Blocks.BARRIER.defaultBlockState(), flags);
                } else if (ring >= 12 && ring <= 16) {
                    // 観客席：3段（外ほど高い）
                    int height = ring <= 13 ? 1 : ring <= 15 ? 2 : 3;
                    for (int y = 1; y < height; y++) {
                        level.setBlock(c.offset(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), flags);
                    }
                    boolean seat = (ring == 13 || ring == 15) && Math.abs(x) != Math.abs(z);
                    if (seat) {
                        Direction facing = Math.abs(x) > Math.abs(z) ? (x > 0 ? Direction.EAST : Direction.WEST)
                                : (z > 0 ? Direction.SOUTH : Direction.NORTH);
                        level.setBlock(c.offset(x, height, z), Blocks.SPRUCE_STAIRS.defaultBlockState()
                                .setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM), flags);
                    } else {
                        level.setBlock(c.offset(x, height, z), Blocks.STONE_BRICK_SLAB.defaultBlockState(), flags);
                        if (height > 1) {
                            level.setBlock(c.offset(x, height - 1, z), Blocks.STONE_BRICKS.defaultBlockState(), flags);
                        }
                    }
                } else if (ring == OUTER) {
                    // 外側の壁（落ちないように）と、見えない壁。ところどころに灯り
                    for (int y = 1; y <= 4; y++) {
                        level.setBlock(c.offset(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), flags);
                    }
                    boolean light = (Math.abs(x) % 4 == 0 && Math.abs(z) == OUTER) || (Math.abs(z) % 4 == 0 && Math.abs(x) == OUTER);
                    level.setBlock(c.offset(x, 5, z), light ? Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false)
                            : Blocks.STONE_BRICK_WALL.defaultBlockState(), flags);
                    for (int y = 6; y <= 9; y++) {
                        level.setBlock(c.offset(x, y, z), Blocks.BARRIER.defaultBlockState(), flags);
                    }
                }
            }
        }
        // 戦う人の印（西は青、東は赤）
        level.setBlock(c.offset(-6, 0, 0), Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState(), flags);
        level.setBlock(c.offset(6, 0, 0), Blocks.RED_GLAZED_TERRACOTTA.defaultBlockState(), flags);
        // 四すみの灯り
        for (int sx : new int[]{-(RING + 1), RING + 1}) {
            for (int sz : new int[]{-(RING + 1), RING + 1}) {
                level.setBlock(c.offset(sx, 1, sz), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), flags);
                level.setBlock(c.offset(sx, 2, sz), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), flags);
            }
        }
    }
}

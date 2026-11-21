package jp.amemaru.lostconnection.tower;

import jp.amemaru.lostconnection.admin.CityCommands;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * 塔の入口の広間の「塔の灯り」と札（Phase 0-B 10章「運営の決まり」：階層を解放すると、塔の入口の「塔の灯り」（広間の東の壁。
 * 章の色のろうそく4段×5本）で、その階のろうそくを灯す。予告をつけた解放は、横の「次にひらく階」の札にその階を出す）。
 *
 * <p>場所は {@code tools/mapgen/build_tower.py} の {@code hall_inside_things}：ろうそくは中央広場の真ん中から (13, 6 + 2 × 章, −167 + 章の中の番号)、
 * 「次にひらく階」の札は (13, 6, −162)、塔前広場の「塔の知らせ」の札は (12, 4, −131)。街を建てていなければ何もしない。</p>
 */
public final class TowerHall {
    private static final String[] COLORS = {"lime", "light_blue", "orange", "white"};

    private TowerHall() {
    }

    /** ろうそくと札を、今の解放と予告に合わせる。 */
    public static void update(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Optional<BlockPos> found = CityCommands.builtOrigin(level);
        if (found.isEmpty()) {
            return;
        }
        BlockPos origin = found.get();
        ProgressionData data = ProgressionData.get(server);
        for (int floor = 1; floor <= FloorDefs.FLOOR_COUNT; floor++) {
            int chapter = (floor - 1) / 5;
            int k = (floor - 1) % 5;
            BlockPos pos = origin.offset(13, 6 + 2 * chapter, -167 + k);
            level.getChunkAt(pos);
            BlockState state = level.getBlockState(pos);
            boolean lit = data.isUnlocked(floor);
            if (state.getBlock() instanceof CandleBlock && state.hasProperty(CandleBlock.LIT)) {
                if (state.getValue(CandleBlock.LIT) != lit) {
                    level.setBlock(pos, state.setValue(CandleBlock.LIT, lit), Block.UPDATE_ALL);
                }
            } else if (state.isAir()) {
                // 直したときに消えていたら、章の色のろうそくを置き直す
                Block candle = ForgeRegistries.BLOCKS.getValue(ResourceLocation.withDefaultNamespace(COLORS[chapter] + "_candle"));
                if (candle instanceof CandleBlock) {
                    level.setBlock(pos, candle.defaultBlockState().setValue(CandleBlock.LIT, lit), Block.UPDATE_ALL);
                }
            }
        }
        ProgressionData.Scheduled next = data.nextFloorUnlock();
        Component[] nextSign;
        if (next != null) {
            FloorDef def = FloorDefs.byNumber(next.value());
            nextSign = new Component[]{Component.translatable("sign.amemaru.tower.next"),
                    Component.translatable("message.amemaru.floor.number", next.value()),
                    Component.literal(def != null ? def.name() : ""), Component.translatable("sign.amemaru.tower.announced")};
        } else {
            nextSign = new Component[]{Component.translatable("sign.amemaru.tower.next"), Component.empty(),
                    Component.translatable("sign.amemaru.tower.none"), Component.empty()};
        }
        setSign(level, origin.offset(13, 6, -162), nextSign);
        int highest = 0;
        for (int floor : data.unlockedFloors()) {
            highest = Math.max(highest, floor);
        }
        setSign(level, origin.offset(12, 4, -131), new Component[]{
                Component.translatable("sign.amemaru.tower.board"),
                highest > 0 ? Component.translatable("sign.amemaru.tower.open_until", highest) : Component.translatable("sign.amemaru.tower.none"),
                Component.translatable("sign.amemaru.tower.next_short"),
                next != null ? Component.translatable("message.amemaru.floor.number", next.value()) : Component.literal("—")});
    }

    private static void setSign(ServerLevel level, BlockPos pos, Component[] lines) {
        level.getChunkAt(pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SignBlockEntity sign)) {
            return;
        }
        SignText text = sign.getFrontText();
        for (int i = 0; i < 4 && i < lines.length; i++) {
            text = text.setMessage(i, lines[i]);
        }
        sign.setText(text, true);
        sign.setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
    }
}

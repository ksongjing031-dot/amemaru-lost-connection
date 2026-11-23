package jp.amemaru.lostconnection.tower;

import jp.amemaru.lostconnection.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 封印が持つもの：どの階の封印か（構造物の設計データが書く。{@code {"floor": 1}}）。 */
public class FloorSealBlockEntity extends BlockEntity {
    private int floor = 1;

    public FloorSealBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLOOR_SEAL.get(), pos, state);
    }

    public int floor() {
        return floor;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        floor = Math.max(1, tag.getInt("floor"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("floor", floor);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

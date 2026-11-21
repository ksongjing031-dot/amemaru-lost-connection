package jp.amemaru.lostconnection.tower;

import jp.amemaru.lostconnection.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 石柱の核が持つもの：どの階の、何本目か（構造物の設計データが書く。{@code {"floor": 1, "index": 0}}）。 */
public class TowerPillarBlockEntity extends BlockEntity {
    private int floor = 1;
    private int index;

    public TowerPillarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOWER_PILLAR.get(), pos, state);
    }

    public int floor() {
        return floor;
    }

    public int index() {
        return index;
    }

    public void set(int floor, int index) {
        this.floor = floor;
        this.index = index;
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        floor = Math.max(1, tag.getInt("floor"));
        index = Math.max(0, tag.getInt("index"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("floor", floor);
        tag.putInt("index", index);
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

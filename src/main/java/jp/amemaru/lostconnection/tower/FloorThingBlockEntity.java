package jp.amemaru.lostconnection.tower;

import jp.amemaru.lostconnection.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 階の仕掛けのブロック（古代の仕掛け石・古代の宝箱）が持つもの：どの階の、何番目か。仕掛け石は組の名前も
 * （構造物の設計データが書く。{@code {"floor": 1, "index": 0, "group": "ruins"}}。11月22日）。
 */
public class FloorThingBlockEntity extends BlockEntity {
    private int floor = 1;
    private int index;
    private String group = "";

    public FloorThingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLOOR_THING.get(), pos, state);
    }

    public int floor() {
        return floor;
    }

    public int index() {
        return index;
    }

    public String group() {
        return group;
    }

    public void set(int floor, int index, String group) {
        this.floor = floor;
        this.index = index;
        this.group = group == null ? "" : group;
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        floor = Math.max(1, tag.getInt("floor"));
        index = Math.max(0, tag.getInt("index"));
        group = tag.getString("group");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("floor", floor);
        tag.putInt("index", index);
        tag.putString("group", group);
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

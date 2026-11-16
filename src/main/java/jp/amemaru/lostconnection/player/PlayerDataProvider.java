package jp.amemaru.lostconnection.player;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * プレイヤー1人分の {@link PlayerData} を持ち、保存と読み込みをForgeに任せるための入れ物。
 */
public final class PlayerDataProvider implements ICapabilitySerializable<CompoundTag> {
    private final PlayerData data = new PlayerData();
    private final LazyOptional<PlayerData> optional = LazyOptional.of(() -> data);

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        return capability == PlayerDataCapability.PLAYER_DATA ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.save();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        data.load(tag);
    }

    /** プレイヤーの体が消えたときに呼ばれる。 */
    public void invalidate() {
        optional.invalidate();
    }
}

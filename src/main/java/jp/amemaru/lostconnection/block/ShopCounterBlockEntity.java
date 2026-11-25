package jp.amemaru.lostconnection.block;

import jp.amemaru.lostconnection.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 店のカウンターの呼び鈴が持つもの：どの店か（{@code {"shop": "amemaru:general"}}。構造物の設計データが書く。Phase 6）。
 */
public class ShopCounterBlockEntity extends BlockEntity {
    @Nullable
    private ResourceLocation shop;

    public ShopCounterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHOP_COUNTER.get(), pos, state);
    }

    @Nullable
    public ResourceLocation shop() {
        return shop;
    }

    public void setShop(@Nullable ResourceLocation shop) {
        this.shop = shop;
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        shop = tag.contains("shop") ? ResourceLocation.tryParse(tag.getString("shop")) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (shop != null) {
            tag.putString("shop", shop.toString());
        }
    }
}

package jp.amemaru.lostconnection.loot;

import jp.amemaru.lostconnection.core.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * 敵のドロップ（Phase 0-A 24章「ドロップは各自が個別に抽選」）。拾えるのは持ち主だけで、ほかの人には見えない
 * （クライアントの描画で隠す）。レアドロップは、持ち主の画面にだけ、レアリティの色の光の柱が立つ。
 */
public class LootItemEntity extends ItemEntity {
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(LootItemEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** レアドロップのレアリティの番号（-1 はふつうのドロップ）。 */
    private static final EntityDataAccessor<Integer> DATA_RARE =
            SynchedEntityData.defineId(LootItemEntity.class, EntityDataSerializers.INT);

    public LootItemEntity(EntityType<? extends LootItemEntity> type, Level level) {
        super(type, level);
    }

    /** 持ち主だけが拾えるドロップを置く。 */
    public static LootItemEntity spawn(ServerLevel level, double x, double y, double z, ItemStack stack, UUID owner, int rareTier) {
        LootItemEntity entity = new LootItemEntity(ModEntities.LOOT_ITEM.get(), level);
        entity.setItem(stack);
        entity.setPos(x, y, z);
        entity.setDeltaMovement(level.random.nextDouble() * 0.2 - 0.1, 0.25, level.random.nextDouble() * 0.2 - 0.1);
        entity.setDefaultPickUpDelay();
        entity.setTarget(owner);
        entity.entityData.set(DATA_OWNER, Optional.of(owner));
        entity.entityData.set(DATA_RARE, rareTier);
        level.addFreshEntity(entity);
        return entity;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_OWNER, Optional.empty());
        entityData.define(DATA_RARE, -1);
    }

    /** 持ち主（なければ null）。 */
    @Nullable
    public UUID owner() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    /** レアドロップのレアリティの番号（-1 はふつう）。 */
    public int rareTier() {
        return entityData.get(DATA_RARE);
    }

    /** その人に見えるか（持ち主がいなければ、みんなに見える）。 */
    public boolean visibleTo(Player player) {
        UUID owner = owner();
        return owner == null || owner.equals(player.getUUID());
    }

    @Override
    public void playerTouch(Player player) {
        if (!visibleTo(player)) {
            return;
        }
        super.playerTouch(player);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID owner = owner();
        if (owner != null) {
            tag.putUUID("AmemaruOwner", owner);
        }
        tag.putInt("AmemaruRare", rareTier());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_OWNER, tag.hasUUID("AmemaruOwner") ? Optional.of(tag.getUUID("AmemaruOwner")) : Optional.empty());
        entityData.set(DATA_RARE, tag.contains("AmemaruRare") ? tag.getInt("AmemaruRare") : -1);
    }
}

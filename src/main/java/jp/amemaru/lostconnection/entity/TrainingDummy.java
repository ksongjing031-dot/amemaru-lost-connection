package jp.amemaru.lostconnection.entity;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.core.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 訓練人形（ギルドの訓練場に置く、倒れない的）【補完】。
 *
 * <p>独自のHP（初期値2,000）と防御を持ち、スキルと近接攻撃のダメージの数字・連携技・状態を確かめるのに使う。
 * HPが0になると、すぐに満タンに戻る。5秒攻撃されないと満タンに戻る。動かず、押されず、ほかの理由では傷つかない。
 * クリエイティブのプレイヤーがしゃがみながら殴ると、片付けられる（アイテムに戻る）。</p>
 */
public class TrainingDummy extends PathfinderMob {
    private static final EntityDataAccessor<Float> DUMMY_HP = SynchedEntityData.defineId(TrainingDummy.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DUMMY_MAX_HP = SynchedEntityData.defineId(TrainingDummy.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DUMMY_DEFENSE = SynchedEntityData.defineId(TrainingDummy.class, EntityDataSerializers.INT);
    private static final int REGEN_TICKS = 100;

    private long lastHitTick = Long.MIN_VALUE / 4;

    public TrainingDummy(EntityType<? extends TrainingDummy> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        float max = 2000.0F;
        entityData.define(DUMMY_HP, max);
        entityData.define(DUMMY_MAX_HP, max);
        entityData.define(DUMMY_DEFENSE, 0);
    }

    @Override
    protected void registerGoals() {
        // 動かない
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            float configured = (float) (int) ServerConfig.TRAINING_DUMMY_HP.get();
            if (getDummyMaxHp() != configured) {
                entityData.set(DUMMY_MAX_HP, configured);
                entityData.set(DUMMY_HP, configured);
            }
            if (getDummyHp() < getDummyMaxHp() && level().getGameTime() - lastHitTick >= REGEN_TICKS) {
                entityData.set(DUMMY_HP, getDummyMaxHp());
            }
        }
    }

    /** 独自のダメージを受ける。0以下になったら、すぐに満タンに戻る。 */
    public void applyCustomDamage(double amount) {
        lastHitTick = level().getGameTime();
        float next = (float) (getDummyHp() - amount);
        if (next <= 0) {
            next = getDummyMaxHp();
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 1.0, getZ(),
                        30, 0.3, 0.6, 0.3, 0.3);
            }
        }
        entityData.set(DUMMY_HP, next);
    }

    public float getDummyHp() {
        return entityData.get(DUMMY_HP);
    }

    public float getDummyMaxHp() {
        return entityData.get(DUMMY_MAX_HP);
    }

    public int getDummyDefense() {
        return entityData.get(DUMMY_DEFENSE);
    }

    public void setDummyDefense(int defense) {
        entityData.set(DUMMY_DEFENSE, Math.max(0, defense));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide() && source.getEntity() instanceof Player player && player.getAbilities().instabuild
                && player.isShiftKeyDown() && source.getDirectEntity() == player) {
            spawnAtLocation(new ItemStack(ModItems.TRAINING_DUMMY.get()));
            discard();
            return true;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // プレイヤーの攻撃とスキル、/kill のような「無敵を無視する」ダメージだけを受ける
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return !(source.getEntity() instanceof Player) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("AmemaruDefense", getDummyDefense());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setDummyDefense(tag.getInt("AmemaruDefense"));
    }
}

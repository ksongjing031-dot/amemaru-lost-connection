package jp.amemaru.lostconnection.enemy.boss;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 古樹の光る節（2階のボス「森番の古樹オールドウォード」の幹に生える弱点。Phase 0-A 9章「幹の光る節を壊すと怯む」）。
 * 動かず、ボスの幹にくっついて回る（場所はボスが毎 tick 決める）。3つとも壊すと、ボスが怯む（{@link OldWard#onKnotBroken}）。
 *
 * <p>11月25日：同じものを「芽吹きの種」にも使う（{@link #setSeed}）。古樹が空き地にまいた光る種で、地面に根づいたまま、
 * {@code grow} tick たつと若木に育って古樹を癒し、迷い灯を呼ぶ（{@link OldWard#onSeedMatured}）。育つ前に壊す。</p>
 */
public class OldWardKnot extends AmemaruMob {
    /** 芽吹きの種か（クライアントで、芽吹く粒を出すため）。 */
    private static final EntityDataAccessor<Boolean> DATA_SEED = SynchedEntityData.defineId(OldWardKnot.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private UUID owner;
    private int index;
    private int grow;

    public OldWardKnot(EntityType<? extends OldWardKnot> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    public String assetName() {
        return "old_ward_knot";
    }

    public void setOwner(OldWard boss, int index) {
        this.owner = boss.getUUID();
        this.index = index;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SEED, false);
    }

    /** 芽吹きの種にする（{@code growTicks} で若木に育つ）。 */
    public void setSeed(OldWard boss, int growTicks) {
        this.owner = boss.getUUID();
        this.index = -1;
        this.grow = Math.max(1, growTicks);
        entityData.set(DATA_SEED, true);
    }

    public boolean seed() {
        return entityData.get(DATA_SEED);
    }

    /** 若木に育つまでの残り（tick）。 */
    public int growTicks() {
        return grow;
    }

    /** 自動テスト用：育つまでの残りを変える。 */
    public void setGrowTicks(int ticks) {
        grow = Math.max(1, ticks);
    }

    public int index() {
        return index;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    protected boolean usesLeash() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if (level() instanceof ServerLevel level) {
            Entity boss = owner == null ? null : level.getEntity(owner);
            if (!(boss instanceof OldWard ward) || !ward.isAlive()) {
                discard();
            } else if (seed() && isAlive() && --grow <= 0) {
                ward.onSeedMatured(this);
                discard();
            }
        } else if (isAlive() && seed()) {
            // 芽吹きの種：光の柱（毎 tick、高さ4ブロックまでの所に光の粒。遠くからでも見つけられるように）
            level().addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 0.15, getY() + 0.5 + random.nextDouble() * 4.0,
                    getZ() + (random.nextDouble() - 0.5) * 0.15, 0.0, 0.03, 0.0);
            if (random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.GLOW, getRandomX(0.5), getY() + random.nextDouble() * 0.8, getRandomZ(0.5), 0.0, 0.01, 0.0);
                // まわりから吸い上げられていく緑の芽
                level().addParticle(ParticleTypes.COMPOSTER, getRandomX(1.6), getY() + 0.1, getRandomZ(1.6), 0.0, 0.05, 0.0);
                level().addParticle(ParticleTypes.HAPPY_VILLAGER, getRandomX(0.6), getY() + 0.6 + random.nextDouble() * 0.6,
                        getRandomZ(0.6), 0.0, 0.02, 0.0);
            }
        } else if (isAlive() && random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.GLOW, getRandomX(0.5), getY() + random.nextDouble() * 0.8, getRandomZ(0.5), 0.0, 0.01, 0.0);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level && owner != null && level.getEntity(owner) instanceof OldWard ward) {
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.4, getZ(), 16, 0.3, 0.3, 0.3, 0.08);
            if (seed()) {
                ward.onSeedDestroyed(this);
            } else {
                ward.onKnotBroken();
            }
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_CLUSTER_BREAK;
    }
}

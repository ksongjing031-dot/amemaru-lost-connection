package jp.amemaru.lostconnection.enemy.boss;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import net.minecraft.core.particles.ParticleTypes;
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
 */
public class OldWardKnot extends AmemaruMob {
    @Nullable
    private UUID owner;
    private int index;

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
            ward.onKnotBroken();
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

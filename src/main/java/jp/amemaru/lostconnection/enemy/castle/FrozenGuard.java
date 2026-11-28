package jp.amemaru.lostconnection.enemy.castle;

import jp.amemaru.lostconnection.combat.PlayerColdService;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 凍れる近衛兵（9階の雑魚。Phase 0-A 9章の9階「凍れる近衛兵」）：氷に覆われたまま、今も城を守っている近衛の兵。兜の中の顔は空白。
 * 青い外套に、氷の斧槍と塔の盾【補完】。
 *
 * <ul>
 *   <li>凍った兵士の像にまじって、像のまま立っている（叩いても硬い：受けるダメージ {@code behavior.dormant_taken}）。プレイヤーが
 *       {@code behavior.wake_range} まで近づく・叩くと、氷を割って目を覚ます（4階の沈黙の石像と同じ）。しばらく誰もいないと、
 *       元の場所でまた像になる</li>
 *   <li>斧槍の突き（thrust。前の細い扇。凍える）と、盾打ち（bash。前の扇。押し返す）</li>
 *   <li>塔の盾：前から受けるダメージは {@code behavior.shield_taken}。<b>隊列</b>（{@code behavior.formation_range} ブロック
 *       以内に、ほかの近衛兵がいる）を組むと、盾がもっと硬い（{@code behavior.formation_taken}）。横か後ろから攻める</li>
 *   <li>近衛長ヴァルグリムの部下（定義 {@code valgrim_guard}）は、近衛長の「盾の誓い」でつながっている間、傷つかない
 *       （{@link jp.amemaru.lostconnection.enemy.boss.Valgrim}）</li>
 * </ul>
 */
public class FrozenGuard extends AmemaruMob {
    private static final EntityDataAccessor<Boolean> DATA_DORMANT = SynchedEntityData.defineId(FrozenGuard.class,
            EntityDataSerializers.BOOLEAN);
    /** 11月28日：近衛長の盾の誓いでつながっているか（クライアントで、青い鎖の光を描く）。 */
    private static final EntityDataAccessor<Boolean> DATA_OATH = SynchedEntityData.defineId(FrozenGuard.class, EntityDataSerializers.BOOLEAN);
    private static final Set<UUID> TOLD = new HashSet<>();
    private int waking;
    private int idle;
    private int lastClang;
    /** 盾の誓いでつないでいる近衛長（なければ null）。 */
    @Nullable
    private UUID oathBoss;
    /** 近衛長に呼ばれて走っている残り（tick）と、行き先。 */
    private int rallyTicks;
    @Nullable
    private Vec3 rallyAt;

    public FrozenGuard(EntityType<? extends FrozenGuard> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_DORMANT, true);
        entityData.define(DATA_OATH, false);
    }

    @Override
    public String assetName() {
        return "frozen_guard";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_wake", "skill_thrust", "skill_guard"};
    }

    /** 像のまま立っているか（クライアントでも読める）。 */
    public boolean dormant() {
        return entityData.get(DATA_DORMANT);
    }

    private void setDormant(boolean value) {
        if (dormant() != value) {
            entityData.set(DATA_DORMANT, value);
        }
    }

    /** 盾の誓いでつながっているか（クライアントでも読める）。 */
    public boolean oathLinked() {
        return entityData.get(DATA_OATH);
    }

    /** 近衛長が、盾の誓いでつなぐ・ほどく（ボスから）。 */
    public void setOath(@Nullable UUID boss, boolean linked) {
        oathBoss = boss;
        if (entityData.get(DATA_OATH) != linked) {
            entityData.set(DATA_OATH, linked);
        }
    }

    @Nullable
    public UUID oathBoss() {
        return oathBoss;
    }

    /** 近衛長に呼ばれる（集え・行進の隊列）：人を追うのをやめて、{@code at} へ走る（{@code ticks} の間）。 */
    public void rallyTo(Vec3 at, int ticks) {
        rallyAt = at;
        rallyTicks = Math.max(rallyTicks, ticks);
        setDormant(false);
        clearTelegraph();
        getNavigation().moveTo(at.x, at.y, at.z, 1.5);
    }

    /** 呼ばれて走っているか。 */
    public boolean rallying() {
        return rallyTicks > 0;
    }

    /** 近衛長の部下は、離れても戻らない（近衛長が呼び戻す）。 */
    @Override
    protected boolean usesLeash() {
        return oathBoss == null && super.usesLeash();
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Dormant", dormant());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Dormant")) {
            setDormant(tag.getBoolean("Dormant"));
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new EnemyMeleeGoal(this, "thrust", "skill_thrust", TELEGRAPH_CONE, 0.95, SoundEvents.TRIDENT_THROW) {
            @Override
            public boolean canUse() {
                return !dormant() && waking <= 0 && rallyTicks <= 0 && ready("thrust") && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return rallyTicks <= 0 && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "bash", "attack", TELEGRAPH_CONE, 0.95, SoundEvents.SHIELD_BLOCK) {
            @Override
            public boolean canUse() {
                return !dormant() && waking <= 0 && rallyTicks <= 0 && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return rallyTicks <= 0 && super.canContinueToUse();
            }
        });
    }

    @Override
    public Map<String, Boolean> boneToggles() {
        // 像の間は霜と氷の殻、目を覚ますと兜の中に青い目の光（顔は空白のまま）
        return Map.of("frost", dormant(), "eyes", !dormant(), "oath", oathLinked());
    }

    @Nullable
    @Override
    protected ServerPlayer findAggroTarget(ServerLevel level) {
        if (dormant()) {
            EnemyDef def = def();
            double wake = def == null ? 5.0 : def.behavior("wake_range", 5.0);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(wake))) {
                if (player.isAlive() && !player.isSpectator() && !player.isCreative() && player.distanceTo(this) <= wake
                        && hasLineOfSight(player)) {
                    wake(level);
                    return player;
                }
            }
            return null;
        }
        return super.findAggroTarget(level);
    }

    @Override
    protected void onAggro(ServerPlayer player, boolean hurt) {
        if (dormant() && level() instanceof ServerLevel level) {
            wake(level);
        }
    }

    /** 氷を割って目を覚ます。 */
    private void wake(ServerLevel level) {
        if (!dormant()) {
            return;
        }
        setDormant(false);
        waking = 20;
        setBusy(waking);
        idle = 0;
        playAction("skill_wake");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.4F, 0.6F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.HOSTILE, 1.4F, 0.7F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), getX(),
                getY() + getBbHeight() * 0.6, getZ(), 30, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.05);
    }

    /** すぐに目を覚まさせる（札を間違えたときの近衛兵・ボスの部下。動きは出さない）。 */
    public void wakeNow() {
        setDormant(false);
        waking = 0;
        idle = 0;
    }

    @Override
    public void onStruck(ServerPlayer player, double coefficient) {
        // 氷の斧槍：凍える
        EnemyDef def = def();
        PlayerColdService.chill(player, 1, (int) Math.round((def == null ? 4.0 : def.behavior("chill_seconds", 4.0)) * 20));
    }

    @Override
    public void onSkillHit(String skill, ServerPlayer player) {
        if ("bash".equals(skill)) {
            // 盾打ち：押し返す
            EnemyDef def = def();
            double push = def == null ? 0.9 : def.behavior("bash_push", 0.9);
            Vec3 away = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, getYRot()) : away.normalize();
            player.push(away.x * push, 0.25, away.z * push);
            player.hurtMarked = true;
        }
    }

    /** 隊列：まわりに、ほかの近衛兵（像でない）がいるか。 */
    public boolean inFormation() {
        EnemyDef def = def();
        double range = def == null ? 4.0 : def.behavior("formation_range", 4.0);
        for (FrozenGuard other : level().getEntitiesOfClass(FrozenGuard.class, getBoundingBox().inflate(range))) {
            if (other != this && other.isAlive() && !other.dormant() && other.distanceTo(this) <= range) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double damageTakenMultiplier(@Nullable Entity attacker) {
        EnemyDef def = def();
        if (def == null) {
            return 1.0;
        }
        // 11月28日：近衛長の盾の誓いでつながっている間は、傷つかない（近衛長の盾が受ける）
        if (oathLinked() && level() instanceof ServerLevel level && oathBoss != null
                && level.getEntity(oathBoss) instanceof jp.amemaru.lostconnection.enemy.boss.Valgrim boss && boss.isAlive()) {
            boss.onOathBlocked(this, attacker);
            return 0.0;
        }
        if (dormant()) {
            return def.behavior("dormant_taken", 0.3);
        }
        if (attacker != null) {
            double angle = def.behavior("shield_angle", 120.0);
            if (inFront(attacker.position(), angle / 2.0)) {
                boolean formation = inFormation();
                clang(attacker, formation);
                return formation ? def.behavior("formation_taken", 0.2) : def.behavior("shield_taken", 0.4);
            }
        }
        return 1.0;
    }

    private void clang(Entity attacker, boolean formation) {
        if (!(level() instanceof ServerLevel level) || tickCount - lastClang < 4) {
            return;
        }
        lastClang = tickCount;
        Vec3 at = position().add(Vec3.directionFromRotation(0.0F, getYRot()).scale(0.7)).add(0.0, getBbHeight() * 0.55, 0.0);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, formation ? 14 : 8, 0.3, 0.4, 0.3, 0.1);
        level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 6, 0.3, 0.4, 0.3, 0.02);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, formation ? 0.6F : 0.9F);
        if (attacker instanceof ServerPlayer player && TOLD.add(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.amemaru.frozen_guard.shield").withStyle(ChatFormatting.AQUA), false);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (isAlive() && oathLinked() && random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.END_ROD, getRandomX(0.6), getY() + getBbHeight() * random.nextDouble(), getRandomZ(0.6),
                        0.0, 0.01, 0.0);
            }
            return;
        }
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return;
        }
        if (waking > 0) {
            waking--;
            getNavigation().stop();
            return;
        }
        if (rallyTicks > 0 && rallyAt != null) {
            rallyTicks--;
            if (rallyAt.distanceToSqr(position()) < 2.5 * 2.5) {
                rallyTicks = 0;
                getNavigation().stop();
            } else if (rallyTicks % 10 == 0) {
                getNavigation().moveTo(rallyAt.x, rallyAt.y, rallyAt.z, 1.5);
            }
            return;
        }
        if (dormant()) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
            return;
        }
        if (oathBoss != null || getTags().contains("amemaru_lineage_woken")) {
            return;     // 近衛長の部下と、札を間違えて目を覚ました近衛兵は、像に戻らない
        }
        if (getTarget() == null) {
            idle++;
            BlockPos home = home();
            if (idle > 240 && home != null && home.closerToCenterThan(position(), 1.5)) {
                setDormant(true);
                getNavigation().stop();
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_PLACE, SoundSource.HOSTILE, 1.0F, 0.6F);
            } else if (idle > 60 && home != null && idle % 40 == 0) {
                getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.8);
            }
        } else {
            idle = 0;
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return dormant() ? null : SoundEvents.IRON_GOLEM_STEP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GLASS_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GLASS_BREAK;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F + (random.nextFloat() - 0.5F) * 0.1F;
    }
}

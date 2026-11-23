package jp.amemaru.lostconnection.enemy.forest;

import jp.amemaru.lostconnection.combat.PoisonService;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.EnumSet;

/**
 * ポコダケ（2階の雑魚。Phase 0-A 9章「毒の胞子を出すキノコ」）。その場から動かない。人が近づくと、周りの円の予兆のあとで
 * 毒の胞子をはき、胞子の雲がしばらく残る（雲の中にいると毒になる。{@link PoisonService}）【補完】。
 */
public class Pokodake extends AmemaruMob {
    private static final DustParticleOptions SPORE = new DustParticleOptions(new Vector3f(0.62F, 0.78F, 0.28F), 1.4F);
    private Vec3 cloud;
    private int cloudTicks;

    public Pokodake(EntityType<? extends Pokodake> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "pokodake";
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new SporeGoal(this));
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_spore"};
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    protected boolean usesLeash() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || cloud == null) {
            return;
        }
        if (cloudTicks-- <= 0) {
            cloud = null;
            return;
        }
        EnemyDef def = def();
        double radius = def == null ? 3.0 : def.skill("spore", 0.4, 0.8, 5.0, 3.0).range();
        if (cloudTicks % 4 == 0) {
            level.sendParticles(SPORE, cloud.x, cloud.y + 0.6, cloud.z, 10, radius * 0.5, 0.4, radius * 0.5, 0.0);
        }
        if (cloudTicks % 10 == 0) {
            int seconds = (int) Math.round(def == null ? 6.0 : def.behavior("poison_seconds", 6.0));
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(radius + 1.0))) {
                if (player.position().distanceTo(cloud) <= radius) {
                    PoisonService.poison(player, seconds, 0);
                }
            }
        }
    }

    /** 胞子をはく（周りの円の予兆のあと）。 */
    void release(double power, double radius) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        clearTelegraph();
        cloud = position();
        EnemyDef def = def();
        cloudTicks = (int) Math.round((def == null ? 4.0 : def.behavior("cloud_seconds", 4.0)) * 20);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PUFFER_FISH_BLOW_OUT, SoundSource.HOSTILE, 1.0F, 0.7F);
        level.sendParticles(SPORE, getX(), getY() + 0.8, getZ(), 40, radius * 0.5, 0.5, radius * 0.5, 0.02);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(radius + 1.0))) {
            if (player.position().distanceTo(position()) <= radius && EnemyCombat.strike(this, player, power, null)) {
                PoisonService.poison(player, (int) Math.round(def == null ? 6.0 : def.behavior("poison_seconds", 6.0)), 0);
            }
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SLIME_SQUISH_SMALL;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SLIME_DEATH_SMALL;
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    }

    /** 人が近くにいる間、決まった間ごとに胞子をはく。 */
    static class SporeGoal extends Goal {
        private final Pokodake shroom;
        private int windup = -1;

        SporeGoal(Pokodake shroom) {
            this.shroom = shroom;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return shroom.getTarget() != null && shroom.def() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return windup >= 0 || canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            if (windup >= 0) {
                shroom.clearTelegraph();
            }
            windup = -1;
        }

        @Override
        public void tick() {
            EnemyDef def = shroom.def();
            if (def == null) {
                return;
            }
            EnemyDef.Skill skill = def.skill("spore", 0.4, 0.8, 5.0, 3.0);
            if (windup >= 0) {
                if (windup-- == 0) {
                    shroom.release(skill.power(), skill.range());
                    shroom.startCooldown("spore", skill.cooldownTicks());
                }
                return;
            }
            var target = shroom.getTarget();
            if (target == null) {
                return;
            }
            double trigger = def.behavior("trigger_range", 5.0);
            if (shroom.distanceTo(target) <= trigger && shroom.ready("spore") && !shroom.isBusy()) {
                windup = Math.max(1, skill.windupTicks());
                shroom.setBusy(windup + 6);
                shroom.playAction("skill_spore");
                shroom.setTelegraph(TELEGRAPH_CIRCLE, windup, shroom.getYRot(), (float) skill.range());
            }
        }
    }
}

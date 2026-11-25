package jp.amemaru.lostconnection.enemy.lake;

import jp.amemaru.lostconnection.combat.LostService;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.enemy.CrystalBee;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.entity.GroundMarker;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Map;

/**
 * 声喰い（8階の雑魚。Phase 0-A 9章の8階「近くにいると効果音が消える」）：大きな口だけの、ふわふわ浮く白い影。のどの奥が、食べた声で
 * 青く光る。そばの人（behavior.silence_range）は、ロスト状態「静寂」（効果音と予兆の音が消える）になる。声の礫を吐く
 * （needle。1階の結晶蜂と同じ動き）【補完】。
 *
 * <p>名のある魔物「千声喰い」（voice_eater_elder）は、静寂の届く所が広く、ときどき大きく息を吸いこんで、まわりの人を引き寄せる
 * （技 inhale。地面の円の予兆のあと、seconds 秒引き寄せ、最後にかみつく）。</p>
 */
public class VoiceEater extends CrystalBee {
    private int inhaleTicks;

    public VoiceEater(EntityType<? extends VoiceEater> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "voice_eater";
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new InhaleGoal());
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_needle", "skill_inhale"};
    }

    /** 静寂の届く所（ブロック）。 */
    public double silenceRange() {
        EnemyDef def = def();
        return def == null ? 7.0 : def.behavior("silence_range", 7.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (isAlive() && random.nextInt(2) == 0) {
                double r = 0.6 + random.nextDouble() * 1.2;
                double a = random.nextDouble() * Math.PI * 2.0;
                level().addParticle(ParticleTypes.SCULK_SOUL, getX() + Math.cos(a) * r, getY() + 0.4, getZ() + Math.sin(a) * r,
                        -Math.cos(a) * 0.03, 0.0, -Math.sin(a) * 0.03);
            }
            return;
        }
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return;
        }
        if (tickCount % 20 == 0) {
            silenceAround(level);
        }
        if (inhaleTicks > 0) {
            tickInhale(level);
        }
    }

    /** そばの人を「静寂」にする（そばにいる間、とぎれない）。 */
    public void silenceAround(ServerLevel level) {
        EnemyDef def = def();
        double range = silenceRange();
        int ticks = (int) Math.round((def == null ? 3.0 : def.behavior("silence_seconds", 3.0)) * 20);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(range))) {
            if (player.distanceTo(this) <= range) {
                LostService.inflict(player, ModEffects.LOST_SILENCE.get(), ticks);
            }
        }
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2.0 * i / 12;
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, getX() + Math.cos(a) * range, getY() + 0.2, getZ() + Math.sin(a) * range, 1,
                    0.0, 0.0, 0.0, 0.0);
        }
    }

    private EnemyDef.Skill inhale() {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(1.4, 1.6, 14.0, 10.0, Map.of()) : def.skill("inhale", 1.4, 1.6, 14.0, 10.0);
    }

    private boolean hasInhale() {
        EnemyDef def = def();
        return def != null && def.skills().containsKey("inhale");
    }

    private void tickInhale(ServerLevel level) {
        EnemyDef.Skill skill = inhale();
        double range = skill.range();
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(range))) {
            Vec3 to = position().subtract(player.position());
            double d = to.length();
            if (d > range || d < 1.2 || player.isCreative() || player.isSpectator()) {
                continue;
            }
            Vec3 pull = to.normalize().scale(skill.param("pull", 0.12));
            player.push(pull.x, 0.0, pull.z);
            player.hurtMarked = true;
        }
        if (inhaleTicks % 4 == 0) {
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2.0 * i / 16;
                level.sendParticles(ParticleTypes.SCULK_SOUL, getX() + Math.cos(a) * range, getY() + 0.3, getZ() + Math.sin(a) * range, 0,
                        -Math.cos(a), 0.0, -Math.sin(a), 0.25);
            }
        }
        if (--inhaleTicks == 0) {
            int silence = (int) Math.round(skill.param("silence_seconds", 8.0) * 20);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(3.0))) {
                if (player.distanceTo(this) <= 3.2 && EnemyCombat.strike(this, player, skill.power(), null)) {
                    LostService.inflict(player, ModEffects.LOST_SILENCE.get(), silence);
                }
            }
            level.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 0.5, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** 自動テスト用：引き寄せている残りの tick。 */
    public int testInhaleTicks() {
        return inhaleTicks;
    }

    /** 大きく息を吸いこむ（名のある魔物だけ）。 */
    private final class InhaleGoal extends Goal {
        private int windup = -1;

        InhaleGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (target == null || !hasInhale() || isBusy() || inhaleTicks > 0 || !ready("inhale")) {
                return false;
            }
            return distanceTo(target) <= inhale().range();
        }

        @Override
        public boolean canContinueToUse() {
            return windup >= 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            EnemyDef.Skill skill = inhale();
            windup = Math.max(1, skill.windupTicks());
            setBusy(windup + (int) Math.round(skill.param("seconds", 2.0) * 20) + 10);
            getNavigation().stop();
            playAction("skill_inhale");
            if (level() instanceof ServerLevel level) {
                GroundMarker.place(level, new Vec3(getX(), getY() - 1.5, getZ()), (float) skill.range(), windup, GroundMarker.KIND_DANGER);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.4F, 1.2F);
            }
        }

        @Override
        public void stop() {
            windup = -1;
        }

        @Override
        public void tick() {
            getNavigation().stop();
            if (windup-- > 0) {
                return;
            }
            EnemyDef.Skill skill = inhale();
            inhaleTicks = (int) Math.round(skill.param("seconds", 2.0) * 20);
            startCooldown("inhale", skill.cooldownTicks());
            windup = -1;
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SCULK_CLICKING;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SCULK_SHRIEKER_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SCULK_SHRIEKER_BREAK;
    }
}

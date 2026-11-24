package jp.amemaru.lostconnection.enemy.altar;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.altar.AltarService;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.Map;

/**
 * 監視眼ウォッチャー（5階の雑魚。Phase 0-A 9章「浮かぶ目。見つかると守護者が強くなる」）。金の枠に囲まれた、宙に浮かぶ
 * 大きな目。
 *
 * <ul>
 *   <li>見つける：はじめて人を見つけると、目が赤く光り、階の「警戒」を {@code behavior.alert} 上げる（{@link AltarService}。
 *       同じ目は {@code behavior.alert_cooldown} 秒に1度だけ）。警戒が高いほど、祭壇の守護者が強くなる</li>
 *   <li>光線（ray）：少し離れて浮かび、一直線の予兆のあと、線の上の人を撃つ</li>
 *   <li>宙に浮いて動く。体はもろい</li>
 *   <li>監視の大眼（名のある魔物）：大きく、遠くまで見え、一度に警戒を2つ上げる</li>
 * </ul>
 */
public class Watcher extends AmemaruMob {
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0F, 0.25F, 0.2F), 1.2F);
    private long lastAlert = -100000L;

    public Watcher(EntityType<? extends Watcher> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createWatcherAttributes() {
        return createAttributes().add(Attributes.FLYING_SPEED, 0.45);
    }

    @Override
    public String assetName() {
        return "watcher";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_spot"};
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new RayGoal());
    }

    @Override
    protected void onAggro(ServerPlayer player, boolean hurt) {
        super.onAggro(player, hurt);
        spot(player);
    }

    /** 人を見つけた：警戒を上げる（同じ目は決まった秒数に1度）。上げたら true。 */
    public boolean spot(ServerPlayer player) {
        if (!(level() instanceof ServerLevel level)) {
            return false;
        }
        EnemyDef def = def();
        long cooldown = Math.round((def == null ? 60.0 : def.behavior("alert_cooldown", 60.0)) * 20);
        if (level.getGameTime() - lastAlert < cooldown) {
            return false;
        }
        FloorDef floor = FloorDefs.floorAt(level, position());
        if (floor == null) {
            return false;
        }
        lastAlert = level.getGameTime();
        playAction("skill_spot");
        level.sendParticles(RED, getX(), getY() + getBbHeight() * 0.5, getZ(), 24, 0.5, 0.5, 0.5, 0.0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.HOSTILE, 1.0F, 1.8F);
        AltarService.raiseAlert(level, floor.number(), player, (int) Math.round(def == null ? 1.0 : def.behavior("alert", 1.0)));
        return true;
    }

    private EnemyDef.Skill ray() {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(1.2, 1.0, 3.5, 14.0, Map.of()) : def.skill("ray", 1.2, 1.0, 3.5, 14.0);
    }

    /** 光線：離れて浮かび、一直線の予兆のあと撃つ。 */
    private final class RayGoal extends Goal {
        private int windup = -1;
        private Vec3 dir = Vec3.ZERO;
        private int wander;

        RayGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return getTarget() != null && getTarget().isAlive();
        }

        @Override
        public void stop() {
            windup = -1;
            clearTelegraph();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null || !(level() instanceof ServerLevel level)) {
                return;
            }
            EnemyDef.Skill skill = ray();
            double range = skill.range();
            if (windup >= 0) {
                setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
                setYHeadRot(getYRot());
                getNavigation().stop();
                if (windup % 4 == 0) {
                    for (double t = 1.0; t <= range; t += 1.5) {
                        Vec3 at = position().add(0.0, getBbHeight() * 0.5, 0.0).add(dir.scale(t));
                        level.sendParticles(RED, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
                    }
                }
                if (--windup > 0) {
                    return;
                }
                windup = -1;
                clearTelegraph();
                fire(level, skill);
                return;
            }
            double dist = distanceTo(target);
            // 少し離れて、目の高さより少し上に浮かぶ
            Vec3 away = position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() < 1.0E-4) {
                away = new Vec3(1.0, 0.0, 0.0);
            }
            away = away.normalize();
            if (--wander <= 0) {
                wander = 30 + random.nextInt(30);
                Vec3 side = new Vec3(-away.z, 0.0, away.x).scale(random.nextBoolean() ? 3.0 : -3.0);
                Vec3 want = target.position().add(away.scale(Math.min(range - 3.0, 8.0))).add(side).add(0.0, 3.0, 0.0);
                getNavigation().moveTo(want.x, want.y, want.z, 1.0);
            }
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (dist <= range && ready("ray") && !isBusy() && hasLineOfSight(target)) {
                dir = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0)
                        .subtract(position().add(0.0, getBbHeight() * 0.5, 0.0)).normalize();
                windup = Math.max(4, skill.windupTicks());
                setBusy(windup + 6);
                float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
                setTelegraph(TELEGRAPH_LINE, windup, yaw, (float) range, (float) skill.param("width", 1.2));
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 0.8F, 1.8F);
            }
        }

        private void fire(ServerLevel level, EnemyDef.Skill skill) {
            double range = skill.range();
            double half = skill.param("width", 1.2) / 2.0 + 0.4;
            Vec3 from = position().add(0.0, getBbHeight() * 0.5, 0.0);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(range + 2.0))) {
                if (player.isSpectator() || player.isCreative()) {
                    continue;
                }
                Vec3 rel = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0).subtract(from);
                double along = rel.dot(dir);
                double side = rel.subtract(dir.scale(along)).length();
                if (along >= 0.0 && along <= range + 0.6 && side <= half) {
                    EnemyCombat.strike(Watcher.this, player, skill.power(), null);
                }
            }
            for (double t = 0.5; t <= range; t += 0.5) {
                Vec3 at = from.add(dir.scale(t));
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
                level.sendParticles(RED, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 1.0F, 1.6F);
            startCooldown("ray", skill.cooldownTicks());
        }
    }
}

package jp.amemaru.lostconnection.enemy.boss;

import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.Tsunomofu;
import jp.amemaru.lostconnection.tower.BossRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 1階のボス、草原の主ヴェルドホーン（Phase 0-A 9章「巨大な角の獣で、突進の通り道が地面に線で表示される。HP50%でツノモフを呼ぶ。
 * ヘイト・回避・蘇生のチュートリアルを兼ねる」）。
 *
 * <ul>
 *   <li>角の薙ぎ払い：敵視のいちばん高い人（盾役）へ。前の扇形（体が光ってから）【提案】</li>
 *   <li>突進：離れた人へ、地面に通り道の線を出してから、まっすぐ突っ込む。壁にぶつかると、しばらく動けない（攻める好機）【提案】</li>
 *   <li>踏みつけ：まわりに人が集まると、地面に円を出してから、まわりをまとめて打つ【提案】</li>
 *   <li>HP50%：吠えて、ツノモフを呼ぶ（2匹＋参加者の数。6匹まで）。突進の予兆が短くなる。HP20%：さらに技の間が短くなる【提案】</li>
 * </ul>
 * 数値は敵の定義（{@code enemies/veldhorn.json}）の技で変えられる。
 */
public class Veldhorn extends AmemaruBoss {
    private enum Action { NONE, SWEEP, CHARGE_WINDUP, CHARGING, STUNNED, STOMP, ROAR }

    private Action action = Action.NONE;
    private int timer;
    private float chargeYaw;
    private Vec3 chargeDir = Vec3.ZERO;
    private double chargeLength;
    private double traveled;
    private final Set<UUID> hit = new HashSet<>();
    private final List<UUID> summoned = new ArrayList<>();
    private int chargesLeft;

    public Veldhorn(EntityType<? extends Veldhorn> type, Level level) {
        super(type, level, BossEvent.BossBarColor.GREEN);
    }

    @Override
    public String assetName() {
        return "veldhorn";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_charge", "skill_stomp", "skill_roar"};
    }

    @Override
    protected int phaseFor(double ratio) {
        if (ratio <= 0.2) {
            return 3;
        }
        return ratio <= 0.5 ? 2 : 1;
    }

    // ---------------------------------------------------------------- 技の数値

    private EnemyDef.Skill skill(String name, double power, double windup, double cooldown, double range) {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(power, windup, cooldown, range, java.util.Map.of())
                : def.skill(name, power, windup, cooldown, range);
    }

    private int cooldown(EnemyDef.Skill skill) {
        double scale = phase() >= 3 ? 0.7 : phase() == 2 ? 0.85 : 1.0;
        return (int) Math.round(skill.cooldownTicks() * scale);
    }

    // ---------------------------------------------------------------- 戦い

    @Override
    public void onFightStarted(ServerLevel level, List<ServerPlayer> participants) {
        line("boss.amemaru.veldhorn.line.start");
        tip("boss.amemaru.veldhorn.tip.hate");
    }

    @Override
    protected void onPhaseChanged(ServerLevel level, int from, int to) {
        if (to >= 2 && from < 2) {
            action = Action.ROAR;
            timer = 40;
            clearTelegraph();
            setTelegraph(TELEGRAPH_ALERT, 40, getYRot(), 0.0F);
            playAction("skill_roar");
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.7F);
            line("boss.amemaru.veldhorn.line.call");
        }
        if (to >= 3 && from < 3) {
            tip("boss.amemaru.veldhorn.tip.enrage");
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6F, 0.9F);
        }
    }

    @Override
    public void resetFight() {
        super.resetFight();
        action = Action.NONE;
        timer = 0;
        hit.clear();
        discardSummoned();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive() || isReturning()) {
            return;
        }
        BossRoom room = room();
        if (room != null && !room.fighting()) {
            // 戦いの前：部屋の真ん中で、ゆっくり見回す
            if (action != Action.NONE) {
                action = Action.NONE;
                clearTelegraph();
            }
            return;
        }
        switch (action) {
            case NONE -> choose(level);
            case SWEEP -> tickSweep(level);
            case CHARGE_WINDUP -> tickChargeWindup(level);
            case CHARGING -> tickCharging(level);
            case STUNNED -> tickStunned(level);
            case STOMP -> tickStomp(level);
            case ROAR -> tickRoar(level);
        }
    }

    private void choose(ServerLevel level) {
        if (isBusy()) {
            return;
        }
        List<ServerPlayer> players = targets(level);
        if (players.isEmpty()) {
            getNavigation().stop();
            return;
        }
        ServerPlayer top = topThreat();
        if (top == null || !players.contains(top)) {
            top = players.get(0);
        }
        // 踏みつけ：まわりに2人以上
        EnemyDef.Skill stomp = skill("stomp", 1.4, 1.2, 12.0, 6.0);
        int near = 0;
        for (ServerPlayer player : players) {
            if (player.distanceTo(this) <= stomp.range()) {
                near++;
            }
        }
        if (ready("stomp") && near >= 2) {
            startStomp(stomp);
            return;
        }
        // 突進：離れた人（いちばん遠い人）
        EnemyDef.Skill charge = skill("charge", 2.2, 1.4, 9.0, 22.0);
        if (ready("charge")) {
            ServerPlayer far = null;
            double best = -1;
            for (ServerPlayer player : players) {
                double d = player.distanceTo(this);
                if (d >= charge.param("min_range", 6.0) && d <= charge.range() && hasLineOfSight(player) && d > best) {
                    far = player;
                    best = d;
                }
            }
            if (far != null) {
                chargesLeft = phase() >= 2 && random.nextInt(3) == 0 ? 2 : 1;
                startCharge(far, charge);
                return;
            }
        }
        // 薙ぎ払い：敵視のいちばん高い人が前にいる
        EnemyDef.Skill sweep = skill("sweep", 1.2, 0.8, 3.5, 5.5);
        double dist = top.distanceTo(this);
        if (ready("sweep") && dist <= sweep.range()) {
            faceTowards(top.position());
            startSweep(sweep);
            return;
        }
        // 近づく
        if (dist > 3.5) {
            getNavigation().moveTo(top, 1.0);
        } else {
            getNavigation().stop();
            faceTowards(top.position());
        }
    }

    /** 部屋の参加者のうち、狙える人（倒れている人・見ているだけの人は除く）。 */
    private List<ServerPlayer> targets(ServerLevel level) {
        List<ServerPlayer> list = new ArrayList<>();
        BossRoom room = room();
        if (room == null) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceTo(this) < 32 && !player.isCreative() && !player.isSpectator()) {
                    list.add(player);
                }
            }
            return list;
        }
        for (ServerPlayer player : room.participants()) {
            if (player.isAlive() && !player.isSpectator() && !player.isCreative() && !BossRoom.isDown(player)) {
                list.add(player);
            }
        }
        return list;
    }

    // ---- 薙ぎ払い

    private void startSweep(EnemyDef.Skill sweep) {
        action = Action.SWEEP;
        timer = Math.max(1, sweep.windupTicks());
        getNavigation().stop();
        setBusy(timer + 10);
        setTelegraph(TELEGRAPH_WINDUP, timer, getYRot(), (float) sweep.range());
        playAction("attack");
    }

    private void tickSweep(ServerLevel level) {
        getNavigation().stop();
        if (--timer > 0) {
            return;
        }
        EnemyDef.Skill sweep = skill("sweep", 1.2, 0.8, 3.5, 5.5);
        double half = sweep.param("angle", 120.0) / 2.0;
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) <= sweep.range() + 0.6 && inFront(player.position(), half)) {
                if (EnemyCombat.strike(this, player, sweep.power(), null)) {
                    Vec3 push = player.position().subtract(position()).multiply(1, 0, 1).normalize().scale(0.8);
                    player.push(push.x, 0.25, push.z);
                    player.hurtMarked = true;
                }
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.4F, 0.8F);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + getLookAngle().x * 2.5, getY() + 1.5, getZ() + getLookAngle().z * 2.5,
                3, 1.0, 0.3, 1.0, 0.0);
        startCooldown("sweep", cooldown(sweep));
        clearTelegraph();
        action = Action.NONE;
    }

    // ---- 突進

    private void startCharge(LivingEntity target, EnemyDef.Skill charge) {
        getNavigation().stop();
        faceTowards(target.position());
        chargeYaw = getYRot();
        chargeDir = Vec3.directionFromRotation(0.0F, chargeYaw).normalize();
        chargeLength = Math.min(charge.range(), distanceTo(target) + 4.0);
        double windup = charge.windup() * (phase() >= 2 ? 0.78 : 1.0);
        timer = Math.max(8, (int) Math.round(windup * 20.0));
        traveled = 0.0;
        hit.clear();
        action = Action.CHARGE_WINDUP;
        setBusy(timer + 80);
        setTelegraph(TELEGRAPH_LINE, timer, chargeYaw, (float) chargeLength);
        playAction("skill_charge");
        if (phase() == 1 && !tipped("charge")) {
            tip("boss.amemaru.veldhorn.tip.charge");
        }
    }

    private final Set<String> tips = new HashSet<>();

    private boolean tipped(String key) {
        return !tips.add(key);
    }

    private void lockRotation() {
        setYRot(chargeYaw);
        setYBodyRot(chargeYaw);
        setYHeadRot(chargeYaw);
    }

    private void tickChargeWindup(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        lockRotation();
        if (timer % 6 == 0) {
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 4, 0.6, 0.05, 0.6, 0.02);
        }
        if (--timer <= 0) {
            action = Action.CHARGING;
            clearTelegraph();
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4F, 1.3F);
        }
    }

    private void tickCharging(ServerLevel level) {
        EnemyDef.Skill charge = skill("charge", 2.2, 1.4, 9.0, 22.0);
        double speed = charge.param("speed", 1.0);
        lockRotation();
        setDeltaMovement(chargeDir.x * speed, getDeltaMovement().y, chargeDir.z * speed);
        hasImpulse = true;
        traveled += speed;
        double width = charge.param("width", 1.6);
        level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 3, 0.5, 0.05, 0.5, 0.02);
        for (ServerPlayer player : targets(level)) {
            if (player.getBoundingBox().intersects(getBoundingBox().inflate(width * 0.5, 0.0, width * 0.5)) && hit.add(player.getUUID())) {
                if (EnemyCombat.strike(this, player, charge.power(), null)) {
                    player.push(chargeDir.x * 1.4, 0.5, chargeDir.z * 1.4);
                    player.hurtMarked = true;
                }
            }
        }
        boolean wall = horizontalCollision;
        if (traveled >= chargeLength || wall) {
            setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
            if (wall) {
                // 壁にぶつかった：しばらく動けない（攻める好機）
                action = Action.STUNNED;
                timer = (int) Math.round(charge.param("stun", 2.5) * 20.0);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.5F);
                level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 2.5, getZ(), 20, 0.8, 0.4, 0.8, 0.1);
                if (!tipped("stun")) {
                    tip("boss.amemaru.veldhorn.tip.stun");
                }
                startCooldown("charge", cooldown(charge));
            } else if (--chargesLeft > 0) {
                // HP50%から：続けてもう一度
                List<ServerPlayer> players = targets(level);
                if (!players.isEmpty()) {
                    startCharge(players.get(random.nextInt(players.size())), charge);
                    return;
                }
                finishCharge(charge);
            } else {
                finishCharge(charge);
            }
        }
    }

    private void finishCharge(EnemyDef.Skill charge) {
        action = Action.STUNNED;
        timer = (int) Math.round(charge.param("recover", 1.0) * 20.0);
        startCooldown("charge", cooldown(charge));
    }

    private void tickStunned(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        if (timer % 10 == 0) {
            level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() + 0.3, getZ(), 4, 0.4, 0.1, 0.4, 0.05);
        }
        if (--timer <= 0) {
            action = Action.NONE;
            clearBusy();
        }
    }

    // ---- 踏みつけ

    private void startStomp(EnemyDef.Skill stomp) {
        action = Action.STOMP;
        timer = Math.max(1, stomp.windupTicks());
        getNavigation().stop();
        setBusy(timer + 10);
        setTelegraph(TELEGRAPH_CIRCLE, timer, getYRot(), (float) stomp.range());
        playAction("skill_stomp");
        if (!tipped("stomp")) {
            tip("boss.amemaru.veldhorn.tip.stomp");
        }
    }

    private void tickStomp(ServerLevel level) {
        getNavigation().stop();
        if (--timer > 0) {
            return;
        }
        EnemyDef.Skill stomp = skill("stomp", 1.4, 1.2, 12.0, 6.0);
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) <= stomp.range() + 0.5) {
                if (EnemyCombat.strike(this, player, stomp.power(), null)) {
                    Vec3 push = player.position().subtract(position()).multiply(1, 0, 1).normalize().scale(0.6);
                    player.push(push.x, 0.6, push.z);
                    player.hurtMarked = true;
                }
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.8F, 0.6F);
        for (int i = 0; i < 24; i++) {
            double a = i / 24.0 * Math.PI * 2.0;
            level.sendParticles(ParticleTypes.POOF, getX() + Math.cos(a) * stomp.range() * 0.8, getY() + 0.1,
                    getZ() + Math.sin(a) * stomp.range() * 0.8, 1, 0.1, 0.0, 0.1, 0.01);
        }
        startCooldown("stomp", cooldown(stomp));
        clearTelegraph();
        action = Action.NONE;
    }

    // ---- 吠える（HP50%：ツノモフを呼ぶ）

    private void tickRoar(ServerLevel level) {
        getNavigation().stop();
        if (--timer > 0) {
            return;
        }
        action = Action.NONE;
        clearBusy();
        EnemyDef.Skill call = skill("call", 0.0, 0.0, 0.0, 10.0);
        BossRoom room = room();
        int participants = room == null ? 1 : room.participants().size();
        int count = Math.min((int) call.param("max", 6.0), (int) call.param("count", 2.0) + participants * (int) call.param("per_player", 1.0));
        List<ServerPlayer> players = targets(level);
        for (int i = 0; i < count; i++) {
            Tsunomofu mofu = ModEntities.TSUNOMOFU.get().create(level);
            if (mofu == null) {
                continue;
            }
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = 4.0 + random.nextDouble() * call.range();
            Vec3 at = room != null ? room.arenaCenter().add(Math.cos(a) * r, 0.0, Math.sin(a) * r)
                    : position().add(Math.cos(a) * r, 0.0, Math.sin(a) * r);
            BlockPos ground = BlockPos.containing(at.x, getY() + 3, at.z);
            int y = ground.getY();
            while (y > getY() - 6 && level.getBlockState(new BlockPos(ground.getX(), y - 1, ground.getZ())).isAir()) {
                y--;
            }
            mofu.moveTo(at.x, y, at.z, random.nextFloat() * 360.0F, 0.0F);
            mofu.setHome(new BlockPos(ground.getX(), y, ground.getZ()));
            mofu.setTransient(true);
            level.addFreshEntity(mofu);
            summoned.add(mofu.getUUID());
            level.sendParticles(ParticleTypes.POOF, at.x, y + 0.5, at.z, 8, 0.4, 0.4, 0.4, 0.02);
            if (!players.isEmpty()) {
                mofu.addThreat(players.get(random.nextInt(players.size())), 5.0);
            }
        }
        tip("boss.amemaru.veldhorn.tip.call");
    }

    /** 呼んだツノモフを消す（戦いをはじめからにするとき）。 */
    public void discardSummoned() {
        if (level() instanceof ServerLevel level) {
            for (UUID id : summoned) {
                net.minecraft.world.entity.Entity entity = level.getEntity(id);
                if (entity instanceof AmemaruMob mob && mob.isAlive()) {
                    mob.discard();
                }
            }
        }
        summoned.clear();
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        discardSummoned();
    }

    /** 突進の向きの線の長さ（予兆の帯の長さを、壁で切らない）。 */
    @Nullable
    public Vec3 chargeDirection() {
        return action == Action.CHARGE_WINDUP || action == Action.CHARGING ? chargeDir : null;
    }

    static float wrap(float yaw) {
        return Mth.wrapDegrees(yaw);
    }
}

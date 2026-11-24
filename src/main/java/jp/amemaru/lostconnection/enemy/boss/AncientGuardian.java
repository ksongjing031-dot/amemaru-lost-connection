package jp.amemaru.lostconnection.enemy.boss;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryService;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.altar.RepairDrone;
import jp.amemaru.lostconnection.enemy.altar.Sentinel;
import jp.amemaru.lostconnection.entity.GroundMarker;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.progression.ProgressionService;
import jp.amemaru.lostconnection.tower.BossRoom;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.altar.AltarBlocks;
import jp.amemaru.lostconnection.tower.altar.AltarService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 5階のボス、古代守護者（Phase 0-A 9章・10章。第1章の章ボス・6人パーティ）。白い石と青銅の巨人。胸に翠の記録核。
 * 左腕は白く欠けている（ロストの侵食：「判定が狂っている」）。
 *
 * <ul>
 *   <li>第1段階（HP100〜70%）：腕の薙ぎ払い（扇形の予兆）・直線の光線・ヘイト1位への叩きつけ。欠けた左腕の薙ぎ払いは、
 *       ときどき打つ直前に向きが裏返る（{@code skills.sweep.flip_chance}。白い光で知らせる）【補完】</li>
 *   <li>第2段階（70〜35%）：4本の柱が結界を張って無敵になる。4つの「結び台」（階の定義の {@code boss_marks.binding_daises}）に、
 *       4人が同時に {@code skills.barrier.hold} 秒乗ると解ける（戦う人が4人より少なければ、その人数）【補完】。結界の間は防衛兵と
 *       修復ドローンを呼ぶ（乗っている人を、残りの人が守る）。解けるとよろめき、しばらく受けるダメージが増える。
 *       第2段階のうちは {@code skills.barrier.cooldown} 秒ごとに張り直す</li>
 *   <li>第3段階（35〜0%）：「接続判定」。予兆のあと、6ブロック（{@code skills.judge.range}）以内に味方がいない人に大ダメージ。
 *       寄り添えば耐えられる（ひとりで戦っているときは判定しない）【補完】</li>
 *   <li>最後：HPが尽きると倒れずに止まり、全員が祭壇の中央（{@code boss_marks.altar_center}）に集まると、膝をつく。
 *       倒れる前に「……登録外の、接続波形……」とつぶやく（確定の前触れ）</li>
 *   <li>警戒：戦いが始まったときの階の警戒（監視眼に見つかった数）1つにつき、攻撃 +10%（{@link AltarService}）</li>
 *   <li>初めて倒されたとき、倒したパーティで結び台に一番長く乗っていた人に「第七の門鍵」（Phase 0-A 23章のユニーク）</li>
 * </ul>
 */
public class AncientGuardian extends AmemaruBoss {
    public static final String DAISES = "binding_daises";
    public static final String CENTER = "altar_center";
    public static final String PILLARS = "barrier_pillars";
    public static final ResourceLocation GATE_KEY = AmemaruMod.id("seventh_gate_key");
    /** 管理者のコマンドで出させられる技（{@code /amemaru admin boss skill 5 <技>}）。 */
    public static final List<String> SKILLS = List.of("sweep", "beam", "slam", "barrier", "judge", "gather");

    private static final EntityDataAccessor<Boolean> DATA_BARRIER = SynchedEntityData.defineId(AncientGuardian.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_KNEEL = SynchedEntityData.defineId(AncientGuardian.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions JADE = new DustParticleOptions(new Vector3f(0.3F, 1.0F, 0.65F), 1.4F);
    private static final DustParticleOptions LINKED = new DustParticleOptions(new Vector3f(0.4F, 1.0F, 0.6F), 1.0F);
    private static final DustParticleOptions ALONE = new DustParticleOptions(new Vector3f(1.0F, 0.3F, 0.25F), 1.0F);

    private enum Action { NONE, SWEEP, BEAM, SLAM, JUDGE, GATHER, KNEEL, RECOVER }

    private Action action = Action.NONE;
    private int timer;
    private float lockedYaw;
    private boolean flip;
    private boolean flipped;
    private Vec3 beamDir = Vec3.ZERO;
    @Nullable
    private Vec3 slamAt;
    private int barrierHold;
    private int barrierCooldown = -1;
    private int callTimer;
    private int judgeCooldown = -1;
    private int vulnerable;
    private boolean allowDeath;
    private final Map<UUID, Integer> daisTicks = new HashMap<>();
    private final List<UUID> summoned = new ArrayList<>();
    private final Set<String> tips = new HashSet<>();

    public AncientGuardian(EntityType<? extends AncientGuardian> type, Level level) {
        super(type, level, BossEvent.BossBarColor.GREEN);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_BARRIER, false);
        entityData.define(DATA_KNEEL, false);
    }

    @Override
    public String assetName() {
        return "ancient_guardian";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_sweep", "skill_beam", "skill_slam", "skill_barrier", "skill_stagger", "skill_judge", "skill_kneel"};
    }

    @Override
    protected int phaseFor(double ratio) {
        if (ratio <= 0.35) {
            return 3;
        }
        return ratio <= 0.7 ? 2 : 1;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    /** 結界を張っているか（クライアントでも読める）。 */
    public boolean barrier() {
        return entityData.get(DATA_BARRIER);
    }

    /** 膝をついているか（集まるのを待つ・膝をつく。クライアントでも読める）。 */
    public boolean kneeling() {
        return entityData.get(DATA_KNEEL);
    }

    private void setBarrierFlag(boolean value) {
        if (barrier() != value) {
            entityData.set(DATA_BARRIER, value);
        }
    }

    @Override
    public Map<String, Boolean> boneToggles() {
        return Map.of("barrier", barrier());
    }

    /** 修復ドローンが直せるか（集まるのを待つ間・膝をついた後は直せない）。 */
    public boolean repairable() {
        return action != Action.GATHER && action != Action.KNEEL;
    }

    private EnemyDef.Skill skill(String name, double power, double windup, double cooldown, double range) {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(power, windup, cooldown, range, Map.of()) : def.skill(name, power, windup, cooldown, range);
    }

    private EnemyDef.Skill sweepSkill() {
        return skill("sweep", 1.3, 1.2, 5.0, 9.0);
    }

    private EnemyDef.Skill beamSkill() {
        return skill("beam", 1.5, 1.6, 8.0, 30.0);
    }

    private EnemyDef.Skill slamSkill() {
        return skill("slam", 1.8, 1.4, 9.0, 30.0);
    }

    private EnemyDef.Skill barrierSkill() {
        return skill("barrier", 0.0, 0.0, 45.0, 0.0);
    }

    private EnemyDef.Skill judgeSkill() {
        return skill("judge", 2.6, 3.0, 14.0, 6.0);
    }

    private int cooldown(EnemyDef.Skill skill) {
        double scale = phase() >= 3 ? 0.8 : phase() == 2 ? 0.9 : 1.0;
        return (int) Math.round(skill.cooldownTicks() * scale * pace());
    }

    /** 結界の間は無敵。集まるのを待つ間・膝をついた後も。結界が解けた直後は {@code skills.barrier.broken} 倍。 */
    @Override
    public double damageTakenMultiplier(@Nullable Entity attacker) {
        if (barrier() || action == Action.GATHER || action == Action.KNEEL) {
            return 0.0;
        }
        return vulnerable > 0 ? barrierSkill().param("broken", 1.5) : 1.0;
    }

    /** HPが尽きても、戦いの間は倒れない（全員が集まるのを待つ）。 */
    @Override
    public void setHealth(float health) {
        BossRoom room = room();
        if (health <= 0.0F && !allowDeath && isAlive() && room != null && room.fighting() && level() instanceof ServerLevel level) {
            super.setHealth(Math.max(0.5F, Math.min(getMaxHealth(), 0.5F)));
            setEnemyHp(1.0);
            if (action != Action.GATHER && action != Action.KNEEL) {
                startGather(level);
            }
            return;
        }
        super.setHealth(health);
    }

    // ---------------------------------------------------------------- 戦い

    @Override
    public void onFightStarted(ServerLevel level, List<ServerPlayer> participants) {
        BossRoom room = room();
        int alert = room == null ? 0 : AltarService.alert(level, room.floor().number());
        if (alert > 0) {
            setAttackMultiplier(attackMultiplier() * (1.0 + AltarService.ALERT_ATTACK_PERCENT / 100.0 * alert));
            for (ServerPlayer player : participants) {
                player.displayClientMessage(Component.translatable("boss.amemaru.ancient_guardian.alerted", alert,
                        alert * AltarService.ALERT_ATTACK_PERCENT).withStyle(ChatFormatting.RED), false);
            }
        }
        daisTicks.clear();
        allowDeath = false;
        judgeCooldown = -1;
        barrierCooldown = -1;
        line("boss.amemaru.ancient_guardian.line.start");
        tipOnce("start");
    }

    @Override
    protected void onPhaseChanged(ServerLevel level, int from, int to) {
        if (to == 2 && from < 2) {
            cancelAction();
            raiseBarrier(level);
        }
        if (to >= 3 && from < 3) {
            if (barrier()) {
                breakBarrier(level, false);
            }
            barrierCooldown = -1;
            judgeCooldown = (int) Math.round(judgeSkill().param("first", 6.0) * 20);
            line("boss.amemaru.ancient_guardian.line.judge");
            tipOnce("judge");
        }
    }

    @Override
    public void resetFight() {
        super.resetFight();
        if (level() instanceof ServerLevel level) {
            lightDaises(level, false);
        }
        cancelAction();
        action = Action.NONE;
        setBarrierFlag(false);
        entityData.set(DATA_KNEEL, false);
        barrierHold = 0;
        barrierCooldown = -1;
        judgeCooldown = -1;
        vulnerable = 0;
        allowDeath = false;
        daisTicks.clear();
        setStatus("", 0);
        discardSummoned();
    }

    private void cancelAction() {
        clearTelegraph();
        slamAt = null;
        flip = false;
        flipped = false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive() || isRemoved()) {
            return;
        }
        if (tickCount % 8 == 0) {
            // 胸の記録核の翠の光
            level.sendParticles(JADE, getX(), getY() + getBbHeight() * 0.62, getZ(), 2, 0.5, 0.4, 0.5, 0.0);
        }
        BossRoom room = room();
        if (room == null || !room.fighting()) {
            if (action != Action.NONE) {
                action = Action.NONE;
                cancelAction();
            }
            return;
        }
        if (vulnerable > 0) {
            vulnerable--;
        }
        if (barrier()) {
            tickBarrier(level);
        } else if (phase() == 2 && barrierCooldown > 0 && --barrierCooldown == 0 && action != Action.GATHER && action != Action.KNEEL) {
            raiseBarrier(level);
        }
        if (phase() >= 3 && judgeCooldown > 0 && action == Action.NONE) {
            judgeCooldown--;
        }
        switch (action) {
            case NONE -> choose(level);
            case SWEEP -> tickSweep(level);
            case BEAM -> tickBeam(level);
            case SLAM -> tickSlam(level);
            case JUDGE -> tickJudge(level);
            case GATHER -> tickGather(level);
            case KNEEL -> tickKneel(level);
            case RECOVER -> {
                if (--timer <= 0) {
                    action = Action.NONE;
                }
            }
        }
    }

    private void tipOnce(String key) {
        if (tips.add(key)) {
            tip("boss.amemaru.ancient_guardian.tip." + key);
        }
    }

    private List<ServerPlayer> targets(ServerLevel level) {
        List<ServerPlayer> list = new ArrayList<>();
        BossRoom room = room();
        if (room == null) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceTo(this) < 40 && !player.isCreative() && !player.isSpectator()) {
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

    private List<BlockPos> marks(String name) {
        BossRoom room = room();
        if (room == null) {
            return List.of();
        }
        FloorDef def = room.floor();
        List<BlockPos> list = new ArrayList<>();
        for (BlockPos p : def.features().bossMarks(name)) {
            list.add(def.world(p));
        }
        return list;
    }

    @Nullable
    private Vec3 center() {
        List<BlockPos> list = marks(CENTER);
        return list.isEmpty() ? null : Vec3.atBottomCenterOf(list.get(0));
    }

    // ---------------------------------------------------------------- 技を選ぶ

    private void choose(ServerLevel level) {
        if (isBusy()) {
            return;
        }
        List<ServerPlayer> players = targets(level);
        if (players.isEmpty()) {
            return;
        }
        if (phase() >= 3 && judgeCooldown == 0 && players.size() >= 1) {
            startJudge(level, players);
            return;
        }
        ServerPlayer top = topThreat();
        if (top == null || !players.contains(top)) {
            top = players.get(random.nextInt(players.size()));
        }
        List<Action> options = new ArrayList<>();
        if (ready("sweep") && top.distanceTo(this) <= sweepSkill().range() + 1.5) {
            options.add(Action.SWEEP);
            options.add(Action.SWEEP);
        }
        if (ready("beam")) {
            options.add(Action.BEAM);
        }
        if (ready("slam")) {
            options.add(Action.SLAM);
        }
        if (options.isEmpty()) {
            if (top.distanceTo(this) > sweepSkill().range() - 1.0) {
                getNavigation().moveTo(top, 1.0);
            }
            return;
        }
        getNavigation().stop();
        switch (options.get(random.nextInt(options.size()))) {
            case SWEEP -> startSweep(level, top);
            case BEAM -> startBeam(level, players.get(random.nextInt(players.size())));
            case SLAM -> startSlam(level, top);
            default -> {
            }
        }
    }

    // ---- 腕の薙ぎ払い（扇。欠けた左腕は、ときどき向きが裏返る）

    private void startSweep(ServerLevel level, ServerPlayer target) {
        EnemyDef.Skill sweep = sweepSkill();
        action = Action.SWEEP;
        faceTowards(target.position());
        lockedYaw = getYRot();
        timer = Math.max(1, sweep.windupTicks());
        flip = random.nextDouble() < sweep.param("flip_chance", 0.3);
        flipped = false;
        setBusy(timer + 10);
        playAction("skill_sweep");
        setCast(flip ? "sweep_lost" : "sweep", timer);
        setTelegraph(TELEGRAPH_CONE, timer, lockedYaw, (float) sweep.range(), (float) sweep.param("angle", 140.0));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    private void tickSweep(ServerLevel level) {
        EnemyDef.Skill sweep = sweepSkill();
        int flipAt = (int) Math.round(sweep.param("flip_at", 0.45) * 20);
        if (flip && !flipped && timer <= flipAt) {
            // 欠けた左腕：判定が狂っている。打つ直前に、裏へ向きが変わる
            flipped = true;
            lockedYaw = lockedYaw + 180.0F;
            setTelegraph(TELEGRAPH_CONE, timer, lockedYaw, (float) sweep.range(), (float) sweep.param("angle", 140.0));
            level.sendParticles(ParticleTypes.FLASH, getX(), getY() + getBbHeight() * 0.6, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.WHITE_ASH, getX(), getY() + getBbHeight() * 0.6, getZ(), 40, 1.2, 1.0, 1.2, 0.05);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 2.0F, 0.6F);
            tipOnce("lost_arm");
        }
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        if (--timer > 0) {
            return;
        }
        clearTelegraph();
        double half = sweep.param("angle", 140.0) / 2.0;
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) - player.getBbWidth() / 2.0 <= sweep.range() + 0.6 && inFront(player.position(), half)
                    && EnemyCombat.strike(this, player, sweep.power(), null)) {
                Vec3 away = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
                if (away.lengthSqr() > 1.0E-4) {
                    away = away.normalize().scale(sweep.param("push", 1.1));
                    player.push(away.x, 0.25, away.z);
                    player.hurtMarked = true;
                }
            }
        }
        double yawRad = Math.toRadians(lockedYaw);
        for (int i = 0; i <= 14; i++) {
            double a = yawRad + Math.toRadians(-half + half * 2.0 * i / 14.0);
            for (double r = 2.0; r <= sweep.range(); r += 2.0) {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() - Math.sin(a) * r, getY() + 1.0, getZ() + Math.cos(a) * r, 1,
                        0.0, 0.0, 0.0, 0.0);
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0F, 0.5F);
        startCooldown("sweep", cooldown(sweep));
        action = Action.RECOVER;
        timer = 10;
    }

    // ---- 直線の光線

    private void startBeam(ServerLevel level, ServerPlayer target) {
        EnemyDef.Skill beam = beamSkill();
        action = Action.BEAM;
        faceTowards(target.position());
        lockedYaw = getYRot();
        beamDir = Vec3.directionFromRotation(0.0F, lockedYaw);
        timer = Math.max(1, beam.windupTicks());
        setBusy(timer + 12);
        playAction("skill_beam");
        setCast("beam", timer);
        setTelegraph(TELEGRAPH_LINE, timer, lockedYaw, (float) beam.range(), (float) beam.param("width", 2.4));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0F, 0.6F);
        tipOnce("beam");
    }

    private void tickBeam(ServerLevel level) {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        if (--timer > 0) {
            if (timer % 3 == 0) {
                Vec3 core = position().add(0.0, getBbHeight() * 0.62, 0.0);
                level.sendParticles(JADE, core.x, core.y, core.z, 6, 0.4, 0.4, 0.4, 0.0);
            }
            return;
        }
        clearTelegraph();
        EnemyDef.Skill beam = beamSkill();
        double half = beam.param("width", 2.4) / 2.0 + 0.4;
        for (ServerPlayer player : targets(level)) {
            Vec3 rel = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            double along = rel.dot(beamDir);
            double side = rel.subtract(beamDir.scale(along)).length();
            if (along >= 0.0 && along <= beam.range() + 0.6 && side <= half && Math.abs(player.getY() - getY()) < 4.0) {
                EnemyCombat.strike(this, player, beam.power(), null);
            }
        }
        Vec3 from = position().add(0.0, getBbHeight() * 0.62, 0.0);
        for (double t = 1.0; t <= beam.range(); t += 0.7) {
            Vec3 at = from.add(beamDir.scale(t)).add(0.0, -Math.min(1.0, t / 8.0) * (getBbHeight() * 0.62 - 1.0), 0.0);
            level.sendParticles(JADE, at.x, at.y, at.z, 3, 0.15, 0.15, 0.15, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.0F, 0.6F);
        startCooldown("beam", cooldown(beam));
        action = Action.RECOVER;
        timer = 12;
    }

    // ---- ヘイト1位への叩きつけ

    private void startSlam(ServerLevel level, ServerPlayer target) {
        EnemyDef.Skill slam = slamSkill();
        action = Action.SLAM;
        faceTowards(target.position());
        lockedYaw = getYRot();
        slamAt = target.position();
        timer = Math.max(10, slam.windupTicks());
        GroundMarker.place(level, slamAt, (float) slam.param("radius", 3.2), timer, GroundMarker.KIND_DANGER);
        setBusy(timer + 10);
        playAction("skill_slam");
        setCast("slam", timer);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 2.0F, 0.5F);
        tipOnce("slam");
    }

    private void tickSlam(ServerLevel level) {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        if (--timer > 0 || slamAt == null) {
            return;
        }
        EnemyDef.Skill slam = slamSkill();
        double radius = slam.param("radius", 3.2) + 0.3;
        for (ServerPlayer player : targets(level)) {
            double dx = player.getX() - slamAt.x;
            double dz = player.getZ() - slamAt.z;
            if (dx * dx + dz * dz <= radius * radius && Math.abs(player.getY() - slamAt.y) < 3.0) {
                EnemyCombat.strike(this, player, slam.power(), null);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, slamAt.x, slamAt.y + 0.5, slamAt.z, 3, 1.0, 0.2, 1.0, 0.0);
        level.sendParticles(JADE, slamAt.x, slamAt.y + 0.3, slamAt.z, 30, 1.6, 0.2, 1.6, 0.0);
        level.playSound(null, slamAt.x, slamAt.y, slamAt.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 0.7F);
        slamAt = null;
        startCooldown("slam", cooldown(slam));
        action = Action.RECOVER;
        timer = 14;
    }

    // ---------------------------------------------------------------- 結界と結び台

    /** 結び台を何人で押さえればよいか（戦う人が4人より少なければ、その人数）。 */
    public int requiredDaises(int fighters) {
        return Math.max(1, Math.min(4, Math.min(marksCount(), fighters)));
    }

    private int marksCount() {
        int n = marks(DAISES).size();
        return n == 0 ? 4 : n;
    }

    private void lightDaises(ServerLevel level, boolean lit) {
        for (BlockPos pos : marks(DAISES)) {
            AltarBlocks.BindingDais.setLit(level, pos, lit);
        }
    }

    private void raiseBarrier(ServerLevel level) {
        setBarrierFlag(true);
        barrierHold = 0;
        callTimer = 40;
        lightDaises(level, true);
        playAction("skill_barrier");
        line("boss.amemaru.ancient_guardian.line.barrier");
        tipOnce("barrier");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.5F, 0.5F);
    }

    private void breakBarrier(ServerLevel level, boolean byDaises) {
        setBarrierFlag(false);
        lightDaises(level, false);
        barrierHold = 0;
        barrierCooldown = phase() == 2 ? cooldown(barrierSkill()) : -1;
        if (byDaises) {
            cancelAction();
            action = Action.RECOVER;
            timer = (int) Math.round(barrierSkill().param("stagger", 4.0) * 20);
            setBusy(timer);
            vulnerable = (int) Math.round(barrierSkill().param("broken_seconds", 10.0) * 20);
            playAction("skill_stagger");
            line("boss.amemaru.ancient_guardian.line.broken");
            tipOnce("broken");
            BossRoom room = room();
            if (room != null) {
                for (ServerPlayer p : room.participants()) {
                    p.displayClientMessage(Component.translatable("boss.amemaru.ancient_guardian.broken",
                            Math.round(barrierSkill().param("broken_seconds", 10.0))).withStyle(ChatFormatting.GOLD), true);
                }
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.5F, 0.6F);
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() * 0.5, getZ(), 80, 2.0, 2.0, 2.0, 0.2);
    }

    /** 結び台に乗っている人（台ごと）。 */
    private int countOnDaises(ServerLevel level, List<ServerPlayer> players) {
        int on = 0;
        for (BlockPos pos : marks(DAISES)) {
            Vec3 c = Vec3.atBottomCenterOf(pos);
            boolean taken = false;
            for (ServerPlayer player : players) {
                double dx = player.getX() - c.x;
                double dz = player.getZ() - c.z;
                if (dx * dx + dz * dz <= 1.5 * 1.5 && Math.abs(player.getY() - (c.y + 0.2)) < 1.6) {
                    taken = true;
                    daisTicks.merge(player.getUUID(), 1, Integer::sum);
                }
            }
            if (taken) {
                on++;
            }
        }
        return on;
    }

    private void tickBarrier(ServerLevel level) {
        List<ServerPlayer> players = targets(level);
        int required = requiredDaises(players.size());
        int on = countOnDaises(level, players);
        int hold = (int) Math.round(barrierSkill().param("hold", 3.0) * 20);
        if (on >= required) {
            barrierHold++;
            if (barrierHold % 10 == 0) {
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.HOSTILE, 1.5F,
                        0.8F + barrierHold / (float) hold);
            }
        } else {
            barrierHold = Math.max(0, barrierHold - 2);
        }
        if (tickCount % 5 == 0) {
            setStatus("barrier_" + required, on);
            // 柱から守護者へ伸びる結界の光
            Vec3 core = position().add(0.0, getBbHeight() * 0.5, 0.0);
            for (BlockPos pillar : marks(PILLARS)) {
                Vec3 top = Vec3.atCenterOf(pillar).add(0.0, 2.0, 0.0);
                Vec3 step = core.subtract(top);
                for (int i = 0; i <= 10; i++) {
                    Vec3 at = top.add(step.scale(i / 10.0));
                    level.sendParticles(JADE, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
                }
            }
        }
        if (barrierHold >= hold) {
            breakBarrier(level, true);
            return;
        }
        // 結界の間：防衛兵と修復ドローンを呼ぶ（乗っている人を、残りの人が守る）
        if (--callTimer <= 0) {
            callTimer = (int) Math.round(barrierSkill().param("call_every", 15.0) * 20);
            callGuards(level, players);
        }
    }

    private void callGuards(ServerLevel level, List<ServerPlayer> players) {
        summoned.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        int max = (int) Math.round(barrierSkill().param("max_guards", 5.0));
        int sentinels = (int) Math.round(barrierSkill().param("sentinels", 2.0));
        int drones = (int) Math.round(barrierSkill().param("drones", 1.0));
        List<AmemaruMob> made = new ArrayList<>();
        for (int i = 0; i < sentinels + drones && summoned.size() + made.size() < max; i++) {
            AmemaruMob mob = i < sentinels ? ModEntities.SENTINEL.get().create(level) : ModEntities.REPAIR_DRONE.get().create(level);
            if (mob == null) {
                continue;
            }
            double a = Math.PI * 2.0 * i / Math.max(1, sentinels + drones) + random.nextDouble();
            mob.moveTo(getX() + Math.cos(a) * 5.0, getY() + (mob instanceof RepairDrone ? 2.0 : 0.2), getZ() + Math.sin(a) * 5.0,
                    random.nextFloat() * 360.0F, 0.0F);
            mob.setHome(BlockPos.containing(position()));
            mob.setTransient(true);
            level.addFreshEntity(mob);
            made.add(mob);
            if (!players.isEmpty() && mob instanceof Sentinel) {
                mob.addThreat(players.get(random.nextInt(players.size())), 2.0);
            }
            level.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 0.5, mob.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        }
        for (AmemaruMob mob : made) {
            summoned.add(mob.getUUID());
        }
        if (!made.isEmpty()) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.5F, 1.2F);
            tipOnce("guards");
        }
    }

    private void discardSummoned() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        for (UUID id : summoned) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        summoned.clear();
    }

    // ---------------------------------------------------------------- 接続判定

    private void startJudge(ServerLevel level, List<ServerPlayer> players) {
        EnemyDef.Skill judge = judgeSkill();
        action = Action.JUDGE;
        timer = Math.max(20, judge.windupTicks());
        setBusy(timer + 10);
        playAction("skill_judge");
        setCast("judge", timer);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 2.0F, 0.7F);
    }

    private void tickJudge(ServerLevel level) {
        EnemyDef.Skill judge = judgeSkill();
        double range = judge.range();
        List<ServerPlayer> players = targets(level);
        if (timer % 5 == 0) {
            // ひとりひとりのまわりに、6ブロックの輪（味方が中にいれば緑、いなければ赤）
            for (ServerPlayer player : players) {
                boolean linked = linked(player, players, range);
                for (int i = 0; i < 24; i++) {
                    double a = Math.PI * 2.0 * i / 24.0;
                    level.sendParticles(linked ? LINKED : ALONE, player.getX() + Math.cos(a) * range, player.getY() + 0.2,
                            player.getZ() + Math.sin(a) * range, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            setStatus("judge", (timer + 19) / 20);
        }
        if (--timer > 0) {
            return;
        }
        if (players.size() <= 1) {
            // ひとりの挑戦者は、判定しない【補完】
            for (ServerPlayer player : players) {
                player.displayClientMessage(Component.translatable("boss.amemaru.ancient_guardian.judge_alone").withStyle(ChatFormatting.GRAY), true);
            }
        } else {
            for (ServerPlayer player : players) {
                if (linked(player, players, range)) {
                    EnemyCombat.strike(this, player, judge.power() * judge.param("linked", 0.12), null);
                } else {
                    EnemyCombat.strike(this, player, judge.power(), null);
                    level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                    player.displayClientMessage(Component.translatable("boss.amemaru.ancient_guardian.judged").withStyle(ChatFormatting.RED), true);
                }
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.5F, 0.8F);
        setStatus("", 0);
        judgeCooldown = cooldown(judge);
        action = Action.RECOVER;
        timer = 16;
    }

    /** 6ブロック以内に、倒れていない味方がいるか。 */
    public static boolean linked(ServerPlayer player, List<ServerPlayer> others, double range) {
        for (ServerPlayer other : others) {
            if (other != player && other.isAlive() && !BossRoom.isDown(other) && other.distanceTo(player) <= range) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 最後：集まると、膝をつく

    private void startGather(ServerLevel level) {
        cancelAction();
        if (barrier()) {
            setBarrierFlag(false);
            lightDaises(level, false);
        }
        discardSummoned();
        action = Action.GATHER;
        timer = (int) Math.round(skill("gather", 0.0, 0.0, 0.0, 5.0).param("timeout", 60.0) * 20);
        entityData.set(DATA_KNEEL, true);
        setBusy(timer + 200);
        getNavigation().stop();
        playAction("skill_kneel");
        line("boss.amemaru.ancient_guardian.line.gather");
        tipOnce("gather");
        Vec3 c = center();
        if (c != null) {
            GroundMarker.place(level, c, (float) skill("gather", 0.0, 0.0, 0.0, 5.0).range(), 100, GroundMarker.KIND_SHARE);
        }
    }

    private void tickGather(ServerLevel level) {
        Vec3 c = center();
        double radius = skill("gather", 0.0, 0.0, 0.0, 5.0).range();
        List<ServerPlayer> players = targets(level);
        if (c != null && timer % 100 == 0) {
            GroundMarker.place(level, c, (float) radius, 100, GroundMarker.KIND_SHARE);
        }
        int inside = 0;
        for (ServerPlayer player : players) {
            if (c == null || player.position().distanceTo(new Vec3(c.x, player.getY(), c.z)) <= radius + 0.5) {
                inside++;
            }
        }
        if (timer % 10 == 0) {
            setStatus("gather", inside);
        }
        if ((!players.isEmpty() && inside >= players.size()) || --timer <= 0) {
            action = Action.KNEEL;
            timer = 80;
            playAction("skill_kneel");
            line("boss.amemaru.ancient_guardian.line.kneel");
            setStatus("", 0);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_DEATH, SoundSource.HOSTILE, 2.0F, 0.5F);
        }
    }

    private void tickKneel(ServerLevel level) {
        if (timer % 10 == 0) {
            level.sendParticles(JADE, getX(), getY() + getBbHeight() * 0.4, getZ(), 12, 0.8, 0.6, 0.8, 0.02);
        }
        if (--timer > 0) {
            return;
        }
        allowDeath = true;
        setEnemyHp(0.0);
        super.setHealth(0.0F);
        die(damageSources().genericKill());
    }

    // ---------------------------------------------------------------- 管理者・自動テスト

    @Override
    public List<String> skillNames() {
        return SKILLS;
    }

    @Override
    public boolean forceSkill(String name) {
        BossRoom room = room();
        if (room == null || !room.fighting() || !(level() instanceof ServerLevel level)) {
            return false;
        }
        List<ServerPlayer> players = targets(level);
        if (players.isEmpty() && !"barrier".equals(name) && !"gather".equals(name)) {
            return false;
        }
        cancelAction();
        action = Action.NONE;
        clearBusy();
        switch (name) {
            case "sweep" -> startSweep(level, players.get(0));
            case "beam" -> startBeam(level, players.get(0));
            case "slam" -> startSlam(level, players.get(0));
            case "barrier" -> {
                if (!barrier()) {
                    raiseBarrier(level);
                }
            }
            case "judge" -> startJudge(level, players);
            case "gather" -> startGather(level);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** 自動テスト用：集まるのを待つ形にする。 */
    public void testGather() {
        if (level() instanceof ServerLevel level) {
            startGather(level);
        }
    }

    /** 自動テスト用：結び台に乗った tick を数える。 */
    public Map<UUID, Integer> testDaisTicks() {
        return daisTicks;
    }

    // ---------------------------------------------------------------- 第七の門鍵

    @Override
    public void onVictory(ServerLevel level, List<ServerPlayer> winners, boolean first) {
        // 古代記録「守護者の記録核」：守護者が身の内に抱えていた記録（倒した全員の記録帳に残る）
        ResourceLocation core = AmemaruMod.id("f05_guardian_core");
        jp.amemaru.lostconnection.guild.RecordDef record = jp.amemaru.lostconnection.guild.RecordDefs.get(core);
        for (ServerPlayer player : winners) {
            // 5日目の物語：守護者を倒した人（ガルドの昇格式の会話の条件）
            jp.amemaru.lostconnection.player.PlayerDataCapability.get(player)
                    .ifPresent(data -> data.getStory().add(AmemaruMod.id("story/guardian_defeated")));
            jp.amemaru.lostconnection.guild.RecordService.find(player, core);
            if (record != null && !record.pages().isEmpty()) {
                jp.amemaru.lostconnection.network.ModNetwork.sendTo(player, new jp.amemaru.lostconnection.network.s2c.StoryTextS2C(
                        jp.amemaru.lostconnection.network.s2c.StoryTextS2C.Kind.ANCIENT, record.title(), record.pages().get(0), 200));
            }
        }
        if (first && !winners.isEmpty()) {
            giveGateKey(level, keyHolder(winners, daisTicks));
        }
    }

    /** 結び台に一番長く乗っていた人（同じなら先の人）。 */
    public static ServerPlayer keyHolder(List<ServerPlayer> winners, Map<UUID, Integer> ticks) {
        ServerPlayer best = winners.get(0);
        int most = -1;
        for (ServerPlayer player : winners) {
            int t = ticks.getOrDefault(player.getUUID(), 0);
            if (t > most) {
                most = t;
                best = player;
            }
        }
        return best;
    }

    /** 第七の門鍵を渡す（取引できない）。 */
    public static void giveGateKey(ServerLevel level, ServerPlayer player) {
        Item item = ForgeRegistries.ITEMS.getValue(GATE_KEY);
        if (item == null) {
            return;
        }
        ItemStack stack = new ItemStack(item);
        AccessoryService.ensureRolled(stack, level.random);
        stack.getOrCreateTag().putBoolean(GearData.BOUND, true);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        ProgressionService.broadcast(level.getServer(), Component.translatable("message.amemaru.guardian.gate_key", player.getDisplayName()),
                jp.amemaru.lostconnection.network.s2c.AnnouncementS2C.Kind.BOSS);
        AmemaruMod.LOGGER.info("[amemaru] {} received the Seventh Gate Key", player.getGameProfile().getName());
    }
}

package jp.amemaru.lostconnection.enemy.boss;

import jp.amemaru.lostconnection.combat.LostService;
import jp.amemaru.lostconnection.combat.PlayerColdService;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.entity.GroundMarker;
import jp.amemaru.lostconnection.tower.BossRoom;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.lake.BellService;
import jp.amemaru.lostconnection.tower.lake.ThinIceService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 8階のボス、声なき湖主ムーネ（11月25日。Phase 0-A 9章の8階：「『静寂』の間は効果音・予兆表示・パーティHUDが消え、ボイスチャットの
 * 声かけだけが頼りになる」）。数値と技は【補完】。
 *
 * <ul>
 *   <li>氷の下を泳ぐ大きな湖の主（白い鱗に、光る長いひげ）。氷の下にいる間は、叩いても届かない（影だけが見える）</li>
 *   <li>突き上げ（rise）：氷の下から、狙った人の足もとに氷のひびの印。氷を割って跳ね上げる（割れた所は水の穴）。何回か突き上げると、
 *       最後の穴から氷の上へ出て、しばらく（surface 秒）叩ける。氷の上では、髭払い（sweep。前の扇）・尾打ち（tail。まわりの円。
 *       まわりの薄氷にひび）を使い、時間が来ると氷の下へ戻る</li>
 *   <li>静寂の帳（veil）：音を出さずに歌う。参加者全員がロスト状態「静寂」の強さ1（効果音と予兆の音・予兆の印・パーティHUDが消える）。
 *       湖心のふちの鳴り石（4つ）のうち、<b>違う2つを {@code window} 秒のうちに鳴らす</b>と帳が破れ、静寂がほどけ、湖主は氷の上に
 *       浮かんで動けなくなる（daze 秒。受けるダメージ broken 倍）。ひとりでは鳴らせない間合い（ボイスチャットで声をかけ合う）</li>
 *   <li>断ち波（wave。第2段階から）：湖のまん中から、氷の下を波が広がる。波が通るときに足が氷についていると、ロスト状態「断線」
 *       （仲間のHPが見えない）。跳んでよける</li>
 *   <li>第3段階：静寂の帳の間が短くなり、突き上げが全員を狙う</li>
 * </ul>
 */
public class Mune extends AmemaruBoss {
    public static final List<String> SKILLS = List.of("rise", "sweep", "tail", "veil", "wave");
    private static final EntityDataAccessor<Boolean> DATA_SUBMERGED = SynchedEntityData.defineId(Mune.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions WHISKER = new DustParticleOptions(new Vector3f(0.75F, 0.95F, 1.0F), 1.5F);
    private static final DustParticleOptions HUSH = new DustParticleOptions(new Vector3f(0.62F, 0.66F, 0.74F), 2.0F);

    private enum Action { NONE, RISE, SWEEP, TAIL, VEIL, WAVE, RECOVER }

    private Action action = Action.NONE;
    private int timer;
    private float lockedYaw;
    private final List<Vec3> marks = new ArrayList<>();
    private int risesLeft = -1;
    /** 氷の上にいられる残り（この tick まで）。 */
    private int surfacedUntil;
    /** 帳が破れて動けない間（この tick まで）。 */
    private int dazedUntil;
    /** 静寂の帳の終わり（この tick まで）。 */
    private int veilUntil;
    private final Map<BlockPos, Integer> bellRings = new LinkedHashMap<>();
    @Nullable
    private Vec3 waveCenter;
    private double waveRadius;
    private final Set<UUID> waveHit = new HashSet<>();
    private final Set<String> tips = new HashSet<>();
    private int iceY = Integer.MIN_VALUE;
    @Nullable
    private Vec3 center;
    private boolean veilQueued;

    public Mune(EntityType<? extends Mune> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    public String assetName() {
        return "mune";
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SUBMERGED, true);
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_rise", "skill_sweep", "skill_tail", "skill_veil", "skill_wave", "skill_dive", "dazed"};
    }

    @Override
    protected int phaseFor(double ratio) {
        if (ratio <= 0.35) {
            return 3;
        }
        return ratio <= 0.7 ? 2 : 1;
    }

    /** 氷の下にいるか（このときは叩いても届かない）。 */
    public boolean submerged() {
        return entityData.get(DATA_SUBMERGED);
    }

    private void setSubmerged(boolean value) {
        entityData.set(DATA_SUBMERGED, value);
    }

    /** 静寂の帳が破れて、動けないでいるか。 */
    public boolean dazed() {
        return dazedUntil > tickCount;
    }

    /** 静寂の帳の最中か。 */
    public boolean veiled() {
        return veilUntil > tickCount;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public boolean isPickable() {
        return !submerged() && super.isPickable();
    }

    @Override
    public double damageTakenMultiplier(@Nullable Entity attacker) {
        if (submerged()) {
            return 0.0;
        }
        return dazed() ? veilSkill().param("broken", 1.3) : 1.0;
    }

    // ---------------------------------------------------------------- 技の数値

    private EnemyDef.Skill skill(String name, double power, double windup, double cooldown, double range) {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(power, windup, cooldown, range, Map.of()) : def.skill(name, power, windup, cooldown, range);
    }

    private EnemyDef.Skill riseSkill() {
        return skill("rise", 1.4, 1.6, 6.0, 2.8);
    }

    private EnemyDef.Skill sweepSkill() {
        return skill("sweep", 1.3, 1.3, 5.0, 9.0);
    }

    private EnemyDef.Skill tailSkill() {
        return skill("tail", 1.5, 1.5, 8.0, 6.5);
    }

    private EnemyDef.Skill veilSkill() {
        return skill("veil", 0.0, 2.0, 70.0, 0.0);
    }

    private EnemyDef.Skill waveSkill() {
        return skill("wave", 1.2, 1.8, 30.0, 46.0);
    }

    private int cooldown(EnemyDef.Skill skill) {
        double scale = phase() >= 3 ? 0.75 : phase() == 2 ? 0.88 : 1.0;
        return (int) Math.round(skill.cooldownTicks() * scale * pace());
    }

    private float animSpeed(EnemyDef.Skill skill) {
        double anim = skill.param("anim_windup", skill.windup());
        return (float) Math.max(0.4, Math.min(1.0, anim / Math.max(0.1, skill.windup())));
    }

    // ---------------------------------------------------------------- 湖

    /** 湖の氷の高さと、湖心のまん中（部屋の階の定義から。部屋がなければ、今いる所から）。 */
    private void findLake() {
        BossRoom room = room();
        FloorDef def = room == null ? null : room.floor();
        if (def != null && def.features().lake() != null) {
            iceY = (int) Math.round(def.originVec().y) + def.features().lake().iceY();
            center = room.arenaCenter();
        } else {
            iceY = (int) Math.floor(getY()) + 4;
            center = position();
        }
    }

    private double swimY() {
        return iceY - 4.5;
    }

    /** 湖心の鳴り石（階の boss_marks の arena_bells）。 */
    public List<BlockPos> arenaBells() {
        BossRoom room = room();
        if (room == null) {
            return List.of();
        }
        List<BlockPos> list = new ArrayList<>();
        for (BlockPos local : room.floor().features().bossMarks().getOrDefault("arena_bells", List.of())) {
            list.add(room.floor().world(local));
        }
        return list;
    }

    // ---------------------------------------------------------------- 戦いの始まり・終わり

    @Override
    public void onFightStarted(ServerLevel level, List<ServerPlayer> participants) {
        findLake();
        surface(level, false);
        dive(level, false);
        risesLeft = -1;
        startCooldown("veil", (int) Math.round(veilSkill().param("first", 25.0) * 20));
        startCooldown("wave", (int) Math.round(waveSkill().param("first", 10.0) * 20));
        startCooldown("rise", 40);
        line("boss.amemaru.mune.line.start");
        tipOnce("start");
    }

    @Override
    protected void onPhaseChanged(ServerLevel level, int from, int to) {
        if (to > from) {
            line("boss.amemaru.mune.line.phase" + Math.min(3, to));
            if (to >= 3) {
                veilQueued = true;
            }
        }
    }

    @Override
    public void resetFight() {
        super.resetFight();
        clearTelegraph();
        action = Action.NONE;
        marks.clear();
        risesLeft = -1;
        surfacedUntil = 0;
        dazedUntil = 0;
        veilUntil = 0;
        veilQueued = false;
        bellRings.clear();
        waveCenter = null;
        waveHit.clear();
        setStatus("", 0);
        if (level() instanceof ServerLevel level) {
            if (iceY == Integer.MIN_VALUE) {
                findLake();
            }
            dive(level, false);
        }
    }

    // ---------------------------------------------------------------- 毎 tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (isAlive() && submerged() && random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.BUBBLE, getRandomX(1.5), getY() + 1.0, getRandomZ(1.5), 0.0, 0.05, 0.0);
            }
            return;
        }
        if (!(level() instanceof ServerLevel level) || !isAlive() || isRemoved()) {
            return;
        }
        if (iceY == Integer.MIN_VALUE) {
            findLake();
            dive(level, false);
        }
        setDeltaMovement(Vec3.ZERO);
        if (tickCount % 6 == 0) {
            level.sendParticles(WHISKER, getX(), getY() + 1.2, getZ(), 2, 1.2, 0.4, 1.2, 0.0);
        }
        BossRoom room = room();
        if (room == null || !room.fighting()) {
            if (action != Action.NONE) {
                action = Action.NONE;
                clearTelegraph();
            }
            return;
        }
        tickVeil(level);
        tickWave(level);
        if (!submerged() && !dazed() && !veiled() && surfacedUntil > tickCount && tickCount % 20 == 0) {
            setStatus("surfaced", (surfacedUntil - tickCount + 19) / 20);
        }
        if (dazed()) {
            setStatus("dazed", (dazedUntil - tickCount + 19) / 20);
            if (dazedUntil - tickCount == 1) {
                setStatus("", 0);
                surfacedUntil = tickCount + 40;
            }
            return;
        }
        switch (action) {
            case NONE -> choose(level);
            case RISE -> tickRise(level);
            case SWEEP -> tickSweep(level);
            case TAIL -> tickTail(level);
            case VEIL -> tickVeilCast(level);
            case WAVE -> tickWaveCast(level);
            case RECOVER -> {
                if (--timer <= 0) {
                    action = Action.NONE;
                }
            }
        }
    }

    private void tipOnce(String key) {
        if (tips.add(key)) {
            tip("boss.amemaru.mune.tip." + key);
        }
    }

    private List<ServerPlayer> targets(ServerLevel level) {
        List<ServerPlayer> list = new ArrayList<>();
        BossRoom room = room();
        if (room == null) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceTo(this) < 48 && !player.isCreative() && !player.isSpectator()) {
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

    // ---------------------------------------------------------------- 技を選ぶ

    private void choose(ServerLevel level) {
        if (isBusy()) {
            return;
        }
        List<ServerPlayer> players = targets(level);
        if (players.isEmpty()) {
            return;
        }
        if ((veilQueued || ready("veil")) && !veiled()) {
            startVeil(level);
            return;
        }
        if (!submerged()) {
            if (tickCount >= surfacedUntil) {
                dive(level, true);
                return;
            }
            ServerPlayer top = topThreat();
            if (top == null || !players.contains(top)) {
                top = players.get(random.nextInt(players.size()));
            }
            int close = 0;
            for (ServerPlayer p : players) {
                if (p.distanceTo(this) <= tailSkill().range() + 0.5) {
                    close++;
                }
            }
            if (ready("tail") && (close >= 2 || close >= 1 && random.nextBoolean())) {
                startTail(level);
            } else if (ready("sweep") && top.distanceTo(this) <= sweepSkill().range() + 1.0) {
                startSweep(level, top);
            } else if (ready("tail") && close >= 1) {
                startTail(level);
            }
            return;
        }
        if (phase() >= 2 && ready("wave")) {
            startWave(level);
            return;
        }
        // 氷の下：狙った人の下へ泳ぎ、突き上げる
        ServerPlayer top = topThreat();
        if (top == null || !players.contains(top)) {
            top = players.get(random.nextInt(players.size()));
        }
        swimToward(level, top.position(), riseSkill().param("swim", 0.2));
        if (ready("rise")) {
            startRise(level, players, top);
        }
    }

    private void swimToward(ServerLevel level, Vec3 goal, double speed) {
        Vec3 to = new Vec3(goal.x - getX(), 0.0, goal.z - getZ());
        double flat = to.length();
        if (flat < 0.5) {
            return;
        }
        double step = Math.min(speed, flat);
        Vec3 next = position().add(to.scale(step / flat));
        if (center != null) {
            Vec3 off = new Vec3(next.x - center.x, 0.0, next.z - center.z);
            double max = arenaRadius() - 4.0;
            if (off.length() > max) {
                next = new Vec3(center.x, next.y, center.z).add(off.normalize().scale(max));
            }
        }
        setPos(next.x, swimY(), next.z);
        faceTowards(goal);
    }

    private double arenaRadius() {
        BossRoom room = room();
        return room == null ? 40.0 : Math.min(44.0, room.def().arenaRadius() - 8.0);
    }

    // ---- 突き上げ（氷の下から。氷のひびの印）

    private void startRise(ServerLevel level, List<ServerPlayer> players, ServerPlayer first) {
        EnemyDef.Skill rise = riseSkill();
        action = Action.RISE;
        marks.clear();
        int count = phase() >= 3 ? players.size() : phase() == 2 ? Math.min(2, players.size()) : 1;
        marks.add(onIce(first.position()));
        for (ServerPlayer p : players) {
            if (marks.size() >= count) {
                break;
            }
            if (p != first) {
                marks.add(onIce(p.position()));
            }
        }
        timer = Math.max(10, rise.windupTicks());
        setBusy(timer + 12);
        for (Vec3 at : marks) {
            GroundMarker.place(level, at, (float) rise.range(), timer, GroundMarker.KIND_CRACK);
        }
        setCast("rise", timer);
        if (risesLeft < 0) {
            risesLeft = (int) Math.round(rise.param(phase() >= 3 ? "surface_after_late" : "surface_after", 2.0));
        }
        level.playSound(null, marks.get(0).x, marks.get(0).y, marks.get(0).z, SoundEvents.GLASS_HIT, SoundSource.HOSTILE, 1.6F, 0.4F);
        tipOnce("rise");
    }

    private Vec3 onIce(Vec3 at) {
        return new Vec3(at.x, iceY + 1.0, at.z);
    }

    private void tickRise(ServerLevel level) {
        if (marks.isEmpty()) {
            action = Action.NONE;
            return;
        }
        Vec3 first = marks.get(0);
        swimToward(level, first, 0.6);
        if (timer % 4 == 0) {
            for (Vec3 at : marks) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), at.x, at.y + 0.05, at.z, 8,
                        1.2, 0.0, 1.2, 0.0);
            }
        }
        if (--timer > 0) {
            return;
        }
        EnemyDef.Skill rise = riseSkill();
        double radius = rise.range() + 0.3;
        int holeTicks = (int) Math.round(rise.param("hole_seconds", 12.0) * 20);
        for (Vec3 at : marks) {
            ThinIceService.breakIce(level, new Vec3(at.x, iceY + 0.5, at.z), rise.param("hole", 2.0), holeTicks);
            level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.4, at.z, 80, 1.4, 0.5, 1.4, 0.25);
            impactFx("rise", TELEGRAPH_CIRCLE, at, 0.0F, (float) radius, 0.0F);  // 11月27日：当たった瞬間の演出
            for (ServerPlayer player : targets(level)) {
                double dx = player.getX() - at.x;
                double dz = player.getZ() - at.z;
                if (dx * dx + dz * dz <= radius * radius && Math.abs(player.getY() - at.y) < 3.0
                        && EnemyCombat.strike(this, player, rise.power(), null)) {
                    player.push(0.0, rise.param("lift", 0.7), 0.0);
                    player.hurtMarked = true;
                    PlayerColdService.chill(player, 1, 80);
                }
            }
        }
        level.playSound(null, first.x, first.y, first.z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2.0F, 0.5F);
        startCooldown("rise", cooldown(rise));
        risesLeft--;
        if (risesLeft <= 0) {
            risesLeft = -1;
            setPos(first.x, swimY(), first.z);
            surface(level, true);
            surfacedUntil = tickCount + (int) Math.round(rise.param("surface", 10.0) * 20);
            tipOnce("surface");
        }
        marks.clear();
        action = Action.RECOVER;
        timer = 10;
    }

    /** 氷の上へ出る（穴のそばに。叩けるようになる）。 */
    private void surface(ServerLevel level, boolean splash) {
        setPos(getX(), iceY + 1.0, getZ());
        setSubmerged(false);
        if (splash) {
            ThinIceService.breakIce(level, new Vec3(getX(), iceY + 0.5, getZ()), 2.5, 200);
            level.sendParticles(ParticleTypes.SPLASH, getX(), iceY + 1.4, getZ(), 80, 1.8, 0.6, 1.8, 0.3);
            playAction("skill_rise");
            setStatus("surfaced", (int) Math.round(riseSkill().param("surface", 10.0)));
        }
    }

    /** 氷の下へ戻る。 */
    private void dive(ServerLevel level, boolean splash) {
        if (splash) {
            ThinIceService.breakIce(level, new Vec3(getX(), iceY + 0.5, getZ()), 2.0, 120);
            level.sendParticles(ParticleTypes.SPLASH, getX(), iceY + 1.4, getZ(), 50, 1.4, 0.4, 1.4, 0.2);
            playAction("skill_dive");
        }
        setPos(getX(), swimY(), getZ());
        setSubmerged(true);
        surfacedUntil = 0;
        if (!veiled() && !dazed()) {
            setStatus("", 0);
        }
    }

    // ---- 髭払い（前の扇）

    private void startSweep(ServerLevel level, ServerPlayer target) {
        EnemyDef.Skill sweep = sweepSkill();
        action = Action.SWEEP;
        faceTowards(target.position());
        lockedYaw = getYRot();
        timer = Math.max(1, sweep.windupTicks());
        setBusy(timer + 10);
        playAction("skill_sweep", animSpeed(sweep));
        setCast("sweep", timer);
        setTelegraph(TELEGRAPH_CONE, timer, lockedYaw, (float) sweep.range(), (float) sweep.param("angle", 120.0));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.HOSTILE, 1.6F, 0.6F);
    }

    private void tickSweep(ServerLevel level) {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        if (--timer > 0) {
            return;
        }
        impactFx("sweep");  // 11月27日：当たった瞬間の演出
        clearTelegraph();
        EnemyDef.Skill sweep = sweepSkill();
        double half = sweep.param("angle", 120.0) / 2.0;
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) - player.getBbWidth() / 2.0 <= sweep.range() + 0.6 && inFront(player.position(), half)
                    && EnemyCombat.strike(this, player, sweep.power(), null)) {
                Vec3 away = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
                if (away.lengthSqr() > 1.0E-4) {
                    away = away.normalize().scale(sweep.param("push", 1.0));
                    player.push(away.x, 0.2, away.z);
                    player.hurtMarked = true;
                }
            }
        }
        double yawRad = Math.toRadians(lockedYaw);
        for (int i = 0; i <= 12; i++) {
            double a = yawRad + Math.toRadians(-half + half * 2.0 * i / 12.0);
            for (double r = 1.5; r <= sweep.range(); r += 1.5) {
                level.sendParticles(WHISKER, getX() - Math.sin(a) * r, getY() + 0.8, getZ() + Math.cos(a) * r, 1, 0.1, 0.1, 0.1, 0.0);
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 1.4F, 0.6F);
        startCooldown("sweep", cooldown(sweep));
        action = Action.RECOVER;
        timer = 10;
    }

    // ---- 尾打ち（まわりの円。薄氷にひび）

    private void startTail(ServerLevel level) {
        EnemyDef.Skill tail = tailSkill();
        action = Action.TAIL;
        timer = Math.max(1, tail.windupTicks());
        setBusy(timer + 10);
        playAction("skill_tail", animSpeed(tail));
        setCast("tail", timer);
        setTelegraph(TELEGRAPH_CIRCLE, timer, 0.0F, (float) tail.range());
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.DOLPHIN_SPLASH, SoundSource.HOSTILE, 1.6F, 0.5F);
    }

    private void tickTail(ServerLevel level) {
        if (--timer > 0) {
            return;
        }
        impactFx("tail");  // 11月27日：当たった瞬間の演出
        clearTelegraph();
        EnemyDef.Skill tail = tailSkill();
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) <= tail.range() + 0.4 && EnemyCombat.strike(this, player, tail.power(), null)) {
                Vec3 away = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
                if (away.lengthSqr() > 1.0E-4) {
                    away = away.normalize().scale(0.8);
                    player.push(away.x, 0.3, away.z);
                    player.hurtMarked = true;
                }
            }
        }
        ThinIceService.crackAround(level, new Vec3(getX(), iceY + 0.5, getZ()), tail.param("crack_radius", 9.0),
                (int) Math.round(tail.param("crack", 2.0)));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), getX(), getY() + 0.2, getZ(), 80,
                tail.range() * 0.5, 0.2, tail.range() * 0.5, 0.1);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 0.7F);
        startCooldown("tail", cooldown(tail));
        action = Action.RECOVER;
        timer = 12;
    }

    // ---- 静寂の帳（全員を深い静寂に。違う鳴り石2つを、続けて鳴らすと破れる）

    private void startVeil(ServerLevel level) {
        EnemyDef.Skill veil = veilSkill();
        action = Action.VEIL;
        veilQueued = false;
        timer = Math.max(10, veil.windupTicks());
        setBusy(timer + 10);
        clearTelegraph();
        playAction("skill_veil", animSpeed(veil));
        setCast("veil", timer);
        line("boss.amemaru.mune.line.veil");
    }

    private void tickVeilCast(ServerLevel level) {
        if (timer % 4 == 0) {
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2.0 * i / 24;
                double r = 3.0 + (Math.max(0, 40 - timer) % 20) * 0.6;
                level.sendParticles(HUSH, getX() + Math.cos(a) * r, iceY + 1.3, getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (--timer > 0) {
            return;
        }
        castVeil(level);
        action = Action.RECOVER;
        timer = 10;
    }

    /** 静寂の帳を広げる（管理者・自動テストからも）。 */
    public void castVeil(ServerLevel level) {
        EnemyDef.Skill veil = veilSkill();
        // 11月26日：ひとり確認モードでは、ひとりで鳴り石を2つ回れるように、帳を長くする。11月27日：ひとりで戦っているときも
        int ticks = (int) Math.round(jp.amemaru.lostconnection.tower.SoloTest.seconds(veil.param("seconds", 20.0),
                jp.amemaru.lostconnection.tower.SoloTest.VEIL_LAST_SECONDS,
                jp.amemaru.lostconnection.tower.SoloTest.aloneInFight(targets(level))) * 20);
        for (ServerPlayer player : targets(level)) {
            LostService.inflict(player, ModEffects.LOST_SILENCE.get(), ticks, 1);
        }
        veilUntil = tickCount + ticks;
        bellRings.clear();
        startCooldown("veil", (int) Math.round(veil.cooldownTicks() * (phase() >= 3 ? veil.param("late_scale", 0.7) : 1.0) * pace()));
        setStatus("veil", ticks / 20);
        tipOnce("veil");
    }

    private void tickVeil(ServerLevel level) {
        if (veilUntil <= 0) {
            return;
        }
        if (veilUntil <= tickCount) {
            veilUntil = 0;
            bellRings.clear();
            if (!dazed()) {
                setStatus(submerged() ? "" : "surfaced", submerged() ? 0 : Math.max(0, (surfacedUntil - tickCount) / 20));
            }
            return;
        }
        if (tickCount % 20 == 0) {
            setStatus("veil", (veilUntil - tickCount + 19) / 20);
        }
    }

    /** 鳴り石が鳴った（静寂の帳の間に、違う2つが {@code window} 秒のうちに鳴ると、帳が破れる）。破れたら true。 */
    public boolean onBell(ServerPlayer player, BlockPos pos) {
        if (!(level() instanceof ServerLevel level)) {
            return false;
        }
        if (!veiled()) {
            player.displayClientMessage(Component.translatable("message.amemaru.mune.bell_quiet").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        EnemyDef.Skill veil = veilSkill();
        // 11月26日：ひとり確認モードでは、ひとりで次の鳴り石へ走れるように延ばす。11月27日：ひとりで戦っているときも
        int window = (int) Math.round(jp.amemaru.lostconnection.tower.SoloTest.seconds(veil.param("window", 3.0),
                jp.amemaru.lostconnection.tower.SoloTest.VEIL_SECONDS,
                jp.amemaru.lostconnection.tower.SoloTest.aloneInFight(targets(level))) * 20);
        bellRings.entrySet().removeIf(e -> tickCount - e.getValue() > window);
        bellRings.put(pos.immutable(), tickCount);
        if (bellRings.size() < 2) {
            for (ServerPlayer p : targets(level)) {
                p.displayClientMessage(Component.translatable("message.amemaru.mune.bell_one", window / 20).withStyle(ChatFormatting.AQUA), true);
            }
            return false;
        }
        breakVeil(level);
        return true;
    }

    /** 静寂の帳が破れる：静寂がほどけ、湖主は氷の上に浮かんで動けない（管理者・自動テストからも）。 */
    public void breakVeil(ServerLevel level) {
        EnemyDef.Skill veil = veilSkill();
        veilUntil = 0;
        bellRings.clear();
        for (ServerPlayer player : targets(level)) {
            if (player.hasEffect(ModEffects.LOST_SILENCE.get())) {
                player.removeEffect(ModEffects.LOST_SILENCE.get());
            }
        }
        clearTelegraph();
        action = Action.NONE;
        clearBusy();
        if (submerged()) {
            surface(level, true);
        }
        dazedUntil = tickCount + (int) Math.round(veil.param("daze", 8.0) * 20);
        playAction("dazed");
        setStatus("dazed", (int) Math.round(veil.param("daze", 8.0)));
        line("boss.amemaru.mune.line.broken");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0F, 0.8F);
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.5, getZ(), 60, 2.0, 1.0, 2.0, 0.1);
    }

    /** 鳴り石が鳴った（BellService から）：そばの湖主へ伝える。 */
    public static void ringArenaBell(ServerPlayer player, BlockPos pos) {
        for (Mune mune : player.serverLevel().getEntitiesOfClass(Mune.class, new AABB(pos).inflate(96.0))) {
            if (mune.isAlive()) {
                BellService.ripple(player.serverLevel(), pos, mune.veiled());
                mune.onBell(player, pos);
                return;
            }
        }
        player.displayClientMessage(Component.translatable("message.amemaru.mune.bell_quiet").withStyle(ChatFormatting.GRAY), true);
    }

    // ---- 断ち波（湖のまん中から、氷の下を波が広がる。足が氷についていると「断線」）

    private void startWave(ServerLevel level) {
        EnemyDef.Skill wave = waveSkill();
        action = Action.WAVE;
        timer = Math.max(10, wave.windupTicks());
        setBusy(timer + 10);
        Vec3 c = center == null ? position() : center;
        swimToward(level, c, 1.5);
        GroundMarker.place(level, onIce(c), 6.0F, timer, GroundMarker.KIND_SEVER);
        setCast("wave", timer);
        playAction("skill_wave", animSpeed(wave));
        tipOnce("wave");
    }

    private void tickWaveCast(ServerLevel level) {
        if (--timer > 0) {
            return;
        }
        startWaveNow(level);
        startCooldown("wave", cooldown(waveSkill()));
        action = Action.RECOVER;
        timer = 10;
    }

    /** 波を出す（管理者・自動テストからも）。 */
    public void startWaveNow(ServerLevel level) {
        Vec3 c = center == null ? position() : center;
        waveCenter = onIce(c);
        waveRadius = 1.5;
        waveHit.clear();
        impactFx("wave", TELEGRAPH_CIRCLE, waveCenter, 0.0F, (float) waveSkill().range(), 0.0F);  // 11月27日：当たった瞬間の演出
        level.playSound(null, waveCenter.x, waveCenter.y, waveCenter.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.6F, 0.5F);
    }

    private void tickWave(ServerLevel level) {
        if (waveCenter == null) {
            return;
        }
        EnemyDef.Skill wave = waveSkill();
        waveRadius += wave.param("speed", 0.8);
        if (waveRadius > wave.range()) {
            waveCenter = null;
            return;
        }
        int n = Math.max(24, (int) (waveRadius * 3));
        if (tickCount % 2 == 0) {
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2.0 * i / n;
                level.sendParticles(ParticleTypes.CLOUD, waveCenter.x + Math.cos(a) * waveRadius, waveCenter.y + 0.15,
                        waveCenter.z + Math.sin(a) * waveRadius, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
        int sever = (int) Math.round(wave.param("sever_seconds", 15.0) * 20);
        for (ServerPlayer player : targets(level)) {
            if (waveHit.contains(player.getUUID())) {
                continue;
            }
            double d = Math.hypot(player.getX() - waveCenter.x, player.getZ() - waveCenter.z);
            if (Math.abs(d - waveRadius) > 1.1) {
                continue;
            }
            boolean grounded = player.getY() - waveCenter.y < 0.45;
            if (!grounded) {
                continue;
            }
            waveHit.add(player.getUUID());
            if (EnemyCombat.strike(this, player, wave.power(), null)) {
                LostService.inflict(player, ModEffects.LOST_SEVER.get(), sever);
            }
        }
    }

    // ---------------------------------------------------------------- 管理者・自動テスト

    /** 自動テスト用：氷の面の高さ。 */
    public int testIceY() {
        if (iceY == Integer.MIN_VALUE) {
            findLake();
        }
        return iceY;
    }

    /** 自動テスト用：すぐに氷の上へ出す（surface 秒）。 */
    public void testSurface() {
        if (level() instanceof ServerLevel level) {
            if (iceY == Integer.MIN_VALUE) {
                findLake();
            }
            surface(level, false);
            surfacedUntil = tickCount + 200;
        }
    }

    /** 自動テスト用：波が今どこまで広がったか（出ていなければ −1）。 */
    public double testWaveRadius() {
        return waveCenter == null ? -1.0 : waveRadius;
    }

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
        if (players.isEmpty()) {
            return false;
        }
        clearTelegraph();
        action = Action.NONE;
        clearBusy();
        switch (name) {
            case "rise" -> {
                if (!submerged()) {
                    dive(level, false);
                }
                startRise(level, players, players.get(0));
            }
            case "sweep" -> {
                if (submerged()) {
                    surface(level, true);
                    surfacedUntil = tickCount + 200;
                }
                startSweep(level, players.get(0));
            }
            case "tail" -> {
                if (submerged()) {
                    surface(level, true);
                    surfacedUntil = tickCount + 200;
                }
                startTail(level);
            }
            case "veil" -> startVeil(level);
            case "wave" -> startWave(level);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** 管理者・CIの写真：氷の上へ出す（叩ける。surface 秒）。 */
    public void forceSurface() {
        testSurface();
    }

    /** CIの写真：鳴り石の数（4）。 */
    public int bellCount() {
        return arenaBells().size();
    }
}

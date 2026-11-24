package jp.amemaru.lostconnection.enemy.boss;

import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.waterway.SuimonNezumi;
import jp.amemaru.lostconnection.entity.GroundMarker;
import jp.amemaru.lostconnection.tower.BossRoom;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import jp.amemaru.lostconnection.tower.waterway.Drains;
import jp.amemaru.lostconnection.tower.waterway.FloodgateService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 3階のボス、大水門の番人ハイドラム（Phase 0-A 9章「水の大砲を撃つ。水位を下げると胸の炉心が露出して弱点になる」）。
 * 大水門を守る、石と青銅の大きな番人。右の腕が水の大砲。胸の炉心は、部屋に水が満ちている間は水の外套に守られている。
 *
 * <ul>
 *   <li>水の鎧：部屋に水が満ちている間は、受けるダメージが 1/5（定義の技 {@code drain} の {@code flooded}）</li>
 *   <li>排水：部屋の東と西の「排水のハンドル」（階の水門 {@link FloorFeatures#DRAIN}）を、2人で同時に回すと水が引き、
 *       番人がよろめいて、胸の炉心がむき出しになる（25秒、受けるダメージ2倍）。そのあと水が戻り、しばらくハンドルは回らない【補完】</li>
 *   <li>技：水の大砲（長い一直線。押し流す）・水の薙ぎ払い（前の扇）・渦（何人かの足もとの円。引き寄せて遅くする）・
 *       大放流（みんなで受ける一撃。{@link AmemaruBoss} の share）</li>
 *   <li>HP60%：水門ネズミを呼ぶ。HP30%：大砲を2連で撃ち、渦が1つ増え、炉心がむき出しの時間が短くなる【補完】</li>
 * </ul>
 * <p>11月25日のご指示「ボスそれぞれの固有のギミックを追加する」【提案】：<b>大放水</b>。HP65%と30%で、大水門をひらく構え
 * （{@code skills.flood.windup} 秒）。番人のまわりに青銅の堤（{@code walls} 枚）がせり上がり、構えが終わると部屋じゅうを
 * 大水が走る。番人から見て堤のかげにいる人は無事、見えている人は押し流される（{@code power}）。堤は少しあとで崩れる。</p>
 * 数値は敵の定義（{@code enemies/hydram.json}）の技で変えられる。
 */
public class Hydram extends AmemaruBoss implements Drains.Listener {
    public static final List<String> SKILLS = List.of("cannon", "sweep", "whirl", "call", "drain", "refill", "flood");
    private static final EntityDataAccessor<Boolean> DATA_DRAINED = SynchedEntityData.defineId(Hydram.class, EntityDataSerializers.BOOLEAN);

    private enum Action { NONE, CANNON, SWEEP, WHIRL, CALL, STAGGER, RECOVER, FLOOD }

    private Action action = Action.NONE;

    /** 技を出している途中か（技が終わったら、ボスは少し息つぎをする。{@link AmemaruBoss#breatheTicks}）。 */
    @Override
    protected boolean acting() {
        return action != Action.NONE;
    }

    private int timer;
    private float lockedYaw;
    private Vec3 cannonDir = Vec3.ZERO;
    private double cannonLength;
    /** 2連の大砲の2発目を撃ったか。 */
    private boolean secondShot;
    private final List<Vec3> whirlSpots = new ArrayList<>();
    private final List<UUID> summoned = new ArrayList<>();
    private final Set<String> tips = new HashSet<>();
    /** 水が引いている（炉心がむき出し）残りの tick。 */
    private int exposed;
    /** 大放水（11月25日）：出した回数（HP65%・30%）・堤のブロック・堤が崩れるまで */
    private int floodsDone;
    private final TempBlocks dikes = new TempBlocks();
    private int dikeTicks;
    /** 水が戻ってから、排水のハンドルがまた回るまでの tick。 */
    private int drainCooldown;
    private int repath;

    public Hydram(EntityType<? extends Hydram> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
        setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_DRAINED, false);
    }

    @Override
    public String assetName() {
        return "hydram";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_cannon", "skill_sweep", "skill_whirl", "skill_call", "skill_stagger"};
    }

    @Override
    protected int phaseFor(double ratio) {
        if (ratio <= 0.3) {
            return 3;
        }
        return ratio <= 0.6 ? 2 : 1;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** 水が引いている（炉心がむき出し）か。クライアントでも読める（水の外套を隠す）。 */
    public boolean drained() {
        return entityData.get(DATA_DRAINED);
    }

    /** 水の外套は、水が満ちている間だけ見せる。 */
    @Override
    public Map<String, Boolean> boneToggles() {
        return Map.of("mantle", !drained());
    }

    private EnemyDef.Skill skill(String name, double power, double windup, double cooldown, double range) {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(power, windup, cooldown, range, Map.of()) : def.skill(name, power, windup, cooldown, range);
    }

    private EnemyDef.Skill drainSkill() {
        return skill("drain", 0.0, 0.0, 12.0, 0.0);
    }

    private int cooldown(EnemyDef.Skill skill) {
        double scale = phase() >= 3 ? 0.75 : phase() == 2 ? 0.88 : 1.0;
        return (int) Math.round(skill.cooldownTicks() * scale * pace());
    }

    /** 水の鎧（1/5）と、むき出しの炉心（2倍）。 */
    @Override
    public double damageTakenMultiplier(@Nullable Entity attacker) {
        EnemyDef.Skill drain = drainSkill();
        return drained() ? drain.param("exposed", 2.0) : drain.param("flooded", 0.2);
    }

    // ---------------------------------------------------------------- 戦い

    @Override
    public void onFightStarted(ServerLevel level, List<ServerPlayer> participants) {
        // 前の戦いで水が引いたまま終わっていたら、満たし直す
        setDrained(false);
        exposed = 0;
        drainCooldown = 0;
        water(level, true);
        line("boss.amemaru.hydram.line.start");
        tipOnce("drain");
    }

    @Override
    protected void onPhaseChanged(ServerLevel level, int from, int to) {
        if (to >= 2 && from < 2) {
            cancelAction();
            action = Action.CALL;
            timer = 30;
            playAction("skill_call");
            setTelegraph(TELEGRAPH_ALERT, timer, getYRot(), 0.0F, 0.0F);
            line("boss.amemaru.hydram.line.call");
        }
        if (to >= 3 && from < 3) {
            line("boss.amemaru.hydram.line.rage");
            tipOnce("rage");
        }
    }

    @Override
    public void resetFight() {
        super.resetFight();
        cancelAction();
        action = Action.NONE;
        exposed = 0;
        drainCooldown = 0;
        if (drained() && level() instanceof ServerLevel level) {
            water(level, true);
        }
        setDrained(false);
        setStatus("", 0);
        floodsDone = 0;
        dikeTicks = 0;
        if (level() instanceof ServerLevel level) {
            dikes.restore(level);
        }
        discardSummoned();
    }

    private void cancelAction() {
        clearTelegraph();
        whirlSpots.clear();
        secondShot = false;
    }

    private void setDrained(boolean value) {
        if (drained() != value) {
            entityData.set(DATA_DRAINED, value);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive() || isRemoved()) {
            return;
        }
        BossRoom room = room();
        if (room != null && !room.fighting()) {
            if (action != Action.NONE) {
                action = Action.NONE;
                cancelAction();
            }
            setStatus("", 0);
            getNavigation().stop();
            return;
        }
        tickWater(level);
        if (dikeTicks > 0 && --dikeTicks == 0) {
            dikes.restore(level);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.COPPER_BREAK, SoundSource.HOSTILE, 1.6F, 0.6F);
        }
        switch (action) {
            case NONE -> choose(level);
            case CANNON -> tickCannon(level);
            case SWEEP -> tickSweep(level);
            case WHIRL -> tickWhirl(level);
            case CALL -> tickCall(level);
            case FLOOD -> tickFlood(level);
            case STAGGER -> {
                getNavigation().stop();
                if (timer % 6 == 0) {
                    level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.3, getZ(), 20, 1.6, 0.2, 1.6, 0.1);
                }
                if (--timer <= 0) {
                    action = Action.NONE;
                }
            }
            case RECOVER -> {
                if (--timer <= 0) {
                    action = Action.NONE;
                }
            }
        }
    }

    // ---------------------------------------------------------------- 水（鎧と排水）

    private void tickWater(ServerLevel level) {
        if (drained()) {
            exposed--;
            if (tickCount % 10 == 0) {
                setStatus("open", (exposed + 19) / 20);
            }
            if (tickCount % 3 == 0) {
                // むき出しの炉心の光
                Vec3 core = corePos();
                level.sendParticles(ParticleTypes.END_ROD, core.x, core.y, core.z, 2, 0.3, 0.3, 0.3, 0.01);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, core.x, core.y, core.z, 2, 0.35, 0.35, 0.35, 0.05);
            }
            if (exposed <= 0) {
                refill(level);
            }
            return;
        }
        if (drainCooldown > 0) {
            drainCooldown--;
            if (tickCount % 10 == 0 && action != Action.FLOOD) {
                setStatus("refilled", (drainCooldown + 19) / 20);
            }
        } else if (tickCount % 10 == 0 && action != Action.FLOOD) {
            setStatus("flooded", 0);
        }
        if (tickCount % 4 == 0) {
            // 水の外套：胸のまわりを流れ落ちる水
            Vec3 core = corePos();
            level.sendParticles(ParticleTypes.FALLING_WATER, core.x, core.y + 0.4, core.z, 4, 0.6, 0.4, 0.6, 0.0);
            level.sendParticles(ParticleTypes.BUBBLE, core.x, core.y, core.z, 2, 0.4, 0.3, 0.4, 0.02);
        }
    }

    /** 胸の炉心の場所。 */
    private Vec3 corePos() {
        Vec3 look = Vec3.directionFromRotation(0.0F, yBodyRot);
        return position().add(look.scale(getBbWidth() * 0.45)).add(0.0, getBbHeight() * 0.55, 0.0);
    }

    /** 排水のハンドルが2つ同時に回された（{@link FloodgateService}）。水を引けたら true。 */
    @Override
    public boolean onDrain(ServerLevel level) {
        BossRoom room = room();
        if (drained() || drainCooldown > 0 || room == null || !room.fighting()) {
            return false;
        }
        EnemyDef.Skill drain = drainSkill();
        double seconds = drain.param("exposed_seconds", 25.0) * (phase() >= 3 ? drain.param("rage_exposed", 0.72) : 1.0);
        exposed = (int) Math.round(seconds * 20);
        setDrained(true);
        setStatus("open", (exposed + 19) / 20);
        // よろめく（技を止める）
        cancelAction();
        action = Action.STAGGER;
        timer = (int) Math.round(drain.param("stagger_seconds", 3.0) * 20);
        setBusy(timer);
        getNavigation().stop();
        playAction("skill_stagger");
        water(level, false);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.HOSTILE, 2.0F, 0.6F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 2.0F, 0.5F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2.0F, 0.5F);
        line("boss.amemaru.hydram.line.drained");
        tipOnce("open");
        for (ServerPlayer player : room.participants()) {
            player.displayClientMessage(Component.translatable("boss.amemaru.hydram.drained", Math.round(seconds))
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        }
        return true;
    }

    /** 水が戻る（炉心がまた水の外套に守られる）。 */
    private void refill(ServerLevel level) {
        setDrained(false);
        exposed = 0;
        drainCooldown = (int) Math.round(drainSkill().cooldownTicks() * pace());
        setStatus("refilled", (drainCooldown + 19) / 20);
        water(level, true);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2.0F, 0.4F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.HOSTILE, 1.6F, 0.6F);
        line("boss.amemaru.hydram.line.refill");
    }

    /** 部屋の水（排水の水門の区画）を、引く・満たす。 */
    private void water(ServerLevel level, boolean fill) {
        BossRoom room = room();
        if (room == null) {
            return;
        }
        FloorDef def = room.floor();
        FloorFeatures.Floodgate gate = def.features().floodgate(FloorFeatures.DRAIN);
        if (gate == null) {
            return;
        }
        for (String id : gate.drains()) {
            FloorFeatures.WaterSection section = def.features().waterSection(id);
            if (section != null) {
                FloodgateService.queue(level, def, section, fill);
            }
        }
    }

    // ---------------------------------------------------------------- 技を選ぶ

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

    private EnemyDef.Skill cannonSkill() {
        return skill("cannon", 1.7, 1.6, 7.0, 30.0);
    }

    private EnemyDef.Skill sweepSkill() {
        return skill("sweep", 1.3, 1.1, 4.5, 8.0);
    }

    private EnemyDef.Skill whirlSkill() {
        return skill("whirl", 1.1, 1.8, 10.0, 26.0);
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
        // 大放水：HP65%と30%で1回ずつ（水が引いている間は出さない。11月25日）
        EnemyDef.Skill flood = floodSkill();
        double ratio = getEnemyHp() / getEnemyMaxHp();
        if (!drained() && (floodsDone == 0 && ratio <= flood.param("first_at", 0.65)
                || floodsDone == 1 && ratio <= flood.param("second_at", 0.3))) {
            floodsDone++;
            startFlood(level, flood);
            return;
        }
        EnemyDef.Skill sweep = sweepSkill();
        EnemyDef.Skill cannon = cannonSkill();
        ServerPlayer near = null;
        ServerPlayer far = null;
        for (ServerPlayer player : players) {
            double d = player.distanceTo(this);
            if (d <= sweep.range() + 1.0 && (near == null || d < near.distanceTo(this))) {
                near = player;
            }
            if (d > 7.0 && d <= cannon.range() && (far == null || random.nextBoolean())) {
                far = player;
            }
        }
        List<Action> options = new ArrayList<>();
        if (near != null && ready("sweep")) {
            options.add(Action.SWEEP);
            options.add(Action.SWEEP);
        }
        if (far != null && ready("cannon")) {
            options.add(Action.CANNON);
            options.add(Action.CANNON);
        }
        if (ready("whirl")) {
            options.add(Action.WHIRL);
        }
        if (options.isEmpty()) {
            approach(players);
            return;
        }
        getNavigation().stop();
        switch (options.get(random.nextInt(options.size()))) {
            case SWEEP -> startSweep(near);
            case CANNON -> startCannon(level, far, false);
            case WHIRL -> startWhirl(level);
            default -> {
            }
        }
    }

    // ---- 大放水（11月25日。HP65%・30%）

    private EnemyDef.Skill floodSkill() {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(2.2, 6.0, 60.0, 30.0, Map.of()) : def.skill("flood", 2.2, 6.0, 60.0, 30.0);
    }

    private void startFlood(ServerLevel level, EnemyDef.Skill flood) {
        cancelAction();
        action = Action.FLOOD;
        timer = Math.max(40, flood.windupTicks());
        getNavigation().stop();
        setBusy(timer + 10);
        setTelegraph(TELEGRAPH_ALERT, timer, getYRot(), 0.0F, 0.0F);
        playAction("skill_cannon");
        setCast("flood", timer);
        line("boss.amemaru.hydram.line.flood");
        tipOnce("flood");
        raiseDikes(level, flood);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.HOSTILE, 2.0F, 0.5F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.HOSTILE, 2.0F, 0.4F);
        setStatus("flood", (timer + 19) / 20);
    }

    /** 番人のまわりに、青銅の堤をせり上げる（輪の上に、外を向いた幅3・高さ3の壁）。 */
    private void raiseDikes(ServerLevel level, EnemyDef.Skill flood) {
        dikes.restore(level);
        int count = Math.max(3, (int) flood.param("walls", 4.0));
        double radius = flood.param("wall_radius", 7.0);
        double start = random.nextDouble() * Math.PI * 2.0;
        net.minecraft.world.level.block.state.BlockState bronze = net.minecraft.world.level.block.Blocks.WAXED_CUT_COPPER.defaultBlockState();
        net.minecraft.world.level.block.state.BlockState top = net.minecraft.world.level.block.Blocks.WAXED_EXPOSED_CUT_COPPER.defaultBlockState();
        int floorY = BlockPos.containing(position()).getY();
        for (int i = 0; i < count; i++) {
            double a = start + i * Math.PI * 2.0 / count;
            double cx = getX() + Math.cos(a) * radius;
            double cz = getZ() + Math.sin(a) * radius;
            // 輪に沿った向き（番人を向く向きと直角）に、3ブロック並べる
            double tx = -Math.sin(a);
            double tz = Math.cos(a);
            for (int k = -1; k <= 1; k++) {
                BlockPos base = BlockPos.containing(cx + tx * k, floorY, cz + tz * k);
                for (int h = 0; h < 3; h++) {
                    dikes.place(level, base.above(h), h == 2 ? top : bronze);
                }
            }
            level.sendParticles(ParticleTypes.SPLASH, cx, floorY + 0.5, cz, 30, 1.2, 0.3, 1.2, 0.2);
        }
        dikeTicks = 0;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    private void tickFlood(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        if (timer % 10 == 0) {
            setStatus("flood", (timer + 19) / 20);
            Vec3 core = corePos();
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, core.x, core.y, core.z, 20, 1.0, 0.6, 1.0, 0.1);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WATER_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.5F);
        }
        if (--timer > 0) {
            return;
        }
        EnemyDef.Skill flood = floodSkill();
        Vec3 from = position().add(0.0, getBbHeight() * 0.6, 0.0);
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) > flood.range()) {
                continue;
            }
            if (behindDike(level, from, player)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("boss.amemaru.hydram.flood_safe")
                        .withStyle(net.minecraft.ChatFormatting.AQUA), true);
                continue;
            }
            if (EnemyCombat.strike(this, player, flood.power(), null)) {
                Vec3 away = player.position().subtract(position()).multiply(1, 0, 1);
                Vec3 push = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(1.6);
                player.push(push.x, 0.45, push.z);
                player.hurtMarked = true;
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("boss.amemaru.hydram.flood_hit")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
            }
        }
        level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.5, getZ(), 300, 12.0, 0.5, 12.0, 0.4);
        level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5, getZ(), 60, 10.0, 0.4, 10.0, 0.1);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2.4F, 0.5F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 1.6F, 0.6F);
        clearTelegraph();
        setStatus("", 0);
        dikeTicks = 30;
        action = Action.RECOVER;
        timer = 16;
    }

    /** 番人から見て、その人との間に堤（壁）があるか（体の高さの2か所のどちらも見えなければ、かげにいる）。 */
    public static boolean behindDike(ServerLevel level, Vec3 from, ServerPlayer player) {
        for (double y : new double[]{player.getEyeHeight() * 0.9, 0.6}) {
            Vec3 to = player.position().add(0.0, y, 0.0);
            net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(from, to,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                return false;
            }
        }
        return true;
    }

    /** 自動テスト：堤のブロックの数。 */
    public int dikeBlocks() {
        return dikes.size();
    }

    /** 技がないときは、いちばん敵視の高い人（いなければ近い人）の方へ、ゆっくり歩く。部屋のまん中から離れすぎない。 */
    private void approach(List<ServerPlayer> players) {
        ServerPlayer target = topThreat();
        if (target == null || !players.contains(target)) {
            target = players.get(0);
        }
        faceTowards(target.position());
        BossRoom room = room();
        Vec3 center = room == null ? position() : room.arenaCenter();
        double leash = drainSkill().param("leash", 14.0);
        if (target.distanceTo(this) > 6.0 && target.position().distanceTo(center) < leash + 6.0) {
            if (--repath <= 0) {
                repath = 15;
                getNavigation().moveTo(target, 0.8);
            }
        } else {
            getNavigation().stop();
        }
        if (position().distanceTo(center) > leash) {
            getNavigation().moveTo(center.x, center.y, center.z, 0.8);
        }
    }

    // ---- 水の大砲（長い一直線）

    private void startCannon(ServerLevel level, ServerPlayer target, boolean second) {
        EnemyDef.Skill cannon = cannonSkill();
        action = Action.CANNON;
        secondShot = second;
        faceTowards(target.position());
        lockedYaw = getYRot();
        Vec3 to = target.position().subtract(position()).multiply(1.0, 0.0, 1.0);
        cannonDir = to.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, lockedYaw) : to.normalize();
        cannonLength = cannon.range();
        timer = Math.max(1, (int) Math.round(cannon.windupTicks() * (second ? 0.6 : 1.0)));
        setBusy(timer + 14);
        playAction("skill_cannon");
        setTelegraph(TELEGRAPH_LINE, timer, lockedYaw, (float) cannonLength, (float) cannon.param("width", 2.6));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.HOSTILE, 1.8F, 1.4F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.HOSTILE, 1.8F, 0.6F);
        tipOnce("cannon");
    }

    private Vec3 muzzle() {
        // 右の腕（体の右 = 向きから時計回りに90度）の先
        Vec3 look = Vec3.directionFromRotation(0.0F, lockedYaw);
        Vec3 right = new Vec3(-look.z, 0.0, look.x);
        return position().add(look.scale(2.2)).add(right.scale(-1.6)).add(0.0, getBbHeight() * 0.5, 0.0);
    }

    private void tickCannon(ServerLevel level) {
        lock();
        if (--timer > 0) {
            if (timer % 2 == 0) {
                // ためる間：砲口に水が渦を巻いて集まる。線の上に水しぶき
                Vec3 m = muzzle();
                double a = timer * 0.7;
                level.sendParticles(ParticleTypes.BUBBLE_POP, m.x + Math.cos(a) * 0.8, m.y + Math.sin(a) * 0.8, m.z, 3, 0.05, 0.05, 0.05, 0.0);
                level.sendParticles(ParticleTypes.SPLASH, m.x, m.y, m.z, 4, 0.3, 0.3, 0.3, 0.05);
            }
            if (timer % 6 == 0) {
                for (double t = 2.0; t <= cannonLength; t += 3.0) {
                    Vec3 at = position().add(cannonDir.scale(t));
                    level.sendParticles(ParticleTypes.SPLASH, at.x, getY() + 0.2, at.z, 3, 0.4, 0.05, 0.4, 0.05);
                }
            }
            return;
        }
        clearTelegraph();
        EnemyDef.Skill cannon = cannonSkill();
        double half = cannon.param("width", 2.6) / 2.0 + 0.4;
        double push = cannon.param("push", 1.4);
        for (ServerPlayer player : targets(level)) {
            Vec3 rel = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            double along = rel.dot(cannonDir);
            double side = rel.subtract(cannonDir.scale(along)).length();
            if (along >= 0.0 && along <= cannonLength + 0.6 && side <= half && Math.abs(player.getY() - getY()) < 3.5
                    && EnemyCombat.strike(this, player, cannon.power(), null)) {
                player.push(cannonDir.x * push, 0.3, cannonDir.z * push);
                player.hurtMarked = true;
            }
        }
        Vec3 m = muzzle();
        for (double t = 1.0; t <= cannonLength; t += 0.6) {
            Vec3 at = position().add(cannonDir.scale(t));
            double y = m.y + (getY() + 1.0 - m.y) * Math.min(1.0, t / 6.0);
            level.sendParticles(ParticleTypes.SPLASH, at.x, y, at.z, 6, 0.35, 0.35, 0.35, 0.25);
            level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, y, at.z, 2, 0.3, 0.3, 0.3, 0.05);
            if (((int) (t / 0.6)) % 4 == 0) {
                level.sendParticles(ParticleTypes.CLOUD, at.x, y, at.z, 1, 0.2, 0.2, 0.2, 0.02);
            }
        }
        Vec3 end = position().add(cannonDir.scale(cannonLength));
        level.sendParticles(ParticleTypes.EXPLOSION, end.x, getY() + 1.0, end.z, 2, 0.6, 0.4, 0.6, 0.0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.4F, 0.6F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 2.0F, 0.6F);
        // 怒り（HP30%）：2連で撃つ（2発目は、ほかの人を狙って、短いためで）
        if (!secondShot && phase() >= 3) {
            List<ServerPlayer> players = targets(level);
            players.removeIf(p -> p.distanceTo(this) < 5.0);
            if (!players.isEmpty()) {
                startCannon(level, players.get(random.nextInt(players.size())), true);
                return;
            }
        }
        startCooldown("cannon", cooldown(cannon));
        action = Action.RECOVER;
        timer = 14;
    }

    private void lock() {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        setYHeadRot(lockedYaw);
        getNavigation().stop();
    }

    // ---- 水の薙ぎ払い（前の扇）

    private void startSweep(ServerPlayer target) {
        EnemyDef.Skill sweep = sweepSkill();
        action = Action.SWEEP;
        faceTowards(target.position());
        lockedYaw = getYRot();
        timer = Math.max(1, sweep.windupTicks());
        setBusy(timer + 10);
        playAction("skill_sweep");
        setTelegraph(TELEGRAPH_CONE, timer, lockedYaw, (float) sweep.range(), (float) sweep.param("angle", 150.0));
        if (level() instanceof ServerLevel level) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.0F, 0.5F);
        }
        tipOnce("sweep");
    }

    private void tickSweep(ServerLevel level) {
        lock();
        if (--timer > 0) {
            return;
        }
        clearTelegraph();
        EnemyDef.Skill sweep = sweepSkill();
        double half = sweep.param("angle", 150.0) / 2.0;
        double push = sweep.param("push", 0.9);
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) - player.getBbWidth() / 2.0 <= sweep.range() + 0.6 && inFront(player.position(), half)
                    && EnemyCombat.strike(this, player, sweep.power(), null)) {
                Vec3 away = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
                if (away.lengthSqr() > 1.0E-4) {
                    away = away.normalize();
                    player.push(away.x * push, 0.25, away.z * push);
                    player.hurtMarked = true;
                }
            }
        }
        double yawRad = Math.toRadians(lockedYaw);
        for (int i = 0; i <= 10; i++) {
            double a = yawRad + Math.toRadians(-half + half * 2.0 * i / 10.0);
            for (double r = 2.0; r <= sweep.range(); r += 1.2) {
                double px = getX() - Math.sin(a) * r;
                double pz = getZ() + Math.cos(a) * r;
                level.sendParticles(ParticleTypes.SPLASH, px, getY() + 0.8, pz, 4, 0.3, 0.3, 0.3, 0.15);
                if (r + 1.2 > sweep.range()) {
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, px, getY() + 1.0, pz, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0F, 0.5F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.6F, 0.7F);
        startCooldown("sweep", cooldown(sweep));
        action = Action.RECOVER;
        timer = 10;
    }

    // ---- 渦（何人かの足もと）

    private void startWhirl(ServerLevel level) {
        EnemyDef.Skill whirl = whirlSkill();
        action = Action.WHIRL;
        whirlSpots.clear();
        timer = Math.max(10, whirl.windupTicks());
        float radius = (float) whirl.param("radius", 2.8);
        List<ServerPlayer> players = new ArrayList<>(targets(level));
        java.util.Collections.shuffle(players, new java.util.Random(random.nextLong()));
        int count = (int) Math.round(whirl.param("count", 2.0)) + (phase() >= 3 ? 1 : 0);
        for (ServerPlayer player : players) {
            if (whirlSpots.size() >= count || player.distanceTo(this) > whirl.range()) {
                continue;
            }
            Vec3 at = new Vec3(player.getX(), player.getY(), player.getZ());
            whirlSpots.add(at);
            GroundMarker.place(level, at, radius, timer);
        }
        setBusy(timer + 16);
        playAction("skill_whirl");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.7F);
        tipOnce("whirl");
    }

    private void tickWhirl(ServerLevel level) {
        EnemyDef.Skill whirl = whirlSkill();
        double radius = whirl.param("radius", 2.8);
        if (--timer > 0) {
            if (timer % 2 == 0) {
                // ためる間：円の中で水が渦を巻く
                for (Vec3 spot : whirlSpots) {
                    for (int k = 0; k < 3; k++) {
                        double a = timer * 0.45 + k * Math.PI * 2.0 / 3.0;
                        double r = radius * (0.3 + 0.6 * ((timer + k * 5) % 12) / 12.0);
                        level.sendParticles(ParticleTypes.SPLASH, spot.x + Math.cos(a) * r, spot.y + 0.2, spot.z + Math.sin(a) * r, 2,
                                0.05, 0.05, 0.05, 0.0);
                        level.sendParticles(ParticleTypes.BUBBLE_POP, spot.x + Math.cos(a) * r, spot.y + 0.3, spot.z + Math.sin(a) * r, 1,
                                0.05, 0.05, 0.05, 0.0);
                    }
                }
            }
            return;
        }
        double hitRadius = radius + 0.4;
        double pull = whirl.param("pull", 0.6);
        int slowTicks = (int) Math.round(whirl.param("slow_seconds", 2.0) * 20);
        Set<UUID> struck = new HashSet<>();
        for (Vec3 spot : whirlSpots) {
            for (ServerPlayer player : targets(level)) {
                double dx = player.getX() - spot.x;
                double dz = player.getZ() - spot.z;
                if (dx * dx + dz * dz <= hitRadius * hitRadius && Math.abs(player.getY() - spot.y) < 3.0 && struck.add(player.getUUID())
                        && EnemyCombat.strike(this, player, whirl.power(), null)) {
                    player.push(-dx * pull * 0.4, 0.15, -dz * pull * 0.4);
                    player.hurtMarked = true;
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowTicks, 1, false, true));
                }
            }
            level.sendParticles(ParticleTypes.SPLASH, spot.x, spot.y + 0.4, spot.z, 60, radius * 0.4, 0.4, radius * 0.4, 0.3);
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, spot.x, spot.y + 0.4, spot.z, 30, radius * 0.3, 0.6, radius * 0.3, 0.1);
            level.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 0.5, spot.z, 8, radius * 0.3, 0.2, radius * 0.3, 0.02);
            level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.HOSTILE, 1.4F, 0.8F);
        }
        whirlSpots.clear();
        startCooldown("whirl", cooldown(whirl));
        action = Action.RECOVER;
        timer = 10;
    }

    // ---- 水門ネズミを呼ぶ（HP60%）

    private void tickCall(ServerLevel level) {
        getNavigation().stop();
        if (--timer > 0) {
            return;
        }
        clearTelegraph();
        EnemyDef.Skill call = skill("call", 0.0, 0.0, 0.0, 12.0);
        BossRoom room = room();
        int participants = room == null ? 1 : room.participants().size();
        int count = Math.min((int) call.param("max", 6.0), (int) call.param("count", 2.0) + participants);
        List<ServerPlayer> players = targets(level);
        Vec3 center = room == null ? position() : room.arenaCenter();
        for (int i = 0; i < count; i++) {
            SuimonNezumi rat = ModEntities.SUIMON_NEZUMI.get().create(level);
            if (rat == null) {
                continue;
            }
            double a = Math.PI * 2.0 * i / count + random.nextDouble() * 0.4;
            double r = call.range();
            rat.moveTo(center.x + Math.cos(a) * r, center.y, center.z + Math.sin(a) * r, random.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(rat)) {
                rat.moveTo(center.x + Math.cos(a) * 4.0, center.y, center.z + Math.sin(a) * 4.0, random.nextFloat() * 360.0F, 0.0F);
            }
            rat.setHome(BlockPos.containing(rat.position()));
            rat.setTransient(true);
            level.addFreshEntity(rat);
            if (!players.isEmpty()) {
                rat.addThreat(players.get(random.nextInt(players.size())), 1.0);
            }
            summoned.add(rat.getUUID());
            level.sendParticles(ParticleTypes.SPLASH, rat.getX(), rat.getY() + 0.3, rat.getZ(), 12, 0.4, 0.2, 0.4, 0.1);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SILVERFISH_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.5F);
        startCooldown("call", 20 * 60);
        action = Action.RECOVER;
        timer = 10;
    }

    private void discardSummoned() {
        if (level() instanceof ServerLevel level) {
            for (UUID id : summoned) {
                Entity entity = level.getEntity(id);
                if (entity instanceof AmemaruMob mob && mob.isAlive()) {
                    mob.discard();
                }
            }
        }
        summoned.clear();
    }

    private void tipOnce(String key) {
        if (tips.add(key)) {
            tip("boss.amemaru.hydram.tip." + key);
        }
    }

    // ---------------------------------------------------------------- 管理者・自動テスト

    @Override
    public List<String> skillNames() {
        return SKILLS;
    }

    @Override
    public boolean forceSkill(String name) {
        if (!(level() instanceof ServerLevel level)) {
            return false;
        }
        List<ServerPlayer> players = targets(level);
        ServerPlayer target = players.isEmpty() ? null : players.get(0);
        switch (name) {
            case "cannon" -> {
                if (target == null) {
                    return false;
                }
                startCannon(level, target, false);
            }
            case "sweep" -> {
                if (target == null) {
                    return false;
                }
                startSweep(target);
            }
            case "whirl" -> startWhirl(level);
            case "call" -> {
                action = Action.CALL;
                timer = 1;
            }
            case "drain" -> {
                drainCooldown = 0;
                return onDrain(level);
            }
            case "refill" -> {
                if (!drained()) {
                    return false;
                }
                refill(level);
            }
            case "flood" -> startFlood(level, floodSkill());
            default -> {
                return false;
            }
        }
        return true;
    }

    /** 自動テスト用：部屋なしで、水が引いた形・満ちた形にする（部屋の水には触らない）。 */
    public void testDrained(boolean value) {
        setDrained(value);
        exposed = value ? 100 : 0;
    }

    /** 自動テスト用：炉心がむき出しの残りの tick・ハンドルがまた回るまでの tick。 */
    public int exposedTicks() {
        return exposed;
    }

    public int drainCooldownTicks() {
        return drainCooldown;
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        discardSummoned();
        setStatus("", 0);
        if (level() instanceof ServerLevel level) {
            dikes.restore(level);
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        discardSummoned();
        // 11月25日：大放水の青銅の堤を戻す
        if (level() instanceof ServerLevel level) {
            dikes.restore(level);
        }
        super.remove(reason);
    }
}

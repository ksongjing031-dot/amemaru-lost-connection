package jp.amemaru.lostconnection.enemy.boss;

import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.forest.MayoiBi;
import jp.amemaru.lostconnection.entity.GroundMarker;
import jp.amemaru.lostconnection.item.LightService;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.TowerS2C;
import jp.amemaru.lostconnection.tower.BossRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 2階のボス、森番の古樹オールドウォード（Phase 0-A 9章「根で地面を割り、幹の光る節を壊すと怯む。HP30%で森が暗くなり、
 * 灯を持つ人のまわりしか見えなくなる」）。空き地の真ん中に根を張った古い大木で、動かない。
 *
 * <ul>
 *   <li>幹の光る節（{@link OldWardKnot}）が3つ残っている間は、樹皮が硬い（守りが高い）。3つとも壊すと、しばらく怯んで
 *       （技を止め、守りがなくなる）、そのあと節がまた生える【補完】</li>
 *   <li>枝の薙ぎ払い：前の扇形。根の割れ目：離れた人へ、地面に一直線の割れ目の予兆を出してから、根が突き上がる。
 *       根の牢：何人かの足もとに円を出し、根で打って足をからめる【補完】</li>
 *   <li>HP60%：迷い灯を呼ぶ。HP30%：森が暗くなる（灯を持つ人のまわりしか見えない。空き地に「森番の灯」が2つ落ちる。
 *       店の手提げランタンも灯になる）【補完】</li>
 * </ul>
 * <p>11月25日のご指示「ボスそれぞれの固有のギミックを追加する」【提案】：<b>芽吹きの種</b>。ときどき空き地に光る種をまく
 * （緑の円のあとに根づく。2つ、3人以上なら3つ）。{@code skills.seeds.grow} 秒たつと若木に育ち、1本ごとに古樹の傷が
 * 最大HPの {@code heal}% ふさがり、迷い灯が1つ出る。育つ前に壊す（遠くから撃てる人の出番）。</p>
 * 数値は敵の定義（{@code enemies/old_ward.json}）の技で変えられる。
 */
public class OldWard extends AmemaruBoss {
    public static final List<String> SKILLS = List.of("sweep", "crack", "cage", "call", "dark", "seeds");

    private enum Action { NONE, SWEEP, CRACK, CAGE, CALL, STUNNED, RECOVER, SEEDS }

    private Action action = Action.NONE;

    /** 技を出している途中か（技が終わったら、ボスは少し息つぎをする。{@link AmemaruBoss#breatheTicks}）。 */
    @Override
    protected boolean acting() {
        return action != Action.NONE;
    }

    private int timer;
    private float lockedYaw;
    private Vec3 crackDir = Vec3.ZERO;
    private double crackLength;
    private final List<Vec3> cageSpots = new ArrayList<>();
    private final List<UUID> knots = new ArrayList<>();
    /** 芽吹きの種（11月25日）：まく所（緑の円が出ている間）と、根づいた種 */
    private final List<Vec3> seedSpots = new ArrayList<>();
    private final List<UUID> seeds = new ArrayList<>();
    private int fightTicks;
    private final List<UUID> summoned = new ArrayList<>();
    private int regrow;
    private boolean dark;
    private final Set<String> tips = new HashSet<>();

    public OldWard(EntityType<? extends OldWard> type, Level level) {
        super(type, level, BossEvent.BossBarColor.GREEN);
    }

    @Override
    public String assetName() {
        return "old_ward";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_sweep", "skill_crack", "skill_cage", "skill_call", "skill_stun"};
    }

    /** 11月28日：全員を狙う技は、節を折られて怯んでいる間（攻めどき）は出さない。 */
    @Override
    protected boolean partyAttackAllowed() {
        return !stunned();
    }

    @Override
    protected int phaseFor(double ratio) {
        if (ratio <= 0.3) {
            return 3;
        }
        return ratio <= 0.6 ? 2 : 1;
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

    /** 節が残っている間は硬い。怯んでいる間は守りがない。 */
    @Override
    public double defense() {
        if (action == Action.STUNNED) {
            return 0.0;
        }
        return aliveKnots() > 0 ? super.defense() + skill("knot", 0.0, 0.0, 0.0, 0.0).param("armor", 100.0) : super.defense();
    }

    private EnemyDef.Skill skill(String name, double power, double windup, double cooldown, double range) {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(power, windup, cooldown, range, java.util.Map.of())
                : def.skill(name, power, windup, cooldown, range);
    }

    private int cooldown(EnemyDef.Skill skill) {
        double scale = phase() >= 3 ? 0.7 : phase() == 2 ? 0.85 : 1.0;
        return (int) Math.round(skill.cooldownTicks() * scale * pace());
    }

    // ---------------------------------------------------------------- 戦い

    @Override
    public void onFightStarted(ServerLevel level, List<ServerPlayer> participants) {
        line("boss.amemaru.old_ward.line.start");
        growKnots(level);
        tip("boss.amemaru.old_ward.tip.knots");
    }

    @Override
    protected void onPhaseChanged(ServerLevel level, int from, int to) {
        if (to >= 2 && from < 2) {
            action = Action.CALL;
            timer = 30;
            playAction("skill_call");
            setTelegraph(TELEGRAPH_ALERT, timer, getYRot(), 0.0F, 0.0F);
            line("boss.amemaru.old_ward.line.call");
        }
        if (to >= 3 && from < 3) {
            startDark(level);
        }
    }

    @Override
    public void resetFight() {
        super.resetFight();
        action = Action.NONE;
        timer = 0;
        cageSpots.clear();
        regrow = 0;
        fightTicks = 0;
        stopDark();
        discardKnots();
        discardSeeds();
        setStatus("", 0);
        discardSummoned();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive() || isRemoved()) {
            return;
        }
        getNavigation().stop();
        BossRoom room = room();
        if (room != null && !room.fighting()) {
            if (action != Action.NONE) {
                action = Action.NONE;
                clearTelegraph();
            }
            return;
        }
        placeKnots(level);
        tickCaged(level);
        fightTicks++;
        if (tickCount % 10 == 0) {
            updateSeedStatus(level);
        }
        if (dark && tickCount % 20 == 0) {
            darkness(level);
        }
        if (regrow > 0 && --regrow == 0) {
            growKnots(level);
        }
        switch (action) {
            case NONE -> choose(level);
            case SWEEP -> tickSweep(level);
            case CRACK -> tickCrack(level);
            case CAGE -> tickCage(level);
            case CALL -> tickCall(level);
            case SEEDS -> tickSeeds(level);
            case STUNNED -> tickStunned(level);
            case RECOVER -> {
                if (--timer <= 0) {
                    action = Action.NONE;
                }
            }
        }
    }

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

    private void choose(ServerLevel level) {
        if (isBusy()) {
            return;
        }
        List<ServerPlayer> players = targets(level);
        if (players.isEmpty()) {
            return;
        }
        EnemyDef.Skill sweep = skill("sweep", 1.2, 0.9, 4.0, 7.0);
        EnemyDef.Skill crack = skill("crack", 1.4, 1.2, 6.0, 22.0);
        EnemyDef.Skill cage = skill("cage", 1.0, 1.4, 9.0, 24.0);
        ServerPlayer near = null;
        ServerPlayer far = null;
        for (ServerPlayer player : players) {
            double d = player.distanceTo(this);
            if (d <= sweep.range() + 1.0 && (near == null || d < near.distanceTo(this))) {
                near = player;
            }
            if (d > 6.0 && d <= crack.range() && (far == null || random.nextBoolean())) {
                far = player;
            }
        }
        // 芽吹きの種：戦いが始まって少したってから、間をあけて（11月25日）
        EnemyDef.Skill seedSkill = skill("seeds", 0.0, 1.5, 40.0, 14.0);
        if (ready("seeds") && fightTicks >= (int) Math.round(seedSkill.param("first", 20.0) * 20) && aliveSeeds(level) == 0) {
            startSeeds(level, seedSkill);
            return;
        }
        List<Action> options = new ArrayList<>();
        if (near != null && ready("sweep")) {
            options.add(Action.SWEEP);
            options.add(Action.SWEEP);
        }
        if (far != null && ready("crack")) {
            options.add(Action.CRACK);
            options.add(Action.CRACK);
        }
        if (ready("cage")) {
            options.add(Action.CAGE);
        }
        if (options.isEmpty()) {
            if (near == null && far == null && ready("crack")) {
                far = players.get(random.nextInt(players.size()));
                options.add(Action.CRACK);
            } else {
                faceTowards(players.get(0).position());
                return;
            }
        }
        Action pick = options.get(random.nextInt(options.size()));
        switch (pick) {
            case SWEEP -> startSweep(near, sweep);
            case CRACK -> startCrack(level, far, crack);
            case CAGE -> startCage(level, cage);
            default -> {
            }
        }
    }

    // ---- 枝の薙ぎ払い（前の扇形）

    private void startSweep(ServerPlayer target, EnemyDef.Skill sweep) {
        action = Action.SWEEP;
        faceTowards(target.position());
        lockedYaw = getYRot();
        timer = Math.max(1, sweep.windupTicks());
        setBusy(timer + 10);
        playAction("skill_sweep");
        setTelegraph(TELEGRAPH_CONE, timer, lockedYaw, (float) sweep.range(), (float) sweep.param("angle", 120.0));
        // 11月24日「ボスの攻撃がわかりづらい」：振りかぶる音（枝がきしむ）と、はじめの手引き
        if (level() instanceof ServerLevel level) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_HIT, SoundSource.HOSTILE, 2.0F, 0.5F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1.6F, 0.5F);
        }
        tipOnce("sweep");
    }

    private void tickSweep(ServerLevel level) {
        lock();
        if (--timer > 0) {
            return;
        }
        impactFx("sweep");  // 11月27日：当たった瞬間の演出
        clearTelegraph();
        EnemyDef.Skill sweep = skill("sweep", 1.2, 0.9, 4.0, 7.0);
        double half = sweep.param("angle", 120.0) / 2.0;
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) - player.getBbWidth() / 2.0 <= sweep.range() + 0.6 && inFront(player.position(), half)) {
                EnemyCombat.strike(this, player, sweep.power(), null);
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_BREAK, SoundSource.HOSTILE, 1.6F, 0.6F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0F, 0.5F);
        // 扇いっぱいに、斬撃と散る葉（当たった所が見えるように。11月24日）
        BlockParticleOption leaves = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DARK_OAK_LEAVES.defaultBlockState());
        double yawRad = Math.toRadians(lockedYaw);
        for (int i = 0; i <= 8; i++) {
            double a = yawRad + Math.toRadians(-half + half * 2.0 * i / 8.0);
            for (double r = 2.0; r <= sweep.range(); r += 1.5) {
                double px = getX() - Math.sin(a) * r;
                double pz = getZ() + Math.cos(a) * r;
                level.sendParticles(leaves, px, getY() + 0.8, pz, 3, 0.3, 0.4, 0.3, 0.05);
                if (r + 1.5 > sweep.range()) {
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, px, getY() + 1.0, pz, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
        startCooldown("sweep", cooldown(sweep));
        action = Action.RECOVER;
        timer = 10;
    }

    private void lock() {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        setYHeadRot(lockedYaw);
    }

    // ---- 根の割れ目（一直線）

    private void startCrack(ServerLevel level, ServerPlayer target, EnemyDef.Skill crack) {
        action = Action.CRACK;
        faceTowards(target.position());
        lockedYaw = getYRot();
        Vec3 to = target.position().subtract(position()).multiply(1.0, 0.0, 1.0);
        crackDir = to.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, lockedYaw) : to.normalize();
        crackLength = Math.min(crack.range(), Math.max(8.0, to.length() + 4.0));
        timer = Math.max(1, crack.windupTicks());
        setBusy(timer + 12);
        playAction("skill_crack");
        setTelegraph(TELEGRAPH_LINE, timer, lockedYaw, (float) crackLength, (float) crack.param("width", 2.4));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.6F, 0.5F);
    }

    private void tickCrack(ServerLevel level) {
        lock();
        if (--timer > 0) {
            if (timer % 4 == 0) {
                // ためる間：線の上で土がふるえる（11月24日）
                BlockParticleOption shake = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState());
                for (double t = 1.5; t <= crackLength; t += 2.0) {
                    Vec3 at = position().add(crackDir.scale(t + random.nextDouble()));
                    level.sendParticles(shake, at.x, getY() + 0.1, at.z, 2, 0.3, 0.05, 0.3, 0.05);
                }
                if (timer % 8 == 0) {
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.0F, 0.5F);
                }
            }
            return;
        }
        impactFx("crack");  // 11月27日：当たった瞬間の演出
        clearTelegraph();
        EnemyDef.Skill crack = skill("crack", 1.4, 1.2, 6.0, 22.0);
        double half = crack.param("width", 2.4) / 2.0 + 0.4;
        BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState());
        BlockParticleOption root = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DARK_OAK_LOG.defaultBlockState());
        for (double t = 1.5; t <= crackLength; t += 1.0) {
            Vec3 at = position().add(crackDir.scale(t));
            level.sendParticles(dirt, at.x, getY() + 0.3, at.z, 6, 0.4, 0.3, 0.4, 0.1);
            // 突き上がる根（11月24日）：丸太の粒を高く
            level.sendParticles(root, at.x, getY() + 1.2, at.z, 5, 0.2, 0.9, 0.2, 0.15);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, position().add(crackDir.scale(crackLength * 0.5)).x, getY() + 0.5,
                position().add(crackDir.scale(crackLength * 0.5)).z, 2, crackLength * 0.3, 0.1, crackLength * 0.3, 0.0);
        for (ServerPlayer player : targets(level)) {
            Vec3 rel = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            double along = rel.dot(crackDir);
            double side = rel.subtract(crackDir.scale(along)).length();
            if (along >= 0.0 && along <= crackLength + 0.6 && side <= half && Math.abs(player.getY() - getY()) < 3.0
                    && EnemyCombat.strike(this, player, crack.power(), null)) {
                player.push(0.0, crack.param("lift", 0.7), 0.0);
                player.hurtMarked = true;
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 1.4F, 0.6F);
        startCooldown("crack", cooldown(crack));
        action = Action.RECOVER;
        timer = 12;
        tipOnce("crack");
    }

    // ---- 根の牢（何人かの足もと）

    /** 根の牢に捕まった人と、残りの tick（捕まっている間、まわりに根の檻を見せる。11月24日）。 */
    private final java.util.Map<UUID, Integer> caged = new java.util.HashMap<>();

    private void tickCaged(ServerLevel level) {
        if (caged.isEmpty()) {
            return;
        }
        BlockParticleOption bark = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DARK_OAK_LOG.defaultBlockState());
        java.util.Iterator<java.util.Map.Entry<UUID, Integer>> it = caged.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<UUID, Integer> entry = it.next();
            int left = entry.getValue() - 1;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (left <= 0 || player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            entry.setValue(left);
            if (left % 3 == 0) {
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2.0 * i / 6.0 + left * 0.1;
                    level.sendParticles(bark, player.getX() + Math.cos(a) * 0.8, player.getY() + 0.6 + (i % 3) * 0.5,
                            player.getZ() + Math.sin(a) * 0.8, 1, 0.02, 0.1, 0.02, 0.0);
                }
            }
        }
    }

    private void startCage(ServerLevel level, EnemyDef.Skill cage) {
        action = Action.CAGE;
        cageSpots.clear();
        timer = Math.max(8, cage.windupTicks());
        float radius = (float) cage.param("radius", 2.4);
        List<ServerPlayer> players = new ArrayList<>(targets(level));
        java.util.Collections.shuffle(players, new java.util.Random(random.nextLong()));
        int count = (int) Math.round(cage.param("count", 3.0)) + (phase() >= 3 ? 1 : 0);
        for (ServerPlayer player : players) {
            if (cageSpots.size() >= count || player.distanceTo(this) > cage.range()) {
                continue;
            }
            Vec3 at = new Vec3(player.getX(), getY(), player.getZ());
            cageSpots.add(at);
            GroundMarker.place(level, at, radius, timer);
        }
        setBusy(timer + 16);
        playAction("skill_cage");
        tipOnce("cage");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.4F, 0.7F);
    }

    private void tickCage(ServerLevel level) {
        if (--timer > 0) {
            if (timer % 4 == 0) {
                // ためる間：円の中で根がうごめく（11月24日）
                BlockParticleOption soil = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState());
                for (Vec3 spot : cageSpots) {
                    level.sendParticles(soil, spot.x, spot.y + 0.1, spot.z, 4, 1.0, 0.05, 1.0, 0.05);
                }
            }
            return;
        }
        EnemyDef.Skill cage = skill("cage", 1.0, 1.4, 9.0, 24.0);
        double radius = cage.param("radius", 2.4) + 0.4;
        int rootTicks = (int) Math.round(cage.param("root_seconds", 2.0) * 20);
        BlockParticleOption moss = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSS_BLOCK.defaultBlockState());
        Set<UUID> struck = new HashSet<>();
        for (Vec3 spot : cageSpots) {
            impactFx("cage", TELEGRAPH_CIRCLE, spot, 0.0F, (float) radius, 0.0F);  // 11月27日：当たった瞬間の演出
            for (ServerPlayer player : targets(level)) {
                double dx = player.getX() - spot.x;
                double dz = player.getZ() - spot.z;
                if (dx * dx + dz * dz <= radius * radius && Math.abs(player.getY() - spot.y) < 3.0 && struck.add(player.getUUID())
                        && EnemyCombat.strike(this, player, cage.power(), null)) {
                    jp.amemaru.lostconnection.combat.AfflictionService.root(player, rootTicks, this);
                    caged.put(player.getUUID(), rootTicks);
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.amemaru.enemy.rooted")
                            .withStyle(net.minecraft.ChatFormatting.DARK_GREEN), true);
                }
            }
            level.sendParticles(moss, spot.x, spot.y + 0.6, spot.z, 24, radius * 0.4, 0.6, radius * 0.4, 0.1);
            // 円のふちに、突き上がる根の柱（11月24日）
            BlockParticleOption bark = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DARK_OAK_LOG.defaultBlockState());
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * 2.0 * i / 10.0;
                level.sendParticles(bark, spot.x + Math.cos(a) * (radius - 0.4), spot.y + 1.0, spot.z + Math.sin(a) * (radius - 0.4), 4,
                        0.1, 0.8, 0.1, 0.05);
            }
            level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 1.0F, 0.8F);
        }
        cageSpots.clear();
        startCooldown("cage", cooldown(cage));
        action = Action.RECOVER;
        timer = 10;
    }

    // ---- 迷い灯を呼ぶ（HP60%）

    private void tickCall(ServerLevel level) {
        if (--timer > 0) {
            return;
        }
        clearTelegraph();
        EnemyDef.Skill call = skill("call", 0.0, 0.0, 0.0, 12.0);
        BossRoom room = room();
        int participants = room == null ? 1 : room.participants().size();
        int count = Math.min((int) call.param("max", 5.0), (int) call.param("count", 2.0) + participants);
        List<ServerPlayer> players = targets(level);
        for (int i = 0; i < count; i++) {
            MayoiBi light = ModEntities.MAYOI_BI.get().create(level);
            if (light == null) {
                continue;
            }
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = 6.0 + random.nextDouble() * call.range();
            Vec3 base = room != null ? room.arenaCenter() : position();
            Vec3 at = base.add(Math.cos(a) * r, 1.5, Math.sin(a) * r);
            light.moveTo(at.x, getY() + 1.5, at.z, random.nextFloat() * 360.0F, 0.0F);
            light.setHome(BlockPos.containing(at.x, getY(), at.z));
            light.setTransient(true);
            level.addFreshEntity(light);
            summoned.add(light.getUUID());
            level.sendParticles(ParticleTypes.END_ROD, at.x, getY() + 1.5, at.z, 8, 0.3, 0.3, 0.3, 0.02);
            if (!players.isEmpty()) {
                light.addThreat(players.get(random.nextInt(players.size())), 5.0);
            }
        }
        tip("boss.amemaru.old_ward.tip.call");
        action = Action.NONE;
    }

    // ---- 芽吹きの種（11月25日）

    private void startSeeds(ServerLevel level, EnemyDef.Skill seedSkill) {
        action = Action.SEEDS;
        timer = Math.max(10, seedSkill.windupTicks());
        setBusy(timer + 5);
        playAction("skill_call");
        setCast("seeds", timer);
        seedSpots.clear();
        BossRoom room = room();
        int participants = room == null ? 1 : room.participants().size();
        int count = (int) seedSkill.param("count", 2.0) + (participants >= 3 ? 1 : 0);
        Vec3 base = room != null ? room.arenaCenter() : position();
        double start = random.nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < count; i++) {
            // 空き地に散らばる（古樹から 7〜range ブロック。同じ所に重ならないよう、向きを分ける）
            double a = start + i * Math.PI * 2.0 / count + (random.nextDouble() - 0.5) * 0.6;
            double r = 7.0 + random.nextDouble() * Math.max(1.0, seedSkill.range() - 7.0);
            Vec3 at = new Vec3(base.x + Math.cos(a) * r, getY(), base.z + Math.sin(a) * r);
            seedSpots.add(at);
            jp.amemaru.lostconnection.entity.GroundMarker.place(level, at, 1.6F, timer, jp.amemaru.lostconnection.entity.GroundMarker.KIND_SEED);
            level.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, at.x, getY() + 4.0, at.z, 10, 0.4, 0.6, 0.4, 0.0);
        }
        line("boss.amemaru.old_ward.line.seeds");
        tipOnce("seeds");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BIG_DRIPLEAF_TILT_DOWN, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    private void tickSeeds(ServerLevel level) {
        if (--timer > 0) {
            return;
        }
        EnemyDef.Skill seedSkill = skill("seeds", 0.0, 1.5, 40.0, 14.0);
        int grow = (int) Math.round(seedSkill.param("grow", 15.0) * 20);
        for (Vec3 at : seedSpots) {
            OldWardKnot seed = ModEntities.OLD_WARD_KNOT.get().create(level);
            if (seed == null) {
                continue;
            }
            seed.setSeed(this, grow);
            // 11月25日（CIの写真で見て足した）：遠くからでも、物のかげでも見つけられるように、ふちを光らせる
            seed.setGlowingTag(true);
            seed.moveTo(at.x, at.y + 0.2, at.z, random.nextFloat() * 360.0F, 0.0F);
            seed.setTransient(true);
            seed.setHpMultiplier(hpMultiplier() * seedSkill.param("hp_scale", 0.4));
            level.addFreshEntity(seed);
            seed.setEnemyHp(seed.getEnemyMaxHp());
            seeds.add(seed.getUUID());
            level.sendParticles(ParticleTypes.COMPOSTER, at.x, at.y + 0.3, at.z, 16, 0.4, 0.2, 0.4, 0.05);
        }
        seedSpots.clear();
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_PLACE, SoundSource.HOSTILE, 2.0F, 0.6F);
        startCooldown("seeds", cooldown(seedSkill));
        action = Action.NONE;
        updateSeedStatus(level);
    }

    private int aliveSeeds(ServerLevel level) {
        int alive = 0;
        for (UUID id : seeds) {
            if (level.getEntity(id) instanceof OldWardKnot seed && seed.isAlive()) {
                alive++;
            }
        }
        return alive;
    }

    /** 「若木：あと N 秒で育つ（M本）」。種がなければ消す。 */
    private void updateSeedStatus(ServerLevel level) {
        int alive = 0;
        int soonest = Integer.MAX_VALUE;
        for (UUID id : seeds) {
            if (level.getEntity(id) instanceof OldWardKnot seed && seed.isAlive()) {
                alive++;
                soonest = Math.min(soonest, seed.growTicks());
            }
        }
        seeds.removeIf(id -> !(level.getEntity(id) instanceof OldWardKnot seed) || !seed.isAlive());
        if (alive > 0) {
            setStatus("seeds:" + alive, (soonest + 19) / 20);
        } else if (status().startsWith("seeds")) {
            setStatus("", 0);
        }
    }

    /** 種が若木に育った：古樹の傷がふさがり、迷い灯が出る。 */
    public void onSeedMatured(OldWardKnot seed) {
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return;
        }
        EnemyDef.Skill seedSkill = skill("seeds", 0.0, 1.5, 40.0, 14.0);
        double heal = getEnemyMaxHp() * seedSkill.param("heal", 4.0) / 100.0;
        setEnemyHp(Math.min(getEnemyMaxHp(), getEnemyHp() + heal));
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, seed.getX(), seed.getY() + 1.0, seed.getZ(), 20, 0.6, 1.0, 0.6, 0.1);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 3.0, getZ(), 30, 1.5, 2.0, 1.5, 0.1);
        level.playSound(null, seed.getX(), seed.getY(), seed.getZ(), SoundEvents.BONE_MEAL_USE, SoundSource.HOSTILE, 2.0F, 0.7F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.6F, 0.6F);
        MayoiBi light = ModEntities.MAYOI_BI.get().create(level);
        if (light != null) {
            light.moveTo(seed.getX(), getY() + 1.5, seed.getZ(), random.nextFloat() * 360.0F, 0.0F);
            light.setHome(BlockPos.containing(seed.getX(), getY(), seed.getZ()));
            light.setTransient(true);
            level.addFreshEntity(light);
            summoned.add(light.getUUID());
            List<ServerPlayer> players = targets(level);
            if (!players.isEmpty()) {
                light.addThreat(players.get(random.nextInt(players.size())), 5.0);
            }
        }
        BossRoom room = room();
        if (room != null) {
            for (ServerPlayer player : room.participants()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("boss.amemaru.old_ward.seed_grown")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
            }
        }
    }

    /** 種を育つ前に壊した。 */
    public void onSeedDestroyed(OldWardKnot seed) {
        if (level() instanceof ServerLevel level) {
            level.playSound(null, seed.getX(), seed.getY(), seed.getZ(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.HOSTILE, 1.6F, 1.4F);
            level.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, seed.getX(), seed.getY() + 1.0, seed.getZ(), 12, 0.4, 0.4, 0.4, 0.0);
        }
    }

    private void discardSeeds() {
        if (level() instanceof ServerLevel level) {
            for (UUID id : seeds) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    entity.discard();
                }
            }
        }
        seeds.clear();
        seedSpots.clear();
    }

    /** 自動テスト：根づいている種の数。 */
    public int seedCount() {
        return level() instanceof ServerLevel level ? aliveSeeds(level) : 0;
    }

    /** 自動テスト：根づいている種。 */
    public List<OldWardKnot> seedEntities() {
        List<OldWardKnot> list = new ArrayList<>();
        if (level() instanceof ServerLevel level) {
            for (UUID id : seeds) {
                if (level.getEntity(id) instanceof OldWardKnot seed && seed.isAlive()) {
                    list.add(seed);
                }
            }
        }
        return list;
    }

    // ---- 光る節

    private int aliveKnots() {
        if (!(level() instanceof ServerLevel level)) {
            return 0;
        }
        int alive = 0;
        for (UUID id : knots) {
            Entity entity = level.getEntity(id);
            if (entity instanceof OldWardKnot knot && knot.isAlive()) {
                alive++;
            }
        }
        return alive;
    }

    /** 節を3つ生やす（幹の前・左うしろ・右うしろの高さ違い）。 */
    private void growKnots(ServerLevel level) {
        discardKnots();
        for (int i = 0; i < 3; i++) {
            OldWardKnot knot = ModEntities.OLD_WARD_KNOT.get().create(level);
            if (knot == null) {
                continue;
            }
            knot.setOwner(this, i);
            Vec3 at = knotPosition(i);
            knot.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            knot.setTransient(true);
            // 節のHPも、ボスと同じく部屋の人数で増える
            knot.setHpMultiplier(hpMultiplier());
            level.addFreshEntity(knot);
            knot.setEnemyHp(knot.getEnemyMaxHp());
            knots.add(knot.getUUID());
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.4F, 0.6F);
    }

    /**
     * 節の場所（幹のまわり。体の向きに合わせて回る）。モデルの幹の受け口（{@code tools/artgen/make_forest_models.py} の
     * KNOT_SOCKETS）に合わせる：表（顔の下、少し右）・裏の高い所・体の右の低い所。x は前を向いたときの横、z は前（+）・後ろ（−）、
     * y は足もと（節の箱の下）。幹の面より少し外へ出す（中に埋まると、打ったときに幹のほうへ当たってしまう）。
     */
    public Vec3 knotPosition(int index) {
        double[][] spots = {{-0.56, 1.5, 1.44}, {0.38, 3.0, -1.19}, {-1.5, 0.88, -0.25}};
        double[] s = spots[Math.max(0, Math.min(2, index))];
        Vec3 local = new Vec3(s[0], 0.0, s[2]).yRot((float) Math.toRadians(-yBodyRot));
        return new Vec3(getX() + local.x, getY() + s[1], getZ() + local.z);
    }

    private void placeKnots(ServerLevel level) {
        for (UUID id : knots) {
            Entity entity = level.getEntity(id);
            if (entity instanceof OldWardKnot knot && knot.isAlive()) {
                Vec3 at = knotPosition(knot.index());
                knot.setPos(at.x, at.y, at.z);
            }
        }
    }

    /** 節が1つ壊れた。3つとも壊れたら、怯む。 */
    public void onKnotBroken() {
        if (!(level() instanceof ServerLevel level) || action == Action.STUNNED) {
            return;
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_BREAK, SoundSource.HOSTILE, 1.6F, 0.5F);
        if (aliveKnots() > 0) {
            return;
        }
        EnemyDef.Skill knot = skill("knot", 0.0, 0.0, 0.0, 0.0);
        clearTelegraph();
        cageSpots.clear();
        action = Action.STUNNED;
        timer = (int) Math.round(knot.param("stun_seconds", 8.0) * 20);
        setBusy(timer);
        playAction("skill_stun");
        line("boss.amemaru.old_ward.line.stun");
        tipOnce("stun");
        level.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, getX(), getY() + 4.0, getZ(), 40, 1.5, 1.0, 1.5, 0.0);
    }

    private void tickStunned(ServerLevel level) {
        if (tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, getX(), getY() + 4.5, getZ(), 6, 1.2, 0.4, 1.2, 0.0);
        }
        if (--timer > 0) {
            return;
        }
        EnemyDef.Skill knot = skill("knot", 0.0, 0.0, 0.0, 0.0);
        regrow = (int) Math.round(knot.param(phase() >= 3 ? "regrow_enraged" : "regrow_seconds", phase() >= 3 ? 15.0 : 25.0) * 20 * pace());
        action = Action.RECOVER;
        timer = 10;
        clearBusy();
    }

    private void discardKnots() {
        if (level() instanceof ServerLevel level) {
            for (UUID id : knots) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    entity.discard();
                }
            }
        }
        knots.clear();
    }

    // ---- 暗い森（HP30%）

    private void startDark(ServerLevel level) {
        dark = true;
        setCast("dark", 40);
        // 森の灯りを吸い込む：古樹のまわりから黒い霧が広がる（11月24日）
        level.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 3.0, getZ(), 80, 3.0, 2.0, 3.0, 0.15);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 60, 6.0, 0.5, 6.0, 0.05);
        line("boss.amemaru.old_ward.line.dark");
        tip("boss.amemaru.old_ward.tip.dark");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 2.0F, 0.6F);
        EnemyDef.Skill darkSkill = skill("dark", 0.0, 0.0, 0.0, 6.0);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("on", true);
        tag.putFloat("radius", (float) darkSkill.range());
        BossRoom room = room();
        for (ServerPlayer player : targets(level)) {
            ModNetwork.sendTo(player, new TowerS2C("dark", tag));
        }
        // 森番の灯が2つ、空き地に落ちる
        Vec3 base = room != null ? room.arenaCenter() : position();
        for (int i = 0; i < (int) Math.round(darkSkill.param("lights", 2.0)); i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            Vec3 at = base.add(Math.cos(a) * 6.0, 1.0, Math.sin(a) * 6.0);
            ItemEntity drop = new ItemEntity(level, at.x, getY() + 1.0, at.z, new ItemStack(ModItems.MANIFEST.get("warden_light").get()));
            drop.setNoPickUpDelay();
            drop.setUnlimitedLifetime();
            level.addFreshEntity(drop);
            level.sendParticles(ParticleTypes.GLOW, at.x, getY() + 1.2, at.z, 20, 0.4, 0.4, 0.4, 0.05);
        }
    }

    /** 灯を持つ人から遠い人は、目がくらむ。 */
    private void darkness(ServerLevel level) {
        double radius = skill("dark", 0.0, 0.0, 0.0, 6.0).range();
        List<ServerPlayer> players = targets(level);
        List<ServerPlayer> holders = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (LightService.holdsLantern(player)) {
                holders.add(player);
            }
        }
        for (ServerPlayer player : players) {
            boolean lit = false;
            for (ServerPlayer holder : holders) {
                if (holder.distanceTo(player) <= radius) {
                    lit = true;
                    break;
                }
            }
            if (!lit) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, true));
            }
        }
    }

    private void stopDark() {
        if (!dark) {
            return;
        }
        dark = false;
        if (level() instanceof ServerLevel level) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("on", false);
            BossRoom room = room();
            List<ServerPlayer> players = room == null ? level.players() : room.participants();
            for (ServerPlayer player : players) {
                player.removeEffect(MobEffects.BLINDNESS);
            }
            // 暗さを戻す知らせは、部屋を出た人にも送る（出たあとも暗いままにならないように。11月24日）
            for (ServerPlayer player : level.players()) {
                ModNetwork.sendTo(player, new TowerS2C("dark", tag));
            }
        }
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
            tip("boss.amemaru.old_ward.tip." + key);
        }
    }

    // ---------------------------------------------------------------- 管理者

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
            case "sweep" -> {
                if (target == null) {
                    return false;
                }
                startSweep(target, skill("sweep", 1.2, 0.9, 4.0, 7.0));
            }
            case "crack" -> {
                if (target == null) {
                    return false;
                }
                startCrack(level, target, skill("crack", 1.4, 1.2, 6.0, 22.0));
            }
            case "cage" -> startCage(level, skill("cage", 1.0, 1.4, 9.0, 24.0));
            case "call" -> {
                action = Action.CALL;
                timer = 1;
            }
            case "dark" -> startDark(level);
            case "seeds" -> startSeeds(level, skill("seeds", 0.0, 1.5, 40.0, 14.0));
            default -> {
                return false;
            }
        }
        return true;
    }

    /** 自動テスト用：怯んでいるか・暗いか・残っている節の数。 */
    public boolean stunned() {
        return action == Action.STUNNED;
    }

    public boolean isDark() {
        return dark;
    }

    public int knotCount() {
        return aliveKnots();
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        stopDark();
        discardKnots();
        discardSummoned();
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        stopDark();
        discardKnots();
        super.remove(reason);
    }
}

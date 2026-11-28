package jp.amemaru.lostconnection.enemy.boss;

import jp.amemaru.lostconnection.combat.LostService;
import jp.amemaru.lostconnection.combat.PlayerColdService;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.iceforest.Mimic;
import jp.amemaru.lostconnection.entity.GroundMarker;
import jp.amemaru.lostconnection.tower.BossRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 7階のボス、鏡像獣リフレクタ（11月25日。Phase 0-A 9章の7階：「パーティメンバーの写し身を作る。本物の見分け方は、名前の表示と
 * HPバーの動き。ここでロスト状態『薄れ（名前が？？？）』が初登場」）。数値と技は【補完】。
 *
 * <ul>
 *   <li>鏡写し（mirror）：戦う人ひとりずつの写し身（{@link Mimic}。変種 {@code reflecta_copy}）を作り、全員を「薄れ」にする
 *       （味方の名前も写し身の名前も「？？？」に見える）。写し身が残っている間は、本体は鏡の守りで受けるダメージが減る
 *       （{@code ward} 倍）。写し身の名前の「？？？」は氷の色、仲間の「？？？」は白（{@code client.LostClientEvents}）。結び直しで
 *       仲間の名前は戻る。写し身が {@code last} 秒残ると、本体へ戻ってHPが少し戻る（1体ごとに {@code heal}%）</li>
 *   <li>鏡爪（claw。前の扇）・鏡片の雨（shards。線。凍える）・屈折の光（prism。見ている方の扇。当たると「薄れ」）・
 *       乱反射（scatter。全員の足もとに円。第2段階から）</li>
 *   <li>第2段階（HP70%）・第3段階（35%）の始まりに、すぐ鏡写し。技の間が短くなる</li>
 * </ul>
 */
public class Reflecta extends AmemaruBoss {
    public static final List<String> SKILLS = List.of("claw", "shards", "prism", "scatter", "mirror");
    public static final ResourceLocation COPY_VARIANT = jp.amemaru.lostconnection.AmemaruMod.id("reflecta_copy");
    private static final DustParticleOptions MIRROR = new DustParticleOptions(new Vector3f(0.78F, 0.92F, 1.0F), 1.6F);
    private static final DustParticleOptions PRISM = new DustParticleOptions(new Vector3f(0.9F, 0.7F, 1.0F), 1.4F);

    private enum Action { NONE, CLAW, SHARDS, PRISM, SCATTER, MIRROR, RECOVER }

    private Action action = Action.NONE;
    private int timer;
    private float lockedYaw;
    @Nullable
    private Vec3 lineDir;
    private final List<Vec3> marks = new ArrayList<>();
    private final Set<UUID> copies = new LinkedHashSet<>();
    private int copiesAge;
    private boolean mirrorQueued;
    /** 写し身をすべて倒した後の、鏡の守りが割れている間（この tick まで。受けるダメージが mirror.broken 倍） */
    private int openUntil;
    private final Set<String> tips = new HashSet<>();

    public Reflecta(EntityType<? extends Reflecta> type, Level level) {
        super(type, level, BossEvent.BossBarColor.WHITE);
    }

    @Override
    public String assetName() {
        return "reflecta";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_shards", "skill_prism", "skill_scatter", "skill_mirror"};
    }

    /** 11月28日：全員を狙う技は、鏡の守りが割れている間（攻めどき）は出さない。 */
    @Override
    protected boolean partyAttackAllowed() {
        return !wardBroken();
    }

    /** 11月28日：全員を狙う技が当たると、凍える。 */
    @Override
    protected void onPartyHit(ServerPlayer player, String skill) {
        jp.amemaru.lostconnection.combat.PlayerColdService.chill(player, 1, 80);
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

    private EnemyDef.Skill skill(String name, double power, double windup, double cooldown, double range) {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(power, windup, cooldown, range, Map.of()) : def.skill(name, power, windup, cooldown, range);
    }

    private EnemyDef.Skill clawSkill() {
        return skill("claw", 1.35, 1.3, 6.0, 6.5);
    }

    private EnemyDef.Skill shardsSkill() {
        return skill("shards", 1.45, 1.6, 10.0, 24.0);
    }

    private EnemyDef.Skill prismSkill() {
        return skill("prism", 1.15, 2.0, 16.0, 20.0);
    }

    private EnemyDef.Skill scatterSkill() {
        return skill("scatter", 1.1, 1.6, 18.0, 3.0);
    }

    private EnemyDef.Skill mirrorSkill() {
        return skill("mirror", 0.0, 2.0, 60.0, 6.0);
    }

    private int cooldown(EnemyDef.Skill skill) {
        double scale = phase() >= 3 ? 0.75 : phase() == 2 ? 0.88 : 1.0;
        return (int) Math.round(skill.cooldownTicks() * scale * pace());
    }

    private float animSpeed(EnemyDef.Skill skill) {
        double anim = skill.param("anim_windup", skill.windup());
        return (float) Math.max(0.4, Math.min(1.0, anim / Math.max(0.1, skill.windup())));
    }

    // ---------------------------------------------------------------- 戦いの始まり・終わり

    @Override
    public void onFightStarted(ServerLevel level, List<ServerPlayer> participants) {
        discardCopies();
        mirrorQueued = false;
        startCooldown("mirror", (int) Math.round(mirrorSkill().param("first", 20.0) * 20));
        startCooldown("prism", (int) Math.round(prismSkill().param("first", 10.0) * 20));
        startCooldown("scatter", (int) Math.round(scatterSkill().param("first", 15.0) * 20));
        line("boss.amemaru.reflecta.line.start");
        tipOnce("start");
    }

    @Override
    protected void onPhaseChanged(ServerLevel level, int from, int to) {
        if (to > from) {
            line("boss.amemaru.reflecta.line.phase" + Math.min(3, to));
            if (copies.isEmpty()) {
                mirrorQueued = true;
            }
        }
    }

    @Override
    public void resetFight() {
        super.resetFight();
        clearTelegraph();
        action = Action.NONE;
        lineDir = null;
        marks.clear();
        mirrorQueued = false;
        discardCopies();
        openUntil = 0;
        setStatus("", 0);
    }

    /** 写し身が残っている間は、鏡の守りで受けるダメージが減る。写し身をすべて倒すと、しばらく守りが割れて多く受ける。 */
    @Override
    public double damageTakenMultiplier(@Nullable Entity attacker) {
        if (!copies.isEmpty()) {
            return mirrorSkill().param("ward", 0.3);
        }
        return openUntil > tickCount ? mirrorSkill().param("broken", 1.3) : 1.0;
    }

    /** 鏡の守りが割れているか（写し身をすべて倒した後の数秒）。 */
    public boolean wardBroken() {
        return copies.isEmpty() && openUntil > tickCount;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive() || isRemoved()) {
            return;
        }
        if (tickCount % 5 == 0) {
            level.sendParticles(MIRROR, getX(), getY() + getBbHeight() * 0.7, getZ(), 2, 0.8, 0.5, 0.8, 0.0);
        }
        BossRoom room = room();
        if (room == null || !room.fighting()) {
            if (action != Action.NONE) {
                action = Action.NONE;
                clearTelegraph();
            }
            return;
        }
        tickCopies(level);
        switch (action) {
            case NONE -> choose(level);
            case CLAW -> tickClaw(level);
            case SHARDS -> tickShards(level);
            case PRISM -> tickPrism(level);
            case SCATTER -> tickScatter(level);
            case MIRROR -> tickMirror(level);
            case RECOVER -> {
                if (--timer <= 0) {
                    action = Action.NONE;
                }
            }
        }
    }

    private void tipOnce(String key) {
        if (tips.add(key)) {
            tip("boss.amemaru.reflecta.tip." + key);
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

    // ---------------------------------------------------------------- 技を選ぶ

    private void choose(ServerLevel level) {
        if (isBusy()) {
            return;
        }
        List<ServerPlayer> players = targets(level);
        if (players.isEmpty()) {
            return;
        }
        if ((mirrorQueued || ready("mirror")) && copies.isEmpty()) {
            startMirror(level);
            return;
        }
        ServerPlayer top = topThreat();
        if (top == null || !players.contains(top)) {
            top = players.get(random.nextInt(players.size()));
        }
        List<Action> options = new ArrayList<>();
        if (ready("claw") && top.distanceTo(this) <= clawSkill().range() + 1.5) {
            options.add(Action.CLAW);
            options.add(Action.CLAW);
        }
        if (ready("shards")) {
            options.add(Action.SHARDS);
        }
        if (ready("prism")) {
            options.add(Action.PRISM);
        }
        if (phase() >= 2 && ready("scatter")) {
            options.add(Action.SCATTER);
        }
        if (options.isEmpty()) {
            if (top.distanceTo(this) > clawSkill().range() - 1.0) {
                getNavigation().moveTo(top, 1.0);
            }
            return;
        }
        getNavigation().stop();
        ServerPlayer someone = players.get(random.nextInt(players.size()));
        switch (options.get(random.nextInt(options.size()))) {
            case CLAW -> startClaw(level, top);
            case SHARDS -> startShards(level, someone);
            case PRISM -> startPrism(level, someone);
            case SCATTER -> startScatter(level, players);
            default -> {
            }
        }
    }

    // ---- 鏡爪（前の扇）

    private void startClaw(ServerLevel level, ServerPlayer target) {
        EnemyDef.Skill claw = clawSkill();
        action = Action.CLAW;
        faceTowards(target.position());
        lockedYaw = getYRot();
        timer = Math.max(1, claw.windupTicks());
        setBusy(timer + 10);
        playAction("attack", animSpeed(claw));
        setCast("claw", timer);
        setTelegraph(TELEGRAPH_CONE, timer, lockedYaw, (float) claw.range(), (float) claw.param("angle", 110.0));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.HOSTILE, 1.6F, 0.6F);
    }

    private void tickClaw(ServerLevel level) {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        if (--timer > 0) {
            return;
        }
        impactFx("claw");  // 11月27日：当たった瞬間の演出
        clearTelegraph();
        EnemyDef.Skill claw = clawSkill();
        double half = claw.param("angle", 110.0) / 2.0;
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) - player.getBbWidth() / 2.0 <= claw.range() + 0.6 && inFront(player.position(), half)
                    && EnemyCombat.strike(this, player, claw.power(), null)) {
                Vec3 away = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
                if (away.lengthSqr() > 1.0E-4) {
                    away = away.normalize().scale(claw.param("push", 0.9));
                    player.push(away.x, 0.2, away.z);
                    player.hurtMarked = true;
                }
            }
        }
        double yawRad = Math.toRadians(lockedYaw);
        for (int i = 0; i <= 10; i++) {
            double a = yawRad + Math.toRadians(-half + half * 2.0 * i / 10.0);
            for (double r = 1.5; r <= claw.range(); r += 1.5) {
                level.sendParticles(MIRROR, getX() - Math.sin(a) * r, getY() + 1.0, getZ() + Math.cos(a) * r, 1, 0.1, 0.1, 0.1, 0.0);
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.4F, 1.2F);
        startCooldown("claw", cooldown(claw));
        action = Action.RECOVER;
        timer = 10;
    }

    // ---- 鏡片の雨（線。凍える）

    private void startShards(ServerLevel level, ServerPlayer target) {
        EnemyDef.Skill shards = shardsSkill();
        action = Action.SHARDS;
        faceTowards(target.position());
        lockedYaw = getYRot();
        lineDir = Vec3.directionFromRotation(0.0F, lockedYaw);
        timer = Math.max(1, shards.windupTicks());
        setBusy(timer + 12);
        playAction("skill_shards", animSpeed(shards));
        setCast("shards", timer);
        setTelegraph(TELEGRAPH_LINE, timer, lockedYaw, (float) shards.range(), (float) shards.param("width", 2.4));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.6F, 0.7F);
        tipOnce("shards");
    }

    private void tickShards(ServerLevel level) {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        if (--timer > 0 || lineDir == null) {
            return;
        }
        impactFx("shards");  // 11月27日：当たった瞬間の演出
        clearTelegraph();
        EnemyDef.Skill shards = shardsSkill();
        double half = shards.param("width", 2.4) / 2.0 + 0.4;
        for (ServerPlayer player : targets(level)) {
            Vec3 rel = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            double along = rel.dot(lineDir);
            double side = rel.subtract(lineDir.scale(along)).length();
            if (along >= 0.0 && along <= shards.range() + 0.6 && side <= half && Math.abs(player.getY() - getY()) < 4.0
                    && EnemyCombat.strike(this, player, shards.power(), null)) {
                PlayerColdService.chill(player, (int) Math.round(shards.param("chill", 1.0)), 80);
            }
        }
        for (double t = 1.5; t <= shards.range(); t += 1.0) {
            Vec3 at = position().add(lineDir.scale(t));
            level.sendParticles(MIRROR, at.x, at.y + 0.8, at.z, 3, 0.3, 0.4, 0.3, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.6, at.z, 1, 0.2, 0.3, 0.2, 0.02);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0F, 1.4F);
        lineDir = null;
        startCooldown("shards", cooldown(shards));
        action = Action.RECOVER;
        timer = 12;
    }

    // ---- 屈折の光（見ている方の扇。当たると「薄れ」）

    private void startPrism(ServerLevel level, ServerPlayer target) {
        EnemyDef.Skill prism = prismSkill();
        action = Action.PRISM;
        faceTowards(target.position());
        lockedYaw = getYRot();
        timer = Math.max(10, prism.windupTicks());
        setBusy(timer + 12);
        playAction("skill_prism", animSpeed(prism));
        setCast("prism", timer);
        setTelegraph(TELEGRAPH_CONE, timer, lockedYaw, (float) prism.range(), (float) prism.param("angle", 50.0));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.6F, 1.6F);
        tipOnce("prism");
    }

    private void tickPrism(ServerLevel level) {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        setYHeadRot(lockedYaw);
        if (--timer > 0) {
            if (timer % 3 == 0) {
                Vec3 eye = getEyePosition();
                level.sendParticles(PRISM, eye.x, eye.y, eye.z, 3, 0.3, 0.2, 0.3, 0.0);
            }
            return;
        }
        impactFx("prism");  // 11月27日：当たった瞬間の演出
        clearTelegraph();
        EnemyDef.Skill prism = prismSkill();
        double half = prism.param("angle", 50.0) / 2.0;
        int fade = (int) Math.round(prism.param("fade_seconds", 12.0) * 20);
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) <= prism.range() + 0.6 && inFront(player.position(), half)
                    && EnemyCombat.strike(this, player, prism.power(), null)) {
                LostService.inflict(player, ModEffects.LOST_FADE.get(), fade);
            }
        }
        double yawRad = Math.toRadians(lockedYaw);
        for (int i = 0; i <= 8; i++) {
            double a = yawRad + Math.toRadians(-half + half * 2.0 * i / 8.0);
            for (double r = 2.0; r <= prism.range(); r += 2.0) {
                level.sendParticles(PRISM, getX() - Math.sin(a) * r, getY() + 1.6, getZ() + Math.cos(a) * r, 1, 0.1, 0.2, 0.1, 0.0);
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.0F, 1.8F);
        startCooldown("prism", cooldown(prism));
        action = Action.RECOVER;
        timer = 12;
    }

    // ---- 乱反射（全員の足もとに円）

    private void startScatter(ServerLevel level, List<ServerPlayer> players) {
        EnemyDef.Skill scatter = scatterSkill();
        action = Action.SCATTER;
        timer = Math.max(10, scatter.windupTicks());
        setBusy(timer + 12);
        playAction("skill_scatter", animSpeed(scatter));
        setCast("scatter", timer);
        marks.clear();
        for (ServerPlayer player : players) {
            Vec3 at = player.position();
            marks.add(at);
            GroundMarker.place(level, at, (float) scatter.range(), timer, GroundMarker.KIND_DANGER);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2.0F, 0.5F);
        tipOnce("scatter");
    }

    private void tickScatter(ServerLevel level) {
        if (--timer > 0) {
            return;
        }
        EnemyDef.Skill scatter = scatterSkill();
        double radius = scatter.range() + 0.3;
        for (Vec3 at : marks) {
            impactFx("scatter", TELEGRAPH_CIRCLE, at, 0.0F, (float) radius, 0.0F);  // 11月27日：当たった瞬間の演出
        }
        for (ServerPlayer player : targets(level)) {
            for (Vec3 at : marks) {
                double dx = player.getX() - at.x;
                double dz = player.getZ() - at.z;
                if (dx * dx + dz * dz <= radius * radius && Math.abs(player.getY() - at.y) < 3.0) {
                    EnemyCombat.strike(this, player, scatter.power(), null);
                    break;
                }
            }
        }
        for (Vec3 at : marks) {
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 20, radius * 0.4, 0.6, radius * 0.4, 0.06);
            level.sendParticles(MIRROR, at.x, at.y + 1.0, at.z, 16, radius * 0.4, 1.0, radius * 0.4, 0.0);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0F, 0.9F);
        marks.clear();
        startCooldown("scatter", cooldown(scatter));
        action = Action.RECOVER;
        timer = 12;
    }

    // ---- 鏡写し（写し身を作り、全員を「薄れ」に）

    private void startMirror(ServerLevel level) {
        EnemyDef.Skill mirror = mirrorSkill();
        action = Action.MIRROR;
        mirrorQueued = false;
        timer = Math.max(10, mirror.windupTicks());
        setBusy(timer + 12);
        getNavigation().stop();
        playAction("skill_mirror", animSpeed(mirror));
        setCast("mirror", timer);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.HOSTILE, 2.0F, 0.8F);
        line("boss.amemaru.reflecta.line.mirror");
    }

    private void tickMirror(ServerLevel level) {
        if (timer % 4 == 0) {
            level.sendParticles(MIRROR, getX(), getY() + 2.0, getZ(), 12, 1.6, 1.2, 1.6, 0.0);
        }
        if (--timer > 0) {
            return;
        }
        spawnCopies(level);
        startCooldown("mirror", cooldown(mirrorSkill()));
        action = Action.RECOVER;
        timer = 12;
    }

    /** 戦う人ひとりずつの写し身を作り、全員を「薄れ」にする（管理者からも）。作った数。 */
    public int spawnCopies(ServerLevel level) {
        return spawnCopies(level, targets(level));
    }

    /** {@code players} のひとりずつの写し身を作り、全員を「薄れ」にする（自動テストからも）。作った数。 */
    public int spawnCopies(ServerLevel level, List<ServerPlayer> players) {
        EnemyDef.Skill mirror = mirrorSkill();
        int max = (int) Math.round(mirror.param("max", 6.0));
        int fade = (int) Math.round(mirror.param("fade_seconds", 20.0) * 20);
        int made = 0;
        for (int i = 0; i < players.size() && made < max; i++) {
            ServerPlayer player = players.get(i);
            Mimic copy = ModEntities.MIMIC.get().create(level);
            if (copy == null) {
                continue;
            }
            copy.setVariant(COPY_VARIANT);
            copy.copy(player.getUUID());
            double a = Math.PI * 2.0 * i / Math.max(1, players.size()) + random.nextDouble() * 0.5;
            double r = mirror.range();
            copy.moveTo(getX() + Math.cos(a) * r, getY() + 0.2, getZ() + Math.sin(a) * r, player.getYRot(), 0.0F);
            copy.setHome(BlockPos.containing(position()));
            copy.setTransient(true);
            level.addFreshEntity(copy);
            ServerPlayer prey = players.size() > 1 ? players.get((i + 1) % players.size()) : player;
            copy.addThreat(prey, 2.0);
            copies.add(copy.getUUID());
            level.sendParticles(ParticleTypes.END_ROD, copy.getX(), copy.getY() + 1.0, copy.getZ(), 20, 0.3, 0.8, 0.3, 0.05);
            made++;
        }
        for (ServerPlayer player : players) {
            LostService.inflict(player, ModEffects.LOST_FADE.get(), fade);
        }
        copiesAge = 0;
        openUntil = 0;
        setStatus("ward:" + copies.size(), 0);
        tipOnce("mirror");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 2.0F, 0.9F);
        return made;
    }

    /** 自動テスト：部屋がなくても、写し身の見張りを1回動かす。 */
    public void testTickCopies(ServerLevel level) {
        tickCopies(level);
    }

    private void tickCopies(ServerLevel level) {
        if (copies.isEmpty()) {
            if (openUntil > 0) {
                int left = (openUntil - tickCount + 19) / 20;
                if (left <= 0) {
                    openUntil = 0;
                    setStatus("", 0);
                } else {
                    setStatus("open", left);
                }
            }
            return;
        }
        int before = copies.size();
        copies.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        if (copies.isEmpty()) {
            int seconds = (int) Math.round(mirrorSkill().param("open_seconds", 10.0));
            openUntil = tickCount + seconds * 20;
            setStatus("open", seconds);
            line("boss.amemaru.reflecta.line.broken");
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0F, 0.6F);
            level.sendParticles(MIRROR, getX(), getY() + 2.0, getZ(), 50, 1.6, 1.4, 1.6, 0.0);
            return;
        }
        if (copies.size() != before) {
            setStatus("ward:" + copies.size(), 0);
        }
        EnemyDef.Skill mirror = mirrorSkill();
        if (++copiesAge >= (int) Math.round(mirror.param("last", 45.0) * 20)) {
            // 長く残った写し身は、本体へ戻る（HPが少し戻る）
            int back = copies.size();
            discardCopies();
            setEnemyHp(Math.min(getEnemyMaxHp(), getEnemyHp() + getEnemyMaxHp() * mirror.param("heal", 3.0) / 100.0 * back));
            setStatus("", 0);
            line("boss.amemaru.reflecta.line.return");
            level.sendParticles(MIRROR, getX(), getY() + 2.0, getZ(), 40, 1.5, 1.5, 1.5, 0.0);
        }
    }

    private void discardCopies() {
        if (level() instanceof ServerLevel level) {
            for (UUID id : copies) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    level.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY() + 1.0, entity.getZ(), 10, 0.3, 0.6, 0.3, 0.04);
                    entity.discard();
                }
            }
        }
        copies.clear();
    }

    // ---------------------------------------------------------------- 管理者・自動テスト

    /** いま残っている写し身の数。 */
    public int copies() {
        return copies.size();
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
            case "claw" -> startClaw(level, players.get(0));
            case "shards" -> startShards(level, players.get(0));
            case "prism" -> startPrism(level, players.get(0));
            case "scatter" -> startScatter(level, players);
            case "mirror" -> {
                discardCopies();
                startMirror(level);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        discardCopies();
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        discardCopies();
        super.remove(reason);
    }
}

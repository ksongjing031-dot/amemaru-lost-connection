package jp.amemaru.lostconnection.enemy.boss;

import jp.amemaru.lostconnection.combat.PlayerColdService;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.castle.FrozenGuard;
import jp.amemaru.lostconnection.entity.GroundMarker;
import jp.amemaru.lostconnection.tower.BossRoom;
import jp.amemaru.lostconnection.tower.FloorDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Blocks;
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
 * 9階のボス、氷の近衛長ヴァルグリム（11月28日。Phase 0-A 9章の9階：「今も女王を守っている近衛隊長の残響。部下を守る『盾の誓い』を
 * 使うため、部下と引き離すと崩れる。守れなかった隊長という点でガルドと重なる」）。数値と技の細かいところは【補完】。
 *
 * <ul>
 *   <li><b>盾の誓い</b>（oath）：部下の近衛（{@link FrozenGuard}。定義 {@code valgrim_guard}）は、近衛長から {@code oath.range}
 *       の中にいる間、青い光の鎖でつながり、<b>傷つかない</b>（近衛長の盾が受ける）。つながった部下が1人でもいる間は、近衛長も
 *       受けるダメージが {@code oath.ward} 倍。部下を引きつけて近衛長から離すと、鎖が切れ、その部下は傷つくようになる</li>
 *   <li><b>崩れ</b>（crumble）：つながっている部下がいなくなると（離した・倒した）、近衛長は膝をつき、{@code crumble.seconds} 秒
 *       動けず、受けるダメージが {@code crumble.taken} 倍（攻めどき）。立ち上がると、残った部下を呼び戻し（集え）、部下が少なければ
 *       部屋のふちから新しい近衛を呼ぶ（増援。第2段階から。{@code reinforce}）。立ち上がってから {@code crumble.regroup} 秒は、
 *       また崩れない</li>
 *   <li>集え（rally）：離れた部下を呼び戻す（部下は人を追うのをやめて、近衛長のそばへ走る。走っている間は傷つく）</li>
 *   <li>槍の突き（thrust。前の線）・盾打ち（bash。前の扇。押し返す）・氷槍の列（spears。何人かへ向けて、足もとから氷の槍の列）・
 *       誓いの構え（guard。前から受けるダメージが {@code guard.front} 倍。後ろへ回る）</li>
 *   <li><b>近衛の行進</b>（march。仕掛けをしくじったときの一撃）：誓いが {@code march.limit} 秒切れないまま続くと、近衛長は部下と
 *       隊列を組み、部屋を横切る太い帯を進む（帯の外へ逃げる。とても強い）</li>
 *   <li>第2段階（{@code 0.65}）：増援が来るようになる。第3段階（{@code 0.3}）「守れなかった隊長」：誓いを解き、残った部下は砕ける。
 *       近衛長ひとりで、速く・強く戦う（技の間 ×0.75。氷槍の列が近衛長のまわり全部へ）</li>
 *   <li>全員を狙う技（一斉の印・大技。{@link PartyAttacks}）は、崩れている間は出さない</li>
 * </ul>
 */
public class Valgrim extends AmemaruBoss {
    public static final List<String> SKILLS = List.of("thrust", "bash", "spears", "guard", "rally", "march");
    public static final ResourceLocation GUARD_VARIANT = jp.amemaru.lostconnection.AmemaruMod.id("valgrim_guard");
    private static final DustParticleOptions OATH = new DustParticleOptions(new Vector3f(0.45F, 0.7F, 1.0F), 1.4F);
    private static final DustParticleOptions FROST = new DustParticleOptions(new Vector3f(0.85F, 0.95F, 1.0F), 1.6F);
    /** 場所の組の名前（階の定義の boss_marks）。 */
    private static final String SPOTS = "guard_spots";
    private static final String REINFORCE = "reinforce";

    private enum Action { NONE, THRUST, BASH, SPEARS, GUARD, RALLY, MARCH, CRUMBLE, RECOVER }

    private Action action = Action.NONE;
    private int timer;
    private float lockedYaw;
    @Nullable
    private Vec3 lineDir;
    private final List<List<Vec3>> spearLines = new ArrayList<>();
    /** 部下（近衛）。 */
    private final Set<UUID> guards = new LinkedHashSet<>();
    /** いまつながっている部下の数（毎 tick 数え直す）。 */
    private int linked;
    /** 誓いが切れずに続いている tick（行進の数え）。 */
    private int unbroken;
    /** 立ち上がってから、また崩れない残り（tick）。 */
    private int regroup;
    /** 誓いの構えの残り（tick）。 */
    private int guarding;
    /** 誓いを解いたか（第3段階）。 */
    private boolean released;
    private final Set<String> tips = new HashSet<>();

    public Valgrim(EntityType<? extends Valgrim> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
    }

    @Override
    public String assetName() {
        return "valgrim";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_thrust", "skill_bash", "skill_spears", "skill_guard", "skill_rally", "skill_march", "skill_crumble"};
    }

    @Override
    protected boolean acting() {
        return action != Action.NONE;
    }

    /** 全員を狙う技は、崩れている間（攻めどき）と、行進の間は出さない。 */
    @Override
    protected boolean partyAttackAllowed() {
        return action != Action.CRUMBLE && action != Action.MARCH;
    }

    /** 全員を狙う技が当たると、凍える。 */
    @Override
    protected void onPartyHit(ServerPlayer player, String skill) {
        PlayerColdService.chill(player, 1, 80);
    }

    @Override
    protected int phaseFor(double ratio) {
        if (ratio <= 0.3) {
            return 3;
        }
        return ratio <= 0.65 ? 2 : 1;
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

    private EnemyDef.Skill thrustSkill() {
        return skill("thrust", 1.5, 1.2, 5.0, 9.0);
    }

    private EnemyDef.Skill bashSkill() {
        return skill("bash", 1.3, 1.0, 6.0, 5.0);
    }

    private EnemyDef.Skill spearsSkill() {
        return skill("spears", 1.4, 1.7, 12.0, 22.0);
    }

    private EnemyDef.Skill guardSkill() {
        return skill("guard", 0.0, 0.6, 20.0, 0.0);
    }

    private EnemyDef.Skill rallySkill() {
        return skill("rally", 0.0, 1.2, 18.0, 30.0);
    }

    private EnemyDef.Skill marchSkill() {
        return skill("march", 3.4, 3.6, 10.0, 46.0);
    }

    private EnemyDef.Skill oathSkill() {
        return skill("oath", 0.0, 0.0, 0.0, 12.0);
    }

    private EnemyDef.Skill crumbleSkill() {
        return skill("crumble", 0.0, 0.0, 0.0, 0.0);
    }

    private EnemyDef.Skill reinforceSkill() {
        return skill("reinforce", 0.0, 0.0, 0.0, 0.0);
    }

    private int cooldown(EnemyDef.Skill skill) {
        double scale = released ? 0.75 : phase() == 2 ? 0.9 : 1.0;
        return (int) Math.round(skill.cooldownTicks() * scale * pace());
    }

    // ---------------------------------------------------------------- 場所

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

    private void tipOnce(String key) {
        if (tips.add(key)) {
            tip("boss.amemaru.valgrim.tip." + key);
        }
    }

    // ---------------------------------------------------------------- 戦いの始まり・終わり

    @Override
    public void onFightStarted(ServerLevel level, List<ServerPlayer> participants) {
        discardGuards(false);
        released = false;
        unbroken = 0;
        regroup = 0;
        guarding = 0;
        tips.clear();
        summonGuards(level, 4, false);
        startCooldown("spears", (int) Math.round(spearsSkill().param("first", 10.0) * 20));
        startCooldown("guard", (int) Math.round(guardSkill().param("first", 16.0) * 20));
        startCooldown("rally", (int) Math.round(rallySkill().param("first", 12.0) * 20));
        line("boss.amemaru.valgrim.line.start");
        tipOnce("oath");
    }

    @Override
    protected void onPhaseChanged(ServerLevel level, int from, int to) {
        if (to <= from) {
            return;
        }
        if (to >= 3) {
            release(level);
            return;
        }
        line("boss.amemaru.valgrim.line.phase2");
        int missing = 4 - aliveGuards(level);
        if (missing > 0) {
            summonGuards(level, Math.min(missing, (int) Math.round(reinforceSkill().param("count", 2.0))), true);
        }
    }

    @Override
    public void resetFight() {
        super.resetFight();
        clearTelegraph();
        action = Action.NONE;
        lineDir = null;
        spearLines.clear();
        discardGuards(false);
        released = false;
        unbroken = 0;
        regroup = 0;
        guarding = 0;
        linked = 0;
        setStatus("", 0);
    }

    @Override
    public void onVictory(ServerLevel level, List<ServerPlayer> winners, boolean first) {
        discardGuards(true);
        line("boss.amemaru.valgrim.line.defeat");
    }

    /** 受けるダメージの倍率：誓い（つながった部下がいる間）・崩れ・誓いの構え（前から）。 */
    @Override
    public double damageTakenMultiplier(@Nullable Entity attacker) {
        if (action == Action.CRUMBLE) {
            return crumbleSkill().param("taken", 1.5);
        }
        double scale = linked > 0 ? oathSkill().param("ward", 0.35) : 1.0;
        if (guarding > 0 && attacker != null && inFront(attacker.position(), guardSkill().param("angle", 140.0) / 2.0)) {
            scale *= guardSkill().param("front", 0.25);
            if (level() instanceof ServerLevel level && tickCount % 4 == 0) {
                level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 2.0, getZ(), 8, 0.6, 0.6, 0.6, 0.1);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.2F, 0.6F);
            }
        }
        return scale;
    }

    /** 部下が、誓いで守られて叩かれた（{@link FrozenGuard} から）：盾が受ける光。 */
    public void onOathBlocked(FrozenGuard guard, @Nullable Entity attacker) {
        if (!(level() instanceof ServerLevel level) || guard.tickCount % 4 != 0) {
            return;
        }
        level.sendParticles(OATH, guard.getX(), guard.getY() + 1.4, guard.getZ(), 6, 0.4, 0.5, 0.4, 0.0);
        level.playSound(null, guard.getX(), guard.getY(), guard.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.9F, 1.4F);
        if (attacker instanceof ServerPlayer) {
            tipOnce("blocked");
        }
    }

    // ---------------------------------------------------------------- 毎 tick

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive() || isRemoved()) {
            return;
        }
        if (tickCount % 6 == 0) {
            level.sendParticles(FROST, getX(), getY() + getBbHeight() * 0.6, getZ(), 2, 0.7, 0.8, 0.7, 0.0);
        }
        BossRoom room = room();
        if (room == null || !room.fighting()) {
            if (action != Action.NONE) {
                action = Action.NONE;
                clearTelegraph();
            }
            return;
        }
        tickOath(level);
        if (guarding > 0) {
            guarding--;
        }
        if (tickCount % 10 == 0) {
            setStatusFromOath();
        }
        switch (action) {
            case NONE -> choose(level);
            case THRUST -> tickThrust(level);
            case BASH -> tickBash(level);
            case SPEARS -> tickSpears(level);
            case GUARD -> tickGuard(level);
            case RALLY -> tickRally(level);
            case MARCH -> tickMarch(level);
            case CRUMBLE -> tickCrumble(level);
            case RECOVER -> {
                if (--timer <= 0) {
                    action = Action.NONE;
                }
            }
        }
    }

    // ---------------------------------------------------------------- 盾の誓い

    private int aliveGuards(ServerLevel level) {
        int n = 0;
        for (UUID id : guards) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) {
                n++;
            }
        }
        return n;
    }

    /** 部下を数え、誓いでつなぐ・ほどく。つながりが消えたら崩れる。 */
    private void tickOath(ServerLevel level) {
        double range = oathSkill().range();
        int before = linked;
        linked = 0;
        int alive = 0;
        guards.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        for (UUID id : guards) {
            if (!(level.getEntity(id) instanceof FrozenGuard guard)) {
                continue;
            }
            alive++;
            boolean link = !released && guard.distanceTo(this) <= range && Math.abs(guard.getY() - getY()) < 6.0;
            guard.setOath(getUUID(), link);
            if (link) {
                linked++;
                if (tickCount % 3 == 0) {
                    drawChain(level, guard);
                }
            } else if (tickCount % 20 == 0) {
                level.sendParticles(ParticleTypes.SNOWFLAKE, guard.getX(), guard.getY() + 1.6, guard.getZ(), 4, 0.3, 0.3, 0.3, 0.01);
            }
        }
        if (before > linked && linked > 0) {
            // 鎖が1本切れた
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.6F, 0.8F);
            tipOnce("separate");
        }
        if (released) {
            return;
        }
        if (linked > 0) {
            if (action != Action.CRUMBLE && action != Action.MARCH) {
                unbroken++;
            }
        } else {
            unbroken = 0;
        }
        if (regroup > 0) {
            regroup--;
        }
        // つながりがなくなった：崩れる（立ち上がってすぐは崩れない）
        if (linked == 0 && before > 0 && regroup <= 0 && action != Action.CRUMBLE && action != Action.MARCH) {
            startCrumble(level);
        } else if (linked == 0 && alive == 0 && regroup <= 0 && action == Action.NONE && !isBusy() && phase() < 3) {
            // 部下がひとりもいない（部屋に入った直後に倒しきった・増援を待っている）：崩れたまま立てない時間はないので、呼ぶ
            startCrumble(level);
        }
    }

    private void setStatusFromOath() {
        if (action == Action.CRUMBLE) {
            return;
        }
        if (guarding > 0) {
            setStatus("guard", (guarding + 19) / 20);
        } else if (released) {
            setStatus("", 0);
        } else if (linked > 0) {
            int left = Math.max(0, (int) Math.round(marchSkill().param("limit", 45.0) - unbroken / 20.0));
            setStatus("oath:" + linked, left);
        } else {
            setStatus("", 0);
        }
    }

    /** 近衛長と部下をつなぐ、青い光の鎖。 */
    private void drawChain(ServerLevel level, FrozenGuard guard) {
        Vec3 from = position().add(0.0, getBbHeight() * 0.55, 0.0);
        Vec3 to = guard.position().add(0.0, guard.getBbHeight() * 0.6, 0.0);
        Vec3 step = to.subtract(from);
        int n = Math.max(2, (int) (step.length() / 0.9));
        for (int i = 1; i < n; i++) {
            double t = (double) i / n;
            double sag = Math.sin(t * Math.PI) * 0.6;
            Vec3 at = from.add(step.scale(t)).add(0.0, -sag, 0.0);
            level.sendParticles(OATH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** 部下を呼ぶ（戦いの始まり：近衛の円のまわり。増援：部屋のふちから歩いてくる）。呼んだ数。自動テストからも。 */
    public int summonGuards(ServerLevel level, int count, boolean fromEdge) {
        List<BlockPos> spots = new ArrayList<>(marks(fromEdge ? REINFORCE : SPOTS));
        List<ServerPlayer> players = targets(level);
        int made = 0;
        for (int i = 0; i < count; i++) {
            FrozenGuard guard = ModEntities.FROZEN_GUARD.get().create(level);
            if (guard == null) {
                continue;
            }
            Vec3 at;
            if (!spots.isEmpty()) {
                BlockPos pick = spots.get(0);
                if (fromEdge) {
                    // 増援は、人から遠いふちから（同じ所に2人は出さない）
                    double bestScore = -1.0;
                    for (BlockPos p : spots) {
                        double near = 1.0E6;
                        for (ServerPlayer player : players) {
                            near = Math.min(near, player.distanceToSqr(Vec3.atBottomCenterOf(p)));
                        }
                        double score = near + random.nextDouble() * 60.0;
                        if (score > bestScore) {
                            bestScore = score;
                            pick = p;
                        }
                    }
                }
                spots.remove(pick);
                at = Vec3.atBottomCenterOf(pick);
            } else {
                double a = Math.PI * 2.0 * i / Math.max(1, count);
                at = position().add(Math.cos(a) * 5.0, 0.0, Math.sin(a) * 5.0);
            }
            guard.setVariant(GUARD_VARIANT);
            float yaw = (float) Math.toDegrees(Math.atan2(-(getX() - at.x), getZ() - at.z));
            guard.moveTo(at.x, at.y, at.z, yaw + 180.0F, 0.0F);
            guard.setHome(BlockPos.containing(at));
            guard.setTransient(true);
            guard.wakeNow();
            guard.setOath(getUUID(), false);
            level.addFreshEntity(guard);
            guards.add(guard.getUUID());
            if (fromEdge) {
                guard.rallyTo(position(), 200);
            } else if (!players.isEmpty()) {
                guard.addThreat(players.get(random.nextInt(players.size())), 1.0);
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), at.x, at.y + 1.0, at.z,
                    24, 0.4, 0.8, 0.4, 0.05);
            level.sendParticles(OATH, at.x, at.y + 1.2, at.z, 12, 0.3, 0.8, 0.3, 0.0);
            made++;
        }
        if (fromEdge && made > 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0F, 1.1F);
            line("boss.amemaru.valgrim.line.reinforce");
            tipOnce("reinforce");
        }
        return made;
    }

    /** 部下を消す（戦いのやり直し・倒されたとき）。{@code shatter}：砕ける演出。 */
    private void discardGuards(boolean shatter) {
        if (level() instanceof ServerLevel level) {
            for (UUID id : guards) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    if (shatter) {
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                                entity.getX(), entity.getY() + 1.0, entity.getZ(), 30, 0.4, 0.8, 0.4, 0.06);
                        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE,
                                1.2F, 0.7F);
                    }
                    entity.discard();
                }
            }
        }
        guards.clear();
        linked = 0;
    }

    /** 第3段階「守れなかった隊長」：誓いを解く。残った部下は砕ける。 */
    private void release(ServerLevel level) {
        released = true;
        clearTelegraph();
        action = Action.NONE;
        clearBusy();
        discardGuards(true);
        setStatus("", 0);
        line("boss.amemaru.valgrim.line.phase3");
        tipOnce("release");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
        level.sendParticles(OATH, getX(), getY() + 2.0, getZ(), 60, 2.0, 1.5, 2.0, 0.0);
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
        // 誓いが長く切れない：近衛の行進（しくじったときの一撃）
        if (!released && linked > 0 && unbroken >= marchSkill().param("limit", 45.0) * 20) {
            startMarch(level, players);
            return;
        }
        // 部下が離れている：集え
        if (!released && ready("rally") && guards.size() > linked) {
            startRally(level);
            return;
        }
        ServerPlayer top = topThreat();
        if (top == null || !players.contains(top)) {
            top = players.get(random.nextInt(players.size()));
        }
        double distance = top.distanceTo(this);
        List<Action> options = new ArrayList<>();
        if (ready("bash") && distance <= bashSkill().range() + 1.0) {
            options.add(Action.BASH);
            options.add(Action.BASH);
        }
        if (ready("thrust") && distance <= thrustSkill().range()) {
            options.add(Action.THRUST);
            options.add(Action.THRUST);
        }
        if (ready("spears")) {
            options.add(Action.SPEARS);
        }
        if (ready("guard") && guarding <= 0) {
            options.add(Action.GUARD);
        }
        if (options.isEmpty()) {
            if (distance > thrustSkill().range() - 1.5) {
                getNavigation().moveTo(top, released ? 1.25 : 1.0);
            }
            return;
        }
        getNavigation().stop();
        switch (options.get(random.nextInt(options.size()))) {
            case BASH -> startBash(level, top);
            case THRUST -> startThrust(level, top);
            case SPEARS -> startSpears(level, players);
            case GUARD -> startGuard(level);
            default -> {
            }
        }
    }

    // ---- 槍の突き（前の線）

    private void startThrust(ServerLevel level, ServerPlayer target) {
        EnemyDef.Skill thrust = thrustSkill();
        action = Action.THRUST;
        faceTowards(target.position());
        lockedYaw = getYRot();
        lineDir = Vec3.directionFromRotation(0.0F, lockedYaw);
        timer = Math.max(1, thrust.windupTicks());
        setBusy(timer + 10);
        playAction("skill_thrust");
        setTelegraph(TELEGRAPH_LINE, timer, lockedYaw, (float) thrust.range(), (float) thrust.param("width", 2.2));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.HOSTILE, 1.4F, 0.7F);
    }

    private void tickThrust(ServerLevel level) {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        if (--timer > 0 || lineDir == null) {
            return;
        }
        impactFx("thrust");
        clearTelegraph();
        EnemyDef.Skill thrust = thrustSkill();
        double half = thrust.param("width", 2.2) / 2.0 + 0.4;
        for (ServerPlayer player : targets(level)) {
            Vec3 rel = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            double along = rel.dot(lineDir);
            double side = rel.subtract(lineDir.scale(along)).length();
            if (along >= -0.5 && along <= thrust.range() + 0.6 && side <= half && Math.abs(player.getY() - getY()) < 4.0
                    && EnemyCombat.strike(this, player, thrust.power(), null)) {
                PlayerColdService.chill(player, 1, 80);
            }
        }
        for (double t = 1.0; t <= thrust.range(); t += 1.0) {
            Vec3 at = position().add(lineDir.scale(t));
            level.sendParticles(FROST, at.x, at.y + 1.2, at.z, 2, 0.2, 0.2, 0.2, 0.0);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 1.6F, 0.6F);
        lineDir = null;
        startCooldown("thrust", cooldown(thrust));
        action = Action.RECOVER;
        timer = 8;
    }

    // ---- 盾打ち（前の扇。押し返す）

    private void startBash(ServerLevel level, ServerPlayer target) {
        EnemyDef.Skill bash = bashSkill();
        action = Action.BASH;
        faceTowards(target.position());
        lockedYaw = getYRot();
        timer = Math.max(1, bash.windupTicks());
        setBusy(timer + 10);
        playAction("skill_bash");
        setTelegraph(TELEGRAPH_CONE, timer, lockedYaw, (float) bash.range(), (float) bash.param("angle", 110.0));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.HOSTILE, 1.6F, 0.6F);
    }

    private void tickBash(ServerLevel level) {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        if (--timer > 0) {
            return;
        }
        impactFx("bash");
        clearTelegraph();
        EnemyDef.Skill bash = bashSkill();
        double half = bash.param("angle", 110.0) / 2.0;
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) - player.getBbWidth() / 2.0 <= bash.range() + 0.6 && inFront(player.position(), half)
                    && EnemyCombat.strike(this, player, bash.power(), null)) {
                Vec3 away = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
                if (away.lengthSqr() > 1.0E-4) {
                    away = away.normalize().scale(bash.param("push", 1.4));
                    player.push(away.x, 0.3, away.z);
                    player.hurtMarked = true;
                }
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 2.0F, 0.5F);
        startCooldown("bash", cooldown(bash));
        action = Action.RECOVER;
        timer = 10;
    }

    // ---- 氷槍の列（何人かへ向けて、足もとから）

    private void startSpears(ServerLevel level, List<ServerPlayer> players) {
        EnemyDef.Skill spears = spearsSkill();
        action = Action.SPEARS;
        timer = Math.max(10, spears.windupTicks());
        setBusy(timer + 12);
        playAction("skill_spears");
        spearLines.clear();
        double gap = spears.param("gap", 2.2);
        float radius = (float) spears.param("radius", 1.4);
        List<Vec3> dirs = new ArrayList<>();
        if (released) {
            // 第3段階：まわり全部へ（8本）
            int n = (int) Math.round(spears.param("ring", 8.0));
            double base = random.nextDouble() * Math.PI * 2.0;
            for (int i = 0; i < n; i++) {
                double a = base + Math.PI * 2.0 * i / n;
                dirs.add(new Vec3(Math.cos(a), 0.0, Math.sin(a)));
            }
        } else {
            List<ServerPlayer> pool = new ArrayList<>(players);
            java.util.Collections.shuffle(pool, new java.util.Random(random.nextLong()));
            int n = Math.min(pool.size(), (int) Math.round(spears.param("lines", 3.0)));
            for (int i = 0; i < n; i++) {
                Vec3 d = pool.get(i).position().subtract(position()).multiply(1.0, 0.0, 1.0);
                dirs.add(d.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, getYRot()) : d.normalize());
            }
        }
        for (Vec3 d : dirs) {
            List<Vec3> line = new ArrayList<>();
            for (double t = 2.5; t <= spears.range(); t += gap) {
                Vec3 at = position().add(d.scale(t));
                line.add(at);
                GroundMarker.place(level, at, radius, timer + (int) (t * 0.6), GroundMarker.KIND_DANGER);
            }
            spearLines.add(line);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_PLACE, SoundSource.HOSTILE, 2.0F, 0.5F);
        tipOnce("spears");
    }

    private void tickSpears(ServerLevel level) {
        timer--;
        if (timer > 0) {
            return;
        }
        // 近い所から順に、少しずつ遅れて突き出す
        EnemyDef.Skill spears = spearsSkill();
        double radius = spears.param("radius", 1.4) + 0.3;
        int step = -timer;
        boolean any = false;
        Set<UUID> hit = new HashSet<>();
        for (List<Vec3> line : spearLines) {
            if (step / 2 < line.size()) {
                any = true;
                if (step % 2 == 0) {
                    Vec3 at = line.get(step / 2);
                    impactFx("spears", TELEGRAPH_CIRCLE, at, 0.0F, (float) radius, 0.0F);
                    for (ServerPlayer player : targets(level)) {
                        double dx = player.getX() - at.x;
                        double dz = player.getZ() - at.z;
                        if (dx * dx + dz * dz <= radius * radius && Math.abs(player.getY() - at.y) < 3.0 && hit.add(player.getUUID())
                                && EnemyCombat.strike(this, player, spears.power(), null)) {
                            player.push(0.0, 0.45, 0.0);
                            player.hurtMarked = true;
                        }
                    }
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), at.x, at.y + 0.6,
                            at.z, 10, 0.3, 0.6, 0.3, 0.05);
                    if (step % 4 == 0) {
                        level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0F, 1.3F);
                    }
                }
            }
        }
        if (!any) {
            spearLines.clear();
            startCooldown("spears", cooldown(spears));
            action = Action.RECOVER;
            timer = 8;
        }
    }

    // ---- 誓いの構え（前から受けるダメージが減る）

    private void startGuard(ServerLevel level) {
        EnemyDef.Skill guard = guardSkill();
        action = Action.GUARD;
        timer = Math.max(1, guard.windupTicks());
        setBusy(timer + 4);
        playAction("skill_guard");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.HOSTILE, 1.6F, 0.6F);
    }

    private void tickGuard(ServerLevel level) {
        if (--timer > 0) {
            return;
        }
        EnemyDef.Skill guard = guardSkill();
        guarding = (int) Math.round(guard.param("seconds", 5.0) * 20);
        setStatus("guard", (guarding + 19) / 20);
        tipOnce("guard");
        level.sendParticles(OATH, getX(), getY() + 1.6, getZ(), 30, 1.0, 1.0, 1.0, 0.0);
        startCooldown("guard", cooldown(guard));
        action = Action.RECOVER;
        timer = 6;
    }

    /** 誓いの構えをしているか。 */
    public boolean guarding() {
        return guarding > 0;
    }

    // ---- 集え（離れた部下を呼び戻す）

    private void startRally(ServerLevel level) {
        EnemyDef.Skill rally = rallySkill();
        action = Action.RALLY;
        timer = Math.max(1, rally.windupTicks());
        setBusy(timer + 6);
        getNavigation().stop();
        playAction("skill_rally");
        line("boss.amemaru.valgrim.line.rally");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.6F, 1.4F);
    }

    private void tickRally(ServerLevel level) {
        if (--timer > 0) {
            return;
        }
        rally(level);
        startCooldown("rally", cooldown(rallySkill()));
        action = Action.RECOVER;
        timer = 6;
    }

    /** 離れた部下を呼び戻す（自動テスト・管理者からも）。呼び戻した数。 */
    public int rally(ServerLevel level) {
        EnemyDef.Skill rally = rallySkill();
        int called = 0;
        int ticks = (int) Math.round(rally.param("seconds", 5.0) * 20);
        for (UUID id : guards) {
            if (level.getEntity(id) instanceof FrozenGuard guard && guard.isAlive() && guard.distanceTo(this) <= rally.range()
                    && guard.distanceTo(this) > oathSkill().range() * 0.6) {
                guard.rallyTo(position(), ticks);
                level.sendParticles(OATH, guard.getX(), guard.getY() + 2.2, guard.getZ(), 6, 0.2, 0.2, 0.2, 0.0);
                called++;
            }
        }
        return called;
    }

    // ---- 崩れ（つながりが消えた。攻めどき）

    private void startCrumble(ServerLevel level) {
        clearTelegraph();
        lineDir = null;
        spearLines.clear();
        guarding = 0;
        action = Action.CRUMBLE;
        EnemyDef.Skill crumble = crumbleSkill();
        timer = (int) Math.round(crumble.param("seconds", 8.0) * 20);
        setBusy(timer + 4);
        getNavigation().stop();
        playAction("skill_crumble");
        unbroken = 0;
        setStatus("open", (timer + 19) / 20);
        line("boss.amemaru.valgrim.line.crumble");
        tipOnce("crumble");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 1.6F, 0.6F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), getX(), getY() + 1.0, getZ(),
                40, 1.0, 0.8, 1.0, 0.06);
    }

    private void tickCrumble(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
        if (timer % 20 == 0) {
            setStatus("open", (timer + 19) / 20);
            level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 2.4, getZ(), 6, 0.6, 0.2, 0.6, 0.01);
        }
        if (--timer > 0) {
            return;
        }
        // 立ち上がる：誓い直す（離れた部下を呼び戻し、少なければ増援）
        regroup = (int) Math.round(crumbleSkill().param("regroup", 6.0) * 20);
        line("boss.amemaru.valgrim.line.rise");
        rally(level);
        int alive = aliveGuards(level);
        if (phase() >= 2 || alive == 0) {
            int want = Math.min(4 - alive, (int) Math.round(reinforceSkill().param("count", 2.0)));
            if (want > 0) {
                summonGuards(level, want, true);
            }
        }
        setStatus("", 0);
        action = Action.RECOVER;
        timer = 16;
    }

    /** 崩れているか（攻めどき）。 */
    public boolean crumbled() {
        return action == Action.CRUMBLE;
    }

    // ---- 近衛の行進（しくじったときの一撃）

    private void startMarch(ServerLevel level, List<ServerPlayer> players) {
        EnemyDef.Skill march = marchSkill();
        action = Action.MARCH;
        ServerPlayer target = topThreat();
        if (target == null || !players.contains(target)) {
            target = players.get(random.nextInt(players.size()));
        }
        faceTowards(target.position());
        lockedYaw = getYRot();
        lineDir = Vec3.directionFromRotation(0.0F, lockedYaw);
        timer = Math.max(20, march.windupTicks());
        setBusy(timer + 30);
        getNavigation().stop();
        playAction("skill_march");
        setTelegraph(TELEGRAPH_LINE, timer, lockedYaw, (float) march.range(), (float) march.param("width", 12.0));
        unbroken = 0;
        line("boss.amemaru.valgrim.line.march");
        tipOnce("march");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.4F, 0.6F);
        // 部下も隊列に並ぶ（近衛長のうしろ）
        int i = 0;
        for (UUID id : guards) {
            if (level.getEntity(id) instanceof FrozenGuard guard && guard.oathLinked()) {
                Vec3 side = new Vec3(-lineDir.z, 0.0, lineDir.x).scale((i % 2 == 0 ? 1 : -1) * (2.5 + i / 2 * 2.5));
                guard.rallyTo(position().add(side).subtract(lineDir.scale(2.0)), timer);
                i++;
            }
        }
    }

    private void tickMarch(ServerLevel level) {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        if (lineDir == null) {
            action = Action.NONE;
            return;
        }
        if (--timer > 0) {
            if (timer % 10 == 0) {
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.HOSTILE, 1.6F, 0.5F);
            }
            return;
        }
        impactFx("march");
        clearTelegraph();
        EnemyDef.Skill march = marchSkill();
        double half = march.param("width", 12.0) / 2.0 + 0.4;
        for (ServerPlayer player : targets(level)) {
            Vec3 rel = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            double along = rel.dot(lineDir);
            double side = rel.subtract(lineDir.scale(along)).length();
            if (along >= -2.0 && along <= march.range() + 0.6 && side <= half && Math.abs(player.getY() - getY()) < 5.0
                    && EnemyCombat.strike(this, player, march.power(), null)) {
                PlayerColdService.chill(player, 2, 120);
                Vec3 push = lineDir.scale(march.param("push", 1.6));
                player.push(push.x, 0.4, push.z);
                player.hurtMarked = true;
            }
        }
        for (double t = 1.0; t <= march.range(); t += 1.5) {
            Vec3 at = position().add(lineDir.scale(t));
            level.sendParticles(FROST, at.x, at.y + 0.8, at.z, 6, half * 0.6, 0.6, 0.6, 0.0);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.6F);
        lineDir = null;
        action = Action.RECOVER;
        timer = 30;
    }

    // ---------------------------------------------------------------- 管理者・自動テスト

    /** いまの部下（生きている数）。 */
    public int guardCount() {
        return level() instanceof ServerLevel level ? aliveGuards(level) : guards.size();
    }

    /** いま誓いでつながっている部下の数。 */
    public int linkedCount() {
        return linked;
    }

    /** 誓いを解いたか（第3段階）。 */
    public boolean released() {
        return released;
    }

    /** 自動テスト：部屋がなくても、誓いの見張りを1回動かす。 */
    public void testTickOath(ServerLevel level) {
        tickOath(level);
    }

    /** 自動テスト：部下として加える。 */
    public void testAddGuard(FrozenGuard guard) {
        guards.add(guard.getUUID());
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
            case "thrust" -> startThrust(level, players.get(0));
            case "bash" -> startBash(level, players.get(0));
            case "spears" -> startSpears(level, players);
            case "guard" -> startGuard(level);
            case "rally" -> startRally(level);
            case "march" -> startMarch(level, players);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        discardGuards(true);
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        discardGuards(false);
        super.remove(reason);
    }
}

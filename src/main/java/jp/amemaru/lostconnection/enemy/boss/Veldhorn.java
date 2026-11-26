package jp.amemaru.lostconnection.enemy.boss;

import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.Tsunomofu;
import jp.amemaru.lostconnection.entity.GroundMarker;
import jp.amemaru.lostconnection.tower.BossRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Blocks;
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
 * <p>どの技も、当たる所を地面に赤く出してから打つ（11月22日のご指示「ボスの通常攻撃も当たり判定を出したい」）。技は、そのとき
 * 出せるものから重みをつけて選び、毎回同じ順番にならないようにする（11月22日のご指示「ボスの攻撃パターンを増やす」）。</p>
 * <ul>
 *   <li>角の薙ぎ払い：敵視のいちばん高い人（盾役）へ。前の扇形（120度・5.5ブロック）【提案】。11月22日から扇形を地面に出す</li>
 *   <li>角の突き上げ：前のまっすぐな帯（幅2.6・長さ7）。当たると打ち上げられる【補完】（11月22日）</li>
 *   <li>後ろ蹴り：しばらく後ろにいる人へ。後ろの扇形（140度・4.5ブロック）【補完】（11月22日）</li>
 *   <li>突進：離れた人へ、地面に通り道の線を出してから、まっすぐ突っ込む。壁にぶつかると、しばらく動けない（攻める好機）【提案】。
 *       11月22日のご指示「突進はもっと早くていい」で、予兆を1.4秒から1.0秒に、速さを1.5倍にした</li>
 *   <li>踏みつけ：まわりに人が集まると、地面に円を出してから、まわりをまとめて打つ【提案】。11月22日から、ひとりでも近くにいれば、ときどき</li>
 *   <li>大跳躍：離れた人の足もとに円を出し、跳んでその円に降りる【補完】（11月22日）</li>
 *   <li>根の牙（HP50%から）：みんなの足もとに円を出し、根の牙が突き上がる。HP20%からは2回続く【補完】（11月22日）</li>
 *   <li>HP50%：吠えて、ツノモフを呼ぶ（2匹＋参加者の数。6匹まで）。突進の予兆が短くなり、続けて2回突っ込むことがある。
 *       HP20%：さらに技の間が短くなり、突進は2〜3回続く【提案】</li>
 * </ul>
 * <p>11月25日のご指示「ボスそれぞれの固有のギミックを追加する」【提案】：<b>大地の咆哮</b>。HP70%と35%で、角を地面に突き立てて
 * 大地の力を吸い上げる（9秒）。その間にみんなで攻撃を集め、最大HPの {@code skills.earth_roar.break}%（2.5%）を削ると、角が折れて
 * 倒れ（{@code stun} 秒）、しばらく受けるダメージが {@code weak} 倍。止められないと、部屋じゅうに大地の衝撃（{@code power}）が走り、
 * 足が重くなる。近くで戦う人が活躍できる「止める」仕掛け。</p>
 * 数値は敵の定義（{@code enemies/veldhorn.json}）の技で変えられる。管理者は {@code /amemaru admin boss skill 1 <技>} で、戦いの間に
 * 技を出させられる（{@link #SKILLS}）。
 */
public class Veldhorn extends AmemaruBoss {
    /** 技の名前（管理者のコマンドで出させる名前）。 */
    public static final List<String> SKILLS = List.of("sweep", "upthrust", "kick", "charge", "stomp", "leap", "roots", "roar", "earth_roar");

    private enum Action { NONE, SWEEP, UPTHRUST, KICK, CHARGE_WINDUP, CHARGING, STUNNED, RECOVER, STOMP, LEAP_WINDUP, LEAPING, ROOTS, ROAR,
        EARTH_ROAR }

    private Action action = Action.NONE;

    /** 技を出している途中か（技が終わったら、ボスは少し息つぎをする。{@link AmemaruBoss#breatheTicks}）。 */
    @Override
    protected boolean acting() {
        return action != Action.NONE;
    }

    /** 角の薙ぎ払いは「attack」のアニメーション。 */
    @Override
    protected String skillOfAction(String action) {
        return "attack".equals(action) ? "sweep" : super.skillOfAction(action);
    }

    private int timer;
    private float chargeYaw;
    private Vec3 chargeDir = Vec3.ZERO;
    private double chargeLength;
    private double traveled;
    private final Set<UUID> hit = new HashSet<>();
    private final List<UUID> summoned = new ArrayList<>();
    private int chargesLeft;
    /** 後ろに人がいる長さ（tick）。後ろ蹴りに使う */
    private int behindTicks;
    /** 大跳躍：跳ぶ所・降りる所・空にいる長さ・跳んでからの tick */
    private Vec3 leapFrom = Vec3.ZERO;
    private Vec3 leapTo = Vec3.ZERO;
    private int leapAir;
    private int leapTicks;
    /** 根の牙：牙が出る所と、残りの回数 */
    private final List<Vec3> rootSpots = new ArrayList<>();
    private int rootWaves;
    /** HP50%で吠えるのを、跳んでいる・突っ込んでいる技が終わるまで待つ */
    private boolean roarPending;
    /** 大地の咆哮（11月25日）：出した回数（HP70%・35%で1回ずつ）・構えの間に受けたダメージ・止めるのに要る量・弱っている残り */
    private int earthRoarsDone;
    private double roarDamage;
    private double roarNeeded;
    private int weakTicks;
    @Nullable
    private jp.amemaru.lostconnection.entity.GroundMarker roarMarker;
    private final Set<String> tips = new HashSet<>();

    public Veldhorn(EntityType<? extends Veldhorn> type, Level level) {
        super(type, level, BossEvent.BossBarColor.GREEN);
    }

    @Override
    public String assetName() {
        return "veldhorn";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_charge", "skill_stomp", "skill_roar", "skill_upthrust", "skill_kick", "skill_leap", "skill_roots"};
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

    private EnemyDef.Skill sweepSkill() {
        return skill("sweep", 1.2, 0.8, 3.5, 5.5);
    }

    private EnemyDef.Skill upthrustSkill() {
        return skill("upthrust", 1.3, 0.6, 5.0, 7.0);
    }

    private EnemyDef.Skill kickSkill() {
        return skill("kick", 1.0, 0.7, 6.0, 4.5);
    }

    private EnemyDef.Skill chargeSkill() {
        return skill("charge", 2.2, 1.0, 9.0, 22.0);
    }

    private EnemyDef.Skill stompSkill() {
        return skill("stomp", 1.4, 1.2, 12.0, 6.0);
    }

    private EnemyDef.Skill leapSkill() {
        return skill("leap", 1.8, 1.1, 11.0, 20.0);
    }

    private EnemyDef.Skill rootsSkill() {
        return skill("roots", 1.0, 1.0, 13.0, 24.0);
    }

    private EnemyDef.Skill earthRoarSkill() {
        return skill("earth_roar", 3.6, 9.0, 60.0, 40.0);
    }

    private int cooldown(EnemyDef.Skill skill) {
        double scale = phase() >= 3 ? 0.7 : phase() == 2 ? 0.85 : 1.0;
        // 11月23日：参加の人数が多いほど、長引いて怒るほど、技の間が短い
        return (int) Math.round(skill.cooldownTicks() * scale * pace());
    }

    private static int ticks(double seconds) {
        return (int) Math.round(seconds * 20.0);
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
            if (action == Action.NONE || action == Action.RECOVER || action == Action.STUNNED) {
                startRoar(level);
            } else {
                roarPending = true;
            }
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
        behindTicks = 0;
        rootSpots.clear();
        rootWaves = 0;
        roarPending = false;
        earthRoarsDone = 0;
        roarDamage = 0.0;
        weakTicks = 0;
        discardRoarMarker();
        setStatus("", 0);
        discardSummoned();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive() || isRemoved() || isReturning()) {
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
            case UPTHRUST -> tickUpthrust(level);
            case KICK -> tickKick(level);
            case CHARGE_WINDUP -> tickChargeWindup(level);
            case CHARGING -> tickCharging(level);
            case STUNNED -> tickStunned(level);
            case RECOVER -> tickRecover();
            case STOMP -> tickStomp(level);
            case LEAP_WINDUP -> tickLeapWindup(level);
            case LEAPING -> tickLeaping(level);
            case ROOTS -> tickRoots(level);
            case ROAR -> tickRoar(level);
            case EARTH_ROAR -> tickEarthRoar(level);
        }
        if (weakTicks > 0 && --weakTicks == 0) {
            setStatus("", 0);
        }
    }

    /** 次の技を選ぶ（何もしていないとき、毎 tick）。 */
    private void choose(ServerLevel level) {
        if (isBusy()) {
            return;
        }
        if (roarPending) {
            roarPending = false;
            startRoar(level);
            return;
        }
        // 大地の咆哮：HP70%と35%で1回ずつ（11月25日）
        EnemyDef.Skill earth = earthRoarSkill();
        double ratio = getEnemyHp() / getEnemyMaxHp();
        if (earthRoarsDone == 0 && ratio <= earth.param("first_at", 0.7)
                || earthRoarsDone == 1 && ratio <= earth.param("second_at", 0.35)) {
            earthRoarsDone++;
            startEarthRoar(level, earth);
            return;
        }
        List<ServerPlayer> players = targets(level);
        if (players.isEmpty()) {
            getNavigation().stop();
            behindTicks = 0;
            return;
        }
        ServerPlayer top = topThreat();
        if (top == null || !players.contains(top)) {
            top = players.get(0);
        }
        // 後ろ蹴り：しばらく後ろにいる人がいる（後ろに回りこみ続けると蹴られる）
        EnemyDef.Skill kick = kickSkill();
        boolean someoneBehind = false;
        for (ServerPlayer player : players) {
            if (player.distanceTo(this) <= kick.range() + 0.5 && behind(player.position(), kick.param("angle", 140.0) / 2.0)) {
                someoneBehind = true;
                break;
            }
        }
        behindTicks = someoneBehind ? behindTicks + 1 : 0;
        if (someoneBehind && behindTicks >= ticks(kick.param("after", 0.8)) && ready("kick")) {
            startKick(kick);
            return;
        }
        // 踏みつけ：まわりに2人以上なら、先に
        EnemyDef.Skill stomp = stompSkill();
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
        // ほかの技：そのとき出せるものから、重みをつけて選ぶ（同じ順番にならないように）
        List<Action> pool = new ArrayList<>();
        double dist = top.distanceTo(this);
        EnemyDef.Skill sweep = sweepSkill();
        EnemyDef.Skill upthrust = upthrustSkill();
        EnemyDef.Skill charge = chargeSkill();
        EnemyDef.Skill leap = leapSkill();
        if (ready("sweep") && dist <= sweep.range()) {
            add(pool, Action.SWEEP, 3);
        }
        if (ready("upthrust") && dist <= upthrust.range() - 0.5) {
            add(pool, Action.UPTHRUST, 2);
        }
        if (ready("stomp") && near >= 1) {
            add(pool, Action.STOMP, 1);
        }
        ServerPlayer far = farthest(players, charge.param("min_range", 6.0), charge.range(), true);
        if (ready("charge") && far != null) {
            add(pool, Action.CHARGE_WINDUP, 3);
        }
        ServerPlayer leapTarget = farthest(players, leap.param("min_range", 6.0), leap.range(), false);
        if (ready("leap") && leapTarget != null) {
            add(pool, Action.LEAP_WINDUP, phase() >= 3 ? 3 : 2);
        }
        if (phase() >= 2 && ready("roots")) {
            add(pool, Action.ROOTS, 2);
        }
        if (!pool.isEmpty()) {
            Action pick = pool.get(random.nextInt(pool.size()));
            switch (pick) {
                case SWEEP -> {
                    faceTowards(top.position());
                    startSweep(sweep);
                }
                case UPTHRUST -> {
                    faceTowards(top.position());
                    startUpthrust(upthrust);
                }
                case STOMP -> startStomp(stomp);
                case CHARGE_WINDUP -> {
                    chargesLeft = chargeCount();
                    startCharge(far, charge);
                }
                case LEAP_WINDUP -> startLeap(level, leapTarget, leap);
                case ROOTS -> startRoots(level, rootsSkill());
                default -> {
                }
            }
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

    private static void add(List<Action> pool, Action action, int weight) {
        for (int i = 0; i < weight; i++) {
            pool.add(action);
        }
    }

    /** 続けて突っ込む回数（HP50%から1/3で2回、HP20%から2〜3回）。 */
    private int chargeCount() {
        if (phase() >= 3) {
            return 2 + random.nextInt(2);
        }
        return phase() == 2 && random.nextInt(3) == 0 ? 2 : 1;
    }

    /** 範囲の中で、いちばん遠い人（{@code sight}：見えている人だけ）。 */
    @Nullable
    private ServerPlayer farthest(List<ServerPlayer> players, double min, double max, boolean sight) {
        ServerPlayer best = null;
        double bestDistance = -1;
        for (ServerPlayer player : players) {
            double d = player.distanceTo(this);
            if (d >= min && d <= max && (!sight || hasLineOfSight(player)) && d > bestDistance) {
                best = player;
                bestDistance = d;
            }
        }
        return best;
    }

    /** その所が、後ろの扇形（半分の角度 {@code halfAngle}）の中か。 */
    private boolean behind(Vec3 point, double halfAngle) {
        return !inFront(point, 180.0 - halfAngle);
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

    private boolean tipped(String key) {
        return !tips.add(key);
    }

    private void tipOnce(String key) {
        if (!tipped(key)) {
            tip("boss.amemaru.veldhorn.tip." + key);
        }
    }

    private void strikeAway(ServerPlayer player, double power, double horizontal, double lift) {
        if (EnemyCombat.strike(this, player, power, null)) {
            Vec3 away = player.position().subtract(position()).multiply(1, 0, 1);
            Vec3 push = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(horizontal);
            player.push(push.x, lift, push.z);
            player.hurtMarked = true;
        }
    }

    // ---- 角の薙ぎ払い（前の扇形）

    private void startSweep(EnemyDef.Skill sweep) {
        action = Action.SWEEP;
        timer = Math.max(1, sweep.windupTicks());
        getNavigation().stop();
        chargeYaw = getYRot();
        setBusy(timer + 10);
        setTelegraph(TELEGRAPH_CONE, timer, getYRot(), (float) sweep.range(), (float) sweep.param("angle", 120.0));
        playAction("attack");
        setCast("sweep", timer);
    }

    private void tickSweep(ServerLevel level) {
        getNavigation().stop();
        // 地面の扇形と同じ向きのまま打つ
        lockRotation();
        if (--timer > 0) {
            return;
        }
        EnemyDef.Skill sweep = sweepSkill();
        double half = sweep.param("angle", 120.0) / 2.0;
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) <= sweep.range() + 0.6 && inFront(player.position(), half)) {
                strikeAway(player, sweep.power(), 0.8, 0.25);
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.4F, 0.8F);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + getLookAngle().x * 2.5, getY() + 1.5, getZ() + getLookAngle().z * 2.5,
                3, 1.0, 0.3, 1.0, 0.0);
        startCooldown("sweep", cooldown(sweep));
        impactFx("sweep");  // 11月27日：当たった瞬間の演出
        clearTelegraph();
        action = Action.NONE;
    }

    // ---- 角の突き上げ（前のまっすぐな帯。打ち上げる）

    private void startUpthrust(EnemyDef.Skill upthrust) {
        action = Action.UPTHRUST;
        timer = Math.max(1, upthrust.windupTicks());
        getNavigation().stop();
        chargeYaw = getYRot();
        setBusy(timer + 12);
        setTelegraph(TELEGRAPH_LINE, timer, chargeYaw, (float) upthrust.range(), (float) upthrust.param("width", 2.6));
        playAction("skill_upthrust");
    }

    private void tickUpthrust(ServerLevel level) {
        getNavigation().stop();
        lockRotation();
        if (--timer > 0) {
            return;
        }
        EnemyDef.Skill upthrust = upthrustSkill();
        Vec3 dir = Vec3.directionFromRotation(0.0F, chargeYaw).normalize();
        double half = upthrust.param("width", 2.6) / 2.0;
        for (ServerPlayer player : targets(level)) {
            Vec3 rel = player.position().subtract(position());
            double along = rel.x * dir.x + rel.z * dir.z;
            double side = Math.abs(rel.x * dir.z - rel.z * dir.x);
            if (along >= -0.5 && along <= upthrust.range() + 0.5 && side <= half + 0.4) {
                if (EnemyCombat.strike(this, player, upthrust.power(), null)) {
                    player.push(dir.x * 0.3, upthrust.param("lift", 0.9), dir.z * 0.3);
                    player.hurtMarked = true;
                }
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.4F, 0.6F);
        for (int i = 1; i <= 6; i++) {
            double d = upthrust.range() * i / 6.0;
            level.sendParticles(ParticleTypes.CRIT, getX() + dir.x * d, getY() + 0.3, getZ() + dir.z * d, 4, 0.4, 0.5, 0.4, 0.15);
        }
        startCooldown("upthrust", cooldown(upthrust));
        impactFx("upthrust");  // 11月27日：当たった瞬間の演出
        clearTelegraph();
        action = Action.RECOVER;
        timer = 6;
    }

    // ---- 後ろ蹴り（後ろの扇形）

    private void startKick(EnemyDef.Skill kick) {
        action = Action.KICK;
        timer = Math.max(1, kick.windupTicks());
        getNavigation().stop();
        chargeYaw = getYRot();
        behindTicks = 0;
        setBusy(timer + 10);
        setTelegraph(TELEGRAPH_CONE, timer, getYRot() + 180.0F, (float) kick.range(), (float) kick.param("angle", 140.0));
        playAction("skill_kick");
        tipOnce("kick");
    }

    private void tickKick(ServerLevel level) {
        getNavigation().stop();
        lockRotation();
        if (--timer > 0) {
            return;
        }
        EnemyDef.Skill kick = kickSkill();
        double half = kick.param("angle", 140.0) / 2.0;
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) <= kick.range() + 0.6 && behind(player.position(), half)) {
                strikeAway(player, kick.power(), kick.param("push", 1.3), 0.4);
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.HORSE_ANGRY, SoundSource.HOSTILE, 1.4F, 0.5F);
        Vec3 back = getLookAngle().multiply(-1, 0, -1).normalize();
        level.sendParticles(ParticleTypes.CLOUD, getX() + back.x * 2.5, getY() + 0.4, getZ() + back.z * 2.5, 12, 1.0, 0.2, 1.0, 0.04);
        startCooldown("kick", cooldown(kick));
        impactFx("kick");  // 11月27日：当たった瞬間の演出
        clearTelegraph();
        action = Action.NONE;
    }

    // ---- 突進（地面に通り道の線）

    private void startCharge(LivingEntity target, EnemyDef.Skill charge) {
        getNavigation().stop();
        faceTowards(target.position());
        chargeYaw = getYRot();
        chargeDir = Vec3.directionFromRotation(0.0F, chargeYaw).normalize();
        chargeLength = Math.min(charge.range(), distanceTo(target) + 4.0);
        double windup = charge.windup() * (phase() >= 2 ? 0.78 : 1.0);
        timer = Math.max(8, ticks(windup));
        traveled = 0.0;
        hit.clear();
        action = Action.CHARGE_WINDUP;
        setBusy(timer + 80);
        setTelegraph(TELEGRAPH_LINE, timer, chargeYaw, (float) chargeLength, (float) charge.param("width", 1.6));
        playAction("skill_charge");
        if (phase() == 1) {
            tipOnce("charge");
        }
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
            impactFx("charge");  // 11月27日：当たった瞬間の演出
            clearTelegraph();
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4F, 1.3F);
        }
    }

    private void tickCharging(ServerLevel level) {
        EnemyDef.Skill charge = chargeSkill();
        double speed = charge.param("speed", 1.5);
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
                timer = ticks(charge.param("stun", 2.5));
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.5F);
                level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 2.5, getZ(), 20, 0.8, 0.4, 0.8, 0.1);
                tipOnce("stun");
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
        action = Action.RECOVER;
        timer = ticks(charge.param("recover", 0.8));
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

    /** 技のあとの、短い立て直し（動かない）。 */
    private void tickRecover() {
        getNavigation().stop();
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        if (--timer <= 0) {
            action = Action.NONE;
            clearBusy();
        }
    }

    // ---- 踏みつけ（まわりの円）

    private void startStomp(EnemyDef.Skill stomp) {
        action = Action.STOMP;
        timer = Math.max(1, stomp.windupTicks());
        getNavigation().stop();
        setBusy(timer + 10);
        setTelegraph(TELEGRAPH_CIRCLE, timer, getYRot(), (float) stomp.range());
        playAction("skill_stomp");
        tipOnce("stomp");
    }

    private void tickStomp(ServerLevel level) {
        getNavigation().stop();
        if (--timer > 0) {
            return;
        }
        EnemyDef.Skill stomp = stompSkill();
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) <= stomp.range() + 0.5) {
                strikeAway(player, stomp.power(), 0.6, 0.6);
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.8F, 0.6F);
        for (int i = 0; i < 24; i++) {
            double a = i / 24.0 * Math.PI * 2.0;
            level.sendParticles(ParticleTypes.POOF, getX() + Math.cos(a) * stomp.range() * 0.8, getY() + 0.1,
                    getZ() + Math.sin(a) * stomp.range() * 0.8, 1, 0.1, 0.0, 0.1, 0.01);
        }
        startCooldown("stomp", cooldown(stomp));
        impactFx("stomp");  // 11月27日：当たった瞬間の演出
        clearTelegraph();
        action = Action.NONE;
    }

    // ---- 大跳躍（降りる所に円を出し、跳んで降りる）

    private void startLeap(ServerLevel level, ServerPlayer target, EnemyDef.Skill leap) {
        action = Action.LEAP_WINDUP;
        timer = Math.max(8, ticks(leap.windup() * (phase() >= 2 ? 0.85 : 1.0)));
        leapAir = Math.max(8, ticks(leap.param("air", 0.8)));
        getNavigation().stop();
        leapFrom = position();
        leapTo = landingSpot(target.position());
        leapTicks = 0;
        faceTowards(leapTo);
        chargeYaw = getYRot();
        setBusy(timer + leapAir + 30);
        GroundMarker.place(level, leapTo, (float) leap.param("radius", 4.0), timer + leapAir);
        playAction("skill_leap");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4F, 0.6F);
        tipOnce("leap");
    }

    /** 降りる所（ねらった人の足もと。部屋の壁の近くなら、部屋の中へ寄せる。高さはボスの足もと＝部屋の床）。 */
    private Vec3 landingSpot(Vec3 target) {
        Vec3 at = new Vec3(target.x, getY(), target.z);
        BossRoom room = room();
        if (room != null) {
            Vec3 center = room.arenaCenter();
            double limit = Math.max(2.0, room.def().arenaRadius() - 4.0);
            Vec3 flat = new Vec3(at.x - center.x, 0.0, at.z - center.z);
            if (flat.length() > limit) {
                flat = flat.normalize().scale(limit);
                at = new Vec3(center.x + flat.x, getY(), center.z + flat.z);
            }
        }
        return at;
    }

    private void tickLeapWindup(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        lockRotation();
        if (timer % 5 == 0) {
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 5, 0.8, 0.05, 0.8, 0.02);
        }
        if (--timer <= 0) {
            action = Action.LEAPING;
            leapFrom = position();
            leapTicks = 0;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.HORSE_JUMP, SoundSource.HOSTILE, 1.6F, 0.5F);
        }
    }

    private void tickLeaping(ServerLevel level) {
        EnemyDef.Skill leap = leapSkill();
        leapTicks++;
        double t = Math.min(1.0, leapTicks / (double) leapAir);
        double height = leap.param("height", 6.0);
        double y = Mth.lerp(t, leapFrom.y, leapTo.y) + 4.0 * height * t * (1.0 - t);
        lockRotation();
        setPos(Mth.lerp(t, leapFrom.x, leapTo.x), y, Mth.lerp(t, leapFrom.z, leapTo.z));
        setDeltaMovement(Vec3.ZERO);
        resetFallDistance();
        if (t < 1.0) {
            return;
        }
        // 降りた：円の中の人を打つ
        double radius = leap.param("radius", 4.0);
        impactFx("leap", TELEGRAPH_CIRCLE, leapTo, 0.0F, (float) radius, 0.0F);  // 11月27日：当たった瞬間の演出
        for (ServerPlayer player : targets(level)) {
            double dx = player.getX() - leapTo.x;
            double dz = player.getZ() - leapTo.z;
            if (dx * dx + dz * dz <= (radius + 0.5) * (radius + 0.5) && Math.abs(player.getY() - leapTo.y) < 3.0) {
                strikeAway(player, leap.power(), 0.9, 0.5);
            }
        }
        level.playSound(null, leapTo.x, leapTo.y, leapTo.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 0.5F);
        level.playSound(null, leapTo.x, leapTo.y, leapTo.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8F, 0.4F);
        level.sendParticles(ParticleTypes.EXPLOSION, leapTo.x, leapTo.y + 0.5, leapTo.z, 3, 1.0, 0.2, 1.0, 0.0);
        for (int i = 0; i < 32; i++) {
            double a = i / 32.0 * Math.PI * 2.0;
            level.sendParticles(ParticleTypes.POOF, leapTo.x + Math.cos(a) * radius, leapTo.y + 0.1, leapTo.z + Math.sin(a) * radius,
                    1, 0.1, 0.0, 0.1, 0.02);
        }
        startCooldown("leap", cooldown(leap));
        action = Action.RECOVER;
        timer = ticks(leap.param("recover", 0.7));
    }

    // ---- 根の牙（みんなの足もとの円。HP50%から。HP20%からは2回）

    private void startRoots(ServerLevel level, EnemyDef.Skill roots) {
        action = Action.ROOTS;
        rootWaves = phase() >= 3 ? (int) Math.round(roots.param("waves_enraged", 2.0)) : 1;
        getNavigation().stop();
        playAction("skill_roots");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.2F, 1.1F);
        placeRoots(level, roots, Math.max(8, roots.windupTicks()));
        tipOnce("roots");
    }

    /** みんなの足もとに円を置く（{@code delay} のあと牙が出る）。 */
    private void placeRoots(ServerLevel level, EnemyDef.Skill roots, int delay) {
        rootSpots.clear();
        timer = delay;
        float radius = (float) roots.param("radius", 2.2);
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) <= roots.range()) {
                Vec3 at = new Vec3(player.getX(), getY(), player.getZ());
                rootSpots.add(at);
                GroundMarker.place(level, at, radius, delay);
            }
        }
        setBusy(delay + 20);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.4F, 0.6F);
    }

    private void tickRoots(ServerLevel level) {
        getNavigation().stop();
        if (--timer > 0) {
            return;
        }
        EnemyDef.Skill roots = rootsSkill();
        double radius = roots.param("radius", 2.2);
        Set<UUID> struck = new HashSet<>();
        BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState());
        BlockParticleOption moss = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSS_BLOCK.defaultBlockState());
        for (Vec3 spot : rootSpots) {
            impactFx("roots", TELEGRAPH_CIRCLE, spot, 0.0F, (float) radius, 0.0F);  // 11月27日：当たった瞬間の演出
            for (ServerPlayer player : targets(level)) {
                double dx = player.getX() - spot.x;
                double dz = player.getZ() - spot.z;
                if (dx * dx + dz * dz <= (radius + 0.4) * (radius + 0.4) && Math.abs(player.getY() - spot.y) < 3.0
                        && struck.add(player.getUUID())) {
                    if (EnemyCombat.strike(this, player, roots.power(), null)) {
                        player.push(0.0, roots.param("lift", 0.8), 0.0);
                        player.hurtMarked = true;
                    }
                }
            }
            level.sendParticles(dirt, spot.x, spot.y + 0.4, spot.z, 30, radius * 0.4, 0.5, radius * 0.4, 0.15);
            level.sendParticles(moss, spot.x, spot.y + 0.8, spot.z, 16, radius * 0.3, 0.6, radius * 0.3, 0.1);
            level.sendParticles(ParticleTypes.CRIT, spot.x, spot.y + 1.0, spot.z, 10, radius * 0.3, 0.6, radius * 0.3, 0.2);
            level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 1.0F, 0.7F);
        }
        if (--rootWaves > 0) {
            // HP20%から：みんなの今の足もとに、もう一度
            placeRoots(level, roots, Math.max(8, ticks(roots.param("wave_gap", 0.8))));
            return;
        }
        rootSpots.clear();
        startCooldown("roots", cooldown(roots));
        action = Action.RECOVER;
        timer = 8;
    }

    // ---- 吠える（HP50%：ツノモフを呼ぶ）

    private void startRoar(ServerLevel level) {
        action = Action.ROAR;
        timer = 40;
        clearTelegraph();
        setTelegraph(TELEGRAPH_ALERT, 40, getYRot(), 0.0F);
        playAction("skill_roar");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.7F);
    }

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

    // ---- 大地の咆哮（11月25日。HP70%・35%）

    private void startEarthRoar(ServerLevel level, EnemyDef.Skill earth) {
        action = Action.EARTH_ROAR;
        timer = Math.max(20, earth.windupTicks());
        roarDamage = 0.0;
        roarNeeded = getEnemyMaxHp() * earth.param("break", 2.5) / 100.0;
        getNavigation().stop();
        setBusy(timer + 10);
        clearTelegraph();
        setTelegraph(TELEGRAPH_ALERT, timer, getYRot(), 0.0F);
        playAction("skill_roar");
        setCast("earth_roar", timer);
        line("boss.amemaru.veldhorn.line.earth_roar");
        tipOnce("earth_roar");
        discardRoarMarker();
        BossRoom room = room();
        Vec3 center = room != null ? room.arenaCenter() : position();
        roarMarker = jp.amemaru.lostconnection.entity.GroundMarker.place(level, new Vec3(center.x, getY(), center.z),
                (float) Math.min(28.0, earth.range()), timer);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.4F, 0.5F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0F, 0.5F);
        updateRoarStatus();
    }

    private void tickEarthRoar(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        Vec3 head = position().add(getLookAngle().multiply(1.0, 0.0, 1.0).scale(1.6)).add(0.0, getBbHeight() * 0.8, 0.0);
        if (timer % 3 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, head.x, head.y, head.z, 6, 0.5, 0.3, 0.5, 0.2);
            level.sendParticles(ParticleTypes.END_ROD, head.x, head.y + 0.4, head.z, 2, 0.3, 0.3, 0.3, 0.02);
        }
        if (timer % 20 == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), getX(), getY() + 0.1, getZ(),
                    30, 4.0, 0.1, 4.0, 0.2);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
        if (timer % 5 == 0) {
            updateRoarStatus();
        }
        if (roarDamage >= roarNeeded) {
            interruptEarthRoar(level);
            return;
        }
        if (--timer > 0) {
            return;
        }
        releaseEarthRoar(level);
    }

    /** 「大地の咆哮まで N 秒 ― 角を打って止めろ（あと M%）」 */
    private void updateRoarStatus() {
        int left = roarNeeded <= 0 ? 0 : (int) Math.ceil(Math.max(0.0, roarNeeded - roarDamage) / roarNeeded * 100.0);
        setStatus("earth_roar:" + left, (timer + 19) / 20);
    }

    /** 止めた：角が折れて倒れ、しばらく弱る。 */
    private void interruptEarthRoar(ServerLevel level) {
        EnemyDef.Skill earth = earthRoarSkill();
        discardRoarMarker();
        clearTelegraph();
        action = Action.STUNNED;
        timer = ticks(earth.param("stun", 5.0));
        weakTicks = ticks(earth.param("weak_seconds", 8.0));
        setBusy(timer + 5);
        setStatus("open_horn", (weakTicks + 19) / 20);
        Vec3 head = position().add(0.0, getBbHeight() * 0.8, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION, head.x, head.y, head.z, 2, 0.5, 0.3, 0.5, 0.0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), head.x, head.y, head.z,
                40, 0.8, 0.5, 0.8, 0.3);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 2.0F, 0.6F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 2.0F, 0.8F);
        line("boss.amemaru.veldhorn.line.horn_broken");
        BossRoom room = room();
        if (room != null) {
            for (ServerPlayer player : room.participants()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("boss.amemaru.veldhorn.horn_broken",
                        Math.round(earth.param("weak", 1.5) * 10.0) / 10.0).withStyle(net.minecraft.ChatFormatting.GOLD,
                        net.minecraft.ChatFormatting.BOLD), true);
            }
        }
    }

    /** 止められなかった：部屋じゅうに大地の衝撃。 */
    private void releaseEarthRoar(ServerLevel level) {
        EnemyDef.Skill earth = earthRoarSkill();
        Vec3 roarCenter = room() != null ? room().arenaCenter() : position();
        impactFx("earth_roar", TELEGRAPH_CIRCLE, new Vec3(roarCenter.x, getY(), roarCenter.z), 0.0F, (float) Math.min(28.0, earth.range()),
                0.0F);  // 11月27日：当たった瞬間の演出
        discardRoarMarker();
        clearTelegraph();
        action = Action.RECOVER;
        timer = 20;
        setStatus("", 0);
        for (ServerPlayer player : targets(level)) {
            strikeAway(player, earth.power(), 1.1, 0.5);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                    60, 1, false, true, true));
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("boss.amemaru.veldhorn.earth_roar_hit")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), getX(), getY() + 0.2, getZ(),
                120, 10.0, 0.2, 10.0, 0.4);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.4F, 0.6F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.4F, 0.4F);
    }

    private void discardRoarMarker() {
        if (roarMarker != null) {
            roarMarker.discard();
            roarMarker = null;
        }
    }

    /** 構えの間に受けたダメージを数える（止めるのに要る量へ）。 */
    @Override
    public void onDamagedBy(@Nullable net.minecraft.world.entity.Entity attacker, double amount) {
        super.onDamagedBy(attacker, amount);
        if (action == Action.EARTH_ROAR && attacker instanceof ServerPlayer) {
            roarDamage += Math.max(0.0, amount);
        }
    }

    /** 角が折れて弱っている間は、受けるダメージが増える。 */
    @Override
    public double damageTakenMultiplier(@Nullable net.minecraft.world.entity.Entity attacker) {
        return weakTicks > 0 ? earthRoarSkill().param("weak", 1.5) : super.damageTakenMultiplier(attacker);
    }

    /** 自動テスト：大地の咆哮の構えの間か・止めるのに要る量・受けた量・弱っている残り。 */
    public boolean earthRoaring() {
        return action == Action.EARTH_ROAR;
    }

    public double earthRoarNeeded() {
        return roarNeeded;
    }

    public int weakTicks() {
        return weakTicks;
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

    // ---------------------------------------------------------------- 管理者

    @Override
    public List<String> skillNames() {
        return SKILLS;
    }

    /** 管理者：技を出させる（戦いの間だけ。間合いや待ち時間は見ない）。出せたら true。 */
    @Override
    public boolean forceSkill(String name) {
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return false;
        }
        BossRoom room = room();
        if (room != null && !room.fighting()) {
            return false;
        }
        List<ServerPlayer> players = targets(level);
        // 大地の咆哮は、狙う人がいなくても構える（部屋の外で試すとき）
        if (!SKILLS.contains(name) || players.isEmpty() && !name.equals("earth_roar")) {
            return false;
        }
        ServerPlayer top = topThreat();
        if (top == null || !players.contains(top)) {
            top = players.isEmpty() ? null : players.get(0);
        }
        clearTelegraph();
        clearBusy();
        action = Action.NONE;
        timer = 0;
        switch (name) {
            case "sweep" -> {
                faceTowards(top.position());
                startSweep(sweepSkill());
            }
            case "upthrust" -> {
                faceTowards(top.position());
                startUpthrust(upthrustSkill());
            }
            case "kick" -> {
                faceTowards(position().add(position().subtract(top.position())));
                startKick(kickSkill());
            }
            case "charge" -> {
                chargesLeft = 1;
                startCharge(top, chargeSkill());
            }
            case "stomp" -> startStomp(stompSkill());
            case "leap" -> startLeap(level, top, leapSkill());
            case "roots" -> startRoots(level, rootsSkill());
            case "earth_roar" -> startEarthRoar(level, earthRoarSkill());
            default -> startRoar(level);
        }
        return true;
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

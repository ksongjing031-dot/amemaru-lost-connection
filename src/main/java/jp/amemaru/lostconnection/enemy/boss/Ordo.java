package jp.amemaru.lostconnection.enemy.boss;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.combat.PlayerColdService;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.entity.GroundMarker;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.TowerS2C;
import jp.amemaru.lostconnection.tower.BossRoom;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.village.VillageService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 6階のボス、村長の残響オルド（11月25日。Phase 0-A 9章の6階：「村を守るつもりで、誰が仲間か分からなくなっている（『おまえは……
 * 村の者か……？』）。戦いながら家々から『記憶の結晶』を3つ集めて渡すと、思い出して戦いをやめる（撃破扱い）」）。数値と技は【補完】。
 *
 * <ul>
 *   <li>記憶の結晶：雪の広場のまわりの3つの家（西の家・東の家・村長の家。{@code boss_marks.crystal_spots}）に、ひとつずつ現れる
 *       （光る品。拾うと運ぶ人は遅くなる。{@link VillageService}）。運ぶ人がオルドのそば（{@code skills.memory.range}）へ来ると、
 *       オルドは結晶を受け取り、思い出しかけてよろめく（{@code stagger} 秒。その間は受けるダメージ {@code broken} 倍）。
 *       次の結晶は、よろめきが終わり、HPが {@code thresholds} を切るか、{@code every} 秒たつと現れる</li>
 *   <li>3つ受け取ると、思い出して戦いをやめ、ひざをつく（撃破扱い）。それまでは、HPが {@code memory.floor} より下がらない
 *       （「まだ……倒れるわけには……」）</li>
 *   <li>技：杖の薙ぎ払い（sweep。前の扇）・氷柱の道（icicle。線。凍える）・吹雪（blizzard。まわりの円。第2段階から）・
 *       疑いの眼差し（gaze。見ている方の扇。家や泉の陰＝目との間にブロックがあれば無事。入ると凍りつく）・
 *       霜狼を呼ぶ（call）。第1段階（HP 100〜70%）・第2段階（70〜35%）・第3段階（35%〜）で間が短くなる</li>
 * </ul>
 */
public class Ordo extends AmemaruBoss {
    public static final List<String> SKILLS = List.of("sweep", "icicle", "blizzard", "gaze", "call", "memory");
    /** 記憶の結晶の台（雪の広場のまわりの家）。 */
    public static final String SPOTS = "crystal_spots";
    public static final int CRYSTALS = 3;
    private static final DustParticleOptions FROST = new DustParticleOptions(new Vector3f(0.65F, 0.88F, 1.0F), 1.5F);
    private static final DustParticleOptions MEMORY = new DustParticleOptions(new Vector3f(1.0F, 0.95F, 0.75F), 1.8F);

    private enum Action { NONE, SWEEP, ICICLE, BLIZZARD, GAZE, CALL, RECEIVE, REMEMBER, RECOVER }

    private Action action = Action.NONE;
    private int timer;
    private float lockedYaw;
    @Nullable
    private Vec3 lineDir;
    /** 渡された記憶の結晶の数。 */
    private int given;
    /** いま広場にある（運ばれている）結晶か。 */
    private boolean crystalOut;
    @Nullable
    private UUID crystalEntity;
    /** 次の結晶を出すまで（tick。よろめきの後から数える）。 */
    private int nextCrystal = -1;
    /** よろめいている残り（tick）。 */
    private int open;
    private boolean allowDeath;
    private final Set<UUID> summoned = new HashSet<>();
    private final Set<String> tips = new HashSet<>();

    public Ordo(EntityType<? extends Ordo> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
    }

    @Override
    public String assetName() {
        return "ordo";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_icicle", "skill_blizzard", "skill_gaze", "skill_call", "skill_receive", "skill_remember"};
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

    private EnemyDef.Skill sweepSkill() {
        return skill("sweep", 1.3, 1.4, 6.0, 7.0);
    }

    private EnemyDef.Skill icicleSkill() {
        return skill("icicle", 1.5, 1.8, 10.0, 26.0);
    }

    private EnemyDef.Skill blizzardSkill() {
        return skill("blizzard", 1.2, 2.2, 18.0, 9.0);
    }

    private EnemyDef.Skill gazeSkill() {
        return skill("gaze", 0.8, 2.8, 24.0, 30.0);
    }

    private EnemyDef.Skill callSkill() {
        return skill("call", 0.0, 1.6, 40.0, 0.0);
    }

    private EnemyDef.Skill memorySkill() {
        return skill("memory", 0.0, 0.0, 0.0, 3.5);
    }

    private int cooldown(EnemyDef.Skill skill) {
        double scale = phase() >= 3 ? 0.75 : phase() == 2 ? 0.88 : 1.0;
        return (int) Math.round(skill.cooldownTicks() * scale * pace());
    }

    // ---------------------------------------------------------------- 戦いの始まり・終わり

    @Override
    public void onFightStarted(ServerLevel level, List<ServerPlayer> participants) {
        given = 0;
        crystalOut = false;
        crystalEntity = null;
        open = 0;
        allowDeath = false;
        nextCrystal = 40;
        startCooldown("gaze", (int) Math.round(gazeSkill().param("first", 14.0) * 20));
        startCooldown("call", (int) Math.round(callSkill().param("first", 30.0) * 20));
        startCooldown("blizzard", (int) Math.round(blizzardSkill().param("first", 20.0) * 20));
        line("boss.amemaru.ordo.line.start");
        tipOnce("start");
        setStatus("memory:0," + CRYSTALS, 0);
    }

    @Override
    protected void onPhaseChanged(ServerLevel level, int from, int to) {
        if (to == 2 && from < 2) {
            line("boss.amemaru.ordo.line.phase2");
        }
        if (to >= 3 && from < 3) {
            line("boss.amemaru.ordo.line.phase3");
        }
    }

    @Override
    public void resetFight() {
        super.resetFight();
        cancelAction();
        action = Action.NONE;
        given = 0;
        open = 0;
        nextCrystal = -1;
        allowDeath = false;
        removeCrystal();
        discardSummoned();
        setStatus("", 0);
    }

    private void cancelAction() {
        clearTelegraph();
        lineDir = null;
    }

    /** よろめいている間は受けるダメージが増える。思い出した後は何も効かない。 */
    @Override
    public double damageTakenMultiplier(@Nullable Entity attacker) {
        if (action == Action.REMEMBER) {
            return 0.0;
        }
        return open > 0 ? memorySkill().param("broken", 1.6) : 1.0;
    }

    /** 記憶の結晶を3つ受け取るまでは、HPが memory.floor より下がらない。 */
    @Override
    public void setHealth(float health) {
        BossRoom room = room();
        double floor = memorySkill().param("floor", 0.1);
        if (!allowDeath && isAlive() && room != null && room.fighting() && given < CRYSTALS && level() instanceof ServerLevel
                && health < getMaxHealth() * floor) {
            super.setHealth((float) (getMaxHealth() * floor));
            if (getEnemyHp() < getEnemyMaxHp() * floor) {
                setEnemyHp(getEnemyMaxHp() * floor);
            }
            if (tips.add("floor")) {
                line("boss.amemaru.ordo.line.floor");
                tip("boss.amemaru.ordo.tip.floor");
            }
            return;
        }
        super.setHealth(health);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive() || isRemoved()) {
            return;
        }
        if (tickCount % 6 == 0) {
            level.sendParticles(FROST, getX(), getY() + getBbHeight() * 0.85, getZ(), 2, 0.6, 0.4, 0.6, 0.0);
        }
        // 3つ目の結晶で思い出したら、部屋の様子によらず最後まで（ひざをつき、礼を言って、消える）
        if (action == Action.REMEMBER) {
            tickRemember(level);
            return;
        }
        BossRoom room = room();
        if (room == null || !room.fighting()) {
            if (action != Action.NONE) {
                action = Action.NONE;
                cancelAction();
            }
            return;
        }
        tickMemory(level);
        if (open > 0) {
            open--;
            if (open % 20 == 0) {
                setStatus("open", open / 20);
            }
            if (open == 0) {
                setStatus("memory:" + given + "," + CRYSTALS, 0);
            }
        }
        switch (action) {
            case NONE -> choose(level);
            case SWEEP -> tickSweep(level);
            case ICICLE -> tickIcicle(level);
            case BLIZZARD -> tickBlizzard(level);
            case GAZE -> tickGaze(level);
            case CALL -> tickCall(level);
            case RECEIVE -> {
                if (--timer <= 0) {
                    action = Action.NONE;
                }
            }
            case REMEMBER -> {
            }
            case RECOVER -> {
                if (--timer <= 0) {
                    action = Action.NONE;
                }
            }
        }
    }

    private void tipOnce(String key) {
        if (tips.add(key)) {
            tip("boss.amemaru.ordo.tip." + key);
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

    private List<BlockPos> spots() {
        BossRoom room = room();
        if (room == null) {
            return List.of();
        }
        FloorDef def = room.floor();
        List<BlockPos> list = new ArrayList<>();
        for (BlockPos p : def.features().bossMarks(SPOTS)) {
            list.add(def.world(p));
        }
        return list;
    }

    // ---------------------------------------------------------------- 記憶の結晶

    private void tickMemory(ServerLevel level) {
        if (action == Action.REMEMBER) {
            return;
        }
        // 結晶を運んできた人：そばへ来たら受け取る
        double range = memorySkill().range();
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) <= range + player.getBbWidth() / 2.0 && VillageService.holds(player, true)) {
                receive(level, player);
                return;
            }
        }
        if (crystalOut) {
            // 結晶の目じるし（光の柱）
            Entity crystal = crystalEntity == null ? null : level.getEntity(crystalEntity);
            if (crystal != null && tickCount % 4 == 0) {
                for (double y = 0.3; y < 6.0; y += 0.6) {
                    level.sendParticles(MEMORY, crystal.getX(), crystal.getY() + y, crystal.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
                }
            }
            if (crystal == null && !anyCarrier(level)) {
                // 拾われずに消えた（落ちた・抜けた人が持って出た）：もう一度、同じ所に
                crystalOut = false;
                nextCrystal = 60;
            }
            return;
        }
        if (given >= CRYSTALS || open > 0) {
            return;
        }
        if (nextCrystal > 0) {
            nextCrystal--;
            double ratio = getEnemyMaxHp() > 0 ? getEnemyHp() / getEnemyMaxHp() : 1.0;
            double threshold = given == 1 ? memorySkill().param("second_at", 0.7) : given == 2 ? memorySkill().param("third_at", 0.4) : 1.0;
            if (ratio <= threshold && nextCrystal > 40) {
                nextCrystal = 40;
            }
            return;
        }
        if (nextCrystal == 0) {
            spawnCrystal(level);
        }
    }

    private boolean anyCarrier(ServerLevel level) {
        for (ServerPlayer player : targets(level)) {
            if (VillageService.holds(player, true)) {
                return true;
            }
        }
        return false;
    }

    /** 記憶の結晶を、次の家の台に出す。 */
    private void spawnCrystal(ServerLevel level) {
        List<BlockPos> spots = spots();
        Vec3 at;
        if (spots.isEmpty()) {
            at = position().add(8.0, 0.5, 0.0);
        } else {
            at = Vec3.atBottomCenterOf(spots.get(Math.min(given, spots.size() - 1))).add(0.0, 0.2, 0.0);
        }
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(VillageService.memoryItem()));
        item.setDeltaMovement(Vec3.ZERO);
        item.setNoGravity(true);
        item.setUnlimitedLifetime();
        item.setGlowingTag(true);
        item.setPickUpDelay(10);
        level.addFreshEntity(item);
        crystalEntity = item.getUUID();
        crystalOut = true;
        nextCrystal = -1;
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.0F, 1.2F);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 24, 0.4, 0.6, 0.4, 0.04);
        String where = Component.translatable("boss.amemaru.ordo.spot." + Math.min(given, 2)).getString();
        for (ServerPlayer player : targets(level)) {
            player.displayClientMessage(Component.translatable("boss.amemaru.ordo.crystal", where).withStyle(ChatFormatting.AQUA), false);
        }
        tipOnce("crystal");
    }

    private void removeCrystal() {
        if (level() instanceof ServerLevel level && crystalEntity != null) {
            Entity e = level.getEntity(crystalEntity);
            if (e != null) {
                e.discard();
            }
            for (ServerPlayer player : level.players()) {
                VillageService.removeCrystals(player, true, 64);
            }
        }
        crystalEntity = null;
        crystalOut = false;
    }

    /** 運ぶ人から記憶の結晶を受け取る：思い出しかけて、よろめく。3つ目で思い出す。 */
    private void receive(ServerLevel level, ServerPlayer player) {
        VillageService.removeCrystals(player, true, 1);
        crystalOut = false;
        crystalEntity = null;
        given++;
        cancelAction();
        discardSummonedSome();
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.0F, 0.8F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.6F, 1.2F);
        level.sendParticles(MEMORY, getX(), getY() + getBbHeight() * 0.6, getZ(), 50, 1.2, 1.2, 1.2, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() * 0.6, getZ(), 30, 1.0, 1.0, 1.0, 0.06);
        for (ServerPlayer p : targets(level)) {
            p.displayClientMessage(Component.translatable("boss.amemaru.ordo.received", player.getDisplayName(), given, CRYSTALS)
                    .withStyle(ChatFormatting.GOLD), false);
        }
        AmemaruMod.LOGGER.info("[amemaru] Ordo received memory crystal {}/{} from {}", given, CRYSTALS, player.getGameProfile().getName());
        if (given >= CRYSTALS) {
            startRemember(level);
            return;
        }
        line("boss.amemaru.ordo.line.memory" + given);
        action = Action.RECEIVE;
        timer = 60;
        setBusy(timer + 10);
        getNavigation().stop();
        playAction("skill_receive");
        open = (int) Math.round(memorySkill().param("stagger", 12.0) * 20);
        setStatus("open", open / 20);
        nextCrystal = open + (int) Math.round(memorySkill().param("every", 100.0) * 20);
        tipOnce("open");
    }

    private void startRemember(ServerLevel level) {
        action = Action.REMEMBER;
        timer = 120;
        open = 0;
        setBusy(timer + 200);
        getNavigation().stop();
        playAction("skill_remember");
        line("boss.amemaru.ordo.line.remember");
        setStatus("", 0);
        discardSummoned();
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 2.0F, 0.7F);
    }

    private void tickRemember(ServerLevel level) {
        if (timer % 10 == 0) {
            level.sendParticles(MEMORY, getX(), getY() + 1.0, getZ(), 16, 1.6, 0.8, 1.6, 0.0);
            level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 2.0, getZ(), 20, 2.0, 1.0, 2.0, 0.02);
        }
        if (timer == 60) {
            line("boss.amemaru.ordo.line.thanks");
        }
        if (--timer > 0) {
            return;
        }
        allowDeath = true;
        setEnemyHp(0.0);
        super.setHealth(0.0F);
        die(damageSources().genericKill());
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
        ServerPlayer top = topThreat();
        if (top == null || !players.contains(top)) {
            top = players.get(random.nextInt(players.size()));
        }
        if (ready("gaze") && open <= 0) {
            startGaze(level, players.get(random.nextInt(players.size())));
            return;
        }
        if (ready("call") && open <= 0) {
            startCall(level);
            return;
        }
        List<Action> options = new ArrayList<>();
        if (ready("sweep") && top.distanceTo(this) <= sweepSkill().range() + 1.5) {
            options.add(Action.SWEEP);
            options.add(Action.SWEEP);
        }
        if (ready("icicle")) {
            options.add(Action.ICICLE);
        }
        if (phase() >= 2 && ready("blizzard")) {
            options.add(Action.BLIZZARD);
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
            case ICICLE -> startIcicle(level, players.get(random.nextInt(players.size())));
            case BLIZZARD -> startBlizzard(level);
            default -> {
            }
        }
    }

    // ---- 杖の薙ぎ払い（前の扇）

    private void startSweep(ServerLevel level, ServerPlayer target) {
        EnemyDef.Skill sweep = sweepSkill();
        action = Action.SWEEP;
        faceTowards(target.position());
        lockedYaw = getYRot();
        timer = Math.max(1, sweep.windupTicks());
        setBusy(timer + 10);
        playAction("attack", animSpeed(sweep));
        setCast("sweep", timer);
        setTelegraph(TELEGRAPH_CONE, timer, lockedYaw, (float) sweep.range(), (float) sweep.param("angle", 120.0));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 0.6F);
    }

    private float animSpeed(EnemyDef.Skill skill) {
        double anim = skill.param("anim_windup", skill.windup());
        return (float) Math.max(0.4, Math.min(1.0, anim / Math.max(0.1, skill.windup())));
    }

    private void tickSweep(ServerLevel level) {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        if (--timer > 0) {
            return;
        }
        clearTelegraph();
        EnemyDef.Skill sweep = sweepSkill();
        double half = sweep.param("angle", 120.0) / 2.0;
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) - player.getBbWidth() / 2.0 <= sweep.range() + 0.6 && inFront(player.position(), half)
                    && EnemyCombat.strike(this, player, sweep.power(), null)) {
                Vec3 away = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
                if (away.lengthSqr() > 1.0E-4) {
                    away = away.normalize().scale(sweep.param("push", 1.0));
                    player.push(away.x, 0.25, away.z);
                    player.hurtMarked = true;
                }
            }
        }
        double yawRad = Math.toRadians(lockedYaw);
        for (int i = 0; i <= 12; i++) {
            double a = yawRad + Math.toRadians(-half + half * 2.0 * i / 12.0);
            for (double r = 2.0; r <= sweep.range(); r += 2.0) {
                level.sendParticles(ParticleTypes.SNOWFLAKE, getX() - Math.sin(a) * r, getY() + 1.0, getZ() + Math.cos(a) * r, 2,
                        0.1, 0.1, 0.1, 0.02);
            }
        }
        startCooldown("sweep", cooldown(sweep));
        action = Action.RECOVER;
        timer = 10;
    }

    // ---- 氷柱の道（線。当たると凍える）

    private void startIcicle(ServerLevel level, ServerPlayer target) {
        EnemyDef.Skill icicle = icicleSkill();
        action = Action.ICICLE;
        faceTowards(target.position());
        lockedYaw = getYRot();
        lineDir = Vec3.directionFromRotation(0.0F, lockedYaw);
        timer = Math.max(1, icicle.windupTicks());
        setBusy(timer + 12);
        playAction("skill_icicle", animSpeed(icicle));
        setCast("icicle", timer);
        setTelegraph(TELEGRAPH_LINE, timer, lockedYaw, (float) icicle.range(), (float) icicle.param("width", 2.6));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.6F, 0.5F);
        tipOnce("icicle");
    }

    private void tickIcicle(ServerLevel level) {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        if (--timer > 0 || lineDir == null) {
            return;
        }
        clearTelegraph();
        EnemyDef.Skill icicle = icicleSkill();
        double half = icicle.param("width", 2.6) / 2.0 + 0.4;
        int chill = (int) Math.round(icicle.param("chill", 2.0));
        for (ServerPlayer player : targets(level)) {
            Vec3 rel = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            double along = rel.dot(lineDir);
            double side = rel.subtract(lineDir.scale(along)).length();
            if (along >= 0.0 && along <= icicle.range() + 0.6 && side <= half && Math.abs(player.getY() - getY()) < 4.0
                    && EnemyCombat.strike(this, player, icicle.power(), null)) {
                PlayerColdService.chill(player, chill, 100);
                player.push(0.0, 0.4, 0.0);
                player.hurtMarked = true;
            }
        }
        for (double t = 1.5; t <= icicle.range(); t += 1.2) {
            Vec3 at = position().add(lineDir.scale(t));
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL, at.x, at.y + 0.6, at.z, 4, 0.3, 0.6, 0.3, 0.05);
            level.sendParticles(FROST, at.x, at.y + 0.3, at.z, 3, 0.4, 0.4, 0.4, 0.0);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0F, 0.8F);
        lineDir = null;
        startCooldown("icicle", cooldown(icicle));
        action = Action.RECOVER;
        timer = 12;
    }

    // ---- 吹雪（まわりの円。第2段階から）

    private void startBlizzard(ServerLevel level) {
        EnemyDef.Skill blizzard = blizzardSkill();
        action = Action.BLIZZARD;
        timer = Math.max(10, blizzard.windupTicks());
        setBusy(timer + 14);
        playAction("skill_blizzard", animSpeed(blizzard));
        setCast("blizzard", timer);
        GroundMarker.place(level, position(), (float) blizzard.range(), timer, GroundMarker.KIND_DANGER);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SNOW_GOLEM_SHOOT, SoundSource.HOSTILE, 2.0F, 0.4F);
        tipOnce("blizzard");
    }

    private void tickBlizzard(ServerLevel level) {
        if (timer % 4 == 0) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 2.0, getZ(), 30, 3.0, 1.0, 3.0, 0.1);
        }
        if (--timer > 0) {
            return;
        }
        EnemyDef.Skill blizzard = blizzardSkill();
        double radius = blizzard.range() + 0.3;
        for (ServerPlayer player : targets(level)) {
            double dx = player.getX() - getX();
            double dz = player.getZ() - getZ();
            if (dx * dx + dz * dz <= radius * radius && Math.abs(player.getY() - getY()) < 4.0
                    && EnemyCombat.strike(this, player, blizzard.power(), null)) {
                PlayerColdService.chill(player, (int) Math.round(blizzard.param("chill", 2.0)), 120);
                CompoundTag tag = new CompoundTag();
                tag.putFloat("strength", 0.9F);
                tag.putInt("ticks", 60);
                ModNetwork.sendTo(player, new TowerS2C("blizzard", tag));
            }
        }
        level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.6, getZ(), 40, radius * 0.5, 0.3, radius * 0.5, 0.08);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SNOW_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
        startCooldown("blizzard", cooldown(blizzard));
        action = Action.RECOVER;
        timer = 14;
    }

    // ---- 疑いの眼差し（見ている方の扇。ブロックの陰に隠れれば無事）

    private void startGaze(ServerLevel level, ServerPlayer target) {
        EnemyDef.Skill gaze = gazeSkill();
        action = Action.GAZE;
        faceTowards(target.position());
        lockedYaw = getYRot();
        timer = Math.max(10, gaze.windupTicks());
        setBusy(timer + 12);
        playAction("skill_gaze", animSpeed(gaze));
        setCast("gaze", timer);
        setTelegraph(TELEGRAPH_CONE, timer, lockedYaw, (float) gaze.range(), (float) gaze.param("angle", 70.0));
        setStatus("gaze", timer / 20);
        line("boss.amemaru.ordo.line.gaze");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0F, 1.4F);
        tipOnce("gaze");
    }

    private void tickGaze(ServerLevel level) {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        setYHeadRot(lockedYaw);
        if (timer % 20 == 0) {
            setStatus("gaze", timer / 20);
        }
        if (--timer > 0) {
            if (timer % 3 == 0) {
                Vec3 eye = getEyePosition();
                level.sendParticles(FROST, eye.x, eye.y, eye.z, 3, 0.2, 0.1, 0.2, 0.0);
            }
            return;
        }
        clearTelegraph();
        EnemyDef.Skill gaze = gazeSkill();
        double half = gaze.param("angle", 70.0) / 2.0;
        int freeze = (int) Math.round(gaze.param("freeze_seconds", 2.0) * 20);
        Vec3 eye = getEyePosition();
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) > gaze.range() + 0.6 || !inFront(player.position(), half)) {
                continue;
            }
            if (hidden(level, eye, player)) {
                player.displayClientMessage(Component.translatable("boss.amemaru.ordo.hidden").withStyle(ChatFormatting.GREEN), true);
                continue;
            }
            if (EnemyCombat.strike(this, player, gaze.power(), null)) {
                player.addEffect(new MobEffectInstance(ModEffects.FROZEN.get(), freeze, 0, false, true, true));
                PlayerColdService.chill(player, 1, 80);
                level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
                player.displayClientMessage(Component.translatable("boss.amemaru.ordo.frozen").withStyle(ChatFormatting.RED), true);
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.4F, 1.6F);
        setStatus(open > 0 ? "open" : "memory:" + given + "," + CRYSTALS, open / 20);
        startCooldown("gaze", cooldown(gaze));
        action = Action.RECOVER;
        timer = 12;
    }

    /** 目と、その人の目・腰の間に、ブロックがあるか（家・泉・灯の柱の陰）。 */
    public static boolean hidden(ServerLevel level, Vec3 eye, ServerPlayer player) {
        Vec3[] points = {player.getEyePosition(), player.position().add(0.0, 0.9, 0.0)};
        for (Vec3 point : points) {
            BlockHitResult hit = level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
            if (hit.getType() == HitResult.Type.MISS) {
                return false;
            }
        }
        return true;
    }

    // ---- 霜狼を呼ぶ

    private void startCall(ServerLevel level) {
        action = Action.CALL;
        timer = Math.max(10, callSkill().windupTicks());
        setBusy(timer + 10);
        playAction("skill_call");
        setCast("call", timer);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOLF_HOWL, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    private void tickCall(ServerLevel level) {
        if (--timer > 0) {
            return;
        }
        EnemyDef.Skill call = callSkill();
        summoned.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        int count = (int) Math.round(call.param(phase() >= 3 ? "count_late" : "count", phase() >= 3 ? 3.0 : 2.0));
        int max = (int) Math.round(call.param("max", 4.0));
        List<ServerPlayer> players = targets(level);
        for (int i = 0; i < count && summoned.size() < max; i++) {
            AmemaruMob wolf = ModEntities.FROST_WOLF.get().create(level);
            if (wolf == null) {
                continue;
            }
            double a = Math.PI * 2.0 * i / Math.max(1, count) + random.nextDouble();
            wolf.moveTo(getX() + Math.cos(a) * 5.0, getY() + 0.2, getZ() + Math.sin(a) * 5.0, random.nextFloat() * 360.0F, 0.0F);
            wolf.setHome(BlockPos.containing(position()));
            wolf.setTransient(true);
            level.addFreshEntity(wolf);
            summoned.add(wolf.getUUID());
            if (!players.isEmpty()) {
                ServerPlayer carrier = null;
                for (ServerPlayer p : players) {
                    if (VillageService.holds(p, true)) {
                        carrier = p;
                    }
                }
                wolf.addThreat(carrier != null ? carrier : players.get(random.nextInt(players.size())), 2.0);
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 16, 0.4, 0.4, 0.4, 0.03);
        }
        tipOnce("call");
        startCooldown("call", cooldown(call));
        action = Action.RECOVER;
        timer = 10;
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

    /** 結晶を受け取ったとき：呼んだ狼のうち、半分は霧になって消える（思い出しかけて、呼び声がゆるむ）。 */
    private void discardSummonedSome() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        int k = 0;
        for (UUID id : new ArrayList<>(summoned)) {
            if (k++ % 2 == 0) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 0.5, entity.getZ(), 10, 0.4, 0.4, 0.4, 0.02);
                    entity.discard();
                }
                summoned.remove(id);
            }
        }
    }

    // ---------------------------------------------------------------- 管理者・自動テスト

    /** 渡された記憶の結晶の数。 */
    public int given() {
        return given;
    }

    /** いま結晶が出ている（運ばれている）か。 */
    public boolean crystalOut() {
        return crystalOut;
    }

    /** 自動テスト・管理者：すぐに次の結晶を出す。 */
    public void testSpawnCrystal() {
        if (level() instanceof ServerLevel level && !crystalOut && given < CRYSTALS) {
            spawnCrystal(level);
        }
    }

    /** 自動テスト・管理者：その人から結晶を受け取ったことにする。 */
    public void testReceive(ServerPlayer player) {
        if (level() instanceof ServerLevel level) {
            if (!VillageService.holds(player, true)) {
                player.getInventory().add(new ItemStack(VillageService.memoryItem()));
            }
            receive(level, player);
        }
    }

    /** 思い出している（ひざをついている）か。 */
    public boolean remembering() {
        return action == Action.REMEMBER;
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
        if (players.isEmpty() && !"call".equals(name) && !"blizzard".equals(name) && !"memory".equals(name)) {
            return false;
        }
        cancelAction();
        action = Action.NONE;
        clearBusy();
        switch (name) {
            case "sweep" -> startSweep(level, players.get(0));
            case "icicle" -> startIcicle(level, players.get(0));
            case "blizzard" -> startBlizzard(level);
            case "gaze" -> startGaze(level, players.get(0));
            case "call" -> startCall(level);
            case "memory" -> {
                removeCrystal();
                spawnCrystal(level);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onVictory(ServerLevel level, List<ServerPlayer> winners, boolean first) {
        for (ServerPlayer player : winners) {
            jp.amemaru.lostconnection.player.PlayerDataCapability.get(player)
                    .ifPresent(data -> data.getStory().add(AmemaruMod.id("story/ordo_remembered")));
        }
        removeCrystal();
    }
}

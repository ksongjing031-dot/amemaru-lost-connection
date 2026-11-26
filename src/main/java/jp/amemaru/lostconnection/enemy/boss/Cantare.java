package jp.amemaru.lostconnection.enemy.boss;

import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.temple.PrayerEcho;
import jp.amemaru.lostconnection.entity.GroundMarker;
import jp.amemaru.lostconnection.tower.BossRoom;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.temple.TempleService;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 4階のボス、祈りの残響カンターレ（Phase 0-A 9章「4体の歌い手で、歌の順番どおりに4つの音の床を踏むと無防備になる」）。
 * 顔のない白い衣の指揮者のまわりを、4人の歌い手（白・青・金・紅）が回る、宙に浮く祈りの残響。
 *
 * <ul>
 *   <li>歌の守り：ふだんは歌い手の歌に守られ、受けるダメージが {@code skills.song.guarded}（1/3）</li>
 *   <li>選律の歌：歌い手が1人ずつ歌い、部屋の4つの音の床（同じ色）が順に光る。歌い終えたら、{@code skills.song.answer} 秒の
 *       うちに、同じ順番で床を踏む（誰が踏んでもよい。手分けすると速い）。そろうと歌い手が黙り、{@code skills.song.exposed} 秒
 *       無防備になる（よろめいて止まり、受けるダメージ {@code skills.song.open}）</li>
 *   <li>違う床を踏む・時間切れ：不協和音（部屋の全員にダメージ）。少しあとで、また歌う</li>
 *   <li>技：嘆きの波（前の扇）・高音の柱（何人かの足もとの円）・祈りの合唱（みんなで受ける一撃。{@link AmemaruBoss} の share）</li>
 *   <li>HP60%：祈り手の残響を2体呼ぶ（歌で治されるので、先に倒す）。HP30%：歌が5音になり、技が速くなる【補完】</li>
 * </ul>
 * 音の床の場所は、階の定義の {@code boss_marks.sound_plates}（4つ。並びの順が音の番号 0〜3）。
 *
 * <p>11月25日のご指示「ボスそれぞれの固有のギミックを追加する」【提案】：<b>残響の色</b>。ときどき、ひとりひとりの頭の上に
 * 歌い手の色（白・青・金・紅）が灯る。{@code skills.echo.windup} 秒のうちに、自分の色の音の床のそば（{@code safe} ブロック）へ
 * 行く。行けなかった人に、残響の和音（{@code power}）。選律の歌（順番）とちがい、色と場所の仕掛け。</p>
 */
public class Cantare extends AmemaruBoss {
    public static final List<String> SKILLS = List.of("wave", "pillars", "song", "call", "echo");
    public static final String PLATES = "sound_plates";
    /** 音の名前（言語ファイルのキーの後ろ）。0 白・1 青・2 金・3 紅。 */
    public static final String[] NOTE_KEYS = {"white", "blue", "gold", "red"};

    private static final EntityDataAccessor<Boolean> DATA_OPEN = SynchedEntityData.defineId(Cantare.class, EntityDataSerializers.BOOLEAN);

    private enum Action { NONE, WAVE, PILLARS, SONG, ANSWER, CALL, OPEN, RECOVER, ECHO }

    /** 残響の色の、頭の上の粒の色（白・青・金・紅）。 */
    private static final net.minecraft.core.particles.DustParticleOptions[] ECHO_DUST = {
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0F, 1.0F, 1.0F), 1.6F),
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.42F, 0.72F, 1.0F), 1.6F),
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.95F, 0.79F, 0.3F), 1.6F),
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0F, 0.42F, 0.35F), 1.6F)};
    private static final ChatFormatting[] ECHO_TEXT = {ChatFormatting.WHITE, ChatFormatting.AQUA, ChatFormatting.GOLD, ChatFormatting.RED};

    private Action action = Action.NONE;

    /** 技を出している途中か（技が終わったら、ボスは少し息つぎをする。{@link AmemaruBoss#breatheTicks}）。 */
    @Override
    protected boolean acting() {
        return action != Action.NONE;
    }

    private int timer;
    private float lockedYaw;
    private int[] notes = new int[0];
    private int answered;
    private int songCooldown = -1;
    /** 残響の色（11月25日）：ひとりひとりの色と、次に出すまで */
    private final Map<UUID, Integer> echoColors = new java.util.HashMap<>();
    private int echoCooldown = -1;
    private final List<Vec3> pillarSpots = new ArrayList<>();
    private final List<UUID> summoned = new ArrayList<>();
    private final Set<String> tips = new HashSet<>();
    /** 無防備の残り（tick）。 */
    private int exposed;

    public Cantare(EntityType<? extends Cantare> type, Level level) {
        super(type, level, BossEvent.BossBarColor.YELLOW);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_OPEN, false);
    }

    @Override
    public String assetName() {
        return "cantare";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_wave", "skill_pillars", "skill_song", "skill_call", "skill_open"};
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

    /** 無防備か（クライアントでも読める：歌い手が黙り、指揮者がうなだれる）。 */
    public boolean open() {
        return entityData.get(DATA_OPEN);
    }

    private void setOpen(boolean value) {
        if (open() != value) {
            entityData.set(DATA_OPEN, value);
        }
    }

    @Override
    public Map<String, Boolean> boneToggles() {
        return Map.of("halo", !open());
    }

    private EnemyDef.Skill skill(String name, double power, double windup, double cooldown, double range) {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(power, windup, cooldown, range, Map.of()) : def.skill(name, power, windup, cooldown, range);
    }

    private EnemyDef.Skill songSkill() {
        return skill("song", 1.2, 0.7, 30.0, 0.0);
    }

    private int cooldown(EnemyDef.Skill skill) {
        double scale = phase() >= 3 ? 0.72 : phase() == 2 ? 0.86 : 1.0;
        return (int) Math.round(skill.cooldownTicks() * scale * pace());
    }

    /** 歌の守り（1/3）と、無防備（2.5倍）。 */
    @Override
    public double damageTakenMultiplier(@Nullable Entity attacker) {
        EnemyDef.Skill song = songSkill();
        return open() ? song.param("open", 2.5) : song.param("guarded", 0.34);
    }

    // ---------------------------------------------------------------- 戦い

    @Override
    public void onFightStarted(ServerLevel level, List<ServerPlayer> participants) {
        setOpen(false);
        exposed = 0;
        songCooldown = (int) Math.round(songSkill().param("first", 8.0) * 20);
        echoCooldown = (int) Math.round(echoSkill().param("first", 25.0) * 20);
        line("boss.amemaru.cantare.line.start");
        tipOnce("song");
    }

    @Override
    protected void onPhaseChanged(ServerLevel level, int from, int to) {
        if (to >= 2 && from < 2) {
            cancelAction(level);
            action = Action.CALL;
            timer = 30;
            playAction("skill_call");
            setTelegraph(TELEGRAPH_ALERT, timer, getYRot(), 0.0F, 0.0F);
            line("boss.amemaru.cantare.line.call");
        }
        if (to >= 3 && from < 3) {
            line("boss.amemaru.cantare.line.rage");
            tipOnce("rage");
        }
    }

    @Override
    public void resetFight() {
        super.resetFight();
        if (level() instanceof ServerLevel level) {
            cancelAction(level);
        }
        action = Action.NONE;
        exposed = 0;
        songCooldown = -1;
        echoCooldown = -1;
        echoColors.clear();
        setOpen(false);
        setStatus("", 0);
        discardSummoned();
    }

    private void cancelAction(ServerLevel level) {
        clearTelegraph();
        pillarSpots.clear();
        notes = new int[0];
        answered = 0;
        lightAll(level, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive() || isRemoved()) {
            return;
        }
        setDeltaMovement(Vec3.ZERO);
        if (tickCount % 6 == 0) {
            // 歌い手の衣の裾：金の粉が舞う
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.4, getZ(), 2, 1.4, 0.2, 1.4, 0.01);
        }
        BossRoom room = room();
        if (room != null && !room.fighting()) {
            if (action != Action.NONE) {
                action = Action.NONE;
                cancelAction(level);
            }
            setStatus("", 0);
            return;
        }
        if (exposed > 0) {
            exposed--;
            if (tickCount % 10 == 0) {
                setStatus("open", (exposed + 19) / 20);
            }
            if (exposed == 0) {
                setOpen(false);
                line("boss.amemaru.cantare.line.recover");
                songCooldown = cooldown(songSkill());
            }
        } else if (action != Action.SONG && action != Action.ANSWER && action != Action.ECHO && tickCount % 10 == 0) {
            setStatus("guarded", Math.max(0, songCooldown) / 20);
        }
        if (songCooldown > 0 && exposed <= 0 && action != Action.SONG && action != Action.ANSWER) {
            songCooldown--;
        }
        if (echoCooldown > 0 && exposed <= 0 && action == Action.NONE) {
            echoCooldown--;
        }
        switch (action) {
            case NONE -> choose(level);
            case WAVE -> tickWave(level);
            case PILLARS -> tickPillars(level);
            case SONG -> tickSong(level);
            case ANSWER -> tickAnswer(level);
            case CALL -> tickCall(level);
            case ECHO -> tickEcho(level);
            case OPEN -> {
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

    private void tipOnce(String key) {
        if (tips.add(key)) {
            tip("boss.amemaru.cantare.tip." + key);
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

    private EnemyDef.Skill waveSkill() {
        return skill("wave", 1.3, 1.2, 5.0, 12.0);
    }

    private EnemyDef.Skill pillarsSkill() {
        return skill("pillars", 1.2, 1.8, 9.0, 30.0);
    }

    private void choose(ServerLevel level) {
        if (isBusy() || exposed > 0) {
            return;
        }
        List<ServerPlayer> players = targets(level);
        if (players.isEmpty()) {
            return;
        }
        if (songCooldown == 0 && plates().size() == 4) {
            startSong(level);
            return;
        }
        if (echoCooldown == 0 && plates().size() == 4) {
            startEcho(level);
            return;
        }
        ServerPlayer target = topThreat();
        if (target == null || !players.contains(target)) {
            target = players.get(random.nextInt(players.size()));
        }
        faceTowards(target.position());
        List<Action> options = new ArrayList<>();
        if (ready("wave") && target.distanceTo(this) <= waveSkill().range() + 2.0) {
            options.add(Action.WAVE);
            options.add(Action.WAVE);
        }
        if (ready("pillars")) {
            options.add(Action.PILLARS);
        }
        if (options.isEmpty()) {
            return;
        }
        switch (options.get(random.nextInt(options.size()))) {
            case WAVE -> startWave(level, target);
            case PILLARS -> startPillars(level);
            default -> {
            }
        }
    }

    // ---- 残響の色（11月25日）

    private EnemyDef.Skill echoSkill() {
        return skill("echo", 1.5, 7.0, 45.0, 30.0);
    }

    private void startEcho(ServerLevel level) {
        EnemyDef.Skill echo = echoSkill();
        action = Action.ECHO;
        timer = Math.max(60, echo.windupTicks());
        setBusy(timer + 10);
        playAction("skill_song");
        setCast("echo", timer);
        line("boss.amemaru.cantare.line.echo");
        tipOnce("echo");
        echoColors.clear();
        // 色は、なるべく人ごとに分ける（4人より多ければ、また白から）
        List<Integer> deck = new ArrayList<>(List.of(0, 1, 2, 3));
        java.util.Collections.shuffle(deck, new java.util.Random(random.nextLong()));
        int k = 0;
        for (ServerPlayer player : targets(level)) {
            int color = deck.get(k++ % deck.size());
            echoColors.put(player.getUUID(), color);
            player.displayClientMessage(Component.translatable("boss.amemaru.cantare.echo_you",
                    Component.translatable("status.amemaru.note." + NOTE_KEYS[color]).withStyle(ECHO_TEXT[color], ChatFormatting.BOLD),
                    (timer + 19) / 20).withStyle(ECHO_TEXT[color]), true);
        }
        List<BlockPos> plates = plates();
        float safe = (float) echo.param("safe", 3.0);
        for (int i = 0; i < plates.size(); i++) {
            BlockPos plate = plates.get(i);
            GroundMarker.place(level, Vec3.atBottomCenterOf(plate), safe, timer, GroundMarker.KIND_NOTE + i);
            TempleService.setLit(level, plate, true);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0F, 0.8F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.0F, 0.5F);
        setStatus("echo", (timer + 19) / 20);
    }

    private void tickEcho(ServerLevel level) {
        if (timer % 5 == 0) {
            for (ServerPlayer player : targets(level)) {
                Integer color = echoColors.get(player.getUUID());
                if (color == null) {
                    continue;
                }
                level.sendParticles(ECHO_DUST[color], player.getX(), player.getY() + player.getBbHeight() + 0.6, player.getZ(), 6,
                        0.25, 0.15, 0.25, 0.0);
                if (timer % 10 == 0) {
                    player.displayClientMessage(Component.translatable("boss.amemaru.cantare.echo_you",
                            Component.translatable("status.amemaru.note." + NOTE_KEYS[color]).withStyle(ECHO_TEXT[color], ChatFormatting.BOLD),
                            (timer + 19) / 20).withStyle(ECHO_TEXT[color]), true);
                }
            }
            setStatus("echo", (timer + 19) / 20);
        }
        if (--timer > 0) {
            return;
        }
        EnemyDef.Skill echo = echoSkill();
        List<BlockPos> plates = plates();
        double safe = echo.param("safe", 3.0) + 0.5;
        impactFx("echo", TELEGRAPH_CIRCLE, position(), 0.0F, 12.0F, 0.0F);  // 11月27日：当たった瞬間の演出
        for (ServerPlayer player : targets(level)) {
            Integer color = echoColors.get(player.getUUID());
            if (color == null || color >= plates.size()) {
                continue;
            }
            if (echoSafe(player, plates.get(color), safe)) {
                level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.5, 0.3, 0.05);
                player.displayClientMessage(Component.translatable("boss.amemaru.cantare.echo_safe").withStyle(ChatFormatting.AQUA), true);
            } else if (EnemyCombat.strike(this, player, echo.power(), null)) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                        50, 1, false, true, true));
                level.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                player.displayClientMessage(Component.translatable("boss.amemaru.cantare.echo_hit").withStyle(ChatFormatting.RED), true);
            }
        }
        lightAll(level, false);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.4F, 1.2F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 2.0F, 0.6F);
        echoColors.clear();
        setStatus("", 0);
        echoCooldown = cooldown(echo);
        action = Action.RECOVER;
        timer = 16;
    }

    /** その人が、自分の色の床のそば（横の距離が {@code safe} まで、高さ3まで）にいるか。 */
    public static boolean echoSafe(ServerPlayer player, BlockPos plate, double safe) {
        double dx = player.getX() - (plate.getX() + 0.5);
        double dz = player.getZ() - (plate.getZ() + 0.5);
        return dx * dx + dz * dz <= safe * safe && Math.abs(player.getY() - plate.getY()) <= 3.0;
    }

    /** 自動テスト：その人の残響の色（なければ -1）。 */
    public int echoColor(ServerPlayer player) {
        Integer color = echoColors.get(player.getUUID());
        return color == null ? -1 : color;
    }

    // ---- 嘆きの波（前の扇）

    private void startWave(ServerLevel level, ServerPlayer target) {
        EnemyDef.Skill wave = waveSkill();
        action = Action.WAVE;
        faceTowards(target.position());
        lockedYaw = getYRot();
        timer = Math.max(1, wave.windupTicks());
        setBusy(timer + 10);
        playAction("skill_wave");
        setCast("wave", timer);
        setTelegraph(TELEGRAPH_CONE, timer, lockedYaw, (float) wave.range(), (float) wave.param("angle", 120.0));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.HOSTILE, 2.0F, 0.5F);
        tipOnce("wave");
    }

    private void tickWave(ServerLevel level) {
        lock();
        if (--timer > 0) {
            return;
        }
        impactFx("wave");  // 11月27日：当たった瞬間の演出
        clearTelegraph();
        EnemyDef.Skill wave = waveSkill();
        double half = wave.param("angle", 120.0) / 2.0;
        for (ServerPlayer player : targets(level)) {
            if (player.distanceTo(this) - player.getBbWidth() / 2.0 <= wave.range() + 0.6 && inFront(player.position(), half)
                    && EnemyCombat.strike(this, player, wave.power(), null)) {
                Vec3 away = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
                if (away.lengthSqr() > 1.0E-4) {
                    away = away.normalize().scale(wave.param("push", 1.0));
                    player.push(away.x, 0.2, away.z);
                    player.hurtMarked = true;
                }
            }
        }
        double yawRad = Math.toRadians(lockedYaw);
        for (int i = 0; i <= 12; i++) {
            double a = yawRad + Math.toRadians(-half + half * 2.0 * i / 12.0);
            for (double r = 1.5; r <= wave.range(); r += 1.5) {
                level.sendParticles(ParticleTypes.NOTE, getX() - Math.sin(a) * r, getY() + 0.8, getZ() + Math.cos(a) * r, 1, 0.1, 0.1, 0.1,
                        random.nextDouble());
            }
        }
        level.sendParticles(ParticleTypes.SONIC_BOOM, getX() - Math.sin(yawRad) * 3.0, getY() + 1.0, getZ() + Math.cos(yawRad) * 3.0,
                1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.2F, 1.6F);
        startCooldown("wave", cooldown(wave));
        action = Action.RECOVER;
        timer = 10;
    }

    private void lock() {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        setYHeadRot(lockedYaw);
    }

    // ---- 高音の柱（何人かの足もとの円）

    private void startPillars(ServerLevel level) {
        EnemyDef.Skill pillars = pillarsSkill();
        action = Action.PILLARS;
        pillarSpots.clear();
        timer = Math.max(10, pillars.windupTicks());
        float radius = (float) pillars.param("radius", 2.4);
        List<ServerPlayer> players = new ArrayList<>(targets(level));
        java.util.Collections.shuffle(players, new java.util.Random(random.nextLong()));
        int count = Math.min(players.size(), (int) Math.round(pillars.param("count", 2.0)) + (phase() >= 3 ? 1 : 0));
        for (int i = 0; i < count; i++) {
            Vec3 at = players.get(i).position();
            pillarSpots.add(at);
            GroundMarker.place(level, at, radius, timer, GroundMarker.KIND_DANGER);
        }
        setBusy(timer + 8);
        playAction("skill_pillars");
        setCast("pillars", timer);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, 2.0F, 1.8F);
        tipOnce("pillars");
    }

    private void tickPillars(ServerLevel level) {
        if (--timer > 0) {
            if (timer % 4 == 0) {
                for (Vec3 at : pillarSpots) {
                    level.sendParticles(ParticleTypes.NOTE, at.x, at.y + 0.3, at.z, 2, 0.8, 0.1, 0.8, random.nextDouble());
                }
            }
            return;
        }
        EnemyDef.Skill pillars = pillarsSkill();
        double radius = pillars.param("radius", 2.4) + 0.3;
        for (Vec3 at : pillarSpots) {
            impactFx("pillars", TELEGRAPH_CIRCLE, at, 0.0F, (float) radius, 0.0F);  // 11月27日：当たった瞬間の演出
            for (ServerPlayer player : targets(level)) {
                double dx = player.getX() - at.x;
                double dz = player.getZ() - at.z;
                if (dx * dx + dz * dz <= radius * radius && Math.abs(player.getY() - at.y) < 3.0) {
                    EnemyCombat.strike(this, player, pillars.power(), null);
                }
            }
            for (int k = 0; k < 6; k++) {
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + k * 0.8, at.z, 4, 0.3, 0.2, 0.3, 0.02);
            }
            level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1.0, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.6F, 1.6F);
        }
        pillarSpots.clear();
        startCooldown("pillars", cooldown(pillars));
        action = Action.RECOVER;
        timer = 8;
    }

    // ---- 祈り手を呼ぶ（HP60%）

    private void tickCall(ServerLevel level) {
        if (--timer > 0) {
            return;
        }
        clearTelegraph();
        int count = (int) Math.round(skill("call", 0.0, 1.5, 0.0, 0.0).param("count", 2.0));
        for (int i = 0; i < count; i++) {
            PrayerEcho echo = ModEntities.PRAYER_ECHO.get().create(level);
            if (echo == null) {
                continue;
            }
            double a = Math.PI * 2.0 * i / count + random.nextDouble();
            echo.moveTo(getX() + Math.cos(a) * 6.0, getY() + 0.5, getZ() + Math.sin(a) * 6.0, random.nextFloat() * 360.0F, 0.0F);
            echo.setHome(BlockPos.containing(position()));
            echo.setTransient(true);
            level.addFreshEntity(echo);
            summoned.add(echo.getUUID());
            ServerPlayer target = topThreat();
            if (target != null) {
                echo.addThreat(target, 1.0);
            }
            level.sendParticles(ParticleTypes.END_ROD, echo.getX(), echo.getY() + 1.0, echo.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.HOSTILE, 2.0F, 0.6F);
        tipOnce("call");
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

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            lightAll(level, false);
        }
        discardSummoned();
    }

    // ---------------------------------------------------------------- 選律の歌

    /** 部屋の音の床（階の座標から世界の座標へ。4つでなければ空）。 */
    private List<BlockPos> plates() {
        BossRoom room = room();
        if (room == null) {
            return List.of();
        }
        FloorDef def = room.floor();
        List<BlockPos> list = new ArrayList<>();
        for (BlockPos p : def.features().bossMarks(PLATES)) {
            list.add(def.world(p));
        }
        return list;
    }

    private void lightAll(ServerLevel level, boolean lit) {
        for (BlockPos plate : plates()) {
            TempleService.setLit(level, plate, lit);
        }
    }

    private void startSong(ServerLevel level) {
        EnemyDef.Skill song = songSkill();
        action = Action.SONG;
        int length = phase() >= 3 ? 5 : 4;
        notes = new int[length];
        for (int i = 0; i < length; i++) {
            notes[i] = random.nextInt(4);
            if (i > 0 && notes[i] == notes[i - 1]) {
                notes[i] = (notes[i] + 1 + random.nextInt(3)) % 4;
            }
        }
        answered = 0;
        int noteTicks = (int) Math.round(song.param("note_seconds", 0.9) * 20);
        timer = noteTicks * length + 10;
        setBusy(timer + 5);
        playAction("skill_song");
        setCast("song", timer);
        line("boss.amemaru.cantare.line.song");
        tipOnce("song");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0F, 1.2F);
    }

    /** 歌い手が1人ずつ歌い、同じ色の床が光る。 */
    private void tickSong(ServerLevel level) {
        EnemyDef.Skill song = songSkill();
        int noteTicks = Math.max(6, (int) Math.round(song.param("note_seconds", 0.9) * 20));
        int total = noteTicks * notes.length + 10;
        int elapsed = total - timer;
        List<BlockPos> plates = plates();
        if (elapsed >= 0 && elapsed % noteTicks == 0 && elapsed / noteTicks < notes.length && plates.size() == 4) {
            int index = elapsed / noteTicks;
            int note = notes[index];
            lightAll(level, false);
            TempleService.playNote(level, plates.get(note), note);
            Vec3 singer = singerPos(note);
            level.sendParticles(ParticleTypes.NOTE, singer.x, singer.y + 0.6, singer.z, 6, 0.3, 0.3, 0.3, note / 4.0);
            level.sendParticles(ParticleTypes.END_ROD, singer.x, singer.y, singer.z, 10, 0.3, 0.5, 0.3, 0.04);
            setStatus("song:" + sequenceKey(index + 1), 0);
        }
        if (--timer > 0) {
            return;
        }
        beginAnswer(level, null);
    }

    /** 最後の音を歌った（このあと踏めば、答えとして数える）。 */
    private boolean lastNoteSung() {
        EnemyDef.Skill song = songSkill();
        int noteTicks = Math.max(6, (int) Math.round(song.param("note_seconds", 0.9) * 20));
        int total = noteTicks * notes.length + 10;
        return notes.length > 0 && total - timer >= noteTicks * (notes.length - 1);
    }

    /**
     * 答えを待ちはじめる。11月25日のご指示「音の通りに床を踏んでも失敗になる」：前は、歌い終えて少したってから受け付け、それより
     * 前に乗った床は数えなかった（乗ったままだと、次の床が「1音目」になって外れた）。最後の音を歌ったら受け付け、そのときもう
     * 床に乗っている人は、その床が次の音なら数える（違っても外れにしない）。{@code skip} は、この答えを始めた踏み（あとで数える）。
     */
    private void beginAnswer(ServerLevel level, @javax.annotation.Nullable ServerPlayer skip) {
        EnemyDef.Skill song = songSkill();
        lightAll(level, false);
        action = Action.ANSWER;
        timer = (int) Math.round(song.param("answer", 12.0) * 20);
        setBusy(4);
        tipOnce("answer");
        BossRoom room = room();
        if (room == null) {
            return;
        }
        for (ServerPlayer player : room.participants()) {
            player.displayClientMessage(Component.translatable("boss.amemaru.cantare.answer", notes.length)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        }
        List<BlockPos> plates = plates();
        for (ServerPlayer player : room.participants()) {
            if (player == skip || action != Action.ANSWER || answered >= notes.length) {
                continue;
            }
            BlockPos on = TempleService.countedPlate(player);
            if (on != null && plates.indexOf(on) == notes[answered]) {
                countNote(level, on, notes[answered]);
            }
        }
    }

    /** 正しい床を踏んだ：1音進める（そろえば無防備に）。 */
    private void countNote(ServerLevel level, BlockPos pos, int index) {
        TempleService.playNote(level, pos, index);
        answered++;
        BossRoom room = room();
        if (answered >= notes.length) {
            expose(level);
        } else if (room != null) {
            for (ServerPlayer p : room.participants()) {
                p.displayClientMessage(Component.translatable("boss.amemaru.cantare.progress", answered, notes.length)
                        .withStyle(ChatFormatting.AQUA), true);
            }
        }
    }

    /** 歌った音の並びを、言語ファイルの引数にできる形（白・青…）で。 */
    private String sequenceKey(int count) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < count && i < notes.length; i++) {
            if (i > 0) {
                b.append(',');
            }
            b.append(NOTE_KEYS[notes[i]]);
        }
        return b.toString();
    }

    /** 歌い手の場所（4人が、指揮者のまわりの4方に）。 */
    private Vec3 singerPos(int note) {
        double a = Math.toRadians(yBodyRot + 45.0 + note * 90.0);
        return position().add(-Math.sin(a) * 2.6, getBbHeight() * 0.55, Math.cos(a) * 2.6);
    }

    /** 答えを待つ間：残りの秒を出し、時間が切れたら不協和音。 */
    private void tickAnswer(ServerLevel level) {
        if (tickCount % 10 == 0) {
            setStatus("answer", (timer + 19) / 20);
        }
        if (--timer > 0) {
            return;
        }
        discord(level);
    }

    /**
     * 音の床が踏まれた（{@link TempleService#step}）。この部屋の床なら true（ほかの仕掛けには渡さない）。
     */
    public boolean onPlateStepped(ServerLevel level, ServerPlayer player, BlockPos pos, int note) {
        List<BlockPos> plates = plates();
        int index = plates.indexOf(pos);
        if (index < 0) {
            return false;
        }
        if (action == Action.SONG && lastNoteSung()) {
            beginAnswer(level, player);
        }
        if (action == Action.SONG) {
            // 歌っている間：床を光らせない（歌の光とまぎれないように）。数えもしない（歌い終えてから、乗っていれば数える）
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.5F, 1.4F);
            return true;
        }
        if (action != Action.ANSWER || notes.length == 0) {
            TempleService.playNote(level, pos, index);
            return true;
        }
        if (notes[answered] != index) {
            TempleService.playNote(level, pos, index);
            discord(level);
            return true;
        }
        countNote(level, pos, index);
        return true;
    }

    /** 歌がそろった：歌い手が黙り、無防備になる。 */
    private void expose(ServerLevel level) {
        EnemyDef.Skill song = songSkill();
        double seconds = song.param("exposed", 12.0) * (phase() >= 3 ? 0.8 : 1.0);
        exposed = (int) Math.round(seconds * 20);
        setOpen(true);
        action = Action.OPEN;
        timer = exposed;
        setBusy(exposed);
        notes = new int[0];
        answered = 0;
        clearTelegraph();
        playAction("skill_open");
        lightAll(level, true);
        setStatus("open", (exposed + 19) / 20);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.0F, 0.7F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ALLAY_DEATH, SoundSource.HOSTILE, 1.6F, 0.5F);
        line("boss.amemaru.cantare.line.open");
        tipOnce("open");
        BossRoom room = room();
        if (room != null) {
            for (ServerPlayer p : room.participants()) {
                p.displayClientMessage(Component.translatable("boss.amemaru.cantare.opened", Math.round(seconds))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            }
        }
    }

    /** 不協和音：部屋の全員にダメージ。少しあとでまた歌う。 */
    private void discord(ServerLevel level) {
        EnemyDef.Skill song = songSkill();
        notes = new int[0];
        answered = 0;
        lightAll(level, false);
        for (ServerPlayer player : targets(level)) {
            EnemyCombat.strike(this, player, song.power(), null);
        }
        level.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 1.5, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.0, getZ(), 40, 6.0, 1.0, 6.0, 0.05);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.HOSTILE, 2.0F, 0.5F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.4F, 0.8F);
        line("boss.amemaru.cantare.line.discord");
        BossRoom room = room();
        if (room != null) {
            for (ServerPlayer p : room.participants()) {
                p.displayClientMessage(Component.translatable("boss.amemaru.cantare.discord").withStyle(ChatFormatting.RED), true);
            }
        }
        songCooldown = (int) Math.round(song.param("retry", 8.0) * 20);
        action = Action.RECOVER;
        timer = 16;
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
        cancelAction(level);
        action = Action.NONE;
        clearBusy();
        switch (name) {
            case "wave" -> {
                List<ServerPlayer> players = targets(level);
                if (players.isEmpty()) {
                    return false;
                }
                startWave(level, players.get(0));
            }
            case "pillars" -> startPillars(level);
            case "song" -> {
                if (plates().size() != 4) {
                    return false;
                }
                startSong(level);
            }
            case "call" -> {
                action = Action.CALL;
                timer = 2;
            }
            case "echo" -> {
                if (plates().size() != 4) {
                    return false;
                }
                startEcho(level);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** 自動テスト用：歌の音の並び（歌っていなければ空）。 */
    public int[] testNotes() {
        return notes.clone();
    }

    /** 自動テスト用：すぐに答えを待つ形にする（歌い終えたことにする）。 */
    public void testFinishSong() {
        if (action == Action.SONG) {
            timer = 1;
            if (level() instanceof ServerLevel level) {
                tickSong(level);
            }
        }
    }
}

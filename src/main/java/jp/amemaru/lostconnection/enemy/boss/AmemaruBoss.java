package jp.amemaru.lostconnection.enemy.boss;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.StoryTextS2C;
import jp.amemaru.lostconnection.tower.BossRoom;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * ボス（Phase 0-B 4.4「ボス（AmemaruBoss）：敵に加えて、段階（フェーズ）・ボスバー・ヘイト表を持つ」）。
 *
 * <ul>
 *   <li>ボスの部屋（{@link BossRoom}）が出し、戦いの参加者の画面にボスバーを出す</li>
 *   <li>HPは、参加者の人数で増える（人数補正。部屋の定義の {@code hp_per_extra_player}）</li>
 *   <li>HPの割合で段階が変わる（{@link #phaseFor}）。段階が変わると {@link #onPhaseChanged} が呼ばれる</li>
 *   <li>倒れたら、部屋に知らせる（初撃破の記録・物語の演出・出口の円）。ドロップと経験値は、ほかの敵と同じく、攻撃を当てた人それぞれに</li>
 *   <li>保存しない（部屋が出し直す）。部屋が今出しているボスでなければ、自分で消える（{@link BossRoom#owns}）</li>
 * </ul>
 */
public abstract class AmemaruBoss extends AmemaruMob {
    /** 今出している技の名前（クライアントで、ボスのHPの帯の下に技の名前を出す。11月24日「ボスの攻撃がわかりづらい」）。 */
    private static final EntityDataAccessor<String> DATA_CAST = SynchedEntityData.defineId(AmemaruBoss.class, EntityDataSerializers.STRING);
    /**
     * 管理者・CIの写真：みんなで受ける一撃を、印と演出だけにする（ダメージなし）。保存しない
     * （{@code /amemaru admin boss share safe}）。
     */
    public static boolean shareSafe;

    private final ServerBossEvent bossEvent;
    @Nullable
    private BossRoom room;
    private int phase = 1;

    protected AmemaruBoss(EntityType<? extends AmemaruBoss> type, Level level, BossEvent.BossBarColor color) {
        super(type, level);
        bossEvent = new ServerBossEvent(Component.empty(), color, BossEvent.BossBarOverlay.NOTCHED_10);
        bossEvent.setDarkenScreen(false);
        setTransient(true);
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_CAST, "");
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return AmemaruMob.createAttributes()
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    // ---------------------------------------------------------------- 部屋と参加者

    public void setRoom(@Nullable BossRoom room) {
        this.room = room;
    }

    @Nullable
    public BossRoom room() {
        return room;
    }

    /** 戦いの参加者（ボスバーを見せる人）。 */
    public void setParticipants(Collection<ServerPlayer> players) {
        for (ServerPlayer player : new ArrayList<>(bossEvent.getPlayers())) {
            if (!players.contains(player)) {
                bossEvent.removePlayer(player);
            }
        }
        for (ServerPlayer player : players) {
            bossEvent.addPlayer(player);
        }
    }

    public void clearParticipants() {
        bossEvent.removeAllPlayers();
    }

    /** ボスバーを見ている人の数（自動テストで使う）。 */
    public int barViewers() {
        return bossEvent.getPlayers().size();
    }

    /**
     * 11月22日：ボスが見えなくなった人（倒れて教会へ戻った・リタイアした・ほかの世界へ移った・抜けた）から、ボスバーを外す。
     * 部屋はその階に人がいる間しか動かないので、参加者がみな教会へ戻ると部屋からは外されず、街に戻ってもバーが出たままだった。
     * 生き返った人は別の {@link ServerPlayer} になるので、同じ人（UUID）の古いものも外す。部屋に戻れば、部屋がまた加える。
     */
    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        for (ServerPlayer viewer : new ArrayList<>(bossEvent.getPlayers())) {
            if (viewer == player || viewer.getUUID().equals(player.getUUID())) {
                bossEvent.removePlayer(viewer);
            }
        }
    }

    /**
     * 11月22日：区画が閉じてボスが消えたとき（このときは {@link #remove} が呼ばれない）も、ボスバーを外す。外さないと、
     * 部屋が出し直したボスのバーと2本になり、古いほうはいつまでも消えなかった。
     */
    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        bossEvent.removeAllPlayers();
    }

    /** 今の段階（1から）。 */
    public int phase() {
        return phase;
    }

    /** HPの割合（0〜1）から段階を決める（ボスごとに上書きする）。 */
    protected abstract int phaseFor(double ratio);

    /** 段階が変わったとき。 */
    protected void onPhaseChanged(ServerLevel level, int from, int to) {
    }

    /** 戦いが始まったとき（部屋が閉じた）。 */
    public void onFightStarted(ServerLevel level, List<ServerPlayer> participants) {
    }

    /** 戦いをはじめからにする（全員が倒れた・部屋を離れた）。 */
    public void resetFight() {
        resetCombat();
        phase = 1;
        fightTicks = 0;
        enraged = false;
        setAttackMultiplier(baseAttackMultiplier);
        resetShare();
    }

    // ---------------------------------------------------------------- 人数補正と怒り（11月23日）

    /** 人数補正の攻撃の倍率（怒りの前）。 */
    private double baseAttackMultiplier = 1.0;
    /** 技の間の倍率（人数が多いほど短い。1 より小さいほど速い）。 */
    private double pace = 1.0;
    /** 怒るまでの tick（0 なら怒らない）。 */
    private int enrageTicks;
    private int fightTicks;
    private boolean enraged;

    /**
     * 戦いの人数補正を決める（部屋が閉じたとき。{@link BossRoom}）。11月23日のご指示「ボス戦の参加人数で強さが変わるように。
     * 意外と簡単に攻略できてしまう」で、HPのほかに、攻撃と技の間も人数で変え、長引くと怒る（攻撃 +50%、技の間 ×0.75）。
     */
    public void setFightScaling(double attackMultiplier, double pace, int enrageSeconds) {
        baseAttackMultiplier = Math.max(0.1, attackMultiplier);
        this.pace = Math.max(0.3, Math.min(1.0, pace));
        enrageTicks = Math.max(0, enrageSeconds) * 20;
        fightTicks = 0;
        enraged = false;
        setAttackMultiplier(baseAttackMultiplier);
        resetShare();
    }

    /** 技の間の倍率（人数補正と怒り）。技のクールダウンにかける。 */
    protected double pace() {
        return enraged ? pace * 0.75 : pace;
    }

    public boolean enraged() {
        return enraged;
    }

    /** 戦いの間、毎 tick（怒るまでを数える）。 */
    private void tickFightTimer(ServerLevel level) {
        if (room == null || !room.fighting() || enrageTicks <= 0 || enraged) {
            return;
        }
        if (++fightTicks >= enrageTicks) {
            enraged = true;
            setAttackMultiplier(baseAttackMultiplier * 1.5);
            onEnraged(level);
        }
    }

    /** 怒ったとき（戦いが長引いた）。 */
    protected void onEnraged(ServerLevel level) {
        tip("boss.amemaru.enraged");
        level.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.RAVAGER_ROAR,
                net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.5F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight(), getZ(),
                12, 1.2, 0.6, 1.2, 0.0);
    }

    // ---------------------------------------------------------------- 技の名前（11月24日）

    private int castClear;

    /** 今出している技（{@code sweep} など。なければ ""）。クライアントは {@code cast.amemaru.<ボス>.<技>} の名前を出す。 */
    public String cast() {
        return entityData.get(DATA_CAST);
    }

    /** 技の名前を出す（{@code ticks} のあとで消す）。 */
    protected void setCast(String skill, int ticks) {
        entityData.set(DATA_CAST, skill);
        castClear = tickCount + Math.max(10, ticks) + 8;
    }

    /** 技のアニメーション（{@code skill_<技>}）を流すときに、技の名前も出す。 */
    @Override
    public void playAction(String name) {
        super.playAction(name);
        if (!level().isClientSide() && name.startsWith("skill_")) {
            setCast(name.substring("skill_".length()), 30);
        }
    }

    // ---------------------------------------------------------------- みんなで受ける一撃（11月24日）

    /*
     * 11月24日のご指示「ソロでは全然倒せない、パーティーで挑まないと絶対にクリアできないぐらいの難易度」【補完】：
     * 敵の定義の技 share があるボスは、ときどき参加者のひとりの足もとに青い円を出し、windup のあとで、円の中の人みなで
     * 「最大HPの power %」を人数で分けて受ける（防御・回避は効かない。障壁・不屈・身代わりの鈴は効く）。円に誰もいなければ、
     * 狙われた人がひとりで受ける。power 160 なら、ひとりは倒れ、2人で80%ずつ、4人で40%ずつ。
     */
    private int shareCooldown = -1;
    private int shareTimer;
    @Nullable
    private Vec3 shareAt;
    @Nullable
    private UUID shareTarget;
    private boolean shareTipShown;

    private void resetShare() {
        shareCooldown = -1;
        shareTimer = 0;
        shareAt = null;
        shareTarget = null;
        shareTipShown = false;
    }

    @Nullable
    private EnemyDef.Skill shareSkill() {
        EnemyDef def = def();
        return def == null ? null : def.skills().get("share");
    }

    /** 青い円が出ている（打つ前）。 */
    public boolean shareActive() {
        return shareTimer > 0;
    }

    /** 管理者・自動テスト：すぐにみんなで受ける一撃を出す（戦いの間だけ）。出せたら true。 */
    public boolean forceShare() {
        EnemyDef.Skill share = shareSkill();
        if (share == null || room == null || !room.fighting() || !(level() instanceof ServerLevel level) || shareTimer > 0) {
            return false;
        }
        return startShare(level, share);
    }

    private void tickShare(ServerLevel level) {
        EnemyDef.Skill share = shareSkill();
        if (share == null || room == null || !room.fighting()) {
            shareCooldown = -1;
            shareTimer = 0;
            shareAt = null;
            return;
        }
        if (shareTimer > 0) {
            if (--shareTimer == 0) {
                landShare(level, share);
            } else if (shareAt != null && shareTimer % 5 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, shareAt.x, shareAt.y + 0.2, shareAt.z, 6, share.range() * 0.5, 0.05,
                        share.range() * 0.5, 0.0);
            }
            return;
        }
        if (shareCooldown < 0) {
            shareCooldown = (int) Math.round(share.param("first", 20.0) * 20.0);
            return;
        }
        if (--shareCooldown <= 0 && !startShare(level, share)) {
            shareCooldown = 40;
        }
    }

    private boolean startShare(ServerLevel level, EnemyDef.Skill share) {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : room.participants()) {
            if (player.isAlive() && player.level() == level && !jp.amemaru.lostconnection.player.DownHandler.isDown(player)) {
                candidates.add(player);
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        ServerPlayer target = candidates.get(random.nextInt(candidates.size()));
        int windup = Math.max(30, share.windupTicks());
        shareTimer = windup;
        shareTarget = target.getUUID();
        shareAt = target.position();
        jp.amemaru.lostconnection.entity.GroundMarker.place(level, shareAt, (float) share.range(), windup,
                jp.amemaru.lostconnection.entity.GroundMarker.KIND_SHARE);
        setCast("share", windup);
        level.playSound(null, shareAt.x, shareAt.y, shareAt.z, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0F, 0.7F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4F, 1.2F);
        for (ServerPlayer player : room.participants()) {
            player.displayClientMessage(player == target
                    ? Component.translatable("boss.amemaru.share.you").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                    : Component.translatable("boss.amemaru.share.other", target.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
        }
        if (!shareTipShown) {
            shareTipShown = true;
            tip("boss.amemaru.share.tip");
        }
        shareCooldown = (int) Math.round(share.cooldownTicks() * pace());
        return true;
    }

    private void landShare(ServerLevel level, EnemyDef.Skill share) {
        Vec3 at = shareAt;
        shareAt = null;
        if (at == null || room == null) {
            return;
        }
        double radius = share.range() + 0.4;
        List<ServerPlayer> inside = new ArrayList<>();
        for (ServerPlayer player : room.participants()) {
            if (!player.isAlive() || player.level() != level || jp.amemaru.lostconnection.player.DownHandler.isDown(player)) {
                continue;
            }
            double dx = player.getX() - at.x;
            double dz = player.getZ() - at.z;
            if (dx * dx + dz * dz <= radius * radius && Math.abs(player.getY() - at.y) <= 3.0) {
                inside.add(player);
            }
        }
        if (inside.isEmpty() && shareTarget != null) {
            // 円に誰もいない：狙われた人が、ひとりで受ける
            for (ServerPlayer player : room.participants()) {
                if (player.getUUID().equals(shareTarget) && player.isAlive()
                        && !jp.amemaru.lostconnection.player.DownHandler.isDown(player)) {
                    inside.add(player);
                }
            }
        }
        double percent = share.power() * (enraged() ? 1.25 : 1.0);
        double each = SharedBlow.split(percent, inside.size());
        for (ServerPlayer player : inside) {
            SharedBlow.hit(player, each);
        }
        for (ServerPlayer player : room.participants()) {
            player.displayClientMessage(Component.translatable(inside.size() <= 1 ? "boss.amemaru.share.alone" : "boss.amemaru.share.shared",
                    inside.size(), Math.round(each * 100.0)).withStyle(inside.size() <= 1 ? ChatFormatting.RED : ChatFormatting.AQUA), true);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 4, 1.0, 0.2, 1.0, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.3, at.z, 50, radius * 0.5, 0.2, radius * 0.5, 0.18);
        level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y + 1.0, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.8F, 0.5F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 0.8F);
        shareTarget = null;
    }

    /** 管理者のコマンドで出させられる技の名前（{@code /amemaru admin boss skill}）。 */
    public List<String> skillNames() {
        return List.of();
    }

    /** 管理者：技を出させる（戦いの間だけ）。出せたら true。 */
    public boolean forceSkill(String name) {
        return false;
    }

    /** 参加者に、戦い方の手がかりを出す（ボスのチュートリアル。Phase 0-A 9章「ヘイト・回避・蘇生のチュートリアルを兼ねる」）。 */
    protected void tip(String key) {
        if (room == null) {
            return;
        }
        for (ServerPlayer player : room.participants()) {
            ModNetwork.sendTo(player, new StoryTextS2C(StoryTextS2C.Kind.TIP, "", Component.translatable(key).getString(), 100));
        }
    }

    /** 参加者に、ボスの台詞を字幕で出す。 */
    protected void line(String key) {
        if (room == null) {
            return;
        }
        EnemyDef def = def();
        String name = def == null ? "" : def.name();
        for (ServerPlayer player : room.participants()) {
            ModNetwork.sendTo(player, new StoryTextS2C(StoryTextS2C.Kind.SUBTITLE, name, Component.translatable(key).getString(), 70));
        }
    }

    // ---------------------------------------------------------------- 毎 tick

    @Override
    public void tick() {
        if (!level().isClientSide() && isAlive() && room != null && !room.owns(this)) {
            // 部屋が今出しているボスではない（部屋が出し直したあとに戻ってきた古いボス）：消す。倒れたボス（部屋はもう手放している）は、倒れる動きを最後まで
            jp.amemaru.lostconnection.AmemaruMod.LOGGER.info("[amemaru] Removed a stale boss of floor {} ({})", room.floor().number(),
                    getStringUUID());
            bossEvent.removeAllPlayers();
            discard();
            return;
        }
        super.tick();
        if (level() instanceof ServerLevel level && isAlive()) {
            EnemyDef def = def();
            if (def != null && bossEvent.getName().getString().isEmpty()) {
                bossEvent.setName(Component.literal(def.name()));
            }
            double ratio = getEnemyHp() / getEnemyMaxHp();
            bossEvent.setProgress((float) Math.max(0.0, Math.min(1.0, ratio)));
            int wanted = phaseFor(ratio);
            if (wanted > phase) {
                int from = phase;
                phase = wanted;
                onPhaseChanged(level, from, wanted);
            }
            tickFightTimer(level);
            tickShare(level);
            if (castClear > 0 && tickCount >= castClear) {
                castClear = 0;
                entityData.set(DATA_CAST, "");
            }
        }
    }

    @Override
    protected double leashRange() {
        // ボスは部屋から出ない（部屋が戦いを終わらせる）
        return 1000.0;
    }

    @Override
    protected boolean usesLeash() {
        // 戻る・HPが満タンになるのは、部屋が決める（参加者がみな部屋からいなくなったとき）
        return false;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel && room != null) {
            room.onBossDefeated(this);
        }
        bossEvent.removeAllPlayers();
    }

    @Override
    public void remove(RemovalReason reason) {
        bossEvent.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }
}

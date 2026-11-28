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
    /** ボスの今の様子（Phase 8 その2：3階のボスの「炉心は水の中」「炉心がむき出し」など。なければ ""）と、残りの秒。 */
    private static final EntityDataAccessor<String> DATA_STATUS = SynchedEntityData.defineId(AmemaruBoss.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_STATUS_SECONDS = SynchedEntityData.defineId(AmemaruBoss.class,
            EntityDataSerializers.INT);
    /**
     * 11月25日：戦いの様子（クライアントの、ボスの曲と演出）。{@link #FIGHT_NONE}・{@link #FIGHT_ON}・{@link #FIGHT_INTENSE}
     * （HPが {@link #INTENSE_RATIO} 以下か、怒った）。
     */
    private static final EntityDataAccessor<Byte> DATA_FIGHT = SynchedEntityData.defineId(AmemaruBoss.class, EntityDataSerializers.BYTE);
    /** 11月25日：今の段階（クライアントで、段階が変わった演出を出す）。 */
    private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(AmemaruBoss.class, EntityDataSerializers.BYTE);
    public static final int FIGHT_NONE = 0;
    public static final int FIGHT_ON = 1;
    public static final int FIGHT_INTENSE = 2;
    /** 追いこんだ（曲が少し速く高くなり、ボスのまわりに赤い気配）と見るHPの割合。 */
    public static final double INTENSE_RATIO = 0.3;
    /**
     * 管理者・CIの写真：みんなで受ける一撃を、印と演出だけにする（ダメージなし）。保存しない
     * （{@code /amemaru admin boss share safe}）。
     */
    public static boolean shareSafe;
    /**
     * 11月28日：参加者全員を狙う技（一斉の印・大技）を、ボスが自分で出すか。CIの写真では止め、管理者のコマンドで出させる
     * （{@code /amemaru admin boss party manual}。保存しない）。
     */
    public static boolean partyAuto = true;

    private final ServerBossEvent bossEvent;
    @Nullable
    private BossRoom room;
    private int phase = 1;
    /** 11月28日：参加者全員を狙う技（一斉の印・大技）。 */
    private final PartyAttacks party = new PartyAttacks(this);

    protected AmemaruBoss(EntityType<? extends AmemaruBoss> type, Level level, BossEvent.BossBarColor color) {
        super(type, level);
        bossEvent = new ServerBossEvent(Component.empty(), color, BossEvent.BossBarOverlay.NOTCHED_10);
        bossEvent.setDarkenScreen(false);
        // 11月25日：HPの帯を見ている人（戦いの参加者）のクライアントが、このボスの曲を流す（BossFx）。バニラでは、ジ・エンドの外では何もしない
        bossEvent.setPlayBossMusic(true);
        setTransient(true);
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_CAST, "");
        entityData.define(DATA_STATUS, "");
        entityData.define(DATA_STATUS_SECONDS, 0);
        entityData.define(DATA_FIGHT, (byte) FIGHT_NONE);
        entityData.define(DATA_PHASE, (byte) 1);
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

    /** 11月25日：戦いの様子と段階を、クライアントへ（変わったときだけ）。 */
    private void syncFight(double ratio) {
        boolean on = room != null && room.fighting() && room.owns(this);
        byte fight = (byte) (!on ? FIGHT_NONE : enraged || ratio <= INTENSE_RATIO ? FIGHT_INTENSE : FIGHT_ON);
        if (entityData.get(DATA_FIGHT) != fight) {
            entityData.set(DATA_FIGHT, fight);
        }
        byte shown = (byte) Math.max(1, Math.min(9, phase));
        if (entityData.get(DATA_PHASE) != shown) {
            entityData.set(DATA_PHASE, shown);
        }
    }

    /** 戦いの様子（{@link #FIGHT_NONE}・{@link #FIGHT_ON}・{@link #FIGHT_INTENSE}。クライアントでも読める）。 */
    public int fightState() {
        return entityData.get(DATA_FIGHT);
    }

    /** 段階（クライアントでも読める）。 */
    public int shownPhase() {
        return entityData.get(DATA_PHASE);
    }

    /** 自動テスト：HPの帯を見ている人のクライアントで、ボスの曲を流す印が立っているか。 */
    public boolean barPlaysMusic() {
        return bossEvent.shouldPlayBossMusic();
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
        party.reset();
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

    // ---------------------------------------------------------------- 息つぎ（11月25日）

    /**
     * 技と技の間の息つぎ（秒。ひとりで戦うとき）。11月25日のご指示「ボスのモーションが早く近接職が辛いので技を出すまでの間隔を
     * 遅くするべき」【補完】：前は技が終わるとすぐ次の技を選び、近くで戦う人が打ち返す間がなかった。技が終わったら、この間は
     * 次の技を出さない（打ち返す好機）。人数が多い・怒っているときは少し短い（0.6倍まで）。
     */
    public static final double BREATHE_SECONDS = 1.4;
    private boolean wasActing;

    /** 技を出している途中か（各ボスが返す。技が終わったのを見て、息つぎをはさむ）。 */
    protected boolean acting() {
        return false;
    }

    /** 息つぎの長さ（tick）。 */
    public int breatheTicks() {
        return (int) Math.round(BREATHE_SECONDS * 20.0 * Math.max(0.6, pace()));
    }

    private void tickBreathe() {
        boolean now = acting();
        if (wasActing && !now && room != null && room.fighting()) {
            setBusy(breatheTicks());
        }
        wasActing = now;
    }

    /**
     * 技のアニメーションを流す（{@code skill_<技>} なら、技の名前も出す）。予兆を長くした技（定義の技の {@code anim_windup}：
     * アニメーションを作ったときの予兆の秒）は、長くした分だけゆっくり流し、腕を振り下ろす瞬間と当たる時をそろえる（11月25日）。
     */
    @Override
    public void playAction(String name) {
        EnemyDef def = def();
        String skillName = skillOfAction(name);
        EnemyDef.Skill skill = def == null || skillName == null ? null : def.skills().get(skillName);
        float speed = 1.0F;
        if (skill != null && skill.windup() > 0.0) {
            double designed = skill.param("anim_windup", skill.windup());
            speed = (float) Math.max(0.3, Math.min(1.0, designed / skill.windup()));
        }
        super.playAction(name, speed);
        if (!level().isClientSide() && name.startsWith("skill_")) {
            setCast(name.substring("skill_".length()), 30);
        }
    }

    /** アニメーションの名前から、技の名前（{@code skill_sweep} → {@code sweep}）。ボスごとに上書きできる。 */
    @Nullable
    protected String skillOfAction(String action) {
        return action.startsWith("skill_") ? action.substring("skill_".length()) : null;
    }

    /** 今出している技（{@code sweep} など。なければ ""）。クライアントは {@code cast.amemaru.<ボス>.<技>} の名前を出す。 */
    public String cast() {
        return entityData.get(DATA_CAST);
    }

    /** 技の名前を出す（{@code ticks} のあとで消す）。 */
    protected void setCast(String skill, int ticks) {
        entityData.set(DATA_CAST, skill);
        castClear = tickCount + Math.max(10, ticks) + 8;
    }

    /**
     * ボスの今の様子（{@code status.amemaru.<ボス>.<様子>} の文で、技の名前の下に出す。{@code seconds} が 0 より大きければ、
     * 残りの秒も出す）。Phase 8 その2：3階のボスの水の鎧と炉心。
     */
    public String status() {
        return entityData.get(DATA_STATUS);
    }

    public int statusSeconds() {
        return entityData.get(DATA_STATUS_SECONDS);
    }

    protected void setStatus(String key, int seconds) {
        if (!key.equals(entityData.get(DATA_STATUS))) {
            entityData.set(DATA_STATUS, key);
        }
        if (seconds != entityData.get(DATA_STATUS_SECONDS)) {
            entityData.set(DATA_STATUS_SECONDS, Math.max(0, seconds));
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

    /**
     * みんなで受ける一撃の数値。11月25日のご指示「「みんなで受ける一撃」はレイドボス限定にしましょう」で、レイドのボス
     * （敵の定義の {@code raid}。10階・15階など）だけが使う。1〜5階のボスは使わない（定義からも外した）。
     */
    @Nullable
    private EnemyDef.Skill shareSkill() {
        EnemyDef def = def();
        return def == null || !def.raid() ? null : def.skills().get("share");
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
        // 11月26日：ひとり確認モードでは、円の中の練習用の人形も、受ける人に数える（人形はダメージを受けない）
        int count = inside.size() + jp.amemaru.lostconnection.tower.SoloTest.standIns(level, at, radius, 3.0);
        double each = SharedBlow.split(percent, count);
        for (ServerPlayer player : inside) {
            SharedBlow.hit(player, each);
        }
        for (ServerPlayer player : room.participants()) {
            player.displayClientMessage(Component.translatable(count <= 1 ? "boss.amemaru.share.alone" : "boss.amemaru.share.shared",
                    count, Math.round(each * 100.0)).withStyle(count <= 1 ? ChatFormatting.RED : ChatFormatting.AQUA), true);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 4, 1.0, 0.2, 1.0, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.3, at.z, 50, radius * 0.5, 0.2, radius * 0.5, 0.18);
        level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y + 1.0, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.8F, 0.5F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 0.8F);
        shareTarget = null;
    }

    // ---------------------------------------------------------------- 参加者全員を狙う技（11月28日）

    /** 参加者全員を狙う技（自動テスト・管理者）。 */
    public PartyAttacks partyAttacks() {
        return party;
    }

    /**
     * 全員を狙う技（一斉の印・大技）を出してよいか。ボスごとに、固有のギミックの最中や、よろめいている間（攻めどき）は止める
     * （11月28日【補完】）。
     */
    protected boolean partyAttackAllowed() {
        return true;
    }

    /** 全員を狙う技が当たった人に（ボスごとに、凍えなどを足す）。{@code skill} は {@code volley}・{@code nova}。 */
    protected void onPartyHit(ServerPlayer player, String skill) {
    }

    /** 全員を狙う技の名前（定義にあるものだけ。{@code volley}・{@code nova}・{@code nova_out} など）。 */
    public List<String> partyAttackNames() {
        return party.names();
    }

    /** 管理者・CIの写真：全員を狙う技をすぐに出す（戦いの間だけ）。出せたら true。 */
    public boolean forcePartyAttack(String name) {
        if (room == null || !room.fighting() || !(level() instanceof ServerLevel level)) {
            return false;
        }
        if (party.active()) {
            party.cancel();
        }
        List<ServerPlayer> targets = new ArrayList<>();
        for (ServerPlayer player : room.participants()) {
            if (player.isAlive() && player.level() == level && !player.isSpectator() && !BossRoom.isDown(player)) {
                targets.add(player);
            }
        }
        if (targets.isEmpty() || !party.names().contains(name)) {
            return false;
        }
        if (name.equals("volley")) {
            return party.startVolley(level, targets);
        }
        int kind = switch (name) {
            case "nova_out" -> PartyAttacks.NOVA_OUT;
            case "nova_in" -> PartyAttacks.NOVA_IN;
            case "nova_safe" -> PartyAttacks.NOVA_SAFE;
            default -> {
                EnemyDef def = def();
                EnemyDef.Skill nova = def == null ? null : def.skills().get("nova");
                int kinds = nova == null ? PartyAttacks.NOVA_SAFE : (int) Math.round(nova.param("kinds", PartyAttacks.NOVA_SAFE));
                yield (kinds & PartyAttacks.NOVA_SAFE) != 0 ? PartyAttacks.NOVA_SAFE : (kinds & PartyAttacks.NOVA_IN) != 0
                        ? PartyAttacks.NOVA_IN : PartyAttacks.NOVA_OUT;
            }
        };
        return party.startNova(level, kind, targets, room.arenaCenter(), room.def().arenaRadius(), room.def().startRadius());
    }

    /** 管理者のコマンドで出させられる技の名前（{@code /amemaru admin boss skill}）。 */
    public List<String> skillNames() {
        return List.of();
    }

    /**
     * 倒されたあと（{@link jp.amemaru.lostconnection.tower.BossRoom} から。{@code winners} は戦った人、{@code first} は
     * サーバーで初めて倒したとき）。11月24日：5階の古代守護者の「第七の門鍵」。ふつうは何もしない。
     */
    public void onVictory(ServerLevel level, List<ServerPlayer> winners, boolean first) {
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

    /**
     * 技が当たった瞬間の演出（11月27日のご指示「ボスの攻撃エフェクトがほぼ存在しない…プレイヤーがスキルを使った時のような
     * エフェクトを追加する」【補完】）。今出している予兆と同じ形（扇・線・円）を、ボスごとの色と材質で光らせる。予兆を消す前に呼ぶ。
     */
    protected void impactFx(String skill) {
        impactFx(skill, telegraph(), position(), telegraphYaw(), telegraphSize(), telegraphArg());
    }

    /** 技が当たった瞬間の演出（形と場所を指定する：足もとの円・着地した所など）。{@code shape} は {@code TELEGRAPH_*}。 */
    protected void impactFx(String skill, int shape, Vec3 at, float yaw, float size, float arg) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        new jp.amemaru.lostconnection.network.s2c.BossImpactS2C(getId(), barKey(), skill, shape, at, yaw, size, arg)
                .sendNear(level.dimension());
    }

    /** HPの帯の本数（敵の定義の {@code behavior.hp_bars}。1〜5。11月27日）。 */
    public static int hpBars(EnemyDef def) {
        return (int) Math.max(1, Math.min(5, Math.round(def.behavior("hp_bars", 1.0))));
    }

    /** HPの帯の見た目の名前（エンティティの種類の名前。veldhorn など）。 */
    public String barKey() {
        net.minecraft.resources.ResourceLocation type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(getType());
        return type == null ? "" : type.getPath();
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
                // 11月27日：ボスごとのHPの帯（クライアントの BossHpBar が、この名前の中の「ボスの種類」と「帯の本数」で描く。
                // バニラの帯として描くときは、名前だけが出る）
                bossEvent.setName(Component.translatable("boss.amemaru.bar", def.name(), hpBars(def), barKey()));
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
            party.tick(level);
            tickBreathe();
            syncFight(ratio);
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

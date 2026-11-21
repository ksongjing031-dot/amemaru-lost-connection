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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
        }
    }

    @Override
    protected double leashRange() {
        // ボスは部屋から出ない（部屋が戦いを終わらせる）
        return 1000.0;
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

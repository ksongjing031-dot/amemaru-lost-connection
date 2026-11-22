package jp.amemaru.lostconnection.enemy;

import jp.amemaru.lostconnection.combat.TauntService;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.loot.DropService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * このModの敵（Phase 0-B 4.4「敵（AmemaruMob）：GeckoLibで描画。独自のHPとステータスを持つ」）。
 *
 * <ul>
 *   <li>数値は敵の定義（データパックの {@code enemies/}）から読む。HPと攻撃には config の倍率がかかる</li>
 *   <li>独自のHPを持つ。バニラの体力は「独自のHPの割合」を写したもの（0になるとバニラのとおりに倒れる）</li>
 *   <li>ヘイトの表を持つ：与えたダメージの分だけ敵視が増え、一番高い人を狙う。ナイトの「呼び声」は表の一番上に置く</li>
 *   <li>戦い始めた所から離れすぎて、しばらく戦っていない（打たれも打ちもしない）と、追うのをやめて戻る。着くとHPが満タンに
 *       なる。戻る間も攻撃は当たり、打たれると戻るのをやめて戦い直す（HPはそのまま）。ノックバックで離れただけでは戻らない
 *       （11月22日のご指示。前は生まれた場所から28ブロックで、戻る間は攻撃が当たらなかった）</li>
 *   <li>攻撃の前には必ず予兆を出す（{@link #setTelegraph}。クライアントが地面の線や円・光で見せる）</li>
 *   <li>倒されると、攻撃を当てた人それぞれにドロップと経験値を配る（{@link DropService}）</li>
 * </ul>
 *
 * <p>見た目のファイルは {@code geo/entity/<名前>.geo.json}・{@code animations/entity/<名前>.animation.json}・
 * {@code textures/entity/<名前>.png}。アニメーションの名前は {@code animation.<名前>.idle / .walk / .attack /
 * .skill_<技> / .death}（Phase 0-B 12章）。</p>
 */
public abstract class AmemaruMob extends PathfinderMob implements GeoEntity, Enemy {
    // ---- 予兆の種類（クライアントの見せ方）
    public static final int TELEGRAPH_NONE = 0;
    /** 体が光る・身構える（近接攻撃・針を撃つ前）。 */
    public static final int TELEGRAPH_WINDUP = 1;
    /** 地面に、向きと長さを持つ線（突進）。 */
    public static final int TELEGRAPH_LINE = 2;
    /** 地面に円（周りへの攻撃）。 */
    public static final int TELEGRAPH_CIRCLE = 3;
    /** 頭の上に「！」（仲間を呼ぶ）。 */
    public static final int TELEGRAPH_ALERT = 4;
    /** 地面に、向きを持つ扇形（薙ぎ払い・後ろ蹴り。開きの角度は {@link #telegraphArg}）。11月22日 */
    public static final int TELEGRAPH_CONE = 5;

    private static final EntityDataAccessor<Float> DATA_HP = SynchedEntityData.defineId(AmemaruMob.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MAX_HP = SynchedEntityData.defineId(AmemaruMob.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_TELEGRAPH = SynchedEntityData.defineId(AmemaruMob.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TELEGRAPH_TICKS = SynchedEntityData.defineId(AmemaruMob.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_TELEGRAPH_YAW = SynchedEntityData.defineId(AmemaruMob.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TELEGRAPH_SIZE = SynchedEntityData.defineId(AmemaruMob.class, EntityDataSerializers.FLOAT);
    /** 予兆の形のほかの数（扇形の開きの角度・帯の幅。0 は決まった形） */
    private static final EntityDataAccessor<Float> DATA_TELEGRAPH_ARG = SynchedEntityData.defineId(AmemaruMob.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_RETURNING = SynchedEntityData.defineId(AmemaruMob.class, EntityDataSerializers.BOOLEAN);
    /** 同じ見た目の別の定義（強い個体）のID（クライアントの名前の表示に使う。11月22日） */
    private static final EntityDataAccessor<String> DATA_VARIANT = SynchedEntityData.defineId(AmemaruMob.class, EntityDataSerializers.STRING);
    /** 見た目と当たりの大きさ（定義の behavior の scale。11月22日） */
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(AmemaruMob.class, EntityDataSerializers.FLOAT);

    /** 倒れてから消えるまで（死ぬアニメーションの長さ）。 */
    private static final int DEATH_TICKS = 30;
    /** 敵視の表から外すまで、攻撃されていない時間。 */
    private static final int FORGET_TICKS = 20 * 20;
    /** 離れすぎても、この間戦っていなければ戻らない（戦っている間は、押し出されても戻らない）。 */
    private static final int LEASH_QUIET_TICKS = 20 * 5;
    /** 離れすぎがその2倍を超えたときは、これだけ戦っていなければ戻る。 */
    private static final int LEASH_FAR_QUIET_TICKS = 20 * 2;
    /** 相手を見失って（追う相手がいない）から、戻り始めるまで。 */
    private static final int LOST_QUIET_TICKS = 20 * 3;
    /** 戻る道がふさがっていても、これだけたったら、その場で戻り終える。 */
    private static final int RETURN_GIVE_UP_TICKS = 20 * 20;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private RawAnimation animIdle;
    private RawAnimation animWalk;
    private RawAnimation animDeath;

    // ---- サーバーだけ
    @Nullable
    private ResourceLocation variant;
    private int appliedGeneration = -1;
    @Nullable
    private BlockPos home;
    /** 戦い始めた所（追い始めた所。戻るときはここへ。巡回の敵は、生まれた場所ではなく、巡回の途中のここへ戻る）。 */
    @Nullable
    private Vec3 combatAnchor;
    /** 最後に戦った時（打たれた・打った。ゲームの時間）。 */
    private long lastCombatTime = Long.MIN_VALUE / 4;
    private int returnTicks;
    private final Map<UUID, Double> threat = new LinkedHashMap<>();
    private final Map<UUID, Double> contributions = new HashMap<>();
    private long lastHurtTick = Long.MIN_VALUE / 4;
    private int busyTicks;
    private int telegraphLeft;
    private final Map<String, Long> cooldowns = new HashMap<>();
    /** 塔の階の湧く所やボスの部屋が出した敵（保存しない。人が離れて区画が閉じたら消え、また人が来たら出し直す。Phase 5）。 */
    private boolean transientMob;
    /** 石柱の守り手なら、その階と名前（11月22日）。 */
    private int guardianFloor;
    @Nullable
    private String guardianId;
    /** HPの倍率（ボスの人数補正。Phase 5）。 */
    private double hpMultiplier = 1.0;
    /** 攻撃の倍率（ボスの人数補正と怒り。11月23日）。 */
    private double attackMultiplier = 1.0;

    // ---- クライアントだけ
    private long telegraphStartClient;

    protected AmemaruMob(EntityType<? extends AmemaruMob> type, Level level) {
        super(type, level);
        // バニラの経験値のオーブは出さない（このModの経験値は DropService で配る）
        xpReward = 0;
    }

    /** バニラの属性（体力は割合を写すだけなので20のまま。速さ・追う距離は定義で上書きする）。 */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    /** 見た目のファイルとアニメーションの名前（{@code tsunomofu} など）。 */
    public abstract String assetName();

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_HP, 100.0F);
        entityData.define(DATA_MAX_HP, 100.0F);
        entityData.define(DATA_TELEGRAPH, TELEGRAPH_NONE);
        entityData.define(DATA_TELEGRAPH_TICKS, 0);
        entityData.define(DATA_TELEGRAPH_YAW, 0.0F);
        entityData.define(DATA_TELEGRAPH_SIZE, 0.0F);
        entityData.define(DATA_TELEGRAPH_ARG, 0.0F);
        entityData.define(DATA_RETURNING, false);
        entityData.define(DATA_VARIANT, "");
        entityData.define(DATA_SCALE, 1.0F);
    }

    // ---------------------------------------------------------------- 定義と数値

    /** この敵の定義（なければ null。データパックから消えた敵は、動かずに立っているだけになる）。 */
    @Nullable
    public EnemyDef def() {
        if (variant != null) {
            EnemyDef def = EnemyDefs.get(variant);
            if (def != null) {
                return def;
            }
        }
        return EnemyDefs.forType(getType());
    }

    /** 定義のID（図鑑・ドロップで使う）。 */
    public ResourceLocation enemyId() {
        EnemyDef def = def();
        if (def != null) {
            return def.id();
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(getType());
        return key == null ? jp.amemaru.lostconnection.AmemaruMod.id("unknown") : key;
    }

    /** 同じ見た目の別の定義（強い個体など）を使う。 */
    public void setVariant(@Nullable ResourceLocation variant) {
        this.variant = variant;
        appliedGeneration = -1;
    }

    /** 攻撃のステータス（config の倍率をかけたもの）。 */
    public double attackPower() {
        EnemyDef def = def();
        return def == null ? 0.0 : def.attack() * ServerConfig.ENEMY_ATTACK_MULTIPLIER.get() * attackMultiplier;
    }

    /** 攻撃の倍率（ボスの人数補正と怒り。11月23日）。 */
    public void setAttackMultiplier(double multiplier) {
        attackMultiplier = Math.max(0.1, multiplier);
    }

    public double attackMultiplier() {
        return attackMultiplier;
    }

    /** 防御のステータス。 */
    public double defense() {
        EnemyDef def = def();
        return def == null ? 0.0 : def.defense();
    }

    public double getEnemyHp() {
        return entityData.get(DATA_HP);
    }

    public double getEnemyMaxHp() {
        return Math.max(1.0F, entityData.get(DATA_MAX_HP));
    }

    public void setEnemyHp(double hp) {
        entityData.set(DATA_HP, (float) Math.max(0.0, Math.min(getEnemyMaxHp(), hp)));
    }

    /** 定義の数値を当てる（初めての tick と、データパックを読み直したとき）。HPの割合は保つ。 */
    private void applyDefinition() {
        EnemyDef def = def();
        appliedGeneration = EnemyDefs.loader().generation();
        if (def == null) {
            return;
        }
        double ratio = getEnemyMaxHp() > 0 ? getEnemyHp() / getEnemyMaxHp() : 1.0;
        double max = def.hp() * ServerConfig.ENEMY_HP_MULTIPLIER.get() * hpMultiplier;
        entityData.set(DATA_MAX_HP, (float) max);
        entityData.set(DATA_HP, (float) Math.max(1.0, max * Math.max(0.0, Math.min(1.0, ratio))));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(def.moveSpeed());
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(def.followRange());
        entityData.set(DATA_VARIANT, variant == null ? "" : variant.toString());
        entityData.set(DATA_SCALE, (float) Math.max(0.3, Math.min(4.0, def.behavior().getOrDefault("scale", 1.0))));
    }

    /** 同じ見た目の別の定義のID（なければ null。クライアントでも読める）。 */
    @Nullable
    public ResourceLocation variantId() {
        String value = entityData.get(DATA_VARIANT);
        return value.isEmpty() ? null : ResourceLocation.tryParse(value);
    }

    /** 見た目と当たりの大きさ（1 がふつう）。 */
    public float modelScale() {
        return entityData.get(DATA_SCALE);
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return super.getDimensions(pose).scale(modelScale());
    }

    /** 石柱の守り手にする（11月22日）。 */
    public void setGuardian(int floor, String id) {
        guardianFloor = floor;
        guardianId = id;
    }

    public int guardianFloor() {
        return guardianFloor;
    }

    @Nullable
    public String guardianId() {
        return guardianId;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide() && guardianId != null) {
            jp.amemaru.lostconnection.tower.TowerService.onGuardianDied(this);
        }
    }

    /** HPの倍率（ボスの人数補正）を変える。HPの割合は保つ。 */
    public void setHpMultiplier(double multiplier) {
        hpMultiplier = Math.max(0.1, multiplier);
        appliedGeneration = -1;
        if (level() instanceof ServerLevel) {
            applyDefinition();
        }
    }

    public double hpMultiplier() {
        return hpMultiplier;
    }

    /** 保存しない敵にする（塔の階の湧く所・ボスの部屋が出した敵）。 */
    public void setTransient(boolean value) {
        transientMob = value;
    }

    public boolean isTransient() {
        return transientMob;
    }

    /*
     * 保存しない敵は、区画が閉じたら消す（11月22日：ボス戦で倒れるとボスが増えた不具合）。
     *
     * 前は shouldBeSaved() を false にしていた。区画を閉じるとき、バニラは shouldBeSaved() の敵だけを書き出して消すので、
     * そうでない敵は書き出されないうえに消えもせず、見えないまま記憶に残り、区画がまた開くと戻ってきた。部屋や湧く所からは
     * 見えない間に「いなくなった」と数えて新しく出すので、人が離れて戻るたびにボスや雑魚が1組ずつ増えた。
     * 今は書き出し（save）だけを断る。区画を閉じるときは、ほかの敵と同じく消える（区画には何も書かれない）。
     */
    @Override
    public boolean save(net.minecraft.nbt.CompoundTag tag) {
        return !transientMob && super.save(tag);
    }

    @Override
    public boolean saveAsPassenger(net.minecraft.nbt.CompoundTag tag) {
        return !transientMob && super.saveAsPassenger(tag);
    }

    /** 戦いをはじめからにする（敵視・貢献・予兆・技の途中を消し、HPを満タンにする）。ボスの部屋で全員が倒れたときに使う。 */
    public void resetCombat() {
        threat.clear();
        contributions.clear();
        cooldowns.clear();
        clearTelegraph();
        clearBusy();
        setTarget(null);
        setReturning(false);
        getNavigation().stop();
        setEnemyHp(getEnemyMaxHp());
    }

    /** 敵視のいちばん高い人（いなければ null）。 */
    @Nullable
    public ServerPlayer topThreat() {
        if (!(level() instanceof ServerLevel level)) {
            return null;
        }
        ServerPlayer best = null;
        double value = -1.0;
        for (Map.Entry<UUID, Double> entry : threat.entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null && player.isAlive() && player.level() == level && entry.getValue() > value
                    && !jp.amemaru.lostconnection.job.StealthService.hidden(player)) {
                best = player;
                value = entry.getValue();
            }
        }
        return best;
    }

    /** 独自のダメージを受ける。 */
    public void applyCustomDamage(double amount) {
        setEnemyHp(getEnemyHp() - amount);
    }

    /** バニラの体力を、独自のHPの割合に合わせる。 */
    private void mirrorHealth() {
        if (isDeadOrDying()) {
            return;
        }
        float wanted = (float) (getEnemyHp() / getEnemyMaxHp() * getMaxHealth());
        if (getEnemyHp() > 0 && wanted < 0.01F) {
            wanted = 0.01F;
        }
        if (Math.abs(getHealth() - wanted) > 1.0E-3F) {
            setHealth(wanted);
        }
    }

    // ---------------------------------------------------------------- 敵視（ヘイトの表）

    /** ダメージを受けたとき（プレイヤーなら、敵視と貢献を足す。戻っている途中なら、戻るのをやめて戦い直す）。 */
    public void onDamagedBy(@Nullable Entity attacker, double amount) {
        lastHurtTick = level().getGameTime();
        if (attacker instanceof ServerPlayer player && !player.isCreative() && !player.isSpectator()) {
            noteCombat();
            if (isReturning()) {
                // HPは満タンにせず、その場から戦い直す。戻る先はそのまま（次に離れすぎたときも、同じ所から数える）
                setReturning(false);
                returnTicks = 0;
                getNavigation().stop();
            }
            if (combatAnchor == null) {
                combatAnchor = position();
            }
            threat.merge(player.getUUID(), Math.max(1.0, amount), Double::sum);
            contributions.merge(player.getUUID(), Math.max(0.0, amount), Double::sum);
            if (getTarget() == null) {
                setTarget(player);
            }
            onAggro(player, true);
        } else if (attacker instanceof Player player && (player.isCreative() || player.isSpectator())) {
            contributions.merge(player.getUUID(), Math.max(0.0, amount), Double::sum);
        }
    }

    /** ナイトの「呼び声」：表の一番上に置く。 */
    public void onTaunted(ServerPlayer taunter) {
        double top = 0.0;
        for (double value : threat.values()) {
            top = Math.max(top, value);
        }
        threat.put(taunter.getUUID(), top + Math.max(10.0, top * 0.1));
        contributions.merge(taunter.getUUID(), 0.0, Double::sum);
        lastHurtTick = level().getGameTime();
        setTarget(taunter);
    }

    /** その人を見失う（潜伏。Phase 6 その2）：敵視を0にし、狙っていれば次の人に移る。 */
    public void forgetThreat(UUID player) {
        threat.remove(player);
        LivingEntity target = getTarget();
        if (target != null && target.getUUID().equals(player)) {
            setTarget(topThreat());
        }
    }

    /** 敵視を足す（仲間を呼ぶ・群れで反撃するとき）。 */
    public void addThreat(ServerPlayer player, double amount) {
        threat.merge(player.getUUID(), amount, Double::sum);
        lastHurtTick = level().getGameTime();
        if (getTarget() == null) {
            setTarget(player);
        }
    }

    /** その人の敵視の値（表になければ0）。 */
    public double threatOf(UUID player) {
        return threat.getOrDefault(player, 0.0);
    }

    /** 攻撃を当てた人（UUID → 与えたダメージ）。 */
    public Map<UUID, Double> contributions() {
        return Map.copyOf(contributions);
    }

    /** 狙いを選び直す（0.5秒ごと）。挑発されていれば挑発した人、そうでなければ敵視の一番高い人。 */
    private void selectTarget(ServerLevel level) {
        long now = level.getGameTime();
        UUID taunter = TauntService.taunterOf(this, now);
        double range = getAttributeValue(Attributes.FOLLOW_RANGE) * 1.5;
        ServerPlayer best = null;
        double bestThreat = -1.0;
        threat.entrySet().removeIf(entry -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            return player == null || !player.isAlive() || player.level() != level || player.isSpectator() || player.isCreative();
        });
        for (Map.Entry<UUID, Double> entry : threat.entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.distanceToSqr(this) > range * range) {
                continue;
            }
            double value = entry.getValue();
            if (entry.getKey().equals(taunter)) {
                value = Double.MAX_VALUE;
            }
            if (value > bestThreat) {
                bestThreat = value;
                best = player;
            }
        }
        if (best == null) {
            best = findAggroTarget(level);
            if (best != null) {
                threat.merge(best.getUUID(), 1.0, Double::sum);
                onAggro(best, false);
            }
        }
        if (best != getTarget()) {
            setTarget(best);
        }
        if (best == null && now - lastHurtTick > FORGET_TICKS) {
            threat.clear();
        }
    }

    /** 自分から気づく相手（定義の aggro_range の中で、見えていて、視野に入っている人）。 */
    @Nullable
    protected ServerPlayer findAggroTarget(ServerLevel level) {
        EnemyDef def = def();
        if (def == null || def.aggroRange() <= 0) {
            return null;
        }
        double range = def.aggroRange();
        double fov = def.behavior("vision_angle", 360.0);
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
                continue;
            }
            double distance = player.distanceToSqr(this);
            if (distance > range * range || distance >= bestDistance || !hasLineOfSight(player)) {
                continue;
            }
            if (fov < 360.0 && !inFront(player.position(), fov / 2.0)) {
                continue;
            }
            best = player;
            bestDistance = distance;
        }
        return best;
    }

    /** 狙いを決めたとき（{@code hurt} は叩かれて気づいたとき）。仲間を呼ぶ敵などが上書きする。 */
    protected void onAggro(ServerPlayer player, boolean hurt) {
    }

    /** その場所が、向いている方向の ±{@code halfAngle} 度の中にあるか。 */
    public boolean inFront(Vec3 point, double halfAngle) {
        Vec3 look = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 to = new Vec3(point.x - getX(), 0.0, point.z - getZ());
        if (to.lengthSqr() < 1.0E-6) {
            return true;
        }
        double dot = look.dot(to.normalize());
        return dot >= Math.cos(Math.toRadians(halfAngle));
    }

    // ---------------------------------------------------------------- 行動の状態

    /** 技を使っている途中か（ほかの技を始めない）。 */
    public boolean isBusy() {
        return busyTicks > 0;
    }

    public void setBusy(int ticks) {
        busyTicks = Math.max(busyTicks, ticks);
    }

    public void clearBusy() {
        busyTicks = 0;
    }

    /** 技のクールダウンが明けているか。 */
    public boolean ready(String skill) {
        Long until = cooldowns.get(skill);
        return until == null || level().getGameTime() >= until;
    }

    public void startCooldown(String skill, int ticks) {
        cooldowns.put(skill, level().getGameTime() + ticks);
    }

    /** 生まれた場所（追うのをやめて戻る場所）。 */
    public BlockPos home() {
        if (home == null) {
            home = blockPosition();
        }
        return home;
    }

    public void setHome(BlockPos pos) {
        home = pos;
    }

    /** 戻っている途中か（攻撃が当たらない）。 */
    public boolean isReturning() {
        return entityData.get(DATA_RETURNING);
    }

    private void setReturning(boolean value) {
        entityData.set(DATA_RETURNING, value);
    }

    // ---------------------------------------------------------------- 予兆

    /**
     * 予兆を出す（クライアントが、その種類に合わせて地面の線や円・光で見せる）。
     *
     * @param yaw  線の向き（度）
     * @param size 線の長さ・円の半径
     */
    public void setTelegraph(int kind, int ticks, float yaw, float size) {
        setTelegraph(kind, ticks, yaw, size, 0.0F);
    }

    /**
     * 予兆を出す（形のほかの数つき）。
     *
     * @param arg 扇形の開きの角度（度）・帯の幅（ブロック）。0 なら決まった形
     */
    public void setTelegraph(int kind, int ticks, float yaw, float size, float arg) {
        entityData.set(DATA_TELEGRAPH_ARG, arg);
        entityData.set(DATA_TELEGRAPH_YAW, yaw);
        entityData.set(DATA_TELEGRAPH_SIZE, size);
        entityData.set(DATA_TELEGRAPH_TICKS, ticks);
        entityData.set(DATA_TELEGRAPH, kind);
        telegraphLeft = ticks;
    }

    public void clearTelegraph() {
        entityData.set(DATA_TELEGRAPH, TELEGRAPH_NONE);
        telegraphLeft = 0;
    }

    public int telegraph() {
        return entityData.get(DATA_TELEGRAPH);
    }

    public int telegraphTicks() {
        return entityData.get(DATA_TELEGRAPH_TICKS);
    }

    public float telegraphYaw() {
        return entityData.get(DATA_TELEGRAPH_YAW);
    }

    public float telegraphSize() {
        return entityData.get(DATA_TELEGRAPH_SIZE);
    }

    public float telegraphArg() {
        return entityData.get(DATA_TELEGRAPH_ARG);
    }

    /** クライアント：予兆が始まってからの時間（tick）。 */
    public float telegraphAge(float partialTick) {
        return (float) (level().getGameTime() - telegraphStartClient) + partialTick;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_TELEGRAPH.equals(key) && level().isClientSide()) {
            telegraphStartClient = level().getGameTime();
        }
        if (DATA_SCALE.equals(key)) {
            refreshDimensions();
        }
    }

    // ---------------------------------------------------------------- 毎 tick

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level && isAlive()) {
            if (appliedGeneration != EnemyDefs.loader().generation()) {
                applyDefinition();
            }
            mirrorHealth();
            if (busyTicks > 0) {
                busyTicks--;
            }
            if (telegraphLeft > 0 && --telegraphLeft == 0) {
                clearTelegraph();
            }
            if (tickCount % 10 == 0) {
                selectTarget(level);
                checkLeash();
            }
            if (isReturning()) {
                tickReturn();
            }
        }
    }

    /**
     * 追うのをやめて戻るか（11月22日に作り直し）：戦い始めた所から離れすぎて、しばらく戦っていない（打たれも打ちもしない）とき、
     * または追う相手を見失ったとき。戦っている間は、ノックバックで押し出されても戻らない。
     */
    private void checkLeash() {
        if (isReturning() || combatAnchor == null || !usesLeash()) {
            return;
        }
        long quiet = level().getGameTime() - lastCombatTime;
        switch (leashDecision(combatAnchor.distanceToSqr(position()), leashRange(), quiet, getTarget() != null)) {
            case LEASH_RETURN -> startReturn();
            // 戦い始めた所のそばで見失った：歩いて戻らずに、その場で戻り終える
            case LEASH_FINISH_HERE -> finishReturn();
            default -> {
            }
        }
    }

    public static final int LEASH_STAY = 0;
    public static final int LEASH_RETURN = 1;
    public static final int LEASH_FINISH_HERE = 2;

    /**
     * 戻るかの決まり。{@code farSqr} は戦い始めた所からの距離の2乗、{@code quiet} は最後に戦って（打たれて・打って）からの tick。
     * 離れすぎ（{@code leash} より遠く）で5秒、ひどく離れすぎ（2倍より遠く）で2秒、相手を見失って3秒、戦っていなければ戻る。
     */
    public static int leashDecision(double farSqr, double leash, long quiet, boolean hasTarget) {
        boolean tooFar = farSqr > leash * leash && quiet > LEASH_QUIET_TICKS;
        boolean wayTooFar = farSqr > 4.0 * leash * leash && quiet > LEASH_FAR_QUIET_TICKS;
        boolean lost = !hasTarget && quiet > LOST_QUIET_TICKS;
        if (!tooFar && !wayTooFar && !lost) {
            return LEASH_STAY;
        }
        return !tooFar && !wayTooFar && farSqr < 9.0 ? LEASH_FINISH_HERE : LEASH_RETURN;
    }

    /** 追うのをやめて、戦い始めた所へ戻り始める（戻る間も攻撃は当たる）。 */
    public void startReturn() {
        setReturning(true);
        returnTicks = 0;
        threat.clear();
        setTarget(null);
        clearTelegraph();
        clearBusy();
    }

    /** 追うのをやめて戻る距離（戦い始めた所から。config の {@code enemyLeashRange}）。 */
    protected double leashRange() {
        return ServerConfig.ENEMY_LEASH_RANGE.get();
    }

    /** 戻る仕組みを使うか（ボスは使わない。部屋が戦いを終わらせる）。 */
    protected boolean usesLeash() {
        return true;
    }

    /** 戦った（打たれた・打った）。押し出されても、戦っている間は戻らない。 */
    public void noteCombat() {
        lastCombatTime = level().getGameTime();
    }

    private void tickReturn() {
        Vec3 to = combatAnchor != null ? combatAnchor : Vec3.atBottomCenterOf(home());
        getNavigation().moveTo(to.x, to.y, to.z, 1.4);
        if (to.distanceToSqr(position()) < 4.0 || ++returnTicks > RETURN_GIVE_UP_TICKS) {
            finishReturn();
        }
    }

    /** 戻り終えた：HPを満タンにして、戦いの記録を消す（打たれずに戻れたときだけ）。 */
    private void finishReturn() {
        setReturning(false);
        setEnemyHp(getEnemyMaxHp());
        contributions.clear();
        threat.clear();
        combatAnchor = null;
        returnTicks = 0;
        getNavigation().stop();
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (isReturning() && target != null) {
            return;
        }
        if (target != null && combatAnchor == null && !level().isClientSide()) {
            combatAnchor = position();
        }
        super.setTarget(target);
    }

    // ---------------------------------------------------------------- 倒れたとき

    @Override
    protected void dropFromLootTable(DamageSource source, boolean causedByPlayer) {
        // ドロップは、攻撃を当てた人それぞれに個別に配る
        if (level() instanceof ServerLevel) {
            DropService.onEnemyDeath(this, source);
        }
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (deathTime >= DEATH_TICKS && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    // ---------------------------------------------------------------- 保存

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CompoundTag own = new CompoundTag();
        own.putFloat("hp", entityData.get(DATA_HP));
        own.putFloat("maxHp", entityData.get(DATA_MAX_HP));
        if (home != null) {
            own.put("home", NbtUtils.writeBlockPos(home));
        }
        if (variant != null) {
            own.putString("variant", variant.toString());
        }
        tag.put("Amemaru", own);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        CompoundTag own = tag.getCompound("Amemaru");
        if (own.contains("maxHp")) {
            entityData.set(DATA_MAX_HP, own.getFloat("maxHp"));
            entityData.set(DATA_HP, own.getFloat("hp"));
        }
        if (own.contains("home")) {
            home = NbtUtils.readBlockPos(own.getCompound("home"));
        }
        variant = own.contains("variant") ? ResourceLocation.tryParse(own.getString("variant")) : null;
        appliedGeneration = -1;
    }

    // ---------------------------------------------------------------- GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String prefix = "animation." + assetName() + ".";
        animIdle = RawAnimation.begin().thenLoop(prefix + "idle");
        animWalk = RawAnimation.begin().thenLoop(prefix + "walk");
        animDeath = RawAnimation.begin().thenPlayAndHold(prefix + "death");
        controllers.add(new AnimationController<>(this, "main", 4, this::mainAnimation));
        AnimationController<AmemaruMob> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
        action.triggerableAnim("attack", RawAnimation.begin().thenPlay(prefix + "attack"));
        for (String skill : actionNames()) {
            action.triggerableAnim(skill, RawAnimation.begin().thenPlay(prefix + skill));
        }
        controllers.add(action);
    }

    /** 「攻撃」のほかに使う技のアニメーションの名前（{@code skill_charge} など）。 */
    protected String[] actionNames() {
        return new String[0];
    }

    private PlayState mainAnimation(AnimationState<AmemaruMob> state) {
        if (isDeadOrDying()) {
            return state.setAndContinue(animDeath);
        }
        return state.setAndContinue(state.isMoving() ? animWalk : animIdle);
    }

    /** 技のアニメーションを流す（サーバーから。まわりのクライアントに届く）。 */
    public void playAction(String name) {
        triggerAnim("action", name);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** 倒れたときの粒（{@code broadcastEntityEvent(60)} の代わりに使う敵もある）。 */
    protected void poof(ServerLevel level) {
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2.0, getZ(), 12,
                getBbWidth() / 2.0, getBbHeight() / 3.0, getBbWidth() / 2.0, 0.02);
    }

    /** 向きを、その場所の方へすぐに変える。 */
    public void faceTowards(Vec3 point) {
        double dx = point.x - getX();
        double dz = point.z - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }
}

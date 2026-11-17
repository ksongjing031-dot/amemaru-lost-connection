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
 *   <li>生まれた場所から離れすぎると、追うのをやめて戻り、HPが満タンになる（戻る間は攻撃が当たらない）</li>
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

    private static final EntityDataAccessor<Float> DATA_HP = SynchedEntityData.defineId(AmemaruMob.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MAX_HP = SynchedEntityData.defineId(AmemaruMob.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_TELEGRAPH = SynchedEntityData.defineId(AmemaruMob.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TELEGRAPH_TICKS = SynchedEntityData.defineId(AmemaruMob.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_TELEGRAPH_YAW = SynchedEntityData.defineId(AmemaruMob.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TELEGRAPH_SIZE = SynchedEntityData.defineId(AmemaruMob.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_RETURNING = SynchedEntityData.defineId(AmemaruMob.class, EntityDataSerializers.BOOLEAN);

    /** 倒れてから消えるまで（死ぬアニメーションの長さ）。 */
    private static final int DEATH_TICKS = 30;
    /** 敵視の表から外すまで、攻撃されていない時間。 */
    private static final int FORGET_TICKS = 20 * 20;

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
    private final Map<UUID, Double> threat = new LinkedHashMap<>();
    private final Map<UUID, Double> contributions = new HashMap<>();
    private long lastHurtTick = Long.MIN_VALUE / 4;
    private int busyTicks;
    private int telegraphLeft;
    private final Map<String, Long> cooldowns = new HashMap<>();

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
        entityData.define(DATA_RETURNING, false);
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
        return def == null ? 0.0 : def.attack() * ServerConfig.ENEMY_ATTACK_MULTIPLIER.get();
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
        double max = def.hp() * ServerConfig.ENEMY_HP_MULTIPLIER.get();
        entityData.set(DATA_MAX_HP, (float) max);
        entityData.set(DATA_HP, (float) Math.max(1.0, max * Math.max(0.0, Math.min(1.0, ratio))));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(def.moveSpeed());
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(def.followRange());
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

    /** ダメージを受けたとき（プレイヤーなら、敵視と貢献を足す）。 */
    public void onDamagedBy(@Nullable Entity attacker, double amount) {
        lastHurtTick = level().getGameTime();
        if (attacker instanceof ServerPlayer player && !player.isCreative() && !player.isSpectator()) {
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

    /** 生まれた場所から離れすぎたら、追うのをやめて戻る。 */
    private void checkLeash() {
        if (isReturning()) {
            return;
        }
        double leash = ServerConfig.ENEMY_LEASH_RANGE.get();
        if (home().distToCenterSqr(position()) > leash * leash) {
            setReturning(true);
            threat.clear();
            setTarget(null);
            clearTelegraph();
            clearBusy();
        }
    }

    private void tickReturn() {
        BlockPos pos = home();
        getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.4);
        setEnemyHp(getEnemyHp() + getEnemyMaxHp() * 0.05);
        if (pos.distToCenterSqr(position()) < 4.0 || tickCount % 200 == 0 && pos.distToCenterSqr(position()) < 16.0) {
            setReturning(false);
            setEnemyHp(getEnemyMaxHp());
            contributions.clear();
            getNavigation().stop();
        }
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (isReturning() && target != null) {
            return;
        }
        super.setTarget(target);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // 戻っている途中は、プレイヤーの攻撃が当たらない（/kill は効く）
        if (isReturning() && source.getEntity() instanceof Player) {
            return true;
        }
        return super.isInvulnerableTo(source);
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

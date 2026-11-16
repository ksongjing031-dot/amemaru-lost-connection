package jp.amemaru.lostconnection.combat;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.core.registry.ModDamageTypes;
import jp.amemaru.lostconnection.entity.TrainingDummy;
import jp.amemaru.lostconnection.job.GaugeService;
import jp.amemaru.lostconnection.job.PassiveService;
import jp.amemaru.lostconnection.job.SkillContext;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.player.BuffState;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.player.VitalsService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * ダメージの受け渡し（Phase 0-B 3章 combat）。すべてサーバーで計算する。
 *
 * <ul>
 *   <li>スキル：{@link DamageCalculator} で計算した独自のダメージを、相手の独自のHPから引く（バニラの「傷ついた」演出・ノックバックはそのまま使う）。</li>
 *   <li>近接攻撃（左クリック）：攻撃のステータス × 係数 × 攻撃のたまり具合で、独自のダメージにする。</li>
 *   <li>魔物の攻撃を受けたとき：バニラのダメージ × 倍率を、防御・強化（城壁の構え・不落の城・鉄壁）で減らし、盾結びで肩代わりし、
 *       不屈の誓いでHPが1より下がらないようにする。落下などの環境のダメージは、最大HPに対する割合のまま。</li>
 *   <li>プレイヤーどうしの攻撃は、configで許さない限り無効（協力のゲームのため）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class CombatService {
    /** 盾結びで肩代わりできる距離。 */
    private static final double BOND_RANGE = 32.0;
    /** 正面とみなす角度の半分（60度 → 正面120度）。 */
    private static final double FRONT_DOT = Math.cos(Math.toRadians(60.0));

    /** いま与えている途中のスキルのダメージ（{@link LivingDamageEvent} で受け取る）。 */
    private static final class PendingHit {
        final LivingEntity target;
        final double amount;
        boolean applied;

        PendingHit(LivingEntity target, double amount) {
            this.target = target;
            this.amount = amount;
        }
    }

    @Nullable
    private static PendingHit pending;

    private CombatService() {
    }

    // ---------------------------------------------------------------- 与える

    /**
     * スキルのダメージを与える。
     *
     * @param power  威力（ステータス × 係数 × 強化の倍率）
     * @param direct 直接当たったもの（弾）。null なら使った人
     * @return 与えたダメージ（与えられなかったときは -1）
     */
    public static double dealSkillDamage(SkillContext ctx, LivingEntity target, double power, @Nullable Entity direct) {
        DamageCalculator.Result result = DamageCalculator.compute(power, ctx.stat(StatType.CRIT), ctx.stat(StatType.CRIT_DAMAGE),
                TargetVitals.defense(target), ServerConfig.DAMAGE_VARIANCE.get(), ctx.random());
        Entity owner = ctx.owner();
        DamageSource source = ModDamageTypes.skill(ctx.level, direct != null ? direct : owner, owner);
        return applyDamage(target, result.amount(), result.crit(), source);
    }

    /**
     * 決まった量の独自のダメージを与える（バニラの「傷ついた」演出・ノックバック・倒したときの記録はバニラのまま）。
     *
     * @return 与えたダメージ（与えられなかったときは -1）
     */
    public static double applyDamage(LivingEntity target, double amount, boolean crit, DamageSource source) {
        if (amount <= 0 || !target.isAlive()) {
            return -1;
        }
        PendingHit hit = new PendingHit(target, amount);
        PendingHit previous = pending;
        pending = hit;
        try {
            target.invulnerableTime = 0;
            float placeholder = (float) Math.max(0.01, amount / TargetVitals.vanillaMultiplier());
            target.hurt(source, placeholder);
        } finally {
            pending = previous;
            // スキルとほかの攻撃が、無敵時間で打ち消し合わないようにする
            target.invulnerableTime = 0;
        }
        if (!hit.applied) {
            return -1;
        }
        CombatTextS2C.send(target, amount, crit ? CombatTextS2C.Kind.CRIT : CombatTextS2C.Kind.DAMAGE);
        return amount;
    }

    // ---------------------------------------------------------------- イベント

    /** プレイヤーどうしの攻撃を止める（configで許していなければ）。 */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide() || ServerConfig.ALLOW_PVP.get()) {
            return;
        }
        if (event.getEntity() instanceof Player victim && event.getSource().getEntity() instanceof Player attacker && attacker != victim) {
            event.setCanceled(true);
        }
    }

    /** ダメージの量を、独自のHPの決まりに合わせて決める（防具などの計算がすべて終わった後）。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        PendingHit hit = pending;
        if (hit != null && hit.target == target) {
            resolveOnTarget(event, target, hit.amount);
            hit.applied = true;
            return;
        }
        DamageSource source = event.getSource();
        if (target instanceof ServerPlayer player) {
            onPlayerDamaged(event, player, source);
            return;
        }
        if (source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker) {
            onMelee(event, attacker, target);
            return;
        }
        if (target instanceof TrainingDummy && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // 訓練人形は、プレイヤーの攻撃とスキルでだけ傷つく（/kill では消える）
            event.setAmount(0.0F);
        }
    }

    /** 独自のダメージを、相手の種類に合わせて引く。 */
    private static void resolveOnTarget(LivingDamageEvent event, LivingEntity target, double amount) {
        if (target instanceof TrainingDummy dummy) {
            dummy.applyCustomDamage(amount);
            event.setAmount(0.0F);
        } else {
            event.setAmount((float) (amount / TargetVitals.vanillaMultiplier()));
        }
    }

    /** 近接攻撃（左クリック）。 */
    private static void onMelee(LivingDamageEvent event, ServerPlayer attacker, LivingEntity target) {
        Optional<PlayerData> found = PlayerDataCapability.get(attacker);
        if (found.isEmpty() || !found.get().isStatsReady()) {
            return;
        }
        PlayerData data = found.get();
        float strength = data.getLastAttackStrength();
        double power = data.getStats().get(StatType.ATTACK) * ServerConfig.BASIC_ATTACK_COEFFICIENT.get()
                * (0.2 + 0.8 * strength * strength);
        DamageCalculator.Result result = DamageCalculator.compute(power, data.getStats().get(StatType.CRIT),
                data.getStats().get(StatType.CRIT_DAMAGE), TargetVitals.defense(target), ServerConfig.DAMAGE_VARIANCE.get(),
                attacker.getRandom());
        resolveOnTarget(event, target, result.amount());
        data.markCombat(attacker.level().getGameTime());
        CombatTextS2C.send(target, result.amount(), result.crit() ? CombatTextS2C.Kind.CRIT : CombatTextS2C.Kind.DAMAGE);
    }

    /** プレイヤーがダメージを受けたとき。 */
    private static void onPlayerDamaged(LivingDamageEvent event, ServerPlayer player, DamageSource source) {
        if (source.is(ModDamageTypes.BOND_SHARE)) {
            // 盾結びの肩代わり：量は計算済み
            return;
        }
        Optional<PlayerData> found = PlayerDataCapability.get(player);
        if (found.isEmpty() || !found.get().isStatsReady()) {
            return;
        }
        PlayerData data = found.get();
        double maxHp = data.getStats().maxHp();
        float vanillaMax = player.getMaxHealth();
        if (maxHp <= 0 || vanillaMax <= 0) {
            return;
        }
        long now = player.level().getGameTime();
        Entity attacker = source.getEntity();
        boolean fromEnemy = attacker instanceof LivingEntity && !(attacker instanceof Player);
        double damage;
        if (fromEnemy) {
            damage = event.getAmount() * ServerConfig.VANILLA_MOB_DAMAGE_MULTIPLIER.get()
                    * DamageCalculator.mitigation(data.getStats().get(StatType.DEFENSE));
            damage = reduce(player, data, damage, attacker, now, true);
            damage = shareWithBond(player, data, damage, attacker, now);
        } else {
            // 落下・溺れ・溶岩などは、最大HPに対する割合のまま（Phase 1）
            damage = event.getAmount() / vanillaMax * maxHp;
        }
        if (data.getBuffs().unyielding(now)) {
            damage = Math.min(damage, Math.max(0.0, data.getHp() - 1.0));
        }
        damage = Math.max(0.0, damage);
        event.setAmount((float) (damage / maxHp * vanillaMax));
        if (damage > 0) {
            data.markCombat(now);
            if (fromEnemy) {
                GaugeService.onDamageTaken(player, data, damage, now);
            }
            CombatTextS2C.send(player, damage, CombatTextS2C.Kind.TAKEN);
        }
    }

    /** 不落の城・城壁の構え（正面だけ）・鉄壁で、受けるダメージを減らす。 */
    private static double reduce(ServerPlayer player, PlayerData data, double damage, @Nullable Entity attacker, long now,
                                 boolean useDirection) {
        BuffState buffs = data.getBuffs();
        double result = damage;
        if (buffs.fortress(now)) {
            result *= 1.0 - Math.min(95.0, buffs.fortressReduction) / 100.0;
        }
        if (useDirection && buffs.rampart(now) && attacker != null && isInFront(player, attacker)) {
            result *= 1.0 - Math.min(95.0, buffs.rampartReduction) / 100.0;
        }
        double ironWall = PassiveService.ironWallReduction(player, data);
        if (ironWall > 0) {
            result *= 1.0 - ironWall / 100.0;
        }
        return result;
    }

    /** 相手が、プレイヤーの正面（120度）にいるか。 */
    public static boolean isInFront(Player player, Entity attacker) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
        Vec3 to = new Vec3(attacker.getX() - player.getX(), 0.0, attacker.getZ() - player.getZ());
        if (flatLook.lengthSqr() < 1.0E-6 || to.lengthSqr() < 1.0E-6) {
            return true;
        }
        return flatLook.normalize().dot(to.normalize()) >= FRONT_DOT;
    }

    /** 盾結び：守ってくれているナイトが、決まった割合を代わりに受ける。 */
    private static double shareWithBond(ServerPlayer player, PlayerData data, double damage, @Nullable Entity attacker, long now) {
        BuffState buffs = data.getBuffs();
        if (!buffs.bonded(now) || buffs.bondGiver == null || buffs.bondGiver.equals(player.getUUID())) {
            return damage;
        }
        ServerPlayer knight = player.server.getPlayerList().getPlayer(buffs.bondGiver);
        if (knight == null || !knight.isAlive() || knight.level() != player.level() || knight.distanceToSqr(player) > BOND_RANGE * BOND_RANGE) {
            return damage;
        }
        double shared = damage * buffs.bondShare / 100.0;
        if (shared <= 0) {
            return damage;
        }
        takeBondShare(knight, shared, attacker, now);
        return damage - shared;
    }

    /** ナイトが肩代わりしたダメージを受ける（ナイト自身の強化で減り、不屈の誓いも効く）。 */
    private static void takeBondShare(ServerPlayer knight, double amount, @Nullable Entity attacker, long now) {
        Optional<PlayerData> found = PlayerDataCapability.get(knight);
        if (found.isEmpty() || !found.get().isStatsReady()) {
            return;
        }
        PlayerData data = found.get();
        double damage = reduce(knight, data, amount, attacker, now, false);
        if (data.getBuffs().unyielding(now)) {
            damage = Math.min(damage, Math.max(0.0, data.getHp() - 1.0));
        }
        if (damage <= 0) {
            return;
        }
        data.markCombat(now);
        GaugeService.onBondAbsorbed(knight, data, damage, now);
        CombatTextS2C.send(knight, damage, CombatTextS2C.Kind.SHARED);
        knight.level().playSound(null, knight.getX(), knight.getY(), knight.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS,
                0.5F, 1.3F);
        if (damage >= data.getHp()) {
            knight.invulnerableTime = 0;
            knight.hurt(ModDamageTypes.bondShare(knight.level(), attacker), Float.MAX_VALUE);
            return;
        }
        data.setHp(data.getHp() - damage);
        VitalsService.pushVanillaHealth(knight, data);
    }
}

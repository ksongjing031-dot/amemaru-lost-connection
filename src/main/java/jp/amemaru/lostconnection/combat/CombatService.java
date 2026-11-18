package jp.amemaru.lostconnection.combat;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.core.registry.ModDamageTypes;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.entity.TrainingDummy;
import jp.amemaru.lostconnection.equipment.GearEffects;
import jp.amemaru.lostconnection.equipment.GearService;
import jp.amemaru.lostconnection.job.GaugeService;
import jp.amemaru.lostconnection.job.PassiveService;
import jp.amemaru.lostconnection.job.ReactiveSkills;
import jp.amemaru.lostconnection.job.SkillContext;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.network.s2c.StatusFxS2C;
import jp.amemaru.lostconnection.player.BuffState;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.SpeedService;
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
import java.util.UUID;

/**
 * ダメージの受け渡し（Phase 0-B 3章 combat）。すべてサーバーで計算する。
 *
 * <ul>
 *   <li>スキル：{@link DamageCalculator} で計算した独自のダメージを、相手の独自のHPから引く（バニラの「傷ついた」演出・ノックバックはそのまま使う）。</li>
 *   <li>近接攻撃（左クリック）：攻撃のステータス × 係数 × 攻撃のたまり具合で、独自のダメージにする。</li>
 *   <li>魔物の攻撃を受けたとき：バニラのダメージ × 倍率を、防御・挑発の極意・強化（城壁の構え・不落の城・誓いの円陣・鉄壁）で減らし、
 *       盾結びで肩代わりし、光の帳の障壁で吸い、不屈の誓いでHPが1より下がらないようにする。落下などの環境のダメージは、
 *       最大HPに対する割合のまま（障壁は吸う）。</li>
 *   <li>受け流しの構えの間の、正面からの魔物の攻撃と、凍結した魔物の攻撃は、攻撃ごと取り消す。</li>
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
        Entity owner = ctx.owner();
        // 装備の特殊効果（HPの少ない敵に強い など）
        if (owner instanceof ServerPlayer player) {
            power *= GearEffects.outgoingMultiplier(player, target, true);
        }
        DamageCalculator.Result result = DamageCalculator.compute(power, ctx.stat(StatType.CRIT), ctx.stat(StatType.CRIT_DAMAGE),
                TargetVitals.defense(target), ServerConfig.DAMAGE_VARIANCE.get(), ctx.random());
        DamageSource source = ModDamageTypes.skill(ctx.level, direct != null ? direct : owner, owner);
        double dealt = applyDamage(target, result.amount(), result.crit(), source);
        if (dealt > 0 && owner instanceof ServerPlayer player) {
            GearEffects.afterHit(player, target, dealt, true, source);
        }
        return dealt;
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

    /** 攻撃そのものを止める：プレイヤーどうし（configで許していなければ）・凍結した魔物・受け流された攻撃。 */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        Entity attackerEntity = source.getEntity();
        // 凍結した魔物は、近くで殴れない
        if (attackerEntity instanceof LivingEntity attacker && source.getDirectEntity() == attacker && StatusService.frozen(attacker)) {
            event.setCanceled(true);
            return;
        }
        if (!ServerConfig.ALLOW_PVP.get() && victim instanceof Player && attackerEntity instanceof Player attacker && attacker != victim) {
            event.setCanceled(true);
            return;
        }
        // 受け流し：正面からの魔物の攻撃を無効にして反撃する
        if (victim instanceof ServerPlayer player && attackerEntity instanceof LivingEntity attacker && !(attacker instanceof Player)
                && ReactiveSkills.tryParry(player, attacker, player.level().getGameTime())) {
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
            if (target instanceof AmemaruMob mob) {
                mob.onDamagedBy(event.getSource().getEntity(), hit.amount);
            }
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
            return;
        }
        if (target instanceof AmemaruMob mob) {
            // このModの敵が、落下・溶岩・バニラの攻撃などで傷ついた：最大の体力に対する割合を、独自のHPから引く
            double amount = event.getAmount() / Math.max(1.0F, mob.getMaxHealth()) * mob.getEnemyMaxHp();
            resolveOnTarget(event, target, amount);
            mob.onDamagedBy(source.getEntity(), amount);
        }
    }

    /** 独自のダメージを、相手の種類に合わせて引く。 */
    private static void resolveOnTarget(LivingDamageEvent event, LivingEntity target, double amount) {
        if (target instanceof TrainingDummy dummy) {
            dummy.applyCustomDamage(amount);
            event.setAmount(0.0F);
        } else if (target instanceof AmemaruMob mob) {
            // このModの敵：独自のHPを減らし、バニラの体力をその割合まで下げる（0になればバニラのとおりに倒れる）
            double remaining = mob.getEnemyHp() - amount;
            mob.setEnemyHp(Math.max(0.0, remaining));
            float wanted = remaining <= 0 ? 0.0F
                    : (float) Math.max(0.01, remaining / mob.getEnemyMaxHp() * mob.getMaxHealth());
            event.setAmount(Math.max(0.0F, mob.getHealth() - wanted));
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
        power *= PassiveService.meleeMultiplier(data, target);
        power *= GearEffects.outgoingMultiplier(attacker, target, false);
        DamageCalculator.Result result = DamageCalculator.compute(power, data.getStats().get(StatType.CRIT),
                data.getStats().get(StatType.CRIT_DAMAGE), TargetVitals.defense(target), ServerConfig.DAMAGE_VARIANCE.get(),
                attacker.getRandom());
        resolveOnTarget(event, target, result.amount());
        data.markCombat(attacker.level().getGameTime());
        CombatTextS2C.send(target, result.amount(), result.crit() ? CombatTextS2C.Kind.CRIT : CombatTextS2C.Kind.DAMAGE);
        if (target instanceof AmemaruMob mob) {
            mob.onDamagedBy(attacker, result.amount());
        }
        // 武器の耐久と、当たった後の特殊効果（追加ダメージは tick の終わりに与える）
        GearService.wearWeapon(attacker, false);
        GearEffects.afterHit(attacker, target, result.amount(), false, event.getSource());
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
            // このModの敵の技は「敵の攻撃 × 技の倍率」、バニラの魔物は「バニラの値 × config の倍率」
            double raw = EnemyCombat.takePending(player);
            if (raw < 0) {
                raw = event.getAmount() * ServerConfig.VANILLA_MOB_DAMAGE_MULTIPLIER.get();
            }
            // 素早さの回避：かわせば、受けるダメージは0（防具の耐久も減らない）
            if (SpeedService.dodges(player, data)) {
                event.setAmount(0.0F);
                data.markCombat(now);
                CombatTextS2C.send(player, 0, CombatTextS2C.Kind.DODGE);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE,
                        SoundSource.PLAYERS, 0.8F, 1.4F);
                return;
            }
            damage = raw * DamageCalculator.mitigation(data.getStats().get(StatType.DEFENSE))
                    * GearEffects.incomingMultiplier(player, data);
            damage = tauntMastery(player, damage, attacker, now);
            damage = reduce(player, data, damage, attacker, now, true);
            damage = shareWithBond(player, data, damage, attacker, now);
        } else {
            // 落下・溺れ・溶岩などは、最大HPに対する割合のまま（Phase 1）
            damage = event.getAmount() / vanillaMax * maxHp;
        }
        damage = absorbBarrier(player, data, damage, now);
        if (data.getBuffs().unyielding(now)) {
            damage = Math.min(damage, Math.max(0.0, data.getHp() - 1.0));
        }
        damage = Math.max(0.0, damage);
        event.setAmount((float) (damage / maxHp * vanillaMax));
        if (damage > 0) {
            data.markCombat(now);
            if (fromEnemy) {
                GaugeService.onDamageTaken(player, data, damage, now);
                GearService.wearArmor(player);
            }
            CombatTextS2C.send(player, damage, CombatTextS2C.Kind.TAKEN);
        }
    }

    /** 挑発の極意：ナイトが挑発している魔物の攻撃は弱まる（誰が受けても）。 */
    private static double tauntMastery(ServerPlayer player, double damage, @Nullable Entity attacker, long now) {
        if (attacker == null) {
            return damage;
        }
        UUID taunter = TauntService.taunterOf(attacker, now);
        if (taunter == null) {
            return damage;
        }
        ServerPlayer knight = player.server.getPlayerList().getPlayer(taunter);
        PlayerData knightData = knight == null ? null : PlayerDataCapability.get(knight).orElse(null);
        double reduction = knightData == null ? 0.0 : PassiveService.tauntMasteryReduction(knightData);
        return reduction > 0 ? damage * (1.0 - reduction / 100.0) : damage;
    }

    /** 光の帳：障壁が残っていれば、先に障壁で吸う。 */
    private static double absorbBarrier(ServerPlayer player, PlayerData data, double damage, long now) {
        BuffState buffs = data.getBuffs();
        if (damage <= 0 || !buffs.barrier(now)) {
            return damage;
        }
        double absorbed = Math.min(buffs.barrierAmount, damage);
        buffs.barrierAmount -= absorbed;
        data.markDirty(PlayerData.SYNC_VITALS);
        CombatTextS2C.send(player, absorbed, CombatTextS2C.Kind.ABSORBED);
        if (buffs.barrierAmount <= 1.0E-6) {
            buffs.clearBarrier();
            player.removeEffect(ModEffects.BARRIER.get());
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK,
                    SoundSource.PLAYERS, 1.0F, 1.2F);
            StatusService.sync(player, StatusFxS2C.Event.BARRIER_BROKEN);
        } else {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_HIT,
                    SoundSource.PLAYERS, 0.8F, 1.4F);
        }
        return damage - absorbed;
    }

    /** 不落の城・誓いの円陣・城壁の構え（正面だけ）・鉄壁で、受けるダメージを減らす。 */
    private static double reduce(ServerPlayer player, PlayerData data, double damage, @Nullable Entity attacker, long now,
                                 boolean useDirection) {
        BuffState buffs = data.getBuffs();
        double result = damage;
        if (buffs.fortress(now)) {
            result *= 1.0 - Math.min(95.0, buffs.fortressReduction) / 100.0;
        }
        if (buffs.warded(now)) {
            result *= 1.0 - Math.min(95.0, buffs.wardReduction) / 100.0;
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
        damage = absorbBarrier(knight, data, damage, now);
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

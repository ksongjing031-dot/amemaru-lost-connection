package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.combat.StatusService;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.job.def.PassiveDef;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.network.s2c.StatusFxS2C;
import jp.amemaru.lostconnection.party.PartyService;
import jp.amemaru.lostconnection.player.BuffState;
import jp.amemaru.lostconnection.player.DownHandler;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.player.VitalsService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 回復の仕組み（Phase 6 その2：ヒーラーとモンク）。
 *
 * <ul>
 *   <li>回復量 ＝ ステータス × 係数 × 倍率 ×（1 ＋ 回復力（アクセサリー）＋ 慈愛 ＋ つながりの癒し）。</li>
 *   <li>余韻：癒した量の一部を、5秒かけてさらに癒す。溢れる癒し：満タンを超えた分の一部を障壁にする（最大HPの15%・8秒）。
 *       満ちる心：ほかの仲間を癒すと、癒した量の一部だけ自分のMPが戻る。</li>
 *   <li>回復し続ける（regen）・命の糸：1秒ごとに癒す。命の糸は、HPが境目を切ると一度だけ大きく癒して切れる。</li>
 *   <li>ほどく（cleanse）：悪い状態（バニラの「害のある効果」とこのModの敵の状態）を消す。教会の衰弱（デスペナルティ）は消さない。
 *       ロスト状態（Phase 8）も、悪い状態として消える。</li>
 *   <li>呼び戻す（revive）：ダウンしている味方を起こし、HPを決まった割合にする。</li>
 * </ul>
 */
public final class HealService {
    /** 余韻の時間（tick）。 */
    private static final int AFTERCARE_TICKS = 100;
    /** 溢れる癒しの障壁：最大HPに対する上限と、時間。 */
    private static final double OVERHEAL_CAP = 0.15;
    private static final int OVERHEAL_TICKS = 160;
    /** つながりの癒しで数える仲間の上限。 */
    private static final int BOND_HEAL_MAX = 4;

    private HealService() {
    }

    /**
     * スキルで味方を癒す。
     *
     * @param base ステータス × 係数 × 倍率（回復力などをかける前）
     * @return 実際に戻った量
     */
    /** 「祈り手の鈴」の防御の上がる時間（5秒）。 */
    public static final int HEAL_WARD_TICKS = 100;

    public static double healBySkill(@Nullable ServerPlayer caster, ServerPlayer target, double base) {
        PlayerData casterData = caster == null ? null : PlayerDataCapability.get(caster).orElse(null);
        double amount = base * healMultiplier(caster, casterData);
        PlayerData data = PlayerDataCapability.get(target).orElse(null);
        if (data == null || !data.isStatsReady() || amount <= 0 || !target.isAlive() || DownHandler.isDown(target)) {
            return 0.0;
        }
        double healed = heal(target, data, amount);
        double overheal = Math.max(0.0, amount - healed);
        if (caster != null && casterData != null) {
            // 4階のレア「祈り手の鈴」：回復した味方の防御が、少しのあいだ上がる（11月24日）
            double ward = jp.amemaru.lostconnection.equipment.GearEffects.healWard(caster, casterData);
            if (ward > 0) {
                long now = target.level().getGameTime();
                jp.amemaru.lostconnection.player.BuffState buffs = data.getBuffs();
                boolean active = buffs.defenseUp(now) > 0;
                buffs.defenseUp = Math.max(active ? buffs.defenseUp : 0.0, ward);
                buffs.defenseUpUntil = Math.max(active ? buffs.defenseUpUntil : 0L, now + HEAL_WARD_TICKS);
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(jp.amemaru.lostconnection.core.registry.ModEffects.BLESSING.get(),
                        (int) (buffs.defenseUpUntil - now), 0, false, false, true), caster);
                if (!active) {
                    jp.amemaru.lostconnection.player.LevelService.recompute(target, data);
                }
            }
        }
        if (casterData != null) {
            double aftercare = PassiveService.value(casterData, PassiveDef.Type.AFTERCARE);
            if (aftercare > 0 && healed > 0) {
                applyRegen(target, healed * aftercare / 100.0 / (AFTERCARE_TICKS / 20.0), AFTERCARE_TICKS, caster);
            }
            double overflow = PassiveService.value(casterData, PassiveDef.Type.OVERHEAL_BARRIER);
            if (overflow > 0 && overheal > 0) {
                double barrier = Math.min(data.getStats().maxHp() * OVERHEAL_CAP, overheal * overflow / 100.0);
                SkillExecutor.applyBarrier(target, barrier, OVERHEAL_TICKS, target.level().getGameTime(), 0.0, null);
            }
            // 覚醒「聖域」（ヒーラー。11月27日）：回復した仲間の与えるダメージが、しばらく上がる
            if (healed > 0 || overheal > 0) {
                AwakeningService.afterHealAlly(caster, casterData, target, data);
            }
            double heartMp = PassiveService.value(casterData, PassiveDef.Type.HEAL_MP);
            if (heartMp > 0 && healed > 0 && target != caster && casterData.isStatsReady()) {
                double before = casterData.getMp();
                casterData.setMp(before + healed * heartMp / 100.0);
                double restored = casterData.getMp() - before;
                if (restored >= 1.0) {
                    CombatTextS2C.send(caster, restored, CombatTextS2C.Kind.MP);
                }
            }
        }
        return healed;
    }

    /** 回復力（アクセサリー）・慈愛・つながりの癒しをまとめた倍率。 */
    public static double healMultiplier(@Nullable ServerPlayer caster, @Nullable PlayerData data) {
        if (caster == null || data == null || !data.isStatsReady()) {
            return 1.0;
        }
        double bonus = data.getStats().get(StatType.HEAL_POWER) + PassiveService.value(data, PassiveDef.Type.HEAL_POWER)
                + AwakeningService.value(data, "heal");  // 覚醒（11月27日）
        PassiveService.Active bond = PassiveService.find(data, PassiveDef.Type.BOND_HEAL);
        if (bond != null) {
            int members = 0;
            double range = bond.radius();
            for (ServerPlayer member : PartyService.onlineMembers(caster)) {
                if (member != caster && member.isAlive() && member.level() == caster.level()
                        && member.distanceToSqr(caster) <= range * range) {
                    members++;
                }
            }
            bonus += bond.value() * Math.min(BOND_HEAL_MAX, members);
        }
        return 1.0 + Math.max(-90.0, bonus) / 100.0;
    }

    /** 回復そのもの（ボーナスなし）。戻った量を返す。 */
    public static double heal(ServerPlayer target, PlayerData data, double amount) {
        if (!data.isStatsReady() || amount <= 0 || target.isDeadOrDying() || DownHandler.isDown(target)) {
            return 0.0;
        }
        double before = data.getHp();
        data.setHp(before + amount);
        double healed = data.getHp() - before;
        if (healed > 0) {
            VitalsService.pushVanillaHealth(target, data);
            CombatTextS2C.send(target, healed, CombatTextS2C.Kind.HEAL);
        }
        return healed;
    }

    /** 最大HPの割合だけ癒す（気の巡り・静の呼吸）。 */
    public static void healSelfPercent(ServerPlayer player, PlayerData data, double percent) {
        if (!data.isStatsReady() || percent <= 0) {
            return;
        }
        heal(player, data, data.getStats().maxHp() * percent / 100.0);
    }

    /** 回復し続ける（毎秒 {@code perSecond}）。強い方を残し、時間はのばす。 */
    public static void applyRegen(ServerPlayer target, double perSecond, int ticks, @Nullable ServerPlayer giver) {
        if (perSecond <= 0 || ticks <= 0) {
            return;
        }
        PlayerDataCapability.get(target).ifPresent(data -> {
            long now = target.level().getGameTime();
            BuffState buffs = data.getBuffs();
            boolean active = buffs.regenerating(now);
            buffs.regenPerSecond = active ? Math.max(buffs.regenPerSecond, perSecond) : perSecond;
            buffs.regenUntil = Math.max(active ? buffs.regenUntil : 0L, now + ticks);
            buffs.regenGiver = giver == null ? null : giver.getUUID();
            target.addEffect(new MobEffectInstance(ModEffects.RENEWAL.get(), (int) (buffs.regenUntil - now), 0, false, false, true));
            if (!active) {
                StatusService.sync(target, StatusFxS2C.Event.NONE);
            }
        });
    }

    /** 毎 tick（{@code SkillService}）：回復し続ける・命の糸・静の呼吸を、1秒ごとに効かせる。 */
    public static void tick(ServerPlayer player, PlayerData data, long now) {
        if (player.tickCount % 20 != 0 || !data.isStatsReady() || DownHandler.isDown(player)) {
            return;
        }
        BuffState buffs = data.getBuffs();
        if (buffs.regenerating(now)) {
            heal(player, data, buffs.regenPerSecond);
        }
        if (buffs.threaded(now)) {
            heal(player, data, buffs.threadPerSecond);
        }
        double calm = PassiveService.value(data, PassiveDef.Type.CALM_BREATH);
        if (calm > 0 && data.getGauge() >= PassiveService.KI_THRESHOLD && now - data.getLastCombatTick() < 100) {
            healSelfPercent(player, data, calm);
        }
    }

    /** 命の糸：ダメージを受けた後、HPが境目を切っていれば、一度だけ大きく癒して糸が切れる。 */
    public static void checkThread(ServerPlayer player, PlayerData data, long now) {
        BuffState buffs = data.getBuffs();
        if (!buffs.threaded(now) || !data.isStatsReady()) {
            return;
        }
        double maxHp = data.getStats().maxHp();
        if (maxHp <= 0 || data.getHp() > maxHp * buffs.threadThreshold / 100.0) {
            return;
        }
        double burst = buffs.threadBurst;
        buffs.threadGiver = null;
        buffs.threadUntil = 0;
        player.removeEffect(ModEffects.LIFE_THREAD.get());
        heal(player, data, burst);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 1.2F, 1.4F);
        StatusService.sync(player, StatusFxS2C.Event.THREAD_BURST);
    }

    /**
     * 悪い状態をほどく。
     *
     * @param count ほどく数（0ならすべて）
     * @return ほどいた数
     */
    public static int cleanse(ServerPlayer target, int count, @Nullable ServerPlayer caster) {
        List<MobEffect> harmful = new ArrayList<>();
        for (MobEffectInstance instance : target.getActiveEffects()) {
            MobEffect effect = instance.getEffect();
            if (effect.getCategory() == MobEffectCategory.HARMFUL && effect != ModEffects.WEAKENED.get()) {
                harmful.add(effect);
            }
        }
        int removed = 0;
        for (MobEffect effect : harmful) {
            if (count > 0 && removed >= count) {
                break;
            }
            target.removeEffect(effect);
            removed++;
        }
        PlayerData casterData = caster == null ? null : PlayerDataCapability.get(caster).orElse(null);
        double guard = casterData == null ? 0.0 : PassiveService.value(casterData, PassiveDef.Type.CLEANSE_GUARD);
        if (guard > 0) {
            SkillExecutor.applyBuff(target, jp.amemaru.lostconnection.job.def.BuffKind.PROTECT, 80, guard,
                    target.level().getGameTime(), caster);
        }
        StatusService.sync(target, StatusFxS2C.Event.CLEANSED);
        return removed;
    }

    /** ダウンしている味方を起こし、HPを最大の {@code hpPercent}% にする。起こせたら true。 */
    public static boolean revive(ServerPlayer downed, double hpPercent, @Nullable ServerPlayer reviver) {
        PlayerData data = PlayerDataCapability.get(downed).orElse(null);
        if (data == null || !data.down().isDown()) {
            return false;
        }
        DownHandler.revive(downed, data, reviver);
        if (data.isStatsReady()) {
            double maxHp = data.getStats().maxHp();
            double wanted = Math.max(1.0, Math.floor(maxHp * hpPercent / 100.0));
            if (wanted > data.getHp()) {
                data.setHp(wanted);
                VitalsService.pushVanillaHealth(downed, data);
            }
        }
        CombatTextS2C.send(downed, 0, CombatTextS2C.Kind.REVIVE);
        StatusService.sync(downed, StatusFxS2C.Event.REVIVED);
        return true;
    }
}

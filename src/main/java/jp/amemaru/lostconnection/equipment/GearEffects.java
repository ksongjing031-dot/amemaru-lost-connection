package jp.amemaru.lostconnection.equipment;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryDef;
import jp.amemaru.lostconnection.accessory.AccessoryDefs;
import jp.amemaru.lostconnection.accessory.AccessoryService;
import jp.amemaru.lostconnection.combat.CombatService;
import jp.amemaru.lostconnection.combat.TargetVitals;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.network.s2c.GearFxS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.VitalsService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 装備とアクセサリーの固有の特殊効果（{@link SpecialEffect}）の処理。
 *
 * <ul>
 *   <li>与えるダメージ：処刑（HPの少ない敵に+%）を、ダメージを決めるときにかける</li>
 *   <li>当たった後：追加ダメージ（確率）・吸収（確率でHP回復）</li>
 *   <li>受けるダメージ：被ダメージ減少</li>
 *   <li>5秒ごと：HP自動回復</li>
 *   <li>移動の速さ：バニラの移動速度の変更（品ごとの属性）</li>
 * </ul>
 *
 * <p>条件（必要レベル・職業）を満たさない品と、壊れた品の効果は効かない。追加ダメージで、また追加ダメージは出ない。
 * 追加ダメージは、ダメージの計算の途中で別のダメージを重ねないよう、その tick の終わりにまとめて与える。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class GearEffects {
    /** tick の終わりに与える追加ダメージ。 */
    private record PendingProc(LivingEntity target, double amount, DamageSource source) {
    }

    private static final List<PendingProc> PENDING = new ArrayList<>();
    private static final UUID[] SPEED_UUIDS = {
            UUID.fromString("7f1e4a02-55c1-4b9a-8d4f-0a1d3e9b6c01"),
            UUID.fromString("7f1e4a02-55c1-4b9a-8d4f-0a1d3e9b6c02"),
            UUID.fromString("7f1e4a02-55c1-4b9a-8d4f-0a1d3e9b6c03"),
            UUID.fromString("7f1e4a02-55c1-4b9a-8d4f-0a1d3e9b6c04"),
            UUID.fromString("7f1e4a02-55c1-4b9a-8d4f-0a1d3e9b6c05"),
            UUID.fromString("7f1e4a02-55c1-4b9a-8d4f-0a1d3e9b6c06")};
    /** 5秒ごとの自動回復。 */
    private static final int REGEN_INTERVAL = 100;

    /** 追加ダメージを与えている途中（追加ダメージで、また追加ダメージが出ないように）。 */
    private static boolean procActive;
    /** 11月28日：処刑の環・認証の盾を、同じ相手に続けて出しすぎないための、最後に出した時（エンティティの番号 → tick）。 */
    private static final java.util.Map<Integer, Long> EXECUTE_SHOWN = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Long> WARD_SHOWN = new java.util.HashMap<>();
    /** 認証（第七の門鍵）が働いている、付けている人。 */
    private static final java.util.Set<UUID> WARD_ACTIVE = new java.util.HashSet<>();
    /** 処刑の環・認証の盾を出す間（tick）。 */
    private static final int FX_GAP = 15;

    private GearEffects() {
    }

    /** いま効いている特殊効果（装備とアクセサリー）。 */
    public static List<SpecialEffect> active(ServerPlayer player, PlayerData data) {
        List<SpecialEffect> list = new ArrayList<>();
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
        for (ItemStack stack : GearService.activeGear(player)) {
            EquipmentDef def = snapshot.get(stack);
            if (def != null && !def.effects().isEmpty() && GearService.usable(data, def) && !GearData.isBroken(stack, def)) {
                list.addAll(def.effects());
            }
        }
        for (ItemStack stack : AccessoryService.equipped(player)) {
            AccessoryDef def = AccessoryDefs.get(stack);
            if (def != null) {
                list.addAll(def.effects());
            }
        }
        // 11月25日：防具を一式そろえたときの効果
        list.addAll(GearService.setEffects(player, data));
        return list;
    }

    // ---------------------------------------------------------------- 与える

    /** 与えるダメージにかける倍率（処刑）。 */
    public static double outgoingMultiplier(ServerPlayer attacker, LivingEntity target, boolean skill) {
        PlayerData data = PlayerDataCapability.get(attacker).orElse(null);
        if (data == null) {
            return 1.0;
        }
        double multiplier = 1.0;
        for (SpecialEffect effect : active(attacker, data)) {
            if (effect.type() == SpecialEffect.Type.ALLY_POWER && effect.source().matches(skill)) {
                // 味方が近いほど強い（5階のレア。5人まで）
                multiplier *= 1.0 + effect.percent() / 100.0 * Math.min(5, alliesNear(attacker, effect.threshold()));
            }
            if (effect.type() == SpecialEffect.Type.EXECUTE && effect.source().matches(skill)) {
                double maxHp = TargetVitals.maxHp(target);
                if (maxHp > 0 && TargetVitals.hp(target) / maxHp * 100.0 <= effect.threshold()) {
                    multiplier *= 1.0 + effect.percent() / 100.0;
                    // 11月28日：処刑が働いた（弱った相手の上に、翠の環）
                    if (shouldShow(EXECUTE_SHOWN, target, attacker.level().getGameTime())) {
                        fx(attacker, GearFxS2C.Kind.EXECUTE, attacker, target, procColor(attacker, effect.type()));
                    }
                }
            }
        }
        return multiplier;
    }

    /** 攻撃が当たった後（追加ダメージ・吸収）。 */
    public static void afterHit(ServerPlayer attacker, LivingEntity target, double dealt, boolean skill, DamageSource source) {
        if (procActive || dealt <= 0) {
            return;
        }
        PlayerData data = PlayerDataCapability.get(attacker).orElse(null);
        if (data == null) {
            return;
        }
        for (SpecialEffect effect : active(attacker, data)) {
            if (!effect.source().matches(skill) || attacker.getRandom().nextDouble() * 100.0 >= effect.chance()) {
                continue;
            }
            if (effect.type() == SpecialEffect.Type.EXTRA_DAMAGE && target.isAlive()) {
                PENDING.add(new PendingProc(target, dealt * effect.percent() / 100.0, source));
                // 11月28日：追加ダメージが出た（相手に、武器の色の衝撃の輪と火花。低い打撃の音）
                fx(attacker, GearFxS2C.Kind.EXTRA, attacker, target, procColor(attacker, effect.type()));
                attacker.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                        net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_KNOCKBACK, net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 0.7F);
            } else if (effect.type() == SpecialEffect.Type.DOUBLE_HIT && target.isAlive()) {
                // 鏡の写しが、もう一度当たる（7階のボスのレア「鏡氷の短剣」）。11月28日：写しの刃の弧と、ガラスのかけら。ガラスの鳴る音
                PENDING.add(new PendingProc(target, dealt * effect.percent() / 100.0, source));
                fx(attacker, GearFxS2C.Kind.DOUBLE, attacker, target, procColor(attacker, effect.type()));
                attacker.level().playSound(null, target.getX(), target.getY(), target.getZ(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.7F);
            } else if (effect.type() == SpecialEffect.Type.HEAL_ON_HIT) {
                if (heal(attacker, data, dealt * effect.percent() / 100.0) > 0) {
                    // 11月28日：吸収（相手から自分へ、緑の光が流れこむ）
                    fx(attacker, GearFxS2C.Kind.DRAIN, attacker, target, procColor(attacker, effect.type()));
                }
            }
        }
    }

    /** たまった追加ダメージを与える（tick の終わり）。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        List<PendingProc> procs = new ArrayList<>(PENDING);
        PENDING.clear();
        procActive = true;
        try {
            for (PendingProc proc : procs) {
                if (proc.target().isAlive()) {
                    CombatService.applyDamage(proc.target(), proc.amount(), false, proc.source());
                }
            }
        } finally {
            procActive = false;
        }
    }

    // ---------------------------------------------------------------- 受ける

    /** 装備の特殊効果のロスト耐性（%）の合計（7階の名のある魔物のレアのお守り）。上限は {@code LostService} が決める。 */
    public static double lostResist(ServerPlayer player, PlayerData data) {
        double sum = 0.0;
        for (SpecialEffect effect : active(player, data)) {
            if (effect.type() == SpecialEffect.Type.LOST_RESIST) {
                sum += effect.percent();
            }
        }
        return sum;
    }

    /** 受けるダメージ（敵から）にかける倍率（被ダメージ減少と、近くの味方の「第七の門鍵」）。 */
    public static double incomingMultiplier(ServerPlayer player, PlayerData data) {
        double multiplier = 1.0;
        for (SpecialEffect effect : active(player, data)) {
            if (effect.type() == SpecialEffect.Type.DAMAGE_REDUCTION) {
                multiplier *= Math.max(0.0, 1.0 - effect.percent() / 100.0);
            }
        }
        double ward = partyWard(player);
        if (ward > 0) {
            multiplier *= Math.max(0.0, 1.0 - ward / 100.0);
            // 11月28日：認証が受けたダメージを減らした（その人に、翠の六角の盾が一瞬光る）
            if (shouldShow(WARD_SHOWN, player, player.level().getGameTime())) {
                fx(player, GearFxS2C.Kind.WARD, null, player, WARD_COLOR);
            }
        }
        return multiplier;
    }

    /** 自分のまわり（{@code radius} ブロック）の、ほかのプレイヤー（味方）の数。決闘の相手は数えない。 */
    public static int alliesNear(ServerPlayer player, double radius) {
        int count = 0;
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other != player && other.isAlive() && !other.isSpectator() && other.distanceToSqr(player) <= radius * radius
                    && !jp.amemaru.lostconnection.player.DownHandler.isDown(other)
                    && !jp.amemaru.lostconnection.social.DuelService.areOpponents(player, other)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 「第七の門鍵」（5階のユニーク。11月24日）：近くに、門鍵を付けていて、まわりに味方が3人以上いる人がいれば、受けるダメージの
     * 減る割合（%。いなければ 0。いくつあっても1つ分）。
     */
    public static double partyWard(ServerPlayer player) {
        double best = 0.0;
        for (ServerPlayer wearer : player.serverLevel().players()) {
            if (!wearer.isAlive() || wearer.isSpectator()) {
                continue;
            }
            PlayerData data = PlayerDataCapability.get(wearer).orElse(null);
            if (data == null) {
                continue;
            }
            for (SpecialEffect effect : active(wearer, data)) {
                if (effect.type() != SpecialEffect.Type.PARTY_WARD || effect.percent() <= best) {
                    continue;
                }
                double radius = effect.threshold();
                if (wearer.distanceToSqr(player) <= radius * radius && alliesNear(wearer, radius) >= 3) {
                    best = effect.percent();
                }
            }
        }
        return best;
    }

    /**
     * 「近衛の誓盾」（9階のボスのレア。11月28日）：{@code player} が受けるダメージを肩代わりする人（まわりで、誓盾を持っていて、
     * いちばん多く肩代わりする人。いなければ null）と、その割合（%）。自分自身・倒れている人・決闘の相手は肩代わりしない。
     */
    @javax.annotation.Nullable
    public static java.util.Map.Entry<ServerPlayer, Double> oathGuard(ServerPlayer player) {
        ServerPlayer best = null;
        double bestPercent = 0.0;
        for (ServerPlayer wearer : player.serverLevel().players()) {
            if (wearer == player || !wearer.isAlive() || wearer.isSpectator()
                    || jp.amemaru.lostconnection.player.DownHandler.isDown(wearer)
                    || jp.amemaru.lostconnection.social.DuelService.areOpponents(player, wearer)) {
                continue;
            }
            PlayerData data = PlayerDataCapability.get(wearer).orElse(null);
            if (data == null) {
                continue;
            }
            for (SpecialEffect effect : active(wearer, data)) {
                if (effect.type() != SpecialEffect.Type.OATH_GUARD || effect.percent() <= bestPercent) {
                    continue;
                }
                double radius = effect.threshold();
                if (wearer.distanceToSqr(player) <= radius * radius) {
                    best = wearer;
                    bestPercent = effect.percent();
                }
            }
        }
        return best == null ? null : java.util.Map.entry(best, bestPercent);
    }

    /** 回復の守り（4階のレア「祈り手の鈴」）：使い手の、回復したときの防御の上がり（%。なければ 0）。 */
    public static double healWard(ServerPlayer caster, PlayerData data) {
        double best = 0.0;
        for (SpecialEffect effect : active(caster, data)) {
            if (effect.type() == SpecialEffect.Type.HEAL_WARD) {
                best = Math.max(best, effect.percent());
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- 時間

    /** 5秒ごとのHP自動回復。 */
    public static void tick(ServerPlayer player, PlayerData data) {
        if (player.tickCount % 20 == 0 && data.isStatsReady()) {
            tickWard(player, data);
        }
        if (player.tickCount % REGEN_INTERVAL != 0 || !data.isStatsReady() || player.isDeadOrDying()) {
            return;
        }
        double percent = 0.0;
        for (SpecialEffect effect : active(player, data)) {
            if (effect.type() == SpecialEffect.Type.REGEN) {
                percent += effect.percent();
            }
        }
        if (percent > 0) {
            heal(player, data, data.getStats().maxHp() * percent / 100.0);
        }
    }

    /** HPを戻す（戻った量を返す）。 */
    private static double heal(ServerPlayer player, PlayerData data, double amount) {
        if (!data.isStatsReady() || amount <= 0 || player.isDeadOrDying()) {
            return 0.0;
        }
        double before = data.getHp();
        data.setHp(before + amount);
        double healed = data.getHp() - before;
        if (healed > 0) {
            VitalsService.pushVanillaHealth(player, data);
            CombatTextS2C.send(player, healed, CombatTextS2C.Kind.HEAL);
        }
        return healed;
    }

    // ---------------------------------------------------------------- 見える演出（11月28日）

    /** 認証（第七の門鍵）の色。 */
    public static final int WARD_COLOR = 0x5FE0A8;

    /** 特殊効果の演出の色：固有スキルのある武器を持っていれば、その武器の色。なければ効果ごとの色。 */
    static int procColor(ServerPlayer player, SpecialEffect.Type type) {
        UniqueSkillDef unique = UniqueSkills.forStack(player.getMainHandItem());
        if (unique != null) {
            return unique.color();
        }
        return switch (type) {
            case EXTRA_DAMAGE -> 0xFFB347;
            case DOUBLE_HIT -> 0xD8F0FF;
            case HEAL_ON_HIT -> 0x8CFF9E;
            case EXECUTE -> 0x5FE0A8;
            default -> 0xFFE08A;
        };
    }

    private static void fx(ServerPlayer near, GearFxS2C.Kind kind, @javax.annotation.Nullable LivingEntity source, LivingEntity target, int color) {
        new GearFxS2C(kind, source == null ? -1 : source.getId(), target.getId(), color).sendNear(near.serverLevel(), target.position());
    }

    /** 同じ相手に続けて出しすぎないか（出してよければ、今の時を覚える）。 */
    private static boolean shouldShow(java.util.Map<Integer, Long> shown, LivingEntity target, long now) {
        Long last = shown.get(target.getId());
        if (last != null && now - last < FX_GAP && now >= last) {
            return false;
        }
        if (shown.size() > 512) {
            shown.clear();
        }
        shown.put(target.getId(), now);
        return true;
    }

    /**
     * 認証（第七の門鍵）が働いているか（1秒ごと）：付けている人のまわりに味方が3人以上いれば、付けている人と、その範囲の味方に
     * 「認証」の状態（アイコン）をつける。働きはじめた・やんだときは、付けている人の足もとの翠の環を出す・消す（{@link GearFxS2C}）。
     * 受けるダメージを減らす働きそのものは {@link #partyWard} のまま。
     */
    private static void tickWard(ServerPlayer player, PlayerData data) {
        double radius = 0.0;
        for (SpecialEffect effect : active(player, data)) {
            if (effect.type() == SpecialEffect.Type.PARTY_WARD) {
                radius = Math.max(radius, effect.threshold());
            }
        }
        boolean on = radius > 0 && player.isAlive() && alliesNear(player, radius) >= 3;
        boolean was = WARD_ACTIVE.contains(player.getUUID());
        if (on) {
            WARD_ACTIVE.add(player.getUUID());
            net.minecraft.world.effect.MobEffect ward = jp.amemaru.lostconnection.core.registry.ModEffects.GATE_WARD.get();
            for (ServerPlayer other : player.serverLevel().players()) {
                if (other.isAlive() && !other.isSpectator() && other.distanceToSqr(player) <= radius * radius
                        && !jp.amemaru.lostconnection.social.DuelService.areOpponents(player, other)) {
                    other.addEffect(new net.minecraft.world.effect.MobEffectInstance(ward, 30, 0, true, false, true));
                }
            }
        } else {
            WARD_ACTIVE.remove(player.getUUID());
        }
        if (on != was) {
            fx(player, on ? GearFxS2C.Kind.WARD_ON : GearFxS2C.Kind.WARD_OFF, player, player, WARD_COLOR);
            if (on) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        jp.amemaru.lostconnection.core.registry.ModSounds.get("unique.ward"), net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);
            }
        }
    }

    /** 認証が働いているか（テストで使う）。 */
    public static boolean wardActive(ServerPlayer player) {
        return WARD_ACTIVE.contains(player.getUUID());
    }

    // ---------------------------------------------------------------- 移動の速さ

    /** 品の属性に、移動の速さの特殊効果を足す（壊れた品には足さない）。 */
    public static Multimap<Attribute, AttributeModifier> withMoveSpeed(Multimap<Attribute, AttributeModifier> base,
                                                                      ItemStack stack, EquipmentSlot slot) {
        EquipmentDef def = GearItems.defs().get(stack);
        if (def == null || def.effects().isEmpty() || GearData.isBroken(stack, def)) {
            return base;
        }
        double percent = 0.0;
        for (SpecialEffect effect : def.effects()) {
            if (effect.type() == SpecialEffect.Type.MOVE_SPEED) {
                percent += effect.percent();
            }
        }
        if (percent <= 0) {
            return base;
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.putAll(base);
        builder.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(SPEED_UUIDS[slot.ordinal() % SPEED_UUIDS.length],
                "amemaru:gear_speed", percent / 100.0, AttributeModifier.Operation.MULTIPLY_BASE));
        return builder.build();
    }
}

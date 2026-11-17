package jp.amemaru.lostconnection.combat;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.network.s2c.StatusFxS2C;
import jp.amemaru.lostconnection.player.BuffState;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 敵に重なる状態（Phase 0-A 19章「スキルの数と、戦闘の深み」【提案】）。中身はバニラの効果（MobEffect）で持ち、
 * 重なった数は効果の強さ（amplifier ＝ 数 − 1）で表す。
 *
 * <ul>
 *   <li>凍え：1つにつき移動 −10%。5つ重なると凍結（{@link #addChill}）。</li>
 *   <li>凍結：動けず、攻撃もできない。氷柱などで「砕く」と大きなダメージになり、凍結は解ける（{@link #shatter}）。
 *       凍結が解けてから3秒は、もう一度は凍結しない（凍えは4つまで）【補完】。</li>
 *   <li>感電：次の雷鎖が、跳ぶ回数を増やして強く流れる（{@link #consumeShock}）。</li>
 *   <li>星痕：星の印（最大5つ）。星爆で一度に弾ける（{@link #consumeBrands}）。</li>
 *   <li>防御崩し：防御 −25%（{@link #ARMOR_BREAK_DEFENSE}）。</li>
 * </ul>
 *
 * <p>状態が変わるたびに、その敵が見えている人へ {@link StatusFxS2C} を送り、氷の塊・頭の上の星などを描かせる。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class StatusService {
    public static final int MAX_CHILL = 5;
    public static final int MAX_BRAND = 5;
    /** 防御崩しで下がる防御の割合。 */
    public static final double ARMOR_BREAK_DEFENSE = 0.25;
    /** 凍結が解けてから、もう一度凍結しない時間（tick）。 */
    private static final int FREEZE_IMMUNITY_AFTER = 60;

    /** 魔物 → この時刻までは凍結しない。 */
    private static final Map<UUID, Long> FREEZE_IMMUNE = new HashMap<>();

    private StatusService() {
    }

    // ---------------------------------------------------------------- 読む

    public static int chill(LivingEntity entity) {
        return stacks(entity, ModEffects.FROST.get());
    }

    public static boolean frozen(LivingEntity entity) {
        return entity.hasEffect(ModEffects.FROZEN.get());
    }

    /** 凍えか凍結の状態か。 */
    public static boolean chilledOrFrozen(LivingEntity entity) {
        return chill(entity) > 0 || frozen(entity);
    }

    public static boolean shocked(LivingEntity entity) {
        return entity.hasEffect(ModEffects.SHOCK.get());
    }

    public static int brands(LivingEntity entity) {
        return stacks(entity, ModEffects.STAR_BRAND.get());
    }

    public static boolean armorBroken(LivingEntity entity) {
        return entity.hasEffect(ModEffects.ARMOR_BREAK.get());
    }

    public static boolean staggered(LivingEntity entity) {
        return entity.hasEffect(ModEffects.STAGGER.get());
    }

    /** IDで指した状態にかかっているか（スキルの「この状態の相手に強い」に使う）。 */
    public static boolean has(LivingEntity entity, @Nullable ResourceLocation id) {
        MobEffect effect = id == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(id);
        return effect != null && entity.hasEffect(effect);
    }

    private static int stacks(LivingEntity entity, MobEffect effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? 0 : instance.getAmplifier() + 1;
    }

    private static int remaining(LivingEntity entity, MobEffect effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? 0 : instance.getDuration();
    }

    // ---------------------------------------------------------------- かける

    /**
     * 凍えを重ねる。
     *
     * @param stacks      重ねる数
     * @param ticks       凍えが続く時間（すでに長く残っていれば、そちらのまま）
     * @param cap         この効果で重ねられる上限（氷結陣の陣は4：陣だけでは凍結しない）
     * @param freezeTicks 5つになったときの凍結の時間（0なら凍結しない）
     * @param freezeIf    凍えがこの数以上なら、重ねずにすぐ凍結させる（0なら使わない。凍てつく波動）
     * @return 凍結させたか
     */
    public static boolean addChill(LivingEntity target, int stacks, int ticks, int cap, int freezeTicks, int freezeIf,
                                   @Nullable Entity source) {
        if (!target.isAlive() || frozen(target)) {
            return false;
        }
        long now = target.level().getGameTime();
        boolean immune = freezeImmune(target, now);
        int current = chill(target);
        if (!immune && freezeTicks > 0 && freezeIf > 0 && current >= freezeIf) {
            freeze(target, freezeTicks, source);
            return true;
        }
        int next = Math.max(current, Math.min(current + Math.max(0, stacks), Math.min(cap, MAX_CHILL)));
        if (!immune && freezeTicks > 0 && next >= MAX_CHILL) {
            freeze(target, freezeTicks, source);
            return true;
        }
        next = Math.min(next, immune ? MAX_CHILL - 1 : MAX_CHILL);
        if (next <= 0) {
            return false;
        }
        int duration = Math.max(ticks, remaining(target, ModEffects.FROST.get()));
        set(target, ModEffects.FROST.get(), next - 1, duration, source);
        sync(target, StatusFxS2C.Event.NONE);
        return false;
    }

    /** 凍結させる（凍えは消える）。 */
    public static void freeze(LivingEntity target, int ticks, @Nullable Entity source) {
        long now = target.level().getGameTime();
        target.removeEffect(ModEffects.FROST.get());
        set(target, ModEffects.FROZEN.get(), 0, ticks, source);
        FREEZE_IMMUNE.put(target.getUUID(), now + ticks + FREEZE_IMMUNITY_AFTER);
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS,
                1.0F, 0.6F);
        CombatTextS2C.send(target, 0, CombatTextS2C.Kind.FROZEN);
        sync(target, StatusFxS2C.Event.FROZE);
    }

    /** 凍結を砕く（解く）。砕けたら true。 */
    public static boolean shatter(LivingEntity target) {
        if (!frozen(target)) {
            return false;
        }
        target.removeEffect(ModEffects.FROZEN.get());
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS,
                1.2F, 0.8F);
        CombatTextS2C.send(target, 0, CombatTextS2C.Kind.SHATTER);
        sync(target, StatusFxS2C.Event.SHATTERED);
        return true;
    }

    private static boolean freezeImmune(LivingEntity target, long now) {
        Long until = FREEZE_IMMUNE.get(target.getUUID());
        return until != null && now < until;
    }

    public static void shock(LivingEntity target, int ticks, @Nullable Entity source) {
        if (!target.isAlive()) {
            return;
        }
        set(target, ModEffects.SHOCK.get(), 0, Math.max(ticks, remaining(target, ModEffects.SHOCK.get())), source);
        sync(target, StatusFxS2C.Event.NONE);
    }

    /** 感電を使う（感電していれば消して true）。 */
    public static boolean consumeShock(LivingEntity target) {
        if (!shocked(target)) {
            return false;
        }
        target.removeEffect(ModEffects.SHOCK.get());
        sync(target, StatusFxS2C.Event.DISCHARGED);
        return true;
    }

    public static void addBrand(LivingEntity target, int stacks, int ticks, @Nullable Entity source) {
        if (!target.isAlive() || stacks <= 0) {
            return;
        }
        int next = Math.min(MAX_BRAND, brands(target) + stacks);
        set(target, ModEffects.STAR_BRAND.get(), next - 1, ticks, source);
        sync(target, StatusFxS2C.Event.NONE);
    }

    /** 星痕を弾けさせる（消す）。弾けた数を返す。 */
    public static int consumeBrands(LivingEntity target) {
        int count = brands(target);
        if (count > 0) {
            target.removeEffect(ModEffects.STAR_BRAND.get());
            sync(target, StatusFxS2C.Event.DETONATED);
        }
        return count;
    }

    public static void armorBreak(LivingEntity target, int ticks, @Nullable Entity source) {
        if (!target.isAlive()) {
            return;
        }
        boolean fresh = !armorBroken(target);
        set(target, ModEffects.ARMOR_BREAK.get(), 0, Math.max(ticks, remaining(target, ModEffects.ARMOR_BREAK.get())), source);
        if (fresh) {
            target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS,
                    0.8F, 0.9F);
        }
        sync(target, fresh ? StatusFxS2C.Event.ARMOR_BROKEN : StatusFxS2C.Event.NONE);
    }

    /** IDで指した状態を消す（凍結なら砕く）。 */
    public static void consume(LivingEntity target, @Nullable ResourceLocation id) {
        if (id == null) {
            return;
        }
        if (id.equals(ModEffects.FROZEN.getId())) {
            shatter(target);
            return;
        }
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id);
        if (effect != null && target.hasEffect(effect)) {
            target.removeEffect(effect);
            sync(target, StatusFxS2C.Event.NONE);
        }
    }

    /** 効果を決まった強さ・時間でかけ直す（バニラの「強い方を残す」決まりを通さないため、いったん消す）。 */
    private static void set(LivingEntity target, MobEffect effect, int amplifier, int ticks, @Nullable Entity source) {
        if (target.hasEffect(effect)) {
            target.removeEffect(effect);
        }
        target.addEffect(new MobEffectInstance(effect, Math.max(1, ticks), Math.max(0, amplifier), false, false, true), source);
    }

    // ---------------------------------------------------------------- 見た目を送る

    /** 今の状態を、その相手が見えている人（本人も）に送る。 */
    public static void sync(LivingEntity target, StatusFxS2C.Event event) {
        if (target.level().isClientSide()) {
            return;
        }
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target), snapshot(target, event));
    }

    /** 今の状態の一覧。 */
    public static StatusFxS2C snapshot(LivingEntity target, StatusFxS2C.Event event) {
        List<StatusFxS2C.Entry> entries = new ArrayList<>();
        add(entries, target, StatusFxS2C.Kind.CHILL, ModEffects.FROST.get());
        add(entries, target, StatusFxS2C.Kind.FROZEN, ModEffects.FROZEN.get());
        add(entries, target, StatusFxS2C.Kind.SHOCK, ModEffects.SHOCK.get());
        add(entries, target, StatusFxS2C.Kind.BRAND, ModEffects.STAR_BRAND.get());
        add(entries, target, StatusFxS2C.Kind.ARMOR_BREAK, ModEffects.ARMOR_BREAK.get());
        add(entries, target, StatusFxS2C.Kind.STAGGER, ModEffects.STAGGER.get());
        if (target instanceof ServerPlayer player) {
            PlayerData data = PlayerDataCapability.get(player).orElse(null);
            if (data != null) {
                long now = player.level().getGameTime();
                BuffState buffs = data.getBuffs();
                if (buffs.barrier(now)) {
                    int level = buffs.barrierMax <= 0 ? 5 : (int) Math.ceil(Math.min(1.0, buffs.barrierAmount / buffs.barrierMax) * 5.0);
                    entries.add(new StatusFxS2C.Entry(StatusFxS2C.Kind.BARRIER, Math.max(1, level), (int) (buffs.barrierUntil - now)));
                }
                if (buffs.warded(now)) {
                    entries.add(new StatusFxS2C.Entry(StatusFxS2C.Kind.WARD, 1, (int) (buffs.wardUntil - now)));
                }
            }
            add(entries, target, StatusFxS2C.Kind.PARRY, ModEffects.PARRY.get());
            add(entries, target, StatusFxS2C.Kind.RAMPART, ModEffects.RAMPART.get());
            add(entries, target, StatusFxS2C.Kind.UNYIELDING, ModEffects.UNYIELDING.get());
            add(entries, target, StatusFxS2C.Kind.FORTRESS, ModEffects.FORTRESS.get());
            add(entries, target, StatusFxS2C.Kind.SHIELD_BOND, ModEffects.SHIELD_BOND.get());
        }
        return new StatusFxS2C(target.getId(), event, entries);
    }

    private static void add(List<StatusFxS2C.Entry> entries, LivingEntity target, StatusFxS2C.Kind kind, MobEffect effect) {
        MobEffectInstance instance = target.getEffect(effect);
        if (instance != null && instance.getDuration() > 0) {
            entries.add(new StatusFxS2C.Entry(kind, instance.getAmplifier() + 1, instance.getDuration()));
        }
    }

    /** 新しく見えるようになった相手の状態を、その人に送る。 */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof LivingEntity target) {
            StatusFxS2C message = snapshot(target, StatusFxS2C.Event.NONE);
            if (!message.entries().isEmpty()) {
                ModNetwork.sendTo(viewer, message);
            }
        }
    }

    /** 古い記録を消す（{@code SkillScheduler} から20秒ごと）。 */
    public static void cleanup(long now) {
        Iterator<Map.Entry<UUID, Long>> it = FREEZE_IMMUNE.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < now - 20) {
                it.remove();
            }
        }
    }

    public static void clear() {
        FREEZE_IMMUNE.clear();
    }
}

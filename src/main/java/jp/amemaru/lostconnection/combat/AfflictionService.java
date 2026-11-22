package jp.amemaru.lostconnection.combat;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.job.PassiveService;
import jp.amemaru.lostconnection.job.def.PassiveDef;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.network.s2c.StatusFxS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 敵にかける、数値つきの状態（Phase 6 その2【提案】）。見た目と残り時間はバニラの効果（{@link ModEffects}）で持ち、
 * 強さ（毒のダメージ・標の上がり幅など）はここに持つ（保存しない）。
 *
 * <ul>
 *   <li>毒：重なった数 × 1つあたりのダメージを、毎秒与える（防御で減る。会心はない）。重ねた人の「毒の心得」が効く。
 *       新しくかけると、時間はのび、1つあたりのダメージは強い方を残す。</li>
 *   <li>標：プレイヤーから受けるダメージが上がる（アーチャーの標の矢）。</li>
 *   <li>縫い止め：その場から動けない（攻撃はできる）。ボスにも効くが、時間は半分。</li>
 *   <li>戒め：その敵の攻撃が弱まる（ヒーラーの戒めの鈴）。</li>
 *   <li>煙：その敵の攻撃が外れやすくなる（ローグの煙玉）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class AfflictionService {
    /** 毒の上限（パッシブで増えても、ここまで）。 */
    public static final int POISON_HARD_CAP = 10;

    private static final class Poison {
        final LivingEntity target;
        int stacks;
        double perStack;
        @Nullable
        UUID source;
        long until;
        long nextTick;
        /** 重ねた人の「蝕み」（毒の間、誰から受けるダメージも上がる%）。 */
        double corrode;

        Poison(LivingEntity target) {
            this.target = target;
        }
    }

    /** 値と時刻（標・戒め・煙）。 */
    private record Valued(double value, long until) {
    }

    private static final Map<UUID, Poison> POISONS = new HashMap<>();
    private static final Map<UUID, Valued> MARKS = new HashMap<>();
    private static final Map<UUID, Valued> WEAKENS = new HashMap<>();
    private static final Map<UUID, Valued> BLINDS = new HashMap<>();
    private static final Map<UUID, Double> STAR_GUIDES = new HashMap<>();

    private AfflictionService() {
    }

    // ---------------------------------------------------------------- 毒

    /**
     * 毒を重ねる。
     *
     * @param stacks   重ねる数
     * @param cap      この効果で重ねられる上限（重ねた人の「しみる毒」は呼ぶ側で足す）
     * @param perStack 1つあたり、毎秒のダメージ（ステータス × 係数 × 倍率 × 毒の心得）
     */
    public static void addPoison(LivingEntity target, int stacks, int cap, double perStack, int ticks, @Nullable ServerPlayer source) {
        if (!target.isAlive() || stacks <= 0 || perStack <= 0) {
            return;
        }
        long now = target.level().getGameTime();
        Poison poison = POISONS.computeIfAbsent(target.getUUID(), id -> new Poison(target));
        if (poison.until <= now) {
            poison.stacks = 0;
            poison.perStack = 0;
            poison.nextTick = now + 20;
        }
        int limit = Math.min(POISON_HARD_CAP, Math.max(cap, poison.stacks));
        poison.stacks = Math.min(limit, poison.stacks + stacks);
        poison.perStack = Math.max(poison.perStack, perStack);
        poison.until = Math.max(poison.until, now + ticks);
        if (source != null) {
            poison.source = source.getUUID();
            PlayerData data = PlayerDataCapability.get(source).orElse(null);
            if (data != null) {
                poison.corrode = Math.max(poison.corrode, PassiveService.value(data, PassiveDef.Type.CORRODE));
            }
        }
        setEffect(target, ModEffects.POISON.get(), poison.stacks - 1, (int) (poison.until - now), source);
        StatusService.sync(target, StatusFxS2C.Event.NONE);
    }

    /** 毒の数（かかっていなければ0）。 */
    public static int poisonStacks(LivingEntity target) {
        Poison poison = POISONS.get(target.getUUID());
        return poison == null || poison.until <= target.level().getGameTime() ? 0 : poison.stacks;
    }

    public static boolean poisoned(LivingEntity target) {
        return poisonStacks(target) > 0;
    }

    /** 蝕み：毒の敵が受けるダメージの上がり幅（%）。 */
    public static double corrodeBonus(LivingEntity target) {
        Poison poison = POISONS.get(target.getUUID());
        return poison == null || poison.until <= target.level().getGameTime() ? 0.0 : poison.corrode;
    }

    /** 星の導き：星痕を刻んだ人のパッシブの値（星痕1つあたりの%）を覚える。 */
    public static void noteStarGuide(LivingEntity target, double perBrand) {
        if (perBrand > 0) {
            STAR_GUIDES.merge(target.getUUID(), perBrand, Math::max);
        }
    }

    /** 星の導き：星痕のある敵が受けるダメージの上がり幅（%）。 */
    public static double starGuideBonus(LivingEntity target) {
        Double perBrand = STAR_GUIDES.get(target.getUUID());
        if (perBrand == null) {
            return 0.0;
        }
        int brands = StatusService.brands(target);
        if (brands <= 0) {
            STAR_GUIDES.remove(target.getUUID());
            return 0.0;
        }
        return perBrand * brands;
    }

    /** 毒を消して、消えた数を返す（毒爆）。 */
    public static int consumePoison(LivingEntity target) {
        int stacks = poisonStacks(target);
        POISONS.remove(target.getUUID());
        if (target.hasEffect(ModEffects.POISON.get())) {
            target.removeEffect(ModEffects.POISON.get());
        }
        if (stacks > 0) {
            StatusService.sync(target, StatusFxS2C.Event.POISON_BURST);
        }
        return stacks;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || POISONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        List<Poison> due = new ArrayList<>();
        Iterator<Map.Entry<UUID, Poison>> it = POISONS.entrySet().iterator();
        while (it.hasNext()) {
            Poison poison = it.next().getValue();
            LivingEntity target = poison.target;
            if (!target.isAlive() || target.isRemoved()) {
                it.remove();
                continue;
            }
            long now = target.level().getGameTime();
            if (now >= poison.until) {
                it.remove();
                continue;
            }
            if (now >= poison.nextTick) {
                poison.nextTick = now + 20;
                due.add(poison);
            }
        }
        for (Poison poison : due) {
            LivingEntity target = poison.target;
            ServerPlayer source = poison.source == null ? null : server.getPlayerList().getPlayer(poison.source);
            double power = poison.perStack * poison.stacks;
            CombatService.dealStatusDamage(target, power, source, CombatTextS2C.Kind.POISON);
        }
    }

    /** 毒の連鎖：毒のかかった敵が倒れると、重ねた人のパッシブで、まわりの敵に毒が移る。 */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) {
            return;
        }
        Poison poison = POISONS.remove(dead.getUUID());
        MARKS.remove(dead.getUUID());
        WEAKENS.remove(dead.getUUID());
        BLINDS.remove(dead.getUUID());
        STAR_GUIDES.remove(dead.getUUID());
        if (poison == null || poison.source == null || poison.until <= dead.level().getGameTime() || !(dead.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer source = level.getServer().getPlayerList().getPlayer(poison.source);
        PlayerData data = source == null ? null : PlayerDataCapability.get(source).orElse(null);
        PassiveService.Active contagion = data == null ? null : PassiveService.find(data, PassiveDef.Type.CONTAGION);
        if (contagion == null) {
            return;
        }
        int stacks = (int) Math.round(contagion.value());
        double radius = contagion.radius();
        int cap = 3 + PassiveService.extraPoisonStacks(data);
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(radius),
                entity -> entity != dead && CombatTargets.isEnemy(entity))) {
            addPoison(near, stacks, cap, poison.perStack, 120, source);
        }
    }

    // ---------------------------------------------------------------- 標・戒め・煙・縫い止め

    /** 標をつける（プレイヤーから受けるダメージ +value%）。強い方の値と、長い方の時間を残す。 */
    public static void mark(LivingEntity target, double value, int ticks, @Nullable Entity source) {
        if (!target.isAlive()) {
            return;
        }
        long now = target.level().getGameTime();
        Valued before = MARKS.get(target.getUUID());
        boolean fresh = before == null || before.until <= now;
        double next = fresh ? value : Math.max(value, before.value);
        long until = fresh ? now + ticks : Math.max(before.until, now + ticks);
        MARKS.put(target.getUUID(), new Valued(next, until));
        setEffect(target, ModEffects.HUNTER_MARK.get(), 0, (int) (until - now), source);
        StatusService.sync(target, fresh ? StatusFxS2C.Event.MARKED : StatusFxS2C.Event.NONE);
    }

    /** 標の上がり幅（%。なければ0）。 */
    public static double markBonus(LivingEntity target) {
        return active(MARKS, target);
    }

    public static boolean marked(LivingEntity target) {
        return markBonus(target) > 0;
    }

    /** 戒め（その敵の攻撃 -value%）。 */
    public static void weaken(LivingEntity target, double value, int ticks, @Nullable Entity source) {
        if (!target.isAlive()) {
            return;
        }
        long now = target.level().getGameTime();
        Valued before = WEAKENS.get(target.getUUID());
        boolean fresh = before == null || before.until <= now;
        double next = fresh ? value : Math.max(value, before.value);
        long until = fresh ? now + ticks : Math.max(before.until, now + ticks);
        WEAKENS.put(target.getUUID(), new Valued(next, until));
        setEffect(target, ModEffects.ADMONISHED.get(), 0, (int) (until - now), source);
        StatusService.sync(target, StatusFxS2C.Event.NONE);
    }

    /** 戒めで弱まる割合（%。なければ0）。 */
    public static double weakness(LivingEntity attacker) {
        return active(WEAKENS, attacker);
    }

    /** 煙（その敵の攻撃が value% で外れる）。 */
    public static void blind(LivingEntity target, double value, int ticks, @Nullable Entity source) {
        if (!target.isAlive()) {
            return;
        }
        long now = target.level().getGameTime();
        Valued before = BLINDS.get(target.getUUID());
        boolean fresh = before == null || before.until <= now;
        double next = fresh ? value : Math.max(value, before.value);
        long until = fresh ? now + ticks : Math.max(before.until, now + ticks);
        BLINDS.put(target.getUUID(), new Valued(next, until));
        setEffect(target, ModEffects.BLINDED.get(), 0, (int) (until - now), source);
        StatusService.sync(target, StatusFxS2C.Event.NONE);
    }

    /** 煙で外れる確率（%。なければ0）。 */
    public static double missChance(LivingEntity attacker) {
        return active(BLINDS, attacker);
    }

    /** 縫い止め（動けない。ボスは時間が半分）。 */
    public static void root(LivingEntity target, int ticks, @Nullable Entity source) {
        if (!target.isAlive()) {
            return;
        }
        int duration = CombatTargets.isBoss(target) ? ticks / 2 : ticks;
        if (duration <= 0) {
            return;
        }
        MobEffectInstance current = target.getEffect(ModEffects.ROOTED.get());
        int keep = current == null ? 0 : current.getDuration();
        setEffect(target, ModEffects.ROOTED.get(), 0, Math.max(keep, duration), source);
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        StatusService.sync(target, StatusFxS2C.Event.NONE);
    }

    public static boolean rooted(LivingEntity target) {
        return target.hasEffect(ModEffects.ROOTED.get());
    }

    private static double active(Map<UUID, Valued> map, LivingEntity entity) {
        Valued valued = map.get(entity.getUUID());
        if (valued == null) {
            return 0.0;
        }
        if (valued.until <= entity.level().getGameTime()) {
            map.remove(entity.getUUID());
            return 0.0;
        }
        return valued.value;
    }

    private static void setEffect(LivingEntity target, MobEffect effect, int amplifier, int ticks, @Nullable Entity source) {
        if (target.hasEffect(effect)) {
            target.removeEffect(effect);
        }
        target.addEffect(new MobEffectInstance(effect, Math.max(1, ticks), Math.max(0, amplifier), false, false, true), source);
    }

    /** 毒が新しくかかったときの音。 */
    public static void poisonSound(LivingEntity target) {
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SLIME_SQUISH_SMALL,
                SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    /** 古い記録を消す（20秒ごと）。 */
    public static void cleanup(long now) {
        MARKS.values().removeIf(valued -> valued.until < now - 20);
        WEAKENS.values().removeIf(valued -> valued.until < now - 20);
        BLINDS.values().removeIf(valued -> valued.until < now - 20);
        if (STAR_GUIDES.size() > 512) {
            STAR_GUIDES.clear();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clear();
    }

    public static void clear() {
        POISONS.clear();
        MARKS.clear();
        WEAKENS.clear();
        BLINDS.clear();
        STAR_GUIDES.clear();
    }
}

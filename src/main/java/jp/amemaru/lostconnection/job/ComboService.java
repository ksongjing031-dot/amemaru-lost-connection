package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.core.registry.ModSounds;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.PassiveDef;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 連撃（Phase 6 その2。11月22日のご指示「モンクなどにコンボを用意し、複数のスキルを回すと火力が上がる」【提案】）。
 *
 * <p>モンク（職業の定義に {@code combo} がある職業）：</p>
 * <ul>
 *   <li>【起】のスキルで連撃が始まる（連撃1）。始まった後は、【起】【継】のスキルを「前と違うスキル」で {@code window} 秒以内に
 *       使うと、連撃が1つ増える（{@code max} まで）。増えるたびに気が {@code ki_per_step} たまる。</li>
 *   <li>同じスキルを続けると、【起】なら連撃1から数え直し、【継】なら連撃は消える。時間があいても消える。</li>
 *   <li>連撃1つにつき、モンクのスキルのダメージ +{@code per_step}%（連撃の心得で増える）。</li>
 *   <li>【極】のスキルは、連撃を残らず使って締めくくる：さらに連撃1つにつき +{@code finisher_per_step}%。
 *       気の巡りで、連撃1つにつき最大HPの一部が戻る。極めの一撃で【極】が強まり、連撃が最大なら必ず会心。</li>
 *   <li>天地流転の間は、【極】を使っても連撃が消えない。受け流し・回避の後3秒（流れを読む）は、同じスキルでも連撃がつながる。</li>
 * </ul>
 * <p>ナイトの「連ね斬り」：剛剣の違うスキルを3秒以内に当て続けると、1つごとに +value%（3つまで）。</p>
 */
public final class ComboService {
    /** 連ね斬りの、つなげる時間と上限。 */
    private static final int BLADE_WINDOW = 60;
    private static final int BLADE_MAX = 3;
    /** 流れを読む：受け流し・回避の後、連撃がつながる時間。 */
    private static final int READ_FLOW_TICKS = 60;

    private static final class Chain {
        @Nullable
        ResourceLocation lastSkill;
        long lastTick;
    }

    private static final class Blade {
        @Nullable
        ResourceLocation lastSkill;
        long lastTick;
        int stacks;
    }

    private static final Map<UUID, Chain> CHAINS = new HashMap<>();
    private static final Map<UUID, Blade> BLADES = new HashMap<>();
    private static final Map<UUID, Long> READ_FLOW = new HashMap<>();

    private ComboService() {
    }

    /** 連撃の結果：ダメージの倍率と、必ず会心にするか。 */
    public record Step(double multiplier, boolean forceCrit) {
        static final Step NONE = new Step(1.0, false);
    }

    /**
     * スキルを使うとき（{@code SkillService}）。連撃を進め、このスキルのダメージの倍率を返す。
     */
    public static Step onSkillUse(ServerPlayer player, PlayerData data, SkillDef skill, long now) {
        JobDef job = SkillRegistry.get().job(data.getCombatJob());
        JobDef.ComboDef combo = job == null ? null : job.combo();
        if (combo == null || !skill.isActive()) {
            return Step.NONE;
        }
        Chain chain = CHAINS.computeIfAbsent(player.getUUID(), id -> new Chain());
        int count = data.getCombo() > 0 && now <= data.getComboUntil() ? data.getCombo() : 0;
        if (count == 0) {
            chain.lastSkill = null;
        }
        boolean readFlow = readingFlow(player.getUUID(), now);
        boolean different = chain.lastSkill == null || !chain.lastSkill.equals(skill.id()) || readFlow;
        if (readFlow) {
            READ_FLOW.remove(player.getUUID());
        }
        double perStep = combo.perStep() + PassiveService.value(data, PassiveDef.Type.CHAIN_MASTERY);
        long until = now + combo.windowTicks();
        double multiplier = 1.0;
        boolean forceCrit = false;
        switch (skill.combo()) {
            case OPENER, LINK -> {
                int next;
                if (count == 0) {
                    next = skill.combo() == SkillDef.ComboRole.OPENER ? 1 : 0;
                } else if (different) {
                    next = Math.min(combo.max(), count + 1);
                } else {
                    next = skill.combo() == SkillDef.ComboRole.OPENER ? 1 : 0;
                }
                if (next > count || (next == 1 && count != 1)) {
                    GaugeService.add(data, combo.gaugePerStep());
                    announce(player, next);
                }
                data.setCombo(next, until);
                multiplier = 1.0 + perStep * next / 100.0;
                if (readFlow) {
                    multiplier *= 1.0 + PassiveService.value(data, PassiveDef.Type.READ_FLOW) / 100.0;
                }
            }
            case FINISHER -> {
                multiplier = 1.0 + perStep * count / 100.0;
                if (count > 0) {
                    multiplier *= 1.0 + combo.finisherPerStep() * count / 100.0;
                    double finishing = PassiveService.value(data, PassiveDef.Type.FINISHING_BLOW);
                    if (finishing > 0) {
                        multiplier *= 1.0 + finishing / 100.0;
                        forceCrit = count >= combo.max();
                    }
                    double flowing = PassiveService.value(data, PassiveDef.Type.FLOWING_KI);
                    if (flowing > 0 && data.isStatsReady()) {
                        HealService.healSelfPercent(player, data, flowing * count);
                    }
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.get("combat.chain_finish"),
                            SoundSource.PLAYERS, 1.0F, 0.8F + 0.08F * count);
                    CombatTextS2C.send(player, count, CombatTextS2C.Kind.CHAIN, new int[]{player.getId()});
                }
                if (data.getBuffs().endlessFlow(now)) {
                    data.setCombo(count, until);
                } else {
                    data.setCombo(0, 0L);
                }
                if (readFlow) {
                    multiplier *= 1.0 + PassiveService.value(data, PassiveDef.Type.READ_FLOW) / 100.0;
                }
            }
            default -> multiplier = 1.0 + perStep * count / 100.0;
        }
        if (skill.combo() != SkillDef.ComboRole.NONE) {
            chain.lastSkill = skill.id();
            chain.lastTick = now;
        }
        return new Step(multiplier, forceCrit);
    }

    /** 天地流転の反撃：連撃を1つ足す。 */
    public static void addStep(ServerPlayer player, PlayerData data, long now) {
        JobDef job = SkillRegistry.get().job(data.getCombatJob());
        JobDef.ComboDef combo = job == null ? null : job.combo();
        if (combo == null) {
            return;
        }
        int count = data.getCombo() > 0 && now <= data.getComboUntil() ? data.getCombo() : 0;
        int next = Math.min(combo.max(), count + 1);
        if (next > count) {
            GaugeService.add(data, combo.gaugePerStep());
            announce(player, next);
        }
        data.setCombo(next, now + combo.windowTicks());
    }

    /** 受け流し・回避をした（流れを読む）。 */
    public static void onFlowRead(ServerPlayer player, PlayerData data, long now) {
        if (PassiveService.value(data, PassiveDef.Type.READ_FLOW) > 0) {
            READ_FLOW.put(player.getUUID(), now + READ_FLOW_TICKS);
        }
    }

    private static boolean readingFlow(UUID player, long now) {
        Long until = READ_FLOW.get(player);
        return until != null && now <= until;
    }

    private static void announce(ServerPlayer player, int count) {
        if (count <= 1) {
            return;
        }
        CombatTextS2C.send(player, count, CombatTextS2C.Kind.CHAIN, new int[]{player.getId()});
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.get("combat.chain"),
                SoundSource.PLAYERS, 0.7F, 0.9F + 0.12F * count);
    }

    /**
     * 連ね斬り（ナイト）：その流派のスキルが敵に当たったとき。このスキルにかける倍率を返す（1回のスキルで1回だけ呼ぶ）。
     */
    public static double bladeChain(ServerPlayer player, PlayerData data, SkillDef skill, long now) {
        PassiveService.Active active = PassiveService.find(data, PassiveDef.Type.BLADE_CHAIN);
        if (active == null || !active.school().equals(skill.school())) {
            return 1.0;
        }
        Blade blade = BLADES.computeIfAbsent(player.getUUID(), id -> new Blade());
        if (now - blade.lastTick > BLADE_WINDOW) {
            blade.stacks = 0;
            blade.lastSkill = null;
        }
        if (blade.lastSkill != null && !blade.lastSkill.equals(skill.id())) {
            blade.stacks = Math.min(BLADE_MAX, blade.stacks + 1);
        } else if (blade.lastSkill != null) {
            blade.stacks = 0;
        }
        blade.lastSkill = skill.id();
        blade.lastTick = now;
        return 1.0 + active.value() * blade.stacks / 100.0;
    }

    public static void forget(UUID player) {
        CHAINS.remove(player);
        BLADES.remove(player);
        READ_FLOW.remove(player);
    }

    public static void clear() {
        CHAINS.clear();
        BLADES.clear();
        READ_FLOW.clear();
    }
}

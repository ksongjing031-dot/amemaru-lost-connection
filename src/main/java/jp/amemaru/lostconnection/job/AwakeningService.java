package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.combat.CombatService;
import jp.amemaru.lostconnection.core.registry.ModDamageTypes;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.network.s2c.AnnouncementS2C;
import jp.amemaru.lostconnection.network.s2c.AwakenFxS2C;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.player.BuffState;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 覚醒（11月27日のご指示「どの役職もどこかのタイミングで強くなるタイミングを追加する（パワースパイクのようなもの）」
 * 「モンクは序盤は弱めでいいが後半で爆発的な攻撃力を」【補完】）。
 *
 * <p>職業の定義の {@code awakenings} に書いたレベルになると、スキルポイントを使わずに自動で効く（{@link JobDef.Awakening}）。
 * 職業ごとに強くなる時期が違う（ナイト Lv12・28／ヒーラーとアーチャー Lv14・30／ウィザード Lv16・30／ローグ Lv18・32／
 * モンク Lv10〜・20・32）。モンクは「練気」でレベルとともに伸び、遅い時期に大きく跳ねる。</p>
 */
public final class AwakeningService {
    /** 「もう一度」が、ひとつのスキルで何回まで出るか（連打の技で、数字と演出が出すぎないように）。 */
    private static final int MAX_ECHOES_PER_SKILL = 6;

    private AwakeningService() {
    }

    /** 今の職業の覚醒（レベルの低い順。まだのものも）。 */
    public static List<JobDef.Awakening> all(PlayerData data) {
        JobDef job = SkillRegistry.get().job(data.getCombatJob());
        return job == null ? List.of() : job.awakenings();
    }

    /** 今の職業で、もう効いている覚醒。 */
    public static List<JobDef.Awakening> active(PlayerData data) {
        List<JobDef.Awakening> out = new ArrayList<>();
        for (JobDef.Awakening awakening : all(data)) {
            if (data.getLevel() >= awakening.level()) {
                out.add(awakening);
            }
        }
        return out;
    }

    /**
     * 効いている覚醒の数の合計（{@code damage} には、レベルで伸びる分も足す）。
     */
    public static double value(PlayerData data, String key) {
        double total = 0.0;
        int level = data.getLevel();
        for (JobDef.Awakening awakening : all(data)) {
            if (level < awakening.level()) {
                continue;
            }
            total += awakening.value(key);
            if ("damage".equals(key) && awakening.scaling() != null) {
                total += awakening.scaling().at(level);
            }
        }
        return total;
    }

    /** 専用ゲージの最大に足す数。 */
    public static int gaugeMaxBonus(PlayerData data) {
        return (int) Math.round(value(data, "gauge_max"));
    }

    /** 連撃の最大に足す数。 */
    public static int comboMaxBonus(PlayerData data) {
        return (int) Math.round(value(data, "combo_max"));
    }

    /** 条件に合う「もう一度」のうち、いちばん強いもの（なければ null）。 */
    @Nullable
    public static JobDef.Echo echo(PlayerData data, String condition) {
        JobDef.Echo best = null;
        for (JobDef.Awakening awakening : active(data)) {
            JobDef.Echo echo = awakening.echo();
            if (echo != null && echo.when().contains(condition) && (best == null || echo.percent() > best.percent())) {
                best = echo;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- 「もう一度」

    /**
     * スキルのダメージが当たったあとに呼ぶ：覚醒の「もう一度」が決まっていれば、少し遅れて同じ相手にもう一度（与えたダメージの%）。
     * 防御はもう引いた後の量なので、もう一度は引かない。
     */
    public static void afterSkillHit(SkillContext ctx, LivingEntity target, double dealt) {
        if (ctx.awakenEcho <= 0.0 || ctx.echo || dealt <= 0.0 || ctx.awakenEchoCount >= MAX_ECHOES_PER_SKILL) {
            return;
        }
        ServerPlayer caster = ctx.caster();
        if (caster == null) {
            return;
        }
        ctx.awakenEchoCount++;
        double amount = Math.max(1.0, Math.round(dealt * ctx.awakenEcho / 100.0));
        int color = jobColor(caster);
        SkillScheduler.schedule(ctx.level, Math.max(1, ctx.awakenEchoDelay), () -> {
            if (!target.isAlive() || !caster.isAlive()) {
                return;
            }
            // 敵への知らせ（狙い・HPで変わる技）は、applyDamage の中で済む
            CombatService.applyDamage(target, amount, CombatTextS2C.Kind.ECHO, ModDamageTypes.skill(ctx.level, caster, caster));
            new AwakenFxS2C(AwakenFxS2C.Kind.ECHO, target.getId(), color).sendNear(ctx.level, target.position());
            ctx.level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS,
                    0.35F, 1.7F);
        });
    }

    // ---------------------------------------------------------------- 回復した仲間（ヒーラー）

    /** ヒーラーの「聖域」：回復した仲間の与えるダメージが、しばらく上がる。 */
    public static void afterHealAlly(ServerPlayer caster, PlayerData casterData, ServerPlayer target, PlayerData targetData) {
        double power = value(casterData, "ally_power");
        if (power <= 0 || caster == target) {
            return;
        }
        double seconds = value(casterData, "ally_power_seconds");
        int ticks = (int) Math.round((seconds > 0 ? seconds : 6.0) * 20.0);
        long now = target.level().getGameTime();
        BuffState buffs = targetData.getBuffs();
        boolean active = buffs.powerUp(now) > 0;
        buffs.powerUp = Math.max(active ? buffs.powerUp : 0.0, power);
        buffs.powerUpUntil = Math.max(active ? buffs.powerUpUntil : 0L, now + ticks);
        target.addEffect(new MobEffectInstance(ModEffects.POWER_UP.get(), (int) (buffs.powerUpUntil - now), 0, false, false, true), caster);
    }

    // ---------------------------------------------------------------- 知らせ

    /** レベルが上がったとき（{@code LevelService}）：このレベルで目覚めた覚醒を知らせる。 */
    public static void onLevelChanged(ServerPlayer player, PlayerData data, int oldLevel, int newLevel) {
        if (newLevel <= oldLevel) {
            return;
        }
        JobDef job = SkillRegistry.get().job(data.getCombatJob());
        if (job == null) {
            return;
        }
        for (JobDef.Awakening awakening : job.awakenings()) {
            if (awakening.level() > oldLevel && awakening.level() <= newLevel) {
                announce(player, job, awakening);
            }
        }
    }

    private static void announce(ServerPlayer player, JobDef job, JobDef.Awakening awakening) {
        String title = Component.translatable("message.amemaru.awaken.title", job.name(), awakening.name()).getString();
        jp.amemaru.lostconnection.network.ModNetwork.sendTo(player, new AnnouncementS2C(AnnouncementS2C.Kind.AWAKEN, title));
        player.sendSystemMessage(Component.translatable("message.amemaru.awaken.chat", job.name(), awakening.name(), awakening.level(),
                awakening.description()).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 0.9F, 1.0F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 1.0F, 1.2F);
        if (player.level() instanceof ServerLevel level) {
            new AwakenFxS2C(AwakenFxS2C.Kind.AWAKEN, player.getId(), job.color()).sendNear(level, player.position());
        }
    }

    private static int jobColor(ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        JobDef job = data == null ? null : SkillRegistry.get().job(data.getCombatJob());
        return job == null ? 0xFFD27A : job.color();
    }
}

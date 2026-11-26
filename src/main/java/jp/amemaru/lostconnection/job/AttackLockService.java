package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.job.def.DeliveryDef;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * 攻撃のあとの硬直（11月26日のご指示「遠距離職は攻撃の際にスキルなどは硬直やほぼほぼ動けないスロウ効果などをつけましょう。
 * 動きながら打てるのが強すぎる。魔法使いはこれがすごくいいが、アーチャーの調整が要検討が必要」【補完】）。
 *
 * <p>職業の定義の {@code attack_lock}（{@link JobDef.AttackLock}）に従って、状態「硬直」（{@code ModEffects.ATTACK_LOCK}。
 * 1段階で足が30%遅くなり、3段階でほぼ止まって、ジャンプとダッシュもできない）をつける。</p>
 * <ul>
 *   <li>ウィザード：攻撃の魔法のあと、重い魔法ほど長く（0.4〜1.2秒）ほぼ止まる。詠唱と溜めの間も、ほぼ止まる</li>
 *   <li>アーチャー【提案】：放つ瞬間だけ足を止める（0.25〜0.6秒）。通常の射撃は、射っている間だけ足が60%遅い（弓を引いて歩く）。
 *       引き絞り（溜め）と流れ星の一矢の詠唱の間は、ほぼ止まる</li>
 *   <li>ヒーラー：攻撃の鈴だけ（回復・強化・障壁などで仲間も支える鈴は、今までどおり動ける）</li>
 * </ul>
 * 移動の技（瞬間移動・跳躍・突進・後ろ跳び）と、ダメージを与えないスキル（強化・回復など）には、硬直をつけない。
 * 近接職（ナイト・ローグ・モンク）には {@code attack_lock} がないので、何も変わらない。
 */
public final class AttackLockService {
    /** 移動の技（硬直をつけない）。 */
    private static final EnumSet<DeliveryDef.Type> MOVES = EnumSet.of(DeliveryDef.Type.BLINK, DeliveryDef.Type.LEAP,
            DeliveryDef.Type.DASH, DeliveryDef.Type.BACKSTEP);
    /** 詠唱・溜めの間の硬直の、終わりのゆとり（tick。詠唱の終わりと同じ tick に切れないように）。 */
    private static final int CAST_SLACK = 2;

    private AttackLockService() {
    }

    /** そのスキルの職業の硬直の決まり（ない職業は null）。 */
    @Nullable
    public static JobDef.AttackLock rule(SkillDef skill) {
        JobDef job = SkillRegistry.get().job(skill.job());
        return job == null ? null : job.attackLock();
    }

    /** このスキルに、職業の硬直がかかるか（攻撃のスキルで、移動の技でない。スキルの定義の lock が0なら、かからない）。 */
    public static boolean applies(@Nullable JobDef.AttackLock lock, SkillDef skill) {
        if (lock == null || !skill.isActive() || skill.lockSeconds() == 0.0) {
            return false;
        }
        if (skill.lockSeconds() > 0.0) {
            return true;
        }
        if (MOVES.contains(skill.delivery().type()) || !skill.dealsDamage()) {
            return false;
        }
        return !(lock.exemptSupport() && skill.supportsAllies());
    }

    /** 使ったあとの硬直の tick（かからないときは0）。クールダウンは、短くする前の定義の秒で数える。 */
    public static int skillTicks(@Nullable JobDef.AttackLock lock, SkillDef skill, int rank) {
        if (!applies(lock, skill) || lock.skillLevel() <= 0) {
            return 0;
        }
        double seconds = skill.lockSeconds() > 0.0 ? skill.lockSeconds() : lock.skillSeconds(skill.cooldown(rank));
        return seconds <= 0.0 ? 0 : toTicks(seconds);
    }

    /** スキルを放ったあと（{@code SkillService.execute} から）。 */
    public static void afterSkill(ServerPlayer player, SkillDef skill, int rank) {
        JobDef.AttackLock lock = rule(skill);
        int ticks = skillTicks(lock, skill, rank);
        if (ticks > 0) {
            apply(player, lock.skillLevel(), ticks);
        }
    }

    /** 詠唱（{@code charge} が false）・溜め（true）を始めたとき。{@code ticks} は、詠唱・溜めの長さ。 */
    public static void duringCast(ServerPlayer player, SkillDef skill, int ticks, boolean charge) {
        JobDef.AttackLock lock = rule(skill);
        if (!applies(lock, skill)) {
            return;
        }
        apply(player, charge ? lock.chargeLevel() : lock.chantLevel(), ticks + CAST_SLACK);
    }

    /**
     * 詠唱・溜めが終わった・やめたとき：その間の硬直を外す（放ったあとの硬直は、そのあと {@link #afterSkill} でつく）。
     * 詠唱・溜めの間に硬直をつけないスキル（回復の溜めなど）では外さない（前の攻撃の硬直を、早く消せないように）。
     */
    public static void endCast(ServerPlayer player, @Nullable ResourceLocation skillId, boolean charge) {
        SkillDef skill = skillId == null ? null : SkillRegistry.get().skill(skillId);
        JobDef.AttackLock lock = skill == null ? null : rule(skill);
        if (lock == null || !applies(lock, skill) || (charge ? lock.chargeLevel() : lock.chantLevel()) <= 0) {
            return;
        }
        player.removeEffect(ModEffects.ATTACK_LOCK.get());
    }

    /** 通常の射撃（弓）を射たあと（{@code BasicShotService}）。 */
    public static void afterBasicShot(ServerPlayer player, @Nullable ResourceLocation combatJob) {
        JobDef job = combatJob == null ? null : SkillRegistry.get().job(combatJob);
        JobDef.AttackLock lock = job == null ? null : job.attackLock();
        if (lock == null || lock.basicLevel() <= 0 || lock.basicSeconds() <= 0.0) {
            return;
        }
        apply(player, lock.basicLevel(), toTicks(lock.basicSeconds()));
    }

    /**
     * 硬直をつける。3段階（ほぼ止まる）は、状態の印も出し、ダッシュを止める。弱い段階（通常の射撃）は、射るたびに出たり消えたり
     * しないよう、印を出さない。
     */
    static void apply(ServerPlayer player, int level, int ticks) {
        if (level <= 0 || ticks <= 0) {
            return;
        }
        int clamped = Math.min(JobDef.AttackLock.MAX_LEVEL, level);
        boolean full = clamped >= JobDef.AttackLock.MAX_LEVEL;
        player.addEffect(new MobEffectInstance(ModEffects.ATTACK_LOCK.get(), ticks, clamped - 1, false, false, full));
        if (full) {
            player.setSprinting(false);
        }
    }

    /** 秒 → tick（切り上げ。0.3＋0.05×12 が 0.9000000000000001 になるような端数で、1 tick 増えないように）。 */
    private static int toTicks(double seconds) {
        return Math.max(1, Mth.ceil(seconds * 20.0 - 1.0E-6));
    }

    /** 足がほぼ止まる段階（ジャンプとダッシュもできない）か。{@code amplifier} は状態の強さ（段階 − 1）。 */
    public static boolean isFull(int amplifier) {
        return amplifier + 1 >= JobDef.AttackLock.MAX_LEVEL;
    }
}

package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.job.def.DeliveryDef;
import jp.amemaru.lostconnection.job.def.EffectDef;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.PassiveDef;
import jp.amemaru.lostconnection.job.def.Ranked;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * スキルの段階ごとの説明文（スキルツリーの画面に出す）を、定義の数値から作る。
 *
 * <p>数値を変えれば説明も変わるので、説明文と実際の強さがずれない。文はサーバーで作ってクライアントに送る
 * （定義はサーバーのデータパックにあるため）。</p>
 */
public final class SkillDescriber {
    private SkillDescriber() {
    }

    /** その段階の説明の行。 */
    public static List<String> describe(SkillDef skill, @Nullable JobDef job, int rank) {
        List<String> lines = new ArrayList<>();
        String gaugeName = job != null && job.gauge() != null ? job.gauge().name() : "ゲージ";
        if (skill.isActive()) {
            describeDelivery(skill.delivery(), rank, lines);
            for (EffectDef effect : skill.effects()) {
                describeEffect(effect, rank, gaugeName, lines, "");
            }
            if (skill.cast(rank) > 0 && skill.castSupportMultiplier() < 1.0) {
                lines.add("味方の支援を受けていると、詠唱が" + percent(1.0 - skill.castSupportMultiplier()) + "短くなる");
            }
        } else if (skill.passive() != null) {
            describePassive(skill.passive(), rank, gaugeName, lines);
        }
        return lines;
    }

    private static void describeDelivery(DeliveryDef delivery, int rank, List<String> lines) {
        String who = delivery.affects() == jp.amemaru.lostconnection.job.def.Affects.ALLIES ? "味方" : "敵";
        switch (delivery.type()) {
            case AROUND -> lines.add("自分のまわり（半径" + num(delivery.radius(rank)) + "）の" + who + (delivery.includeSelf() && "味方".equals(who) ? "と自分" : ""));
            case TARGET -> lines.add("狙った" + who + "1人（射程" + num(delivery.range(rank)) + "）");
            case POINT -> {
                lines.add("狙った地点のまわり（射程" + num(delivery.range(rank)) + "・半径" + num(delivery.radius(rank)) + "）");
                if (delivery.impactDelay(rank) > 0) {
                    lines.add(num(delivery.impactDelay(rank)) + "秒後に落ちる");
                }
            }
            case PROJECTILE -> lines.add("前へ飛ぶ弾（射程" + num(delivery.range(rank)) + "）。最初に当たった敵");
            case DASH -> lines.add("前へ" + num(delivery.distance(rank)) + "ブロック踏み込み、最初に触れた敵");
            case CHAIN -> lines.add("狙った敵から、近くの敵へ最大" + delivery.jumps(rank) + "回跳ぶ（跳ぶたびに"
                    + percent(1.0 - delivery.falloff(rank)) + "弱まる）");
            case BLINK -> lines.add("向いている方向へ、最大" + num(delivery.distance(rank)) + "ブロック瞬間移動する");
            case SELF -> {
            }
        }
    }

    private static void describeEffect(EffectDef effect, int rank, String gaugeName, List<String> lines, String prefix) {
        switch (effect.type()) {
            case DAMAGE -> {
                String stat = effect.stat() == StatType.MAGIC ? "魔力" : "攻撃";
                lines.add(prefix + stat + "の" + percent(effect.value("coefficient", rank)) + "のダメージ");
            }
            case STATUS -> lines.add(prefix + statusText(effect.statusEffect(), effect.value("duration", rank), (int) Math.round(effect.value("level", rank))));
            case TAUNT -> lines.add(prefix + num(effect.value("duration", rank)) + "秒、敵の狙いを自分に向ける");
            case GAUGE -> {
                double amount = effect.value("amount", rank);
                double perTarget = effect.value("per_target", rank);
                double max = effect.value("max", rank);
                if (amount > 0) {
                    lines.add(prefix + gaugeName + "が" + num(amount) + "たまる");
                }
                if (perTarget > 0) {
                    lines.add(prefix + "当たった敵1体ごとに" + gaugeName + "が" + num(perTarget) + "たまる"
                            + (max > 0 ? "（最大" + num(max) + "）" : ""));
                }
            }
            case GAUGE_EMPOWER -> lines.add(prefix + gaugeName + "が" + num(effect.value("consume", rank)) + "以上あれば、"
                    + num(effect.value("consume", rank)) + "使って威力" + percent(effect.value("multiplier", rank)));
            case SHIELD_BOND -> lines.add(prefix + num(effect.value("duration", rank)) + "秒、味方の受けるダメージの"
                    + num(effect.value("share", rank)) + "%を肩代わりする");
            case BUFF -> {
                double duration = effect.value("duration", rank);
                double value = effect.value("value", rank);
                if (effect.buff() != null) {
                    switch (effect.buff()) {
                        case RAMPART -> lines.add(prefix + num(duration) + "秒、正面からのダメージを" + num(value) + "%減らす（足が遅くなる）");
                        case UNYIELDING -> lines.add(prefix + num(duration) + "秒、HPが1より下がらない");
                        case FORTRESS -> lines.add(prefix + num(duration) + "秒、受けるダメージを" + num(value) + "%減らす");
                    }
                }
            }
            case MP_TRANSFER -> lines.add(prefix + "自分のMPを最大" + num(effect.value("amount", rank)) + "、味方に分ける");
            case ZONE -> {
                lines.add(prefix + "半径" + num(effect.value("radius", rank)) + "の陣を" + num(effect.value("duration", rank)) + "秒残す");
                for (EffectDef inner : effect.nested()) {
                    describeEffect(inner, rank, gaugeName, lines, prefix + "　陣の中：");
                }
            }
            case KNOCKBACK -> lines.add(prefix + "吹き飛ばす");
        }
    }

    private static void describePassive(PassiveDef passive, int rank, String gaugeName, List<String> lines) {
        String value = num(passive.value(rank));
        String radius = num(passive.radius(rank));
        switch (passive.type()) {
            case GUARDIAN_PRIDE -> lines.add("まわり" + radius + "ブロックに味方がいると、防御+" + value + "%");
            case IRON_WALL -> lines.add("盾を持っていると、受けるダメージ-" + value + "%（両手剣のときは効かない）");
            case OATH_CYCLE -> lines.add(gaugeName + "を使うと、使った量の" + value + "%のMPが戻る");
            case SWIFT_CHANT -> lines.add("詠唱の時間-" + value + "%");
            case MANA_RESONANCE -> lines.add("まわり" + radius + "ブロックに魔法を使う味方がいると、魔力+" + value + "%");
            case MANA_SPRING -> lines.add("MPの自然回復+" + value + "%");
        }
    }

    private static String statusText(@Nullable ResourceLocation id, double duration, int level) {
        String name = "状態";
        if (id != null) {
            if (id.getPath().equals("stagger")) {
                return num(duration) + "秒よろめかせる（足が止まる）";
            }
            if (id.getPath().equals("frost")) {
                return num(duration) + "秒、凍えで" + (level * 10) + "%遅くする";
            }
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id);
            if (effect != null) {
                name = effect.getDisplayName().getString();
            }
        }
        return name + "（" + num(duration) + "秒）";
    }

    private static String num(double value) {
        return Ranked.trim(value);
    }

    private static String percent(double ratio) {
        return Ranked.trim(ratio * 100.0) + "%";
    }
}

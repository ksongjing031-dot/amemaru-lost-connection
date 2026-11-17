package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.combat.StatusService;
import jp.amemaru.lostconnection.job.def.Affects;
import jp.amemaru.lostconnection.job.def.BuffKind;
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
            describeCharge(skill, lines);
            describeDelivery(skill, rank, lines);
            for (EffectDef effect : skill.effects()) {
                describeEffect(effect, rank, gaugeName, lines, "");
            }
            for (EffectDef effect : skill.fullChargeEffects()) {
                describeEffect(effect, rank, gaugeName, lines, "溜めきると：");
            }
            if (skill.cast(rank) > 0 && skill.castSupportMultiplier() < 1.0) {
                lines.add("味方の支援を受けていると、詠唱が" + percent(1.0 - skill.castSupportMultiplier()) + "短くなる");
            }
        } else if (skill.passive() != null) {
            describePassive(skill.passive(), rank, gaugeName, lines);
        }
        return lines;
    }

    private static void describeCharge(SkillDef skill, List<String> lines) {
        if (!skill.isCharged()) {
            return;
        }
        String head = "キーを押している間溜め（最大" + num(skill.chargeTime()) + "秒）、離して放つ";
        if (skill.chargeMaxMultiplier() > skill.chargeMinMultiplier() + 1.0E-6) {
            head += "。溜めるほど威力が上がる（" + num(skill.chargeMinMultiplier()) + "〜" + num(skill.chargeMaxMultiplier()) + "倍）";
        }
        lines.add(head);
    }

    private static void describeDelivery(SkillDef skill, int rank, List<String> lines) {
        DeliveryDef delivery = skill.delivery();
        String who = delivery.affects() == Affects.ALLIES ? "味方" : "敵";
        switch (delivery.type()) {
            case AROUND -> lines.add("自分のまわり（半径" + num(delivery.radius(rank)) + "）の" + who
                    + (delivery.includeSelf() && "味方".equals(who) ? "と自分" : ""));
            case TARGET -> lines.add("狙った" + who + "1人（射程" + num(delivery.range(rank)) + "）"
                    + (delivery.fallbackSelf() ? "。狙える味方がいなければ自分" : ""));
            case POINT -> {
                lines.add("狙った地点のまわり（射程" + num(delivery.range(rank)) + "・半径" + num(delivery.radius(rank)) + "）");
                if (delivery.impactDelay(rank) > 0) {
                    lines.add(num(delivery.impactDelay(rank)) + "秒後に落ちる");
                }
            }
            case PROJECTILE -> {
                int count = delivery.count(rank);
                int charged = delivery.countCharged(rank);
                if (skill.isCharged() && charged > count) {
                    lines.add("前へ飛ぶ弾を扇に" + count + "〜" + charged + "発（溜めるほど増える・射程" + num(delivery.range(rank))
                            + "）。それぞれ最初に当たった敵");
                } else if (count > 1) {
                    lines.add("前へ飛ぶ弾を扇に" + count + "発（射程" + num(delivery.range(rank)) + "）。それぞれ最初に当たった敵");
                } else {
                    lines.add("前へ飛ぶ弾（射程" + num(delivery.range(rank)) + "）。最初に当たった敵");
                }
            }
            case DASH -> lines.add("前へ" + num(delivery.distance(rank)) + "ブロック踏み込み、最初に触れた敵");
            case CHAIN -> {
                lines.add("狙った敵から、近くの敵へ最大" + delivery.jumps(rank) + "回跳ぶ（跳ぶたびに"
                        + percent(1.0 - delivery.falloff(rank)) + "弱まる）");
                if (delivery.shockedJumps(rank) > 0 || delivery.shockedMultiplier(rank) > 1.0) {
                    lines.add("最初の敵が感電していれば、跳ぶ回数+" + delivery.shockedJumps(rank) + "・威力"
                            + percent(delivery.shockedMultiplier(rank)) + "（感電は消える）");
                }
            }
            case BLINK -> lines.add("向いている方向へ、最大" + num(delivery.distance(rank)) + "ブロック瞬間移動する");
            case LEAP -> lines.add("狙った地点（射程" + num(delivery.range(rank)) + "）へ跳び、着地したところのまわり（半径"
                    + num(delivery.radius(rank)) + "）の敵。落下のダメージは受けない");
            case LINE -> lines.add("前へまっすぐ貫く（長さ" + num(delivery.range(rank)) + "・幅" + num(delivery.width(rank))
                    + "）。線の上の敵すべて");
            case CONE -> lines.add("前の扇（半径" + num(delivery.radius(rank)) + "・左右" + num(delivery.angle(rank)) + "度）の敵すべて");
            case BRANDED -> lines.add("まわり" + num(delivery.range(rank)) + "ブロック以内の、星痕のある敵すべて（いなければ使えない）");
            case SELF -> {
            }
        }
    }

    private static void describeEffect(EffectDef effect, int rank, String gaugeName, List<String> lines, String prefix) {
        switch (effect.type()) {
            case DAMAGE -> {
                String stat = effect.stat() == StatType.MAGIC ? "魔力" : "攻撃";
                lines.add(prefix + stat + "の" + percent(effect.value("coefficient", rank)) + "のダメージ");
                if (effect.bonusStatus() != null) {
                    lines.add(prefix + "　" + statusName(effect.bonusStatus()) + "の敵には" + percent(effect.value("bonus", rank))
                            + (effect.consumeStatus() ? "（" + consumeText(effect.bonusStatus()) + "）" : ""));
                }
                if (effect.value("lifesteal", rank) > 0) {
                    lines.add(prefix + "　与えたダメージの" + num(effect.value("lifesteal", rank)) + "%だけHPが戻る");
                }
            }
            case STATUS -> lines.add(prefix + statusText(effect.statusEffect(), effect.value("duration", rank),
                    (int) Math.round(effect.value("level", rank))));
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
            case GAUGE_BURST -> lines.add(prefix + gaugeName + "を残らず使い、使った1につき威力+"
                    + num(effect.value("per_gauge", rank) * 100.0) + "%（100使えば" + percent(1.0 + 100.0 * effect.value("per_gauge", rank)) + "）");
            case SHIELD_BOND -> lines.add(prefix + num(effect.value("duration", rank)) + "秒、味方の受けるダメージの"
                    + num(effect.value("share", rank)) + "%を肩代わりする");
            case BUFF -> describeBuff(effect, rank, gaugeName, lines, prefix);
            case MP_TRANSFER -> lines.add(prefix + "自分のMPを最大" + num(effect.value("amount", rank)) + "、味方に分ける");
            case ZONE -> {
                lines.add(prefix + "半径" + num(effect.value("radius", rank)) + "の陣を" + num(effect.value("duration", rank)) + "秒残す");
                for (EffectDef inner : effect.nested()) {
                    describeEffect(inner, rank, gaugeName, lines, prefix + (inner.to() == Affects.ALLIES ? "　陣の中の味方：" : "　陣の中の敵："));
                }
            }
            case KNOCKBACK -> lines.add(prefix + "吹き飛ばす");
            case CHILL -> {
                int stacks = (int) Math.round(effect.value("stacks", rank));
                int max = (int) Math.round(effect.value("max", rank));
                double freeze = effect.value("freeze", rank);
                int freezeIf = (int) Math.round(effect.value("freeze_if", rank));
                if (freezeIf > 0 && freeze > 0) {
                    lines.add(prefix + "凍えが" + freezeIf + "つ以上の敵は、すぐ凍結させる（" + num(freeze) + "秒）");
                    lines.add(prefix + "ほかの敵には、凍えを" + stacks + "つ重ねる");
                } else {
                    String line = prefix + "凍えを" + stacks + "つ重ねる（" + num(effect.value("duration", rank)) + "秒・1つにつき移動-10%）";
                    if (max < StatusService.MAX_CHILL) {
                        line += "。ここでは" + max + "つまで";
                    } else if (freeze > 0) {
                        line += "。5つで凍結（" + num(freeze) + "秒動けない）";
                    }
                    lines.add(line);
                }
            }
            case SHOCK -> lines.add(prefix + num(effect.value("duration", rank)) + "秒、感電させる（次の雷鎖がよく跳ぶ）");
            case BRAND -> lines.add(prefix + "星痕を" + (int) Math.round(effect.value("stacks", rank)) + "つ刻む（"
                    + num(effect.value("duration", rank)) + "秒・最大" + StatusService.MAX_BRAND + "つ）");
            case DETONATE -> {
                String stat = effect.stat() == StatType.MAGIC ? "魔力" : "攻撃";
                lines.add(prefix + "星痕を弾けさせ、" + stat + "の" + percent(effect.value("base", rank)) + "＋星痕1つにつき"
                        + percent(effect.value("per_stack", rank)) + "のダメージ（星痕は消える）");
            }
            case PULL -> lines.add(prefix + "自分の" + num(effect.value("stop", rank)) + "ブロック手前まで引き寄せる");
            case BARRIER -> {
                String stat = effect.stat() == StatType.MAGIC ? "魔力" : "攻撃";
                lines.add(prefix + num(effect.value("duration", rank)) + "秒、" + stat + "の" + percent(effect.value("coefficient", rank))
                        + "のダメージを吸う障壁を張る");
            }
        }
    }

    private static void describeBuff(EffectDef effect, int rank, String gaugeName, List<String> lines, String prefix) {
        double duration = effect.value("duration", rank);
        double value = effect.value("value", rank);
        BuffKind kind = effect.buff();
        if (kind == null) {
            return;
        }
        switch (kind) {
            case RAMPART -> lines.add(prefix + num(duration) + "秒、正面からのダメージを" + num(value) + "%減らす（足が遅くなる）");
            case UNYIELDING -> lines.add(prefix + num(duration) + "秒、HPが1より下がらない");
            case FORTRESS -> lines.add(prefix + num(duration) + "秒、受けるダメージを" + num(value) + "%減らす");
            case WARD -> lines.add(prefix + "受けるダメージ-" + num(value) + "%");
            case PARRY -> {
                lines.add(prefix + num(duration) + "秒構え、その間の正面からの魔物の攻撃を無効にする");
                lines.add(prefix + "最初の1回は反撃する：");
                for (EffectDef inner : effect.nested()) {
                    describeEffect(inner, rank, gaugeName, lines, prefix + "　");
                }
                if (effect.value("refund", rank) > 0) {
                    lines.add(prefix + "　クールダウンの残りが" + percent(effect.value("refund", rank)) + "短くなる");
                }
            }
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
            case TAUNT_MASTERY -> lines.add("自分が挑発している敵の攻撃が-" + value + "%（誰が受けても）");
            case BREAKER -> lines.add("よろめき・防御崩しの敵へのダメージ+" + value + "%");
            case FIGHTING_SPIRIT -> lines.add("HPが半分を切ると、攻撃+" + value + "%");
            case STAR_AFTERGLOW -> lines.add("星痕を弾けさせると、星痕1つにつきMPが" + value + "戻る");
            case FROST_HEART -> lines.add("凍え・凍結の敵へのダメージ+" + value + "%");
            case DEEP_CHILL -> lines.add("凍えが" + num(PassiveService.DEEP_CHILL_DURATION) + "倍長く続き、凍結が" + value + "秒長くなる");
        }
    }

    private static String statusText(@Nullable ResourceLocation id, double duration, int level) {
        if (id != null) {
            switch (id.getPath()) {
                case "stagger" -> {
                    return num(duration) + "秒よろめかせる（足が止まる）";
                }
                case "frost" -> {
                    return num(duration) + "秒、凍えで" + (level * 10) + "%遅くする";
                }
                case "armor_break" -> {
                    return num(duration) + "秒、防御崩し（防御-" + percent(StatusService.ARMOR_BREAK_DEFENSE) + "）";
                }
                case "frozen" -> {
                    return num(duration) + "秒、凍結させる（動けない）";
                }
                default -> {
                }
            }
        }
        return statusName(id) + "（" + num(duration) + "秒）";
    }

    /** 状態の名前（説明文はサーバーで作るため、サーバーに言語のファイルがなくても読めるよう、このModの状態は名前を書いておく）。 */
    private static String statusName(@Nullable ResourceLocation id) {
        if (id == null) {
            return "状態";
        }
        if (id.getNamespace().equals(jp.amemaru.lostconnection.AmemaruMod.MOD_ID)) {
            switch (id.getPath()) {
                case "stagger" -> {
                    return "よろめき";
                }
                case "frost" -> {
                    return "凍え";
                }
                case "frozen" -> {
                    return "凍結";
                }
                case "shock" -> {
                    return "感電";
                }
                case "star_brand" -> {
                    return "星痕";
                }
                case "armor_break" -> {
                    return "防御崩し";
                }
                default -> {
                }
            }
        }
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id);
        return effect != null ? effect.getDisplayName().getString() : id.getPath();
    }

    private static String consumeText(ResourceLocation id) {
        return id.getPath().equals("frozen") ? "凍結を砕く" : statusName(id) + "は消える";
    }

    private static String num(double value) {
        return Ranked.trim(value);
    }

    private static String percent(double ratio) {
        return Ranked.trim(ratio * 100.0) + "%";
    }
}

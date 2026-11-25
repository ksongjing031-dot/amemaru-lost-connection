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
            describeCombo(skill, job, lines);
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

    /** モンクの連撃の役（【起】【継】【極】）。 */
    private static void describeCombo(SkillDef skill, @Nullable JobDef job, List<String> lines) {
        JobDef.ComboDef combo = job == null ? null : job.combo();
        if (combo == null) {
            return;
        }
        switch (skill.combo()) {
            case OPENER -> lines.add("【起】連撃を始める。始まった後は、前と違う技なら連撃が1つ増える（" + num(combo.window()) + "秒以内）");
            case LINK -> lines.add("【継】連撃が続いていれば、前と違う技で連撃が1つ増える（" + num(combo.window()) + "秒以内）");
            case FINISHER -> lines.add("【極】連撃を残らず使い、連撃1つにつき威力+" + num(combo.finisherPerStep()) + "%");
            default -> {
            }
        }
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
                    + (delivery.includeSelf() && "味方".equals(who) ? "と自分" : "")
                    + (delivery.includeDowned() ? "（ダウンしている味方も）" : ""));
            case TARGET -> {
                if (delivery.downed()) {
                    lines.add("ダウンしている味方1人（射程" + num(delivery.range(rank)) + "。壁の向こうでも届く）");
                } else if (delivery.smart()) {
                    lines.add("狙った味方1人（射程" + num(delivery.range(rank)) + "）。狙った先にいなければ、いちばん傷ついた味方（いなければ自分）");
                } else {
                    lines.add("狙った" + who + "1人（射程" + num(delivery.range(rank)) + "）"
                            + (delivery.fallbackSelf() ? "。狙える味方がいなければ自分" : ""));
                }
            }
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
            case BEHIND -> lines.add("狙った敵（射程" + num(delivery.range(rank)) + "）の背後へ回り込み、その敵");
            case BACKSTEP -> lines.add("後ろへ" + num(delivery.distance(rank)) + "ブロック跳びながら、前へ弾を撃つ（射程"
                    + num(delivery.range(rank)) + "）");
            case SELF -> {
            }
        }
        if (delivery.explode(rank) > 0) {
            lines.add("当たった所で弾け、まわり（半径" + num(delivery.explode(rank)) + "）の敵すべてに効く");
        }
        if (delivery.burst(rank) > 1) {
            lines.add(delivery.burst(rank) + "回続けて撃つ");
        }
    }

    private static void describeEffect(EffectDef effect, int rank, String gaugeName, List<String> lines, String prefix) {
        if (effect.self()) {
            prefix = prefix + "自分に：";
        }
        switch (effect.type()) {
            case DAMAGE -> {
                String stat = statName(effect.stat());
                int hits = (int) Math.round(effect.value("hits", rank));
                if (hits > 1) {
                    lines.add(prefix + stat + "の" + percent(effect.value("coefficient", rank)) + "のダメージを" + hits + "回");
                } else {
                    lines.add(prefix + stat + "の" + percent(effect.value("coefficient", rank)) + "のダメージ"
                            + (effect.value("delay", rank) > 0 ? "（最後に）" : ""));
                }
                if (effect.value("backstab", rank) > 1.0) {
                    lines.add(prefix + "　背後からなら" + percent(effect.value("backstab", rank)));
                }
                if (effect.value("pierce", rank) > 0) {
                    lines.add(prefix + "　敵の防御の" + num(effect.value("pierce", rank)) + "%を無視する");
                }
                if (effect.value("bonus_linked", rank) > 1.0) {
                    lines.add(prefix + "　仲間の起点の印がある敵には" + percent(effect.value("bonus_linked", rank)));
                }
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
                if (effect.zoneMode() == EffectDef.ZoneMode.NEAREST) {
                    lines.add(prefix + num(effect.value("duration", rank)) + "秒の間、" + num(effect.value("interval", rank))
                            + "秒ごとに、まわり" + num(effect.value("radius", rank)) + "ブロックでいちばん近い敵へ");
                } else if (effect.zoneMode() == EffectDef.ZoneMode.CONE) {
                    lines.add(prefix + num(effect.value("duration", rank)) + "秒の間、" + num(effect.value("interval", rank))
                            + "秒ごとに、前の扇（半径" + num(effect.value("radius", rank)) + "）の敵へ");
                } else if (effect.follow()) {
                    lines.add(prefix + num(effect.value("duration", rank)) + "秒の間、自分のまわり（半径" + num(effect.value("radius", rank))
                            + "）に、" + num(effect.value("interval", rank)) + "秒ごとに");
                } else {
                    lines.add(prefix + "半径" + num(effect.value("radius", rank)) + "の陣を" + num(effect.value("duration", rank)) + "秒残す");
                }
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
                String stat = statName(effect.stat());
                lines.add(prefix + "星痕を弾けさせ、" + stat + "の" + percent(effect.value("base", rank)) + "＋星痕1つにつき"
                        + percent(effect.value("per_stack", rank)) + "のダメージ（星痕は消える）");
            }
            case PULL -> lines.add(prefix + "自分の" + num(effect.value("stop", rank)) + "ブロック手前まで引き寄せる");
            case BARRIER -> {
                String stat = statName(effect.stat());
                lines.add(prefix + num(effect.value("duration", rank)) + "秒、" + stat + "の" + percent(effect.value("coefficient", rank))
                        + "のダメージを吸う障壁を張る");
                if (effect.value("heal_on_end", rank) > 0) {
                    lines.add(prefix + "　障壁が消えるとき、障壁の" + num(effect.value("heal_on_end", rank)) + "%だけ癒す");
                }
            }
            case HEAL -> lines.add(prefix + statName(effect.stat()) + "の" + percent(effect.value("coefficient", rank)) + "だけHPを癒す");
            case HEAL_PERCENT -> lines.add(prefix + "最大HPの" + num(effect.value("percent", rank)) + "%を癒す");
            case REGEN -> lines.add(prefix + num(effect.value("duration", rank)) + "秒、毎秒" + statName(effect.stat()) + "の"
                    + percent(effect.value("coefficient", rank)) + "ずつ癒す");
            case CLEANSE -> {
                int count = (int) Math.round(effect.value("count", rank));
                lines.add(prefix + (count > 0 ? "悪い状態を" + count + "つほどく" : "悪い状態をすべてほどく") + "（ロスト状態も）");
            }
            case REVIVE -> lines.add(prefix + "ダウンしている味方を起こす（HPは最大の" + num(effect.value("hp", rank)) + "%）");
            case POISON -> lines.add(prefix + "毒を" + (int) Math.round(effect.value("stacks", rank)) + "つ重ねる（"
                    + num(effect.value("duration", rank)) + "秒・毎秒" + statName(effect.stat()) + "の" + percent(effect.value("coefficient", rank))
                    + "×毒の数・" + (int) Math.round(effect.value("max", rank)) + "つまで）");
            case DETONATE_POISON -> {
                lines.add(prefix + "毒を弾けさせ、" + statName(effect.stat()) + "の" + percent(effect.value("base", rank)) + "＋毒1つにつき"
                        + percent(effect.value("per_stack", rank)) + "のダメージ（毒は消える）");
                if (effect.value("radius", rank) > 0) {
                    lines.add(prefix + "　まわり" + num(effect.value("radius", rank)) + "ブロックの敵の毒も弾ける");
                }
            }
            case MARK -> lines.add(prefix + num(effect.value("duration", rank)) + "秒、標をつける（プレイヤーから受けるダメージ+"
                    + num(effect.value("value", rank)) + "%）");
            case ROOT -> lines.add(prefix + num(effect.value("duration", rank)) + "秒、その場から動けなくする（ボスは半分の時間）");
            case WEAKEN -> lines.add(prefix + num(effect.value("duration", rank)) + "秒、敵の攻撃を" + num(effect.value("value", rank)) + "%弱める");
            case BLIND -> lines.add(prefix + "敵の攻撃が" + num(effect.value("value", rank)) + "%の確率で外れる（"
                    + num(effect.value("duration", rank)) + "秒）");
            case STEALTH -> lines.add(prefix + num(effect.value("duration", rank)) + "秒、自分が敵に見失われる");
            case TELEPORT_TO -> lines.add(prefix + "その味方のそばへ移る");
            case THROW -> lines.add(prefix + "自分の後ろへ投げ飛ばす（ボスは投げられない）");
            case LAUNCH -> lines.add(prefix + "打ち上げる");
            case COMBO_STEP -> lines.add(prefix + "連撃が1つ増える");
        }
    }

    private static String statName(@Nullable StatType stat) {
        if (stat == StatType.MAGIC) {
            return "魔力";
        }
        return stat == StatType.DEFENSE ? "防御" : "攻撃";
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
            case DEFENSE_UP -> lines.add(prefix + num(duration) + "秒、防御+" + num(value) + "%");
            case PROTECT -> lines.add(prefix + num(duration) + "秒、受けるダメージ-" + num(value) + "%");
            case CRIT_UP -> lines.add(prefix + num(duration) + "秒、会心+" + num(value));
            case HASTE -> lines.add(prefix + num(duration) + "秒、足が" + num(value) + "%速くなる");
            case EMPOWER_NEXT -> lines.add(prefix + num(duration) + "秒の間に使う次のスキルの威力+" + num(value) + "%");
            case AIM -> lines.add(prefix + num(duration) + "秒の間、次の矢" + (int) Math.round(effect.value("count", rank)) + "本の威力+"
                    + num(value) + "%（足が遅くなる）");
            case LIFE_THREAD -> {
                lines.add(prefix + num(duration) + "秒、命の糸でつなぐ：毎秒、魔力の" + percent(value) + "ずつ癒す");
                lines.add(prefix + "　HPが" + num(effect.value("threshold", rank)) + "%を切ると、一度だけ魔力の"
                        + percent(effect.value("burst", rank)) + "癒して切れる");
            }
            case GUARDIAN -> lines.add(prefix + num(duration) + "秒の間に倒れるほどの攻撃を受けると、一度だけ防ぎ、HPを最大の" + num(value) + "%まで戻す");
            case STAR_RING -> lines.add(prefix + num(duration) + "秒、星を" + (int) Math.round(effect.value("count", rank))
                    + "つ巡らせる。近づいた敵に、魔力の" + percent(value) + "のダメージと星痕");
            case SHADOW_CLONE -> lines.add(prefix + num(duration) + "秒、スキルが当たるたびに、影がもう一度斬る（ダメージの" + num(value) + "%）");
            case VENOM -> lines.add(prefix + num(duration) + "秒、攻撃が当たるたびに毒が1つ重なる（毎秒、攻撃の" + percent(value) + "）");
            case BOW_DANCE -> lines.add(prefix + num(duration) + "秒、足・矢のダメージ+" + num(value) + "%、クールダウンが" + num(value) + "%早く戻る");
            case EVADE -> lines.add(prefix + num(duration) + "秒、敵の攻撃をすり抜ける");
            case ENDLESS_FLOW -> lines.add(prefix + num(duration) + "秒、【極】の技を使っても連撃が消えない");
            case PARRY -> {
                boolean all = effect.value("all_directions", rank) > 0.5;
                int counters = (int) Math.round(effect.value("counters", rank));
                lines.add(prefix + num(duration) + "秒構え、その間の" + (all ? "どの向きからの" : "正面からの") + "魔物の攻撃を無効にする");
                lines.add(prefix + (counters > 1 ? "受け流すたびに反撃する：" : "最初の1回は反撃する："));
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
            case HEAL_POWER -> lines.add("回復量+" + value + "%");
            case AFTERCARE -> lines.add("癒した仲間は、癒した量の" + value + "%を、5秒かけてさらに癒える");
            case BOND_HEAL -> lines.add("まわり" + radius + "ブロックのパーティの仲間1人につき、回復量+" + value + "%（4人まで）");
            case OVERHEAL_BARRIER -> lines.add("満タンを超えて癒した分の" + value + "%が、障壁になる（最大HPの15%まで・8秒）");
            case HEAL_MP -> lines.add("ほかの仲間を癒すと、癒した量の" + value + "%だけMPが戻る");
            case BELL_GUARD -> lines.add("まわり" + radius + "ブロックの仲間（自分も）の受けるダメージ-" + value + "%");
            case CLEANSE_GUARD -> lines.add("自分のほどく技を受けた仲間は、4秒受けるダメージ-" + value + "%");
            case WEAKEN_ECHO -> lines.add("スキルでダメージを与えた敵の攻撃を、4秒-" + value + "%");
            case STEADY_PRAYER -> lines.add("詠唱と溜めの間、受けるダメージ-" + value + "%");
            case BELL_ECHO -> lines.add("スキルで与えたダメージの" + value + "%で、まわり" + radius + "ブロックのいちばん傷ついた仲間を癒す");
            case HAWK_EYE -> lines.add("会心+" + value);
            case STILL_FOCUS -> lines.add("立ち止まっている間の" + gaugeName + "のたまり方+" + value + "%");
            case LONG_SHOT -> lines.add("矢で、6ブロックより遠い敵へのダメージが1ブロックごとに+1%（最大+" + value + "%）");
            case WEAK_POINT -> lines.add("標・縫い止めの敵への会心ダメージ+" + value + "%");
            case FOCUS_REFUND -> lines.add("スキルで使った" + gaugeName + "の" + value + "%が戻る");
            case NIMBLE_ARCHER -> lines.add("弓を持っていると、素早さ+" + value + "%");
            case SCHOOL_HASTE -> lines.add("この流派のスキルのクールダウン-" + value + "%");
            case ARROW_CHAIN -> lines.add("2秒以内に矢を当て続けると、1本ごとにダメージ+" + value + "%（10本まで）");
            case WIND_READING -> lines.add("矢のスキルのダメージ+" + value + "%。矢が速く、遠くまで飛ぶ");
            case QUIVER_MASTER -> lines.add("この流派のスキルを使うと、" + value + "%の確率でクールダウンが半分");
            case BACKSTAB_MASTERY -> lines.add("背後から当てたダメージ+" + value + "%");
            case NIMBLE -> lines.add("回避+" + value + "%");
            case FADE -> lines.add("戦いの間、3秒攻撃しないでいると敵に見失われ、次の一撃のダメージ+" + value + "%");
            case SHADOW_STEP -> lines.add("影走り・影討ち・影縫いの後3秒、次の一撃の会心+" + value);
            case DARK_EDGE -> lines.add("会心ダメージ+" + value + "%");
            case TOXIN_POWER -> lines.add("毒のダメージ+" + value + "%");
            case CORRODE -> lines.add("自分の毒にかかった敵は、誰から受けるダメージも+" + value + "%");
            case DEEP_TOXIN -> lines.add("毒の重なる上限+" + value);
            case EXPLOIT -> lines.add("毒にかかった敵への会心+" + value);
            case CONTAGION -> lines.add("毒にかかった敵が倒れると、まわり" + radius + "ブロックの敵に毒を" + value + "つ重ねる");
            case CHAIN_MASTERY -> lines.add("連撃1つごとのダメージ+" + value + "%（足す分）");
            case IRON_BODY -> lines.add("防御+" + value + "%");
            case BATTLE_AURA -> lines.add(gaugeName + "が50以上ある間、攻撃+" + value + "%");
            case FLOWING_KI -> lines.add("【極】の技で連撃を使うと、連撃1つにつき最大HPの" + value + "%が戻る");
            case FINISHING_BLOW -> lines.add("【極】の技のダメージ+" + value + "%。連撃が最大のときは必ず会心");
            case ALLY_FIST -> lines.add("まわり" + radius + "ブロックに近接で戦う仲間がいると、素早さ+" + value + "%");
            case COUNTER_MASTERY -> lines.add("受け流しからの反撃のダメージ+" + value + "%。反撃すると" + gaugeName + "が10たまる");
            case SOFT_WAY -> lines.add("回避+" + value + "%。かわすと" + gaugeName + "が8たまる");
            case CALM_BREATH -> lines.add("戦いの間、" + gaugeName + "が50以上あると、毎秒最大HPの" + value + "%が戻る");
            case READ_FLOW -> lines.add("受け流し・回避の後3秒は、次のスキルが同じ技でも連撃をつなぎ、ダメージ+" + value + "%");
            case STEADFAST_SHIELD -> lines.add("城壁の構え・受け流し・誓いの円陣の間、受けるダメージがさらに-" + value + "%");
            case GUARDIAN_HEART -> lines.add("まわり" + radius + "ブロックの仲間のHPが3割を切ると、" + gaugeName + "が20たまり、5秒受けるダメージ-"
                    + value + "%（20秒に1回）");
            case SCHOOL_POWER -> lines.add("この流派のスキルのダメージ+" + value + "%");
            case BLADE_CHAIN -> lines.add("3秒以内に、この流派の違うスキルを続けて当てると、1つごとにダメージ+" + value + "%（3つまで）");
            case STAR_GUIDE -> lines.add("星痕のある敵は、星痕1つにつき、誰から受けるダメージも+" + value + "%");
            case STARRY_SKY -> lines.add("星痕を弾けさせると、星痕1つにつき、この流派のスキルのクールダウンが" + value + "秒縮む");
            case THUNDER_MASTERY -> lines.add("感電した敵へのダメージ+" + value + "%。感電が2秒長く続く");
            case PERMAFROST -> lines.add("凍結を砕くと、まわりの敵に魔力の" + Ranked.trim(passive.value(rank) * 100.0) + "%のダメージと凍え");
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
                case "hunter_mark" -> {
                    return "標";
                }
                case "rooted" -> {
                    return "縫い止め";
                }
                case "poison" -> {
                    return "毒";
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

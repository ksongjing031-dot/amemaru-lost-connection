package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.combat.AfflictionService;
import jp.amemaru.lostconnection.combat.StatusService;
import jp.amemaru.lostconnection.equipment.GearItem;
import jp.amemaru.lostconnection.item.AmemaruWeaponItem;
import jp.amemaru.lostconnection.item.WeaponKind;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.PassiveDef;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.player.BuffState;
import jp.amemaru.lostconnection.player.CollectStatsEvent;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

/**
 * パッシブスキルと、職業・見習い装備によるステータスの上乗せ。
 *
 * <p>ステータスに効くもの（職業の伸び方・「守る者の誇り」・「魔力の共鳴」・「闘志」・「鋼の肉体」・「闘気」・「仲間の拳」・
 * 「鷹の目」・「闇の刃」・「身軽な射手」と、加護の灯・鷹の眼差しの強化）は {@link CollectStatsEvent} で足し、ステータス画面にも出る。
 * 近くに味方がいるか・HPが半分を切ったか・強化がかかっているかは0.5秒ごとに見直し、変わったらすぐ計算し直す。</p>
 * <p>ダメージにかかるもの（砕きの心得・氷の心臓・深冷・星の残光など）は、スキルを使った瞬間に {@link SkillContext} に写す。
 * Phase 6 その2で足したパッシブの多くは {@code CombatBonuses}・{@code HitHooks}・{@code HealService} から、ここの関数で読む。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class PassiveService {
    /** 習得しているパッシブと段階（{@code skill} は、そのパッシブのスキル。流派を見るのに使う）。 */
    public record Active(PassiveDef def, int rank, SkillDef skill) {
        public double value() {
            return def.value(rank);
        }

        public double radius() {
            return def.radius(rank);
        }

        /** このパッシブがある流派。 */
        public String school() {
            return skill.school();
        }
    }

    /** 深冷：凍えの時間の倍率。 */
    public static final double DEEP_CHILL_DURATION = 1.5;
    /** 闘志が効く、HPの割合。 */
    private static final double FIGHTING_SPIRIT_HP = 0.5;
    /** 闘気・静の呼吸が効く、気の量。 */
    public static final double KI_THRESHOLD = 50.0;

    /** 近くに味方がいるかの、前回の結果（変わったら計算し直す）。 */
    private static final Map<UUID, Integer> LAST_CONDITIONS = new HashMap<>();

    private PassiveService() {
    }

    /** 今の職業で習得している、その種類のパッシブ（なければ null）。同じ種類がいくつかあれば、強い方。 */
    @Nullable
    public static Active find(PlayerData data, PassiveDef.Type type) {
        if (!data.hasCombatJob()) {
            return null;
        }
        SkillRegistry.Snapshot snapshot = SkillRegistry.get();
        Active best = null;
        for (Entry<ResourceLocation, Integer> entry : data.getLearnedSkills().entrySet()) {
            SkillDef skill = snapshot.skill(entry.getKey());
            if (skill == null || skill.passive() == null || skill.passive().type() != type || !skill.job().equals(data.getCombatJob())) {
                continue;
            }
            int rank = Math.min(entry.getValue(), skill.maxRank());
            Active active = new Active(skill.passive(), rank, skill);
            if (best == null || active.value() > best.value()) {
                best = active;
            }
        }
        return best;
    }

    /** その種類のパッシブの値（なければ0）。 */
    public static double value(PlayerData data, PassiveDef.Type type) {
        Active active = find(data, type);
        return active == null ? 0.0 : active.value();
    }

    /** そのスキルの流派に効く、その種類のパッシブの値（流派が違えば0）。 */
    public static double schoolValue(PlayerData data, PassiveDef.Type type, @Nullable SkillDef skill) {
        if (skill == null) {
            return 0.0;
        }
        Active active = find(data, type);
        return active != null && active.school().equals(skill.school()) && active.skill().job().equals(skill.job()) ? active.value() : 0.0;
    }

    /** 鉄壁：盾をもう片方の手に持っているとき、受けるダメージを減らす割合（%）。 */
    public static double ironWallReduction(ServerPlayer player, PlayerData data) {
        Active active = find(data, PassiveDef.Type.IRON_WALL);
        if (active == null || !holdsShield(player)) {
            return 0.0;
        }
        return Math.min(95.0, active.value());
    }

    /** 盾を持ち、両手持ちの武器を持っていないか。 */
    public static boolean holdsShield(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof GearItem weapon && weapon.isTwoHanded()) {
            return false;
        }
        return player.getOffhandItem().getItem() instanceof ShieldItem;
    }

    /** 弓を持っているか（身軽な射手・弓の通常の射撃）。 */
    public static boolean holdsBow(LivingEntity player) {
        return player.getMainHandItem().getItem() instanceof AmemaruWeaponItem weapon && weapon.kind() == WeaponKind.BOW;
    }

    /** 詠唱短縮：詠唱の時間にかける倍率。 */
    public static double castMultiplier(PlayerData data) {
        Active active = find(data, PassiveDef.Type.SWIFT_CHANT);
        return active == null ? 1.0 : Math.max(0.05, 1.0 - active.value() / 100.0);
    }

    /** マナの泉：MPの自然回復にかける倍率。 */
    public static double mpRegenMultiplier(PlayerData data) {
        Active active = find(data, PassiveDef.Type.MANA_SPRING);
        return active == null ? 1.0 : 1.0 + active.value() / 100.0;
    }

    /** 挑発の極意：自分が挑発した敵の攻撃を弱める割合（%）。 */
    public static double tauntMasteryReduction(PlayerData data) {
        Active active = find(data, PassiveDef.Type.TAUNT_MASTERY);
        return active == null ? 0.0 : Math.min(95.0, active.value());
    }

    /** 砕きの心得・氷の心臓：近接攻撃（左クリック）のダメージにかかる倍率。 */
    public static double meleeMultiplier(PlayerData data, LivingEntity target) {
        double result = 1.0;
        Active breaker = find(data, PassiveDef.Type.BREAKER);
        if (breaker != null && (StatusService.staggered(target) || StatusService.armorBroken(target))) {
            result *= 1.0 + breaker.value() / 100.0;
        }
        Active frostHeart = find(data, PassiveDef.Type.FROST_HEART);
        if (frostHeart != null && StatusService.chilledOrFrozen(target)) {
            result *= 1.0 + frostHeart.value() / 100.0;
        }
        return result;
    }

    /** 誓気の循環：使ったゲージのうち、MPとして戻る割合（0〜1）。 */
    public static double oathRefundRatio(PlayerData data) {
        Active active = find(data, PassiveDef.Type.OATH_CYCLE);
        return active == null ? 0.0 : active.value() / 100.0;
    }

    /** しみる毒：毒の重なる上限に足す数。 */
    public static int extraPoisonStacks(@Nullable PlayerData data) {
        return data == null ? 0 : (int) Math.round(value(data, PassiveDef.Type.DEEP_TOXIN));
    }

    /** 回避の確率に足す分（%。身軽・柔の理）。 */
    public static double dodgeBonus(PlayerData data) {
        return value(data, PassiveDef.Type.NIMBLE) + value(data, PassiveDef.Type.SOFT_WAY);
    }

    // ---------------------------------------------------------------- ステータスの上乗せ

    @SubscribeEvent
    public static void onCollectStats(CollectStatsEvent event) {
        ServerPlayer player = event.getPlayer();
        PlayerData data = event.getData();
        if (player == null || data == null) {
            return;
        }
        // 職業の伸び方
        JobDef job = SkillRegistry.get().job(data.getCombatJob());
        if (job != null) {
            for (StatType type : StatType.all()) {
                if (job.statPercent(type) != 0) {
                    event.addPercent(type, job.statPercent(type));
                }
                if (job.statFlat(type) != 0) {
                    event.addFlat(type, job.statFlat(type));
                }
            }
        }
        // 装備と結晶果の分は GearService、アクセサリーの分は AccessoryService が足す（Phase 3）
        int conditions = conditions(player, data);
        Active pride = find(data, PassiveDef.Type.GUARDIAN_PRIDE);
        if (pride != null && (conditions & 1) != 0) {
            event.addPercent(StatType.DEFENSE, pride.value());
        }
        Active resonance = find(data, PassiveDef.Type.MANA_RESONANCE);
        if (resonance != null && (conditions & 2) != 0) {
            event.addPercent(StatType.MAGIC, resonance.value());
        }
        Active spirit = find(data, PassiveDef.Type.FIGHTING_SPIRIT);
        if (spirit != null && (conditions & 4) != 0) {
            event.addPercent(StatType.ATTACK, spirit.value());
        }
        // Phase 6 その2
        double ironBody = value(data, PassiveDef.Type.IRON_BODY);
        if (ironBody > 0) {
            event.addPercent(StatType.DEFENSE, ironBody);
        }
        Active aura = find(data, PassiveDef.Type.BATTLE_AURA);
        if (aura != null && (conditions & 8) != 0) {
            event.addPercent(StatType.ATTACK, aura.value());
        }
        Active allyFist = find(data, PassiveDef.Type.ALLY_FIST);
        if (allyFist != null && (conditions & 16) != 0) {
            event.addPercent(StatType.SPEED, allyFist.value());
        }
        Active nimbleArcher = find(data, PassiveDef.Type.NIMBLE_ARCHER);
        if (nimbleArcher != null && (conditions & 32) != 0) {
            event.addPercent(StatType.SPEED, nimbleArcher.value());
        }
        double hawkEye = value(data, PassiveDef.Type.HAWK_EYE);
        if (hawkEye > 0) {
            event.addFlat(StatType.CRIT, hawkEye);
        }
        double darkEdge = value(data, PassiveDef.Type.DARK_EDGE);
        if (darkEdge > 0) {
            event.addFlat(StatType.CRIT_DAMAGE, darkEdge);
        }
        long now = player.level().getGameTime();
        BuffState buffs = data.getBuffs();
        if ((conditions & 64) != 0) {
            event.addPercent(StatType.DEFENSE, buffs.defenseUp(now));
        }
        if ((conditions & 128) != 0) {
            event.addFlat(StatType.CRIT, buffs.critUp(now));
        }
        LAST_CONDITIONS.put(player.getUUID(), conditions);
    }

    /**
     * パッシブ・強化が効く条件を調べる。1：「守る者の誇り」の範囲に味方がいる、2：「魔力の共鳴」の範囲に魔法を使う味方がいる、
     * 4：HPが半分を切っている（「闘志」）、8：気が50以上（「闘気」）、16：「仲間の拳」の範囲に近接の味方がいる、
     * 32：弓を持っている（「身軽な射手」）、64：加護の灯がかかっている、128：会心の強化がかかっている。
     */
    private static int conditions(ServerPlayer player, PlayerData data) {
        int result = 0;
        long now = player.level().getGameTime();
        if (find(data, PassiveDef.Type.FIGHTING_SPIRIT) != null) {
            double maxHp = data.getStats().maxHp();
            if (maxHp > 0 && data.vitalsInitialized() && data.getHp() > 0 && data.getHp() < maxHp * FIGHTING_SPIRIT_HP) {
                result |= 4;
            }
        }
        if (find(data, PassiveDef.Type.BATTLE_AURA) != null && data.getGauge() >= KI_THRESHOLD) {
            result |= 8;
        }
        if (find(data, PassiveDef.Type.NIMBLE_ARCHER) != null && holdsBow(player)) {
            result |= 32;
        }
        BuffState buffs = data.getBuffs();
        if (buffs.defenseUp(now) > 0) {
            result |= 64;
        }
        if (buffs.critUp(now) > 0) {
            result |= 128;
        }
        Active pride = find(data, PassiveDef.Type.GUARDIAN_PRIDE);
        Active resonance = find(data, PassiveDef.Type.MANA_RESONANCE);
        Active allyFist = find(data, PassiveDef.Type.ALLY_FIST);
        if (pride == null && resonance == null && allyFist == null) {
            return result;
        }
        double prideRange = pride == null ? 0 : pride.radius();
        double resonanceRange = resonance == null ? 0 : resonance.radius();
        double fistRange = allyFist == null ? 0 : allyFist.radius();
        SkillRegistry.Snapshot snapshot = SkillRegistry.get();
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || !other.isAlive() || other.isSpectator()) {
                continue;
            }
            double distanceSq = other.distanceToSqr(player);
            if (pride != null && distanceSq <= prideRange * prideRange) {
                result |= 1;
            }
            boolean needJob = (resonance != null && distanceSq <= resonanceRange * resonanceRange)
                    || (allyFist != null && distanceSq <= fistRange * fistRange);
            if (!needJob) {
                continue;
            }
            PlayerData otherData = PlayerDataCapability.get(other).orElse(null);
            JobDef otherJob = otherData == null ? null : snapshot.job(otherData.getCombatJob());
            if (otherJob == null) {
                continue;
            }
            if (resonance != null && distanceSq <= resonanceRange * resonanceRange && otherJob.hasTag("magic")) {
                result |= 2;
            }
            if (allyFist != null && distanceSq <= fistRange * fistRange && otherJob.hasTag("melee")) {
                result |= 16;
            }
        }
        return result;
    }

    /**
     * 0.5秒ごと：近くの味方が増えた・減った、HPが半分を切った・戻った、強化がかかった・切れたら、すぐにステータスを計算し直す
     * （次の tick まで待つと、その間に押したスキルが「準備中」で断られるため）。
     */
    public static void tick(ServerPlayer player, PlayerData data) {
        if (player.tickCount % 10 != 0 || !data.isStatsReady()) {
            return;
        }
        int now = conditions(player, data);
        Integer last = LAST_CONDITIONS.get(player.getUUID());
        if (last == null || last != now) {
            LevelService.recompute(player, data);
        }
    }

    /** 強化がかかった・切れたときに、すぐ計算し直す。 */
    public static void refresh(ServerPlayer player, PlayerData data) {
        if (!data.isStatsReady()) {
            return;
        }
        int now = conditions(player, data);
        Integer last = LAST_CONDITIONS.get(player.getUUID());
        if (last == null || last != now) {
            LevelService.recompute(player, data);
        }
    }

    /** 毒の敵か（弱り目・蝕み）。 */
    public static boolean poisoned(LivingEntity target) {
        return AfflictionService.poisoned(target);
    }

    /** ログアウトしたプレイヤーの記録を消す。 */
    public static void forget(UUID player) {
        LAST_CONDITIONS.remove(player);
    }
}

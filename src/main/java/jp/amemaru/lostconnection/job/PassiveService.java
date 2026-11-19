package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.equipment.GearItem;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.PassiveDef;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.player.CollectStatsEvent;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
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
 * <p>ステータスに効くもの（職業の伸び方・装備・「守る者の誇り」・「魔力の共鳴」・「闘志」）は {@link CollectStatsEvent} で足し、
 * ステータス画面にも出る。近くに味方がいるか・HPが半分を切ったかは0.5秒ごとに見直し、変わったらすぐ計算し直す。</p>
 * <p>ダメージにかかるもの（砕きの心得・氷の心臓・深冷・星の残光）は、スキルを使った瞬間に {@link SkillContext} に写す。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class PassiveService {
    /** 習得しているパッシブと段階。 */
    public record Active(PassiveDef def, int rank) {
        public double value() {
            return def.value(rank);
        }

        public double radius() {
            return def.radius(rank);
        }
    }

    /** 深冷：凍えの時間の倍率。 */
    public static final double DEEP_CHILL_DURATION = 1.5;
    /** 闘志が効く、HPの割合。 */
    private static final double FIGHTING_SPIRIT_HP = 0.5;

    /** 近くに味方がいるかの、前回の結果（変わったら計算し直す）。 */
    private static final Map<UUID, Integer> LAST_CONDITIONS = new HashMap<>();

    private PassiveService() {
    }

    /** 今の職業で習得している、その種類のパッシブ（なければ null）。 */
    @Nullable
    public static Active find(PlayerData data, PassiveDef.Type type) {
        if (!data.hasCombatJob()) {
            return null;
        }
        SkillRegistry.Snapshot snapshot = SkillRegistry.get();
        Active best = null;
        for (Entry<net.minecraft.resources.ResourceLocation, Integer> entry : data.getLearnedSkills().entrySet()) {
            SkillDef skill = snapshot.skill(entry.getKey());
            if (skill == null || skill.passive() == null || skill.passive().type() != type || !skill.job().equals(data.getCombatJob())) {
                continue;
            }
            int rank = Math.min(entry.getValue(), skill.maxRank());
            Active active = new Active(skill.passive(), rank);
            if (best == null || active.value() > best.value()) {
                best = active;
            }
        }
        return best;
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
    public static double meleeMultiplier(PlayerData data, net.minecraft.world.entity.LivingEntity target) {
        double result = 1.0;
        Active breaker = find(data, PassiveDef.Type.BREAKER);
        if (breaker != null && (jp.amemaru.lostconnection.combat.StatusService.staggered(target)
                || jp.amemaru.lostconnection.combat.StatusService.armorBroken(target))) {
            result *= 1.0 + breaker.value() / 100.0;
        }
        Active frostHeart = find(data, PassiveDef.Type.FROST_HEART);
        if (frostHeart != null && jp.amemaru.lostconnection.combat.StatusService.chilledOrFrozen(target)) {
            result *= 1.0 + frostHeart.value() / 100.0;
        }
        return result;
    }

    /** 誓気の循環：使ったゲージのうち、MPとして戻る割合（0〜1）。 */
    public static double oathRefundRatio(PlayerData data) {
        Active active = find(data, PassiveDef.Type.OATH_CYCLE);
        return active == null ? 0.0 : active.value() / 100.0;
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
        // 近くに味方がいると上がるパッシブ
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
        LAST_CONDITIONS.put(player.getUUID(), conditions);
    }

    /**
     * パッシブが効く条件を調べる。1：「守る者の誇り」の範囲に味方がいる、2：「魔力の共鳴」の範囲に魔法を使う味方がいる、
     * 4：HPが半分を切っている（「闘志」）。
     */
    private static int conditions(ServerPlayer player, PlayerData data) {
        int result = 0;
        if (find(data, PassiveDef.Type.FIGHTING_SPIRIT) != null) {
            double maxHp = data.getStats().maxHp();
            if (maxHp > 0 && data.vitalsInitialized() && data.getHp() > 0 && data.getHp() < maxHp * FIGHTING_SPIRIT_HP) {
                result |= 4;
            }
        }
        Active pride = find(data, PassiveDef.Type.GUARDIAN_PRIDE);
        Active resonance = find(data, PassiveDef.Type.MANA_RESONANCE);
        if (pride == null && resonance == null) {
            return result;
        }
        double prideRange = pride == null ? 0 : pride.radius();
        double resonanceRange = resonance == null ? 0 : resonance.radius();
        SkillRegistry.Snapshot snapshot = SkillRegistry.get();
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || !other.isAlive() || other.isSpectator()) {
                continue;
            }
            double distanceSq = other.distanceToSqr(player);
            if (pride != null && distanceSq <= prideRange * prideRange) {
                result |= 1;
            }
            if (resonance != null && distanceSq <= resonanceRange * resonanceRange) {
                PlayerData otherData = PlayerDataCapability.get(other).orElse(null);
                JobDef otherJob = otherData == null ? null : snapshot.job(otherData.getCombatJob());
                if (otherJob != null && otherJob.hasTag("magic")) {
                    result |= 2;
                }
            }
        }
        return result;
    }

    /**
     * 0.5秒ごと：近くの味方が増えた・減った、HPが半分を切った・戻ったら、すぐにステータスを計算し直す
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



    /** ログアウトしたプレイヤーの記録を消す。 */
    public static void forget(UUID player) {
        LAST_CONDITIONS.remove(player);
    }
}

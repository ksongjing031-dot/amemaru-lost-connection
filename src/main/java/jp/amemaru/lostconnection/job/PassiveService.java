package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.item.ApprenticeShieldItem;
import jp.amemaru.lostconnection.item.ApprenticeWeaponItem;
import jp.amemaru.lostconnection.item.GearStats;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.PassiveDef;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.player.CollectStatsEvent;
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
 * <p>ステータスに効くもの（職業の伸び方・装備・「守る者の誇り」・「魔力の共鳴」）は {@link CollectStatsEvent} で足し、
 * ステータス画面にも出る。近くに味方がいるかどうかは0.5秒ごとに見直し、変わったら計算し直す。</p>
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
        if (main.getItem() instanceof ApprenticeWeaponItem weapon && weapon.isTwoHanded()) {
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
        // 見習い装備（手に持っている武器と、もう片方の手の盾）
        ItemStack main = player.getMainHandItem();
        boolean twoHanded = false;
        if (main.getItem() instanceof ApprenticeWeaponItem weapon) {
            addGear(event, weapon.stats());
            twoHanded = weapon.isTwoHanded();
        }
        if (!twoHanded && player.getOffhandItem().getItem() instanceof ApprenticeShieldItem shield) {
            addGear(event, shield.stats());
        }
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
        LAST_CONDITIONS.put(player.getUUID(), conditions);
    }

    private static void addGear(CollectStatsEvent event, GearStats stats) {
        for (Map.Entry<StatType, Double> entry : stats.values().entrySet()) {
            event.addFlat(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 近くに味方がいるかを調べる。1：「守る者の誇り」の範囲に味方がいる、2：「魔力の共鳴」の範囲に魔法を使う味方がいる。
     */
    private static int conditions(ServerPlayer player, PlayerData data) {
        int result = 0;
        Active pride = find(data, PassiveDef.Type.GUARDIAN_PRIDE);
        Active resonance = find(data, PassiveDef.Type.MANA_RESONANCE);
        if (pride == null && resonance == null) {
            return 0;
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

    /** 0.5秒ごと：近くの味方が増えた・減ったら、ステータスを計算し直す。 */
    public static void tick(ServerPlayer player, PlayerData data) {
        if (player.tickCount % 10 != 0 || !data.isStatsReady()) {
            return;
        }
        int now = conditions(player, data);
        Integer last = LAST_CONDITIONS.get(player.getUUID());
        if (last == null || last != now) {
            data.invalidateStats();
        }
    }

    /** 持ち物を持ちかえたら、装備の分を計算し直す。 */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        EquipmentSlot slot = event.getSlot();
        if (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) {
            return;
        }
        PlayerDataCapability.get(player).ifPresent(PlayerData::invalidateStats);
    }

    /** ログアウトしたプレイヤーの記録を消す。 */
    public static void forget(UUID player) {
        LAST_CONDITIONS.remove(player);
    }
}

package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import jp.amemaru.lostconnection.job.GaugeService;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.player.CastState;
import jp.amemaru.lostconnection.player.GrowthDef;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.SkillCooldown;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 本人のプレイヤーデータを送る（Phase 0-B 5章 {@code SyncPlayerData}）。
 *
 * <p>「全体、または変わったまとまりだけ」を送る。まとまりは {@link PlayerData#SYNC_GROWTH}・{@link PlayerData#SYNC_STATS}・
 * {@link PlayerData#SYNC_VITALS}・{@link PlayerData#SYNC_JOB}・{@link PlayerData#SYNC_SKILLS}・{@link PlayerData#SYNC_GAUGE}・
 * {@link PlayerData#SYNC_COOLDOWNS}・{@link PlayerData#SYNC_CAST} の組み合わせ。職業とスキルは、定義の一覧
 * （{@code SyncDefinitionsS2C}）の番号で送る。クールダウンと詠唱は「残りの tick」で送る。</p>
 */
public final class SyncPlayerDataS2C {
    private static final int MAX_ARRAY = 64;
    private static final int MAX_SKILLS = 512;

    private final int sections;
    // 成長
    private final int level;
    private final int maxLevel;
    private final int exp;
    private final int expToNext;
    private final int statPoints;
    private final int skillPoints;
    private final int[] allocated;
    // ステータス
    private final double[] stats;
    // HPとMP
    private final double hp;
    private final double mp;
    private double barrier;
    private double barrierMax;
    // 職業
    private int defsVersion;
    private int jobIndex = -1;
    private boolean promoted;
    private boolean promotedOnce;
    // スキル
    private int[] learnedSkills = new int[0];
    private int[] learnedRanks = new int[0];
    private int[] loadout = new int[0];
    /** スキルツリーで取ったステータスの節（11月24日）。 */
    private String[] treeNodes = new String[0];
    // ゲージ
    private float gauge;
    private int gaugeMax;
    /** モンクの連撃の数と、消えるまでの tick（Phase 6 その2）。 */
    private int combo;
    private int comboRemaining;
    // クールダウン
    private int[] cooldownSkills = new int[0];
    private int[] cooldownRemaining = new int[0];
    private int[] cooldownTotal = new int[0];
    // 詠唱
    private int castSkill = -1;
    private int castRemaining;
    private int castTotal;
    private boolean castCharging;

    private SyncPlayerDataS2C(int sections, int level, int maxLevel, int exp, int expToNext, int statPoints,
                              int skillPoints, int[] allocated, double[] stats, double hp, double mp) {
        this.sections = sections;
        this.level = level;
        this.maxLevel = maxLevel;
        this.exp = exp;
        this.expToNext = expToNext;
        this.statPoints = statPoints;
        this.skillPoints = skillPoints;
        this.allocated = allocated;
        this.stats = stats;
        this.hp = hp;
        this.mp = mp;
    }

    /**
     * サーバーで、今のデータから作る。
     *
     * @param levelCap 今の章のレベルの上限（Phase 3。上限では経験値の代わりに結晶の欠片がたまるため、画面では「上限」と出す）
     */
    public static SyncPlayerDataS2C of(PlayerData data, GrowthDef growth, int levelCap, int sections, long now) {
        int[] allocated = new int[StatType.ALLOCATABLE_COUNT];
        for (StatType type : StatType.allocatable()) {
            allocated[type.allocIndex()] = data.getAllocated(type);
        }
        SyncPlayerDataS2C message = new SyncPlayerDataS2C(
                sections,
                data.getLevel(),
                Math.min(growth.maxLevel(), levelCap),
                data.getExp(),
                data.getLevel() >= levelCap ? 0 : growth.expToNext(data.getLevel()),
                LevelService.availableStatPoints(data, growth),
                LevelService.availableSkillPoints(data, growth),
                allocated,
                data.getStats().toArray(),
                data.getHp(),
                data.getMp());
        if (data.getBuffs().barrier(now)) {
            message.barrier = data.getBuffs().barrierAmount;
            message.barrierMax = data.getBuffs().barrierMax;
        }
        SkillRegistry.Snapshot snapshot = SkillRegistry.get();
        message.defsVersion = snapshot.version();
        message.jobIndex = snapshot.jobIndex(data.getCombatJob());
        message.promoted = data.isPromoted();
        message.promotedOnce = data.hasPromotedOnce();

        Map<ResourceLocation, Integer> learned = data.getLearnedSkills();
        int count = 0;
        int[] skills = new int[learned.size()];
        int[] ranks = new int[learned.size()];
        for (Map.Entry<ResourceLocation, Integer> entry : learned.entrySet()) {
            int index = snapshot.skillIndex(entry.getKey());
            if (index >= 0) {
                skills[count] = index;
                ranks[count] = entry.getValue();
                count++;
            }
        }
        message.learnedSkills = java.util.Arrays.copyOf(skills, count);
        message.learnedRanks = java.util.Arrays.copyOf(ranks, count);
        message.loadout = new int[PlayerData.LOADOUT_SIZE];
        for (int i = 0; i < PlayerData.LOADOUT_SIZE; i++) {
            message.loadout[i] = snapshot.skillIndex(data.getLoadout(i));
        }
        message.treeNodes = data.getTreeNodes().toArray(new String[0]);

        message.gauge = (float) data.getGauge();
        message.gaugeMax = GaugeService.max(data);
        message.combo = data.getCombo();
        message.comboRemaining = data.getCombo() > 0 ? (int) Math.max(0L, data.getComboUntil() - now) : 0;

        Map<ResourceLocation, SkillCooldown> cooldowns = data.getCooldowns();
        int[] cdSkills = new int[cooldowns.size()];
        int[] cdRemaining = new int[cooldowns.size()];
        int[] cdTotal = new int[cooldowns.size()];
        int cdCount = 0;
        for (Map.Entry<ResourceLocation, SkillCooldown> entry : cooldowns.entrySet()) {
            int index = snapshot.skillIndex(entry.getKey());
            if (index < 0 && jp.amemaru.lostconnection.equipment.UniqueSkills.isUniqueSkillId(entry.getKey())) {
                // 11月28日：ユニーク武器の固有スキル（番号は UniqueSkills.INDEX_BASE から）
                index = jp.amemaru.lostconnection.equipment.UniqueSkills.indexOfSkill(entry.getKey());
            }
            int remaining = entry.getValue().remaining(now);
            if (index >= 0 && remaining > 0) {
                cdSkills[cdCount] = index;
                cdRemaining[cdCount] = remaining;
                cdTotal[cdCount] = entry.getValue().totalTicks();
                cdCount++;
            }
        }
        message.cooldownSkills = java.util.Arrays.copyOf(cdSkills, cdCount);
        message.cooldownRemaining = java.util.Arrays.copyOf(cdRemaining, cdCount);
        message.cooldownTotal = java.util.Arrays.copyOf(cdTotal, cdCount);

        CastState casting = data.getCasting();
        if (casting != null) {
            message.castSkill = snapshot.skillIndex(casting.skill());
            message.castRemaining = casting.remaining(now);
            message.castTotal = casting.totalTicks();
            message.castCharging = casting.charging();
        }
        return message;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sections);
        if ((sections & PlayerData.SYNC_GROWTH) != 0) {
            buf.writeVarInt(level);
            buf.writeVarInt(maxLevel);
            buf.writeVarInt(exp);
            buf.writeVarInt(expToNext);
            buf.writeVarInt(statPoints);
            buf.writeVarInt(skillPoints);
            buf.writeVarInt(allocated.length);
            for (int value : allocated) {
                buf.writeVarInt(value);
            }
        }
        if ((sections & PlayerData.SYNC_STATS) != 0) {
            buf.writeVarInt(stats.length);
            for (double value : stats) {
                buf.writeDouble(value);
            }
        }
        if ((sections & PlayerData.SYNC_VITALS) != 0) {
            buf.writeDouble(hp);
            buf.writeDouble(mp);
            buf.writeDouble(barrier);
            buf.writeDouble(barrierMax);
        }
        if ((sections & PlayerData.SYNC_JOB) != 0) {
            buf.writeVarInt(defsVersion);
            buf.writeVarInt(jobIndex + 1);
            buf.writeBoolean(promoted);
            buf.writeBoolean(promotedOnce);
        }
        if ((sections & PlayerData.SYNC_SKILLS) != 0) {
            buf.writeVarInt(learnedSkills.length);
            for (int i = 0; i < learnedSkills.length; i++) {
                buf.writeVarInt(learnedSkills[i]);
                buf.writeVarInt(learnedRanks[i]);
            }
            buf.writeVarInt(loadout.length);
            for (int value : loadout) {
                buf.writeVarInt(value + 1);
            }
            buf.writeVarInt(treeNodes.length);
            for (String node : treeNodes) {
                buf.writeUtf(node, 64);
            }
        }
        if ((sections & PlayerData.SYNC_GAUGE) != 0) {
            buf.writeFloat(gauge);
            buf.writeVarInt(gaugeMax);
            buf.writeVarInt(combo);
            buf.writeVarInt(comboRemaining);
        }
        if ((sections & PlayerData.SYNC_COOLDOWNS) != 0) {
            buf.writeVarInt(cooldownSkills.length);
            for (int i = 0; i < cooldownSkills.length; i++) {
                buf.writeVarInt(cooldownSkills[i]);
                buf.writeVarInt(cooldownRemaining[i]);
                buf.writeVarInt(cooldownTotal[i]);
            }
        }
        if ((sections & PlayerData.SYNC_CAST) != 0) {
            buf.writeVarInt(castSkill + 1);
            buf.writeVarInt(castRemaining);
            buf.writeVarInt(castTotal);
            buf.writeBoolean(castCharging);
        }
    }

    public static SyncPlayerDataS2C decode(FriendlyByteBuf buf) {
        int sections = buf.readVarInt();
        int level = 0;
        int maxLevel = 0;
        int exp = 0;
        int expToNext = 0;
        int statPoints = 0;
        int skillPoints = 0;
        int[] allocated = new int[0];
        double[] stats = new double[0];
        double hp = 0;
        double mp = 0;
        double barrier = 0;
        double barrierMax = 0;
        if ((sections & PlayerData.SYNC_GROWTH) != 0) {
            level = buf.readVarInt();
            maxLevel = buf.readVarInt();
            exp = buf.readVarInt();
            expToNext = buf.readVarInt();
            statPoints = buf.readVarInt();
            skillPoints = buf.readVarInt();
            allocated = new int[checkLength(buf.readVarInt())];
            for (int i = 0; i < allocated.length; i++) {
                allocated[i] = buf.readVarInt();
            }
        }
        if ((sections & PlayerData.SYNC_STATS) != 0) {
            stats = new double[checkLength(buf.readVarInt())];
            for (int i = 0; i < stats.length; i++) {
                stats[i] = buf.readDouble();
            }
        }
        if ((sections & PlayerData.SYNC_VITALS) != 0) {
            hp = buf.readDouble();
            mp = buf.readDouble();
            barrier = buf.readDouble();
            barrierMax = buf.readDouble();
        }
        SyncPlayerDataS2C message = new SyncPlayerDataS2C(sections, level, maxLevel, exp, expToNext, statPoints, skillPoints,
                allocated, stats, hp, mp);
        message.barrier = barrier;
        message.barrierMax = barrierMax;
        if ((sections & PlayerData.SYNC_JOB) != 0) {
            message.defsVersion = buf.readVarInt();
            message.jobIndex = buf.readVarInt() - 1;
            message.promoted = buf.readBoolean();
            message.promotedOnce = buf.readBoolean();
        }
        if ((sections & PlayerData.SYNC_SKILLS) != 0) {
            int count = checkLength(buf.readVarInt(), MAX_SKILLS);
            message.learnedSkills = new int[count];
            message.learnedRanks = new int[count];
            for (int i = 0; i < count; i++) {
                message.learnedSkills[i] = buf.readVarInt();
                message.learnedRanks[i] = buf.readVarInt();
            }
            int slots = checkLength(buf.readVarInt(), MAX_ARRAY);
            message.loadout = new int[slots];
            for (int i = 0; i < slots; i++) {
                message.loadout[i] = buf.readVarInt() - 1;
            }
            int nodes = checkLength(buf.readVarInt(), 1024);
            message.treeNodes = new String[nodes];
            for (int i = 0; i < nodes; i++) {
                message.treeNodes[i] = buf.readUtf(64);
            }
        }
        if ((sections & PlayerData.SYNC_GAUGE) != 0) {
            message.gauge = buf.readFloat();
            message.gaugeMax = buf.readVarInt();
            message.combo = Math.min(99, buf.readVarInt());
            message.comboRemaining = buf.readVarInt();
        }
        if ((sections & PlayerData.SYNC_COOLDOWNS) != 0) {
            int count = checkLength(buf.readVarInt(), MAX_SKILLS);
            message.cooldownSkills = new int[count];
            message.cooldownRemaining = new int[count];
            message.cooldownTotal = new int[count];
            for (int i = 0; i < count; i++) {
                message.cooldownSkills[i] = buf.readVarInt();
                message.cooldownRemaining[i] = buf.readVarInt();
                message.cooldownTotal[i] = buf.readVarInt();
            }
        }
        if ((sections & PlayerData.SYNC_CAST) != 0) {
            message.castSkill = buf.readVarInt() - 1;
            message.castRemaining = buf.readVarInt();
            message.castTotal = buf.readVarInt();
            message.castCharging = buf.readBoolean();
        }
        return message;
    }

    private static int checkLength(int length) {
        return checkLength(length, MAX_ARRAY);
    }

    private static int checkLength(int length, int max) {
        if (length < 0 || length > max) {
            throw new IllegalArgumentException("Bad array length in SyncPlayerDataS2C: " + length);
        }
        return length;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSync(this));
    }

    public int sections() {
        return sections;
    }

    public int level() {
        return level;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public int exp() {
        return exp;
    }

    public int expToNext() {
        return expToNext;
    }

    public int statPoints() {
        return statPoints;
    }

    public int skillPoints() {
        return skillPoints;
    }

    public int[] allocated() {
        return allocated;
    }

    public double[] stats() {
        return stats;
    }

    public double hp() {
        return hp;
    }

    public double mp() {
        return mp;
    }

    /** 光の帳の障壁の残り（なければ0）。 */
    public double barrier() {
        return barrier;
    }

    public double barrierMax() {
        return barrierMax;
    }

    public int defsVersion() {
        return defsVersion;
    }

    public int jobIndex() {
        return jobIndex;
    }

    public boolean promoted() {
        return promoted;
    }

    public boolean promotedOnce() {
        return promotedOnce;
    }

    public int[] learnedSkills() {
        return learnedSkills;
    }

    public int[] learnedRanks() {
        return learnedRanks;
    }

    public String[] treeNodes() {
        return treeNodes;
    }

    public int[] loadout() {
        return loadout;
    }

    public float gauge() {
        return gauge;
    }

    public int combo() {
        return combo;
    }

    public int comboRemaining() {
        return comboRemaining;
    }

    public int gaugeMax() {
        return gaugeMax;
    }

    public int[] cooldownSkills() {
        return cooldownSkills;
    }

    public int[] cooldownRemaining() {
        return cooldownRemaining;
    }

    public int[] cooldownTotal() {
        return cooldownTotal;
    }

    public int castSkill() {
        return castSkill;
    }

    public int castRemaining() {
        return castRemaining;
    }

    public int castTotal() {
        return castTotal;
    }

    /** 溜め（詠唱ではなく）か。 */
    public boolean castCharging() {
        return castCharging;
    }
}

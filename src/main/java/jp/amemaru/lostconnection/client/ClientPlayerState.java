package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.network.s2c.SyncPlayerDataS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.client.Minecraft;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * サーバーから受け取った、自分のデータの写し（クライアント専用）。HUDと画面はここを読む。
 *
 * <p>数値の計算はサーバーで済んでいるため、ここでは受け取った値をそのまま持つだけ。</p>
 */
public final class ClientPlayerState {
    private static final ClientPlayerState INSTANCE = new ClientPlayerState();

    private boolean growthReceived;
    private boolean statsReceived;
    private boolean vitalsReceived;

    private int level = 1;
    private int maxLevel = 1;
    private int exp;
    private int expToNext;
    private int statPoints;
    private int skillPoints;
    private final int[] allocated = new int[StatType.ALLOCATABLE_COUNT];
    private final double[] stats = new double[StatType.COUNT];
    private double hp;
    private double mp;

    // 職業とスキル（番号は ClientDefinitions の番号）
    private int jobIndex = -1;
    private boolean promoted;
    private boolean promotedOnce;
    private final Map<Integer, Integer> learned = new HashMap<>();
    /** スキルツリーで取ったステータスの節（11月24日）。 */
    private final java.util.Set<String> treeNodes = new java.util.HashSet<>();
    private final int[] loadout = new int[PlayerData.LOADOUT_SIZE];
    private float gauge;
    private int gaugeMax;
    /** モンクの連撃の数と、消える時刻（Phase 6 その2）。 */
    private int combo;
    private long comboUntil;
    /** スキルの番号 → {使えるようになるゲーム内の時刻, クールダウンの長さ}。 */
    private final Map<Integer, long[]> cooldowns = new HashMap<>();
    private int castSkill = -1;
    private long castEnd;
    private int castTotal;
    private boolean castCharging;
    private double barrier;
    private double barrierMax;

    private ClientPlayerState() {
        Arrays.fill(loadout, -1);
    }

    public static ClientPlayerState get() {
        return INSTANCE;
    }

    /** 受け取ったまとまりだけを書きかえる。 */
    public void apply(SyncPlayerDataS2C message) {
        int sections = message.sections();
        if ((sections & PlayerData.SYNC_GROWTH) != 0) {
            level = message.level();
            maxLevel = message.maxLevel();
            exp = message.exp();
            expToNext = message.expToNext();
            statPoints = message.statPoints();
            skillPoints = message.skillPoints();
            int[] received = message.allocated();
            for (int i = 0; i < allocated.length; i++) {
                allocated[i] = i < received.length ? received[i] : 0;
            }
            growthReceived = true;
        }
        if ((sections & PlayerData.SYNC_STATS) != 0) {
            double[] received = message.stats();
            for (int i = 0; i < stats.length; i++) {
                stats[i] = i < received.length ? received[i] : 0;
            }
            statsReceived = true;
        }
        if ((sections & PlayerData.SYNC_VITALS) != 0) {
            if (vitalsReceived && message.hp() < hp - 0.5) {
                // 11月24日：HPが減った（戦っている）。右上のクエストの一覧を、戦いの間はしまうため
                noteCombat();
            }
            hp = message.hp();
            mp = message.mp();
            vitalsReceived = true;
            barrier = message.barrier();
            barrierMax = message.barrierMax();
        }
        long now = gameTime();
        if ((sections & PlayerData.SYNC_JOB) != 0) {
            jobIndex = message.jobIndex();
            promoted = message.promoted();
            promotedOnce = message.promotedOnce();
        }
        if ((sections & PlayerData.SYNC_SKILLS) != 0) {
            learned.clear();
            int[] skills = message.learnedSkills();
            int[] ranks = message.learnedRanks();
            for (int i = 0; i < skills.length && i < ranks.length; i++) {
                learned.put(skills[i], ranks[i]);
            }
            int[] received = message.loadout();
            for (int i = 0; i < loadout.length; i++) {
                loadout[i] = i < received.length ? received[i] : -1;
            }
            treeNodes.clear();
            java.util.Collections.addAll(treeNodes, message.treeNodes());
        }
        if ((sections & PlayerData.SYNC_GAUGE) != 0) {
            gauge = message.gauge();
            gaugeMax = message.gaugeMax();
            combo = message.combo();
            comboUntil = message.combo() > 0 ? now + message.comboRemaining() : 0L;
        }
        if ((sections & PlayerData.SYNC_COOLDOWNS) != 0) {
            cooldowns.clear();
            int[] skills = message.cooldownSkills();
            for (int i = 0; i < skills.length; i++) {
                cooldowns.put(skills[i], new long[]{now + message.cooldownRemaining()[i], message.cooldownTotal()[i]});
            }
        }
        if ((sections & PlayerData.SYNC_CAST) != 0) {
            castSkill = message.castSkill();
            castEnd = now + message.castRemaining();
            castTotal = message.castTotal();
            castCharging = castSkill >= 0 && message.castCharging();
        }
    }

    /** 最後に戦った時刻（HPが減った・スキルを使った。{@link net.minecraft.Util#getMillis()}）。 */
    private long lastCombatMillis;

    /** 戦った印（HPが減った・スキルを使った）。11月24日：戦いの間は、右上のクエストの一覧などをしまう。 */
    public void noteCombat() {
        lastCombatMillis = net.minecraft.Util.getMillis();
    }

    /** 戦っている最中か（最後に戦ってから {@code windowMillis} のうち、または狙いを固定している間）。 */
    public boolean inCombat(long windowMillis) {
        return net.minecraft.Util.getMillis() - lastCombatMillis < windowMillis || TargetLock.locked();
    }

    /** クライアントのゲーム内の時刻（ワールドに入っていなければ0）。 */
    public static long gameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    /** ログアウトしたときに消す。 */
    /** 所持金（ノット。Phase 4） */
    private long knots;

    public long knots() {
        return knots;
    }

    public void setKnots(long value) {
        knots = Math.max(0L, value);
    }

    public void reset() {
        knots = 0;
        growthReceived = false;
        statsReceived = false;
        vitalsReceived = false;
        level = 1;
        maxLevel = 1;
        exp = 0;
        expToNext = 0;
        statPoints = 0;
        skillPoints = 0;
        java.util.Arrays.fill(allocated, 0);
        java.util.Arrays.fill(stats, 0);
        hp = 0;
        mp = 0;
        jobIndex = -1;
        promoted = false;
        promotedOnce = false;
        treeNodes.clear();
        learned.clear();
        Arrays.fill(loadout, -1);
        gauge = 0;
        gaugeMax = 0;
        cooldowns.clear();
        castSkill = -1;
        castCharging = false;
        barrier = 0;
        barrierMax = 0;
    }

    /** すべてのまとまりを一度は受け取ったか（このModのサーバーにつながっているか）。 */
    public boolean hasData() {
        return growthReceived && statsReceived && vitalsReceived;
    }

    public int level() {
        return level;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public boolean isMaxLevel() {
        return level >= maxLevel;
    }

    public int exp() {
        return exp;
    }

    public int expToNext() {
        return expToNext;
    }

    /** 次のレベルまでの進み具合（0〜1）。最大レベルでは1。 */
    public float expProgress() {
        if (isMaxLevel() || expToNext <= 0) {
            return 1.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (float) exp / expToNext));
    }

    /** スキルツリーで、そのステータスの節を取っているか（11月24日）。 */
    public boolean hasTreeNode(String node) {
        return treeNodes.contains(node);
    }

    public int treeNodeCount() {
        return treeNodes.size();
    }

    public int statPoints() {
        return statPoints;
    }

    public int skillPoints() {
        return skillPoints;
    }

    public int allocated(StatType type) {
        int index = type.allocIndex();
        return index < 0 ? 0 : allocated[index];
    }

    public double stat(StatType type) {
        return stats[type.ordinal()];
    }

    public double hp() {
        return hp;
    }

    public double maxHp() {
        return stat(StatType.HP);
    }

    public double mp() {
        return mp;
    }

    public double maxMp() {
        return stat(StatType.MP);
    }

    // ---------------------------------------------------------------- 職業とスキル

    /** 職業の番号（なければ -1）。 */
    public int jobIndex() {
        return jobIndex;
    }

    public boolean hasJob() {
        return jobIndex >= 0;
    }

    public boolean promoted() {
        return promoted;
    }

    public boolean promotedOnce() {
        return promotedOnce;
    }

    /** 習得したスキルの段階（覚えていなければ0）。 */
    public int rank(int skillIndex) {
        Integer rank = learned.get(skillIndex);
        return rank == null ? 0 : rank;
    }

    /** スキル枠に入っているスキルの番号（空なら -1）。 */
    public int loadout(int slot) {
        return slot >= 0 && slot < loadout.length ? loadout[slot] : -1;
    }

    /** そのスキルが入っている枠（なければ -1）。 */
    public int slotOf(int skillIndex) {
        for (int i = 0; i < loadout.length; i++) {
            if (loadout[i] == skillIndex) {
                return i;
            }
        }
        return -1;
    }

    public float gauge() {
        return gauge;
    }

    /** 連撃の数（消えていれば0）。 */
    public int combo() {
        return combo > 0 && gameTime() < comboUntil ? combo : 0;
    }

    /** 連撃が消えるまでの tick。 */
    public int comboRemaining() {
        return combo() > 0 ? (int) Math.max(0L, comboUntil - gameTime()) : 0;
    }

    public int gaugeMax() {
        return gaugeMax;
    }

    /** クールダウンの残り（tick。終わっていれば0）。 */
    public int cooldownRemaining(int skillIndex) {
        long[] entry = cooldowns.get(skillIndex);
        return entry == null ? 0 : (int) Math.max(0L, entry[0] - gameTime());
    }

    /** クールダウンの長さ（tick）。 */
    public int cooldownTotal(int skillIndex) {
        long[] entry = cooldowns.get(skillIndex);
        return entry == null ? 0 : (int) entry[1];
    }

    /** 詠唱中・溜め中のスキル（なければ -1）。溜めは、離して放つまで続く。 */
    public int castSkill() {
        if (castSkill >= 0 && castCharging) {
            return castSkill;
        }
        return castSkill >= 0 && castEnd > gameTime() - 2 ? castSkill : -1;
    }

    /** 溜めているところか。 */
    public boolean charging() {
        return castSkill >= 0 && castCharging;
    }

    /** 光の帳の障壁の残り。 */
    public double barrier() {
        return barrier;
    }

    public double barrierMax() {
        return barrierMax;
    }

    /** 詠唱の進み具合（0〜1）。 */
    public float castProgress(float partialTick) {
        if (castTotal <= 0) {
            return 0.0F;
        }
        float remaining = castEnd - gameTime() - partialTick;
        return Math.max(0.0F, Math.min(1.0F, 1.0F - remaining / castTotal));
    }
}

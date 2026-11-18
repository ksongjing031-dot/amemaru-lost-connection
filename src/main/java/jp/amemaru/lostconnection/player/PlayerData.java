package jp.amemaru.lostconnection.player;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * プレイヤーごとのデータ（Capability。Phase 0-B 4.1）。
 *
 * <p>Phase 1で「成長」（レベル・経験値・振り分けたステータス）と「戦闘」のうちHPとMPを持ち、Phase 2で「職業」
 * （戦闘職・上位職）と「スキル」（習得したスキルと段階・スキル枠6つ・専用ゲージ）を足した（版2）。
 * 所持金・物語などのまとまりは、使い始めるPhaseで足し、{@link #DATA_VERSION} を上げて古い保存データを読み替える。</p>
 *
 * <p>クールダウン・詠唱・専用ゲージ・強化は保存しない（ログアウトすると消える。ゲージは戦闘が終わると0）。</p>
 *
 * <p>ステータスポイントとスキルポイントの残りは保存せず、レベルと振り分けた量から毎回求める
 * （定義の数値を変えても、ずれが起きないようにするため）。</p>
 */
public final class PlayerData {
    /** 保存データの版。項目を増やしたら上げ、{@link #load} で古い版を読み替える。 */
    public static final int DATA_VERSION = 3;
    /** スキル枠の数。 */
    public static final int LOADOUT_SIZE = 6;

    /** 同期するまとまり：レベル・経験値・ポイント・振り分け。 */
    public static final int SYNC_GROWTH = 1;
    /** 同期するまとまり：計算済みのステータス。 */
    public static final int SYNC_STATS = 1 << 1;
    /** 同期するまとまり：今のHPとMP。 */
    public static final int SYNC_VITALS = 1 << 2;
    /** 同期するまとまり：職業と上位職。 */
    public static final int SYNC_JOB = 1 << 3;
    /** 同期するまとまり：習得したスキル・スキル枠・残りのスキルポイント。 */
    public static final int SYNC_SKILLS = 1 << 4;
    /** 同期するまとまり：専用ゲージ。 */
    public static final int SYNC_GAUGE = 1 << 5;
    /** 同期するまとまり：クールダウン。 */
    public static final int SYNC_COOLDOWNS = 1 << 6;
    /** 同期するまとまり：詠唱。 */
    public static final int SYNC_CAST = 1 << 7;
    /** 同期するまとまり：冒険の記録（結晶果・生産職と熟練度・図鑑・アクセサリーの表示。Phase 3）。別のパケットで送る。 */
    public static final int SYNC_ADVENTURE = 1 << 8;
    /** すべてのまとまり。 */
    public static final int SYNC_ALL = SYNC_GROWTH | SYNC_STATS | SYNC_VITALS | SYNC_JOB | SYNC_SKILLS | SYNC_GAUGE
            | SYNC_COOLDOWNS | SYNC_CAST | SYNC_ADVENTURE;

    // ---- 保存する項目
    private int level = 1;
    private int exp;
    private final int[] allocated = new int[StatType.ALLOCATABLE_COUNT];
    /** 今のHP。負の値は「まだ決めていない」（初めてのログイン）。 */
    private double hp = -1;
    /** 今のMP。負の値は「まだ決めていない」。 */
    private double mp = -1;

    // ---- 職業（保存する）
    @Nullable
    private ResourceLocation combatJob;
    private boolean promoted;
    private boolean promotedOnce;
    private final Set<ResourceLocation> kitsReceived = new LinkedHashSet<>();
    private boolean freeResetItemsGiven;
    private int jobChanges;

    // ---- スキル（保存する）
    private final Map<ResourceLocation, Integer> learned = new LinkedHashMap<>();
    private final ResourceLocation[] loadout = new ResourceLocation[LOADOUT_SIZE];

    // ---- スキル（保存しない）
    private double gauge;
    private final Map<ResourceLocation, SkillCooldown> cooldowns = new HashMap<>();
    @Nullable
    private CastState casting;
    private final BuffState buffs = new BuffState();
    private long skillWindowStart = Long.MIN_VALUE / 4;
    private int skillRequestsInWindow;
    private float lastAttackStrength = 1.0F;
    private String pendingConfirm = "";
    private long pendingConfirmUntil;
    @Nullable
    private BlockPos oathCrystalPos;
    @Nullable
    private ResourceKey<Level> oathCrystalLevel;
    private long oathCrystalUntil;

    // ---- 冒険の記録（保存する。Phase 3）
    private final AdventureData adventure = new AdventureData(() -> markDirty(SYNC_ADVENTURE));

    // ---- 保存しない項目
    private final StatBlock stats = new StatBlock();
    private boolean statsReady;
    private long lastCombatTick = Long.MIN_VALUE / 4;
    private float expectedVanillaHealth = -1.0F;
    private int dirty = SYNC_ALL;

    // ---------------------------------------------------------------- 成長

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        int next = Math.max(1, level);
        if (next != this.level) {
            this.level = next;
            dirty |= SYNC_GROWTH;
        }
    }

    /** 今のレベルの中でたまっている経験値。 */
    public int getExp() {
        return exp;
    }

    public void setExp(int exp) {
        int next = Math.max(0, exp);
        if (next != this.exp) {
            this.exp = next;
            dirty |= SYNC_GROWTH;
        }
    }

    /** 振り分けたポイントの数。振り分けられない種類は 0。 */
    public int getAllocated(StatType type) {
        int index = type.allocIndex();
        return index < 0 ? 0 : allocated[index];
    }

    public void setAllocated(StatType type, int points) {
        int index = type.allocIndex();
        if (index < 0) {
            return;
        }
        int next = Math.max(0, points);
        if (next != allocated[index]) {
            allocated[index] = next;
            dirty |= SYNC_GROWTH;
        }
    }

    /** 振り分けたポイントの合計。 */
    public int totalAllocated() {
        int total = 0;
        for (int points : allocated) {
            total += points;
        }
        return total;
    }

    /** 振り分けをすべて戻す。 */
    public void clearAllocated() {
        for (int i = 0; i < allocated.length; i++) {
            if (allocated[i] != 0) {
                allocated[i] = 0;
                dirty |= SYNC_GROWTH;
            }
        }
    }

    // ---------------------------------------------------------------- ステータス

    /** 計算済みのステータス（{@link #isStatsReady()} が false の間は 0）。 */
    public StatBlock getStats() {
        return stats;
    }

    public boolean isStatsReady() {
        return statsReady;
    }

    /**
     * 計算し直したステータスを入れる。最大HP・最大MPが増えたときは今の値も同じだけ増やし、
     * 減ったときは新しい最大値に収める。
     */
    public void applyStats(StatBlock block) {
        boolean changed = !statsReady || !stats.sameAs(block);
        double oldMaxHp = statsReady ? stats.maxHp() : -1;
        double oldMaxMp = statsReady ? stats.maxMp() : -1;
        stats.copyFrom(block);
        statsReady = true;

        if (hp >= 0) {
            double next = hp;
            if (oldMaxHp > 0 && block.maxHp() > oldMaxHp) {
                next += block.maxHp() - oldMaxHp;
            }
            setHp(next);
        }
        if (mp >= 0) {
            double next = mp;
            if (oldMaxMp > 0 && block.maxMp() > oldMaxMp) {
                next += block.maxMp() - oldMaxMp;
            }
            setMp(next);
        }
        if (changed) {
            dirty |= SYNC_STATS | SYNC_VITALS;
        }
    }

    /** ステータスを計算し直すよう印をつける（次の tick で計算される）。 */
    public void invalidateStats() {
        statsReady = false;
    }

    // ---------------------------------------------------------------- HPとMP

    /** HPとMPが決まっているか（初めてのログインでは false）。 */
    public boolean vitalsInitialized() {
        return hp >= 0 && mp >= 0;
    }

    public double getHp() {
        return Math.max(0, hp);
    }

    /** HPを入れる（0〜最大HPに収める）。 */
    public void setHp(double value) {
        double max = statsReady ? stats.maxHp() : Double.MAX_VALUE;
        double next = Math.min(Math.max(0, value), max);
        if (next != hp) {
            hp = next;
            dirty |= SYNC_VITALS;
        }
    }

    public double getMp() {
        return Math.max(0, mp);
    }

    /** MPを入れる（0〜最大MPに収める）。 */
    public void setMp(double value) {
        double max = statsReady ? stats.maxMp() : Double.MAX_VALUE;
        double next = Math.min(Math.max(0, value), max);
        if (next != mp) {
            mp = next;
            dirty |= SYNC_VITALS;
        }
    }

    /** 最後に攻撃した・攻撃を受けたときの時刻（ゲーム内の tick）。 */
    public long getLastCombatTick() {
        return lastCombatTick;
    }

    public void markCombat(long gameTime) {
        lastCombatTick = gameTime;
    }

    /** 最後にこちらで決めたバニラの体力。外から変わったかを見分けるために使う。 */
    public float getExpectedVanillaHealth() {
        return expectedVanillaHealth;
    }

    public void setExpectedVanillaHealth(float value) {
        expectedVanillaHealth = value;
    }

    // ---------------------------------------------------------------- 職業

    /** 戦闘職（まだ選んでいなければ null）。 */
    @Nullable
    public ResourceLocation getCombatJob() {
        return combatJob;
    }

    public boolean hasCombatJob() {
        return combatJob != null;
    }

    public void setCombatJob(@Nullable ResourceLocation job) {
        if (job == null ? combatJob != null : !job.equals(combatJob)) {
            combatJob = job;
            dirty |= SYNC_JOB | SYNC_SKILLS | SYNC_GAUGE;
        }
    }

    /** 上位職に昇格しているか（今の戦闘職で）。 */
    public boolean isPromoted() {
        return promoted;
    }

    public void setPromoted(boolean value) {
        if (promoted != value) {
            promoted = value;
            dirty |= SYNC_JOB | SYNC_SKILLS;
        }
        if (value) {
            promotedOnce = true;
        }
    }

    /** 一度でも昇格したことがあるか（転職しても、昇格クエストなしで昇格できる。Phase 0-A 19章）。 */
    public boolean hasPromotedOnce() {
        return promotedOnce;
    }

    /** その職業の見習い装備を、もう受け取ったか。 */
    public boolean hasReceivedKit(ResourceLocation job) {
        return kitsReceived.contains(job);
    }

    public void markKitReceived(ResourceLocation job) {
        kitsReceived.add(job);
    }

    /** 最初の1つずつの「忘れ水」と「白紙の誓約書」を、もう受け取ったか。 */
    public boolean hasFreeResetItems() {
        return freeResetItemsGiven;
    }

    public void markFreeResetItemsGiven() {
        freeResetItemsGiven = true;
    }

    /** 戦闘職を変えた回数（最初の選択は数えない）。 */
    public int getJobChanges() {
        return jobChanges;
    }

    public void countJobChange() {
        jobChanges++;
    }

    // ---------------------------------------------------------------- スキル

    /** 習得したスキルの段階（覚えていなければ0）。 */
    public int getSkillRank(ResourceLocation skill) {
        Integer rank = learned.get(skill);
        return rank == null ? 0 : rank;
    }

    public void setSkillRank(ResourceLocation skill, int rank) {
        if (rank <= 0) {
            if (learned.remove(skill) != null) {
                dirty |= SYNC_SKILLS;
            }
            for (int i = 0; i < loadout.length; i++) {
                if (skill.equals(loadout[i])) {
                    loadout[i] = null;
                }
            }
            return;
        }
        Integer old = learned.put(skill, rank);
        if (old == null || old != rank) {
            dirty |= SYNC_SKILLS;
        }
    }

    /** 習得したすべてのスキルと段階（読み取り専用）。 */
    public Map<ResourceLocation, Integer> getLearnedSkills() {
        return Collections.unmodifiableMap(learned);
    }

    /** 習得したスキルとスキル枠をすべて消す。 */
    public void clearSkills() {
        if (!learned.isEmpty()) {
            learned.clear();
            dirty |= SYNC_SKILLS;
        }
        for (int i = 0; i < loadout.length; i++) {
            if (loadout[i] != null) {
                loadout[i] = null;
                dirty |= SYNC_SKILLS;
            }
        }
    }

    /** スキル枠に入っているスキル（空なら null）。 */
    @Nullable
    public ResourceLocation getLoadout(int slot) {
        return slot >= 0 && slot < loadout.length ? loadout[slot] : null;
    }

    public void setLoadout(int slot, @Nullable ResourceLocation skill) {
        if (slot < 0 || slot >= loadout.length) {
            return;
        }
        if (skill != null) {
            // 同じスキルを2つの枠に入れない
            for (int i = 0; i < loadout.length; i++) {
                if (i != slot && skill.equals(loadout[i])) {
                    loadout[i] = null;
                }
            }
        }
        loadout[slot] = skill;
        dirty |= SYNC_SKILLS;
    }

    /** スキル枠のどこかに入っているか。 */
    public int loadoutSlotOf(ResourceLocation skill) {
        for (int i = 0; i < loadout.length; i++) {
            if (skill.equals(loadout[i])) {
                return i;
            }
        }
        return -1;
    }

    /** 専用ゲージの値。 */
    public double getGauge() {
        return gauge;
    }

    public void setGauge(double value) {
        double next = Math.max(0.0, value);
        if (Math.floor(next) != Math.floor(gauge)) {
            dirty |= SYNC_GAUGE;
        }
        gauge = next;
    }

    /** スキルのクールダウン（終わっているものも残っていることがある）。 */
    @Nullable
    public SkillCooldown getCooldown(ResourceLocation skill) {
        return cooldowns.get(skill);
    }

    public void setCooldown(ResourceLocation skill, SkillCooldown cooldown) {
        cooldowns.put(skill, cooldown);
        dirty |= SYNC_COOLDOWNS;
    }

    /** すべてのクールダウン（読み取り専用）。 */
    public Map<ResourceLocation, SkillCooldown> getCooldowns() {
        return Collections.unmodifiableMap(cooldowns);
    }

    /** 終わったクールダウンを消す。 */
    public void pruneCooldowns(long now) {
        cooldowns.values().removeIf(cooldown -> cooldown.endTick() <= now);
    }

    public void clearCooldowns() {
        if (!cooldowns.isEmpty()) {
            cooldowns.clear();
            dirty |= SYNC_COOLDOWNS;
        }
    }

    @Nullable
    public CastState getCasting() {
        return casting;
    }

    public void setCasting(@Nullable CastState state) {
        casting = state;
        dirty |= SYNC_CAST;
    }

    /** スキルでかかっている、数値つきの強化。 */
    public BuffState getBuffs() {
        return buffs;
    }

    /**
     * スキルの要求の回数を数える（1秒あたりの上限を超えたら false）。Phase 0-B 5章「UseSkill は1秒に10回まで」。
     */
    public boolean allowSkillRequest(long now, int perSecond) {
        if (now - skillWindowStart >= 20) {
            skillWindowStart = now;
            skillRequestsInWindow = 0;
        }
        skillRequestsInWindow++;
        return skillRequestsInWindow <= perSecond;
    }

    /** 最後に近接攻撃したときの、攻撃のたまり具合（0〜1）。 */
    public float getLastAttackStrength() {
        return lastAttackStrength;
    }

    public void setLastAttackStrength(float value) {
        lastAttackStrength = value;
    }

    /** 「もう一度使うと実行します」の確認。同じものを時間内にもう一度使えば true を返して確認を消す。 */
    public boolean confirm(String key, long now, int windowTicks) {
        if (key.equals(pendingConfirm) && now <= pendingConfirmUntil) {
            pendingConfirm = "";
            return true;
        }
        pendingConfirm = key;
        pendingConfirmUntil = now + windowTicks;
        return false;
    }

    /** 職業選択の画面を開いた誓約の結晶の場所（画面を開いている間だけ有効）。 */
    public void setOathCrystal(@Nullable BlockPos pos, @Nullable ResourceKey<Level> level, long until) {
        oathCrystalPos = pos;
        oathCrystalLevel = level;
        oathCrystalUntil = until;
    }

    @Nullable
    public BlockPos getOathCrystalPos(long now) {
        return now <= oathCrystalUntil ? oathCrystalPos : null;
    }

    @Nullable
    public ResourceKey<Level> getOathCrystalLevel() {
        return oathCrystalLevel;
    }

    // ---------------------------------------------------------------- 冒険の記録

    /** 結晶果・生産職・図鑑など（Phase 3）。 */
    public AdventureData getAdventure() {
        return adventure;
    }

    // ---------------------------------------------------------------- 同期の印

    public void markDirty(int sections) {
        dirty |= sections;
    }

    /** 同期が必要なまとまりを返し、印を消す。 */
    public int consumeDirty() {
        int value = dirty;
        dirty = 0;
        return value;
    }

    /** 保存しない項目を初めの状態に戻す（リスポーンの後など）。 */
    public void resetTransient() {
        statsReady = false;
        lastCombatTick = Long.MIN_VALUE / 4;
        expectedVanillaHealth = -1.0F;
        gauge = 0.0;
        casting = null;
        buffs.clear();
        pendingConfirm = "";
        oathCrystalPos = null;
        oathCrystalLevel = null;
        dirty = SYNC_ALL;
    }

    // ---------------------------------------------------------------- 保存

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", DATA_VERSION);

        CompoundTag growth = new CompoundTag();
        growth.putInt("level", level);
        growth.putInt("exp", exp);
        CompoundTag alloc = new CompoundTag();
        for (StatType type : StatType.allocatable()) {
            alloc.putInt(type.id(), allocated[type.allocIndex()]);
        }
        growth.put("allocated", alloc);
        tag.put("growth", growth);

        CompoundTag vitals = new CompoundTag();
        vitals.putDouble("hp", hp);
        vitals.putDouble("mp", mp);
        tag.put("vitals", vitals);

        CompoundTag job = new CompoundTag();
        if (combatJob != null) {
            job.putString("combat", combatJob.toString());
        }
        job.putBoolean("promoted", promoted);
        job.putBoolean("promotedOnce", promotedOnce);
        ListTag kits = new ListTag();
        for (ResourceLocation kit : kitsReceived) {
            kits.add(StringTag.valueOf(kit.toString()));
        }
        job.put("kits", kits);
        job.putBoolean("freeResetItems", freeResetItemsGiven);
        job.putInt("changes", jobChanges);
        tag.put("job", job);

        CompoundTag skills = new CompoundTag();
        CompoundTag learnedTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, Integer> entry : learned.entrySet()) {
            learnedTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        skills.put("learned", learnedTag);
        ListTag loadoutTag = new ListTag();
        for (ResourceLocation skill : loadout) {
            loadoutTag.add(StringTag.valueOf(skill == null ? "" : skill.toString()));
        }
        skills.put("loadout", loadoutTag);
        // クールダウンは、ログアウトしても続く（ログインし直して使い直す、を防ぐ）。時刻はすべてのディメンションで共通のゲーム内時刻
        CompoundTag cooldownTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, SkillCooldown> entry : cooldowns.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putLong("end", entry.getValue().endTick());
            one.putInt("total", entry.getValue().totalTicks());
            cooldownTag.put(entry.getKey().toString(), one);
        }
        skills.put("cooldowns", cooldownTag);
        tag.put("skills", skills);

        tag.put("adventure", adventure.save());
        return tag;
    }

    public void load(CompoundTag tag) {
        int version = tag.getInt("version");
        if (version > DATA_VERSION) {
            AmemaruMod.LOGGER.warn("[amemaru] Player data version {} is newer than this mod ({}). Unknown fields are ignored.",
                    version, DATA_VERSION);
        }
        // 版1 → 版2：職業とスキルのまとまりが増えた。版1のデータには無いので、職業なし・スキルなしで始まる
        // 版2 → 版3：冒険の記録（結晶果・生産職・図鑑など）が増えた。版2のデータには無いので、空で始まる

        CompoundTag growth = tag.getCompound("growth");
        level = growth.contains("level") ? Math.max(1, growth.getInt("level")) : 1;
        exp = Math.max(0, growth.getInt("exp"));
        CompoundTag alloc = growth.getCompound("allocated");
        for (StatType type : StatType.allocatable()) {
            allocated[type.allocIndex()] = Math.max(0, alloc.getInt(type.id()));
        }

        CompoundTag vitals = tag.getCompound("vitals");
        hp = vitals.contains("hp") ? vitals.getDouble("hp") : -1;
        mp = vitals.contains("mp") ? vitals.getDouble("mp") : -1;

        CompoundTag job = tag.getCompound("job");
        combatJob = job.contains("combat") ? ResourceLocation.tryParse(job.getString("combat")) : null;
        promoted = job.getBoolean("promoted");
        promotedOnce = job.getBoolean("promotedOnce") || promoted;
        kitsReceived.clear();
        ListTag kits = job.getList("kits", Tag.TAG_STRING);
        for (int i = 0; i < kits.size(); i++) {
            ResourceLocation kit = ResourceLocation.tryParse(kits.getString(i));
            if (kit != null) {
                kitsReceived.add(kit);
            }
        }
        freeResetItemsGiven = job.getBoolean("freeResetItems");
        jobChanges = Math.max(0, job.getInt("changes"));

        CompoundTag skills = tag.getCompound("skills");
        learned.clear();
        CompoundTag learnedTag = skills.getCompound("learned");
        for (String key : learnedTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            int rank = learnedTag.getInt(key);
            if (id != null && rank > 0) {
                learned.put(id, rank);
            }
        }
        ListTag loadoutTag = skills.getList("loadout", Tag.TAG_STRING);
        for (int i = 0; i < loadout.length; i++) {
            String value = i < loadoutTag.size() ? loadoutTag.getString(i) : "";
            loadout[i] = value.isEmpty() ? null : ResourceLocation.tryParse(value);
        }

        cooldowns.clear();
        CompoundTag cooldownTag = skills.getCompound("cooldowns");
        for (String key : cooldownTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            CompoundTag one = cooldownTag.getCompound(key);
            int total = one.getInt("total");
            if (id != null && total > 0) {
                cooldowns.put(id, new SkillCooldown(one.getLong("end"), total));
            }
        }
        adventure.load(tag.getCompound("adventure"));
        resetTransient();
    }

    /** 死亡・リスポーン・エンドからの帰還のとき、古い体のデータを写す。 */
    public void copyFrom(PlayerData other) {
        level = other.level;
        exp = other.exp;
        System.arraycopy(other.allocated, 0, allocated, 0, allocated.length);
        hp = other.hp;
        mp = other.mp;

        combatJob = other.combatJob;
        promoted = other.promoted;
        promotedOnce = other.promotedOnce;
        kitsReceived.clear();
        kitsReceived.addAll(other.kitsReceived);
        freeResetItemsGiven = other.freeResetItemsGiven;
        jobChanges = other.jobChanges;
        learned.clear();
        learned.putAll(other.learned);
        System.arraycopy(other.loadout, 0, loadout, 0, loadout.length);
        // クールダウンは、死んでも消えない（死んで使い直す、を防ぐ）。ゲージ・詠唱・強化は消える
        cooldowns.clear();
        cooldowns.putAll(other.cooldowns);
        adventure.copyFrom(other.adventure);
        resetTransient();
    }
}

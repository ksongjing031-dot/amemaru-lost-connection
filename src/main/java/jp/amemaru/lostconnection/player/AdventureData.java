package jp.amemaru.lostconnection.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * プレイヤーデータのうち、Phase 3で増えた「冒険の記録」（Phase 0-B 4.1）。{@link PlayerData} の中に持ち、一緒に保存する。
 *
 * <ul>
 *   <li>結晶果を使った数（ステータスごと。上限+15。Phase 0-A 20章）</li>
 *   <li>生産職と、生産の熟練度（職業ごとに経験を持つので、生産職を変えても戻せば続きから。Phase 0-A 19章）</li>
 *   <li>図鑑（敵ごとの倒した数と、手に入れたドロップ。Phase 0-A 20章【確定】）</li>
 *   <li>レアドロップの天井（ボスごとの、レアが出なかった回数。Phase 0-A 20章）</li>
 *   <li>体に表示するアクセサリーの、枠ごとの表示のオン・オフ（Phase 0-A 21章）</li>
 *   <li>レベルの上限に届いている間にたまった経験値（結晶の欠片に変わるまでの端数）</li>
 *   <li>これから贈る称号（完璧なアクセサリーなど。称号の仕組みはPhase 6なので、それまで預かる）</li>
 * </ul>
 */
public final class AdventureData {
    /** 図鑑の1つの敵の記録。 */
    public static final class BestiaryEntry {
        private int kills;
        private final Set<ResourceLocation> drops = new LinkedHashSet<>();

        public int kills() {
            return kills;
        }

        public Set<ResourceLocation> drops() {
            return Collections.unmodifiableSet(drops);
        }
    }

    private final Runnable onChange;

    private final int[] fruits = new int[StatType.COUNT];
    @Nullable
    private ResourceLocation productionJob;
    private final Map<ResourceLocation, Integer> masteryExp = new LinkedHashMap<>();
    private int productionChanges;
    private int crafted;
    private final Map<ResourceLocation, BestiaryEntry> bestiary = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> pity = new LinkedHashMap<>();
    private int hiddenAccessories;
    private long overflowExp;
    private final Set<String> pendingTitles = new LinkedHashSet<>();
    /** ガチャ（祈願）の記録（祈願のID → 記録。11月27日）。 */
    private final Map<String, GachaProgress> gacha = new LinkedHashMap<>();

    /**
     * 祈願ひとつの記録（11月27日）：引いた回数・★5から数えた回数（天井まで）・10連の★4の保証の残り・★5を引いた数。
     */
    public static final class GachaProgress {
        private int pulls;
        private int sinceFive;
        private int fives;

        public int pulls() {
            return pulls;
        }

        public int sinceFive() {
            return sinceFive;
        }

        public int fives() {
            return fives;
        }

        /** 管理者のコマンド用：★5が出ないまま引いた回数を決める。 */
        public void setSinceFive(int value) {
            sinceFive = Math.max(0, value);
        }

        /** 1回引いた。★5なら天井の数を戻す。 */
        public void record(boolean five) {
            pulls++;
            if (five) {
                fives++;
                sinceFive = 0;
            } else {
                sinceFive++;
            }
        }
    }

    public AdventureData(Runnable onChange) {
        this.onChange = onChange;
    }

    private void changed() {
        onChange.run();
    }

    // ---------------------------------------------------------------- 結晶果

    /** その種類の結晶果を使った数。 */
    public int fruits(StatType type) {
        return fruits[type.ordinal()];
    }

    public void setFruits(StatType type, int count) {
        int next = Math.max(0, count);
        if (fruits[type.ordinal()] != next) {
            fruits[type.ordinal()] = next;
            changed();
        }
    }

    // ---------------------------------------------------------------- 生産職

    @Nullable
    public ResourceLocation productionJob() {
        return productionJob;
    }

    public void setProductionJob(@Nullable ResourceLocation job) {
        if (job == null ? productionJob != null : !job.equals(productionJob)) {
            productionJob = job;
            changed();
        }
    }

    /** 生産職を変えた回数。 */
    public int productionChanges() {
        return productionChanges;
    }

    public void addProductionChange() {
        productionChanges++;
        changed();
    }

    /** その生産職の熟練度の経験。 */
    public int masteryExp(ResourceLocation job) {
        return masteryExp.getOrDefault(job, 0);
    }

    public void setMasteryExp(ResourceLocation job, int exp) {
        int next = Math.max(0, exp);
        Integer old = masteryExp.put(job, next);
        if (old == null || old != next) {
            changed();
        }
    }

    /** 生産職ごとの熟練度の経験（読み取り用）。 */
    public Map<ResourceLocation, Integer> allMasteryExp() {
        return Collections.unmodifiableMap(masteryExp);
    }

    /** 作った装備・アクセサリーの数（Phase 6の称号「見習い職人」などの進み具合）。 */
    public int crafted() {
        return crafted;
    }

    public void addCrafted(int count) {
        crafted += Math.max(0, count);
        changed();
    }

    // ---------------------------------------------------------------- 図鑑

    @Nullable
    public BestiaryEntry bestiary(ResourceLocation enemy) {
        return bestiary.get(enemy);
    }

    public Map<ResourceLocation, BestiaryEntry> allBestiary() {
        return Collections.unmodifiableMap(bestiary);
    }

    /** 倒した数を1つ増やす。 */
    public void recordKill(ResourceLocation enemy) {
        bestiary.computeIfAbsent(enemy, id -> new BestiaryEntry()).kills++;
        changed();
    }

    /**
     * 手に入れたドロップを記録する。
     *
     * @return 初めて手に入れた物なら true
     */
    public boolean recordDrop(ResourceLocation enemy, ResourceLocation item) {
        boolean added = bestiary.computeIfAbsent(enemy, id -> new BestiaryEntry()).drops.add(item);
        if (added) {
            changed();
        }
        return added;
    }

    /** 倒した敵の数の合計。 */
    public int totalKills() {
        int total = 0;
        for (BestiaryEntry entry : bestiary.values()) {
            total += entry.kills;
        }
        return total;
    }

    // ---------------------------------------------------------------- 天井

    /** その敵からレアが出なかった回数（天井のある敵だけ数える）。 */
    public int pity(ResourceLocation enemy) {
        return pity.getOrDefault(enemy, 0);
    }

    public void setPity(ResourceLocation enemy, int misses) {
        if (misses <= 0) {
            if (pity.remove(enemy) != null) {
                changed();
            }
            return;
        }
        Integer old = pity.put(enemy, misses);
        if (old == null || old != misses) {
            changed();
        }
    }

    // ---------------------------------------------------------------- アクセサリーの表示

    /** その枠（番号）のアクセサリーを体に表示するか。 */
    public boolean accessoryVisible(int slotIndex) {
        return (hiddenAccessories & (1 << slotIndex)) == 0;
    }

    public void setAccessoryVisible(int slotIndex, boolean visible) {
        int next = visible ? hiddenAccessories & ~(1 << slotIndex) : hiddenAccessories | (1 << slotIndex);
        if (next != hiddenAccessories) {
            hiddenAccessories = next;
            changed();
        }
    }

    /** 表示しない枠のビット（通信用）。 */
    public int hiddenAccessoryBits() {
        return hiddenAccessories;
    }

    // ---------------------------------------------------------------- 上限の後の経験値

    public long overflowExp() {
        return overflowExp;
    }

    public void setOverflowExp(long exp) {
        long next = Math.max(0L, exp);
        if (next != overflowExp) {
            overflowExp = next;
            changed();
        }
    }

    // ---------------------------------------------------------------- 称号（Phase 6まで預かる）

    public Set<String> pendingTitles() {
        return Collections.unmodifiableSet(pendingTitles);
    }

    /** 贈る称号を預かる。初めてなら true。 */
    public boolean addPendingTitle(String title) {
        boolean added = pendingTitles.add(title);
        if (added) {
            changed();
        }
        return added;
    }

    // ---------------------------------------------------------------- 祈願（ガチャ。11月27日）

    public GachaProgress gacha(String banner) {
        return gacha.computeIfAbsent(banner, key -> new GachaProgress());
    }

    /** 引いたあとに呼ぶ（保存と同期の印）。 */
    public void gachaChanged() {
        changed();
    }

    // ---------------------------------------------------------------- 保存

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag fruitTag = new CompoundTag();
        for (StatType type : StatType.all()) {
            if (fruits[type.ordinal()] > 0) {
                fruitTag.putInt(type.id(), fruits[type.ordinal()]);
            }
        }
        tag.put("fruits", fruitTag);

        CompoundTag production = new CompoundTag();
        if (productionJob != null) {
            production.putString("job", productionJob.toString());
        }
        CompoundTag mastery = new CompoundTag();
        for (Map.Entry<ResourceLocation, Integer> entry : masteryExp.entrySet()) {
            mastery.putInt(entry.getKey().toString(), entry.getValue());
        }
        production.put("mastery", mastery);
        production.putInt("changes", productionChanges);
        production.putInt("crafted", crafted);
        tag.put("production", production);

        CompoundTag bestiaryTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, BestiaryEntry> entry : bestiary.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putInt("kills", entry.getValue().kills);
            ListTag drops = new ListTag();
            for (ResourceLocation drop : entry.getValue().drops) {
                drops.add(StringTag.valueOf(drop.toString()));
            }
            one.put("drops", drops);
            bestiaryTag.put(entry.getKey().toString(), one);
        }
        tag.put("bestiary", bestiaryTag);

        CompoundTag pityTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, Integer> entry : pity.entrySet()) {
            pityTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put("pity", pityTag);

        tag.putInt("hiddenAccessories", hiddenAccessories);
        tag.putLong("overflowExp", overflowExp);
        ListTag titles = new ListTag();
        for (String title : pendingTitles) {
            titles.add(StringTag.valueOf(title));
        }
        tag.put("pendingTitles", titles);
        CompoundTag gachaTag = new CompoundTag();
        for (Map.Entry<String, GachaProgress> entry : gacha.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putInt("pulls", entry.getValue().pulls);
            one.putInt("since", entry.getValue().sinceFive);
            one.putInt("fives", entry.getValue().fives);
            gachaTag.put(entry.getKey(), one);
        }
        tag.put("gacha", gachaTag);
        return tag;
    }

    public void load(CompoundTag tag) {
        CompoundTag fruitTag = tag.getCompound("fruits");
        for (StatType type : StatType.all()) {
            fruits[type.ordinal()] = Math.max(0, fruitTag.getInt(type.id()));
        }

        CompoundTag production = tag.getCompound("production");
        productionJob = production.contains("job") ? ResourceLocation.tryParse(production.getString("job")) : null;
        masteryExp.clear();
        CompoundTag mastery = production.getCompound("mastery");
        for (String key : mastery.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                masteryExp.put(id, Math.max(0, mastery.getInt(key)));
            }
        }
        productionChanges = Math.max(0, production.getInt("changes"));
        crafted = Math.max(0, production.getInt("crafted"));

        bestiary.clear();
        CompoundTag bestiaryTag = tag.getCompound("bestiary");
        for (String key : bestiaryTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id == null) {
                continue;
            }
            CompoundTag one = bestiaryTag.getCompound(key);
            BestiaryEntry entry = new BestiaryEntry();
            entry.kills = Math.max(0, one.getInt("kills"));
            ListTag drops = one.getList("drops", Tag.TAG_STRING);
            for (int i = 0; i < drops.size(); i++) {
                ResourceLocation drop = ResourceLocation.tryParse(drops.getString(i));
                if (drop != null) {
                    entry.drops.add(drop);
                }
            }
            bestiary.put(id, entry);
        }

        pity.clear();
        CompoundTag pityTag = tag.getCompound("pity");
        for (String key : pityTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null && pityTag.getInt(key) > 0) {
                pity.put(id, pityTag.getInt(key));
            }
        }

        hiddenAccessories = tag.getInt("hiddenAccessories");
        overflowExp = Math.max(0L, tag.getLong("overflowExp"));
        pendingTitles.clear();
        ListTag titles = tag.getList("pendingTitles", Tag.TAG_STRING);
        for (int i = 0; i < titles.size(); i++) {
            pendingTitles.add(titles.getString(i));
        }
        gacha.clear();
        CompoundTag gachaTag = tag.getCompound("gacha");
        for (String key : gachaTag.getAllKeys()) {
            CompoundTag one = gachaTag.getCompound(key);
            GachaProgress progress = new GachaProgress();
            progress.pulls = Math.max(0, one.getInt("pulls"));
            progress.sinceFive = Math.max(0, one.getInt("since"));
            progress.fives = Math.max(0, one.getInt("fives"));
            gacha.put(key, progress);
        }
    }

    /** 死亡・リスポーンのとき、古い体の記録を写す（すべて残る）。 */
    public void copyFrom(AdventureData other) {
        load(other.save());
        changed();
    }
}

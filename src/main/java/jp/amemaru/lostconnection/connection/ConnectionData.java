package jp.amemaru.lostconnection.connection;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * サーバー全体のコネクション量（Phase 0-B 4.2「ConnectionData：累計CP、分野ごとの合計、輝き段階、加護の星、直近3日の
 * アクティブ人数」）。ワールド（オーバーワールド）に保存する。
 *
 * <ul>
 *   <li>累計CPと分野ごとの合計。輝き段階と加護の星は、届いたいちばん上を覚えておき、下がらない（人数補正の倍率が後から
 *       上がっても、一度灯った段階は消えない【補完】）</li>
 *   <li>日ごとのアクティブな人（毎日4時に切り替わる日。直近の数日だけ残す）</li>
 *   <li>人ごとの分野別の累計（エンディングのスタッフロールの「その人らしい一行」と、貢献の一覧のもと）</li>
 *   <li>いない人に届いたCP（フリマで売れた・露店に人が来た・結び札をもらった）。次に入ったときに数える</li>
 *   <li>今日の相棒（Phase 0-A 15章）：今日だれがだれの相棒か・ふたりで倒した数・終えたか（日が変わると空にする）。
 *       最近来た人の名前（相棒を、いない人からも選ぶため）</li>
 * </ul>
 */
public final class ConnectionData extends SavedData {
    private static final String NAME = "amemaru_connection";
    /** アクティブな人を残す日数（設定の上限14日より少し多く）。 */
    private static final int ACTIVE_KEEP = 16;

    private long total;
    private final long[] byCategory = new long[ConnectionCategory.values().length];
    private int stageReached = 1;
    private int starsReached;
    private final Map<Integer, Long> reachedAt = new HashMap<>();
    private final LinkedHashMap<String, Set<UUID>> active = new LinkedHashMap<>();
    private final Map<UUID, Contributor> contributors = new LinkedHashMap<>();
    private final Map<UUID, List<CompoundTag>> pending = new HashMap<>();
    private String partnerDay = "";
    private final Map<UUID, UUID> partnerOf = new HashMap<>();
    private final Map<String, Integer> pairProgress = new HashMap<>();
    private final Set<String> pairDone = new java.util.HashSet<>();
    private final Map<UUID, String> names = new HashMap<>();

    /** 人ごとの分野別の累計。 */
    public static final class Contributor {
        String name = "";
        final long[] byCategory = new long[ConnectionCategory.values().length];

        public String name() {
            return name;
        }

        public long get(ConnectionCategory category) {
            return byCategory[category.ordinal()];
        }

        public long total() {
            long sum = 0;
            for (long v : byCategory) {
                sum += v;
            }
            return sum;
        }
    }

    public static ConnectionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ConnectionData::load, ConnectionData::new, NAME);
    }

    // ---------------------------------------------------------------- 累計

    public long total() {
        return total;
    }

    public long category(ConnectionCategory category) {
        return byCategory[category.ordinal()];
    }

    void add(UUID who, String name, ConnectionCategory category, int cp) {
        if (cp <= 0) {
            return;
        }
        total += cp;
        byCategory[category.ordinal()] += cp;
        Contributor c = contributors.computeIfAbsent(who, k -> new Contributor());
        if (name != null && !name.isEmpty()) {
            c.name = name;
        }
        c.byCategory[category.ordinal()] += cp;
        setDirty();
    }

    /** 管理者のコマンド・自動テスト：累計を決める（分野の合計は変えない）。段階は下げない。 */
    void setTotal(long value) {
        total = Math.max(0L, value);
        setDirty();
    }

    public int stageReached() {
        return stageReached;
    }

    public int starsReached() {
        return starsReached;
    }

    void setStageReached(int stage, long when) {
        if (stage > stageReached) {
            for (int s = stageReached + 1; s <= stage; s++) {
                reachedAt.putIfAbsent(s, when);
            }
        }
        stageReached = Math.max(1, stage);
        setDirty();
    }

    void setStarsReached(int stars) {
        starsReached = Math.max(0, stars);
        setDirty();
    }

    /** 管理者：段階を下げる（上の段階の届いた時刻と、星も消す）。 */
    void lowerStage(int stage) {
        stageReached = Math.max(1, stage);
        reachedAt.keySet().removeIf(s -> s > stageReached);
        starsReached = 0;
        setDirty();
    }

    /** 管理者：段階と星を初めに戻す（累計も0にする）。 */
    void reset() {
        total = 0;
        java.util.Arrays.fill(byCategory, 0L);
        stageReached = 1;
        starsReached = 0;
        reachedAt.clear();
        contributors.clear();
        partnerDay = "";
        partnerOf.clear();
        pairProgress.clear();
        pairDone.clear();
        setDirty();
    }

    public Map<Integer, Long> reachedAt() {
        return Collections.unmodifiableMap(reachedAt);
    }

    public Map<UUID, Contributor> contributors() {
        return Collections.unmodifiableMap(contributors);
    }

    // ---------------------------------------------------------------- アクティブな人

    /** その日にアクティブだった人に足す。新しく足したなら true。 */
    boolean markActive(String day, UUID who) {
        Set<UUID> set = active.computeIfAbsent(day, k -> new LinkedHashSet<>());
        boolean added = set.add(who);
        while (active.size() > ACTIVE_KEEP) {
            String oldest = active.keySet().iterator().next();
            active.remove(oldest);
        }
        if (added) {
            setDirty();
        }
        return added;
    }

    /** その日のアクティブな人数。 */
    public int activeOn(String day) {
        Set<UUID> set = active.get(day);
        return set == null ? 0 : set.size();
    }

    // ---------------------------------------------------------------- いない人に届いたCP

    void addPending(UUID who, CompoundTag award) {
        pending.computeIfAbsent(who, k -> new ArrayList<>()).add(award);
        setDirty();
    }

    List<CompoundTag> takePending(UUID who) {
        List<CompoundTag> list = pending.remove(who);
        if (list != null) {
            setDirty();
        }
        return list == null ? List.of() : list;
    }

    /** 直近の日（新しい順のキー）にアクティブだった人。 */
    List<UUID> activeOn(List<String> days) {
        Set<UUID> all = new LinkedHashSet<>();
        for (String day : days) {
            Set<UUID> set = active.get(day);
            if (set != null) {
                all.addAll(set);
            }
        }
        return new ArrayList<>(all);
    }

    // ---------------------------------------------------------------- 今日の相棒

    /** 日が変わっていたら、相棒を空にする。 */
    void rolloverPartners(String today) {
        if (!today.equals(partnerDay)) {
            partnerDay = today;
            partnerOf.clear();
            pairProgress.clear();
            pairDone.clear();
            setDirty();
        }
    }

    @javax.annotation.Nullable
    public UUID partnerOf(UUID who) {
        return partnerOf.get(who);
    }

    void setPartner(UUID who, UUID partner) {
        partnerOf.put(who, partner);
        setDirty();
    }

    /** ふたりの組の鍵（順番によらない）。 */
    static String pairKey(UUID a, UUID b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    public int pairProgress(UUID a, UUID b) {
        return pairProgress.getOrDefault(pairKey(a, b), 0);
    }

    int addPairProgress(UUID a, UUID b, int amount) {
        int now = pairProgress.merge(pairKey(a, b), amount, Integer::sum);
        setDirty();
        return now;
    }

    public boolean pairDone(UUID a, UUID b) {
        return pairDone.contains(pairKey(a, b));
    }

    void markPairDone(UUID a, UUID b) {
        pairDone.add(pairKey(a, b));
        setDirty();
    }

    void rememberName(UUID who, String name) {
        if (name != null && !name.isEmpty() && !name.equals(names.get(who))) {
            names.put(who, name);
            setDirty();
        }
    }

    @javax.annotation.Nullable
    public String nameOf(UUID who) {
        return names.get(who);
    }

    // ---------------------------------------------------------------- 保存

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("total", total);
        tag.put("categories", new LongArrayTag(byCategory.clone()));
        tag.putInt("stage", stageReached);
        tag.putInt("stars", starsReached);
        CompoundTag at = new CompoundTag();
        reachedAt.forEach((stage, when) -> at.putLong(String.valueOf(stage), when));
        tag.put("reachedAt", at);
        CompoundTag act = new CompoundTag();
        active.forEach((day, set) -> {
            ListTag list = new ListTag();
            for (UUID uuid : set) {
                list.add(StringTag.valueOf(uuid.toString()));
            }
            act.put(day, list);
        });
        tag.put("active", act);
        ListTag people = new ListTag();
        contributors.forEach((uuid, c) -> {
            CompoundTag one = new CompoundTag();
            one.putUUID("id", uuid);
            one.putString("name", c.name);
            one.put("cp", new LongArrayTag(c.byCategory.clone()));
            people.add(one);
        });
        tag.put("contributors", people);
        ListTag waiting = new ListTag();
        pending.forEach((uuid, list) -> {
            CompoundTag one = new CompoundTag();
            one.putUUID("id", uuid);
            ListTag awards = new ListTag();
            awards.addAll(list);
            one.put("awards", awards);
            waiting.add(one);
        });
        tag.put("pending", waiting);
        CompoundTag partners = new CompoundTag();
        partners.putString("day", partnerDay);
        ListTag pairs = new ListTag();
        partnerOf.forEach((who, partner) -> {
            CompoundTag one = new CompoundTag();
            one.putUUID("who", who);
            one.putUUID("partner", partner);
            pairs.add(one);
        });
        partners.put("of", pairs);
        CompoundTag prog = new CompoundTag();
        pairProgress.forEach(prog::putInt);
        partners.put("progress", prog);
        ListTag done = new ListTag();
        for (String key : pairDone) {
            done.add(StringTag.valueOf(key));
        }
        partners.put("done", done);
        tag.put("partners", partners);
        ListTag known = new ListTag();
        names.forEach((uuid, name) -> {
            CompoundTag one = new CompoundTag();
            one.putUUID("id", uuid);
            one.putString("name", name);
            known.add(one);
        });
        tag.put("names", known);
        return tag;
    }

    static ConnectionData load(CompoundTag tag) {
        ConnectionData data = new ConnectionData();
        data.total = tag.getLong("total");
        long[] cats = tag.getLongArray("categories");
        System.arraycopy(cats, 0, data.byCategory, 0, Math.min(cats.length, data.byCategory.length));
        data.stageReached = Math.max(1, tag.getInt("stage"));
        data.starsReached = tag.getInt("stars");
        CompoundTag at = tag.getCompound("reachedAt");
        for (String key : at.getAllKeys()) {
            try {
                data.reachedAt.put(Integer.parseInt(key), at.getLong(key));
            } catch (NumberFormatException ignored) {
            }
        }
        CompoundTag act = tag.getCompound("active");
        List<String> days = new ArrayList<>(act.getAllKeys());
        Collections.sort(days);
        for (String day : days) {
            Set<UUID> set = new LinkedHashSet<>();
            for (Tag t : act.getList(day, Tag.TAG_STRING)) {
                try {
                    set.add(UUID.fromString(t.getAsString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
            data.active.put(day, set);
        }
        for (Tag t : tag.getList("contributors", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) t;
            if (!one.hasUUID("id")) {
                continue;
            }
            Contributor c = new Contributor();
            c.name = one.getString("name");
            long[] cp = one.getLongArray("cp");
            System.arraycopy(cp, 0, c.byCategory, 0, Math.min(cp.length, c.byCategory.length));
            data.contributors.put(one.getUUID("id"), c);
        }
        for (Tag t : tag.getList("pending", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) t;
            if (!one.hasUUID("id")) {
                continue;
            }
            List<CompoundTag> list = new ArrayList<>();
            for (Tag a : one.getList("awards", Tag.TAG_COMPOUND)) {
                list.add((CompoundTag) a);
            }
            data.pending.put(one.getUUID("id"), list);
        }
        CompoundTag partners = tag.getCompound("partners");
        data.partnerDay = partners.getString("day");
        for (Tag t : partners.getList("of", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) t;
            if (one.hasUUID("who") && one.hasUUID("partner")) {
                data.partnerOf.put(one.getUUID("who"), one.getUUID("partner"));
            }
        }
        CompoundTag prog = partners.getCompound("progress");
        for (String key : prog.getAllKeys()) {
            data.pairProgress.put(key, prog.getInt(key));
        }
        for (Tag t : partners.getList("done", Tag.TAG_STRING)) {
            data.pairDone.add(t.getAsString());
        }
        for (Tag t : tag.getList("names", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) t;
            if (one.hasUUID("id")) {
                data.names.put(one.getUUID("id"), one.getString("name"));
            }
        }
        return data;
    }
}

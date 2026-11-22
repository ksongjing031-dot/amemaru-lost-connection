package jp.amemaru.lostconnection.guild;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * プレイヤーごとの「ギルドとクエスト・主要NPC・古代記録・称号」の記録（Phase 6 その3。Phase 0-B 4.1「物語：…個人クエストの進み、
 * 見つけた古代記録、NPCごとの親密度」「称号」）。
 *
 * <ul>
 *   <li>称号：持っている称号（いつ取ったか）と、表示している称号（なしもある）</li>
 *   <li>数：称号の条件に使う数（連携技・取引・露店の来客など。倒した数・作った数・起こした数は、もとの記録から数える）</li>
 *   <li>古代記録：見つけた記録（未解読・解読済み）と、記録帳をもらったか</li>
 *   <li>親密度：NPCごとの点・会ったか・今日話したか・今日贈ったか・段階のごほうびを渡したか</li>
 *   <li>クエスト：受けているクエストと進み、終えたクエスト、今日のデイリー</li>
 *   <li>縁帳（Phase 0-A 15章【提案】）：一緒に組んだ人・取引した人・起こした人と起こしてくれた人</li>
 * </ul>
 */
public final class GuildData {
    /** 縁帳に残す相手の数の上限（古いものから消す）。 */
    public static final int CONNECTIONS_MAX = 200;

    private final Runnable onChange;

    // ---- 称号
    private final Map<ResourceLocation, OwnedTitle> titles = new LinkedHashMap<>();
    @Nullable
    private ResourceLocation displayed;

    // ---- 称号の条件に使う数
    private final Map<String, Long> counters = new LinkedHashMap<>();
    /** ゼロからの一歩（隠し称号）：所持金が0になってから稼いだ額。0になる前は -1。 */
    private long earnedSinceZero = -1L;
    /** 露店の来客（自分の露店に来た人。1日に1人1回）。 */
    private int visitorsDay;
    private final Set<UUID> visitorsToday = new LinkedHashSet<>();

    // ---- 古代記録
    private final Map<ResourceLocation, RecordEntry> records = new LinkedHashMap<>();
    private boolean recordBook;

    // ---- 親密度
    private final Map<ResourceLocation, Bond> bonds = new LinkedHashMap<>();

    // ---- クエスト
    private final Map<ResourceLocation, QuestProgress> quests = new LinkedHashMap<>();
    private final Map<ResourceLocation, Completed> completed = new LinkedHashMap<>();
    private int dailyDay;
    private final List<ResourceLocation> dailyOffer = new ArrayList<>();
    private int dailyDone;

    // ---- 縁帳
    private final Map<UUID, Connection> connections = new LinkedHashMap<>();

    public GuildData(Runnable onChange) {
        this.onChange = onChange;
    }

    private void changed() {
        onChange.run();
    }

    // ================================================================ 称号

    /** 持っている称号（取った日と時刻）。 */
    public record OwnedTitle(int day, long epochMillis) {
    }

    public boolean hasTitle(ResourceLocation id) {
        return titles.containsKey(id);
    }

    public Map<ResourceLocation, OwnedTitle> titles() {
        return Collections.unmodifiableMap(titles);
    }

    /** 称号を足す。初めてなら true。 */
    public boolean addTitle(ResourceLocation id, int day, long epochMillis) {
        if (titles.containsKey(id)) {
            return false;
        }
        titles.put(id, new OwnedTitle(day, epochMillis));
        changed();
        return true;
    }

    public boolean removeTitle(ResourceLocation id) {
        if (titles.remove(id) == null) {
            return false;
        }
        if (id.equals(displayed)) {
            displayed = null;
        }
        changed();
        return true;
    }

    @Nullable
    public ResourceLocation displayedTitle() {
        return displayed;
    }

    /** 表示する称号を決める（null で表示しない）。持っていない称号は選べない。 */
    public boolean setDisplayedTitle(@Nullable ResourceLocation id) {
        if (id != null && !titles.containsKey(id)) {
            return false;
        }
        if (id == null ? displayed == null : id.equals(displayed)) {
            return false;
        }
        displayed = id;
        changed();
        return true;
    }

    // ================================================================ 数

    public long counter(String key) {
        return counters.getOrDefault(key, 0L);
    }

    public void addCounter(String key, long amount) {
        if (amount == 0) {
            return;
        }
        counters.put(key, Math.max(0L, counter(key) + amount));
        changed();
    }

    public void setCounter(String key, long value) {
        counters.put(key, Math.max(0L, value));
        changed();
    }

    public Map<String, Long> counters() {
        return Collections.unmodifiableMap(counters);
    }

    /** 所持金が0になったときの印（ゼロからの一歩）。 */
    public void markZero() {
        if (earnedSinceZero != 0L) {
            earnedSinceZero = 0L;
            changed();
        }
    }

    /** 0になったあとに稼いだ額を足す。0になる前は数えない。 */
    public void addEarnedSinceZero(long amount) {
        if (earnedSinceZero < 0 || amount <= 0) {
            return;
        }
        earnedSinceZero = earnedSinceZero + amount < 0 ? Long.MAX_VALUE : earnedSinceZero + amount;
        changed();
    }

    public long earnedSinceZero() {
        return earnedSinceZero;
    }

    /** 自分の露店に来た人を数える（1日に1人1回）。数えたら true。 */
    public boolean countVisitor(int day, UUID visitor) {
        if (visitorsDay != day) {
            visitorsDay = day;
            visitorsToday.clear();
        }
        if (!visitorsToday.add(visitor)) {
            return false;
        }
        addCounter(GuildCounters.STALL_VISITORS, 1);
        return true;
    }

    // ================================================================ 古代記録

    /** 見つけた記録（解読したか・見つけた日）。 */
    public record RecordEntry(boolean deciphered, int foundDay) {
    }

    public Map<ResourceLocation, RecordEntry> records() {
        return Collections.unmodifiableMap(records);
    }

    @Nullable
    public RecordEntry record(ResourceLocation id) {
        return records.get(id);
    }

    /** 記録を見つけた（未解読で残す）。初めてなら true。 */
    public boolean findRecord(ResourceLocation id, int day) {
        if (records.containsKey(id)) {
            return false;
        }
        records.put(id, new RecordEntry(false, day));
        changed();
        return true;
    }

    /** 解読した。解読していなかったなら true。 */
    public boolean decipherRecord(ResourceLocation id) {
        RecordEntry entry = records.get(id);
        if (entry == null || entry.deciphered()) {
            return false;
        }
        records.put(id, new RecordEntry(true, entry.foundDay()));
        changed();
        return true;
    }

    public boolean forgetRecord(ResourceLocation id) {
        if (records.remove(id) == null) {
            return false;
        }
        changed();
        return true;
    }

    public boolean hasRecordBook() {
        return recordBook;
    }

    public void setRecordBook(boolean value) {
        if (recordBook != value) {
            recordBook = value;
            changed();
        }
    }

    // ================================================================ 親密度

    /** NPCとの縁（点・会ったか・今日話したか・今日贈ったか・段階のごほうび）。 */
    public static final class Bond {
        int points;
        boolean met;
        int lastTalkDay;
        int lastGiftDay;
        int giftsToday;
        int rewards;

        public int points() {
            return points;
        }

        public boolean met() {
            return met;
        }

        public int lastTalkDay() {
            return lastTalkDay;
        }

        public int lastGiftDay() {
            return lastGiftDay;
        }

        public int giftsToday() {
            return giftsToday;
        }

        /** 段階のごほうびを渡したか（段階ごとのビット）。 */
        public boolean rewarded(int stage) {
            return (rewards & (1 << stage)) != 0;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("points", points);
            tag.putBoolean("met", met);
            tag.putInt("talk", lastTalkDay);
            tag.putInt("gift", lastGiftDay);
            tag.putInt("gifts", giftsToday);
            tag.putInt("rewards", rewards);
            return tag;
        }

        static Bond load(CompoundTag tag) {
            Bond bond = new Bond();
            bond.points = Math.max(0, tag.getInt("points"));
            bond.met = tag.getBoolean("met");
            bond.lastTalkDay = tag.getInt("talk");
            bond.lastGiftDay = tag.getInt("gift");
            bond.giftsToday = Math.max(0, tag.getInt("gifts"));
            bond.rewards = tag.getInt("rewards");
            return bond;
        }

        Bond copy() {
            Bond bond = new Bond();
            bond.points = points;
            bond.met = met;
            bond.lastTalkDay = lastTalkDay;
            bond.lastGiftDay = lastGiftDay;
            bond.giftsToday = giftsToday;
            bond.rewards = rewards;
            return bond;
        }
    }

    public Map<ResourceLocation, Bond> bonds() {
        return Collections.unmodifiableMap(bonds);
    }

    @Nullable
    public Bond bond(ResourceLocation npc) {
        return bonds.get(npc);
    }

    private Bond bondOrNew(ResourceLocation npc) {
        return bonds.computeIfAbsent(npc, key -> new Bond());
    }

    /** 初めて会った。初めてなら true。 */
    public boolean meet(ResourceLocation npc) {
        Bond bond = bondOrNew(npc);
        if (bond.met) {
            return false;
        }
        bond.met = true;
        changed();
        return true;
    }

    public void addBondPoints(ResourceLocation npc, int amount, int cap) {
        Bond bond = bondOrNew(npc);
        bond.met = true;
        bond.points = Math.max(0, Math.min(cap, bond.points + amount));
        changed();
    }

    public void setBondPoints(ResourceLocation npc, int points) {
        Bond bond = bondOrNew(npc);
        bond.met = true;
        bond.points = Math.max(0, points);
        changed();
    }

    /** 今日はじめて話した。今日すでに話していれば false。 */
    public boolean talkToday(ResourceLocation npc, int day) {
        Bond bond = bondOrNew(npc);
        if (bond.lastTalkDay == day) {
            return false;
        }
        bond.lastTalkDay = day;
        changed();
        return true;
    }

    /** 今日の贈り物の数を数える。上限をこえるなら false。 */
    public boolean giftToday(ResourceLocation npc, int day, int perDay) {
        Bond bond = bondOrNew(npc);
        if (bond.lastGiftDay != day) {
            bond.lastGiftDay = day;
            bond.giftsToday = 0;
        }
        if (bond.giftsToday >= perDay) {
            return false;
        }
        bond.giftsToday++;
        changed();
        return true;
    }

    public void markBondReward(ResourceLocation npc, int stage) {
        Bond bond = bondOrNew(npc);
        bond.rewards |= 1 << stage;
        changed();
    }

    public boolean forgetBond(ResourceLocation npc) {
        if (bonds.remove(npc) == null) {
            return false;
        }
        changed();
        return true;
    }

    // ================================================================ クエスト

    /** 受けているクエストの進み（目標ごとの数）。 */
    public static final class QuestProgress {
        final int[] counts;
        int acceptedDay;
        boolean tracked;

        QuestProgress(int objectives, int acceptedDay) {
            this.counts = new int[Math.max(0, objectives)];
            this.acceptedDay = acceptedDay;
            this.tracked = true;
        }

        public int count(int index) {
            return index >= 0 && index < counts.length ? counts[index] : 0;
        }

        public int size() {
            return counts.length;
        }

        public int acceptedDay() {
            return acceptedDay;
        }

        public boolean tracked() {
            return tracked;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putIntArray("counts", counts);
            tag.putInt("day", acceptedDay);
            tag.putBoolean("tracked", tracked);
            return tag;
        }

        static QuestProgress load(CompoundTag tag) {
            int[] counts = tag.getIntArray("counts");
            QuestProgress progress = new QuestProgress(counts.length, tag.getInt("day"));
            System.arraycopy(counts, 0, progress.counts, 0, counts.length);
            progress.tracked = !tag.contains("tracked") || tag.getBoolean("tracked");
            return progress;
        }

        QuestProgress copy() {
            QuestProgress progress = new QuestProgress(counts.length, acceptedDay);
            System.arraycopy(counts, 0, progress.counts, 0, counts.length);
            progress.tracked = tracked;
            return progress;
        }
    }

    /** 終えたクエスト（回数と最後の日）。 */
    public record Completed(int times, int lastDay) {
    }

    public Map<ResourceLocation, QuestProgress> quests() {
        return Collections.unmodifiableMap(quests);
    }

    @Nullable
    public QuestProgress quest(ResourceLocation id) {
        return quests.get(id);
    }

    public boolean startQuest(ResourceLocation id, int objectives, int day) {
        if (quests.containsKey(id)) {
            return false;
        }
        quests.put(id, new QuestProgress(objectives, day));
        changed();
        return true;
    }

    /** 進みを決める（上限で止める）。変わったら true。 */
    public boolean setQuestCount(ResourceLocation id, int index, int value, int max) {
        QuestProgress progress = quests.get(id);
        if (progress == null || index < 0 || index >= progress.counts.length) {
            return false;
        }
        int clamped = Math.max(0, Math.min(max, value));
        if (progress.counts[index] == clamped) {
            return false;
        }
        progress.counts[index] = clamped;
        changed();
        return true;
    }

    public void setQuestTracked(ResourceLocation id, boolean tracked) {
        QuestProgress progress = quests.get(id);
        if (progress != null && progress.tracked != tracked) {
            progress.tracked = tracked;
            changed();
        }
    }

    public boolean dropQuest(ResourceLocation id) {
        if (quests.remove(id) == null) {
            return false;
        }
        changed();
        return true;
    }

    public void completeQuest(ResourceLocation id, int day) {
        quests.remove(id);
        Completed before = completed.get(id);
        completed.put(id, new Completed(before == null ? 1 : before.times() + 1, day));
        changed();
    }

    @Nullable
    public Completed completed(ResourceLocation id) {
        return completed.get(id);
    }

    public Map<ResourceLocation, Completed> completedQuests() {
        return Collections.unmodifiableMap(completed);
    }

    public boolean forgetCompleted(ResourceLocation id) {
        if (completed.remove(id) == null) {
            return false;
        }
        changed();
        return true;
    }

    public int dailyDay() {
        return dailyDay;
    }

    public List<ResourceLocation> dailyOffer() {
        return Collections.unmodifiableList(dailyOffer);
    }

    public int dailyDone() {
        return dailyDone;
    }

    /** 今日のデイリーを決め直す（日が変わったとき）。 */
    public void resetDaily(int day, List<ResourceLocation> offer) {
        dailyDay = day;
        dailyOffer.clear();
        dailyOffer.addAll(offer);
        dailyDone = 0;
        changed();
    }

    public void countDailyDone() {
        dailyDone++;
        changed();
    }

    // ================================================================ 縁帳

    /** 縁帳の一人分（Phase 0-A 15章【提案】）。 */
    public static final class Connection {
        String name;
        int party;
        int trades;
        int revived;
        int revivedBy;
        /** 依頼台の依頼を頼んだ・受けた回数（Phase 6 その4）。 */
        int commissions;
        /** 決闘した回数（Phase 6 その4）。 */
        int duels;
        int firstDay;
        int lastDay;

        public String name() {
            return name;
        }

        /** 同じパーティになった回数。 */
        public int party() {
            return party;
        }

        /** 取引した回数（フリマ・受け渡し）。 */
        public int trades() {
            return trades;
        }

        /** この人を起こした回数。 */
        public int revived() {
            return revived;
        }

        /** この人に起こしてもらった回数。 */
        public int revivedBy() {
            return revivedBy;
        }

        /** 依頼台の依頼を頼んだ・受けた回数。 */
        public int commissions() {
            return commissions;
        }

        /** 決闘した回数。 */
        public int duels() {
            return duels;
        }

        public int firstDay() {
            return firstDay;
        }

        public int lastDay() {
            return lastDay;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", name == null ? "" : name);
            tag.putInt("party", party);
            tag.putInt("trades", trades);
            tag.putInt("revived", revived);
            tag.putInt("revivedBy", revivedBy);
            tag.putInt("commissions", commissions);
            tag.putInt("duels", duels);
            tag.putInt("first", firstDay);
            tag.putInt("last", lastDay);
            return tag;
        }

        static Connection load(CompoundTag tag) {
            Connection c = new Connection();
            c.name = tag.getString("name");
            c.party = Math.max(0, tag.getInt("party"));
            c.trades = Math.max(0, tag.getInt("trades"));
            c.revived = Math.max(0, tag.getInt("revived"));
            c.revivedBy = Math.max(0, tag.getInt("revivedBy"));
            c.commissions = Math.max(0, tag.getInt("commissions"));
            c.duels = Math.max(0, tag.getInt("duels"));
            c.firstDay = tag.getInt("first");
            c.lastDay = tag.getInt("last");
            return c;
        }

        Connection copy() {
            Connection c = new Connection();
            c.name = name;
            c.party = party;
            c.trades = trades;
            c.revived = revived;
            c.revivedBy = revivedBy;
            c.commissions = commissions;
            c.duels = duels;
            c.firstDay = firstDay;
            c.lastDay = lastDay;
            return c;
        }
    }

    /** 縁帳のどの数を足すか（COMMISSION・DUEL は Phase 6 その4）。 */
    public enum ConnectionKind {
        PARTY, TRADE, REVIVED, REVIVED_BY, COMMISSION, DUEL;

        @Nullable
        public static ConnectionKind byName(String name) {
            for (ConnectionKind kind : values()) {
                if (kind.name().equalsIgnoreCase(name)) {
                    return kind;
                }
            }
            return null;
        }
    }

    public Map<UUID, Connection> connections() {
        return Collections.unmodifiableMap(connections);
    }

    @Nullable
    public Connection connection(UUID other) {
        return connections.get(other);
    }

    /** 縁帳に足す。初めての相手なら true。 */
    public boolean addConnection(UUID other, String name, ConnectionKind kind, int day) {
        Connection c = connections.get(other);
        boolean first = c == null;
        if (first) {
            c = new Connection();
            c.firstDay = day;
            connections.put(other, c);
            while (connections.size() > CONNECTIONS_MAX) {
                UUID oldest = connections.keySet().iterator().next();
                connections.remove(oldest);
            }
        }
        if (name != null && !name.isEmpty()) {
            c.name = name;
        }
        c.lastDay = day;
        switch (kind) {
            case PARTY -> c.party++;
            case TRADE -> c.trades++;
            case REVIVED -> c.revived++;
            case REVIVED_BY -> c.revivedBy++;
            case COMMISSION -> c.commissions++;
            case DUEL -> c.duels++;
        }
        changed();
        return first;
    }

    /** 一緒にパーティを組んだことのある人の数。 */
    public int partners() {
        int count = 0;
        for (Connection c : connections.values()) {
            if (c.party > 0) {
                count++;
            }
        }
        return count;
    }

    // ================================================================ 保存

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag titleTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, OwnedTitle> entry : titles.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putInt("day", entry.getValue().day());
            one.putLong("at", entry.getValue().epochMillis());
            titleTag.put(entry.getKey().toString(), one);
        }
        tag.put("titles", titleTag);
        tag.putString("displayed", displayed == null ? "" : displayed.toString());

        CompoundTag counterTag = new CompoundTag();
        counters.forEach(counterTag::putLong);
        tag.put("counters", counterTag);
        tag.putLong("sinceZero", earnedSinceZero);
        tag.putInt("visitorsDay", visitorsDay);
        ListTag visitors = new ListTag();
        for (UUID visitor : visitorsToday) {
            visitors.add(StringTag.valueOf(visitor.toString()));
        }
        tag.put("visitors", visitors);

        CompoundTag recordTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, RecordEntry> entry : records.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putBoolean("read", entry.getValue().deciphered());
            one.putInt("day", entry.getValue().foundDay());
            recordTag.put(entry.getKey().toString(), one);
        }
        tag.put("records", recordTag);
        tag.putBoolean("recordBook", recordBook);

        CompoundTag bondTag = new CompoundTag();
        bonds.forEach((npc, bond) -> bondTag.put(npc.toString(), bond.save()));
        tag.put("bonds", bondTag);

        CompoundTag questTag = new CompoundTag();
        quests.forEach((id, progress) -> questTag.put(id.toString(), progress.save()));
        tag.put("quests", questTag);
        CompoundTag doneTag = new CompoundTag();
        completed.forEach((id, done) -> {
            CompoundTag one = new CompoundTag();
            one.putInt("times", done.times());
            one.putInt("day", done.lastDay());
            doneTag.put(id.toString(), one);
        });
        tag.put("completed", doneTag);
        tag.putInt("dailyDay", dailyDay);
        ListTag offer = new ListTag();
        for (ResourceLocation id : dailyOffer) {
            offer.add(StringTag.valueOf(id.toString()));
        }
        tag.put("dailyOffer", offer);
        tag.putInt("dailyDone", dailyDone);

        CompoundTag connectionTag = new CompoundTag();
        connections.forEach((uuid, c) -> connectionTag.put(uuid.toString(), c.save()));
        tag.put("connections", connectionTag);
        return tag;
    }

    public void load(CompoundTag tag) {
        titles.clear();
        CompoundTag titleTag = tag.getCompound("titles");
        for (String key : titleTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                CompoundTag one = titleTag.getCompound(key);
                titles.put(id, new OwnedTitle(one.getInt("day"), one.getLong("at")));
            }
        }
        String shown = tag.getString("displayed");
        displayed = shown.isEmpty() ? null : ResourceLocation.tryParse(shown);
        if (displayed != null && !titles.containsKey(displayed)) {
            displayed = null;
        }

        counters.clear();
        CompoundTag counterTag = tag.getCompound("counters");
        for (String key : counterTag.getAllKeys()) {
            counters.put(key, Math.max(0L, counterTag.getLong(key)));
        }
        earnedSinceZero = tag.contains("sinceZero") ? tag.getLong("sinceZero") : -1L;
        visitorsDay = tag.getInt("visitorsDay");
        visitorsToday.clear();
        ListTag visitors = tag.getList("visitors", Tag.TAG_STRING);
        for (int i = 0; i < visitors.size(); i++) {
            try {
                visitorsToday.add(UUID.fromString(visitors.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // 壊れた値は飛ばす
            }
        }

        records.clear();
        CompoundTag recordTag = tag.getCompound("records");
        for (String key : recordTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                CompoundTag one = recordTag.getCompound(key);
                records.put(id, new RecordEntry(one.getBoolean("read"), one.getInt("day")));
            }
        }
        recordBook = tag.getBoolean("recordBook");

        bonds.clear();
        CompoundTag bondTag = tag.getCompound("bonds");
        for (String key : bondTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                bonds.put(id, Bond.load(bondTag.getCompound(key)));
            }
        }

        quests.clear();
        CompoundTag questTag = tag.getCompound("quests");
        for (String key : questTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                quests.put(id, QuestProgress.load(questTag.getCompound(key)));
            }
        }
        completed.clear();
        CompoundTag doneTag = tag.getCompound("completed");
        for (String key : doneTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                CompoundTag one = doneTag.getCompound(key);
                completed.put(id, new Completed(Math.max(1, one.getInt("times")), one.getInt("day")));
            }
        }
        dailyDay = tag.getInt("dailyDay");
        dailyOffer.clear();
        ListTag offer = tag.getList("dailyOffer", Tag.TAG_STRING);
        for (int i = 0; i < offer.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(offer.getString(i));
            if (id != null) {
                dailyOffer.add(id);
            }
        }
        dailyDone = Math.max(0, tag.getInt("dailyDone"));

        connections.clear();
        CompoundTag connectionTag = tag.getCompound("connections");
        for (String key : connectionTag.getAllKeys()) {
            try {
                connections.put(UUID.fromString(key), Connection.load(connectionTag.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
                // 壊れた値は飛ばす
            }
        }
    }

    /** 死亡・リスポーンのとき、古い体のデータを写す。 */
    public void copyFrom(GuildData other) {
        titles.clear();
        titles.putAll(other.titles);
        displayed = other.displayed;
        counters.clear();
        counters.putAll(other.counters);
        earnedSinceZero = other.earnedSinceZero;
        visitorsDay = other.visitorsDay;
        visitorsToday.clear();
        visitorsToday.addAll(other.visitorsToday);
        records.clear();
        records.putAll(other.records);
        recordBook = other.recordBook;
        bonds.clear();
        other.bonds.forEach((npc, bond) -> bonds.put(npc, bond.copy()));
        quests.clear();
        other.quests.forEach((id, progress) -> quests.put(id, progress.copy()));
        completed.clear();
        completed.putAll(other.completed);
        dailyDay = other.dailyDay;
        dailyOffer.clear();
        dailyOffer.addAll(other.dailyOffer);
        dailyDone = other.dailyDone;
        connections.clear();
        other.connections.forEach((uuid, c) -> connections.put(uuid, c.copy()));
    }
}

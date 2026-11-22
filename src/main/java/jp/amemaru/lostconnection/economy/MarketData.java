package jp.amemaru.lostconnection.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * フリーマーケットの台帳（Phase 0-B 8章 MarketData「出品（出品者・品物・価格・期限）、品物ごとの取引の履歴と相場」。Phase 6）。
 * ワールドの data の {@code amemaru_market.dat}。
 *
 * <ul>
 *   <li>出品：番号・出品者・品物（数ごと）・1個の値段・出品した日・下がる日</li>
 *   <li>売上：売り手ごとの、まだ受け取っていない額（手数料を引いたあと）</li>
 *   <li>取引：品ごとの最近の取引（日・1個の値段・数）。相場の変動と「最近の取引価格」に使う</li>
 *   <li>相場：品ごとの倍率（相場の変動で動いた分。相場＝{@link MarketPrices} の基準価格×倍率）</li>
 *   <li>履歴：人ごとの最近の出品・売れた・買った・取り消し・受け取り</li>
 *   <li>露店：区画ごとの借りている人・店名・最後の日。寄付で区画が増えたか</li>
 * </ul>
 */
public class MarketData extends SavedData {
    private static final String NAME = "amemaru_market";
    /** 品ごとに残す取引の数 */
    public static final int TRADES_PER_ITEM = 60;
    /** 人ごとに残す履歴の数 */
    public static final int HISTORY_PER_PLAYER = 40;

    /** 出品 */
    public static final class Listing {
        public final long id;
        public final UUID seller;
        public String sellerName;
        public final ItemStack item;
        public final long unitPrice;
        public final int listedDay;
        /** この日の朝に下がる（0なら下げない） */
        public int expiresDay;
        public final long listedAt;

        public Listing(long id, UUID seller, String sellerName, ItemStack item, long unitPrice, int listedDay, int expiresDay, long listedAt) {
            this.id = id;
            this.seller = seller;
            this.sellerName = sellerName;
            this.item = item;
            this.unitPrice = unitPrice;
            this.listedDay = listedDay;
            this.expiresDay = expiresDay;
            this.listedAt = listedAt;
        }

        public long total() {
            return Wallet.saturatedMultiply(unitPrice, item.getCount());
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("id", id);
            tag.putUUID("seller", seller);
            tag.putString("sellerName", sellerName);
            tag.put("item", item.save(new CompoundTag()));
            tag.putLong("unitPrice", unitPrice);
            tag.putInt("listedDay", listedDay);
            tag.putInt("expiresDay", expiresDay);
            tag.putLong("listedAt", listedAt);
            return tag;
        }

        static Listing load(CompoundTag tag) {
            return new Listing(tag.getLong("id"), tag.getUUID("seller"), tag.getString("sellerName"), ItemStack.of(tag.getCompound("item")),
                    tag.getLong("unitPrice"), tag.getInt("listedDay"), tag.getInt("expiresDay"), tag.getLong("listedAt"));
        }
    }

    /** 1件の取引（base は、そのときの相場表の基準価格。相場の変動で、値段との比を見る） */
    public record Trade(int day, long unitPrice, int count, long base) {
    }

    /** 履歴の種類 */
    public enum Kind { LISTED, SOLD, BOUGHT, CANCELLED, EXPIRED, COLLECTED, RENTED }

    /** 履歴の1行（品は数ごと。受け取り・借りたときは空） */
    public record History(int day, Kind kind, ItemStack item, long amount, long fee, String other) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("day", day);
            tag.putString("kind", kind.name());
            if (!item.isEmpty()) {
                tag.put("item", item.save(new CompoundTag()));
            }
            tag.putLong("amount", amount);
            tag.putLong("fee", fee);
            tag.putString("other", other);
            return tag;
        }

        static History load(CompoundTag tag) {
            Kind kind;
            try {
                kind = Kind.valueOf(tag.getString("kind"));
            } catch (IllegalArgumentException e) {
                kind = Kind.LISTED;
            }
            return new History(tag.getInt("day"), kind, tag.contains("item") ? ItemStack.of(tag.getCompound("item")) : ItemStack.EMPTY,
                    tag.getLong("amount"), tag.getLong("fee"), tag.getString("other"));
        }
    }

    /** 借りている露店 */
    public static final class StallRent {
        public final UUID owner;
        public String ownerName;
        public String name;
        /** 借りている最後の日（この日まで） */
        public int lastDay;
        /** 管理者が店名を隠したか */
        public boolean hidden;
        /** 露店の画面を開いた人の数（来客） */
        public int visitors;

        public StallRent(UUID owner, String ownerName, String name, int lastDay) {
            this.owner = owner;
            this.ownerName = ownerName;
            this.name = name;
            this.lastDay = lastDay;
        }
    }

    private long nextId = 1;
    private final Map<Long, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, Long> proceeds = new HashMap<>();
    private final Map<String, Deque<Trade>> trades = new HashMap<>();
    /** 品ごとの相場の倍率（相場表の基準価格に対して。なければ1.0） */
    private final Map<String, Double> factors = new HashMap<>();
    private final Map<UUID, Deque<History>> history = new HashMap<>();
    private final Map<Integer, StallRent> stalls = new TreeMap<>();
    /** 寄付で露店の区画が20に増えたか */
    private boolean expanded;
    /** 1件でも取引が成立したか（称号「市場の開拓者」のため） */
    private boolean firstTradeDone;
    /** 最初の取引の売り手と買い手（Phase 0-A 22章の称号「市場の開拓者」。称号は Phase 6 の3番目で渡す） */
    @Nullable
    private UUID firstSeller;
    @Nullable
    private UUID firstBuyer;

    public static MarketData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(MarketData::load, MarketData::new, NAME);
    }

    // ---------------------------------------------------------------- 出品

    public Listing add(UUID seller, String sellerName, ItemStack item, long unitPrice, int day, int expiresDay, long now) {
        Listing listing = new Listing(nextId++, seller, sellerName, item.copy(), unitPrice, day, expiresDay, now);
        listings.put(listing.id, listing);
        setDirty();
        return listing;
    }

    @Nullable
    public Listing listing(long id) {
        return listings.get(id);
    }

    @Nullable
    public Listing remove(long id) {
        Listing removed = listings.remove(id);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    public List<Listing> listings() {
        return new ArrayList<>(listings.values());
    }

    public List<Listing> listingsOf(UUID seller) {
        List<Listing> list = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (listing.seller.equals(seller)) {
                list.add(listing);
            }
        }
        return list;
    }

    public int countOf(UUID seller) {
        int n = 0;
        for (Listing listing : listings.values()) {
            if (listing.seller.equals(seller)) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 売上

    public long proceeds(UUID seller) {
        return proceeds.getOrDefault(seller, 0L);
    }

    public void addProceeds(UUID seller, long amount) {
        if (amount > 0) {
            proceeds.merge(seller, amount, Wallet::saturatedAdd);
            setDirty();
        }
    }

    /** 売上を全部取り出す（取り出した額を返す）。 */
    public long takeProceeds(UUID seller) {
        Long amount = proceeds.remove(seller);
        if (amount != null) {
            setDirty();
        }
        return amount == null ? 0 : amount;
    }

    /** まだ受け取られていない売上の合計（世界にあるノットの一部）。 */
    public long pendingProceeds() {
        long sum = 0;
        for (long amount : proceeds.values()) {
            sum = Wallet.saturatedAdd(sum, amount);
        }
        return sum;
    }

    // ---------------------------------------------------------------- 取引と相場

    public void recordTrade(String key, Trade trade) {
        Deque<Trade> deque = trades.computeIfAbsent(key, k -> new ArrayDeque<>());
        deque.addFirst(trade);
        while (deque.size() > TRADES_PER_ITEM) {
            deque.removeLast();
        }
        firstTradeDone = true;
        setDirty();
    }

    /** 新しい順の取引。 */
    public List<Trade> trades(String key) {
        Deque<Trade> deque = trades.get(key);
        return deque == null ? List.of() : new ArrayList<>(deque);
    }

    public Map<String, Deque<Trade>> allTrades() {
        return trades;
    }

    /** 相場の倍率（なければ1.0）。 */
    public double factor(String key) {
        return factors.getOrDefault(key, 1.0);
    }

    public void setFactor(String key, double value) {
        if (Math.abs(value - 1.0) < 1.0E-9) {
            factors.remove(key);
        } else {
            factors.put(key, value);
        }
        setDirty();
    }

    public Map<String, Double> factors() {
        return factors;
    }

    public boolean firstTradeDone() {
        return firstTradeDone;
    }

    /** 最初の取引の2人を控える（もう控えていれば何もしない）。 */
    public void markFirstTrade(UUID seller, UUID buyer) {
        if (firstSeller == null && firstBuyer == null) {
            firstSeller = seller;
            firstBuyer = buyer;
            setDirty();
        }
    }

    @Nullable
    public UUID firstSeller() {
        return firstSeller;
    }

    @Nullable
    public UUID firstBuyer() {
        return firstBuyer;
    }

    // ---------------------------------------------------------------- 履歴

    public void addHistory(UUID player, History line) {
        Deque<History> deque = history.computeIfAbsent(player, k -> new ArrayDeque<>());
        deque.addFirst(line);
        while (deque.size() > HISTORY_PER_PLAYER) {
            deque.removeLast();
        }
        setDirty();
    }

    /** 新しい順。 */
    public List<History> history(UUID player) {
        Deque<History> deque = history.get(player);
        return deque == null ? List.of() : new ArrayList<>(deque);
    }

    // ---------------------------------------------------------------- 露店

    @Nullable
    public StallRent stall(int num) {
        return stalls.get(num);
    }

    public Map<Integer, StallRent> stalls() {
        return stalls;
    }

    /** その人が借りている区画の番号（なければ0）。 */
    public int stallOf(UUID owner) {
        for (Map.Entry<Integer, StallRent> entry : stalls.entrySet()) {
            if (entry.getValue().owner.equals(owner)) {
                return entry.getKey();
            }
        }
        return 0;
    }

    public void setStall(int num, @Nullable StallRent rent) {
        if (rent == null) {
            stalls.remove(num);
        } else {
            stalls.put(num, rent);
        }
        setDirty();
    }

    public boolean expanded() {
        return expanded;
    }

    public void setExpanded(boolean value) {
        expanded = value;
        setDirty();
    }

    // ---------------------------------------------------------------- 保存

    public static MarketData fromTag(CompoundTag tag) {
        return load(tag);
    }

    static MarketData load(CompoundTag tag) {
        MarketData data = new MarketData();
        data.nextId = Math.max(1, tag.getLong("nextId"));
        ListTag list = tag.getList("listings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Listing listing = Listing.load(list.getCompound(i));
            if (!listing.item.isEmpty()) {
                data.listings.put(listing.id, listing);
                data.nextId = Math.max(data.nextId, listing.id + 1);
            }
        }
        CompoundTag proceeds = tag.getCompound("proceeds");
        for (String key : proceeds.getAllKeys()) {
            try {
                data.proceeds.put(UUID.fromString(key), proceeds.getLong(key));
            } catch (IllegalArgumentException ignored) {
                // 読めない人の番号は飛ばす
            }
        }
        CompoundTag trades = tag.getCompound("trades");
        for (String key : trades.getAllKeys()) {
            ListTag entries = trades.getList(key, Tag.TAG_COMPOUND);
            Deque<Trade> deque = new ArrayDeque<>();
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag t = entries.getCompound(i);
                deque.addLast(new Trade(t.getInt("day"), t.getLong("price"), Math.max(1, t.getInt("count")), t.getLong("base")));
            }
            data.trades.put(key, deque);
        }
        CompoundTag factors = tag.getCompound("factors");
        for (String key : factors.getAllKeys()) {
            data.factors.put(key, factors.getDouble(key));
        }
        CompoundTag history = tag.getCompound("history");
        for (String key : history.getAllKeys()) {
            try {
                UUID owner = UUID.fromString(key);
                ListTag entries = history.getList(key, Tag.TAG_COMPOUND);
                Deque<History> deque = new ArrayDeque<>();
                for (int i = 0; i < entries.size(); i++) {
                    deque.addLast(History.load(entries.getCompound(i)));
                }
                data.history.put(owner, deque);
            } catch (IllegalArgumentException ignored) {
                // 読めない人の番号は飛ばす
            }
        }
        ListTag stalls = tag.getList("stalls", Tag.TAG_COMPOUND);
        for (int i = 0; i < stalls.size(); i++) {
            CompoundTag s = stalls.getCompound(i);
            StallRent rent = new StallRent(s.getUUID("owner"), s.getString("ownerName"), s.getString("name"), s.getInt("lastDay"));
            rent.hidden = s.getBoolean("hidden");
            rent.visitors = s.getInt("visitors");
            data.stalls.put(s.getInt("num"), rent);
        }
        data.expanded = tag.getBoolean("expanded");
        data.firstTradeDone = tag.getBoolean("firstTradeDone");
        if (tag.hasUUID("firstSeller")) {
            data.firstSeller = tag.getUUID("firstSeller");
        }
        if (tag.hasUUID("firstBuyer")) {
            data.firstBuyer = tag.getUUID("firstBuyer");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("nextId", nextId);
        ListTag list = new ListTag();
        for (Listing listing : listings.values()) {
            list.add(listing.save());
        }
        tag.put("listings", list);
        CompoundTag proceedsTag = new CompoundTag();
        for (Map.Entry<UUID, Long> entry : proceeds.entrySet()) {
            proceedsTag.putLong(entry.getKey().toString(), entry.getValue());
        }
        tag.put("proceeds", proceedsTag);
        CompoundTag tradesTag = new CompoundTag();
        for (Map.Entry<String, Deque<Trade>> entry : trades.entrySet()) {
            ListTag entries = new ListTag();
            for (Trade trade : entry.getValue()) {
                CompoundTag t = new CompoundTag();
                t.putInt("day", trade.day());
                t.putLong("price", trade.unitPrice());
                t.putInt("count", trade.count());
                t.putLong("base", trade.base());
                entries.add(t);
            }
            tradesTag.put(entry.getKey(), entries);
        }
        tag.put("trades", tradesTag);
        CompoundTag factorsTag = new CompoundTag();
        for (Map.Entry<String, Double> entry : factors.entrySet()) {
            factorsTag.putDouble(entry.getKey(), entry.getValue());
        }
        tag.put("factors", factorsTag);
        CompoundTag historyTag = new CompoundTag();
        for (Map.Entry<UUID, Deque<History>> entry : history.entrySet()) {
            ListTag entries = new ListTag();
            for (History line : entry.getValue()) {
                entries.add(line.save());
            }
            historyTag.put(entry.getKey().toString(), entries);
        }
        tag.put("history", historyTag);
        ListTag stallsTag = new ListTag();
        for (Map.Entry<Integer, StallRent> entry : stalls.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.putInt("num", entry.getKey());
            s.putUUID("owner", entry.getValue().owner);
            s.putString("ownerName", entry.getValue().ownerName);
            s.putString("name", entry.getValue().name);
            s.putInt("lastDay", entry.getValue().lastDay);
            s.putBoolean("hidden", entry.getValue().hidden);
            s.putInt("visitors", entry.getValue().visitors);
            stallsTag.add(s);
        }
        tag.put("stalls", stallsTag);
        tag.putBoolean("expanded", expanded);
        tag.putBoolean("firstTradeDone", firstTradeDone);
        if (firstSeller != null) {
            tag.putUUID("firstSeller", firstSeller);
        }
        if (firstBuyer != null) {
            tag.putUUID("firstBuyer", firstBuyer);
        }
        return tag;
    }
}

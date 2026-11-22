package jp.amemaru.lostconnection.guild;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * サーバー全体の「ギルド」の記録（Phase 6 その3。ワールドの data フォルダの {@code amemaru_guild.dat}）。
 *
 * <ul>
 *   <li>限定称号を取った人（サーバーで最初の1人・1パーティだけ）</li>
 *   <li>管理者がその場で作ったユニーク称号（名前・色・説明・1人限定か）</li>
 *   <li>称号の記録（誰がどの称号を、いつ取ったか。Phase 0-A 22章【確定】）</li>
 *   <li>冒険日誌（サーバーで起きた出来事の年表。Phase 0-A 14章「途中参加・見逃し対策」）</li>
 *   <li>露店の区画を増やす寄付（Phase 0-A 7章：フィンが寄付を募り、目標に届いた次の朝に増える）</li>
 *   <li>いない人あての知らせ（フリマで売れたときの取引の数・縁帳など。入ったときに渡す）</li>
 * </ul>
 */
public final class GuildState extends SavedData {
    private static final String NAME = "amemaru_guild";
    /** 称号の記録の上限（古いものから消す）。 */
    public static final int TITLE_LOG_MAX = 4000;
    /** 冒険日誌の上限。 */
    public static final int CHRONICLE_MAX = 500;
    /** いない人あての知らせの上限（1人あたり）。 */
    public static final int PENDING_MAX = 200;

    /** 限定称号を取った人。 */
    public record Holder(UUID uuid, String name, int day) {
    }

    /** 管理者が作ったユニーク称号（Phase 0-A 22章【確定】）。{@code color} が -1 なら虹色。 */
    public record CustomTitle(ResourceLocation id, String name, String description, int color, boolean single, String createdBy,
                              int day) {
    }

    /** 称号の記録。 */
    public record TitleLog(UUID uuid, String name, ResourceLocation title, int day, long epochMillis) {
    }

    /** 冒険日誌の一行（翻訳キーと、差し込む文字）。 */
    public record ChronicleEntry(int day, long epochMillis, String key, List<String> args) {
    }

    private final Map<ResourceLocation, List<Holder>> limited = new LinkedHashMap<>();
    private final Map<ResourceLocation, CustomTitle> customs = new LinkedHashMap<>();
    private int nextCustom = 1;
    private final List<TitleLog> titleLog = new ArrayList<>();
    private final List<ChronicleEntry> chronicle = new ArrayList<>();
    private final Map<UUID, List<CompoundTag>> pending = new LinkedHashMap<>();
    /**
     * 称号のない「サーバーで最初」のできごと（偉業。Phase 6 その4）。ユニーク装備（Phase 8）を渡すときに使う。
     * 例：{@code first_commission_perfect}（他人からの依頼で、完璧なアクセサリーを最初に仕上げた装飾師＝真眼の拡大鏡。Phase 0-A 23章）。
     */
    private final Map<String, Holder> feats = new LinkedHashMap<>();

    // ---- 露店の区画を増やす寄付
    private long stallDonated;
    private final Map<UUID, Long> stallDonors = new LinkedHashMap<>();
    private final Map<UUID, String> stallDonorNames = new LinkedHashMap<>();
    /** 目標に届いた日（0なら届いていない）。次の朝（次の日）に区画が増える。 */
    private int stallReachedDay;
    private boolean stallExpanded;

    public static GuildState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(GuildState::load, GuildState::new, NAME);
    }

    // ================================================================ 偉業（Phase 6 その4）

    /** まだ誰も成していなければ記録して true。 */
    public boolean markFeat(String event, Holder holder) {
        if (feats.containsKey(event)) {
            return false;
        }
        feats.put(event, holder);
        setDirty();
        return true;
    }

    @Nullable
    public Holder feat(String event) {
        return feats.get(event);
    }

    // ================================================================ 限定称号

    public List<Holder> holders(ResourceLocation title) {
        return Collections.unmodifiableList(limited.getOrDefault(title, List.of()));
    }

    public boolean limitedTaken(ResourceLocation title) {
        return !limited.getOrDefault(title, List.of()).isEmpty();
    }

    public void setHolders(ResourceLocation title, List<Holder> holders) {
        limited.put(title, new ArrayList<>(holders));
        setDirty();
    }

    public void clearHolders(ResourceLocation title) {
        if (limited.remove(title) != null) {
            setDirty();
        }
    }

    // ================================================================ ユニーク称号

    public Map<ResourceLocation, CustomTitle> customs() {
        return Collections.unmodifiableMap(customs);
    }

    @Nullable
    public CustomTitle custom(ResourceLocation id) {
        return customs.get(id);
    }

    public CustomTitle createCustom(String name, String description, int color, boolean single, String createdBy, int day) {
        ResourceLocation id = new ResourceLocation("amemaru", "unique/" + nextCustom++);
        CustomTitle title = new CustomTitle(id, name, description, color, single, createdBy, day);
        customs.put(id, title);
        setDirty();
        return title;
    }

    public boolean removeCustom(ResourceLocation id) {
        if (customs.remove(id) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    // ================================================================ 称号の記録

    public void logTitle(UUID uuid, String name, ResourceLocation title, int day, long epochMillis) {
        titleLog.add(new TitleLog(uuid, name, title, day, epochMillis));
        while (titleLog.size() > TITLE_LOG_MAX) {
            titleLog.remove(0);
        }
        setDirty();
    }

    public List<TitleLog> titleLog() {
        return Collections.unmodifiableList(titleLog);
    }

    /** その称号を持っている人の数（記録から。取り消しは数えない）。 */
    public int ownersOf(ResourceLocation title) {
        java.util.Set<UUID> owners = new java.util.HashSet<>();
        for (TitleLog log : titleLog) {
            if (log.title().equals(title)) {
                owners.add(log.uuid());
            }
        }
        return owners.size();
    }

    // ================================================================ 冒険日誌

    public void chronicle(int day, long epochMillis, String key, List<String> args) {
        chronicle.add(new ChronicleEntry(day, epochMillis, key, List.copyOf(args)));
        while (chronicle.size() > CHRONICLE_MAX) {
            chronicle.remove(0);
        }
        setDirty();
    }

    public List<ChronicleEntry> chronicle() {
        return Collections.unmodifiableList(chronicle);
    }

    // ================================================================ いない人あての知らせ

    public void addPending(UUID uuid, CompoundTag event) {
        List<CompoundTag> list = pending.computeIfAbsent(uuid, key -> new ArrayList<>());
        list.add(event);
        while (list.size() > PENDING_MAX) {
            list.remove(0);
        }
        setDirty();
    }

    /** 知らせを取り出す（取り出したら消える）。 */
    public List<CompoundTag> takePending(UUID uuid) {
        List<CompoundTag> list = pending.remove(uuid);
        if (list == null) {
            return List.of();
        }
        setDirty();
        return list;
    }

    // ================================================================ 露店の区画の寄付

    public long stallDonated() {
        return stallDonated;
    }

    public void addStallDonation(UUID uuid, String name, long amount) {
        if (amount <= 0) {
            return;
        }
        stallDonated += amount;
        stallDonors.merge(uuid, amount, Long::sum);
        stallDonorNames.put(uuid, name);
        setDirty();
    }

    public Map<UUID, Long> stallDonors() {
        return Collections.unmodifiableMap(stallDonors);
    }

    public String stallDonorName(UUID uuid) {
        return stallDonorNames.getOrDefault(uuid, "?");
    }

    public int stallReachedDay() {
        return stallReachedDay;
    }

    public void setStallReachedDay(int day) {
        stallReachedDay = day;
        setDirty();
    }

    public boolean stallExpanded() {
        return stallExpanded;
    }

    public void setStallExpanded(boolean value) {
        stallExpanded = value;
        setDirty();
    }

    /** 寄付をはじめからにする（管理者）。 */
    public void resetStallDonation() {
        stallDonated = 0;
        stallDonors.clear();
        stallDonorNames.clear();
        stallReachedDay = 0;
        stallExpanded = false;
        setDirty();
    }

    // ================================================================ 保存

    public static GuildState load(CompoundTag tag) {
        GuildState state = new GuildState();
        CompoundTag limitedTag = tag.getCompound("limited");
        for (String key : limitedTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id == null) {
                continue;
            }
            List<Holder> holders = new ArrayList<>();
            for (Tag t : limitedTag.getList(key, Tag.TAG_COMPOUND)) {
                CompoundTag one = (CompoundTag) t;
                if (one.hasUUID("uuid")) {
                    holders.add(new Holder(one.getUUID("uuid"), one.getString("name"), one.getInt("day")));
                }
            }
            state.limited.put(id, holders);
        }
        for (Tag t : tag.getList("customs", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) t;
            ResourceLocation id = ResourceLocation.tryParse(one.getString("id"));
            if (id != null) {
                state.customs.put(id, new CustomTitle(id, one.getString("name"), one.getString("description"), one.getInt("color"),
                        one.getBoolean("single"), one.getString("by"), one.getInt("day")));
            }
        }
        state.nextCustom = Math.max(1, tag.getInt("nextCustom"));
        for (Tag t : tag.getList("titleLog", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) t;
            ResourceLocation id = ResourceLocation.tryParse(one.getString("title"));
            if (id != null && one.hasUUID("uuid")) {
                state.titleLog.add(new TitleLog(one.getUUID("uuid"), one.getString("name"), id, one.getInt("day"), one.getLong("at")));
            }
        }
        for (Tag t : tag.getList("chronicle", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) t;
            List<String> args = new ArrayList<>();
            for (Tag a : one.getList("args", Tag.TAG_STRING)) {
                args.add(a.getAsString());
            }
            state.chronicle.add(new ChronicleEntry(one.getInt("day"), one.getLong("at"), one.getString("key"), args));
        }
        CompoundTag pendingTag = tag.getCompound("pending");
        for (String key : pendingTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                List<CompoundTag> list = new ArrayList<>();
                for (Tag t : pendingTag.getList(key, Tag.TAG_COMPOUND)) {
                    list.add((CompoundTag) t);
                }
                state.pending.put(uuid, list);
            } catch (IllegalArgumentException ignored) {
                // 壊れた値は飛ばす
            }
        }
        CompoundTag stall = tag.getCompound("stallDonation");
        state.stallDonated = Math.max(0L, stall.getLong("total"));
        CompoundTag donors = stall.getCompound("donors");
        for (String key : donors.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                CompoundTag one = donors.getCompound(key);
                state.stallDonors.put(uuid, Math.max(0L, one.getLong("amount")));
                state.stallDonorNames.put(uuid, one.getString("name"));
            } catch (IllegalArgumentException ignored) {
                // 壊れた値は飛ばす
            }
        }
        state.stallReachedDay = stall.getInt("reachedDay");
        state.stallExpanded = stall.getBoolean("expanded");
        CompoundTag featTag = tag.getCompound("feats");
        for (String event : featTag.getAllKeys()) {
            CompoundTag one = featTag.getCompound(event);
            if (one.hasUUID("uuid")) {
                state.feats.put(event, new Holder(one.getUUID("uuid"), one.getString("name"), one.getInt("day")));
            }
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", 1);
        CompoundTag featTag = new CompoundTag();
        feats.forEach((event, holder) -> {
            CompoundTag one = new CompoundTag();
            one.putUUID("uuid", holder.uuid());
            one.putString("name", holder.name());
            one.putInt("day", holder.day());
            featTag.put(event, one);
        });
        tag.put("feats", featTag);
        CompoundTag limitedTag = new CompoundTag();
        limited.forEach((id, holders) -> {
            ListTag list = new ListTag();
            for (Holder holder : holders) {
                CompoundTag one = new CompoundTag();
                one.putUUID("uuid", holder.uuid());
                one.putString("name", holder.name());
                one.putInt("day", holder.day());
                list.add(one);
            }
            limitedTag.put(id.toString(), list);
        });
        tag.put("limited", limitedTag);
        ListTag customTag = new ListTag();
        for (CustomTitle title : customs.values()) {
            CompoundTag one = new CompoundTag();
            one.putString("id", title.id().toString());
            one.putString("name", title.name());
            one.putString("description", title.description());
            one.putInt("color", title.color());
            one.putBoolean("single", title.single());
            one.putString("by", title.createdBy());
            one.putInt("day", title.day());
            customTag.add(one);
        }
        tag.put("customs", customTag);
        tag.putInt("nextCustom", nextCustom);
        ListTag logTag = new ListTag();
        for (TitleLog log : titleLog) {
            CompoundTag one = new CompoundTag();
            one.putUUID("uuid", log.uuid());
            one.putString("name", log.name());
            one.putString("title", log.title().toString());
            one.putInt("day", log.day());
            one.putLong("at", log.epochMillis());
            logTag.add(one);
        }
        tag.put("titleLog", logTag);
        ListTag chronicleTag = new ListTag();
        for (ChronicleEntry entry : chronicle) {
            CompoundTag one = new CompoundTag();
            one.putInt("day", entry.day());
            one.putLong("at", entry.epochMillis());
            one.putString("key", entry.key());
            ListTag args = new ListTag();
            for (String arg : entry.args()) {
                args.add(net.minecraft.nbt.StringTag.valueOf(arg));
            }
            one.put("args", args);
            chronicleTag.add(one);
        }
        tag.put("chronicle", chronicleTag);
        CompoundTag pendingTag = new CompoundTag();
        pending.forEach((uuid, list) -> {
            ListTag events = new ListTag();
            events.addAll(list);
            pendingTag.put(uuid.toString(), events);
        });
        tag.put("pending", pendingTag);
        CompoundTag stall = new CompoundTag();
        stall.putLong("total", stallDonated);
        CompoundTag donors = new CompoundTag();
        stallDonors.forEach((uuid, amount) -> {
            CompoundTag one = new CompoundTag();
            one.putLong("amount", amount);
            one.putString("name", stallDonorNames.getOrDefault(uuid, ""));
            donors.put(uuid.toString(), one);
        });
        stall.put("donors", donors);
        stall.putInt("reachedDay", stallReachedDay);
        stall.putBoolean("expanded", stallExpanded);
        tag.put("stallDonation", stall);
        return tag;
    }
}

package jp.amemaru.lostconnection.social;

import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 依頼台の依頼の一件（Phase 6 その4。Phase 0-A 21章「装飾師による依頼の厳選」【提案】）。
 *
 * <p>持ち主がアクセサリーと素材・ノットを預け、「固定するサブ」「目標」「回数の上限」「手数料」を決める。装飾師が受けて、
 * 持ち主の素材で厳選する。品と素材・ノットはサーバーが預かる（{@link CommissionData}）。</p>
 */
public final class Commission {
    /** 依頼の状態。 */
    public enum State {
        /** 受付中（装飾師を待っている）。 */
        OPEN,
        /** 装飾師が受けて、厳選している。 */
        WORKING,
        /** 終わった（品と残りは持ち主へ、手数料は装飾師へ渡した）。 */
        DONE
    }

    /** 何を頼むか。 */
    public enum Kind {
        /** 数値だけ振り直す。 */
        VALUES,
        /** 種類ごと振り直す。 */
        TYPES,
        /** 強化する（目標の段まで）。 */
        ENHANCE;

        public static Kind byName(String name) {
            for (Kind kind : values()) {
                if (kind.name().equalsIgnoreCase(name)) {
                    return kind;
                }
            }
            return VALUES;
        }
    }

    /** 目標の種類。 */
    public enum TargetType {
        /** あるサブが、上限に対して X% 以上（例：会心が割合90%以上）。 */
        STAT,
        /** 品質が ★N 以上。 */
        QUALITY,
        /** 完璧なアクセサリー。 */
        PERFECT,
        /** 強化値が +N 以上（強化の依頼）。 */
        ENHANCE,
        /** 目標なし（回数の上限まで振り直す）。 */
        NONE;

        public static TargetType byName(String name) {
            for (TargetType type : values()) {
                if (type.name().equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return NONE;
        }
    }

    /** 終わった理由。 */
    public enum EndReason {
        TARGET, STOPPED, LIMIT, EXPIRED, CANCELLED, NO_FUNDS, NONE;

        public static EndReason byName(String name) {
            for (EndReason reason : values()) {
                if (reason.name().equalsIgnoreCase(name)) {
                    return reason;
                }
            }
            return NONE;
        }
    }

    int id;
    UUID owner;
    String ownerName = "";
    @Nullable
    UUID crafter;
    String crafterName = "";
    State state = State.OPEN;
    Kind kind = Kind.VALUES;
    ItemStack item = ItemStack.EMPTY;
    TargetType targetType = TargetType.NONE;
    @Nullable
    StatType targetStat;
    /** STAT の割合（%）・QUALITY の★・ENHANCE の段。 */
    int targetValue;
    int maxAttempts;
    int attempts;
    long fee;
    long escrowKnots;
    long spentKnots;
    final Map<ResourceLocation, Integer> escrowItems = new LinkedHashMap<>();
    int createdDay;
    long createdMillis;
    int endDay;
    EndReason endReason = EndReason.NONE;
    /** 振り直しの結果（新しいものが後ろ。JSONの文）。 */
    final List<String> log = new ArrayList<>();
    static final int LOG_MAX = 12;

    public int id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    @Nullable
    public UUID crafter() {
        return crafter;
    }

    public String crafterName() {
        return crafterName;
    }

    public State state() {
        return state;
    }

    public Kind kind() {
        return kind;
    }

    public ItemStack item() {
        return item;
    }

    public TargetType targetType() {
        return targetType;
    }

    @Nullable
    public StatType targetStat() {
        return targetStat;
    }

    public int targetValue() {
        return targetValue;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public int attempts() {
        return attempts;
    }

    public long fee() {
        return fee;
    }

    public long escrowKnots() {
        return escrowKnots;
    }

    public long spentKnots() {
        return spentKnots;
    }

    public Map<ResourceLocation, Integer> escrowItems() {
        return escrowItems;
    }

    public int createdDay() {
        return createdDay;
    }

    public EndReason endReason() {
        return endReason;
    }

    public List<String> log() {
        return log;
    }

    void addLog(String json) {
        log.add(json);
        while (log.size() > LOG_MAX) {
            log.remove(0);
        }
    }

    /** 預かっている素材の数。 */
    public int escrowCount(ResourceLocation item) {
        return escrowItems.getOrDefault(item, 0);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        if (crafter != null) {
            tag.putUUID("crafter", crafter);
            tag.putString("crafterName", crafterName);
        }
        tag.putString("state", state.name());
        tag.putString("kind", kind.name());
        tag.put("item", item.save(new CompoundTag()));
        tag.putString("target", targetType.name());
        if (targetStat != null) {
            tag.putString("targetStat", targetStat.id());
        }
        tag.putInt("targetValue", targetValue);
        tag.putInt("maxAttempts", maxAttempts);
        tag.putInt("attempts", attempts);
        tag.putLong("fee", fee);
        tag.putLong("escrowKnots", escrowKnots);
        tag.putLong("spentKnots", spentKnots);
        CompoundTag items = new CompoundTag();
        escrowItems.forEach((id, count) -> items.putInt(id.toString(), count));
        tag.put("escrowItems", items);
        tag.putInt("createdDay", createdDay);
        tag.putLong("createdMillis", createdMillis);
        tag.putInt("endDay", endDay);
        tag.putString("endReason", endReason.name());
        ListTag logTag = new ListTag();
        for (String line : log) {
            logTag.add(StringTag.valueOf(line));
        }
        tag.put("log", logTag);
        return tag;
    }

    @Nullable
    static Commission load(CompoundTag tag) {
        if (!tag.hasUUID("owner")) {
            return null;
        }
        Commission c = new Commission();
        c.id = tag.getInt("id");
        c.owner = tag.getUUID("owner");
        c.ownerName = tag.getString("ownerName");
        if (tag.hasUUID("crafter")) {
            c.crafter = tag.getUUID("crafter");
            c.crafterName = tag.getString("crafterName");
        }
        try {
            c.state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            c.state = State.DONE;
        }
        c.kind = Kind.byName(tag.getString("kind"));
        c.item = ItemStack.of(tag.getCompound("item"));
        c.targetType = TargetType.byName(tag.getString("target"));
        c.targetStat = tag.contains("targetStat") ? StatType.byId(tag.getString("targetStat")).orElse(null) : null;
        c.targetValue = tag.getInt("targetValue");
        c.maxAttempts = Math.max(0, tag.getInt("maxAttempts"));
        c.attempts = Math.max(0, tag.getInt("attempts"));
        c.fee = Math.max(0L, tag.getLong("fee"));
        c.escrowKnots = Math.max(0L, tag.getLong("escrowKnots"));
        c.spentKnots = Math.max(0L, tag.getLong("spentKnots"));
        CompoundTag items = tag.getCompound("escrowItems");
        for (String key : items.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null && items.getInt(key) > 0) {
                c.escrowItems.put(id, items.getInt(key));
            }
        }
        c.createdDay = tag.getInt("createdDay");
        c.createdMillis = tag.getLong("createdMillis");
        c.endDay = tag.getInt("endDay");
        c.endReason = EndReason.byName(tag.getString("endReason"));
        for (Tag t : tag.getList("log", Tag.TAG_STRING)) {
            c.log.add(t.getAsString());
        }
        return c;
    }
}

package jp.amemaru.lostconnection.social;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 依頼台の預かり（Phase 6 その4。ワールドの data フォルダの {@code amemaru_commissions.dat}。Phase 0-B 4.2 の
 * {@code SocialData}「協力依頼の預かり品と報酬」）。
 *
 * <ul>
 *   <li>依頼（預かっているアクセサリー・素材・ノット・手数料・結果）</li>
 *   <li>いない人あての受け取り（品・ノット。入ったときに渡す）</li>
 * </ul>
 */
public final class CommissionData extends SavedData {
    private static final String NAME = "amemaru_commissions";
    /** 終わった依頼を何日（進行日）残すか（画面で結果を見返すため）。 */
    public static final int KEEP_DONE_DAYS = 2;

    /** いない人あての受け取り。 */
    public static final class Payout {
        long knots;
        final List<ItemStack> items = new ArrayList<>();

        public long knots() {
            return knots;
        }

        public List<ItemStack> items() {
            return items;
        }

        boolean isEmpty() {
            return knots <= 0 && items.isEmpty();
        }
    }

    private final Map<Integer, Commission> commissions = new LinkedHashMap<>();
    private final Map<UUID, Payout> payouts = new LinkedHashMap<>();
    private int nextId = 1;

    public static CommissionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CommissionData::load, CommissionData::new, NAME);
    }

    // ================================================================ 依頼

    public Collection<Commission> all() {
        return Collections.unmodifiableCollection(commissions.values());
    }

    @Nullable
    public Commission byId(int id) {
        return commissions.get(id);
    }

    Commission create() {
        Commission c = new Commission();
        c.id = nextId++;
        commissions.put(c.id, c);
        setDirty();
        return c;
    }

    void remove(int id) {
        if (commissions.remove(id) != null) {
            setDirty();
        }
    }

    /** その人が出している、終わっていない依頼の数。 */
    public int activeOf(UUID owner) {
        int count = 0;
        for (Commission c : commissions.values()) {
            if (c.owner.equals(owner) && c.state != Commission.State.DONE) {
                count++;
            }
        }
        return count;
    }

    /** その装飾師が受けている依頼の数。 */
    public int workingOf(UUID crafter) {
        int count = 0;
        for (Commission c : commissions.values()) {
            if (c.state == Commission.State.WORKING && crafter.equals(c.crafter)) {
                count++;
            }
        }
        return count;
    }

    public void changed() {
        setDirty();
    }

    // ================================================================ いない人あての受け取り

    void addPayout(UUID uuid, long knots, List<ItemStack> items) {
        Payout payout = payouts.computeIfAbsent(uuid, key -> new Payout());
        payout.knots += Math.max(0L, knots);
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                payout.items.add(stack.copy());
            }
        }
        if (payout.isEmpty()) {
            payouts.remove(uuid);
        }
        setDirty();
    }

    @Nullable
    Payout takePayout(UUID uuid) {
        Payout payout = payouts.remove(uuid);
        if (payout != null) {
            setDirty();
        }
        return payout;
    }

    @Nullable
    public Payout peekPayout(UUID uuid) {
        return payouts.get(uuid);
    }

    /** 預かっているノットの合計（管理者の確かめ用）。 */
    public long heldKnots() {
        long total = 0;
        for (Commission c : commissions.values()) {
            if (c.state != Commission.State.DONE) {
                total += c.escrowKnots + c.fee;
            }
        }
        for (Payout payout : payouts.values()) {
            total += payout.knots;
        }
        return total;
    }

    // ================================================================ 保存

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", 1);
        tag.putInt("nextId", nextId);
        ListTag list = new ListTag();
        for (Commission c : commissions.values()) {
            list.add(c.save());
        }
        tag.put("commissions", list);
        CompoundTag payoutTag = new CompoundTag();
        payouts.forEach((uuid, payout) -> {
            CompoundTag one = new CompoundTag();
            one.putLong("knots", payout.knots);
            ListTag items = new ListTag();
            for (ItemStack stack : payout.items) {
                items.add(stack.save(new CompoundTag()));
            }
            one.put("items", items);
            payoutTag.put(uuid.toString(), one);
        });
        tag.put("payouts", payoutTag);
        return tag;
    }

    public static CommissionData load(CompoundTag tag) {
        CommissionData data = new CommissionData();
        data.nextId = Math.max(1, tag.getInt("nextId"));
        for (Tag t : tag.getList("commissions", Tag.TAG_COMPOUND)) {
            Commission c = Commission.load((CompoundTag) t);
            if (c != null) {
                data.commissions.put(c.id, c);
                data.nextId = Math.max(data.nextId, c.id + 1);
            }
        }
        CompoundTag payoutTag = tag.getCompound("payouts");
        for (String key : payoutTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                CompoundTag one = payoutTag.getCompound(key);
                Payout payout = new Payout();
                payout.knots = Math.max(0L, one.getLong("knots"));
                for (Tag t : one.getList("items", Tag.TAG_COMPOUND)) {
                    ItemStack stack = ItemStack.of((CompoundTag) t);
                    if (!stack.isEmpty()) {
                        payout.items.add(stack);
                    }
                }
                if (!payout.isEmpty()) {
                    data.payouts.put(uuid, payout);
                }
            } catch (IllegalArgumentException ignored) {
                // 壊れた値は飛ばす
            }
        }
        return data;
    }
}

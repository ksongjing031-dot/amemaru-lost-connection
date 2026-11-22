package jp.amemaru.lostconnection.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 個人倉庫の中身と契約（Phase 0-B 4.2 {@code StorageData}。Phase 6）。ワールドに1つ（ワールドの data の
 * {@code amemaru_storage.dat}）。本人がオフラインでも、管理者が確かめられる。
 *
 * <ul>
 *   <li>契約したページ数（0なら契約していない。1ページ＝27枠、上限は config。Phase 0-A 20章「上限5ページ（135枠）」）</li>
 *   <li>中身（ページ数×27枠）</li>
 *   <li>受け取り箱（フリマで買った品などが届く。契約していなくても使える）</li>
 * </ul>
 */
public final class StorageData extends SavedData {
    private static final String NAME = "amemaru_storage";
    /** 1ページの枠の数 */
    public static final int PAGE_SIZE = 27;

    /** 1人の倉庫 */
    public static final class Box {
        private int pages;
        private final List<ItemStack> items = new ArrayList<>();
        private final List<ItemStack> received = new ArrayList<>();
        private String name = "";

        public int pages() {
            return pages;
        }

        public boolean contracted() {
            return pages > 0;
        }

        public int capacity() {
            return pages * PAGE_SIZE;
        }

        /** 枠の品（契約の外なら空）。 */
        public ItemStack get(int index) {
            return index >= 0 && index < items.size() && index < capacity() ? items.get(index) : ItemStack.EMPTY;
        }

        public void set(int index, ItemStack stack) {
            if (index >= 0 && index < capacity()) {
                while (items.size() <= index) {
                    items.add(ItemStack.EMPTY);
                }
                items.set(index, stack);
            }
        }

        /** 受け取り箱（空の品は入れない。取り出した所は空になり、閉じたときに詰める）。 */
        public List<ItemStack> received() {
            return received;
        }

        /** 受け取り箱の空いた所を詰める。 */
        public void compactReceived() {
            received.removeIf(ItemStack::isEmpty);
        }

        /** 受け取り箱に入っている品の数（空いた所は数えない）。 */
        public int receivedCount() {
            int count = 0;
            for (ItemStack stack : received) {
                if (!stack.isEmpty()) {
                    count++;
                }
            }
            return count;
        }

        /** 中身（空いた枠も含む）。 */
        public List<ItemStack> items() {
            return Collections.unmodifiableList(items.subList(0, Math.min(items.size(), capacity())));
        }

        /** 入っている品の数（空いた枠は数えない）。 */
        public int usedSlots() {
            int count = 0;
            for (int i = 0; i < capacity() && i < items.size(); i++) {
                if (!items.get(i).isEmpty()) {
                    count++;
                }
            }
            return count;
        }

        public String name() {
            return name;
        }

        public void setName(String name) {
            this.name = name == null ? "" : name;
        }

        /**
         * ページ数を変える。減らすときは、外に出る枠の品を受け取り箱へ移す（なくさない）。
         */
        public void setPages(int value) {
            int next = Math.max(0, value);
            if (next < pages) {
                for (int i = next * PAGE_SIZE; i < items.size(); i++) {
                    ItemStack stack = items.get(i);
                    if (!stack.isEmpty()) {
                        received.add(stack);
                    }
                }
                while (items.size() > next * PAGE_SIZE) {
                    items.remove(items.size() - 1);
                }
            }
            pages = next;
            while (items.size() < pages * PAGE_SIZE) {
                items.add(ItemStack.EMPTY);
            }
        }
    }

    private final Map<UUID, Box> boxes = new HashMap<>();

    public static StorageData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(StorageData::load, StorageData::new, NAME);
    }

    /** その人の倉庫（なければ作る。契約はまだ）。 */
    public Box box(UUID owner) {
        return boxes.computeIfAbsent(owner, id -> new Box());
    }

    /** その人の倉庫（なければ null。作らない）。 */
    @Nullable
    public Box peek(UUID owner) {
        return boxes.get(owner);
    }

    /** 名前で探す（管理者のコマンド。オフラインの人も）。 */
    @Nullable
    public UUID findByName(String name) {
        for (Map.Entry<UUID, Box> entry : boxes.entrySet()) {
            if (entry.getValue().name.equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Map<UUID, Box> all() {
        return Collections.unmodifiableMap(boxes);
    }

    // ---------------------------------------------------------------- 保存

    /** 保存の形から作る（自動テストで、保存して読み直すときに使う）。 */
    public static StorageData fromTag(CompoundTag tag) {
        return load(tag);
    }

    static StorageData load(CompoundTag tag) {
        StorageData data = new StorageData();
        ListTag list = tag.getList("boxes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("owner")) {
                continue;
            }
            Box box = new Box();
            box.name = entry.getString("name");
            box.setPages(entry.getInt("pages"));
            ListTag items = entry.getList("items", Tag.TAG_COMPOUND);
            for (int j = 0; j < items.size(); j++) {
                CompoundTag item = items.getCompound(j);
                box.set(item.getInt("Slot"), ItemStack.of(item));
            }
            ListTag received = entry.getList("received", Tag.TAG_COMPOUND);
            for (int j = 0; j < received.size(); j++) {
                ItemStack stack = ItemStack.of(received.getCompound(j));
                if (!stack.isEmpty()) {
                    box.received.add(stack);
                }
            }
            data.boxes.put(entry.getUUID("owner"), box);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Box> e : boxes.entrySet()) {
            Box box = e.getValue();
            if (!box.contracted() && box.receivedCount() == 0) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putUUID("owner", e.getKey());
            entry.putString("name", box.name);
            entry.putInt("pages", box.pages);
            ListTag items = new ListTag();
            for (int i = 0; i < box.items.size() && i < box.capacity(); i++) {
                ItemStack stack = box.items.get(i);
                if (!stack.isEmpty()) {
                    CompoundTag item = stack.save(new CompoundTag());
                    item.putInt("Slot", i);
                    items.add(item);
                }
            }
            entry.put("items", items);
            ListTag received = new ListTag();
            for (ItemStack stack : box.received) {
                if (!stack.isEmpty()) {
                    received.add(stack.save(new CompoundTag()));
                }
            }
            entry.put("received", received);
            list.add(entry);
        }
        tag.put("boxes", list);
        return tag;
    }
}

package jp.amemaru.lostconnection.economy;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.StorageContentsS2C;
import jp.amemaru.lostconnection.workshop.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 個人倉庫の画面の中身（サーバーとクライアントで同じ形。Phase 6）。27枠（いま見ているページ）と持ち物。
 *
 * <p>ページの切り替え・並べ替え・契約・拡張・受け取り箱の品を全部取る、は画面のボタン（{@link #clickMenuButton}）で
 * サーバーに頼む。品の出し入れは、ふつうの箱と同じく、サーバーが1つずつ確かめて行う（Phase 0-B 9章 {@code StorageMove}）。</p>
 */
public class StorageMenu extends AbstractContainerMenu {
    /** 契約の案内（ページの枠を出さない） */
    public static final int CONTRACT_PAGE = -1;
    /** 受け取り箱の k ページ目は {@code RECEIVE_PAGE_BASE + k} */
    public static final int RECEIVE_PAGE_BASE = 100;
    /** 受け取り箱のページの数の上限（画面のボタン） */
    public static final int RECEIVE_PAGES = 10;

    /** ボタン：ページ（0〜19） */
    public static final int BUTTON_PAGE = 0;
    /** ボタン：受け取り箱の k ページ目（20〜29） */
    public static final int BUTTON_RECEIVE = 20;
    public static final int BUTTON_SORT = 40;
    public static final int BUTTON_CONTRACT = 41;
    public static final int BUTTON_EXPAND = 42;
    public static final int BUTTON_TAKE_ALL = 43;
    public static final int BUTTON_CONTRACT_INFO = 44;

    /** 枠の場所（画面の中の座標。画面の絵と合わせる） */
    public static final int GRID_X = 38;
    public static final int GRID_Y = 42;
    public static final int INVENTORY_X = 38;
    public static final int INVENTORY_Y = 140;

    private static final int DATA_PAGES = 0;
    private static final int DATA_PAGE = 1;
    private static final int DATA_RECEIVED = 2;
    private static final int DATA_COUNT = 3;

    private final BlockPos pos;
    private final Container view;
    private final ContainerData data;
    @Nullable
    private final StorageData storage;
    @Nullable
    private final StorageData.Box box;
    @Nullable
    private final Player owner;
    /** サーバー：いま見ているページ */
    private int page;
    /** サーバー：中身が変わった（探すための一覧を送り直す） */
    private boolean contentsDirty = true;

    /** サーバー側。 */
    public StorageMenu(int id, Inventory inventory, StorageData storage, StorageData.Box box, BlockPos pos, int page) {
        super(ModMenus.STORAGE.get(), id);
        this.pos = pos;
        this.storage = storage;
        this.box = box;
        this.owner = inventory.player;
        this.page = page;
        this.view = new PageView();
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_PAGES -> box.pages();
                    case DATA_PAGE -> StorageMenu.this.page;
                    case DATA_RECEIVED -> box.receivedCount();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        addSlots(inventory);
    }

    /** クライアント側。 */
    private StorageMenu(int id, Inventory inventory, BlockPos pos, int page) {
        super(ModMenus.STORAGE.get(), id);
        this.pos = pos;
        this.storage = null;
        this.box = null;
        this.owner = null;
        this.page = page;
        this.view = new SimpleContainer(StorageData.PAGE_SIZE);
        this.data = new SimpleContainerData(DATA_COUNT);
        this.data.set(DATA_PAGE, page);
        addSlots(inventory);
    }

    public static StorageMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int page = buf.readVarInt();
        return new StorageMenu(id, inventory, pos, page);
    }

    private void addSlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new StorageSlot(view, column + row * 9, GRID_X + column * 18, GRID_Y + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, INVENTORY_Y + 58));
        }
        addDataSlots(data);
    }

    // ---------------------------------------------------------------- 状態（画面が読む）

    public BlockPos pos() {
        return pos;
    }

    public int pages() {
        return data.get(DATA_PAGES);
    }

    public int page() {
        return data.get(DATA_PAGE);
    }

    public int receivedCount() {
        return data.get(DATA_RECEIVED);
    }

    public boolean onStoragePage() {
        int p = page();
        return p >= 0 && p < pages();
    }

    public boolean onReceivePage() {
        return page() >= RECEIVE_PAGE_BASE;
    }

    public boolean onContractPage() {
        return !onStoragePage() && !onReceivePage();
    }

    /** サーバー：中身が変わった（受け取り箱に品が届いたなど）。 */
    void markContentsChanged() {
        contentsDirty = true;
    }

    // ---------------------------------------------------------------- ボタン

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (box == null || storage == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id >= BUTTON_PAGE && id < BUTTON_PAGE + 20) {
            if (id - BUTTON_PAGE < box.pages()) {
                setPage(id - BUTTON_PAGE);
                return true;
            }
            return false;
        }
        if (id >= BUTTON_RECEIVE && id < BUTTON_RECEIVE + RECEIVE_PAGES) {
            setPage(RECEIVE_PAGE_BASE + (id - BUTTON_RECEIVE));
            return true;
        }
        switch (id) {
            case BUTTON_SORT -> {
                if (box.contracted()) {
                    StorageService.sort(box);
                    changed();
                }
                return true;
            }
            case BUTTON_CONTRACT -> {
                StorageService.Result result = StorageService.contract(serverPlayer, box);
                StorageService.tell(serverPlayer, result, box, true);
                if (result == StorageService.Result.OK) {
                    setPage(0);
                }
                return true;
            }
            case BUTTON_EXPAND -> {
                StorageService.Result result = StorageService.expand(serverPlayer, box);
                StorageService.tell(serverPlayer, result, box, false);
                if (result == StorageService.Result.OK) {
                    setPage(box.pages() - 1);
                }
                return true;
            }
            case BUTTON_TAKE_ALL -> {
                StorageService.takeReceived(serverPlayer, box);
                changed();
                return true;
            }
            case BUTTON_CONTRACT_INFO -> {
                setPage(CONTRACT_PAGE);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void setPage(int next) {
        if (box != null && page >= RECEIVE_PAGE_BASE && next < RECEIVE_PAGE_BASE) {
            box.compactReceived();
        }
        page = next;
        changed();
    }

    private void changed() {
        if (storage != null) {
            storage.setDirty();
        }
        contentsDirty = true;
        broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (contentsDirty && box != null && owner instanceof ServerPlayer serverPlayer
                && !(owner instanceof net.minecraftforge.common.util.FakePlayer)) {
            contentsDirty = false;
            List<ItemStack> items = new ArrayList<>(box.items());
            List<ItemStack> received = new ArrayList<>(box.received());
            ModNetwork.sendTo(serverPlayer, new StorageContentsS2C(containerId, items, received,
                    StorageService.nextPrice(box.pages()), StorageService.maxPages()));
        }
    }

    // ---------------------------------------------------------------- 持ち物との出し入れ

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int storageEnd = StorageData.PAGE_SIZE;
        if (index < storageEnd) {
            if (!slot.mayPickup(player) || !moveItemStackTo(stack, storageEnd, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!onStoragePage() || !moveItemStackTo(stack, 0, storageEnd, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        double reach = ServerConfig.STORAGE_REACH.get();
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > reach * reach) {
            return false;
        }
        return box == null || StorageService.isCurrent(player.getUUID(), this);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (box != null) {
            box.compactReceived();
            StorageService.closed(player.getUUID(), this);
            if (storage != null) {
                storage.setDirty();
            }
        }
    }

    // ---------------------------------------------------------------- 枠

    /** 倉庫の枠：契約の案内のときは出さない。受け取り箱には置けない（取り出すだけ）。 */
    private final class StorageSlot extends Slot {
        StorageSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return onStoragePage();
        }

        @Override
        public boolean mayPickup(Player player) {
            return !onContractPage();
        }

        @Override
        public boolean isActive() {
            return !onContractPage();
        }
    }

    /** サーバーの、いま見ているページ（倉庫の27枠か、受け取り箱の27個）。 */
    private final class PageView implements Container {
        private int storageIndex(int slot) {
            return page * StorageData.PAGE_SIZE + slot;
        }

        private int receivedIndex(int slot) {
            return (page - RECEIVE_PAGE_BASE) * StorageData.PAGE_SIZE + slot;
        }

        @Override
        public int getContainerSize() {
            return StorageData.PAGE_SIZE;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < getContainerSize(); i++) {
                if (!getItem(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            if (box == null) {
                return ItemStack.EMPTY;
            }
            if (onStoragePage()) {
                return box.get(storageIndex(slot));
            }
            if (page >= RECEIVE_PAGE_BASE) {
                int index = receivedIndex(slot);
                return index >= 0 && index < box.received().size() ? box.received().get(index) : ItemStack.EMPTY;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = getItem(slot);
            if (stack.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack split = stack.split(amount);
            if (stack.isEmpty()) {
                setItem(slot, ItemStack.EMPTY);
            } else {
                setChanged();
            }
            return split;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = getItem(slot);
            setItem(slot, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (box == null) {
                return;
            }
            if (onStoragePage()) {
                box.set(storageIndex(slot), stack);
            } else if (page >= RECEIVE_PAGE_BASE) {
                int index = receivedIndex(slot);
                if (index >= 0 && index < box.received().size()) {
                    box.received().set(index, stack);
                }
            }
            setChanged();
        }

        @Override
        public void setChanged() {
            if (storage != null) {
                storage.setDirty();
            }
            contentsDirty = true;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
        }
    }
}

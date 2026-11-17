package jp.amemaru.lostconnection.accessory;

import jp.amemaru.lostconnection.workshop.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * アクセサリーの枠の画面の中身（Phase 0-A 21章。枠の入れ物は Curios、画面はこのModで作る）。
 *
 * <p>枠は いつも6つ並べる（指輪2・首飾り・腕輪・お守り・留め具）。留め具は11日目まで閉じていて、置けない。
 * 枠の中身は Curios の入れ物そのものなので、付けかえは Curios が見つけて、ステータスと見た目に反映される。</p>
 */
public class AccessoryMenu extends AbstractContainerMenu {
    /** 枠の並び（指輪・指輪・首飾り・腕輪・お守り・留め具）。 */
    public static final AccessorySlot[] LAYOUT = {AccessorySlot.RING, AccessorySlot.RING, AccessorySlot.NECKLACE,
            AccessorySlot.BRACELET, AccessorySlot.CHARM, AccessorySlot.CLASP};
    /** 枠の場所（画面の座標）。 */
    public static final int[][] POSITIONS = {{18, 30}, {18, 52}, {150, 30}, {150, 52}, {150, 74}, {18, 74}};
    public static final int INVENTORY_X = 12;
    public static final int INVENTORY_Y = 140;

    private final Player player;
    private final boolean claspOpen;
    private final int claspUnlockDay;

    /**
     * @param claspOpen      留め具の枠が開いているか
     * @param claspUnlockDay 留め具の枠が開く日（画面の「〇日目〜」に出す。config の {@code claspUnlockDay}）
     */
    public AccessoryMenu(int id, Inventory inventory, boolean claspOpen, int claspUnlockDay) {
        super(ModMenus.ACCESSORY.get(), id);
        this.player = inventory.player;
        this.claspOpen = claspOpen;
        this.claspUnlockDay = claspUnlockDay;
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        int[] used = new int[AccessorySlot.all().length];
        for (int i = 0; i < LAYOUT.length; i++) {
            AccessorySlot slot = LAYOUT[i];
            int index = used[slot.ordinal()]++;
            IItemHandlerModifiable items = null;
            if (handler != null) {
                ICurioStacksHandler stacks = handler.getCurios().get(slot.curiosId());
                if (stacks != null && index < stacks.getSlots()) {
                    items = stacks.getStacks();
                }
            }
            boolean locked = items == null || (slot == AccessorySlot.CLASP && !claspOpen);
            addSlot(new AccessorySlotView(locked ? new ItemStackHandler(1) : items, locked ? 0 : index, POSITIONS[i][0],
                    POSITIONS[i][1], slot, locked));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, INVENTORY_Y + 58));
        }
    }

    /** クライアント側（サーバーから開く知らせを受けたとき）。 */
    public static AccessoryMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buf) {
        boolean clasp = buf.readBoolean();
        return new AccessoryMenu(id, inventory, clasp, buf.readVarInt());
    }

    /** 留め具の枠が開いているか。 */
    public boolean claspOpen() {
        return claspOpen;
    }

    /** 留め具の枠が開く日。 */
    public int claspUnlockDay() {
        return claspUnlockDay;
    }

    /** 枠（画面の番号）の種類。 */
    public AccessorySlot slotType(int index) {
        return LAYOUT[index];
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < LAYOUT.length) {
            if (!moveItemStackTo(stack, LAYOUT.length, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof AccessoryItem) {
            boolean moved = false;
            for (int i = 0; i < LAYOUT.length && !moved; i++) {
                Slot target = slots.get(i);
                if (!target.hasItem() && target.mayPlace(stack)) {
                    target.set(stack.split(1));
                    moved = true;
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    /** アクセサリーの枠（その枠の種類のアクセサリーだけ置ける。閉じた枠には置けない）。 */
    public static class AccessorySlotView extends SlotItemHandler {
        private final AccessorySlot type;
        private final boolean locked;

        AccessorySlotView(IItemHandlerModifiable handler, int index, int x, int y, AccessorySlot type, boolean locked) {
            super(handler, index, x, y);
            this.type = type;
            this.locked = locked;
        }

        public AccessorySlot type() {
            return type;
        }

        public boolean locked() {
            return locked;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !locked && stack.getItem() instanceof AccessoryItem item && item.slot() == type;
        }

        @Override
        public boolean mayPickup(Player player) {
            return !locked && super.mayPickup(player);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }
}

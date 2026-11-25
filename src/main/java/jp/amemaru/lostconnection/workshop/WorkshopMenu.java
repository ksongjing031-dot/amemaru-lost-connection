package jp.amemaru.lostconnection.workshop;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 工房の画面の中身（サーバーとクライアントで同じ形）。強化・修理する品を置く台が1つと、持ち物。
 * 画面を閉じると、台の品は持ち物に戻る。作る・強化・修理の操作は {@code WorkshopActionC2S} で頼む。
 */
public class WorkshopMenu extends AbstractContainerMenu {
    /** 台の場所（画面の中の座標。画面の絵と合わせる）。 */
    public static final int WORK_X = 26;
    public static final int WORK_Y = 108;
    public static final int INVENTORY_X = 92;
    public static final int INVENTORY_Y = 140;

    private final WorkshopType type;
    private final BlockPos pos;
    private final Player owner;
    private final Container work = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            WorkshopMenu.this.slotsChanged(this);
        }
    };

    /** サーバー側（作業台を右クリックしたとき）。 */
    public WorkshopMenu(int id, Inventory inventory, WorkshopType type, BlockPos pos) {
        super(ModMenus.WORKSHOP.get(), id);
        this.type = type;
        this.pos = pos;
        this.owner = inventory.player;
        addSlot(new Slot(work, 0, WORK_X, WORK_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return WorkshopMenu.this.type.accepts(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
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
    public static WorkshopMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buf) {
        WorkshopType type = WorkshopType.byIndex(buf.readByte());
        BlockPos pos = buf.readBlockPos();
        return new WorkshopMenu(id, inventory, type, pos);
    }

    public WorkshopType type() {
        return type;
    }

    public BlockPos pos() {
        return pos;
    }

    /** 台に置いた品。 */
    public ItemStack workStack() {
        return work.getItem(0);
    }

    /** 台の品を消す（分解。Phase 6 その4）。 */
    public void clearWork() {
        work.setItem(0, ItemStack.EMPTY);
    }

    /** 台の品が変わった：装飾工房なら、サーバーから厳選の見積もりを送る（Phase 6 その4）。 */
    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == work && type == WorkshopType.ACCESSORY && owner instanceof net.minecraft.server.level.ServerPlayer player) {
            jp.amemaru.lostconnection.accessory.RefineService.sendQuote(player, this);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        double reach = ServerConfig.WORKSHOP_REACH.get();
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= reach * reach
                && player.level().getBlockState(pos).getBlock() instanceof WorkshopBlock block && block.type() == type;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (type.accepts(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
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

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, work);
    }
}

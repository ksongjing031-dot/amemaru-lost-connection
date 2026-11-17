package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.accessory.AccessoryData;
import jp.amemaru.lostconnection.accessory.AccessoryMenu;
import jp.amemaru.lostconnection.accessory.AccessorySlot;
import jp.amemaru.lostconnection.client.ClientAdventure;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.AccessoryActionC2S;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * アクセサリーの枠の画面（Phase 0-A 21章。枠の入れ物は Curios、画面はこのMod）。
 *
 * <p>左に指輪2つと留め具（11日目に解放）、右に首飾り・腕輪・お守り。真ん中に自分の姿（体に表示するアクセサリーが見える）。
 * 姿の下の切りかえ（2つずつ2段）で、首飾り・腕輪・お守り・留め具を体に表示するかを選ぶ（まわりの人の画面にも効く）。
 * 付けているアクセサリーのステータスの合計も出す。</p>
 */
public class AccessoryScreen extends AbstractContainerScreen<AccessoryMenu> {
    private static final AccessorySlot[] SHOWN = {AccessorySlot.NECKLACE, AccessorySlot.BRACELET, AccessorySlot.CHARM, AccessorySlot.CLASP};
    private final List<Button> toggles = new ArrayList<>();

    public AccessoryScreen(AccessoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 196;
        imageHeight = 222;
        inventoryLabelX = AccessoryMenu.INVENTORY_X;
        inventoryLabelY = AccessoryMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        toggles.clear();
        for (int i = 0; i < SHOWN.length; i++) {
            AccessorySlot slot = SHOWN[i];
            // 2つずつ2段に並べる（自分の姿の下）
            CrystalButton button = new CrystalButton(leftPos + 8 + (i % 2) * 92, topPos + 98 + (i / 2) * 15, 88, 13, toggleLabel(slot),
                    pressed -> {
                boolean visible = !ClientAdventure.ownVisible(slot.ordinal());
                ModNetwork.sendToServer(new AccessoryActionC2S(AccessoryActionC2S.Action.SET_VISIBLE, slot.ordinal(), visible));
            });
            toggles.add(addRenderableWidget(button));
        }
    }

    private static Component toggleLabel(AccessorySlot slot) {
        boolean visible = ClientAdventure.ownVisible(slot.ordinal());
        return Component.translatable(visible ? "screen.amemaru.accessory.shown" : "screen.amemaru.accessory.hidden",
                Component.translatable(slot.translationKey()));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        for (int i = 0; i < toggles.size(); i++) {
            toggles.get(i).setMessage(toggleLabel(SHOWN[i]));
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        UiStyle.panel(graphics, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        for (int i = 0; i < AccessoryMenu.LAYOUT.length; i++) {
            int x = leftPos + AccessoryMenu.POSITIONS[i][0] - 1;
            int y = topPos + AccessoryMenu.POSITIONS[i][1] - 1;
            Slot slot = menu.slots.get(i);
            boolean locked = slot instanceof AccessoryMenu.AccessorySlotView view && view.locked();
            graphics.fill(x - 1, y - 1, x + 19, y + 19, locked ? 0xFF3A2A2A : 0xFF4F6B99);
            graphics.fill(x, y, x + 18, y + 18, 0xFF0B1022);
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                frame(graphics, leftPos + AccessoryMenu.INVENTORY_X - 1 + column * 18, topPos + AccessoryMenu.INVENTORY_Y - 1 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            frame(graphics, leftPos + AccessoryMenu.INVENTORY_X - 1 + column * 18, topPos + AccessoryMenu.INVENTORY_Y + 57);
        }
        // 真ん中に自分の姿
        if (Minecraft.getInstance().player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, leftPos + imageWidth / 2, topPos + 90, 32,
                    (float) (leftPos + imageWidth / 2 - mouseX), (float) (topPos + 40 - mouseY), Minecraft.getInstance().player);
        }
    }

    private static void frame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF26324D);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF0B1022);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 7, UiStyle.TEXT_HEADER, true);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiStyle.TEXT_DIM, true);
        for (int i = 0; i < AccessoryMenu.LAYOUT.length; i++) {
            AccessorySlot type = AccessoryMenu.LAYOUT[i];
            int x = AccessoryMenu.POSITIONS[i][0];
            int y = AccessoryMenu.POSITIONS[i][1];
            Slot slot = menu.slots.get(i);
            boolean left = x < imageWidth / 2;
            boolean locked = slot instanceof AccessoryMenu.AccessorySlotView view && view.locked();
            Component name = Component.translatable(type.translationKey());
            int labelX = left ? x + 20 : x - 3 - font.width(name);
            if (locked && type == AccessorySlot.CLASP) {
                // 閉じている留め具：名前の下に、開く日（自分の姿に重ならないよう2行にする）
                Component day = Component.translatable("screen.amemaru.accessory.locked_day", menu.claspUnlockDay());
                graphics.drawString(font, name, labelX, y, 0x806E7891, false);
                graphics.drawString(font, day, left ? x + 20 : x - 3 - font.width(day), y + 10, 0x80B08A6A, false);
            } else {
                graphics.drawString(font, name, labelX, y + 5, locked ? 0x806E7891 : UiStyle.TEXT_DIM, false);
            }
            if (!slot.hasItem()) {
                String full = Component.translatable(type.translationKey()).getString();
                String mark = locked ? "×" : full.isEmpty() ? "" : full.substring(0, 1);
                graphics.drawString(font, mark, x + 8 - font.width(mark) / 2, y + 4, 0x40FFFFFF, false);
            }
        }
        renderTotals(graphics);
    }

    /** 付けているアクセサリーのステータスの合計（右上）。 */
    private void renderTotals(GuiGraphics graphics) {
        Map<StatType, Double> totals = new EnumMap<>(StatType.class);
        for (int i = 0; i < AccessoryMenu.LAYOUT.length; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            for (AccessoryData.StatLine line : AccessoryData.allLines(stack)) {
                totals.merge(line.type(), line.value(), Double::sum);
            }
        }
        if (totals.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.accessory.none"), 8, 18, UiStyle.TEXT_DIM, false);
            return;
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<StatType, Double> entry : totals.entrySet()) {
            if (!text.isEmpty()) {
                text.append("  ");
            }
            text.append(Component.translatable(entry.getKey().translationKey()).getString()).append(" +")
                    .append(entry.getKey().format(entry.getValue()));
        }
        UiStyle.wrapped(graphics, font, text.toString(), 8, 18, imageWidth - 16, UiStyle.TEXT_GOOD, 1);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}

package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.client.ClientPlayerState;
import jp.amemaru.lostconnection.client.ClientStorage;
import jp.amemaru.lostconnection.economy.StorageData;
import jp.amemaru.lostconnection.economy.StorageMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * 個人倉庫の画面（Phase 6）。上にページの札（1〜5）と受け取り箱、右上に「さがす」、枠の下に並べ替え・ページを増やす。
 * 契約していない人には、契約の案内を出す。
 */
public class StorageScreen extends AbstractContainerScreen<StorageMenu> {
    private static final int TAB_Y = 22;
    private static final int SLOT_BG = 0xFF1B2238;
    private static final int SLOT_EDGE = 0xFF0A0E18;
    private static final int MATCH = 0xFFFFD36A;

    private EditBox search;
    private String query = "";
    private int builtPage = Integer.MIN_VALUE;
    private int builtPages = -1;
    private int builtVersion = -1;
    private int builtReceived = -1;
    private final List<Button> dynamic = new ArrayList<>();

    public StorageScreen(StorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 236;
        imageHeight = 224;
        titleLabelX = 8;
        titleLabelY = 7;
        inventoryLabelX = StorageMenu.INVENTORY_X;
        inventoryLabelY = StorageMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, leftPos + imageWidth - 112, topPos + 4, 104, 12, Component.translatable("screen.amemaru.storage.search"));
        search.setMaxLength(32);
        search.setValue(query);
        search.setResponder(text -> query = text);
        addRenderableWidget(search);
        builtPage = Integer.MIN_VALUE;
        rebuildButtons();
    }

    /** ページ・ページ数・中身が変わったら、札とボタンを作り直す。 */
    private void rebuildButtons() {
        for (Button button : dynamic) {
            removeWidget(button);
        }
        dynamic.clear();
        builtPage = menu.page();
        builtPages = menu.pages();
        builtVersion = ClientStorage.version();
        builtReceived = menu.receivedCount();

        // ページの札
        int x = leftPos + 8;
        for (int i = 0; i < menu.pages(); i++) {
            int page = i;
            Button tab = add(new CrystalButton(x, topPos + TAB_Y, 18, 14, Component.literal(Integer.toString(i + 1)),
                    b -> press(StorageMenu.BUTTON_PAGE + page)));
            tab.active = menu.page() != page;
            x += 20;
        }
        // 受け取り箱（ページがあれば < > で送る）
        int receivePages = receivePages();
        Button receive = add(new CrystalButton(x + 4, topPos + TAB_Y, 84, 14,
                Component.translatable("screen.amemaru.storage.receive_tab", menu.receivedCount()),
                b -> press(StorageMenu.BUTTON_RECEIVE)));
        receive.active = !menu.onReceivePage() || menu.page() != StorageMenu.RECEIVE_PAGE_BASE;
        x += 92;
        Button info = add(new CrystalButton(x, topPos + TAB_Y, 52, 14, Component.translatable("screen.amemaru.storage.contract_tab"),
                b -> press(StorageMenu.BUTTON_CONTRACT_INFO)));
        info.active = !menu.onContractPage();

        int rowY = StorageMenu.GRID_Y + 3 * 18 + 4 + topPos;
        if (menu.onStoragePage()) {
            add(new CrystalButton(leftPos + StorageMenu.GRID_X, rowY, 54, 14, Component.translatable("screen.amemaru.storage.sort"),
                    b -> press(StorageMenu.BUTTON_SORT)));
            long price = ClientStorage.nextPrice();
            if (price >= 0) {
                Button expand = add(new CrystalButton(leftPos + StorageMenu.GRID_X + 58, rowY, 104, 14,
                        Component.translatable("screen.amemaru.storage.expand", String.format(Locale.ROOT, "%,d", price)),
                        b -> press(StorageMenu.BUTTON_EXPAND)));
                expand.active = ClientPlayerState.get().knots() >= price;
            }
        } else if (menu.onReceivePage()) {
            Button takeAll = add(new CrystalButton(leftPos + StorageMenu.GRID_X, rowY, 72, 14,
                    Component.translatable("screen.amemaru.storage.take_all"), b -> press(StorageMenu.BUTTON_TAKE_ALL)));
            takeAll.active = menu.receivedCount() > 0;
            if (receivePages > 1) {
                int current = menu.page() - StorageMenu.RECEIVE_PAGE_BASE;
                Button prev = add(new CrystalButton(leftPos + StorageMenu.GRID_X + 120, rowY, 20, 14, Component.literal("<"),
                        b -> press(StorageMenu.BUTTON_RECEIVE + current - 1)));
                Button next = add(new CrystalButton(leftPos + StorageMenu.GRID_X + 142, rowY, 20, 14, Component.literal(">"),
                        b -> press(StorageMenu.BUTTON_RECEIVE + current + 1)));
                prev.active = current > 0;
                next.active = current < receivePages - 1;
            }
        } else {
            // 契約の案内：契約する・ページを増やす
            long price = ClientStorage.nextPrice();
            int y = topPos + 104;
            if (menu.pages() == 0) {
                Button contract = add(new CrystalButton(leftPos + imageWidth / 2 - 70, y, 140, 16,
                        Component.translatable("screen.amemaru.storage.contract", String.format(Locale.ROOT, "%,d", Math.max(0L, price))),
                        b -> press(StorageMenu.BUTTON_CONTRACT)));
                contract.active = price >= 0 && ClientPlayerState.get().knots() >= price;
            } else if (price >= 0) {
                Button expand = add(new CrystalButton(leftPos + imageWidth / 2 - 70, y, 140, 16,
                        Component.translatable("screen.amemaru.storage.expand", String.format(Locale.ROOT, "%,d", price)),
                        b -> press(StorageMenu.BUTTON_EXPAND)));
                expand.active = ClientPlayerState.get().knots() >= price;
            }
        }
    }

    private Button add(Button button) {
        dynamic.add(button);
        return addRenderableWidget(button);
    }

    private int receivePages() {
        int size = ClientStorage.received(menu.containerId).size();
        return Math.max(1, Math.min(StorageMenu.RECEIVE_PAGES, (size + StorageData.PAGE_SIZE - 1) / StorageData.PAGE_SIZE));
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null && minecraft.player != null) {
            menu.clickMenuButton(minecraft.player, id);
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (search != null) {
            search.tick();
        }
        if (builtPage != menu.page() || builtPages != menu.pages() || builtVersion != ClientStorage.version()
                || builtReceived != menu.receivedCount()) {
            rebuildButtons();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
            return true;
        }
        if (search != null && search.isFocused()) {
            return search.keyPressed(keyCode, scanCode, modifiers) || search.canConsumeInput() || true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---------------------------------------------------------------- さがす

    private boolean matches(ItemStack stack) {
        if (query.isBlank() || stack.isEmpty()) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q);
    }

    /** 見つかったページ（1から。受け取り箱は0）。 */
    private Set<Integer> matchedPages() {
        Set<Integer> pages = new TreeSet<>();
        if (query.isBlank()) {
            return pages;
        }
        List<ItemStack> items = ClientStorage.items(menu.containerId);
        for (int i = 0; i < items.size(); i++) {
            if (matches(items.get(i))) {
                pages.add(i / StorageData.PAGE_SIZE + 1);
            }
        }
        for (ItemStack stack : ClientStorage.received(menu.containerId)) {
            if (matches(stack)) {
                pages.add(0);
                break;
            }
        }
        return pages;
    }

    // ---------------------------------------------------------------- 描画

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        UiStyle.panel(graphics, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        UiStyle.divider(graphics, leftPos + 8, leftPos + imageWidth - 8, topPos + 18);
        // 選んでいる札の下に金の線
        int tabX = -1;
        if (menu.onStoragePage()) {
            tabX = leftPos + 8 + menu.page() * 20;
            graphics.fill(tabX, topPos + TAB_Y + 15, tabX + 18, topPos + TAB_Y + 16, MATCH);
        }
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, SLOT_EDGE);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_BG);
            if (slot.index < StorageData.PAGE_SIZE && matches(slot.getItem())) {
                graphics.renderOutline(x, y, 18, 18, MATCH);
            }
        }
        if (menu.onContractPage()) {
            renderContract(graphics);
        }
    }

    private void renderContract(GuiGraphics graphics) {
        int x = leftPos + 14;
        int y = topPos + 44;
        int width = imageWidth - 28;
        String head;
        if (menu.pages() == 0) {
            head = Component.translatable("screen.amemaru.storage.contract_head").getString();
        } else {
            head = Component.translatable("screen.amemaru.storage.contract_now", menu.pages(), menu.pages() * StorageData.PAGE_SIZE,
                    ClientStorage.maxPages(), ClientStorage.maxPages() * StorageData.PAGE_SIZE).getString();
        }
        y += UiStyle.wrapped(graphics, font, head, x, y, width, UiStyle.TEXT, 3) * 10 + 4;
        UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.storage.contract_body").getString(), x, y, width,
                UiStyle.TEXT_DIM, 4);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, UiStyle.TEXT_HEADER, true);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiStyle.TEXT_DIM, true);
        int rowY = StorageMenu.GRID_Y + 3 * 18 + 8;
        if (menu.onStoragePage()) {
            int used = 0;
            List<ItemStack> items = ClientStorage.items(menu.containerId);
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    used++;
                }
            }
            String count = Component.translatable("screen.amemaru.storage.used", used, menu.pages() * StorageData.PAGE_SIZE).getString();
            graphics.drawString(font, count, imageWidth - 10 - font.width(count), rowY + 14, UiStyle.TEXT_DIM, true);
        } else if (menu.onReceivePage()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.storage.receive_hint"), StorageMenu.GRID_X - 30,
                    StorageMenu.GRID_Y - 10, UiStyle.TEXT_DIM, true);
        }
        Set<Integer> pages = matchedPages();
        if (!query.isBlank()) {
            String found;
            if (pages.isEmpty()) {
                found = Component.translatable("screen.amemaru.storage.search_none").getString();
            } else {
                List<String> names = new ArrayList<>();
                for (int page : pages) {
                    names.add(page == 0 ? Component.translatable("screen.amemaru.storage.search_receive").getString() : Integer.toString(page));
                }
                found = Component.translatable("screen.amemaru.storage.search_found", String.join("・", names)).getString();
            }
            graphics.drawString(font, UiStyle.clip(font, found, 150), imageWidth - 10 - Math.min(150, font.width(found)),
                    StorageMenu.INVENTORY_Y - 11, MATCH, true);
        }
    }
}

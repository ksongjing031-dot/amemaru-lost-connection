package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.client.ClientPlayerState;
import jp.amemaru.lostconnection.economy.ShopDef;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.ShopActionC2S;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * 店の画面（Phase 6。11月22日に決定：施設は右クリックでGUIを開いて使う）。カウンターの呼び鈴を右クリックで開く。
 *
 * <ul>
 *   <li>「買う」「売る」のタブ。品の行をクリックして選び、数（−10・−1・+1・+10・最大）を決めて「買う」「売る」</li>
 *   <li>「売る」は、持っている数を行に出す（名前を付けた品は売らない）</li>
 *   <li>値段・所持金・持ち物の空きは、サーバーで確かめる。結果は画面の下の行（とチャット）に出る</li>
 * </ul>
 */
public class ShopScreen extends Screen {
    private static final int PANEL_WIDTH = 288;
    private static final int PANEL_HEIGHT = 250;
    private static final int ROW = 20;
    private static final int PER_PAGE = 6;
    private static final int LIST_TOP = 50;
    /** 数の行（−10・−1・数・+1・+10・最大。右に合計） */
    private static final int COUNT_Y = PANEL_HEIGHT - 58;
    /** 「買う」「売る」と「閉じる」の行 */
    private static final int BUTTON_Y = PANEL_HEIGHT - 26;

    private final BlockPos pos;
    private final ShopDef shop;
    private boolean selling;
    private int selected = -1;
    private int page;
    private int count = 1;
    @Nullable
    private Component status;
    private boolean statusOk;

    private int left;
    private int top;
    @Nullable
    private Button buyTab;
    @Nullable
    private Button sellTab;
    @Nullable
    private Button actionButton;
    @Nullable
    private Button prevButton;
    @Nullable
    private Button nextButton;

    public ShopScreen(BlockPos pos, ShopDef shop) {
        super(Component.literal(shop.name()));
        this.pos = pos;
        this.shop = shop;
        this.selling = shop.sells().isEmpty() && !shop.buys().isEmpty();
    }

    public BlockPos pos() {
        return pos;
    }

    /** サーバーからの結果（買えた・足りないなど）を、画面の下の行に出す。 */
    public void showResult(Component message, boolean ok) {
        status = message;
        statusOk = ok;
        clampCount();
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        buyTab = addRenderableWidget(new CrystalButton(left + 14, top + 32, 56, 14, Component.translatable("screen.amemaru.shop.buy_tab"),
                b -> switchTab(false)));
        sellTab = addRenderableWidget(new CrystalButton(left + 74, top + 32, 56, 14, Component.translatable("screen.amemaru.shop.sell_tab"),
                b -> switchTab(true)));

        int arrowsY = top + LIST_TOP + PER_PAGE * ROW + 1;
        prevButton = addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 62, arrowsY, 20, 12, Component.literal("<"), b -> turn(-1)));
        nextButton = addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 36, arrowsY, 20, 12, Component.literal(">"), b -> turn(1)));

        int countY = top + COUNT_Y;
        addRenderableWidget(new CrystalButton(left + 14, countY, 20, 14, Component.literal("-10"), b -> addCount(-10)));
        addRenderableWidget(new CrystalButton(left + 36, countY, 20, 14, Component.literal("-1"), b -> addCount(-1)));
        addRenderableWidget(new CrystalButton(left + 90, countY, 20, 14, Component.literal("+1"), b -> addCount(1)));
        addRenderableWidget(new CrystalButton(left + 112, countY, 20, 14, Component.literal("+10"), b -> addCount(10)));
        addRenderableWidget(new CrystalButton(left + 134, countY, 32, 14, Component.translatable("screen.amemaru.shop.max"), b -> {
            count = maxCount();
            clampCount();
        }));

        int buttonY = top + BUTTON_Y;
        actionButton = addRenderableWidget(new CrystalButton(left + 14, buttonY, 72, 16, Component.translatable("screen.amemaru.shop.buy"),
                b -> act()));
        addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 54, buttonY, 42, 16, Component.translatable("screen.amemaru.status.close"),
                b -> onClose()));
        updateButtons();
    }

    private List<ShopDef.Entry> entries() {
        return selling ? shop.buys() : shop.sells();
    }

    private void switchTab(boolean sell) {
        if (selling != sell) {
            selling = sell;
            selected = -1;
            page = 0;
            count = 1;
            status = null;
        }
        updateButtons();
    }

    private int pages() {
        return Math.max(1, (entries().size() + PER_PAGE - 1) / PER_PAGE);
    }

    private void turn(int delta) {
        page = Mth.clamp(page + delta, 0, pages() - 1);
        updateButtons();
    }

    private void addCount(int delta) {
        count += delta;
        clampCount();
    }

    private void clampCount() {
        count = Mth.clamp(count, 1, Math.max(1, maxCount()));
        updateButtons();
    }

    @Nullable
    private ShopDef.Entry selectedEntry() {
        List<ShopDef.Entry> list = entries();
        return selected >= 0 && selected < list.size() ? list.get(selected) : null;
    }

    private static Item item(ShopDef.Entry entry) {
        Item item = ForgeRegistries.ITEMS.getValue(entry.item());
        return item == null ? Items.AIR : item;
    }

    /** 選んだ品で、いま買える・売れる数の上限。 */
    private int maxCount() {
        ShopDef.Entry entry = selectedEntry();
        if (entry == null || minecraft == null || minecraft.player == null) {
            return 1;
        }
        Item item = item(entry);
        Inventory inventory = minecraft.player.getInventory();
        if (selling) {
            return Math.min(owned(inventory, item), 64 * 36);
        }
        long price = Math.max(1L, entry.price());
        long affordable = ClientPlayerState.get().knots() / price;
        int room = room(inventory, new ItemStack(item));
        return (int) Math.max(0L, Math.min(Math.min(64L, affordable), room));
    }

    private static int owned(Inventory inventory, Item item) {
        int n = 0;
        for (ItemStack stack : inventory.items) {
            if (!stack.isEmpty() && stack.is(item) && !stack.hasCustomHoverName()) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static int room(Inventory inventory, ItemStack sample) {
        if (sample.isEmpty()) {
            return 0;
        }
        int room = 0;
        for (ItemStack stack : inventory.items) {
            if (stack.isEmpty()) {
                room += sample.getMaxStackSize();
            } else if (ItemStack.isSameItemSameTags(stack, sample)) {
                room += Math.max(0, stack.getMaxStackSize() - stack.getCount());
            }
        }
        return room;
    }

    private void act() {
        ShopDef.Entry entry = selectedEntry();
        if (entry == null || count < 1 || count > maxCount()) {
            return;
        }
        ModNetwork.sendToServer(new ShopActionC2S(pos, shop.id(), selling, selected, count));
    }

    private void updateButtons() {
        page = Mth.clamp(page, 0, pages() - 1);
        if (buyTab != null && sellTab != null) {
            buyTab.active = selling && !shop.sells().isEmpty();
            sellTab.active = !selling && !shop.buys().isEmpty();
        }
        if (actionButton != null) {
            actionButton.setMessage(Component.translatable(selling ? "screen.amemaru.shop.sell" : "screen.amemaru.shop.buy"));
            actionButton.active = selectedEntry() != null && maxCount() >= 1 && count <= maxCount();
        }
        if (prevButton != null && nextButton != null) {
            prevButton.visible = nextButton.visible = pages() > 1;
            prevButton.active = page > 0;
            nextButton.active = page < pages() - 1;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<ShopDef.Entry> list = entries();
            int y = top + LIST_TOP;
            for (int i = page * PER_PAGE; i < Math.min(list.size(), (page + 1) * PER_PAGE); i++) {
                if (mouseX >= left + 14 && mouseX < left + PANEL_WIDTH - 14 && mouseY >= y && mouseY < y + ROW - 2) {
                    if (selected != i) {
                        selected = i;
                        count = 1;
                    }
                    clampCount();
                    return true;
                }
                y += ROW;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft != null && minecraft.player != null
                && minecraft.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 12.0 * 12.0) {
            onClose();
            return;
        }
        updateButtons();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String knots(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT);
        Component heading = shop.keeper().isBlank() ? title
                : Component.translatable("screen.amemaru.shop.title", shop.name(), shop.keeper());
        graphics.drawCenteredString(font, heading, width / 2, top + 7, UiStyle.TEXT_HEADER);
        UiStyle.divider(graphics, left + 12, left + PANEL_WIDTH - 12, top + 19);
        long balance = ClientPlayerState.get().knots();
        Component wallet = Component.translatable("screen.amemaru.transfer.balance", knots(balance));
        graphics.drawString(font, wallet, left + PANEL_WIDTH - 14 - font.width(wallet), top + 35, UiStyle.TEXT_GOLD, true);

        List<ShopDef.Entry> list = entries();
        Inventory inventory = minecraft == null || minecraft.player == null ? null : minecraft.player.getInventory();
        int y = top + LIST_TOP;
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = page * PER_PAGE; i < Math.min(list.size(), (page + 1) * PER_PAGE); i++) {
            ShopDef.Entry entry = list.get(i);
            ItemStack stack = new ItemStack(item(entry));
            boolean chosen = i == selected;
            boolean hover = mouseX >= left + 14 && mouseX < left + PANEL_WIDTH - 14 && mouseY >= y && mouseY < y + ROW - 2;
            UiStyle.card(graphics, left + 14, y, left + PANEL_WIDTH - 14, y + ROW - 2, chosen ? 0xFFFFD36A : 0xFF8FD3FF, chosen || hover);
            graphics.renderItem(stack, left + 17, y + 1);
            graphics.drawString(font, UiStyle.clip(font, stack.getHoverName().getString(), 132), left + 38, y + 5,
                    chosen ? UiStyle.TEXT_GOLD : UiStyle.TEXT, true);
            if (selling && inventory != null) {
                String have = Component.translatable("screen.amemaru.shop.have", owned(inventory, stack.getItem())).getString();
                graphics.drawString(font, have, left + 176, y + 5, UiStyle.TEXT_DIM, true);
            }
            String price = Component.translatable("screen.amemaru.shop.price", knots(entry.price())).getString();
            graphics.drawString(font, price, left + PANEL_WIDTH - 20 - font.width(price), y + 5,
                    !selling && entry.price() > balance ? UiStyle.TEXT_BAD : UiStyle.TEXT_GOLD, true);
            if (mouseX >= left + 16 && mouseX < left + 34 && mouseY >= y && mouseY < y + 18) {
                hovered = stack;
            }
            y += ROW;
        }
        if (list.isEmpty()) {
            graphics.drawString(font, Component.translatable(selling ? "screen.amemaru.shop.no_buys" : "screen.amemaru.shop.no_sells"),
                    left + 16, top + LIST_TOP + 4, UiStyle.TEXT_DIM, true);
        }

        int countY = top + COUNT_Y;
        graphics.drawCenteredString(font, "×" + count, left + 73, countY + 3, UiStyle.TEXT);
        ShopDef.Entry entry = selectedEntry();
        if (entry != null) {
            long total = entry.price() * count;
            String totalText = Component.translatable(selling ? "screen.amemaru.shop.total_sell" : "screen.amemaru.shop.total_buy",
                    knots(total)).getString();
            graphics.drawString(font, totalText, left + PANEL_WIDTH - 14 - font.width(totalText), countY + 3,
                    !selling && total > balance ? UiStyle.TEXT_BAD : UiStyle.TEXT_GOLD, true);
            if (maxCount() < 1) {
                String why = Component.translatable(selling ? "screen.amemaru.shop.none_to_sell" : "screen.amemaru.shop.cannot_buy").getString();
                graphics.drawString(font, UiStyle.clip(font, why, PANEL_WIDTH - 152), left + 92, top + BUTTON_Y + 4, UiStyle.TEXT_BAD, true);
            }
        } else {
            graphics.drawString(font, UiStyle.clip(font, Component.translatable("screen.amemaru.shop.pick").getString(), PANEL_WIDTH - 152),
                    left + 92, top + BUTTON_Y + 4, UiStyle.TEXT_DIM, true);
        }
        if (status != null) {
            // 結果の行（ページの矢印の左）
            graphics.drawString(font, UiStyle.clip(font, status.getString(), PANEL_WIDTH - 92), left + 14, top + LIST_TOP + PER_PAGE * ROW + 4,
                    statusOk ? UiStyle.TEXT_GOOD : UiStyle.TEXT_BAD, true);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }
}

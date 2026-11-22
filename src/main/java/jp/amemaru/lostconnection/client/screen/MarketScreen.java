package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.client.ClientMarket;
import jp.amemaru.lostconnection.client.ClientPlayerState;
import jp.amemaru.lostconnection.economy.MarketService;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.MarketActionC2S;
import jp.amemaru.lostconnection.network.s2c.MarketMineS2C;
import jp.amemaru.lostconnection.network.s2c.MarketPageS2C;
import jp.amemaru.lostconnection.network.s2c.MarketPriceS2C;
import jp.amemaru.lostconnection.network.s2c.MarketStallsS2C;
import jp.amemaru.lostconnection.network.s2c.OpenMarketS2C;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 取引所の画面（Phase 6。Phase 0-A 20章【確定】「名前での検索、カテゴリ別の一覧、価格順、相場と最近の取引価格、自分の出品と売上の履歴」）。
 * 市場の手帳・市場監督の小屋・露店（区画）から開く。
 *
 * <ul>
 *   <li>さがす：名前（自分の言葉の名前で探し、合う品のIDをサーバーに送る）・分類・並び（安い順・高い順・新しい順）。選んで「買う」→ 確かめ</li>
 *   <li>出品する：持ち物から品を選び、数と1個の値段（相場の50〜200%）を決める。手数料と、売れたら受け取る額を出す</li>
 *   <li>出品中：自分の出品と期限。取り消す（品は持ち物へ）</li>
 *   <li>売上と履歴：まだ受け取っていない売上を受け取る。最近の出品・売れた・買ったの記録</li>
 *   <li>露店：区画の一覧。市場監督の小屋では、借りる・延ばす・名前を変える・返す</li>
 *   <li>露店から開いたときは、その露店の品だけ（直接買うと、売り手の手数料が半分）</li>
 * </ul>
 */
public class MarketScreen extends Screen {
    private static final int PANEL_WIDTH = 324;
    /** 画面の高さ（GUIの倍率2で、高さ480の窓（240）に収める） */
    private static final int PANEL_HEIGHT = 238;
    private static final int ROW = 18;
    /** 1ページの行の数（サーバーの {@link MarketService#PAGE_SIZE} と同じ） */
    private static final int ROWS = MarketService.PAGE_SIZE;
    private static final int LIST_TOP = 58;
    private static final int BUTTON_Y = PANEL_HEIGHT - 24;
    private static final int STATUS_Y = PANEL_HEIGHT - 38;
    /** 出品する：持ち物の枠の上の端 */
    private static final int GRID_Y = 50;

    private enum Tab { BROWSE, SELL, MINE, HISTORY, STALLS }

    private final MarketService.Mode mode;
    private final int stall;
    private final boolean open;
    private final int openDay;
    private final int today;

    private Tab tab = Tab.BROWSE;
    private String query = "";
    private int category;
    private int sort;
    private int page;
    private long selectedId = -1;
    private boolean confirming;
    private int sellSlot = -1;
    private int sellCount = 1;
    private String priceText = "";
    private long mineSelected = -1;
    private int historyPage;
    private int stallPage;
    private int stallSelected = -1;
    private String stallName = "";
    @Nullable
    private Component status;
    private boolean statusOk;
    private int seenVersion = -1;
    private int seenPriceSlot = -2;

    private int left;
    private int top;
    private final List<Button> tabButtons = new ArrayList<>();
    @Nullable
    private EditBox search;
    @Nullable
    private EditBox priceBox;
    @Nullable
    private EditBox nameBox;
    @Nullable
    private Button actionButton;
    @Nullable
    private Button secondButton;
    @Nullable
    private Button thirdButton;
    @Nullable
    private Button fourthButton;
    @Nullable
    private Button prevButton;
    @Nullable
    private Button nextButton;
    @Nullable
    private Button categoryButton;
    @Nullable
    private Button sortButton;

    public MarketScreen(OpenMarketS2C message) {
        super(Component.translatable("screen.amemaru.market.title_notebook"));
        this.mode = message.mode();
        this.stall = message.stall();
        this.open = message.open();
        this.openDay = message.openDay();
        this.today = message.today();
        if (mode == MarketService.Mode.BOOTH) {
            tab = Tab.BROWSE;
        }
    }

    // ---------------------------------------------------------------- 結果

    /** サーバーからの結果。うまくいったら、今のタブの中身を取り直す。 */
    public void showResult(Component message, boolean ok) {
        status = message;
        statusOk = ok;
        if (ok) {
            confirming = false;
            if (tab == Tab.SELL) {
                sellSlot = -1;
                priceText = "";
                ClientMarket.setPrice(null);
            }
            request();
            if (tab == Tab.SELL || tab == Tab.STALLS) {
                ModNetwork.sendToServer(MarketActionC2S.of(MarketActionC2S.Action.MINE));
            }
            rebuildWidgets();
        }
    }

    // ---------------------------------------------------------------- 組み立て

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        tabButtons.clear();
        search = null;
        priceBox = null;
        nameBox = null;
        actionButton = secondButton = thirdButton = fourthButton = prevButton = nextButton = categoryButton = sortButton = null;

        addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 54, top + BUTTON_Y, 42, 16,
                Component.translatable("screen.amemaru.status.close"), b -> onClose()));
        if (!open) {
            return;
        }
        if (mode != MarketService.Mode.STALL) {
            Tab[] tabs = Tab.values();
            int x = left + 12;
            for (Tab t : tabs) {
                int w = t == Tab.HISTORY ? 66 : 52;
                Button button = addRenderableWidget(new CrystalButton(x, top + 22, w, 14,
                        Component.translatable("screen.amemaru.market.tab." + t.name().toLowerCase(Locale.ROOT)), b -> switchTab(t)));
                button.active = tab != t;
                tabButtons.add(button);
                x += w + 2;
            }
        }
        switch (tab) {
            case BROWSE -> initBrowse();
            case SELL -> initSell();
            case MINE -> initMine();
            case HISTORY -> initHistory();
            case STALLS -> initStalls();
        }
        if (seenVersion < 0) {
            request();
            if (mode != MarketService.Mode.STALL) {
                ModNetwork.sendToServer(MarketActionC2S.of(MarketActionC2S.Action.MINE));
            }
            seenVersion = 0;
        }
        updateButtons();
    }

    /** 自動の確認の写真で、タブを変える（0：さがす・1：出品する・2：出品中・3：売上と履歴・4：露店）。 */
    public void ciTab(int index) {
        switchTab(Tab.values()[Math.floorMod(index, Tab.values().length)]);
    }

    private void switchTab(Tab next) {
        if (tab == next) {
            return;
        }
        tab = next;
        confirming = false;
        status = null;
        selectedId = -1;
        mineSelected = -1;
        rebuildWidgets();
        request();
    }

    /** 今のタブの中身をサーバーに頼む。 */
    private void request() {
        switch (tab) {
            case BROWSE -> sendQuery();
            case SELL, MINE, HISTORY -> ModNetwork.sendToServer(MarketActionC2S.of(MarketActionC2S.Action.MINE));
            case STALLS -> ModNetwork.sendToServer(MarketActionC2S.of(MarketActionC2S.Action.STALLS));
        }
    }

    private void sendQuery() {
        List<ResourceLocation> ids = new ArrayList<>();
        if (!query.isBlank()) {
            String q = query.toLowerCase(Locale.ROOT).strip();
            for (Item item : BuiltInRegistries.ITEM) {
                String name = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (name.contains(q) || id.getPath().contains(q)) {
                    ids.add(id);
                    if (ids.size() >= MarketService.MAX_SEARCH_IDS) {
                        break;
                    }
                }
            }
        }
        long stallFilter = mode == MarketService.Mode.STALL ? stall : 0;
        ModNetwork.sendToServer(new MarketActionC2S(MarketActionC2S.Action.QUERY, stallFilter, category, sort, page, 0, query, ids));
    }

    private void initBrowse() {
        int y = top + 40;
        if (mode != MarketService.Mode.STALL) {
            search = new EditBox(font, left + 12, y, 150, 14, Component.translatable("screen.amemaru.market.search"));
            search.setMaxLength(32);
            search.setValue(query);
            search.setResponder(text -> {
                if (!text.equals(query)) {
                    query = text;
                    page = 0;
                    selectedId = -1;
                    sendQuery();
                }
            });
            addRenderableWidget(search);
            categoryButton = addRenderableWidget(new CrystalButton(left + 166, y, 72, 14, categoryLabel(), b -> {
                category = (category + 1) % MarketService.Category.values().length;
                page = 0;
                selectedId = -1;
                b.setMessage(categoryLabel());
                sendQuery();
            }));
            sortButton = addRenderableWidget(new CrystalButton(left + 242, y, 70, 14, sortLabel(), b -> {
                sort = (sort + 1) % MarketService.Sort.values().length;
                page = 0;
                b.setMessage(sortLabel());
                sendQuery();
            }));
        }
        int arrowsY = top + LIST_TOP + ROWS * ROW + 2;
        prevButton = addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 62, arrowsY, 20, 12, Component.literal("<"), b -> {
            page = Math.max(0, page - 1);
            selectedId = -1;
            sendQuery();
        }));
        nextButton = addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 36, arrowsY, 20, 12, Component.literal(">"), b -> {
            page++;
            selectedId = -1;
            sendQuery();
        }));
        if (confirming) {
            actionButton = addRenderableWidget(new CrystalButton(left + 12, top + BUTTON_Y, 60, 16,
                    Component.translatable("screen.amemaru.market.yes"), b -> buySelected()));
            secondButton = addRenderableWidget(new CrystalButton(left + 76, top + BUTTON_Y, 60, 16,
                    Component.translatable("screen.amemaru.market.no"), b -> {
                        confirming = false;
                        rebuildWidgets();
                    }));
        } else {
            actionButton = addRenderableWidget(new CrystalButton(left + 12, top + BUTTON_Y, 150, 16,
                    Component.translatable("screen.amemaru.market.buy_pick"), b -> {
                        if (selectedRow() != null && !selectedRow().mine()) {
                            confirming = true;
                            rebuildWidgets();
                        }
                    }));
        }
    }

    private void initSell() {
        int x = left + 186;
        int y = top + 106;
        addRenderableWidget(new CrystalButton(x, y, 18, 14, Component.literal("-"), b -> addCount(-1)));
        addRenderableWidget(new CrystalButton(x + 56, y, 18, 14, Component.literal("+"), b -> addCount(1)));
        addRenderableWidget(new CrystalButton(x + 78, y, 34, 14, Component.translatable("screen.amemaru.market.sell.all"), b -> {
            ItemStack stack = slotStack(sellSlot);
            sellCount = stack.isEmpty() ? 1 : stack.getCount();
        }));
        priceBox = new EditBox(font, x, y + 28, 70, 14, Component.translatable("screen.amemaru.market.sell.price"));
        priceBox.setMaxLength(12);
        priceBox.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        priceBox.setValue(priceText);
        priceBox.setResponder(text -> priceText = text);
        addRenderableWidget(priceBox);
        addRenderableWidget(new CrystalButton(x + 74, y + 28, 38, 14, Component.translatable("screen.amemaru.market.sell.reset"), b -> {
            MarketPriceS2C info = ClientMarket.price();
            if (info != null && info.slot() == sellSlot && info.reference() > 0) {
                priceText = Long.toString(info.reference());
                if (priceBox != null) {
                    priceBox.setValue(priceText);
                }
            }
        }));
        actionButton = addRenderableWidget(new CrystalButton(left + 12, top + BUTTON_Y, 90, 16,
                Component.translatable("screen.amemaru.market.sell.button"), b -> listSelected()));
    }

    private void initMine() {
        actionButton = addRenderableWidget(new CrystalButton(left + 12, top + BUTTON_Y, 90, 16,
                Component.translatable("screen.amemaru.market.mine.cancel"), b -> {
                    if (mineSelected >= 0) {
                        ModNetwork.sendToServer(new MarketActionC2S(MarketActionC2S.Action.CANCEL, mineSelected, 0, 0, 0, 0, "", List.of()));
                    }
                }));
    }

    private void initHistory() {
        actionButton = addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 92, top + 40, 80, 14,
                Component.translatable("screen.amemaru.market.history.collect"),
                b -> ModNetwork.sendToServer(MarketActionC2S.of(MarketActionC2S.Action.COLLECT))));
        int arrowsY = top + LIST_TOP + ROWS * ROW + 2;
        prevButton = addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 62, arrowsY, 20, 12, Component.literal("<"),
                b -> historyPage = Math.max(0, historyPage - 1)));
        nextButton = addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 36, arrowsY, 20, 12, Component.literal(">"),
                b -> historyPage++));
    }

    private void initStalls() {
        int arrowsY = top + LIST_TOP + ROWS * ROW + 2;
        prevButton = addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 62, arrowsY, 20, 12, Component.literal("<"),
                b -> stallPage = Math.max(0, stallPage - 1)));
        nextButton = addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 36, arrowsY, 20, 12, Component.literal(">"),
                b -> stallPage++));
        if (mode != MarketService.Mode.BOOTH) {
            return;
        }
        nameBox = new EditBox(font, left + 12, top + 40, 120, 14, Component.translatable("screen.amemaru.market.stalls.name"));
        nameBox.setMaxLength(16);
        nameBox.setValue(stallName);
        nameBox.setResponder(text -> stallName = text);
        addRenderableWidget(nameBox);
        MarketStallsS2C stalls = ClientMarket.stalls();
        actionButton = addRenderableWidget(new CrystalButton(left + 12, top + BUTTON_Y, 120, 16,
                Component.translatable("screen.amemaru.market.stalls.rent", knots(stalls.rentPrice()), stalls.rentDays()), b -> {
                    if (stallSelected > 0) {
                        ModNetwork.sendToServer(new MarketActionC2S(MarketActionC2S.Action.RENT, 0, stallSelected, 0, 0, 0, stallName, List.of()));
                    }
                }));
        secondButton = addRenderableWidget(new CrystalButton(left + 136, top + 40, 54, 14, Component.translatable("screen.amemaru.market.stalls.rename"),
                b -> ModNetwork.sendToServer(new MarketActionC2S(MarketActionC2S.Action.RENAME, 0, 0, 0, 0, 0, stallName, List.of()))));
        thirdButton = addRenderableWidget(new CrystalButton(left + 194, top + 40, 54, 14, Component.translatable("screen.amemaru.market.stalls.extend"),
                b -> ModNetwork.sendToServer(MarketActionC2S.of(MarketActionC2S.Action.EXTEND))));
        fourthButton = addRenderableWidget(new CrystalButton(left + 252, top + 40, 60, 14, Component.translatable("screen.amemaru.market.stalls.release"),
                b -> ModNetwork.sendToServer(MarketActionC2S.of(MarketActionC2S.Action.RELEASE))));
    }

    // ---------------------------------------------------------------- 操作

    private Component categoryLabel() {
        return Component.translatable("screen.amemaru.market.category." + MarketService.Category.values()[category].name().toLowerCase(Locale.ROOT));
    }

    private Component sortLabel() {
        return Component.translatable("screen.amemaru.market.sort." + MarketService.Sort.values()[sort].name().toLowerCase(Locale.ROOT));
    }

    @Nullable
    private MarketPageS2C.Row selectedRow() {
        MarketPageS2C data = ClientMarket.page();
        if (data == null || selectedId < 0) {
            return null;
        }
        for (MarketPageS2C.Row row : data.rows()) {
            if (row.id() == selectedId) {
                return row;
            }
        }
        return null;
    }

    private void buySelected() {
        MarketPageS2C.Row row = selectedRow();
        if (row != null && !row.mine()) {
            ModNetwork.sendToServer(new MarketActionC2S(MarketActionC2S.Action.BUY, row.id(), mode == MarketService.Mode.STALL ? stall : 0,
                    0, 0, row.unitPrice(), "", List.of()));
        }
    }

    private ItemStack slotStack(int slot) {
        if (minecraft == null || minecraft.player == null || slot < 0) {
            return ItemStack.EMPTY;
        }
        Inventory inventory = minecraft.player.getInventory();
        return slot < inventory.items.size() ? inventory.items.get(slot) : ItemStack.EMPTY;
    }

    private void addCount(int delta) {
        ItemStack stack = slotStack(sellSlot);
        sellCount = Mth.clamp(sellCount + delta, 1, Math.max(1, stack.getCount()));
    }

    private long parsedPrice() {
        if (priceText.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(priceText);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void listSelected() {
        ItemStack stack = slotStack(sellSlot);
        MarketPriceS2C info = ClientMarket.price();
        if (stack.isEmpty() || info == null || info.slot() != sellSlot || !info.problem().isEmpty()) {
            return;
        }
        ModNetwork.sendToServer(new MarketActionC2S(MarketActionC2S.Action.LIST, 0, sellSlot, Math.min(sellCount, stack.getCount()), 0,
                parsedPrice(), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), List.of()));
    }

    private void updateButtons() {
        for (int i = 0; i < tabButtons.size(); i++) {
            tabButtons.get(i).active = Tab.values()[i] != tab;
        }
        MarketPageS2C data = ClientMarket.page();
        switch (tab) {
            case BROWSE -> {
                if (prevButton != null && nextButton != null) {
                    int pages = data == null ? 1 : data.pages();
                    page = data == null ? page : Math.min(page, data.pages() - 1);
                    prevButton.visible = nextButton.visible = pages > 1;
                    prevButton.active = page > 0;
                    nextButton.active = page < pages - 1;
                }
                if (actionButton != null && !confirming) {
                    MarketPageS2C.Row row = selectedRow();
                    actionButton.active = row != null && !row.mine() && row.total() <= ClientPlayerState.get().knots();
                    actionButton.setMessage(row == null ? Component.translatable("screen.amemaru.market.buy_pick")
                            : Component.translatable("screen.amemaru.market.buy", knots(row.total())));
                }
            }
            case SELL -> {
                if (actionButton != null) {
                    MarketPriceS2C info = ClientMarket.price();
                    long price = parsedPrice();
                    actionButton.active = info != null && info.slot() == sellSlot && info.problem().isEmpty() && price >= info.min()
                            && price <= info.max() && info.listings() < info.limit() && !slotStack(sellSlot).isEmpty();
                }
            }
            case MINE -> {
                if (actionButton != null) {
                    actionButton.active = mineSelected >= 0;
                }
            }
            case HISTORY -> {
                MarketMineS2C mine = ClientMarket.mine();
                if (actionButton != null) {
                    actionButton.active = mine != null && mine.proceeds() > 0;
                }
                if (prevButton != null && nextButton != null) {
                    int pages = mine == null ? 1 : Math.max(1, (mine.history().size() + ROWS - 1) / ROWS);
                    historyPage = Mth.clamp(historyPage, 0, pages - 1);
                    prevButton.visible = nextButton.visible = pages > 1;
                    prevButton.active = historyPage > 0;
                    nextButton.active = historyPage < pages - 1;
                }
            }
            case STALLS -> {
                MarketStallsS2C stalls = ClientMarket.stalls();
                int pages = Math.max(1, (stalls.stalls().size() + ROWS - 1) / ROWS);
                stallPage = Mth.clamp(stallPage, 0, pages - 1);
                if (prevButton != null && nextButton != null) {
                    prevButton.visible = nextButton.visible = pages > 1;
                    prevButton.active = stallPage > 0;
                    nextButton.active = stallPage < pages - 1;
                }
                MarketStallsS2C.View mine = myStall();
                if (actionButton != null) {
                    MarketStallsS2C.View chosen = ClientMarket.stall(stallSelected);
                    actionButton.active = mine == null && chosen != null && chosen.active() && chosen.owner() == null
                            && ClientPlayerState.get().knots() >= stalls.rentPrice();
                }
                if (secondButton != null) {
                    secondButton.active = mine != null && !stallName.isBlank();
                }
                if (thirdButton != null) {
                    thirdButton.active = mine != null && mine.lastDay() - stalls.today() + 1 < stalls.rentDays();
                }
                if (fourthButton != null) {
                    fourthButton.active = mine != null;
                }
            }
        }
    }

    @Nullable
    private MarketStallsS2C.View myStall() {
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        UUID self = minecraft.player.getUUID();
        for (MarketStallsS2C.View view : ClientMarket.stalls().stalls()) {
            if (self.equals(view.owner())) {
                return view;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- マウス

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && open && !confirming) {
            int x0 = left + 12;
            int x1 = left + PANEL_WIDTH - 12;
            int y = top + LIST_TOP;
            switch (tab) {
                case BROWSE -> {
                    MarketPageS2C data = ClientMarket.page();
                    if (data != null) {
                        for (MarketPageS2C.Row row : data.rows()) {
                            if (mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW - 2) {
                                selectedId = row.id();
                                updateButtons();
                                return true;
                            }
                            y += ROW;
                        }
                    }
                }
                case SELL -> {
                    int slot = inventorySlotAt(mouseX, mouseY);
                    if (slot >= 0 && !slotStack(slot).isEmpty()) {
                        sellSlot = slot;
                        sellCount = slotStack(slot).getCount();
                        priceText = "";
                        if (priceBox != null) {
                            priceBox.setValue("");
                        }
                        ClientMarket.setPrice(null);
                        ModNetwork.sendToServer(new MarketActionC2S(MarketActionC2S.Action.PRICE, 0, slot, 0, 0, 0, "", List.of()));
                        return true;
                    }
                }
                case MINE -> {
                    MarketMineS2C mine = ClientMarket.mine();
                    if (mine != null) {
                        for (int i = 0; i < Math.min(ROWS, mine.listings().size()); i++) {
                            if (mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW - 2) {
                                mineSelected = mine.listings().get(i).id();
                                updateButtons();
                                return true;
                            }
                            y += ROW;
                        }
                    }
                }
                case STALLS -> {
                    List<MarketStallsS2C.View> views = ClientMarket.stalls().stalls();
                    for (int i = stallPage * ROWS; i < Math.min(views.size(), (stallPage + 1) * ROWS); i++) {
                        if (mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW - 2) {
                            stallSelected = views.get(i).num();
                            updateButtons();
                            return true;
                        }
                        y += ROW;
                    }
                }
                default -> {
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 出品する：持ち物の枠（上3段が 9〜35、下の段が 0〜8）。 */
    private int inventorySlotAt(double mouseX, double mouseY) {
        int gx = left + 12;
        int gy = top + GRID_Y;
        for (int i = 0; i < 36; i++) {
            int[] at = slotXY(i, gx, gy);
            if (mouseX >= at[0] && mouseX < at[0] + 18 && mouseY >= at[1] && mouseY < at[1] + 18) {
                return i;
            }
        }
        return -1;
    }

    private static int[] slotXY(int slot, int gx, int gy) {
        if (slot < 9) {
            return new int[]{gx + slot * 18, gy + 3 * 18 + 4};
        }
        int row = (slot - 9) / 9;
        int col = (slot - 9) % 9;
        return new int[]{gx + col * 18, gy + row * 18};
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditBox box : new EditBox[]{search, priceBox, nameBox}) {
            if (box != null && box.isFocused() && keyCode != 256) {
                return box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput() || super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        for (EditBox box : new EditBox[]{search, priceBox, nameBox}) {
            if (box != null) {
                box.tick();
            }
        }
        if (ClientMarket.version() != seenVersion) {
            seenVersion = ClientMarket.version();
            MarketPriceS2C info = ClientMarket.price();
            if (tab == Tab.SELL && info != null && info.slot() == sellSlot && seenPriceSlot != sellSlot) {
                seenPriceSlot = sellSlot;
                if (priceText.isEmpty() && info.reference() > 0) {
                    priceText = Long.toString(info.reference());
                    if (priceBox != null) {
                        priceBox.setValue(priceText);
                    }
                }
            }
            if (info == null) {
                seenPriceSlot = -2;
            }
        }
        updateButtons();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- 描く

    private static String knots(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private Component heading() {
        if (mode == MarketService.Mode.BOOTH) {
            return Component.translatable("screen.amemaru.market.title_booth");
        }
        if (mode == MarketService.Mode.STALL) {
            MarketStallsS2C.View view = ClientMarket.stall(stall);
            if (view == null || view.owner() == null) {
                return Component.translatable("screen.amemaru.market.title_stall_empty", stall);
            }
            String name = view.name().isBlank() ? view.ownerName() : view.name();
            return Component.translatable("screen.amemaru.market.title_stall", stall, name);
        }
        return title;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT);
        graphics.drawCenteredString(font, heading(), width / 2, top + 7, UiStyle.TEXT_HEADER);
        UiStyle.divider(graphics, left + 12, left + PANEL_WIDTH - 12, top + 19);
        Component wallet = Component.translatable("screen.amemaru.transfer.balance", knots(ClientPlayerState.get().knots()));
        int walletY = mode == MarketService.Mode.STALL ? top + 24 : top + 8;
        graphics.drawString(font, wallet, left + PANEL_WIDTH - 14 - font.width(wallet), walletY, UiStyle.TEXT_GOLD, true);

        ItemStack hovered = ItemStack.EMPTY;
        if (!open) {
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.market.closed", openDay, today).getString(),
                    left + 16, top + 60, PANEL_WIDTH - 32, UiStyle.TEXT, 4);
        } else {
            switch (tab) {
                case BROWSE -> hovered = renderBrowse(graphics, mouseX, mouseY);
                case SELL -> hovered = renderSell(graphics, mouseX, mouseY);
                case MINE -> hovered = renderMine(graphics, mouseX, mouseY);
                case HISTORY -> hovered = renderHistory(graphics, mouseX, mouseY);
                case STALLS -> renderStalls(graphics, mouseX, mouseY);
            }
        }
        if (status != null) {
            graphics.drawString(font, UiStyle.clip(font, status.getString(), PANEL_WIDTH - 96), left + 14, top + STATUS_Y,
                    statusOk ? UiStyle.TEXT_GOOD : UiStyle.TEXT_BAD, true);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        placeholder(graphics, search, "screen.amemaru.market.search");
        placeholder(graphics, nameBox, "screen.amemaru.market.stalls.name");
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    /** 文字の欄が空で、入力していないときの、うすい案内。 */
    private void placeholder(GuiGraphics graphics, @Nullable EditBox box, String key) {
        if (box != null && box.getValue().isEmpty() && !box.isFocused()) {
            graphics.drawString(font, Component.translatable(key), box.getX() + 4, box.getY() + (box.getHeight() - 8) / 2, UiStyle.TEXT_DIM, false);
        }
    }

    private boolean over(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private ItemStack renderBrowse(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        if (mode == MarketService.Mode.STALL) {
            MarketStallsS2C.View view = ClientMarket.stall(stall);
            boolean own = view != null && minecraft != null && minecraft.player != null && minecraft.player.getUUID().equals(view.owner());
            String note = view == null || view.owner() == null ? Component.translatable("screen.amemaru.market.stall.empty").getString()
                    : own ? Component.translatable("screen.amemaru.market.stall.owner_note").getString()
                    : Component.translatable("screen.amemaru.market.stall.half").getString();
            graphics.drawString(font, UiStyle.clip(font, note, PANEL_WIDTH - 130), left + 14, top + 24, UiStyle.TEXT_DIM, true);
        }
        MarketPageS2C data = ClientMarket.page();
        int x0 = left + 12;
        int x1 = left + PANEL_WIDTH - 12;
        int y = top + LIST_TOP;
        if (data == null || data.rows().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.market.none"), x0 + 4, y + 4, UiStyle.TEXT_DIM, true);
        } else {
            for (MarketPageS2C.Row row : data.rows()) {
                boolean chosen = row.id() == selectedId;
                boolean hover = over(mouseX, mouseY, x0, y, x1 - x0, ROW - 2);
                UiStyle.card(graphics, x0, y, x1, y + ROW - 2, chosen ? 0xFFFFD36A : 0xFF8FD3FF, chosen || hover);
                graphics.renderItem(row.item(), x0 + 1, y);
                String name = row.item().getHoverName().getString() + (row.item().getCount() > 1 ? " ×" + row.item().getCount() : "");
                graphics.drawString(font, UiStyle.clip(font, name, 118), x0 + 21, y + 4, chosen ? UiStyle.TEXT_GOLD : UiStyle.TEXT, true);
                String unit = Component.translatable("screen.amemaru.market.unit", knots(row.unitPrice())).getString();
                graphics.drawString(font, unit, x0 + 206 - font.width(unit), y + 4, UiStyle.TEXT_GOLD, true);
                String seller = row.mine() ? Component.translatable("screen.amemaru.market.own").getString()
                        : row.sellerStall() > 0 ? Component.translatable("screen.amemaru.market.row_stall", row.seller(), row.sellerStall()).getString()
                        : row.seller();
                graphics.drawString(font, UiStyle.clip(font, seller, x1 - x0 - 214), x0 + 212, y + 4, row.mine() ? UiStyle.TEXT_DIM : UiStyle.TEXT,
                        true);
                if (over(mouseX, mouseY, x0 + 1, y, 16, 16)) {
                    hovered = row.item();
                }
                y += ROW;
            }
            String pages = Component.translatable("screen.amemaru.market.page", data.page() + 1, data.pages(), data.total()).getString();
            graphics.drawString(font, pages, left + PANEL_WIDTH - 68 - font.width(pages), top + LIST_TOP + ROWS * ROW + 4, UiStyle.TEXT_DIM, true);
        }
        MarketPageS2C.Row row = selectedRow();
        int infoY = top + LIST_TOP + ROWS * ROW + 4;
        if (row != null && status == null) {
            String info = Component.translatable("screen.amemaru.market.info", knots(row.reference())).getString() + "　"
                    + (row.recent().length == 0 ? Component.translatable("screen.amemaru.market.recent_none").getString()
                    : Component.translatable("screen.amemaru.market.recent", joinPrices(row.recent())).getString());
            graphics.drawString(font, UiStyle.clip(font, info, PANEL_WIDTH - 100), left + 14, infoY, UiStyle.TEXT_DIM, true);
        }
        if (confirming && row != null) {
            String text = Component.translatable("screen.amemaru.market.confirm", row.item().getHoverName(), row.item().getCount(),
                    knots(row.total())).getString();
            int boxY = top + LIST_TOP + 30;
            graphics.fill(left + 30, boxY - 6, left + PANEL_WIDTH - 30, boxY + 56, 0xF0101828);
            UiStyle.wrapped(graphics, font, text, left + 40, boxY, PANEL_WIDTH - 80, UiStyle.TEXT, 3);
            UiStyle.wrapped(graphics, font, Component.translatable(mode == MarketService.Mode.STALL ? "screen.amemaru.market.confirm_note_stall"
                    : "screen.amemaru.market.confirm_note").getString(), left + 40, boxY + 26, PANEL_WIDTH - 80, UiStyle.TEXT_DIM, 3);
        }
        return hovered;
    }

    private static String joinPrices(long[] prices) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < prices.length; i++) {
            if (i > 0) {
                out.append("・");
            }
            out.append(knots(prices[i]));
        }
        return out.toString();
    }

    private ItemStack renderSell(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        int gx = left + 12;
        int gy = top + GRID_Y;
        graphics.drawString(font, Component.translatable("screen.amemaru.market.sell.pick"), gx, top + 40, UiStyle.TEXT_DIM, true);
        for (int i = 0; i < 36; i++) {
            int[] at = slotXY(i, gx, gy);
            boolean chosen = i == sellSlot;
            graphics.fill(at[0], at[1], at[0] + 17, at[1] + 17, chosen ? 0xC0FFD36A : 0x80202A40);
            graphics.fill(at[0] + 1, at[1] + 1, at[0] + 16, at[1] + 16, 0xC0101622);
            ItemStack stack = slotStack(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, at[0], at[1]);
                graphics.renderItemDecorations(font, stack, at[0], at[1]);
                if (over(mouseX, mouseY, at[0], at[1], 17, 17)) {
                    hovered = stack;
                }
            }
        }
        int x = left + 186;
        ItemStack stack = slotStack(sellSlot);
        MarketPriceS2C info = ClientMarket.price();
        if (stack.isEmpty()) {
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.market.sell.pick_hint").getString(), x, top + 52,
                    PANEL_WIDTH - 198, UiStyle.TEXT_DIM, 6);
            return hovered;
        }
        sellCount = Mth.clamp(sellCount, 1, stack.getCount());
        graphics.renderItem(stack, x, top + 44);
        graphics.drawString(font, UiStyle.clip(font, stack.getHoverName().getString(), PANEL_WIDTH - 220), x + 20, top + 48, UiStyle.TEXT, true);
        graphics.drawString(font, Component.translatable("screen.amemaru.market.sell.count"), x, top + 96, UiStyle.TEXT_HEADER, true);
        graphics.drawCenteredString(font, "×" + sellCount, x + 37, top + 109, UiStyle.TEXT);
        graphics.drawString(font, Component.translatable("screen.amemaru.market.sell.price"), x, top + 124, UiStyle.TEXT_HEADER, true);
        if (info == null || info.slot() != sellSlot) {
            graphics.drawString(font, Component.translatable("screen.amemaru.market.loading"), x, top + 62, UiStyle.TEXT_DIM, true);
            return hovered;
        }
        if (!info.problem().isEmpty()) {
            UiStyle.wrapped(graphics, font, Component.translatable(info.problem()).getString(), x, top + 62, PANEL_WIDTH - 198, UiStyle.TEXT_BAD, 3);
            return hovered;
        }
        graphics.drawString(font, Component.translatable("screen.amemaru.market.sell.reference", knots(info.reference())), x, top + 62,
                UiStyle.TEXT_GOLD, true);
        graphics.drawString(font, UiStyle.clip(font, Component.translatable("screen.amemaru.market.sell.range", knots(info.min()),
                knots(info.max())).getString(), PANEL_WIDTH - 198), x, top + 73, UiStyle.TEXT_DIM, true);
        long price = parsedPrice();
        long total = price * sellCount;
        boolean inRange = price >= info.min() && price <= info.max();
        int y = top + 152;
        graphics.drawString(font, Component.translatable("screen.amemaru.market.sell.total", knots(total)), x, y,
                inRange ? UiStyle.TEXT : UiStyle.TEXT_BAD, true);
        long listingFee = info.freeDay() ? 0 : fee(total, info.listingFee());
        long saleFee = fee(total, info.saleFee());
        long stallFee = fee(total, info.saleFee() / 2.0);
        graphics.drawString(font, info.freeDay() ? Component.translatable("screen.amemaru.market.sell.fee_free")
                : Component.translatable("screen.amemaru.market.sell.fee", knots(listingFee)), x, y + 11, UiStyle.TEXT_DIM, true);
        graphics.drawString(font, UiStyle.clip(font, Component.translatable("screen.amemaru.market.sell.sale_fee", knots(saleFee), knots(stallFee))
                .getString(), PANEL_WIDTH - 198), x, y + 22, UiStyle.TEXT_DIM, true);
        graphics.drawString(font, Component.translatable("screen.amemaru.market.sell.receive", knots(Math.max(0, total - saleFee))), x, y + 33,
                UiStyle.TEXT_GOLD, true);
        graphics.drawString(font, Component.translatable("screen.amemaru.market.sell.listings", info.listings(), info.limit()), x, top + 84,
                info.listings() >= info.limit() ? UiStyle.TEXT_BAD : UiStyle.TEXT_DIM, true);
        if (info.recent().length > 0) {
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.market.recent", joinPrices(info.recent())).getString(),
                    left + 14, top + GRID_Y + 82, 160, UiStyle.TEXT_DIM, 2);
        }
        return hovered;
    }

    private static long fee(long total, double percent) {
        if (total <= 0 || percent <= 0) {
            return 0;
        }
        return Math.max(1L, (long) Math.ceil(total * percent / 100.0));
    }

    private ItemStack renderMine(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        MarketMineS2C mine = ClientMarket.mine();
        int x0 = left + 12;
        int x1 = left + PANEL_WIDTH - 12;
        if (mine == null) {
            graphics.drawString(font, Component.translatable("screen.amemaru.market.loading"), x0 + 4, top + LIST_TOP + 4, UiStyle.TEXT_DIM, true);
            return hovered;
        }
        graphics.drawString(font, Component.translatable("screen.amemaru.market.mine.count", mine.listings().size(), mine.limit()), x0, top + 43,
                UiStyle.TEXT_HEADER, true);
        int y = top + LIST_TOP;
        if (mine.listings().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.market.mine.none"), x0 + 4, y + 4, UiStyle.TEXT_DIM, true);
        }
        for (int i = 0; i < Math.min(ROWS, mine.listings().size()); i++) {
            MarketPageS2C.Row row = mine.listings().get(i);
            boolean chosen = row.id() == mineSelected;
            boolean hover = over(mouseX, mouseY, x0, y, x1 - x0, ROW - 2);
            UiStyle.card(graphics, x0, y, x1, y + ROW - 2, chosen ? 0xFFFFD36A : 0xFF8FD3FF, chosen || hover);
            graphics.renderItem(row.item(), x0 + 1, y);
            String name = row.item().getHoverName().getString() + (row.item().getCount() > 1 ? " ×" + row.item().getCount() : "");
            graphics.drawString(font, UiStyle.clip(font, name, 118), x0 + 21, y + 4, chosen ? UiStyle.TEXT_GOLD : UiStyle.TEXT, true);
            String unit = Component.translatable("screen.amemaru.market.unit", knots(row.unitPrice())).getString();
            graphics.drawString(font, unit, x0 + 206 - font.width(unit), y + 4, UiStyle.TEXT_GOLD, true);
            String expires = row.expiresDay() > 0 ? Component.translatable("screen.amemaru.market.mine.expires", row.expiresDay()).getString()
                    : Component.translatable("screen.amemaru.market.mine.no_expiry").getString();
            graphics.drawString(font, UiStyle.clip(font, expires, x1 - x0 - 214), x0 + 212, y + 4, UiStyle.TEXT_DIM, true);
            if (over(mouseX, mouseY, x0 + 1, y, 16, 16)) {
                hovered = row.item();
            }
            y += ROW;
        }
        if (mine.listings().size() > ROWS) {
            graphics.drawString(font, Component.translatable("screen.amemaru.market.mine.more", mine.listings().size() - ROWS), x0 + 4,
                    top + LIST_TOP + ROWS * ROW + 4, UiStyle.TEXT_DIM, true);
        }
        return hovered;
    }

    private ItemStack renderHistory(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        MarketMineS2C mine = ClientMarket.mine();
        int x0 = left + 12;
        int x1 = left + PANEL_WIDTH - 12;
        if (mine == null) {
            graphics.drawString(font, Component.translatable("screen.amemaru.market.loading"), x0 + 4, top + LIST_TOP + 4, UiStyle.TEXT_DIM, true);
            return hovered;
        }
        graphics.drawString(font, Component.translatable("screen.amemaru.market.history.proceeds", knots(mine.proceeds())), x0, top + 43,
                mine.proceeds() > 0 ? UiStyle.TEXT_GOLD : UiStyle.TEXT_DIM, true);
        int y = top + LIST_TOP;
        if (mine.history().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.market.history.none"), x0 + 4, y + 4, UiStyle.TEXT_DIM, true);
        }
        for (int i = historyPage * ROWS; i < Math.min(mine.history().size(), (historyPage + 1) * ROWS); i++) {
            MarketMineS2C.Line line = mine.history().get(i);
            UiStyle.card(graphics, x0, y, x1, y + ROW - 2, 0xFF8FD3FF, false);
            int tx = x0 + 4;
            String head = Component.translatable("screen.amemaru.market.history.line", line.day(),
                    Component.translatable("screen.amemaru.market.history.kind." + line.kind().toLowerCase(Locale.ROOT))).getString();
            graphics.drawString(font, head, tx, y + 4, UiStyle.TEXT_HEADER, true);
            tx += font.width(head) + 4;
            if (!line.item().isEmpty()) {
                graphics.renderItem(line.item(), tx, y);
                if (over(mouseX, mouseY, tx, y, 16, 16)) {
                    hovered = line.item();
                }
                tx += 18;
                String name = line.item().getHoverName().getString() + " ×" + line.item().getCount();
                graphics.drawString(font, UiStyle.clip(font, name, 96), tx, y + 4, UiStyle.TEXT, true);
                tx += Math.min(96, font.width(name)) + 6;
            }
            String amount = amountText(line);
            if (!amount.isEmpty()) {
                int color = line.kind().equals("SOLD") || line.kind().equals("COLLECTED") ? UiStyle.TEXT_GOOD : UiStyle.TEXT_GOLD;
                graphics.drawString(font, UiStyle.clip(font, amount, Math.max(10, x1 - 4 - tx)), tx, y + 4, color, true);
            }
            y += ROW;
        }
        return hovered;
    }

    private String amountText(MarketMineS2C.Line line) {
        String fee = line.fee() > 0 ? Component.translatable("screen.amemaru.market.history.fee", knots(line.fee())).getString() : "";
        return switch (line.kind()) {
            case "SOLD" -> Component.translatable("screen.amemaru.market.history.plus", knots(line.amount())).getString() + fee + " "
                    + Component.translatable("screen.amemaru.market.history.buyer", line.other()).getString();
            case "BOUGHT" -> Component.translatable("screen.amemaru.market.history.minus", knots(line.amount())).getString() + " "
                    + Component.translatable("screen.amemaru.market.history.seller", line.other()).getString();
            case "COLLECTED" -> Component.translatable("screen.amemaru.market.history.plus", knots(line.amount())).getString();
            case "LISTED" -> fee.isEmpty() ? "" : Component.translatable("screen.amemaru.market.history.listing_fee", knots(line.fee())).getString();
            case "RENTED" -> Component.translatable("screen.amemaru.market.history.stall", line.other(), knots(line.amount())).getString();
            default -> "";
        };
    }

    private void renderStalls(GuiGraphics graphics, int mouseX, int mouseY) {
        MarketStallsS2C stalls = ClientMarket.stalls();
        int x0 = left + 12;
        int x1 = left + PANEL_WIDTH - 12;
        MarketStallsS2C.View mine = myStall();
        String mineText = mine == null ? Component.translatable("screen.amemaru.market.stalls.none_mine").getString()
                : Component.translatable("screen.amemaru.market.stalls.mine", mine.num(), mine.name(), mine.lastDay()).getString();
        if (mode != MarketService.Mode.BOOTH) {
            graphics.drawString(font, UiStyle.clip(font, mineText, PANEL_WIDTH - 24), x0, top + 43, mine == null ? UiStyle.TEXT_DIM : UiStyle.TEXT_GOLD,
                    true);
        }
        int y = top + LIST_TOP;
        if (stalls.stalls().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.market.stalls.unknown"), x0 + 4, y + 4, UiStyle.TEXT_DIM, true);
        }
        List<MarketStallsS2C.View> views = stalls.stalls();
        for (int i = stallPage * ROWS; i < Math.min(views.size(), (stallPage + 1) * ROWS); i++) {
            MarketStallsS2C.View view = views.get(i);
            boolean chosen = view.num() == stallSelected;
            boolean hover = over(mouseX, mouseY, x0, y, x1 - x0, ROW - 2);
            UiStyle.card(graphics, x0, y, x1, y + ROW - 2, chosen ? 0xFFFFD36A : 0xFF8FD3FF, chosen || hover);
            String head = Component.translatable("screen.amemaru.market.stalls.num", view.num()).getString();
            graphics.drawString(font, head, x0 + 4, y + 4, UiStyle.TEXT_HEADER, true);
            String body;
            int color;
            if (!view.active()) {
                body = Component.translatable("screen.amemaru.market.stalls.closed").getString();
                color = UiStyle.TEXT_DIM;
            } else if (view.owner() == null) {
                body = Component.translatable("screen.amemaru.market.stalls.vacant", view.title()).getString();
                color = UiStyle.TEXT_GOOD;
            } else {
                body = Component.translatable("screen.amemaru.market.stalls.rented", view.name(), view.ownerName(), view.lastDay(), view.listings())
                        .getString();
                color = mine != null && mine.num() == view.num() ? UiStyle.TEXT_GOLD : UiStyle.TEXT;
            }
            graphics.drawString(font, UiStyle.clip(font, body, x1 - x0 - 52), x0 + 48, y + 4, color, true);
            y += ROW;
        }
        int noteY = top + LIST_TOP + ROWS * ROW + 4;
        if (status == null) {
            String note = mode == MarketService.Mode.BOOTH ? mineText : Component.translatable("screen.amemaru.market.stalls.booth_only").getString();
            graphics.drawString(font, UiStyle.clip(font, note, PANEL_WIDTH - 100), left + 14, noteY, mine != null ? UiStyle.TEXT_GOLD : UiStyle.TEXT_DIM,
                    true);
        }
        if (mode == MarketService.Mode.BOOTH) {
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.market.stalls.note").getString(), left + 140,
                    top + BUTTON_Y - 2, PANEL_WIDTH - 200, UiStyle.TEXT_DIM, 2);
        }
    }
}

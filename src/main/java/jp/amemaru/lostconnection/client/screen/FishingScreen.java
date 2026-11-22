package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.FishingC2S;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ポッカ爺の釣り具屋（Phase 7 その2。Phase 0-A 7章「釣り堀：釣りのミニゲーム、魚図鑑、魚の売却」・15章「使い道：売却、
 * 留め糸との交換」）。ポッカ爺に話しかけて開く。タブは 図鑑・売る・留め糸と交換・釣り大会。
 */
public class FishingScreen extends Screen {
    private static final int W = 340;
    private static final int H = 226;
    private static final String[] TABS = {"dex", "sell", "trade", "tournament"};
    private static final int CELL = 18;
    private static final int COLS = 8;
    private static final int ROW = 20;
    private static final int ROWS = 7;

    private CompoundTag data;
    private String tab;
    private int left;
    private int top;
    private int selected;
    private int scroll;

    public FishingScreen(CompoundTag data) {
        super(Component.translatable("screen.amemaru.fishing.title"));
        this.data = data;
        this.tab = normalize(data.getString("tab"));
    }

    private static String normalize(String tab) {
        for (String t : TABS) {
            if (t.equals(tab)) {
                return t;
            }
        }
        return "dex";
    }

    /** 売った・交換したあとの中身（サーバーから）。 */
    public void update(CompoundTag next) {
        CompoundTag tournament = data.getCompound("tournament");
        data = next;
        if (!next.contains("tournament")) {
            data.put("tournament", tournament);
        }
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows().size() - ROWS));
    }

    /** 大会のようすが変わった（サーバーから）。 */
    public void updateTournament(CompoundTag tournament) {
        data.put("tournament", tournament);
    }

    /** 自動の確認の写真：タブを変える。 */
    public void ciTab(String name) {
        tab = normalize(name);
        scroll = 0;
        rebuild();
    }

    /** 自動の確認の写真：図鑑の index 番目を選ぶ。 */
    public void ciSelect(int index) {
        selected = Mth.clamp(index, 0, Math.max(0, dex().size() - 1));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        addRenderableWidget(new CrystalButton(left + W - 20, top + 5, 14, 12, Component.literal("×"), b -> onClose()));
        for (int i = 0; i < TABS.length; i++) {
            String name = TABS[i];
            CrystalButton button = new CrystalButton(left + 8 + i * 82, top + 20, 78, 14,
                    Component.translatable("screen.amemaru.fishing.tab." + name), b -> {
                tab = name;
                scroll = 0;
                rebuild();
            });
            button.active = !name.equals(tab);
            addRenderableWidget(button);
        }
        if ("sell".equals(tab)) {
            addRenderableWidget(new CrystalButton(left + 8, top + H - 24, 150, 16, Component.translatable("screen.amemaru.fishing.sell_all"),
                    b -> send("sell_all", new CompoundTag())));
            CrystalButton rod = new CrystalButton(left + W - 118, top + H - 24, 110, 16,
                    Component.translatable("screen.amemaru.fishing.rent_rod"), b -> send("rent_rod", new CompoundTag()));
            rod.active = !data.getBoolean("hasRod");
            addRenderableWidget(rod);
        } else if ("trade".equals(tab)) {
            addRenderableWidget(new CrystalButton(left + 8, top + H - 24, 150, 16, Component.translatable("screen.amemaru.fishing.trade_all"),
                    b -> send("trade_all", new CompoundTag())));
        }
    }

    private void send(String action, CompoundTag args) {
        args.putString("tab", tab);
        ModNetwork.sendToServer(new FishingC2S(action, args));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ListTag dex() {
        return data.getList("dex", Tag.TAG_COMPOUND);
    }

    /** 売る・交換のタブの行（交換は、交換できる魚だけ）。 */
    private List<CompoundTag> rows() {
        List<CompoundTag> list = new ArrayList<>();
        for (Tag t : data.getList("inv", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) t;
            if (!"trade".equals(tab) || one.getBoolean("tradable")) {
                list.add(one);
            }
        }
        return list;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + W, top + H);
        graphics.drawCenteredString(font, title, left + W / 2, top + 7, UiStyle.TEXT_HEADER);
        switch (tab) {
            case "sell" -> renderRows(graphics, mouseX, mouseY, false);
            case "trade" -> renderRows(graphics, mouseX, mouseY, true);
            case "tournament" -> renderTournament(graphics);
            default -> renderDex(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // ---------------------------------------------------------------- 図鑑

    private void renderDex(GuiGraphics graphics, int mouseX, int mouseY) {
        ListTag dex = dex();
        int total = dex.size();
        int species = data.getInt("species");
        int x0 = left + 8;
        int y0 = top + 40;
        Component head = Component.translatable("screen.amemaru.fishing.dex_head", species, total,
                total == 0 ? 0 : species * 100 / total, data.getInt("total"));
        graphics.drawString(font, UiStyle.clip(font, head.getString(), 156), x0, y0, UiStyle.TEXT_GOLD, false);
        int gx = x0;
        int gy = y0 + 12;
        UiStyle.card(graphics, gx - 2, gy - 2, gx + COLS * CELL + 2, gy + 5 * CELL + 2, 0x6AC8E8, false);
        for (int i = 0; i < total; i++) {
            CompoundTag one = dex.getCompound(i);
            int cx = gx + (i % COLS) * CELL;
            int cy = gy + (i / COLS) * CELL;
            boolean known = one.getBoolean("known");
            int bg = i == selected ? 0xFF35507A : (one.getBoolean("memory") || one.getBoolean("lord") ? 0xFF2A2140 : 0xFF141D33);
            graphics.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, bg);
            if (known) {
                graphics.renderItem(stackOf(one), cx + 1, cy + 1);
            } else {
                graphics.drawCenteredString(font, "?", cx + CELL / 2, cy + 5, 0xFF4A5674);
            }
        }
        if (data.contains("lordBy")) {
            graphics.drawString(font, UiStyle.clip(font, Component.translatable("screen.amemaru.fishing.lord_by", data.getString("lordBy"))
                    .getString(), COLS * CELL), x0, gy + 5 * CELL + 6, 0xFFFFC870, false);
        }
        // 右：選んだ魚
        int rx0 = left + 168;
        int rx1 = left + W - 8;
        UiStyle.card(graphics, rx0, y0 - 2, rx1, top + H - 8, 0x6AC8E8, false);
        if (selected >= total || total == 0) {
            return;
        }
        CompoundTag one = dex.getCompound(selected);
        boolean known = one.getBoolean("known");
        int x = rx0 + 6;
        int y = y0 + 4;
        if (known) {
            graphics.renderItem(stackOf(one), x, y - 2);
        }
        String name = known ? stackOf(one).getHoverName().getString() : Component.translatable("screen.amemaru.fishing.unknown").getString();
        graphics.drawString(font, UiStyle.clip(font, name, rx1 - x - 26), x + 20, y + 2, known ? UiStyle.TEXT : UiStyle.TEXT_DIM, false);
        y += 18;
        graphics.drawString(font, "★".repeat(Math.max(1, one.getInt("stars"))), x, y, 0xFFFFD866, false);
        Component kind = one.getBoolean("lord") ? Component.translatable("screen.amemaru.fishing.kind_lord")
                : one.getBoolean("memory") ? Component.translatable("screen.amemaru.fishing.kind_memory")
                : Component.translatable("screen.amemaru.fishing.chapter", one.getInt("chapter"));
        graphics.drawString(font, kind, x + 40, y, UiStyle.TEXT_DIM, false);
        y += 11;
        graphics.drawString(font, UiStyle.clip(font, Component.translatable("screen.amemaru.fishing.where", spots(one)).getString(), rx1 - x - 4),
                x, y, UiStyle.TEXT, false);
        y += 11;
        graphics.drawString(font, Component.translatable("screen.amemaru.fishing.size", fmt(one.getDouble("min")), fmt(one.getDouble("max"))),
                x, y, UiStyle.TEXT, false);
        y += 11;
        if (known) {
            graphics.drawString(font, Component.translatable("screen.amemaru.fishing.best", fmt(one.getDouble("best"))), x, y, UiStyle.TEXT_GOLD,
                    false);
            y += 11;
            graphics.drawString(font, Component.translatable("screen.amemaru.fishing.count", one.getInt("count"), one.getInt("first")), x, y,
                    UiStyle.TEXT_DIM, false);
            y += 13;
            UiStyle.wrapped(graphics, font, "「" + one.getString("comment") + "」", x, y, rx1 - x - 6, 0xFFBFE8FF, 5);
        } else {
            y += 2;
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.fishing.not_yet").getString(), x, y, rx1 - x - 6,
                    UiStyle.TEXT_DIM, 4);
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private String spots(CompoundTag one) {
        List<String> names = new ArrayList<>();
        for (Tag t : one.getList("spots", Tag.TAG_STRING)) {
            names.add(Component.translatable("screen.amemaru.fishing.spot." + t.getAsString()).getString());
        }
        return String.join("・", names);
    }

    private static ItemStack stackOf(CompoundTag one) {
        ResourceLocation id = ResourceLocation.tryParse(one.getString("item"));
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
        return new ItemStack(item);
    }

    // ---------------------------------------------------------------- 売る・交換

    private int[] rowButton(int row) {
        int y = top + 54 + row * ROW;
        return new int[]{left + W - 70, y + 3, left + W - 12, y + 16};
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY, boolean trade) {
        int x0 = left + 8;
        int y0 = top + 40;
        if (trade) {
            graphics.drawString(font, Component.translatable("screen.amemaru.fishing.trade_head", data.getInt("points"), data.getInt("per")),
                    x0, y0, UiStyle.TEXT_GOLD, false);
        } else {
            long sum = 0;
            for (CompoundTag one : rows()) {
                if (one.getBoolean("bulk")) {
                    sum += one.getLong("price") * ItemStack.of(one.getCompound("stack")).getCount();
                }
            }
            graphics.drawString(font, Component.translatable("screen.amemaru.fishing.sell_head", sum), x0, y0, UiStyle.TEXT_GOLD, false);
            Component note = Component.translatable("screen.amemaru.fishing.sell_note");
            graphics.drawString(font, note, left + W - 8 - font.width(note), y0, UiStyle.TEXT_DIM, false);
        }
        List<CompoundTag> rows = rows();
        UiStyle.card(graphics, x0 - 2, top + 52, left + W - 6, top + 54 + ROWS * ROW + 2, 0x6AC8E8, false);
        if (rows.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(trade ? "screen.amemaru.fishing.no_trade" : "screen.amemaru.fishing.no_fish"),
                    left + W / 2, top + 54 + ROW * 3, UiStyle.TEXT_DIM);
        }
        for (int i = scroll; i < Math.min(rows.size(), scroll + ROWS); i++) {
            CompoundTag one = rows.get(i);
            int y = top + 54 + (i - scroll) * ROW;
            ItemStack stack = ItemStack.of(one.getCompound("stack"));
            graphics.renderItem(stack, x0 + 2, y + 2);
            graphics.renderItemDecorations(font, stack, x0 + 2, y + 2);
            String name = UiStyle.clip(font, stack.getHoverName().getString(), 120);
            graphics.drawString(font, name, x0 + 22, y + 6, UiStyle.TEXT, false);
            Component value = trade
                    ? Component.translatable("screen.amemaru.fishing.row_points", one.getInt("points"), one.getInt("points") * stack.getCount())
                    : Component.translatable("screen.amemaru.fishing.row_price", one.getLong("price"), one.getLong("price") * stack.getCount());
            graphics.drawString(font, value, left + 158, y + 6, UiStyle.TEXT_DIM, false);
            int[] b = rowButton(i - scroll);
            boolean hover = mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3];
            graphics.fill(b[0], b[1], b[2], b[3], hover ? 0xFF2F4C80 : 0xFF22345A);
            graphics.drawCenteredString(font, Component.translatable(trade ? "screen.amemaru.fishing.trade" : "screen.amemaru.fishing.sell"),
                    (b[0] + b[2]) / 2, b[1] + 3, UiStyle.TEXT);
        }
    }

    // ---------------------------------------------------------------- 大会

    private void renderTournament(GuiGraphics graphics) {
        CompoundTag t = data.getCompound("tournament");
        int x = left + 10;
        int y = top + 42;
        int width = W - 20;
        if (t.getBoolean("on")) {
            graphics.drawString(font, Component.translatable("screen.amemaru.fishing.tour_on", t.getInt("number")), x, y, UiStyle.TEXT_GOLD, false);
            y += 12;
            graphics.drawString(font, Component.translatable(t.getBoolean("lake") ? "fishing.amemaru.tournament.venue_lake"
                    : "fishing.amemaru.tournament.venue_pond"), x, y, UiStyle.TEXT, false);
            y += 14;
            ListTag topList = t.getList("top", Tag.TAG_COMPOUND);
            if (topList.isEmpty()) {
                graphics.drawString(font, Component.translatable("fishing.amemaru.hud.none"), x, y, UiStyle.TEXT_DIM, false);
                y += 11;
            }
            for (int i = 0; i < topList.size(); i++) {
                CompoundTag one = topList.getCompound(i);
                graphics.drawString(font, Component.translatable("screen.amemaru.fishing.tour_row", one.getInt("rank"), one.getString("name"),
                        one.getLong("score"), one.getString("fish"), fmt(one.getDouble("size"))), x, y, i == 0 ? UiStyle.TEXT_GOLD : UiStyle.TEXT,
                        false);
                y += 11;
            }
            if (t.contains("mine")) {
                CompoundTag mine = t.getCompound("mine");
                graphics.drawString(font, Component.translatable("screen.amemaru.fishing.tour_mine", mine.getInt("rank"), mine.getLong("score"),
                        mine.getString("fish"), mine.getInt("catches")), x, y + 2, 0xFFE3A6FF, false);
                y += 13;
            }
            y += 6;
        } else {
            graphics.drawString(font, Component.translatable("screen.amemaru.fishing.tour_off", t.getInt("number")), x, y, UiStyle.TEXT_DIM, false);
            y += 16;
        }
        graphics.drawString(font, Component.translatable("screen.amemaru.fishing.tour_rules_head"), x, y, UiStyle.TEXT_HEADER, false);
        y += 12;
        UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.fishing.tour_rules").getString(), x, y, width, UiStyle.TEXT, 8);
    }

    // ---------------------------------------------------------------- 操作

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && "dex".equals(tab)) {
            int gx = left + 8;
            int gy = top + 52;
            if (mouseX >= gx && mouseX < gx + COLS * CELL && mouseY >= gy && mouseY < gy + 5 * CELL) {
                int index = (int) ((mouseY - gy) / CELL) * COLS + (int) ((mouseX - gx) / CELL);
                if (index >= 0 && index < dex().size()) {
                    selected = index;
                    return true;
                }
            }
        }
        if (button == 0 && ("sell".equals(tab) || "trade".equals(tab))) {
            List<CompoundTag> rows = rows();
            for (int i = scroll; i < Math.min(rows.size(), scroll + ROWS); i++) {
                int[] b = rowButton(i - scroll);
                if (mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3]) {
                    CompoundTag args = new CompoundTag();
                    args.putInt("slot", rows.get(i).getInt("slot"));
                    send("sell".equals(tab) ? "sell" : "trade", args);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if ("sell".equals(tab) || "trade".equals(tab)) {
            scroll = Mth.clamp(scroll + (delta > 0 ? -1 : 1), 0, Math.max(0, rows().size() - ROWS));
            return true;
        }
        if ("dex".equals(tab)) {
            int total = dex().size();
            if (total > 0) {
                selected = Mth.clamp(selected + (delta > 0 ? -1 : 1), 0, total - 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}

package jp.amemaru.lostconnection.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 記録帳（Phase 6 その3。Phase 0-A 14章「古代記録と記録帳」）。階ごとの収集率と、見つけた記録。
 * 解読していない記録は「未解読」（エルネストに渡すと読める）。見つけていない記録は、階とヒントだけ。
 * 解読した記録は、本文（ページをめくる）とエルネストのひと言が読める。
 */
public class RecordBookScreen extends Screen {
    private static final int W = 380;
    private static final int H = 222;
    private static final int FLOOR_W = 74;
    private static final int FLOOR_ROW = 14;
    private static final int FLOOR_ROWS = 11;
    private static final int ENTRY_H = 32;
    private static final int ENTRY_ROWS = 5;

    private CompoundTag data;
    private int left;
    private int top;
    private int floor = -1;
    private int floorScroll;
    private int entryScroll;
    @Nullable
    private String reading;
    private int page;

    public RecordBookScreen(CompoundTag data) {
        super(Component.translatable("screen.amemaru.record_book.title"));
        this.data = data;
    }

    public void update(CompoundTag data) {
        this.data = data;
        rebuildWidgets();
    }

    /** 自動の確認の写真で、その記録を開く（解読してあれば）。 */
    public void ciRead(String id) {
        for (Tag tag : data.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) tag;
            if (one.getString("id").equals(id)) {
                floor = one.getInt("floor");
            }
        }
        reading = id;
        page = 0;
        rebuildWidgets();
    }

    private List<Integer> floors() {
        int[] total = data.getIntArray("total");
        List<Integer> list = new ArrayList<>();
        for (int f = 0; f < total.length; f++) {
            if (total[f] > 0) {
                list.add(f);
            }
        }
        return list;
    }

    private List<CompoundTag> entries() {
        List<CompoundTag> list = new ArrayList<>();
        for (Tag tag : data.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) tag;
            if (one.getInt("floor") == floor) {
                list.add(one);
            }
        }
        return list;
    }

    @Nullable
    private CompoundTag readingEntry() {
        if (reading == null) {
            return null;
        }
        for (Tag tag : data.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) tag;
            if (one.getString("id").equals(reading) && "read".equals(one.getString("state"))) {
                return one;
            }
        }
        return null;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        List<Integer> floors = floors();
        if (!floors.contains(floor)) {
            floor = floors.isEmpty() ? 1 : floors.get(0);
        }
        addRenderableWidget(new CrystalButton(left + W - 20, top + 5, 14, 12, Component.literal("×"), b -> onClose()));
        CompoundTag entry = readingEntry();
        if (entry != null) {
            int pages = Math.max(1, entry.getList("pages", Tag.TAG_STRING).size());
            page = Mth.clamp(page, 0, pages - 1);
            int bx = left + FLOOR_W + 22;
            addRenderableWidget(new CrystalButton(bx, top + H - 22, 56, 14, Component.translatable("screen.amemaru.record_book.back"), b -> {
                reading = null;
                rebuildWidgets();
            }));
            if (pages > 1) {
                CrystalButton prev = addRenderableWidget(new CrystalButton(left + W - 76, top + H - 22, 20, 14, Component.literal("<"), b -> {
                    page--;
                    rebuildWidgets();
                }));
                CrystalButton next = addRenderableWidget(new CrystalButton(left + W - 32, top + H - 22, 20, 14, Component.literal(">"), b -> {
                    page++;
                    rebuildWidgets();
                }));
                prev.active = page > 0;
                next.active = page < pages - 1;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int step = delta > 0 ? -1 : 1;
        if (mouseX < left + FLOOR_W + 16) {
            floorScroll = Mth.clamp(floorScroll + step, 0, Math.max(0, floors().size() - FLOOR_ROWS));
        } else if (reading == null) {
            entryScroll = Mth.clamp(entryScroll + step, 0, Math.max(0, entries().size() - ENTRY_ROWS));
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<Integer> floors = floors();
        int fx = left + 10;
        int fy = top + 40;
        if (mouseX >= fx && mouseX < fx + FLOOR_W && mouseY >= fy && mouseY < fy + FLOOR_ROWS * FLOOR_ROW) {
            int index = floorScroll + (int) ((mouseY - fy) / FLOOR_ROW);
            if (index >= 0 && index < floors.size()) {
                floor = floors.get(index);
                reading = null;
                entryScroll = 0;
                rebuildWidgets();
                return true;
            }
        }
        if (reading == null) {
            List<CompoundTag> entries = entries();
            int ex = left + FLOOR_W + 22;
            int ey = top + 40;
            if (mouseX >= ex && mouseX < left + W - 12 && mouseY >= ey && mouseY < ey + ENTRY_ROWS * ENTRY_H) {
                int index = entryScroll + (int) ((mouseY - ey) / ENTRY_H);
                if (index >= 0 && index < entries.size() && "read".equals(entries.get(index).getString("state"))) {
                    reading = entries.get(index).getString("id");
                    page = 0;
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + W, top + H);
        graphics.drawCenteredString(font, title, left + W / 2, top + 7, UiStyle.TEXT_HEADER);
        graphics.drawString(font, Component.translatable("screen.amemaru.record_book.percent", data.getLong("percent")), left + 14, top + 22,
                UiStyle.TEXT_GOLD, false);
        UiStyle.divider(graphics, left + 12, left + W - 12, top + 34);
        // 階
        int[] total = data.getIntArray("total");
        int[] found = data.getIntArray("found");
        List<Integer> floors = floors();
        int fx = left + 10;
        int fy = top + 40;
        for (int i = floorScroll; i < Math.min(floors.size(), floorScroll + FLOOR_ROWS); i++) {
            int f = floors.get(i);
            int ry = fy + (i - floorScroll) * FLOOR_ROW;
            boolean on = f == floor;
            if (on) {
                graphics.fill(fx, ry, fx + FLOOR_W, ry + FLOOR_ROW - 1, 0x603E5274);
            }
            String name = f == 0 ? Component.translatable("screen.amemaru.record_book.floor0").getString()
                    : Component.translatable("screen.amemaru.record_book.floor", f).getString();
            int have = f < found.length ? found[f] : 0;
            int all = f < total.length ? total[f] : 0;
            graphics.drawString(font, name, fx + 3, ry + 3, on ? UiStyle.TEXT_GOLD : UiStyle.TEXT, false);
            String count = have + "/" + all;
            graphics.drawString(font, count, fx + FLOOR_W - 3 - font.width(count), ry + 3, have >= all ? UiStyle.TEXT_GOOD : UiStyle.TEXT_DIM, false);
        }
        CompoundTag entry = readingEntry();
        if (entry != null) {
            renderReading(graphics, entry);
        } else {
            renderEntries(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderEntries(GuiGraphics graphics, int mouseX, int mouseY) {
        List<CompoundTag> entries = entries();
        int ex = left + FLOOR_W + 22;
        int ew = W - FLOOR_W - 34;
        int ey = top + 40;
        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.record_book.empty"), ex, ey + 4, UiStyle.TEXT_DIM, false);
        }
        for (int i = entryScroll; i < Math.min(entries.size(), entryScroll + ENTRY_ROWS); i++) {
            CompoundTag one = entries.get(i);
            int ry = ey + (i - entryScroll) * ENTRY_H;
            String state = one.getString("state");
            boolean hover = "read".equals(state) && mouseX >= ex && mouseX < ex + ew && mouseY >= ry && mouseY < ry + ENTRY_H - 3;
            int accent = "read".equals(state) ? 0xFFF2A8 : "unread".equals(state) ? 0x8FD3FF : 0x3E5274;
            UiStyle.card(graphics, ex, ry, ex + ew, ry + ENTRY_H - 3, accent, hover);
            switch (state) {
                case "read" -> {
                    graphics.drawString(font, UiStyle.clip(font, one.getString("title"), ew - 60), ex + 4, ry + 3, UiStyle.TEXT_GOLD, false);
                    Component category = Component.translatable("screen.amemaru.record_book.category." + one.getString("category"));
                    graphics.drawString(font, category, ex + ew - 4 - font.width(category), ry + 3, UiStyle.TEXT_HEADER, false);
                    String sub = Component.translatable("screen.amemaru.record_book.read_hint", one.getInt("day")).getString();
                    graphics.drawString(font, UiStyle.clip(font, sub, ew - 8), ex + 4, ry + 15, UiStyle.TEXT_DIM, false);
                }
                case "unread" -> {
                    graphics.drawString(font, Component.translatable("screen.amemaru.record_book.unread"), ex + 4, ry + 3, UiStyle.TEXT, false);
                    String sub = Component.translatable("screen.amemaru.record_book.unread_hint").getString();
                    graphics.drawString(font, UiStyle.clip(font, sub, ew - 8), ex + 4, ry + 15, UiStyle.TEXT_DIM, false);
                }
                default -> {
                    graphics.drawString(font, Component.translatable("screen.amemaru.record_book.missing"), ex + 4, ry + 3, 0x6E7891, false);
                    String hint = one.getString("hint");
                    graphics.drawString(font, UiStyle.clip(font, hint.isEmpty() ? "…" : hint, ew - 8), ex + 4, ry + 15, UiStyle.TEXT_DIM, false);
                }
            }
            if (!one.getString("titleHint").isEmpty() && hover) {
                graphics.renderTooltip(font, UiStyle.tooltipLines(font,
                        Component.translatable("screen.amemaru.record_book.title_hint", one.getString("titleHint")).getString(), 200), mouseX, mouseY);
            }
        }
        if (entries.size() > ENTRY_ROWS) {
            String more = (entryScroll + 1) + "-" + Math.min(entries.size(), entryScroll + ENTRY_ROWS) + "/" + entries.size();
            graphics.drawString(font, more, left + W - 14 - font.width(more), top + H - 14, UiStyle.TEXT_DIM, false);
        }
        graphics.drawString(font, Component.translatable("screen.amemaru.record_book.note"), ex, top + H - 14, UiStyle.TEXT_DIM, false);
    }

    private void renderReading(GuiGraphics graphics, CompoundTag entry) {
        int x = left + FLOOR_W + 22;
        int w = W - FLOOR_W - 34;
        int y = top + 40;
        graphics.drawString(font, UiStyle.clip(font, entry.getString("title"), w - 60), x, y, UiStyle.TEXT_GOLD, false);
        Component category = Component.translatable("screen.amemaru.record_book.category." + entry.getString("category"));
        graphics.drawString(font, category, x + w - font.width(category), y, UiStyle.TEXT_HEADER, false);
        y += 14;
        ListTag pages = entry.getList("pages", Tag.TAG_STRING);
        String text = pages.isEmpty() ? "" : pages.getString(Mth.clamp(page, 0, pages.size() - 1));
        graphics.fill(x - 2, y - 3, x + w + 2, y + 104, 0x40D9C9A0);
        UiStyle.wrapped(graphics, font, text, x + 2, y, w - 4, 0xFFF2E6C8, 10);
        y += 110;
        String comment = entry.getString("comment");
        if (!comment.isEmpty()) {
            String line = Component.translatable("screen.amemaru.record_book.comment", comment).getString();
            UiStyle.wrapped(graphics, font, line, x, y, w, UiStyle.TEXT_HEADER, 2);
        }
        if (pages.size() > 1) {
            String count = (page + 1) + "/" + pages.size();
            graphics.drawCenteredString(font, count, left + W - 44, top + H - 19, UiStyle.TEXT_DIM);
        }
    }
}

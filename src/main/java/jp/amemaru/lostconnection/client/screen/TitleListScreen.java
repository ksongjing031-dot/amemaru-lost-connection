package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.client.ClientGuild;
import jp.amemaru.lostconnection.client.TitleText;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 称号の一覧（Phase 6 その3。Phase 0-A 22章）。持っている称号から一つ選んで表示する（表示しない もできる）。
 * 持っていない称号は、条件と進み具合を見せる。隠し称号は、手に入れるまで「？？？」。限定称号は、取った人の名前を出す。
 */
public class TitleListScreen extends Screen {
    private static final int W = 380;
    private static final int H = 222;
    private static final String[] FIELDS = {"all", "combat", "craft", "trade", "social", "leisure", "story"};
    private static final int FIELD_W = 50;
    private static final int CARD_W = 175;
    private static final int CARD_H = 34;
    private static final int COLUMNS = 2;
    private static final int ROWS = 4;

    @Nullable
    private final Screen parent;
    private int left;
    private int top;
    private String field = "all";
    private int scroll;
    private int version = -1;
    private final List<CompoundTag> shown = new ArrayList<>();

    public TitleListScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.amemaru.titles.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        version = ClientGuild.version();
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        collect();
        if (!ClientGuild.displayed().isEmpty()) {
            addRenderableWidget(new CrystalButton(left + W - 80, top + 20, 66, 12, Component.translatable("screen.amemaru.titles.hide"),
                    b -> display("")));
        }
        addRenderableWidget(new CrystalButton(left + W - 20, top + 5, 14, 12, Component.literal("×"), b -> onClose()));
        if (parent != null) {
            addRenderableWidget(new CrystalButton(left + 12, top + H - 20, 48, 14, Component.translatable("screen.amemaru.titles.back"),
                    b -> onClose()));
        }
    }

    private void collect() {
        shown.clear();
        for (Tag tag : ClientGuild.catalog().getList("titles", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) tag;
            if ("all".equals(field) || field.equals(one.getString("field"))) {
                shown.add(one);
            }
        }
        shown.sort(Comparator.comparingInt((CompoundTag t) -> fieldIndex(t.getString("field")))
                .thenComparingInt(t -> t.getInt("order"))
                .thenComparing(t -> t.getString("id")));
        int maxScroll = Math.max(0, (shown.size() + COLUMNS - 1) / COLUMNS - ROWS);
        scroll = Mth.clamp(scroll, 0, maxScroll);
    }

    private static int fieldIndex(String field) {
        for (int i = 1; i < FIELDS.length; i++) {
            if (FIELDS[i].equals(field)) {
                return i;
            }
        }
        return FIELDS.length;
    }

    private void display(String id) {
        CompoundTag args = new CompoundTag();
        args.putString("id", id);
        ClientGuild.send("title_display", args);
    }

    @Override
    public void onClose() {
        if (minecraft != null && parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (version != ClientGuild.version()) {
            rebuildWidgets();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, (shown.size() + COLUMNS - 1) / COLUMNS - ROWS);
        scroll = Mth.clamp(scroll + (delta > 0 ? -1 : 1), 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < FIELDS.length; i++) {
            int fx = left + 12 + i * (FIELD_W + 2);
            if (mouseX >= fx && mouseX < fx + FIELD_W && mouseY >= top + 36 && mouseY < top + 49) {
                field = FIELDS[i];
                scroll = 0;
                collect();
                return true;
            }
        }
        CompoundTag card = cardAt(mouseX, mouseY);
        if (card != null) {
            String id = card.getString("id");
            if (ClientGuild.owned().containsKey(id)) {
                display(id.equals(ClientGuild.displayed()) ? "" : id);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Nullable
    private CompoundTag cardAt(double mouseX, double mouseY) {
        for (int i = 0; i < ROWS * COLUMNS; i++) {
            int index = scroll * COLUMNS + i;
            if (index >= shown.size()) {
                break;
            }
            int cx = left + 12 + (i % COLUMNS) * (CARD_W + 6);
            int cy = top + 54 + (i / COLUMNS) * (CARD_H + 4);
            if (mouseX >= cx && mouseX < cx + CARD_W && mouseY >= cy && mouseY < cy + CARD_H) {
                return shown.get(index);
            }
        }
        return null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + W, top + H);
        graphics.drawCenteredString(font, title, left + W / 2, top + 7, UiStyle.TEXT_HEADER);
        long now = Util.getMillis();
        Map<String, CompoundTag> owned = ClientGuild.owned();
        CompoundTag displayed = owned.get(ClientGuild.displayed());
        int total = ClientGuild.catalog().getList("titles", Tag.TAG_COMPOUND).size();
        graphics.drawString(font, Component.translatable("screen.amemaru.titles.count", owned.size(), total), left + 14, top + 22,
                UiStyle.TEXT, false);
        Component label = Component.translatable("screen.amemaru.titles.shown");
        int lx = left + 110;
        graphics.drawString(font, label, lx, top + 22, UiStyle.TEXT_DIM, false);
        if (displayed != null) {
            graphics.drawString(font, TitleText.of(displayed.getString("name"), displayed.getInt("color"), displayed.getString("kind"),
                    displayed.getBoolean("rainbow"), now, true), lx + font.width(label) + 2, top + 22, 0xFFFFFF, false);
        } else {
            graphics.drawString(font, Component.translatable("screen.amemaru.titles.none_shown"), lx + font.width(label) + 2, top + 22,
                    UiStyle.TEXT_DIM, false);
        }
        for (int i = 0; i < FIELDS.length; i++) {
            int fx = left + 12 + i * (FIELD_W + 2);
            boolean on = FIELDS[i].equals(field);
            UiStyle.card(graphics, fx, top + 36, fx + FIELD_W, top + 49, on ? 0x8FD3FF : 0x3E5274, on);
            graphics.drawCenteredString(font, Component.translatable("screen.amemaru.titles.field." + FIELDS[i]), fx + FIELD_W / 2, top + 39,
                    on ? UiStyle.TEXT_GOLD : UiStyle.TEXT_DIM);
        }
        CompoundTag counters = ClientGuild.self().getCompound("counters");
        for (int i = 0; i < ROWS * COLUMNS; i++) {
            int index = scroll * COLUMNS + i;
            if (index >= shown.size()) {
                break;
            }
            CompoundTag def = shown.get(index);
            CompoundTag mine = owned.get(def.getString("id"));
            int cx = left + 12 + (i % COLUMNS) * (CARD_W + 6);
            int cy = top + 54 + (i / COLUMNS) * (CARD_H + 4);
            boolean isShown = def.getString("id").equals(ClientGuild.displayed());
            String kind = def.getString("kind");
            int color = def.getInt("color");
            boolean rainbow = def.getBoolean("rainbow");
            UiStyle.card(graphics, cx, cy, cx + CARD_W, cy + CARD_H, mine != null ? TitleText.color(color, kind, rainbow, now) : 0x3E5274, isShown);
            String name = mine != null ? mine.getString("name") : def.getString("name");
            if (mine != null) {
                graphics.drawString(font, TitleText.of(name, color, kind, rainbow, now, true), cx + 4, cy + 3, 0xFFFFFF, false);
            } else {
                graphics.drawString(font, "【" + name + "】", cx + 4, cy + 3, 0x6E7891, false);
            }
            if (!"normal".equals(kind)) {
                Component badge = Component.translatable("screen.amemaru.titles.kind." + kind);
                graphics.drawString(font, badge, cx + CARD_W - 4 - font.width(badge), cy + 3, TitleText.color(color, kind, rainbow, now), false);
            } else if (def.getInt("tiers") > 1) {
                String tier = def.getInt("tier") + "/" + def.getInt("tiers");
                graphics.drawString(font, tier, cx + CARD_W - 4 - font.width(tier), cy + 3, UiStyle.TEXT_DIM, false);
            }
            String description = mine != null ? mine.getString("description") : def.getString("description");
            if (description.isEmpty()) {
                description = Component.translatable("screen.amemaru.titles.secret").getString();
            }
            graphics.drawString(font, UiStyle.clip(font, description, CARD_W - 8), cx + 4, cy + 13, mine != null ? UiStyle.TEXT : UiStyle.TEXT_DIM,
                    false);
            renderFoot(graphics, def, mine, counters, cx, cy + 23);
        }
        int rows = (shown.size() + COLUMNS - 1) / COLUMNS;
        if (rows > ROWS) {
            String more = (scroll + 1) + "-" + Math.min(rows, scroll + ROWS) + "/" + rows;
            graphics.drawString(font, more, left + W - 14 - font.width(more), top + H - 16, UiStyle.TEXT_DIM, false);
        }
        graphics.drawString(font, Component.translatable("screen.amemaru.titles.click"), left + (parent != null ? 66 : 14), top + H - 16,
                UiStyle.TEXT_DIM, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        CompoundTag hovered = cardAt(mouseX, mouseY);
        if (hovered != null) {
            renderHover(graphics, hovered, owned.get(hovered.getString("id")), mouseX, mouseY);
        }
    }

    /** カードの下の行：取った日・進み具合・取った人（限定）・効果。 */
    private void renderFoot(GuiGraphics graphics, CompoundTag def, @Nullable CompoundTag mine, CompoundTag counters, int x, int y) {
        String kind = def.getString("kind");
        if ("limited".equals(kind)) {
            ListTag holders = def.getList("holders", Tag.TAG_STRING);
            String text = holders.isEmpty() ? Component.translatable("screen.amemaru.titles.limited_open").getString()
                    : Component.translatable("screen.amemaru.titles.limited_by", joined(holders)).getString();
            graphics.drawString(font, UiStyle.clip(font, text, CARD_W - 8), x + 4, y, holders.isEmpty() ? UiStyle.TEXT_GOLD : UiStyle.TEXT_DIM,
                    false);
            return;
        }
        if (mine != null) {
            String text = Component.translatable("screen.amemaru.titles.got_day", mine.getInt("day")).getString();
            if (!def.getString("effect").isEmpty()) {
                text += "  " + Component.translatable("screen.amemaru.titles.effect." + def.getString("effect"), def.getInt("effectAmount"))
                        .getString();
            }
            graphics.drawString(font, UiStyle.clip(font, text, CARD_W - 8), x + 4, y, UiStyle.TEXT_GOOD, false);
            return;
        }
        if ("counter".equals(def.getString("condType"))) {
            long need = Math.max(1, def.getLong("condAmount"));
            long have = Math.min(need, counters.getLong(def.getString("condKey")));
            String progress = have + "/" + need;
            int barW = CARD_W - 16 - font.width(progress);
            graphics.fill(x + 4, y + 2, x + 4 + barW, y + 6, 0xFF1B2030);
            graphics.fill(x + 4, y + 2, x + 4 + Math.round(barW * (have / (float) need)), y + 6, 0xFF8FD3FF);
            graphics.drawString(font, progress, x + CARD_W - 4 - font.width(progress), y, UiStyle.TEXT_DIM, false);
            return;
        }
        if ("affinity".equals(def.getString("condType"))) {
            int stage = 0;
            String npc = def.getString("condTarget");
            for (Tag tag : ClientGuild.self().getList("bonds", Tag.TAG_COMPOUND)) {
                CompoundTag bond = (CompoundTag) tag;
                if (bond.getString("id").equals(npc)) {
                    stage = bond.getInt("stage");
                }
            }
            String text = Component.translatable("screen.amemaru.titles.affinity", stage, def.getLong("condAmount")).getString();
            graphics.drawString(font, text, x + 4, y, UiStyle.TEXT_DIM, false);
        }
    }

    private static String joined(ListTag names) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            list.add(names.getString(i));
        }
        return String.join("・", list);
    }

    private void renderHover(GuiGraphics graphics, CompoundTag def, @Nullable CompoundTag mine, int mouseX, int mouseY) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        String name = mine != null ? mine.getString("name") : def.getString("name");
        lines.add(Component.literal("【" + name + "】").getVisualOrderText());
        String description = mine != null ? mine.getString("description") : def.getString("description");
        if (!description.isEmpty()) {
            lines.addAll(UiStyle.tooltipLines(font, description, 200));
        }
        if ("limited".equals(def.getString("kind"))) {
            ListTag holders = def.getList("holders", Tag.TAG_STRING);
            if (!holders.isEmpty()) {
                lines.addAll(UiStyle.tooltipLines(font, Component.translatable("screen.amemaru.titles.limited_by", joined(holders)).getString(), 200));
            }
        }
        if (mine != null) {
            lines.add(Component.translatable(mine.getString("id").equals(ClientGuild.displayed()) ? "screen.amemaru.titles.click_hide"
                    : "screen.amemaru.titles.click_show").getVisualOrderText());
        }
        graphics.renderTooltip(font, lines, mouseX, mouseY);
    }
}

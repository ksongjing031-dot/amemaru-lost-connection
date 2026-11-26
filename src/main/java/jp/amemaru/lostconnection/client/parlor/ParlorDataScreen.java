package jp.amemaru.lostconnection.client.parlor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.ParlorC2S;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * 島のデータの画面（11月27日【補完】。本物の店のデータ公開と同じ）。島の台ごとに、今日の回転数・大当たり（スロットは BIG・REG・AT）・
 * 今のハマり・差枚の流れ（スロット）・前の日と前の前の日のまとめ。台を選ぶ手がかり（設定と釘そのものは見せない）。
 */
public class ParlorDataScreen extends Screen {
    private static final int W = 420;
    private static final int H = 246;
    private static final int ROW = 17;
    private static final int ROWS = 12;

    private CompoundTag data;
    private int left;
    private int top;
    private int selected;

    public ParlorDataScreen(CompoundTag data) {
        super(Component.translatable("screen.amemaru.parlor.data_title"));
        this.data = data;
    }

    public void update(CompoundTag next) {
        data = next;
        selected = 0;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        ListTag islands = data.getList("islands", Tag.TAG_COMPOUND);
        int x = left + 6;
        for (int i = 0; i < islands.size(); i++) {
            CompoundTag one = islands.getCompound(i);
            String id = one.getString("id");
            Component name = Component.literal(("slot".equals(one.getString("kind")) ? "S " : "P ") + one.getString("name"));
            int w = Math.min(100, font.width(name) + 10);
            Button b = Button.builder(name, btn -> {
                CompoundTag args = new CompoundTag();
                args.putString("island", id);
                ModNetwork.sendToServer(new ParlorC2S("data", args));
            }).bounds(x, top + 18, w, 14).build();
            b.active = !id.equals(data.getString("island"));
            addRenderableWidget(b);
            x += w + 2;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        ListTag machines = data.getList("machines", Tag.TAG_COMPOUND);
        int y0 = top + 50;
        for (int i = 0; i < Math.min(ROWS, machines.size()); i++) {
            if (mx >= left + 4 && mx < left + 236 && my >= y0 + i * ROW && my < y0 + (i + 1) * ROW) {
                selected = i;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        ParlorDraw.bevel(g, left, top, W, H, 0x1A1624);
        g.drawString(font, Component.translatable("screen.amemaru.parlor.data_title"), left + 6, top + 5, 0xF2C84A, false);
        CompoundTag rules = data.getCompound("rules");
        String day = rules.getString("day");
        String head = day + (rules.getBoolean("event") ? "  " + Component.translatable("screen.amemaru.parlor.event_day").getString() : "");
        g.drawString(font, head, left + W - 6 - font.width(head), top + 5, rules.getBoolean("event")
                ? ParlorDraw.rainbow(System.currentTimeMillis(), 2400, 0) : 0xA8A0C0, false);
        ListTag machines = data.getList("machines", Tag.TAG_COMPOUND);
        boolean slot = !machines.isEmpty() && "slot".equals(machines.getCompound(0).getString("kind"));
        boolean at = false;
        // 見出し
        int y0 = top + 38;
        g.drawString(font, Component.translatable("screen.amemaru.parlor.col_no"), left + 8, y0, 0x9A90B0, false);
        if (slot) {
            g.drawString(font, Component.translatable("screen.amemaru.parlor.col_games"), left + 40, y0, 0x9A90B0, false);
            g.drawString(font, "BIG/AT", left + 92, y0, 0x9A90B0, false);
            g.drawString(font, "REG/CZ", left + 132, y0, 0x9A90B0, false);
        } else {
            g.drawString(font, Component.translatable("screen.amemaru.parlor.col_spins"), left + 40, y0, 0x9A90B0, false);
            g.drawString(font, Component.translatable("screen.amemaru.parlor.col_hits"), left + 92, y0, 0x9A90B0, false);
            g.drawString(font, Component.translatable("screen.amemaru.parlor.col_first"), left + 132, y0, 0x9A90B0, false);
        }
        g.drawString(font, Component.translatable("screen.amemaru.parlor.col_since"), left + 178, y0, 0x9A90B0, false);
        for (int i = 0; i < Math.min(ROWS, machines.size()); i++) {
            CompoundTag m = machines.getCompound(i);
            JsonObject r = json(m);
            int y = top + 50 + i * ROW;
            boolean sel = i == selected;
            g.fill(left + 4, y, left + 236, y + ROW - 1, sel ? 0xFF3A2E52 : (i % 2 == 0 ? 0xFF221C2E : 0xFF1C1828));
            int textY = y + 4;
            g.drawString(font, Integer.toString(m.getInt("no")), left + 8, textY, m.getBoolean("busy") ? 0xFF8080 : 0xFFFFFF, false);
            if ("slot".equals(m.getString("kind"))) {
                at = r.has("at") && (r.get("at").getAsInt() > 0 || r.get("cz").getAsInt() > 0);
                g.drawString(font, num(r, "games"), left + 40, textY, 0xFFFFFF, false);
                g.drawString(font, at ? num(r, "at") : num(r, "big"), left + 96, textY, 0xFF7080, false);
                g.drawString(font, at ? num(r, "cz") : num(r, "reg"), left + 136, textY, 0x80A8FF, false);
            } else {
                g.drawString(font, num(r, "spins"), left + 40, textY, 0xFFFFFF, false);
                g.drawString(font, num(r, "hits"), left + 96, textY, 0xFF7080, false);
                g.drawString(font, num(r, "firstHits"), left + 136, textY, 0xFFB070, false);
            }
            g.drawString(font, num(r, "since"), left + 182, textY, 0xFFE070, false);
            if (m.getBoolean("busy")) {
                g.drawString(font, Component.translatable("screen.amemaru.parlor.busy"), left + 212, textY, 0xFF8080, false);
            }
        }
        // 選んだ台の詳しいデータ
        if (selected < machines.size()) {
            renderDetail(g, machines.getCompound(selected), left + 242, top + 38);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    private void renderDetail(GuiGraphics g, CompoundTag m, int x, int y) {
        JsonObject r = json(m);
        int w = W - 248;
        ParlorDraw.inset(g, x, y, w, H - 44, 0x14101C);
        g.drawString(font, Component.translatable("screen.amemaru.parlor.machine_no", m.getInt("no")), x + 4, y + 4, 0xF2C84A, false);
        g.drawString(font, m.getString("name"), x + 4, y + 14, 0xFFFFFF, false);
        boolean slot = "slot".equals(m.getString("kind"));
        int gy = y + 28;
        if (slot) {
            g.drawString(font, Component.translatable("screen.amemaru.parlor.slump"), x + 4, gy, 0xA8A0C0, false);
            ParlorDraw.inset(g, x + 4, gy + 10, w - 8, 52, 0x0A0810);
            graph(g, x + 4, gy + 10, w - 8, 52, r.has("slump") ? r.getAsJsonArray("slump") : new JsonArray());
            gy += 68;
        }
        // 当たりの履歴
        g.drawString(font, Component.translatable("screen.amemaru.parlor.history"), x + 4, gy, 0xA8A0C0, false);
        JsonArray hits = r.has("hitList") ? r.getAsJsonArray("hitList") : new JsonArray();
        int shownRows = slot ? 6 : 11;
        for (int i = 0; i < Math.min(shownRows, hits.size()); i++) {
            JsonObject h = hits.get(i).getAsJsonObject();
            String kind = h.get("kind").getAsString();
            String line;
            int c;
            if (slot) {
                line = kind.toUpperCase(Locale.ROOT) + "  " + h.get("at").getAsInt() + "G";
                c = "big".equals(kind) ? 0xFF7080 : "at".equals(kind) ? 0xFFB060 : 0x80A8FF;
            } else {
                int chain = h.get("chain").getAsInt();
                line = (chain <= 1 ? "◆" : " " + chain) + "  " + h.get("at").getAsInt()
                        + Component.translatable("screen.amemaru.parlor.spins_unit").getString() + "  " + h.get("balls").getAsInt();
                c = chain <= 1 ? 0xFFB070 : 0xFF7080;
            }
            g.drawString(font, line, x + 6, gy + 10 + i * 9, c, false);
        }
        // 前の日
        int hy = y + H - 44 - 40;
        g.drawString(font, Component.translatable("screen.amemaru.parlor.past"), x + 4, hy, 0xA8A0C0, false);
        JsonArray history = r.has("history") ? r.getAsJsonArray("history") : new JsonArray();
        for (int i = 0; i < Math.min(3, history.size()); i++) {
            JsonObject d = history.get(i).getAsJsonObject();
            String day = d.get("day").getAsString();
            String text = day.substring(Math.max(0, day.length() - 5)) + "  " + (slot
                    ? d.get("games").getAsInt() + "G  B" + d.get("a").getAsInt() + " R" + d.get("b").getAsInt()
                    : d.get("games").getAsInt() + Component.translatable("screen.amemaru.parlor.spins_unit").getString() + "  "
                    + d.get("a").getAsInt() + "(" + d.get("b").getAsInt() + ")");
            g.drawString(font, text, x + 6, hy + 10 + i * 9, 0xD0C8E0, false);
        }
    }

    /** 差枚の流れのグラフ（0 の線と、プラスは赤・マイナスは青）。 */
    public static void graph(GuiGraphics g, int x, int y, int w, int h, JsonArray points) {
        int mid = y + h / 2;
        g.fill(x, mid, x + w, mid + 1, 0x60FFFFFF);
        if (points.size() < 2) {
            return;
        }
        int max = 500;
        for (int i = 0; i < points.size(); i++) {
            max = Math.max(max, Math.abs(points.get(i).getAsInt()));
        }
        float px = -1;
        float py = 0;
        for (int i = 0; i < points.size(); i++) {
            int v = points.get(i).getAsInt();
            float qx = x + 1 + (w - 2) * i / (float) Math.max(1, points.size() - 1);
            float qy = mid - (h / 2.0F - 2) * v / max;
            if (px >= 0) {
                ParlorDraw.line(g, px, py, qx, qy, v >= 0 ? 0xFFFF5060 : 0xFF50A0FF);
            }
            px = qx;
            py = qy;
        }
    }

    private static JsonObject json(CompoundTag m) {
        try {
            return JsonParser.parseString(m.getString("json")).getAsJsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private static String num(JsonObject r, String key) {
        return r.has(key) ? String.format(Locale.ROOT, "%,d", r.get(key).getAsLong()) : "0";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package jp.amemaru.lostconnection.client.parlor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.ParlorC2S;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * 遊技台の画面の土台（11月27日【補完】）。左に台（{@link SlotScreen}・{@link PachinkoScreen} が描く）、右に持ち玉・持ちメダルと
 * チップ・玉やメダルを借りる・チップに戻す・島のデータ・台を離れる、と今日のデータカウンター。
 */
public abstract class MachineScreen extends Screen {
    protected static final int W = 404;
    protected static final int H = 246;
    protected static final int PANEL_X = 256;

    protected final int no;
    protected final String kind;
    protected final String modelId;
    protected final JsonObject model;
    protected final String island;
    protected final String islandId;
    protected final boolean admin;
    protected CompoundTag rules;
    protected CompoundTag state;
    protected CompoundTag record;
    protected JsonObject recordJson = new JsonObject();
    protected int left;
    protected int top;
    protected final int frameColor;
    protected final int accent;
    protected final int screenColor;
    protected final int glowColor;
    protected boolean closedByServer;

    protected MachineScreen(CompoundTag open) {
        super(Component.translatable("screen.amemaru.parlor.title"));
        this.no = open.getInt("no");
        this.kind = open.getString("kind");
        this.modelId = open.getString("modelId");
        this.model = JsonParser.parseString(open.getString("model")).getAsJsonObject();
        this.island = open.getString("island");
        this.islandId = open.getString("islandId");
        this.admin = open.getBoolean("admin");
        this.rules = open.getCompound("rules");
        this.state = open.getCompound("state");
        setRecord(open.getCompound("record"));
        JsonObject theme = model.has("theme") ? model.getAsJsonObject("theme") : new JsonObject();
        frameColor = ParlorDraw.parse(theme.has("frame") ? theme.get("frame").getAsString() : "#303040", 0xFF303040);
        accent = ParlorDraw.parse(theme.has("accent") ? theme.get("accent").getAsString() : "#F0C850", 0xFFF0C850);
        screenColor = ParlorDraw.parse(theme.has("screen") ? theme.get("screen").getAsString() : "#101018", 0xFF101018);
        glowColor = ParlorDraw.parse(theme.has("glow") ? theme.get("glow").getAsString() : "#80C0FF", 0xFF80C0FF);
    }

    public int no() {
        return no;
    }

    protected String modelName() {
        return model.has("name") ? model.get("name").getAsString() : modelId;
    }

    protected void setRecord(CompoundTag rec) {
        record = rec;
        try {
            recordJson = JsonParser.parseString(rec.getString("json")).getAsJsonObject();
        } catch (RuntimeException e) {
            recordJson = new JsonObject();
        }
    }

    protected int rec(String key) {
        return recordJson.has(key) ? recordJson.get(key).getAsInt() : 0;
    }

    /** サーバーからの台の様子（データカウンターが変わったときは、それも入っている）。 */
    public void onState(CompoundTag next) {
        state = next;
        if (next.contains("record")) {
            setRecord(next.getCompound("record"));
        }
    }

    public void onRecord(CompoundTag next) {
        setRecord(next);
    }

    /** サーバーから閉じられた（台を離れた）。 */
    public void closeFromServer() {
        closedByServer = true;
        onClose();
    }

    protected void send(String action, CompoundTag args) {
        args.putInt("no", no);
        ModNetwork.sendToServer(new ParlorC2S(action, args));
    }

    protected void send(String action) {
        send(action, new CompoundTag());
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int x = left + PANEL_X + 6;
        int y = top + 64;
        int[] lend = rules.getIntArray("lend");
        int bw = (W - PANEL_X - 12 - (lend.length - 1) * 2) / Math.max(1, lend.length);
        for (int i = 0; i < lend.length; i++) {
            int chips = lend[i];
            addRenderableWidget(Button.builder(Component.translatable("screen.amemaru.parlor.lend", chips), b -> {
                CompoundTag args = new CompoundTag();
                args.putInt("chips", chips);
                send("lend", args);
            }).bounds(x + i * (bw + 2), y, bw, 16).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.amemaru.parlor.cashout"), b -> send("cashout"))
                .bounds(x, y + 19, (W - PANEL_X - 14) / 2, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.amemaru.parlor.data"), b ->
                ModNetwork.sendToServer(new ParlorC2S("data", islandArgs()))).bounds(x + (W - PANEL_X - 14) / 2 + 2, y + 19, (W - PANEL_X - 14) / 2, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.amemaru.parlor.leave"), b -> send("leave"))
                .bounds(x, top + H - 22, W - PANEL_X - 12, 16).build());
        initMachine();
    }

    private CompoundTag islandArgs() {
        CompoundTag args = new CompoundTag();
        args.putString("island", islandId);
        return args;
    }

    /** 台ごとのボタン（レバー・ハンドル）。 */
    protected abstract void initMachine();

    /** 台（左）を描く。 */
    protected abstract void renderMachine(GuiGraphics g, int mouseX, int mouseY, float partial, long now);

    /** 右の欄の、台ごとのデータ。 */
    protected abstract void renderData(GuiGraphics g, int x, int y, long now);

    /** 持ち玉か持ちメダル（数と単位）。 */
    protected abstract String holdingText();

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        long now = System.currentTimeMillis();
        // 台のまわり
        ParlorDraw.bevel(g, left, top, W, H, 0x1A1420);
        renderMachine(g, mouseX, mouseY, partial, now);
        // 右の欄
        int x = left + PANEL_X;
        ParlorDraw.bevel(g, x, top + 2, W - PANEL_X - 2, H - 4, 0x221C2A);
        g.drawString(font, Component.translatable("screen.amemaru.parlor.machine_no", no), x + 6, top + 7, accent, false);
        g.drawString(font, modelName(), x + 6, top + 18, 0xFFFFFF, false);
        g.drawString(font, island, x + 6, top + 28, 0x9A90B0, false);
        g.drawString(font, holdingText(), x + 6, top + 42, 0xFFE070, false);
        g.drawString(font, Component.translatable("screen.amemaru.parlor.chips", state.getLong("chips")), x + 6, top + 52, 0xC8C0E0, false);
        // 下の3つのボタン（チップで玉・メダルを借りる）の見出し。ボタンには枚数だけを書く
        Component lendCaption = Component.translatable("screen.amemaru.parlor.lend_caption");
        g.drawString(font, lendCaption, x + W - PANEL_X - 8 - font.width(lendCaption), top + 52, 0x9A90B0, false);
        if (rules.getBoolean("event")) {
            int c = ParlorDraw.rainbow(now, 2400, 0);
            g.drawString(font, Component.translatable("screen.amemaru.parlor.event_day"), x + W - PANEL_X - 8
                    - font.width(Component.translatable("screen.amemaru.parlor.event_day")), top + 7, c, false);
        }
        renderData(g, x + 6, top + 104, now);
        super.render(g, mouseX, mouseY, partial);
    }

    /** データカウンターの1行（名前と数）。 */
    protected void dataLine(GuiGraphics g, int x, int y, Component name, String value, int color) {
        g.drawString(font, name, x, y, 0xA8A0C0, false);
        g.drawString(font, value, x + W - PANEL_X - 14 - font.width(value), y, color, false);
    }

    protected static String num(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 画面を閉じた・ほかの画面に替わった：サーバーに知らせる（しばらくはその人の台のまま）。 */
    @Override
    public void removed() {
        if (!closedByServer) {
            send("close");
        }
        super.removed();
    }
}

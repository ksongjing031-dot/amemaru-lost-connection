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
 * 縁帳（Phase 6 その3。Phase 0-A 15章【提案】「組んだ人・取引した人・助けてくれた人の一覧を見られる。相手ごとに一緒にしたこと」）。
 * 最近会った順。フリマ・露店・人から人への受け渡しの取引も「取引」に数える（Phase 6 その1 で約束した、対面の取引のつながり）。
 */
public class ConnectionsScreen extends Screen {
    private static final int W = 340;
    private static final int H = 210;
    private static final int ROW = 26;
    private static final int ROWS = 6;

    private final CompoundTag data;
    @Nullable
    private final Screen parent;
    private int left;
    private int top;
    private int scroll;

    public ConnectionsScreen(CompoundTag data, @Nullable Screen parent) {
        super(Component.translatable("screen.amemaru.connections.title"));
        this.data = data;
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        addRenderableWidget(new CrystalButton(left + W - 20, top + 5, 14, 12, Component.literal("×"), b -> onClose()));
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int size = data.getList("people", Tag.TAG_COMPOUND).size();
        scroll = Mth.clamp(scroll + (delta > 0 ? -1 : 1), 0, Math.max(0, size - ROWS));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + W, top + H);
        graphics.drawCenteredString(font, title, left + W / 2, top + 7, UiStyle.TEXT_HEADER);
        graphics.drawString(font, Component.translatable("screen.amemaru.connections.total", data.getInt("total")), left + 14, top + 20,
                UiStyle.TEXT_DIM, false);
        ListTag people = data.getList("people", Tag.TAG_COMPOUND);
        int y = top + 34;
        if (people.isEmpty()) {
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.connections.none").getString(), left + 14, y + 4, W - 28,
                    UiStyle.TEXT_DIM, 3);
        }
        for (int i = scroll; i < Math.min(people.size(), scroll + ROWS); i++) {
            CompoundTag one = people.getCompound(i);
            UiStyle.card(graphics, left + 12, y, left + W - 12, y + ROW - 3, one.getBoolean("online") ? 0x9CFFB0 : 0x3E5274, false);
            graphics.drawString(font, (one.getBoolean("online") ? "● " : "○ ") + one.getString("name"), left + 16, y + 3,
                    one.getBoolean("online") ? UiStyle.TEXT_GOOD : UiStyle.TEXT, false);
            String days = Component.translatable("screen.amemaru.connections.days", one.getInt("first"), one.getInt("last")).getString();
            graphics.drawString(font, days, left + W - 16 - font.width(days), y + 3, UiStyle.TEXT_DIM, false);
            List<String> together = new ArrayList<>();
            add(together, "party", one.getInt("party"));
            add(together, "trades", one.getInt("trades"));
            add(together, "revived", one.getInt("revived"));
            add(together, "revived_by", one.getInt("revivedBy"));
            add(together, "commissions", one.getInt("commissions"));
            add(together, "duels", one.getInt("duels"));
            graphics.drawString(font, UiStyle.clip(font, String.join("・", together), W - 36), left + 16, y + 13, UiStyle.TEXT_HEADER, false);
            y += ROW;
        }
        if (people.size() > ROWS) {
            String more = (scroll + 1) + "-" + Math.min(people.size(), scroll + ROWS) + "/" + people.size();
            graphics.drawString(font, more, left + W - 14 - font.width(more), top + H - 14, UiStyle.TEXT_DIM, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static void add(List<String> list, String key, int count) {
        if (count > 0) {
            list.add(Component.translatable("screen.amemaru.connections." + key, count).getString());
        }
    }
}

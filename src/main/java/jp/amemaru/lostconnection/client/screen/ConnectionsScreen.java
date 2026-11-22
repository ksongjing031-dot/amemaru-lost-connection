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
 * Phase 7 その1：相手ごとの「結び札」のボタンで、ありがとうの札を贈る（1日3枚・同じ人へは1日1枚）。
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

    /** 結び札のボタン（その行の右下）。 */
    private int[] thanksButton(int row) {
        int y = top + 34 + row * ROW;
        return new int[]{left + W - 16 - 54, y + 12, left + W - 16, y + 22};
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            ListTag people = data.getList("people", Tag.TAG_COMPOUND);
            for (int i = scroll; i < Math.min(people.size(), scroll + ROWS); i++) {
                CompoundTag one = people.getCompound(i);
                int[] b = thanksButton(i - scroll);
                if (mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3] && sendThanks(one)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 結び札を贈る（贈れる人なら、サーバーへ送って true）。 */
    private boolean sendThanks(CompoundTag one) {
        if (!one.hasUUID("id") || one.getBoolean("thanked") || data.getInt("thanksLeft") <= 0) {
            return false;
        }
        CompoundTag args = new CompoundTag();
        args.putUUID("to", one.getUUID("id"));
        args.putBoolean("refresh", true);
        jp.amemaru.lostconnection.network.ModNetwork.sendToServer(new jp.amemaru.lostconnection.network.c2s.ConnectionActionC2S("thanks", args));
        return true;
    }

    /** 自動の確認の写真：一覧の index 番目の人に結び札を贈る（ボタンを押したのと同じ）。 */
    public void ciThanks(int index) {
        ListTag people = data.getList("people", Tag.TAG_COMPOUND);
        if (index >= 0 && index < people.size()) {
            sendThanks(people.getCompound(index));
        }
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
        if (data.contains("thanksLeft")) {
            Component thanksText = Component.translatable("screen.amemaru.connections.thanks_left", data.getInt("thanksLeft"));
            graphics.drawString(font, thanksText, left + W - 26 - font.width(thanksText), top + 20, 0xFFE3A6FF, false);
        }
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
            graphics.drawString(font, UiStyle.clip(font, String.join("・", together), W - 36 - 60), left + 16, y + 13, UiStyle.TEXT_HEADER, false);
            if (one.hasUUID("id")) {
                int[] b = thanksButton(i - scroll);
                boolean done = one.getBoolean("thanked");
                boolean can = !done && data.getInt("thanksLeft") > 0;
                boolean hover = can && mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3];
                graphics.fill(b[0], b[1], b[2], b[3], hover ? 0xFF7A4FA8 : can ? 0xFF4E3570 : 0xFF2A2F40);
                Component label = Component.translatable(done ? "screen.amemaru.connections.thanked" : "screen.amemaru.connections.thanks");
                graphics.drawCenteredString(font, label, (b[0] + b[2]) / 2, b[1] + 1, can || done ? 0xFFF1E0FF : UiStyle.TEXT_DIM);
            }
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

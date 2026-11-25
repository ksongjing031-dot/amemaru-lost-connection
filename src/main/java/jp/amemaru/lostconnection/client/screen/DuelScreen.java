package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.client.ClientSocial;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * 決闘の画面（Phase 6 その4。11月22日に決定）：届いた申し込み（受ける・断る）、観戦できる決闘（観戦する）、申し込む相手
 * （申し込む）、自分の戦績。パーティの画面の「決闘」か、{@code /amemaru duel} で開く。
 */
public class DuelScreen extends Screen {
    private static final int W = 320;
    private static final int H = 222;
    private static final int ROW = 13;
    private static final int PEOPLE_ROWS = 5;

    @Nullable
    private final Screen parent;
    private int left;
    private int top;
    private int seen = -1;
    private int page;

    public DuelScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.amemaru.duel.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        seen = ClientSocial.version();
        CompoundTag data = ClientSocial.duel();
        addRenderableWidget(new CrystalButton(left + W - 20, top + 5, 14, 12, Component.literal("×"), b -> onClose()));
        // 届いた申し込み
        int y = top + 52;
        ListTag incoming = data.getList("incoming", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(2, incoming.size()); i++) {
            String name = incoming.getCompound(i).getString("name");
            addRenderableWidget(new CrystalButton(left + W - 100, y + i * ROW - 1, 42, 12, Component.translatable("screen.amemaru.duel.accept"),
                    b -> act("duel_accept", name)));
            addRenderableWidget(new CrystalButton(left + W - 54, y + i * ROW - 1, 42, 12, Component.translatable("screen.amemaru.duel.decline"),
                    b -> act("duel_decline", name)));
        }
        // 観戦できる決闘
        y = top + 94;
        ListTag duels = data.getList("duels", Tag.TAG_COMPOUND);
        boolean free = !data.getBoolean("fighting") && !data.getBoolean("spectating");
        for (int i = 0; i < Math.min(2, duels.size()); i++) {
            CompoundTag one = duels.getCompound(i);
            int id = one.getInt("id");
            CrystalButton watch = addRenderableWidget(new CrystalButton(left + W - 72, y + i * ROW - 1, 60, 12,
                    Component.translatable("screen.amemaru.duel.watch"), b -> {
                        CompoundTag args = new CompoundTag();
                        args.putInt("id", id);
                        ClientSocial.send("duel_watch", args);
                    }));
            watch.active = free && !"END".equals(one.getString("phase"));
        }
        // 申し込む相手
        y = top + 136;
        ListTag people = data.getList("people", Tag.TAG_COMPOUND);
        int pages = Math.max(1, (people.size() + PEOPLE_ROWS - 1) / PEOPLE_ROWS);
        page = Math.max(0, Math.min(pages - 1, page));
        for (int i = 0; i < PEOPLE_ROWS; i++) {
            int index = page * PEOPLE_ROWS + i;
            if (index >= people.size()) {
                break;
            }
            CompoundTag one = people.getCompound(index);
            String name = one.getString("name");
            CrystalButton challenge = addRenderableWidget(new CrystalButton(left + W - 72, y + i * ROW - 1, 60, 12,
                    Component.translatable("screen.amemaru.duel.challenge"), b -> act("duel_challenge", name)));
            challenge.active = free && data.getString("unavailable").isEmpty() && !one.getBoolean("busy") && data.getBoolean("enabled");
        }
        if (pages > 1) {
            addRenderableWidget(new CrystalButton(left + 14, top + H - 22, 18, 14, Component.literal("<"), b -> {
                page = Math.max(0, page - 1);
                rebuildWidgets();
            }));
            addRenderableWidget(new CrystalButton(left + 36, top + H - 22, 18, 14, Component.literal(">"), b -> {
                page = page + 1;
                rebuildWidgets();
            }));
        }
        if (data.getBoolean("spectating")) {
            addRenderableWidget(new CrystalButton(left + W - 112, top + H - 22, 100, 16, Component.translatable("screen.amemaru.duel.leave"),
                    b -> ClientSocial.send("duel_leave")));
        } else if (data.getBoolean("fighting")) {
            addRenderableWidget(new CrystalButton(left + W - 112, top + H - 22, 100, 16, Component.translatable("screen.amemaru.duel.surrender"),
                    b -> ClientSocial.send("duel_leave")));
        }
    }

    private static void act(String action, String name) {
        CompoundTag args = new CompoundTag();
        args.putString("name", name);
        ClientSocial.send(action, args);
    }

    @Override
    public void tick() {
        if (seen != ClientSocial.version()) {
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + W, top + H);
        CompoundTag data = ClientSocial.duel();
        graphics.drawString(font, title, left + 12, top + 8, UiStyle.TEXT_HEADER, true);
        Component record = Component.translatable("screen.amemaru.duel.record", data.getLong("wins"), data.getLong("losses"), data.getLong("draws"));
        graphics.drawString(font, record, left + W - 28 - font.width(record), top + 8, UiStyle.TEXT_GOLD, true);
        UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.duel.help", data.getInt("seconds") / 60).getString(), left + 12,
                top + 20, W - 24, UiStyle.TEXT_DIM, 2);
        // 届いた申し込み
        int y = top + 52;
        graphics.drawString(font, Component.translatable("screen.amemaru.duel.incoming"), left + 12, y - 11, UiStyle.TEXT_HEADER, true);
        ListTag incoming = data.getList("incoming", Tag.TAG_COMPOUND);
        ListTag outgoing = data.getList("outgoing", Tag.TAG_COMPOUND);
        if (incoming.isEmpty() && outgoing.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.duel.no_incoming"), left + 16, y + 1, UiStyle.TEXT_DIM, false);
        }
        for (int i = 0; i < Math.min(2, incoming.size()); i++) {
            CompoundTag one = incoming.getCompound(i);
            graphics.drawString(font, Component.translatable("screen.amemaru.duel.from", one.getString("name"), (one.getLong("left") + 19) / 20),
                    left + 16, y + i * ROW + 1, UiStyle.TEXT_GOLD, false);
        }
        if (incoming.isEmpty() && !outgoing.isEmpty()) {
            CompoundTag one = outgoing.getCompound(0);
            graphics.drawString(font, Component.translatable("screen.amemaru.duel.to", one.getString("name"), (one.getLong("left") + 19) / 20),
                    left + 16, y + 1, UiStyle.TEXT_DIM, false);
        }
        // 観戦できる決闘
        y = top + 94;
        graphics.drawString(font, Component.translatable("screen.amemaru.duel.duels"), left + 12, y - 11, UiStyle.TEXT_HEADER, true);
        ListTag duels = data.getList("duels", Tag.TAG_COMPOUND);
        if (duels.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.duel.no_duels"), left + 16, y + 1, UiStyle.TEXT_DIM, false);
        }
        for (int i = 0; i < Math.min(2, duels.size()); i++) {
            CompoundTag one = duels.getCompound(i);
            long left = one.getLong("left");
            String state = switch (one.getString("phase")) {
                case "FIGHT" -> String.format(Locale.ROOT, "%d:%02d", left / 1200, (left / 20) % 60);
                case "END" -> Component.translatable("hud.amemaru.duel.end").getString();
                default -> Component.translatable("hud.amemaru.duel.ready").getString();
            };
            graphics.drawString(font, UiStyle.clip(font, Component.translatable("screen.amemaru.duel.match", one.getString("a"), one.getString("b"),
                    state, one.getInt("watchers")).getString(), W - 96), this.left + 16, y + i * ROW + 1, UiStyle.TEXT, false);
        }
        // 申し込む相手
        y = top + 136;
        graphics.drawString(font, Component.translatable("screen.amemaru.duel.people"), left + 12, y - 11, UiStyle.TEXT_HEADER, true);
        String unavailable = data.getString("unavailable");
        if (!unavailable.isEmpty()) {
            Component why = Component.translatable(unavailable);
            graphics.drawString(font, UiStyle.clip(font, why.getString(), W - 140), left + W - 14 - Math.min(W - 140, font.width(why)),
                    y - 11, UiStyle.TEXT_BAD, false);
        }
        ListTag people = data.getList("people", Tag.TAG_COMPOUND);
        if (people.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.duel.no_people"), left + 16, y + 1, UiStyle.TEXT_DIM, false);
        }
        for (int i = 0; i < PEOPLE_ROWS; i++) {
            int index = page * PEOPLE_ROWS + i;
            if (index >= people.size()) {
                break;
            }
            CompoundTag one = people.getCompound(index);
            String name = one.getString("name");
            if (one.getBoolean("fighting")) {
                name += " " + Component.translatable("screen.amemaru.duel.fighting").getString();
            } else if (one.getBoolean("busy")) {
                name += " " + Component.translatable("screen.amemaru.duel.busy").getString();
            }
            graphics.drawString(font, UiStyle.clip(font, name, W - 96), left + 16, y + i * ROW + 1,
                    one.getBoolean("busy") ? UiStyle.TEXT_DIM : UiStyle.TEXT, false);
        }
        int pages = Math.max(1, (people.size() + PEOPLE_ROWS - 1) / PEOPLE_ROWS);
        if (pages > 1) {
            graphics.drawString(font, (page + 1) + "/" + pages, left + 60, top + H - 19, UiStyle.TEXT_DIM, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        ClientSocial.send("duel_close");
        minecraft.setScreen(parent instanceof DuelScreen ? null : parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

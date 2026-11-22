package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.client.ClientGuild;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * 露店の区画を増やす寄付（Phase 6 その3。Phase 0-A 7章：フィンが寄付を募り、目標に届いた次の朝に 12 → 20 区画に増える。
 * 11月20日に決定）。市場監督のフィン・ロッソとの会話から開く。集まった額・目標・自分の寄付・寄付した人（多い順）。
 */
public class DonationScreen extends Screen {
    private static final int W = 300;
    private static final int H = 196;

    private CompoundTag data;
    private int left;
    private int top;
    private EditBox amount;
    private String draft = "";

    public DonationScreen(CompoundTag data) {
        super(Component.translatable("screen.amemaru.donation.title"));
        this.data = data;
    }

    public void update(CompoundTag data) {
        this.data = data;
        rebuildWidgets();
    }

    private static String knots(long value) {
        return NumberFormat.getIntegerInstance(Locale.ROOT).format(value);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        addRenderableWidget(new CrystalButton(left + W - 20, top + 5, 14, 12, Component.literal("×"), b -> onClose()));
        boolean open = !data.getBoolean("reached") && !data.getBoolean("expanded");
        int y = top + H - 46;
        amount = new EditBox(font, left + 14, y, 90, 14, Component.translatable("screen.amemaru.donation.amount"));
        amount.setMaxLength(9);
        amount.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        amount.setValue(draft);
        amount.setResponder(text -> draft = text);
        amount.setHint(Component.translatable("screen.amemaru.donation.amount").withStyle(s -> s.withColor(0x6E7891)));
        amount.active = open;
        addRenderableWidget(amount);
        int bx = left + 110;
        for (long preset : new long[]{100, 1000, 10000}) {
            Button button = addRenderableWidget(new CrystalButton(bx, y, 42, 14, Component.literal("+" + knots(preset)), b -> {
                long now = parse(draft);
                draft = String.valueOf(Math.min(999_999_999L, now + preset));
                amount.setValue(draft);
            }));
            button.active = open;
            bx += 46;
        }
        Button donate = addRenderableWidget(new CrystalButton(left + 14, y + 20, 90, 16, Component.translatable("screen.amemaru.donation.donate"),
                b -> {
                    long value = parse(draft);
                    if (value > 0) {
                        CompoundTag args = new CompoundTag();
                        args.putLong("amount", value);
                        ClientGuild.send("donate", args);
                        draft = "";
                    }
                }));
        donate.active = open;
    }

    private static long parse(String text) {
        try {
            return text.isEmpty() ? 0 : Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (amount != null) {
            amount.tick();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + W, top + H);
        graphics.drawCenteredString(font, title, left + W / 2, top + 7, UiStyle.TEXT_HEADER);
        int x = left + 14;
        int y = top + 22;
        UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.donation.intro").getString(), x, y, W - 28, UiStyle.TEXT_DIM, 2);
        y += 24;
        long total = data.getLong("total");
        long goal = Math.max(1, data.getLong("goal"));
        int barW = W - 28;
        graphics.fill(x, y, x + barW, y + 8, 0xFF1B2030);
        graphics.fill(x, y, x + Math.round(barW * Math.min(1.0F, total / (float) goal)), y + 8, 0xFFE8C15A);
        y += 11;
        graphics.drawString(font, Component.translatable("screen.amemaru.donation.progress", knots(total), knots(goal)), x, y, UiStyle.TEXT_GOLD,
                false);
        String state = data.getBoolean("expanded") ? "screen.amemaru.donation.expanded"
                : data.getBoolean("reached") ? "screen.amemaru.donation.reached" : "";
        if (!state.isEmpty()) {
            Component s = Component.translatable(state);
            graphics.drawString(font, s, left + W - 14 - font.width(s), y, UiStyle.TEXT_GOOD, false);
        }
        y += 12;
        graphics.drawString(font, Component.translatable("screen.amemaru.donation.mine", knots(data.getLong("mine")), knots(data.getLong("knots"))),
                x, y, UiStyle.TEXT, false);
        y += 14;
        graphics.drawString(font, Component.translatable("screen.amemaru.donation.donors", data.getInt("donorCount")), x, y, UiStyle.TEXT_HEADER,
                false);
        y += 11;
        ListTag donors = data.getList("donors", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(5, donors.size()); i++) {
            CompoundTag one = donors.getCompound(i);
            String line = (i + 1) + ". " + one.getString("name");
            String value = knots(one.getLong("amount"));
            graphics.drawString(font, UiStyle.clip(font, line, 150), x + 4, y, UiStyle.TEXT, false);
            graphics.drawString(font, value, x + 210 - font.width(value), y, UiStyle.TEXT_GOLD, false);
            y += 10;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}

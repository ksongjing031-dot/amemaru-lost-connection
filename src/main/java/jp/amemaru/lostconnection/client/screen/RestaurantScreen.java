package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.ProjectC2S;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * ペトラ・ポムの料理屋の画面（Phase 7 その4。共同事業③のあと）。料理ごとに、食べると上がる力・材料（持っている数／要る数）・代金と、
 * 「作ってもらう」のボタン。上に、いま効いている料理の強化。
 */
public class RestaurantScreen extends Screen {
    private static final int W = 380;
    private static final int H = 220;
    private static final int ROW = 28;

    private CompoundTag data;
    private int left;
    private int top;

    public RestaurantScreen(CompoundTag data) {
        super(Component.translatable("screen.amemaru.restaurant.title"));
        this.data = data;
    }

    public void update(CompoundTag next) {
        data = next;
        rebuild();
    }

    /** 自動の確認の写真：index 番目の料理を作ってもらう。 */
    public void ciCook(int index) {
        ListTag dishes = data.getList("dishes", Tag.TAG_COMPOUND);
        if (index >= 0 && index < dishes.size()) {
            cook(dishes.getCompound(index).getString("item"));
        }
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        rebuild();
    }

    private void rebuild() {
        if (minecraft == null) {
            return;
        }
        clearWidgets();
        addRenderableWidget(new CrystalButton(left + W - 20, top + 5, 14, 12, Component.literal("×"), b -> onClose()));
        ListTag dishes = data.getList("dishes", Tag.TAG_COMPOUND);
        for (int i = 0; i < dishes.size(); i++) {
            CompoundTag one = dishes.getCompound(i);
            CrystalButton b = new CrystalButton(left + W - 78, top + 38 + i * ROW + 6, 68, 14,
                    Component.translatable("screen.amemaru.restaurant.cook"), x -> cook(one.getString("item")));
            b.active = one.getBoolean("can");
            addRenderableWidget(b);
        }
    }

    private void cook(String item) {
        CompoundTag args = new CompoundTag();
        args.putString("dish", item);
        ModNetwork.sendToServer(new ProjectC2S("cook", args));
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
        Component food = data.contains("food") ? Component.Serializer.fromJson(data.getString("food")) : null;
        graphics.drawString(font, food != null ? food : Component.translatable("screen.amemaru.restaurant.no_food", data.getInt("minutes")),
                left + 10, top + 22, food != null ? UiStyle.TEXT_GOLD : UiStyle.TEXT_DIM, false);
        String knots = Component.translatable("screen.amemaru.restaurant.knots", String.format(Locale.ROOT, "%,d", data.getLong("knots"))).getString();
        graphics.drawString(font, knots, left + W - 10 - font.width(knots), top + 22, UiStyle.TEXT, false);
        ListTag dishes = data.getList("dishes", Tag.TAG_COMPOUND);
        for (int i = 0; i < dishes.size(); i++) {
            CompoundTag one = dishes.getCompound(i);
            int y = top + 38 + i * ROW;
            UiStyle.card(graphics, left + 8, y, left + W - 8, y + ROW - 2, 0xE08A3C, false);
            ItemStack stack = ItemStack.of(one.getCompound("stack"));
            graphics.renderItem(stack, left + 12, y + 5);
            graphics.drawString(font, UiStyle.clip(font, stack.getHoverName().getString(), 120), left + 32, y + 3, UiStyle.TEXT, false);
            Component effect = Component.Serializer.fromJson(one.getString("effect"));
            if (effect != null) {
                graphics.drawString(font, UiStyle.clip(font, effect.getString(), 120), left + 32, y + 14, UiStyle.TEXT_GOLD, false);
            }
            // 材料（アイコンと 持っている数／要る数）と代金
            int x = left + 158;
            for (Tag t : one.getList("ingredients", Tag.TAG_COMPOUND)) {
                CompoundTag in = (CompoundTag) t;
                int have = in.getInt("have");
                int need = in.getInt("need");
                if (in.contains("stars")) {
                    String label = "★" + in.getInt("stars") + "+";
                    graphics.drawString(font, label, x, y + 4, 0xFFE38A, false);
                    graphics.drawString(font, have + "/" + need, x, y + 14, have >= need ? UiStyle.TEXT_GOOD : UiStyle.TEXT_BAD, false);
                    x += Math.max(font.width(label), font.width(have + "/" + need)) + 6;
                } else {
                    ItemStack in2 = ItemStack.of(in.getCompound("stack"));
                    graphics.renderItem(in2, x, y + 2);
                    graphics.drawString(font, have + "/" + need, x + 1, y + 17, have >= need ? UiStyle.TEXT_GOOD : UiStyle.TEXT_BAD, false);
                    if (mouseX >= x && mouseX < x + 16 && mouseY >= y + 2 && mouseY < y + 18) {
                        graphics.renderTooltip(font, in2, mouseX, mouseY);
                    }
                    x += 22;
                }
            }
            String fee = Component.translatable("screen.amemaru.restaurant.fee", one.getLong("fee")).getString();
            graphics.drawString(font, fee, left + W - 86 - font.width(fee), y + 9, UiStyle.TEXT_DIM, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}

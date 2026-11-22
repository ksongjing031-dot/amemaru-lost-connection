package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.core.registry.ModItems;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * ギルドの知らせ（右上に出る小さな札。Phase 6 その3）：称号を手に入れた・クエストを終えた・親密度の段階が上がった・
 * 古代記録を見つけた。称号は、色と光り方を称号の種類に合わせる（Phase 0-A 22章「手に入れたら本人の画面に演出」）。
 */
public class GuildToast implements Toast {
    private static final long DURATION = 5500L;
    private static final long DURATION_SPECIAL = 8000L;

    private final String type;
    private final Component header;
    private final Component line;
    private final ItemStack icon;
    /** 称号の名前（称号の知らせだけ。光らせるため、描くたびに作る） */
    private final String titleName;
    private final int titleColor;
    private final String titleKind;
    private final boolean titleRainbow;
    private final int width;
    private final int accent;

    private GuildToast(String type, Component header, Component line, ItemStack icon, String titleName, int titleColor, String titleKind,
                       boolean titleRainbow, int accent) {
        this.type = type;
        this.header = header;
        this.line = line;
        this.icon = icon;
        this.titleName = titleName;
        this.titleColor = titleColor;
        this.titleKind = titleKind;
        this.titleRainbow = titleRainbow;
        this.accent = accent;
        Font font = Minecraft.getInstance().font;
        int text = Math.max(font.width(header), "title".equals(type)
                ? font.width(TitleText.of(titleName, titleColor, titleKind, titleRainbow, 0L, true)) : font.width(line));
        this.width = Math.max(160, Math.min(260, 32 + text + 10));
    }

    /** サーバーからの知らせ（{@code toast}）を、札にして出す。 */
    public static void show(CompoundTag data) {
        Minecraft minecraft = Minecraft.getInstance();
        GuildToast toast = switch (data.getString("type")) {
            case "title" -> {
                String kind = data.getString("kind");
                Component header = Component.translatable("limited".equals(kind) ? "toast.amemaru.title.limited"
                        : "unique".equals(kind) ? "toast.amemaru.title.unique" : "toast.amemaru.title.got");
                yield new GuildToast("title", header, Component.empty(), new ItemStack(ModItems.OATH_CRYSTAL.get()),
                        data.getString("name"), data.getInt("color"), kind, data.getBoolean("rainbow"),
                        TitleText.color(data.getInt("color"), kind, data.getBoolean("rainbow"), 0L));
            }
            case "quest" -> {
                long knots = data.getLong("knots");
                long exp = data.getLong("exp");
                Component reward = knots > 0 && exp > 0
                        ? Component.translatable("toast.amemaru.quest.reward_both", knots, exp)
                        : knots > 0 ? Component.translatable("toast.amemaru.quest.reward_knots", knots)
                        : exp > 0 ? Component.translatable("toast.amemaru.quest.reward_exp", exp)
                        : Component.literal(data.getString("name"));
                yield new GuildToast("quest", Component.translatable("toast.amemaru.quest.done", data.getString("name")), reward,
                        new ItemStack(Items.WRITABLE_BOOK), "", 0, "", false, 0x9CFFB0);
            }
            case "bond" -> new GuildToast("bond", Component.translatable("toast.amemaru.bond.up"),
                    Component.translatable("toast.amemaru.bond.stage", data.getString("npc"),
                            Component.translatable("bond.amemaru.stage." + data.getInt("stage"))),
                    new ItemStack(Items.POPPY), "", 0, "", false, 0xFF9ACD);
            case "record" -> new GuildToast("record", Component.translatable("toast.amemaru.record.found"),
                    Component.translatable("toast.amemaru.record.floor", data.getInt("floor")),
                    new ItemStack(ModItems.RECORD_BOOK.get()), "", 0, "", false, 0xBFEFFF);
            default -> null;
        };
        if (toast != null) {
            minecraft.getToasts().addToast(toast);
        }
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent component, long time) {
        Font font = component.getMinecraft().font;
        int w = width();
        int h = height();
        long now = Util.getMillis();
        int edge = "title".equals(type) ? TitleText.color(titleColor, titleKind, titleRainbow, now) : accent;
        graphics.fill(0, 0, w, h, 0xFF090C14);
        graphics.fill(1, 1, w - 1, h - 1, 0xFF000000 | edge);
        graphics.fillGradient(2, 2, w - 2, h - 2, 0xF2131B33, 0xF20B1022);
        graphics.fill(2, 2, w - 2, 3, (0x90 << 24) | (edge & 0xFFFFFF));
        graphics.renderFakeItem(icon, 8, 8);
        graphics.drawString(font, header, 30, 7, 0xBFEFFF, false);
        if ("title".equals(type)) {
            graphics.drawString(font, TitleText.of(titleName, titleColor, titleKind, titleRainbow, now, true), 30, 18, 0xFFFFFF, false);
        } else {
            graphics.drawString(font, line, 30, 18, 0xFFFFFF, false);
        }
        boolean special = "limited".equals(titleKind) || "unique".equals(titleKind);
        return time >= (special ? DURATION_SPECIAL : DURATION) ? Visibility.HIDE : Visibility.SHOW;
    }
}

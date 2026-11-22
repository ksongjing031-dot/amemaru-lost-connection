package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.ProjectC2S;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * 寄付台の画面（Phase 7 その4。Phase 0-A 7章「共同事業（寄付による施設の発展）」）。
 *
 * <p>左に7つの共同事業（受付前・受付中・輝き段階を待つ・完成・できた）、右に選んだ共同事業の「完成すると」・寄付の帯（今の目標は
 * 25人基準 × 人数補正）・必要な輝き段階・受付の日・寄付のボタン・銘板（寄付した人の名前と額）。</p>
 */
public class ProjectScreen extends Screen {
    private static final int W = 400;
    private static final int H = 226;
    private static final int ROW = 24;
    private static final long[] AMOUNTS = {100, 1_000, 10_000, Long.MAX_VALUE};

    private CompoundTag data;
    private int selected;
    private int left;
    private int top;

    public ProjectScreen(CompoundTag data) {
        super(Component.translatable("screen.amemaru.project.title"));
        this.data = data;
        String select = data.getString("select");
        ListTag list = projects();
        selected = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            if (select.isEmpty() ? "open".equals(one.getString("status")) : select.equals(one.getString("id"))) {
                selected = i;
                break;
            }
        }
    }

    /** 寄付のあと（サーバーから）。 */
    public void update(CompoundTag next) {
        data = next;
        rebuild();
    }

    /** 自動の確認の写真：index 番目を選ぶ。 */
    public void ciSelect(int index) {
        selected = Mth.clamp(index, 0, Math.max(0, projects().size() - 1));
        rebuild();
    }

    /** 自動の確認の写真：選んでいる共同事業に寄付する。 */
    public void ciDonate(long amount) {
        donate(amount);
    }

    private ListTag projects() {
        return data.getList("projects", Tag.TAG_COMPOUND);
    }

    private CompoundTag current() {
        ListTag list = projects();
        return list.isEmpty() ? new CompoundTag() : list.getCompound(Mth.clamp(selected, 0, list.size() - 1));
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
        ListTag list = projects();
        for (int i = 0; i < list.size(); i++) {
            int index = i;
            CompoundTag one = list.getCompound(i);
            CrystalButton b = new CrystalButton(left + 8, top + 24 + i * ROW, 132, ROW - 4,
                    Component.literal(circled(one.getInt("order")) + " " + one.getString("short")), x -> {
                selected = index;
                rebuild();
            });
            b.active = i != selected;
            addRenderableWidget(b);
        }
        CompoundTag cur = current();
        boolean open = "open".equals(cur.getString("status"));
        for (int i = 0; i < AMOUNTS.length; i++) {
            long amount = AMOUNTS[i];
            Component label = amount == Long.MAX_VALUE ? Component.translatable("screen.amemaru.project.donate_rest")
                    : Component.translatable("screen.amemaru.project.donate", String.format(Locale.ROOT, "%,d", amount));
            CrystalButton b = new CrystalButton(left + 150 + i * 61, top + 120, 58, 14, label, x -> donate(amount));
            b.active = open && data.getLong("knots") > 0;
            addRenderableWidget(b);
        }
    }

    private void donate(long amount) {
        CompoundTag args = new CompoundTag();
        args.putString("id", current().getString("id"));
        args.putLong("amount", amount);
        ModNetwork.sendToServer(new ProjectC2S("donate", args));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String circled(int n) {
        return n >= 1 && n <= 10 ? String.valueOf((char) ('①' + n - 1)) : String.valueOf(n);
    }

    private static int statusColor(String status) {
        return switch (status) {
            case "open" -> UiStyle.TEXT_GOOD;
            case "waiting" -> 0xFFE38A;
            case "complete" -> UiStyle.TEXT_GOLD;
            case "built" -> 0xB8E8FF;
            default -> UiStyle.TEXT_DIM;
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + W, top + H);
        graphics.drawCenteredString(font, title, left + W / 2, top + 7, UiStyle.TEXT_HEADER);
        super.render(graphics, mouseX, mouseY, partialTick);
        // 左の列の状態の印
        ListTag list = projects();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            String status = one.getString("status");
            int y = top + 24 + i * ROW;
            graphics.fill(left + 136, y + 3, left + 139, y + ROW - 7, 0xFF000000 | statusColor(status));
        }
        CompoundTag cur = current();
        int x0 = left + 150;
        int x1 = left + W - 8;
        int y = top + 22;
        UiStyle.card(graphics, x0 - 4, y - 2, x1 + 2, top + H - 8, 0xE0B040, false);
        graphics.drawString(font, UiStyle.clip(font, circled(cur.getInt("order")) + " " + cur.getString("name"), x1 - x0),
                x0, y + 2, UiStyle.TEXT_GOLD, false);
        String status = cur.getString("status");
        Component st = switch (status) {
            case "locked" -> Component.translatable("screen.amemaru.project.status.locked", cur.getInt("startDay"));
            case "waiting" -> Component.translatable("screen.amemaru.project.status.waiting", cur.getInt("needStage"));
            case "complete" -> Component.translatable("screen.amemaru.project.status.complete");
            case "built" -> Component.translatable("screen.amemaru.project.status.built", cur.getInt("builtDay"));
            default -> Component.translatable("screen.amemaru.project.status.open");
        };
        graphics.drawString(font, st, x0, y + 14, statusColor(status), false);
        graphics.drawString(font, UiStyle.clip(font, Component.translatable("screen.amemaru.project.result", cur.getString("result")).getString(),
                x1 - x0), x0, y + 26, UiStyle.TEXT_HEADER, false);
        UiStyle.wrapped(graphics, font, cur.getString("description"), x0, y + 38, x1 - x0, UiStyle.TEXT_DIM, 2);
        // 寄付の帯
        long goal = Math.max(1, cur.getLong("goal"));
        long donated = cur.getLong("donated");
        int by = y + 62;
        graphics.fill(x0, by, x1, by + 8, 0xFF0B1022);
        int fill = (int) Math.round((x1 - x0 - 2) * Math.min(1.0, donated / (double) goal));
        graphics.fill(x0 + 1, by + 1, x0 + 1 + fill, by + 7, donated >= goal ? 0xFFFFD866 : 0xFF6AC8E8);
        graphics.drawString(font, Component.translatable("screen.amemaru.project.money", String.format(Locale.ROOT, "%,d", donated),
                String.format(Locale.ROOT, "%,d", goal), String.format(Locale.ROOT, "%,d", cur.getLong("baseGoal"))), x0, by + 11, UiStyle.TEXT, false);
        int stage = data.getInt("stage");
        int need = cur.getInt("needStage");
        graphics.drawString(font, Component.translatable(stage >= need ? "screen.amemaru.project.stage_ok" : "screen.amemaru.project.stage_need",
                need, stage), x0, by + 23, stage >= need ? UiStyle.TEXT_GOOD : 0xFFE38A, false);
        graphics.drawString(font, Component.translatable("screen.amemaru.project.knots", String.format(Locale.ROOT, "%,d", data.getLong("knots")),
                String.format(Locale.ROOT, "%,d", cur.getLong("mine"))), x0, by + 52, UiStyle.TEXT_DIM, false);
        // 銘板
        int py = by + 66;
        graphics.drawString(font, Component.translatable("screen.amemaru.project.plate", cur.getInt("donors")), x0, py, UiStyle.TEXT_HEADER, false);
        ListTag plate = cur.getList("plate", Tag.TAG_COMPOUND);
        if (plate.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.project.plate_none"), x0 + 4, py + 11, UiStyle.TEXT_DIM, false);
        }
        int col = (x1 - x0) / 2;
        for (int i = 0; i < Math.min(8, plate.size()); i++) {
            CompoundTag d = plate.getCompound(i);
            int px = x0 + 4 + (i % 2) * col;
            int ry = py + 11 + (i / 2) * 10;
            String amount = String.format(Locale.ROOT, "%,d", d.getLong("amount"));
            graphics.drawString(font, UiStyle.clip(font, d.getString("name"), col - font.width(amount) - 12), px, ry, UiStyle.TEXT, false);
            graphics.drawString(font, amount, px + col - 8 - font.width(amount), ry, UiStyle.TEXT_GOLD, false);
        }
    }
}

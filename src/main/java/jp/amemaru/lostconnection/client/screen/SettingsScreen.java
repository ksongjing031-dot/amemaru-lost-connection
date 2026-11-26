package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.client.BossFx;
import jp.amemaru.lostconnection.core.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;

import java.util.List;

/**
 * 見た目と音の設定の画面（11月26日【補完】）。Mod の一覧でこのModを選び、「設定」を押すと開く。
 *
 * <p>「ボス戦のBGMがなっていない」のご指摘で、ボス戦の曲を Minecraft の「音楽」の音量から切り離し、このModの設定
 * （{@code boss.musicVolume}）で大きさを決めるようにした。設定ファイル（{@code config/amemaru-client.toml}）を開かなくても
 * 変えられるように、見た目の設定と一緒に、この画面に並べた。「曲を試しに聞く」で、1〜8階のボスの曲を順に10秒ずつ流せる
 * （戦わなくても、音が出るかを確かめられる）。</p>
 */
public final class SettingsScreen extends Screen {
    /** 試しに聞く曲（1〜8階のボス）。 */
    private static final List<String> SONGS = List.of("veldhorn", "old_ward", "hydram", "cantare", "ancient_guardian", "ordo",
            "reflecta", "mune");
    private static final int PREVIEW_SECONDS = 10;
    private static final int ROW = 24;
    /** ボス戦の組の前の、見出しの分のすきま。 */
    private static final int BOSS_GAP = 18;
    /** 前に試した曲（0から。-1ならまだ）。次に押すと、その次の階の曲。 */
    private static int previewed = -1;

    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("screen.amemaru.settings.title"));
        this.parent = parent;
    }

    /** Mod の一覧の「設定」ボタンから開けるようにする（Modの入り口から、クライアントのときだけ呼ぶ）。 */
    public static void register(ModLoadingContext context) {
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new SettingsScreen(parent)));
    }

    @Override
    protected void init() {
        int left = width / 2 - 155;
        int right = width / 2 + 5;
        int y = top();
        int row = ROW;
        addRenderableWidget(new Slider(left, y, "hud_scale", 0.5, 2.0, 0.1, ClientConfig.HUD_SCALE));
        toggle(right, y, "hud_numbers", ClientConfig.HUD_SHOW_NUMBERS);
        y += row;
        toggle(left, y, "damage_numbers", ClientConfig.SHOW_DAMAGE_NUMBERS);
        toggle(right, y, "screen_shake", ClientConfig.SCREEN_SHAKE);
        y += row;
        CrystalButton control = new CrystalButton(left, y, 150, 20, controlLabel(), button -> {
            ClientConfig.SkillControl next = ClientConfig.skillControl() == ClientConfig.SkillControl.KEYS
                    ? ClientConfig.SkillControl.HOTBAR_ALT : ClientConfig.SkillControl.KEYS;
            ClientConfig.SKILL_CONTROL.set(next);
            ClientConfig.SKILL_CONTROL.save();
            button.setMessage(controlLabel());
        });
        control.setTooltip(Tooltip.create(Component.translatable("screen.amemaru.settings.skill_control.tip")));
        addRenderableWidget(control);
        toggle(right, y, "gear_sparkles", ClientConfig.GEAR_SPARKLES);
        y += row + BOSS_GAP;
        toggle(left, y, "boss_music", ClientConfig.BOSS_MUSIC);
        addRenderableWidget(new Slider(right, y, "boss_music_volume", 0.0, 1.0, 0.05, ClientConfig.BOSS_MUSIC_VOLUME));
        y += row;
        toggle(left, y, "boss_effects", ClientConfig.BOSS_EFFECTS);
        CrystalButton listen = new CrystalButton(right, y, 150, 20, previewLabel(), button -> {
            previewed = (previewed + 1) % SONGS.size();
            BossFx.preview(SONGS.get(previewed), PREVIEW_SECONDS);
            button.setMessage(previewLabel());
        });
        listen.setTooltip(Tooltip.create(Component.translatable("screen.amemaru.settings.preview.tip", PREVIEW_SECONDS)));
        addRenderableWidget(listen);
        y += row + 12;
        addRenderableWidget(new CrystalButton(width / 2 - 100, y, 200, 20, Component.translatable("gui.done"), button -> onClose()));
    }

    private void toggle(int x, int y, String key, ForgeConfigSpec.BooleanValue value) {
        CrystalButton button = new CrystalButton(x, y, 150, 20, toggleLabel(key, value.get()), pressed -> {
            boolean next = !value.get();
            value.set(next);
            value.save();
            pressed.setMessage(toggleLabel(key, next));
        });
        button.setTooltip(Tooltip.create(Component.translatable("screen.amemaru.settings." + key + ".tip")));
        addRenderableWidget(button);
    }

    private static Component toggleLabel(String key, boolean on) {
        return Component.translatable("screen.amemaru.settings." + key,
                Component.translatable(on ? "screen.amemaru.settings.on" : "screen.amemaru.settings.off"));
    }

    private static Component controlLabel() {
        return Component.translatable("screen.amemaru.settings.skill_control", Component.translatable(
                ClientConfig.skillControl() == ClientConfig.SkillControl.KEYS
                        ? "screen.amemaru.settings.skill_control.keys" : "screen.amemaru.settings.skill_control.hotbar"));
    }

    private static Component previewLabel() {
        if (previewed < 0) {
            return Component.translatable("screen.amemaru.settings.preview");
        }
        return Component.translatable("screen.amemaru.settings.preview.floor", previewed + 1);
    }

    /** 1行めの高さ。 */
    private int top() {
        return Math.max(34, height / 2 - 96);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int top = top();
        int doneY = top + ROW * 5 + BOSS_GAP + 12;
        UiStyle.panel(graphics, width / 2 - 165, top - 26, width / 2 + 165, doneY + 28);
        graphics.drawCenteredString(font, title, width / 2, top - 18, UiStyle.TEXT_HEADER);
        // 区切り：ボス戦
        int headerY = top + ROW * 3 + 1;
        graphics.drawString(font, Component.translatable("screen.amemaru.settings.boss_header"), width / 2 - 155, headerY,
                UiStyle.TEXT_GOLD, false);
        UiStyle.divider(graphics, width / 2 - 155, width / 2 + 155, headerY + 11);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 開いていても、ゲームを止めない（止めると音も止まり、「曲を試しに聞く」が聞こえない）。 */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        BossFx.stopPreview();
        Minecraft minecraft = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        minecraft.setScreen(parent);
    }

    /** 数の設定のつまみ（HUDの大きさ・ボス戦の曲の大きさ）。動かしたら、すぐに保存する。 */
    private static final class Slider extends AbstractSliderButton {
        private final String key;
        private final double min;
        private final double max;
        private final double step;
        private final ForgeConfigSpec.DoubleValue config;

        Slider(int x, int y, String key, double min, double max, double step, ForgeConfigSpec.DoubleValue config) {
            super(x, y, 150, 20, Component.empty(), (Mth.clamp(config.get(), min, max) - min) / (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.step = step;
            this.config = config;
            setTooltip(Tooltip.create(Component.translatable("screen.amemaru.settings." + key + ".tip")));
            updateMessage();
        }

        private double current() {
            double raw = min + value * (max - min);
            return Mth.clamp(Math.round(raw / step) * step, min, max);
        }

        @Override
        protected void updateMessage() {
            double v = current();
            Component shown = "boss_music_volume".equals(key)
                    ? Component.literal(Math.round(v * 100.0) + "%")
                    : Component.literal(String.format(java.util.Locale.ROOT, "%.1f", v));
            setMessage(Component.translatable("screen.amemaru.settings." + key, shown));
        }

        @Override
        protected void applyValue() {
            config.set(current());
            config.save();
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            boolean hover = isHoveredOrFocused();
            graphics.fill(x + 1, y, x + width - 1, y + height, 0xFF090C14);
            graphics.fill(x, y + 1, x + width, y + height - 1, 0xFF090C14);
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, hover ? 0xFFAEE8FF : 0xFF4F6B99);
            graphics.fillGradient(x + 2, y + 2, x + width - 2, y + height - 2, 0xFF17233F, 0xFF101A30);
            // 入っている分（左から）と、つまみ
            int filled = x + 2 + (int) Math.round(value * (width - 4));
            graphics.fillGradient(x + 2, y + 2, filled, y + height - 2, 0xFF2F4C80, 0xFF1F3560);
            int knob = Mth.clamp(filled - 3, x + 2, x + width - 8);
            graphics.fill(knob, y + 1, knob + 6, y + height - 1, hover ? 0xFFFFFFFF : 0xFFBFEFFF);
            var font = Minecraft.getInstance().font;
            int textWidth = font.width(getMessage());
            graphics.drawString(font, getMessage(), x + (width - textWidth) / 2 + 1, y + (height - 8) / 2 + 1, 0xFFFFFF, true);
        }
    }
}

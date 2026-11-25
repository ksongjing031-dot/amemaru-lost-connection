package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ロスト状態の見え方（11月25日、7階から。Phase 0-A 13章）。
 * <ul>
 *   <li>薄れ：ほかの人の頭の上の名前と、パーティの欄の名前が「？？？」に見える（{@link #fade()}）</li>
 *   <li>ロスト状態のあいだは、画面の縁に白いノイズ（配信で分かりやすいように。{@link #OVERLAY}）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class LostClientEvents {
    public static final String HIDDEN = "？？？";
    private static final RandomSource NOISE = RandomSource.create();

    private LostClientEvents() {
    }

    /** 自分が「薄れ」の状態か（味方の名前が読めない）。 */
    public static boolean fade() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.hasEffect(ModEffects.LOST_FADE.get());
    }

    /** 自分が「断線」の状態か（パーティの欄から、仲間のHPが消える。8階から）。 */
    public static boolean severed() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.hasEffect(ModEffects.LOST_SEVER.get());
    }

    /** なにかのロスト状態か。 */
    public static boolean anyLost() {
        Minecraft minecraft = Minecraft.getInstance();
        return fade() || severed() || minecraft.player != null && minecraft.player.hasEffect(ModEffects.LOST_SILENCE.get());
    }

    // 称号・ダウンの秒数を足したあとに、まるごと「？？？」にする。写し身（7階）の名前は、いつも氷の色の「？？？」
    // （仲間の「？？？」は白。名前の表示で見分ける。Phase 0-A 9章の7階「本物の見分け方は、名前の表示とHPバーの動き」）
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof jp.amemaru.lostconnection.enemy.iceforest.Mimic) {
            event.setContent(Component.literal(HIDDEN).withStyle(s -> s.withColor(COPY_COLOR)));
            return;
        }
        if (!(event.getEntity() instanceof Player player) || player == Minecraft.getInstance().player || !fade()) {
            return;
        }
        event.setContent(Component.literal(HIDDEN));
    }

    /** 写し身の名前の色（淡い氷の色）。 */
    public static final int COPY_COLOR = 0x9FD8FF;

    /** 画面の縁の白いノイズ（ちらちらする細かい点と、縁のうすい白）。 */
    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, width, height) -> {
        if (!anyLost() || Minecraft.getInstance().options.hideGui) {
            return;
        }
        draw(graphics, width, height);
    };

    private static void draw(GuiGraphics graphics, int width, int height) {
        int band = Math.max(12, Math.min(width, height) / 10);
        long time = net.minecraft.Util.getMillis() / 60;
        NOISE.setSeed(time);
        for (int k = 0; k < band; k++) {
            int alpha = (int) (60.0 * (1.0 - k / (double) band));
            int color = alpha << 24 | 0xF4F8FF;
            graphics.fill(k, k, width - k, k + 1, color);
            graphics.fill(k, height - k - 1, width - k, height - k, color);
            graphics.fill(k, k, k + 1, height - k, color);
            graphics.fill(width - k - 1, k, width - k, height - k, color);
        }
        int specks = (width + height) / 2;
        for (int i = 0; i < specks; i++) {
            int side = NOISE.nextInt(4);
            int depth = (int) (Math.pow(NOISE.nextDouble(), 2.0) * band * 1.6);
            int x;
            int y;
            switch (side) {
                case 0 -> {
                    x = NOISE.nextInt(width);
                    y = depth;
                }
                case 1 -> {
                    x = NOISE.nextInt(width);
                    y = height - 1 - depth;
                }
                case 2 -> {
                    x = depth;
                    y = NOISE.nextInt(height);
                }
                default -> {
                    x = width - 1 - depth;
                    y = NOISE.nextInt(height);
                }
            }
            int size = 1 + NOISE.nextInt(2);
            int alpha = 90 + NOISE.nextInt(120);
            graphics.fill(x, y, x + size, y + size, alpha << 24 | 0xFFFFFF);
        }
    }
}

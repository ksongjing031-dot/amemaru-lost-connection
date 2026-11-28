package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ボスごとのHPの帯（11月27日のご指示「ボスのHPバーはオリジナルでそれぞれのボスのデザインにする。一層は1ゲージでいいが、それから
 * 強くなるにつれてボスのHPバーが多くなるようにする（ここでプレイの際の見づらさを考慮すること）」【補完】）。
 *
 * <p>このModのボスのバー（名前が {@code boss.amemaru.bar}。引数は 名前・帯の本数・ボスの種類）だけ、バニラの帯の代わりに描く。
 * 枠はボスごとの絵（{@code textures/gui/boss_bar/<ボス>.png}。256×24。{@code tools/artgen/make_boss_bars.py}）。</p>
 *
 * <p>見やすさのため【補完】：</p>
 * <ul>
 *   <li>帯は何本あっても1本ぶんの高さに重ねて描き、残りの本数を名前の右に「×3」と大きく出す（帯を縦に並べない）</li>
 *   <li>今の帯の下に、次の帯の色を敷く（今の帯が減ると、次の色が見えてくる）。最後の1本は赤</li>
 *   <li>減った分は、少し遅れて明るい色で縮む（どれだけ削ったかが分かる）。10%ごとの細い目盛り</li>
 *   <li>幅はバニラの帯より少し広いだけ（216ドット）。名前の位置もバニラと同じ</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class BossHpBar {
    private static final int TEX_W = 256;
    private static final int TEX_H = 24;
    private static final int FILL_X = 20;
    private static final int FILL_Y = 9;
    private static final int FILL_W = 216;
    private static final int FILL_H = 6;
    private static final int LAST = 0xD8423A;
    private static final int SLOT = 0xE0140F14;

    /** ボスごとの帯の色（上の帯から。最後の1本は赤 {@link #LAST}。1本だけのボスは、はじめの色）。 */
    private static final Map<String, int[]> PALETTES = Map.of(
            "veldhorn", new int[]{0xE2B65A},
            "old_ward", new int[]{0x7ED957},
            "hydram", new int[]{0x4FB6F0},
            "cantare", new int[]{0xF2C94C, 0xB58CFF},
            "ancient_guardian", new int[]{0x4FCC9E, 0xE6E0D2},
            "ordo", new int[]{0xBFEFFF, 0x7FC8F0, 0xF2C94C},
            "reflecta", new int[]{0xF0F4FF, 0xC9A8FF, 0x8FF0FF},
            "mune", new int[]{0xA8F7FF, 0x7FDDE8, 0x5FA8E0, 0x6F7FE0},
            "valgrim", new int[]{0xEAF4FF, 0x9CC8FF, 0x5A80DC, 0x3C5A9E});
    private static final int[] DEFAULT_PALETTE = {0xE0C89A, 0xB8A27A, 0x9C8A6A, 0x8A7A5E};

    /** 減った分の、遅れて縮む明るい帯（バーごと）。 */
    private static final Map<UUID, Trail> TRAILS = new HashMap<>();

    private BossHpBar() {
    }

    /** このModのボスのバーか（名前に、ボスの種類と帯の本数が入っている）。 */
    record Info(Component name, int bars, String key) {
    }

    static Info parse(Component name) {
        if (!(name.getContents() instanceof TranslatableContents contents) || !"boss.amemaru.bar".equals(contents.getKey())) {
            return null;
        }
        Object[] args = contents.getArgs();
        if (args.length < 3) {
            return null;
        }
        Component display = args[0] instanceof Component c ? c : Component.literal(String.valueOf(args[0]));
        int bars;
        try {
            bars = Math.max(1, Math.min(5, Integer.parseInt(String.valueOf(args[1]))));
        } catch (NumberFormatException e) {
            bars = 1;
        }
        return new Info(display, bars, String.valueOf(args[2]));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent bar = event.getBossEvent();
        Info info = parse(bar.getName());
        if (info == null) {
            return;
        }
        event.setCanceled(true);
        GuiGraphics graphics = event.getGuiGraphics();
        int x = graphics.guiWidth() / 2 - TEX_W / 2;
        int top = event.getY() - 5;
        draw(graphics, x, top, info, bar.getProgress(), bar.getId());
        event.setIncrement(TEX_H + 10);
        StoryHud.noteBossBar(top + TEX_H + 2);
    }

    /** 帯を描く（{@code top}：枠の上の端。名前はその上）。 */
    static void draw(GuiGraphics graphics, int x, int top, Info info, float progress, UUID id) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        float p = Math.max(0.0F, Math.min(1.0F, progress));
        int bars = info.bars();
        // 今の帯（0 が最後の1本）と、その中の割合
        int index = p >= 1.0F ? bars - 1 : (int) Math.floor(p * bars);
        index = Math.max(0, Math.min(bars - 1, index));
        float within = p <= 0.0F ? 0.0F : Math.min(1.0F, p * bars - index);
        int[] colors = colors(info.key(), bars);

        // 枠（ボスごとの絵）
        ResourceLocation frame = AmemaruMod.id("textures/gui/boss_bar/" + (info.key().isEmpty() ? "default" : info.key()) + ".png");
        graphics.blit(frame, x, top, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);

        int fx = x + FILL_X;
        int fy = top + FILL_Y;
        graphics.fill(fx, fy, fx + FILL_W, fy + FILL_H, SLOT);
        // 次の帯の色を敷く（暗く）
        if (index > 0) {
            graphics.fill(fx, fy, fx + FILL_W, fy + FILL_H, 0xFF000000 | darker(colors[index - 1], 0.55F));
        }
        // 遅れて縮む明るい分
        float trail = trail(id, p);
        float trailWithin = Math.max(within, Math.min(1.0F, trail * bars - index));
        int now = Math.round(FILL_W * within);
        int lag = Math.round(FILL_W * trailWithin);
        if (lag > now) {
            graphics.fill(fx + now, fy, fx + lag, fy + FILL_H, 0xFFFFF4D8);
        }
        // 今の帯
        int color = colors[index];
        if (now > 0) {
            graphics.fill(fx, fy, fx + now, fy + FILL_H, 0xFF000000 | color);
            graphics.fill(fx, fy, fx + now, fy + 1, 0xFF000000 | lighter(color, 0.45F));
            graphics.fill(fx, fy + FILL_H - 1, fx + now, fy + FILL_H, 0xFF000000 | darker(color, 0.7F));
        }
        // 10%ごとの目盛り
        for (int i = 1; i < 10; i++) {
            int tx = fx + FILL_W * i / 10;
            graphics.fill(tx, fy + 1, tx + 1, fy + FILL_H - 1, 0x55000000);
        }
        // 名前と、残りの本数（×3）
        int nameY = top - 5;
        int nameWidth = font.width(info.name());
        graphics.drawString(font, info.name(), x + TEX_W / 2 - nameWidth / 2, nameY, 0xFFFFFF, true);
        if (bars > 1) {
            String count = "×" + (index + 1);
            Component label = Component.literal(count).withStyle(net.minecraft.ChatFormatting.BOLD);
            graphics.drawString(font, label, x + TEX_W / 2 + nameWidth / 2 + 6, nameY, color, true);
        }
    }

    /** 帯の色（index 0 が最後の1本。上の帯ほど index が大きい）。 */
    static int[] colors(String key, int bars) {
        int[] palette = PALETTES.getOrDefault(key, DEFAULT_PALETTE);
        int[] out = new int[bars];
        if (bars == 1) {
            out[0] = palette[0];
            return out;
        }
        out[0] = LAST;
        for (int i = 1; i < bars; i++) {
            // 上の帯（index = bars - 1）がパレットのはじめ
            out[i] = palette[Math.min(palette.length - 1, bars - 1 - i)];
        }
        return out;
    }

    /** 遅れて縮む帯の様子（バーごと）。 */
    private static final class Trail {
        float value;
        float last;
        long hitAt;
        long drawnAt;

        Trail(float progress, long now) {
            value = progress;
            last = progress;
            hitAt = now;
            drawnAt = now;
        }
    }

    /** 遅れて縮む値（減ってから0.4秒待ち、1秒に全体の30%ずつ縮む。フレームの速さによらない）。 */
    private static float trail(UUID id, float progress) {
        long now = System.currentTimeMillis();
        Trail state = TRAILS.computeIfAbsent(id, k -> new Trail(progress, now));
        if (progress < state.last - 1.0E-4F) {
            state.hitAt = now;
        }
        float seconds = Math.min(0.25F, (now - state.drawnAt) / 1000.0F);
        if (progress > state.value) {
            state.value = progress;
        } else if (now - state.hitAt > 400L) {
            state.value = Math.max(progress, state.value - 0.3F * seconds);
        }
        state.last = progress;
        state.drawnAt = now;
        if (TRAILS.size() > 16) {
            TRAILS.keySet().removeIf(key -> !key.equals(id));
        }
        return state.value;
    }

    private static int darker(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f);
        int g = (int) (((rgb >> 8) & 0xFF) * f);
        int b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static int lighter(int rgb, float f) {
        int r = ((rgb >> 16) & 0xFF);
        int g = ((rgb >> 8) & 0xFF);
        int b = (rgb & 0xFF);
        r = (int) (r + (255 - r) * f);
        g = (int) (g + (255 - g) * f);
        b = (int) (b + (255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }
}

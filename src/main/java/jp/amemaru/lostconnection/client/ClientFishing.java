package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.client.screen.FishingScreen;
import jp.amemaru.lostconnection.client.screen.UiStyle;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.FishingC2S;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * 釣り（クライアント。Phase 7 その2）。
 *
 * <ul>
 *   <li>糸の張りのミニゲーム（Phase 0-A 15章「浮きが沈んだら合わせ、そのあと数秒間『糸の張り』のゲージを、切れないように保つ」）：
 *       右クリックを押している間は巻いて張りが上がり、離すとゆるむ。魚は波のように、ときどき強く引く。張りが帯（よい所）の中に
 *       あるほど「寄せ」が進み、いっぱいになると釣り上げる。張りがいちばん上に届くと糸が切れ、下に落ちたままだと逃げられる</li>
 *   <li>ミニゲームの間は、右クリックで竿を投げ直さない（入力を止める）</li>
 *   <li>釣り大会の残り時間と順位（画面の上の真ん中）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class ClientFishing {
    private static boolean active;
    private static int id;
    private static double tension;
    private static double lastTension;
    private static double progress;
    private static int age;
    private static int ticks;
    private static int escape;
    private static int limit;
    private static int lowTicks;
    private static double center;
    private static double half;
    private static double reel;
    private static double slack;
    private static double pull;
    private static double burst;
    private static double decay;
    private static double difficulty;
    private static int stars;
    private static double burstVel;
    private static int nextBurst;
    private static RandomSource random = RandomSource.create();
    /** 自動の確認の写真：張りを帯の真ん中に保つように、自動で巻く。 */
    private static boolean auto;
    private static Component endText = Component.empty();
    private static int endShow;
    private static int endColor;

    private static CompoundTag tournament = new CompoundTag();
    private static long tournamentAt;

    public static final IGuiOverlay OVERLAY = ClientFishing::render;

    private ClientFishing() {
    }

    public static void handle(String kind, CompoundTag data) {
        switch (kind) {
            case "start" -> start(data);
            case "end" -> active = false;
            case "screen" -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (data.getBoolean("refresh") && minecraft.screen instanceof FishingScreen screen) {
                    screen.update(data);
                } else {
                    minecraft.setScreen(new FishingScreen(data));
                }
            }
            case "tournament" -> {
                tournament = data;
                tournamentAt = System.currentTimeMillis();
                if (Minecraft.getInstance().screen instanceof FishingScreen screen) {
                    screen.updateTournament(data);
                }
            }
            default -> {
            }
        }
    }

    private static void start(CompoundTag d) {
        active = true;
        id = d.getInt("id");
        stars = d.getInt("stars");
        difficulty = d.getDouble("difficulty");
        center = d.getDouble("center");
        half = d.getDouble("half");
        reel = d.getDouble("reel");
        slack = d.getDouble("slack");
        pull = d.getDouble("pull");
        burst = d.getDouble("burst");
        decay = d.contains("decay") ? d.getDouble("decay") : 0.5;
        ticks = Math.max(10, d.getInt("ticks"));
        escape = Math.max(5, d.getInt("escape"));
        limit = Math.max(40, d.getInt("limit"));
        random = RandomSource.create(d.getLong("seed"));
        tension = center;
        lastTension = center;
        progress = 0.25;
        age = 0;
        lowTicks = 0;
        burstVel = 0;
        nextBurst = 20;
        endShow = 0;
    }

    public static boolean active() {
        return active;
    }

    /** 自動の確認の写真：自動で巻く。 */
    public static void setAuto(boolean value) {
        auto = value;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (endShow > 0) {
            endShow--;
        }
        if (!active) {
            return;
        }
        if (minecraft.player == null) {
            active = false;
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        boolean hold = auto ? tension < center - half * 0.1 : minecraft.options.keyUse.isDown() && minecraft.screen == null;
        lastTension = tension;
        tension += hold ? reel : -slack;
        // 魚の引き：ゆるい波と、ときどきの強い引き（上にも下にも）
        tension += Math.sin(age * (0.16 + difficulty * 0.14)) * pull;
        if (age >= nextBurst) {
            burstVel = (random.nextBoolean() ? 1.0 : -1.0) * burst * (0.6 + random.nextDouble() * 0.8);
            nextBurst = age + 14 + random.nextInt(Math.max(4, 30 - (int) Math.round(difficulty * 16)));
        }
        tension += burstVel;
        burstVel *= 0.78;
        boolean inZone = Math.abs(tension - center) <= half;
        progress = Mth.clamp(progress + (inZone ? 1.0 / ticks : -decay / ticks), 0.0, 1.0);
        if (hold && age % 4 == 0) {
            minecraft.player.playSound(SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.15F, 1.6F + (float) tension * 0.3F);
        }
        if (tension > 0.86 && age % 5 == 0) {
            minecraft.player.playSound(SoundEvents.TRIPWIRE_CLICK_ON, 0.3F, 1.8F);
        }
        age++;
        if (tension >= 1.0) {
            finish(false, "snap");
        } else if (progress >= 1.0) {
            finish(true, "");
        } else if (age >= limit) {
            finish(false, "escape");
        } else if (tension <= 0.0) {
            tension = 0.0;
            if (++lowTicks >= escape) {
                finish(false, "escape");
            }
        } else {
            lowTicks = 0;
        }
    }

    private static void finish(boolean success, String reason) {
        active = false;
        CompoundTag args = new CompoundTag();
        args.putInt("id", id);
        args.putBoolean("success", success);
        args.putString("reason", reason);
        ModNetwork.sendToServer(new FishingC2S("result", args));
        endText = Component.translatable(success ? "fishing.amemaru.mini.caught" : "snap".equals(reason)
                ? "fishing.amemaru.mini.snapped" : "fishing.amemaru.mini.escaped");
        endColor = success ? UiStyle.TEXT_GOLD : UiStyle.TEXT_BAD;
        endShow = 30;
    }

    /** ミニゲームの間は、右クリックで竿を投げ直さない。 */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (active && event.isUseItem()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** ログアウトしたら止める。 */
    public static void clear() {
        active = false;
        endShow = 0;
        tournament = new CompoundTag();
    }

    // ================================================================ 画面

    private static void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics graphics, float partialTick, int width,
                               int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        Font font = minecraft.font;
        renderTournament(graphics, font, width);
        if (!active && endShow <= 0) {
            return;
        }
        int w = 150;
        int x0 = (width - w) / 2;
        int y0 = height / 2 + 22;
        UiStyle.panel(graphics, x0, y0, x0 + w, y0 + 40);
        if (!active) {
            graphics.drawCenteredString(font, endText, width / 2, y0 + 16, endColor);
            return;
        }
        Component title = Component.translatable(age < 40 ? "fishing.amemaru.mini.title_hooked" : "fishing.amemaru.mini.title",
                "★".repeat(Math.max(1, stars)));
        graphics.drawString(font, title, x0 + 6, y0 + 4, UiStyle.TEXT_HEADER, false);
        // 張りの帯
        int bx0 = x0 + 6;
        int bx1 = x0 + w - 6;
        int by = y0 + 15;
        graphics.fill(bx0, by, bx1, by + 9, 0xFF0B1022);
        int span = bx1 - bx0 - 2;
        int zx0 = bx0 + 1 + (int) Math.round(span * Mth.clamp(center - half, 0.0, 1.0));
        int zx1 = bx0 + 1 + (int) Math.round(span * Mth.clamp(center + half, 0.0, 1.0));
        double t = Mth.lerp(partialTick, lastTension, tension);
        boolean inZone = Math.abs(t - center) <= half;
        graphics.fill(zx0, by + 1, zx1, by + 8, inZone ? 0xFF3FD2B4 : 0xFF2A7F78);
        // 切れそうな所（右の端）
        graphics.fill(bx1 - 1 - span / 10, by + 1, bx1 - 1, by + 8, 0xFF7A2A2A);
        int mx = bx0 + 1 + (int) Math.round(span * Mth.clamp(t, 0.0, 1.0));
        int marker = t > 0.86 ? 0xFFFF6A6A : 0xFFFFFFFF;
        graphics.fill(mx - 1, by - 2, mx + 1, by + 11, marker);
        // 寄せ（いっぱいで釣り上げる）
        int py = by + 13;
        graphics.fill(bx0, py, bx1, py + 4, 0xFF0B1022);
        graphics.fill(bx0 + 1, py + 1, bx0 + 1 + (int) Math.round(span * progress), py + 3, 0xFFFFD866);
        if (age < 60 || auto) {
            Component hint = Component.translatable("fishing.amemaru.mini.hint");
            graphics.drawCenteredString(font, hint, width / 2, y0 + 42, UiStyle.TEXT_DIM);
        }
    }

    /** 釣り大会（画面の上の真ん中）：残り時間・上位3人・自分。 */
    private static void renderTournament(GuiGraphics graphics, Font font, int width) {
        if (!tournament.getBoolean("on")) {
            return;
        }
        long passed = System.currentTimeMillis() - tournamentAt;
        long startIn = Math.max(0, tournament.getLong("startIn") - passed);
        long endIn = Math.max(0, tournament.getLong("endIn") - passed);
        // 上の真ん中の「次の階がひらくまで」の札の下。左のパーティ（幅112）と右のクエストの札（幅150）に重ならないよう、
        // 狭い画面では真ん中より左に寄せる
        int w = 144;
        int x0 = Math.max(124, Math.min((width - w) / 2, width - 150 - 10 - w));
        int y0 = Math.max(6, StoryHud.guideBottom() + 4);
        var top = tournament.getList("top", Tag.TAG_COMPOUND);
        int lines = 1 + Math.max(1, top.size()) + 1;
        UiStyle.panel(graphics, x0, y0, x0 + w, y0 + 6 + lines * 10);
        Component head = startIn > 0
                ? Component.translatable("fishing.amemaru.hud.soon", tournament.getInt("number"), clock(startIn))
                : Component.translatable("fishing.amemaru.hud.left", tournament.getInt("number"), clock(endIn));
        graphics.drawCenteredString(font, head, x0 + w / 2, y0 + 4, UiStyle.TEXT_GOLD);
        int y = y0 + 14;
        if (top.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("fishing.amemaru.hud.none"), x0 + w / 2, y, UiStyle.TEXT_DIM);
            y += 10;
        }
        for (int i = 0; i < top.size(); i++) {
            CompoundTag one = top.getCompound(i);
            String line = UiStyle.clip(font, Component.translatable("fishing.amemaru.hud.row", one.getInt("rank"), one.getString("name"),
                    one.getLong("score"), one.getString("fish")).getString(), w - 10);
            graphics.drawString(font, line, x0 + 5, y, i == 0 ? UiStyle.TEXT_GOLD : UiStyle.TEXT, false);
            y += 10;
        }
        Component mine = tournament.contains("mine")
                ? Component.translatable("fishing.amemaru.hud.mine", tournament.getCompound("mine").getInt("rank"),
                tournament.getCompound("mine").getLong("score"))
                : Component.translatable("fishing.amemaru.hud.mine_none");
        graphics.drawString(font, UiStyle.clip(font, mine.getString(), w - 10), x0 + 5, y, 0xFFE3A6FF, false);
    }

    private static String clock(long millis) {
        long s = (millis + 999) / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}

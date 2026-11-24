package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.screen.UiStyle;
import jp.amemaru.lostconnection.network.s2c.AnnouncementS2C;
import jp.amemaru.lostconnection.network.s2c.StoryTextS2C;
import jp.amemaru.lostconnection.network.s2c.WorldStateS2C;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;

/**
 * 物語と進行の画面の表示（Phase 5）：
 * <ul>
 *   <li>暗転（演出・オープニングの準備待ち）</li>
 *   <li>章題・階の名前（画面の真ん中）、字幕（画面の下）、石柱の古代文字（古代の文字から読める字に変わる）、戦い方の手がかり</li>
 *   <li>知らせの帯（階がひらいた・日が進んだ・予告）と、予告のカウントダウン（画面の上）</li>
 *   <li>はじめの手引き（画面の右上：今することと、行き先の向きと距離）</li>
 * </ul>
 */
public final class StoryHud {
    public static final IGuiOverlay OVERLAY = StoryHud::render;
    private static final Style ALT = Style.EMPTY.withFont(ResourceLocation.withDefaultNamespace("alt"));
    private static final String GLYPHS = "abcdefghijklmnopqrstuvwxyz";

    private StoryHud() {
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        // 暗転
        float fade = ScenePlayer.isActive() ? ScenePlayer.fade(partialTick) : 0.0F;
        if (ClientStory.openingPending() && !ScenePlayer.isActive()) {
            fade = 1.0F;
        }
        if (fade > 0.001F) {
            int a = Mth.clamp(Math.round(fade * 255.0F), 0, 255);
            graphics.fill(0, 0, width, height, (a << 24) | ScenePlayer.fadeColor());
        }
        if (ClientStory.openingPending() && !ScenePlayer.isActive()) {
            // 準備を待っている間：まわりが読み込めるまで、真っ暗のまま小さく知らせる
            Component wait = Component.translatable("hud.amemaru.story.waiting");
            int alpha = (int) (128 + 100 * Math.sin(Util.getMillis() / 400.0));
            graphics.drawString(font, wait, (width - font.width(wait)) / 2, height / 2 + 30, (alpha << 24) | 0x9AA6BF, false);
            return;
        }
        boolean hud = !ScenePlayer.hideHud();
        for (ClientStory.ShownText text : ClientStory.texts()) {
            switch (text.kind()) {
                case TITLE -> renderTitle(graphics, font, text, width, height);
                case SUBTITLE -> renderSubtitle(graphics, font, text, width, height);
                case ANCIENT -> renderAncient(graphics, font, text, width, height);
                case TIP -> renderTip(graphics, font, text, width);
            }
        }
        if (!hud) {
            return;
        }
        renderAnnouncements(graphics, font, width);
        renderBossCast(graphics, font, width, partialTick);
        renderBossStatus(graphics, font, width);
        renderCountdowns(graphics, font, width);
        renderGuide(graphics, font, width, minecraft);
    }

    /** 出てから消えるまでの濃さ（はじめと終わりを、ふわっと）。 */
    private static float alpha(ClientStory.ShownText text, float in, float out) {
        float age = text.age();
        float total = text.durationMillis() / 1000.0F;
        return Mth.clamp(Math.min(age / in, (total - age) / out), 0.0F, 1.0F);
    }

    private static int color(float alpha, int rgb) {
        return (Math.max(4, Math.round(alpha * 255.0F)) << 24) | (rgb & 0xFFFFFF);
    }

    private static void renderTitle(GuiGraphics graphics, Font font, ClientStory.ShownText text, int width, int height) {
        float a = alpha(text, 0.8F, 1.0F);
        if (a <= 0.02F) {
            return;
        }
        int y = height / 2 - 40;
        if (!text.speaker().isEmpty()) {
            graphics.drawString(font, text.speaker(), (width - font.width(text.speaker())) / 2, y - 16, color(a, 0xBFEFFF), true);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0F, y, 0.0F);
        graphics.pose().scale(2.2F, 2.2F, 1.0F);
        graphics.drawString(font, text.text(), -font.width(text.text()) / 2, 0, color(a, 0xFFFFFF), true);
        graphics.pose().popPose();
        int lineW = Math.round((font.width(text.text()) * 2.2F + 40) * Math.min(1.0F, text.age() / 0.8F));
        graphics.fill(width / 2 - lineW / 2, y + 24, width / 2 + lineW / 2, y + 25, color(a * 0.7F, 0x8FD3FF));
    }

    private static void renderSubtitle(GuiGraphics graphics, Font font, ClientStory.ShownText text, int width, int height) {
        float a = alpha(text, 0.3F, 0.5F);
        if (a <= 0.02F) {
            return;
        }
        boolean core = "？？？".equals(text.speaker());
        List<String> lines = UiStyle.wrap(font, text.text(), Math.min(width - 60, 320));
        int h = lines.size() * 11 + 6;
        int y = height - 78 - h;
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, font.width(line));
        }
        int x = (width - w) / 2;
        graphics.fill(x - 10, y - 4, x + w + 10, y + h, color(a * 0.55F, 0x000000));
        if (!text.speaker().isEmpty()) {
            graphics.drawString(font, text.speaker(), x - 6, y - 15, color(a, core ? 0xCFF5FF : 0xBFEFFF), true);
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            // 大結晶の声は、ゆれる淡い光の字
            int rgb = core ? (0xCFF5FF ^ ((int) (Math.sin(Util.getMillis() / 120.0 + i) * 8) & 0x0F)) : 0xFFFFFF;
            graphics.drawString(font, line, (width - font.width(line)) / 2, y + 2 + i * 11, color(a, rgb), true);
        }
    }

    private static void renderAncient(GuiGraphics graphics, Font font, ClientStory.ShownText text, int width, int height) {
        float a = alpha(text, 0.4F, 0.8F);
        if (a <= 0.02F) {
            return;
        }
        int y = height / 2 + 24;
        if (!text.speaker().isEmpty()) {
            graphics.drawString(font, text.speaker(), (width - font.width(text.speaker())) / 2, y - 14, color(a * 0.8F, 0x9CFFB0), true);
        }
        // 11月24日：長い文（4階の壁画の歴史・5階の守護者の記録核など）は、画面の幅に合わせて折り返す
        // （前は1行のままで、画面の左右からはみ出して読めなかった）
        float scale = 1.4F;
        int lineH = 15;
        List<String> lines = UiStyle.wrap(font, text.text(), Math.max(80, (int) ((width - 60) / scale)));
        // はじめの1.2秒は古代の文字。そのあと、大結晶の仲立ちで読める字に変わる
        float morph = Mth.clamp((text.age() - 0.9F) / 0.6F, 0.0F, 1.0F);
        if (morph < 1.0F) {
            for (int line = 0; line < lines.size(); line++) {
                StringBuilder glyphs = new StringBuilder();
                int n = Math.max(6, lines.get(line).length() * 2);
                long seed = text.start() + line * 977L;
                for (int i = 0; i < n; i++) {
                    glyphs.append(GLYPHS.charAt((int) Math.abs((seed * 31 + i * 7919L) % GLYPHS.length())));
                }
                Component alt = Component.literal(glyphs.toString()).withStyle(ALT);
                graphics.drawString(font, alt, (width - font.width(alt)) / 2, y + line * lineH, color(a * (1.0F - morph), 0x9CFFB0), true);
            }
        }
        if (morph > 0.0F) {
            for (int line = 0; line < lines.size(); line++) {
                String one = lines.get(line);
                graphics.pose().pushPose();
                graphics.pose().translate(width / 2.0F, y - 2 + line * lineH, 0.0F);
                graphics.pose().scale(scale, scale, 1.0F);
                graphics.drawString(font, one, -font.width(one) / 2, 0, color(a * morph, 0xE8FFF0), true);
                graphics.pose().popPose();
            }
        }
    }

    private static void renderTip(GuiGraphics graphics, Font font, ClientStory.ShownText text, int width) {
        float a = alpha(text, 0.3F, 0.6F);
        if (a <= 0.02F) {
            return;
        }
        List<String> lines = UiStyle.wrap(font, text.text(), 230);
        int w = 250;
        int h = 16 + lines.size() * 10;
        int x = (width - w) / 2;
        // ボスの帯・技の名前・状態・「〇〇まで」の札の下に出す（重ならないように。前の絵の位置を使う）
        int y = Math.max(56, Math.max(countdownBottom, castBottom) + 4);
        graphics.fill(x, y, x + w, y + h, color(a * 0.75F, 0x0B1022));
        graphics.fill(x, y, x + w, y + 1, color(a, 0xFFF2A8));
        graphics.drawString(font, Component.translatable("hud.amemaru.story.tip"), x + 6, y + 4, color(a, 0xFFF2A8), false);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x + 10, y + 15 + i * 10, color(a, 0xFFFFFF), false);
        }
    }

    /**
     * 知らせ（初撃破・物語など）が出ているか。11月24日：右上のクエストの一覧と重なって読めなかったので、出ている間は一覧をしまう
     * （{@link GuildHud}）。
     */
    public static boolean announcementShown() {
        long now = Util.getMillis();
        for (ClientStory.ShownAnnouncement shown : ClientStory.announcements()) {
            if (now - shown.start() < ClientStory.ANNOUNCE_MILLIS) {
                return true;
            }
        }
        return false;
    }

    private static void renderAnnouncements(GuiGraphics graphics, Font font, int width) {
        List<ClientStory.ShownAnnouncement> list = ClientStory.announcements();
        int y = 28;
        for (ClientStory.ShownAnnouncement shown : list) {
            long age = Util.getMillis() - shown.start();
            float a = Mth.clamp(Math.min(age / 300.0F, (ClientStory.ANNOUNCE_MILLIS - age) / 800.0F), 0.0F, 1.0F);
            if (a <= 0.02F) {
                continue;
            }
            AnnouncementS2C.Kind kind = shown.kind();
            Component label = Component.translatable(kind.labelKey());
            List<String> lines = UiStyle.wrap(font, shown.text(), Math.min(width - 80, 300));
            int w = 0;
            for (String line : lines) {
                w = Math.max(w, font.width(line));
            }
            w = Math.max(w, font.width(label)) + 24;
            int h = 14 + lines.size() * 10 + 4;
            int x = (width - w) / 2;
            graphics.fill(x, y, x + w, y + h, color(a * 0.8F, 0x0B1022));
            graphics.fill(x, y, x + w, y + 1, color(a, kind.color()));
            graphics.fill(x, y + h - 1, x + w, y + h, color(a, kind.color()));
            graphics.drawString(font, label, (width - font.width(label)) / 2, y + 3, color(a, kind.color()), false);
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawString(font, lines.get(i), (width - font.width(lines.get(i))) / 2, y + 14 + i * 10, color(a, 0xFFFFFF), true);
            }
            y += h + 4;
        }
    }

    private static void renderCountdowns(GuiGraphics graphics, Font font, int width) {
        WorldStateS2C world = ClientStory.world();
        int y = 4;
        // ボスのHPの帯が出ているときは、その下に出す（ボスの技の名前が出ていれば、さらにその下）
        if (System.currentTimeMillis() - bossSeen < 250L) {
            y = Math.max(y, bossBottom + 2);
        }
        if (castBottom > 0) {
            y = Math.max(y, castBottom + 2);
        }
        countdownBottom = 0;
        for (WorldStateS2C.Countdown countdown : world.countdowns()) {
            long left = ClientStory.remaining(countdown) / 1000L;
            String text = Component.translatable("hud.amemaru.story.countdown", countdown.text(),
                    String.format(java.util.Locale.ROOT, "%d:%02d", left / 60, left % 60)).getString();
            int w = font.width(text) + 12;
            int x = (width - w) / 2;
            graphics.fill(x, y, x + w, y + 12, 0xB00B1022);
            graphics.drawString(font, text, x + 6, y + 2, 0xFFFFE9A8, false);
            countdownBottom = y + 12;
            y += 14;
        }
    }

    /** ボスの技の名前の札の下の端（出していなければ 0）。 */
    private static int castBottom;

    /**
     * ボスの技の名前（ボスのHPの帯の下。11月24日「ボスの攻撃がわかりづらい」）。いちばん近いボスが技を出し始めたら、
     * 「▶ 枝の薙ぎ払い」のように出し、下の細い帯で、当たるまでの残りを見せる。赤はよける技、青はみんなで受ける一撃。
     */
    private static void renderBossCast(GuiGraphics graphics, Font font, int width, float partialTick) {
        castBottom = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        jp.amemaru.lostconnection.enemy.boss.AmemaruBoss boss = null;
        double best = 48.0 * 48.0;
        for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof jp.amemaru.lostconnection.enemy.boss.AmemaruBoss candidate && candidate.isAlive()
                    && !candidate.cast().isEmpty()) {
                double d = candidate.distanceToSqr(minecraft.player);
                if (d < best) {
                    best = d;
                    boss = candidate;
                }
            }
        }
        if (boss == null) {
            return;
        }
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType());
        String key = "cast." + id.getNamespace() + "." + id.getPath() + "." + boss.cast();
        if (!net.minecraft.client.resources.language.I18n.exists(key)) {
            return;
        }
        boolean share = "share".equals(boss.cast());
        int accent = share ? 0x48C8FF : 0xFF6A5A;
        String text = "\u25B6 " + net.minecraft.client.resources.language.I18n.get(key);
        int w = font.width(text) + 16;
        int x = (width - w) / 2;
        int y = System.currentTimeMillis() - bossSeen < 250L ? bossBottom + 2 : 4;
        graphics.fill(x, y, x + w, y + 15, 0xC80B1022);
        graphics.fill(x, y, x + 2, y + 15, 0xFF000000 | accent);
        graphics.drawString(font, text, x + 8, y + 3, 0xFF000000 | (share ? 0xC8F0FF : 0xFFDCD4), false);
        float progress = castProgress(minecraft, boss, share, partialTick);
        if (progress >= 0.0F) {
            int bar = Math.round((w - 4) * (1.0F - progress));
            graphics.fill(x + 2, y + 13, x + 2 + bar, y + 15, 0xFF000000 | accent);
        }
        castBottom = y + 15;
    }

    /**
     * ボスの今の様子（技の名前の下。Phase 8 その2：3階のボスの「炉心は水の中」「炉心がむき出し！ あと 18 秒」）。
     * 金色は攻めどき、灰色は守りの固いとき。{@code castBottom} を、この札の下の端にする（「〇〇まで」の札をその下に出す）。
     */
    private static void renderBossStatus(GuiGraphics graphics, Font font, int width) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        jp.amemaru.lostconnection.enemy.boss.AmemaruBoss boss = null;
        double best = 48.0 * 48.0;
        for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof jp.amemaru.lostconnection.enemy.boss.AmemaruBoss candidate && candidate.isAlive()
                    && !candidate.status().isEmpty()) {
                double d = candidate.distanceToSqr(minecraft.player);
                if (d < best) {
                    best = d;
                    boss = candidate;
                }
            }
        }
        if (boss == null) {
            return;
        }
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType());
        boolean good = boss.status().startsWith("open");
        int accent = good ? 0xF2C94C : 0x8FA3B8;
        String text;
        if (boss.status().startsWith("song:")) {
            // 11月24日（4階のボス）：歌った音の並び（白 → 青 → 金 …）。歌っている間だけ出る
            StringBuilder notes = new StringBuilder();
            for (String note : boss.status().substring(5).split(",")) {
                if (notes.length() > 0) {
                    notes.append(" \u2192 ");
                }
                notes.append(net.minecraft.client.resources.language.I18n.get("status.amemaru.note." + note));
            }
            accent = 0xC8A8FF;
            text = "\u266A " + net.minecraft.client.resources.language.I18n.get("status." + id.getNamespace() + "." + id.getPath() + ".song",
                    notes.toString());
        } else {
            String key = "status." + id.getNamespace() + "." + id.getPath() + "." + boss.status();
            if (!net.minecraft.client.resources.language.I18n.exists(key)) {
                return;
            }
            text = (good ? "\u25C6 " : "\u25C7 ") + net.minecraft.client.resources.language.I18n.get(key, boss.statusSeconds());
        }
        // 長い文は、画面の幅に合わせて折り返す（小さな画面でも、右のはしの欄と重ならない）
        List<String> lines = UiStyle.wrap(font, text, Math.max(120, Math.min(360, width - 40)));
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, font.width(line));
        }
        w += 16;
        int h = 3 + lines.size() * 10;
        int x = (width - w) / 2;
        int y = castBottom > 0 ? castBottom + 2 : System.currentTimeMillis() - bossSeen < 250L ? bossBottom + 2 : 4;
        graphics.fill(x, y, x + w, y + h, 0xC80B1022);
        graphics.fill(x, y, x + 2, y + h, 0xFF000000 | accent);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x + 8, y + 3 + i * 10, 0xFF000000 | (good ? 0xFFF0C0 : 0xD8E2EC), false);
        }
        castBottom = y + h;
    }

    /** 技が当たるまでの進み（0〜1。分からなければ -1）。地面の印か、ボスの予兆から。 */
    private static float castProgress(Minecraft minecraft, jp.amemaru.lostconnection.enemy.boss.AmemaruBoss boss, boolean share,
                                      float partialTick) {
        if (share) {
            jp.amemaru.lostconnection.entity.GroundMarker nearest = null;
            double best = 48.0 * 48.0;
            for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity instanceof jp.amemaru.lostconnection.entity.GroundMarker marker
                        && marker.kind() == jp.amemaru.lostconnection.entity.GroundMarker.KIND_SHARE) {
                    double d = marker.distanceToSqr(boss);
                    if (d < best) {
                        best = d;
                        nearest = marker;
                    }
                }
            }
            return nearest == null ? -1.0F : nearest.progress(partialTick);
        }
        if (boss.telegraph() != jp.amemaru.lostconnection.enemy.AmemaruMob.TELEGRAPH_NONE && boss.telegraphTicks() > 0) {
            return Mth.clamp(boss.telegraphAge(partialTick) / boss.telegraphTicks(), 0.0F, 1.0F);
        }
        return -1.0F;
    }

    /** ボスのHPの帯の下の端と、最後に描いた時刻（{@link ClientForgeEvents#onBossBar}）。 */
    private static int bossBottom;
    private static long bossSeen;

    /**
     * ボスの帯が出ているか（いま出ているか、出たばかり）。ボスと戦っている間は、ギルドのクエストの一覧を出さない
     * （画面の上がボスの帯・技の名前・状態・手がかりでいっぱいになり、小さな画面では重なるため。Phase 8 その2）。
     */
    public static boolean bossBarShown() {
        return System.currentTimeMillis() - bossSeen < 1500L;
    }

    /** ボスのHPの帯を1本描いた（{@code bottom}：その帯の下の端）。同じ絵の中の帯は、いちばん下のものを覚える。 */
    public static void noteBossBar(int bottom) {
        long now = System.currentTimeMillis();
        bossBottom = now - bossSeen > 50L ? bottom : Math.max(bossBottom, bottom);
        bossSeen = now;
    }

    /** 上の真ん中の「〇〇まで」の札の下の端（出していなければ 0。釣り大会の札を、その下に出すため。Phase 7 その2） */
    private static int countdownBottom;

    public static int countdownBottom() {
        return countdownBottom;
    }

    /** はじめの手引きの下の端（出していなければ 0。ギルドのクエストの一覧を、その下に出すため。Phase 6 その3） */
    private static int guideBottom;

    public static int guideBottom() {
        return guideBottom;
    }

    private static void renderGuide(GuiGraphics graphics, Font font, int width, Minecraft minecraft) {
        guideBottom = 0;
        var story = ClientStory.story();
        if (story.guideIndex() < 0 || story.guideText().isEmpty()
                || minecraft.screen instanceof jp.amemaru.lostconnection.client.screen.DialogueScreen) {
            return;
        }
        int w = 150;
        List<String> lines = UiStyle.wrap(font, story.guideText(), w - 12);
        Vec3 target = ClientStory.guideTarget();
        int h = 16 + lines.size() * 10 + (target != null ? 12 : 0);
        int x = width - w - 6;
        int y = 6;
        guideBottom = y + h;
        graphics.fill(x, y, x + w, y + h, 0xA00B1022);
        graphics.fill(x, y, x + 1, y + h, 0xFF8FD3FF);
        graphics.drawString(font, Component.translatable("hud.amemaru.story.guide", story.guideIndex() + 1, story.guideTotal()), x + 6, y + 4,
                0xFFBFEFFF, false);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x + 6, y + 15 + i * 10, 0xFFFFFFFF, false);
        }
        if (target != null && minecraft.player != null) {
            Vec3 d = target.subtract(minecraft.player.position());
            double distance = Math.sqrt(d.x * d.x + d.z * d.z);
            float yaw = (float) (Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F;
            float rel = Mth.wrapDegrees(yaw - minecraft.player.getYRot());
            String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
            int sector = Math.floorMod(Math.round(rel / 45.0F), 8);
            String arrow = distance < 3.0 ? "●" : arrows[sector];
            graphics.drawString(font, arrow + " " + Math.round(distance) + "m", x + 6, y + 15 + lines.size() * 10, 0xFFFFF2A8, false);
        }
    }
}

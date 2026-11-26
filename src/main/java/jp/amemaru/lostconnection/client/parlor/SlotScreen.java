package jp.amemaru.lostconnection.client.parlor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * スロットの台の画面（11月27日【補完】。本物のパチスロと同じく、レバー（スペース）でリールが回り、止めボタン（Z・X・C）で止める）。
 *
 * <ul>
 *   <li>リールは押した所から最大4コマすべって止まる（サーバーの制御。すべる様子も見せる）。有効ラインは中段</li>
 *   <li>Aタイプ：左下の告知ランプ（ノットランプ）。レバーで光る「先ペカ」、3つ目を止めたあとの「後ペカ」、まれに虹色・フリーズ・
 *       遅れ。光ったら7をねらって押す（目押し）</li>
 *   <li>AT：上の液晶に、通常・チャンスゾーン・AT の残りのゲーム数と獲得枚数、上乗せ。AT の間は押し順のナビ（①②③）</li>
 * </ul>
 */
public class SlotScreen extends MachineScreen {
    private static final int ROW_H = 26;
    private static final int REEL_W = 44;
    private static final int REEL_GAP = 6;
    private static final String[] SYMBOL_FILES = {"7:seven", "B:bar", "G:grape", "R:replay", "C:cherry", "L:bell", "K:knot",
            "S:melon", "X:star"};

    private final String type;
    private final String[] reels = new String[3];
    private final int n;
    private final double speed;
    private final Map<Character, ResourceLocation> textures = new HashMap<>();
    private final Map<Character, Integer> symbolColors = new HashMap<>();
    private final JsonObject labels;
    private final Reel[] reel = {new Reel(), new Reel(), new Reel()};
    private final int waitMs;

    private boolean gameActive;
    private boolean waitingStart;
    private long stopsEnabledAt;
    private long lastLeverAt;
    private long lastFrame;
    private long startDelayUntil;
    private String effect = "";
    private boolean lampOn;
    private long lampLitAt;
    private boolean rainbowLamp;
    private int navOrder = -1;
    private int lastPay;
    private int bet;
    private String bannerText = "";
    private int bannerColor = 0xFFFFFF;
    private long bannerUntil;
    private final List<Popup> popups = new ArrayList<>();
    private final List<float[]> coins = new ArrayList<>();
    private long nextFanfare;
    private String error = "";
    private long errorUntil;

    private static final class Reel {
        double pos;
        int stage;          // 0 止まっている・1 回る・2 押した（サーバーを待つ）・3 すべる
        int pressed = -1;
        int target = -1;
        long slipStart;
        int slip;
    }

    private record Popup(String text, int color, long until) {
    }

    public SlotScreen(CompoundTag open) {
        super(open);
        this.type = model.has("type") ? model.get("type").getAsString() : "a";
        JsonArray r = model.getAsJsonArray("reels");
        for (int i = 0; i < 3; i++) {
            reels[i] = r.get(i).getAsString();
        }
        this.n = reels[0].length();
        int rpm = model.has("rpm") ? model.get("rpm").getAsInt() : 60;
        this.speed = rpm * n / 60000.0;
        this.waitMs = model.has("wait_ms") ? model.get("wait_ms").getAsInt() : 3200;
        this.labels = model.has("labels") ? model.getAsJsonObject("labels") : new JsonObject();
        for (String f : SYMBOL_FILES) {
            textures.put(f.charAt(0), AmemaruMod.id("textures/gui/parlor/sym_" + f.substring(2) + ".png"));
        }
        for (JsonElement e : model.getAsJsonArray("symbols")) {
            JsonObject s = e.getAsJsonObject();
            symbolColors.put(s.get("id").getAsString().charAt(0), ParlorDraw.parse(s.get("color").getAsString(), 0xFFFFFFFF));
        }
        java.util.Random rnd = new java.util.Random(no * 31L);
        int[] pos = state.getIntArray("pos");
        for (int i = 0; i < 3; i++) {
            reel[i].pos = pos.length == 3 && pos[i] >= 0 ? pos[i] : rnd.nextInt(n);
        }
        lampOn = state.getBoolean("lamp");
        bet = state.getInt("bet");
    }

    private String label(String key, String fallback) {
        return labels.has(key) ? labels.get(key).getAsString() : fallback;
    }

    @Override
    protected String holdingText() {
        return Component.translatable("screen.amemaru.parlor.medals", num(state.getLong("medals"))).getString();
    }

    @Override
    protected void initMachine() {
        lastFrame = System.currentTimeMillis();
    }

    // ================================================================ 操作

    private int cabX() {
        return left + 6;
    }

    private int reelX(int i) {
        int total = REEL_W * 3 + REEL_GAP * 2;
        return cabX() + (244 - total) / 2 + i * (REEL_W + REEL_GAP);
    }

    private int reelY() {
        return top + 82;
    }

    private void pullLever() {
        long now = System.currentTimeMillis();
        if (gameActive || waitingStart) {
            return;
        }
        if (now - lastLeverAt < waitMs) {
            // ウェイト（本物と同じく、1ゲームの最短の時間）：待つ間は回らない
            return;
        }
        waitingStart = true;
        lastLeverAt = now;
        ParlorDraw.play(SoundEvents.UI_BUTTON_CLICK.get(), 0.5F, 0.6F);
        send("lever");
    }

    private void pressStop(int i) {
        long now = System.currentTimeMillis();
        Reel r = reel[i];
        if (!gameActive || r.stage != 1 || now < stopsEnabledAt) {
            return;
        }
        int press = Math.floorMod((int) Math.round(r.pos), n);
        r.pos = press;
        r.pressed = press;
        r.stage = 2;
        ParlorDraw.play(SoundEvents.UI_BUTTON_CLICK.get(), 0.45F, 1.5F);
        CompoundTag args = new CompoundTag();
        args.putInt("reel", i);
        args.putInt("pos", press);
        send("stop", args);
    }

    /** 自動の確認の写真：レバー（ウェイトは待たない）。 */
    public void ciLever() {
        lastLeverAt = 0;
        pullLever();
    }

    /** 自動の確認の写真：その図柄が中段に止まるようにねらって押す（目押し。図柄の2コマ手前。左はチェリーのとなりの所をさける）。 */
    public void ciAim(int i, char symbol) {
        Reel r = reel[i];
        if (!gameActive || r.stage != 1) {
            return;
        }
        String strip = reels[i];
        int press = -1;
        for (int k = 0; k < n; k++) {
            if (strip.charAt(k) != symbol) {
                continue;
            }
            boolean cherry = strip.charAt(Math.floorMod(k - 1, n)) == 'C' || strip.charAt(Math.floorMod(k + 1, n)) == 'C';
            if (i != 0 || !cherry) {
                press = Math.floorMod(k + 2, n);
                break;
            }
        }
        if (press < 0) {
            pressStop(i);
            return;
        }
        r.pos = press;
        r.pressed = press;
        r.stage = 2;
        CompoundTag args = new CompoundTag();
        args.putInt("reel", i);
        args.putInt("pos", press);
        send("stop", args);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
            pullLever();
            return true;
        }
        if (key == GLFW.GLFW_KEY_Z || key == GLFW.GLFW_KEY_1) {
            pressStop(0);
            return true;
        }
        if (key == GLFW.GLFW_KEY_X || key == GLFW.GLFW_KEY_2) {
            pressStop(1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_C || key == GLFW.GLFW_KEY_3) {
            pressStop(2);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int y = top + 198;
            for (int i = 0; i < 3; i++) {
                int x = reelX(i) + 7;
                if (mx >= x && mx < x + 30 && my >= y && my < y + 14) {
                    pressStop(i);
                    return true;
                }
            }
            int lx = cabX() + 14;
            int ly = top + 186;
            if (mx >= lx - 2 && mx < lx + 26 && my >= ly - 2 && my < ly + 40) {
                pullLever();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    // ================================================================ サーバーから

    /** レバー・止めボタン・払い出しの知らせ。 */
    public void onSlot(CompoundTag t) {
        long now = System.currentTimeMillis();
        if (t.contains("error")) {
            waitingStart = false;
            lastLeverAt = 0;
            error = Component.translatable(t.getString("error")).getString();
            errorUntil = now + 2500;
            ParlorDraw.play(SoundEvents.NOTE_BLOCK_BASS.get(), 0.5F, 0.6F);
            return;
        }
        switch (t.getString("type")) {
            case "start" -> onStart(t, now);
            case "stop" -> onStopped(t.getInt("reel"), t.getInt("at"), now);
            case "result" -> onResult(t, now);
            default -> {
            }
        }
    }

    private void onStart(CompoundTag t, long now) {
        waitingStart = false;
        gameActive = true;
        lastPay = 0;
        bet = t.getInt("bet");
        state.putLong("medals", t.getLong("medals"));
        effect = t.getString("effect");
        navOrder = t.getInt("nav");
        long delay = switch (effect) {
            case "freeze" -> 2600;
            case "late" -> 450;
            default -> 0;
        };
        startDelayUntil = now + delay;
        stopsEnabledAt = startDelayUntil + 450;
        for (Reel r : reel) {
            r.stage = 1;
            r.pressed = -1;
            r.target = -1;
        }
        if ("freeze".equals(effect)) {
            ParlorDraw.play(SoundEvents.BEACON_DEACTIVATE, 0.7F, 0.6F);
        } else if ("late".equals(effect)) {
            ParlorDraw.play(SoundEvents.CHAIN_FALL, 0.6F, 0.5F);
        } else {
            ParlorDraw.play(SoundEvents.ITEM_PICKUP, 0.2F, 0.5F);
        }
        if (t.getBoolean("lampNow")) {
            rainbowLamp = "rainbow".equals(effect);
            if ("freeze".equals(effect)) {
                lampLitAt = startDelayUntil - 600;
            } else {
                lightLamp(now);
            }
        }
        if (t.getBoolean("lamp") && !lampOn) {
            // もう光っている（前のゲームで光った・管理者が決めた）
            lampOn = true;
            lampLitAt = now;
        }
        if (t.getBoolean("free")) {
            popups.add(new Popup("REPLAY", 0x7FC8FF, now + 900));
        }
        if (navOrder >= 0) {
            ParlorDraw.play(SoundEvents.NOTE_BLOCK_HARP.get(), 0.5F, 1.4F);
        }
        handleEvents(t.getList("events", Tag.TAG_STRING), now);
    }

    private void onStopped(int i, int at, long now) {
        Reel r = reel[i];
        if (r.pressed < 0) {
            r.pressed = Math.floorMod((int) Math.round(r.pos), n);
        }
        r.target = at;
        r.slip = Math.floorMod(r.pressed - at, n);
        r.slipStart = now;
        r.stage = r.slip == 0 ? 0 : 3;
        if (r.slip == 0) {
            r.pos = at;
        }
    }

    private void onResult(CompoundTag t, long now) {
        gameActive = false;
        navOrder = -1;
        lastPay = t.getInt("payout");
        onState(t.getCompound("state"));
        onRecord(t.getCompound("record"));
        bet = state.getInt("bet");
        if (lastPay > 0) {
            for (int i = 0; i < Math.min(15, lastPay); i++) {
                coins.add(new float[]{(float) (Math.random() * 40 - 20), (float) (-i * 6), (float) (Math.random() * 0.6 + 0.6)});
            }
            ParlorDraw.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.35F, 1.2F + Math.min(0.6F, lastPay / 30.0F));
        }
        if (t.getBoolean("lampNow")) {
            rainbowLamp = "rainbow".equals(effect) || rainbowLamp;
            lightLamp(now);
        }
        String started = t.getString("bonusStarted");
        if (!started.isEmpty()) {
            lampOn = false;
            bannerText = "big".equals(started) ? label("big", "BIG BONUS") : label("reg", "REGULAR BONUS");
            bannerColor = "big".equals(started) ? 0xFF4060 : 0x60A0FF;
            bannerUntil = now + 3500;
            ParlorDraw.play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.0F);
            ParlorDraw.play(SoundEvents.PLAYER_LEVELUP, 0.5F, 1.2F);
        }
        if (!t.getString("bonusEnded").isEmpty()) {
            bannerText = Component.translatable("screen.amemaru.parlor.bonus_end", t.getInt("bonusPaid")).getString();
            bannerColor = 0xFFE070;
            bannerUntil = now + 2500;
            ParlorDraw.play(SoundEvents.NOTE_BLOCK_CHIME.get(), 0.6F, 1.0F);
        }
        if (!state.getBoolean("lamp") && started.isEmpty()) {
            lampOn = false;
        }
        handleEvents(t.getList("events", Tag.TAG_STRING), now);
    }

    private void handleEvents(ListTag events, long now) {
        for (int i = 0; i < events.size(); i++) {
            String e = events.getString(i);
            if (e.startsWith("add:")) {
                popups.add(new Popup("+" + e.substring(4) + "G", 0xFFE060, now + 1800));
                ParlorDraw.play(SoundEvents.NOTE_BLOCK_PLING.get(), 0.6F, 1.8F);
            } else if ("cz_start".equals(e)) {
                bannerText = label("cz", "CHANCE ZONE");
                bannerColor = 0x7FE0FF;
                bannerUntil = now + 2200;
                ParlorDraw.play(SoundEvents.NOTE_BLOCK_BIT.get(), 0.6F, 1.6F);
            } else if ("cz_hit".equals(e)) {
                popups.add(new Popup("★", ParlorDraw.rainbow(now, 1200, 0) & 0xFFFFFF, now + 1500));
                ParlorDraw.play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.6F);
            } else if ("at_start".equals(e) || "cz_success".equals(e)) {
                bannerText = label("at", "AT") + "!!";
                bannerColor = -1;
                bannerUntil = now + 3500;
                ParlorDraw.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.7F, 1.0F);
                ParlorDraw.play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.1F);
            } else if ("cz_fail".equals(e)) {
                popups.add(new Popup(Component.translatable("screen.amemaru.parlor.cz_fail").getString(), 0xA0A0C0, now + 1800));
            } else if ("ceiling".equals(e)) {
                popups.add(new Popup(Component.translatable("screen.amemaru.parlor.ceiling").getString(), 0xFF8060, now + 2500));
            } else if (e.startsWith("at_end:")) {
                bannerText = Component.translatable("screen.amemaru.parlor.at_end", e.substring(7)).getString();
                bannerColor = 0xFFE070;
                bannerUntil = now + 3000;
            }
        }
    }

    private void lightLamp(long now) {
        lampOn = true;
        lampLitAt = now;
        ParlorDraw.play(SoundEvents.NOTE_BLOCK_BELL.get(), 0.9F, 2.0F);
        ParlorDraw.play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.7F);
    }

    // ================================================================ 描く

    private void step(long now) {
        long dt = Math.min(100, now - lastFrame);
        lastFrame = now;
        for (Reel r : reel) {
            if (r.stage == 1 || r.stage == 2 && r.pressed < 0) {
                if (now >= startDelayUntil) {
                    r.pos -= speed * dt;
                }
            } else if (r.stage == 3) {
                double p = Math.min(1.0, (now - r.slipStart) / (r.slip * 45.0));
                r.pos = r.pressed - r.slip * p;
                if (p >= 1.0) {
                    r.pos = r.target;
                    r.stage = 0;
                    ParlorDraw.play(SoundEvents.UI_BUTTON_CLICK.get(), 0.25F, 1.9F);
                }
            }
            r.pos = ((r.pos % n) + n) % n;
        }
        if (!state.getString("bonus").isEmpty() && now >= nextFanfare) {
            nextFanfare = now + 640;
            float[] notes = {1.0F, 1.26F, 1.5F, 2.0F};
            ParlorDraw.play(SoundEvents.NOTE_BLOCK_PLING.get(), 0.18F, notes[(int) (now / 640 % 4)]);
        }
    }

    @Override
    protected void renderMachine(GuiGraphics g, int mouseX, int mouseY, float partial, long now) {
        step(now);
        int cx = cabX();
        int cw = 244;
        boolean bonus = !state.getString("bonus").isEmpty();
        boolean at = state.getInt("atGames") > 0;
        boolean freeze = now < startDelayUntil && "freeze".equals(effect);
        // 筐体
        ParlorDraw.bevel(g, cx, top + 4, cw, H - 8, frameColor);
        g.fill(cx + 4, top + 8, cx + cw - 4, top + H - 12, ParlorDraw.darker(frameColor, 0.35F));
        // 筐体のふちのランプ
        for (int i = 0; i < 18; i++) {
            float phase = (now / 90.0F + i) % 18;
            int c = bonus || at ? ParlorDraw.rainbow(now, 1500, i / 18.0F) : phase < 2 ? accent : ParlorDraw.darker(accent, 0.6F);
            if (freeze) {
                c = 0xFF101010;
            }
            g.fill(cx + 6 + i * 13, top + 6, cx + 14 + i * 13, top + 8, c);
        }
        // 機種の名前
        ParlorDraw.bigCentered(g, font, modelName(), cx + cw / 2, top + 20, 1.5F, accent, 0xFF000000);
        // 液晶（AT）・飾りの窓（Aタイプ）
        renderLcd(g, cx + 10, top + 32, cw - 20, 42, now, freeze);
        // リールの窓
        int rx = reelX(0) - 6;
        int ry = reelY() - 4;
        int rw = REEL_W * 3 + REEL_GAP * 2 + 12;
        int rh = ROW_H * 3 + 8;
        ParlorDraw.inset(g, rx, ry, rw, rh, 0x101014);
        for (int i = 0; i < 3; i++) {
            renderReel(g, i, now, bonus, freeze);
        }
        // 有効ライン（中段）
        int ly = reelY() + ROW_H + ROW_H / 2;
        int lc = lastPay > 0 && !gameActive ? ParlorDraw.rainbow(now, 900, 0) : 0x80FF3050;
        g.fill(rx - 4, ly - 1, rx, ly + 1, lc | 0xFF000000);
        g.fill(rx + rw, ly - 1, rx + rw + 4, ly + 1, lc | 0xFF000000);
        // 押し順ナビ（AT）
        if (navOrder >= 0 && gameActive) {
            int[] order = ORDERS[navOrder];
            for (int k = 0; k < 3; k++) {
                int reelIndex = order[k];
                if (reel[reelIndex].stage != 1) {
                    continue;
                }
                int x = reelX(reelIndex) + REEL_W / 2;
                float pulse = 0.6F + 0.4F * (float) Math.sin(now / 120.0);
                ParlorDraw.glow(g, x - 9, reelY() - 2, 18, 16, 0xFF2040, pulse);
                ParlorDraw.bigCentered(g, font, String.valueOf(k + 1), x, reelY() + 6, 1.6F, 0xFFFFFF, 0xFF600010);
            }
        }
        // 告知ランプ（Aタイプ）
        if ("a".equals(type)) {
            renderLamp(g, rx - 26, ry + rh - 26, now, freeze);
        }
        // クレジット・払い出し・BET
        int dy = top + 166;
        g.drawString(font, "CREDIT", cx + 14, dy, 0xB0A8C0, false);
        ParlorDraw.segments(g, cx + 14, dy + 10, Math.min(50, state.getLong("medals")), 2, 0xFFFF3030, 0xFF301010, 6);
        g.drawString(font, "PAY", cx + cw - 52, dy, 0xB0A8C0, false);
        ParlorDraw.segments(g, cx + cw - 52, dy + 10, lastPay, 3, 0xFFFF3030, 0xFF301010, 6);
        for (int i = 0; i < 3; i++) {
            boolean lit = i < bet && (gameActive || waitingStart);
            ParlorDraw.disc(g, cx + cw / 2 - 16 + i * 16, dy + 14, 4, lit ? 0xFFFFD040 : 0xFF403020);
        }
        g.drawString(font, "BET", cx + cw / 2 - 8, dy + 22, 0x908898, false);
        // レバー
        int lx = cx + 14;
        int lyy = top + 186;
        boolean leverDown = waitingStart || gameActive && now - lastLeverAt < 250;
        g.fill(lx + 10, lyy + 8, lx + 14, lyy + 34, 0xFF909098);
        ParlorDraw.disc(g, lx + 12, lyy + (leverDown ? 30 : 8), 8, 0xFFE02030);
        ParlorDraw.disc(g, lx + 10, lyy + (leverDown ? 28 : 6), 3, 0xFFFF8090);
        // 止めボタン
        for (int i = 0; i < 3; i++) {
            int bx = reelX(i) + 7;
            int by = top + 198;
            boolean ready = gameActive && reel[i].stage == 1 && now >= stopsEnabledAt;
            int c = ready ? 0xFF30A0FF : 0xFF303848;
            if (ready && navOrder >= 0 && ORDERS[navOrder][stopIndex()] == i) {
                c = (now / 150) % 2 == 0 ? 0xFFFF3050 : 0xFF30A0FF;
            }
            ParlorDraw.bevel(g, bx, by, 30, 14, c & 0xFFFFFF);
            g.drawString(font, new String[]{"Z", "X", "C"}[i], bx + 12, by + 3, ready ? 0xFFFFFF : 0x808090, false);
        }
        // 受け皿のメダル
        renderCoins(g, cx + cw / 2, top + H - 24, now);
        // 待つ・知らせ
        if (!gameActive && !waitingStart) {
            long wait = waitMs - (now - lastLeverAt);
            String hint = wait > 0 ? "WAIT" : Component.translatable("screen.amemaru.parlor.lever_hint").getString();
            g.drawString(font, hint, cx + cw / 2 - font.width(hint) / 2, top + 220, wait > 0 ? 0xFF8060 : 0x9AA0B8, false);
        }
        if (now < errorUntil) {
            ParlorDraw.bigCentered(g, font, error, cx + cw / 2, top + 120, 1.0F, 0xFF6060, 0xFF000000);
        }
        // 大きな知らせ（BIG・AT など）
        if (now < bannerUntil) {
            float a = Mth.clamp((bannerUntil - now) / 400.0F, 0, 1);
            int c = bannerColor < 0 ? ParlorDraw.rainbow(now, 900, 0) : bannerColor;
            g.fill(cx + 8, top + 108, cx + cw - 8, top + 134, ParlorDraw.alpha(0x000000, 0.7F * a));
            ParlorDraw.bigCentered(g, font, bannerText, cx + cw / 2, top + 121, 1.8F, c | 0xFF000000, 0xFF000000);
        }
        for (int i = popups.size() - 1; i >= 0; i--) {
            Popup p = popups.get(i);
            if (now > p.until()) {
                popups.remove(i);
                continue;
            }
            float rise = (p.until() - now) / 1800.0F;
            ParlorDraw.bigCentered(g, font, p.text(), cx + cw / 2, top + 60 + (int) (rise * 10), 1.6F, p.color() | 0xFF000000, 0xFF000000);
        }
    }

    private static final int[][] ORDERS = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};

    private int stopIndex() {
        int k = 0;
        for (Reel r : reel) {
            if (r.stage != 1) {
                k++;
            }
        }
        return Math.min(2, k);
    }

    private void renderReel(GuiGraphics g, int i, long now, boolean bonus, boolean freeze) {
        int x = reelX(i);
        int y = reelY();
        int bg = bonus ? ParlorDraw.mix(0xFFF8F4E8, ParlorDraw.rainbow(now, 2000, i / 3.0F), 0.25F) : 0xFFF4F0E4;
        if (freeze) {
            bg = 0xFF404040;
        }
        g.fill(x, y, x + REEL_W, y + ROW_H * 3, bg);
        g.enableScissor(x, y, x + REEL_W, y + ROW_H * 3);
        Reel r = reel[i];
        double pos = r.pos;
        int base = (int) Math.floor(pos);
        int cy = y + ROW_H + ROW_H / 2;
        for (int k = base - 2; k <= base + 3; k++) {
            char c = reels[i].charAt(Math.floorMod(k, n));
            float sy = (float) (cy + (k - pos) * ROW_H);
            drawSymbol(g, c, x + REEL_W / 2, Math.round(sy), r.stage == 1 && now >= startDelayUntil);
        }
        g.disableScissor();
        // 上と下の影（丸いリールに見せる）
        for (int s = 0; s < 6; s++) {
            int a = (int) (90 * (1 - s / 6.0F));
            g.fill(x, y + s, x + REEL_W, y + s + 1, a << 24);
            g.fill(x, y + ROW_H * 3 - s - 1, x + REEL_W, y + ROW_H * 3 - s, a << 24);
        }
    }

    private void drawSymbol(GuiGraphics g, char c, int cx, int cy, boolean blur) {
        ResourceLocation tex = textures.get(c);
        int s = 24;
        if (tex != null) {
            ParlorDraw.enableBlend();
            g.setColor(1.0F, 1.0F, 1.0F, blur ? 0.75F : 1.0F);
            g.blit(tex, cx - s / 2, cy - s / 2, 0, 0, s, s, s, s);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        } else {
            int col = symbolColors.getOrDefault(c, 0xFF000000);
            ParlorDraw.bigCentered(g, font, String.valueOf(c), cx, cy, 2.0F, col, 0);
        }
    }

    private void renderLamp(GuiGraphics g, int x, int y, long now, boolean freeze) {
        // 結び目の形のランプ（4つの丸と真ん中）
        boolean lit = lampOn && now >= lampLitAt;
        int c;
        if (!lit) {
            c = 0xFF3A2030;
        } else if (rainbowLamp) {
            c = ParlorDraw.rainbow(now, 700, 0);
        } else {
            float pulse = 0.75F + 0.25F * (float) Math.sin((now - lampLitAt) / 160.0);
            c = ParlorDraw.mix(0xFF000000, glowColor, pulse);
        }
        if (freeze && (now / 120) % 2 == 0) {
            c = 0xFF101010;
        }
        if (lit) {
            ParlorDraw.glow(g, x - 2, y - 2, 24, 24, c, 0.35F);
        }
        int[][] dots = {{6, 6}, {16, 6}, {6, 16}, {16, 16}};
        for (int[] d : dots) {
            ParlorDraw.disc(g, x + d[0], y + d[1], 5, c);
        }
        ParlorDraw.disc(g, x + 11, y + 11, 4, lit ? 0xFFFFFFFF : 0xFF2A1828);
        String name = label("lamp", model.has("lamp") ? model.getAsJsonObject("lamp").get("name").getAsString() : "");
        if (!name.isEmpty()) {
            g.drawString(font, name, x - 4, y + 25, lit ? c : 0x705868, false);
        }
    }

    private void renderLcd(GuiGraphics g, int x, int y, int w, int h, long now, boolean freeze) {
        boolean bonus = !state.getString("bonus").isEmpty();
        int atGames = state.getInt("atGames");
        int czGames = state.getInt("czGames");
        int bg = freeze ? 0xFF000000 : screenColor;
        if ("at".equals(type) && atGames > 0) {
            bg = ParlorDraw.mix(screenColor, 0xFF802040, 0.5F + 0.2F * (float) Math.sin(now / 300.0));
        } else if ("at".equals(type) && czGames > 0) {
            bg = ParlorDraw.mix(screenColor, 0xFF205080, 0.6F);
        } else if (bonus) {
            bg = ParlorDraw.mix(screenColor, ParlorDraw.rainbow(now, 3000, 0), 0.35F);
        }
        ParlorDraw.inset(g, x, y, w, h, bg & 0xFFFFFF);
        if (freeze) {
            return;
        }
        // 星（背景）
        for (int i = 0; i < 14; i++) {
            int sx = x + 4 + (int) ((i * 53 + now / 40) % (w - 8));
            int sy = y + 4 + (i * 17) % (h - 8);
            g.fill(sx, sy, sx + 1, sy + 1, ParlorDraw.alpha(0xFFFFFF, 0.3F + 0.3F * ((i + now / 300) % 3) / 2.0F));
        }
        if ("at".equals(type)) {
            if (atGames > 0) {
                ParlorDraw.bigCentered(g, font, label("at", "AT"), x + w / 2, y + 10, 1.3F, ParlorDraw.rainbow(now, 1600, 0), 0xFF000000);
                ParlorDraw.bigCentered(g, font, Component.translatable("screen.amemaru.parlor.at_left", atGames,
                        state.getInt("atGot")).getString(), x + w / 2, y + 28, 1.0F, 0xFFFFFFFF, 0xFF000000);
            } else if (czGames > 0) {
                ParlorDraw.bigCentered(g, font, label("cz", "CZ"), x + w / 2, y + 10, 1.3F, 0xFF8FE8FF, 0xFF000000);
                ParlorDraw.bigCentered(g, font, Component.translatable("screen.amemaru.parlor.cz_left", czGames).getString(),
                        x + w / 2, y + 28, 1.0F, 0xFFFFFFFF, 0xFF000000);
            } else {
                ParlorDraw.bigCentered(g, font, label("stage", ""), x + w / 2, y + 12, 1.2F, accent, 0xFF000000);
                g.drawString(font, Component.translatable("screen.amemaru.parlor.games_now", rec("since")), x + 6, y + h - 11, 0x9AA8C8,
                        false);
            }
        } else if (bonus) {
            String b = state.getString("bonus");
            ParlorDraw.bigCentered(g, font, "big".equals(b) ? label("big", "BIG BONUS") : label("reg", "REGULAR BONUS"), x + w / 2, y + 12,
                    1.3F, ParlorDraw.rainbow(now, 1200, 0), 0xFF000000);
            ParlorDraw.bigCentered(g, font, Component.translatable("screen.amemaru.parlor.bonus_paid", state.getInt("bonusPaid"),
                    state.getInt("bonusCap")).getString(), x + w / 2, y + 28, 1.0F, 0xFFFFFFFF, 0xFF000000);
        } else {
            ParlorDraw.bigCentered(g, font, label("stage", ""), x + w / 2, y + 14, 1.4F, accent, 0xFF000000);
            g.drawString(font, Component.translatable("screen.amemaru.parlor.aim_hint"), x + 6, y + h - 11, 0x8A80A0, false);
        }
    }

    private void renderCoins(GuiGraphics g, int cx, int y, long now) {
        g.fill(cx - 40, y, cx + 40, y + 8, 0xFF202028);
        for (int i = coins.size() - 1; i >= 0; i--) {
            float[] c = coins.get(i);
            c[1] += c[2] * 3.0F;
            if (c[1] > 0) {
                coins.remove(i);
                continue;
            }
            ParlorDraw.disc(g, cx + c[0], y + c[1], 3, 0xFFE0E0E8);
        }
    }

    @Override
    protected void renderData(GuiGraphics g, int x, int y, long now) {
        boolean at = "at".equals(type);
        g.drawString(font, Component.translatable("screen.amemaru.parlor.data_today"), x, y, accent, false);
        int row = y + 12;
        dataLine(g, x, row, Component.translatable("screen.amemaru.parlor.d_games"), num(rec("games")), 0xFFFFFF);
        dataLine(g, x, row + 10, Component.translatable("screen.amemaru.parlor.d_since"), num(rec("since")), 0xFFE070);
        if (at) {
            dataLine(g, x, row + 20, Component.literal("AT"), num(rec("at")), 0xFF8080);
            dataLine(g, x, row + 30, Component.literal("CZ"), num(rec("cz")), 0x80C0FF);
        } else {
            dataLine(g, x, row + 20, Component.literal("BIG"), num(rec("big")), 0xFF6070);
            dataLine(g, x, row + 30, Component.literal("REG"), num(rec("reg")), 0x70A0FF);
            int hits = rec("big") + rec("reg");
            dataLine(g, x, row + 40, Component.translatable("screen.amemaru.parlor.d_combined"),
                    hits == 0 ? "-" : String.format(Locale.ROOT, "1/%.1f", rec("games") / (double) hits), 0xFFFFFF);
        }
        // 差枚の流れ（データの画面と同じ）
        int gy = row + 54;
        int gw = W - PANEL_X - 14;
        int gh = 34;
        ParlorDraw.inset(g, x, gy, gw, gh, 0x0E0C14);
        JsonArray slump = recordJson.has("slump") ? recordJson.getAsJsonArray("slump") : new JsonArray();
        ParlorDataScreen.graph(g, x, gy, gw, gh, slump);
        // 当たりの履歴
        JsonArray hits = recordJson.has("hitList") ? recordJson.getAsJsonArray("hitList") : new JsonArray();
        int hy = gy + gh + 4;
        for (int i = 0; i < Math.min(3, hits.size()); i++) {
            JsonObject h = hits.get(i).getAsJsonObject();
            String kind = h.get("kind").getAsString().toUpperCase(Locale.ROOT);
            g.drawString(font, kind + "  " + h.get("at").getAsInt() + "G", x, hy + i * 9, "BIG".equals(kind) ? 0xFF7080
                    : "AT".equals(kind) ? 0xFFB060 : 0x80A8FF, false);
        }
    }
}

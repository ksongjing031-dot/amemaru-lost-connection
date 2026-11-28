package jp.amemaru.lostconnection.client.parlor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * パチンコの台の画面（11月27日【補完】。本物の「デジパチ」と同じ見た目と流れ）。
 *
 * <ul>
 *   <li>ハンドル：「打つ」（スペース）で1分に100個。強さ（↑↓・右の目盛り）で飛び方が変わり、55〜80 がヘソに入りやすい。
 *       電サポ（確変・ST・時短）と大当たりの間は「右打ち」（R）</li>
 *   <li>真ん中の液晶：3つの図柄が回り、左・右・真ん中の順に止まる。リーチ（ノーマル・スーパー・スペシャル・全回転）、
 *       予告（ステップアップ・群れ・カットイン・激アツ・擬似連）、保留の色（白・青・緑・赤・金・虹）。当たりと見せ方は
 *       サーバーが決めた台本どおり</li>
 *   <li>大当たり：アタッカーが開き、ラウンドごとに10カウント。終わると確変・ST・時短・通常へ。確変の当たりを偶数の図柄で
 *       見せて、ラウンドの中で「昇格」させることもある</li>
 * </ul>
 * <p>11月28日のご指示「パチンコとスロットにもっとアニメーションをつける」【補完】：図柄が止まるときのはね、リーチの文字の
 * 飛びこみと揃った図柄の脈、スーパー以上の集中線と揺れ、斜めの帯のカットイン、盤面の上の役物（結晶の飾り）が落ちる演出、
 * 保留が進むすべりと色が変わる保留変化、大当たりのフラッシュ・紙吹雪・輪・ファンファーレ、ラウンドの飛びこみとカウントの
 * 「+」、終わりの払い出しの数え上げ、盤面のふちのランプの光り方（待ち・回転・リーチ・大リーチ・大当たり）。
 * どれも見せ方だけで、当たりは台本のとおり（保留変化は、最後にサーバーが決めた色になる）。</p>
 */
public class PachinkoScreen extends MachineScreen {
    private static final int[] HOLD_COLORS = {0xFFF0F0F0, 0xFF3C8CFF, 0xFF40D060, 0xFFFF3040, 0xFFFFC830, 0};
    private static final int[] DIGIT_COLORS = {0, 0xFFFF4040, 0xFF40A0FF, 0xFFFF4040, 0xFF40D060, 0xFFFF4040, 0xFFB070FF,
            0xFFFF2020, 0xFF40C0E0, 0xFFFF4040};

    private final JsonObject labels;
    private final Random random = new Random();
    private JsonObject script;
    private long spinStart;
    private boolean spinSoundReach;
    private boolean spinSoundCut;
    private boolean spinSoundEnd;
    private int[] shown = {7, 7, 7};
    private boolean lastHit;
    private long resultAt;

    private boolean shooting;
    private int strength = 70;
    private boolean right;
    private boolean dragging;

    private long lastShots = -1;
    private long lastHeso;
    private long lastDenchu;
    private long lastAttacker;
    private final List<Ball> balls = new ArrayList<>();
    private long lastFrame;
    private String lastPhase = "";
    private int lastRound;
    private int lastCount;
    private boolean promotedShown;
    private String endText = "";
    private int endColor;
    private long endUntil;
    private int[] lastHolds = new int[0];

    // 11月28日：動き
    private final ParlorFx fx = new ParlorFx();
    private final boolean[] colStopped = new boolean[3];
    private final long[] colStopAt = new long[3];
    private long gimmickDropAt;
    private long gimmickRiseAt;
    private boolean gimmickDown;
    private long fanfareAt;
    private int fanfareNote;
    private int roundShown = -1;
    private long roundAt;
    private int countShown;
    private long endingAt;
    private long shiftAt;
    private final List<HoldView> holdViews = new ArrayList<>();
    @javax.annotation.Nullable
    private HoldView pendingCurrent;
    @javax.annotation.Nullable
    private HoldView current;
    private final List<long[]> roundPops = new ArrayList<>();
    private int boardX;
    private int boardY;
    private int boardW;
    private int boardH;

    /** 保留1つの見え方（保留変化：はじめは低い色で見せ、あとでサーバーが決めた色に変わることがある）。 */
    private static final class HoldView {
        int trueColor;
        int shown;
        long upgradeAt;
        long poppedAt;
    }

    private static final class Ball {
        float x;
        float y;
        float vx;
        float vy;
        int kind;       // 0 はずれ・1 ヘソ・2 電チュー・3 アタッカー
        float t;
        boolean rightShot;
    }

    public PachinkoScreen(CompoundTag open) {
        super(open);
        this.labels = model.has("labels") ? model.getAsJsonObject("labels") : new JsonObject();
        shooting = state.getBoolean("shooting");
        strength = state.contains("strength") ? state.getInt("strength") : 70;
        right = state.getBoolean("right");
        if (state.contains("script")) {
            startScript(state.getString("script"), state.getInt("spinTick"));
        }
        lastShots = state.getLong("shots");
        lastHeso = state.getLong("hesoIn");
        lastDenchu = state.getLong("denchuIn");
        lastAttacker = state.getLong("attackerIn");
    }

    private String label(String key, String fallback) {
        return labels.has(key) ? labels.get(key).getAsString() : fallback;
    }

    @Override
    protected String holdingText() {
        return Component.translatable("screen.amemaru.parlor.balls", num(state.getLong("balls"))).getString();
    }

    @Override
    protected void initMachine() {
        lastFrame = System.currentTimeMillis();
    }

    // ================================================================ 操作

    private void sendHandle() {
        CompoundTag args = new CompoundTag();
        args.putBoolean("on", shooting);
        args.putInt("strength", strength);
        args.putBoolean("right", right);
        send("handle", args);
    }

    /** 自動の確認の写真：ハンドル（打つ・右打ち・強さ）。 */
    public void ciHandle(boolean on, boolean rightShot, int value) {
        shooting = on;
        right = rightShot;
        strength = Mth.clamp(value, 0, 100);
        sendHandle();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE) {
            shooting = !shooting;
            ParlorDraw.play(SoundEvents.UI_BUTTON_CLICK.get(), 0.4F, shooting ? 1.2F : 0.8F);
            sendHandle();
            return true;
        }
        if (key == GLFW.GLFW_KEY_R) {
            right = !right;
            ParlorDraw.play(SoundEvents.UI_BUTTON_CLICK.get(), 0.4F, right ? 1.5F : 1.0F);
            sendHandle();
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) {
            strength = Math.min(100, strength + 5);
            sendHandle();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) {
            strength = Math.max(0, strength - 5);
            sendHandle();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private int handleX() {
        return left + 6 + 244 - 40;
    }

    private int handleY() {
        return top + H - 44;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            // 打つ・右打ちのボタン
            int bx = left + 16;
            int by = top + H - 30;
            if (mx >= bx && mx < bx + 46 && my >= by && my < by + 16) {
                shooting = !shooting;
                sendHandle();
                return true;
            }
            if (mx >= bx + 50 && mx < bx + 96 && my >= by && my < by + 16) {
                right = !right;
                sendHandle();
                return true;
            }
            // ハンドル（目盛り）
            if (Math.hypot(mx - handleX(), my - handleY()) < 22) {
                dragging = true;
                dragTo(mx, my);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            dragTo(mx, my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            sendHandle();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        strength = Mth.clamp(strength + (delta > 0 ? 5 : -5), 0, 100);
        sendHandle();
        return true;
    }

    private void dragTo(double mx, double my) {
        // 目盛りは左下（0）から上を通って右下（100）までの半円
        double a = Math.atan2(my - handleY(), mx - handleX());
        double deg = Math.toDegrees(a);   // −180〜180（上が −90）
        double v = (deg + 180) / 180.0 * 100.0;
        if (deg > 0) {
            v = deg > 90 ? 0 : 100;
        }
        strength = Mth.clamp((int) Math.round(v), 0, 100);
    }

    // ================================================================ サーバーから

    @Override
    public void onState(CompoundTag next) {
        CompoundTag before = state;
        super.onState(next);
        long now = System.currentTimeMillis();
        // 新しく打った玉（ヘソ・電チュー・アタッカーに入った数で、玉の行き先を決める）
        long shots = next.getLong("shots");
        if (lastShots >= 0) {
            int newShots = (int) Math.min(12, Math.max(0, shots - lastShots));
            int heso = (int) Math.max(0, next.getLong("hesoIn") - lastHeso);
            int denchu = (int) Math.max(0, next.getLong("denchuIn") - lastDenchu);
            int attacker = (int) Math.max(0, next.getLong("attackerIn") - lastAttacker);
            for (int i = 0; i < newShots; i++) {
                int kind = heso-- > 0 ? 1 : denchu-- > 0 ? 2 : attacker-- > 0 ? 3 : 0;
                spawnBall(kind, right || kind >= 2, i * 0.15F);
            }
            if (next.getLong("hesoIn") > lastHeso) {
                ParlorDraw.play(SoundEvents.NOTE_BLOCK_CHIME.get(), 0.15F, 1.9F);
            }
            if (attacker > 0 || next.getLong("attackerIn") > lastAttacker) {
                ParlorDraw.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.12F, 1.4F + random.nextFloat() * 0.3F);
            }
        }
        lastShots = shots;
        lastHeso = next.getLong("hesoIn");
        lastDenchu = next.getLong("denchuIn");
        lastAttacker = next.getLong("attackerIn");
        // 保留の色が変わった・増えた（11月28日：保留が進むすべりと、保留変化）
        int[] holds = next.getIntArray("heso");
        reconcileHolds(holds, now);
        lastHolds = holds;
        // 大当たりのラウンド
        CompoundTag round = next.getCompound("round");
        String phase = next.contains("round") ? round.getString("phase") : "";
        if (!phase.equals(lastPhase)) {
            switch (phase) {
                case "OPENING" -> {
                    ParlorDraw.play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F);
                    ParlorDraw.play(SoundEvents.FIREWORK_ROCKET_BLAST, 0.6F, 1.0F);
                    promotedShown = false;
                    roundShown = -1;
                }
                case "ROUND" -> ParlorDraw.play(SoundEvents.NOTE_BLOCK_PLING.get(), 0.5F, 1.0F + 0.05F * round.getInt("round"));
                case "ENDING" -> {
                    ParlorDraw.play(SoundEvents.PLAYER_LEVELUP, 0.6F, 1.0F);
                    endingAt = now;
                    if (round.getBoolean("kakuhen") && boardW > 0) {
                        fx.confetti(boardX, boardX + boardW, boardY, 70, null);
                    }
                }
                default -> {
                }
            }
            lastPhase = phase;
        }
        if (next.contains("jackpotEnd")) {
            String mode = next.getString("mode");
            endUntil = now + 4000;
            int paid = next.getInt("jackpotPaid");
            switch (mode) {
                case "KAKUHEN" -> {
                    endText = Component.translatable("screen.amemaru.parlor.enter", label("kakuhen", "確変")).getString();
                    endColor = -1;
                }
                case "ST" -> {
                    endText = Component.translatable("screen.amemaru.parlor.enter", next.getBoolean("lt")
                            ? label("lt", "LUCKY TRIGGER") : label("st", "RUSH")).getString();
                    endColor = next.getBoolean("lt") ? -2 : -1;
                }
                case "JITAN" -> {
                    endText = Component.translatable("screen.amemaru.parlor.jitan", label("jitan", "時短"), next.getInt("modeSpins")).getString();
                    endColor = 0xFF60C0FF;
                }
                default -> {
                    endText = Component.translatable("screen.amemaru.parlor.normal_back").getString();
                    endColor = 0xFFC0C0C0;
                }
            }
            endText = endText + "  " + Component.translatable("screen.amemaru.parlor.paid", paid).getString();
        }
        if (before.getBoolean("spinning") && !next.getBoolean("spinning") && script != null && !next.contains("round")) {
            // 図柄が止まった（はずれ）：次の台本を待つ
        }
    }

    /** 保留の見え方を、サーバーの保留（色の並び）に合わせる。 */
    private void reconcileHolds(int[] holds, long now) {
        // 回り始めて減った：先頭が当該（回っている保留）になる
        while (holdViews.size() > holds.length) {
            pendingCurrent = holdViews.remove(0);
            shiftAt = now;
        }
        for (int i = 0; i < holdViews.size(); i++) {
            if (holdViews.get(i).trueColor != holds[i]) {
                holdViews.clear();
                break;
            }
        }
        for (int i = holdViews.size(); i < holds.length; i++) {
            HoldView view = new HoldView();
            view.trueColor = holds[i];
            if (view.trueColor >= 1 && random.nextFloat() < 0.5F) {
                // 保留変化：はじめは低い色で、しばらくしてから本当の色に変わる
                view.shown = random.nextInt(view.trueColor);
                view.upgradeAt = now + 1200 + random.nextInt(2600);
            } else {
                view.shown = view.trueColor;
                if (view.trueColor >= 1) {
                    view.poppedAt = now;
                    ParlorDraw.play(SoundEvents.NOTE_BLOCK_BELL.get(), 0.5F, 0.9F + view.trueColor * 0.2F);
                }
            }
            holdViews.add(view);
        }
    }

    /** 図柄が回り始めた（台本）。 */
    public void onSpin(String json) {
        startScript(json, 0);
    }

    private void startScript(String json, int tick) {
        try {
            script = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            script = null;
            return;
        }
        spinStart = System.currentTimeMillis() - tick * 50L;
        spinSoundReach = false;
        spinSoundCut = false;
        spinSoundEnd = false;
        java.util.Arrays.fill(colStopped, tick > 0);
        // 当該保留：回り始めた保留の見え方を引き継ぐ（低い色で見せていたら、回っている間に本当の色へ変わる＝当該変化）
        int truth = si("hold");
        HoldView view = null;
        if (pendingCurrent != null && pendingCurrent.trueColor == truth) {
            view = pendingCurrent;
        } else if (!holdViews.isEmpty() && holdViews.get(0).trueColor == truth) {
            view = holdViews.get(0);
        }
        if (view == null) {
            view = new HoldView();
            view.trueColor = truth;
            view.shown = truth;
        }
        if (view.shown < view.trueColor) {
            view.upgradeAt = System.currentTimeMillis() + 500 + random.nextInt(1200);
        }
        current = view;
        pendingCurrent = null;
    }

    private int si(String key) {
        return script != null && script.has(key) ? script.get(key).getAsInt() : 0;
    }

    private String ss(String key) {
        return script != null && script.has(key) ? script.get(key).getAsString() : "";
    }

    private boolean sb(String key) {
        return script != null && script.has(key) && script.get(key).getAsBoolean();
    }

    // ================================================================ 描く

    private void spawnBall(int kind, boolean rightShot, float delay) {
        Ball b = new Ball();
        b.kind = kind;
        b.rightShot = rightShot;
        b.t = -delay;
        balls.add(b);
    }

    @Override
    protected void renderMachine(GuiGraphics g, int mouseX, int mouseY, float partial, long now) {
        long dt = Math.min(100, now - lastFrame);
        lastFrame = now;
        int cx = left + 6;
        int cw = 244;
        boolean support = !"NORMAL".equals(state.getString("mode")) && !state.getString("mode").isEmpty();
        boolean inRound = state.contains("round");
        double spinT = script == null ? 1.0E9 : (now - spinStart) / 50.0;
        boolean spinning = script != null && spinT < si("center");
        String reachKind = ss("reach");
        boolean reaching = spinning && !reachKind.isEmpty() && spinT >= si("right");
        boolean bigReach = reaching && ("super".equals(reachKind) || "special".equals(reachKind) || "all".equals(reachKind));
        boolean fanfare = fanfareAt > 0 && now - fanfareAt < 3500;
        playFanfare(now);
        // 枠（11月28日：ランプの光り方を、待ち・回転・リーチ・大リーチ・大当たりで変える）
        ParlorDraw.bevel(g, cx, top + 4, cw, H - 8, frameColor);
        for (int i = 0; i < 24; i++) {
            double a = i / 24.0 * Math.PI * 2;
            int lx = cx + cw / 2 + (int) (Math.cos(a) * 114);
            int ly = top + 102 + (int) (Math.sin(a) * 92);
            int c;
            float size = 2.5F;
            if (fanfare) {
                c = (now / 60 + i) % 2 == 0 ? ParlorDraw.rainbow(now, 300, i / 24.0F) : 0xFFFFFFFF;
                size = 3.0F;
            } else if (inRound || support) {
                c = ParlorDraw.rainbow(now, 1200, i / 24.0F);
            } else if (bigReach) {
                c = (now / 40 + i) % 6 < 2 ? 0xFFFF3030 : 0xFF501818;
                size = 3.0F;
            } else if (reaching) {
                c = (now / 60 + i) % 8 < 3 ? 0xFFFFFFFF : accent;
            } else if (spinning) {
                c = (now / 120 + i) % 12 < 2 ? accent : ParlorDraw.darker(accent, 0.55F);
            } else {
                // 待っている間は、ゆっくり息をするように
                float b = 0.45F + 0.4F * (float) Math.sin(now / 700.0 + i * 0.26);
                c = ParlorDraw.mix(0xFF000000, accent, b);
            }
            ParlorDraw.disc(g, lx, ly, size, c);
        }
        // 盤面（ガラス）
        int bx = cx + 14;
        int by = top + 14;
        int bw = cw - 28;
        int bh = 176;
        boardX = bx;
        boardY = by;
        boardW = bw;
        boardH = bh;
        g.fill(bx, by, bx + bw, by + bh, ParlorDraw.darker(frameColor, 0.55F));
        g.fillGradient(bx, by, bx + bw, by + bh, 0x30FFFFFF, 0x00000000);
        // 外のレール（左から上へ）
        for (int i = 0; i < 60; i++) {
            double a = Math.PI * (1.0 - i / 60.0);
            int rx = bx + bw / 2 + (int) (Math.cos(a) * (bw / 2 - 2));
            int ry = by + bh / 2 - (int) (Math.sin(a) * (bh / 2 - 2));
            g.fill(rx, ry, rx + 2, ry + 2, 0xFFB8B8C8);
        }
        // 釘
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 16; col++) {
                int nx = bx + 12 + col * 13 + (row % 2) * 6;
                int ny = by + 26 + row * 16;
                if (nx > bx + 50 && nx < bx + bw - 50 && ny > by + 18 && ny < by + 118) {
                    continue;   // 液晶の所
                }
                g.fill(nx, ny, nx + 1, ny + 1, 0xFFD8D8E0);
            }
        }
        // 液晶
        int lx = bx + 46;
        int ly = by + 18;
        int lw = bw - 92;
        int lh = 96;
        renderLcd(g, lx, ly, lw, lh, now, support, inRound);
        // 役物（盤面の上の結晶の飾り）：強いリーチと大当たりで落ちてくる
        renderGimmick(g, lx + lw / 2, by, ly, now, spinning, reaching && (bigReach || si("cutin") >= 3), fanfare);
        // 保留
        renderHolds(g, lx, ly + lh + 3, lw, now);
        // ヘソ・電チュー・アタッカー
        int hx = bx + bw / 2;
        int hy = by + 142;
        g.fill(hx - 7, hy, hx + 7, hy + 3, 0xFFF0D040);
        g.fill(hx - 4, hy + 3, hx + 4, hy + 8, 0xFF806010);
        boolean tulip = support && (now / 300) % 2 == 0;
        int tx = bx + bw - 30;
        int ty = by + 118;
        g.fill(tx - (tulip ? 7 : 3), ty, tx - 1, ty + 6, 0xFFE04060);
        g.fill(tx + 1, ty, tx + (tulip ? 7 : 3), ty + 6, 0xFFE04060);
        boolean attackerOpen = inRound && "ROUND".equals(state.getCompound("round").getString("phase"));
        int ax = bx + bw / 2 + 24;
        int ay = by + 156;
        g.fill(ax, ay, ax + 36, ay + 6, attackerOpen ? ParlorDraw.rainbow(now, 600, 0) : 0xFF603020);
        if (attackerOpen) {
            g.fill(ax + 2, ay + 2, ax + 34, ay + 4, 0xFF000000);
        }
        // 玉
        stepBalls(g, bx, by, bw, bh, dt, hx, hy, tx, ty, ax + 18, ay);
        // 右打ちの知らせ
        if ((support || inRound) && !right) {
            if ((now / 250) % 2 == 0) {
                ParlorDraw.bigCentered(g, font, Component.translatable("screen.amemaru.parlor.right_please").getString(), bx + bw / 2,
                        by + bh - 12, 1.1F, 0xFFFF4040, 0xFF000000);
            }
        } else if (!support && !inRound && right && shooting) {
            ParlorDraw.bigCentered(g, font, Component.translatable("screen.amemaru.parlor.left_please").getString(), bx + bw / 2,
                    by + bh - 12, 1.0F, 0xFFFFC040, 0xFF000000);
        }
        // 下の上皿・ボタン・ハンドル
        int py = top + H - 50;
        g.fill(cx + 8, py, cx + cw - 8, py + 42, ParlorDraw.darker(frameColor, 0.2F));
        g.fill(cx + 12, py + 4, cx + 150, py + 14, 0xFF202028);
        long held = state.getLong("balls");
        int shownBalls = (int) Math.min(40, held / 25);
        for (int i = 0; i < shownBalls; i++) {
            ParlorDraw.disc(g, cx + 16 + i * 3.4F, py + 9, 1.6F, 0xFFD0D0D8);
        }
        int bbx = left + 16;
        int bby = top + H - 30;
        ParlorDraw.bevel(g, bbx, bby, 46, 16, shooting ? 0x30A040 : 0x404050);
        g.drawString(font, Component.translatable(shooting ? "screen.amemaru.parlor.shooting" : "screen.amemaru.parlor.shoot"),
                bbx + 4, bby + 4, 0xFFFFFF, false);
        ParlorDraw.bevel(g, bbx + 50, bby, 46, 16, right ? 0xC04030 : 0x404050);
        g.drawString(font, Component.translatable("screen.amemaru.parlor.right"), bbx + 54, bby + 4, 0xFFFFFF, false);
        renderHandle(g, now);
        // 終わりの知らせ
        if (now < endUntil) {
            int c = endColor == -1 ? ParlorDraw.rainbow(now, 900, 0) : endColor == -2 ? ParlorDraw.rainbow(now, 400, 0) : endColor;
            float in = ParlorFx.easeOutBack(Math.min(1.0F, (4000 - (endUntil - now)) / 300.0F));
            int half = Math.max(1, Math.round(12 * in));
            g.fill(lx, ly + lh / 2 - half, lx + lw, ly + lh / 2 + half, 0xC0000000);
            if (in > 0.6F) {
                ParlorDraw.bigCentered(g, font, endText, lx + lw / 2, ly + lh / 2, 1.0F, c, 0xFF000000);
            }
        }
        // 紙吹雪・火花・光
        fx.render(g, now, cx, top + 4, cx + cw, top + H - 4);
        fx.renderFlash(g, bx, by, bw, bh, now);
    }

    /** 大当たりのファンファーレ（短い上がりの音の並び）。 */
    private void playFanfare(long now) {
        if (fanfareAt <= 0) {
            return;
        }
        int[] at = {0, 130, 260, 390, 650, 780};
        float[] pitch = {1.0F, 1.26F, 1.5F, 2.0F, 1.5F, 2.0F};
        while (fanfareNote < at.length && now - fanfareAt >= at[fanfareNote]) {
            ParlorDraw.play(SoundEvents.NOTE_BLOCK_BELL.get(), 0.55F, pitch[fanfareNote]);
            fanfareNote++;
        }
    }

    /**
     * 役物（盤面の上の、結晶の飾り）。ふだんは盤面の上にしまってあり、強いリーチ（スーパー以上・カットインが赤以上）と大当たりで、
     * 液晶の上のふちまで落ちてはずむ。落ちている間は光の筋が回る。止まって終わると、上へ戻る。
     */
    private void renderGimmick(GuiGraphics g, int x, int boardTop, int lcdTop, long now, boolean spinning, boolean strong, boolean fanfare) {
        if ((strong || fanfare) && !gimmickDown) {
            gimmickDown = true;
            gimmickDropAt = now;
            ParlorDraw.play(SoundEvents.ANVIL_LAND, 0.25F, 1.5F);
            ParlorDraw.play(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 1.2F);
            fx.shake(350, 2.0F);
        } else if (gimmickDown && !spinning && !fanfare) {
            gimmickDown = false;
            gimmickRiseAt = now;
        }
        float rest = boardTop + 3;
        float drop = lcdTop - 3;
        float y;
        if (gimmickDown) {
            y = rest + (drop - rest) * ParlorFx.bounce((now - gimmickDropAt) / 450.0F);
        } else if (now - gimmickRiseAt < 400) {
            y = drop - (drop - rest) * ParlorFx.easeOut((now - gimmickRiseAt) / 400.0F);
        } else {
            y = rest;
        }
        int gem = fanfare ? ParlorDraw.rainbow(now, 500, 0) : gimmickDown ? ParlorDraw.mix(accent, 0xFFFFFFFF, 0.35F) : accent;
        if (gimmickDown || fanfare) {
            // 回る光の筋
            for (int k = 0; k < 8; k++) {
                double a = k / 8.0 * Math.PI * 2 + now / 300.0;
                ParlorDraw.line(g, x, y, x + (float) Math.cos(a) * 16, y + (float) Math.sin(a) * 16, ParlorDraw.alpha(gem, 0.35F));
            }
            ParlorDraw.disc(g, x, y, 9, ParlorDraw.alpha(gem, 0.25F));
        }
        // ひし形の結晶（上は明るく、下は暗く）
        for (int dy = -7; dy <= 7; dy++) {
            int half = Math.round(6 - Math.abs(dy) * 0.8F);
            if (half <= 0) {
                continue;
            }
            int c = dy < 0 ? ParlorDraw.lighter(gem, 0.35F) : ParlorDraw.darker(gem, 0.75F);
            g.fill(x - half, Math.round(y + dy), x + half, Math.round(y + dy + 1), c | 0xFF000000);
        }
        g.fill(x - 1, Math.round(y - 5), x, Math.round(y - 2), 0xFFFFFFFF);
    }

    private void renderHandle(GuiGraphics g, long now) {
        int x = handleX();
        int y = handleY();
        ParlorDraw.disc(g, x, y, 18, 0xFF303038);
        // 良い強さ（55〜80）の帯
        for (int v = 0; v <= 100; v += 2) {
            double a = Math.PI + v / 100.0 * Math.PI;
            int px = x + (int) (Math.cos(a) * 15);
            int py = y + (int) (Math.sin(a) * 15);
            int c = v >= 55 && v <= 80 ? 0xFF40E060 : 0xFF707080;
            g.fill(px, py, px + 1, py + 1, c);
        }
        double a = Math.PI + strength / 100.0 * Math.PI;
        ParlorDraw.line(g, x, y, x + (float) Math.cos(a) * 13, y + (float) Math.sin(a) * 13, shooting ? 0xFFFFE040 : 0xFFC0C0C8);
        ParlorDraw.disc(g, x, y, 4, shooting ? 0xFFE0A020 : 0xFF808088);
        g.drawString(font, Integer.toString(strength), x - font.width(Integer.toString(strength)) / 2, y + 6, 0xFFFFFF, false);
    }

    private void stepBalls(GuiGraphics g, int bx, int by, int bw, int bh, long dt, int hx, int hy, int tx, int ty, int ax, int ay) {
        float s = dt / 1000.0F;
        for (int i = balls.size() - 1; i >= 0; i--) {
            Ball b = balls.get(i);
            b.t += s;
            if (b.t < 0) {
                continue;
            }
            if (b.t < 0.35F) {
                // レールを上がる
                double p = b.t / 0.35;
                double a = Math.PI * (1.0 - p * (b.rightShot ? 0.86 : 0.55));
                b.x = (float) (bx + bw / 2.0 + Math.cos(a) * (bw / 2.0 - 4));
                b.y = (float) (by + bh / 2.0 - Math.sin(a) * (bh / 2.0 - 4));
                b.vx = b.rightShot ? 6 : 14;
                b.vy = 0;
            } else {
                b.vy += 260 * s;
                float targetX = b.kind == 1 ? hx : b.kind == 2 ? tx : b.kind == 3 ? ax : b.rightShot ? bx + bw - 14 : bx + 30 + (i * 37 % 60);
                b.vx += (targetX - b.x) * 2.2F * s + (random.nextFloat() - 0.5F) * 80 * s;
                b.vx *= 0.98F;
                b.x += b.vx * s;
                b.y += b.vy * s;
                float endY = b.kind == 1 ? hy : b.kind == 2 ? ty + 4 : b.kind == 3 ? ay : by + bh - 4;
                if (b.y >= endY) {
                    balls.remove(i);
                    continue;
                }
            }
            ParlorDraw.disc(g, b.x, b.y, 1.8F, 0xFFE8E8F0);
            g.fill(Math.round(b.x) - 1, Math.round(b.y) - 1, Math.round(b.x), Math.round(b.y), 0xFFFFFFFF);
        }
        while (balls.size() > 80) {
            balls.remove(0);
        }
    }

    private void renderHolds(GuiGraphics g, int x, int y, int w, long now) {
        int[] denchu = state.getIntArray("denchu");
        // 当該（回っている保留）。低い色で見せていた保留は、回っている間に本当の色へ変わる
        if (script != null && state.getBoolean("spinning")) {
            if (current != null) {
                drawHoldView(g, x + 8, y + 5, 4.5F, current, now);
            } else {
                drawHold(g, x + 8, y + 5, 4.5F, si("hold"), now);
            }
        }
        // 保留が1つ進んだら、左へすべる
        float slide = 12.0F * (1.0F - ParlorFx.easeOut((now - shiftAt) / 180.0F));
        for (int i = 0; i < 4; i++) {
            float hx = x + 26 + i * 12 + slide;
            if (i < holdViews.size()) {
                drawHoldView(g, hx, y + 5, 3.5F, holdViews.get(i), now);
            } else {
                ParlorDraw.disc(g, x + 26 + i * 12, y + 5, 3.5F, 0x40FFFFFF);
            }
        }
        for (int i = 0; i < 4; i++) {
            int hx = x + w - 48 + i * 11;
            if (i < denchu.length) {
                drawHold(g, hx, y + 5, 3.0F, denchu[i], now);
            } else {
                ParlorDraw.disc(g, hx, y + 5, 3.0F, 0x30FFFFFF);
            }
        }
    }

    /** 保留1つ（保留変化の時が来たら本当の色に変え、はじけるように光らせる）。 */
    private void drawHoldView(GuiGraphics g, float x, float y, float r, HoldView view, long now) {
        if (view.shown < view.trueColor && view.upgradeAt > 0 && now >= view.upgradeAt) {
            view.shown = view.trueColor;
            view.poppedAt = now;
            int c = view.trueColor == 5 ? 0xFFFFFFFF : HOLD_COLORS[Mth.clamp(view.trueColor, 0, 4)];
            fx.burst(x, y, 10 + view.trueColor * 4, c, 0.09F, ParlorFx.STAR);
            fx.ring(x, y, c, 420, 12 + view.trueColor * 2);
            ParlorDraw.play(SoundEvents.NOTE_BLOCK_CHIME.get(), 0.6F, 0.8F + view.trueColor * 0.2F);
            ParlorDraw.play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 1.2F);
        }
        float pop = view.poppedAt > 0 && now - view.poppedAt < 320 ? 1.0F + 0.7F * (1.0F - (now - view.poppedAt) / 320.0F) : 1.0F;
        drawHold(g, x, y, r * pop, view.shown, now);
    }

    private void drawHold(GuiGraphics g, float x, float y, float r, int color, long now) {
        int c = color == 5 ? ParlorDraw.rainbow(now, 900, 0) : HOLD_COLORS[Mth.clamp(color, 0, 4)];
        if (color >= 3) {
            ParlorDraw.disc(g, x, y, r + 1.5F, ParlorDraw.alpha(c, 0.35F));
        }
        ParlorDraw.disc(g, x, y, r, c);
    }

    private void renderLcd(GuiGraphics g, int x, int y, int w, int h, long now, boolean support, boolean inRound) {
        String mode = state.getString("mode");
        int bg = screenColor;
        if (inRound) {
            bg = ParlorDraw.mix(screenColor, ParlorDraw.rainbow(now, 4000, 0), 0.45F);
        } else if ("KAKUHEN".equals(mode)) {
            bg = ParlorDraw.mix(screenColor, 0xFFC03018, 0.55F + 0.1F * (float) Math.sin(now / 250.0));
        } else if ("ST".equals(mode)) {
            bg = state.getBoolean("lt") ? ParlorDraw.mix(screenColor, ParlorDraw.rainbow(now, 2500, 0), 0.5F)
                    : ParlorDraw.mix(screenColor, 0xFFD0A020, 0.45F);
        } else if ("JITAN".equals(mode)) {
            bg = ParlorDraw.mix(screenColor, 0xFF5040B0, 0.5F);
        }
        double t = script == null ? 1.0E9 : (now - spinStart) / 50.0;
        String reach = ss("reach");
        boolean inReach = script != null && !reach.isEmpty() && t >= si("right") && t < si("center");
        boolean bigReach = inReach && ("super".equals(reach) || "special".equals(reach) || "all".equals(reach));
        if (bigReach && t > si("right") + 20) {
            bg = ParlorDraw.mix(bg, "special".equals(reach) ? 0xFF400010 : 0xFF000000, 0.6F);
        }
        ParlorDraw.inset(g, x, y, w, h, bg & 0xFFFFFF);
        g.enableScissor(x, y, x + w, y + h);
        // 背景の動き（光の粒）
        for (int i = 0; i < 18; i++) {
            float px = x + (i * 37 + now / 30.0F * (1 + i % 3)) % w;
            float py = y + (i * 23) % h;
            g.fill((int) px, (int) py, (int) px + 1, (int) py + 1, ParlorDraw.alpha(glowColor, 0.25F + (i % 4) * 0.12F));
        }
        // 11月28日：スーパー以上のリーチは、真ん中へ向かう集中線（回る）。スペシャルは画面が小さく揺れる
        if (bigReach && t > si("right") + 12) {
            int mx = x + w / 2;
            int my = y + h / 2 + 2;
            for (int k = 0; k < 20; k++) {
                double a = k / 20.0 * Math.PI * 2 + now / 900.0;
                float r0 = 24 + (k % 3) * 4;
                float r1 = 90;
                ParlorDraw.line(g, mx + (float) Math.cos(a) * r0, my + (float) Math.sin(a) * r0 * 0.7F, mx + (float) Math.cos(a) * r1,
                        my + (float) Math.sin(a) * r1 * 0.7F, ParlorDraw.alpha(0xFFFFFF, 0.18F + (k % 2) * 0.1F));
            }
        }
        if ("special".equals(reach) && inReach && t > si("right") + 20) {
            int sx = (int) Math.round(Math.sin(now / 37.0) * 1.5);
            int sy = (int) Math.round(Math.cos(now / 29.0) * 1.0);
            x += sx;
            y += sy;
        }
        if (inRound) {
            renderRound(g, x, y, w, h, now);
        } else {
            renderDigits(g, x, y, w, h, now, t, reach, inReach);
            renderNotices(g, x, y, w, h, now, t, reach, inReach);
            // 状態（確変・ST・時短）
            if (!"NORMAL".equals(mode) && !mode.isEmpty()) {
                String name = switch (mode) {
                    case "KAKUHEN" -> label("kakuhen", "確変");
                    case "ST" -> state.getBoolean("lt") ? label("lt", "LT") : label("st", "ST");
                    default -> label("jitan", "時短");
                };
                int c = "JITAN".equals(mode) ? 0xFF90C8FF : ParlorDraw.rainbow(now, 1500, 0);
                g.drawString(font, name, x + 4, y + 3, c, false);
                String right = "KAKUHEN".equals(mode)
                        ? Component.translatable("screen.amemaru.parlor.chain", state.getInt("chain")).getString()
                        : Component.translatable("screen.amemaru.parlor.spins_left", state.getInt("modeSpins")).getString();
                g.drawString(font, right, x + w - 4 - font.width(right), y + 3, 0xFFFFFFFF, false);
            } else {
                g.drawString(font, label("stage", ""), x + 4, y + 3, ParlorDraw.alpha(accent, 0.9F), false);
            }
        }
        g.disableScissor();
    }

    /** 3つの図柄。 */
    private void renderDigits(GuiGraphics g, int x, int y, int w, int h, long now, double t, String reach, boolean inReach) {
        int[] fin = new int[3];
        if (script != null) {
            JsonArray d = script.getAsJsonArray("digits");
            for (int i = 0; i < 3; i++) {
                fin[i] = d.get(i).getAsInt();
            }
        } else {
            fin = shown;
        }
        int[] stopAt = {si("left"), si("center"), si("right")};
        boolean all = "all".equals(reach);
        int colW = w / 3;
        for (int col = 0; col < 3; col++) {
            float scale = 3.0F;
            int ccx = x + colW * col + colW / 2;
            int ccy = y + h / 2 + 2;
            boolean stopped = script == null || t >= stopAt[col];
            // 11月28日：止まった瞬間に、図柄が小さくはねて白く光る（カチッと鳴る）
            if (script != null && stopped && !colStopped[col]) {
                colStopped[col] = true;
                colStopAt[col] = now;
                ParlorDraw.play(SoundEvents.UI_BUTTON_CLICK.get(), 0.12F, 1.7F);
            }
            long sinceStop = now - colStopAt[col];
            if (script != null && stopped && sinceStop < 220) {
                ccy -= Math.round((float) Math.sin(sinceStop / 220.0 * Math.PI) * 4 * (1.0F - sinceStop / 220.0F));
            }
            // リーチの揃った2つ（左と右）は、脈を打つように大きくなり、うしろが光る
            if (inReach && !all && col != 1) {
                scale *= 1.0F + 0.07F * (float) Math.sin(now / 90.0);
                ParlorDraw.glow(g, ccx - 13, ccy - 14, 26, 28, accent, 0.18F);
            }
            int digit;
            float slide = 0;
            if (stopped) {
                digit = fin[col];
            } else if (col == 1 && inReach && !all) {
                // リーチ：真ん中の図柄はゆっくり回る
                double left = stopAt[1] - t;
                double period = 6 + Math.max(0, 22 - left / 10.0);
                int steps = (int) Math.ceil(left / period);
                digit = Math.floorMod(fin[1] - steps - 1, 9) + 1;
                slide = (float) ((left % period) / period);
            } else if (all && t >= 60) {
                double left = stopAt[col] - t;
                digit = Math.floorMod(fin[col] - (int) (left / 8) - 1, 9) + 1;
                slide = (float) ((left % 8) / 8);
            } else {
                double phase = t * 0.9 + col * 3.1;
                digit = (int) (phase % 9) + 1;
                slide = (float) (phase % 1);
            }
            int color = DIGIT_COLORS[digit];
            if (all && !stopped) {
                color = ParlorDraw.rainbow(now, 700, col / 3.0F);
            }
            boolean hitFlash = script != null && sb("hit") && t >= si("center") && (now / 120) % 2 == 0;
            if (hitFlash) {
                color = 0xFFFFFFFF;
            }
            if (!stopped) {
                // 上下に流れる
                int prev = digit % 9 + 1;
                ParlorDraw.bigCentered(g, font, Integer.toString(prev), ccx, (int) (ccy - 30 + slide * 30), scale * 0.8F,
                        ParlorDraw.alpha(DIGIT_COLORS[prev], 0.35F), 0);
                ParlorDraw.bigCentered(g, font, Integer.toString(digit), ccx, (int) (ccy + slide * 30), scale, color, 0xFF000000);
            } else {
                ParlorDraw.bigCentered(g, font, Integer.toString(digit), ccx, ccy, scale, color, 0xFF000000);
                if (script != null && sinceStop < 90) {
                    g.fill(ccx - 11, ccy - 13, ccx + 11, ccy + 13, ParlorDraw.alpha(0xFFFFFF, 0.5F * (1.0F - sinceStop / 90.0F)));
                }
            }
        }
        if (script != null && t >= si("center") && !spinSoundEnd) {
            spinSoundEnd = true;
            shown = fin;
            if (sb("hit")) {
                ParlorDraw.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.7F, 1.0F);
                ParlorDraw.play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.2F);
                // 11月28日：大当たり！ 画面が白く光り、紙吹雪と光の輪、ファンファーレ
                fanfareAt = now;
                fanfareNote = 0;
                fx.flash(0xFFFFFFFF, 280);
                fx.shake(450, 2.5F);
                if (boardW > 0) {
                    fx.confetti(boardX, boardX + boardW, boardY, 110, null);
                }
                fx.ring(x + w / 2, y + h / 2, 0xFFFFE060, 700, 70);
                fx.ring(x + w / 2, y + h / 2, 0xFFFFFFFF, 480, 44);
                fx.burst(x + w / 2, y + h / 2, 40, 0, 0.16F, ParlorFx.STAR);
            } else if (!reach.isEmpty()) {
                ParlorDraw.play(SoundEvents.NOTE_BLOCK_BASS.get(), 0.5F, 0.5F);
            }
        }
        if (script != null && sb("hit") && t >= si("center")) {
            waveText(g, Component.translatable("screen.amemaru.parlor.jackpot").getString(), x + w / 2, y + h - 12, 1.4F, now);
        }
    }

    /** 1文字ずつ波打つ、虹の色の文字（大当たり）。 */
    private void waveText(GuiGraphics g, String text, int cx, int cy, float scale, long now) {
        int total = 0;
        int[] widths = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            widths[i] = font.width(String.valueOf(text.charAt(i)));
            total += widths[i];
        }
        float x = cx - total * scale / 2.0F;
        for (int i = 0; i < text.length(); i++) {
            float wave = (float) Math.sin(now / 90.0 - i * 0.7) * 2.5F;
            float cw = widths[i] * scale;
            ParlorDraw.bigCentered(g, font, String.valueOf(text.charAt(i)), Math.round(x + cw / 2.0F), Math.round(cy + wave), scale,
                    ParlorDraw.rainbow(now, 500, i / (float) Math.max(1, text.length())), 0xFF000000);
            x += cw;
        }
    }

    /** 予告とリーチの見せ方。 */
    private void renderNotices(GuiGraphics g, int x, int y, int w, int h, long now, double t, String reach, boolean inReach) {
        if (script == null || t >= si("center") + 20) {
            return;
        }
        boolean support = sb("support");
        // ステップアップ予告
        int step = si("step");
        if (!support && step > 0 && t >= 8 && t < 8 + step * 12 + 30) {
            int shownSteps = Math.min(step, (int) ((t - 8) / 12) + 1);
            int[] colors = {0xFFFFFFFF, 0xFF4090FF, 0xFF40D060, 0xFFFF3040, 0xFFFFC830};
            for (int i = 0; i < shownSteps; i++) {
                int c = i == 4 ? ParlorDraw.rainbow(now, 600, 0) : colors[i];
                int sx = x + 6 + i * 22;
                g.fill(sx, y + 14, sx + 20, y + 26, ParlorDraw.alpha(0x000000, 0.6F));
                g.drawString(font, "STEP" + (i + 1), sx + 1, y + 16, c, false);
            }
        }
        // 群れの予告
        if (sb("group") && t >= 14 && t < 44) {
            float p = (float) ((t - 14) / 30.0);
            for (int i = 0; i < 16; i++) {
                float px = x + w + 20 - p * (w + 60) + (i % 4) * 9;
                float py = y + 20 + (i * 13) % (h - 30);
                ParlorDraw.disc(g, px, py, 2.5F, ParlorDraw.alpha(accent, 0.9F));
                g.fill((int) px + 2, (int) py, (int) px + 6, (int) py + 1, ParlorDraw.alpha(accent, 0.6F));
            }
            if (t < 16) {
                ParlorDraw.play(SoundEvents.PHANTOM_FLAP, 0.4F, 1.6F);
            }
        }
        // 擬似連
        int pseudo = si("pseudo");
        for (int k = 1; k <= pseudo; k++) {
            double at = 30 + (k - 1) * 60 + 40;
            if (t >= at && t < at + 18) {
                ParlorDraw.bigCentered(g, font, "NEXT ×" + (k + 1), x + w / 2, y + 22, 1.4F, k >= 2 ? 0xFFFF4040 : 0xFF60C0FF, 0xFF000000);
                if (t < at + 1) {
                    ParlorDraw.play(SoundEvents.NOTE_BLOCK_PLING.get(), 0.5F, 1.2F + k * 0.2F);
                }
            }
        }
        // リーチ
        if (inReach) {
            double since = t - si("right");
            if (!spinSoundReach) {
                spinSoundReach = true;
                ParlorDraw.play(SoundEvents.NOTE_BLOCK_PLING.get(), 0.6F, 1.5F);
                ParlorDraw.play(SoundEvents.NOTE_BLOCK_PLING.get(), 0.6F, 2.0F);
            }
            if (since < 30) {
                // 11月28日：大きく飛びこんでから、少し行き過ぎて落ち着く（はじめは小さく震える）
                float k = ParlorFx.easeOutBack((float) since / 6.0F);
                float sc = 3.0F - 1.4F * k;
                int jitter = since < 10 ? (int) Math.round(Math.sin(now / 25.0) * 1.5) : 0;
                ParlorDraw.bigCentered(g, font, Component.translatable("screen.amemaru.parlor.reach").getString(), x + w / 2 + jitter, y + 18,
                        sc, 0xFFFFE040, 0xFF800000);
            }
            String title = switch (reach) {
                case "super" -> label("super", "SUPER");
                case "special" -> label("special", "SPECIAL");
                case "all" -> label("all", "ALL");
                default -> "";
            };
            if (!title.isEmpty() && since >= 20) {
                int c = "special".equals(reach) || "all".equals(reach) ? ParlorDraw.rainbow(now, 900, 0) : 0xFFFFD050;
                g.fill(x, y + 12, x + w, y + 24, 0xA0000000);
                ParlorDraw.bigCentered(g, font, title, x + w / 2, y + 18, 1.0F, c, 0xFF000000);
                if ("special".equals(reach) && (now / 90) % 7 == 0) {
                    g.fill(x, y, x + w, y + h, 0x40FFFFFF);
                }
            }
            if (sb("hot") && since >= 20 && since < 60) {
                ParlorDraw.bigCentered(g, font, Component.translatable("screen.amemaru.parlor.hot").getString(), x + w / 2, y + h - 20, 2.0F,
                        0xFFFF2020, 0xFFFFE040);
            }
            // カットイン
            int cut = si("cutin");
            double reachLen = si("center") - si("right");
            double cutAt = reachLen * 0.55;
            if (cut > 0 && since >= cutAt && since < cutAt + 28) {
                // 11月28日：斜めの帯が、行き過ぎるように飛びこみ、止まって、速く抜けていく。うしろに流れる線、左に結晶の印
                int[] colors = {0, 0xFF3C8CFF, 0xFF40D060, 0xFFFF3040, 0xFFFFC830, 0};
                int c = cut == 5 ? ParlorDraw.rainbow(now, 400, 0) : colors[cut];
                float p = (float) ((since - cutAt) / 28.0);
                float enter = ParlorFx.easeOutBack(Math.min(1.0F, p / 0.25F));
                float exit = p > 0.75F ? ParlorFx.easeIn((p - 0.75F) / 0.25F) : 0.0F;
                int off = Math.round((1.0F - enter) * (w + 40) - exit * (w + 40));
                int cy = y + h / 2;
                int bandL = x + 6 + off;
                int bandR = x + w - 6 + off;
                for (int r = -15; r <= 15; r++) {
                    int skew = Math.round(-r * 0.6F);
                    int rowColor = Math.abs(r) >= 14 ? 0xFFFFFFFF : r < 0 ? ParlorDraw.lighter(c, 0.2F) | 0xFF000000 : c;
                    g.fill(bandL + skew, cy + r, bandR + skew, cy + r + 1, rowColor);
                }
                for (int k = 0; k < 9; k++) {
                    int ly = cy - 12 + (k * 7) % 25;
                    int len = 10 + (k * 5) % 14;
                    int lx = bandR - (int) ((now / 2 + k * 37) % Math.max(1, bandR - bandL));
                    g.fill(lx, ly, lx + len, ly + 1, ParlorDraw.alpha(0xFFFFFF, 0.45F));
                }
                String[] words = {"", "CHANCE", "チャンス!", "激アツ!", "大チャンス!!", "確定!!"};
                int textX = Math.max(x + 34, Math.min(x + w - 30, (bandL + bandR) / 2 + 8));
                drawCrest(g, textX - font.width(words[cut]) * 3 / 4 - 12, cy, 0xFFFFFFFF);
                ParlorDraw.bigCentered(g, font, words[cut], textX, cy, 1.5F, 0xFFFFFFFF, 0xFF000000);
                if (!spinSoundCut) {
                    spinSoundCut = true;
                    fx.flash(c, 160);
                    ParlorDraw.play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 0.8F + cut * 0.2F);
                    ParlorDraw.play(SoundEvents.NOTE_BLOCK_BIT.get(), 0.5F, 1.0F + cut * 0.15F);
                    ParlorDraw.play(SoundEvents.PLAYER_ATTACK_SWEEP, 0.4F, 1.6F);
                }
            }
        }
    }

    /** 小さな結晶の印（カットインの帯の左）。 */
    private static void drawCrest(GuiGraphics g, int x, int y, int color) {
        for (int dy = -6; dy <= 6; dy++) {
            int half = 5 - Math.abs(dy) * 5 / 6;
            if (half > 0) {
                g.fill(x - half, y + dy, x + half, y + dy + 1, color);
            }
        }
        g.fill(x - 1, y - 3, x, y, 0xFF000000);
    }

    /** 大当たりのラウンド。 */
    private void renderRound(GuiGraphics g, int x, int y, int w, int h, long now) {
        CompoundTag r = state.getCompound("round");
        String phase = r.getString("phase");
        boolean kakuhen = r.getBoolean("kakuhen");
        boolean promoted = r.getBoolean("promoted");
        int round = r.getInt("round");
        int rounds = r.getInt("rounds");
        // 図柄（左上に小さく）
        int digit = shown[0];
        if (promoted && round >= Math.max(2, rounds - 1) && kakuhen) {
            if (!promotedShown) {
                promotedShown = true;
                ParlorDraw.play(SoundEvents.PLAYER_LEVELUP, 0.7F, 1.4F);
            }
            digit = digit % 2 == 0 ? digit + 1 : digit;
            if ((now / 400) % 2 == 0) {
                ParlorDraw.bigCentered(g, font, Component.translatable("screen.amemaru.parlor.promote").getString(), x + w / 2, y + 22, 1.6F,
                        ParlorDraw.rainbow(now, 500, 0), 0xFF000000);
            }
        }
        ParlorDraw.bigCentered(g, font, digit + " " + digit + " " + digit, x + 30, y + 12, 1.2F, DIGIT_COLORS[Mth.clamp(digit, 1, 9)],
                0xFF000000);
        switch (phase) {
            case "OPENING" -> {
                ParlorDraw.bigCentered(g, font, Component.translatable("screen.amemaru.parlor.jackpot").getString(), x + w / 2, y + h / 2 - 8,
                        2.2F, ParlorDraw.rainbow(now, 500, 0), 0xFF000000);
                ParlorDraw.bigCentered(g, font, r.getString("name"), x + w / 2, y + h / 2 + 16, 1.2F, 0xFFFFFFFF, 0xFF000000);
            }
            case "ROUND", "INTERVAL" -> {
                // 11月28日：ラウンドが変わると、文字が左から飛びこむ。1カウントごとに「+」が浮かぶ
                if (round != roundShown) {
                    roundShown = round;
                    roundAt = now;
                    countShown = 0;
                    roundPops.clear();
                }
                int slideIn = Math.round((1.0F - ParlorFx.easeOutBack(Math.min(1.0F, (now - roundAt) / 300.0F))) * -w);
                ParlorDraw.bigCentered(g, font, "ROUND " + round + " / " + rounds, x + w / 2 + slideIn, y + 34, 1.5F, 0xFFFFE060, 0xFF000000);
                int count = r.getInt("count");
                int barW = w - 30;
                g.fill(x + 15, y + 50, x + 15 + barW, y + 56, 0xFF202020);
                int cMax = model.has("count") ? model.get("count").getAsInt() : 10;
                int fill = barW * Math.min(count, cMax) / cMax;
                g.fill(x + 15, y + 50, x + 15 + fill, y + 56, ParlorDraw.rainbow(now, 1200, 0));
                if (count > countShown) {
                    int pay = model.has("ball_pay") ? model.get("ball_pay").getAsInt() : 15;
                    for (int k = countShown; k < count; k++) {
                        roundPops.add(new long[]{now + (k - countShown) * 60L, pay});
                    }
                    countShown = count;
                    if (boardW > 0) {
                        fx.burst(boardX + boardW / 2 + 42, boardY + 156, 6, 0xFFFFE060, 0.08F, ParlorFx.SPARK);
                    }
                }
                for (int k = roundPops.size() - 1; k >= 0; k--) {
                    long[] pop = roundPops.get(k);
                    long age = now - pop[0];
                    if (age > 700) {
                        roundPops.remove(k);
                    } else if (age >= 0) {
                        float a = 1.0F - age / 700.0F;
                        g.drawString(font, "+" + pop[1], x + 15 + fill - 6, y + 46 - (int) (age / 40), ParlorDraw.alpha(0xFFFFE060, a), false);
                    }
                }
                ParlorDraw.bigCentered(g, font, Component.translatable("screen.amemaru.parlor.paid", r.getInt("paid")).getString(),
                        x + w / 2, y + 70, 1.2F, 0xFFFFFFFF, 0xFF000000);
                if (count != lastCount && count > 0) {
                    lastCount = count;
                }
                if (round != lastRound) {
                    lastRound = round;
                    lastCount = 0;
                }
            }
            case "ENDING" -> {
                // 11月28日：払い出しの数を、0から数え上げる
                int paid = r.getInt("paid");
                int counted = endingAt > 0 ? Math.round(paid * ParlorFx.easeOut((now - endingAt) / 1200.0F)) : paid;
                ParlorDraw.bigCentered(g, font, Component.translatable("screen.amemaru.parlor.paid", counted).getString(),
                        x + w / 2, y + h / 2 + 14, 1.3F, 0xFFFFFFFF, 0xFF000000);
                ParlorDraw.bigCentered(g, font, kakuhen ? Component.translatable("screen.amemaru.parlor.enter",
                                label(r.getString("jackpot").startsWith("lt") ? "lt" : (label("st", "").isEmpty() ? "kakuhen" : "st"), "確変")).getString()
                                : Component.translatable("screen.amemaru.parlor.round_end").getString(), x + w / 2, y + h / 2 - 8, 1.3F,
                        kakuhen ? ParlorDraw.rainbow(now, 700, 0) : 0xFFC0E0FF, 0xFF000000);
            }
            default -> {
            }
        }
    }

    @Override
    protected void renderData(GuiGraphics g, int x, int y, long now) {
        g.drawString(font, Component.translatable("screen.amemaru.parlor.data_today"), x, y, accent, false);
        int row = y + 12;
        dataLine(g, x, row, Component.translatable("screen.amemaru.parlor.d_spins"), num(rec("spins")), 0xFFFFFF);
        dataLine(g, x, row + 10, Component.translatable("screen.amemaru.parlor.d_since"), num(rec("since")), 0xFFE070);
        dataLine(g, x, row + 20, Component.translatable("screen.amemaru.parlor.d_hits"), num(rec("hits")), 0xFF7080);
        dataLine(g, x, row + 30, Component.translatable("screen.amemaru.parlor.d_first"), num(rec("firstHits")), 0xFFB070);
        dataLine(g, x, row + 40, Component.translatable("screen.amemaru.parlor.d_chain"), num(rec("maxChain")), 0x80C0FF);
        int firsts = rec("firstHits");
        dataLine(g, x, row + 50, Component.translatable("screen.amemaru.parlor.d_prob"),
                firsts == 0 ? "-" : String.format(Locale.ROOT, "1/%.1f", rec("spins") / (double) firsts), 0xFFFFFF);
        JsonArray hits = recordJson.has("hitList") ? recordJson.getAsJsonArray("hitList") : new JsonArray();
        int hy = row + 64;
        g.drawString(font, Component.translatable("screen.amemaru.parlor.history"), x, hy, 0xA8A0C0, false);
        for (int i = 0; i < Math.min(5, hits.size()); i++) {
            JsonObject h = hits.get(i).getAsJsonObject();
            int chain = h.get("chain").getAsInt();
            String line = (chain <= 1 ? "◆ " : "  ") + h.get("at").getAsInt() + Component.translatable("screen.amemaru.parlor.spins_unit")
                    .getString() + "  " + num(h.get("balls").getAsInt());
            g.drawString(font, line, x, hy + 10 + i * 9, chain <= 1 ? 0xFFB070 : 0xFF7080, false);
        }
    }
}

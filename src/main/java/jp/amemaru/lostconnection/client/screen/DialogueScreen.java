package jp.amemaru.lostconnection.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.DialogueChoiceC2S;
import jp.amemaru.lostconnection.network.s2c.DialogueS2C;
import jp.amemaru.lostconnection.npc.NpcDef;
import jp.amemaru.lostconnection.npc.NpcDefs;
import jp.amemaru.lostconnection.npc.NpcEntity;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 会話の画面（Phase 0-A 14章「会話GUI」：画面下に吹き出し、左に立ち絵（表情差分）、名前札。選択肢は最大4つ。文字送り、ログ
 * （さかのぼって読める）、既読のみスキップ。配信向けに、会話中も画面を暗くしすぎない）。
 *
 * <ul>
 *   <li>立ち絵は、NPCの体（プレイヤーと同じ形のスキン）を大きく描く。表情は顔の絵の差し替え</li>
 *   <li>クリック・スペース・エンターで文字送り（文字が出きる前なら、全部出す）</li>
 *   <li>L でログ。2回目からの会話は「スキップ」で選択肢まで飛ばせる</li>
 *   <li>Esc で閉じる（最後まで読んでいないので、会話の終わりの処理はしない）</li>
 * </ul>
 */
public class DialogueScreen extends Screen {
    private static final float CHARS_PER_SECOND = 32.0F;
    private static final int BOX_HEIGHT = 74;

    private int session;
    private ResourceLocation npc;
    private final List<DialogueS2C.Page> pages = new ArrayList<>();
    private final List<DialogueS2C.Page> log = new ArrayList<>();
    private List<DialogueS2C.ChoiceView> choices = List.of();
    private boolean ends;
    private boolean read;
    private boolean replay;
    private int page;
    private long pageStart = Util.getMillis();
    private boolean showLog;
    private boolean done;
    @Nullable
    private NpcEntity portrait;
    @Nullable
    private ResourceLocation portraitNpc;

    public DialogueScreen(DialogueS2C message) {
        super(Component.translatable("screen.amemaru.dialogue"));
        accept(message);
    }

    /** 同じ会話の続き（選んだ後）を受け取る。 */
    public void accept(DialogueS2C message) {
        // 前の吹き出しは、読み進めたときにログへ入っている
        pages.clear();
        page = 0;
        session = message.session();
        npc = message.npc();
        pages.addAll(message.pages());
        choices = message.choices();
        ends = message.ends();
        read = message.read();
        replay = message.replay();
        pageStart = Util.getMillis();
        done = false;
        if (!pages.isEmpty()) {
            log.add(pages.get(0));
        }
        if (pages.isEmpty() && choices.isEmpty() && ends) {
            finish(-1);
        }
    }

    public int session() {
        return session;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
    }

    // ---------------------------------------------------------------- 進める

    @Nullable
    private DialogueS2C.Page current() {
        return page < pages.size() ? pages.get(page) : null;
    }

    private int visibleChars() {
        return (int) ((Util.getMillis() - pageStart) / 1000.0F * CHARS_PER_SECOND);
    }

    private boolean typing() {
        DialogueS2C.Page p = current();
        return p != null && visibleChars() < p.text().length();
    }

    private boolean atChoices() {
        return page >= pages.size() - 1 && !typing() && !choices.isEmpty();
    }

    private void advance() {
        if (done) {
            return;
        }
        if (typing()) {
            pageStart = Util.getMillis() - 100_000L;
            return;
        }
        if (page < pages.size() - 1) {
            page++;
            pageStart = Util.getMillis();
            log.add(pages.get(page));
            click();
            return;
        }
        if (choices.isEmpty()) {
            finish(-1);
        }
    }

    private void skip() {
        if (page < pages.size() - 1) {
            for (int i = page + 1; i < pages.size(); i++) {
                log.add(pages.get(i));
            }
            page = pages.size() - 1;
        }
        pageStart = Util.getMillis() - 100_000L;
        if (choices.isEmpty()) {
            finish(-1);
        }
    }

    private void choose(int index) {
        if (done || index < 0 || index >= choices.size()) {
            return;
        }
        DialogueS2C.ChoiceView choice = choices.get(index);
        log.add(new DialogueS2C.Page("player", Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getGameProfile()
                .getName(), null, "", choice.text()));
        choices = List.of();
        click();
        ModNetwork.sendToServer(new DialogueChoiceC2S(session, choice.index()));
        // 続きが来るまで待つ（来なければ、閉じる知らせが来る）
        pages.clear();
        page = 0;
    }

    private void finish(int code) {
        if (done) {
            return;
        }
        done = true;
        ModNetwork.sendToServer(new DialogueChoiceC2S(session, code));
        Minecraft.getInstance().setScreen(null);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6F, 0.25F));
    }

    @Override
    public void onClose() {
        if (!done) {
            done = true;
            ModNetwork.sendToServer(new DialogueChoiceC2S(session, -2));
        }
        super.onClose();
    }

    /** サーバーから閉じる知らせが来た。 */
    public void closeFromServer() {
        done = true;
        Minecraft.getInstance().setScreen(null);
    }

    // ---------------------------------------------------------------- 入力

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showLog) {
            showLog = false;
            return true;
        }
        int[] box = box();
        // 選択肢
        if (atChoices()) {
            for (int i = 0; i < choices.size(); i++) {
                int[] r = choiceRect(i, box);
                if (mouseX >= r[0] && mouseX <= r[2] && mouseY >= r[1] && mouseY <= r[3]) {
                    choose(i);
                    return true;
                }
            }
            return true;
        }
        // スキップ・ログのボタン
        int[] skip = skipRect(box);
        if (read && mouseX >= skip[0] && mouseX <= skip[2] && mouseY >= skip[1] && mouseY <= skip[3]) {
            skip();
            return true;
        }
        int[] logButton = logRect(box);
        if (mouseX >= logButton[0] && mouseX <= logButton[2] && mouseY >= logButton[1] && mouseY <= logButton[3]) {
            showLog = true;
            return true;
        }
        if (button == 0) {
            advance();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            if (showLog) {
                showLog = false;
                return true;
            }
            onClose();
            return true;
        }
        if (keyCode == InputConstants.KEY_L) {
            showLog = !showLog;
            return true;
        }
        if (showLog) {
            return true;
        }
        if (atChoices() && keyCode >= InputConstants.KEY_1 && keyCode <= InputConstants.KEY_4) {
            choose(keyCode - InputConstants.KEY_1);
            return true;
        }
        if (keyCode == InputConstants.KEY_SPACE || keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            advance();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---------------------------------------------------------------- 描く

    private int[] box() {
        int w = Math.min(width - 40, 440);
        int x0 = (width - w) / 2;
        int y1 = height - 14;
        return new int[]{x0, y1 - BOX_HEIGHT, x0 + w, y1};
    }

    private int[] choiceRect(int i, int[] box) {
        int w = Math.min(220, (box[2] - box[0]) / 2);
        int x1 = box[2];
        int h = 18;
        int y1 = box[1] - 10 - (choices.size() - 1 - i) * (h + 4);
        return new int[]{x1 - w, y1 - h, x1, y1};
    }

    private int[] skipRect(int[] box) {
        return new int[]{box[2] - 52, box[3] - 14, box[2] - 6, box[3] - 3};
    }

    private int[] logRect(int[] box) {
        return new int[]{box[2] - 98, box[3] - 14, box[2] - 58, box[3] - 3};
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 背景は暗くしすぎない（画面の下だけ少し暗く）
        graphics.fillGradient(0, height - 120, width, height, 0x00000000, 0x90000000);
        int[] box = box();
        DialogueS2C.Page p = current();
        // 立ち絵
        if (p != null && p.portrait() != null) {
            renderPortrait(graphics, box, p);
        } else if (p == null && portraitNpc != null && portrait != null) {
            renderPortraitEntity(graphics, box);
        }
        UiStyle.panel(graphics, box[0], box[1], box[2], box[3]);
        int textX = box[0] + 12;
        int textW = box[2] - box[0] - 24;
        // 名前札
        if (p != null && !p.speaker().isEmpty()) {
            int nameW = font.width(p.speaker()) + 16;
            int nx = box[0] + 8;
            int ny = box[1] - 14;
            UiStyle.card(graphics, nx, ny, nx + nameW, ny + 14, "player".equals(p.speakerKind()) ? 0xFFF2A8 : 0x8FD3FF, true);
            graphics.drawString(font, p.speaker(), nx + 8, ny + 3, "player".equals(p.speakerKind()) ? UiStyle.TEXT_GOLD : UiStyle.TEXT_HEADER,
                    false);
            NpcDef def = p.portrait() == null ? null : NpcDefs.client(p.portrait());
            if (def != null && !def.title().isEmpty()) {
                graphics.drawString(font, def.title(), nx + nameW + 6, ny + 3, UiStyle.TEXT_DIM, false);
            }
        }
        if (replay) {
            graphics.drawString(font, Component.translatable("screen.amemaru.dialogue.replay"), box[2] - 90, box[1] - 11, UiStyle.TEXT_DIM, false);
        }
        // 文
        if (p != null) {
            String text = p.text();
            int shown = Math.min(text.length(), visibleChars());
            int color = "narration".equals(p.speakerKind()) ? UiStyle.TEXT_DIM : UiStyle.TEXT;
            UiStyle.wrapped(graphics, font, text.substring(0, shown), textX, box[1] + 12, textW, color, 4);
            if (!typing() && (page < pages.size() - 1 || (choices.isEmpty() && !done))) {
                // 続きの印（下向きの小さな三角が上下に揺れる）
                int bob = (int) (Math.sin(Util.getMillis() / 180.0) * 1.5);
                graphics.drawString(font, "▼", box[2] - 16, box[3] - 24 + bob, UiStyle.BORDER_LIGHT, false);
            }
        } else if (choices.isEmpty() && !ends) {
            graphics.drawString(font, "……", textX, box[1] + 12, UiStyle.TEXT_DIM, false);
        }
        // 選択肢
        if (atChoices()) {
            for (int i = 0; i < choices.size(); i++) {
                int[] r = choiceRect(i, box);
                boolean hover = mouseX >= r[0] && mouseX <= r[2] && mouseY >= r[1] && mouseY <= r[3];
                UiStyle.card(graphics, r[0], r[1], r[2], r[3], hover ? 0xFFF2A8 : 0x8FD3FF, hover);
                graphics.drawString(font, (i + 1) + "．" + UiStyle.clip(font, choices.get(i).text(), r[2] - r[0] - 24), r[0] + 8, r[1] + 5,
                        hover ? UiStyle.TEXT_GOLD : UiStyle.TEXT, false);
            }
        }
        // ボタン
        int[] logButton = logRect(box);
        graphics.drawString(font, Component.translatable("screen.amemaru.dialogue.log"), logButton[0], logButton[1] + 2, UiStyle.TEXT_DIM, false);
        if (read && !atChoices()) {
            int[] skip = skipRect(box);
            graphics.drawString(font, Component.translatable("screen.amemaru.dialogue.skip"), skip[0], skip[1] + 2, UiStyle.TEXT_DIM, false);
        }
        if (showLog) {
            renderLog(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPortrait(GuiGraphics graphics, int[] box, DialogueS2C.Page p) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (portrait == null || !p.portrait().equals(portraitNpc)) {
            portrait = ModEntities.NPC.get().create(minecraft.level);
            portraitNpc = p.portrait();
            if (portrait != null) {
                portrait.setNpcId(p.portrait());
            }
        }
        if (portrait == null) {
            return;
        }
        portrait.setExpression(p.expression());
        renderPortraitEntity(graphics, box);
    }

    private void renderPortraitEntity(GuiGraphics graphics, int[] box) {
        if (portrait == null) {
            return;
        }
        int scale = Mth.clamp(height / 4, 46, 76);
        int x = box[0] + 44;
        int y = box[1] + (int) (scale * 1.35F);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(-4.0F * ((float) Math.PI / 180F));
        pose.mul(camera);
        portrait.yBodyRot = 205.0F;
        portrait.setYRot(200.0F);
        portrait.setXRot(-4.0F);
        portrait.yHeadRot = 195.0F;
        portrait.yHeadRotO = 195.0F;
        graphics.enableScissor(0, 0, width, box[1] + 2);
        InventoryScreen.renderEntityInInventory(graphics, x, y, scale, pose, camera, portrait);
        graphics.disableScissor();
    }

    private void renderLog(GuiGraphics graphics) {
        int w = Math.min(width - 60, 420);
        int x0 = (width - w) / 2;
        int y0 = 30;
        int y1 = height - 30;
        graphics.fill(0, 0, width, height, 0xA0000000);
        UiStyle.panel(graphics, x0, y0, x0 + w, y1);
        graphics.drawString(font, Component.translatable("screen.amemaru.dialogue.log_title"), x0 + 10, y0 + 8, UiStyle.TEXT_HEADER, false);
        UiStyle.divider(graphics, x0 + 8, x0 + w - 8, y0 + 20);
        int y = y1 - 12;
        for (int i = log.size() - 1; i >= 0 && y > y0 + 26; i--) {
            DialogueS2C.Page p = log.get(i);
            List<String> lines = UiStyle.wrap(font, p.text(), w - 40);
            y -= lines.size() * 10 + 12;
            if (y < y0 + 26) {
                break;
            }
            graphics.drawString(font, p.speaker().isEmpty() ? "　" : p.speaker(), x0 + 12, y,
                    "player".equals(p.speakerKind()) ? UiStyle.TEXT_GOLD : UiStyle.TEXT_HEADER, false);
            for (int k = 0; k < lines.size(); k++) {
                graphics.drawString(font, lines.get(k), x0 + 24, y + 11 + k * 10, UiStyle.TEXT, false);
            }
        }
    }
}

package jp.amemaru.lostconnection.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import jp.amemaru.lostconnection.client.ClientDefinitions;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.JobActionC2S;
import jp.amemaru.lostconnection.network.s2c.OpenJobScreenS2C;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 職業選択の画面（誓約の結晶を右クリックすると開く）。
 *
 * <p>職業ごとのカードに、役割・使う武器・専用ゲージ・ステータスの伸び方・最初から使えるスキル・見習い装備・説明を出す。
 * 選ぶと確認を出し、「誓う」でサーバーに送る（費用と場所はサーバーで確かめる）。職業が3つ以上あるときは、左右の矢印で切りかえる。</p>
 */
public class JobSelectScreen extends Screen {
    private static final int PANEL_WIDTH = 344;
    private static final int PANEL_HEIGHT = 226;
    private static final int CARD_WIDTH = 160;
    private static final int CARD_HEIGHT = 160;
    private static final int PER_PAGE = 2;

    private final OpenJobScreenS2C info;
    private int left;
    private int top;
    private int page;
    /** 確認中の職業（-1 なら確認していない）。 */
    private int confirming = -1;

    public JobSelectScreen(OpenJobScreenS2C info) {
        super(Component.translatable("screen.amemaru.job.title"));
        this.info = info;
        if (info.currentJob() >= 0) {
            page = info.currentJob() / PER_PAGE;
        }
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        List<SyncDefinitionsS2C.JobInfo> jobs = ClientDefinitions.get().jobs();
        int pages = Math.max(1, (jobs.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));

        if (confirming >= 0) {
            int y = top + PANEL_HEIGHT / 2 + 18;
            addRenderableWidget(new CrystalButton(width / 2 - 84, y, 80, 18, Component.translatable("screen.amemaru.job.swear"), b -> {
                ModNetwork.sendToServer(new JobActionC2S(JobActionC2S.Action.SELECT, confirming));
                onClose();
            }));
            addRenderableWidget(new CrystalButton(width / 2 + 4, y, 80, 18, Component.translatable("screen.amemaru.job.back"), b -> {
                confirming = -1;
                rebuild();
            }));
            return;
        }

        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index >= jobs.size()) {
                break;
            }
            SyncDefinitionsS2C.JobInfo job = jobs.get(index);
            int cardX = cardX(i);
            boolean current = job.index() == info.currentJob();
            boolean affordable = info.free() || info.tokensHeld() >= info.tokensNeeded();
            CrystalButton button = new CrystalButton(cardX + 20, top + 38 + CARD_HEIGHT - 22, CARD_WIDTH - 40, 16,
                    Component.translatable(current ? "screen.amemaru.job.current" : "screen.amemaru.job.choose"), b -> {
                confirming = job.index();
                rebuild();
            });
            button.active = !current && affordable;
            addRenderableWidget(button);
        }
        if (pages > 1) {
            CrystalButton prev = new CrystalButton(left + 6, top + 38 + CARD_HEIGHT / 2 - 8, 14, 16, Component.literal("<"), b -> {
                page--;
                rebuild();
            });
            prev.active = page > 0;
            addRenderableWidget(prev);
            CrystalButton next = new CrystalButton(left + PANEL_WIDTH - 20, top + 38 + CARD_HEIGHT / 2 - 8, 14, 16,
                    Component.literal(">"), b -> {
                page++;
                rebuild();
            });
            next.active = page < pages - 1;
            addRenderableWidget(next);
        }
        if (info.currentJob() >= 0 && info.promotedOnce() && !info.promoted()) {
            addRenderableWidget(new CrystalButton(left + 12, top + PANEL_HEIGHT - 22, 110, 16,
                    Component.translatable("screen.amemaru.job.promote"), b -> {
                ModNetwork.sendToServer(new JobActionC2S(JobActionC2S.Action.PROMOTE, info.currentJob()));
                onClose();
            }));
        }
        addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 102, top + PANEL_HEIGHT - 22, 90, 16,
                Component.translatable("screen.amemaru.status.close"), b -> onClose()));
    }

    private int cardX(int column) {
        return left + 12 + column * (CARD_WIDTH + 8);
    }

    // ---------------------------------------------------------------- 描画

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT);
        graphics.drawCenteredString(font, title, width / 2, top + 7, UiStyle.TEXT_HEADER);
        UiStyle.divider(graphics, left + 12, left + PANEL_WIDTH - 12, top + 19);
        graphics.drawCenteredString(font, ruleText(), width / 2, top + 25, ruleColor());

        List<SyncDefinitionsS2C.JobInfo> jobs = ClientDefinitions.get().jobs();
        if (jobs.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.amemaru.job.empty"), width / 2, top + PANEL_HEIGHT / 2,
                    UiStyle.TEXT_DIM);
        } else if (confirming >= 0) {
            renderConfirm(graphics, ClientDefinitions.get().job(confirming));
        } else {
            for (int i = 0; i < PER_PAGE; i++) {
                int index = page * PER_PAGE + i;
                if (index < jobs.size()) {
                    renderCard(graphics, jobs.get(index), cardX(i), top + 38, mouseX, mouseY);
                }
            }
            // ページの印（Phase 6 その2：戦闘職が6つになり、3ページになった）
            int pages = Math.max(1, (jobs.size() + PER_PAGE - 1) / PER_PAGE);
            if (pages > 1) {
                int dotsY = top + 38 + CARD_HEIGHT + 6;
                int dotsX = width / 2 - (pages * 10 - 4) / 2;
                for (int p = 0; p < pages; p++) {
                    int color = p == page ? 0xFFFFF2A8 : 0xFF3E5274;
                    graphics.fill(dotsX + p * 10, dotsY, dotsX + p * 10 + 6, dotsY + 6, color);
                }
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component ruleText() {
        if (info.currentJob() < 0) {
            return Component.translatable("screen.amemaru.job.rule.first", info.freeUntilDay());
        }
        if (info.free()) {
            return Component.translatable("screen.amemaru.job.rule.free", info.day(), info.freeUntilDay());
        }
        return Component.translatable("screen.amemaru.job.rule.token", info.tokensNeeded(), info.tokensHeld());
    }

    private int ruleColor() {
        if (info.free() || info.tokensHeld() >= info.tokensNeeded()) {
            return UiStyle.TEXT_GOLD;
        }
        return UiStyle.TEXT_BAD;
    }

    private void renderCard(GuiGraphics graphics, SyncDefinitionsS2C.JobInfo job, int x, int y, int mouseX, int mouseY) {
        boolean current = job.index() == info.currentJob();
        boolean hover = mouseX >= x && mouseX < x + CARD_WIDTH && mouseY >= y && mouseY < y + CARD_HEIGHT;
        UiStyle.card(graphics, x, y, x + CARD_WIDTH, y + CARD_HEIGHT, job.color(), current || hover);

        // 職業の紋章（64×64 の絵を 28×28 で）
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(job.icon(), x + 6, y + 6, 28, 28, 0, 0, 64, 64, 64, 64);
        graphics.drawString(font, job.name(), x + 40, y + 8, 0xFF000000 | job.color(), true);
        graphics.drawString(font, job.role(), x + 40 + font.width(job.name()) + 6, y + 8, UiStyle.TEXT_DIM, false);
        graphics.drawString(font, Component.translatable("screen.amemaru.job.weapons", job.weaponText()), x + 40, y + 20, UiStyle.TEXT, false);

        int lineY = y + 38;
        if (job.hasGauge()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.job.gauge", job.gaugeName()), x + 6, lineY,
                    0xFF000000 | job.gaugeColor(), false);
            lineY += 10;
        }
        String stats = statText(job);
        if (!stats.isEmpty()) {
            lineY += 10 * UiStyle.wrapped(graphics, font, stats, x + 6, lineY, CARD_WIDTH - 12, UiStyle.TEXT_GOOD, 2);
        }
        lineY += 2;
        // 説明：「最初の技」の行の上までに入る行数だけ描き、入りきらなければ「…」にして、マウスを乗せると全文を出す
        int iconY = y + CARD_HEIGHT - 44;
        int descLines = Math.max(1, (iconY - 2 - lineY) / 10);
        UiStyle.wrapped(graphics, font, job.description(), x + 6, lineY, CARD_WIDTH - 12, UiStyle.TEXT_DIM, descLines);
        if (mouseX >= x + 6 && mouseX < x + CARD_WIDTH - 6 && mouseY >= lineY && mouseY < lineY + descLines * 10
                && UiStyle.overflows(font, job.description(), CARD_WIDTH - 12, descLines)) {
            setTooltipForNextRenderPass(UiStyle.tooltipLines(font, job.description(), 200));
        }

        // 最初から使えるスキル
        graphics.drawString(font, Component.translatable("screen.amemaru.job.starting"), x + 6, iconY + 4, UiStyle.TEXT_HEADER, false);
        int iconX = x + 6 + font.width(Component.translatable("screen.amemaru.job.starting")) + 4;
        for (int skillIndex : job.startingSkills()) {
            SyncDefinitionsS2C.SkillInfo skill = ClientDefinitions.get().skill(skillIndex);
            if (skill != null) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                graphics.blit(skill.icon(), iconX, iconY, 16, 16, 0, 0, 32, 32, 32, 32);
                if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16) {
                    setTooltipForNextRenderPass(tooltip(skill.name(), skill.description()));
                }
                iconX += 18;
            }
        }
        // 見習い装備
        int kitX = x + CARD_WIDTH - 6;
        for (int i = job.kitItems().size() - 1; i >= 0; i--) {
            Item item = ForgeRegistries.ITEMS.getValue(job.kitItems().get(i));
            if (item == null) {
                continue;
            }
            kitX -= 17;
            ItemStack stack = new ItemStack(item);
            graphics.renderItem(stack, kitX, iconY);
            if (mouseX >= kitX && mouseX < kitX + 16 && mouseY >= iconY && mouseY < iconY + 16) {
                setTooltipForNextRenderPass(stack.getHoverName());
            }
        }
    }

    /** 名前と説明の、折り返したツールチップ。 */
    private List<net.minecraft.util.FormattedCharSequence> tooltip(String name, String description) {
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.literal(name).withStyle(net.minecraft.ChatFormatting.AQUA).getVisualOrderText());
        lines.addAll(UiStyle.tooltipLines(font, description, 200));
        return lines;
    }

    /** ステータスの伸び方（例：HP +20%・防御 +30%）。 */
    private static String statText(SyncDefinitionsS2C.JobInfo job) {
        List<String> parts = new ArrayList<>();
        for (StatType type : StatType.all()) {
            double value = type.ordinal() < job.statPercent().length ? job.statPercent()[type.ordinal()] : 0;
            if (value != 0) {
                parts.add(Component.translatable(type.translationKey()).getString()
                        + String.format(Locale.ROOT, " %s%d%%", value > 0 ? "+" : "", Math.round(value)));
            }
        }
        return String.join("・", parts);
    }

    private void renderConfirm(GuiGraphics graphics, @Nullable SyncDefinitionsS2C.JobInfo job) {
        if (job == null) {
            return;
        }
        int y = top + PANEL_HEIGHT / 2 - 30;
        UiStyle.card(graphics, left + 40, y - 8, left + PANEL_WIDTH - 40, y + 70, job.color(), true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(job.icon(), width / 2 - 14, y - 2, 28, 28, 0, 0, 64, 64, 64, 64);
        graphics.drawCenteredString(font, Component.translatable("screen.amemaru.job.confirm", job.name()), width / 2, y + 30, UiStyle.TEXT);
        Component note = info.currentJob() < 0
                ? Component.translatable("screen.amemaru.job.confirm_first")
                : info.free() ? Component.translatable("screen.amemaru.job.confirm_change_free")
                : Component.translatable("screen.amemaru.job.confirm_change_token", info.tokensNeeded());
        graphics.drawCenteredString(font, note, width / 2, y + 42, UiStyle.TEXT_DIM);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 画面の職業の名前（自動の確認で使う）。 */
    @Nullable
    public ResourceLocation shownJob(int column) {
        SyncDefinitionsS2C.JobInfo job = ClientDefinitions.get().job(page * PER_PAGE + column);
        return job == null ? null : job.id();
    }
}

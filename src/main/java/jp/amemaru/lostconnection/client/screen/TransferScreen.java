package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.client.ClientPlayerState;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.TransferKnotsC2S;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * ノットを渡す画面（11月22日に決定。Phase 6）。ステータスの画面の「渡す」か、しゃがんで相手を右クリックで開く。
 *
 * <ol>
 *   <li>渡す相手を選ぶ（サーバーにいる人。名前で絞り込める。行をクリックして選ぶ）</li>
 *   <li>額を入れる（+100・+1,000・+10,000・全部）</li>
 *   <li>「渡す」→ 確かめの文 →「渡す」で送る。所持金・相手・上限はサーバーで確かめる</li>
 * </ol>
 */
public class TransferScreen extends Screen {
    private static final int PANEL_WIDTH = 268;
    private static final int PANEL_HEIGHT = 232;
    private static final int ROW = 15;
    private static final int PER_PAGE = 6;
    private static final int LIST_TOP = 64;

    @Nullable
    private UUID selected;
    @Nullable
    private String selectedName;
    private final List<PlayerInfo> players = new ArrayList<>();
    private int page;
    private boolean confirming;
    private String query = "";
    private String amountText = "";

    private int left;
    private int top;
    @Nullable
    private EditBox search;
    @Nullable
    private EditBox amount;
    @Nullable
    private Button giveButton;
    @Nullable
    private Button prevButton;
    @Nullable
    private Button nextButton;

    public TransferScreen(@Nullable UUID target, @Nullable String targetName) {
        super(Component.translatable("screen.amemaru.transfer.title"));
        this.selected = target;
        this.selectedName = targetName;
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        search = null;
        amount = null;
        giveButton = null;
        prevButton = null;
        nextButton = null;
        if (confirming) {
            initConfirm();
            return;
        }
        collectPlayers();

        search = new EditBox(font, left + 14, top + 44, PANEL_WIDTH - 28, 14, Component.translatable("screen.amemaru.transfer.search"));
        search.setMaxLength(16);
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            page = 0;
            updateButtons();
        });
        addRenderableWidget(search);

        int arrowsY = top + LIST_TOP + PER_PAGE * ROW + 2;
        prevButton = addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 62, arrowsY, 20, 12, Component.literal("<"),
                b -> turn(-1)));
        nextButton = addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 36, arrowsY, 20, 12, Component.literal(">"),
                b -> turn(1)));

        int amountY = top + PANEL_HEIGHT - 66;
        amount = new EditBox(font, left + 14, amountY, 96, 14, Component.translatable("screen.amemaru.transfer.amount"));
        amount.setMaxLength(15);
        amount.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        amount.setValue(amountText);
        amount.setResponder(text -> {
            amountText = text;
            updateButtons();
        });
        addRenderableWidget(amount);
        int bx = left + 116;
        for (long step : new long[]{100L, 1_000L, 10_000L}) {
            int w = step >= 10_000L ? 40 : 34;
            addRenderableWidget(new CrystalButton(bx, amountY, w, 14,
                    Component.literal("+" + String.format(Locale.ROOT, "%,d", step)), b -> add(step)));
            bx += w + 2;
        }
        addRenderableWidget(new CrystalButton(bx, amountY, 30, 14, Component.translatable("screen.amemaru.transfer.all"),
                b -> setAmount(ClientPlayerState.get().knots())));

        int buttonY = top + PANEL_HEIGHT - 22;
        giveButton = addRenderableWidget(new CrystalButton(left + 14, buttonY, 72, 16,
                Component.translatable("screen.amemaru.transfer.give"), b -> {
                    if (canGive()) {
                        confirming = true;
                        rebuildWidgets();
                    }
                }));
        addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 54, buttonY, 42, 16,
                Component.translatable("screen.amemaru.status.close"), b -> onClose()));
        updateButtons();
    }

    private void initConfirm() {
        int buttonY = top + PANEL_HEIGHT - 22;
        addRenderableWidget(new CrystalButton(left + 14, buttonY, 72, 16, Component.translatable("screen.amemaru.transfer.give"), b -> {
            if (selected != null && parsedAmount() > 0) {
                ModNetwork.sendToServer(new TransferKnotsC2S(selected, parsedAmount()));
            }
            onClose();
        }));
        addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 74, buttonY, 62, 16, Component.translatable("screen.amemaru.transfer.back"),
                b -> {
                    confirming = false;
                    rebuildWidgets();
                }));
    }

    private void collectPlayers() {
        players.clear();
        if (minecraft == null || minecraft.getConnection() == null || minecraft.player == null) {
            return;
        }
        UUID self = minecraft.player.getUUID();
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            if (!info.getProfile().getId().equals(self)) {
                players.add(info);
            }
        }
        players.sort(Comparator.comparing((PlayerInfo info) -> info.getProfile().getName(), String.CASE_INSENSITIVE_ORDER));
    }

    private List<PlayerInfo> filtered() {
        if (query.isBlank()) {
            return players;
        }
        String q = query.toLowerCase(Locale.ROOT);
        List<PlayerInfo> list = new ArrayList<>();
        for (PlayerInfo info : players) {
            if (info.getProfile().getName().toLowerCase(Locale.ROOT).contains(q)) {
                list.add(info);
            }
        }
        return list;
    }

    private int pages() {
        return Math.max(1, (filtered().size() + PER_PAGE - 1) / PER_PAGE);
    }

    private void turn(int delta) {
        page = Mth.clamp(page + delta, 0, pages() - 1);
        updateButtons();
    }

    private void add(long step) {
        setAmount(Math.min(ClientPlayerState.get().knots(), parsedAmount() + step));
    }

    private void setAmount(long value) {
        amountText = value <= 0 ? "" : Long.toString(value);
        if (amount != null) {
            amount.setValue(amountText);
        }
        updateButtons();
    }

    private long parsedAmount() {
        if (amountText.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(amountText);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private boolean canGive() {
        long value = parsedAmount();
        return selected != null && value > 0 && value <= ClientPlayerState.get().knots();
    }

    private void updateButtons() {
        page = Mth.clamp(page, 0, pages() - 1);
        if (giveButton != null) {
            giveButton.active = canGive();
        }
        if (prevButton != null && nextButton != null) {
            prevButton.visible = nextButton.visible = pages() > 1;
            prevButton.active = page > 0;
            nextButton.active = page < pages() - 1;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!confirming && button == 0) {
            List<PlayerInfo> shown = filtered();
            int y = top + LIST_TOP;
            for (int i = page * PER_PAGE; i < Math.min(shown.size(), (page + 1) * PER_PAGE); i++) {
                if (mouseX >= left + 14 && mouseX < left + PANEL_WIDTH - 14 && mouseY >= y && mouseY < y + ROW - 2) {
                    selected = shown.get(i).getProfile().getId();
                    selectedName = shown.get(i).getProfile().getName();
                    updateButtons();
                    return true;
                }
                y += ROW;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        super.tick();
        if (search != null) {
            search.tick();
        }
        if (amount != null) {
            amount.tick();
        }
        updateButtons();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT);
        graphics.drawCenteredString(font, title, width / 2, top + 7, UiStyle.TEXT_HEADER);
        UiStyle.divider(graphics, left + 12, left + PANEL_WIDTH - 12, top + 19);
        long balance = ClientPlayerState.get().knots();
        graphics.drawString(font, Component.translatable("screen.amemaru.transfer.balance", String.format(Locale.ROOT, "%,d", balance)),
                left + 14, top + 24, UiStyle.TEXT_GOLD, true);

        if (confirming) {
            String name = selectedName == null ? "?" : selectedName;
            String text = Component.translatable("screen.amemaru.transfer.confirm", name,
                    String.format(Locale.ROOT, "%,d", parsedAmount())).getString();
            UiStyle.wrapped(graphics, font, text, left + 16, top + 70, PANEL_WIDTH - 32, UiStyle.TEXT, 4);
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.transfer.confirm_note").getString(),
                    left + 16, top + 120, PANEL_WIDTH - 32, UiStyle.TEXT_DIM, 3);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        graphics.drawString(font, Component.translatable("screen.amemaru.transfer.to"), left + 12, top + 34, UiStyle.TEXT_HEADER, true);
        List<PlayerInfo> shown = filtered();
        int y = top + LIST_TOP;
        for (int i = page * PER_PAGE; i < Math.min(shown.size(), (page + 1) * PER_PAGE); i++) {
            PlayerInfo info = shown.get(i);
            boolean chosen = info.getProfile().getId().equals(selected);
            boolean hover = mouseX >= left + 14 && mouseX < left + PANEL_WIDTH - 14 && mouseY >= y && mouseY < y + ROW - 2;
            UiStyle.card(graphics, left + 14, y, left + PANEL_WIDTH - 14, y + ROW - 2, chosen ? 0xFFFFD36A : 0xFF8FD3FF,
                    chosen || hover);
            graphics.drawString(font, (chosen ? "▶ " : "") + info.getProfile().getName(), left + 20, y + 3,
                    chosen ? UiStyle.TEXT_GOLD : UiStyle.TEXT, true);
            y += ROW;
        }
        if (shown.isEmpty()) {
            graphics.drawString(font, Component.translatable(players.isEmpty() ? "screen.amemaru.transfer.no_players"
                    : "screen.amemaru.transfer.no_match"), left + 16, top + LIST_TOP + 2, UiStyle.TEXT_DIM, true);
        }
        graphics.drawString(font, Component.translatable("screen.amemaru.transfer.amount"), left + 12, top + PANEL_HEIGHT - 78,
                UiStyle.TEXT_HEADER, true);
        String chosen = selectedName == null ? Component.translatable("screen.amemaru.transfer.no_target").getString()
                : Component.translatable("screen.amemaru.transfer.target", selectedName).getString();
        graphics.drawString(font, UiStyle.clip(font, chosen, PANEL_WIDTH - 150), left + 92, top + PANEL_HEIGHT - 18,
                selectedName == null ? UiStyle.TEXT_DIM : UiStyle.TEXT, true);
        if (parsedAmount() > balance) {
            graphics.drawString(font, Component.translatable("screen.amemaru.transfer.too_much"), left + 14, top + PANEL_HEIGHT - 48,
                    UiStyle.TEXT_BAD, true);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}

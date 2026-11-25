package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.client.ClientParty;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.PartyActionC2S;
import jp.amemaru.lostconnection.network.s2c.PartyStateS2C;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * パーティの画面（Phase 4。Uキー）：メンバー（HP・レベル・リーダー）と、届いている誘い、誘える人（サーバーにいる人）。
 *
 * <ul>
 *   <li>リーダーは、メンバーを外す・リーダーを渡す、ができる。だれでも抜けられる。</li>
 *   <li>誘えるのは、パーティに入っていない人か、リーダー（6人まで。サーバーで確かめる）。</li>
 * </ul>
 */
public class PartyScreen extends Screen {
    /** 幅：名前（96）・HPのバー（40）・レベルの右に、リーダーのボタン（リーダーに・外す）が重ならない幅 */
    private static final int PANEL_WIDTH = 292;
    private static final int ROW = 16;
    private static final int MAX_MEMBERS_SHOWN = 6;
    private static final int PLAYERS_PER_PAGE = 5;

    private int left;
    private int top;
    /** パネルの高さ（メンバー・誘い・誘える人の数で決める） */
    private int panelHeight;
    private int version = -1;
    private int page;
    /** 誘える人（名前の順） */
    private final List<PlayerInfo> candidates = new ArrayList<>();
    private int membersTop;
    private int invitesTop;
    private int playersTop;

    public PartyScreen() {
        super(Component.translatable("screen.amemaru.party.title"));
    }

    @Override
    protected void init() {
        version = ClientParty.version();
        PartyStateS2C state = ClientParty.state();
        boolean leader = ClientParty.selfIsLeader();
        UUID self = ClientParty.selfId();
        // 誘える人（サーバーにいる人のうち、自分とメンバーを除く）
        candidates.clear();
        if (minecraft != null && minecraft.getConnection() != null) {
            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                UUID id = info.getProfile().getId();
                if (!id.equals(self) && !ClientParty.isMember(id)) {
                    candidates.add(info);
                }
            }
        }
        candidates.sort(Comparator.comparing((PlayerInfo info) -> info.getProfile().getName(), String.CASE_INSENSITIVE_ORDER));
        int pages = Math.max(1, (candidates.size() + PLAYERS_PER_PAGE - 1) / PLAYERS_PER_PAGE);
        page = Mth.clamp(page, 0, pages - 1);
        int memberRows = state.inParty() ? Math.min(state.members().size(), MAX_MEMBERS_SHOWN) : 1;
        int inviteRows = state.invites().isEmpty() ? 1 : Math.min(state.invites().size(), 3);
        int playerRows = candidates.isEmpty() ? 1 : pages > 1 ? PLAYERS_PER_PAGE : candidates.size();
        panelHeight = 34 + memberRows * ROW + 14 + inviteRows * ROW + 14 + playerRows * ROW + (pages > 1 ? 62 : 46);
        left = (width - PANEL_WIDTH) / 2;
        top = Math.max(4, (height - panelHeight) / 2);

        // メンバー
        membersTop = top + 34;
        int y = membersTop;
        int shown = 0;
        for (PartyStateS2C.Member member : state.members()) {
            if (shown++ >= MAX_MEMBERS_SHOWN) {
                break;
            }
            if (leader && !member.id().equals(self)) {
                UUID id = member.id();
                addRenderableWidget(small(left + PANEL_WIDTH - 104, y - 2, 48, "screen.amemaru.party.promote",
                        b -> send(PartyActionC2S.Action.PROMOTE, id)));
                addRenderableWidget(small(left + PANEL_WIDTH - 52, y - 2, 40, "screen.amemaru.party.kick",
                        b -> send(PartyActionC2S.Action.KICK, id)));
            }
            y += ROW;
        }
        if (!state.inParty()) {
            y += ROW;
        }

        // 届いている誘い
        invitesTop = y + 14;
        y = invitesTop;
        for (PartyStateS2C.Invite invite : state.invites()) {
            UUID from = invite.inviter();
            addRenderableWidget(small(left + PANEL_WIDTH - 104, y - 2, 48, "screen.amemaru.party.accept",
                    b -> send(PartyActionC2S.Action.ACCEPT, from)));
            addRenderableWidget(small(left + PANEL_WIDTH - 52, y - 2, 40, "screen.amemaru.party.decline",
                    b -> send(PartyActionC2S.Action.DECLINE, from)));
            y += ROW;
            if (y > invitesTop + ROW * 2) {
                break;
            }
        }
        if (state.invites().isEmpty()) {
            y += ROW;
        }

        // 誘える人
        playersTop = y + 14;
        boolean canInvite = !state.inParty() || leader;
        y = playersTop;
        for (int i = page * PLAYERS_PER_PAGE; i < Math.min(candidates.size(), (page + 1) * PLAYERS_PER_PAGE); i++) {
            UUID id = candidates.get(i).getProfile().getId();
            Button button = small(left + PANEL_WIDTH - 52, y - 2, 40, "screen.amemaru.party.invite",
                    b -> send(PartyActionC2S.Action.INVITE, id));
            button.active = canInvite;
            addRenderableWidget(button);
            y += ROW;
        }
        if (pages > 1) {
            int arrowsY = top + panelHeight - 40;
            Button prev = addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 64, arrowsY, 20, 14, Component.literal("<"),
                    b -> turn(-1)));
            Button next = addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 32, arrowsY, 20, 14, Component.literal(">"),
                    b -> turn(1)));
            prev.active = page > 0;
            next.active = page < pages - 1;
        }

        int buttonY = top + panelHeight - 20;
        if (state.inParty()) {
            addRenderableWidget(new CrystalButton(left + 12, buttonY, 64, 16, Component.translatable("screen.amemaru.party.leave"),
                    b -> send(PartyActionC2S.Action.LEAVE, null)));
        }
        // 決闘（Phase 6 その4）：申し込む・受ける・観戦する画面
        addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 108, buttonY, 50, 16,
                Component.translatable("screen.amemaru.party.duel"), b -> jp.amemaru.lostconnection.client.ClientSocial.send("duel_open")));
        addRenderableWidget(new CrystalButton(left + PANEL_WIDTH - 54, buttonY, 42, 16,
                Component.translatable("screen.amemaru.status.close"), b -> onClose()));
    }

    private CrystalButton small(int x, int y, int w, String key, Button.OnPress press) {
        return new CrystalButton(x, y, w, 12, Component.translatable(key), press);
    }

    private void send(PartyActionC2S.Action action, UUID target) {
        ModNetwork.sendToServer(new PartyActionC2S(action, target));
    }

    private void turn(int delta) {
        page += delta;
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        // パーティの様子が変わったら（入った・抜けた・誘われた）、ボタンを作り直す
        if (version != ClientParty.version()) {
            rebuildWidgets();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + PANEL_WIDTH, top + panelHeight);
        graphics.drawCenteredString(font, title, width / 2, top + 7, UiStyle.TEXT_HEADER);
        UiStyle.divider(graphics, left + 12, left + PANEL_WIDTH - 12, top + 19);
        PartyStateS2C state = ClientParty.state();

        // メンバー
        int x = left + 14;
        Component header = state.inParty()
                ? Component.translatable("screen.amemaru.party.members", state.members().size())
                : Component.translatable("screen.amemaru.party.members_none");
        graphics.drawString(font, header, x - 2, membersTop - 12, UiStyle.TEXT_HEADER, true);
        int y = membersTop;
        int shown = 0;
        for (PartyStateS2C.Member member : state.members()) {
            if (shown++ >= MAX_MEMBERS_SHOWN) {
                break;
            }
            drawMember(graphics, member, x, y);
            y += ROW;
        }
        if (!state.inParty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.party.none_hint"), x, y, UiStyle.TEXT_DIM, true);
        }

        // 誘い
        graphics.drawString(font, Component.translatable("screen.amemaru.party.invites"), x - 2, invitesTop - 12, UiStyle.TEXT_HEADER, true);
        y = invitesTop;
        long age = ClientParty.age();
        int count = 0;
        for (PartyStateS2C.Invite invite : state.invites()) {
            long remain = Math.max(0, invite.ticksLeft() - age);
            graphics.drawString(font, Component.translatable("screen.amemaru.party.invite_from", invite.name(), (remain + 19) / 20),
                    x, y, UiStyle.TEXT, true);
            y += ROW;
            if (++count > 2) {
                break;
            }
        }
        if (state.invites().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.party.no_invites"), x, y, UiStyle.TEXT_DIM, true);
        }

        // 誘える人
        graphics.drawString(font, Component.translatable("screen.amemaru.party.players"), x - 2, playersTop - 12, UiStyle.TEXT_HEADER, true);
        y = playersTop;
        if (candidates.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.party.no_players"), x, y, UiStyle.TEXT_DIM, true);
        }
        for (int i = page * PLAYERS_PER_PAGE; i < Math.min(candidates.size(), (page + 1) * PLAYERS_PER_PAGE); i++) {
            graphics.drawString(font, candidates.get(i).getProfile().getName(), x, y, UiStyle.TEXT, true);
            y += ROW;
        }
        if (state.inParty() && !ClientParty.selfIsLeader()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.party.leader_only"), x, top + panelHeight - 36,
                    UiStyle.TEXT_DIM, true);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawMember(GuiGraphics graphics, PartyStateS2C.Member member, int x, int y) {
        boolean self = member.id().equals(ClientParty.selfId());
        String name = (member.leader() ? "★" : "") + member.name() + (self ? " " + Component.translatable("screen.amemaru.party.you").getString() : "");
        int color = !member.online() ? UiStyle.TEXT_DIM : member.leader() ? UiStyle.TEXT_GOLD : UiStyle.TEXT;
        graphics.drawString(font, font.plainSubstrByWidth(name, 96), x, y, color, true);
        int barX = x + 100;
        if (!member.online()) {
            graphics.drawString(font, Component.translatable("hud.amemaru.party.offline"), barX, y, UiStyle.TEXT_DIM, true);
            return;
        }
        if (member.down()) {
            long remain = Math.max(0, member.downTicks() - ClientParty.age());
            graphics.drawString(font, Component.translatable("hud.amemaru.party.down", (remain + 19) / 20), barX, y, 0xFF6A5A, true);
            return;
        }
        graphics.fill(barX, y + 2, barX + 40, y + 6, 0xFF1B2030);
        float hp = Mth.clamp(member.hp(), 0.0F, 1.0F);
        int barColor = hp > 0.5F ? 0xFF52DC6E : hp > 0.25F ? 0xFFE8C860 : 0xFFF06A4A;
        graphics.fill(barX, y + 2, barX + Math.round(40 * hp), y + 6, barColor);
        graphics.drawString(font, "Lv" + member.level(), barX + 44, y, UiStyle.TEXT_DIM, true);
    }
}

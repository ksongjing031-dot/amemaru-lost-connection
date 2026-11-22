package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.economy.MarketService;
import jp.amemaru.lostconnection.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 取引所の操作（Phase 6）。値段・所持金・持ち物・出品の有無・場所は、すべてサーバーで確かめる（{@link MarketService}）。
 *
 * <ul>
 *   <li>QUERY：a＝分類、b＝並び、c＝ページ、id＝露店の番号（0なら全部）、text＝検索の文字（空なら名前で絞らない）、
 *       ids＝検索の文字に名前が合う品（クライアントが自分の言葉の名前で探す。サーバーは名前の言葉を持たないため）</li>
 *   <li>BUY：id＝出品の番号、price＝見ていた1個の値段、a＝露店の番号（露店で直接買うとき）</li>
 *   <li>PRICE：a＝持ち物の枠。LIST：a＝枠、b＝数、price＝1個の値段、text＝枠の品のID（変わっていないかを確かめる）</li>
 *   <li>CANCEL：id。MINE・COLLECT・STALLS：なし。RENT：a＝区画、text＝店名。EXTEND・RELEASE：なし。RENAME：text＝店名</li>
 * </ul>
 */
public record MarketActionC2S(Action action, long id, int a, int b, int c, long price, String text, List<ResourceLocation> ids) {
    public enum Action { QUERY, BUY, PRICE, LIST, CANCEL, MINE, COLLECT, STALLS, RENT, EXTEND, RELEASE, RENAME }

    public static MarketActionC2S of(Action action) {
        return new MarketActionC2S(action, 0, 0, 0, 0, 0, "", List.of());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeVarLong(id);
        buf.writeVarInt(a);
        buf.writeVarInt(b);
        buf.writeVarInt(c);
        buf.writeVarLong(price);
        buf.writeUtf(text, 256);
        buf.writeVarInt(Math.min(ids.size(), MarketService.MAX_SEARCH_IDS));
        for (int i = 0; i < Math.min(ids.size(), MarketService.MAX_SEARCH_IDS); i++) {
            buf.writeResourceLocation(ids.get(i));
        }
    }

    public static MarketActionC2S decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        long id = buf.readVarLong();
        int a = buf.readVarInt();
        int b = buf.readVarInt();
        int c = buf.readVarInt();
        long price = buf.readVarLong();
        String text = buf.readUtf(256);
        int n = Math.min(buf.readVarInt(), MarketService.MAX_SEARCH_IDS);
        List<ResourceLocation> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(buf.readResourceLocation());
        }
        return new MarketActionC2S(action, id, a, b, c, price, text, ids);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
        MarketService.Access access = MarketService.Access.of(player);
        switch (action) {
            case QUERY -> {
                if (!access.exchange() && (id <= 0 || access.stall() != id)) {
                    return;
                }
                MarketService.Category category = MarketService.Category.values()[Math.floorMod(a, MarketService.Category.values().length)];
                MarketService.Sort sort = MarketService.Sort.values()[Math.floorMod(b, MarketService.Sort.values().length)];
                Set<ResourceLocation> filter = text.isBlank() ? null : new HashSet<>(ids);
                ModNetwork.sendTo(player, MarketService.page(player.server, player.getUUID(), category, sort, c, filter, (int) Math.max(0, id)));
            }
            case BUY -> MarketService.buy(player, access, id, price, Math.max(0, a));
            case PRICE -> {
                if (access.exchange()) {
                    ModNetwork.sendTo(player, MarketService.priceInfo(player, a));
                }
            }
            case LIST -> MarketService.list(player, access, a, b, price, text);
            case CANCEL -> MarketService.cancel(player, access, id);
            case MINE -> {
                if (access.exchange()) {
                    ModNetwork.sendTo(player, MarketService.mine(player.server, player.getUUID()));
                }
            }
            case COLLECT -> MarketService.collect(player, access);
            case STALLS -> ModNetwork.sendTo(player, jp.amemaru.lostconnection.network.s2c.MarketStallsS2C.current(player.server));
            case RENT -> MarketService.rent(player, access, a, text);
            case EXTEND -> MarketService.extend(player, access);
            case RELEASE -> MarketService.release(player, access);
            case RENAME -> MarketService.rename(player, access, text);
            default -> {
            }
        }
    }
}

package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.economy.ShopService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 店で買う・売る（Phase 6）：カウンターの場所・店・品の番号・数。値段・所持金・持ち物の空き・持っている数・カウンターの近くか、は
 * サーバーで確かめる（{@link ShopService}）。
 */
public record ShopActionC2S(BlockPos pos, ResourceLocation shop, boolean sell, int index, int count) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeResourceLocation(shop);
        buf.writeBoolean(sell);
        buf.writeVarInt(index);
        buf.writeVarInt(count);
    }

    public static ShopActionC2S decode(FriendlyByteBuf buf) {
        return new ShopActionC2S(buf.readBlockPos(), buf.readResourceLocation(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
        if (sell) {
            ShopService.sell(player, pos, shop, index, count);
        } else {
            ShopService.buy(player, pos, shop, index, count);
        }
    }
}

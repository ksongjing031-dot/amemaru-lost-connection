package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 個人倉庫の中身の一覧（Phase 6）：倉庫のすべてのページと受け取り箱。画面の「さがす」で、ほかのページの品も見つけるため。
 * 次のページの値段（契約していなければ契約の値段。増やせなければ -1）と、ページ数の上限も送る。
 * 出し入れはこの一覧ではなく、画面の枠（サーバーが確かめる）で行う。
 */
public record StorageContentsS2C(int containerId, List<ItemStack> items, List<ItemStack> received, long nextPrice, int maxPages) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(items.size());
        for (ItemStack stack : items) {
            buf.writeItem(stack);
        }
        buf.writeVarInt(received.size());
        for (ItemStack stack : received) {
            buf.writeItem(stack);
        }
        buf.writeLong(nextPrice);
        buf.writeVarInt(maxPages);
    }

    public static StorageContentsS2C decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int count = Math.min(buf.readVarInt(), 4096);
        List<ItemStack> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(buf.readItem());
        }
        int receivedCount = Math.min(buf.readVarInt(), 4096);
        List<ItemStack> received = new ArrayList<>(receivedCount);
        for (int i = 0; i < receivedCount; i++) {
            received.add(buf.readItem());
        }
        long nextPrice = buf.readLong();
        int maxPages = buf.readVarInt();
        return new StorageContentsS2C(id, items, received, nextPrice, maxPages);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleStorageContents(this));
    }
}

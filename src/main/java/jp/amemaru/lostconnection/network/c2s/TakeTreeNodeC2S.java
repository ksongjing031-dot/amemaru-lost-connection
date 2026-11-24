package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.job.SkillTreeService;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * スキルツリーの節を取る（11月24日。スキルの節なら次の段階を覚える）。となりの節・残りのポイント・職業はサーバーで確かめる。
 */
public final class TakeTreeNodeC2S {
    private static final int MAX_ID = 64;
    private final String node;

    public TakeTreeNodeC2S(String node) {
        this.node = node;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(node, MAX_ID);
    }

    public static TakeTreeNodeC2S decode(FriendlyByteBuf buf) {
        return new TakeTreeNodeC2S(buf.readUtf(MAX_ID));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
        PlayerDataCapability.get(player).ifPresent(data -> SkillTreeService.takeNode(player, data, node));
    }
}

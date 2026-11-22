package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.combat.BasicShotService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 弓の通常の射撃（Phase 6 その2：弓を持って左クリック）。向きはサーバーで見るので、中身はない。
 * 射てる間隔（攻撃の速さ）と、弓を持っているかはサーバーで確かめる（{@link BasicShotService}）。
 */
public record BasicShotC2S() {
    public void encode(FriendlyByteBuf buf) {
    }

    public static BasicShotC2S decode(FriendlyByteBuf buf) {
        return new BasicShotC2S();
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            BasicShotService.tryShoot(player);
        }
    }
}

package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 本人の画面だけに出す特別な演出（Phase 0-B 5章 {@code SpecialMoment}）：レアドロップ、完璧なアクセサリー、工房の大成功・完成。
 */
public record SpecialMomentS2C(Kind kind, ItemStack stack) {
    public enum Kind {
        /** レアドロップ（光と音、画面の上に品の名前）。 */
        RARE_DROP,
        /** 完璧なアクセサリー（虹色の光と特別な音）。 */
        PERFECT_ACCESSORY,
        /** 工房で作った。 */
        CRAFTED,
        /** 工房の「大成功」。 */
        GREAT_SUCCESS,
        /** 強化した。 */
        ENHANCED,
        /** 修理した。 */
        REPAIRED
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(kind.ordinal());
        buf.writeItem(stack);
    }

    public static SpecialMomentS2C decode(FriendlyByteBuf buf) {
        Kind[] kinds = Kind.values();
        int index = buf.readByte();
        Kind kind = kinds[Math.max(0, Math.min(kinds.length - 1, index))];
        return new SpecialMomentS2C(kind, buf.readItem());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSpecialMoment(this));
    }
}

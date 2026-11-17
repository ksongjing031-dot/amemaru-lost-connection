package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * スキルの要求の結果（本人だけ）。クライアントは押した瞬間にモーションを始めているので、だめだったときは止める
 * （Phase 0-B 9章「だめなら取り消しを返し、手元のモーションを止める」）。
 */
public final class SkillResultS2C {
    private final int sequence;
    private final boolean ok;
    private final int failure;

    public SkillResultS2C(int sequence, boolean ok, int failure) {
        this.sequence = sequence;
        this.ok = ok;
        this.failure = failure;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sequence);
        buf.writeBoolean(ok);
        buf.writeVarInt(failure);
    }

    public static SkillResultS2C decode(FriendlyByteBuf buf) {
        return new SkillResultS2C(buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSkillResult(this));
    }

    public int sequence() {
        return sequence;
    }

    public boolean ok() {
        return ok;
    }

    /** 失敗の理由の番号（{@code SkillService.Failure} の順）。 */
    public int failure() {
        return failure;
    }
}

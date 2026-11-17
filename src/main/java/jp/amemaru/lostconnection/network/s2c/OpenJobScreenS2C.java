package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 職業選択の画面を開く（誓約の結晶を右クリックしたとき）。今の職業と、転職の費用の決まりを送る。
 */
public final class OpenJobScreenS2C {
    private final int currentJob;
    private final boolean free;
    private final int tokensNeeded;
    private final int tokensHeld;
    private final int day;
    private final int freeUntilDay;
    private final boolean promoted;
    private final boolean promotedOnce;

    public OpenJobScreenS2C(int currentJob, boolean free, int tokensNeeded, int tokensHeld, int day, int freeUntilDay,
                            boolean promoted, boolean promotedOnce) {
        this.currentJob = currentJob;
        this.free = free;
        this.tokensNeeded = tokensNeeded;
        this.tokensHeld = tokensHeld;
        this.day = day;
        this.freeUntilDay = freeUntilDay;
        this.promoted = promoted;
        this.promotedOnce = promotedOnce;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(currentJob + 1);
        buf.writeBoolean(free);
        buf.writeVarInt(tokensNeeded);
        buf.writeVarInt(tokensHeld);
        buf.writeVarInt(day);
        buf.writeVarInt(freeUntilDay);
        buf.writeBoolean(promoted);
        buf.writeBoolean(promotedOnce);
    }

    public static OpenJobScreenS2C decode(FriendlyByteBuf buf) {
        return new OpenJobScreenS2C(buf.readVarInt() - 1, buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleOpenJobScreen(this));
    }

    /** 今の職業の番号（なければ -1）。 */
    public int currentJob() {
        return currentJob;
    }

    /** 無料で選べる（初めて・お試し期間）か。 */
    public boolean free() {
        return free;
    }

    public int tokensNeeded() {
        return tokensNeeded;
    }

    public int tokensHeld() {
        return tokensHeld;
    }

    public int day() {
        return day;
    }

    public int freeUntilDay() {
        return freeUntilDay;
    }

    public boolean promoted() {
        return promoted;
    }

    public boolean promotedOnce() {
        return promotedOnce;
    }
}

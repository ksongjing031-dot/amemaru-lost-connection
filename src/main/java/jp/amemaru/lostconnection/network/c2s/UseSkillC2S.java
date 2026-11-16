package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.job.SkillService;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.SkillResultS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * スキルを使う（Phase 0-B 5章 {@code UseSkill}：スキル枠の番号、向きと狙った相手）。
 *
 * <p>送るのは「どの枠を・何を狙って」だけ。射程・視線・クールダウン・MPはサーバーで確かめる。
 * 番号（{@code sequence}）は、結果（{@link SkillResultS2C}）と手元のモーションを結びつけるために使う。</p>
 */
public final class UseSkillC2S {
    private final int sequence;
    private final int slot;
    private final int targetEntity;
    @Nullable
    private final Vec3 targetPoint;

    public UseSkillC2S(int sequence, int slot, int targetEntity, @Nullable Vec3 targetPoint) {
        this.sequence = sequence;
        this.slot = slot;
        this.targetEntity = targetEntity;
        this.targetPoint = targetPoint;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sequence);
        buf.writeByte(slot);
        buf.writeVarInt(targetEntity + 1);
        buf.writeBoolean(targetPoint != null);
        if (targetPoint != null) {
            buf.writeDouble(targetPoint.x);
            buf.writeDouble(targetPoint.y);
            buf.writeDouble(targetPoint.z);
        }
    }

    public static UseSkillC2S decode(FriendlyByteBuf buf) {
        int sequence = buf.readVarInt();
        int slot = buf.readByte();
        int entity = buf.readVarInt() - 1;
        Vec3 point = null;
        if (buf.readBoolean()) {
            point = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        }
        return new UseSkillC2S(sequence, slot, entity, point);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null || slot < 0 || slot >= PlayerData.LOADOUT_SIZE) {
            return;
        }
        if (targetPoint != null && (!Double.isFinite(targetPoint.x) || !Double.isFinite(targetPoint.y) || !Double.isFinite(targetPoint.z))) {
            return;
        }
        PlayerDataCapability.get(player).ifPresent(data -> {
            SkillService.Result result = SkillService.tryUse(player, data, slot, targetEntity, targetPoint);
            ModNetwork.sendTo(player, new SkillResultS2C(sequence, result.ok(),
                    result.failure() == null ? -1 : result.failure().ordinal()));
        });
    }
}

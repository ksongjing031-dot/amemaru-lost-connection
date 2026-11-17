package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.job.SkillTreeService;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * スキル枠にスキルを入れる・空にする（Phase 0-B 5章 {@code SetLoadout}）。スキルの番号が -1 なら空にする。
 */
public final class SetLoadoutC2S {
    private final int slot;
    private final int skillIndex;

    public SetLoadoutC2S(int slot, int skillIndex) {
        this.slot = slot;
        this.skillIndex = skillIndex;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(slot);
        buf.writeVarInt(skillIndex + 1);
    }

    public static SetLoadoutC2S decode(FriendlyByteBuf buf) {
        return new SetLoadoutC2S(buf.readByte(), buf.readVarInt() - 1);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
        SkillDef skill = skillIndex < 0 ? null : SkillRegistry.get().skillAt(skillIndex);
        if (skillIndex >= 0 && skill == null) {
            return;
        }
        PlayerDataCapability.get(player).ifPresent(data ->
                SkillTreeService.setLoadout(player, data, slot, skill == null ? null : skill.id()));
    }
}

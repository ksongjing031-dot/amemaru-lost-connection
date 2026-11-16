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
 * スキルの次の段階を覚える（Phase 0-B 5章 {@code LearnSkill}）。残りポイント・前提スキル・職業はサーバーで確かめる。
 */
public final class LearnSkillC2S {
    private final int skillIndex;

    public LearnSkillC2S(int skillIndex) {
        this.skillIndex = skillIndex;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(skillIndex);
    }

    public static LearnSkillC2S decode(FriendlyByteBuf buf) {
        return new LearnSkillC2S(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
        SkillDef skill = SkillRegistry.get().skillAt(skillIndex);
        if (skill == null) {
            return;
        }
        PlayerDataCapability.get(player).ifPresent(data -> SkillTreeService.learn(player, data, skill.id()));
    }
}

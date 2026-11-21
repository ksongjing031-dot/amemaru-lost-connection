package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * パーティの様子（Phase 0-B 5章 {@code PartyState}。送り先はパーティのメンバーだけ）：メンバー（名前・リーダーか・いるか・
 * レベル・HPの割合・ダウンの残り・同じディメンションか）と、本人に届いている誘い。
 */
public record PartyStateS2C(boolean inParty, List<Member> members, List<Invite> invites) {
    private static final int MAX_MEMBERS = 16;
    private static final int MAX_INVITES = 32;

    public record Member(UUID id, String name, boolean leader, boolean online, int level, float hp, boolean down, int downTicks,
                         int downLength, boolean sameLevel) {
    }

    public record Invite(UUID inviter, String name, int ticksLeft) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(inParty);
        buf.writeVarInt(Math.min(members.size(), MAX_MEMBERS));
        for (int i = 0; i < Math.min(members.size(), MAX_MEMBERS); i++) {
            Member m = members.get(i);
            buf.writeUUID(m.id());
            buf.writeUtf(m.name(), 32);
            buf.writeBoolean(m.leader());
            buf.writeBoolean(m.online());
            buf.writeVarInt(Math.max(0, m.level()));
            buf.writeFloat(m.hp());
            buf.writeBoolean(m.down());
            buf.writeVarInt(Math.max(0, m.downTicks()));
            buf.writeVarInt(Math.max(0, m.downLength()));
            buf.writeBoolean(m.sameLevel());
        }
        buf.writeVarInt(Math.min(invites.size(), MAX_INVITES));
        for (int i = 0; i < Math.min(invites.size(), MAX_INVITES); i++) {
            Invite invite = invites.get(i);
            buf.writeUUID(invite.inviter());
            buf.writeUtf(invite.name(), 32);
            buf.writeVarInt(Math.max(0, invite.ticksLeft()));
        }
    }

    public static PartyStateS2C decode(FriendlyByteBuf buf) {
        boolean inParty = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_MEMBERS) {
            throw new IllegalArgumentException("too many party members: " + count);
        }
        List<Member> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            members.add(new Member(buf.readUUID(), buf.readUtf(32), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),
                    buf.readFloat(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        }
        int inviteCount = buf.readVarInt();
        if (inviteCount < 0 || inviteCount > MAX_INVITES) {
            throw new IllegalArgumentException("too many party invites: " + inviteCount);
        }
        List<Invite> invites = new ArrayList<>(inviteCount);
        for (int i = 0; i < inviteCount; i++) {
            invites.add(new Invite(buf.readUUID(), buf.readUtf(32), buf.readVarInt()));
        }
        return new PartyStateS2C(inParty, members, invites);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handlePartyState(this));
    }
}

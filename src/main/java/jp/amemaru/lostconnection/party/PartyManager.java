package jp.amemaru.lostconnection.party;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * パーティの一覧（ワールドの data の {@code amemaru_party.dat}。Phase 0-B 3章 {@code PartyManager}）。
 *
 * <p>ここは、パーティを作る・入れる・抜く・リーダーを変える・招待を覚える、だけをする（決まりの確かめと知らせは
 * {@link PartyService}）。招待は保存しない（サーバーを止めると消える）。</p>
 */
public final class PartyManager extends SavedData {
    private static final String NAME = "amemaru_party";

    private final Map<UUID, Party> parties = new LinkedHashMap<>();
    private final Map<UUID, UUID> memberOf = new HashMap<>();
    /** 招待：誘われた人 → 誘った人 → 招待 */
    private final Map<UUID, Map<UUID, Invite>> invites = new HashMap<>();

    /** 招待（誘った人と、消える時刻） */
    public record Invite(UUID inviter, String inviterName, long expires) {
    }

    public static PartyManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PartyManager::load, PartyManager::new, NAME);
    }

    // ---------------------------------------------------------------- 読む

    @Nullable
    public Party partyOf(UUID player) {
        UUID id = memberOf.get(player);
        return id == null ? null : parties.get(id);
    }

    @Nullable
    public Party party(UUID id) {
        return parties.get(id);
    }

    public Collection<Party> parties() {
        return Collections.unmodifiableCollection(parties.values());
    }

    // ---------------------------------------------------------------- 変える

    /** 新しいパーティを作る（作った人がリーダー）。 */
    Party create(UUID leader, String leaderName) {
        Party party = new Party(UUID.randomUUID(), leader, leaderName);
        parties.put(party.id(), party);
        memberOf.put(leader, party.id());
        setDirty();
        return party;
    }

    void add(Party party, UUID player, String name) {
        party.add(player, name);
        memberOf.put(player, party.id());
        setDirty();
    }

    /** 抜く。残りが1人以下になったら解散する（解散したら true）。 */
    boolean remove(Party party, UUID player) {
        party.remove(player);
        memberOf.remove(player);
        setDirty();
        if (party.size() <= 1) {
            disband(party);
            return true;
        }
        return false;
    }

    void disband(Party party) {
        for (UUID member : party.members()) {
            memberOf.remove(member);
        }
        parties.remove(party.id());
        setDirty();
    }

    void setLeader(Party party, UUID player) {
        party.setLeader(player);
        setDirty();
    }

    void remember(Party party, UUID player, String name) {
        if (!name.equals(party.name(player))) {
            party.remember(player, name);
            setDirty();
        }
    }

    // ---------------------------------------------------------------- 招待

    void invite(UUID target, UUID inviter, String inviterName, long expires) {
        invites.computeIfAbsent(target, k -> new LinkedHashMap<>()).put(inviter, new Invite(inviter, inviterName, expires));
    }

    @Nullable
    Invite invite(UUID target, UUID inviter, long now) {
        Map<UUID, Invite> map = invites.get(target);
        Invite invite = map == null ? null : map.get(inviter);
        return invite != null && invite.expires() > now ? invite : null;
    }

    /** 届いている招待（新しい順。消えたものは除く）。 */
    public List<Invite> invitesFor(UUID target, long now) {
        Map<UUID, Invite> map = invites.get(target);
        if (map == null) {
            return List.of();
        }
        List<Invite> list = new ArrayList<>();
        for (Invite invite : map.values()) {
            if (invite.expires() > now) {
                list.add(invite);
            }
        }
        Collections.reverse(list);
        return list;
    }

    void removeInvite(UUID target, UUID inviter) {
        Map<UUID, Invite> map = invites.get(target);
        if (map != null) {
            map.remove(inviter);
            if (map.isEmpty()) {
                invites.remove(target);
            }
        }
    }

    void clearInvitesFor(UUID target) {
        invites.remove(target);
    }

    /** 消えた招待を捨てる。捨てた招待（誘われた人 → 招待）を返す。 */
    List<Map.Entry<UUID, Invite>> pruneInvites(long now) {
        List<Map.Entry<UUID, Invite>> expired = new ArrayList<>();
        for (Iterator<Map.Entry<UUID, Map<UUID, Invite>>> it = invites.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<UUID, Invite>> entry = it.next();
            for (Iterator<Invite> inner = entry.getValue().values().iterator(); inner.hasNext(); ) {
                Invite invite = inner.next();
                if (invite.expires() <= now) {
                    expired.add(Map.entry(entry.getKey(), invite));
                    inner.remove();
                }
            }
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
        return expired;
    }

    // ---------------------------------------------------------------- 保存

    public static PartyManager load(CompoundTag tag) {
        PartyManager manager = new PartyManager();
        ListTag list = tag.getList("parties", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Party party = Party.load(list.getCompound(i));
            if (party == null || party.size() <= 1) {
                continue;
            }
            manager.parties.put(party.id(), party);
            for (UUID member : party.members()) {
                // 同じ人が2つのパーティにいたら、先のパーティに入れておく
                manager.memberOf.putIfAbsent(member, party.id());
            }
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Party party : parties.values()) {
            list.add(party.save());
        }
        tag.put("parties", list);
        return tag;
    }
}

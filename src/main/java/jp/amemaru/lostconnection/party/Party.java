package jp.amemaru.lostconnection.party;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * パーティ（Phase 4。Phase 0-B 4.2 {@code SocialData} の「パーティの構成」）。メンバーは入った順に並ぶ。
 * いない間（ログアウト中）もメンバーのまま。名前は、最後に見た名前を覚えておく（いない人の表示用）。
 */
public final class Party {
    private final UUID id;
    private UUID leader;
    private final List<UUID> members = new ArrayList<>();
    private final Map<UUID, String> names = new HashMap<>();

    Party(UUID id, UUID leader, String leaderName) {
        this.id = id;
        this.leader = leader;
        add(leader, leaderName);
    }

    private Party(UUID id, UUID leader) {
        this.id = id;
        this.leader = leader;
    }

    public UUID id() {
        return id;
    }

    public UUID leader() {
        return leader;
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    public List<UUID> members() {
        return Collections.unmodifiableList(members);
    }

    public int size() {
        return members.size();
    }

    public boolean contains(UUID player) {
        return members.contains(player);
    }

    /** 覚えている名前（知らなければ空）。 */
    public String name(UUID player) {
        return names.getOrDefault(player, "");
    }

    /** 名前からメンバーを探す（大文字と小文字は区別しない）。 */
    @Nullable
    public UUID find(String name) {
        for (UUID member : members) {
            if (names.getOrDefault(member, "").equalsIgnoreCase(name)) {
                return member;
            }
        }
        return null;
    }

    void add(UUID player, String name) {
        if (!members.contains(player)) {
            members.add(player);
        }
        remember(player, name);
    }

    void remember(UUID player, String name) {
        if (name != null && !name.isEmpty()) {
            names.put(player, name);
        }
    }

    /** 抜ける。リーダーが抜けたら、次に入った人がリーダーになる。 */
    void remove(UUID player) {
        members.remove(player);
        names.remove(player);
        if (leader.equals(player) && !members.isEmpty()) {
            leader = members.get(0);
        }
    }

    void setLeader(UUID player) {
        if (members.contains(player)) {
            leader = player;
        }
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("leader", leader);
        ListTag list = new ListTag();
        for (UUID member : members) {
            CompoundTag one = new CompoundTag();
            one.putUUID("id", member);
            one.putString("name", names.getOrDefault(member, ""));
            list.add(one);
        }
        tag.put("members", list);
        return tag;
    }

    @Nullable
    static Party load(CompoundTag tag) {
        if (!tag.hasUUID("id") || !tag.hasUUID("leader")) {
            return null;
        }
        Party party = new Party(tag.getUUID("id"), tag.getUUID("leader"));
        ListTag list = tag.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            if (one.hasUUID("id")) {
                party.add(one.getUUID("id"), one.getString("name"));
            }
        }
        if (party.members.isEmpty()) {
            return null;
        }
        if (!party.members.contains(party.leader)) {
            party.leader = party.members.get(0);
        }
        return party;
    }

    @Override
    public String toString() {
        List<String> list = new ArrayList<>();
        for (UUID member : members) {
            list.add((member.equals(leader) ? "*" : "") + names.getOrDefault(member, member.toString()));
        }
        return "Party" + list;
    }
}

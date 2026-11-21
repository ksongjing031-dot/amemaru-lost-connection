package jp.amemaru.lostconnection.party;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 敵を倒したときの、パーティの経験値とドロップの分け方（Phase 4。Phase 0-A 20章・26章の回答）。
 *
 * <ul>
 *   <li>受け取る人：攻撃を当てた人（と挑発した人。Phase 3の決まり）に、その人のパーティのメンバーで、倒れた場所から
 *       32ブロック以内（config の {@code party.expShareRange}）の人を足す（攻撃していない回復・支援の人も）【補完】。</li>
 *   <li>経験値：敵の経験値を受け取る人の数で均等に分け、パーティごとに人数のボーナスをかける。パーティの合計は
 *       「1＋0.2×（そのパーティの人数−1）」倍（{@code party.expBonusPerMember}）。1つのパーティで倒せば、
 *       Phase 0-A のとおり「近くのメンバーで均等＋人数ボーナス」。ソロの人はボーナスなし（Phase 3と同じ）。</li>
 *   <li>ドロップ：受け取る人が一人ずつ抽選する（{@code DropService}。Phase 3と同じ）。</li>
 * </ul>
 */
public final class PartyExp {
    private PartyExp() {
    }

    /** 受け取る人に、近くのパーティのメンバーを足す。 */
    public static List<ServerPlayer> withPartyMembers(List<ServerPlayer> receivers, Entity fallen, ServerLevel level) {
        double range = ServerConfig.PARTY_EXP_RANGE.get();
        Set<UUID> seen = new LinkedHashSet<>();
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer player : receivers) {
            if (seen.add(player.getUUID())) {
                list.add(player);
            }
        }
        for (ServerPlayer player : receivers) {
            for (ServerPlayer member : PartyService.onlineMembers(player)) {
                if (member.level() == level && !member.isSpectator() && member.distanceToSqr(fallen) <= range * range
                        && seen.add(member.getUUID())) {
                    list.add(member);
                }
            }
        }
        return list;
    }

    /** 一人ずつの経験値（パーティごとに人数のボーナスをかける）。 */
    public static Map<UUID, Integer> shares(int exp, List<ServerPlayer> receivers) {
        Map<UUID, Integer> result = new LinkedHashMap<>();
        if (exp <= 0 || receivers.isEmpty()) {
            return result;
        }
        // パーティごとに数える（パーティに入っていない人は、その人だけの組）
        Map<UUID, Integer> groupSize = new HashMap<>();
        Map<UUID, UUID> groupOf = new HashMap<>();
        for (ServerPlayer player : receivers) {
            Party party = PartyService.partyOf(player);
            UUID group = party != null ? party.id() : player.getUUID();
            groupOf.put(player.getUUID(), group);
            groupSize.merge(group, 1, Integer::sum);
        }
        double bonus = ServerConfig.PARTY_EXP_BONUS.get();
        for (ServerPlayer player : receivers) {
            int size = groupSize.get(groupOf.get(player.getUUID()));
            result.put(player.getUUID(), share(exp, receivers.size(), size, bonus));
        }
        return result;
    }

    /**
     * 一人の経験値：敵の経験値 ÷ 受け取る人の数 ×（1 ＋ ボーナス ×（パーティの人数 − 1））を切り上げ。
     * 例：経験値100を3人のパーティで倒すと、1人 ⌈100 ÷ 3 × 1.4⌉ = 47（合計141 ≒ 1.4倍）。
     */
    public static int share(int exp, int receivers, int groupSize, double bonus) {
        if (exp <= 0 || receivers <= 0) {
            return 0;
        }
        double each = (double) exp / receivers * (1.0 + bonus * Math.max(0, groupSize - 1));
        return (int) Math.ceil(each - 1.0E-9);
    }
}

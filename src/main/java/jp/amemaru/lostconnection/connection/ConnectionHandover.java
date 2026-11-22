package jp.amemaru.lostconnection.connection;

import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.equipment.GearItem;
import jp.amemaru.lostconnection.guild.GuildCounters;
import jp.amemaru.lostconnection.guild.GuildState;
import jp.amemaru.lostconnection.guild.TitleService;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 生産のコネクション量（Phase 7 その1。Phase 0-A 13章「他人のための制作（依頼の完了・手渡し）」「他人の装備の修理」）。
 *
 * <ul>
 *   <li>手渡し：作り手と違う人が、その装備を初めて身に着けたとき、作り手に15CP（同じ相手は1日3回。装備に印を付けて1度だけ）</li>
 *   <li>持ち主の印：装備を使って耐久を減らした人（攻撃・スキル・敵の攻撃を受けた）を、その装備の「持ち主」として覚える【補完】。
 *       修理した人が持ち主でなければ「他人の装備の修理」（持ち主の耐久の減りを、ほかの人が直した）</li>
 * </ul>
 * どちらも、称号「誰かのための槌」の数（ほかの人のための制作・修理・厳選）にも足す。
 */
public final class ConnectionHandover {
    /** 持ち主（最後に使って耐久を減らした人）。 */
    public static final String OWNER = "amemaru_owner";
    /** 作り手に「手渡し」のCPを渡したか。 */
    public static final String HANDED = "amemaru_handed";

    private ConnectionHandover() {
    }

    /** 装備を身に着けた・持ちかえた（GearService の装備の変化から）。作り手とちがう人が初めて身に着けたら「手渡し」。 */
    public static void onEquip(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GearItem)) {
            return;
        }
        UUID crafter = GearData.crafterId(stack);
        if (crafter == null || crafter.equals(player.getUUID())) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.getBoolean(HANDED)) {
            return;
        }
        tag.putBoolean(HANDED, true);
        ConnectionService.awardLater(player.server, crafter, ConnectionActions.CRAFT_FOR_OTHER, player.getUUID(), 1);
        addHelped(player.server, crafter);
    }

    /** 装備を使って耐久が減った（GearService の耐久から）：使った人を、その装備の持ち主として覚える。 */
    public static void markUser(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.hasUUID(OWNER) || !tag.getUUID(OWNER).equals(player.getUUID())) {
            tag.putUUID(OWNER, player.getUUID());
        }
    }

    /** 持ち主（最後に使って耐久を減らした人）。印がなければ null。 */
    @Nullable
    public static UUID owner(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(OWNER) ? tag.getUUID(OWNER) : null;
    }

    /** 工房で修理した（修理した人と、装備の持ち主）。 */
    public static void onRepaired(ServerPlayer player, ItemStack stack) {
        UUID owner = owner(stack);
        if (owner == null || owner.equals(player.getUUID())) {
            return;
        }
        if (ConnectionService.award(player, ConnectionActions.REPAIR_FOR_OTHER, owner) > 0) {
            addHelped(player.server, player.getUUID());
        }
    }

    /** 「ほかの人のための制作・修理・厳選」の数を足す（いなければ、次に入ったときに）。 */
    private static void addHelped(MinecraftServer server, UUID who) {
        ServerPlayer online = server.getPlayerList().getPlayer(who);
        if (online != null) {
            PlayerDataCapability.get(online).ifPresent(data -> data.getGuild().addCounter(GuildCounters.HELPED, 1));
            TitleService.check(online);
            return;
        }
        CompoundTag pending = new CompoundTag();
        pending.putString("type", "counter");
        pending.putString("key", GuildCounters.HELPED);
        pending.putLong("amount", 1);
        GuildState.get(server).addPending(who, pending);
    }
}

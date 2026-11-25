package jp.amemaru.lostconnection.guild;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.economy.MarketData;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.GuildS2C;
import jp.amemaru.lostconnection.network.s2c.MarketStallsS2C;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * フリーマーケットの露店の区画を増やす寄付（Phase 0-A 7章「寄付で露店の数が増える（12区画から20区画。フィンが寄付を募り、目標に届いた
 * 次の朝に増える。11月20日に決定）」。Phase 6 その3。その1で約束した分）。
 *
 * <ul>
 *   <li>市場監督フィン・ロッソとの会話から、寄付の画面を開く。寄付したノットは世界から消える（回収）</li>
 *   <li>目標は25人基準の {@code stallDonationGoal}（2万ノット【補完】）を、参加人数で増減する（Phase 0-A 7章の寄付の決まりと同じ）</li>
 *   <li>目標に届いた次の進行日に、区画が20に増える。寄付した人の名前と額は記録に残す（銘板とスタッフロールは Phase 7・9）</li>
 *   <li>寄付で増えるCP（100ノットごとに1、1日50まで）は、コネクション量を作るとき（Phase 7）に数える</li>
 * </ul>
 */
public final class StallDonation {
    /** 1回に寄付できる額の上限（押し間違いを防ぐ）。 */
    public static final long MAX_ONCE = 1_000_000L;

    private StallDonation() {
    }

    /** 目標（参加人数で増減。25人基準）。 */
    public static long goal() {
        long base = ServerConfig.GUILD_STALL_DONATION_GOAL.get();
        int participants = ServerConfig.value(ServerConfig.GUILD_PARTICIPANTS);
        return Math.max(1L, Math.round(base * participants / 25.0));
    }

    public static void open(ServerPlayer player) {
        CompoundTag tag = tag(player);
        tag.putString("screen", "donation");
        ModNetwork.sendTo(player, new GuildS2C("screen", tag));
    }

    public static CompoundTag tag(ServerPlayer player) {
        GuildState state = GuildState.get(player.server);
        CompoundTag tag = new CompoundTag();
        tag.putLong("total", state.stallDonated());
        tag.putLong("goal", goal());
        tag.putBoolean("reached", state.stallReachedDay() > 0);
        tag.putBoolean("expanded", MarketData.get(player.server).expanded());
        tag.putLong("mine", state.stallDonors().getOrDefault(player.getUUID(), 0L));
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        tag.putLong("knots", data == null ? 0 : data.getKnots());
        List<Map.Entry<UUID, Long>> donors = new ArrayList<>(state.stallDonors().entrySet());
        donors.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        ListTag list = new ListTag();
        for (int i = 0; i < Math.min(10, donors.size()); i++) {
            CompoundTag one = new CompoundTag();
            one.putString("name", state.stallDonorName(donors.get(i).getKey()));
            one.putLong("amount", donors.get(i).getValue());
            list.add(one);
        }
        tag.put("donors", list);
        tag.putInt("donorCount", donors.size());
        return tag;
    }

    /** 寄付する。寄付できたら true。 */
    public static boolean donate(ServerPlayer player, long amount) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || amount <= 0 || amount > MAX_ONCE) {
            return false;
        }
        GuildState state = GuildState.get(player.server);
        if (MarketData.get(player.server).expanded() || state.stallReachedDay() > 0) {
            player.displayClientMessage(Component.translatable("message.amemaru.donation.done").withStyle(ChatFormatting.GRAY), false);
            return false;
        }
        long need = Math.max(0L, goal() - state.stallDonated());
        long pay = Math.min(amount, need);
        if (pay <= 0 || !Wallet.pay(player, data, pay, "stall donation")) {
            player.displayClientMessage(Component.translatable("message.amemaru.donation.not_enough").withStyle(ChatFormatting.RED), false);
            return false;
        }
        state.addStallDonation(player.getUUID(), player.getGameProfile().getName(), pay);
        player.displayClientMessage(Component.translatable("message.amemaru.donation.thanks", pay).withStyle(ChatFormatting.GOLD), false);
        // Phase 7 その1：施設への寄付（100ノットごとに1CP、1日50まで）
        jp.amemaru.lostconnection.connection.ConnectionService.award(player,
                jp.amemaru.lostconnection.connection.ConnectionActions.DONATION, null, (int) Math.min(Integer.MAX_VALUE, pay));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
        if (state.stallDonated() >= goal()) {
            int day = ProgressionData.get(player.server).day();
            state.setStallReachedDay(day);
            player.server.getPlayerList().broadcastSystemMessage(Component.translatable("message.amemaru.donation.reached")
                    .withStyle(ChatFormatting.GOLD), false);
            state.chronicle(day, System.currentTimeMillis(), "chronicle.amemaru.stall_donation", List.of(String.valueOf(state.stallDonors()
                    .size())));
        }
        open(player);
        return true;
    }

    /** 日が変わったら、目標に届いていた区画を増やす（届いた日の次の朝）。 */
    public static void onDay(MinecraftServer server, int day) {
        GuildState state = GuildState.get(server);
        MarketData market = MarketData.get(server);
        if (state.stallReachedDay() > 0 && day > state.stallReachedDay() && !market.expanded()) {
            market.setExpanded(true);
            server.getPlayerList().broadcastSystemMessage(Component.translatable("message.amemaru.donation.expanded")
                    .withStyle(ChatFormatting.GOLD), false);
            state.chronicle(day, System.currentTimeMillis(), "chronicle.amemaru.stall_expanded", List.of());
            ModNetwork.sendToAll(MarketStallsS2C.current(server));
        }
    }
}

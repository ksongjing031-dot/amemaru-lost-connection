package jp.amemaru.lostconnection.economy;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤー同士のノットの受け渡し（11月22日に決定。Phase 6）。
 *
 * <ul>
 *   <li>渡す相手はサーバーにいる人だけ（オフラインの人には渡せない）【補完】</li>
 *   <li>持っている分まで。1回の上限は config の {@code transferMax}（初期値は上限なし）【補完】</li>
 *   <li>押しすぎを防ぐため、渡したあと {@code transferCooldownSeconds} 秒は次を受け付けない【補完】</li>
 *   <li>出し入れは {@link Wallet#transfer} を通り、{@link EconomyLog} に「移動」として残る</li>
 * </ul>
 */
public final class KnotTransfer {
    private KnotTransfer() {
    }

    /** 受け渡しの結果 */
    public enum Result { OK, DISABLED, OFFLINE, SELF, INVALID_AMOUNT, OVER_LIMIT, NOT_ENOUGH, TOO_FAST }

    /** 最後に渡した時刻（ミリ秒。押しすぎを防ぐ） */
    private static final Map<UUID, Long> LAST = new HashMap<>();

    /**
     * 渡す。{@code to} が null ならオフライン（いない）。結果は両方の人に知らせる（OK のとき）か、渡そうとした人に知らせる。
     */
    public static Result transfer(ServerPlayer from, @Nullable ServerPlayer to, long amount) {
        Result result = check(from, to, amount);
        if (result != Result.OK || to == null) {
            tell(from, result, to, amount);
            return result;
        }
        PlayerData fromData = PlayerDataCapability.get(from).orElse(null);
        PlayerData toData = PlayerDataCapability.get(to).orElse(null);
        if (fromData == null || toData == null) {
            tell(from, Result.OFFLINE, to, amount);
            return Result.OFFLINE;
        }
        if (!Wallet.transfer(from, fromData, to, toData, amount, "transfer")) {
            tell(from, Result.NOT_ENOUGH, to, amount);
            return Result.NOT_ENOUGH;
        }
        LAST.put(from.getUUID(), System.currentTimeMillis());
        String shown = format(amount);
        from.sendSystemMessage(Component.translatable("message.amemaru.transfer.sent", to.getGameProfile().getName(), shown)
                .withStyle(ChatFormatting.GOLD));
        to.sendSystemMessage(Component.translatable("message.amemaru.transfer.received", from.getGameProfile().getName(), shown)
                .withStyle(ChatFormatting.GOLD));
        to.level().playSound(null, to.getX(), to.getY(), to.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        return Result.OK;
    }

    /** 渡せるか（所持金・相手・上限・間隔）。渡さない。 */
    public static Result check(ServerPlayer from, @Nullable ServerPlayer to, long amount) {
        if (!ServerConfig.TRANSFER_ENABLED.get()) {
            return Result.DISABLED;
        }
        if (to == null || to.hasDisconnected()) {
            return Result.OFFLINE;
        }
        if (to.getUUID().equals(from.getUUID())) {
            return Result.SELF;
        }
        if (amount <= 0) {
            return Result.INVALID_AMOUNT;
        }
        long max = ServerConfig.TRANSFER_MAX.get();
        if (max > 0 && amount > max) {
            return Result.OVER_LIMIT;
        }
        PlayerData data = PlayerDataCapability.get(from).orElse(null);
        if (data == null || Wallet.balance(data) < amount) {
            return Result.NOT_ENOUGH;
        }
        Long last = LAST.get(from.getUUID());
        long cooldown = Math.round(ServerConfig.TRANSFER_COOLDOWN_SECONDS.get() * 1000.0);
        if (last != null && System.currentTimeMillis() - last < cooldown) {
            return Result.TOO_FAST;
        }
        return Result.OK;
    }

    /** 間隔の記録を消す（自動テストで使う）。 */
    public static void resetCooldown(UUID player) {
        LAST.remove(player);
    }

    private static void tell(ServerPlayer from, Result result, @Nullable ServerPlayer to, long amount) {
        if (result == Result.OK) {
            return;
        }
        Component message = switch (result) {
            case DISABLED -> Component.translatable("message.amemaru.transfer.disabled");
            case OFFLINE -> Component.translatable("message.amemaru.transfer.offline");
            case SELF -> Component.translatable("message.amemaru.transfer.self");
            case INVALID_AMOUNT -> Component.translatable("message.amemaru.transfer.invalid");
            case OVER_LIMIT -> Component.translatable("message.amemaru.transfer.over_limit", format(ServerConfig.TRANSFER_MAX.get()));
            case NOT_ENOUGH -> Component.translatable("message.amemaru.transfer.not_enough", format(amount));
            case TOO_FAST -> Component.translatable("message.amemaru.transfer.too_fast");
            default -> Component.empty();
        };
        from.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED));
    }

    public static String format(long amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }
}

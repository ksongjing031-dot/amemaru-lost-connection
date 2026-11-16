package jp.amemaru.lostconnection.core.data;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * データパックの読み込みで見つかった問題を、管理者（権限レベル2以上）に知らせる。
 *
 * <p>知らせるのは、{@code /reload} の後と、管理者がログインしたとき。サーバーのログにも残る。</p>
 */
public final class DataReport {
    private static final int MAX_LINES = 8;
    private static final List<DefinitionLoader<?>> LOADERS = new CopyOnWriteArrayList<>();

    private DataReport() {
    }

    static void register(DefinitionLoader<?> loader) {
        LOADERS.add(loader);
    }

    /** すべての種類の問題をまとめて返す。 */
    public static List<String> allErrors() {
        List<String> all = new ArrayList<>();
        for (DefinitionLoader<?> loader : LOADERS) {
            all.addAll(loader.errors());
        }
        return all;
    }

    /** 渡されたプレイヤーのうち、管理者にだけ問題を知らせる。問題がなければ何もしない。 */
    public static void notifyAdmins(Collection<ServerPlayer> players) {
        List<String> errors = allErrors();
        if (errors.isEmpty()) {
            return;
        }
        for (ServerPlayer player : players) {
            if (!player.hasPermissions(2)) {
                continue;
            }
            player.sendSystemMessage(Component.translatable("message.amemaru.data_errors", errors.size())
                    .withStyle(ChatFormatting.RED));
            for (int i = 0; i < errors.size() && i < MAX_LINES; i++) {
                player.sendSystemMessage(Component.literal("・" + errors.get(i)).withStyle(ChatFormatting.GRAY));
            }
            if (errors.size() > MAX_LINES) {
                player.sendSystemMessage(Component.translatable("message.amemaru.data_errors_more", errors.size() - MAX_LINES)
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }
}

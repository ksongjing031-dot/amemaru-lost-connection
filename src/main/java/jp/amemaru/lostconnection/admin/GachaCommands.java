package jp.amemaru.lostconnection.admin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import jp.amemaru.lostconnection.gacha.GachaRules;
import jp.amemaru.lostconnection.gacha.GachaService;
import jp.amemaru.lostconnection.player.AdventureData;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;

/**
 * 管理者のコマンド：祈願（ガチャ。11月27日）。
 *
 * <ul>
 *   <li>{@code gacha open <人> [祈願]}：祈願の画面を開く（巫女から離れていても引ける。まだ開いていない祈願も見える）</li>
 *   <li>{@code gacha tokens <人> <祈願> <数>}：その祈願の祈願札を渡す</li>
 *   <li>{@code gacha pull <人> <祈願> <回数>}：引く（祈願札は使う。画面を開いていれば、結果の演出が出る）</li>
 *   <li>{@code gacha pity <人> <祈願> <回数>}：★5が出ないまま引いた回数を決める（天井の確かめ）</li>
 * </ul>
 */
public final class GachaCommands {
    private static final SuggestionProvider<CommandSourceStack> BANNERS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(GachaRules.current().banners().stream().map(GachaRules.Banner::id), builder);

    private GachaCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> gachaCommand() {
        return Commands.literal("gacha")
                .then(Commands.literal("open")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> open(EntityArgument.getPlayer(c, "target"), ""))
                                .then(Commands.argument("banner", StringArgumentType.word()).suggests(BANNERS)
                                        .executes(c -> open(EntityArgument.getPlayer(c, "target"), StringArgumentType.getString(c, "banner"))))))
                .then(Commands.literal("tokens")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("banner", StringArgumentType.word()).suggests(BANNERS)
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 640))
                                                .executes(c -> tokens(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                        StringArgumentType.getString(c, "banner"), IntegerArgumentType.getInteger(c, "count")))))))
                .then(Commands.literal("pull")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("banner", StringArgumentType.word()).suggests(BANNERS)
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 10))
                                                .executes(c -> pull(c.getSource(), EntityArgument.getPlayer(c, "target"),
                                                        StringArgumentType.getString(c, "banner"), IntegerArgumentType.getInteger(c, "count")))))))
                .then(Commands.literal("pity")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("banner", StringArgumentType.word()).suggests(BANNERS)
                                        .then(Commands.argument("since", IntegerArgumentType.integer(0, 1000))
                                                .executes(c -> pity(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                        StringArgumentType.getString(c, "banner"), IntegerArgumentType.getInteger(c, "since")))))));
    }

    private static int open(ServerPlayer player, String banner) {
        GachaService.openAdmin(player, banner);
        return 1;
    }

    private static int tokens(CommandSourceStack source, Collection<ServerPlayer> targets, String bannerId, int count) {
        GachaRules.Banner banner = GachaRules.current().banner(bannerId);
        Item item = banner == null ? null : ForgeRegistries.ITEMS.getValue(banner.token());
        if (item == null) {
            source.sendFailure(Component.translatable("command.amemaru.gacha.unknown", bannerId));
            return 0;
        }
        for (ServerPlayer player : targets) {
            int left = count;
            while (left > 0) {
                int n = Math.min(left, item.getMaxStackSize());
                ItemStack stack = new ItemStack(item, n);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                left -= n;
            }
        }
        AdminCommands.log(source, "gacha tokens " + bannerId + " x" + count);
        source.sendSuccess(() -> Component.translatable("command.amemaru.gacha.tokens", targets.size(), bannerId, count), true);
        return targets.size();
    }

    private static int pull(CommandSourceStack source, ServerPlayer player, String bannerId, int count) {
        if (GachaRules.current().banner(bannerId) == null) {
            source.sendFailure(Component.translatable("command.amemaru.gacha.unknown", bannerId));
            return 0;
        }
        GachaService.allowAnywhere(player);
        GachaService.onPull(player, bannerId, count == 1 ? 1 : GachaRules.current().tenCount());
        return 1;
    }

    private static int pity(CommandSourceStack source, Collection<ServerPlayer> targets, String bannerId, int since) {
        if (GachaRules.current().banner(bannerId) == null) {
            source.sendFailure(Component.translatable("command.amemaru.gacha.unknown", bannerId));
            return 0;
        }
        for (ServerPlayer player : targets) {
            PlayerData data = PlayerDataCapability.get(player).orElse(null);
            if (data != null) {
                data.getAdventure().gacha(bannerId).setSinceFive(since);
                data.getAdventure().gachaChanged();
            }
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.gacha.pity", targets.size(), bannerId, since), true);
        return targets.size();
    }
}

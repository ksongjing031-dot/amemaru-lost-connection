package jp.amemaru.lostconnection.party;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * パーティのコマンド {@code /amemaru party …}（Phase 4。だれでも使える）。パーティの画面（Uキー）と同じことができる。
 *
 * <ul>
 *   <li>{@code /amemaru party}：パーティの様子（メンバーと、届いている誘い）</li>
 *   <li>{@code invite <人>}・{@code accept [人]}・{@code decline [人]}：誘う・参加する・断る（人を省くと、いちばん新しい誘い）</li>
 *   <li>{@code leave}・{@code kick <名前>}・{@code leader <名前>}：抜ける・外す・リーダーを渡す（外す・渡すはリーダーだけ）</li>
 * </ul>
 */
public final class PartyCommands {
    private static final SuggestionProvider<CommandSourceStack> MEMBERS = (ctx, builder) -> {
        List<String> names = new ArrayList<>();
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            Party party = PartyService.partyOf(player);
            if (party != null) {
                for (UUID member : party.members()) {
                    if (!member.equals(player.getUUID())) {
                        names.add(party.name(member));
                    }
                }
            }
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private PartyCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> partyCommand() {
        return Commands.literal("party")
                .executes(ctx -> show(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    PartyService.invite(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"));
                                    return 1;
                                })))
                .then(Commands.literal("accept")
                        .executes(ctx -> {
                            PartyService.accept(ctx.getSource().getPlayerOrException(), null);
                            return 1;
                        })
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    PartyService.accept(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player").getUUID());
                                    return 1;
                                })))
                .then(Commands.literal("decline")
                        .executes(ctx -> {
                            PartyService.decline(ctx.getSource().getPlayerOrException(), null);
                            return 1;
                        })
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    PartyService.decline(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player").getUUID());
                                    return 1;
                                })))
                .then(Commands.literal("leave")
                        .executes(ctx -> {
                            PartyService.leave(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(Commands.literal("kick")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(MEMBERS)
                                .executes(ctx -> byName(ctx, false))))
                .then(Commands.literal("leader")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(MEMBERS)
                                .executes(ctx -> byName(ctx, true))));
    }

    /** 外す・リーダーを渡す（名前は、いない人でもよい）。 */
    private static int byName(CommandContext<CommandSourceStack> ctx, boolean promote) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        Party party = PartyService.partyOf(player);
        UUID target = party == null ? null : party.find(name);
        if (party != null && target == null) {
            ServerPlayer online = player.server.getPlayerList().getPlayerByName(name);
            target = online == null ? null : online.getUUID();
        }
        if (target == null) {
            ctx.getSource().sendFailure(Component.translatable("message.amemaru.party.not_member"));
            return 0;
        }
        if (promote) {
            PartyService.promote(player, target);
        } else {
            PartyService.kick(player, target);
        }
        return 1;
    }

    /** パーティの様子をチャットに出す。 */
    private static int show(ServerPlayer player) {
        Party party = PartyService.partyOf(player);
        if (party == null) {
            player.sendSystemMessage(Component.translatable("message.amemaru.party.none").withStyle(ChatFormatting.GRAY));
        } else {
            MutableComponent list = Component.empty();
            for (int i = 0; i < party.members().size(); i++) {
                UUID member = party.members().get(i);
                if (i > 0) {
                    list.append(Component.translatable("commands.amemaru.separator"));
                }
                boolean online = player.server.getPlayerList().getPlayer(member) != null;
                MutableComponent one = Component.literal((party.isLeader(member) ? "★" : "") + party.name(member));
                list.append(one.withStyle(online ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
            }
            player.sendSystemMessage(Component.translatable("message.amemaru.party.list", party.size(), list).withStyle(ChatFormatting.AQUA));
        }
        for (PartyManager.Invite invite : PartyManager.get(player.server).invitesFor(player.getUUID(), player.server.overworld().getGameTime())) {
            player.sendSystemMessage(Component.translatable("message.amemaru.party.list_invite", invite.inviterName()).withStyle(ChatFormatting.AQUA));
        }
        return 1;
    }
}

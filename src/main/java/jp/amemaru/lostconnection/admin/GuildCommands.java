package jp.amemaru.lostconnection.admin;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import jp.amemaru.lostconnection.guild.BondService;
import jp.amemaru.lostconnection.guild.GuildData;
import jp.amemaru.lostconnection.guild.GuildService;
import jp.amemaru.lostconnection.guild.GuildState;
import jp.amemaru.lostconnection.guild.QuestDef;
import jp.amemaru.lostconnection.guild.QuestDefs;
import jp.amemaru.lostconnection.guild.QuestService;
import jp.amemaru.lostconnection.guild.RecordDefs;
import jp.amemaru.lostconnection.guild.RecordService;
import jp.amemaru.lostconnection.guild.StallDonation;
import jp.amemaru.lostconnection.guild.TitleDef;
import jp.amemaru.lostconnection.guild.TitleService;
import jp.amemaru.lostconnection.npc.NpcDef;
import jp.amemaru.lostconnection.npc.NpcDefs;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.economy.MarketData;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * ギルドの管理（Phase 6 その3）：称号・ユニーク称号・古代記録・親密度・クエスト・露店の区画の寄付・冒険日誌。
 *
 * <ul>
 *   <li>{@code title grant|revoke <人> <称号>}・{@code title list <人>}・{@code title show <人> <称号|none>}・{@code title owners <称号>}</li>
 *   <li>{@code title unique create <色|rainbow> <single|multi> <名前> [説明]}：その場でユニーク称号を作る（Phase 0-A 22章【確定】。
 *       名前・説明に空白を入れるときは "" で囲む）。{@code title unique give <人> <称号>}・{@code list}・{@code remove <称号>}</li>
 *   <li>{@code title limited list}・{@code title limited reset <称号>}（試すとき。取った人の記録を消す）</li>
 *   <li>{@code record find <人> <記録>}・{@code record decipher <人>}・{@code record book <人>}・{@code record forget <人> <記録|all>}</li>
 *   <li>{@code bond set|add <人> <NPC> <点>}・{@code bond show <人>}</li>
 *   <li>{@code quest start|complete|reset <人> <クエスト>}・{@code quest list <人>}・{@code quest open <人> <タブ>}</li>
 *   <li>{@code donation}（集まった額）・{@code donation add <額>}・{@code donation reset}・{@code donation expand}</li>
 *   <li>{@code chronicle}（冒険日誌の新しい順に10件）</li>
 * </ul>
 */
public final class GuildCommands {
    private static final SuggestionProvider<CommandSourceStack> TITLES = (context, builder) ->
            SharedSuggestionProvider.suggestResource(TitleService.all(context.getSource().getServer()).keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> RECORDS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(RecordDefs.all().keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> QUESTS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(QuestDefs.all().keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> NPCS = (context, builder) -> {
        List<ResourceLocation> ids = new ArrayList<>();
        for (NpcDef def : NpcDefs.all().values()) {
            if (def.bond() != null) {
                ids.add(def.id());
            }
        }
        return SharedSuggestionProvider.suggestResource(ids, builder);
    };
    private static final SuggestionProvider<CommandSourceStack> TABS = (context, builder) ->
            SharedSuggestionProvider.suggest(List.of("quests", "party", "titles", "chronicle", "bonds"), builder);

    private GuildCommands() {
    }

    // ================================================================ 称号

    public static LiteralArgumentBuilder<CommandSourceStack> titleCommand() {
        return Commands.literal("title")
                .then(Commands.literal("grant")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("title", ResourceLocationArgument.id()).suggests(TITLES)
                                        .executes(c -> grant(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                ResourceLocationArgument.getId(c, "title"))))))
                .then(Commands.literal("revoke")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("title", ResourceLocationArgument.id()).suggests(TITLES)
                                        .executes(c -> revoke(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                ResourceLocationArgument.getId(c, "title"))))))
                .then(Commands.literal("list")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> list(c.getSource(), EntityArgument.getPlayer(c, "target")))))
                .then(Commands.literal("show")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.literal("none")
                                        .executes(c -> show(c.getSource(), EntityArgument.getPlayer(c, "target"), null)))
                                .then(Commands.argument("title", ResourceLocationArgument.id()).suggests(TITLES)
                                        .executes(c -> show(c.getSource(), EntityArgument.getPlayer(c, "target"),
                                                ResourceLocationArgument.getId(c, "title"))))))
                .then(Commands.literal("owners")
                        .then(Commands.argument("title", ResourceLocationArgument.id()).suggests(TITLES)
                                .executes(c -> owners(c.getSource(), ResourceLocationArgument.getId(c, "title")))))
                .then(Commands.literal("unique")
                        .then(Commands.literal("create")
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("rainbow", "#FF7AD9", "#5DE0C0"), b))
                                        .then(Commands.argument("scope", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("single", "multi"), b))
                                                .then(Commands.argument("name", StringArgumentType.string())
                                                        .executes(c -> createUnique(c.getSource(), StringArgumentType.getString(c, "color"),
                                                                StringArgumentType.getString(c, "scope"), StringArgumentType.getString(c, "name"), ""))
                                                        .then(Commands.argument("description", StringArgumentType.greedyString())
                                                                .executes(c -> createUnique(c.getSource(), StringArgumentType.getString(c, "color"),
                                                                        StringArgumentType.getString(c, "scope"), StringArgumentType.getString(c, "name"),
                                                                        StringArgumentType.getString(c, "description"))))))))
                        .then(Commands.literal("give")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("title", ResourceLocationArgument.id()).suggests(TITLES)
                                                .executes(c -> grant(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                        ResourceLocationArgument.getId(c, "title"))))))
                        .then(Commands.literal("list").executes(c -> listUnique(c.getSource())))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("title", ResourceLocationArgument.id()).suggests(TITLES)
                                        .executes(c -> removeUnique(c.getSource(), ResourceLocationArgument.getId(c, "title"))))))
                .then(Commands.literal("limited")
                        .then(Commands.literal("list").executes(c -> listLimited(c.getSource())))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("title", ResourceLocationArgument.id()).suggests(TITLES)
                                        .executes(c -> resetLimited(c.getSource(), ResourceLocationArgument.getId(c, "title"))))));
    }

    private static int grant(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation id) throws CommandSyntaxException {
        TitleDef def = TitleService.def(source.getServer(), id);
        if (def == null) {
            source.sendFailure(Component.translatable("command.amemaru.title.unknown", id.toString()));
            return 0;
        }
        if (def.kind() == TitleDef.Kind.UNIQUE && TitleService.uniqueTaken(source.getServer(), id)) {
            source.sendFailure(Component.translatable("command.amemaru.title.unique_taken", def.name()));
            return 0;
        }
        if (def.kind() == TitleDef.Kind.UNIQUE) {
            GuildState.CustomTitle custom = GuildState.get(source.getServer()).custom(id);
            if (custom != null && custom.single() && targets.size() > 1) {
                source.sendFailure(Component.translatable("command.amemaru.title.unique_single", def.name()));
                return 0;
            }
        }
        int count = 0;
        for (ServerPlayer player : targets) {
            if (TitleService.grant(player, id, "admin " + source.getTextName())) {
                count++;
            }
        }
        int done = count;
        source.sendSuccess(() -> Component.translatable("command.amemaru.title.granted", def.name(), done), true);
        return count;
    }

    private static int revoke(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation id) {
        int count = 0;
        for (ServerPlayer player : targets) {
            if (TitleService.revoke(player, id)) {
                count++;
            }
        }
        int done = count;
        source.sendSuccess(() -> Component.translatable("command.amemaru.title.revoked", id.toString(), done), true);
        return count;
    }

    private static int list(CommandSourceStack source, ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.title.list", player.getGameProfile().getName(),
                data.getGuild().titles().size(), TitleService.all(source.getServer()).size()), false);
        for (Map.Entry<ResourceLocation, GuildData.OwnedTitle> entry : data.getGuild().titles().entrySet()) {
            TitleDef def = TitleService.def(source.getServer(), entry.getKey());
            Component name = def == null ? Component.literal(entry.getKey().toString()) : TitleService.styled(def);
            boolean shown = entry.getKey().equals(data.getGuild().displayedTitle());
            source.sendSuccess(() -> Component.literal(" ").append(name).append(Component.literal("  " + entry.getKey() + "  "
                    + Component.translatable("screen.amemaru.guild.day", entry.getValue().day()).getString() + (shown ? "  ★" : ""))
                    .withStyle(ChatFormatting.GRAY)), false);
        }
        return data.getGuild().titles().size();
    }

    private static int show(CommandSourceStack source, ServerPlayer player, ResourceLocation id) {
        if (!TitleService.setDisplayed(player, id)) {
            source.sendFailure(Component.translatable("command.amemaru.title.not_owned"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.title.shown", player.getGameProfile().getName(),
                id == null ? "-" : id.toString()), true);
        return 1;
    }

    private static int owners(CommandSourceStack source, ResourceLocation id) {
        List<GuildState.TitleLog> log = GuildState.get(source.getServer()).titleLog();
        int count = 0;
        for (GuildState.TitleLog entry : log) {
            if (entry.title().equals(id)) {
                count++;
                source.sendSuccess(() -> Component.translatable("command.amemaru.title.owner", entry.name(), entry.day()), false);
            }
        }
        int total = count;
        source.sendSuccess(() -> Component.translatable("command.amemaru.title.owners", id.toString(), total), false);
        return count;
    }

    private static int createUnique(CommandSourceStack source, String color, String scope, String name, String description) {
        String cleanName = TitleService.sanitize(name, 16);
        if (cleanName.isEmpty()) {
            source.sendFailure(Component.translatable("command.amemaru.title.bad_name"));
            return 0;
        }
        int rgb;
        if ("rainbow".equalsIgnoreCase(color)) {
            rgb = -1;
        } else {
            String hex = color.startsWith("#") ? color.substring(1) : color;
            try {
                if (hex.length() != 6) {
                    throw new NumberFormatException(hex);
                }
                rgb = Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                source.sendFailure(Component.translatable("command.amemaru.title.bad_color", color));
                return 0;
            }
        }
        boolean single;
        if ("single".equalsIgnoreCase(scope)) {
            single = true;
        } else if ("multi".equalsIgnoreCase(scope)) {
            single = false;
        } else {
            source.sendFailure(Component.translatable("command.amemaru.title.bad_scope", scope));
            return 0;
        }
        GuildState.CustomTitle custom = TitleService.createUnique(source.getServer(), cleanName, TitleService.sanitize(description, 80), rgb,
                single, source.getTextName());
        jp.amemaru.lostconnection.guild.GuildService.sendCatalogToAll(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.amemaru.title.unique_created", custom.name(), custom.id().toString(),
                Component.translatable(single ? "command.amemaru.title.single" : "command.amemaru.title.multi")), true);
        return 1;
    }

    private static int listUnique(CommandSourceStack source) {
        Map<ResourceLocation, GuildState.CustomTitle> customs = GuildState.get(source.getServer()).customs();
        source.sendSuccess(() -> Component.translatable("command.amemaru.title.unique_list", customs.size()), false);
        for (GuildState.CustomTitle custom : customs.values()) {
            int owners = GuildState.get(source.getServer()).ownersOf(custom.id());
            source.sendSuccess(() -> Component.literal(" " + custom.id() + "  【" + custom.name() + "】  "
                    + (custom.single() ? "single" : "multi") + "  " + owners + "  " + custom.createdBy()).withStyle(ChatFormatting.GRAY), false);
        }
        return customs.size();
    }

    private static int removeUnique(CommandSourceStack source, ResourceLocation id) {
        if (!GuildState.get(source.getServer()).removeCustom(id)) {
            source.sendFailure(Component.translatable("command.amemaru.title.unknown", id.toString()));
            return 0;
        }
        jp.amemaru.lostconnection.guild.GuildService.sendCatalogToAll(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.amemaru.title.unique_removed", id.toString()), true);
        return 1;
    }

    private static int listLimited(CommandSourceStack source) {
        GuildState state = GuildState.get(source.getServer());
        int count = 0;
        for (TitleDef def : TitleService.all(source.getServer()).values()) {
            if (def.kind() != TitleDef.Kind.LIMITED) {
                continue;
            }
            count++;
            List<String> names = new ArrayList<>();
            for (GuildState.Holder holder : state.holders(def.id())) {
                names.add(holder.name() + "(" + holder.day() + ")");
            }
            source.sendSuccess(() -> Component.literal(" ").append(TitleService.styled(def)).append(Component.literal("  "
                    + (names.isEmpty() ? "-" : String.join("・", names))).withStyle(ChatFormatting.GRAY)), false);
        }
        return count;
    }

    private static int resetLimited(CommandSourceStack source, ResourceLocation id) {
        GuildState.get(source.getServer()).clearHolders(id);
        jp.amemaru.lostconnection.guild.GuildService.sendCatalogToAll(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.amemaru.title.limited_reset", id.toString()), true);
        return 1;
    }

    // ================================================================ 古代記録

    public static LiteralArgumentBuilder<CommandSourceStack> recordCommand() {
        return Commands.literal("record")
                .then(Commands.literal("find")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("record", ResourceLocationArgument.id()).suggests(RECORDS)
                                        .executes(c -> {
                                            ResourceLocation id = ResourceLocationArgument.getId(c, "record");
                                            if (RecordDefs.get(id) == null) {
                                                c.getSource().sendFailure(Component.translatable("command.amemaru.record.unknown", id.toString()));
                                                return 0;
                                            }
                                            int count = 0;
                                            for (ServerPlayer player : EntityArgument.getPlayers(c, "targets")) {
                                                if (RecordService.find(player, id)) {
                                                    count++;
                                                }
                                            }
                                            int done = count;
                                            c.getSource().sendSuccess(() -> Component.translatable("command.amemaru.record.found", id.toString(), done), true);
                                            return count;
                                        }))))
                .then(Commands.literal("decipher")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> {
                                    int count = 0;
                                    for (ServerPlayer player : EntityArgument.getPlayers(c, "targets")) {
                                        count += RecordService.decipher(player);
                                    }
                                    int done = count;
                                    c.getSource().sendSuccess(() -> Component.translatable("command.amemaru.record.deciphered", done), true);
                                    return count;
                                })))
                .then(Commands.literal("book")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> {
                                    for (ServerPlayer player : EntityArgument.getPlayers(c, "targets")) {
                                        RecordService.giveBook(player);
                                    }
                                    c.getSource().sendSuccess(() -> Component.translatable("command.amemaru.record.book"), true);
                                    return 1;
                                })))
                .then(Commands.literal("forget")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.literal("all")
                                        .executes(c -> forgetRecords(c.getSource(), EntityArgument.getPlayers(c, "targets"), null)))
                                .then(Commands.argument("record", ResourceLocationArgument.id()).suggests(RECORDS)
                                        .executes(c -> forgetRecords(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                ResourceLocationArgument.getId(c, "record"))))));
    }

    private static int forgetRecords(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation id) {
        int count = 0;
        for (ServerPlayer player : targets) {
            PlayerData data = PlayerDataCapability.get(player).orElse(null);
            if (data == null) {
                continue;
            }
            List<ResourceLocation> ids = id == null ? List.copyOf(data.getGuild().records().keySet()) : List.of(id);
            for (ResourceLocation one : ids) {
                if (data.getGuild().forgetRecord(one)) {
                    count++;
                }
            }
            GuildService.syncSelf(player, data);
        }
        int done = count;
        source.sendSuccess(() -> Component.translatable("command.amemaru.record.forgot", done), true);
        return count;
    }

    // ================================================================ 親密度

    public static LiteralArgumentBuilder<CommandSourceStack> bondCommand() {
        return Commands.literal("bond")
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("npc", ResourceLocationArgument.id()).suggests(NPCS)
                                        .then(Commands.argument("points", IntegerArgumentType.integer(0, BondService.POINTS_CAP))
                                                .executes(c -> bond(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                        ResourceLocationArgument.getId(c, "npc"), IntegerArgumentType.getInteger(c, "points"), true))))))
                .then(Commands.literal("add")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("npc", ResourceLocationArgument.id()).suggests(NPCS)
                                        .then(Commands.argument("points", IntegerArgumentType.integer(-BondService.POINTS_CAP, BondService.POINTS_CAP))
                                                .executes(c -> bond(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                        ResourceLocationArgument.getId(c, "npc"), IntegerArgumentType.getInteger(c, "points"), false))))))
                .then(Commands.literal("show")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> showBonds(c.getSource(), EntityArgument.getPlayer(c, "target")))));
    }

    private static int bond(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation npc, int points, boolean set) {
        if (!BondService.hasBond(npc)) {
            source.sendFailure(Component.translatable("command.amemaru.bond.unknown", npc.toString()));
            return 0;
        }
        for (ServerPlayer player : targets) {
            if (set) {
                BondService.set(player, npc, points);
            } else {
                BondService.add(player, npc, points, "admin");
            }
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.bond.changed", npc.toString(), targets.size()), true);
        return targets.size();
    }

    private static int showBonds(CommandSourceStack source, ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return 0;
        }
        int count = 0;
        for (NpcDef def : NpcDefs.all().values()) {
            if (def.bond() == null) {
                continue;
            }
            count++;
            int points = BondService.points(data, def.id());
            int stage = BondService.stage(data, def.id());
            source.sendSuccess(() -> Component.literal(" " + def.name() + "  " + points + "pt  ").append(BondService.stageName(stage))
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return count;
    }

    // ================================================================ クエスト

    public static LiteralArgumentBuilder<CommandSourceStack> questCommand() {
        return Commands.literal("quest")
                .then(Commands.literal("start")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("quest", ResourceLocationArgument.id()).suggests(QUESTS)
                                        .executes(c -> questStart(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                ResourceLocationArgument.getId(c, "quest"))))))
                .then(Commands.literal("complete")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("quest", ResourceLocationArgument.id()).suggests(QUESTS)
                                        .executes(c -> {
                                            ResourceLocation id = ResourceLocationArgument.getId(c, "quest");
                                            int count = 0;
                                            for (ServerPlayer player : EntityArgument.getPlayers(c, "targets")) {
                                                if (QuestService.forceComplete(player, id)) {
                                                    count++;
                                                }
                                            }
                                            int done = count;
                                            c.getSource().sendSuccess(() -> Component.translatable("command.amemaru.quest.completed", id.toString(), done),
                                                    true);
                                            return count;
                                        }))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.literal("all")
                                        .executes(c -> questReset(c.getSource(), EntityArgument.getPlayers(c, "targets"), null)))
                                .then(Commands.argument("quest", ResourceLocationArgument.id()).suggests(QUESTS)
                                        .executes(c -> questReset(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                ResourceLocationArgument.getId(c, "quest"))))))
                .then(Commands.literal("list")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> questList(c.getSource(), EntityArgument.getPlayer(c, "target")))))
                .then(Commands.literal("open")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("tab", StringArgumentType.word()).suggests(TABS)
                                        .executes(c -> {
                                            for (ServerPlayer player : EntityArgument.getPlayers(c, "targets")) {
                                                GuildService.openGuild(player, StringArgumentType.getString(c, "tab"));
                                            }
                                            return 1;
                                        }))));
    }

    private static int questStart(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation id) {
        QuestDef def = QuestDefs.get(id);
        if (def == null) {
            source.sendFailure(Component.translatable("command.amemaru.quest.unknown", id.toString()));
            return 0;
        }
        int day = ProgressionData.get(source.getServer()).day();
        int count = 0;
        for (ServerPlayer player : targets) {
            PlayerData data = PlayerDataCapability.get(player).orElse(null);
            if (data != null && data.getGuild().startQuest(id, def.objectives().size(), day)) {
                data.getGuild().setQuestTracked(id, true);
                GuildService.syncSelf(player, data);
                count++;
            }
        }
        int done = count;
        source.sendSuccess(() -> Component.translatable("command.amemaru.quest.started", def.name(), done), true);
        return count;
    }

    private static int questReset(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation id) {
        int count = 0;
        for (ServerPlayer player : targets) {
            PlayerData data = PlayerDataCapability.get(player).orElse(null);
            if (data == null) {
                continue;
            }
            List<ResourceLocation> ids = new ArrayList<>();
            if (id == null) {
                ids.addAll(data.getGuild().quests().keySet());
                ids.addAll(data.getGuild().completedQuests().keySet());
            } else {
                ids.add(id);
            }
            for (ResourceLocation one : ids) {
                boolean changed = data.getGuild().dropQuest(one);
                changed |= data.getGuild().forgetCompleted(one);
                if (changed) {
                    count++;
                }
            }
            if (id == null) {
                data.getGuild().resetDaily(-1, List.of());
            }
            GuildService.syncSelf(player, data);
        }
        int done = count;
        source.sendSuccess(() -> Component.translatable("command.amemaru.quest.reset", done), true);
        return count;
    }

    private static int questList(CommandSourceStack source, ServerPlayer player) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return 0;
        }
        for (Map.Entry<ResourceLocation, GuildData.QuestProgress> entry : data.getGuild().quests().entrySet()) {
            QuestDef def = QuestDefs.get(entry.getKey());
            StringBuilder counts = new StringBuilder();
            for (int i = 0; i < entry.getValue().size(); i++) {
                counts.append(i == 0 ? "" : ",").append(def == null ? entry.getValue().count(i)
                        : QuestService.objectiveValue(player, def, i, entry.getValue()));
            }
            source.sendSuccess(() -> Component.literal(" ▶ " + entry.getKey() + " [" + counts + "]").withStyle(ChatFormatting.GRAY), false);
        }
        for (Map.Entry<ResourceLocation, GuildData.Completed> entry : data.getGuild().completedQuests().entrySet()) {
            source.sendSuccess(() -> Component.literal(" ✓ " + entry.getKey() + " ×" + entry.getValue().times() + " ("
                    + entry.getValue().lastDay() + ")").withStyle(ChatFormatting.DARK_GRAY), false);
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.quest.daily", data.getGuild().dailyDone(),
                String.valueOf(data.getGuild().dailyOffer())), false);
        return data.getGuild().quests().size();
    }

    // ================================================================ 露店の区画の寄付

    public static LiteralArgumentBuilder<CommandSourceStack> donationCommand() {
        return Commands.literal("donation")
                .executes(c -> donationStatus(c.getSource()))
                .then(Commands.literal("add")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1, 100_000_000L))
                                .executes(c -> {
                                    GuildState state = GuildState.get(c.getSource().getServer());
                                    state.addStallDonation(new java.util.UUID(0L, 0L), "admin", LongArgumentType.getLong(c, "amount"));
                                    if (state.stallDonated() >= StallDonation.goal() && state.stallReachedDay() <= 0) {
                                        state.setStallReachedDay(ProgressionData.get(c.getSource().getServer()).day());
                                    }
                                    return donationStatus(c.getSource());
                                })))
                .then(Commands.literal("reset")
                        .executes(c -> {
                            GuildState.get(c.getSource().getServer()).resetStallDonation();
                            MarketData.get(c.getSource().getServer()).setExpanded(false);
                            return donationStatus(c.getSource());
                        }))
                .then(Commands.literal("expand")
                        .then(Commands.argument("on", BoolArgumentType.bool())
                                .executes(c -> {
                                    MarketData.get(c.getSource().getServer()).setExpanded(BoolArgumentType.getBool(c, "on"));
                                    jp.amemaru.lostconnection.network.ModNetwork.sendToAll(
                                            jp.amemaru.lostconnection.network.s2c.MarketStallsS2C.current(c.getSource().getServer()));
                                    return donationStatus(c.getSource());
                                })));
    }

    private static int donationStatus(CommandSourceStack source) {
        GuildState state = GuildState.get(source.getServer());
        boolean expanded = MarketData.get(source.getServer()).expanded();
        source.sendSuccess(() -> Component.translatable("command.amemaru.donation.status", state.stallDonated(), StallDonation.goal(),
                state.stallDonors().size(), state.stallReachedDay(), String.valueOf(expanded)), false);
        return (int) Math.min(Integer.MAX_VALUE, state.stallDonated());
    }

    // ================================================================ 冒険日誌

    public static LiteralArgumentBuilder<CommandSourceStack> chronicleCommand() {
        return Commands.literal("chronicle")
                .executes(c -> {
                    List<GuildState.ChronicleEntry> entries = GuildState.get(c.getSource().getServer()).chronicle();
                    for (int i = entries.size() - 1; i >= Math.max(0, entries.size() - 10); i--) {
                        GuildState.ChronicleEntry entry = entries.get(i);
                        Component text = Component.translatable(entry.key(), entry.args().toArray());
                        c.getSource().sendSuccess(() -> Component.translatable("screen.amemaru.guild.day", entry.day()).append(" ").append(text)
                                .withStyle(ChatFormatting.GRAY), false);
                    }
                    return entries.size();
                });
    }
}

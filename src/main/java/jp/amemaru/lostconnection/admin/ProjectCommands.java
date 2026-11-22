package jp.amemaru.lostconnection.admin;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import jp.amemaru.lostconnection.town.ProjectDef;
import jp.amemaru.lostconnection.town.ProjectDefs;
import jp.amemaru.lostconnection.town.ProjectService;
import jp.amemaru.lostconnection.town.TownData;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.Locale;

/**
 * 管理者のコマンド：寄付と共同事業（Phase 7 その4）。
 *
 * <ul>
 *   <li>{@code project}：7つの共同事業の状態（寄付・目標・輝き段階・受付の日）</li>
 *   <li>{@code project open <人> [共同事業]}：寄付台の画面を開く（距離を見ない）</li>
 *   <li>{@code project add <共同事業> <額>}：寄付があったことにする（管理者の寄付。ノットは減らない）</li>
 *   <li>{@code project complete <共同事業>}：完成にする（次の朝に施設が変わる）。{@code project build <共同事業>}：今すぐ施設を変える</li>
 *   <li>{@code project reset <共同事業>}：初めに戻す（寄付と物語の印を消す。重ねた形は、街を建て直すと消える）</li>
 * </ul>
 */
public final class ProjectCommands {
    private static final SuggestionProvider<CommandSourceStack> PROJECTS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(ProjectDefs.all().keySet(), builder);

    private ProjectCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> projectCommand() {
        return Commands.literal("project")
                .executes(c -> list(c.getSource()))
                .then(Commands.literal("open")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> {
                                    ProjectService.open(EntityArgument.getPlayer(c, "target"), "", true);
                                    return 1;
                                })
                                .then(Commands.argument("project", ResourceLocationArgument.id()).suggests(PROJECTS)
                                        .executes(c -> {
                                            ProjectService.open(EntityArgument.getPlayer(c, "target"),
                                                    ResourceLocationArgument.getId(c, "project").toString(), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("add")
                        .then(Commands.argument("project", ResourceLocationArgument.id()).suggests(PROJECTS)
                                .then(Commands.argument("amount", LongArgumentType.longArg(1, 1_000_000_000_000L))
                                        .executes(c -> add(c.getSource(), ResourceLocationArgument.getId(c, "project"),
                                                LongArgumentType.getLong(c, "amount"))))))
                .then(Commands.literal("complete")
                        .then(Commands.argument("project", ResourceLocationArgument.id()).suggests(PROJECTS)
                                .executes(c -> complete(c.getSource(), ResourceLocationArgument.getId(c, "project")))))
                .then(Commands.literal("build")
                        .then(Commands.argument("project", ResourceLocationArgument.id()).suggests(PROJECTS)
                                .executes(c -> build(c.getSource(), ResourceLocationArgument.getId(c, "project")))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("project", ResourceLocationArgument.id()).suggests(PROJECTS)
                                .executes(c -> reset(c.getSource(), ResourceLocationArgument.getId(c, "project")))));
    }

    private static ProjectDef def(CommandSourceStack source, ResourceLocation id) {
        ProjectDef def = ProjectDefs.get(id);
        if (def == null) {
            source.sendFailure(Component.translatable("command.amemaru.project.unknown", id.toString()));
        }
        return def;
    }

    private static int list(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        TownData town = TownData.get(server);
        for (ProjectDef def : ProjectDefs.ordered()) {
            TownData.Project p = town.project(def.id());
            source.sendSuccess(() -> Component.translatable("command.amemaru.project.line", def.order(), def.shortName(),
                    Component.translatable("screen.amemaru.project.state." + ProjectService.status(server, def)),
                    String.format(Locale.ROOT, "%,d", p.donated()), String.format(Locale.ROOT, "%,d", ProjectService.goal(server, def)),
                    def.stage(), def.startDay(), p.plate().size()), false);
        }
        return ProjectDefs.all().size();
    }

    private static int add(CommandSourceStack source, ResourceLocation id, long amount) {
        ProjectDef def = def(source, id);
        if (def == null) {
            return 0;
        }
        String name = source.getEntity() == null ? source.getTextName() : source.getEntity().getName().getString();
        ProjectService.adminDonate(source.getServer(), def, source.getEntity() == null ? Util.NIL_UUID : source.getEntity().getUUID(), name, amount);
        source.sendSuccess(() -> Component.translatable("command.amemaru.project.added", def.shortName(), String.format(Locale.ROOT, "%,d", amount),
                Component.translatable("screen.amemaru.project.state." + ProjectService.status(source.getServer(), def))), true);
        return 1;
    }

    private static int complete(CommandSourceStack source, ResourceLocation id) {
        ProjectDef def = def(source, id);
        if (def == null) {
            return 0;
        }
        MinecraftServer server = source.getServer();
        TownData.Project p = TownData.get(server).project(def.id());
        if (!p.completed()) {
            long need = ProjectService.goal(server, def) - p.donated();
            if (need > 0) {
                ProjectService.adminDonate(server, def, Util.NIL_UUID, "admin", need);
            }
            if (!TownData.get(server).project(def.id()).completed()) {
                // 輝き段階が足りなくても、管理者は完成にできる
                ProjectService.forceComplete(server, def);
            }
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.project.completed", def.shortName()), true);
        return 1;
    }

    private static int build(CommandSourceStack source, ResourceLocation id) {
        ProjectDef def = def(source, id);
        if (def == null) {
            return 0;
        }
        if (!ProjectService.build(source.getServer(), def)) {
            source.sendFailure(Component.translatable("command.amemaru.project.already", def.shortName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.amemaru.project.built", def.shortName()), true);
        return 1;
    }

    private static int reset(CommandSourceStack source, ResourceLocation id) {
        ProjectDef def = def(source, id);
        if (def == null) {
            return 0;
        }
        ProjectService.reset(source.getServer(), def);
        source.sendSuccess(() -> Component.translatable("command.amemaru.project.reset", def.shortName()), true);
        return 1;
    }
}

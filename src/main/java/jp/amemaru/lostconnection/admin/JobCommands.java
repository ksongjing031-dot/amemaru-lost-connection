package jp.amemaru.lostconnection.admin;

import jp.amemaru.lostconnection.AmemaruMod;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.entity.TrainingDummy;
import jp.amemaru.lostconnection.job.GaugeService;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.job.SkillTreeService;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 2の管理者のコマンド：職業・スキル・進行日・訓練人形（{@code /amemaru admin …}）。
 *
 * <ul>
 *   <li>{@code job set <対象> <職業>}・{@code job clear <対象>}・{@code job promote <対象> [条件を無視]}・{@code job demote <対象>}・
 *       {@code job kit <対象>}・{@code job info <対象>}</li>
 *   <li>{@code skill learn <対象> <スキル> <段階>}・{@code skill reset <対象>}・{@code skill cooldown <対象>}・
 *       {@code skill gauge <対象> <値>}</li>
 *   <li>{@code day set <日>}・{@code day next}・{@code day info}（進行日。Phase 5で階層の解放などを足す）</li>
 *   <li>{@code dummy [防御]}：目の前に訓練人形を置く</li>
 * </ul>
 */
final class JobCommands {
    private static final SuggestionProvider<CommandSourceStack> JOBS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(SkillRegistry.get().jobs().stream().map(JobDef::id), builder);
    private static final SuggestionProvider<CommandSourceStack> SKILLS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(SkillRegistry.get().skills().stream().map(SkillDef::id), builder);

    private JobCommands() {
    }

    /**
     * 名前空間を省いて打った名前（{@code knight} → {@code minecraft:knight} と読まれる）を、このModの名前
     * （{@code amemaru:knight}）として探し直す。
     *
     * @param found そのままの名前で見つかったか
     */
    private static ResourceLocation withDefaultNamespace(ResourceLocation id, boolean found) {
        if (found || !"minecraft".equals(id.getNamespace())) {
            return id;
        }
        return AmemaruMod.id(id.getPath());
    }

    // ---------------------------------------------------------------- job

    static LiteralArgumentBuilder<CommandSourceStack> jobCommand() {
        return Commands.literal("job")
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("job", ResourceLocationArgument.id()).suggests(JOBS)
                                        .executes(ctx -> setJob(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                ResourceLocationArgument.getId(ctx, "job"))))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> clearJob(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("promote")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> promote(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), false))
                                .then(Commands.argument("force", BoolArgumentType.bool())
                                        .executes(ctx -> promote(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                BoolArgumentType.getBool(ctx, "force"))))))
                .then(Commands.literal("demote")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> demote(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("kit")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> kit(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("info")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> info(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))));
    }

    private static int setJob(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation id) {
        JobDef job = SkillRegistry.get().job(withDefaultNamespace(id, SkillRegistry.get().job(id) != null));
        if (job == null || job.kind() != JobDef.Kind.COMBAT) {
            source.sendFailure(Component.translatable("commands.amemaru.job.unknown", id.toString()));
            return 0;
        }
        int count = 0;
        for (ServerPlayer player : targets) {
            Optional<PlayerData> data = AdminCommands.data(source, player);
            if (data.isEmpty()) {
                continue;
            }
            JobService.setJobForced(player, data.get(), job);
            AdminCommands.log(source, "job set " + player.getGameProfile().getName() + " " + id);
            source.sendSuccess(() -> Component.translatable("commands.amemaru.job.set", player.getDisplayName(), job.name()), true);
            count++;
        }
        return count;
    }

    private static int clearJob(CommandSourceStack source, Collection<ServerPlayer> targets) {
        int count = 0;
        for (ServerPlayer player : targets) {
            Optional<PlayerData> data = AdminCommands.data(source, player);
            if (data.isEmpty()) {
                continue;
            }
            JobService.clearJob(player, data.get());
            AdminCommands.log(source, "job clear " + player.getGameProfile().getName());
            source.sendSuccess(() -> Component.translatable("commands.amemaru.job.clear", player.getDisplayName()), true);
            count++;
        }
        return count;
    }

    private static int promote(CommandSourceStack source, Collection<ServerPlayer> targets, boolean force) {
        int count = 0;
        for (ServerPlayer player : targets) {
            Optional<PlayerData> data = AdminCommands.data(source, player);
            if (data.isEmpty()) {
                continue;
            }
            if (JobService.promote(player, data.get(), force)) {
                AdminCommands.log(source, "job promote " + player.getGameProfile().getName() + (force ? " (force)" : ""));
                source.sendSuccess(() -> Component.translatable("commands.amemaru.job.promote", player.getDisplayName()), true);
                count++;
            } else {
                source.sendFailure(Component.translatable("commands.amemaru.job.promote_failed", player.getDisplayName()));
            }
        }
        return count;
    }

    private static int demote(CommandSourceStack source, Collection<ServerPlayer> targets) {
        int count = 0;
        for (ServerPlayer player : targets) {
            Optional<PlayerData> data = AdminCommands.data(source, player);
            if (data.isEmpty()) {
                continue;
            }
            JobService.demote(player, data.get());
            AdminCommands.log(source, "job demote " + player.getGameProfile().getName());
            source.sendSuccess(() -> Component.translatable("commands.amemaru.job.demote", player.getDisplayName()), true);
            count++;
        }
        return count;
    }

    private static int kit(CommandSourceStack source, Collection<ServerPlayer> targets) {
        int count = 0;
        for (ServerPlayer player : targets) {
            Optional<PlayerData> data = AdminCommands.data(source, player);
            JobDef job = data.map(d -> SkillRegistry.get().job(d.getCombatJob())).orElse(null);
            if (job == null) {
                source.sendFailure(Component.translatable("commands.amemaru.job.none", player.getDisplayName()));
                continue;
            }
            JobService.giveStarterKit(player, job);
            AdminCommands.log(source, "job kit " + player.getGameProfile().getName() + " " + job.id());
            source.sendSuccess(() -> Component.translatable("commands.amemaru.job.kit", player.getDisplayName(), job.name()), true);
            count++;
        }
        return count;
    }

    private static int info(CommandSourceStack source, ServerPlayer player) {
        Optional<PlayerData> found = AdminCommands.data(source, player);
        if (found.isEmpty()) {
            return 0;
        }
        PlayerData data = found.get();
        SkillRegistry.Snapshot snapshot = SkillRegistry.get();
        JobDef job = snapshot.job(data.getCombatJob());
        String upper = job != null && job.upper() != null && data.isPromoted() ? job.upper().name() : "―";
        source.sendSuccess(() -> Component.translatable("commands.amemaru.job.info.header", player.getDisplayName(),
                job == null ? "―" : job.name(), upper, data.getJobChanges()).withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("commands.amemaru.job.info.points",
                SkillTreeService.availablePoints(data, jp.amemaru.lostconnection.player.GrowthRules.current()),
                SkillTreeService.spentPoints(data), (long) Math.floor(data.getGauge()), GaugeService.max(data)), false);
        StringBuilder skills = new StringBuilder();
        for (Map.Entry<ResourceLocation, Integer> entry : data.getLearnedSkills().entrySet()) {
            SkillDef skill = snapshot.skill(entry.getKey());
            if (skills.length() > 0) {
                skills.append("、");
            }
            skills.append(skill == null ? entry.getKey().toString() : skill.name()).append(" ").append(entry.getValue());
        }
        source.sendSuccess(() -> Component.translatable("commands.amemaru.job.info.skills",
                skills.length() == 0 ? "―" : skills.toString()), false);
        StringBuilder loadout = new StringBuilder();
        for (int i = 0; i < PlayerData.LOADOUT_SIZE; i++) {
            SkillDef skill = snapshot.skill(data.getLoadout(i));
            if (i > 0) {
                loadout.append(" / ");
            }
            loadout.append(i + 1).append(":").append(skill == null ? "―" : skill.name());
        }
        source.sendSuccess(() -> Component.translatable("commands.amemaru.job.info.loadout", loadout.toString()), false);
        return 1;
    }

    // ---------------------------------------------------------------- skill

    static LiteralArgumentBuilder<CommandSourceStack> skillCommand() {
        return Commands.literal("skill")
                .then(Commands.literal("learn")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILLS)
                                        .then(Commands.argument("rank", IntegerArgumentType.integer(0, 5))
                                                .executes(ctx -> learn(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                        ResourceLocationArgument.getId(ctx, "skill"),
                                                        IntegerArgumentType.getInteger(ctx, "rank")))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> reset(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("cooldown")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> clearCooldowns(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("gauge")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 10000))
                                        .executes(ctx -> gauge(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                IntegerArgumentType.getInteger(ctx, "value"))))));
    }

    private static int learn(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation id, int rank) {
        SkillDef skill = SkillRegistry.get().skill(withDefaultNamespace(id, SkillRegistry.get().skill(id) != null));
        if (skill == null) {
            source.sendFailure(Component.translatable("commands.amemaru.skill.unknown", id.toString()));
            return 0;
        }
        int count = 0;
        for (ServerPlayer player : targets) {
            Optional<PlayerData> data = AdminCommands.data(source, player);
            if (data.isEmpty()) {
                continue;
            }
            if (!skill.job().equals(data.get().getCombatJob())) {
                source.sendFailure(Component.translatable("commands.amemaru.skill.other_job", player.getDisplayName(), skill.name()));
                continue;
            }
            SkillTreeService.forceRank(player, data.get(), skill, rank);
            AdminCommands.log(source, "skill learn " + player.getGameProfile().getName() + " " + id + " " + rank);
            source.sendSuccess(() -> Component.translatable("commands.amemaru.skill.learn", player.getDisplayName(), skill.name(), rank), true);
            count++;
        }
        return count;
    }

    private static int reset(CommandSourceStack source, Collection<ServerPlayer> targets) {
        int count = 0;
        for (ServerPlayer player : targets) {
            Optional<PlayerData> data = AdminCommands.data(source, player);
            if (data.isEmpty()) {
                continue;
            }
            SkillTreeService.resetSkills(player, data.get());
            AdminCommands.log(source, "skill reset " + player.getGameProfile().getName());
            source.sendSuccess(() -> Component.translatable("commands.amemaru.skill.reset", player.getDisplayName()), true);
            count++;
        }
        return count;
    }

    private static int clearCooldowns(CommandSourceStack source, Collection<ServerPlayer> targets) {
        int count = 0;
        for (ServerPlayer player : targets) {
            Optional<PlayerData> data = AdminCommands.data(source, player);
            if (data.isEmpty()) {
                continue;
            }
            data.get().clearCooldowns();
            AdminCommands.log(source, "skill cooldown " + player.getGameProfile().getName());
            source.sendSuccess(() -> Component.translatable("commands.amemaru.skill.cooldown", player.getDisplayName()), true);
            count++;
        }
        return count;
    }

    private static int gauge(CommandSourceStack source, Collection<ServerPlayer> targets, int value) {
        int count = 0;
        for (ServerPlayer player : targets) {
            Optional<PlayerData> data = AdminCommands.data(source, player);
            if (data.isEmpty()) {
                continue;
            }
            int max = GaugeService.max(data.get());
            data.get().setGauge(Math.min(max, value));
            // ゲージは戦闘が終わると0に戻るため、確かめやすいよう「戦闘中」にしておく
            data.get().markCombat(player.level().getGameTime());
            AdminCommands.log(source, "skill gauge " + player.getGameProfile().getName() + " " + value);
            source.sendSuccess(() -> Component.translatable("commands.amemaru.skill.gauge", player.getDisplayName(),
                    Math.min(max, value), max), true);
            count++;
        }
        return count;
    }

    // ---------------------------------------------------------------- day

    static LiteralArgumentBuilder<CommandSourceStack> dayCommand() {
        return Commands.literal("day")
                .then(Commands.literal("set")
                        .then(Commands.argument("day", IntegerArgumentType.integer(1, ProgressionData.MAX_DAY))
                                .executes(ctx -> setDay(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "day")))))
                .then(Commands.literal("next")
                        .executes(ctx -> setDay(ctx.getSource(), ProgressionData.currentDay(ctx.getSource().getServer()) + 1))
                        // 予告をつけて、時間をおいて進める（Phase 0-B 9章。Phase 5）
                        .then(Commands.literal("in")
                                .then(Commands.argument("minutes", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.1, 1440.0))
                                        .executes(ctx -> scheduleNextDay(ctx.getSource(),
                                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "minutes"))))))
                .then(Commands.literal("info")
                        .executes(ctx -> {
                            int day = ProgressionData.currentDay(ctx.getSource().getServer());
                            int chapter = ProgressionData.currentChapter(ctx.getSource().getServer());
                            int cap = ProgressionData.currentLevelCap(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.amemaru.day.info_chapter", day,
                                    chapter, cap), false);
                            return day;
                        }));
    }

    private static int setDay(CommandSourceStack source, int day) {
        // その日の予定（階層の解放・知らせ）もまとめて行う（Phase 5）
        int result = jp.amemaru.lostconnection.progression.ProgressionService.setDay(source.getServer(), day);
        AdminCommands.log(source, "day set " + result);
        source.sendSuccess(() -> Component.translatable("commands.amemaru.day.set", result), true);
        return result;
    }

    private static int scheduleNextDay(CommandSourceStack source, double minutes) {
        int next = ProgressionData.currentDay(source.getServer()) + 1;
        jp.amemaru.lostconnection.progression.ProgressionData.Scheduled s = jp.amemaru.lostconnection.progression.ProgressionService.schedule(
                source.getServer(), jp.amemaru.lostconnection.progression.ProgressionData.ScheduleKind.DAY, next, "", minutes);
        AdminCommands.log(source, "day next in " + minutes + " (#" + s.id() + ")");
        source.sendSuccess(() -> Component.translatable("commands.amemaru.schedule.added", s.id()), true);
        return s.id();
    }

    // ---------------------------------------------------------------- dummy

    static LiteralArgumentBuilder<CommandSourceStack> dummyCommand() {
        return Commands.literal("dummy")
                .executes(ctx -> spawnDummy(ctx.getSource(), 0))
                .then(Commands.argument("defense", IntegerArgumentType.integer(0, 10000))
                        .executes(ctx -> spawnDummy(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "defense"))));
    }

    private static int spawnDummy(CommandSourceStack source, int defense) {
        ServerLevel level = source.getLevel();
        TrainingDummy dummy = ModEntities.TRAINING_DUMMY.get().create(level);
        if (dummy == null) {
            return 0;
        }
        Vec3 at = source.getPosition();
        float yaw = 0.0F;
        if (source.getEntity() != null) {
            Vec3 look = source.getEntity().getLookAngle();
            Vec3 flat = new Vec3(look.x, 0.0, look.z);
            if (flat.lengthSqr() > 1.0E-4) {
                flat = flat.normalize();
                at = at.add(flat.scale(3.0));
            }
            yaw = Mth.wrapDegrees(source.getEntity().getYRot() + 180.0F);
        }
        dummy.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        dummy.setYHeadRot(yaw);
        dummy.setYBodyRot(yaw);
        dummy.setDummyDefense(defense);
        level.addFreshEntity(dummy);
        AdminCommands.log(source, "dummy " + defense + " at " + dummy.blockPosition().toShortString());
        source.sendSuccess(() -> Component.translatable("commands.amemaru.dummy", defense), true);
        return 1;
    }
}

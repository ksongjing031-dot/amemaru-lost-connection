package jp.amemaru.lostconnection.admin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.combat.StatusService;
import jp.amemaru.lostconnection.player.GrowthDef;
import jp.amemaru.lostconnection.player.GrowthRules;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.player.VitalsService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 管理者のコマンド {@code /amemaru admin …}（権限レベル2以上。Phase 0-B 10章）。
 *
 * <p>Phase 1で {@code level}（レベル・経験値・振り分けの操作と確認）と、動作確認用の {@code debug vitals} を作り、
 * Phase 2で {@code job}（職業）・{@code skill}（スキル）・{@code day}（進行日）・{@code dummy}（訓練人形）と、
 * 状態の見た目の確かめ用の {@code debug status}（凍え・凍結・感電・星痕・防御崩しをかける）を足した。
 * ほかのコマンドは、それを使う仕組みと同じPhaseで足す。実行した人・対象・内容は、サーバーのログに残す。</p>
 *
 * <p>確かめ用の {@code city}（コネリアの街の11施設をまとめて建てて、見て回る）は、11月21日に足した（{@link CityCommands}）。</p>
 *
 * <p>Phase 4で {@code down}（ダウン・起こす・教会へ）・{@code church}（教会での復活の場所）・{@code money}（所持金）・
 * {@code party list} を足した（{@link DownCommands}）。だれでも使える {@code /amemaru party …} もここで登録する。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID)
public final class AdminCommands {
    private static final List<StatType> MAIN_STATS = List.of(
            StatType.ATTACK, StatType.MAGIC, StatType.DEFENSE, StatType.SPEED, StatType.CRIT);
    private static final List<StatType> SUB_STATS = List.of(
            StatType.CRIT_DAMAGE, StatType.HEAL_POWER, StatType.COOLDOWN_REDUCTION, StatType.LOST_RESIST);

    private AdminCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("amemaru")
                // だれでも使える：パーティ（Phase 4）
                .then(jp.amemaru.lostconnection.party.PartyCommands.partyCommand())
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(levelCommand())
                        .then(JobCommands.jobCommand())
                        .then(JobCommands.skillCommand())
                        .then(JobCommands.dayCommand())
                        .then(JobCommands.dummyCommand())
                        .then(GearCommands.gearCommand())
                        .then(GearCommands.accessoryCommand())
                        .then(GearCommands.enemyCommand())
                        .then(GearCommands.productionCommand())
                        .then(GearCommands.fruitCommand())
                        .then(GearCommands.adventureCommand())
                        .then(CityCommands.cityCommand())
                        .then(DownCommands.downCommand())
                        .then(DownCommands.churchCommand())
                        .then(DownCommands.moneyCommand())
                        .then(DownCommands.partyCommand())
                        // Phase 5：塔の階・進行・物語・NPC
                        .then(StoryCommands.mapCommand())
                        .then(StoryCommands.floorCommand())
                        .then(StoryCommands.bossCommand())
                        .then(StoryCommands.blessingCommand())
                        .then(StoryCommands.announceCommand())
                        .then(StoryCommands.scheduleCommand())
                        .then(StoryCommands.storyCommand())
                        .then(StoryCommands.flagCommand())
                        .then(StoryCommands.npcCommand())
                        .then(StoryCommands.pillarsCommand())
                        .then(StoryCommands.chestsCommand())
                        .then(debugCommand())));
    }

    // ---------------------------------------------------------------- level

    private static LiteralArgumentBuilder<CommandSourceStack> levelCommand() {
        return Commands.literal("level")
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 200))
                                        .executes(ctx -> setLevel(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"),
                                                IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("add")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("levels", IntegerArgumentType.integer(1, 200))
                                        .executes(ctx -> addLevels(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"),
                                                IntegerArgumentType.getInteger(ctx, "levels"))))))
                .then(Commands.literal("exp")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> addExp(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"),
                                                IntegerArgumentType.getInteger(ctx, "amount"))))))
                .then(Commands.literal("resetstats")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> resetStats(ctx.getSource(),
                                        EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("info")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> info(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))));
    }

    private static int setLevel(CommandSourceStack source, Collection<ServerPlayer> targets, int level) {
        int count = 0;
        for (ServerPlayer player : targets) {
            Optional<PlayerData> data = data(source, player);
            if (data.isEmpty()) {
                continue;
            }
            LevelService.setLevel(player, data.get(), level);
            int result = data.get().getLevel();
            log(source, "level set " + player.getGameProfile().getName() + " " + result);
            source.sendSuccess(() -> Component.translatable("commands.amemaru.level.set", player.getDisplayName(), result), true);
            count++;
        }
        return count;
    }

    private static int addLevels(CommandSourceStack source, Collection<ServerPlayer> targets, int levels) {
        int count = 0;
        for (ServerPlayer player : targets) {
            Optional<PlayerData> data = data(source, player);
            if (data.isEmpty()) {
                continue;
            }
            LevelService.setLevel(player, data.get(), data.get().getLevel() + levels);
            int result = data.get().getLevel();
            log(source, "level add " + player.getGameProfile().getName() + " +" + levels + " -> " + result);
            source.sendSuccess(() -> Component.translatable("commands.amemaru.level.add", player.getDisplayName(), levels, result), true);
            count++;
        }
        return count;
    }

    private static int addExp(CommandSourceStack source, Collection<ServerPlayer> targets, int amount) {
        int count = 0;
        for (ServerPlayer player : targets) {
            Optional<PlayerData> data = data(source, player);
            if (data.isEmpty()) {
                continue;
            }
            LevelService.addExp(player, data.get(), amount, LevelService.ExpSource.ADMIN);
            int result = data.get().getLevel();
            log(source, "level exp " + player.getGameProfile().getName() + " +" + amount + " -> Lv" + result);
            source.sendSuccess(() -> Component.translatable("commands.amemaru.level.exp", player.getDisplayName(), amount, result), true);
            count++;
        }
        return count;
    }

    private static int resetStats(CommandSourceStack source, Collection<ServerPlayer> targets) {
        int count = 0;
        for (ServerPlayer player : targets) {
            Optional<PlayerData> data = data(source, player);
            if (data.isEmpty()) {
                continue;
            }
            int refunded = LevelService.resetAllocation(player, data.get());
            log(source, "level resetstats " + player.getGameProfile().getName() + " (" + refunded + ")");
            source.sendSuccess(() -> Component.translatable("commands.amemaru.level.resetstats", player.getDisplayName(), refunded), true);
            count++;
        }
        return count;
    }

    private static int info(CommandSourceStack source, ServerPlayer player) {
        Optional<PlayerData> found = data(source, player);
        if (found.isEmpty()) {
            return 0;
        }
        PlayerData data = found.get();
        if (!data.isStatsReady()) {
            LevelService.recompute(player, data);
        }
        GrowthDef growth = GrowthRules.current();

        source.sendSuccess(() -> Component.translatable("commands.amemaru.level.info.header",
                player.getDisplayName(), data.getLevel(), data.getExp(), growth.expToNext(data.getLevel()))
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("commands.amemaru.level.info.points",
                LevelService.availableStatPoints(data, growth), data.totalAllocated(),
                LevelService.availableSkillPoints(data, growth)), false);
        source.sendSuccess(() -> Component.translatable("commands.amemaru.level.info.vitals",
                (long) Math.ceil(data.getHp()), (long) data.getStats().maxHp(),
                (long) Math.ceil(data.getMp()), (long) data.getStats().maxMp()), false);
        source.sendSuccess(() -> statLine(data, MAIN_STATS), false);
        source.sendSuccess(() -> statLine(data, SUB_STATS), false);
        return 1;
    }

    private static MutableComponent statLine(PlayerData data, List<StatType> types) {
        MutableComponent line = Component.empty();
        for (int i = 0; i < types.size(); i++) {
            StatType type = types.get(i);
            if (i > 0) {
                line.append(Component.translatable("commands.amemaru.separator"));
            }
            line.append(Component.translatable("commands.amemaru.stat_value",
                    Component.translatable(type.translationKey()), type.format(data.getStats().get(type))));
        }
        return line;
    }

    // ---------------------------------------------------------------- debug

    private static LiteralArgumentBuilder<CommandSourceStack> debugCommand() {
        return Commands.literal("debug")
                .then(Commands.literal("status")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(statusKind("chill", 1, 5))
                                .then(statusKind("freeze", 1, 30))
                                .then(statusKind("shock", 1, 60))
                                .then(statusKind("brand", 1, 5))
                                .then(statusKind("armor_break", 1, 60))))
                .then(Commands.literal("vitals")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("hp", IntegerArgumentType.integer(1, 100))
                                        .then(Commands.argument("mp", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> setVitals(ctx.getSource(),
                                                        EntityArgument.getPlayers(ctx, "targets"),
                                                        IntegerArgumentType.getInteger(ctx, "hp"),
                                                        IntegerArgumentType.getInteger(ctx, "mp")))))));
    }

    /** {@code debug status <相手> <種類> <数>}：凍え・星痕は重ねる数、ほかは秒。 */
    private static LiteralArgumentBuilder<CommandSourceStack> statusKind(String kind, int min, int max) {
        return Commands.literal(kind)
                .then(Commands.argument("amount", IntegerArgumentType.integer(min, max))
                        .executes(ctx -> applyStatus(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"), kind,
                                IntegerArgumentType.getInteger(ctx, "amount"))));
    }

    /** 状態をかける（演出・凍結の決まりの確かめ用。ダメージは与えない）。 */
    private static int applyStatus(CommandSourceStack source, Collection<? extends Entity> targets, String kind, int amount) {
        int count = 0;
        for (Entity entity : targets) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            switch (kind) {
                case "chill" -> StatusService.addChill(living, amount, 200, StatusService.MAX_CHILL, 40, 0, source.getEntity());
                case "freeze" -> StatusService.freeze(living, amount * 20, source.getEntity());
                case "shock" -> StatusService.shock(living, amount * 20, source.getEntity());
                case "brand" -> StatusService.addBrand(living, amount, 200, source.getEntity());
                case "armor_break" -> StatusService.armorBreak(living, amount * 20, source.getEntity());
                default -> {
                    continue;
                }
            }
            count++;
        }
        int done = count;
        log(source, "debug status " + kind + " " + amount + " on " + done + " entities");
        source.sendSuccess(() -> Component.translatable("commands.amemaru.debug.status", kind, amount, done), true);
        return done;
    }

    /** HPとMPを最大値の割合で入れる（自然回復やHUDの確かめ用）。 */
    private static int setVitals(CommandSourceStack source, Collection<ServerPlayer> targets, int hpPercent, int mpPercent) {
        int count = 0;
        for (ServerPlayer player : targets) {
            Optional<PlayerData> found = data(source, player);
            if (found.isEmpty()) {
                continue;
            }
            PlayerData data = found.get();
            if (!data.isStatsReady()) {
                LevelService.recompute(player, data);
            }
            data.setHp(data.getStats().maxHp() * hpPercent / 100.0);
            data.setMp(data.getStats().maxMp() * mpPercent / 100.0);
            VitalsService.pushVanillaHealth(player, data);
            log(source, "debug vitals " + player.getGameProfile().getName() + " hp=" + hpPercent + "% mp=" + mpPercent + "%");
            source.sendSuccess(() -> Component.translatable("commands.amemaru.debug.vitals", player.getDisplayName(), hpPercent, mpPercent), true);
            count++;
        }
        return count;
    }

    // ---------------------------------------------------------------- 共通

    static Optional<PlayerData> data(CommandSourceStack source, ServerPlayer player) {
        Optional<PlayerData> data = PlayerDataCapability.get(player);
        if (data.isEmpty()) {
            source.sendFailure(Component.translatable("commands.amemaru.no_data", player.getDisplayName()));
        }
        return data;
    }

    public static void log(CommandSourceStack source, String action) {
        AmemaruMod.LOGGER.info("[amemaru][admin] {} : {}", source.getTextName(), action);
    }
}

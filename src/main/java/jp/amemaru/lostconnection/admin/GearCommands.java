package jp.amemaru.lostconnection.admin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import jp.amemaru.lostconnection.accessory.AccessoryDef;
import jp.amemaru.lostconnection.accessory.AccessoryDefs;
import jp.amemaru.lostconnection.accessory.AccessoryRoller;
import jp.amemaru.lostconnection.accessory.AccessoryRules;
import jp.amemaru.lostconnection.accessory.AccessoryService;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.player.AdventureData;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.workshop.ProductionRules;
import jp.amemaru.lostconnection.workshop.WorkshopService;
import jp.amemaru.lostconnection.workshop.WorkshopType;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Phase 3 の管理者コマンド（{@code /amemaru admin ...}）：装備・アクセサリーを決まった品質で出す、敵を呼ぶ・片付ける、
 * 生産職と熟練度・結晶果を決める、冒険の記録を見る。テストと配信の準備に使う。
 */
final class GearCommands {
    private static final SuggestionProvider<CommandSourceStack> GEAR = (context, builder) ->
            SharedSuggestionProvider.suggestResource(EquipmentDefs.server().equipment().keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> ACCESSORIES = (context, builder) ->
            SharedSuggestionProvider.suggestResource(AccessoryDefs.all().keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> ENEMIES = (context, builder) ->
            SharedSuggestionProvider.suggestResource(EnemyDefs.all().keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> RARITIES = (context, builder) -> {
        for (Rarity rarity : Rarity.all()) {
            builder.suggest(rarity.id());
        }
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> WORKSHOPS = (context, builder) -> {
        for (WorkshopType type : WorkshopType.values()) {
            builder.suggest(type.id());
        }
        return builder.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> STATS = (context, builder) -> {
        for (StatType type : StatType.all()) {
            builder.suggest(type.id());
        }
        return builder.buildFuture();
    };

    private GearCommands() {
    }

    // ---------------------------------------------------------------- gear

    static LiteralArgumentBuilder<CommandSourceStack> gearCommand() {
        return Commands.literal("gear")
                .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("item", ResourceLocationArgument.id()).suggests(GEAR)
                                        .executes(ctx -> giveGear(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                ResourceLocationArgument.getId(ctx, "item"), null, -1, 0))
                                        .then(Commands.argument("rarity", StringArgumentType.word()).suggests(RARITIES)
                                                .executes(ctx -> giveGear(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                        ResourceLocationArgument.getId(ctx, "item"),
                                                        StringArgumentType.getString(ctx, "rarity"), -1, 0))
                                                .then(Commands.argument("quality", IntegerArgumentType.integer(0, 100))
                                                        .executes(ctx -> giveGear(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                                ResourceLocationArgument.getId(ctx, "item"),
                                                                StringArgumentType.getString(ctx, "rarity"),
                                                                IntegerArgumentType.getInteger(ctx, "quality"), 0))
                                                        .then(Commands.argument("enhance", IntegerArgumentType.integer(0, 100))
                                                                .executes(ctx -> giveGear(ctx.getSource(),
                                                                        EntityArgument.getPlayers(ctx, "targets"),
                                                                        ResourceLocationArgument.getId(ctx, "item"),
                                                                        StringArgumentType.getString(ctx, "rarity"),
                                                                        IntegerArgumentType.getInteger(ctx, "quality"),
                                                                        IntegerArgumentType.getInteger(ctx, "enhance")))))))))
                .then(Commands.literal("durability")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100000))
                                        .executes(ctx -> setDurability(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                IntegerArgumentType.getInteger(ctx, "value"))))));
    }

    private static int giveGear(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation id, String rarityName,
                                int quality, int enhance) {
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
        EquipmentDef def = snapshot.get(id);
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (def == null || item == null) {
            source.sendFailure(Component.translatable("commands.amemaru.gear.unknown", id.toString()));
            return 0;
        }
        Rarity rarity = rarityName == null ? null : Rarity.byId(rarityName);
        if (rarityName != null && rarity == null) {
            source.sendFailure(Component.translatable("commands.amemaru.gear.bad_rarity", rarityName));
            return 0;
        }
        for (ServerPlayer player : targets) {
            ItemStack stack = new ItemStack(item);
            GearData.roll(stack, def, snapshot.rules(), player.getRandom(), rarity, false, null);
            if (quality >= 0) {
                GearData.setAllRatios(stack, def, quality / 100.0);
            }
            if (enhance > 0) {
                GearData.setEnhance(stack, Math.min(enhance, snapshot.rules().maxEnhance()));
            }
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        AdminCommands.log(source, "gear give " + id + " " + rarityName + " " + quality + " " + enhance + " x" + targets.size());
        source.sendSuccess(() -> Component.translatable("commands.amemaru.gear.given", id.toString(), targets.size()), true);
        return targets.size();
    }

    private static int setDurability(CommandSourceStack source, Collection<ServerPlayer> targets, int value) {
        int changed = 0;
        for (ServerPlayer player : targets) {
            ItemStack stack = player.getMainHandItem();
            EquipmentDef def = EquipmentDefs.server().get(stack);
            if (def != null && def.durability() > 0) {
                GearData.setDurability(stack, def, value);
                changed++;
            }
        }
        int result = changed;
        AdminCommands.log(source, "gear durability " + value + " x" + result);
        source.sendSuccess(() -> Component.translatable("commands.amemaru.gear.durability", value, result), true);
        return result;
    }

    // ---------------------------------------------------------------- accessory

    static LiteralArgumentBuilder<CommandSourceStack> accessoryCommand() {
        return Commands.literal("accessory")
                .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("item", ResourceLocationArgument.id()).suggests(ACCESSORIES)
                                        .executes(ctx -> giveAccessory(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                ResourceLocationArgument.getId(ctx, "item"), 0, -1))
                                        .then(Commands.argument("subs", IntegerArgumentType.integer(1, 4))
                                                .executes(ctx -> giveAccessory(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                        ResourceLocationArgument.getId(ctx, "item"),
                                                        IntegerArgumentType.getInteger(ctx, "subs"), -1))
                                                .then(Commands.argument("quality", IntegerArgumentType.integer(0, 100))
                                                        .executes(ctx -> giveAccessory(ctx.getSource(),
                                                                EntityArgument.getPlayers(ctx, "targets"),
                                                                ResourceLocationArgument.getId(ctx, "item"),
                                                                IntegerArgumentType.getInteger(ctx, "subs"),
                                                                IntegerArgumentType.getInteger(ctx, "quality"))))))));
    }

    private static int giveAccessory(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation id, int subs,
                                     int quality) {
        AccessoryDef def = AccessoryDefs.all().get(id);
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (def == null || item == null) {
            source.sendFailure(Component.translatable("commands.amemaru.gear.unknown", id.toString()));
            return 0;
        }
        for (ServerPlayer player : targets) {
            ItemStack stack = new ItemStack(item);
            AccessoryRoller.roll(stack, def, AccessoryRules.current(), player.getRandom(), false, null, subs);
            if (quality >= 0) {
                AccessoryRoller.forceRatio(stack, AccessoryRules.current(), quality / 100.0);
            }
            ItemStack shown = stack.copy();
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            AccessoryService.checkPerfect(player, shown);
        }
        AdminCommands.log(source, "accessory give " + id + " subs=" + subs + " quality=" + quality + " x" + targets.size());
        source.sendSuccess(() -> Component.translatable("commands.amemaru.gear.given", id.toString(), targets.size()), true);
        return targets.size();
    }

    // ---------------------------------------------------------------- enemy

    static LiteralArgumentBuilder<CommandSourceStack> enemyCommand() {
        return Commands.literal("enemy")
                .then(Commands.literal("spawn")
                        .then(Commands.argument("type", ResourceLocationArgument.id()).suggests(ENEMIES)
                                .executes(ctx -> spawn(ctx.getSource(), ResourceLocationArgument.getId(ctx, "type"), 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
                                        .executes(ctx -> spawn(ctx.getSource(), ResourceLocationArgument.getId(ctx, "type"),
                                                IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("clear")
                        .executes(ctx -> clear(ctx.getSource())));
    }

    private static int spawn(CommandSourceStack source, ResourceLocation id, int count) {
        // 見た目を借りる個体（群れの長など）は、借りる先の敵を出して定義だけ替える（11月22日）
        jp.amemaru.lostconnection.enemy.EnemyDef def = EnemyDefs.get(id);
        ResourceLocation entityId = def != null ? def.entity() : id;
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
        if (type == null || def == null || !entityId.equals(ForgeRegistries.ENTITY_TYPES.getKey(type))) {
            source.sendFailure(Component.translatable("commands.amemaru.enemy.unknown", id.toString()));
            return 0;
        }
        ServerLevel level = source.getLevel();
        Vec3 origin = source.getPosition();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2.0 / Math.max(1, count);
            double radius = count == 1 ? 0.0 : 2.5;
            Vec3 pos = origin.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            Entity entity = type.create(level);
            if (entity instanceof AmemaruMob mob) {
                mob.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360.0F, 0.0F);
                if (def.model() != null) {
                    mob.setVariant(id);
                }
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.COMMAND, null, null);
                mob.setHome(mob.blockPosition());
                level.addFreshEntity(mob);
                spawned++;
            }
        }
        int result = spawned;
        AdminCommands.log(source, "enemy spawn " + id + " x" + result);
        source.sendSuccess(() -> Component.translatable("commands.amemaru.enemy.spawned", id.toString(), result), true);
        return result;
    }

    private static int clear(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        AABB box = new AABB(source.getPosition(), source.getPosition()).inflate(96.0);
        List<AmemaruMob> mobs = level.getEntitiesOfClass(AmemaruMob.class, box);
        for (AmemaruMob mob : mobs) {
            mob.discard();
        }
        int result = mobs.size();
        AdminCommands.log(source, "enemy clear x" + result);
        source.sendSuccess(() -> Component.translatable("commands.amemaru.enemy.cleared", result), true);
        return result;
    }

    // ---------------------------------------------------------------- production / fruit / adventure

    static LiteralArgumentBuilder<CommandSourceStack> productionCommand() {
        return Commands.literal("production")
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("workshop", StringArgumentType.word()).suggests(WORKSHOPS)
                                        .executes(ctx -> setProduction(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                StringArgumentType.getString(ctx, "workshop"), 0))
                                        .then(Commands.argument("mastery", IntegerArgumentType.integer(1, 10))
                                                .executes(ctx -> setProduction(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                        StringArgumentType.getString(ctx, "workshop"),
                                                        IntegerArgumentType.getInteger(ctx, "mastery")))))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> clearProduction(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))));
    }

    private static int setProduction(CommandSourceStack source, Collection<ServerPlayer> targets, String workshop, int mastery) {
        WorkshopType type = WorkshopType.byId(workshop);
        JobDef job = type == null ? null : WorkshopService.jobFor(type);
        if (job == null) {
            source.sendFailure(Component.translatable("commands.amemaru.production.unknown", workshop));
            return 0;
        }
        for (ServerPlayer player : targets) {
            PlayerDataCapability.get(player).ifPresent(data -> {
                AdventureData adventure = data.getAdventure();
                adventure.setProductionJob(job.id());
                if (mastery > 0) {
                    adventure.setMasteryExp(job.id(), ProductionRules.current().threshold(mastery));
                }
            });
        }
        AdminCommands.log(source, "production set " + job.id() + " mastery=" + mastery + " x" + targets.size());
        source.sendSuccess(() -> Component.translatable("commands.amemaru.production.set", job.name(), targets.size()), true);
        return targets.size();
    }

    private static int clearProduction(CommandSourceStack source, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            PlayerDataCapability.get(player).ifPresent(data -> data.getAdventure().setProductionJob(null));
        }
        AdminCommands.log(source, "production clear x" + targets.size());
        source.sendSuccess(() -> Component.translatable("commands.amemaru.production.cleared", targets.size()), true);
        return targets.size();
    }

    static LiteralArgumentBuilder<CommandSourceStack> fruitCommand() {
        return Commands.literal("fruit")
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("stat", StringArgumentType.word()).suggests(STATS)
                                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 1000))
                                                .executes(ctx -> setFruit(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                        StringArgumentType.getString(ctx, "stat"),
                                                        IntegerArgumentType.getInteger(ctx, "count")))))));
    }

    private static int setFruit(CommandSourceStack source, Collection<ServerPlayer> targets, String statName, int count) {
        StatType stat = StatType.byId(statName).orElse(null);
        if (stat == null) {
            source.sendFailure(Component.translatable("commands.amemaru.fruit.unknown", statName));
            return 0;
        }
        for (ServerPlayer player : targets) {
            PlayerDataCapability.get(player).ifPresent(data -> {
                data.getAdventure().setFruits(stat, count);
                LevelService.recompute(player, data);
            });
        }
        AdminCommands.log(source, "fruit set " + stat.id() + " " + count + " x" + targets.size());
        source.sendSuccess(() -> Component.translatable("commands.amemaru.fruit.set", Component.translatable(stat.translationKey()),
                count, targets.size()), true);
        return targets.size();
    }

    static LiteralArgumentBuilder<CommandSourceStack> adventureCommand() {
        return Commands.literal("adventure")
                .then(Commands.literal("info")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> adventureInfo(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))));
    }

    private static int adventureInfo(CommandSourceStack source, ServerPlayer player) throws CommandSyntaxException {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return 0;
        }
        AdventureData adventure = data.getAdventure();
        StringBuilder fruits = new StringBuilder();
        for (StatType type : StatType.all()) {
            if (adventure.fruits(type) > 0) {
                fruits.append(type.id()).append('=').append(adventure.fruits(type)).append(' ');
            }
        }
        StringBuilder bestiary = new StringBuilder();
        for (Map.Entry<ResourceLocation, AdventureData.BestiaryEntry> entry : adventure.allBestiary().entrySet()) {
            bestiary.append(entry.getKey().getPath()).append('×').append(entry.getValue().kills())
                    .append('(').append(entry.getValue().drops().size()).append(") ");
        }
        int chapter = ProgressionData.currentChapter(player.server);
        String production = adventure.productionJob() == null ? "-" : adventure.productionJob() + " exp="
                + adventure.masteryExp(adventure.productionJob()) + " lv="
                + ProductionRules.current().level(adventure.masteryExp(adventure.productionJob()), chapter);
        source.sendSuccess(() -> Component.literal("[" + player.getGameProfile().getName() + "] fruits: " + fruits
                + "| production: " + production + " | crafted: " + adventure.crafted() + " | overflow exp: "
                + adventure.overflowExp() + " | titles: " + adventure.pendingTitles()).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("bestiary: " + bestiary).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}

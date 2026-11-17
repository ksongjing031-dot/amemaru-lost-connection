package jp.amemaru.lostconnection.workshop;

import jp.amemaru.lostconnection.accessory.AccessoryDef;
import jp.amemaru.lostconnection.accessory.AccessoryDefs;
import jp.amemaru.lostconnection.accessory.AccessoryItem;
import jp.amemaru.lostconnection.accessory.AccessoryRoller;
import jp.amemaru.lostconnection.accessory.AccessoryRules;
import jp.amemaru.lostconnection.accessory.AccessoryService;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.equipment.GearItem;
import jp.amemaru.lostconnection.equipment.GearRules;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.s2c.SpecialMomentS2C;
import jp.amemaru.lostconnection.player.AdventureData;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 工房の操作（サーバー）：生産職になる・作る・強化・修理（Phase 0-A 19章・20章）。すべてサーバーで確かめてから行う
 * （Phase 0-B 5章 {@code WorkshopCraft}：素材を持っているか、熟練度、工房の近くか）。
 *
 * <ul>
 *   <li>作る：その工房の生産職の人だけ。熟練度がレシピの熟練度以上。大成功なら、装備は品質が一段上がり、
 *       アクセサリーはサブが一つ多くつく。作った品には「〇〇作」と入る</li>
 *   <li>強化：+0〜+10、失敗なし【確定】。武器は武器職人、防具・盾は防具職人が行う。素材だけを使う
 *       （ノットはPhase 6の経済で足す）</li>
 *   <li>修理：誰でもできるが、その系統の生産職でない人は素材が2倍【補完】（NPCの修理の高い料金は、ノットを作るPhase 6で足す）</li>
 *   <li>アクセサリーの強化と厳選（再抽選・固定・分解）はPhase 6</li>
 * </ul>
 */
public final class WorkshopService {
    private WorkshopService() {
    }

    // ---------------------------------------------------------------- 生産職

    /** その工房の生産職（データパックの jobs のうち、kind が production で workshop が合うもの）。 */
    @Nullable
    public static JobDef jobFor(WorkshopType type) {
        return SkillRegistry.get().productionJobFor(type.id());
    }

    /** 今の熟練度（1〜。生産職がその工房の職でなければ0）。 */
    public static int mastery(ServerPlayer player, PlayerData data, WorkshopType type) {
        JobDef job = jobFor(type);
        if (job == null || !job.id().equals(data.getAdventure().productionJob())) {
            return 0;
        }
        return ProductionRules.current().level(data.getAdventure().masteryExp(job.id()), ProgressionData.currentChapter(player.server));
    }

    /**
     * その工房の生産職になる（変える）。お試し期間（戦闘職と同じ config の日まで）は無料、その後は「転職の証」を使う【補完】。
     * 熟練度は職業ごとに残るので、戻せば続きから。
     */
    public static boolean chooseJob(ServerPlayer player, WorkshopType type) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        JobDef job = jobFor(type);
        if (data == null || job == null) {
            fail(player, "message.amemaru.workshop.no_job_def");
            return false;
        }
        AdventureData adventure = data.getAdventure();
        if (job.id().equals(adventure.productionJob())) {
            fail(player, "message.amemaru.workshop.already");
            return false;
        }
        if (adventure.productionJob() != null) {
            int day = ProgressionData.currentDay(player.server);
            int tokens = ServerConfig.JOB_CHANGE_TOKENS.get();
            if (day > ServerConfig.JOB_CHANGE_FREE_UNTIL_DAY.get() && tokens > 0 && !player.getAbilities().instabuild) {
                if (count(player, ModItems.JOB_CHANGE_TOKEN.get()) < tokens) {
                    player.displayClientMessage(Component.translatable("message.amemaru.workshop.need_token", tokens)
                            .withStyle(ChatFormatting.RED), true);
                    return false;
                }
                consume(player, ModItems.JOB_CHANGE_TOKEN.get(), tokens);
            }
            adventure.addProductionChange();
        }
        adventure.setProductionJob(job.id());
        player.displayClientMessage(Component.translatable("message.amemaru.workshop.joined", job.name())
                .withStyle(ChatFormatting.GOLD), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_WORK_TOOLSMITH,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    // ---------------------------------------------------------------- 作る

    /** レシピで作る。 */
    public static boolean craft(ServerPlayer player, WorkshopType type, ResourceLocation recipeId) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        Optional<? extends Recipe<?>> found = player.server.getRecipeManager().byKey(recipeId);
        if (data == null || found.isEmpty() || !(found.get() instanceof WorkshopRecipe recipe) || recipe.workshop() != type) {
            return false;
        }
        JobDef job = jobFor(type);
        if (job == null || !job.id().equals(data.getAdventure().productionJob())) {
            fail(player, "message.amemaru.workshop.need_job", job == null ? "" : job.name());
            return false;
        }
        int mastery = mastery(player, data, type);
        if (mastery < recipe.mastery()) {
            fail(player, "message.amemaru.workshop.need_mastery", recipe.mastery());
            return false;
        }
        if (!hasAll(player, recipe.ingredients(), 1.0)) {
            fail(player, "message.amemaru.workshop.need_materials");
            return false;
        }
        consumeAll(player, recipe.ingredients(), 1.0);

        ItemStack result = recipe.result();
        double chance = ProductionRules.current().greatChance(mastery, recipe.mastery());
        boolean great = player.getRandom().nextDouble() * 100.0 < chance;
        boolean rolled = false;
        if (result.getItem() instanceof GearItem) {
            EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
            EquipmentDef def = snapshot.get(result);
            if (def != null) {
                GearData.roll(result, def, snapshot.rules(), player.getRandom(), recipe.rarity(), great, player);
                rolled = true;
            }
        } else if (result.getItem() instanceof AccessoryItem) {
            AccessoryDef def = AccessoryDefs.get(result);
            if (def != null) {
                AccessoryRoller.roll(result, def, AccessoryRules.current(), player.getRandom(), great, player, 0);
                rolled = true;
            }
        }
        great = great && rolled;

        int before = mastery;
        AdventureData adventure = data.getAdventure();
        int chapter = ProgressionData.currentChapter(player.server);
        adventure.setMasteryExp(job.id(), ProductionRules.current().addExp(adventure.masteryExp(job.id()), recipe.masteryExp(), chapter));
        adventure.addCrafted(result.getCount());
        int after = ProductionRules.current().level(adventure.masteryExp(job.id()), chapter);

        ItemStack shown = result.copy();
        give(player, result);
        ModNetwork.sendTo(player, new SpecialMomentS2C(great ? SpecialMomentS2C.Kind.GREAT_SUCCESS : SpecialMomentS2C.Kind.CRAFTED, shown));
        player.displayClientMessage(Component.translatable(great ? "message.amemaru.workshop.great" : "message.amemaru.workshop.crafted",
                shown.getHoverName()).withStyle(great ? ChatFormatting.GOLD : ChatFormatting.GREEN), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                great ? SoundEvents.PLAYER_LEVELUP : SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.8F, great ? 1.4F : 1.0F);
        if (after > before) {
            player.displayClientMessage(Component.translatable("message.amemaru.workshop.mastery_up", job.name(), after)
                    .withStyle(ChatFormatting.AQUA), false);
        }
        if (shown.getItem() instanceof AccessoryItem) {
            AccessoryService.checkPerfect(player, shown);
        }
        return true;
    }

    // ---------------------------------------------------------------- 強化

    /** 強化の素材（次の段に上げるとき。上げられなければ空）。 */
    public static List<EquipmentDef.Cost> enhanceCost(ItemStack stack, EquipmentDef def, GearRules rules) {
        int target = GearData.enhance(stack) + 1;
        List<EquipmentDef.Cost> list = new ArrayList<>();
        if (target > rules.maxEnhance()) {
            return list;
        }
        for (GearRules.EnhanceCost cost : rules.enhanceCosts(def.generation())) {
            int count = cost.countFor(target);
            if (count > 0) {
                list.add(new EquipmentDef.Cost(cost.item(), count));
            }
        }
        return list;
    }

    /** 工房の台に置いた装備を、1段強化する（失敗しない）。 */
    public static boolean enhance(ServerPlayer player, WorkshopType type, ItemStack stack) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || stack.isEmpty()) {
            return false;
        }
        if (type == WorkshopType.ACCESSORY || stack.getItem() instanceof AccessoryItem) {
            fail(player, "message.amemaru.workshop.accessory_later");
            return false;
        }
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
        EquipmentDef def = snapshot.get(stack);
        if (def == null || !type.accepts(stack)) {
            fail(player, "message.amemaru.workshop.wrong_item");
            return false;
        }
        JobDef job = jobFor(type);
        if (job == null || !job.id().equals(data.getAdventure().productionJob())) {
            fail(player, "message.amemaru.workshop.need_job_enhance", job == null ? "" : job.name());
            return false;
        }
        if (GearData.enhance(stack) >= snapshot.rules().maxEnhance()) {
            fail(player, "message.amemaru.workshop.max_enhance");
            return false;
        }
        List<EquipmentDef.Cost> cost = enhanceCost(stack, def, snapshot.rules());
        if (!hasAll(player, cost, 1.0)) {
            fail(player, "message.amemaru.workshop.need_materials");
            return false;
        }
        consumeAll(player, cost, 1.0);
        GearData.ensureRolled(stack, def, snapshot.rules(), player.getRandom());
        GearData.setEnhance(stack, GearData.enhance(stack) + 1);
        ModNetwork.sendTo(player, new SpecialMomentS2C(SpecialMomentS2C.Kind.ENHANCED, stack.copy()));
        player.displayClientMessage(Component.translatable("message.amemaru.workshop.enhanced", stack.getHoverName())
                .withStyle(ChatFormatting.GREEN), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.3F);
        PlayerDataCapability.get(player).ifPresent(PlayerData::invalidateStats);
        return true;
    }

    // ---------------------------------------------------------------- 修理

    /** 修理の素材（減った分の割合。生産職でなければ倍率がかかる）。 */
    public static List<EquipmentDef.Cost> repairCost(ItemStack stack, EquipmentDef def, double multiplier) {
        List<EquipmentDef.Cost> list = new ArrayList<>();
        if (def.durability() <= 0) {
            return list;
        }
        int missing = def.durability() - GearData.durability(stack, def);
        if (missing <= 0) {
            return list;
        }
        double ratio = (double) missing / def.durability();
        for (EquipmentDef.Cost cost : def.repair()) {
            int count = (int) Math.ceil(cost.count() * ratio * multiplier - 1.0E-6);
            list.add(new EquipmentDef.Cost(cost.item(), Math.max(1, count)));
        }
        return list;
    }

    /** 修理の素材の倍率（その系統の生産職なら1、そうでなければ config の倍率）。 */
    public static double repairMultiplier(PlayerData data, WorkshopType type) {
        JobDef job = jobFor(type);
        boolean crafter = job != null && job.id().equals(data.getAdventure().productionJob());
        return crafter ? 1.0 : EquipmentDefs.server().rules().nonCrafterRepair();
    }

    /** 工房の台に置いた装備の耐久を、最大まで戻す。 */
    public static boolean repair(ServerPlayer player, WorkshopType type, ItemStack stack) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || stack.isEmpty()) {
            return false;
        }
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
        EquipmentDef def = snapshot.get(stack);
        if (def == null || !type.accepts(stack)) {
            fail(player, "message.amemaru.workshop.wrong_item");
            return false;
        }
        if (def.durability() <= 0 || GearData.durability(stack, def) >= def.durability()) {
            fail(player, "message.amemaru.workshop.no_repair");
            return false;
        }
        List<EquipmentDef.Cost> cost = repairCost(stack, def, repairMultiplier(data, type));
        if (!hasAll(player, cost, 1.0)) {
            fail(player, "message.amemaru.workshop.need_materials");
            return false;
        }
        consumeAll(player, cost, 1.0);
        GearData.setDurability(stack, def, def.durability());
        ModNetwork.sendTo(player, new SpecialMomentS2C(SpecialMomentS2C.Kind.REPAIRED, stack.copy()));
        player.displayClientMessage(Component.translatable("message.amemaru.workshop.repaired", stack.getHoverName())
                .withStyle(ChatFormatting.GREEN), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.8F, 1.0F);
        PlayerDataCapability.get(player).ifPresent(PlayerData::invalidateStats);
        return true;
    }

    // ---------------------------------------------------------------- 素材

    /** 持ち物の中の、その品の数。 */
    public static int count(Player player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** すべての素材がそろっているか（クリエイティブは、いつもそろっている）。 */
    public static boolean hasAll(Player player, List<EquipmentDef.Cost> costs, double multiplier) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        for (EquipmentDef.Cost cost : costs) {
            Item item = ForgeRegistries.ITEMS.getValue(cost.item());
            if (item == null || count(player, item) < Math.ceil(cost.count() * multiplier)) {
                return false;
            }
        }
        return true;
    }

    private static void consumeAll(Player player, List<EquipmentDef.Cost> costs, double multiplier) {
        if (player.getAbilities().instabuild) {
            return;
        }
        for (EquipmentDef.Cost cost : costs) {
            Item item = ForgeRegistries.ITEMS.getValue(cost.item());
            if (item != null) {
                consume(player, item, (int) Math.ceil(cost.count() * multiplier));
            }
        }
    }

    private static void consume(Player player, Item item, int amount) {
        int left = amount;
        for (List<ItemStack> list : List.of(player.getInventory().items, player.getInventory().offhand)) {
            for (ItemStack stack : list) {
                if (left <= 0) {
                    return;
                }
                if (stack.is(item)) {
                    int take = Math.min(left, stack.getCount());
                    stack.shrink(take);
                    left -= take;
                }
            }
        }
        player.getInventory().setChanged();
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void fail(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
    }
}

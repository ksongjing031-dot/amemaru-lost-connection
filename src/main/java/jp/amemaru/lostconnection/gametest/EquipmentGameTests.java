package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryData;
import jp.amemaru.lostconnection.accessory.AccessoryDef;
import jp.amemaru.lostconnection.accessory.AccessoryDefs;
import jp.amemaru.lostconnection.accessory.AccessoryRoller;
import jp.amemaru.lostconnection.accessory.AccessoryRules;
import jp.amemaru.lostconnection.accessory.AccessoryService;
import jp.amemaru.lostconnection.accessory.AccessorySlot;
import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.equipment.GearEffects;
import jp.amemaru.lostconnection.equipment.GearRules;
import jp.amemaru.lostconnection.equipment.GearService;
import jp.amemaru.lostconnection.equipment.GearSlot;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.equipment.SpecialEffect;
import jp.amemaru.lostconnection.item.AmemaruShieldItem;
import jp.amemaru.lostconnection.item.ItemManifest;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.loot.DropService;
import jp.amemaru.lostconnection.loot.LootItemEntity;
import jp.amemaru.lostconnection.player.AdventureData;
import jp.amemaru.lostconnection.player.GrowthDef;
import jp.amemaru.lostconnection.player.GrowthRules;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.SpeedService;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.progression.ProgressionData;
import jp.amemaru.lostconnection.workshop.ModRecipes;
import jp.amemaru.lostconnection.workshop.ProductionRules;
import jp.amemaru.lostconnection.workshop.WorkshopRecipe;
import jp.amemaru.lostconnection.workshop.WorkshopService;
import jp.amemaru.lostconnection.workshop.WorkshopType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 3（敵・ドロップ・装備・工房・アクセサリー）の自動テスト。
 *
 * <p>数値は同梱のデータ（{@code equipment/**}・{@code accessories/**}・{@code gear_rules}・{@code accessory_rules}・
 * {@code production_rules}・{@code growth}・{@code enemies/**}・工房のレシピ）と config の初期値が前提。</p>
 *
 * <p>偽のプレイヤーはプレイヤー一覧に入らないため、「攻撃を当てた人」を一覧から探す処理（2人で倒したときの経験値の山分け・
 * 敵が狙いを選び直す処理）は、計算の部分だけをここで確かめ、実際の2人の動きはLANの2人で確かめる（後回し）。</p>
 *
 * <p>進行日を変えるテストは、同じ呼び出しの中で元に戻す（同じ回の、ほかのテストに影響させないため）。</p>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EquipmentGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final double EPSILON = 1.0E-6;
    /** 割合は float で持つため、比べるときは少し幅を持たせる。 */
    private static final double RATIO_EPSILON = 1.0E-4;

    private EquipmentGameTests() {
    }

    // ================================================================ 定義

    /** 装備20・アクセサリー5・敵4・生産職3・工房のレシピ17が読め、アイテムと定義がそろっている。 */
    @GameTest(template = EMPTY)
    public static void phase3DefinitionsLoad(GameTestHelper helper) {
        check(helper, EquipmentDefs.loader().errors().isEmpty(), "装備の定義に問題があります：" + EquipmentDefs.loader().errors());
        check(helper, GearRules.loader().errors().isEmpty(), "装備の決まりに問題があります：" + GearRules.loader().errors());
        check(helper, AccessoryDefs.loader().errors().isEmpty(), "アクセサリーの定義に問題があります：" + AccessoryDefs.loader().errors());
        check(helper, AccessoryRules.loader().errors().isEmpty(), "アクセサリーの決まりに問題があります：" + AccessoryRules.loader().errors());
        check(helper, EnemyDefs.loader().errors().isEmpty(), "敵の定義に問題があります：" + EnemyDefs.loader().errors());
        check(helper, ProductionRules.loader().errors().isEmpty(), "生産の決まりに問題があります：" + ProductionRules.loader().errors());
        check(helper, SkillRegistry.get().problems().isEmpty(), "職業の定義に問題があります：" + SkillRegistry.get().problems());

        EquipmentDefs.Snapshot gear = EquipmentDefs.server();
        // Phase 6 その2 で、4職業の見習い・古代石の武器（8）と、古代石シリーズ（軽装）の防具（4）が増えた
        // Phase 8 その1 で、2階のボスのレア「古樹の枝杖」が増えた
        // Phase 8 その2 で、3階のボスのレア「水門の鍵盾」と、3階からの職業ごとの装備「流紋シリーズ」（6職業の防具24・武器7・盾1）が増えた
        // 11月25日：5階の守護者シリーズを防具一式に（6職業の頭・脚・足の18）と、6階のボスのレア「村長の毛皮」一式4、
        // 第2章の職業ごとの装備「霜銀シリーズ」（6職業の防具24・武器7・盾1）で132
        // 11月25日：7階のボスのレア「鏡氷の短剣」で133
        check(helper, gear.equipment().size() == 133, "装備の定義が133ではありません：" + gear.equipment().size());
        for (ItemManifest.Category category : new ItemManifest.Category[]{
                ItemManifest.Category.WEAPON, ItemManifest.Category.SHIELD, ItemManifest.Category.ARMOR}) {
            for (RegistryObject<Item> item : ModItems.MANIFEST.category(category)) {
                check(helper, gear.get(item.get()) != null, "装備の定義がありません：" + item.getId());
            }
        }
        // Phase 5 で、1階のボスのレアのお守り「草原の角笛」が増えた
        // 11月24日：2〜5階のアクセサリー（階ごとに、作る5つと、名のある魔物が落とすレアのお守り1つ）の24が増えた
        // 11月25日：6階のアクセサリー（作る5つと、名のある魔物2体が落とすレアのお守り2つ）の7で38
        // 11月25日：7階のアクセサリー（作る5つと、名のある魔物2体が落とすレアのお守り2つ）の7で45
        check(helper, AccessoryDefs.all().size() == 45, "アクセサリーの定義が45ではありません：" + AccessoryDefs.all().size());
        for (RegistryObject<Item> item : ModItems.MANIFEST.category(ItemManifest.Category.ACCESSORY)) {
            check(helper, AccessoryDefs.get(item.get()) != null, "アクセサリーの定義がありません：" + item.getId());
        }

        // Phase 5 で、1階のボス「草原の主ヴェルドホーン」が増えた
        // 11月22日：1階の西の丘の石柱の守り手「群れの長」（ツノモフの見た目を借りる別の定義）が増えた
        // Phase 8 その1：2階の雑魚4種（迷い灯・樹皮兵・ポコダケ・葉影ギツネ）と名のある魔物2体、ボス「森番の古樹オールドウォード」と、
        // その幹の「光る節」
        // Phase 8 その2：3階の雑魚4種（水門ネズミ・澱みスライムと小さな澱みスライム・水路掃除機兵・牙ナマズ）と名のある魔物3体、
        // ボス「大水門の番人ハイドラム」
        // 11月25日：6階の雑魚4種（霜狼・氷像の残響・凍て鳥・霜の欠け熊）と名のある魔物2体、ボス「村長の残響オルド」で44
        // 11月25日：7階の雑魚4種（写し身・氷角鹿・つらら蝙蝠・吹雪の精）と名のある魔物2体（白角・吹雪の大精）、
        // ボス「鏡像獣リフレクタ」と、その写し身（図鑑には載せない）で52
        check(helper, EnemyDefs.all().size() == 52, "敵の定義が52ではありません：" + EnemyDefs.all().keySet());
        EnemyDef alpha = EnemyDefs.get(AmemaruMod.id("tsunomofu_alpha"));
        check(helper, alpha != null && AmemaruMod.id("tsunomofu").equals(alpha.entity()) && alpha.behavior().getOrDefault("scale", 1.0) > 1.0,
                "群れの長が、ツノモフの見た目を借りる大きな個体になっていません");
        for (EntityType<?> type : List.<EntityType<?>>of(ModEntities.TSUNOMOFU.get(), ModEntities.ISHIKURE_SOLDIER.get(),
                ModEntities.CRYSTAL_BEE.get(), ModEntities.KAKEKIBA_WOLF.get(), ModEntities.MAYOI_BI.get(), ModEntities.JUHIHEI.get(),
                ModEntities.POKODAKE.get(), ModEntities.HAKAGE_KITSUNE.get(), ModEntities.OLD_WARD.get())) {
            EnemyDef def = EnemyDefs.forType(type);
            check(helper, def != null, "敵の定義がありません：" + ForgeRegistries.ENTITY_TYPES.getKey(type));
            check(helper, def.lootTable() != null, def.id() + " にドロップ表がありません");
            for (EnemyDef.RareDrop rare : def.rare()) {
                check(helper, ForgeRegistries.ITEMS.containsKey(rare.item()), def.id() + " のレアドロップの品がありません：" + rare.item());
            }
        }

        // 生産職：武器職人・防具職人・装飾師。戦闘職の一覧には入らない
        String[][] expected = {{"weapon", "weaponsmith"}, {"armor", "armorsmith"}, {"accessory", "jeweler"}};
        check(helper, SkillRegistry.get().productionJobs().size() == 3,
                "生産職が3つではありません：" + SkillRegistry.get().productionJobs().size());
        for (String[] pair : expected) {
            JobDef job = WorkshopService.jobFor(WorkshopType.byId(pair[0]));
            check(helper, job != null && job.id().equals(id(pair[1])), pair[0] + " の工房の生産職が " + pair[1] + " ではありません");
            check(helper, SkillRegistry.get().jobs().stream().noneMatch(combat -> combat.id().equals(id(pair[1]))),
                    pair[1] + " が戦闘職の一覧に入っています");
        }

        // 工房のレシピ：できる品はその工房で扱う品、素材はすべてある
        List<WorkshopRecipe> recipes = helper.getLevel().getServer().getRecipeManager()
                .getAllRecipesFor(ModRecipes.WORKSHOP_TYPE.get());
        // Phase 6 その2 で、古代石の鈴杖・弓・双短剣・籠手と、古代石シリーズ（軽装）の4つ（計8）が増えた
        // Phase 8 その2 で、流紋シリーズの32が増えた
        // 11月24日：2〜5階のアクセサリーの20が増えた
        // 11月25日：6階のアクセサリーの5と、霜銀シリーズの32、生産職どうしの部品3（補強金具・台座布・結晶細工）が増えた
        // 11月25日：7階のアクセサリーの5が増えた
        check(helper, recipes.size() == 122, "工房のレシピが122ではありません：" + recipes.size());
        for (WorkshopRecipe recipe : recipes) {
            check(helper, recipe.workshop().makes(recipe.result()), recipe.getId() + " の品が、その工房で作る品ではありません");
            check(helper, !recipe.ingredients().isEmpty(), recipe.getId() + " に素材がありません");
            for (EquipmentDef.Cost cost : recipe.ingredients()) {
                check(helper, ForgeRegistries.ITEMS.containsKey(cost.item()) && cost.count() > 0,
                        recipe.getId() + " の素材がおかしいです：" + cost);
            }
        }
        helper.succeed();
    }

    // ================================================================ 装備の抽選・品質・数値

    /** 抽選は70〜100%（大成功は+10%）。品質の★、レアリティと強化の倍率、決まった数値の見習い装備、取引の印。 */
    @GameTest(template = EMPTY)
    public static void gearRollsQualityAndValues(GameTestHelper helper) {
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
        GearRules rules = snapshot.rules();
        EquipmentDef sword = gearDef("ancient_stone_sword");
        RandomSource random = RandomSource.create(20261118L);
        check(helper, Math.abs(rules.rollMin() - 0.70) < EPSILON && Math.abs(rules.rollMax() - 1.0) < EPSILON
                && Math.abs(rules.greatSuccessBonus() - 0.10) < EPSILON, "抽選の幅が70〜100%（大成功+10%）ではありません");
        for (int i = 0; i < 300; i++) {
            ItemStack stack = stack("ancient_stone_sword");
            boolean great = i % 3 == 0;
            GearData.roll(stack, sword, rules, random, null, great, null);
            double ratio = GearData.ratio(stack, StatType.ATTACK, sword, rules);
            double min = great ? rules.rollMin() + rules.greatSuccessBonus() : rules.rollMin();
            check(helper, ratio >= min - RATIO_EPSILON && ratio <= rules.rollMax() + RATIO_EPSILON,
                    "抽選の割合が幅の外です：" + ratio + (great ? "（大成功）" : ""));
            double value = GearData.value(stack, StatType.ATTACK, sword, rules);
            check(helper, value >= 10 && value <= 14, "古代石の剣の攻撃が10〜14の外です：" + value);
            check(helper, GearData.rarity(stack, sword) == Rarity.COMMON, "レアリティが定義のコモンではありません");
            check(helper, GearData.isGreat(stack) == great, "大成功の印が合いません");
            check(helper, GearData.enhance(stack) == 0 && GearData.durability(stack, sword) == sword.durability(),
                    "抽選したての品の強化・耐久がおかしいです");
        }
        // 品質：平均の割合 <60 ★1・60〜 ★2・70〜 ★3・80〜 ★4・90〜 ★5
        double[][] qualities = {{0.95, 5}, {0.85, 4}, {0.75, 3}, {0.65, 2}, {0.55, 1}};
        for (double[] pair : qualities) {
            ItemStack stack = rolled("ancient_stone_sword", Rarity.COMMON, pair[0], 0);
            int quality = GearData.quality(stack, sword, rules);
            check(helper, quality == (int) pair[1], "割合" + pair[0] + "の品質が★" + (int) pair[1] + "ではありません：★" + quality);
        }
        // 数値：攻撃14 × レアリティ × 割合 × 強化（+1ごとに4%）。整数に丸める
        expectValue(helper, rolled("ancient_stone_sword", Rarity.COMMON, 1.0, 0), sword, 14, "コモン・100%");
        expectValue(helper, rolled("ancient_stone_sword", Rarity.COMMON, 0.70, 0), sword, 10, "コモン・70%");
        expectValue(helper, rolled("ancient_stone_sword", Rarity.RARE, 1.0, 0), sword, 16, "レア・100%");
        expectValue(helper, rolled("ancient_stone_sword", Rarity.RARE, 1.0, 5), sword, 19, "レア・100%・+5");
        expectValue(helper, rolled("ancient_stone_sword", Rarity.LEGENDARY, 1.0, 10), sword, 26, "レジェンダリー・100%・+10");
        ItemStack ranged = rolled("ancient_stone_sword", Rarity.COMMON, 0.8, 0);
        check(helper, GearData.minValue(ranged, StatType.ATTACK, sword, rules) == 10
                && GearData.maxValue(ranged, StatType.ATTACK, sword, rules) == 14, "幅の表示が10〜14ではありません");
        // 強化で光る段：+4・+7・+10
        check(helper, rules.glowTier(3) == 0 && rules.glowTier(4) == 1 && rules.glowTier(7) == 2 && rules.glowTier(10) == 3,
                "強化で光る段が+4・+7・+10ではありません");
        check(helper, rules.maxEnhance() == 10, "強化の最大が+10ではありません");

        // 見習い装備：数値は決まっていて（抽選なし）、取引できない
        EquipmentDef apprentice = gearDef("apprentice_sword");
        ItemStack kit = stack("apprentice_sword");
        GearData.roll(kit, apprentice, rules, random, null, false, null);
        expectValue(helper, kit, apprentice, 6, "見習いの剣");
        check(helper, GearData.isBound(kit), "見習いの剣が取引できない品になっていません");
        check(helper, GearData.durability(kit, apprentice) == 0 && !GearData.isBroken(kit, apprentice),
                "見習いの剣（壊れない）の耐久の扱いがおかしいです");

        // レア装備：一度装備すると取引できなくなる
        EquipmentDef sentinel = gearDef("sentinel_blade");
        ItemStack rare = stack("sentinel_blade");
        GearData.roll(rare, sentinel, rules, random, Rarity.RARE, false, null);
        check(helper, GearData.bindsOnEquip(rare) && !GearData.isBound(rare), "見張りの石刃に「装備すると取引不可」の印がありません");
        GearData.bind(rare);
        check(helper, GearData.isBound(rare) && !GearData.bindsOnEquip(rare), "装備した後に取引できない品になっていません");
        helper.succeed();
    }

    /**
     * 11月25日「防具は一式全部揃えた形に」：同じシリーズの頭・胴・脚・足をそろえると、一式の効果（set_effects）が効く。
     * 3つだけ・ほかのシリーズが混ざる・レベルが足りない・壊れた品があるときは効かない。
     */
    @GameTest(template = EMPTY)
    public static void armorSetBonusNeedsAllFour(GameTestHelper helper) {
        FakePlayer player = fake(helper, "armor_set");
        PlayerData data = knight(player, 22);
        ItemStack helm = rolled("guardian_knight_helm", Rarity.EPIC, 1.0, 0);
        ItemStack shell = rolled("guardian_knight_shell", Rarity.EPIC, 1.0, 0);
        ItemStack greaves = rolled("guardian_knight_greaves", Rarity.EPIC, 1.0, 0);
        ItemStack sabatons = rolled("guardian_knight_sabatons", Rarity.EPIC, 1.0, 0);
        player.setItemSlot(EquipmentSlot.HEAD, helm);
        player.setItemSlot(EquipmentSlot.CHEST, shell);
        player.setItemSlot(EquipmentSlot.LEGS, greaves);
        check(helper, GearService.setEffects(player, data).isEmpty() && GearService.seriesWorn(player, "guardian_knight") == 3,
                "3つだけで、一式の効果が効いています");
        player.setItemSlot(EquipmentSlot.FEET, sabatons);
        check(helper, GearService.seriesWorn(player, "guardian_knight") == 4, "守護者シリーズを4つ着ていることになっていません");
        check(helper, GearService.setEffects(player, data).stream().anyMatch(e -> e.type() == SpecialEffect.Type.DAMAGE_REDUCTION
                && Math.abs(e.percent() - 5.0) < EPSILON), "一式そろえても、一式の効果（被ダメージ −5%）が効いていません");
        long wards = GearEffects.active(player, data).stream().filter(e -> e.type() == SpecialEffect.Type.DAMAGE_REDUCTION).count();
        check(helper, wards == 2, "外装の効果と一式の効果の2つが効いていません：" + wards);

        // ほかのシリーズが混ざると効かない
        player.setItemSlot(EquipmentSlot.FEET, rolled("ryumon_knight_sabatons", Rarity.COMMON, 1.0, 0));
        check(helper, GearService.setEffects(player, data).isEmpty(), "流紋の鉄靴が混ざっても、一式の効果が効いています");
        player.setItemSlot(EquipmentSlot.FEET, sabatons);
        // レベルが足りないと効かない
        LevelService.setLevel(player, data, 21);
        check(helper, GearService.setEffects(player, data).isEmpty(), "レベル21（使えない）でも、一式の効果が効いています");
        LevelService.setLevel(player, data, 22);
        check(helper, !GearService.setEffects(player, data).isEmpty(), "レベルを戻しても、一式の効果が戻りません");
        // 壊れた品があると効かない
        EquipmentDef helmDef = gearDef("guardian_knight_helm");
        GearData.setDurability(player.getItemBySlot(EquipmentSlot.HEAD), helmDef, 0);
        check(helper, GearService.setEffects(player, data).isEmpty(), "壊れた兜があっても、一式の効果が効いています");
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        helper.succeed();
    }

    /** 耐久：攻撃・スキルで1ずつ減り、0で壊れる（なくならない）。防具と盾は、敵から受けたダメージに合わせて減る（11月24日）。 */
    @GameTest(template = EMPTY)
    public static void gearDurabilityWearsAndBreaks(GameTestHelper helper) {
        FakePlayer player = fake(helper, "durability");
        PlayerData data = knight(player, 10);
        EquipmentDef swordDef = gearDef("ancient_stone_sword");
        ItemStack sword = rolled("ancient_stone_sword", Rarity.COMMON, 1.0, 0);
        ItemStack shield = rolled("ancient_stone_shield", Rarity.COMMON, 1.0, 0);
        ItemStack cuirass = rolled("ancient_stone_cuirass", Rarity.COMMON, 1.0, 0);
        player.setItemSlot(EquipmentSlot.CHEST, cuirass);
        equip(player, data, sword, shield);
        check(helper, GearData.durability(sword, swordDef) == 400, "古代石の剣の耐久が400ではありません");

        GearService.wearWeapon(player, false);
        GearService.wearWeapon(player, true);
        check(helper, GearData.durability(player.getMainHandItem(), swordDef) == 398, "攻撃とスキルで耐久が2減っていません："
                + GearData.durability(player.getMainHandItem(), swordDef));
        EquipmentDef shieldDef = gearDef("ancient_stone_shield");
        EquipmentDef cuirassDef = gearDef("ancient_stone_cuirass");
        int shieldBefore = GearData.durability(player.getOffhandItem(), shieldDef);
        int cuirassBefore = GearData.durability(player.getItemBySlot(EquipmentSlot.CHEST), cuirassDef);
        // 11月24日：防具と盾の耐久は、受けたダメージに合わせて減る（最大HPの10%ごとに1。端数は確率で）
        GearRules gearRules = EquipmentDefs.server().rules();
        check(helper, Math.abs(gearRules.armorHpPercent() - 10.0) < EPSILON, "防具の耐久が減るダメージが最大HPの10%ではありません："
                + gearRules.armorHpPercent());
        GearService.wearArmor(player, 0.10);
        check(helper, GearData.durability(player.getOffhandItem(), shieldDef) == shieldBefore - 1
                        && GearData.durability(player.getItemBySlot(EquipmentSlot.CHEST), cuirassDef) == cuirassBefore - 1,
                "最大HPの10%のダメージで、盾と防具の耐久が1ずつ減っていません");
        GearService.wearArmor(player, 0.30);
        GearService.wearArmor(player, 0.0);
        check(helper, GearData.durability(player.getOffhandItem(), shieldDef) == shieldBefore - 4
                        && GearData.durability(player.getItemBySlot(EquipmentSlot.CHEST), cuirassDef) == cuirassBefore - 4,
                "最大HPの30%のダメージで3、ダメージ0で0減っていません：" + GearData.durability(player.getOffhandItem(), shieldDef));
        check(helper, GearService.armorLoss(gearRules, 0.05, 0.49) == 1 && GearService.armorLoss(gearRules, 0.05, 0.51) == 0
                && GearService.armorLoss(gearRules, 0.25, 0.99) == 2, "端数の耐久の減り方（確率）がおかしいです");
        check(helper, GearData.durability(player.getMainHandItem(), swordDef) == 398, "敵の攻撃で武器の耐久が減っています");

        // 壊れる：攻撃のステータスは何も持たないときと同じになるが、品はなくならない
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        equip(player, data, ItemStack.EMPTY, ItemStack.EMPTY);
        double bare = data.getStats().get(StatType.ATTACK);
        GearData.setDurability(sword, swordDef, 1);
        equip(player, data, sword, ItemStack.EMPTY);
        check(helper, data.getStats().get(StatType.ATTACK) > bare, "耐久1の剣の攻撃が効いていません");
        GearService.wearWeapon(player, false);
        check(helper, GearData.isBroken(player.getMainHandItem(), swordDef) && !player.getMainHandItem().isEmpty(),
                "耐久0で壊れていない、または品がなくなっています");
        LevelService.recompute(player, data);
        expectStat(helper, data, StatType.ATTACK, bare, "壊れた剣");
        GearService.wearWeapon(player, false);
        check(helper, GearData.durability(player.getMainHandItem(), swordDef) == 0, "壊れた品の耐久が0より下がりました");

        // 壊れない装備（見習い装備）は減らない
        ItemStack kit = new ItemStack(ModItems.APPRENTICE_SWORD.get());
        equip(player, data, kit, ItemStack.EMPTY);
        GearService.wearWeapon(player, false);
        check(helper, !GearData.isBroken(player.getMainHandItem(), gearDef("apprentice_sword")), "見習いの剣が壊れました");
        helper.succeed();
    }

    /** ステータス：レベル・職業の条件を満たし、壊れていない装備だけが効く。防具も同じ。 */
    @GameTest(template = EMPTY)
    public static void gearStatsFollowRequirements(GameTestHelper helper) {
        FakePlayer player = fake(helper, "gear_stats");
        PlayerData data = knight(player, 10);
        equip(player, data, ItemStack.EMPTY, ItemStack.EMPTY);
        // 11月24日：レベルで上がる攻撃は1レベルにつき0.9（レベル10で18）
        expectStat(helper, data, StatType.ATTACK, 18, "何も持たないレベル10のナイト");
        equip(player, data, rolled("ancient_stone_sword", Rarity.COMMON, 1.0, 0), ItemStack.EMPTY);
        expectStat(helper, data, StatType.ATTACK, 32, "古代石の剣（コモン・100%）");
        equip(player, data, rolled("ancient_stone_sword", Rarity.RARE, 1.0, 5), ItemStack.EMPTY);
        expectStat(helper, data, StatType.ATTACK, 37, "古代石の剣（レア・100%・+5）");
        // 両手剣：もう片方の手の盾は効かない
        equip(player, data, rolled("ancient_stone_greatsword", Rarity.COMMON, 1.0, 0),
                rolled("ancient_stone_shield", Rarity.COMMON, 1.0, 0));
        double greatsword = data.getStats().get(StatType.DEFENSE);
        equip(player, data, ItemStack.EMPTY, ItemStack.EMPTY);
        expectStat(helper, data, StatType.DEFENSE, greatsword, "両手剣と盾（盾が効いている）");

        // 必要レベルに届かない（古代石の剣はLv10から。11月24日にレベルの上限を上げ、必要レベルも合わせた）
        LevelService.setLevel(player, data, 9);
        equip(player, data, ItemStack.EMPTY, ItemStack.EMPTY);
        double bare = data.getStats().get(StatType.ATTACK);
        equip(player, data, rolled("ancient_stone_sword", Rarity.COMMON, 1.0, 0), ItemStack.EMPTY);
        expectStat(helper, data, StatType.ATTACK, bare, "レベル9で持った古代石の剣");

        // 職業が違う（ナイトの剣を持ったウィザード）
        LevelService.setLevel(player, data, 10);
        JobService.setJobForced(player, data, job("wizard"));
        equip(player, data, ItemStack.EMPTY, ItemStack.EMPTY);
        double wizard = data.getStats().get(StatType.ATTACK);
        equip(player, data, rolled("ancient_stone_sword", Rarity.COMMON, 1.0, 0), ItemStack.EMPTY);
        expectStat(helper, data, StatType.ATTACK, wizard, "ウィザードが持ったナイトの剣");
        equip(player, data, rolled("ancient_stone_staff", Rarity.COMMON, 1.0, 0), ItemStack.EMPTY);
        check(helper, data.getStats().get(StatType.MAGIC) > 0 && data.getStats().get(StatType.ATTACK) <= wizard + EPSILON,
                "ウィザードの杖の数値がおかしいです");

        // 防具：着ると防御が上がり、壊れると戻る
        JobService.setJobForced(player, data, job("knight"));
        equip(player, data, ItemStack.EMPTY, ItemStack.EMPTY);
        double defense = data.getStats().get(StatType.DEFENSE);
        EquipmentDef cuirassDef = gearDef("ancient_stone_cuirass");
        ItemStack cuirass = rolled("ancient_stone_cuirass", Rarity.COMMON, 1.0, 0);
        player.setItemSlot(EquipmentSlot.CHEST, cuirass);
        LevelService.recompute(player, data);
        check(helper, data.getStats().get(StatType.DEFENSE) > defense, "古代石の胴鎧で防御が上がっていません");
        GearData.setDurability(cuirass, cuirassDef, 0);
        LevelService.recompute(player, data);
        expectStat(helper, data, StatType.DEFENSE, defense, "壊れた胴鎧");
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        LevelService.recompute(player, data);
        helper.succeed();
    }

    /** 特殊効果：欠け牙の大剣の「処刑」は、相手のHPが50%以下のときだけ与えるダメージ+12%。壊れると効かない。 */
    @GameTest(template = EMPTY)
    public static void gearExecuteEffect(GameTestHelper helper) {
        FakePlayer player = fake(helper, "execute");
        PlayerData data = knight(player, 10);
        ItemStack fang = rolled("chipped_fang_greatsword", Rarity.RARE, 1.0, 0);
        equip(player, data, fang, ItemStack.EMPTY);
        AmemaruMob mob = helper.spawnWithNoFreeWill(ModEntities.TSUNOMOFU.get(), new BlockPos(1, 1, 1));
        try {
            mob.setEnemyHp(mob.getEnemyMaxHp());
            double full = GearEffects.outgoingMultiplier(player, mob, false);
            check(helper, Math.abs(full - 1.0) < EPSILON, "HPが満タンの相手に処刑が効いています：" + full);
            mob.setEnemyHp(mob.getEnemyMaxHp() * 0.4);
            double low = GearEffects.outgoingMultiplier(player, mob, false);
            check(helper, Math.abs(low - 1.12) < EPSILON, "HP40%の相手への処刑が+12%ではありません：" + low);
            check(helper, Math.abs(GearEffects.outgoingMultiplier(player, mob, true) - 1.12) < EPSILON,
                    "スキルの攻撃に処刑が効いていません");
            GearData.setDurability(fang, gearDef("chipped_fang_greatsword"), 0);
            check(helper, Math.abs(GearEffects.outgoingMultiplier(player, mob, false) - 1.0) < EPSILON, "壊れた大剣の処刑が効いています");
        } finally {
            mob.discard();
        }
        helper.succeed();
    }

    /**
     * 水門の鍵盾（3階のボスのレア装備・エピック）：ナイトの左手の盾。持つと防御とHPが上がり、
     * 固有効果「水の膜」で敵から受けるダメージ −6%。必要レベル（15。11月24日に7から）に届かない・壊れていると効かない。
     */
    @GameTest(template = EMPTY)
    public static void floodgateKeyShieldWaterVeil(GameTestHelper helper) {
        EquipmentDef def = gearDef("floodgate_key_shield");
        check(helper, def.rarity() == Rarity.EPIC, "水門の鍵盾がエピックではありません：" + def.rarity());
        check(helper, def.rare() && def.slot() == GearSlot.OFFHAND, "水門の鍵盾が「左手のレア装備」になっていません");
        check(helper, item("floodgate_key_shield") instanceof AmemaruShieldItem,
                "水門の鍵盾が盾のアイテムではありません");

        FakePlayer player = fake(helper, "floodgate");
        PlayerData data = knight(player, 15);
        double bareDefense = data.getStats().get(StatType.DEFENSE);
        check(helper, Math.abs(GearEffects.incomingMultiplier(player, data) - 1.0) < EPSILON, "盾を持つ前から、受けるダメージが減っています");
        ItemStack shield = rolled("floodgate_key_shield", Rarity.EPIC, 1.0, 0);
        equip(player, data, ItemStack.EMPTY, shield);
        check(helper, data.getStats().get(StatType.DEFENSE) > bareDefense, "水門の鍵盾で防御が上がっていません");
        double veil = GearEffects.incomingMultiplier(player, data);
        check(helper, Math.abs(veil - 0.94) < EPSILON, "水の膜で、受けるダメージが −6% になっていません：" + veil);

        // 必要レベルに届かないと効かない
        LevelService.setLevel(player, data, 14);
        LevelService.recompute(player, data);
        check(helper, Math.abs(GearEffects.incomingMultiplier(player, data) - 1.0) < EPSILON, "Lv14で水の膜が効いています");
        LevelService.setLevel(player, data, 15);
        // 壊れると効かない
        GearData.setDurability(player.getOffhandItem(), def, 0);
        LevelService.recompute(player, data);
        check(helper, Math.abs(GearEffects.incomingMultiplier(player, data) - 1.0) < EPSILON, "壊れた盾の水の膜が効いています");
        helper.succeed();
    }

    // ================================================================ 工房

    /** 生産職になる・作る（作り手の名前・熟練度）・強化（失敗しない）・修理（職人でなければ素材2倍）・生産職を変える。 */
    @GameTest(template = EMPTY)
    public static void workshopCraftEnhanceRepair(GameTestHelper helper) {
        FakePlayer smith = fake(helper, "smith");
        PlayerData data = data(smith);
        AdventureData adventure = data.getAdventure();
        smith.getInventory().clearContent();
        ResourceLocation recipe = id("workshop/ancient_stone_sword");
        ResourceLocation weaponsmith = id("weaponsmith");

        give(smith, "moss_iron", 5);
        give(smith, "crystal_shard", 2);
        check(helper, !WorkshopService.craft(smith, WorkshopType.WEAPON, recipe), "生産職でないのに作れました");
        check(helper, count(smith, "moss_iron") == 5, "作れなかったのに素材が減りました");
        check(helper, WorkshopService.chooseJob(smith, WorkshopType.WEAPON), "武器職人になれません");
        check(helper, weaponsmith.equals(adventure.productionJob()), "生産職が武器職人になっていません：" + adventure.productionJob());
        check(helper, WorkshopService.mastery(smith, data, WorkshopType.WEAPON) == 1, "最初の熟練度が1ではありません");
        check(helper, WorkshopService.mastery(smith, data, WorkshopType.ARMOR) == 0, "ほかの工房の熟練度が0ではありません");

        check(helper, WorkshopService.craft(smith, WorkshopType.WEAPON, recipe), "古代石の剣が作れません");
        check(helper, count(smith, "moss_iron") == 0 && count(smith, "crystal_shard") == 0, "素材（苔鉄5・結晶の欠片2）が使われていません");
        ItemStack made = find(smith, "ancient_stone_sword");
        check(helper, !made.isEmpty() && GearData.isRolled(made), "作った剣が持ち物にない、または抽選されていません");
        check(helper, smith.getGameProfile().getName().equals(GearData.crafterName(made)) && smith.getUUID().equals(GearData.crafterId(made)),
                "作った剣に作り手が入っていません：" + GearData.crafterName(made));
        check(helper, adventure.masteryExp(weaponsmith) == 10 && adventure.crafted() == 1,
                "熟練度の経験（10）・作った数（1）が合いません：" + adventure.masteryExp(weaponsmith) + "・" + adventure.crafted());
        check(helper, !WorkshopService.craft(smith, WorkshopType.WEAPON, recipe), "素材がないのに作れました");
        give(smith, "ancient_stone", 20);
        give(smith, "moss_iron", 20);
        check(helper, !WorkshopService.craft(smith, WorkshopType.ARMOR, id("workshop/ancient_stone_cuirass")),
                "武器職人が防具を作れました");
        smith.getInventory().clearContent();

        // 強化：+1は苔鉄1、+4は苔鉄4と結晶の欠片1。失敗しない
        EquipmentDef def = gearDef("ancient_stone_sword");
        check(helper, !WorkshopService.enhance(smith, WorkshopType.WEAPON, made), "素材がないのに強化できました");
        give(smith, "moss_iron", 1);
        check(helper, WorkshopService.enhance(smith, WorkshopType.WEAPON, made) && GearData.enhance(made) == 1, "+1に強化できません");
        check(helper, count(smith, "moss_iron") == 0, "+1の強化で苔鉄1が使われていません");
        GearData.setEnhance(made, 3);
        List<EquipmentDef.Cost> cost = WorkshopService.enhanceCost(made, def, EquipmentDefs.server().rules());
        check(helper, cost.size() == 2 && cost.get(0).count() == 4 && cost.get(1).count() == 1, "+4の強化の素材が苔鉄4・結晶の欠片1ではありません：" + cost);
        give(smith, "moss_iron", 4);
        give(smith, "crystal_shard", 1);
        check(helper, WorkshopService.enhance(smith, WorkshopType.WEAPON, made) && GearData.enhance(made) == 4, "+4に強化できません");
        check(helper, count(smith, "moss_iron") == 0 && count(smith, "crystal_shard") == 0, "+4の強化の素材が使われていません");
        GearData.setEnhance(made, 10);
        give(smith, "moss_iron", 20);
        give(smith, "crystal_shard", 20);
        check(helper, !WorkshopService.enhance(smith, WorkshopType.WEAPON, made) && GearData.enhance(made) == 10, "+10より上に強化できました");
        ItemStack cuirass = rolled("ancient_stone_cuirass", Rarity.COMMON, 1.0, 0);
        check(helper, !WorkshopService.enhance(smith, WorkshopType.ARMOR, cuirass), "武器職人が防具を強化できました");
        smith.getInventory().clearContent();

        // 修理：耐久が半分なら苔鉄2の半分＝1。職人でない人は2倍の2
        GearData.setDurability(made, def, 200);
        List<EquipmentDef.Cost> repair = WorkshopService.repairCost(made, def, WorkshopService.repairMultiplier(data, WorkshopType.WEAPON));
        check(helper, repair.size() == 1 && repair.get(0).count() == 1, "職人の修理の素材が苔鉄1ではありません：" + repair);
        FakePlayer stranger = fake(helper, "stranger");
        PlayerData strangerData = data(stranger);
        strangerData.getAdventure().setProductionJob(null);
        List<EquipmentDef.Cost> strangerCost = WorkshopService.repairCost(made, def,
                WorkshopService.repairMultiplier(strangerData, WorkshopType.WEAPON));
        check(helper, strangerCost.size() == 1 && strangerCost.get(0).count() == 2, "職人でない人の修理の素材が苔鉄2ではありません：" + strangerCost);
        check(helper, !WorkshopService.repair(smith, WorkshopType.WEAPON, made), "素材がないのに修理できました");
        give(smith, "moss_iron", 1);
        check(helper, WorkshopService.repair(smith, WorkshopType.WEAPON, made) && GearData.durability(made, def) == 400, "修理で耐久が400に戻りません");
        check(helper, count(smith, "moss_iron") == 0, "修理の素材が使われていません");
        check(helper, !WorkshopService.repair(smith, WorkshopType.WEAPON, made), "耐久が満タンなのに修理できました");

        // 生産職を変える：いつでも無料（戦闘職のお試し期間の後でも、転職の証は要らない）。熟練度は職業ごとに別
        ProgressionData progression = ProgressionData.get(helper.getLevel().getServer());
        int originalDay = progression.day();
        try {
            progression.setDay(ServerConfig.JOB_CHANGE_FREE_UNTIL_DAY.get() + 5);
            check(helper, WorkshopService.chooseJob(smith, WorkshopType.ARMOR), "防具職人に変えられません");
            check(helper, id("armorsmith").equals(adventure.productionJob()) && adventure.productionChanges() == 1,
                    "防具職人に変わっていない、または変えた回数が1ではありません");
            check(helper, WorkshopService.mastery(smith, data, WorkshopType.ARMOR) == 1 && adventure.masteryExp(id("armorsmith")) == 0,
                    "防具職人の熟練度が1から始まっていません（引き継いでいます）");
            check(helper, WorkshopService.mastery(smith, data, WorkshopType.WEAPON) == 0, "武器職人でなくなったのに武器の熟練度があります");
            check(helper, WorkshopService.chooseJob(smith, WorkshopType.WEAPON) && adventure.masteryExp(weaponsmith) == 10,
                    "武器職人に戻したとき、武器職人の熟練度の経験（10）が残っていません");
        } finally {
            progression.setDay(originalDay);
        }
        helper.succeed();
    }

    /** 熟練度：経験の表・章ごとの上限（第1章は3）・上限の手前で止まる・大成功の確率（5%＋差×4%、最大40%）。 */
    @GameTest(template = EMPTY)
    public static void productionMasteryRules(GameTestHelper helper) {
        ProductionRules rules = ProductionRules.current();
        check(helper, rules.level(0, 1) == 1 && rules.level(20, 1) == 2 && rules.level(50, 1) == 3, "熟練度1〜3の経験が0・20・50ではありません");
        check(helper, rules.level(100, 1) == 3 && rules.level(100, 2) == 4 && rules.level(950, 4) == 10,
                "章ごとの熟練度の上限（3・5・8・10）が効いていません");
        check(helper, rules.addExp(90, 50, 1) == 99, "第1章で、熟練度4の手前（99）で止まりません：" + rules.addExp(90, 50, 1));
        check(helper, rules.addExp(90, 50, 2) == 140, "第2章で経験が足されません");
        check(helper, Math.abs(rules.greatChance(1, 1) - 5.0) < EPSILON && Math.abs(rules.greatChance(3, 1) - 13.0) < EPSILON
                && Math.abs(rules.greatChance(10, 1) - 40.0) < EPSILON, "大成功の確率が5%・13%・40%ではありません");
        helper.succeed();
    }

    // ================================================================ アクセサリー

    /** 抽選：メインは「上限 × 70〜100%」、サブは1〜4個（30・40・25・5%）で重ならない。完璧はサブ4つが95%以上。 */
    @GameTest(template = EMPTY)
    public static void accessoryRollsFollowRules(GameTestHelper helper) {
        AccessoryRules rules = AccessoryRules.current();
        AccessoryDef ring = accessoryDef("moss_iron_battle_ring");
        check(helper, Math.abs(rules.mainCap(StatType.ATTACK, 3) - 12.0) < EPSILON && Math.abs(rules.mainCap(StatType.ATTACK, 1) - 4.8) < EPSILON,
                "攻撃のメインの上限が第3章12・第1章4.8ではありません");
        RandomSource random = RandomSource.create(1118L);
        int[] counts = new int[5];
        for (int i = 0; i < 2000; i++) {
            ItemStack stack = stack("moss_iron_battle_ring");
            AccessoryRoller.roll(stack, ring, rules, random, false, null, 0);
            AccessoryData.StatLine main = AccessoryData.main(stack);
            check(helper, main != null && main.type() == StatType.ATTACK, "戦いの指輪のメインが攻撃ではありません");
            check(helper, main.value() >= 4 && main.value() <= 5, "第1章の攻撃のメインが4〜5の外です：" + main.value());
            List<AccessoryData.StatLine> subs = AccessoryData.subs(stack);
            check(helper, subs.size() >= 1 && subs.size() <= 4, "サブの数が1〜4の外です：" + subs.size());
            counts[subs.size()]++;
            Set<StatType> seen = EnumSet.of(main.type());
            for (AccessoryData.StatLine sub : subs) {
                check(helper, seen.add(sub.type()), "サブがメインやほかのサブと重なっています：" + sub.type());
                double cap = sub.type().isPercent() ? Math.max(0.1, Math.round(rules.subCap(sub.type(), 1) * 10.0) / 10.0)
                        : Math.max(1.0, Math.round(rules.subCap(sub.type(), 1)));
                check(helper, Math.abs(sub.max() - cap) < EPSILON, sub.type() + " のサブの上限が " + cap + " ではありません：" + sub.max());
                check(helper, sub.value() >= sub.min() - EPSILON && sub.value() <= sub.max() + EPSILON,
                        sub.type() + " のサブが幅の外です：" + sub.value());
            }
            int quality = AccessoryData.quality(stack, rules);
            check(helper, quality >= 1 && quality <= 5 && AccessoryData.storedQuality(stack) == quality, "品質が記録されていません");
        }
        check(helper, counts[2] > counts[1] && counts[1] > counts[3] && counts[3] > counts[4] && counts[4] > 0,
                "サブの数の出やすさが2個>1個>3個>4個になっていません：1個" + counts[1] + "・2個" + counts[2] + "・3個" + counts[3]
                        + "・4個" + counts[4]);
        // 大成功：サブが一つ多い（2個以上）
        for (int i = 0; i < 200; i++) {
            ItemStack stack = stack("moss_iron_battle_ring");
            AccessoryRoller.roll(stack, ring, rules, random, true, null, 0);
            check(helper, AccessoryData.subs(stack).size() >= 2 && AccessoryData.isGreat(stack), "大成功のサブが2個以上ではありません");
        }
        // 完璧：サブ4つで、すべて95%以上
        ItemStack perfect = stack("moss_iron_battle_ring");
        AccessoryRoller.roll(perfect, ring, rules, random, false, null, 4);
        check(helper, AccessoryData.subs(perfect).size() == 4, "サブの数を4に決めて抽選できません");
        AccessoryRoller.forceRatio(perfect, rules, 1.0);
        check(helper, AccessoryData.isPerfect(perfect, rules) && AccessoryData.storedPerfect(perfect)
                && AccessoryData.quality(perfect, rules) == 5, "サブ4つ・100%が完璧（★5）になっていません");
        AccessoryRoller.forceRatio(perfect, rules, 0.6);
        check(helper, !AccessoryData.isPerfect(perfect, rules), "割合が低いのに完璧になっています");
        ItemStack three = stack("moss_iron_battle_ring");
        AccessoryRoller.roll(three, ring, rules, random, false, null, 3);
        AccessoryRoller.forceRatio(three, rules, 1.0);
        check(helper, !AccessoryData.isPerfect(three, rules), "サブ3つで完璧になっています");
        helper.succeed();
    }

    /** 付けると数値が足され、外すと戻る。指輪は2つ。留め具の枠は11日目から開く。 */
    @GameTest(template = EMPTY)
    public static void accessoriesAddStatsWhenWorn(GameTestHelper helper) {
        FakePlayer player = fake(helper, "accessory");
        PlayerData data = knight(player, 10);
        equip(player, data, ItemStack.EMPTY, ItemStack.EMPTY);
        expectStat(helper, data, StatType.ATTACK, 18, "何も付けないレベル10のナイト");
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        check(helper, handler != null, "Curios の持ち物がありません");
        for (AccessorySlot slot : AccessorySlot.values()) {
            check(helper, handler.getCurios().containsKey(slot.curiosId()), "アクセサリーの枠がありません：" + slot.curiosId());
        }
        ICurioStacksHandler rings = handler.getCurios().get(AccessorySlot.RING.curiosId());
        check(helper, rings.getSlots() == 2, "指輪の枠が2つではありません：" + rings.getSlots());

        AccessoryRules rules = AccessoryRules.current();
        AccessoryDef def = accessoryDef("moss_iron_battle_ring");
        RandomSource random = RandomSource.create(77L);
        ItemStack first = stack("moss_iron_battle_ring");
        AccessoryRoller.roll(first, def, rules, random, false, null, 2);
        AccessoryRoller.forceRatio(first, rules, 1.0);
        ItemStack second = stack("moss_iron_battle_ring");
        AccessoryRoller.roll(second, def, rules, random, false, null, 1);
        AccessoryRoller.forceRatio(second, rules, 1.0);
        try {
            rings.getStacks().setStackInSlot(0, first);
            LevelService.recompute(player, data);
            expectStat(helper, data, StatType.ATTACK, 23, "戦いの指輪（攻撃+5）を1つ");
            rings.getStacks().setStackInSlot(1, second);
            LevelService.recompute(player, data);
            expectStat(helper, data, StatType.ATTACK, 28, "戦いの指輪（攻撃+5）を2つ");
            check(helper, AccessoryService.equipped(player).size() == 2, "付けているアクセサリーが2つではありません");
        } finally {
            rings.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            rings.getStacks().setStackInSlot(1, ItemStack.EMPTY);
        }
        LevelService.recompute(player, data);
        expectStat(helper, data, StatType.ATTACK, 18, "指輪を外した後");

        // 留め具：11日目から1枠
        ICurioStacksHandler clasp = handler.getCurios().get(AccessorySlot.CLASP.curiosId());
        ProgressionData progression = ProgressionData.get(helper.getLevel().getServer());
        int originalDay = progression.day();
        try {
            progression.setDay(1);
            AccessoryService.updateClaspSlot(player);
            clasp.update();
            check(helper, clasp.getSlots() == 0 && !AccessoryService.claspOpen(player), "1日目に留め具の枠が開いています");
            progression.setDay(ServerConfig.CLASP_UNLOCK_DAY.get());
            AccessoryService.updateClaspSlot(player);
            clasp.update();
            check(helper, clasp.getSlots() == 1 && AccessoryService.claspOpen(player),
                    ServerConfig.CLASP_UNLOCK_DAY.get() + "日目に留め具の枠が開いていません：" + clasp.getSlots());
        } finally {
            progression.setDay(originalDay);
            AccessoryService.updateClaspSlot(player);
            clasp.update();
        }
        helper.succeed();
    }

    /** 結晶果：1つでステータスポイント1つ分。上限（15）を超えては食べられない。 */
    @GameTest(template = EMPTY)
    public static void crystalFruitsRaiseStats(GameTestHelper helper) {
        FakePlayer player = fake(helper, "fruits");
        PlayerData data = knight(player, 10);
        data.getAdventure().setFruits(StatType.ATTACK, 0);
        equip(player, data, ItemStack.EMPTY, ItemStack.EMPTY);
        // 11月24日：レベル10のナイトの攻撃は18（レベルで上がるのは1レベルにつき0.9）
        expectStat(helper, data, StatType.ATTACK, 18, "結晶果を食べる前");
        ServerLevel level = helper.getLevel();
        try {
            ItemStack fruits = new ItemStack(item("crystal_fruit_crimson"), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, fruits);
            for (int i = 0; i < 3; i++) {
                player.getMainHandItem().getItem().use(level, player, InteractionHand.MAIN_HAND);
            }
            check(helper, data.getAdventure().fruits(StatType.ATTACK) == 3 && player.getMainHandItem().isEmpty(),
                    "紅の結晶果を3つ食べられていません：" + data.getAdventure().fruits(StatType.ATTACK));
            expectStat(helper, data, StatType.ATTACK, 21, "紅の結晶果を3つ（攻撃+1ずつ）");

            int cap = ServerConfig.FRUIT_CAP.get();
            data.getAdventure().setFruits(StatType.ATTACK, cap);
            ItemStack more = new ItemStack(item("crystal_fruit_crimson"));
            player.setItemInHand(InteractionHand.MAIN_HAND, more);
            InteractionResultHolder<ItemStack> result = more.getItem().use(level, player, InteractionHand.MAIN_HAND);
            check(helper, result.getResult() == InteractionResult.FAIL && more.getCount() == 1
                    && data.getAdventure().fruits(StatType.ATTACK) == cap, "上限（" + cap + "）を超えて食べられました");
        } finally {
            data.getAdventure().setFruits(StatType.ATTACK, 0);
            equip(player, data, ItemStack.EMPTY, ItemStack.EMPTY);
        }
        helper.succeed();
    }

    /** 素早さ：基礎値を超えた1あたり、移動と攻撃の速さ+0.5%（最大25%）・回避0.2%（最大10%）。 */
    @GameTest(template = EMPTY)
    public static void speedRaisesMovementAndDodge(GameTestHelper helper) {
        FakePlayer player = fake(helper, "speed");
        PlayerData data = knight(player, 10);
        LevelService.resetAllocation(player, data);
        data.getAdventure().setFruits(StatType.SPEED, 0);
        LevelService.recompute(player, data);
        GrowthDef growth = GrowthRules.current();
        double baseMove = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        try {
            check(helper, SpeedService.movePercent(data, growth) == 0 && SpeedService.dodgeChance(data, growth) == 0,
                    "素早さを上げていないのに素早さの効果があります");
            check(helper, Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseMove) < EPSILON, "移動の速さが変わっています");
            // 11月24日：ステータスの振り分けはなくなった。結晶果（1つで素早さ+1）で上げる
            data.getAdventure().setFruits(StatType.SPEED, 20);
            LevelService.recompute(player, data);
            check(helper, Math.abs(SpeedService.movePercent(data, growth) - 10.0) < EPSILON
                    && Math.abs(SpeedService.attackSpeedPercent(data, growth) - 10.0) < EPSILON
                    && Math.abs(SpeedService.dodgeChance(data, growth) - 4.0) < EPSILON, "素早さ+20で、移動・攻撃+10%・回避4%になっていません");
            check(helper, Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseMove * 1.10) < 1.0E-4,
                    "移動の速さの属性が+10%になっていません：" + player.getAttributeValue(Attributes.MOVEMENT_SPEED));
            data.getAdventure().setFruits(StatType.SPEED, 100);
            LevelService.recompute(player, data);
            check(helper, Math.abs(SpeedService.movePercent(data, growth) - 25.0) < EPSILON
                    && Math.abs(SpeedService.dodgeChance(data, growth) - 10.0) < EPSILON, "素早さの効果の上限（25%・10%）が効いていません");
        } finally {
            data.getAdventure().setFruits(StatType.SPEED, 0);
            LevelService.resetAllocation(player, data);
            LevelService.recompute(player, data);
        }
        check(helper, Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseMove) < EPSILON, "素早さを戻しても移動の速さが戻りません");
        helper.succeed();
    }

    // ================================================================ 成長（章の上限・結晶の欠片・追いつき補正）

    /** 第1章はLv30で止まり、あふれた経験値は200ごとに結晶の欠片になる。6日目からLv55まで（11月24日）。中央値と追いつき補正。 */
    @GameTest(template = EMPTY)
    public static void levelCapShardsAndCatchUp(GameTestHelper helper) {
        GrowthDef growth = GrowthRules.current();
        check(helper, growth.chapterForDay(1) == 1 && growth.chapterForDay(5) == 1 && growth.chapterForDay(6) == 2
                && growth.chapterForDay(11) == 3 && growth.chapterForDay(16) == 4 && growth.chapterForDay(20) == 4, "日と章の対応が違います");
        check(helper, growth.levelCap(1) == 30 && growth.levelCap(2) == 55 && growth.levelCap(3) == 80 && growth.levelCap(4) == 100,
                "章のレベルの上限が30・55・80・100ではありません");
        // 11月24日：レベルの幅が倍になったので、追いつき補正のレベル差も倍に
        check(helper, growth.catchUpBonus(9) == 0 && growth.catchUpBonus(10) == 50 && growth.catchUpBonus(19) == 50
                && growth.catchUpBonus(20) == 100, "追いつき補正が「10以上低い+50%・20以上低い+100%」ではありません");

        ProgressionData sample = ProgressionData.load(new CompoundTag());
        check(helper, sample.medianLevel() == 1, "記録がないときの中央値が1ではありません");
        sample.recordLevel(UUID.randomUUID(), 10);
        sample.recordLevel(UUID.randomUUID(), 30);
        sample.recordLevel(UUID.randomUUID(), 20);
        check(helper, sample.medianLevel() == 20, "3人の中央値が20ではありません：" + sample.medianLevel());
        sample.recordLevel(UUID.randomUUID(), 40);
        check(helper, sample.medianLevel() == 20, "4人の中央値（低い方）が20ではありません：" + sample.medianLevel());

        FakePlayer player = fake(helper, "level_cap");
        PlayerData data = data(player);
        player.getInventory().clearContent();
        data.getAdventure().setOverflowExp(0);
        ProgressionData progression = ProgressionData.get(helper.getLevel().getServer());
        int originalDay = progression.day();
        try {
            // 11月24日：章ごとのレベルの上限は 30・55・80・100
            progression.setDay(1);
            LevelService.setLevel(player, data, 30);
            LevelService.addExp(player, data, 450, LevelService.ExpSource.ADMIN);
            check(helper, data.getLevel() == 30 && data.getExp() == 0, "第1章でLv30を超えました：Lv" + data.getLevel());
            check(helper, count(player, ModItems.CRYSTAL_SHARD.get()) == 2 && data.getAdventure().overflowExp() == 50,
                    "あふれた経験値450が、結晶の欠片2つと残り50になっていません：" + count(player, ModItems.CRYSTAL_SHARD.get())
                            + "・" + data.getAdventure().overflowExp());
            LevelService.setLevel(player, data, 29);
            LevelService.addExp(player, data, growth.expToNext(29) + 300L, LevelService.ExpSource.ADMIN);
            check(helper, data.getLevel() == 30 && data.getAdventure().overflowExp() == 150,
                    "上限に届いた残りの経験値が結晶の欠片の分に回っていません：Lv" + data.getLevel() + "・" + data.getAdventure().overflowExp());

            progression.setDay(6);
            LevelService.setLevel(player, data, 30);
            LevelService.addExp(player, data, growth.expToNext(30), LevelService.ExpSource.ADMIN);
            check(helper, data.getLevel() == 31, "6日目（第2章）にLv31へ上がりません：Lv" + data.getLevel());
        } finally {
            progression.setDay(originalDay);
            data.getAdventure().setOverflowExp(0);
        }
        helper.succeed();
    }

    // ================================================================ 敵・ドロップ

    /** 倒した人に経験値・図鑑の記録・その人だけのドロップ。ドロップはほかの人には見えず、拾えない。 */
    @GameTest(template = EMPTY)
    public static void enemyDropsBelongToKiller(GameTestHelper helper) {
        FakePlayer hunter = fake(helper, "hunter");
        FakePlayer other = fake(helper, "bystander");
        PlayerData data = data(hunter);
        hunter.getInventory().clearContent();
        LevelService.setLevel(hunter, data, 1);
        ResourceLocation enemyId = id("tsunomofu");
        int killsBefore = data.getAdventure().bestiary(enemyId) == null ? 0 : data.getAdventure().bestiary(enemyId).kills();
        AmemaruMob mob = helper.spawnWithNoFreeWill(ModEntities.TSUNOMOFU.get(), new BlockPos(1, 1, 1));
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16.0);
        try {
            mob.onDamagedBy(hunter, 40.0);
            check(helper, mob.contributions().containsKey(hunter.getUUID()), "攻撃を当てた人が記録されていません");
            // ふつうのドロップは確率なので、10回倒して1つも出ないことはまずない（約0.00005%）
            for (int i = 0; i < 10; i++) {
                DropService.onEnemyDeath(mob, hunter.damageSources().playerAttack(hunter));
            }
            AdventureData.BestiaryEntry entry = data.getAdventure().bestiary(enemyId);
            check(helper, entry != null && entry.kills() == killsBefore + 10, "図鑑の倒した数が10増えていません");
            check(helper, data.getLevel() > 1 || data.getExp() > 0, "経験値が入っていません");
            List<LootItemEntity> drops = helper.getLevel().getEntitiesOfClass(LootItemEntity.class, area);
            check(helper, !drops.isEmpty(), "ドロップが1つも出ていません");
            for (LootItemEntity drop : drops) {
                check(helper, hunter.getUUID().equals(drop.owner()) && drop.visibleTo(hunter) && !drop.visibleTo(other),
                        "ドロップの持ち主が倒した人ではありません");
                ResourceLocation item = ForgeRegistries.ITEMS.getKey(drop.getItem().getItem());
                check(helper, entry.drops().contains(item), "図鑑に手に入れた品が記録されていません：" + item);
            }
            LootItemEntity first = drops.get(0);
            first.setNoPickUpDelay();
            Item dropped = first.getItem().getItem();
            first.playerTouch(other);
            check(helper, count(other, dropped) == 0 && first.isAlive(), "ほかの人がドロップを拾えました");
            first.playerTouch(hunter);
            check(helper, count(hunter, dropped) > 0, "倒した人がドロップを拾えません");
        } finally {
            mob.discard();
            for (LootItemEntity drop : helper.getLevel().getEntitiesOfClass(LootItemEntity.class, area)) {
                drop.discard();
            }
        }
        helper.succeed();
    }

    /** レアドロップの確率（定義 × 倍率 × 天井）・天井の数え方・告知の境目・経験値の山分け。 */
    @GameTest(template = EMPTY)
    public static void rareDropChanceAndPity(GameTestHelper helper) {
        EnemyDef wolf = EnemyDefs.forType(ModEntities.KAKEKIBA_WOLF.get());
        check(helper, wolf != null && !wolf.rare().isEmpty(), "欠け牙の狼のレアドロップがありません");
        EnemyDef.RareDrop fang = wolf.rare().get(0);
        check(helper, fang.item().equals(id("chipped_fang_greatsword")) && Math.abs(fang.chance() - 0.8) < EPSILON,
                "欠け牙の大剣のレアドロップが0.8%ではありません");
        double multiplier = ServerConfig.RARE_DROP_MULTIPLIER.get();
        double step = ServerConfig.PITY_STEP.get();
        check(helper, Math.abs(DropService.rareChance(fang, 0) - 0.8 * multiplier) < EPSILON, "天井なしの確率が違います");
        check(helper, Math.abs(DropService.rareChance(fang, 5) - 0.8 * multiplier * (1.0 + 5 * step / 100.0)) < EPSILON,
                "5回外れた後の確率が違います（外れるたびに+" + step + "%）");
        check(helper, DropService.nextPity(3, false) == 4 && DropService.nextPity(3, true) == 0, "天井の数え方が違います");
        check(helper, DropService.announces(0.8) && DropService.announces(ServerConfig.RARE_ANNOUNCE_THRESHOLD.get())
                && !DropService.announces(ServerConfig.RARE_ANNOUNCE_THRESHOLD.get() + 0.1), "告知の境目（3%以下）が違います");
        check(helper, DropService.expShare(7, 1) == 7 && DropService.expShare(7, 2) == 4 && DropService.expShare(6, 4) == 2
                && DropService.expShare(0, 3) == 0, "経験値の山分け（人数で割って切り上げ）が違います");
        helper.succeed();
    }

    /** 敵視：与えたダメージの多い人が上。挑発した人は一番上に置かれ、狙われる。 */
    @GameTest(template = EMPTY)
    public static void enemyThreatAndTaunt(GameTestHelper helper) {
        FakePlayer tank = fake(helper, "tank");
        FakePlayer dealer = fake(helper, "dealer");
        AmemaruMob mob = helper.spawnWithNoFreeWill(ModEntities.KAKEKIBA_WOLF.get(), new BlockPos(1, 1, 1));
        try {
            mob.onDamagedBy(tank, 10.0);
            mob.onDamagedBy(dealer, 30.0);
            check(helper, mob.threatOf(dealer.getUUID()) > mob.threatOf(tank.getUUID()), "ダメージの多い人の敵視が上になっていません");
            check(helper, mob.contributions().size() == 2, "攻撃を当てた2人が記録されていません");
            mob.onTaunted(tank);
            check(helper, mob.threatOf(tank.getUUID()) > mob.threatOf(dealer.getUUID()), "挑発した人の敵視が一番上になっていません");
            check(helper, mob.getTarget() == tank, "挑発した人を狙っていません");
        } finally {
            mob.discard();
        }
        helper.succeed();
    }

    /**
     * 戻る仕組み（11月22日に作り直し）：戦っている間は、押し出されて離れても戻らない。戻るのは、離れすぎてしばらく戦っていない
     * ときか、相手を見失ったとき。戻る間も攻撃は当たり、打たれると戻るのをやめて戦い直す（HPはそのまま）。打たれずに戻れたら満タン。
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void enemyLeashOnlyWhenQuiet(GameTestHelper helper) {
        double leash = 28.0;
        check(helper, AmemaruMob.leashDecision(40 * 40, leash, 10, true) == AmemaruMob.LEASH_STAY,
                "戦っている間に押し出されて離れただけで、戻ろうとします");
        check(helper, AmemaruMob.leashDecision(40 * 40, leash, 20 * 6, true) == AmemaruMob.LEASH_RETURN,
                "離れすぎて5秒戦っていないのに、戻りません");
        check(helper, AmemaruMob.leashDecision(60 * 60, leash, 20 * 3, true) == AmemaruMob.LEASH_RETURN,
                "ひどく離れすぎて2秒戦っていないのに、戻りません");
        check(helper, AmemaruMob.leashDecision(10 * 10, leash, 20 * 60, true) == AmemaruMob.LEASH_STAY,
                "戦い始めた所の近くで相手がいるのに、戻ります");
        check(helper, AmemaruMob.leashDecision(20 * 20, leash, 20, false) == AmemaruMob.LEASH_STAY,
                "相手を見失ってすぐに戻ります（3秒待つ）");
        check(helper, AmemaruMob.leashDecision(20 * 20, leash, 20 * 4, false) == AmemaruMob.LEASH_RETURN,
                "相手を見失っても戻りません");
        check(helper, AmemaruMob.leashDecision(2 * 2, leash, 20 * 4, false) == AmemaruMob.LEASH_FINISH_HERE,
                "戦い始めた所のそばで見失ったのに、歩いて戻ろうとします");
        FakePlayer hunter = fake(helper, "leash");
        AmemaruMob mob = helper.spawnWithNoFreeWill(ModEntities.TSUNOMOFU.get(), new BlockPos(1, 1, 1));
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    double max = mob.getEnemyMaxHp();
                    mob.onDamagedBy(hunter, 5.0);
                    mob.applyCustomDamage(max / 2.0);
                    mob.startReturn();
                    check(helper, mob.isReturning(), "戻り始めません");
                    check(helper, !mob.isInvulnerableTo(mob.damageSources().playerAttack(hunter)), "戻る間に、攻撃が当たりません");
                    mob.onDamagedBy(hunter, 1.0);
                    check(helper, !mob.isReturning() && Math.abs(mob.getEnemyHp() - max / 2.0) < 0.01,
                            "打たれても戻り続けるか、HPが戻りました：" + mob.getEnemyHp() + "/" + max);
                    // 打たれずに戻る（戦い始めた所にいるので、すぐ着く）
                    mob.startReturn();
                })
                .thenExecuteAfter(5, () -> {
                    check(helper, !mob.isReturning() && Math.abs(mob.getEnemyHp() - mob.getEnemyMaxHp()) < 0.01,
                            "戻り終えても、HPが満タンになりません：" + mob.getEnemyHp());
                    mob.discard();
                })
                .thenSucceed();
    }

    /** 敵のHP：定義のHP × config の倍率。独自のHPの割合が、バニラの体力にも映る。 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void enemyHpScalesAndMirrors(GameTestHelper helper) {
        AmemaruMob mob = helper.spawnWithNoFreeWill(ModEntities.ISHIKURE_SOLDIER.get(), new BlockPos(1, 1, 1));
        EnemyDef def = EnemyDefs.forType(ModEntities.ISHIKURE_SOLDIER.get());
        double max = def.hp() * ServerConfig.ENEMY_HP_MULTIPLIER.get();
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    check(helper, Math.abs(mob.getEnemyMaxHp() - max) < 0.01 && Math.abs(mob.getEnemyHp() - max) < 0.01,
                            "石くれ兵のHPが定義の " + max + " ではありません：" + mob.getEnemyHp() + "/" + mob.getEnemyMaxHp());
                    mob.applyCustomDamage(max / 2.0);
                })
                .thenExecuteAfter(3, () -> {
                    double ratio = mob.getHealth() / mob.getMaxHealth();
                    check(helper, Math.abs(ratio - 0.5) < 0.01, "バニラの体力の割合が独自のHPの割合（50%）と合いません：" + ratio);
                    mob.discard();
                })
                .thenSucceed();
    }

    /** 冒険のデータ（結晶果・生産職・熟練度・図鑑・天井・表示・称号）が、保存して読み直しても同じ。 */
    @GameTest(template = EMPTY)
    public static void adventureDataSurvivesSave(GameTestHelper helper) {
        AdventureData data = new AdventureData(() -> {
        });
        data.setFruits(StatType.ATTACK, 3);
        data.setFruits(StatType.HP, 7);
        data.setProductionJob(id("jeweler"));
        data.setMasteryExp(id("jeweler"), 123);
        data.addCrafted(4);
        data.recordKill(id("tsunomofu"));
        data.recordKill(id("tsunomofu"));
        data.recordDrop(id("tsunomofu"), id("tsunomofu_fluff"));
        data.setPity(id("kakekiba_wolf"), 6);
        data.setAccessoryVisible(AccessorySlot.NECKLACE.ordinal(), false);
        data.setOverflowExp(77);
        data.addPendingTitle(AccessoryService.PERFECT_TITLE);

        AdventureData loaded = new AdventureData(() -> {
        });
        loaded.load(data.save());
        check(helper, loaded.fruits(StatType.ATTACK) == 3 && loaded.fruits(StatType.HP) == 7, "結晶果が保存されていません");
        check(helper, id("jeweler").equals(loaded.productionJob()) && loaded.masteryExp(id("jeweler")) == 123 && loaded.crafted() == 4,
                "生産職・熟練度・作った数が保存されていません");
        AdventureData.BestiaryEntry entry = loaded.bestiary(id("tsunomofu"));
        check(helper, entry != null && entry.kills() == 2 && entry.drops().contains(id("tsunomofu_fluff")), "図鑑が保存されていません");
        check(helper, loaded.pity(id("kakekiba_wolf")) == 6, "天井の数が保存されていません");
        check(helper, !loaded.accessoryVisible(AccessorySlot.NECKLACE.ordinal()) && loaded.accessoryVisible(AccessorySlot.CHARM.ordinal()),
                "アクセサリーの表示が保存されていません");
        check(helper, loaded.overflowExp() == 77 && loaded.pendingTitles().contains(AccessoryService.PERFECT_TITLE),
                "あふれた経験値・預かっている称号が保存されていません");
        helper.succeed();
    }

    // ================================================================ 共通

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "am3_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest-phase3:" + name).getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(id, profileName));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    private static PlayerData data(ServerPlayer player) {
        return PlayerDataCapability.get(player).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
    }

    /** そのレベルのナイト（持ち物は空）。 */
    private static PlayerData knight(ServerPlayer player, int level) {
        PlayerData data = data(player);
        LevelService.setLevel(player, data, level);
        JobService.setJobForced(player, data, job("knight"));
        player.getInventory().clearContent();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        LevelService.recompute(player, data);
        return data;
    }

    private static void equip(ServerPlayer player, PlayerData data, ItemStack main, ItemStack off) {
        player.setItemSlot(EquipmentSlot.MAINHAND, main);
        player.setItemSlot(EquipmentSlot.OFFHAND, off);
        LevelService.recompute(player, data);
    }

    private static ResourceLocation id(String path) {
        return AmemaruMod.id(path);
    }

    private static Item item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(id(path));
        if (item == null || !id(path).equals(ForgeRegistries.ITEMS.getKey(item))) {
            throw new GameTestAssertException("アイテムが見つかりません：" + path);
        }
        return item;
    }

    private static ItemStack stack(String path) {
        return new ItemStack(item(path));
    }

    private static EquipmentDef gearDef(String path) {
        EquipmentDef def = EquipmentDefs.server().get(id(path));
        if (def == null) {
            throw new GameTestAssertException("装備の定義が見つかりません：" + path);
        }
        return def;
    }

    private static AccessoryDef accessoryDef(String path) {
        AccessoryDef def = AccessoryDefs.get(item(path));
        if (def == null) {
            throw new GameTestAssertException("アクセサリーの定義が見つかりません：" + path);
        }
        return def;
    }

    /** レアリティ・割合・強化を決めた装備。 */
    private static ItemStack rolled(String path, Rarity rarity, double ratio, int enhance) {
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
        EquipmentDef def = gearDef(path);
        ItemStack stack = stack(path);
        GearData.roll(stack, def, snapshot.rules(), RandomSource.create(1L), rarity, false, null);
        GearData.setAllRatios(stack, def, ratio);
        GearData.setEnhance(stack, enhance);
        return stack;
    }

    private static JobDef job(String name) {
        JobDef job = SkillRegistry.get().job(id(name));
        if (job == null) {
            throw new GameTestAssertException("職業が見つかりません：" + name);
        }
        return job;
    }

    private static void give(ServerPlayer player, String path, int count) {
        player.getInventory().add(new ItemStack(item(path), count));
    }

    private static int count(ServerPlayer player, String path) {
        return count(player, item(path));
    }

    private static int count(ServerPlayer player, Item item) {
        return WorkshopService.count(player, item);
    }

    private static ItemStack find(ServerPlayer player, String path) {
        Item item = item(path);
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void expectValue(GameTestHelper helper, ItemStack stack, EquipmentDef def, double expected, String label) {
        double value = GearData.value(stack, StatType.ATTACK, def, EquipmentDefs.server().rules());
        check(helper, Math.abs(value - expected) < EPSILON, label + "の攻撃が " + expected + " ではありません：" + value);
    }

    private static void expectStat(GameTestHelper helper, PlayerData data, StatType type, double expected, String label) {
        double actual = data.getStats().get(type);
        check(helper, Math.abs(actual - expected) < EPSILON, label + "：" + type.id() + " が " + expected + " ではありません：" + actual);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}

package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModCreativeTabs;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.economy.ShopDef;
import jp.amemaru.lostconnection.economy.ShopDefs;
import jp.amemaru.lostconnection.item.ConsumableDef;
import jp.amemaru.lostconnection.item.ConsumableDefs;
import jp.amemaru.lostconnection.item.ItemBuffService;
import jp.amemaru.lostconnection.player.DownHandler;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 11月29日：キャンディー（しばらくステータスが上がる）と、クリエイティブの6つのタブ。 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CandyGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final String[] CANDIES = {"candy_strength", "candy_wisdom", "candy_iron", "candy_gale", "candy_keen", "candy_vigor",
            "candy_mercy"};

    private CandyGameTests() {
    }

    /** キャンディー7つの数値・効き目の印・パン屋で売っていること。 */
    @GameTest(template = EMPTY)
    public static void candyDefinitions(GameTestHelper helper) {
        ShopDef bakery = ShopDefs.all().get(AmemaruMod.id("bakery"));
        for (String name : CANDIES) {
            ResourceLocation id = AmemaruMod.id(name);
            check(helper, ForgeRegistries.ITEMS.containsKey(id), "キャンディー " + name + " がありません");
            ConsumableDef def = ConsumableDefs.get(id);
            check(helper, def != null && def.buff() != null && def.restore() == null && def.eat() && def.group().equals("candy"),
                    name + " の数値が、ステータスを上げるだけの食べる品になっていません");
            check(helper, def.buff().seconds() == 180 && def.buff().amount() > 0, name + " の時間か上がる量が違います：" + def.buff());
            check(helper, ModEffects.isCandy(ModEffects.candy(def.buff().stat())), name + " の効き目の印がありません");
            check(helper, bakery != null && bakery.sells().stream().anyMatch(e -> e.item().equals(id)), "パン屋で " + name + " を売っていません");
        }
        ConsumableDef strength = ConsumableDefs.get(AmemaruMod.id("candy_strength"));
        check(helper, strength.buff().stat() == StatType.ATTACK && strength.buff().percent(), "剛力キャンディーが攻撃を割合で上げません");
        helper.succeed();
    }

    /**
     * 食べると3分、ステータスが上がる（効き目の印と残りの時間）。満タンでも食べられる。新しく食べると入れかわり（上がるのは一度に1つ）、
     * 同じ組（キャンディー）にクールダウンがかかる。効き目が消えると元に戻る。ダウンの間は食べられない。
     */
    @GameTest(template = EMPTY)
    public static void candyBuffsAndReplaces(GameTestHelper helper) {
        FakePlayer p = fake(helper, "candy_a");
        PlayerData data = PlayerDataCapability.get(p).orElseThrow(() -> new IllegalStateException("no player data"));
        Item strength = item("candy_strength");
        Item keen = item("candy_keen");
        try {
            p.removeAllEffects();
            ItemBuffService.clearForTest(p);
            // 攻撃は整数に切り捨てるので、レベルを上げて、8%が見えるだけの攻撃にする
            data.setLevel(60);
            LevelService.recompute(p, data);
            data.setHp(data.getStats().maxHp());
            double attack = data.getStats().get(StatType.ATTACK);
            double crit = data.getStats().get(StatType.CRIT);
            for (String name : CANDIES) {
                p.getCooldowns().removeCooldown(item(name));
            }

            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(strength, 2));
            strength.use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
            LevelService.recompute(p, data);
            MobEffectInstance effect = p.getEffect(ModEffects.candy(StatType.ATTACK));
            double up = data.getStats().get(StatType.ATTACK);
            check(helper, p.getMainHandItem().getCount() == 1, "HPが満タンのとき、剛力キャンディーが食べられません（減りません）");
            check(helper, effect != null && effect.getDuration() > 170 * 20 && effect.getDuration() <= 180 * 20,
                    "剛力キャンディーの効き目（3分）が付きません：" + effect);
            check(helper, ItemBuffService.active(p) != null && up > attack && Math.abs(up / attack - 1.08) < 0.02,
                    "剛力キャンディーで攻撃が8%上がりません（" + attack + " → " + up + "）");
            check(helper, p.getCooldowns().isOnCooldown(keen), "キャンディーを食べたあと、ほかのキャンディーにクールダウンがかかりません");

            p.getCooldowns().removeCooldown(keen);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(keen, 1));
            keen.use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
            LevelService.recompute(p, data);
            check(helper, !p.hasEffect(ModEffects.candy(StatType.ATTACK)) && p.hasEffect(ModEffects.candy(StatType.CRIT)),
                    "新しく食べたのに、前のキャンディーの効き目が残っているか、新しい効き目が付きません");
            check(helper, Math.abs(data.getStats().get(StatType.ATTACK) - attack) < 1.0E-6
                            && Math.abs(data.getStats().get(StatType.CRIT) - (crit + 4.0)) < 1.0E-6,
                    "入れかわったあとの攻撃・会心が違います（攻撃 " + data.getStats().get(StatType.ATTACK) + "・会心 " + data.getStats().get(StatType.CRIT) + "）");

            // 効き目が消えた（牛乳など）：元に戻る
            p.removeEffect(ModEffects.candy(StatType.CRIT));
            LevelService.recompute(p, data);
            check(helper, ItemBuffService.active(p) == null && Math.abs(data.getStats().get(StatType.CRIT) - crit) < 1.0E-6,
                    "効き目が消えても、会心が上がったままです");

            // ダウンの間は食べられない
            p.getCooldowns().removeCooldown(strength);
            DownHandler.goDown(p, data);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(strength, 1));
            strength.use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
            check(helper, p.getMainHandItem().getCount() == 1 && !p.hasEffect(ModEffects.candy(StatType.ATTACK)), "ダウンの間にキャンディーが食べられました");
            DownHandler.revive(p, data, p);
        } finally {
            ItemBuffService.clearForTest(p);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            for (String name : CANDIES) {
                p.getCooldowns().removeCooldown(item(name));
            }
            p.removeAllEffects();
            data.setLevel(1);
            LevelService.recompute(p, data);
            data.setHp(data.getStats().maxHp());
        }
        helper.succeed();
    }

    /**
     * クリエイティブの6つのタブ（武器・防具＆アクセサリー・建築に使うもの・素材・使用アイテム・スポーンエッグ）：このModのどの品も、
     * どれか1つのタブにだけ入る。前はどのタブにも入っていなかった品（ボスの卵・仕掛けのブロック）も入る。
     */
    @GameTest(template = EMPTY)
    public static void creativeTabsHoldEveryItem(GameTestHelper helper) {
        Map<ModCreativeTabs.Tab, List<Item>> tabs = new EnumMap<>(ModCreativeTabs.Tab.class);
        for (ModCreativeTabs.Tab tab : ModCreativeTabs.Tab.values()) {
            tabs.put(tab, ModCreativeTabs.items(tab));
            check(helper, !tabs.get(tab).isEmpty(), "タブ " + tab + " に品がありません");
        }
        for (RegistryObject<Item> entry : ModItems.ITEMS.getEntries()) {
            int found = 0;
            for (List<Item> list : tabs.values()) {
                found += (int) list.stream().filter(item -> item == entry.get()).count();
            }
            check(helper, found == 1, entry.getId() + " が、タブに " + found + " 回入っています（1回だけのはず）");
        }
        expect(helper, tabs, "apprentice_sword", ModCreativeTabs.Tab.WEAPONS);
        expect(helper, tabs, "royal_oath_shield", ModCreativeTabs.Tab.ARMOR);
        expect(helper, tabs, "ancient_stone_cuirass", ModCreativeTabs.Tab.ARMOR);
        expect(helper, tabs, "portrait_necklace", ModCreativeTabs.Tab.ARMOR);
        expect(helper, tabs, "royal_portrait", ModCreativeTabs.Tab.BLOCKS);
        expect(helper, tabs, "oath_crystal", ModCreativeTabs.Tab.BLOCKS);
        expect(helper, tabs, "house_door_spruce", ModCreativeTabs.Tab.BLOCKS);
        expect(helper, tabs, "training_dummy", ModCreativeTabs.Tab.BLOCKS);
        expect(helper, tabs, "frost_silver", ModCreativeTabs.Tab.MATERIALS);
        expect(helper, tabs, "fish_pebble_dace", ModCreativeTabs.Tab.MATERIALS);
        expect(helper, tabs, "candy_strength", ModCreativeTabs.Tab.USABLES);
        expect(helper, tabs, "healing_potion_small", ModCreativeTabs.Tab.USABLES);
        expect(helper, tabs, "name_plate_bertram", ModCreativeTabs.Tab.USABLES);
        expect(helper, tabs, "queen_letter_1", ModCreativeTabs.Tab.USABLES);
        expect(helper, tabs, "forget_water", ModCreativeTabs.Tab.USABLES);
        expect(helper, tabs, "valgrim_spawn_egg", ModCreativeTabs.Tab.SPAWN_EGGS);
        expect(helper, tabs, "veldhorn_spawn_egg", ModCreativeTabs.Tab.SPAWN_EGGS);
        expect(helper, tabs, "npc_spawn_egg", ModCreativeTabs.Tab.SPAWN_EGGS);
        check(helper, ModCreativeTabs.WEAPONS.isPresent() && ModCreativeTabs.SPAWN_EGGS.isPresent(), "クリエイティブのタブが登録されていません");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 道具

    private static void expect(GameTestHelper helper, Map<ModCreativeTabs.Tab, List<Item>> tabs, String name, ModCreativeTabs.Tab tab) {
        Item item = ForgeRegistries.ITEMS.getValue(AmemaruMod.id(name));
        check(helper, item != null && tabs.get(tab).contains(item), name + " が「" + tab + "」のタブにありません");
    }

    private static Item item(String name) {
        return ModItems.MANIFEST.get(name).get();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "amt_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest:" + name).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(level, new GameProfile(id, profileName));
    }
}

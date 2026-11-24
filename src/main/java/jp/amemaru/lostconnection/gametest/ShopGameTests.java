package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryData;
import jp.amemaru.lostconnection.block.ShopCounterBlockEntity;
import jp.amemaru.lostconnection.core.registry.ModBlocks;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.economy.EconomyLog;
import jp.amemaru.lostconnection.economy.MarketPrices;
import jp.amemaru.lostconnection.economy.ShopDef;
import jp.amemaru.lostconnection.economy.ShopDefs;
import jp.amemaru.lostconnection.economy.ShopService;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.item.ConsumableDefs;
import jp.amemaru.lostconnection.player.DownHandler;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.progression.ProgressionData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 6 の商店街の店（買う・売る）・相場の基準価格・消耗品（回復薬（小）・マナ水（小））。
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShopGameTests {
    private static final String EMPTY = "empty3x3x3";

    private ShopGameTests() {
    }

    /**
     * 店の定義（13軒）と相場の表が読み込め、どの品もあり、値段がつく。買い取り値は、どの店の売値よりも安い（買って売るだけでは
     * 増えない）。基準価格は Phase 0-A の計算例のとおり（苔鉄40・古代石の剣500・草原の角笛2,050）。表にない装備は
     * レアリティの基準額 ×（1＋0.035×装備レベル。11月24日にレベルの上限を上げたため0.08から）、アクセサリーは品質の倍率をかける。
     */
    @GameTest(template = EMPTY)
    public static void shopDefinitionsAndPrices(GameTestHelper helper) {
        check(helper, ShopDefs.loader().errors().isEmpty(), "店の定義に問題があります：" + ShopDefs.loader().errors());
        check(helper, MarketPrices.loader().errors().isEmpty(), "相場の表に問題があります：" + MarketPrices.loader().errors());
        check(helper, ConsumableDefs.loader().errors().isEmpty(), "消耗品の定義に問題があります：" + ConsumableDefs.loader().errors());
        check(helper, ShopDefs.all().size() == 13, "店の定義が13軒ではありません：" + ShopDefs.all().keySet());
        // 消耗品：回復薬（小・中）・マナ水（小・中）・香草茶と、11月23日の直しで数値を付けたバニラの食べ物15種
        check(helper, ConsumableDefs.server().size() == 20, "消耗品の定義が20ではありません：" + ConsumableDefs.server().size());
        for (jp.amemaru.lostconnection.item.ConsumableDef def : ConsumableDefs.server()) {
            check(helper, ForgeRegistries.ITEMS.containsKey(def.id()), "消耗品の定義の品がありません：" + def.id());
        }

        Map<ResourceLocation, Long> lowestSell = new HashMap<>();
        for (ShopDef def : ShopDefs.all().values()) {
            for (ShopDef.Entry entry : def.sells()) {
                check(helper, ForgeRegistries.ITEMS.containsKey(entry.item()), def.id() + " の売る品がありません：" + entry.item());
                check(helper, entry.resolvedPrice() > 0, def.id() + " の売る品に値段がありません：" + entry.item());
                lowestSell.merge(entry.item(), entry.resolvedPrice(), Math::min);
            }
        }
        for (ShopDef def : ShopDefs.all().values()) {
            for (ShopDef.Entry entry : def.buys()) {
                check(helper, ForgeRegistries.ITEMS.containsKey(entry.item()), def.id() + " の買い取る品がありません：" + entry.item());
                long price = entry.resolvedPrice();
                check(helper, price > 0, def.id() + " の買い取る品に値段がありません（相場の表にない）：" + entry.item());
                Long sell = lowestSell.get(entry.item());
                check(helper, sell == null || price < sell, def.id() + " の買い取り値が売値以上です：" + entry.item() + " " + price + "・" + sell);
            }
        }
        ShopDef general = ShopDefs.get(AmemaruMod.id("general"));
        check(helper, general != null && "マルタ・ポム".equals(general.keeper()), "雑貨屋（マルタ・ポム）の定義がありません");
        check(helper, general.sells().get(0).item().equals(AmemaruMod.id("healing_potion_small")) && general.sells().get(0).price() == 100,
                "雑貨屋の最初の品が、回復薬（小）100ノットではありません");
        boolean buysTowerMaterial = false;
        for (ShopDef.Entry entry : general.buys()) {
            check(helper, !entry.item().getPath().startsWith("crystal_fruit") && !entry.item().getPath().equals("light_shard"),
                    "取引できない品を買い取ります：" + entry.item());
            buysTowerMaterial |= entry.item().equals(AmemaruMod.id("moss_iron"));
        }
        check(helper, buysTowerMaterial, "雑貨屋が塔の素材（苔鉄）を買い取りません");
        for (ShopDef def : ShopDefs.all().values()) {
            if (def != general) {
                for (ShopDef.Entry entry : def.buys()) {
                    // 11月23日の直しで、ほかの店もこのModの品（回復薬・道具）を売り、買い戻すようになった。塔の素材は雑貨屋だけ
                    check(helper, !(ForgeRegistries.ITEMS.getValue(entry.item()) instanceof jp.amemaru.lostconnection.item.MaterialItem),
                            "塔の素材の買い取りは雑貨屋だけのはずです：" + def.id() + " " + entry.item());
                }
            }
        }

        check(helper, MarketPrices.base(AmemaruMod.id("moss_iron")) == 40, "苔鉄の基準価格が40ではありません");
        check(helper, MarketPrices.base(AmemaruMod.id("crystal_shard")) == 110, "結晶の欠片の基準価格が110ではありません");
        check(helper, MarketPrices.base(new ItemStack(item("ancient_stone_sword"))) == 500, "古代石の剣の基準価格が500ではありません："
                + MarketPrices.base(new ItemStack(item("ancient_stone_sword"))));
        check(helper, MarketPrices.base(new ItemStack(item("grassland_horn"))) == 2050, "草原の角笛の基準価格が2,050ではありません");
        check(helper, MarketPrices.base(new ItemStack(item("healing_potion_small"))) == 80, "回復薬（小）の基準価格が、売値の80%ではありません");
        // 表にない装備：水門の鍵盾（エピック・装備レベル15）は 1,600 ×（1＋0.035×15）＝ 2,440（11月24日。前は Lv7・0.08 で 2,496）
        long shield = MarketPrices.base(new ItemStack(item("floodgate_key_shield")));
        check(helper, shield == 2440, "表にない装備の基準価格が、レアリティの基準額 ×（1＋0.035×装備レベル）ではありません：" + shield);
        // アクセサリーの品質：★5 は×3.0、★1 は×0.6
        ItemStack charm = new ItemStack(item("waystone_charm"));
        long plain = MarketPrices.base(charm);
        AccessoryData.storeQuality(charm, 5, false);
        long five = MarketPrices.base(charm);
        AccessoryData.storeQuality(charm, 1, false);
        long one = MarketPrices.base(charm);
        check(helper, plain == 470 && five == 1410 && one == 282, "アクセサリーの品質の倍率が違います：" + plain + "・" + five + "・" + one);
        helper.succeed();
    }

    /**
     * 店で買う・売る：所持金と持ち物の空きを確かめてから払って渡す（足りなければ何も変わらない）。売るときは、名前を付けた品は
     * 数えない。買う＝回収、売る＝発行として記録する。カウンターから離れている・カウンターの店と違う・品の番号が違うときは断る。
     */
    @GameTest(template = EMPTY)
    public static void shopBuyAndSell(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos rel = new BlockPos(1, 2, 1);
        helper.setBlock(rel, ModBlocks.SHOP_COUNTER.get().defaultBlockState());
        BlockPos pos = helper.absolutePos(rel);
        check(helper, helper.getLevel().getBlockEntity(pos) instanceof ShopCounterBlockEntity, "呼び鈴にブロックエンティティがありません");
        ShopCounterBlockEntity counter = (ShopCounterBlockEntity) helper.getLevel().getBlockEntity(pos);
        ResourceLocation general = AmemaruMod.id("general");
        counter.setShop(general);
        ShopDef def = ShopDefs.get(general);
        check(helper, def != null, "雑貨屋の定義がありません");

        FakePlayer p = fake(helper, "shop_a");
        PlayerData data = data(helper, p);
        p.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5);
        p.getInventory().clearContent();
        Wallet.set(p, data, 1000, "gametest");
        EconomyLog log = EconomyLog.get(server);
        int day = ProgressionData.currentDay(server);
        long issued = log.day(day).issued();
        long collected = log.day(day).collected();

        Item potion = item("healing_potion_small");
        check(helper, ShopService.buy(p, pos, general, 0, 3) == ShopService.Result.OK, "回復薬（小）を3個買えません");
        check(helper, data.getKnots() == 700 && ShopService.count(p.getInventory(), potion) == 3,
                "買ったあとの所持金か持ち物が違います：" + data.getKnots() + "・" + ShopService.count(p.getInventory(), potion));
        check(helper, ShopService.buy(p, pos, general, 0, 64) == ShopService.Result.NOT_ENOUGH_MONEY && data.getKnots() == 700
                && ShopService.count(p.getInventory(), potion) == 3, "所持金が足りないのに買えたか、何かが変わりました");

        int mossIndex = -1;
        for (int i = 0; i < def.buys().size(); i++) {
            if (def.buys().get(i).item().equals(AmemaruMod.id("moss_iron"))) {
                mossIndex = i;
            }
        }
        check(helper, mossIndex >= 0, "雑貨屋の買い取りに苔鉄がありません");
        long mossPrice = def.buys().get(mossIndex).resolvedPrice();
        check(helper, mossPrice == 20, "苔鉄の買い取り値が、基準価格の半分（20）ではありません：" + mossPrice);
        Item moss = item("moss_iron");
        p.getInventory().add(new ItemStack(moss, 5));
        ItemStack named = new ItemStack(moss, 2);
        named.setHoverName(Component.literal("お守りの苔鉄"));
        p.getInventory().add(named);
        check(helper, ShopService.count(p.getInventory(), moss) == 5, "名前を付けた品まで、売れる数に入っています");
        check(helper, ShopService.sell(p, pos, general, mossIndex, 6) == ShopService.Result.NOT_HAVE && data.getKnots() == 700,
                "持っている数より多く売れたか、所持金が変わりました");
        check(helper, ShopService.sell(p, pos, general, mossIndex, 5) == ShopService.Result.OK && data.getKnots() == 800,
                "苔鉄を5個売れないか、所持金が違います：" + data.getKnots());
        check(helper, ShopService.count(p.getInventory(), moss) == 0 && countAll(p, moss) == 2,
                "売ったあとの持ち物が違います（名前を付けた品は残るはず）");
        check(helper, log.day(day).collected() - collected == 300 && log.day(day).issued() - issued == 100,
                "買う＝回収・売る＝発行として記録されていません");

        check(helper, ShopService.buy(p, pos, AmemaruMod.id("bakery"), 0, 1) == ShopService.Result.NO_SHOP, "カウンターと違う店で買えました");
        check(helper, ShopService.buy(p, pos, general, 999, 1) == ShopService.Result.INVALID, "ない品の番号で買えました");
        check(helper, ShopService.buy(p, pos, general, 0, 0) == ShopService.Result.INVALID, "0個で買えました");
        p.moveTo(pos.getX() + 30.5, pos.getY(), pos.getZ() + 0.5);
        check(helper, ShopService.buy(p, pos, general, 0, 1) == ShopService.Result.TOO_FAR && data.getKnots() == 800,
                "カウンターから離れているのに買えました");
        p.getInventory().clearContent();
        Wallet.set(p, data, 0, "gametest");
        helper.succeed();
    }

    /**
     * 回復薬（小）は最大HPの25%、マナ水（小）は最大MPの30%を、すぐに戻す。使うと1つ減り、同じ種類の薬にクールダウンがかかる。
     * 満タンのときとダウンの間は使えない（減らない）。
     */
    @GameTest(template = EMPTY)
    public static void consumablesRestore(GameTestHelper helper) {
        FakePlayer p = fake(helper, "potion_a");
        PlayerData data = data(helper, p);
        p.removeAllEffects();
        LevelService.recompute(p, data);
        Item potion = item("healing_potion_small");
        Item water = item("mana_water_small");
        p.getCooldowns().removeCooldown(potion);
        p.getCooldowns().removeCooldown(water);
        double maxHp = data.getStats().maxHp();
        double maxMp = data.getStats().maxMp();

        data.setHp(maxHp);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(potion, 3));
        potion.use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
        check(helper, p.getMainHandItem().getCount() == 3, "HPが満タンなのに使われました");

        data.setHp(maxHp * 0.5);
        potion.use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
        check(helper, Math.abs(data.getHp() - maxHp * 0.75) < 1.0E-6, "回復薬（小）で最大HPの25%が戻りません：" + data.getHp() + "／" + maxHp);
        check(helper, p.getMainHandItem().getCount() == 2, "使った回復薬（小）が減りません");
        check(helper, p.getCooldowns().isOnCooldown(potion) && !p.getCooldowns().isOnCooldown(water),
                "回復薬（小）のクールダウンがかからないか、マナ水（小）にまでかかりました");

        data.setMp(0);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(water, 2));
        water.use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
        check(helper, Math.abs(data.getMp() - maxMp * 0.3) < 1.0E-6, "マナ水（小）で最大MPの30%が戻りません：" + data.getMp() + "／" + maxMp);
        check(helper, p.getMainHandItem().getCount() == 1 && p.getCooldowns().isOnCooldown(water), "マナ水（小）が減らないか、クールダウンがかかりません");

        p.getCooldowns().removeCooldown(potion);
        DownHandler.goDown(p, data);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(potion, 1));
        potion.use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
        check(helper, data.down().isDown() && data.getHp() == 0 && p.getMainHandItem().getCount() == 1, "ダウンの間に回復薬（小）が使えました");
        DownHandler.revive(p, data, p);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.getCooldowns().removeCooldown(potion);
        p.getCooldowns().removeCooldown(water);
        data.setHp(data.getStats().maxHp());
        data.setMp(data.getStats().maxMp());
        p.removeAllEffects();
        helper.succeed();
    }

    // ---------------------------------------------------------------- 小物

    private static Item item(String name) {
        return ModItems.MANIFEST.get(name).get();
    }

    private static int countAll(ServerPlayer player, Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
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

    private static PlayerData data(GameTestHelper helper, ServerPlayer player) {
        return PlayerDataCapability.get(player).orElseThrow(() -> {
            helper.fail("プレイヤーデータが付いていません");
            return new IllegalStateException("no player data");
        });
    }
}

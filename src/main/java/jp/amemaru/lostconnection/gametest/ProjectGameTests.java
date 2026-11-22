package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.admin.CityCommands;
import jp.amemaru.lostconnection.casino.CasinoRules;
import jp.amemaru.lostconnection.connection.ConnectionCategory;
import jp.amemaru.lostconnection.connection.ConnectionService;
import jp.amemaru.lostconnection.economy.Wallet;
import jp.amemaru.lostconnection.fishing.FishDefs;
import jp.amemaru.lostconnection.fishing.FishingRules;
import jp.amemaru.lostconnection.fishing.FishingService;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.story.StoryState;
import jp.amemaru.lostconnection.town.ProjectDef;
import jp.amemaru.lostconnection.town.ProjectDefs;
import jp.amemaru.lostconnection.town.DishItem;
import jp.amemaru.lostconnection.town.ProjectService;
import jp.amemaru.lostconnection.town.RestaurantRules;
import jp.amemaru.lostconnection.town.RestaurantService;
import jp.amemaru.lostconnection.town.TownData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 7 その4：寄付と共同事業（7つの定義・寄付・目標と輝き段階の両方で完成・次の朝・施設が変わると物語の印が付く・結晶の加護）。
 * 進行日と輝き段階は、ほかのテストに触らないよう {@link ProjectService#setForTest} で決める。
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProjectGameTests {
    private static final String EMPTY = "empty3x3x3";

    private ProjectGameTests() {
    }

    /** 定義：7つ（Phase 0-A 7章の表の寄付目標・輝き段階・開始日）。印は釣りとカジノが見る印と同じ。重ねる形は育ったあとの形。 */
    @GameTest(template = EMPTY)
    public static void projectDefinitionsLoad(GameTestHelper helper) {
        check(helper, ProjectDefs.loader().errors().isEmpty() && ProjectDefs.all().size() == 7,
                "共同事業の定義が読めません（" + ProjectDefs.all().size() + "）：" + ProjectDefs.loader().errors());
        long[][] table = {{25000, 3, 4}, {40000, 4, 7}, {60000, 5, 9}, {75000, 6, 11}, {75000, 6, 13}, {100000, 7, 15}, {250000, 9, 18}};
        List<ProjectDef> list = ProjectDefs.ordered();
        Set<String> flags = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            ProjectDef def = list.get(i);
            check(helper, def.order() == i + 1 && def.goal() == table[i][0] && def.stage() == table[i][1] && def.startDay() == table[i][2],
                    def.id() + " の寄付目標・輝き段階・開始日が 7章の表とちがいます");
            check(helper, flags.add(def.flag().toString()), "物語の印が重なっています：" + def.flag());
            check(helper, def.overlay().isEmpty() || CityCommands.isGrownForm(def.overlay()), def.id() + " の重ねる形がありません：" + def.overlay());
        }
        check(helper, flags.contains(FishingRules.current().lakeFlag().toString()) && flags.contains(CasinoRules.current().highLowFlag().toString())
                && flags.contains(CasinoRules.current().fishRaceFlag().toString()), "湖畔の釣り場・ハイ＆ロー・魚レースの印が、釣りとカジノの印とちがいます");
        check(helper, ProjectDefs.get(AmemaruMod.id("crystal")).blessing() == 1, "大結晶の強化で、結晶の加護が一段上がりません");
        helper.succeed();
    }

    /**
     * 寄付：受付前は寄付できない・寄付したノットが減る・銘板に残る・施設への寄付のCP（100ノットごとに1、1日50）・目標まで。
     * 目標に届いても輝き段階が足りなければ待つ。そろうと完成し、施設が変わると物語の印（湖畔の釣り場が開く）。
     */
    @GameTest(template = EMPTY)
    public static void projectDonateAndBuild(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer a = fake(helper, "proj_a");
        PlayerData data = data(helper, a);
        ProjectDef lake = ProjectDefs.get(AmemaruMod.id("lakeside"));
        StoryState story = StoryState.get(server);
        boolean hadFlag = story.has(lake.flag());
        try {
            ConnectionService.setClockForTest(() -> LocalDateTime.of(2026, 11, 23, 12, 0));
            data.getConnection().load(new CompoundTag());
            ProjectService.reset(server, lake);
            Wallet.take(a, data, Wallet.balance(data), "gametest");
            Wallet.give(a, data, 200_000, "gametest");
            ProjectService.setForTest(3, 2);
            check(helper, "locked".equals(ProjectService.status(server, lake))
                    && "project.amemaru.error.locked".equals(ProjectService.donate(a, lake, 1000)), "受付前（4日目より前）に寄付できました");
            ProjectService.setForTest(4, 2);
            // 目標は 25人基準 × 人数補正（0.4〜2.0倍）なので、1万〜5万。まず半分を寄付する
            long goal = ProjectService.goal(server, lake);
            long first = goal / 2;
            check(helper, ProjectService.donate(a, lake, first) == null && Wallet.balance(data) == 200_000 - first
                    && TownData.get(server).project(lake.id()).donated() == first, "寄付できないか、ノットが減りません");
            TownData.Project p = TownData.get(server).project(lake.id());
            check(helper, p.plate().size() == 1 && p.plate().get(0).amount() == first && p.plate().get(0).name().equals(a.getGameProfile().getName()),
                    "銘板に寄付した人が残りません");
            check(helper, data.getConnection().today(ConnectionCategory.TOWN) == 50, "施設への寄付のCPが1日50になりません："
                    + data.getConnection().today(ConnectionCategory.TOWN));
            long before = Wallet.balance(data);
            check(helper, ProjectService.donate(a, lake, Long.MAX_VALUE) == null && Wallet.balance(data) == before - (goal - first)
                    && p.donated() == goal, "残りすべての寄付が、目標までになりません（目標 " + goal + "）");
            check(helper, "waiting".equals(ProjectService.status(server, lake)) && !p.completed()
                    && "project.amemaru.error.reached".equals(ProjectService.donate(a, lake, 100)), "輝き段階が足りないのに完成したか、目標のあとも寄付できました");
            ProjectService.setForTest(4, 3);
            ProjectService.checkAll(server);
            check(helper, p.completed() && !p.built() && "complete".equals(ProjectService.status(server, lake)), "目標と輝き段階がそろっても完成しません");
            check(helper, ProjectService.morningPassed(23_980, 0) && ProjectService.morningPassed(18_000, 1_000)
                    && !ProjectService.morningPassed(1_000, 1_020) && !ProjectService.morningPassed(-1, 0), "朝を回ったかの見分けがちがいます");
            check(helper, ProjectService.build(server, lake) && p.built() && story.has(lake.flag()) && FishingService.lakeOpen(server)
                    && !ProjectService.build(server, lake), "施設が変わらないか、湖畔の釣り場が開きません");
            check(helper, ProjectService.builtOverlays(server).contains("fishing_lakeside"), "建て直しても、湖畔の釣り場の形が重なりません");
        } finally {
            ProjectService.setForTest(null, null);
            ConnectionService.setClockForTest(null);
            ProjectService.reset(server, lake);
            if (hadFlag) {
                story.add(lake.flag());
            }
            data.getConnection().load(new CompoundTag());
            Wallet.take(a, data, Wallet.balance(data), "gametest");
        }
        helper.succeed();
    }

    /** 大結晶の強化（⑦）：完成すると、最終決戦の「結晶の加護」が一段上がる。管理者は輝き段階が足りなくても完成にできる。 */
    @GameTest(template = EMPTY)
    public static void projectCrystalBlessing(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ProjectDef crystal = ProjectDefs.get(AmemaruMod.id("crystal"));
        StoryState story = StoryState.get(server);
        boolean hadFlag = story.has(crystal.flag());
        try {
            ProjectService.reset(server, crystal);
            double before = ConnectionService.finalBlessing(server);
            ProjectService.setForTest(18, 1);
            ProjectService.adminDonate(server, crystal, UUID.nameUUIDFromBytes("amemaru-gametest:proj_admin".getBytes(StandardCharsets.UTF_8)),
                    "admin", ProjectService.goal(server, crystal));
            check(helper, !TownData.get(server).project(crystal.id()).completed(), "輝き段階1で、大結晶の強化（段階9）が完成しました");
            ProjectService.forceComplete(server, crystal);
            check(helper, ProjectService.build(server, crystal) && ConnectionService.finalBlessing(server) == before + 1,
                    "大結晶の強化で、結晶の加護が一段上がりません");
        } finally {
            ProjectService.setForTest(null, null);
            ProjectService.reset(server, crystal);
            if (hadFlag) {
                story.add(crystal.flag());
            }
        }
        helper.succeed();
    }

    /**
     * 料理屋（③のあと）：材料（★の魚・野菜）と代金で料理を作ってもらう。魚は安い★1から使い、記憶魚は使わない。食べると攻撃が上がる。
     * 新しく食べると入れかわる（強化は1つだけ）。
     */
    @GameTest(template = EMPTY)
    public static void restaurantCookAndEat(GameTestHelper helper) {
        FakePlayer a = fake(helper, "proj_cook");
        PlayerData data = data(helper, a);
        try {
            check(helper, RestaurantRules.loader().errors().isEmpty() && RestaurantRules.current().dishes().size() == 6,
                    "料理屋の決まりが読めません：" + RestaurantRules.loader().errors());
            for (RestaurantRules.Dish dish : RestaurantRules.current().dishes()) {
                check(helper, !RestaurantService.dishStack(dish, 20).isEmpty(), "料理のアイテムがありません：" + dish.item());
            }
            RestaurantService.clearForTest(a);
            a.getInventory().clearContent();
            Wallet.take(a, data, Wallet.balance(data), "gametest");
            Wallet.give(a, data, 1000, "gametest");
            Item dace = FishingService.itemOf(FishDefs.get(AmemaruMod.id("pebble_dace")));
            Item memory = FishingService.itemOf(FishDefs.get(AmemaruMod.id("memory_dawn")));
            a.getInventory().add(new ItemStack(memory, 1));
            a.getInventory().add(new ItemStack(dace, 2));
            RestaurantRules.Dish grilled = RestaurantRules.current().dish(AmemaruMod.id("dish_grilled_fish"));
            check(helper, "restaurant.amemaru.error.ingredients".equals(RestaurantService.cook(a, grilled)),
                    "魚が足りない（記憶魚は数えない）のに、作れました");
            a.getInventory().add(new ItemStack(dace, 2));
            check(helper, RestaurantService.cook(a, grilled) == null && a.getInventory().countItem(dace) == 1
                    && a.getInventory().countItem(memory) == 1 && Wallet.balance(data) == 1000 - grilled.fee(),
                    "材料と代金が出ないか、記憶魚を使いました");
            ItemStack dish = ItemStack.EMPTY;
            for (ItemStack s : a.getInventory().items) {
                if (s.getItem() instanceof DishItem) {
                    dish = s;
                }
            }
            check(helper, !dish.isEmpty() && dish.getTagElement(DishItem.TAG) != null, "料理が渡らないか、強化の中身が書かれていません");
            // 攻撃は整数に切り捨てるので、レベルを上げて、5%が見えるだけの攻撃にする
            data.setLevel(60);
            LevelService.recompute(a, data);
            double attack = data.getStats().get(StatType.ATTACK);
            double lost = data.getStats().get(StatType.LOST_RESIST);
            RestaurantService.eat(a, dish);
            LevelService.recompute(a, data);
            double up = data.getStats().get(StatType.ATTACK);
            check(helper, RestaurantService.active(a) != null && up > attack && Math.abs(up / attack - 1.0 - grilled.amount() / 100.0) < 0.03,
                    "料理を食べても攻撃が上がりません（" + attack + " → " + up + "）");
            RestaurantRules.Dish feast = RestaurantRules.current().dish(AmemaruMod.id("dish_lake_feast"));
            RestaurantService.eat(a, RestaurantService.dishStack(feast, 20));
            LevelService.recompute(a, data);
            check(helper, Math.abs(data.getStats().get(StatType.ATTACK) - attack) < 1e-6
                    && Math.abs(data.getStats().get(StatType.LOST_RESIST) - (lost + feast.amount())) < 1e-6,
                    "新しく食べても、前の料理の強化が残っているか、ごちそうのロスト耐性が上がりません");
        } finally {
            RestaurantService.clearForTest(a);
            a.getInventory().clearContent();
            Wallet.take(a, data, Wallet.balance(data), "gametest");
            data.setLevel(1);
            LevelService.recompute(a, data);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 道具

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

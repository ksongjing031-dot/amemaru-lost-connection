package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.accessory.AccessoryDef;
import jp.amemaru.lostconnection.accessory.AccessoryDefs;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.gacha.GachaRules;
import jp.amemaru.lostconnection.gacha.GachaService;
import jp.amemaru.lostconnection.player.AdventureData;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 祈願（ガチャ。11月27日のご指示「ガチャ限定の武器や装備」「ボスを二回目以上から倒すと出る専用アイテム」「各層ごとのガチャ」
 * 「ガチャ限のユニーク武器」）。
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GachaGameTests {
    private static final String EMPTY = "empty3x3x3";

    private GachaGameTests() {
    }

    /** 8つの階の祈願が読め、中身がみなある。★5はガチャ限のユニーク武器（いつも上限・本人専用）、★4にガチャ限のアクセサリー。 */
    @GameTest(template = EMPTY)
    public static void bannersAreComplete(GameTestHelper helper) {
        check(helper, GachaRules.loader().errors().isEmpty() && GachaRules.loader().get(GachaRules.DEFAULT_ID).isPresent(),
                "祈願の決まりが読めません：" + GachaRules.loader().errors());
        GachaRules rules = GachaRules.current();
        check(helper, rules.banners().size() >= 8, "祈願が8つありません：" + rules.banners().size());
        for (int floor = 1; floor <= 8; floor++) {
            GachaRules.Banner banner = rules.bannerOfFloor(floor);
            check(helper, banner != null, floor + "階の祈願がありません");
            check(helper, item(banner.token()) != null, banner.id() + " の祈願札がありません：" + banner.token());
            for (int stars = 3; stars <= 5; stars++) {
                for (GachaRules.Entry entry : banner.pool(stars)) {
                    check(helper, item(entry.item()) != null, banner.id() + " の★" + stars + "の品がありません：" + entry.item());
                }
            }
            GachaRules.Entry five = banner.pool(5).get(0);
            EquipmentDef weapon = EquipmentDefs.server().get(five.item());
            check(helper, weapon != null && weapon.rarity() == Rarity.UNIQUE && weapon.fixed()
                            && weapon.binding() == EquipmentDef.Binding.BOUND,
                    banner.id() + " の★5が、いつも上限・本人専用のユニーク武器ではありません：" + five.item());
            boolean accessory = false;
            for (GachaRules.Entry entry : banner.pool(4)) {
                AccessoryDef def = AccessoryDefs.get(item(entry.item()));
                if (def != null) {
                    accessory = true;
                    check(helper, def.rarity() == Rarity.LEGENDARY && !def.effects().isEmpty() && def.floor() == floor,
                            entry.item() + " が、その階のレジェンダリー（特殊効果つき）になっていません");
                }
            }
            check(helper, accessory, banner.id() + " の★4に、ガチャ限のアクセサリーがありません");
        }
        helper.succeed();
    }

    /** 天井：★5が出ないまま天井の回数目は、必ず★5。★5が出ると数え直す。 */
    @GameTest(template = EMPTY)
    public static void pityGivesFive(GameTestHelper helper) {
        GachaRules rules = GachaRules.current();
        GachaRules.Banner banner = rules.bannerOfFloor(1);
        check(helper, banner != null, "1階の祈願がありません");
        AdventureData.GachaProgress progress = new AdventureData.GachaProgress();
        for (int i = 0; i < banner.pity() - 1; i++) {
            progress.record(false);
        }
        List<GachaService.Result> results = GachaService.roll(progress, banner, rules, 1, unlucky());
        check(helper, results.size() == 1 && results.get(0).stars() == 5, "天井の回数目に★5が出ません：" + results.get(0).stars());
        check(helper, progress.sinceFive() == 0 && progress.fives() == 1 && progress.pulls() == banner.pity(),
                "★5のあと、天井の数が戻っていません：" + progress.sinceFive());
        check(helper, results.get(0).stack().is(item(banner.pool(5).get(0).item())), "天井の★5が、その階のユニーク武器ではありません");
        helper.succeed();
    }

    /** 10回まとめて引くと、運が悪くても★4以上が1つは出る（1回ずつでは出ない）。 */
    @GameTest(template = EMPTY)
    public static void tenPullHasAFour(GameTestHelper helper) {
        GachaRules rules = GachaRules.current();
        GachaRules.Banner banner = rules.bannerOfFloor(2);
        List<GachaService.Result> ten = GachaService.roll(new AdventureData.GachaProgress(), banner, rules, rules.tenCount(), unlucky());
        long high = ten.stream().filter(r -> r.stars() >= rules.tenGuarantee()).count();
        check(helper, ten.size() == rules.tenCount() && high == 1, "運の悪い10回で、★4以上がちょうど1つになっていません：" + high);
        List<GachaService.Result> one = GachaService.roll(new AdventureData.GachaProgress(), banner, rules, 1, unlucky());
        check(helper, one.get(0).stars() == 3, "運の悪い1回で★3以外が出ました");
        for (GachaService.Result result : ten) {
            check(helper, !result.stack().isEmpty(), "引いた品が空です（★" + result.stars() + "）");
        }
        helper.succeed();
    }

    /** 祈願札：その階のボスを1回目に倒したときは出ず、2回目から出る。 */
    @GameTest(template = EMPTY)
    public static void tokensFromRepeatKills(GameTestHelper helper) {
        FakePlayer player = fake(helper, "tokens");
        PlayerData data = data(player);
        player.getInventory().clearContent();
        int floor = 3;
        GachaRules.Banner banner = GachaRules.current().bannerOfFloor(floor);
        if (data.getStory().bossKills(floor) == 0) {
            check(helper, GachaService.onBossDefeated(player, data, floor) == 0, "1回目のボスで祈願札が出ました");
            data.getStory().addBossKill(floor);
        }
        int given = GachaService.onBossDefeated(player, data, floor);
        check(helper, given == GachaRules.current().bossTokens() && GachaService.countTokens(player, banner.token()) == given,
                "2回目のボスで、祈願札が" + GachaRules.current().bossTokens() + "枚になっていません：" + given);
        player.getInventory().clearContent();
        helper.succeed();
    }

    /** 引く：祈願札を使い、出た品が持ち物に入り、記録が進む。札が足りなければ何も起きない。 */
    @GameTest(template = EMPTY)
    public static void pullUsesTokens(GameTestHelper helper) {
        FakePlayer player = fake(helper, "pull");
        PlayerData data = data(player);
        player.getInventory().clearContent();
        GachaRules rules = GachaRules.current();
        GachaRules.Banner banner = rules.bannerOfFloor(1);
        int before = data.getAdventure().gacha(banner.id()).pulls();
        GachaService.allowAnywhere(player);
        player.getInventory().add(new ItemStack(item(banner.token()), 9));
        GachaService.onPull(player, banner.id(), rules.tenCount());
        check(helper, GachaService.countTokens(player, banner.token()) == 9 && data.getAdventure().gacha(banner.id()).pulls() == before,
                "祈願札が9枚なのに、10回引けました");
        player.getInventory().add(new ItemStack(item(banner.token()), 1));
        GachaService.onPull(player, banner.id(), rules.tenCount());
        check(helper, GachaService.countTokens(player, banner.token()) == 0, "10回で祈願札を10枚使っていません");
        check(helper, data.getAdventure().gacha(banner.id()).pulls() == before + rules.tenCount(), "10回引いた記録になっていません");
        int items = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (!player.getInventory().getItem(i).isEmpty()) {
                items++;
            }
        }
        check(helper, items >= 1, "引いた品が持ち物に入っていません");
        player.getInventory().clearContent();
        helper.succeed();
    }

    // ---------------------------------------------------------------- 道具

    /** いつもいちばん外れる（nextDouble が 0.99）。 */
    private static LegacyRandomSource unlucky() {
        return new LegacyRandomSource(42L) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
    }

    private static Item item(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? null : item;
    }

    private static FakePlayer fake(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        String full = "gch_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest-gacha:" + name).getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(id, profileName));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    private static PlayerData data(FakePlayer player) {
        return PlayerDataCapability.get(player).orElseThrow(() -> new GameTestAssertException("プレイヤーデータが付いていません"));
    }

    private static void check(GameTestHelper helper, boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }
}

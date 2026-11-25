package jp.amemaru.lostconnection.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.registry.ModItems;
import jp.amemaru.lostconnection.economy.ShopDef;
import jp.amemaru.lostconnection.economy.ShopDefs;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.item.ConsumableDef;
import jp.amemaru.lostconnection.item.ConsumableService;
import jp.amemaru.lostconnection.item.LightService;
import jp.amemaru.lostconnection.item.Looks;
import jp.amemaru.lostconnection.item.RepairKitItem;
import jp.amemaru.lostconnection.item.ReturnFeatherItem;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 11月23日の直し「色々な店があるがどこで使うかわからないものが多いので実用的なものを置く」の自動テスト。
 *
 * <ul>
 *   <li>どの店の品にも使い道がある（言語ファイルの itemuse）。見た目の服には名前と色がある</li>
 *   <li>バニラの食べ物で、すぐにHPかMPが戻る（食べ物どうしでクールダウン。回復薬とは別）</li>
 *   <li>携帯修理具・灯りの小瓶・帰還の羽根・見た目の服が働く</li>
 * </ul>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PracticalGoodsGameTests {
    private static final String EMPTY = "empty3x3x3";

    private PracticalGoodsGameTests() {
    }

    /** どの店の品にも使い道（見た目の服は名前）がある。売る品はどれもこのModの使い道のある品で、使い道のない品は並ばない。 */
    @GameTest(template = EMPTY)
    public static void everyShopItemHasAUse(GameTestHelper helper) {
        JsonObject lang = readLanguage(helper);
        int looks = 0;
        for (ShopDef def : ShopDefs.all().values()) {
            for (ShopDef.Entry entry : def.sells()) {
                if (entry.isLook()) {
                    looks++;
                    check(helper, lang.has("shop.amemaru.look." + entry.look()) && entry.color() != 0,
                            def.id() + " の見た目の服に名前か色がありません：" + entry.look());
                    continue;
                }
                String key = "itemuse." + entry.item().getNamespace() + "." + entry.item().getPath();
                check(helper, lang.has(key), def.id() + " の品に使い道がありません：" + entry.item());
            }
        }
        check(helper, looks == 12, "見た目の服が12点（仕立て8・革4）ではありません：" + looks);
        ShopDef cloth = ShopDefs.get(AmemaruMod.id("cloth"));
        check(helper, cloth != null && cloth.sells().stream().allMatch(ShopDef.Entry::isLook) && cloth.buys().isEmpty(),
                "布と仕立ては見た目の服を売り、買い取りはしないはずです");
        helper.succeed();
    }

    /** バニラのパンで最大HPの10%がすぐに戻り、食べ物どうしにクールダウンがかかる（回復薬にはかからない）。満タンなら減らない。 */
    @GameTest(template = EMPTY)
    public static void foodRestoresRightAway(GameTestHelper helper) {
        FakePlayer p = fake(helper, "food_a");
        PlayerData data = data(helper, p);
        LevelService.recompute(p, data);
        p.getCooldowns().removeCooldown(Items.BREAD);
        p.getCooldowns().removeCooldown(Items.COOKED_BEEF);
        double maxHp = data.getStats().maxHp();
        ItemStack bread = new ItemStack(Items.BREAD, 3);
        ConsumableDef def = ConsumableService.defOf(bread);
        check(helper, def != null && def.eat() && def.group().equals("food"), "パンに食べ物の数値がありません");

        data.setHp(maxHp);
        check(helper, !ConsumableService.consume(p, bread, def) && bread.getCount() == 3, "HPが満タンなのにパンが減りました");
        data.setHp(maxHp * 0.5);
        check(helper, ConsumableService.consume(p, bread, def), "パンが食べられません");
        check(helper, Math.abs(data.getHp() - maxHp * 0.6) < 1.0E-6 && bread.getCount() == 2,
                "パンで最大HPの10%が戻らないか、減りません：" + data.getHp() + "／" + maxHp);
        Item potion = ModItems.MANIFEST.get("healing_potion_small").get();
        check(helper, p.getCooldowns().isOnCooldown(Items.BREAD) && p.getCooldowns().isOnCooldown(Items.COOKED_BEEF)
                && !p.getCooldowns().isOnCooldown(potion), "食べ物のクールダウンが、ほかの食べ物にかからないか、回復薬にまでかかりました");
        p.getCooldowns().removeCooldown(Items.BREAD);
        p.getCooldowns().removeCooldown(Items.COOKED_BEEF);
        data.setHp(maxHp);
        helper.succeed();
    }

    /** 携帯修理具：身につけた防具の耐久が、最大の30%戻る（減っていなければ直さない）。 */
    @GameTest(template = EMPTY)
    public static void repairKitRestoresDurability(GameTestHelper helper) {
        FakePlayer p = fake(helper, "kit_a");
        Item cuirass = ModItems.MANIFEST.get("ancient_stone_cuirass").get();
        ItemStack armor = new ItemStack(cuirass);
        EquipmentDef def = EquipmentDefs.server().get(armor);
        check(helper, def != null && def.durability() > 0, "古代石の胴鎧に耐久がありません");
        GearData.setDurability(armor, def, 10);
        p.setItemSlot(EquipmentSlot.CHEST, armor);
        ItemStack kit = new ItemStack(ModItems.MANIFEST.get("repair_kit").get());
        int repaired = RepairKitItem.repairWorn(p, kit);
        int expected = 10 + (int) Math.ceil(def.durability() * RepairKitItem.RATIO);
        check(helper, repaired == 1 && GearData.durability(p.getItemBySlot(EquipmentSlot.CHEST), def) == expected,
                "携帯修理具で耐久が30%戻りません：" + GearData.durability(p.getItemBySlot(EquipmentSlot.CHEST), def) + "（" + expected + "）");
        GearData.setDurability(p.getItemBySlot(EquipmentSlot.CHEST), def, def.durability());
        check(helper, RepairKitItem.repairWorn(p, kit) == 0, "耐久が減っていないのに直しました");
        p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        helper.succeed();
    }

    /** 灯りの小瓶：その場に見えない灯りを置く。帰還の羽根：塔の外では使えない。見た目の服：守りと強さがなく、色と名前がある。 */
    @GameTest(template = EMPTY)
    public static void lightsFeatherAndLooks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer p = fake(helper, "vial_a");
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        p.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        BlockPos eye = BlockPos.containing(p.getEyePosition());
        int placed = LightService.placeVial(p);
        check(helper, placed >= 1 && level.getBlockState(eye).is(Blocks.LIGHT), "灯りの小瓶で、その場に灯りが置かれません：" + placed);
        for (BlockPos pos : new BlockPos[]{eye, eye.offset(3, 0, 0), eye.offset(-3, 0, 0), eye.offset(0, 0, 3), eye.offset(0, 0, -3),
                eye.below(), eye.offset(3, -1, 0), eye.offset(-3, -1, 0), eye.offset(0, -1, 3), eye.offset(0, -1, -3)}) {
            if (level.getBlockState(pos).is(Blocks.LIGHT)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }

        check(helper, "message.amemaru.return_feather.not_tower".equals(ReturnFeatherItem.blocked(p)),
                "塔の外で帰還の羽根が使えてしまいます");

        ShopDef cloth = ShopDefs.get(AmemaruMod.id("cloth"));
        ShopDef.Entry first = cloth.sells().get(0);
        ItemStack look = first.stack(ForgeRegistries.ITEMS.getValue(first.item()), 1);
        ListTag modifiers = look.getTag() == null ? null : look.getTag().getList("AttributeModifiers", Tag.TAG_COMPOUND);
        check(helper, Looks.isLook(look) && modifiers != null && modifiers.isEmpty() && look.getTag().getBoolean("Unbreakable")
                && look.getTag().getCompound("display").getInt("color") == first.color(),
                "見た目の服に、守りと強さが残っているか、色がありません");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 小物

    private static JsonObject readLanguage(GameTestHelper helper) {
        String path = "/assets/" + AmemaruMod.MOD_ID + "/lang/ja_jp.json";
        try (InputStream stream = PracticalGoodsGameTests.class.getResourceAsStream(path)) {
            check(helper, stream != null, path + " が見つかりません");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            throw new GameTestAssertException(path + " を読めません：" + e.getMessage());
        }
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

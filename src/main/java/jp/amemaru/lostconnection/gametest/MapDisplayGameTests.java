package jp.amemaru.lostconnection.gametest;

import com.mojang.authlib.GameProfile;
import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 街の飾り（額縁と防具立て）を、プレイヤーが取れないことの自動テスト（GameTest）。
 *
 * <p>建物の構造物（{@code data/amemaru/structures/map/conelia/*.nbt}）に入っている額縁と防具立てを、保存された
 * データのまま空のテストの場所（3×3×3）に作り、サバイバルとアドベンチャーの偽のプレイヤーで、1つずつ
 * 「左クリック・素手で右クリック・物を持って右クリック・投げた物・爆発」を試す。1つ試すたびに、中身・向き・装備が
 * 置いたときのままで、プレイヤーの手に何も来ていないことを確かめる。最後に、アイテムが1つも落ちていないことを確かめる。</p>
 *
 * <p>建物そのものは置かない。自動の確認では、クライアントの世界でも同じテストを出現地点のそばで回し、その30マス上で
 * スキルの写真を撮るため、高さ38のギルドなどを置くと写真の邪魔になる。守りは額縁と防具立てのデータだけで決まる。</p>
 *
 * <p>アドベンチャーモードだけでは守れない（バニラでは、ふつうの額縁の中身も防具立ての装備も、アドベンチャーで取れる。
 * {@link #plainDisplaysCanBeTakenInAdventure} で確かめる）。そのため、建物の額縁は「固定（Fixed）・壊れない
 * （Invulnerable）」、防具立ては「すべての場所の出し入れを止める（DisabledSlots）・壊れない」にしてある
 * （{@code tools/mapgen/mapgen/world.py} の item_frame と armor_stand）。クリエイティブのプレイヤー（管理者と建築係）だけは、
 * 額縁と防具立てを壊せる（建物を直すため）。</p>
 *
 * <p>額縁か防具立てのある建物を足したら、ここにテストを1つ足す（額縁と防具立ての数も書く）。</p>
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MapDisplayGameTests {
    private static final String EMPTY = "empty3x3x3";
    private static final String GUILD = "map/conelia/guild_hall";
    private static final String WEAPON = "map/conelia/weapon_workshop";
    private static final String ARMOR = "map/conelia/armor_workshop";
    private static final String ACCESSORY = "map/conelia/accessory_workshop";
    private static final String MARKET_NORTH = "map/conelia/market_north";
    private static final String MARKET_SOUTH = "map/conelia/market_south";
    private static final String MARKET_OPEN = "map/conelia/market_restaurant_open";

    /** 防具立てを右クリックする高さ（足・脚・胴・手・頭のあたり）。 */
    private static final double[] CLICK_HEIGHTS = {0.2, 0.6, 1.0, 1.3, 1.8};
    /** 防具立てに持たせようとする物（手・頭・胴・脚・足・左手）。 */
    private static final Item[] OFFERS = {Items.FEATHER, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE,
            Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS, Items.SHIELD};

    private MapDisplayGameTests() {
    }

    /** 冒険者ギルド：額縁1（地図）。 */
    @GameTest(template = EMPTY)
    public static void guildHallDisplaysCannotBeTaken(GameTestHelper helper) {
        checkBuilding(helper, GUILD, "guild", 1, 0);
    }

    /** 武器工房：額縁30（剣・斧・弓など）、防具立て2。 */
    @GameTest(template = EMPTY)
    public static void weaponWorkshopDisplaysCannotBeTaken(GameTestHelper helper) {
        checkBuilding(helper, WEAPON, "weapon", 30, 2);
    }

    /** 防具工房：額縁27（盾・兜・型紙など。空の額縁3を含む）、防具立て9（ダイヤの防具を含む）。 */
    @GameTest(template = EMPTY)
    public static void armorWorkshopDisplaysCannotBeTaken(GameTestHelper helper) {
        checkBuilding(helper, ARMOR, "armor", 27, 9);
    }

    /** 装飾工房：額縁32（宝石・鉱石・依頼の紙・素材の見本など。空の留め具の枠1を含む）。防具立てはない。 */
    @GameTest(template = EMPTY)
    public static void accessoryWorkshopDisplaysCannotBeTaken(GameTestHelper helper) {
        checkBuilding(helper, ACCESSORY, "accessory", 32, 0);
    }

    /** 商店街の北（料理屋が開く前）：額縁55（8軒の店の品・看板の下の品・素材の見本など）。防具立てはない。 */
    @GameTest(template = EMPTY)
    public static void marketNorthDisplaysCannotBeTaken(GameTestHelper helper) {
        checkBuilding(helper, MARKET_NORTH, "market_north", 55, 0);
    }

    /** 商店街の南：額縁59（6軒の店の品）。防具立てはない。 */
    @GameTest(template = EMPTY)
    public static void marketSouthDisplaysCannotBeTaken(GameTestHelper helper) {
        checkBuilding(helper, MARKET_SOUTH, "market_south", 59, 0);
    }

    /** 寄付で開いたあとの料理屋（北の上に重ねる）：額縁8（品書き・その日の魚・看板の下の品・店先の器）。 */
    @GameTest(template = EMPTY)
    public static void marketRestaurantOpenDisplaysCannotBeTaken(GameTestHelper helper) {
        checkBuilding(helper, MARKET_OPEN, "market_open", 8, 0);
    }

    /**
     * ふつうの額縁と防具立て（固定していない）なら、アドベンチャーでも中身が取れる。
     * バニラの決まりの確かめで、上のテストのやり方（左クリック・右クリックが本当に届いていること）の確かめでもある。
     */
    @GameTest(template = EMPTY)
    public static void plainDisplaysCanBeTakenInAdventure(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = fake(helper, "plain_adventure");
        player.setGameMode(GameType.ADVENTURE);
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));

        ItemFrame frame = new ItemFrame(level, at, Direction.UP);
        frame.setItem(new ItemStack(Items.DIAMOND));
        level.addFreshEntity(frame);
        hold(player, ItemStack.EMPTY);
        player.attack(frame);
        helper.assertTrue(frame.getItem().isEmpty(),
                "ふつうの額縁の中身が、アドベンチャーの左クリックで外れません（テストのやり方を見直す）");

        ArmorStand stand = new ArmorStand(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        level.addFreshEntity(stand);
        hold(player, ItemStack.EMPTY);
        stand.interactAt(player, new Vec3(0.0, 1.0, 0.0), InteractionHand.MAIN_HAND);
        helper.assertTrue(stand.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                        && player.getMainHandItem().is(Items.IRON_CHESTPLATE),
                "ふつうの防具立ての胴の防具を、アドベンチャーの右クリックで取れません（テストのやり方を見直す）");

        frame.discard();
        stand.discard();
        level.getEntitiesOfClass(ItemEntity.class, area(helper)).forEach(Entity::discard);
        hold(player, ItemStack.EMPTY);
        helper.succeed();
    }

    // ================================================================ 建物の確かめ

    private static void checkBuilding(GameTestHelper helper, String template, String key, int frameCount, int standCount) {
        ServerLevel level = helper.getLevel();
        AABB area = area(helper);
        List<Entity> made = spawnDisplays(helper, template);
        List<ItemFrame> frames = new ArrayList<>();
        List<ArmorStand> stands = new ArrayList<>();
        for (Entity entity : made) {
            if (entity instanceof ItemFrame frame) {
                frames.add(frame);
            } else if (entity instanceof ArmorStand stand) {
                stands.add(stand);
            }
        }
        helper.assertTrue(frames.size() + stands.size() == made.size(),
                template + "：額縁と防具立てのほかの「もの」があります（このテストに足す）");
        helper.assertTrue(frames.size() == frameCount,
                template + "：額縁の数が " + frameCount + " ではありません：" + frames.size());
        helper.assertTrue(stands.size() == standCount,
                template + "：防具立ての数が " + standCount + " ではありません：" + stands.size());

        List<ItemStack> frameItems = new ArrayList<>();
        List<Integer> frameTurns = new ArrayList<>();
        for (ItemFrame frame : frames) {
            frameItems.add(frame.getItem().copy());
            frameTurns.add(frame.getRotation());
        }
        List<List<ItemStack>> standItems = new ArrayList<>();
        for (ArmorStand stand : stands) {
            standItems.add(equipment(stand));
        }
        int droppedBefore = level.getEntitiesOfClass(ItemEntity.class, area).size();

        for (GameType mode : new GameType[] {GameType.SURVIVAL, GameType.ADVENTURE}) {
            FakePlayer player = fake(helper, key + "_" + mode.getName());
            player.setGameMode(mode);
            for (int i = 0; i < frames.size(); i++) {
                tryFrame(helper, level, player, frames.get(i), frameItems.get(i), frameTurns.get(i),
                        template + " の額縁 " + (i + 1) + "（" + frameItems.get(i) + "、" + mode.getName() + "）");
            }
            for (int i = 0; i < stands.size(); i++) {
                tryStand(helper, level, player, stands.get(i), standItems.get(i),
                        template + " の防具立て " + (i + 1) + "（" + mode.getName() + "）");
            }
            hold(player, ItemStack.EMPTY);
        }

        int droppedAfter = level.getEntitiesOfClass(ItemEntity.class, area).size();
        helper.assertTrue(droppedAfter == droppedBefore,
                template + "：アイテムが落ちました：" + (droppedAfter - droppedBefore) + "個");
        made.forEach(Entity::discard);
        helper.succeed();
    }

    /**
     * 建物の構造物に入っている「もの」（額縁と防具立て）を、保存されたデータのまま、テストの場所の真ん中に作る
     * （構造物を置いたときと同じ作り方。重なってもかまわない。固定した額縁は支えがなくても落ちない）。
     */
    private static List<Entity> spawnDisplays(GameTestHelper helper, String template) {
        ServerLevel level = helper.getLevel();
        StructureTemplate structure = level.getStructureManager()
                .get(AmemaruMod.id(template))
                .orElseThrow(() -> new GameTestAssertException("構造物が見つかりません：" + template));
        ListTag entities = structure.save(new CompoundTag()).getList("entities", Tag.TAG_COMPOUND);
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.5, 1.5));
        List<Entity> made = new ArrayList<>();
        BlockPos tile = BlockPos.containing(at);
        for (int i = 0; i < entities.size(); i++) {
            // 置く所を書きかえてから作る（額縁の「掛かっているマス」が遠いと、作るときにエラーの記録が出るため）
            CompoundTag nbt = entities.getCompound(i).getCompound("nbt").copy();
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(at.x));
            pos.add(DoubleTag.valueOf(at.y));
            pos.add(DoubleTag.valueOf(at.z));
            nbt.put("Pos", pos);
            if (nbt.contains("TileX")) {
                nbt.putInt("TileX", tile.getX());
                nbt.putInt("TileY", tile.getY());
                nbt.putInt("TileZ", tile.getZ());
            }
            Entity entity = EntityType.create(nbt, level)
                    .orElseThrow(() -> new GameTestAssertException(template + "：作れない「もの」があります：" + nbt.getString("id")));
            entity.setPos(at.x, at.y, at.z);
            level.addFreshEntity(entity);
            made.add(entity);
        }
        return made;
    }

    /** 額縁に、取る・回す・入れる・壊すを試す。1つ試すたびに、置いたときのままかを見る。 */
    private static void tryFrame(GameTestHelper helper, ServerLevel level, FakePlayer player, ItemFrame frame,
                                 ItemStack item, int turn, String where) {
        hold(player, ItemStack.EMPTY);
        player.attack(frame);
        sameFrame(helper, frame, item, turn, where + "：左クリック");
        expectHand(helper, player, Items.AIR, where + "：左クリック");

        player.interactOn(frame, InteractionHand.MAIN_HAND);
        sameFrame(helper, frame, item, turn, where + "：素手で右クリック");
        expectHand(helper, player, Items.AIR, where + "：素手で右クリック");

        hold(player, new ItemStack(Items.FEATHER));
        player.interactOn(frame, InteractionHand.MAIN_HAND);
        sameFrame(helper, frame, item, turn, where + "：羽根を持って右クリック");
        expectHand(helper, player, Items.FEATHER, where + "：羽根を持って右クリック");
        player.attack(frame);
        sameFrame(helper, frame, item, turn, where + "：羽根を持って左クリック");

        hold(player, ItemStack.EMPTY);
        frame.hurt(level.damageSources().thrown(player, player), 1.0F);
        sameFrame(helper, frame, item, turn, where + "：投げた物が当たる");
        frame.hurt(level.damageSources().explosion(null, null), 20.0F);
        sameFrame(helper, frame, item, turn, where + "：爆発");
    }

    /** 防具立てに、壊す・取る・装備させる・取りかえるを試す。1つ試すたびに、置いたときのままかを見る。 */
    private static void tryStand(GameTestHelper helper, ServerLevel level, FakePlayer player, ArmorStand stand,
                                 List<ItemStack> items, String where) {
        hold(player, ItemStack.EMPTY);
        player.attack(stand);
        player.attack(stand);
        sameStand(helper, stand, items, where + "：左クリック2回");

        for (double y : CLICK_HEIGHTS) {
            hold(player, ItemStack.EMPTY);
            stand.interactAt(player, new Vec3(0.0, y, 0.0), InteractionHand.MAIN_HAND);
            sameStand(helper, stand, items, where + "：高さ " + y + " を素手で右クリック");
            expectHand(helper, player, Items.AIR, where + "：高さ " + y + " を素手で右クリック");
        }
        for (Item offer : OFFERS) {
            for (double y : CLICK_HEIGHTS) {
                hold(player, new ItemStack(offer));
                stand.interactAt(player, new Vec3(0.0, y, 0.0), InteractionHand.MAIN_HAND);
                sameStand(helper, stand, items, where + "：" + offer + " を持って高さ " + y + " を右クリック");
                expectHand(helper, player, offer, where + "：" + offer + " を持って高さ " + y + " を右クリック");
            }
        }

        hold(player, ItemStack.EMPTY);
        stand.hurt(level.damageSources().thrown(player, player), 1.0F);
        sameStand(helper, stand, items, where + "：投げた物が当たる");
        stand.hurt(level.damageSources().explosion(null, null), 20.0F);
        sameStand(helper, stand, items, where + "：爆発");
    }

    // ================================================================ 共通

    private static void sameFrame(GameTestHelper helper, ItemFrame frame, ItemStack item, int turn, String what) {
        helper.assertTrue(frame.isAlive(), what + "で、額縁が壊れました");
        helper.assertTrue(ItemStack.matches(frame.getItem(), item),
                what + "で、額縁の中身が変わりました：" + item + " → " + frame.getItem());
        helper.assertTrue(frame.getRotation() == turn, what + "で、額縁の中身が回りました");
    }

    private static void sameStand(GameTestHelper helper, ArmorStand stand, List<ItemStack> items, String what) {
        helper.assertTrue(stand.isAlive(), what + "で、防具立てが壊れました");
        List<ItemStack> now = equipment(stand);
        EquipmentSlot[] slots = EquipmentSlot.values();
        for (int s = 0; s < now.size(); s++) {
            helper.assertTrue(ItemStack.matches(now.get(s), items.get(s)),
                    what + "で、防具立ての " + slots[s].getName() + " が変わりました：" + items.get(s) + " → " + now.get(s));
        }
    }

    /** 手に持っている物が、持たせた物のまま（素手なら素手のまま）か。何かを取れていたら、ここで分かる。 */
    private static void expectHand(GameTestHelper helper, FakePlayer player, Item expected, String what) {
        ItemStack hand = player.getMainHandItem();
        boolean ok = expected == Items.AIR ? hand.isEmpty() : hand.is(expected) && hand.getCount() == 1;
        helper.assertTrue(ok, what + "で、手に持つ物が変わりました：" + hand);
    }

    private static List<ItemStack> equipment(ArmorStand stand) {
        List<ItemStack> items = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            items.add(stand.getItemBySlot(slot).copy());
        }
        return items;
    }

    /** テストの場所（3×3×3）とまわり。となりのテストの場所は5マス以上離れている。 */
    private static AABB area(GameTestHelper helper) {
        return new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(4.0);
    }

    private static void hold(FakePlayer player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    }

    private static FakePlayer fake(GameTestHelper helper, String name) {
        String full = "amd_" + name;
        String profileName = full.substring(0, Math.min(16, full.length()));
        UUID id = UUID.nameUUIDFromBytes(("amemaru-gametest-display:" + name).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, profileName));
    }
}

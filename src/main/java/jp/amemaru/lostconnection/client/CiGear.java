package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.equipment.Rarity;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.Locale;

/**
 * CIの写真：装備のデザイン（水門の鍵盾）を、ゲームの中で確かめるための準備（{@link CiScreenshots} から使う）。
 *
 * <p>一人称（持っているとき・構えたとき）・三人称（前と後ろ）・ゲームの中の4方向（表示用のエンティティ）・夜（光る部分）を撮る。</p>
 */
final class CiGear {
    /** 4方向の写真の、表示用のエンティティの大きさ（盾が3ブロックほどの高さになる）。 */
    private static final float TURNAROUND_SCALE = 2.0F;

    private CiGear() {
    }

    /** 右手は空、左手に水門の鍵盾（エピック）。 */
    static void equipShield(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.OFFHAND, CiPhase3.gear("floodgate_key_shield", Rarity.EPIC, 1.0, 0));
    }

    /** 左手の盾を構える（右クリックを押し続けたときと同じ）・下ろす。 */
    static void block(Minecraft minecraft, boolean start) {
        if (minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        if (start) {
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.OFF_HAND);
        } else if (minecraft.player.isUsingItem()) {
            minecraft.gameMode.releaseUsingItem(minecraft.player);
        }
    }

    /** ゲームの中で並べる装備（ID・モデルの中心のピクセル座標）。武器は (8, y, 8) のまわり、盾は (0, 0, 0) のまわりに作ってある。 */
    record Piece(String id, double cx, double cy, double cz) {
    }

    /** 作り直した装備（見習い・古代石）。左から並べる順。 */
    static final List<Piece> REDESIGN_1 = List.of(
            new Piece("apprentice_sword", 8, 6.5, 8), new Piece("apprentice_greatsword", 8.7, 13.6, 8),
            new Piece("apprentice_staff", 8.8, 7.3, 8), new Piece("apprentice_shield", 0, -2.0, -0.3),
            new Piece("ancient_stone_sword", 8, 9.3, 8.2), new Piece("ancient_stone_greatsword", 8.4, 13.1, 8),
            new Piece("ancient_stone_staff", 8, 7, 8), new Piece("ancient_stone_shield", 0, 0, -0.3));

    /** 作り直した1階のレア装備（⑤）。蜂晶の杖は GeckoLib なので、中心は (8, 8.16, 8) からの位置。 */
    static final List<Piece> REDESIGN_RARE = List.of(
            new Piece("sentinel_blade", 8.5, 10.9, 8), new Piece("chipped_fang_greatsword", 8, 10.7, 8),
            new Piece("bee_crystal_staff", 8, 9.5, 5.6));

    /**
     * 表示用のエンティティで、装備を1つ置くコマンド（置き方は none。モデルの中心がエンティティの位置に来るように動かす）。
     * 描くときの変換は「位置 → 拡大 → 180度回す → −0.5」なので、中心 c が原点に来る移動は (s(cx/16−0.5), −s(cy/16−0.5), s(cz/16−0.5))。
     */
    static String display(Piece piece, double x, double y, double z, float yaw, float s) {
        double tx = s * (piece.cx() / 16.0 - 0.5);
        double ty = -s * (piece.cy() / 16.0 - 0.5);
        double tz = s * (piece.cz() / 16.0 - 0.5);
        return String.format(Locale.ROOT,
                "summon minecraft:item_display %.2f %.2f %.2f {item:{id:\"amemaru:%s\",Count:1b},"
                        + "item_display:\"none\",Rotation:[%.1ff,0f],Tags:[\"amemaru_ci_gear\"],"
                        + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                        + "translation:[%.3ff,%.3ff,%.3ff],scale:[%.2ff,%.2ff,%.2ff]}}",
                x, y, z, piece.id(), yaw, tx, ty, tz, s, s, s);
    }

    /** 防具立て（腕あり・台なし）に、右手と左手の装備を持たせるコマンド。空なら null。 */
    static String stand(String main, String off, double x, double y, double z, float yaw) {
        return String.format(Locale.ROOT,
                "summon minecraft:armor_stand %.2f %.2f %.2f {ShowArms:1b,NoBasePlate:1b,NoGravity:1b,Rotation:[%.1ff,0f],"
                        + "Tags:[\"amemaru_ci_gear\"],HandItems:[%s,%s]}",
                x, y, z, yaw, hand(main), hand(off));
    }

    /** 防具立てに、防具（頭・胴・脚・足の順の4つ。空なら null）と手の持ち物を着せて置くコマンド。 */
    static String armoredStand(String main, String off, String[] armor, double x, double y, double z, float yaw) {
        return String.format(Locale.ROOT,
                "summon minecraft:armor_stand %.2f %.2f %.2f {ShowArms:1b,NoBasePlate:1b,NoGravity:1b,Rotation:[%.1ff,0f],"
                        + "Tags:[\"amemaru_ci_gear\"],HandItems:[%s,%s],ArmorItems:[%s,%s,%s,%s]}",
                x, y, z, yaw, hand(main), hand(off), hand(armor[3]), hand(armor[2]), hand(armor[1]), hand(armor[0]));
    }

    /** 作り直した防具（③重鎧・④法衣）。頭・胴・脚・足の順。 */
    static final String[] PLATE = {"ancient_stone_helm", "ancient_stone_cuirass", "ancient_stone_greaves", "ancient_stone_sabatons"};
    static final String[] ROBE = {"ancient_stone_hood", "ancient_stone_robe", "ancient_stone_legwear", "ancient_stone_boots"};

    /** 体に表示するアクセサリー（⑥）を付ける。armor が null でなければ、その防具（頭・胴・脚・足）も着る。 */
    static void wearAccessories(ServerPlayer player, String[] armor) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int k = 0; k < slots.length; k++) {
            player.setItemSlot(slots[k], armor == null ? ItemStack.EMPTY
                    : new ItemStack(BuiltInRegistries.ITEM.get(AmemaruMod.id(armor[k]))));
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        CiPhase3.setCurio(player, jp.amemaru.lostconnection.accessory.AccessorySlot.NECKLACE,
                CiPhase3.accessory("grassland_necklace", 3, 0.9));
        CiPhase3.setCurio(player, jp.amemaru.lostconnection.accessory.AccessorySlot.BRACELET,
                CiPhase3.accessory("ishikure_bracelet", 2, 0.85));
        CiPhase3.setCurio(player, jp.amemaru.lostconnection.accessory.AccessorySlot.CHARM,
                CiPhase3.accessory("waystone_charm", 2, 0.8));
    }

    private static String hand(String id) {
        return id == null ? "{}" : "{id:\"amemaru:" + id + "\",Count:1b}";
    }

    /** 手に持たせる（右手・左手。空なら null）。 */
    static void hold(ServerPlayer player, String main, String off) {
        player.setItemSlot(EquipmentSlot.MAINHAND, main == null ? ItemStack.EMPTY
                : new ItemStack(BuiltInRegistries.ITEM.get(AmemaruMod.id(main))));
        player.setItemSlot(EquipmentSlot.OFFHAND, off == null ? ItemStack.EMPTY
                : new ItemStack(BuiltInRegistries.ITEM.get(AmemaruMod.id(off))));
    }

    /**
     * 表示用のエンティティ（item_display）を出すコマンド。モデルの中心がエンティティの位置に来るように、置き方（none）と
     * 拡大に合わせて動かす（アイテムの描画の「−0.5」と、表示用のエンティティが描く前に回す180度の分）。
     */
    static String turnaround(double x, double y, double z, float yaw) {
        float s = TURNAROUND_SCALE;
        float t = s / 2.0F;
        return String.format(Locale.ROOT,
                "summon minecraft:item_display %.2f %.2f %.2f {item:{id:\"amemaru:floodgate_key_shield\",Count:1b},"
                        + "item_display:\"none\",Rotation:[%.1ff,0f],Tags:[\"amemaru_ci_gear\"],"
                        + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                        + "translation:[%.2ff,%.2ff,%.2ff],scale:[%.2ff,%.2ff,%.2ff]}}",
                x, y, z, yaw, -t, t, -t, s, s, s);
    }
}

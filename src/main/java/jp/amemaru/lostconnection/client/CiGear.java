package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.equipment.Rarity;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

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

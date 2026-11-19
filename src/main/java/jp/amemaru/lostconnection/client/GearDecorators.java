package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.accessory.AccessoryData;
import jp.amemaru.lostconnection.equipment.GearItems;
import jp.amemaru.lostconnection.equipment.Rarity;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IItemDecorator;

/**
 * 持ち物のアイコンの飾り（クライアント）。
 *
 * <ul>
 *   <li>アクセサリー：品質★4はうっすら光る（エンチャントの光沢。アイテムの側で出す）。★5は虹色のきらめきを重ねる
 *       （Phase 0-A 21章）。完璧なアクセサリーは、きらめきが増える</li>
 *   <li>装備：強化+4で光沢、+7で小さな星、+10で星がまたたく（Phase 0-A 20章「+4・+7・+10で光り方が一段ずつ増える」）</li>
 * </ul>
 */
public final class GearDecorators {
    /** アクセサリーの飾り。 */
    public static final IItemDecorator ACCESSORY = GearDecorators::accessory;
    /** 装備の飾り。 */
    public static final IItemDecorator GEAR = GearDecorators::gear;

    private GearDecorators() {
    }

    private static boolean accessory(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (AccessoryData.storedQuality(stack) < 5) {
            return false;
        }
        int count = AccessoryData.storedPerfect(stack) ? 5 : 3;
        long now = Util.getMillis();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        for (int i = 0; i < count; i++) {
            long phase = now + i * 397L;
            float twinkle = (Mth.sin(phase / 180.0F) + 1.0F) / 2.0F;
            if (twinkle < 0.35F) {
                continue;
            }
            int px = x + 1 + (int) ((phase / 61L + i * 5L) % 14L);
            int py = y + 1 + (int) ((phase / 89L + i * 7L) % 14L);
            int color = Rarity.UNIQUE.colorAt(phase) & 0xFFFFFF;
            int alpha = Math.round(twinkle * 255.0F) << 24;
            graphics.fill(px, py - 1, px + 1, py + 2, alpha | color);
            graphics.fill(px - 1, py, px + 2, py + 1, alpha | color);
        }
        graphics.pose().popPose();
        return false;
    }

    private static boolean gear(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        int tier = GearItems.glowTier(stack);
        if (tier < 2) {
            return false;
        }
        long now = Util.getMillis();
        float twinkle = tier >= 3 ? (Mth.sin(now / 160.0F) + 1.0F) / 2.0F : 1.0F;
        int alpha = Math.round((0.45F + 0.55F * twinkle) * 255.0F) << 24;
        int color = alpha | 0xFFF2A8;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        int px = x + 2;
        int py = y + 2;
        graphics.fill(px, py - 1, px + 1, py + 2, color);
        graphics.fill(px - 1, py, px + 2, py + 1, color);
        if (tier >= 3) {
            int qx = x + 12;
            int qy = y + 12 - Math.round(twinkle);
            graphics.fill(qx, qy - 1, qx + 1, qy + 2, color);
            graphics.fill(qx - 1, qy, qx + 2, qy + 1, color);
        }
        graphics.pose().popPose();
        return false;
    }
}

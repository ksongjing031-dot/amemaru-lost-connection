package jp.amemaru.lostconnection.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 11月28日の確かめの写真の段取り（CIの写真用）：ユニーク武器の固有スキルを使う・手に持った武器の説明を出す。
 *
 * <ul>
 *   <li>{@code p8u_use}：固有スキルのキー（初期値：Y）を押したのと同じ</li>
 *   <li>{@code p8u_tooltip}：品の説明文を出す（{@code held} で手に持っている品、ほかは品のID。「,」で2つまで。「-」で閉じる）</li>
 *   <li>{@code p8u_ward}：「第七の門鍵」の認証が働いている見た目（足もとの翠の環・状態のアイコン・盾の光。「-」で消す）</li>
 * </ul>
 */
final class CiUnique {
    private CiUnique() {
    }

    /** その種類の行なら動かして true。 */
    static boolean action(Minecraft minecraft, String[] shot, boolean first) {
        String kind = shot[0];
        if (!List.of("p8u_use", "p8u_tooltip", "p8u_ward").contains(kind)) {
            return false;
        }
        if (!first || minecraft.player == null) {
            return true;
        }
        String arg = shot.length > 3 && shot[3] != null ? shot[3] : "";
        switch (kind) {
            case "p8u_use" -> SkillInput.useUnique(minecraft, minecraft.player);
            case "p8u_ward" -> {
                // 認証（第七の門鍵）：まわりに3人そろうのはCIでは作れないので、サーバーから届くのと同じ演出と状態を、手元で出す
                int id = minecraft.player.getId();
                boolean on = !"-".equals(arg);
                UniqueSkillFx.gear(new jp.amemaru.lostconnection.network.s2c.GearFxS2C(on
                        ? jp.amemaru.lostconnection.network.s2c.GearFxS2C.Kind.WARD_ON : jp.amemaru.lostconnection.network.s2c.GearFxS2C.Kind.WARD_OFF,
                        id, id, jp.amemaru.lostconnection.equipment.GearEffects.WARD_COLOR));
                if (on) {
                    minecraft.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            jp.amemaru.lostconnection.core.registry.ModEffects.GATE_WARD.get(), 400, 0, true, false, true));
                    UniqueSkillFx.gear(new jp.amemaru.lostconnection.network.s2c.GearFxS2C(
                            jp.amemaru.lostconnection.network.s2c.GearFxS2C.Kind.WARD, -1, id,
                            jp.amemaru.lostconnection.equipment.GearEffects.WARD_COLOR));
                } else {
                    minecraft.player.removeEffect(jp.amemaru.lostconnection.core.registry.ModEffects.GATE_WARD.get());
                }
            }
            default -> {
                if ("-".equals(arg)) {
                    minecraft.setScreen(CiScreenshots.screenAfterClose());
                } else {
                    // 「held」：手に持っている品。それ以外は品のID（「,」で2つまで）
                    List<ItemStack> stacks = new java.util.ArrayList<>();
                    for (String part : arg.split(",")) {
                        if ("held".equals(part)) {
                            stacks.add(minecraft.player.getItemBySlot(EquipmentSlot.MAINHAND).copy());
                        } else if (!part.isEmpty()) {
                            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                                    net.minecraft.resources.ResourceLocation.tryParse(part));
                            if (item != null) {
                                stacks.add(new ItemStack(item));
                            }
                        }
                    }
                    minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    minecraft.setScreen(new CiPhase3.TooltipScreen(stacks));
                }
            }
        }
        return true;
    }
}

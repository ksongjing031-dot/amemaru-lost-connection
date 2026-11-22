package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.accessory.AccessoryData;
import jp.amemaru.lostconnection.accessory.AccessoryItem;
import jp.amemaru.lostconnection.client.screen.CommissionScreen;
import jp.amemaru.lostconnection.client.screen.WorkshopScreen;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.AccessoryOperationC2S;
import jp.amemaru.lostconnection.workshop.WorkshopMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 自動の確認の写真（Phase 6 その4：厳選と決闘）の段取り：装飾工房の台を置いて開く・アクセサリーを台に置く・タブを変える・
 * 固定する・振り直す・分解の確かめ・依頼台を開いてタブを変える・依頼を出す・決闘の画面を開く。
 */
final class CiPhase6Refine {
    private static BlockPos bench;

    private CiPhase6Refine() {
    }

    static boolean action(Minecraft minecraft, String[] shot, boolean first) {
        String kind = shot[0];
        if (!List.of("p6r_bench", "p6r_open", "p6r_place", "p6r_tab", "p6r_lock", "p6r_reroll", "p6r_confirm", "p6r_commission",
                "p6r_ctab", "p6r_create", "p6r_duel").contains(kind)) {
            return false;
        }
        if (!first) {
            return true;
        }
        switch (kind) {
            case "p6r_bench" -> {
                // 装飾工房のカウンターの作業台（目の前、北へ1ブロック）。地図に入っていなければ置く
                bench = minecraft.player == null ? null : minecraft.player.blockPosition().north(1);
                if (bench != null && minecraft.level != null
                        && !(minecraft.level.getBlockState(bench).getBlock() instanceof jp.amemaru.lostconnection.workshop.WorkshopBlock)) {
                    CiScreenshots.runCommand(minecraft, "setblock " + bench.getX() + " " + bench.getY() + " " + bench.getZ()
                            + " amemaru:accessory_workbench[facing=south]");
                }
            }
            case "p6r_open" -> {
                if (bench != null) {
                    CiPhase3.useWorkbench(minecraft, bench);
                }
            }
            case "p6r_place" -> place(minecraft);
            case "p6r_tab" -> {
                if (minecraft.screen instanceof WorkshopScreen workshop) {
                    workshop.selectTab(Integer.parseInt(shot[3]));
                }
            }
            case "p6r_lock" -> ModNetwork.sendToServer(new AccessoryOperationC2S(AccessoryOperationC2S.Action.TOGGLE_LOCK,
                    Integer.parseInt(shot[3])));
            case "p6r_reroll" -> ModNetwork.sendToServer(new AccessoryOperationC2S("types".equals(shot[3])
                    ? AccessoryOperationC2S.Action.REROLL_TYPES : AccessoryOperationC2S.Action.REROLL_VALUES, 0));
            case "p6r_confirm" -> {
                if (minecraft.screen instanceof WorkshopScreen workshop) {
                    workshop.ciConfirmDismantle();
                }
            }
            case "p6r_commission" -> {
                if (minecraft.screen instanceof WorkshopScreen workshop) {
                    // 工房の画面を閉じてから依頼台を開く。閉じた画面が空のままにならないよう、すぐ何も描かない画面にする
                    workshop.openCommissionBoard();
                    CiScreenshots.hold(minecraft);
                } else if (bench != null) {
                    CompoundTag args = new CompoundTag();
                    args.putLong("pos", bench.asLong());
                    ClientSocial.send("commission_open", args);
                }
            }
            case "p6r_ctab" -> {
                if (minecraft.screen instanceof CommissionScreen screen) {
                    screen.ciTab(shot[3]);
                }
            }
            case "p6r_create" -> {
                // 持ち物の最初のアクセサリーを、数値だけ・固定1つ・目標なし・5回・手数料800で出す
                if (minecraft.player != null) {
                    List<ItemStack> items = minecraft.player.getInventory().items;
                    for (int i = 0; i < items.size(); i++) {
                        if (items.get(i).getItem() instanceof AccessoryItem && AccessoryData.isRolled(items.get(i))) {
                            CompoundTag args = new CompoundTag();
                            args.putInt("slot", i);
                            args.putString("kind", "VALUES");
                            args.putIntArray("locks", new int[]{0});
                            args.putString("target", "NONE");
                            args.putInt("attempts", 5);
                            args.putLong("fee", 800);
                            ClientSocial.send("commission_create", args);
                            break;
                        }
                    }
                }
            }
            case "p6r_duel" -> ClientSocial.send("duel_open");
            default -> {
            }
        }
        return true;
    }

    /** 工房の画面で、持ち物のアクセサリーを台に置く（シフトを押してクリックと同じ）。 */
    private static void place(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.gameMode == null || !(minecraft.player.containerMenu instanceof WorkshopMenu menu)) {
            return;
        }
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (slot.index > 0 && stack.getItem() instanceof AccessoryItem && AccessoryData.isRolled(stack)) {
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.QUICK_MOVE, minecraft.player);
                return;
            }
        }
    }
}

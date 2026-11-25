package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.accessory.AccessoryRefine;
import jp.amemaru.lostconnection.accessory.RefineService;
import jp.amemaru.lostconnection.workshop.WorkshopMenu;
import jp.amemaru.lostconnection.workshop.WorkshopType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 厳選の操作を頼む（Phase 0-B 5章 {@code AccessoryOperation}：再抽選・固定・強化・分解。Phase 6 その4）。
 * サーバーは、装飾工房の画面を開いていて作業台の近くにいること、台の品・費用・固定できる数を確かめてから行う。
 */
public record AccessoryOperationC2S(Action action, int index) {
    public enum Action {
        REROLL_VALUES, REROLL_TYPES, TOGGLE_LOCK, ENHANCE, DISMANTLE, QUOTE
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeByte(index);
    }

    public static AccessoryOperationC2S decode(FriendlyByteBuf buf) {
        Action[] actions = Action.values();
        int ordinal = buf.readByte();
        Action action = actions[Math.max(0, Math.min(actions.length - 1, ordinal))];
        return new AccessoryOperationC2S(action, buf.readByte());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null || !(player.containerMenu instanceof WorkshopMenu menu) || !menu.stillValid(player)
                || menu.type() != WorkshopType.ACCESSORY) {
            return;
        }
        switch (action) {
            case REROLL_VALUES -> RefineService.perform(player, menu, AccessoryRefine.Op.REROLL_VALUES);
            case REROLL_TYPES -> RefineService.perform(player, menu, AccessoryRefine.Op.REROLL_TYPES);
            case ENHANCE -> RefineService.perform(player, menu, AccessoryRefine.Op.ENHANCE);
            case DISMANTLE -> RefineService.perform(player, menu, AccessoryRefine.Op.DISMANTLE);
            case TOGGLE_LOCK -> RefineService.toggleLock(player, menu, index);
            case QUOTE -> RefineService.sendQuote(player, menu);
        }
        menu.broadcastChanges();
    }
}

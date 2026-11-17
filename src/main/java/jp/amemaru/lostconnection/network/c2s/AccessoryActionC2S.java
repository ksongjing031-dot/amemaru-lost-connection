package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.accessory.AccessoryMenu;
import jp.amemaru.lostconnection.accessory.AccessoryService;
import jp.amemaru.lostconnection.accessory.AccessorySlot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

/**
 * アクセサリーの枠の操作を頼む：枠の画面を開く、体に表示するかを変える（Phase 0-B 5章 {@code SetAccessoryVisibility}）。
 * 厳選の操作（{@code AccessoryOperation}）はPhase 6。
 */
public record AccessoryActionC2S(Action action, int slot, boolean value) {
    public enum Action {
        OPEN, SET_VISIBLE
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeByte(slot);
        buf.writeBoolean(value);
    }

    public static AccessoryActionC2S decode(FriendlyByteBuf buf) {
        Action[] actions = Action.values();
        int index = buf.readByte();
        Action action = actions[Math.max(0, Math.min(actions.length - 1, index))];
        return new AccessoryActionC2S(action, buf.readByte(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
        switch (action) {
            case OPEN -> {
                boolean clasp = AccessoryService.claspOpen(player);
                AccessoryService.updateClaspSlot(player);
                NetworkHooks.openScreen(player, new SimpleMenuProvider(
                        (id, inventory, opener) -> new AccessoryMenu(id, inventory, clasp),
                        Component.translatable("screen.amemaru.accessory.title")), buf -> buf.writeBoolean(clasp));
            }
            case SET_VISIBLE -> {
                if (slot >= 0 && slot < AccessorySlot.all().length) {
                    AccessoryService.setVisible(player, AccessorySlot.byIndex(slot), value);
                }
            }
        }
    }
}

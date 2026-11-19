package jp.amemaru.lostconnection.network.c2s;

import jp.amemaru.lostconnection.workshop.WorkshopMenu;
import jp.amemaru.lostconnection.workshop.WorkshopService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 工房の操作を頼む（Phase 0-B 5章 {@code WorkshopCraft}）：生産職になる・レシピで作る・台の品を強化・修理。
 * サーバーは、工房の画面を開いていて、作業台の近くにいることを確かめてから行う。
 */
public record WorkshopActionC2S(Action action, ResourceLocation recipe) {
    public enum Action {
        JOIN, CRAFT, ENHANCE, REPAIR
    }

    private static final ResourceLocation NONE = new ResourceLocation("amemaru", "none");

    public static WorkshopActionC2S of(Action action) {
        return new WorkshopActionC2S(action, NONE);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeResourceLocation(recipe);
    }

    public static WorkshopActionC2S decode(FriendlyByteBuf buf) {
        Action[] actions = Action.values();
        int index = buf.readByte();
        Action action = actions[Math.max(0, Math.min(actions.length - 1, index))];
        return new WorkshopActionC2S(action, buf.readResourceLocation());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null || !(player.containerMenu instanceof WorkshopMenu menu) || !menu.stillValid(player)) {
            return;
        }
        switch (action) {
            case JOIN -> WorkshopService.chooseJob(player, menu.type());
            case CRAFT -> WorkshopService.craft(player, menu.type(), recipe);
            case ENHANCE -> WorkshopService.enhance(player, menu.type(), menu.workStack());
            case REPAIR -> WorkshopService.repair(player, menu.type(), menu.workStack());
        }
        menu.broadcastChanges();
    }
}

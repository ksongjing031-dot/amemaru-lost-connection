package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.client.screen.CrystalButton;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.AccessoryActionC2S;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 持ち物の画面のボタン（クライアント）。
 *
 * <p>Curios が持ち物の画面に足す「アクセサリーの枠」のボタンは、Curios の画面（このModの枠は出ない）を開くので外し、
 * 代わりに、このModのアクセサリーの枠の画面を開くボタンを置く（レシピ本を開いても同じ場所についていく）。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class InventoryButtons {
    private static final String CURIOS_BUTTON = "top.theillusivec4.curios.client.gui.CuriosButton";
    private static final int OFFSET_X = 128;
    private static final int OFFSET_Y = 61;

    private static CrystalButton button;

    private InventoryButtons() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) {
            return;
        }
        List<GuiEventListener> remove = new ArrayList<>();
        for (GuiEventListener listener : event.getListenersList()) {
            if (CURIOS_BUTTON.equals(listener.getClass().getName())) {
                remove.add(listener);
            }
        }
        for (GuiEventListener listener : remove) {
            event.removeListener(listener);
        }
        if (screen instanceof InventoryScreen inventory) {
            button = new CrystalButton(inventory.getGuiLeft() + OFFSET_X, inventory.getGuiTop() + OFFSET_Y, 40, 14,
                    Component.translatable("screen.amemaru.inventory.accessory"),
                    pressed -> ModNetwork.sendToServer(new AccessoryActionC2S(AccessoryActionC2S.Action.OPEN, 0, false)));
            event.addListener(button);
        }
    }

    /** レシピ本を開くと持ち物の画面が横に動くので、ボタンもついていく。 */
    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof InventoryScreen inventory && button != null
                && inventory.children().contains(button)) {
            AbstractWidget widget = button;
            widget.setX(((AbstractContainerScreen<?>) inventory).getGuiLeft() + OFFSET_X);
            widget.setY(inventory.getGuiTop() + OFFSET_Y);
        }
    }
}

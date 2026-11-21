package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.client.screen.PartyScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ダウンの見た目と操作（クライアント。Phase 4）。
 *
 * <ul>
 *   <li>ダウンしている人は、地面に伏せた姿（はう姿勢）になる。自分なら、目の高さも低くなる。</li>
 *   <li>ダウンしている間は、歩く・跳ぶ・しゃがむ・攻撃・使う、ができない（サーバーでも止める）。</li>
 *   <li>ダウンしている人の名前の後ろに、教会へ戻るまでの秒数を出す。</li>
 *   <li>Uキーで、パーティの画面を開く。</li>
 *   <li>ダウンしている間、リタイアのキー（初期値H）を3秒押し続けると、教会へ戻る（11月22日のご指示）。ダウンする前から
 *       押していたキーは数えない（一度離してから）。画面を開いている間は数えない。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class DownClientEvents {
    /** リタイアのキーを押し続ける長さ（3秒）【補完】 */
    public static final int RETIRE_TICKS = 60;
    private static int retireHold;
    /** ダウンしてから一度キーを離した（ダウンする前から押していたキーは数えない） */
    private static boolean retireArmed;
    /** リタイアを送った（教会へ戻るのを待っている） */
    private static boolean retireSent;

    private DownClientEvents() {
    }

    /** リタイアのキーを押している割合（0〜1）。 */
    public static float retireProgress(float partialTick) {
        if (retireSent) {
            return 1.0F;
        }
        if (retireHold <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (retireHold + partialTick) / RETIRE_TICKS);
    }

    public static boolean retireSent() {
        return retireSent;
    }

    private static void tickRetire(Minecraft minecraft) {
        while (KeyBindings.RETIRE.consumeClick()) {
            // 押した回数は使わない（押し続けた長さで決める）
        }
        if (minecraft.player == null || !ClientDown.localDown()) {
            retireHold = 0;
            retireArmed = false;
            retireSent = false;
            return;
        }
        boolean held = minecraft.screen == null && KeyBindings.RETIRE.isDown();
        if (!held) {
            retireHold = 0;
            retireArmed = true;
            return;
        }
        if (!retireArmed || retireSent) {
            return;
        }
        retireHold++;
        if (retireHold >= RETIRE_TICKS) {
            retireSent = true;
            jp.amemaru.lostconnection.network.ModNetwork.sendToServer(new jp.amemaru.lostconnection.network.c2s.RetireC2S());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientDown.tick(minecraft);
        tickRetire(minecraft);
        if (minecraft.level != null) {
            // 伏せた姿：毎 tick の終わりに、はう姿勢にする（バニラは tick ごとに立ち姿に戻すため）
            for (Player player : minecraft.level.players()) {
                if (ClientDown.isDown(player) && player.getPose() != Pose.SWIMMING) {
                    player.setPose(Pose.SWIMMING);
                }
            }
        }
        while (KeyBindings.OPEN_PARTY.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new PartyScreen());
            }
        }
    }

    /** ダウンの間は動けない。 */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!ClientDown.isDown(event.getEntity())) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    /** ダウンの間は、攻撃・使う・選んで取る、ができない。 */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (ClientDown.localDown()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** ダウンしている人の名前に、残りの秒数を足す（近くの人が気づけるように）。 */
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ClientDown.Info info = ClientDown.get(player.getId());
        if (info == null) {
            return;
        }
        event.setContent(Component.empty().append(event.getContent()).append(" ")
                .append(Component.translatable("hud.amemaru.down.tag", info.secondsLeftNow()).withStyle(ChatFormatting.RED)));
        // ほかの人のダウンは、名前が見えない所でも出す。自分の名前は出さない（三人称で、画面の「ダウン」と重なるため）
        if (player != Minecraft.getInstance().getCameraEntity()) {
            event.setResult(Event.Result.ALLOW);
        }
    }
}

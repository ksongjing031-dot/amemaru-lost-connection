package jp.amemaru.lostconnection.client;

import com.mojang.blaze3d.platform.NativeImage;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.client.screen.SkillTreeScreen;
import jp.amemaru.lostconnection.client.screen.StatusScreen;
import jp.amemaru.lostconnection.job.JobService;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.AllocateStatC2S;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Base64;

/**
 * GitHub Actions の自動の確認でだけ動く、画面写真の撮影（遊ぶときは動かない）。
 *
 * <p>Java の起動オプションに {@code -Damemaru.ci.screenshots=true} があるときだけ、シングルプレイのワールドに
 * 入った後で、次の写真を撮る。写真はPNGをBase64にしてログに書き、ワークフローがそれを取り出して保存する。</p>
 * <ul>
 *   <li>Phase 1：HUD（ふつう・HPが減った直後・HPが少ないとき）、ステータス画面</li>
 *   <li>Phase 2：ナイトのスキル枠と誓気・訓練人形のダメージの数字、スキルツリー、職業の登録の画面、
 *       ウィザードの詠唱（三人称・モーション）と流星の着弾</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class CiScreenshots {
    private static final boolean ENABLED = Boolean.getBoolean("amemaru.ci.screenshots");
    private static final int CHUNK = 4000;

    private static int stage;
    private static int wait;
    @Nullable
    private static String pendingShot;

    private CiScreenshots() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !ClientPlayerState.get().hasData() || pendingShot != null) {
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (stage) {
            case 0 -> {
                if (minecraft.screen != null) {
                    return;
                }
                // 透明な画面を開いておく（自動の確認の側は、画面が開いている間は終了を待つ）。HUDはそのまま見える
                minecraft.setScreen(new HoldScreen());
                // 見本の状態：レベル12、経験値を半分ほど、HPと攻撃に振り分け（残りのポイントがあるので結晶が光る）
                command(minecraft, "amemaru admin level set @a 12");
                command(minecraft, "amemaru admin level exp @a 180");
                // ホットバーとオフハンドの見え方を確かめるためのアイテム
                command(minecraft, "give @a minecraft:diamond_sword");
                command(minecraft, "give @a minecraft:iron_pickaxe");
                command(minecraft, "give @a minecraft:bread 12");
                command(minecraft, "give @a minecraft:torch 64");
                command(minecraft, "item replace entity @a weapon.offhand with minecraft:shield");
                ModNetwork.sendToServer(new AllocateStatC2S(StatType.HP, 3));
                ModNetwork.sendToServer(new AllocateStatC2S(StatType.ATTACK, 2));
                next(1, 20);
            }
            case 1 -> {
                command(minecraft, "amemaru admin debug vitals @a 82 46");
                next(2, 40);
            }
            case 2 -> {
                // HPを減らした直後（減った分の残像が見える）
                command(minecraft, "amemaru admin debug vitals @a 61 46");
                next(3, 3);
            }
            case 3 -> {
                pendingShot = "hud";
                next(4, 2);
            }
            case 4 -> {
                minecraft.setScreen(new StatusScreen());
                next(5, 10);
            }
            case 5 -> {
                pendingShot = "status";
                next(6, 2);
            }
            case 6 -> {
                minecraft.setScreen(new HoldScreen());
                command(minecraft, "amemaru admin debug vitals @a 18 46");
                next(7, 40);
            }
            case 7 -> {
                pendingShot = "hud_low";
                next(8, 2);
            }
            // ---------------------------------------------------------------- Phase 2：ナイト
            case 8 -> {
                // コマンドの結果の文がチャットに並んで、写真のじゃまにならないようにする
                command(minecraft, "gamerule sendCommandFeedback false");
                command(minecraft, "gamerule logAdminCommands false");
                command(minecraft, "amemaru admin debug vitals @a 82 46");
                command(minecraft, "time set noon");
                if (minecraft.player != null) {
                    minecraft.player.setXRot(15.0F);
                }
                command(minecraft, "amemaru admin job set @a amemaru:knight");
                command(minecraft, "item replace entity @a weapon.mainhand with amemaru:apprentice_sword");
                command(minecraft, "item replace entity @a weapon.offhand with amemaru:apprentice_shield");
                // スキル枠を6つ埋める（呼び声・突進斬り・盾結び・城壁の構え・旋風断・不屈の誓い）
                command(minecraft, "amemaru admin skill learn @a amemaru:knight/shield_bond 1");
                command(minecraft, "amemaru admin skill learn @a amemaru:knight/rampart 1");
                command(minecraft, "amemaru admin skill learn @a amemaru:knight/whirlwind 1");
                command(minecraft, "amemaru admin skill learn @a amemaru:knight/unyielding_vow 1");
                // 目の前（3ブロック先）に訓練人形
                command(minecraft, "execute as @p at @p run amemaru admin dummy 20");
                next(9, 30);
            }
            case 9 -> {
                // 誓気をためておく（旋風断が30使って強くなる）
                command(minecraft, "amemaru admin skill gauge @a 70");
                next(10, 6);
            }
            case 10 -> {
                useSkill(minecraft, 4); // 旋風断（訓練人形にダメージの数字）
                useSkill(minecraft, 0); // 呼び声
                useSkill(minecraft, 3); // 城壁の構え
                next(11, 8);
            }
            case 11 -> {
                pendingShot = "knight_skills";
                next(12, 2);
            }
            case 12 -> {
                minecraft.setScreen(new SkillTreeScreen());
                next(13, 10);
            }
            case 13 -> {
                pendingShot = "skill_tree";
                next(14, 2);
            }
            case 14 -> {
                // 誓約の結晶に手を当てたときと同じ画面（サーバーから開く）
                IntegratedServer server = minecraft.getSingleplayerServer();
                if (server != null && minecraft.player != null) {
                    java.util.UUID id = minecraft.player.getUUID();
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayer(id);
                        if (player != null) {
                            JobService.openJobScreen(player, player.blockPosition());
                        }
                    });
                }
                next(15, 15);
            }
            case 15 -> {
                pendingShot = "job_select";
                next(16, 2);
            }
            // ---------------------------------------------------------------- Phase 2：ウィザード
            case 16 -> {
                minecraft.setScreen(new HoldScreen());
                if (minecraft.player != null) {
                    // 少し下を向いて、陣と流星が近くに落ちるようにする
                    minecraft.player.setXRot(28.0F);
                }
                command(minecraft, "amemaru admin job set @a amemaru:wizard");
                command(minecraft, "item replace entity @a weapon.mainhand with amemaru:apprentice_staff");
                command(minecraft, "item replace entity @a weapon.offhand with minecraft:air");
                command(minecraft, "amemaru admin skill learn @a amemaru:wizard/meteor_shower 1");
                command(minecraft, "amemaru admin skill learn @a amemaru:wizard/chain_lightning 1");
                command(minecraft, "amemaru admin skill learn @a amemaru:wizard/mana_thread 1");
                command(minecraft, "amemaru admin skill learn @a amemaru:wizard/blink 1");
                next(17, 20);
            }
            case 17 -> {
                // 三人称（正面）にして、モーションが見えるようにする
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                useSkill(minecraft, 1); // 氷結陣
                next(18, 10);
            }
            case 18 -> {
                useSkill(minecraft, 2); // 流星群（2秒の詠唱）
                next(19, 16);
            }
            case 19 -> {
                pendingShot = "wizard_cast";
                next(20, 2);
            }
            case 20 -> {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                // 詠唱の残り（約22tick）＋着弾まで（12tick）
                next(21, 36);
            }
            case 21 -> {
                pendingShot = "wizard_impact";
                next(22, 2);
            }
            case 22 -> {
                minecraft.setScreen(null);
                next(23, 0);
            }
            default -> {
            }
        }
    }

    /** スキル枠のスキルを、キーを押したときと同じように使う。 */
    private static void useSkill(Minecraft minecraft, int slot) {
        if (minecraft.player != null) {
            SkillInput.use(minecraft, minecraft.player, slot);
        }
    }

    /** 描き終わったフレームを写す。 */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || pendingShot == null) {
            return;
        }
        String name = pendingShot;
        pendingShot = null;
        Minecraft minecraft = Minecraft.getInstance();
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            String base64 = Base64.getEncoder().encodeToString(image.asByteArray());
            int total = (base64.length() + CHUNK - 1) / CHUNK;
            AmemaruMod.LOGGER.info("[amemaru] CI screenshot '{}' {}x{} ({} chunks)", name, image.getWidth(), image.getHeight(), total);
            for (int i = 0; i < total; i++) {
                String part = base64.substring(i * CHUNK, Math.min(base64.length(), (i + 1) * CHUNK));
                AmemaruMod.LOGGER.info("AMEMARU_SHOT|{}|{}|{}|{}", name, i, total, part);
            }
        } catch (Exception e) {
            AmemaruMod.LOGGER.error("[amemaru] CI screenshot '{}' failed", name, e);
        }
    }

    private static void next(int nextStage, int ticks) {
        stage = nextStage;
        wait = ticks;
    }

    /** 何も描かない、ゲームを止めない画面。開いている間も、後ろのHUDは描かれる。 */
    private static final class HoldScreen extends Screen {
        HoldScreen() {
            super(Component.empty());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }
    }

    private static void command(Minecraft minecraft, String command) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }
}

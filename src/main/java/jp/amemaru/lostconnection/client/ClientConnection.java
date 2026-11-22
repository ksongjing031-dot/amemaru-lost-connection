package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.screen.GlowScreen;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.ConnectionActionC2S;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * コネクション量（クライアント。Phase 7 その1）：サーバー全体の輝きと、本人の今日の分野別CP。段階が上がったときの
 * 大結晶の一言（画面の真ん中の大きな文字）。
 */
public final class ClientConnection {
    private static CompoundTag state = new CompoundTag();
    private static int version;

    private ClientConnection() {
    }

    public static void handle(String kind, CompoundTag data) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (kind) {
            case "state" -> {
                state = data;
                version++;
            }
            case "screen" -> minecraft.setScreen(new GlowScreen());
            case "stage" -> {
                int stage = data.getInt("stage");
                String voice = data.getString("voice");
                minecraft.gui.setTimes(10, 90, 30);
                minecraft.gui.setSubtitle(voice.isEmpty() ? Component.empty()
                        : Component.literal("「" + voice + "」").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
                minecraft.gui.setTitle(Component.translatable("connection.amemaru.stage_title", stage).withStyle(ChatFormatting.WHITE));
                if (minecraft.player != null) {
                    minecraft.player.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.9F);
                    minecraft.player.playSound(SoundEvents.PLAYER_LEVELUP, 0.6F, 1.4F);
                }
            }
            case "star" -> {
                String voice = data.getString("voice");
                minecraft.gui.setTimes(10, 70, 30);
                minecraft.gui.setSubtitle(voice.isEmpty() ? Component.empty()
                        : Component.literal("「" + voice + "」").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
                minecraft.gui.setTitle(Component.translatable("connection.amemaru.star_title", data.getInt("star")).withStyle(ChatFormatting.GOLD));
                if (minecraft.player != null) {
                    minecraft.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
                }
            }
            case "thanks" -> {
                if (minecraft.player != null) {
                    minecraft.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.3F);
                }
            }
            default -> {
            }
        }
    }

    public static CompoundTag state() {
        return state;
    }

    public static int version() {
        return version;
    }

    public static boolean has() {
        return state.contains("stage", Tag.TAG_INT);
    }

    public static void send(String action, CompoundTag args) {
        ModNetwork.sendToServer(new ConnectionActionC2S(action, args));
    }

    /** ログアウトしたら空にする。 */
    public static void clear() {
        state = new CompoundTag();
        version++;
    }
}

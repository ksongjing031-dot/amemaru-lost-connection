package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.screen.CommissionScreen;
import jp.amemaru.lostconnection.client.screen.DuelScreen;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.SocialActionC2S;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * 交流の、クライアントの写し（Phase 6 その4）：依頼台の一覧（{@code commissions}）と見積もり（{@code commission_quote}）、
 * 決闘の画面の中身（{@code duel}）と、決闘の間の表示（{@code duel_hud}：残り時間・二人のHPの割合・相手の番号）。
 */
public final class ClientSocial {
    private static CompoundTag commissions = new CompoundTag();
    private static CompoundTag quote = new CompoundTag();
    private static CompoundTag duel = new CompoundTag();
    private static CompoundTag hud = new CompoundTag();
    private static long hudAt;
    private static int version;

    private ClientSocial() {
    }

    public static void handle(String kind, CompoundTag data) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (kind) {
            case "commissions" -> {
                commissions = data;
                version++;
                if (data.getBoolean("screen") && minecraft.player != null && !(minecraft.screen instanceof CommissionScreen)) {
                    minecraft.setScreen(new CommissionScreen());
                }
            }
            case "commission_quote" -> {
                quote = data;
                version++;
            }
            case "duel" -> {
                duel = data;
                version++;
                if (data.getBoolean("screen") && minecraft.player != null && !(minecraft.screen instanceof DuelScreen)) {
                    minecraft.setScreen(new DuelScreen(minecraft.screen));
                }
            }
            case "duel_hud" -> {
                hud = data;
                hudAt = System.currentTimeMillis();
            }
            default -> {
            }
        }
    }

    public static void send(String action, CompoundTag args) {
        ModNetwork.sendToServer(new SocialActionC2S(action, args));
    }

    public static void send(String action) {
        send(action, new CompoundTag());
    }

    public static CompoundTag commissions() {
        return commissions;
    }

    public static CompoundTag quote() {
        return quote;
    }

    public static CompoundTag duel() {
        return duel;
    }

    public static CompoundTag hud() {
        return hud;
    }

    /** 決闘の表示が新しいか（届いてから5秒まで。終わったら空が届く）。 */
    public static boolean hudActive() {
        return !hud.isEmpty() && System.currentTimeMillis() - hudAt < 5000L;
    }

    public static int version() {
        return version;
    }

    /** 決闘で戦っている相手の番号（狙いを決めるため。いなければ -1）。 */
    public static int opponentId() {
        if (!hudActive() || !"FIGHT".equals(hud.getString("phase")) || hud.getString("me").isEmpty()) {
            return -1;
        }
        for (Tag tag : hud.getList("fighters", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) tag;
            if (!one.getString("name").equals(hud.getString("me"))) {
                return one.getInt("entity");
            }
        }
        return -1;
    }

    /** 抜けたとき。 */
    public static void clear() {
        commissions = new CompoundTag();
        quote = new CompoundTag();
        duel = new CompoundTag();
        hud = new CompoundTag();
        version++;
    }
}

package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.screen.ConnectionsScreen;
import jp.amemaru.lostconnection.client.screen.DonationScreen;
import jp.amemaru.lostconnection.client.screen.GuildScreen;
import jp.amemaru.lostconnection.client.screen.RecordBookScreen;
import jp.amemaru.lostconnection.client.screen.TitleListScreen;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.GuildActionC2S;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ギルドの、クライアントの写し（Phase 6 その3）：本人の称号・古代記録・親密度・クエスト（{@code self}）、
 * 称号の一覧（{@code catalog}）、ほかの人が表示している称号（{@code shown}）。画面を開く知らせ（{@code screen}）と、
 * 演出（{@code toast}）もここで受ける。
 */
public final class ClientGuild {
    private static CompoundTag self = new CompoundTag();
    private static CompoundTag catalog = new CompoundTag();
    private static final Map<UUID, Shown> SHOWN = new HashMap<>();
    /** 届くたびに増える（画面が、新しい中身が来たかを見る） */
    private static int version;

    /** ほかの人が表示している称号。 */
    public record Shown(String name, int color, String kind, boolean rainbow) {
    }

    private ClientGuild() {
    }

    public static void handle(String kind, CompoundTag data) {
        switch (kind) {
            case "self" -> {
                self = data;
                version++;
            }
            case "catalog" -> {
                catalog = data;
                version++;
            }
            case "shown" -> {
                for (Tag tag : data.getList("players", Tag.TAG_COMPOUND)) {
                    CompoundTag one = (CompoundTag) tag;
                    if (!one.hasUUID("uuid")) {
                        continue;
                    }
                    if (one.getString("name").isEmpty()) {
                        SHOWN.remove(one.getUUID("uuid"));
                    } else {
                        SHOWN.put(one.getUUID("uuid"), new Shown(one.getString("name"), one.getInt("color"), one.getString("kind"),
                                one.getBoolean("rainbow")));
                    }
                }
            }
            case "toast" -> GuildToast.show(data);
            case "screen" -> openScreen(data);
            default -> {
            }
        }
    }

    private static void openScreen(CompoundTag data) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Screen current = minecraft.screen;
        switch (data.getString("screen")) {
            case "guild" -> {
                if (current instanceof GuildScreen guild) {
                    guild.update(data);
                } else {
                    minecraft.setScreen(new GuildScreen(data));
                }
            }
            case "titles" -> {
                if (!(current instanceof TitleListScreen)) {
                    minecraft.setScreen(new TitleListScreen(current instanceof GuildScreen ? current : null));
                }
            }
            case "record_book" -> {
                if (current instanceof RecordBookScreen book) {
                    book.update(data);
                } else {
                    minecraft.setScreen(new RecordBookScreen(data));
                }
            }
            case "connections" -> minecraft.setScreen(new ConnectionsScreen(data, current instanceof GuildScreen ? current : null));
            case "donation" -> {
                if (current instanceof DonationScreen donation) {
                    donation.update(data);
                } else {
                    minecraft.setScreen(new DonationScreen(data));
                }
            }
            default -> {
            }
        }
    }

    /** サーバーへ操作を送る（{@link jp.amemaru.lostconnection.guild.GuildService#onAction}）。 */
    public static void send(String action, CompoundTag args) {
        ModNetwork.sendToServer(new GuildActionC2S(action, args));
    }

    public static void send(String action) {
        send(action, new CompoundTag());
    }

    public static CompoundTag self() {
        return self;
    }

    public static CompoundTag catalog() {
        return catalog;
    }

    public static int version() {
        return version;
    }

    @Nullable
    public static Shown shown(UUID player) {
        return SHOWN.get(player);
    }

    /** 本人が表示している称号のID（なければ空）。 */
    public static String displayed() {
        return self.getString("displayed");
    }

    /** 本人が持っている称号（ID → 持っている称号の中身）。 */
    public static Map<String, CompoundTag> owned() {
        Map<String, CompoundTag> map = new HashMap<>();
        for (Tag tag : self.getList("titles", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) tag;
            map.put(one.getString("id"), one);
        }
        return map;
    }

    /** 受けているクエストのうち、画面の右上に出すもの。 */
    public static List<CompoundTag> trackedQuests() {
        List<CompoundTag> list = new ArrayList<>();
        ListTag quests = self.getList("quests", Tag.TAG_COMPOUND);
        for (Tag tag : quests) {
            CompoundTag one = (CompoundTag) tag;
            if (one.getBoolean("tracked")) {
                list.add(one);
            }
        }
        return list;
    }

    /** 抜けたとき（ほかのサーバーに入り直すときに、前の写しが残らないように）。 */
    public static void clear() {
        self = new CompoundTag();
        catalog = new CompoundTag();
        SHOWN.clear();
        version++;
    }
}

package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.network.s2c.SyncAdventureS2C;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * サーバーから受け取った「冒険の記録」の写し（クライアント専用）：進行日と章、結晶果、生産職と熟練度、図鑑、
 * 体に表示するアクセサリーのオン・オフ（自分の分とまわりの人の分）。
 */
public final class ClientAdventure {
    private static int day = 1;
    private static int chapter = 1;
    private static final int[] fruits = new int[StatType.COUNT];
    @Nullable
    private static ResourceLocation productionJob;
    private static final Map<ResourceLocation, Integer> mastery = new LinkedHashMap<>();
    private static int crafted;
    private static final Map<ResourceLocation, SyncAdventureS2C.Bestiary> bestiary = new LinkedHashMap<>();
    private static int ownHidden;
    private static long overflowExp;
    /** まわりの人（エンティティの番号）の、表示しない枠のビット。 */
    private static final Map<Integer, Integer> hiddenByEntity = new HashMap<>();

    private ClientAdventure() {
    }

    public static void apply(SyncAdventureS2C message) {
        day = message.day();
        chapter = message.chapter();
        int[] received = message.fruits();
        for (int i = 0; i < fruits.length; i++) {
            fruits[i] = i < received.length ? received[i] : 0;
        }
        productionJob = message.productionJob();
        mastery.clear();
        mastery.putAll(message.mastery());
        crafted = message.crafted();
        bestiary.clear();
        for (SyncAdventureS2C.Bestiary entry : message.bestiary()) {
            bestiary.put(entry.enemy(), entry);
        }
        ownHidden = message.hiddenAccessories();
        overflowExp = message.overflowExp();
    }

    public static void setHidden(int entityId, int bits) {
        hiddenByEntity.put(entityId, bits);
    }

    /** その人の、その枠のアクセサリーを体に表示するか。 */
    public static boolean accessoryVisible(int entityId, int slotIndex) {
        Integer bits = hiddenByEntity.get(entityId);
        return bits == null || (bits & (1 << slotIndex)) == 0;
    }

    /** 自分の、その枠のアクセサリーを体に表示するか。 */
    public static boolean ownVisible(int slotIndex) {
        return (ownHidden & (1 << slotIndex)) == 0;
    }

    public static int day() {
        return day;
    }

    public static int chapter() {
        return chapter;
    }

    public static int fruits(StatType type) {
        return fruits[type.ordinal()];
    }

    @Nullable
    public static ResourceLocation productionJob() {
        return productionJob;
    }

    public static int masteryExp(ResourceLocation job) {
        return mastery.getOrDefault(job, 0);
    }

    public static int crafted() {
        return crafted;
    }

    @Nullable
    public static SyncAdventureS2C.Bestiary bestiary(ResourceLocation enemy) {
        return bestiary.get(enemy);
    }

    public static List<SyncAdventureS2C.Bestiary> allBestiary() {
        return List.copyOf(bestiary.values());
    }

    public static long overflowExp() {
        return overflowExp;
    }

    /** ログアウトしたときに消す。 */
    public static void reset() {
        day = 1;
        chapter = 1;
        java.util.Arrays.fill(fruits, 0);
        productionJob = null;
        mastery.clear();
        crafted = 0;
        bestiary.clear();
        ownHidden = 0;
        overflowExp = 0;
        hiddenByEntity.clear();
    }
}

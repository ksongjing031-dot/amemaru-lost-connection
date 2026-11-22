package jp.amemaru.lostconnection.client;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * クライアントが覚えている、個人倉庫の中身の一覧（Phase 6。画面の「さがす」で使う）。開いている画面の番号と組で持つ。
 */
public final class ClientStorage {
    private static int containerId = -1;
    private static List<ItemStack> items = List.of();
    private static List<ItemStack> received = List.of();
    private static int version;
    private static long nextPrice = -1L;
    private static int maxPages = 5;

    private ClientStorage() {
    }

    public static void set(int id, List<ItemStack> newItems, List<ItemStack> newReceived, long price, int max) {
        containerId = id;
        nextPrice = price;
        maxPages = max;
        items = new ArrayList<>(newItems);
        received = new ArrayList<>(newReceived);
        version++;
    }

    /** その画面の一覧（ほかの画面のものなら空）。 */
    public static List<ItemStack> items(int id) {
        return id == containerId ? items : List.of();
    }

    public static List<ItemStack> received(int id) {
        return id == containerId ? received : List.of();
    }

    public static int version() {
        return version;
    }

    /** 次のページの値段（契約していなければ契約の値段）。増やせなければ -1。 */
    public static long nextPrice() {
        return nextPrice;
    }

    public static int maxPages() {
        return maxPages;
    }
}

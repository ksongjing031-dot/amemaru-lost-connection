package jp.amemaru.lostconnection.economy;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 個人倉庫（Phase 0-A 7章・20章、Phase 0-B 9章「倉庫を使う」。Phase 6）。
 *
 * <ul>
 *   <li>契約は買い切り。1ページ目 500ノット、増やすたびに 5,000／20,000／60,000／150,000、上限5ページ（config）</li>
 *   <li>使う所：個人倉庫の地下の小部屋の「呼び出しの封印箱」（中身）、1階のカウンターの「契約・拡張」の窓口と
 *       「受け取り口」（11月22日に決定：施設は右クリックでGUIを開いて使う）</li>
 *   <li>本人しか開けず、同じ倉庫を同時に2か所から開かない【確定】。新しく開くと、前の画面は閉じる</li>
 *   <li>中身はデスペナルティで失われず、ユニーク装備も預けられる【確定】（持ち物とは別に、ワールドに保存する）</li>
 *   <li>受け取り箱：市場で買った品などが届く。契約していなくても使える【補完】</li>
 * </ul>
 */
public final class StorageService {
    private StorageService() {
    }

    /** どの窓口から開いたか */
    public enum Access { BOX, CONTRACT, RECEIVE }

    /** 契約・拡張の結果 */
    public enum Result { OK, NOT_ENOUGH, ALREADY, MAX, NOT_CONTRACTED }

    /** 開いている倉庫（持ち主 → 画面）。同じ倉庫を2か所から開かないため */
    private static final Map<UUID, StorageMenu> OPEN = new HashMap<>();

    /** 倉庫の画面を開く。 */
    public static void open(ServerPlayer player, BlockPos pos, Access access) {
        StorageData storage = StorageData.get(player.server);
        StorageData.Box box = storage.box(player.getUUID());
        box.setName(player.getGameProfile().getName());
        StorageMenu old = OPEN.get(player.getUUID());
        if (old != null && player.containerMenu == old) {
            player.closeContainer();
        }
        int page = access == Access.RECEIVE ? StorageMenu.RECEIVE_PAGE_BASE
                : access == Access.CONTRACT || !box.contracted() ? StorageMenu.CONTRACT_PAGE : 0;
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, opener) -> {
            StorageMenu menu = new StorageMenu(id, inventory, storage, box, pos, page);
            OPEN.put(player.getUUID(), menu);
            return menu;
        }, Component.translatable("screen.amemaru.storage.title")), buf -> {
            buf.writeBlockPos(pos);
            buf.writeVarInt(page);
        });
    }

    /** 画面が閉じた。 */
    static void closed(UUID owner, StorageMenu menu) {
        OPEN.remove(owner, menu);
    }

    /** その人の倉庫が、いま開いている画面か。 */
    static boolean isCurrent(UUID owner, StorageMenu menu) {
        return OPEN.get(owner) == menu;
    }

    // ---------------------------------------------------------------- 値段

    /** いまのページ数から、次のページの値段（契約していなければ契約の値段）。もう増やせなければ -1。 */
    public static long nextPrice(int pages) {
        if (pages <= 0) {
            return ServerConfig.STORAGE_CONTRACT_PRICE.get();
        }
        if (pages >= maxPages()) {
            return -1L;
        }
        List<? extends Number> prices = ServerConfig.STORAGE_PAGE_PRICES.get();
        int index = pages - 1;
        return index < prices.size() ? Math.max(0L, ((Number) prices.get(index)).longValue()) : -1L;
    }

    /** ページ数の上限（設定。増やす値段の数＋1まで）。 */
    public static int maxPages() {
        return Math.max(1, Math.min(ServerConfig.STORAGE_MAX_PAGES.get(), ServerConfig.STORAGE_PAGE_PRICES.get().size() + 1));
    }

    // ---------------------------------------------------------------- 契約と拡張

    /** 契約する（1ページ目）。 */
    public static Result contract(ServerPlayer player, StorageData.Box box) {
        if (box.contracted()) {
            return Result.ALREADY;
        }
        return buyPage(player, box, "storage contract");
    }

    /** ページを1つ増やす。 */
    public static Result expand(ServerPlayer player, StorageData.Box box) {
        if (!box.contracted()) {
            return Result.NOT_CONTRACTED;
        }
        return buyPage(player, box, "storage expand");
    }

    private static Result buyPage(ServerPlayer player, StorageData.Box box, String reason) {
        long price = nextPrice(box.pages());
        if (price < 0) {
            return Result.MAX;
        }
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null || !Wallet.pay(player, data, price, reason)) {
            return Result.NOT_ENOUGH;
        }
        box.setPages(box.pages() + 1);
        StorageData.get(player.server).setDirty();
        return Result.OK;
    }

    /** 契約・拡張の結果を知らせる。 */
    static void tell(ServerPlayer player, Result result, StorageData.Box box, boolean contract) {
        switch (result) {
            case OK -> {
                player.sendSystemMessage(Component.translatable(contract ? "message.amemaru.storage.contracted"
                        : "message.amemaru.storage.expanded", box.pages(), box.capacity()).withStyle(ChatFormatting.GOLD));
                player.level().playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.4F);
            }
            case NOT_ENOUGH -> player.sendSystemMessage(Component.translatable("message.amemaru.storage.not_enough",
                    KnotTransfer.format(nextPrice(box.pages()))).withStyle(ChatFormatting.RED));
            case ALREADY -> player.sendSystemMessage(Component.translatable("message.amemaru.storage.already").withStyle(ChatFormatting.RED));
            case MAX -> player.sendSystemMessage(Component.translatable("message.amemaru.storage.max", maxPages()).withStyle(ChatFormatting.RED));
            case NOT_CONTRACTED -> player.sendSystemMessage(Component.translatable("message.amemaru.storage.not_contracted")
                    .withStyle(ChatFormatting.RED));
        }
    }

    // ---------------------------------------------------------------- 受け取り箱

    /**
     * 受け取り箱に届ける（市場で買った品・取り下げた品など）。いっぱいなら届けずに false。
     * 持ち主がオフラインでも届く。
     */
    public static boolean deliver(MinecraftServer server, UUID owner, String ownerName, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        StorageData storage = StorageData.get(server);
        StorageData.Box box = storage.box(owner);
        if (box.name().isEmpty()) {
            box.setName(ownerName);
        }
        if (!OPEN.containsKey(owner)) {
            // 画面を開いていなければ、取り出した後の空いた所を詰める（開いている間は、枠の並びを変えない）
            box.compactReceived();
        }
        if (box.receivedCount() >= ServerConfig.value(ServerConfig.STORAGE_RECEIVED_MAX)) {
            return false;
        }
        box.received().add(stack.copy());
        storage.setDirty();
        StorageMenu menu = OPEN.get(owner);
        if (menu != null) {
            menu.markContentsChanged();
        }
        return true;
    }

    /** 受け取り箱にあと何個入るか。 */
    public static int receivedRoom(MinecraftServer server, UUID owner) {
        StorageData.Box box = StorageData.get(server).peek(owner);
        int used = box == null ? 0 : box.receivedCount();
        return Math.max(0, ServerConfig.value(ServerConfig.STORAGE_RECEIVED_MAX) - used);
    }

    /** 受け取り箱の品を、持ち物に入るだけ移す。移した品の数を返す。 */
    public static int takeReceived(ServerPlayer player, StorageData.Box box) {
        int moved = 0;
        for (int i = 0; i < box.received().size(); i++) {
            ItemStack stack = box.received().get(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack copy = stack.copy();
            if (player.getInventory().add(copy) && copy.isEmpty()) {
                box.received().set(i, ItemStack.EMPTY);
                moved++;
            } else {
                // 一部だけ入った：残りを戻す
                box.received().set(i, copy);
                break;
            }
        }
        StorageData.get(player.server).setDirty();
        return moved;
    }

    // ---------------------------------------------------------------- 並べ替え

    /** 倉庫の中身を並べ替える：同じ品をまとめ、種類（アイテムのID）と名前の順に前から詰める。 */
    public static void sort(StorageData.Box box) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : box.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = stack.copy();
            for (ItemStack target : merged) {
                if (rest.isEmpty()) {
                    break;
                }
                if (ItemStack.isSameItemSameTags(target, rest) && target.getCount() < target.getMaxStackSize()) {
                    int move = Math.min(rest.getCount(), target.getMaxStackSize() - target.getCount());
                    target.grow(move);
                    rest.shrink(move);
                }
            }
            if (!rest.isEmpty()) {
                merged.add(rest);
            }
        }
        merged.sort(Comparator.comparing((ItemStack stack) -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                .thenComparing(stack -> stack.getHoverName().getString())
                .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed()));
        for (int i = 0; i < box.capacity(); i++) {
            box.set(i, i < merged.size() ? merged.get(i) : ItemStack.EMPTY);
        }
    }
}

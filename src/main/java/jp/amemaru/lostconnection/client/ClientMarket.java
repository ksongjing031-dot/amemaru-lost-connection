package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.network.s2c.MarketMineS2C;
import jp.amemaru.lostconnection.network.s2c.MarketPageS2C;
import jp.amemaru.lostconnection.network.s2c.MarketPriceS2C;
import jp.amemaru.lostconnection.network.s2c.MarketStallsS2C;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.List;

/** フリーマーケットの、クライアントの写し（Phase 6）：露店の様子・取引所の一覧・自分の出品・出品の相場。 */
public final class ClientMarket {
    private static MarketStallsS2C stalls = new MarketStallsS2C(List.of(), null, null, List.of(), 0, 0, 0, false);
    @Nullable
    private static MarketPageS2C page;
    @Nullable
    private static MarketMineS2C mine;
    @Nullable
    private static MarketPriceS2C price;
    /** 届くたびに増える（画面が、新しい中身が来たかを見る） */
    private static int version;

    private ClientMarket() {
    }

    public static MarketStallsS2C stalls() {
        return stalls;
    }

    public static void setStalls(MarketStallsS2C value) {
        stalls = value;
        version++;
    }

    @Nullable
    public static MarketPageS2C page() {
        return page;
    }

    public static void setPage(MarketPageS2C value) {
        page = value;
        version++;
    }

    @Nullable
    public static MarketMineS2C mine() {
        return mine;
    }

    public static void setMine(MarketMineS2C value) {
        mine = value;
        version++;
    }

    @Nullable
    public static MarketPriceS2C price() {
        return price;
    }

    public static void setPrice(@Nullable MarketPriceS2C value) {
        price = value;
        version++;
    }

    public static int version() {
        return version;
    }

    /** その場所を含む露店（なければ null）。 */
    @Nullable
    public static MarketStallsS2C.View stallAt(BlockPos pos) {
        for (MarketStallsS2C.View view : stalls.stalls()) {
            if (view.active() && view.contains(pos)) {
                return view;
            }
        }
        return null;
    }

    @Nullable
    public static MarketStallsS2C.View stall(int num) {
        for (MarketStallsS2C.View view : stalls.stalls()) {
            if (view.num() == num) {
                return view;
            }
        }
        return null;
    }

    /** 取引所の画面を開くとき、前の一覧を消す（新しい中身が届くまで、古いものを見せない）。 */
    public static void resetViews() {
        page = null;
        mine = null;
        price = null;
        version++;
    }

    /** ログアウトしたら消す。 */
    public static void clear() {
        stalls = new MarketStallsS2C(List.of(), null, null, List.of(), 0, 0, 0, false);
        page = null;
        mine = null;
        price = null;
        version++;
    }
}

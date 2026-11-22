package jp.amemaru.lostconnection.client;

import net.minecraft.nbt.CompoundTag;

/**
 * 装飾工房の台の品の、厳選の見積もり（Phase 6 その4。サーバーから {@code RefineQuoteS2C} で届く）。
 */
public final class ClientRefine {
    private static CompoundTag quote = new CompoundTag();

    private ClientRefine() {
    }

    public static void setQuote(CompoundTag tag) {
        quote = tag == null ? new CompoundTag() : tag;
    }

    public static CompoundTag quote() {
        return quote;
    }

    public static void clear() {
        quote = new CompoundTag();
    }
}

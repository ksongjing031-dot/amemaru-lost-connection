package jp.amemaru.lostconnection.casino;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * サーバー全体のカジノ（Phase 7 その3）：カジノナイト（Phase 0-A 15章「夜の催し：不定期（運営が開催）。4人以上の卓に配当ボーナス」）の
 * 最中か・何回目か・終わる時刻と、館の勘定（賭けられたチップと払い戻したチップの合計）。
 */
public final class CasinoData extends SavedData {
    private static final String NAME = "amemaru_casino";

    private boolean night;
    private int nightHeld;
    private long nightEndMillis;
    private long wagered;
    private long paid;

    public static CasinoData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CasinoData::load, CasinoData::new, NAME);
    }

    public boolean night() {
        return night;
    }

    public int nightHeld() {
        return nightHeld;
    }

    public long nightEndMillis() {
        return nightEndMillis;
    }

    void startNight(long endMillis) {
        night = true;
        nightHeld++;
        nightEndMillis = endMillis;
        setDirty();
    }

    void endNight() {
        night = false;
        nightEndMillis = 0;
        setDirty();
    }

    public long wagered() {
        return wagered;
    }

    public long paid() {
        return paid;
    }

    void account(long stake, long back) {
        wagered += stake;
        paid += back;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("night", night);
        tag.putInt("nightHeld", nightHeld);
        tag.putLong("nightEnd", nightEndMillis);
        tag.putLong("wagered", wagered);
        tag.putLong("paid", paid);
        return tag;
    }

    static CasinoData load(CompoundTag tag) {
        CasinoData data = new CasinoData();
        data.night = tag.getBoolean("night");
        data.nightHeld = tag.getInt("nightHeld");
        data.nightEndMillis = tag.getLong("nightEnd");
        data.wagered = tag.getLong("wagered");
        data.paid = tag.getLong("paid");
        return data;
    }
}

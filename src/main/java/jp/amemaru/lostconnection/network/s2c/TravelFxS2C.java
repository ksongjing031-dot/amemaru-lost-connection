package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 塔を進む・戻るときの魔法陣（11月27日のご指示「塔を進むときや戻るときの場所のエフェクトがminecraftのエフェクトではなく魔法陣の
 * エフェクトにしてわかりやすくする」【補完】）。発つ所（{@code arrive} が false）には、足もとの青い魔法陣と、上へ昇る光の柱と、集まる
 * 光の筋。着く所（true）には、金色の魔法陣が広がり、光の輪と光。クライアントは {@code TravelFx}。音もバニラのエンダーマンの音から、
 * 澄んだ鐘のような音に変えた。
 */
public final class TravelFxS2C {
    private static final double RANGE = 48.0;

    private final Vec3 at;
    private final boolean arrive;

    public TravelFxS2C(Vec3 at, boolean arrive) {
        this.at = at;
        this.arrive = arrive;
    }

    /** 発つ所（移る前の場所）に出す。 */
    public static void depart(ServerLevel level, Vec3 at) {
        send(level, at, false);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.5F);
    }

    /** 着く所（移った先）に出す。 */
    public static void arrive(ServerLevel level, Vec3 at) {
        send(level, at, true);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 0.8F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    private static void send(ServerLevel level, Vec3 at, boolean arrive) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() ->
                new PacketDistributor.TargetPoint(at.x, at.y, at.z, RANGE, level.dimension())), new TravelFxS2C(at, arrive));
    }

    public static TravelFxS2C decode(FriendlyByteBuf buf) {
        return new TravelFxS2C(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(at.x);
        buf.writeDouble(at.y);
        buf.writeDouble(at.z);
        buf.writeBoolean(arrive);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> jp.amemaru.lostconnection.client.TravelFx.play(this));
    }

    public Vec3 at() {
        return at;
    }

    public boolean arrive() {
        return arrive;
    }
}

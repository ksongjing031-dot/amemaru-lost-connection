package jp.amemaru.lostconnection.player;

import jp.amemaru.lostconnection.admin.CityCommands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * 教会での復活の場所：結びの教会の「呼び戻しの円」（Phase 4。Phase 0-A 7章・13章・20章、セレナの「呼び戻しの儀」）。
 *
 * <p>場所は、次の順に決める。</p>
 * <ol>
 *   <li>管理者が {@code /amemaru admin church set} で決めた場所（ワールドの data の {@code amemaru_church.dat}）</li>
 *   <li>確かめ用の街を建てていれば、その教会の呼び戻しの円の真ん中（{@link CityCommands#churchCircle}）</li>
 *   <li>ワールドの初めの場所（スポーン地点）</li>
 * </ol>
 *
 * <p>同時に戻った人が重ならないように、円の中で少しばらす（config の {@code churchScatter}。足もとがあり、体が
 * 入る所だけ）。向きは、決めた向き（呼び戻しの円では西の祭壇の方）。</p>
 */
public final class ChurchPoint extends SavedData {
    private static final String NAME = "amemaru_church";

    @Nullable
    private ResourceKey<Level> dimension;
    private double x;
    private double y;
    private double z;
    private float yaw;

    /** 決めた場所の出どころ */
    public enum Source { SET, CITY, SPAWN }

    /** 戻る場所 */
    public record Spot(ServerLevel level, Vec3 pos, float yaw, Source source) {
    }

    public static ChurchPoint get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ChurchPoint::load, ChurchPoint::new, NAME);
    }

    static ChurchPoint load(CompoundTag tag) {
        ChurchPoint point = new ChurchPoint();
        if (tag.contains("dimension")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("dimension"));
            if (id != null) {
                point.dimension = ResourceKey.create(Registries.DIMENSION, id);
                point.x = tag.getDouble("x");
                point.y = tag.getDouble("y");
                point.z = tag.getDouble("z");
                point.yaw = tag.getFloat("yaw");
            }
        }
        return point;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (dimension != null) {
            tag.putString("dimension", dimension.location().toString());
            tag.putDouble("x", x);
            tag.putDouble("y", y);
            tag.putDouble("z", z);
            tag.putFloat("yaw", yaw);
        }
        return tag;
    }

    /** 管理者が場所を決める。 */
    public void set(ServerLevel level, Vec3 pos, float yaw) {
        this.dimension = level.dimension();
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.yaw = yaw;
        setDirty();
    }

    /** 管理者が決めた場所を消す（街の教会か、スポーン地点に戻る）。 */
    public void clear() {
        dimension = null;
        setDirty();
    }

    /** 戻る場所（上の順に決める）。 */
    public static Spot resolve(MinecraftServer server) {
        ChurchPoint point = get(server);
        if (point.dimension != null) {
            ServerLevel level = server.getLevel(point.dimension);
            if (level != null) {
                return new Spot(level, new Vec3(point.x, point.y, point.z), point.yaw, Source.SET);
            }
        }
        ServerLevel overworld = server.overworld();
        Optional<Vec3> circle = CityCommands.churchCircle(overworld);
        if (circle.isPresent()) {
            // 祭壇（西）を向く
            return new Spot(overworld, circle.get(), 90.0F, Source.CITY);
        }
        BlockPos spawn = overworld.getSharedSpawnPos();
        return new Spot(overworld, Vec3.atBottomCenterOf(spawn), overworld.getSharedSpawnAngle(), Source.SPAWN);
    }

    /** 円の中で、少しばらした場所（足もとがあり、体が入る所。見つからなければ真ん中）。 */
    public static Vec3 scatter(Spot spot, double radius, RandomSource random) {
        if (radius <= 0.0) {
            return spot.pos();
        }
        EntityDimensions body = EntityDimensions.scalable(0.6F, 1.8F);
        for (int i = 0; i < 12; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double r = radius * Math.sqrt(random.nextDouble());
            Vec3 at = new Vec3(spot.pos().x + Mth.cos((float) angle) * r, spot.pos().y, spot.pos().z + Mth.sin((float) angle) * r);
            AABB box = body.makeBoundingBox(at);
            BlockPos below = BlockPos.containing(at.x, at.y - 0.5, at.z);
            if (spot.level().noCollision(box) && !spot.level().getBlockState(below).getCollisionShape(spot.level(), below).isEmpty()) {
                return at;
            }
        }
        return spot.pos();
    }
}

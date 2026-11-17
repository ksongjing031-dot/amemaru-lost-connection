package jp.amemaru.lostconnection.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.amemaru.lostconnection.core.registry.ModParticles;
import jp.amemaru.lostconnection.network.s2c.StatusFxS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 相手にかかっている状態の見た目（クライアント専用）。サーバーの {@link StatusFxS2C} で覚え、残り時間で消す。
 *
 * <ul>
 *   <li>凍え：頭の上に雪の結晶（数だけ）と、足もとの冷気。凍結：体を包む氷の塊。</li>
 *   <li>感電：体のまわりを走る小さな稲妻。星痕：頭の上を回る星（数だけ）。防御崩し：ひびの入った盾の印。よろめき：頭のまわりの星。</li>
 *   <li>プレイヤー：光の帳（光の球）・誓いの円陣（足もとの紋）・受け流しの構え・城壁の構え（前の光の壁）・不屈の誓い（金のオーラ）・
 *       不落の城（白い光）・盾結び（頭の上の盾）。</li>
 * </ul>
 * <p>自分の体の見た目は、一人称のときは描かない（目の前をふさがないため）。</p>
 */
public final class StatusVisuals {
    private static final class State {
        final EnumMap<StatusFxS2C.Kind, long[]> kinds = new EnumMap<>(StatusFxS2C.Kind.class);
    }

    private static final Map<Integer, State> STATES = new HashMap<>();

    // 色
    private static final int ICE = 0xBFF2FF;
    private static final int FROST = 0x7FD8FF;
    private static final int GOLD = 0xFFD27A;
    private static final int SPARK = 0xFFF2A0;
    private static final int EMBER = 0xFF8C40;
    private static final int KNIGHT = 0x8FD3FF;
    /** 頭の上の印の影（明るい空の前でも形が見えるように、光の下に敷く濃い色）。 */
    private static final int MARK_SHADE_FROST = 0x1F5F9A;
    private static final int MARK_SHADE_GOLD = 0x8A5200;
    private static final int MARK_SHADE_EMBER = 0x7A2400;
    /** 頭の上の印の1段の高さ。 */
    private static final float MARK_ROW = 0.3F;

    private StatusVisuals() {
    }

    /** 頭の上の印が占める高さ（浮かぶ数字は、この上から出す。印と数字が重ならないように）。 */
    public static float markHeight(int entityId) {
        State state = STATES.get(entityId);
        if (state == null) {
            return 0.0F;
        }
        int rows = 0;
        for (StatusFxS2C.Kind kind : new StatusFxS2C.Kind[]{StatusFxS2C.Kind.CHILL, StatusFxS2C.Kind.BRAND,
                StatusFxS2C.Kind.ARMOR_BREAK, StatusFxS2C.Kind.SHIELD_BOND}) {
            if (state.kinds.containsKey(kind)) {
                rows++;
            }
        }
        return rows * MARK_ROW;
    }

    /** その相手に、今その状態が付いているか（訓練人形の防御の表示などに使う）。 */
    public static boolean has(int entityId, StatusFxS2C.Kind kind) {
        State state = STATES.get(entityId);
        return state != null && state.kinds.containsKey(kind);
    }

    /** サーバーから、ある相手の状態が届いた。 */
    public static void handle(StatusFxS2C message) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        State state = new State();
        for (StatusFxS2C.Entry entry : message.entries()) {
            state.kinds.put(entry.kind(), new long[]{entry.level(), now + entry.ticks()});
        }
        if (state.kinds.isEmpty()) {
            STATES.remove(message.entityId());
        } else {
            STATES.put(message.entityId(), state);
        }
        Entity entity = level.getEntity(message.entityId());
        if (entity != null) {
            event(level, entity, message.event());
        }
    }

    /** その瞬間の演出（凍結した・砕けた・星痕が弾けた・受け流した…）。 */
    private static void event(ClientLevel level, Entity entity, StatusFxS2C.Event event) {
        Vec3 center = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
        float size = Math.max(entity.getBbWidth(), entity.getBbHeight() * 0.6F);
        switch (event) {
            case FROZE -> {
                Fx.burst(level, ModParticles.SNOW.get(), center, 24, ICE, 0.18F, 30, 0.25);
                Fx.burst(level, ModParticles.GLOW.get(), center, 10, FROST, 0.6F, 12, 0.08);
                FxWorld.add(new FxWorld.Shockwave(entity.position(), 0.3F, size * 1.6F + 0.8F, 10, ICE, 0.8F, 0.8F));
            }
            case SHATTERED -> {
                Fx.burst(level, ModParticles.SHARD.get(), center, 40, ICE, 0.35F, 30, 0.55);
                Fx.burst(level, ModParticles.SPARK.get(), center, 16, 0xFFFFFF, 0.12F, 10, 0.6);
                FxWorld.add(new FxWorld.Flare(FxWorld.Anchor.at(center), size * 3.0F + 1.0F, ICE, 8, FxTextures.STAR, 0.3F, 2));
                FxWorld.add(new FxWorld.Shockwave(entity.position(), 0.3F, size * 2.5F + 1.5F, 12, ICE, 1.2F, 0.9F));
                FxWorld.shake(center, 5, 0.6F);
            }
            case DETONATED -> {
                Fx.burst(level, ModParticles.STAR.get(), center, 18, GOLD, 0.45F, 20, 0.35);
                Fx.burst(level, ModParticles.SPARK.get(), center, 12, 0xFFF2C0, 0.12F, 12, 0.5);
            }
            case DISCHARGED -> Fx.burst(level, ModParticles.SPARK.get(), center, 14, SPARK, 0.1F, 10, 0.5);
            case ARMOR_BROKEN -> {
                Fx.burst(level, ModParticles.SHARD.get(), center, 16, EMBER, 0.25F, 24, 0.35);
                FxWorld.add(new FxWorld.Flare(FxWorld.Anchor.center(entity.getId(), new Vec3(0.0, entity.getBbHeight() * 0.6, 0.0)),
                        1.2F, EMBER, 10, FxTextures.CRACK, 0.0F, 2));
            }
            case PARRIED -> {
                Vec3 front = center.add(entity.getLookAngle().scale(0.8));
                FxWorld.add(new FxWorld.Flare(FxWorld.Anchor.at(front), 2.6F, 0xFFFFFF, 7, FxTextures.STAR, 0.4F, 1));
                FxWorld.add(new FxWorld.Flare(FxWorld.Anchor.at(front), 1.8F, KNIGHT, 10, FxTextures.SHIELD, 0.0F, 1));
                Fx.burst(level, ModParticles.SPARK.get(), front, 24, 0xFFF6D0, 0.12F, 12, 0.7);
                FxWorld.shake(center, 4, 0.5F);
            }
            case BARRIER_BROKEN -> {
                Fx.burst(level, ModParticles.SHARD.get(), center, 30, GOLD, 0.3F, 26, 0.45);
                FxWorld.add(new FxWorld.Shockwave(entity.position(), 0.5F, 2.4F, 10, GOLD, 1.0F, 0.8F));
            }
            case NONE -> {
            }
        }
    }

    /** 毎 tick：時間切れを消し、続く状態のパーティクルを出す。 */
    static void tick(ClientLevel level) {
        long now = level.getGameTime();
        RandomSource random = level.random;
        Iterator<Map.Entry<Integer, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, State> entry = it.next();
            State state = entry.getValue();
            state.kinds.values().removeIf(value -> value[1] <= now);
            Entity entity = level.getEntity(entry.getKey());
            if (state.kinds.isEmpty() || entity == null || !entity.isAlive()) {
                it.remove();
                continue;
            }
            Vec3 feet = entity.position();
            float width = entity.getBbWidth();
            float height = entity.getBbHeight();
            if (state.kinds.containsKey(StatusFxS2C.Kind.CHILL) && random.nextInt(3) == 0) {
                Fx.scatter(level, ModParticles.SNOW.get(), feet.add(0.0, height * 0.2, 0.0), width * 0.8, 1, ICE, 0.1F, 24, height * 0.8,
                        new Vec3(0.0, -0.01, 0.0));
            }
            if (state.kinds.containsKey(StatusFxS2C.Kind.FROZEN) && random.nextInt(4) == 0) {
                Fx.scatter(level, ModParticles.GLOW.get(), feet, width, 1, FROST, 0.35F, 20, 0.3, new Vec3(0.0, 0.01, 0.0));
            }
            if (state.kinds.containsKey(StatusFxS2C.Kind.SHOCK) && random.nextInt(3) == 0) {
                Vec3 at = feet.add((random.nextDouble() - 0.5) * width, random.nextDouble() * height, (random.nextDouble() - 0.5) * width);
                Fx.burst(level, ModParticles.SPARK.get(), at, 2, SPARK, 0.07F, 6, 0.25);
            }
            if (state.kinds.containsKey(StatusFxS2C.Kind.UNYIELDING) && random.nextInt(2) == 0) {
                Fx.ring(level, ModParticles.GLOW.get(), feet.add(0.0, 0.1, 0.0), 0.7, 2, GOLD, 0.25F, 20, 0.0, 0.06);
            }
            if (state.kinds.containsKey(StatusFxS2C.Kind.ARMOR_BREAK) && random.nextInt(6) == 0) {
                Fx.burst(level, ModParticles.SPARK.get(), feet.add(0.0, height * 0.6, 0.0), 1, EMBER, 0.06F, 10, 0.12);
            }
        }
    }

    /** 毎フレーム描く。 */
    static void render(FxDraw draw, ClientLevel level) {
        if (STATES.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long now = level.getGameTime();
        float partial = draw.partialTick();
        float time = now + partial;
        for (Map.Entry<Integer, State> entry : STATES.entrySet()) {
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null) {
                continue;
            }
            boolean firstPersonSelf = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
            Vec3 feet = entity.getPosition(partial);
            float width = entity.getBbWidth();
            float height = entity.getBbHeight();
            Vec3 head = feet.add(0.0, height + 0.45, 0.0);
            State state = entry.getValue();
            long[] value;

            if (!firstPersonSelf && (value = state.kinds.get(StatusFxS2C.Kind.FROZEN)) != null) {
                float remain = Mth.clamp((value[1] - time) / 10.0F, 0.0F, 1.0F);
                AABB box = entity.getBoundingBox().move(feet.subtract(entity.position())).inflate(0.18, 0.1, 0.18);
                draw.box(draw.translucent(FxTextures.ICE), box, ICE, 0.55F * remain);
                draw.box(draw.glow(FxTextures.ICE), box.inflate(0.02), FROST, 0.35F * remain);
            }
            // 頭の上の印は、同じ高さに横に並べる
            float row = 0.0F;
            if ((value = state.kinds.get(StatusFxS2C.Kind.CHILL)) != null && !firstPersonSelf) {
                int count = (int) value[0];
                // 明るい空の前でも見えるよう、先に濃い色の影を重ねてから光らせる
                VertexConsumer shade = draw.translucent(FxTextures.SNOWFLAKE);
                for (int i = 0; i < count; i++) {
                    Vec3 at = sideways(draw, head.add(0.0, row, 0.0), (i - (count - 1) / 2.0F) * 0.26F);
                    draw.billboard(shade, at, 0.3F, time * 0.05F + i, MARK_SHADE_FROST, 0.7F);
                }
                VertexConsumer vc = draw.glow(FxTextures.SNOWFLAKE);
                for (int i = 0; i < count; i++) {
                    Vec3 at = sideways(draw, head.add(0.0, row, 0.0), (i - (count - 1) / 2.0F) * 0.26F);
                    draw.billboard(vc, at, 0.26F, time * 0.05F + i, count >= 4 ? 0xFFFFFF : ICE, 0.65F);
                }
                draw.ground(draw.glow(FxTextures.RING), feet.add(0.0, 0.04, 0.0), width * 0.9F + 0.2F, 0.0F, FROST,
                        0.12F + 0.08F * count);
                row += MARK_ROW;
            }
            if ((value = state.kinds.get(StatusFxS2C.Kind.BRAND)) != null && !firstPersonSelf) {
                int count = (int) value[0];
                Vec3[] at = new Vec3[count];
                for (int i = 0; i < count; i++) {
                    float angle = time * 0.12F + i * Mth.TWO_PI / count;
                    float r = width * 0.5F + 0.35F;
                    at[i] = head.add(Mth.cos(angle) * r, row + 0.05 + Mth.sin(time * 0.2F + i) * 0.05, Mth.sin(angle) * r);
                }
                VertexConsumer shade = draw.translucent(FxTextures.STAR);
                for (int i = 0; i < count; i++) {
                    draw.billboard(shade, at[i], 0.4F + 0.04F * count, time * 0.1F, MARK_SHADE_GOLD, 0.75F);
                }
                VertexConsumer vc = draw.glow(FxTextures.STAR);
                for (int i = 0; i < count; i++) {
                    draw.billboard(vc, at[i], 0.34F + 0.04F * count, time * 0.1F, count >= 5 ? 0xFFFFFF : GOLD, 0.6F);
                }
                row += MARK_ROW;
            }
            if ((value = state.kinds.get(StatusFxS2C.Kind.ARMOR_BREAK)) != null && !firstPersonSelf) {
                float pulse = 0.5F + 0.15F * Mth.sin(time * 0.3F);
                draw.billboard(draw.translucent(FxTextures.CRACK), head.add(0.0, row + 0.05, 0.0), 0.46F, 0.0F, MARK_SHADE_EMBER, 0.7F);
                draw.billboard(draw.glow(FxTextures.CRACK), head.add(0.0, row + 0.05, 0.0), 0.42F, 0.0F, EMBER, pulse);
                row += MARK_ROW;
            }
            if ((value = state.kinds.get(StatusFxS2C.Kind.STAGGER)) != null && !firstPersonSelf) {
                VertexConsumer vc = draw.glow(FxTextures.STAR);
                for (int i = 0; i < 3; i++) {
                    float angle = time * 0.35F + i * Mth.TWO_PI / 3.0F;
                    Vec3 at = feet.add(Mth.cos(angle) * (width * 0.5 + 0.2), height + 0.12, Mth.sin(angle) * (width * 0.5 + 0.2));
                    draw.billboard(vc, at, 0.22F, time * 0.2F, 0xFFE27A, 0.9F);
                }
            }
            if ((value = state.kinds.get(StatusFxS2C.Kind.SHOCK)) != null && !firstPersonSelf) {
                long seed = entity.getId() * 31L + (long) (time / 2.0F);
                java.util.Random random = new java.util.Random(seed);
                for (int i = 0; i < 2; i++) {
                    Vec3 a = feet.add((random.nextDouble() - 0.5) * width * 1.2, random.nextDouble() * height,
                            (random.nextDouble() - 0.5) * width * 1.2);
                    Vec3 b = feet.add((random.nextDouble() - 0.5) * width * 1.2, random.nextDouble() * height,
                            (random.nextDouble() - 0.5) * width * 1.2);
                    draw.polyline(draw.glow(FxTextures.LIGHTNING), FxDraw.lightningPoints(a, b, 4, 0.35F, seed + i), 0.09F, SPARK, 0.9F);
                }
            }
            // プレイヤー
            if ((value = state.kinds.get(StatusFxS2C.Kind.BARRIER)) != null && !firstPersonSelf) {
                float strength = Mth.clamp(value[0] / 5.0F, 0.2F, 1.0F);
                draw.sphere(draw.glow(FxTextures.DOME), feet.add(0.0, height * 0.5, 0.0), Math.max(width, height) * 0.62F + 0.2F, 8, 20,
                        false, time * 0.003F, GOLD, 0.3F + 0.25F * strength);
            }
            if ((value = state.kinds.get(StatusFxS2C.Kind.WARD)) != null) {
                draw.ground(draw.glow(FxTextures.SIGIL_OATH), feet.add(0.0, 0.05, 0.0), 0.75F, time * 0.03F, KNIGHT, 0.55F);
            }
            if ((value = state.kinds.get(StatusFxS2C.Kind.PARRY)) != null && !firstPersonSelf) {
                Vec3 look = Vec3.directionFromRotation(0.0F, Mth.lerp(partial, entity.yRotO, entity.getYRot()));
                draw.billboard(draw.glow(FxTextures.SHIELD), feet.add(0.0, height * 0.55, 0.0).add(look.scale(0.7)), 1.3F, 0.0F,
                        0xFFFFFF, 0.6F + 0.3F * Mth.sin(time * 1.3F));
            }
            if ((value = state.kinds.get(StatusFxS2C.Kind.RAMPART)) != null && !firstPersonSelf) {
                FxWorld.FrontWall.drawWall(draw, entity, partial, 1.1F, 1.9F, 110.0F, KNIGHT, 0.45F + 0.1F * Mth.sin(time * 0.2F),
                        FxTextures.DOME);
            }
            if ((value = state.kinds.get(StatusFxS2C.Kind.FORTRESS)) != null && !firstPersonSelf) {
                draw.ringWall(draw.glow(FxTextures.WALL), feet, width * 0.8F + 0.25F, height * 0.9F, 16, time * 0.02F, 2.0F, 0xE8F4FF,
                        0.35F, 0.0F);
            }
            if ((value = state.kinds.get(StatusFxS2C.Kind.SHIELD_BOND)) != null && !firstPersonSelf) {
                draw.billboard(draw.glow(FxTextures.SHIELD), head.add(0.0, row + 0.05, 0.0), 0.38F, 0.0F, KNIGHT, 0.85F);
            }
        }
    }

    /** 頭の上の印を、画面の横向きにずらす。 */
    private static Vec3 sideways(FxDraw draw, Vec3 at, float amount) {
        org.joml.Vector3f left = draw.camera().getLeftVector();
        return at.add(left.x() * amount, left.y() * amount, left.z() * amount);
    }

    /** 今その相手にかかっている状態の重なり（なければ0。演出の大きさに使う）。 */
    public static int level(int entityId, StatusFxS2C.Kind kind) {
        State state = STATES.get(entityId);
        long[] value = state == null ? null : state.kinds.get(kind);
        return value == null ? 0 : (int) value[0];
    }

    public static void reset() {
        STATES.clear();
    }
}

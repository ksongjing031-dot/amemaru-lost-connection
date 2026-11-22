package jp.amemaru.lostconnection.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.amemaru.lostconnection.core.registry.ModParticles;
import jp.amemaru.lostconnection.network.s2c.StatusFxS2C;
import jp.amemaru.lostconnection.particle.FxParticleOptions;
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
 *   <li>Phase 6 その2：毒（緑の泡）・標（頭の上の赤い印）・縫い止め（足もとの糸の紋）・戒め（頭の上の鈴の印）・煙（頭のまわりの煙）。
 *       味方の強化：加護（金の輪）・守り（淡い膜）・鋭い目・疾さ（風）・結びの力（胸の灯）・狙撃の構え・命の糸（胸の金の輪）・
 *       身代わりの鈴（頭の上の鈴）・星の環（回る星）・影分身（紫の影）・毒塗り（したたる毒）・弓舞（舞う羽根）・影走り・天地流転・
 *       癒し続け（花びら）・潜伏（立ちのぼる影）。</li>
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
    private static final int VENOM = 0x9CF060;
    private static final int MARK_RED = 0xFF5A4A;
    private static final int BELL = 0xFFE7A0;
    private static final int LIFE = 0xB8FF9C;
    private static final int WIND = 0xA8F0C8;
    private static final int SHADOW = 0x8A6CFF;
    private static final int DUSK = 0x3A2E5A;
    private static final int FIST = 0xFFB46E;
    private static final int KI = 0x9FDCFF;
    private static final int MARK_SHADE_RED = 0x6A1010;
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
                StatusFxS2C.Kind.ARMOR_BREAK, StatusFxS2C.Kind.SHIELD_BOND, StatusFxS2C.Kind.MARK, StatusFxS2C.Kind.ADMONISHED,
                StatusFxS2C.Kind.GUARDIAN_BELL}) {
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
            case CLEANSED -> {
                Fx.burst(level, ModParticles.STAR.get(), center, 14, 0xFFFFFF, 0.22F, 18, 0.3);
                FxWorld.add(new FxWorld.Shockwave(entity.position(), 0.3F, 1.6F, 10, 0xFFFFFF, 0.6F, 0.6F));
            }
            case GUARDIAN_SAVED -> {
                FxWorld.add(new FxWorld.Flare(FxWorld.Anchor.at(center), size * 3.0F + 1.5F, BELL, 12, FxTextures.STAR, 0.2F, 2));
                FxWorld.add(new FxWorld.Shockwave(entity.position(), 0.5F, 3.5F, 16, BELL, 1.4F, 1.0F));
                FxWorld.add(new FxWorld.Pillar(FxWorld.Anchor.entity(entity.getId(), Vec3.ZERO), 0.8F, 6.0F, BELL, 20));
                Fx.burst(level, ModParticles.STAR.get(), center, 30, BELL, 0.3F, 26, 0.5);
                FxWorld.shake(center, 6, 0.6F);
            }
            case THREAD_BURST -> {
                Fx.burst(level, ModParticles.PETAL.get(), center, 20, GOLD, 0.22F, 30, 0.3);
                FxWorld.add(new FxWorld.Flare(FxWorld.Anchor.at(center), 2.4F, GOLD, 10, FxTextures.GLOW, 0.0F, 2));
            }
            case POISON_BURST -> {
                Fx.burst(level, ModParticles.BUBBLE.get(), center, 24, VENOM, 0.18F, 24, 0.4);
                FxWorld.add(new FxWorld.Shockwave(entity.position(), 0.3F, 2.4F, 10, VENOM, 0.7F, 0.8F));
            }
            case MARKED -> FxWorld.add(new FxWorld.Flare(FxWorld.Anchor.center(entity.getId(), Vec3.ZERO), size * 2.0F + 0.8F, MARK_RED, 8,
                    FxTextures.RING, 0.0F, 2));
            case REVIVED -> {
                FxWorld.add(new FxWorld.Pillar(FxWorld.Anchor.entity(entity.getId(), Vec3.ZERO), 0.9F, 8.0F, LIFE, 26));
                Fx.fountain(level, ModParticles.PETAL.get(), entity.position(), 30, LIFE, 0.22F, 40, 0.3, 0.15);
                FxWorld.add(new FxWorld.Shockwave(entity.position(), 0.4F, 3.0F, 16, LIFE, 1.0F, 0.9F));
            }
            case COUNTERED -> {
                Vec3 front = center.add(entity.getLookAngle().scale(0.8));
                FxWorld.add(new FxWorld.Flare(FxWorld.Anchor.at(front), 2.2F, KI, 8, FxTextures.STAR, 0.4F, 1));
                Fx.burst(level, ModParticles.GLOW.get(), front, 16, KI, 0.3F, 12, 0.5);
                FxWorld.shake(center, 3, 0.4F);
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
            tickPhase6(level, random, state, entity, feet, width, height);
        }
    }

    /** Phase 6 その2 の状態の、続く粒。 */
    private static void tickPhase6(ClientLevel level, RandomSource random, State state, Entity entity, Vec3 feet, float width, float height) {
        long[] poison = state.kinds.get(StatusFxS2C.Kind.POISON);
        if (poison != null && random.nextInt(Math.max(1, 6 - (int) poison[0])) == 0) {
            Fx.scatter(level, ModParticles.BUBBLE.get(), feet.add(0.0, height * 0.2, 0.0), width * 0.6, 1, VENOM, 0.12F, 20, height * 0.7,
                    new Vec3(0.0, 0.02, 0.0));
        }
        if (state.kinds.containsKey(StatusFxS2C.Kind.ROOTED) && random.nextInt(4) == 0) {
            Fx.ring(level, ModParticles.RUNE.get(), feet.add(0.0, 0.05, 0.0), width * 0.6 + 0.3, 1, 0xD8D0FF, 0.18F, 16, 0.0, 0.01);
        }
        if (state.kinds.containsKey(StatusFxS2C.Kind.BLINDED) && random.nextInt(3) == 0) {
            Fx.scatter(level, ModParticles.SMOKE.get(), feet.add(0.0, height * 0.85, 0.0), width * 0.5, 1, 0x8C8A9A, 0.35F, 24, 0.3,
                    new Vec3(0.0, 0.01, 0.0));
        }
        if (state.kinds.containsKey(StatusFxS2C.Kind.KEEN) && random.nextInt(8) == 0) {
            Fx.burst(level, ModParticles.STAR.get(), feet.add(0.0, height + 0.1, 0.0), 1, 0xFFE27A, 0.15F, 14, 0.08);
        }
        if ((state.kinds.containsKey(StatusFxS2C.Kind.SWIFT) || state.kinds.containsKey(StatusFxS2C.Kind.EVADE))
                && random.nextInt(3) == 0 && entity.getDeltaMovement().horizontalDistanceSqr() > 0.002) {
            boolean shadow = state.kinds.containsKey(StatusFxS2C.Kind.EVADE);
            Fx.add(level, shadow ? FxParticleOptions.of(ModParticles.SMOKE.get(), DUSK, 0.35F, 18) : Fx.glow(WIND, 0.2F, 12),
                    feet.add((random.nextDouble() - 0.5) * width, 0.2 + random.nextDouble() * height * 0.6, (random.nextDouble() - 0.5) * width),
                    Vec3.ZERO);
        }
        if (state.kinds.containsKey(StatusFxS2C.Kind.VENOM_COAT) && random.nextInt(5) == 0) {
            Fx.add(level, FxParticleOptions.of(ModParticles.BUBBLE.get(), VENOM, 0.1F, 18),
                    feet.add((random.nextDouble() - 0.5) * width * 1.4, height * 0.45, (random.nextDouble() - 0.5) * width * 1.4),
                    new Vec3(0.0, -0.03, 0.0));
        }
        if (state.kinds.containsKey(StatusFxS2C.Kind.BOW_DANCE) && random.nextInt(4) == 0) {
            Fx.ring(level, ModParticles.FEATHER.get(), feet.add(0.0, 0.3 + random.nextDouble() * height, 0.0), width * 0.8 + 0.4, 1, 0xE8FFF0,
                    0.12F, 18, 0.02, 0.03);
        }
        if (state.kinds.containsKey(StatusFxS2C.Kind.RENEWAL) && random.nextInt(5) == 0) {
            Fx.scatter(level, ModParticles.PETAL.get(), feet.add(0.0, 0.1, 0.0), width * 0.7, 1, LIFE, 0.16F, 30, 0.2, new Vec3(0.0, 0.05, 0.0));
        }
        if (state.kinds.containsKey(StatusFxS2C.Kind.HIDDEN) && random.nextInt(3) == 0) {
            Fx.scatter(level, ModParticles.SMOKE.get(), feet, width * 0.6, 1, DUSK, 0.3F, 26, height, new Vec3(0.0, 0.03, 0.0));
        }
        if (state.kinds.containsKey(StatusFxS2C.Kind.SHADOW_CLONE) && random.nextInt(6) == 0) {
            Fx.scatter(level, ModParticles.SMOKE.get(), feet, width * 0.8, 1, SHADOW, 0.25F, 20, height, Vec3.ZERO);
        }
        if (state.kinds.containsKey(StatusFxS2C.Kind.BLESSING) && random.nextInt(6) == 0) {
            Fx.scatter(level, ModParticles.GLOW.get(), feet, width * 0.6, 1, GOLD, 0.2F, 20, 0.3, new Vec3(0.0, 0.05, 0.0));
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
                row += MARK_ROW;
            }
            renderPhase6(draw, state, entity, feet, head, width, height, time, row, firstPersonSelf);
        }
    }

    /** Phase 6 その2 の状態の見た目。{@code row} は頭の上の印の、次の段の高さ。 */
    private static void renderPhase6(FxDraw draw, State state, Entity entity, Vec3 feet, Vec3 head, float width, float height, float time,
                                     float row, boolean firstPersonSelf) {
        long[] value;
        // 頭の上の印（標・戒め・身代わりの鈴）
        if ((value = state.kinds.get(StatusFxS2C.Kind.MARK)) != null && !firstPersonSelf) {
            Vec3 at = head.add(0.0, row + 0.05, 0.0);
            draw.billboard(draw.translucent(FxTextures.RING), at, 0.5F, 0.0F, MARK_SHADE_RED, 0.6F);
            draw.billboard(draw.glow(FxTextures.RING), at, 0.46F, 0.0F, MARK_RED, 0.8F);
            draw.billboard(draw.glow(FxTextures.STAR), at, 0.3F, time * 0.08F, MARK_RED, 0.9F);
            row += MARK_ROW;
        }
        if ((value = state.kinds.get(StatusFxS2C.Kind.ADMONISHED)) != null && !firstPersonSelf) {
            Vec3 at = head.add(0.0, row + 0.05, 0.0);
            draw.billboard(draw.translucent(FxTextures.CRACK), at, 0.4F, 0.0F, MARK_SHADE_GOLD, 0.6F);
            draw.billboard(draw.glow(FxTextures.CRACK), at, 0.36F, 0.0F, BELL, 0.7F);
            row += MARK_ROW;
        }
        if ((value = state.kinds.get(StatusFxS2C.Kind.GUARDIAN_BELL)) != null && !firstPersonSelf) {
            Vec3 at = head.add(0.0, row + 0.08 + Mth.sin(time * 0.15F) * 0.05, 0.0);
            draw.billboard(draw.translucent(FxTextures.STAR), at, 0.44F, time * 0.03F, MARK_SHADE_GOLD, 0.6F);
            draw.billboard(draw.glow(FxTextures.STAR), at, 0.4F, time * 0.03F, BELL, 0.85F);
            row += MARK_ROW;
        }
        // 足もと・体
        if ((value = state.kinds.get(StatusFxS2C.Kind.POISON)) != null) {
            draw.ground(draw.glow(FxTextures.RING), feet.add(0.0, 0.04, 0.0), width * 0.8F + 0.2F, 0.0F, VENOM, 0.1F + 0.05F * value[0]);
        }
        if ((value = state.kinds.get(StatusFxS2C.Kind.ROOTED)) != null) {
            draw.ground(draw.glow(FxTextures.SIGIL_ARCANE), feet.add(0.0, 0.05, 0.0), width * 0.9F + 0.4F, time * 0.01F, 0xD8D0FF, 0.45F);
        }
        if ((value = state.kinds.get(StatusFxS2C.Kind.BLESSING)) != null) {
            draw.ground(draw.glow(FxTextures.RING), feet.add(0.0, 0.05, 0.0), width * 0.9F + 0.4F, 0.0F, GOLD,
                    0.35F + 0.1F * Mth.sin(time * 0.15F));
        }
        if ((value = state.kinds.get(StatusFxS2C.Kind.PROTECTED)) != null && !firstPersonSelf) {
            draw.sphere(draw.glow(FxTextures.DOME), feet.add(0.0, height * 0.5, 0.0), Math.max(width, height) * 0.6F + 0.15F, 8, 20, false,
                    time * 0.002F, 0xE8F4FF, 0.18F);
        }
        if ((value = state.kinds.get(StatusFxS2C.Kind.EMPOWERED)) != null && !firstPersonSelf) {
            draw.billboard(draw.glow(FxTextures.GLOW), feet.add(0.0, height * 0.6, 0.0), 0.9F + 0.1F * Mth.sin(time * 0.3F), 0.0F, FIST, 0.45F);
        }
        if ((value = state.kinds.get(StatusFxS2C.Kind.AIMING)) != null) {
            draw.ground(draw.glow(FxTextures.SIGIL_STAR), feet.add(0.0, 0.05, 0.0), 0.9F, time * 0.02F, WIND, 0.5F);
        }
        if ((value = state.kinds.get(StatusFxS2C.Kind.LIFE_THREAD)) != null) {
            draw.ground(draw.glow(FxTextures.RING), feet.add(0.0, height * 0.55, 0.0), width * 0.8F + 0.35F, time * 0.05F, GOLD, 0.5F);
        }
        if ((value = state.kinds.get(StatusFxS2C.Kind.STAR_RING)) != null && !firstPersonSelf) {
            VertexConsumer vc = draw.glow(FxTextures.STAR);
            for (int i = 0; i < 5; i++) {
                float angle = time * 0.15F + i * Mth.TWO_PI / 5.0F;
                float r = width * 0.5F + 0.8F;
                Vec3 at = feet.add(Mth.cos(angle) * r, height * 0.55 + Mth.sin(time * 0.2F + i) * 0.1, Mth.sin(angle) * r);
                draw.billboard(vc, at, 0.32F, time * 0.1F, GOLD, 0.9F);
            }
        }
        if ((value = state.kinds.get(StatusFxS2C.Kind.SHADOW_CLONE)) != null && !firstPersonSelf) {
            float yaw = entity.getYRot() * Mth.DEG_TO_RAD;
            Vec3 behind = feet.add(Mth.sin(yaw) * 0.6, height * 0.5, -Mth.cos(yaw) * 0.6);
            draw.billboard(draw.glow(FxTextures.GLOW), behind, height * 0.9F, 0.0F, SHADOW, 0.25F + 0.1F * Mth.sin(time * 0.4F));
        }
        if ((value = state.kinds.get(StatusFxS2C.Kind.ENDLESS_FLOW)) != null) {
            draw.ground(draw.glow(FxTextures.SIGIL_OATH), feet.add(0.0, 0.05, 0.0), 1.4F, time * 0.04F, FIST, 0.5F);
            draw.ground(draw.glow(FxTextures.SIGIL_FROST), feet.add(0.0, 0.06, 0.0), 1.0F, -time * 0.06F, KI, 0.5F);
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

package jp.amemaru.lostconnection.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * ワールドの中に描く演出の形（陣・波紋・斬撃の弧・光の帯・稲妻・障壁・氷の棘など。クライアント専用）。
 *
 * <p>形は {@link Visual} として足し、毎 tick 進め、毎フレーム描く。パーティクルより大きな、はっきりした形を作るのに使う。
 * 画面の揺れもここで扱う（設定で止められる）。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class FxWorld {
    private static final List<Visual> VISUALS = new ArrayList<>();
    private static final List<Visual> PENDING = new ArrayList<>();
    private static final int MAX_VISUALS = 384;
    private static boolean ticking;

    private static int shakeTicks;
    private static float shakePower;

    private FxWorld() {
    }

    /** 形を足す。 */
    public static <T extends Visual> T add(T visual) {
        if (ticking) {
            PENDING.add(visual);
        } else {
            if (VISUALS.size() >= MAX_VISUALS) {
                VISUALS.remove(0);
            }
            VISUALS.add(visual);
        }
        return visual;
    }

    /** {@code delay} tick 後に動かす（返した形に印をつければ、詠唱をやめたときに取り消せる）。 */
    public static Visual later(int delay, Runnable task) {
        return add(new Visual(Math.max(1, delay)) {
            @Override
            protected void onEnd(ClientLevel level) {
                task.run();
            }

            @Override
            public void render(FxDraw draw, ClientLevel level) {
            }
        });
    }

    /** 条件に合う形を消す。 */
    public static void removeIf(java.util.function.Predicate<Visual> filter) {
        VISUALS.removeIf(filter);
        PENDING.removeIf(filter);
    }

    /** ログアウトしたときに消す。 */
    public static void reset() {
        VISUALS.clear();
        PENDING.clear();
        shakeTicks = 0;
    }

    // ---------------------------------------------------------------- 画面の揺れ

    /** 画面を少し揺らす（近くにいるときだけ。設定で止められる）。 */
    public static void shake(Vec3 at, int ticks, float power) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.position().distanceToSqr(at) > 24 * 24) {
            return;
        }
        shakePower = shakeTicks > 0 ? Math.max(shakePower, power) : power;
        shakeTicks = Math.max(shakeTicks, ticks);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shakeTicks <= 0 || !ClientConfig.screenShake()) {
            return;
        }
        float power = shakePower * Math.min(1.0F, shakeTicks / 10.0F);
        double time = (System.nanoTime() % 1_000_000_000_000L) / 1.0E9;
        event.setPitch(event.getPitch() + (float) Math.sin(time * 71.0) * power * 0.5F);
        event.setYaw(event.getYaw() + (float) Math.sin(time * 53.0 + 1.3) * power * 0.5F);
        event.setRoll(event.getRoll() + (float) Math.sin(time * 37.0 + 2.1) * power * 0.35F);
    }

    // ---------------------------------------------------------------- 進める・描く

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            reset();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        ticking = true;
        try {
            Iterator<Visual> it = VISUALS.iterator();
            while (it.hasNext()) {
                Visual visual = it.next();
                boolean alive;
                try {
                    alive = visual.tick(level);
                } catch (RuntimeException e) {
                    AmemaruMod.LOGGER.warn("[amemaru] A skill visual failed and was removed", e);
                    alive = false;
                }
                if (!alive) {
                    it.remove();
                }
            }
        } finally {
            ticking = false;
        }
        for (Visual visual : PENDING) {
            add(visual);
        }
        PENDING.clear();
        if (shakeTicks > 0) {
            shakeTicks--;
        }
        StatusVisuals.tick(level);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        FxDraw draw = new FxDraw(pose, buffers, event.getCamera(), event.getPartialTick());
        try {
            for (Visual visual : VISUALS) {
                visual.render(draw, level);
            }
            StatusVisuals.render(draw, level);
        } catch (RuntimeException e) {
            AmemaruMod.LOGGER.warn("[amemaru] Drawing skill visuals failed", e);
        }
        buffers.endBatch();
    }

    // ---------------------------------------------------------------- 形の決まり

    /** 1つの形。{@code life} tick で消える。 */
    public abstract static class Visual {
        protected final int life;
        protected int age;
        /** 持ち主のエンティティ番号と、印（詠唱をやめたときにまとめて消すため）。 */
        public int owner = -1;
        public int tag;

        protected Visual(int life) {
            this.life = Math.max(1, life);
        }

        /** 持ち主と印をつける。 */
        public Visual tag(int owner, int tag) {
            this.owner = owner;
            this.tag = tag;
            return this;
        }

        /** 1 tick 進める。続けるなら true。 */
        boolean tick(ClientLevel level) {
            age++;
            if (age >= life || !alive(level)) {
                onEnd(level);
                return false;
            }
            onTick(level);
            return true;
        }

        protected void onTick(ClientLevel level) {
        }

        protected void onEnd(ClientLevel level) {
        }

        protected boolean alive(ClientLevel level) {
            return true;
        }

        public abstract void render(FxDraw draw, ClientLevel level);

        /** 0〜1 の進み具合。 */
        protected float progress(float partial) {
            return Mth.clamp((age + partial) / life, 0.0F, 1.0F);
        }

        /** はじめの in tick で現れ、最後の out tick で消える不透明度（0〜1）。 */
        protected float fade(float partial, int in, int out) {
            float t = age + partial;
            float a = in <= 0 ? 1.0F : Mth.clamp(t / in, 0.0F, 1.0F);
            float b = out <= 0 ? 1.0F : Mth.clamp((life - t) / out, 0.0F, 1.0F);
            return Math.min(a, b);
        }
    }

    /** 場所：決まった点か、エンティティ（と、そこからのずれ）。 */
    public static final class Anchor {
        private final Vec3 fixed;
        private final int entityId;
        private final Vec3 offset;
        private final boolean eye;
        private Vec3 last;

        private Anchor(@Nullable Vec3 fixed, int entityId, Vec3 offset, boolean eye) {
            this.fixed = fixed;
            this.entityId = entityId;
            this.offset = offset;
            this.eye = eye;
            this.last = fixed != null ? fixed : Vec3.ZERO;
        }

        public static Anchor at(Vec3 point) {
            return new Anchor(point, -1, Vec3.ZERO, false);
        }

        /** エンティティの足もと（から offset ずれた所）。 */
        public static Anchor entity(int entityId, Vec3 offset) {
            return new Anchor(null, entityId, offset, false);
        }

        /** エンティティの体の真ん中（から offset ずれた所）。 */
        public static Anchor center(int entityId, Vec3 offset) {
            return new Anchor(null, entityId, offset, true);
        }

        /** 今の場所（エンティティがいなくなったら、最後に見た場所）。 */
        public Vec3 get(ClientLevel level, float partial) {
            if (fixed != null) {
                return fixed;
            }
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                Vec3 base = entity.getPosition(partial);
                if (eye) {
                    base = base.add(0.0, entity.getBbHeight() * 0.55, 0.0);
                }
                last = base.add(offset);
            }
            return last;
        }

        public boolean present(ClientLevel level) {
            return fixed != null || level.getEntity(entityId) != null;
        }

        @Nullable
        public Entity entity(ClientLevel level) {
            return fixed != null ? null : level.getEntity(entityId);
        }
    }

    // ---------------------------------------------------------------- 形

    /** 地面の魔法陣：回りながら広がって現れ、消えるときは薄れる。内側に逆回りの小さな陣も重ねる。 */
    public static final class Sigil extends Visual {
        private final Anchor anchor;
        private final float radius;
        private final ResourceLocation texture;
        private final int color;
        private final float spin;
        private final int grow;
        private final float alpha;
        private boolean inner = true;

        public Sigil(Anchor anchor, float radius, ResourceLocation texture, int color, int life, float spin, int grow, float alpha) {
            super(life);
            this.anchor = anchor;
            this.radius = radius;
            this.texture = texture;
            this.color = color;
            this.spin = spin;
            this.grow = grow;
            this.alpha = alpha;
        }

        public Sigil noInner() {
            inner = false;
            return this;
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            float partial = draw.partialTick();
            float t = age + partial;
            float scale = grow <= 0 ? 1.0F : 1.0F - (float) Math.pow(1.0F - Mth.clamp(t / grow, 0.0F, 1.0F), 3.0);
            float a = alpha * fade(partial, 2, Math.min(10, life / 3 + 1));
            Vec3 center = anchor.get(level, partial).add(0.0, 0.03, 0.0);
            VertexConsumer vc = draw.glow(texture);
            draw.ground(vc, center, radius * scale, t * spin, color, a);
            if (inner) {
                draw.ground(vc, center.add(0.0, 0.01, 0.0), radius * scale * 0.62F, -t * spin * 1.6F, color, a * 0.7F);
            }
            // 真ん中のやわらかな光
            draw.ground(draw.glow(FxTextures.GLOW), center.add(0.0, 0.02, 0.0), radius * scale * 0.9F, 0.0F, color, a * 0.35F);
        }

        @Override
        protected boolean alive(ClientLevel level) {
            return anchor.present(level);
        }
    }

    /** 広がる波紋：地面の輪と、立ち上がる光の壁。 */
    public static final class Shockwave extends Visual {
        private final Vec3 center;
        private final float from;
        private final float to;
        private final int color;
        private final float height;
        private final float alpha;

        public Shockwave(Vec3 center, float from, float to, int life, int color, float height, float alpha) {
            super(life);
            this.center = center;
            this.from = from;
            this.to = to;
            this.color = color;
            this.height = height;
            this.alpha = alpha;
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            float p = progress(draw.partialTick());
            float eased = 1.0F - (1.0F - p) * (1.0F - p);
            float radius = Mth.lerp(eased, from, to);
            float a = alpha * (1.0F - p);
            Vec3 c = center.add(0.0, 0.05, 0.0);
            draw.ground(draw.glow(FxTextures.RING), c, radius * 1.08F, 0.0F, color, a);
            if (height > 0) {
                int segments = Mth.clamp((int) (radius * 6), 16, 64);
                draw.ringWall(draw.glow(FxTextures.WALL), c, radius, height * (1.0F - p * 0.5F), segments, 0.0F, 1.0F, color, a, 0.0F);
            }
        }
    }

    /** 動かない光の輪の壁（円陣のふち・城壁のふち）。 */
    public static final class Rim extends Visual {
        private final Vec3 center;
        private final float radius;
        private final float height;
        private final int color;
        private final float alpha;

        public Rim(Vec3 center, float radius, float height, int color, int life, float alpha) {
            super(life);
            this.center = center;
            this.radius = radius;
            this.height = height;
            this.color = color;
            this.alpha = alpha;
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            float partial = draw.partialTick();
            float t = age + partial;
            float a = alpha * fade(partial, 6, Math.min(10, life / 3 + 1)) * (0.8F + 0.2F * Mth.sin(t * 0.3F));
            int segments = Mth.clamp((int) (radius * 8), 16, 72);
            draw.ringWall(draw.glow(FxTextures.WALL), center.add(0.0, 0.03, 0.0), radius, height, segments, t * 0.01F, radius * 2.0F,
                    color, a, 0.0F);
            draw.ground(draw.glow(FxTextures.RING), center.add(0.0, 0.04, 0.0), radius * 1.06F, 0.0F, color, a * 0.8F);
        }
    }

    /** 斬撃の弧：刃先が start から end へ走り、その後に光の帯が残って消える。 */
    public static final class Slash extends Visual {
        private final Anchor anchor;
        private final float yaw;
        private final float tilt;
        private final float radius;
        private final float width;
        private final float start;
        private final float end;
        private final int color;
        private final int sweep;

        public Slash(Anchor anchor, float yaw, float tilt, float radius, float width, float start, float end, int life, int sweep,
                     int color) {
            super(life);
            this.anchor = anchor;
            this.yaw = yaw;
            this.tilt = tilt;
            this.radius = radius;
            this.width = width;
            this.start = start;
            this.end = end;
            this.sweep = Math.max(1, sweep);
            this.color = color;
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            float partial = draw.partialTick();
            float t = age + partial;
            float swept = Mth.clamp(t / sweep, 0.0F, 1.0F);
            float head = start + (end - start) * (1.0F - (1.0F - swept) * (1.0F - swept));
            float fadeOut = Mth.clamp((life - t) / Math.max(1.0F, life - sweep), 0.0F, 1.0F);
            float tail = start + (head - start) * Mth.clamp((t - sweep) / Math.max(1.0F, life - sweep), 0.0F, 1.0F) * 0.6F;
            Vec3 center = anchor.get(level, partial);
            int segments = Mth.clamp((int) (Math.abs(head - tail) / 6.0F), 4, 48);
            VertexConsumer vc = draw.glow(FxTextures.SLASH);
            draw.arc(vc, center, radius, width, yaw, tilt, tail, head, segments, color, 0.15F * fadeOut, fadeOut);
            draw.arc(vc, center, radius * 1.02F, width * 0.35F, yaw, tilt, tail, head, segments, 0xFFFFFF, 0.0F, 0.85F * fadeOut);
        }
    }

    /** 2点を結ぶ光の帯（糸・鎖・軌跡）。{@code repeat} は1ブロックあたりのテクスチャのくり返し。 */
    public static final class Beam extends Visual {
        private final Anchor from;
        private final Anchor to;
        private final float width;
        private final ResourceLocation texture;
        private final int color;
        private final float repeat;
        private final float scroll;
        private float reach = 1.0F;
        private int extend;

        public Beam(Anchor from, Anchor to, float width, ResourceLocation texture, int color, int life, float repeat, float scroll) {
            super(life);
            this.from = from;
            this.to = to;
            this.width = width;
            this.texture = texture;
            this.color = color;
            this.repeat = repeat;
            this.scroll = scroll;
        }

        /** はじめの ticks の間、根もとから先へ伸びていく。 */
        public Beam extend(int ticks) {
            this.extend = ticks;
            return this;
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            float partial = draw.partialTick();
            Vec3 a = from.get(level, partial);
            Vec3 b = to.get(level, partial);
            if (extend > 0) {
                reach = Mth.clamp((age + partial) / extend, 0.0F, 1.0F);
                b = a.lerp(b, reach);
            }
            float length = (float) a.distanceTo(b);
            float u0 = -(age + partial) * scroll;
            float alpha = fade(partial, 2, Math.min(6, life / 2 + 1));
            draw.beam(draw.glow(texture), a, b, width, u0, u0 + length * repeat, color, alpha);
            if (texture != FxTextures.CHAIN) {
                draw.beam(draw.glow(FxTextures.BEAM), a, b, width * 0.35F, 0.0F, 1.0F, 0xFFFFFF, alpha * 0.8F);
            }
        }

        @Override
        protected boolean alive(ClientLevel level) {
            return from.present(level) && to.present(level);
        }
    }

    /** 稲妻：2 tick ごとに形が変わる、ギザギザの光。 */
    public static final class Lightning extends Visual {
        private final Anchor from;
        private final Anchor to;
        private final float width;
        private final int color;
        private final float jitter;
        private final long seed;

        public Lightning(Anchor from, Anchor to, float width, int color, int life, float jitter) {
            super(life);
            this.from = from;
            this.to = to;
            this.width = width;
            this.color = color;
            this.jitter = jitter;
            this.seed = System.nanoTime();
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            float partial = draw.partialTick();
            Vec3 a = from.get(level, partial);
            Vec3 b = to.get(level, partial);
            int segments = Mth.clamp((int) (a.distanceTo(b) * 1.6), 4, 32);
            Vec3[] points = FxDraw.lightningPoints(a, b, segments, jitter, seed + age / 2);
            float flicker = (age / 2) % 2 == 0 ? 1.0F : 0.7F;
            float alpha = fade(partial, 0, Math.min(5, life)) * flicker;
            draw.polyline(draw.glow(FxTextures.LIGHTNING), points, width * 2.6F, color, alpha * 0.8F);
            draw.polyline(draw.glow(FxTextures.LIGHTNING), points, width, 0xFFFFFF, alpha);
            // 枝分かれ
            if (segments > 5) {
                Vec3 mid = points[segments / 2];
                Vec3 branch = mid.add(b.subtract(a).normalize().scale(1.2)).add(0.0, -0.6, 0.0);
                Vec3[] twig = FxDraw.lightningPoints(mid, branch, 3, jitter * 0.5F, seed * 31 + age / 2);
                draw.polyline(draw.glow(FxTextures.LIGHTNING), twig, width * 0.7F, color, alpha * 0.7F);
            }
        }
    }

    /** カメラの方を向いた光（光の玉・星のきらめき）。大きくなってから消える。 */
    public static final class Flare extends Visual {
        private final Anchor anchor;
        private final float size;
        private final int color;
        private final ResourceLocation texture;
        private final float spin;
        private final int grow;

        public Flare(Anchor anchor, float size, int color, int life, ResourceLocation texture, float spin, int grow) {
            super(life);
            this.anchor = anchor;
            this.size = size;
            this.color = color;
            this.texture = texture;
            this.spin = spin;
            this.grow = grow;
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            float partial = draw.partialTick();
            float t = age + partial;
            float scale = grow <= 0 ? 1.0F : Mth.clamp(t / grow, 0.0F, 1.0F);
            float a = fade(partial, 0, Math.max(1, life - grow));
            draw.billboard(draw.glow(texture), anchor.get(level, partial), size * (0.3F + 0.7F * scale), t * spin, color, a);
        }

        @Override
        protected boolean alive(ClientLevel level) {
            return anchor.present(level);
        }
    }

    /** 空から落ちてくる星：光の芯と、長い尾。着いたら {@code onArrive} を動かす。 */
    public static final class Meteor extends Visual {
        private final Vec3 from;
        private final Vec3 to;
        private final float size;
        private final int color;
        @Nullable
        private final Runnable onArrive;

        public Meteor(Vec3 from, Vec3 to, int travel, float size, int color, @Nullable Runnable onArrive) {
            super(travel);
            this.from = from;
            this.to = to;
            this.size = size;
            this.color = color;
            this.onArrive = onArrive;
        }

        @Override
        protected void onTick(ClientLevel level) {
            Vec3 at = from.lerp(to, progress(0.0F));
            Fx.burst(level, jp.amemaru.lostconnection.core.registry.ModParticles.GLOW.get(), at, 2, color, size * 0.5F, 10, 0.03);
            if (level.random.nextInt(2) == 0) {
                Fx.burst(level, jp.amemaru.lostconnection.core.registry.ModParticles.SPARK.get(), at, 1, 0xFFF2C0, 0.12F, 8, 0.15);
            }
        }

        @Override
        protected void onEnd(ClientLevel level) {
            if (onArrive != null) {
                onArrive.run();
            }
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            float p = progress(draw.partialTick());
            float eased = p * p;
            Vec3 at = from.lerp(to, eased);
            Vec3 tail = from.lerp(to, Math.max(0.0F, eased - 0.35F));
            draw.beam(draw.glow(FxTextures.BEAM), tail, at, size * 0.9F, 0.0F, 1.0F, color, 0.0F, 0.9F);
            VertexConsumer glow = draw.glow(FxTextures.GLOW);
            draw.billboard(glow, at, size * 2.4F, 0.0F, color, 0.9F);
            draw.billboard(glow, at, size * 1.1F, 0.0F, 0xFFFFFF, 1.0F);
            draw.billboard(draw.glow(FxTextures.STAR), at, size * 3.2F, (age + draw.partialTick()) * 0.2F, color, 0.6F);
        }
    }

    /** 球・ドーム（障壁・城壁）：六角の網目がゆっくり回る。 */
    public static final class Dome extends Visual {
        private final Anchor anchor;
        private final float radius;
        private final int color;
        private final boolean hemisphere;
        private final float alpha;
        private final int grow;

        public Dome(Anchor anchor, float radius, int color, int life, boolean hemisphere, float alpha, int grow) {
            super(life);
            this.anchor = anchor;
            this.radius = radius;
            this.color = color;
            this.hemisphere = hemisphere;
            this.alpha = alpha;
            this.grow = grow;
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            float partial = draw.partialTick();
            float t = age + partial;
            float scale = grow <= 0 ? 1.0F : 1.0F - (float) Math.pow(1.0F - Mth.clamp(t / grow, 0.0F, 1.0F), 3.0);
            float a = alpha * fade(partial, 2, Math.min(10, life / 3 + 1)) * (0.85F + 0.15F * Mth.sin(t * 0.25F));
            Vec3 center = anchor.get(level, partial);
            draw.sphere(draw.glow(FxTextures.DOME), center, radius * scale, hemisphere ? 6 : 10, 24, hemisphere, t * 0.004F, color, a);
        }

        @Override
        protected boolean alive(ClientLevel level) {
            return anchor.present(level);
        }
    }

    /** 光の柱：地面から空へ立ち上がる。 */
    public static final class Pillar extends Visual {
        private final Anchor anchor;
        private final float radius;
        private final float height;
        private final int color;

        public Pillar(Anchor anchor, float radius, float height, int color, int life) {
            super(life);
            this.anchor = anchor;
            this.radius = radius;
            this.height = height;
            this.color = color;
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            float partial = draw.partialTick();
            float p = progress(partial);
            float rise = Mth.clamp((age + partial) / 4.0F, 0.0F, 1.0F);
            float a = fade(partial, 0, Math.max(1, life / 2));
            Vec3 base = anchor.get(level, partial);
            float r = radius * (1.0F - 0.5F * p);
            draw.ringWall(draw.glow(FxTextures.WALL), base, r, height * rise, 20, (age + partial) * 0.05F, 2.0F, color, a, 0.0F);
            draw.beam(draw.glow(FxTextures.BEAM), base, base.add(0.0, height * rise, 0.0), r * 1.4F, 0.0F, 1.0F, 0xFFFFFF, a * 0.9F, 0.0F);
        }
    }

    /** 地面から突き出す氷の棘（輪に並ぶ）。突き出してから沈む。 */
    public static final class Spikes extends Visual {
        private final Vec3 center;
        private final float radius;
        private final int count;
        private final float height;
        private final int color;
        private final float[] offsets;

        public Spikes(Vec3 center, float radius, int count, float height, int color, int life, long seed) {
            super(life);
            this.center = center;
            this.radius = radius;
            this.count = count;
            this.height = height;
            this.color = color;
            this.offsets = new float[count * 3];
            java.util.Random random = new java.util.Random(seed);
            for (int i = 0; i < offsets.length; i++) {
                offsets[i] = random.nextFloat();
            }
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            float partial = draw.partialTick();
            float t = age + partial;
            float up = Mth.clamp(t / 3.0F, 0.0F, 1.0F);
            float down = Mth.clamp((life - t) / 6.0F, 0.0F, 1.0F);
            float h = height * Math.min(up, down);
            if (h <= 0.01F) {
                return;
            }
            VertexConsumer vc = draw.translucent(FxTextures.ICE);
            VertexConsumer glow = draw.glow(FxTextures.ICE);
            for (int i = 0; i < count; i++) {
                float angle = (i + offsets[i * 3] * 0.6F) / count * Mth.TWO_PI;
                float r = radius * (0.75F + 0.35F * offsets[i * 3 + 1]);
                float size = 0.6F + 0.6F * offsets[i * 3 + 2];
                Vec3 base = center.add(Mth.cos(angle) * r, -0.2, Mth.sin(angle) * r);
                Vec3 lean = new Vec3(Mth.cos(angle), 0.0, Mth.sin(angle)).scale(0.35 * h);
                Vec3 tip = base.add(lean.x, h * size, lean.z);
                draw.spike(vc, base, tip, 0.22F * size + 0.08F, angle, color, 0.75F);
                draw.spike(glow, base, tip, 0.24F * size + 0.08F, angle, color, 0.35F);
            }
        }
    }

    /**
     * 光の大剣（天断ち）。切っ先を下に向けて立ち、2枚の板を十字に重ねるので、どの向きからも剣の形に見える。
     * はじめの appear tick で光りながら現れる。{@link #drop} を付けると、空から落ちて地面に突き立つ。
     */
    public static final class GiantSword extends Visual {
        private static final int SHADE_GOLD = 0xA8701C;
        private final Anchor tip;
        private final float length;
        private final float width;
        private final float yaw;
        private final int color;
        private final int appear;
        private float dropHeight;
        private int dropTicks;
        private float sink;

        /**
         * @param tip    切っ先の場所
         * @param length 柄頭から切っ先までの長さ
         * @param width  鍔の幅（刃はその半分ほど）
         * @param yaw    剣の向き（度。刃の平らな面が、この向きを向く）
         */
        public GiantSword(Anchor tip, float length, float width, float yaw, int color, int life, int appear) {
            super(life);
            this.tip = tip;
            this.length = length;
            this.width = width;
            this.yaw = yaw;
            this.color = color;
            this.appear = appear;
        }

        /** height ブロック上から ticks で落ちてきて、切っ先が sink ブロック地面に刺さる。 */
        public GiantSword drop(float height, int ticks, float sink) {
            this.dropHeight = height;
            this.dropTicks = Math.max(1, ticks);
            this.sink = sink;
            return this;
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            float partial = draw.partialTick();
            float t = age + partial;
            float shown = appear <= 0 ? 1.0F : Mth.clamp(t / appear, 0.0F, 1.0F);
            float a = shown * fade(partial, 0, Math.min(10, life / 3 + 1));
            if (a <= 0.003F) {
                return;
            }
            Vec3 point = tip.get(level, partial);
            if (dropTicks > 0) {
                float p = Mth.clamp(t / dropTicks, 0.0F, 1.0F);
                point = point.add(0.0, dropHeight * (1.0F - p * p) - sink * p * p, 0.0);
            }
            // 現れるときは、上から下へ光が満ちる（下の方ほど遅れて見える）
            Vec3 top = point.add(0.0, length, 0.0);
            float rad = yaw * Mth.DEG_TO_RAD;
            Vec3 sideA = new Vec3(Mth.cos(rad), 0.0, Mth.sin(rad)).scale(width / 2.0);
            Vec3 sideB = new Vec3(-Mth.sin(rad), 0.0, Mth.cos(rad)).scale(width / 2.0);
            float v1 = 0.15F + 0.85F * shown;
            Vec3 bottom = top.lerp(point, v1);
            // 明るい空の前でも金色に見えるよう、先に濃い金の影を敷いてから光らせる
            VertexConsumer shade = draw.translucent(FxTextures.GREATSWORD);
            for (Vec3 side : new Vec3[]{sideA, sideB}) {
                draw.quad(shade, top.subtract(side), bottom.subtract(side), bottom.add(side), top.add(side), 0.0F, 0.0F, 1.0F, v1, SHADE_GOLD,
                        a * 0.5F);
            }
            VertexConsumer vc = draw.glow(FxTextures.GREATSWORD);
            float shimmer = 0.85F + 0.15F * Mth.sin(t * 0.6F);
            for (Vec3 side : new Vec3[]{sideA, sideB}) {
                draw.quad(vc, top.subtract(side), bottom.subtract(side), bottom.add(side), top.add(side), 0.0F, 0.0F, 1.0F, v1, color,
                        a * shimmer);
                // 芯の白い光
                draw.quad(vc, top.subtract(side.scale(0.55)), bottom.subtract(side.scale(0.55)), bottom.add(side.scale(0.55)),
                        top.add(side.scale(0.55)), 0.0F, 0.0F, 1.0F, v1, 0xFFFFFF, a * 0.45F);
            }
            // 鍔のあたりの光
            draw.billboard(draw.glow(FxTextures.GLOW), top.subtract(0.0, length * 0.25, 0.0), width * 1.4F, 0.0F, color, a * 0.5F);
        }

        @Override
        protected boolean alive(ClientLevel level) {
            return tip.present(level);
        }
    }

    /**
     * 地面を走る切り口（天断ち）：from から to へ、extend tick で先へ伸びる光の帯。真ん中に裂け目の線。
     */
    public static final class GroundCut extends Visual {
        private final Vec3 from;
        private final Vec3 to;
        private final float width;
        private final int color;
        private final int extend;
        private final long seed;

        public GroundCut(Vec3 from, Vec3 to, float width, int color, int life, int extend) {
            super(life);
            this.from = from;
            this.to = to;
            this.width = width;
            this.color = color;
            this.extend = Math.max(1, extend);
            this.seed = Double.doubleToLongBits(from.x * 31.0 + to.z);
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            float partial = draw.partialTick();
            float t = age + partial;
            float reach = Mth.clamp(t / extend, 0.0F, 1.0F);
            float a = fade(partial, 0, Math.max(1, life - extend));
            Vec3 start = from.add(0.0, 0.06, 0.0);
            Vec3 end = start.lerp(to.add(0.0, 0.06, 0.0), 1.0F - (1.0F - reach) * (1.0F - reach));
            Vec3 dir = end.subtract(start);
            if (dir.lengthSqr() < 1.0E-6) {
                return;
            }
            Vec3 side = new Vec3(-dir.z, 0.0, dir.x).normalize().scale(width / 2.0);
            // 帯（地面に寝かせる。中ほどが明るい）
            draw.quad(draw.glow(FxTextures.BEAM), start.subtract(side), start.add(side), end.add(side), end.subtract(side), 0.0F, 0.0F, 1.0F,
                    1.0F, color, a * 0.7F);
            // 真ん中の裂け目（ギザギザの白い線）
            int segments = Mth.clamp((int) (dir.length() * 1.5), 4, 32);
            Vec3[] points = FxDraw.lightningPoints(start.add(0.0, 0.02, 0.0), end.add(0.0, 0.02, 0.0), segments, 0.25F, seed);
            for (int i = 0; i < points.length; i++) {
                points[i] = new Vec3(points[i].x, start.y + 0.02, points[i].z);
            }
            draw.polyline(draw.glow(FxTextures.LIGHTNING), points, 0.35F, 0xFFFFFF, a);
            draw.polyline(draw.glow(FxTextures.LIGHTNING), points, 0.9F, color, a * 0.6F);
        }
    }

    /**
     * 詠唱の間の、狙う向きの印（天断ち）：エンティティの足もとから、今向いている方へ length ブロックの帯。
     */
    public static final class LinePreview extends Visual {
        private final int entityId;
        private final float length;
        private final float width;
        private final int color;

        public LinePreview(int entityId, float length, float width, int color, int life) {
            super(life);
            this.entityId = entityId;
            this.length = length;
            this.width = width;
            this.color = color;
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            Entity entity = level.getEntity(entityId);
            if (entity == null) {
                return;
            }
            float partial = draw.partialTick();
            float t = age + partial;
            float yaw = Mth.lerp(partial, entity.yRotO, entity.getYRot()) * Mth.DEG_TO_RAD;
            Vec3 dir = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
            Vec3 start = entity.getPosition(partial).add(0.0, 0.07, 0.0).add(dir.scale(0.6));
            Vec3 end = start.add(dir.scale(Math.max(0.5F, length - 0.6F)));
            Vec3 side = new Vec3(-dir.z, 0.0, dir.x).scale(width / 2.0);
            float a = fade(partial, 6, 3) * (0.3F + 0.15F * Mth.sin(t * 0.5F));
            draw.quad(draw.glow(FxTextures.BEAM), start.subtract(side), start.add(side), end.add(side), end.subtract(side), 0.0F, 0.0F, 1.0F,
                    1.0F, color, a);
            // ふちの細い線と、先へ流れる光
            draw.beam(draw.glow(FxTextures.BEAM), start.subtract(side), end.subtract(side), 0.08F, 0.0F, 1.0F, color, a * 1.6F);
            draw.beam(draw.glow(FxTextures.BEAM), start.add(side), end.add(side), 0.08F, 0.0F, 1.0F, color, a * 1.6F);
            float run = (t * 0.06F) % 1.0F;
            draw.billboard(draw.glow(FxTextures.GLOW), start.lerp(end, run).add(0.0, 0.15, 0.0), width * 0.8F, 0.0F, color, a * 1.4F);
        }

        @Override
        protected boolean alive(ClientLevel level) {
            return level.getEntity(entityId) != null;
        }
    }

    /** 飛んでいく氷の槍（氷柱）。 */
    public static final class Spear extends Visual {
        private final Vec3 from;
        private final Vec3 to;
        private final int color;

        public Spear(Vec3 from, Vec3 to, int travel, int color) {
            super(travel + 4);
            this.from = from;
            this.to = to;
            this.color = color;
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            float partial = draw.partialTick();
            float p = Mth.clamp((age + partial) / Math.max(1, life - 4), 0.0F, 1.0F);
            float a = fade(partial, 0, 4);
            Vec3 dir = to.subtract(from).normalize();
            Vec3 tip = from.lerp(to, p);
            Vec3 back = tip.subtract(dir.scale(1.6));
            draw.spike(draw.translucent(FxTextures.ICE), back, tip, 0.22F, (age + partial) * 0.4F, color, 0.9F * a);
            draw.spike(draw.glow(FxTextures.ICE), back, tip, 0.26F, (age + partial) * 0.4F, color, 0.4F * a);
            draw.beam(draw.glow(FxTextures.BEAM), from.lerp(tip, 0.4), back, 0.45F, 0.0F, 1.0F, color, 0.0F, 0.7F * a);
        }
    }

    /** エンティティの前に立つ、曲がった光の壁（城壁の構え・受け流し）。 */
    public static final class FrontWall extends Visual {
        private final int entityId;
        private final float radius;
        private final float height;
        private final float span;
        private final int color;
        private final float alpha;
        private final ResourceLocation texture;

        public FrontWall(int entityId, float radius, float height, float span, int color, int life, float alpha,
                         ResourceLocation texture) {
            super(life);
            this.entityId = entityId;
            this.radius = radius;
            this.height = height;
            this.span = span;
            this.color = color;
            this.alpha = alpha;
            this.texture = texture;
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            Entity entity = level.getEntity(entityId);
            if (entity == null) {
                return;
            }
            float partial = draw.partialTick();
            drawWall(draw, entity, partial, radius, height, span, color, alpha * fade(partial, 2, Math.min(6, life / 2 + 1)), texture);
        }

        /** エンティティの前に、曲がった光の壁を描く（{@code span} は左右あわせた角度）。 */
        public static void drawWall(FxDraw draw, Entity entity, float partial, float radius, float height, float span, int color,
                                    float a, ResourceLocation texture) {
            Vec3 base = entity.getPosition(partial).add(0.0, 0.1, 0.0);
            float yaw = Mth.lerp(partial, entity.yRotO, entity.getYRot()) * Mth.DEG_TO_RAD;
            VertexConsumer vc = draw.glow(texture);
            int segments = 8;
            for (int i = 0; i < segments; i++) {
                float a0 = (-span / 2.0F + span * i / segments) * Mth.DEG_TO_RAD;
                float a1 = (-span / 2.0F + span * (i + 1) / segments) * Mth.DEG_TO_RAD;
                Vec3 p0 = base.add(-Mth.sin(yaw + a0) * radius, 0.0, Mth.cos(yaw + a0) * radius);
                Vec3 p1 = base.add(-Mth.sin(yaw + a1) * radius, 0.0, Mth.cos(yaw + a1) * radius);
                float u0 = i / (float) segments;
                float u1 = (i + 1) / (float) segments;
                draw.vertex(vc, p0.x, p0.y, p0.z, u0, 1.0F, color, a);
                draw.vertex(vc, p1.x, p1.y, p1.z, u1, 1.0F, color, a);
                draw.vertex(vc, p1.x, p1.y + height, p1.z, u1, 0.0F, color, a * 0.6F);
                draw.vertex(vc, p0.x, p0.y + height, p0.z, u0, 0.0F, color, a * 0.6F);
            }
        }

        @Override
        protected boolean alive(ClientLevel level) {
            return level.getEntity(entityId) != null;
        }
    }

    /** 毎 tick パーティクルを出し続ける（軌跡・オーラ）。 */
    public static final class Emitter extends Visual {
        private final Anchor anchor;
        private final Consumer<Vec3> spawn;

        public Emitter(Anchor anchor, int life, Consumer<Vec3> spawn) {
            super(life);
            this.anchor = anchor;
            this.spawn = spawn;
        }

        @Override
        protected void onTick(ClientLevel level) {
            spawn.accept(anchor.get(level, 0.0F));
        }

        @Override
        protected boolean alive(ClientLevel level) {
            return anchor.present(level);
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
        }
    }

    /** 何本もの光の筋が、まわりから中心へ集まる（力を溜めるとき）。 */
    public static final class Gather extends Visual {
        private final Anchor anchor;
        private final float radius;
        private final int color;
        private final int streaks;
        private final long seed;

        public Gather(Anchor anchor, float radius, int color, int life, int streaks) {
            super(life);
            this.anchor = anchor;
            this.radius = radius;
            this.color = color;
            this.streaks = streaks;
            this.seed = System.nanoTime();
        }

        @Override
        public void render(FxDraw draw, ClientLevel level) {
            float partial = draw.partialTick();
            Vec3 center = anchor.get(level, partial);
            java.util.Random random = new java.util.Random(seed);
            VertexConsumer vc = draw.glow(FxTextures.BEAM);
            float t = age + partial;
            for (int i = 0; i < streaks; i++) {
                Vec3 dir = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.3, random.nextDouble() - 0.5).normalize();
                float phase = (t * 0.09F + random.nextFloat()) % 1.0F;
                float outer = radius * (1.0F - phase);
                float inner = Math.max(0.0F, outer - radius * 0.35F);
                float a = Mth.sin(Mth.PI * phase) * fade(partial, 3, 3);
                draw.beam(vc, center.add(dir.scale(outer)), center.add(dir.scale(inner)), 0.08F, 0.0F, 1.0F, color, 0.0F, a);
            }
        }

        @Override
        protected boolean alive(ClientLevel level) {
            return anchor.present(level);
        }
    }
}

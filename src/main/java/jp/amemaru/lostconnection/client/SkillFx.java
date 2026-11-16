package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ClientConfig;
import jp.amemaru.lostconnection.network.s2c.SkillFxS2C;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * スキルの演出（クライアント専用）：パーティクル・陣・降ってくる星・強化のオーラ・詠唱の魔法陣・画面の揺れ・モーション。
 *
 * <p>サーバーからは「誰が・どのスキルを・どこで」（{@link SkillFxS2C}）だけが届き、ここで形を作る。パーティクルはバニラのもの
 * （色つきの粉・光の粒・雪の結晶など）を使う仮の演出【仮素材】。専用のパーティクルは、後のPhaseで同じ名前の演出に差し替える。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class SkillFx {
    private static final List<Effect> ACTIVE = new ArrayList<>();
    private static final int MAX_ACTIVE = 96;
    /** 自分が押した瞬間に始めたモーション（スキルの番号 → 時刻）。サーバーからの演出で二重に始めないため。 */
    private static final Map<Integer, Long> PREDICTED = new HashMap<>();

    private static int shakeTicks;
    private static float shakePower;

    // 色
    private static final Vector3f GOLD = new Vector3f(1.0F, 0.82F, 0.35F);
    private static final Vector3f CYAN = new Vector3f(0.55F, 0.85F, 1.0F);
    private static final Vector3f WHITE = new Vector3f(0.95F, 0.97F, 1.0F);
    private static final Vector3f BLUE = new Vector3f(0.42F, 0.66F, 1.0F);
    private static final Vector3f ICE = new Vector3f(0.75F, 0.95F, 1.0F);
    private static final Vector3f VIOLET = new Vector3f(0.71F, 0.49F, 1.0F);
    private static final Vector3f EMBER = new Vector3f(1.0F, 0.55F, 0.25F);
    private static final Vector3f STEEL = new Vector3f(0.8F, 0.85F, 0.95F);

    private SkillFx() {
    }

    /** 自分がキーを押した瞬間に、モーションを先に始めたことを覚える。 */
    public static void markPredicted(int skillIndex) {
        PREDICTED.put(skillIndex, System.currentTimeMillis());
    }

    private static boolean wasPredicted(int skillIndex) {
        Long at = PREDICTED.get(skillIndex);
        return at != null && System.currentTimeMillis() - at < 1500;
    }

    // ---------------------------------------------------------------- 受け取り

    public static void handle(SkillFxS2C message) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        SyncDefinitionsS2C.SkillInfo skill = ClientDefinitions.get().skill(message.skillIndex());
        Entity caster = level.getEntity(message.casterId());
        boolean self = caster != null && caster == minecraft.player;
        String fx = skill == null ? "" : skill.fx();
        switch (message.phase()) {
            case CAST_START -> {
                if (caster instanceof AbstractClientPlayer player && skill != null && !(self && wasPredicted(skill.index()))) {
                    PlayerAnimations.play(player, skill.castAnimation());
                }
                add(new CastCircle(message.casterId(), message.ticks(), fx.startsWith("wizard") ? VIOLET : GOLD));
                if (fx.equals("wizard_meteor_shower") || fx.equals("wizard_starfall")) {
                    add(new GroundMarker(message.to(), Math.max(1.0F, message.radius()), message.ticks(), fx.equals("wizard_starfall") ? GOLD : EMBER));
                }
            }
            case CAST_CANCEL -> {
                if (caster instanceof AbstractClientPlayer player) {
                    PlayerAnimations.stop(player);
                }
                ACTIVE.removeIf(effect -> effect instanceof CastCircle circle && circle.entityId == message.casterId()
                        || effect instanceof GroundMarker);
            }
            case RELEASE -> {
                ACTIVE.removeIf(effect -> effect instanceof CastCircle circle && circle.entityId == message.casterId());
                boolean predictedRelease = self && skill != null && wasPredicted(skill.index()) && !hasCast(skill);
                if (caster instanceof AbstractClientPlayer player && skill != null && !predictedRelease) {
                    PlayerAnimations.play(player, skill.animation());
                }
                release(level, fx, message, caster);
            }
            case IMPACT -> impact(level, fx, message);
        }
    }

    // ---------------------------------------------------------------- 放ったとき

    private static void release(ClientLevel level, String fx, SkillFxS2C message, Entity caster) {
        RandomSource random = level.random;
        Vec3 from = message.from();
        Vec3 to = message.to();
        float radius = message.radius();
        switch (fx) {
            case "knight_call" -> {
                ring(level, from.add(0, 0.2, 0), radius, 48, new DustParticleOptions(GOLD, 1.6F), 0.02);
                ring(level, from.add(0, 1.0, 0), radius * 0.6F, 28, ParticleTypes.ENCHANTED_HIT, 0.0);
                for (int target : message.targets()) {
                    Entity entity = level.getEntity(target);
                    if (entity != null) {
                        burst(level, entity.position().add(0, entity.getBbHeight() + 0.3, 0), 6, new DustParticleOptions(EMBER, 1.2F), 0.05);
                    }
                }
            }
            case "knight_shield_bond", "wizard_mana_thread" -> {
                boolean mana = fx.startsWith("wizard");
                line(level, from, to, 28, new DustColorTransitionOptions(mana ? VIOLET : GOLD, mana ? CYAN : WHITE, 1.1F));
                burst(level, to, 14, mana ? ParticleTypes.ENCHANT : ParticleTypes.END_ROD, 0.15);
                for (int target : message.targets()) {
                    add(new Aura(target, 30, mana ? CYAN : BLUE, 1.0F));
                }
            }
            case "knight_rampart" -> add(new Aura(message.casterId(), Math.max(20, message.ticks()), BLUE, 0.9F));
            case "knight_unyielding_vow" -> {
                add(new Aura(message.casterId(), Math.max(20, message.ticks()), GOLD, 1.2F));
                burst(level, from.add(0, 1.0, 0), 30, ParticleTypes.TOTEM_OF_UNDYING, 0.4);
            }
            case "knight_unfallen_castle" -> {
                ring(level, from.add(0, 0.1, 0), radius, 72, new DustParticleOptions(WHITE, 2.0F), 0.0);
                ring(level, from.add(0, 0.1, 0), radius * 0.66F, 48, new DustParticleOptions(GOLD, 1.6F), 0.0);
                for (int target : message.targets()) {
                    add(new Aura(target, Math.max(20, message.ticks()), WHITE, 1.1F));
                }
                shake(from, 6, 0.6F);
            }
            case "knight_charge_slash" -> {
                line(level, from.add(0, 0.9, 0), to.add(0, 0.9, 0), 24, new DustParticleOptions(STEEL, 1.4F));
                line(level, from.add(0, 0.2, 0), to.add(0, 0.2, 0), 12, ParticleTypes.CLOUD);
                for (int target : message.targets()) {
                    Entity entity = level.getEntity(target);
                    if (entity != null) {
                        level.addParticle(ParticleTypes.SWEEP_ATTACK, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(), 0, 0, 0);
                        burst(level, entity.position().add(0, entity.getBbHeight() * 0.6, 0), 10, ParticleTypes.CRIT, 0.4);
                    }
                }
            }
            case "knight_whirlwind" -> {
                for (int i = 0; i < 10; i++) {
                    double angle = i / 10.0 * Math.PI * 2;
                    level.addParticle(ParticleTypes.SWEEP_ATTACK, from.x + Math.cos(angle) * radius * 0.7, from.y + 1.0,
                            from.z + Math.sin(angle) * radius * 0.7, 0, 0, 0);
                }
                ring(level, from.add(0, 0.9, 0), radius, 40, new DustParticleOptions(STEEL, 1.3F), 0.0);
            }
            case "wizard_lamp_bolt" -> burst(level, from, 6, ParticleTypes.FLAME, 0.05);
            case "wizard_frost_circle" -> {
                ring(level, to.add(0, 0.1, 0), radius, 48, new DustParticleOptions(ICE, 1.5F), 0.0);
                burst(level, to.add(0, 0.5, 0), 30, ParticleTypes.SNOWFLAKE, 0.25);
                if (message.ticks() > 0) {
                    add(new FrostZone(to, radius, message.ticks()));
                }
            }
            case "wizard_meteor_shower" -> add(new MeteorFall(to, radius, Math.max(4, message.ticks()), 7));
            case "wizard_starfall" -> add(new StarFall(to, Math.max(6, message.ticks())));
            case "wizard_chain_lightning" -> {
                Vec3 last = from;
                for (int target : message.targets()) {
                    Entity entity = level.getEntity(target);
                    if (entity == null) {
                        continue;
                    }
                    Vec3 next = entity.position().add(0, entity.getBbHeight() * 0.6, 0);
                    lightning(level, random, last, next);
                    burst(level, next, 8, ParticleTypes.ELECTRIC_SPARK, 0.3);
                    last = next;
                }
            }
            case "wizard_blink" -> {
                burst(level, from.add(0, 1.0, 0), 24, ParticleTypes.PORTAL, 0.6);
                burst(level, to.add(0, 1.0, 0), 24, ParticleTypes.REVERSE_PORTAL, 0.3);
                line(level, from.add(0, 1.0, 0), to.add(0, 1.0, 0), 16, new DustParticleOptions(VIOLET, 1.0F));
            }
            default -> burst(level, to, 12, ParticleTypes.END_ROD, 0.1);
        }
    }

    // ---------------------------------------------------------------- 着弾

    private static void impact(ClientLevel level, String fx, SkillFxS2C message) {
        Vec3 at = message.to();
        float radius = message.radius();
        switch (fx) {
            case "wizard_meteor_shower" -> {
                ACTIVE.removeIf(effect -> effect instanceof GroundMarker);
                for (int i = 0; i < 6; i++) {
                    double angle = level.random.nextDouble() * Math.PI * 2;
                    double r = level.random.nextDouble() * radius;
                    level.addParticle(ParticleTypes.EXPLOSION, at.x + Math.cos(angle) * r, at.y + 0.5, at.z + Math.sin(angle) * r, 0, 0, 0);
                }
                ring(level, at.add(0, 0.2, 0), radius, 48, ParticleTypes.FLAME, 0.05);
                burst(level, at.add(0, 0.5, 0), 40, ParticleTypes.LAVA, 0.3);
                shake(at, 8, 0.8F);
            }
            case "wizard_starfall" -> {
                ACTIVE.removeIf(effect -> effect instanceof GroundMarker);
                level.addParticle(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 0, 0, 0);
                ring(level, at.add(0, 0.2, 0), radius, 96, new DustParticleOptions(GOLD, 2.5F), 0.1);
                ring(level, at.add(0, 0.6, 0), radius * 0.6F, 64, ParticleTypes.END_ROD, 0.2);
                burst(level, at.add(0, 1.0, 0), 60, ParticleTypes.FIREWORK, 0.6);
                shake(at, 14, 1.4F);
            }
            case "wizard_lamp_bolt" -> {
                burst(level, at, 12, ParticleTypes.FLAME, 0.12);
                burst(level, at, 6, new DustParticleOptions(EMBER, 1.2F), 0.1);
            }
            default -> burst(level, at, 16, ParticleTypes.END_ROD, 0.2);
        }
    }

    // ---------------------------------------------------------------- 毎 tick

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) {
            if (level == null) {
                ACTIVE.clear();
            }
            return;
        }
        Iterator<Effect> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Effect effect = it.next();
            if (!effect.tick(level)) {
                it.remove();
            }
        }
        if (shakeTicks > 0) {
            shakeTicks--;
        }
    }

    /** 画面を少し揺らす（設定で止められる）。 */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shakeTicks <= 0 || !ClientConfig.screenShake()) {
            return;
        }
        float power = shakePower * shakeTicks / 14.0F;
        RandomSource random = RandomSource.create();
        event.setPitch(event.getPitch() + (random.nextFloat() - 0.5F) * power);
        event.setYaw(event.getYaw() + (random.nextFloat() - 0.5F) * power);
        event.setRoll(event.getRoll() + (random.nextFloat() - 0.5F) * power * 0.6F);
    }

    private static void shake(Vec3 at, int ticks, float power) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.position().distanceToSqr(at) > 24 * 24) {
            return;
        }
        shakePower = shakeTicks > 0 ? Math.max(shakePower, power) : power;
        shakeTicks = Math.max(shakeTicks, ticks);
    }

    /** 詠唱のあるスキルか（1段目で見る）。 */
    static boolean hasCast(SyncDefinitionsS2C.SkillInfo skill) {
        return skill.cast().length > 0 && skill.cast()[0] > 0;
    }

    private static void add(Effect effect) {
        if (ACTIVE.size() >= MAX_ACTIVE) {
            ACTIVE.remove(0);
        }
        ACTIVE.add(effect);
    }

    /** ログアウトしたときに消す。 */
    public static void reset() {
        ACTIVE.clear();
        PREDICTED.clear();
        shakeTicks = 0;
    }

    // ---------------------------------------------------------------- 形の道具

    private static void ring(ClientLevel level, Vec3 center, float radius, int count, ParticleOptions particle, double rise) {
        for (int i = 0; i < count; i++) {
            double angle = i / (double) count * Math.PI * 2;
            level.addParticle(particle, center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius, 0, rise, 0);
        }
    }

    private static void burst(ClientLevel level, Vec3 at, int count, ParticleOptions particle, double speed) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            level.addParticle(particle, at.x, at.y, at.z, (random.nextDouble() - 0.5) * speed * 2, (random.nextDouble() - 0.3) * speed * 2,
                    (random.nextDouble() - 0.5) * speed * 2);
        }
    }

    private static void line(ClientLevel level, Vec3 from, Vec3 to, int count, ParticleOptions particle) {
        for (int i = 0; i <= count; i++) {
            Vec3 at = from.lerp(to, i / (double) count);
            level.addParticle(particle, at.x, at.y, at.z, 0, 0, 0);
        }
    }

    private static void lightning(ClientLevel level, RandomSource random, Vec3 from, Vec3 to) {
        int segments = Math.max(4, (int) (from.distanceTo(to) * 2));
        Vec3 last = from;
        for (int i = 1; i <= segments; i++) {
            Vec3 point = from.lerp(to, i / (double) segments);
            if (i < segments) {
                point = point.add((random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6);
            }
            line(level, last, point, 3, ParticleTypes.ELECTRIC_SPARK);
            last = point;
        }
    }

    // ---------------------------------------------------------------- 続く演出

    private interface Effect {
        /** 1 tick 進める。終わったら false。 */
        boolean tick(ClientLevel level);
    }

    /** 詠唱中の足もとの魔法陣。 */
    private static final class CastCircle implements Effect {
        final int entityId;
        int ticks;
        final Vector3f color;

        CastCircle(int entityId, int ticks, Vector3f color) {
            this.entityId = entityId;
            this.ticks = ticks + 2;
            this.color = color;
        }

        @Override
        public boolean tick(ClientLevel level) {
            Entity entity = level.getEntity(entityId);
            if (entity == null || --ticks < 0) {
                return false;
            }
            float phase = (level.getGameTime() % 40) / 40.0F * Mth.TWO_PI;
            for (int i = 0; i < 3; i++) {
                double angle = phase + i * Mth.TWO_PI / 3;
                level.addParticle(new DustParticleOptions(color, 1.0F), entity.getX() + Math.cos(angle) * 0.9, entity.getY() + 0.05,
                        entity.getZ() + Math.sin(angle) * 0.9, 0, 0, 0);
            }
            if (level.random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.ENCHANT, entity.getX(), entity.getY() + 1.9, entity.getZ(),
                        (level.random.nextDouble() - 0.5) * 2, -1.2, (level.random.nextDouble() - 0.5) * 2);
            }
            return true;
        }
    }

    /** 着弾する場所の目印（流星群・星落としの詠唱中）。 */
    private static final class GroundMarker implements Effect {
        final Vec3 center;
        final float radius;
        int ticks;
        final Vector3f color;

        GroundMarker(Vec3 center, float radius, int ticks, Vector3f color) {
            this.center = center;
            this.radius = radius;
            this.ticks = ticks + 30;
            this.color = color;
        }

        @Override
        public boolean tick(ClientLevel level) {
            if (--ticks < 0) {
                return false;
            }
            if (level.getGameTime() % 3 == 0) {
                ring(level, center.add(0, 0.1, 0), radius, 24, new DustParticleOptions(color, 1.0F), 0.0);
            }
            return true;
        }
    }

    /** 強化のオーラ（城壁の構え・不屈の誓い・不落の城・支援を受けた味方）。 */
    private static final class Aura implements Effect {
        final int entityId;
        int ticks;
        final Vector3f color;
        final float size;

        Aura(int entityId, int ticks, Vector3f color, float size) {
            this.entityId = entityId;
            this.ticks = ticks;
            this.color = color;
            this.size = size;
        }

        @Override
        public boolean tick(ClientLevel level) {
            Entity entity = level.getEntity(entityId);
            if (entity == null || --ticks < 0) {
                return false;
            }
            float phase = (level.getGameTime() % 30) / 30.0F * Mth.TWO_PI;
            for (int i = 0; i < 2; i++) {
                double angle = phase + i * Math.PI;
                double y = entity.getY() + 0.2 + ((level.getGameTime() + i * 15) % 30) / 30.0 * entity.getBbHeight();
                level.addParticle(new DustParticleOptions(color, size), entity.getX() + Math.cos(angle) * 0.7, y,
                        entity.getZ() + Math.sin(angle) * 0.7, 0, 0.01, 0);
            }
            return true;
        }
    }

    /** 氷結陣：地面に残る氷の陣。 */
    private static final class FrostZone implements Effect {
        final Vec3 center;
        final float radius;
        int ticks;

        FrostZone(Vec3 center, float radius, int ticks) {
            this.center = center;
            this.radius = radius;
            this.ticks = ticks;
        }

        @Override
        public boolean tick(ClientLevel level) {
            if (--ticks < 0) {
                return false;
            }
            if (level.getGameTime() % 4 == 0) {
                ring(level, center.add(0, 0.08, 0), radius, 32, new DustParticleOptions(ICE, 1.2F), 0.0);
            }
            RandomSource random = level.random;
            for (int i = 0; i < 3; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(random.nextDouble()) * radius;
                level.addParticle(ParticleTypes.SNOWFLAKE, center.x + Math.cos(angle) * r, center.y + 0.1 + random.nextDouble() * 0.8,
                        center.z + Math.sin(angle) * r, 0, 0.02, 0);
            }
            return true;
        }
    }

    /** 流星群：空から星が降ってくる。 */
    private static final class MeteorFall implements Effect {
        final Vec3 center;
        final float radius;
        int ticks;
        final int total;
        final int meteors;

        MeteorFall(Vec3 center, float radius, int ticks, int meteors) {
            this.center = center;
            this.radius = radius;
            this.ticks = ticks;
            this.total = ticks;
            this.meteors = meteors;
        }

        @Override
        public boolean tick(ClientLevel level) {
            if (--ticks < 0) {
                return false;
            }
            float progress = 1.0F - ticks / (float) total;
            RandomSource random = level.random;
            for (int i = 0; i < meteors; i++) {
                double angle = i / (double) meteors * Math.PI * 2 + i * 0.7;
                double r = radius * (0.25 + 0.7 * ((i * 37) % 10) / 10.0);
                double x = center.x + Math.cos(angle) * r + (1 - progress) * 4;
                double z = center.z + Math.sin(angle) * r;
                double y = center.y + (1 - progress) * 16 + 0.5;
                level.addParticle(ParticleTypes.FLAME, x, y, z, 0, -0.2, 0);
                level.addParticle(new DustParticleOptions(EMBER, 1.6F), x + (random.nextDouble() - 0.5) * 0.3, y + 0.4, z, 0, 0, 0);
            }
            return true;
        }
    }

    /** 星落とし：大きな星が1つ降ってくる。 */
    private static final class StarFall implements Effect {
        final Vec3 center;
        int ticks;
        final int total;

        StarFall(Vec3 center, int ticks) {
            this.center = center;
            this.ticks = ticks;
            this.total = ticks;
        }

        @Override
        public boolean tick(ClientLevel level) {
            if (--ticks < 0) {
                return false;
            }
            float progress = 1.0F - ticks / (float) total;
            double y = center.y + (1 - progress) * 24 + 1;
            double x = center.x + (1 - progress) * 6;
            for (int i = 0; i < 6; i++) {
                level.addParticle(new DustParticleOptions(GOLD, 3.0F), x + (level.random.nextDouble() - 0.5), y + (level.random.nextDouble() - 0.5),
                        center.z + (level.random.nextDouble() - 0.5), 0, 0, 0);
            }
            level.addParticle(ParticleTypes.END_ROD, x, y + 0.8, center.z, 0.05, 0.1, 0);
            level.addParticle(ParticleTypes.FIREWORK, x, y, center.z, 0, 0.05, 0);
            return true;
        }
    }
}

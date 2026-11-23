package jp.amemaru.lostconnection.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.client.screen.DialogueScreen;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.SceneStateC2S;
import jp.amemaru.lostconnection.network.s2c.SceneS2C;
import jp.amemaru.lostconnection.network.s2c.StoryTextS2C;
import jp.amemaru.lostconnection.npc.NpcEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ストーリーイベントの演出を、この画面だけで流す（Phase 0-A 14章「ストーリーイベントの演出」：暗転・字幕・章題・カメラ・効果音）。
 *
 * <ul>
 *   <li>サーバーから手順をまとめて受け取り（{@code SceneS2C}）、この画面の時間で上から順に流す。通信の遅れで止まったり、
 *       飛ばされたりしない</li>
 *   <li>演出の間は、視点を固定し（カメラ）、動けないようにし、HUDを隠す</li>
 *   <li>役者（オープニングで駆け寄るリリアなど）は、この画面の中にだけいるNPC。ほかの人の画面には出ない</li>
 *   <li>流し終えたら、サーバーに知らせる（{@code SceneStateC2S(DONE)}）。続けて会話が開くときは、会話が終わるまで役者とカメラを残す</li>
 * </ul>
 */
public final class ScenePlayer {
    private static final int ACTOR_ID_BASE = -7_000_000;

    private static boolean playing;
    private static int scene;
    private static final List<JsonObject> steps = new ArrayList<>();
    private static int index;
    private static int stepTicks;
    private static int stepLength;
    /** 暗転の色（0xRRGGBB）と濃さ（0〜1）。 */
    private static int fadeColor = 0x000000;
    private static float fadeFrom;
    private static float fadeTo;
    private static int fadeTicks;
    private static int fadeLength;
    private static boolean hideHud;
    @Nullable
    private static Vec3 cameraTarget;
    @Nullable
    private static Integer cameraActor;
    private static float cameraYaw;
    private static float cameraPitch;
    private static boolean cameraInit;
    private static final Map<String, Integer> actors = new HashMap<>();
    private static final Map<Integer, ActorMove> moves = new HashMap<>();
    private static int nextActorId = ACTOR_ID_BASE;
    /** 流し終えた後、会話が終わるまで役者とカメラを残す時間。 */
    private static int linger;

    private record ActorMove(Vec3 from, Vec3 to, int length, int[] age) {
    }

    private ScenePlayer() {
    }

    // ---------------------------------------------------------------- 始める・終える

    public static void start(SceneS2C message) {
        cleanup();
        steps.clear();
        try {
            JsonArray array = JsonParser.parseString(message.stepsJson()).getAsJsonArray();
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    steps.add(element.getAsJsonObject());
                }
            }
        } catch (RuntimeException e) {
            AmemaruMod.LOGGER.error("[amemaru] Could not read the scene {}", message.event(), e);
        }
        scene = message.scene();
        playing = true;
        index = 0;
        stepTicks = 0;
        stepLength = 0;
        linger = 0;
        cameraInit = false;
        hideHud = true;
        beginStep();
    }

    public static boolean isPlaying() {
        return playing;
    }

    /** 演出中か、演出の後に会話の終わりを待っているか（この間は動けない）。 */
    public static boolean isActive() {
        return playing || linger > 0;
    }

    public static boolean hideHud() {
        return isActive() && hideHud;
    }

    /** 暗転の濃さ（0〜1）。 */
    public static float fade(float partialTick) {
        if (fadeLength <= 0) {
            return fadeTo;
        }
        float t = Mth.clamp((fadeTicks + partialTick) / fadeLength, 0.0F, 1.0F);
        return Mth.lerp(t, fadeFrom, fadeTo);
    }

    public static int fadeColor() {
        return fadeColor;
    }

    private static void finish() {
        playing = false;
        ModNetwork.sendToServer(new SceneStateC2S(SceneStateC2S.State.DONE, scene));
        // 会話が続くかもしれないので、しばらく役者とカメラを残す
        linger = actors.isEmpty() ? 1 : 60;
        fadeTo = 0.0F;
        fadeLength = 0;
    }

    /** 役者を消し、カメラと暗転を戻す。 */
    public static void cleanup() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != null) {
            for (int id : actors.values()) {
                level.removeEntity(id, Entity.RemovalReason.DISCARDED);
            }
        }
        // 演出の最後の向きを、本人の向きにする（視点が飛ばないように）
        if (cameraInit && minecraft.player != null) {
            minecraft.player.setYRot(cameraYaw);
            minecraft.player.setXRot(Mth.clamp(cameraPitch, -89.0F, 89.0F));
            minecraft.player.yRotO = cameraYaw;
            minecraft.player.xRotO = minecraft.player.getXRot();
        }
        actors.clear();
        moves.clear();
        cameraTarget = null;
        cameraActor = null;
        cameraInit = false;
        playing = false;
        linger = 0;
        hideHud = false;
        fadeFrom = 0.0F;
        fadeTo = 0.0F;
        fadeLength = 0;
    }

    // ---------------------------------------------------------------- 毎 tick

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            if (isActive()) {
                cleanup();
            }
            return;
        }
        if (fadeLength > 0 && fadeTicks < fadeLength) {
            fadeTicks++;
        }
        tickActors(minecraft.level);
        if (playing) {
            stepTicks++;
            while (playing && stepTicks >= stepLength) {
                index++;
                stepTicks = 0;
                if (index >= steps.size()) {
                    finish();
                    break;
                }
                beginStep();
            }
        } else if (linger > 0) {
            if (minecraft.screen instanceof DialogueScreen) {
                linger = Math.max(linger, 2);
            } else if (--linger <= 0) {
                cleanup();
            }
        }
    }

    private static void tickActors(ClientLevel level) {
        for (Map.Entry<Integer, ActorMove> entry : new ArrayList<>(moves.entrySet())) {
            Entity entity = level.getEntity(entry.getKey());
            ActorMove move = entry.getValue();
            if (entity == null) {
                moves.remove(entry.getKey());
                continue;
            }
            move.age()[0]++;
            float t = Mth.clamp(move.age()[0] / (float) Math.max(1, move.length()), 0.0F, 1.0F);
            float eased = t * t * (3.0F - 2.0F * t);
            Vec3 pos = move.from().lerp(move.to(), eased);
            Vec3 old = entity.position();
            entity.setOldPosAndRot();
            entity.setPos(pos.x, pos.y, pos.z);
            Vec3 delta = pos.subtract(old);
            if (delta.horizontalDistanceSqr() > 1.0E-4) {
                float yaw = (float) (Mth.atan2(delta.z, delta.x) * (180.0 / Math.PI)) - 90.0F;
                entity.setYRot(yaw);
                entity.setYHeadRot(yaw);
                if (entity instanceof NpcEntity npc) {
                    npc.yBodyRot = yaw;
                    npc.walkAnimation.update((float) Math.min(1.0, delta.horizontalDistance() * 4.0), 0.4F);
                }
            }
            if (t >= 1.0F) {
                moves.remove(entry.getKey());
            }
        }
    }

    // ---------------------------------------------------------------- 手順

    private static void beginStep() {
        if (index >= steps.size()) {
            stepLength = 0;
            return;
        }
        JsonObject step = steps.get(index);
        String type = string(step, "type", "");
        int ticks = integer(step, "ticks", 0);
        boolean wait = bool(step, "wait", true);
        stepLength = 0;
        Minecraft minecraft = Minecraft.getInstance();
        switch (type) {
            case "amemaru:fade" -> {
                String from = string(step, "from", "");
                String to = string(step, "to", "");
                fadeColor = "white".equals(from) || "white".equals(to) ? 0xFFFFFF : 0x000000;
                fadeFrom = from.isEmpty() || "none".equals(from) ? fade(0.0F) : 1.0F;
                fadeTo = to.isEmpty() || "none".equals(to) ? 0.0F : 1.0F;
                fadeTicks = 0;
                fadeLength = Math.max(1, ticks);
                stepLength = wait ? ticks : 0;
            }
            case "amemaru:wait" -> stepLength = ticks;
            case "amemaru:subtitle" -> {
                String style = string(step, "style", "normal");
                StoryTextS2C.Kind kind = "ancient".equals(style) ? StoryTextS2C.Kind.ANCIENT : StoryTextS2C.Kind.SUBTITLE;
                String speaker = string(step, "speaker", "");
                ClientStory.showText(kind, "core".equals(style) && speaker.isEmpty() ? "？？？" : speaker, string(step, "text", ""),
                        Math.max(20, ticks));
                stepLength = wait ? ticks : 0;
            }
            case "amemaru:title" -> {
                ClientStory.showText(StoryTextS2C.Kind.TITLE, string(step, "subtitle", ""), string(step, "title", ""), Math.max(20, ticks));
                stepLength = wait ? ticks : 0;
            }
            case "amemaru:sound" -> {
                ResourceLocation id = ResourceLocation.tryParse(string(step, "sound", ""));
                SoundEvent sound = id == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(id);
                if (sound != null) {
                    Vec3 at = vec(step, "pos");
                    float volume = (float) number(step, "volume", 1.0);
                    float pitch = (float) number(step, "pitch", 1.0);
                    if (at != null && minecraft.level != null) {
                        minecraft.level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.AMBIENT, volume, pitch, false);
                    } else {
                        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
                    }
                }
            }
            case "amemaru:camera" -> {
                if ("release".equals(string(step, "mode", ""))) {
                    cameraTarget = null;
                    cameraActor = null;
                } else {
                    String actor = string(step, "actor", "");
                    cameraActor = actor.isEmpty() ? null : actors.get(actor);
                    cameraTarget = vec(step, "look_at");
                }
                stepLength = wait ? ticks : 0;
            }
            case "amemaru:actor" -> spawnActor(step);
            case "amemaru:actor_move" -> {
                Integer id = actors.get(string(step, "id", ""));
                Vec3 to = vec(step, "to");
                if (id != null && to != null && minecraft.level != null) {
                    Entity entity = minecraft.level.getEntity(id);
                    if (entity != null) {
                        moves.put(id, new ActorMove(entity.position(), to, Math.max(1, ticks), new int[1]));
                    }
                }
                stepLength = wait ? ticks : 0;
            }
            case "amemaru:actor_look" -> {
                Integer id = actors.get(string(step, "id", ""));
                if (id != null && minecraft.level != null && minecraft.player != null) {
                    Entity entity = minecraft.level.getEntity(id);
                    Vec3 at = vec(step, "look_at");
                    if (at == null) {
                        at = minecraft.player.getEyePosition();
                    }
                    if (entity != null) {
                        double dx = at.x - entity.getX();
                        double dz = at.z - entity.getZ();
                        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                        entity.setYRot(yaw);
                        entity.setYHeadRot(yaw);
                        if (entity instanceof NpcEntity npc) {
                            npc.yBodyRot = yaw;
                            String expression = string(step, "expression", "");
                            if (!expression.isEmpty()) {
                                npc.setExpression(expression);
                            }
                        }
                    }
                }
            }
            case "amemaru:actor_remove" -> {
                Integer id = actors.remove(string(step, "id", ""));
                if (id != null && minecraft.level != null) {
                    minecraft.level.removeEntity(id, Entity.RemovalReason.DISCARDED);
                    moves.remove(id);
                }
            }
            case "amemaru:particles" -> {
                Vec3 at = vec(step, "pos");
                ResourceLocation id = ResourceLocation.tryParse(string(step, "particle", "minecraft:end_rod"));
                ParticleOptions particle = ParticleTypes.END_ROD;
                if (id != null && ForgeRegistries.PARTICLE_TYPES.getValue(id) instanceof SimpleParticleType simple) {
                    particle = simple;
                }
                if (at != null && minecraft.level != null) {
                    int count = integer(step, "count", 20);
                    double spread = number(step, "spread", 1.0);
                    for (int i = 0; i < count; i++) {
                        minecraft.level.addParticle(particle, at.x + (Math.random() - 0.5) * spread * 2, at.y + Math.random() * spread,
                                at.z + (Math.random() - 0.5) * spread * 2, 0.0, 0.03, 0.0);
                    }
                }
            }
            case "amemaru:hud" -> hideHud = bool(step, "hide", true);
            default -> {
                // 知らない手順は飛ばす
            }
        }
    }

    private static void spawnActor(JsonObject step) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Vec3 pos = vec(step, "pos");
        ResourceLocation npc = ResourceLocation.tryParse(string(step, "npc", ""));
        if (level == null || pos == null || npc == null) {
            return;
        }
        NpcEntity actor = ModEntities.NPC.get().create(level);
        if (actor == null) {
            return;
        }
        int id = nextActorId--;
        actor.setId(id);
        actor.setNpcId(npc);
        float yaw = (float) number(step, "yaw", 0.0);
        actor.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        actor.setYHeadRot(yaw);
        actor.yBodyRot = yaw;
        actor.noPhysics = true;
        String expression = string(step, "expression", "");
        if (!expression.isEmpty()) {
            actor.setExpression(expression);
        }
        String key = string(step, "id", npc.getPath());
        Integer old = actors.put(key, id);
        if (old != null) {
            level.removeEntity(old, Entity.RemovalReason.DISCARDED);
        }
        level.putNonPlayerEntity(id, actor);
    }

    // ---------------------------------------------------------------- カメラ（ClientStoryEvents から）

    /** 演出の視点（向き）。null なら、ふつうの視点。 */
    @Nullable
    public static float[] cameraAngles(Vec3 eye, float partialTick, float currentYaw, float currentPitch) {
        if (!isActive()) {
            return null;
        }
        Vec3 target = cameraTarget;
        Minecraft minecraft = Minecraft.getInstance();
        if (cameraActor != null && minecraft.level != null) {
            Entity actor = minecraft.level.getEntity(cameraActor);
            if (actor != null) {
                target = actor.getPosition(partialTick).add(0.0, actor.getBbHeight() * 0.85, 0.0);
            }
        }
        if (target == null) {
            if (cameraInit) {
                return new float[]{cameraYaw, cameraPitch};
            }
            return null;
        }
        Vec3 d = target.subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180.0 / Math.PI));
        if (!cameraInit) {
            cameraYaw = currentYaw;
            cameraPitch = currentPitch;
            cameraInit = true;
        }
        // 少しずつ向ける
        cameraYaw += Mth.wrapDegrees(yaw - cameraYaw) * 0.08F;
        cameraPitch += (pitch - cameraPitch) * 0.08F;
        return new float[]{cameraYaw, cameraPitch};
    }

    // ---------------------------------------------------------------- JSON の小さな道具

    private static String string(JsonObject json, String key, String fallback) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static int integer(JsonObject json, String key, int fallback) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : fallback;
    }

    private static double number(JsonObject json, String key, double fallback) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : fallback;
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
    }

    @Nullable
    private static Vec3 vec(JsonObject json, String key) {
        JsonElement e = json.get(key);
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() != 3) {
            return null;
        }
        JsonArray a = e.getAsJsonArray();
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }
}

package jp.amemaru.lostconnection.client;

import com.mojang.blaze3d.platform.InputConstants;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.client.screen.SkillTreeScreen;
import jp.amemaru.lostconnection.core.config.ClientConfig;
import jp.amemaru.lostconnection.core.registry.ModSounds;
import jp.amemaru.lostconnection.entity.TrainingDummy;
import jp.amemaru.lostconnection.job.def.DeliveryDef;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.UseSkillC2S;
import jp.amemaru.lostconnection.network.s2c.SkillResultS2C;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * スキルのキーの入力（クライアント専用。Phase 0-B 9章）。
 *
 * <ul>
 *   <li>キーに直接（初期値）：スキル枠1〜6を R・Z・X・C・B・Y で使う。</li>
 *   <li>ホットバー式（設定で選ぶ）：左Altを押している間だけ、ホットバーの1〜6がスキルになる。</li>
 * </ul>
 * <p>押すと、手元で使えるかを軽く確かめ（クールダウン・MP）、狙い（相手・地点）を決めてサーバーに送る。
 * 待ち時間を感じないよう、モーションは先に始め、サーバーに断られたら止める。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class SkillInput {
    private static int sequence;
    /** 送った要求の番号 → スキルの番号（断られたときにモーションを止めるため）。 */
    private static final Map<Integer, Integer> PENDING = new HashMap<>();

    private SkillInput() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ToolbarKeys.releaseOnce();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) {
            drain();
            return;
        }
        while (KeyBindings.OPEN_SKILL_TREE.consumeClick()) {
            minecraft.setScreen(new SkillTreeScreen());
            return;
        }
        boolean altMode = ClientConfig.skillControl() == ClientConfig.SkillControl.HOTBAR_ALT;
        for (int slot = 0; slot < KeyBindings.SKILLS.length; slot++) {
            while (KeyBindings.SKILLS[slot].consumeClick()) {
                if (!altMode) {
                    use(minecraft, player, slot);
                }
            }
        }
        if (altMode && altDown(minecraft)) {
            // バニラがホットバーを切りかえる前に、1〜6の押下を受け取ってスキルにする
            KeyMapping[] hotbar = minecraft.options.keyHotbarSlots;
            for (int slot = 0; slot < 6 && slot < hotbar.length; slot++) {
                while (hotbar[slot].consumeClick()) {
                    use(minecraft, player, slot);
                }
            }
        }
    }

    /** 左Altを押しているか（ホットバー式のとき）。 */
    public static boolean altDown(Minecraft minecraft) {
        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT);
    }

    private static void drain() {
        for (KeyMapping key : KeyBindings.SKILLS) {
            while (key.consumeClick()) {
                // 画面を開いている間の押下は捨てる
            }
        }
    }

    /** スキル枠のスキルを使う。 */
    static void use(Minecraft minecraft, LocalPlayer player, int slot) {
        ClientPlayerState state = ClientPlayerState.get();
        if (!state.hasData()) {
            return;
        }
        if (!state.hasJob()) {
            fail(minecraft, Component.translatable("message.amemaru.skill.no_job"));
            return;
        }
        int skillIndex = state.loadout(slot);
        SyncDefinitionsS2C.SkillInfo skill = ClientDefinitions.get().skill(skillIndex);
        int rank = skill == null ? 0 : state.rank(skillIndex);
        if (skill == null || rank <= 0) {
            fail(minecraft, Component.translatable("message.amemaru.skill.empty_slot"));
            return;
        }
        int casting = state.castSkill();
        if (casting >= 0 && casting != skillIndex) {
            fail(minecraft, Component.translatable("message.amemaru.skill.casting"));
            return;
        }
        if (casting < 0) {
            int remaining = state.cooldownRemaining(skillIndex);
            if (remaining > 0) {
                fail(minecraft, Component.translatable("message.amemaru.skill.cooldown", skill.name(),
                        String.format(Locale.ROOT, "%.1f", remaining / 20.0)));
                return;
            }
            int r = Math.min(rank, skill.maxRank()) - 1;
            if (r >= 0 && r < skill.mpCost().length && state.mp() + 1.0E-6 < skill.mpCost()[r]) {
                fail(minecraft, Component.translatable("message.amemaru.skill.no_mp"));
                return;
            }
        }

        // 狙い
        int r = Math.max(0, Math.min(rank, skill.maxRank()) - 1);
        double range = r < skill.aimRange().length ? skill.aimRange()[r] : 16.0;
        DeliveryDef.Type delivery = DeliveryDef.Type.byIndex(skill.delivery());
        int entityId = -1;
        Vec3 point = null;
        if (delivery == DeliveryDef.Type.TARGET || delivery == DeliveryDef.Type.CHAIN) {
            Entity target = pickEntity(player, range, skill.alliesOnly());
            entityId = target == null ? -1 : target.getId();
        } else if (delivery == DeliveryDef.Type.POINT) {
            point = pickPoint(player, range);
        }

        sequence++;
        PENDING.put(sequence, skillIndex);
        if (PENDING.size() > 32) {
            PENDING.keySet().removeIf(key -> key < sequence - 32);
        }
        ModNetwork.sendToServer(new UseSkillC2S(sequence, slot, entityId, point));

        // モーションを先に始める（詠唱を止めるための押下のときは始めない）
        if (casting < 0) {
            String animation = SkillFx.hasCast(skill) ? skill.castAnimation() : skill.animation();
            if (PlayerAnimations.play(player, animation)) {
                SkillFx.markPredicted(skillIndex);
            }
        }
    }

    /** サーバーの答え。断られたら、先に始めたモーションを止める。 */
    public static void handleResult(SkillResultS2C message) {
        Integer skillIndex = PENDING.remove(message.sequence());
        if (message.ok() || skillIndex == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        PlayerAnimations.stop(minecraft.player);
    }

    private static void fail(Minecraft minecraft, Component message) {
        minecraft.gui.setOverlayMessage(message.copy().withStyle(ChatFormatting.RED), false);
        if (minecraft.player != null) {
            minecraft.player.playSound(ModSounds.get("skill.fail"), 0.6F, 1.0F);
        }
    }

    // ---------------------------------------------------------------- 狙い

    /** 視線の先の相手（少し外れていても、近いものを選ぶ）。 */
    @Nullable
    public static Entity pickEntity(LocalPlayer player, double range, boolean allies) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double limit = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye) + 0.5;
        Predicate<Entity> filter = allies
                ? entity -> entity instanceof Player other && other != player && other.isAlive() && !other.isSpectator()
                : entity -> entity instanceof LivingEntity living && living.isAlive() && (entity instanceof Enemy || entity instanceof TrainingDummy);
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(2.0);
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : player.level().getEntities(player, box, filter)) {
            AABB bounds = entity.getBoundingBox().inflate(0.6);
            var hit = bounds.clip(eye, eye.add(look.scale(limit)));
            if (hit.isPresent()) {
                double distance = hit.get().distanceTo(eye);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = entity;
                }
            }
        }
        if (best != null) {
            return best;
        }
        // 当たらなければ、視線から15度以内でいちばんずれの小さい相手
        double bestDot = Math.cos(Math.toRadians(15.0));
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(range), filter)) {
            Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance > range || distance < 1.0E-3) {
                continue;
            }
            double dot = to.scale(1.0 / distance).dot(look);
            if (dot > bestDot && player.hasLineOfSight(entity)) {
                bestDot = dot;
                best = entity;
            }
        }
        return best;
    }

    /** 視線の先の地点（ブロックに当たればそこ、なければ射程の端）。 */
    public static Vec3 pickPoint(LocalPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(range));
        BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return block.getType() == HitResult.Type.MISS ? end : block.getLocation();
    }

    /** ログアウトしたときに消す。 */
    public static void reset() {
        PENDING.clear();
    }
}

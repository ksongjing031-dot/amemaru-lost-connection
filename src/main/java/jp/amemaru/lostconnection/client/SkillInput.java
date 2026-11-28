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
 *   <li>キーに直接（初期値）：スキル枠1〜6を R・G・V・Z・X・C で使う。</li>
 *   <li>ホットバー式（設定で選ぶ）：左Altを押している間だけ、ホットバーの1〜6がスキルになる。</li>
 * </ul>
 * <p>押すと、手元で使えるかを軽く確かめ（クールダウン・MP）、狙い（相手・地点）を決めてサーバーに送る。
 * 待ち時間を感じないよう、モーションは先に始め、サーバーに断られたら止める。</p>
 * <p>溜めるスキルは、押したときに「溜めはじめ」を送り、キーを離したときに狙いを決めて「放つ」を送る。
 * 画面を開いたときも離したことにする。サーバーが溜めきって自動で放ったときは、何も送らない。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class SkillInput {
    private static int sequence;
    /** 送った要求の番号 → スキルの番号（断られたときにモーションを止めるため）。 */
    private static final Map<Integer, Integer> PENDING = new HashMap<>();
    /** 溜めている枠とスキル（溜めていなければ -1）。 */
    private static int chargingSlot = -1;
    private static int chargingSkill = -1;
    /** サーバーが「溜め中」と知らせてきたか（その後で溜め中でなくなったら、サーバーが自動で放った）。 */
    private static boolean serverCharging;

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
        if (player == null) {
            clearCharge();
            drain();
            return;
        }
        if (minecraft.screen != null) {
            if (chargingSlot >= 0) {
                release(minecraft, player);
            }
            drain();
            return;
        }
        while (KeyBindings.OPEN_SKILL_TREE.consumeClick()) {
            minecraft.setScreen(new SkillTreeScreen());
            return;
        }
        if (ClientDown.localDown()) {
            // ダウンの間はスキルを使えない（Phase 4。サーバーでも断る）。押したキーは捨てる
            if (chargingSlot >= 0) {
                clearCharge();
            }
            drain();
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
        // 11月28日：ユニーク武器の固有スキル（初期値：Y）
        while (KeyBindings.UNIQUE_SKILL.consumeClick()) {
            useUnique(minecraft, player);
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
        tickCharge(minecraft, player, altMode);
    }

    /** 溜めている間：キーを離したら放つ。サーバーが自動で放っていたら、何もせず終える。 */
    private static void tickCharge(Minecraft minecraft, LocalPlayer player, boolean altMode) {
        if (chargingSlot < 0) {
            return;
        }
        ClientPlayerState state = ClientPlayerState.get();
        if (state.charging() && state.castSkill() == chargingSkill) {
            serverCharging = true;
        } else if (serverCharging) {
            clearCharge();
            return;
        }
        boolean held;
        if (altMode) {
            KeyMapping[] hotbar = minecraft.options.keyHotbarSlots;
            held = altDown(minecraft) && chargingSlot < hotbar.length && hotbar[chargingSlot].isDown();
        } else {
            held = KeyBindings.SKILLS[chargingSlot].isDown();
        }
        if (!held) {
            release(minecraft, player);
        }
    }

    /** 溜めを離して放つ：狙いを決めて送り、放つモーションを先に始める。 */
    private static void release(Minecraft minecraft, LocalPlayer player) {
        int slot = chargingSlot;
        int skillIndex = chargingSkill;
        clearCharge();
        SyncDefinitionsS2C.SkillInfo skill = ClientDefinitions.get().skill(skillIndex);
        if (skill == null || slot < 0) {
            return;
        }
        Aim aim = aim(player, skill, ClientPlayerState.get().rank(skillIndex));
        sequence++;
        ModNetwork.sendToServer(new UseSkillC2S(sequence, UseSkillC2S.RELEASE, slot, aim.entityId, aim.point, aim.faceId));
        if (aim.faceId >= 0) {
            TargetLock.beginTurn();
        }
        if (PlayerAnimations.play(player, skill.animation())) {
            SkillFx.markPredicted(skillIndex);
        }
    }

    private static void clearCharge() {
        chargingSlot = -1;
        chargingSkill = -1;
        serverCharging = false;
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
        while (KeyBindings.UNIQUE_SKILL.consumeClick()) {
            // 固有スキルのキーも同じ
        }
    }

    /**
     * 手に持っているユニーク武器の固有スキルを使う（11月28日【補完】）。手元で軽く確かめ（武器・詠唱中・クールダウン・MP）、
     * 狙いを決めて、スキル枠の代わりに固有スキルの印（{@link UseSkillC2S#UNIQUE_SLOT}）で送る。モーションは先に始める。
     */
    static void useUnique(Minecraft minecraft, LocalPlayer player) {
        ClientPlayerState.get().noteCombat();
        ClientPlayerState state = ClientPlayerState.get();
        if (!state.hasData()) {
            return;
        }
        jp.amemaru.lostconnection.network.s2c.SyncUniqueSkillsS2C.Entry entry = ClientUniqueSkills.forStack(player.getMainHandItem());
        if (entry == null) {
            fail(minecraft, Component.translatable("message.amemaru.unique.no_weapon"));
            return;
        }
        SyncDefinitionsS2C.SkillInfo skill = entry.skill();
        int skillIndex = skill.index();
        if (chargingSlot >= 0 || state.castSkill() >= 0) {
            fail(minecraft, Component.translatable("message.amemaru.skill.casting"));
            return;
        }
        int remaining = state.cooldownRemaining(skillIndex);
        if (remaining > 0) {
            fail(minecraft, Component.translatable("message.amemaru.skill.cooldown", skill.name(),
                    String.format(Locale.ROOT, "%.1f", remaining / 20.0)));
            return;
        }
        if (skill.mpCost().length > 0 && state.mp() + 1.0E-6 < skill.mpCost()[0]) {
            fail(minecraft, Component.translatable("message.amemaru.skill.no_mp"));
            return;
        }
        Aim aim = aim(player, skill, 1);
        sequence++;
        remember(sequence, skillIndex);
        ModNetwork.sendToServer(new UseSkillC2S(sequence, UseSkillC2S.PRESS, UseSkillC2S.UNIQUE_SLOT, aim.entityId, aim.point, aim.faceId));
        if (aim.faceId >= 0) {
            TargetLock.beginTurn();
        }
        if (PlayerAnimations.play(player, skill.animation())) {
            SkillFx.markPredicted(skillIndex);
        }
    }

    /** スキル枠のスキルを使う。 */
    static void use(Minecraft minecraft, LocalPlayer player, int slot) {
        // 11月24日：スキルを使った（戦っている）。右上のクエストの一覧を、戦いの間はしまうため
        ClientPlayerState.get().noteCombat();
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
        if (chargingSlot >= 0) {
            // 溜めている間は、ほかのキーを受けつけない（同じキーの押し直しも無視する）
            if (chargingSlot != slot) {
                fail(minecraft, Component.translatable("message.amemaru.skill.casting"));
            }
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

        if (skill.charged() && casting < 0) {
            // 溜め：押したことだけを送り、溜めのモーションを先に始める（狙いは離したときに決める）
            sequence++;
            remember(sequence, skillIndex);
            // ロック中は、溜めきって自動で放つときのために、ロックした敵も知らせる（11月24日）
            LivingEntity locked = TargetLock.targetFor(skill);
            ModNetwork.sendToServer(new UseSkillC2S(sequence, UseSkillC2S.PRESS, slot, -1, null, locked == null ? -1 : locked.getId()));
            chargingSlot = slot;
            chargingSkill = skillIndex;
            serverCharging = false;
            if (PlayerAnimations.play(player, skill.chargeAnimation())) {
                SkillFx.markPredicted(skillIndex);
            }
            return;
        }

        Aim aim = aim(player, skill, rank);
        sequence++;
        remember(sequence, skillIndex);
        ModNetwork.sendToServer(new UseSkillC2S(sequence, UseSkillC2S.PRESS, slot, aim.entityId, aim.point, aim.faceId));
        if (aim.faceId >= 0 && casting < 0) {
            // ロックした敵のほうへ向き直る（サーバーでも向きを合わせる。11月24日）
            TargetLock.beginTurn();
        }

        // モーションを先に始める（詠唱を止めるための押下のときは始めない）
        if (casting < 0) {
            String animation = SkillFx.hasCast(skill) ? skill.castAnimation() : skill.animation();
            if (PlayerAnimations.play(player, animation)) {
                SkillFx.markPredicted(skillIndex);
            }
        }
    }

    /**
     * 狙い。
     *
     * @param faceId ロックした敵（サーバーに向きを合わせてもらう相手。ロックしていない・敵に向けないスキルは -1）
     */
    private record Aim(int entityId, @Nullable Vec3 point, int faceId) {
    }

    /** 届け方に合わせて、狙い（相手・地点）を決める。ロックしていれば、ロックした敵を狙う（11月24日）。 */
    private static Aim aim(LocalPlayer player, SyncDefinitionsS2C.SkillInfo skill, int rank) {
        int r = Math.max(0, Math.min(rank, skill.maxRank()) - 1);
        double range = r < skill.aimRange().length ? skill.aimRange()[r] : 16.0;
        DeliveryDef.Type delivery = DeliveryDef.Type.byIndex(skill.delivery());
        LivingEntity locked = TargetLock.targetFor(skill);
        int face = locked == null ? -1 : locked.getId();
        if (delivery == DeliveryDef.Type.TARGET || delivery == DeliveryDef.Type.CHAIN || delivery == DeliveryDef.Type.BEHIND) {
            if (locked != null) {
                return new Aim(locked.getId(), null, face);
            }
            if (delivery == DeliveryDef.Type.BEHIND) {
                return new Aim(-1, null, -1);
            }
            Entity target = pickEntity(player, range, skill.alliesOnly());
            return new Aim(target == null ? -1 : target.getId(), null, -1);
        }
        if (delivery == DeliveryDef.Type.POINT || delivery == DeliveryDef.Type.LEAP) {
            if (locked != null) {
                // ロックした敵の足もと（射程の外なら、サーバーが敵の方向の射程の端にする）
                return new Aim(-1, locked.position(), face);
            }
            return new Aim(-1, pickPoint(player, range), -1);
        }
        return new Aim(-1, null, face);
    }

    private static void remember(int sequence, int skillIndex) {
        PENDING.put(sequence, skillIndex);
        if (PENDING.size() > 32) {
            PENDING.keySet().removeIf(key -> key < sequence - 32);
        }
    }

    /** サーバーの答え。断られたら、先に始めたモーションを止める（溜めも終える）。 */
    public static void handleResult(SkillResultS2C message) {
        Integer skillIndex = PENDING.remove(message.sequence());
        if (message.ok() || skillIndex == null) {
            return;
        }
        if (skillIndex == chargingSkill) {
            clearCharge();
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

    /** 自分の敵か（敵・訓練人形・決闘の相手）。ロックオンでも使う。 */
    public static boolean isFoe(LocalPlayer player, Entity entity) {
        return entity != player && entity instanceof LivingEntity living && living.isAlive()
                && (entity instanceof Enemy || entity instanceof TrainingDummy
                // 決闘の相手（Phase 6 その4）
                || entity.getId() == ClientSocial.opponentId());
    }

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
                : entity -> isFoe(player, entity);
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
        clearCharge();
    }
}

package jp.amemaru.lostconnection.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.client.fx.FxDraw;
import jp.amemaru.lostconnection.client.fx.FxTextures;
import jp.amemaru.lostconnection.client.screen.UiStyle;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.boss.AmemaruBoss;
import jp.amemaru.lostconnection.entity.TrainingDummy;
import jp.amemaru.lostconnection.job.LockOnFacing;
import jp.amemaru.lostconnection.job.def.DeliveryDef;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ターゲットのロックオン（クライアント専用。11月24日のご指示「スキルにターゲットロックオン機能を追加しよう」）。
 *
 * <ul>
 *   <li>ロックのキー（初期値 B）を押す：照準にいちばん近い敵をロックする。ロック中に押すと、右どなりの敵へ移る。</li>
 *   <li>長押し（0.4秒）：ロックを外す。</li>
 *   <li>敵が倒れた・{@value #KEEP_RANGE} ブロックより離れた・3秒見えなかった・自分がダウンした・世界を移ったときは、自動で外れる。</li>
 *   <li>ロック中に使った、敵に向けるスキル（相手・地点・扇・線・飛び道具・突進・跳躍・背後・飛び退き）は、
 *       ロックした敵に向かう。自分はその敵のほうへ素早く向き直り、サーバーでも向きを合わせる（{@link LockOnFacing}）。
 *       味方に使うスキル・自分のまわりのスキル・瞬間移動は、今までどおり。</li>
 *   <li>敵には、壁ごしでも見える照準の印を出し、画面の真ん中の下に、名前・距離・HPを出す。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class TargetLock {
    /** ロックできる距離。 */
    public static final double LOCK_RANGE = 24.0;
    /** これより離れると外れる。 */
    public static final double KEEP_RANGE = 32.0;
    /** 照準からこの角度（度）の中の敵を、先に選ぶ。 */
    private static final double PREFERRED_ANGLE = 40.0;
    /** 照準の外でも、この距離の中なら選べる（背中側の敵も）。 */
    private static final double NEAR_RANGE = 10.0;
    /** これだけ見えない時間が続くと外れる（3秒）。 */
    private static final int UNSEEN_LIMIT = 60;
    /** 長押しで外す（0.4秒）。 */
    private static final int HOLD_TICKS = 8;
    /** スキルを使ったときに向き直る時間。 */
    private static final int TURN_TICKS = 4;

    public static final IGuiOverlay OVERLAY = TargetLock::renderHud;

    private static int lockedId = -1;
    @Nullable
    private static ClientLevel lockedLevel;
    private static int unseen;
    private static long lockedAt;
    /** キーを押している tick 数（押していなければ -1）。 */
    private static int held = -1;
    /** この押下でロックを始めたか（離しても次へ移らない・長押しでも外さない）。 */
    private static boolean pressLocked;
    private static int turnTicks;

    private TargetLock() {
    }

    // ---------------------------------------------------------------- 状態

    /** ロックしている敵（いなければ null）。 */
    @Nullable
    public static LivingEntity target() {
        Minecraft minecraft = Minecraft.getInstance();
        if (lockedId < 0 || minecraft.level == null || minecraft.level != lockedLevel) {
            return null;
        }
        Entity entity = minecraft.level.getEntity(lockedId);
        return entity instanceof LivingEntity living && living.isAlive() && !living.isRemoved() ? living : null;
    }

    public static boolean locked() {
        return target() != null;
    }

    /** 届け方が、ロックした敵に向けるものか（向き直す・敵の番号を送る）。 */
    public static boolean aimsAtLock(SyncDefinitionsS2C.SkillInfo skill) {
        if (skill.alliesOnly()) {
            return false;
        }
        return switch (DeliveryDef.Type.byIndex(skill.delivery())) {
            case TARGET, CHAIN, BEHIND, POINT, LEAP, PROJECTILE, LINE, CONE, DASH, BACKSTEP -> true;
            default -> false;
        };
    }

    /** スキルに使う、ロックした敵（敵に向けるスキルでなければ null）。 */
    @Nullable
    public static LivingEntity targetFor(SyncDefinitionsS2C.SkillInfo skill) {
        return aimsAtLock(skill) ? target() : null;
    }

    /** スキルを使ったとき：ロックした敵のほうへ、数 tick で向き直る。 */
    public static void beginTurn() {
        if (target() != null) {
            turnTicks = TURN_TICKS;
        }
    }

    public static void reset() {
        lockedId = -1;
        lockedLevel = null;
        unseen = 0;
        held = -1;
        pressLocked = false;
        turnTicks = 0;
    }

    private static void unlock(boolean sound) {
        if (lockedId >= 0 && sound) {
            play(0.75F, 0.35F);
        }
        lockedId = -1;
        lockedLevel = null;
        unseen = 0;
        turnTicks = 0;
    }

    /**
     * 自動の確認（CIの写真）用：ロックのキーを1回押したのと同じ。ロックしていなければ見ている敵を、していれば右どなりの敵をロックする。
     * CI は透明な画面を開いたまま撮るので（画面を開いている間のキーは捨てる決まり）、キーではなくここから呼ぶ。
     */
    static void ciPress(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        LivingEntity current = target();
        LivingEntity next = pick(player, current);
        if (next != null && next != current) {
            lock(next);
        }
    }

    private static void lock(LivingEntity entity) {
        lockedId = entity.getId();
        lockedLevel = (ClientLevel) entity.level();
        lockedAt = Util.getMillis();
        unseen = 0;
        turnTicks = 0;
        play(1.6F, 0.5F);
    }

    private static void play(float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, pitch, volume));
    }

    // ---------------------------------------------------------------- 毎 tick

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            reset();
            return;
        }
        if (lockedId >= 0) {
            keepOrDrop(minecraft, player);
        }
        KeyMapping key = KeyBindings.LOCK_ON;
        if (minecraft.screen != null) {
            while (key.consumeClick()) {
                // 画面を開いている間の押下は捨てる
            }
            held = -1;
            return;
        }
        boolean down = ClientDown.localDown();
        while (key.consumeClick()) {
            if (down || held >= 0) {
                continue;
            }
            held = 0;
            pressLocked = false;
            if (lockedId < 0) {
                LivingEntity first = pick(player, null);
                if (first != null) {
                    lock(first);
                    pressLocked = true;
                } else {
                    minecraft.gui.setOverlayMessage(Component.translatable("message.amemaru.lock.none").withStyle(ChatFormatting.GRAY), false);
                }
            }
        }
        if (held >= 0) {
            if (key.isDown()) {
                held++;
                if (held == HOLD_TICKS && !pressLocked && lockedId >= 0) {
                    unlock(true);
                    pressLocked = true;
                }
            } else {
                if (!pressLocked && held < HOLD_TICKS && lockedId >= 0) {
                    LivingEntity current = target();
                    LivingEntity next = pick(player, current);
                    if (next != null && next != current) {
                        lock(next);
                    }
                }
                held = -1;
            }
        }
        tickTurn(player);
    }

    /** ロックを続けるか確かめる（倒れた・遠い・長く見えない・ダウン・世界を移った）。 */
    private static void keepOrDrop(Minecraft minecraft, LocalPlayer player) {
        LivingEntity target = target();
        if (target == null || !SkillInput.isFoe(player, target) || ClientDown.localDown()) {
            unlock(false);
            return;
        }
        if (target.distanceToSqr(player) > KEEP_RANGE * KEEP_RANGE) {
            unlock(true);
            return;
        }
        if (player.hasLineOfSight(target)) {
            unseen = 0;
        } else if (++unseen > UNSEEN_LIMIT) {
            unlock(true);
        }
    }

    /** スキルを使ったあと：ロックした敵のほうへ、なめらかに向き直る。 */
    private static void tickTurn(LocalPlayer player) {
        if (turnTicks <= 0) {
            return;
        }
        LivingEntity target = target();
        if (target == null) {
            turnTicks = 0;
            return;
        }
        float[] goal = LockOnFacing.angles(player.getEyePosition(), target.getBoundingBox().getCenter());
        float factor = turnTicks <= 1 ? 1.0F : 0.55F;
        float yaw = player.getYRot() + Mth.wrapDegrees(goal[0] - player.getYRot()) * factor;
        float pitch = Mth.clamp(player.getXRot() + (goal[1] - player.getXRot()) * factor, -90.0F, 90.0F);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        turnTicks--;
    }

    // ---------------------------------------------------------------- 選ぶ

    /**
     * ロックする敵を選ぶ。{@code current} がなければ照準にいちばん近い敵、あれば右どなりの敵（いちばん右なら左端へ戻る）。
     */
    @Nullable
    static LivingEntity pick(LocalPlayer player, @Nullable LivingEntity current) {
        List<LivingEntity> candidates = candidates(player);
        if (candidates.isEmpty()) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        if (current == null) {
            return candidates.stream()
                    .min(Comparator.comparingDouble((LivingEntity e) -> angleTo(eye, look, e)).thenComparingDouble(e -> e.distanceToSqr(player)))
                    .orElse(null);
        }
        // 自分の向きから見た左右の角度で並べ、今の相手の右どなりへ
        float yaw = player.getYRot();
        List<LivingEntity> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(e -> relativeYaw(eye, yaw, e)));
        double currentYaw = relativeYaw(eye, yaw, current);
        for (LivingEntity entity : sorted) {
            if (entity != current && relativeYaw(eye, yaw, entity) > currentYaw + 1.0E-3) {
                return entity;
            }
        }
        for (LivingEntity entity : sorted) {
            if (entity != current) {
                return entity;
            }
        }
        return current;
    }

    /** ロックできる敵：見えていて、照準から {@value #PREFERRED_ANGLE} 度の中（なければ近くの敵）。 */
    private static List<LivingEntity> candidates(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        List<LivingEntity> inView = new ArrayList<>();
        List<LivingEntity> near = new ArrayList<>();
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(LOCK_RANGE))) {
            if (!(entity instanceof LivingEntity living) || !SkillInput.isFoe(player, living)) {
                continue;
            }
            double distance = living.getBoundingBox().getCenter().distanceTo(eye);
            if (distance > LOCK_RANGE || !player.hasLineOfSight(living)) {
                continue;
            }
            if (angleTo(eye, look, living) <= PREFERRED_ANGLE) {
                inView.add(living);
            } else if (distance <= NEAR_RANGE) {
                near.add(living);
            }
        }
        return inView.isEmpty() ? near : inView;
    }

    private static double angleTo(Vec3 eye, Vec3 look, LivingEntity entity) {
        Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
        double length = to.length();
        if (length < 1.0E-4) {
            return 0.0;
        }
        return Math.toDegrees(Math.acos(Mth.clamp(to.scale(1.0 / length).dot(look), -1.0, 1.0)));
    }

    /** 自分の向きから見た、相手の左右の角度（左がマイナス、右がプラス）。 */
    private static double relativeYaw(Vec3 eye, float yaw, LivingEntity entity) {
        float[] angles = LockOnFacing.angles(eye, entity.getBoundingBox().getCenter());
        return Mth.wrapDegrees(angles[0] - yaw);
    }

    // ---------------------------------------------------------------- 世界の中の印

    /** 照準の印（{@code FxWorld} から毎フレーム呼ぶ）。壁ごしでも見える。 */
    public static void render(FxDraw draw, ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        LivingEntity target = target();
        if (target == null || minecraft.options.hideGui || target.level() != level) {
            return;
        }
        float partial = draw.partialTick();
        double x = Mth.lerp(partial, target.xo, target.getX());
        double y = Mth.lerp(partial, target.yo, target.getY());
        double z = Mth.lerp(partial, target.zo, target.getZ());
        float height = target.getBbHeight();
        float width = target.getBbWidth();
        Vec3 center = new Vec3(x, y + height * 0.5, z);
        long elapsed = Util.getMillis() - lockedAt;
        float appear = Mth.clamp(elapsed / 220.0F, 0.0F, 1.0F);
        float pulse = 1.0F + (1.0F - appear) * 0.7F;
        float breathe = 1.0F + 0.04F * Mth.sin(elapsed / 160.0F);
        float size = Mth.clamp(Math.max(width, height) * 1.25F + 0.5F, 1.0F, 7.0F) * pulse * breathe;
        float spin = elapsed / 1400.0F;
        int color = target instanceof AmemaruBoss ? 0xFF7A5C : 0xFFC94D;
        VertexConsumer overlay = draw.overlay(FxTextures.LOCK);
        draw.billboard(overlay, center, size, spin, color, 0.85F * appear);
        // 頭の上の小さな矢印
        double bob = 0.12 * Mth.sin(elapsed / 220.0F);
        Vec3 above = new Vec3(x, y + height + 0.55 + bob, z);
        draw.billboard(draw.overlay(FxTextures.LOCK_ARROW), above, 0.55F, 0.0F, color, 0.95F * appear);
    }

    // ---------------------------------------------------------------- 画面の表示

    private static void renderHud(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        LivingEntity target = target();
        if (target == null || minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        Font font = minecraft.font;
        String name = target.getDisplayName().getString();
        int distance = (int) Math.round(Math.sqrt(target.distanceToSqr(minecraft.player)));
        String meters = Component.translatable("hud.amemaru.lock.distance", distance).getString();
        String mark = "◆ ";
        int nameWidth = font.width(mark) + font.width(name) + 6 + font.width(meters);
        int cx = width / 2;
        int y = height / 2 + 12;
        int x = cx - nameWidth / 2;
        boolean boss = target instanceof AmemaruBoss;
        int accent = boss ? 0xFF7A5C : 0xFFC94D;
        graphics.drawString(font, mark, x, y, accent, true);
        x += font.width(mark);
        graphics.drawString(font, name, x, y, UiStyle.TEXT, true);
        x += font.width(name) + 6;
        graphics.drawString(font, meters, x, y, UiStyle.TEXT_DIM, true);

        double hp = -1;
        double max = 1;
        if (target instanceof AmemaruMob mob) {
            hp = mob.getEnemyHp();
            max = mob.getEnemyMaxHp();
        } else if (target instanceof TrainingDummy dummy) {
            hp = dummy.getDummyHp();
            max = dummy.getDummyMaxHp();
        }
        if (hp >= 0) {
            int barWidth = 72;
            int bx = cx - barWidth / 2;
            int by = y + 11;
            float ratio = (float) Mth.clamp(hp / Math.max(1.0, max), 0.0, 1.0);
            graphics.fill(bx - 1, by - 1, bx + barWidth + 1, by + 4, 0xC0090C14);
            graphics.fill(bx, by, bx + barWidth, by + 3, 0xFF2A1A1F);
            graphics.fill(bx, by, bx + Math.round(barWidth * ratio), by + 3, boss ? 0xFFE0563C : 0xFFE8B23C);
        }
    }
}

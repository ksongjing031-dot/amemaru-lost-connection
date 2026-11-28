package jp.amemaru.lostconnection.enemy.castle;

import jp.amemaru.lostconnection.combat.PlayerColdService;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import jp.amemaru.lostconnection.entity.GroundMarker;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 霜の猟犬（9階の雑魚。Phase 0-A 9章の9階「霜の猟犬」）：王城で飼われていた狩りの犬。痩せた体に霜の毛、首に近衛の青い首輪【補完】。
 * 3〜4匹で出る。
 *
 * <ul>
 *   <li>噛みつき（{@code skills.bite}）と、飛びかかり（{@code skills.pounce}。離れた所から、狙った人の足もとに印を出して跳びこむ）</li>
 *   <li><b>霜の匂い</b>：噛まれた人には、しばらく霜の匂いがつく（{@code behavior.scent_seconds} 秒。体から白い霜が立つ）。まわり
 *       （{@code behavior.pack_range}）の猟犬は、匂いのついた人を狙い、速く走り、強く噛む（{@code behavior.scent_multiplier} 倍）。
 *       仲間のそばでしゃがむ（結び直しと同じ）と匂いが消える【補完】</li>
 *   <li>噛むと凍える</li>
 * </ul>
 */
public class FrostHound extends AmemaruMob {
    /** 霜の匂い（プレイヤー → 消える時刻）。 */
    private static final Map<UUID, Long> SCENT = new HashMap<>();
    private static final Set<UUID> TOLD = new HashSet<>();

    public FrostHound(EntityType<? extends FrostHound> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "frost_hound";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_pounce", "skill_howl"};
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PounceGoal());
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "bite", "attack", TELEGRAPH_WINDUP, 1.4, SoundEvents.WOLF_GROWL) {
            @Override
            public void tick() {
                // 匂いのついた人へは、速く走る
                LivingEntity target = getTarget();
                double boost = target instanceof ServerPlayer player && scented(player) && def() != null
                        ? def().behavior("scent_speed", 1.3) : 1.0;
                getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                        .setBaseValue(def() == null ? 0.3 : def().moveSpeed() * boost);
                super.tick();
            }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** その人に霜の匂いがついているか。 */
    public static boolean scented(ServerPlayer player) {
        Long until = SCENT.get(player.getUUID());
        return until != null && until > player.level().getGameTime();
    }

    /** 霜の匂いを消す（しゃがんで仲間に触れたとき・倒れたとき）。 */
    public static void clearScent(UUID player) {
        SCENT.remove(player);
    }

    /** 自動テスト用：匂いを全部消す。 */
    public static void resetForTest() {
        SCENT.clear();
    }

    @Override
    public void onStruck(ServerPlayer player, double coefficient) {
        EnemyDef def = def();
        PlayerColdService.chill(player, 1, (int) Math.round((def == null ? 3.0 : def.behavior("chill_seconds", 3.0)) * 20));
        mark(player);
    }

    /** 霜の匂いをつけ、まわりの猟犬に知らせる。 */
    public void mark(ServerPlayer player) {
        EnemyDef def = def();
        if (def == null || !(level() instanceof ServerLevel level)) {
            return;
        }
        boolean fresh = !scented(player);
        SCENT.put(player.getUUID(), level.getGameTime() + Math.round(def.behavior("scent_seconds", 8.0) * 20));
        double range = def.behavior("pack_range", 16.0);
        for (FrostHound other : level.getEntitiesOfClass(FrostHound.class, getBoundingBox().inflate(range))) {
            if (other != this && other.isAlive()) {
                other.addThreat(player, def.behavior("scent_threat", 30.0));
                other.setTarget(player);
            }
        }
        if (fresh) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOLF_HOWL, SoundSource.HOSTILE, 1.2F, 1.3F);
            playAction("skill_howl");
            if (TOLD.add(player.getUUID())) {
                player.displayClientMessage(Component.translatable("message.amemaru.frost_hound.scent").withStyle(ChatFormatting.AQUA), false);
            }
        }
    }

    /** 匂いのついた人へは、強く噛む。 */
    public double scentMultiplier(ServerPlayer player) {
        EnemyDef def = def();
        return def != null && scented(player) ? def.behavior("scent_multiplier", 1.3) : 1.0;
    }

    @Override
    public double attackPower() {
        double base = super.attackPower();
        LivingEntity target = getTarget();
        return target instanceof ServerPlayer player ? base * scentMultiplier(player) : base;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return;
        }
        if (tickCount % 10 == 0 && getTarget() instanceof ServerPlayer player && scented(player) && getId() % 3 == tickCount / 10 % 3) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.3, 0.5, 0.3, 0.01);
            level.sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 1.6, player.getZ(), 3, 0.2, 0.2, 0.2, 0.0);
        }
        // しゃがんで仲間に触れると、匂いが消える（結び直しと同じ「触れる」。Phase 0-A 13章「ほぼすべてのロスト状態が解ける」）
        if (tickCount % 20 == 0) {
            long now = level.getGameTime();
            SCENT.entrySet().removeIf(entry -> entry.getValue() <= now);
            for (UUID id : Set.copyOf(SCENT.keySet())) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                if (player == null || !player.isAlive()) {
                    SCENT.remove(id);
                    continue;
                }
                if (player.isShiftKeyDown()) {
                    for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(2.5))) {
                        if (other != player && other.isAlive() && !other.isSpectator()) {
                            SCENT.remove(id);
                            player.displayClientMessage(Component.translatable("message.amemaru.frost_hound.scent_cleared")
                                    .withStyle(ChatFormatting.GRAY), true);
                            break;
                        }
                    }
                }
            }
        }
    }

    /** 飛びかかり：離れた所から、狙った人の足もとに印を出し、予兆の後に跳びこむ。 */
    private final class PounceGoal extends Goal {
        private int timer = -1;
        private int flight;
        private Vec3 at = Vec3.ZERO;

        PounceGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        private EnemyDef.Skill skill() {
            EnemyDef def = def();
            return def == null ? new EnemyDef.Skill(1.3, 0.8, 6.0, 9.0, Map.of()) : def.skill("pounce", 1.3, 0.8, 6.0, 9.0);
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive() || isBusy() || !ready("pounce") || isReturning() || !onGround()) {
                return false;
            }
            double distance = distanceTo(target);
            return distance >= skill().param("min_range", 4.0) && distance <= skill().range() && hasLineOfSight(target);
        }

        @Override
        public boolean canContinueToUse() {
            return (timer >= 0 || flight > 0) && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            LivingEntity target = getTarget();
            if (target == null || !(level() instanceof ServerLevel level)) {
                timer = -1;
                return;
            }
            EnemyDef.Skill skill = skill();
            at = target.position();
            timer = Math.max(1, skill.windupTicks());
            flight = 0;
            setBusy(timer + 24);
            getNavigation().stop();
            faceTowards(at);
            playAction("skill_pounce");
            GroundMarker.place(level, at, (float) skill.param("radius", 1.6), timer + 8, GroundMarker.KIND_DANGER);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOLF_GROWL, SoundSource.HOSTILE, 1.0F, 1.2F);
        }

        @Override
        public void stop() {
            timer = -1;
            flight = 0;
            startCooldown("pounce", skill().cooldownTicks());
        }

        @Override
        public void tick() {
            if (!(level() instanceof ServerLevel level)) {
                return;
            }
            if (timer >= 0) {
                getNavigation().stop();
                faceTowards(at);
                if (timer-- == 0) {
                    // 跳ぶ：8 tick で印まで届く弧
                    Vec3 delta = at.subtract(position());
                    setDeltaMovement(delta.x / 8.0, 0.55, delta.z / 8.0);
                    hasImpulse = true;
                    flight = 12;
                    timer = -1;
                }
                return;
            }
            if (--flight <= 0 || (flight < 9 && onGround())) {
                flight = 0;
                EnemyDef.Skill skill = skill();
                double radius = skill.param("radius", 1.6);
                for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(at, at).inflate(radius, 2.0, radius))) {
                    double dx = player.getX() - at.x;
                    double dz = player.getZ() - at.z;
                    if (Math.sqrt(dx * dx + dz * dz) <= radius + player.getBbWidth() / 2.0) {
                        EnemyCombat.strike(FrostHound.this, player, skill.power(), null);
                    }
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 0.2, getZ(), 14, 0.6, 0.1, 0.6, 0.05);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOLF_GROWL, SoundSource.HOSTILE, 1.0F, 0.8F);
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WOLF_PANT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOLF_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.1F + (random.nextFloat() - 0.5F) * 0.15F;
    }
}

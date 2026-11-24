package jp.amemaru.lostconnection.enemy.temple;

import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.ai.ChargeGoal;
import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 欠けた聖獣（4階の雑魚。Phase 0-A 9章「神殿の守護獣がロストで欠けたもの」）。白い石の毛並みに金のたてがみの、獅子のような
 * 守護獣。体のところどころが白く抜け落ちている（ロスト）。
 *
 * <ul>
 *   <li>前足の爪（前の扇）と、跳びかかり（短い線の予兆。{@link ChargeGoal}）</li>
 *   <li>咆哮：HPが半分を下回ると一度、吠えて、まわりの神殿の敵の攻撃を {@code behavior.roar_attack} 倍に
 *       （{@code behavior.roar_seconds} 秒）【補完】</li>
 *   <li>欠けた体：ときどき、守るべきものを忘れて立ち止まる（{@code behavior.forget_chance}。ロストの侵食）</li>
 *   <li>古き番獣（名のある魔物）：大きく、咆哮が2回</li>
 * </ul>
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = jp.amemaru.lostconnection.AmemaruMod.MOD_ID)
public class BrokenBeast extends AmemaruMob {
    private int roars;
    private int forget;

    public BrokenBeast(EntityType<? extends BrokenBeast> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "broken_beast";
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_charge", "skill_roar", "skill_forget"};
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ChargeGoal(this, SoundEvents.RAVAGER_ROAR));
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "claw", "attack", TELEGRAPH_CONE, 1.3, SoundEvents.PLAYER_ATTACK_SWEEP));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return;
        }
        EnemyDef def = def();
        if (def == null) {
            return;
        }
        if (forget > 0) {
            forget--;
            getNavigation().stop();
            if (forget % 10 == 0) {
                level.sendParticles(ParticleTypes.WHITE_ASH, getX(), getY() + getBbHeight(), getZ(), 6, 0.3, 0.2, 0.3, 0.0);
            }
            return;
        }
        int maxRoars = (int) Math.round(def.behavior("roars", 1.0));
        double ratio = getEnemyHp() / getEnemyMaxHp();
        double nextAt = roars == 0 ? 0.5 : 0.25;
        if (roars < maxRoars && ratio <= nextAt && getTarget() != null && !isBusy()) {
            roar(level, def);
        }
        if (getTarget() != null && tickCount % 40 == 0 && !isBusy() && random.nextDouble() < def.behavior("forget_chance", 0.04)) {
            // 守るものを忘れて、しばらく立ち止まる（ロスト）
            forget = 30;
            setBusy(forget);
            playAction("skill_forget");
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOLF_WHINE, SoundSource.HOSTILE, 1.0F, 0.6F);
        }
    }

    /** 咆哮：まわりの神殿の敵の攻撃が上がる。 */
    private void roar(ServerLevel level, EnemyDef def) {
        roars++;
        setBusy(24);
        getNavigation().stop();
        playAction("skill_roar");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 1.1F);
        level.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + getBbHeight() * 0.6, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        double radius = def.behavior("roar_radius", 10.0);
        double multiplier = def.behavior("roar_attack", 1.25);
        int ticks = (int) Math.round(def.behavior("roar_seconds", 10.0) * 20);
        for (AmemaruMob ally : level.getEntitiesOfClass(AmemaruMob.class, getBoundingBox().inflate(radius))) {
            if (ally.isAlive() && (ally instanceof BrokenBeast || ally instanceof SilentStatue || ally instanceof CrystalBeetle
                    || ally instanceof PrayerEcho)) {
                ally.setAttackMultiplier(Math.max(ally.attackMultiplier(), multiplier));
                ROARED.put(ally.getUUID(), level.getGameTime() + ticks);
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, ally.getX(), ally.getY() + ally.getBbHeight() + 0.2, ally.getZ(), 2, 0.2, 0.1,
                        0.2, 0.0);
            }
        }
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(16.0))) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.amemaru.beast.roar")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), true);
        }
    }

    /** 咆哮で攻撃が上がった敵と、終わる時刻。 */
    private static final java.util.Map<java.util.UUID, Long> ROARED = new java.util.HashMap<>();

    /** 咆哮の時間が過ぎたら、攻撃を元に戻す（どの敵でも、毎 tick の終わりに）。 */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || ROARED.isEmpty()) {
            return;
        }
        net.minecraft.server.level.ServerLevel tower = jp.amemaru.lostconnection.tower.FloorDefs.towerLevel(event.getServer());
        if (tower == null) {
            ROARED.clear();
            return;
        }
        long now = tower.getGameTime();
        ROARED.entrySet().removeIf(entry -> {
            if (now < entry.getValue()) {
                return false;
            }
            if (tower.getEntity(entry.getKey()) instanceof AmemaruMob mob && !(mob instanceof jp.amemaru.lostconnection.enemy.boss.AmemaruBoss)) {
                mob.setAttackMultiplier(1.0);
            }
            return true;
        });
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WOLF_GROWL;
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
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WOLF_STEP, 0.6F, 0.6F);
    }

    @Override
    public float getVoicePitch() {
        return 0.55F;
    }
}

package jp.amemaru.lostconnection.enemy.castle;

import jp.amemaru.lostconnection.combat.AfflictionService;
import jp.amemaru.lostconnection.combat.LostService;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.enemy.CrystalBee;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.entity.CrystalNeedle;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Map;

/**
 * 書架霊（9階の雑魚。Phase 0-A 9章の9階「書架霊（ページが舞う亡霊。魔法攻撃）」）：王家の書庫の本から抜け出た亡霊。ほどけた頁が
 * まわりを舞い、顔のあるはずの所は白い空白【補完】。
 *
 * <ul>
 *   <li>宙に浮いて、少し離れた所から頁を撃つ（結晶蜂と同じ動き。技 {@code skills.needle}）。頁は<b>魔法</b>で、受ける人の守りの
 *       {@code behavior.pierce} を通り抜ける（鎧を通る魔法【補完】）</li>
 *   <li>頁に当たると、ロスト状態「薄れ」（味方の名前が「？？？」に見える。{@code behavior.fade_seconds} 秒）。9階のロストの侵食
 *       「名前と顔の空白」</li>
 *   <li>頁の嵐（{@code skills.storm}）：狙った人の足もとに印を出し、そこへ頁を渦巻かせる（範囲の魔法。当たると薄れ）</li>
 *   <li>綴じ手（名のある魔物。定義 {@code shelf_wraith_binder}）：頁の嵐に当たった人を、頁で綴じて
 *       {@code behavior.bind_seconds} 秒動けなくする</li>
 * </ul>
 */
public class ShelfWraith extends CrystalBee {
    public ShelfWraith(EntityType<? extends ShelfWraith> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "shelf_wraith";
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new StormGoal());
        goalSelector.addGoal(2, new jp.amemaru.lostconnection.enemy.ai.HoverShootGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_needle", "skill_storm"};
    }

    @Override
    public int projectileStyle() {
        return CrystalNeedle.STYLE_PAGE;
    }

    @Override
    public void onStruck(ServerPlayer player, double coefficient) {
        EnemyDef def = def();
        double seconds = def == null ? 8.0 : def.behavior("fade_seconds", 8.0);
        LostService.inflict(player, ModEffects.LOST_FADE.get(), (int) Math.round(seconds * 20));
    }

    @Override
    public Map<String, Boolean> boneToggles() {
        // 綴じ手だけ、胸の前に開いた大きな本
        net.minecraft.resources.ResourceLocation id = variantId();
        return Map.of("tome", id != null && id.getPath().endsWith("_binder"));
    }

    private EnemyDef.Skill storm() {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(1.2, 1.4, 9.0, 14.0, Map.of()) : def.skill("storm", 1.2, 1.4, 9.0, 14.0);
    }

    /** 頁の嵐が落ちた：印の中の人に当てる。自動テストからも呼べる（当たった人数を返す）。 */
    public int landStorm(ServerLevel level, Vec3 at) {
        EnemyDef.Skill skill = storm();
        EnemyDef def = def();
        double radius = skill.param("radius", 2.5);
        double pierce = def == null ? 0.0 : def.behavior("pierce", 0.0);
        int bindTicks = def == null ? 0 : (int) Math.round(def.behavior("bind_seconds", 0.0) * 20);
        int hits = 0;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                new net.minecraft.world.phys.AABB(at, at).inflate(radius, 3.0, radius))) {
            double dx = player.getX() - at.x;
            double dz = player.getZ() - at.z;
            if (Math.sqrt(dx * dx + dz * dz) > radius + player.getBbWidth() / 2.0 || Math.abs(player.getY() - at.y) > 3.0) {
                continue;
            }
            if (EnemyCombat.strike(this, player, skill.power(), null, pierce)) {
                hits++;
                if (bindTicks > 0) {
                    AfflictionService.root(player, bindTicks, this);
                    player.displayClientMessage(Component.translatable("message.amemaru.shelf_wraith.bound")
                            .withStyle(ChatFormatting.AQUA), true);
                }
            }
        }
        level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 1.0, at.z, 60, radius * 0.5, 0.8, radius * 0.5, 0.6);
        level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 0.6, at.z, 20, radius * 0.5, 0.4, radius * 0.5, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 1.6F, 0.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.HOSTILE, 1.0F, 1.3F);
        return hits;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && isAlive()) {
            // ほどけた頁と、青白い文字の粒
            if (random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.ENCHANT, getRandomX(0.9), getY() + random.nextDouble() * getBbHeight(),
                        getRandomZ(0.9), (random.nextDouble() - 0.5) * 0.4, 0.1, (random.nextDouble() - 0.5) * 0.4);
            }
            if (random.nextInt(5) == 0) {
                level().addParticle(ParticleTypes.WHITE_ASH, getRandomX(0.6), getY() + getBbHeight() * 0.8, getRandomZ(0.6), 0.0, -0.02, 0.0);
            }
        }
    }

    /** 頁の嵐：止まって、狙った人の足もとに印を出し、予兆の後に頁を渦巻かせる。 */
    private final class StormGoal extends Goal {
        private int timer = -1;
        private Vec3 at = Vec3.ZERO;

        StormGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive() || isBusy() || !ready("storm") || isReturning() || def() == null) {
                return false;
            }
            double distance = distanceTo(target);
            return distance <= storm().range() && distance >= 4.0 && hasLineOfSight(target);
        }

        @Override
        public boolean canContinueToUse() {
            return timer >= 0 && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            LivingEntity target = getTarget();
            EnemyDef.Skill skill = storm();
            if (target == null || !(level() instanceof ServerLevel level)) {
                timer = -1;
                return;
            }
            at = new Vec3(target.getX(), Math.floor(target.getY() + 0.01), target.getZ());
            timer = Math.max(1, skill.windupTicks());
            setBusy(timer + 10);
            getNavigation().stop();
            faceTowards(at);
            playAction("skill_storm");
            GroundMarker.place(level, at, (float) skill.param("radius", 2.5), timer, GroundMarker.KIND_DANGER);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 1.2F, 1.4F);
        }

        @Override
        public void stop() {
            timer = -1;
            startCooldown("storm", storm().cooldownTicks());
        }

        @Override
        public void tick() {
            setDeltaMovement(getDeltaMovement().scale(0.6));
            getLookControl().setLookAt(at.x, at.y + 1.0, at.z);
            if (level() instanceof ServerLevel level) {
                if (timer % 4 == 0) {
                    level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 2.5, at.z, 6, 1.0, 0.4, 1.0, 0.4);
                }
                if (--timer < 0) {
                    landStorm(level, at);
                }
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BOOK_PAGE_TURN;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VEX_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    }
}

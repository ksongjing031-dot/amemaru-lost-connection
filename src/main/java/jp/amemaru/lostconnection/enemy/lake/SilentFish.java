package jp.amemaru.lostconnection.enemy.lake;

import jp.amemaru.lostconnection.combat.PlayerColdService;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyCombat;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.entity.GroundMarker;
import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.lake.ThinIceService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * 無音の大魚（8階の雑魚。Phase 0-A 9章の8階「氷の下から襲う。割れ目が予兆」）：鱗の白い、大きな湖の魚。ふだんは氷の下を泳いでいて、
 * 叩いても届かない（氷ごしに、暗い影だけが見える）。氷の上の人の真下へ来ると、足もとに氷のひびの印（予兆）を出し、氷を割って
 * 跳び上がる（lunge。当たると跳ね上げられ、凍える）。跳んだあとはしばらく氷の上でもがいていて（beached 秒）、この間だけ叩ける。
 * かみつく（bite）。割れた氷は、しばらく水の穴になる【補完】。
 */
public class SilentFish extends AmemaruMob {
    private static final EntityDataAccessor<Boolean> DATA_SUBMERGED = SynchedEntityData.defineId(SilentFish.class,
            EntityDataSerializers.BOOLEAN);

    private enum State { LURK, RISE, SURFACED }

    private State state = State.LURK;
    private int timer;
    @Nullable
    private Vec3 strikeAt;
    /** 氷の面のブロックの高さ（世界の y）。初めての tick に決める。 */
    private int iceY = Integer.MIN_VALUE;
    private int wanderTimer;
    private Vec3 wander = Vec3.ZERO;

    public SilentFish(EntityType<? extends SilentFish> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    public String assetName() {
        return "silent_fish";
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SUBMERGED, true);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_lunge", "skill_dive"};
    }

    /** 氷の下にいるか（このときは叩いても届かない）。 */
    public boolean submerged() {
        return entityData.get(DATA_SUBMERGED);
    }

    private void setSubmerged(boolean value) {
        entityData.set(DATA_SUBMERGED, value);
    }

    @Override
    public double damageTakenMultiplier(@Nullable Entity attacker) {
        return submerged() ? 0.0 : 1.0;
    }

    @Override
    public boolean isPickable() {
        return !submerged() && super.isPickable();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    protected boolean usesLeash() {
        return false;
    }

    /** 氷の下から、氷の上を歩く人の足音（震え）で気づく（見えていなくてよい）。 */
    @Nullable
    @Override
    protected ServerPlayer findAggroTarget(ServerLevel level) {
        EnemyDef def = def();
        if (def == null || def.aggroRange() <= 0) {
            return null;
        }
        double range = def.aggroRange();
        ServerPlayer best = null;
        double bestDistance = range * range;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
                continue;
            }
            double distance = player.distanceToSqr(this);
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    private EnemyDef.Skill lunge() {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(1.5, 1.3, 7.0, 18.0, Map.of()) : def.skill("lunge", 1.5, 1.3, 7.0, 18.0);
    }

    private EnemyDef.Skill bite() {
        EnemyDef def = def();
        return def == null ? new EnemyDef.Skill(1.0, 0.5, 1.6, 2.6, Map.of()) : def.skill("bite", 1.0, 0.5, 1.6, 2.6);
    }

    /** 氷の面の高さを決める（8階なら階の湖の ice_y。ほかの所なら、生まれた足もとのひとつ下）。 */
    private void findIce(ServerLevel level) {
        FloorDef def = FloorDefs.floorAt(level, position());
        if (def != null && def.features().lake() != null) {
            iceY = (int) Math.round(def.originVec().y) + def.features().lake().iceY();
        } else {
            iceY = (int) Math.floor(getY()) - 1;
        }
    }

    /** 氷の下の、泳ぐ高さ。 */
    private double swimY() {
        return iceY - 1.7;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            if (level().isClientSide() && isAlive() && submerged() && random.nextInt(4) == 0) {
                level().addParticle(ParticleTypes.BUBBLE, getRandomX(0.8), getY() + 0.5, getRandomZ(0.8), 0.0, 0.04, 0.0);
            }
            return;
        }
        if (iceY == Integer.MIN_VALUE) {
            findIce(level);
            dive(level, false);
        }
        setDeltaMovement(Vec3.ZERO);
        switch (state) {
            case LURK -> tickLurk(level);
            case RISE -> tickRise(level);
            case SURFACED -> tickSurfaced(level);
        }
    }

    private void tickLurk(ServerLevel level) {
        LivingEntity target = getTarget();
        boolean onIce = target != null && target.isAlive() && Math.abs(target.getY() - (iceY + 1.0)) < 1.3
                && target.distanceToSqr(getX(), target.getY(), getZ()) < 24.0 * 24.0;
        Vec3 goal;
        double speed;
        if (onIce) {
            goal = new Vec3(target.getX(), swimY(), target.getZ());
            speed = lunge().param("swim", 0.22);
        } else {
            if (--wanderTimer <= 0) {
                BlockPos home = home();
                Vec3 base = home == null ? position() : new Vec3(home.getX() + 0.5, swimY(), home.getZ() + 0.5);
                wander = base.add((random.nextDouble() - 0.5) * 14.0, 0.0, (random.nextDouble() - 0.5) * 14.0);
                wanderTimer = 60 + random.nextInt(60);
            }
            goal = new Vec3(wander.x, swimY(), wander.z);
            speed = 0.06;
        }
        Vec3 to = goal.subtract(position());
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        if (flat > 0.05) {
            double step = Math.min(speed, flat);
            Vec3 next = position().add(to.x / flat * step, 0.0, to.z / flat * step);
            if (underIce(level, next)) {
                setPos(next.x, swimY(), next.z);
                faceTowards(goal);
            }
        }
        if (onIce && flat < 1.2 && ready("lunge")) {
            startRise(level, target.position());
        }
    }

    /** そこは氷の下の水か（氷の面のブロックが湖の氷・薄氷・水の穴で、その下が水）。 */
    private boolean underIce(ServerLevel level, Vec3 at) {
        BlockPos below = BlockPos.containing(at.x, iceY - 1, at.z);
        BlockState surface = level.getBlockState(below.above());
        return level.getBlockState(below).is(Blocks.WATER)
                && (ThinIceService.breakable(surface) || surface.is(Blocks.WATER));
    }

    private void startRise(ServerLevel level, Vec3 at) {
        EnemyDef.Skill skill = lunge();
        state = State.RISE;
        strikeAt = new Vec3(at.x, iceY + 1.0, at.z);
        timer = Math.max(10, skill.windupTicks());
        setBusy(timer + 20);
        GroundMarker.place(level, strikeAt, (float) skill.param("radius", 2.2), timer, GroundMarker.KIND_CRACK);
        level.playSound(null, strikeAt.x, strikeAt.y, strikeAt.z, SoundEvents.GLASS_HIT, SoundSource.HOSTILE, 1.2F, 0.5F);
    }

    private void tickRise(ServerLevel level) {
        if (strikeAt == null) {
            state = State.LURK;
            return;
        }
        if (timer % 4 == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), strikeAt.x, strikeAt.y + 0.05,
                    strikeAt.z, 6, 1.0, 0.0, 1.0, 0.0);
        }
        if (--timer > 0) {
            return;
        }
        EnemyDef.Skill skill = lunge();
        double radius = skill.param("radius", 2.2);
        ThinIceService.breakIce(level, new Vec3(strikeAt.x, iceY + 0.5, strikeAt.z), skill.param("hole", 1.6),
                (int) Math.round(skill.param("hole_seconds", 8.0) * 20));
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(strikeAt, strikeAt).inflate(radius + 0.5, 2.0,
                radius + 0.5))) {
            if (player.position().distanceTo(new Vec3(strikeAt.x, player.getY(), strikeAt.z)) > radius + 0.3) {
                continue;
            }
            if (EnemyCombat.strike(this, player, skill.power(), null)) {
                player.push(0.0, skill.param("lift", 0.55), 0.0);
                player.hurtMarked = true;
                PlayerColdService.chill(player, 1, 80);
            }
        }
        level.sendParticles(ParticleTypes.SPLASH, strikeAt.x, strikeAt.y + 0.3, strikeAt.z, 60, 1.0, 0.4, 1.0, 0.2);
        level.playSound(null, strikeAt.x, strikeAt.y, strikeAt.z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.6F, 0.7F);
        // 跳んだあとは、穴のそばの氷の上でもがく（この間だけ叩ける）
        Vec3 land = landing(level, strikeAt, skill.param("hole", 1.6) + 1.0);
        setPos(land.x, land.y, land.z);
        setSubmerged(false);
        playAction("skill_lunge");
        state = State.SURFACED;
        timer = (int) Math.round(skill.param("beached", 4.0) * 20);
        startCooldown("lunge", skill.cooldownTicks());
    }

    /** 穴のそばの、割れていない氷の上（なければ穴の上）。 */
    private Vec3 landing(ServerLevel level, Vec3 center, double distance) {
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2.0 * k / 8 + random.nextDouble() * 0.3;
            Vec3 at = center.add(Math.cos(a) * distance, 0.0, Math.sin(a) * distance);
            BlockPos ice = BlockPos.containing(at.x, iceY, at.z);
            if (ThinIceService.breakable(level.getBlockState(ice)) && level.getBlockState(ice.above()).getCollisionShape(level, ice.above())
                    .isEmpty()) {
                return new Vec3(at.x, iceY + 1.0, at.z);
            }
        }
        return center;
    }

    private void tickSurfaced(ServerLevel level) {
        EnemyDef.Skill bite = bite();
        ServerPlayer near = null;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(bite.range()))) {
            if (player.isAlive() && !player.isCreative() && !player.isSpectator() && player.distanceTo(this) <= bite.range()) {
                near = player;
                break;
            }
        }
        if (near != null && ready("bite")) {
            faceTowards(near.position());
            playAction("attack");
            if (EnemyCombat.strike(this, near, bite.power(), null)) {
                PlayerColdService.chill(near, 1, 60);
            }
            startCooldown("bite", bite.cooldownTicks());
        }
        if (timer % 10 == 0) {
            level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.3, getZ(), 8, 0.6, 0.2, 0.6, 0.1);
        }
        if (--timer <= 0) {
            dive(level, true);
        }
    }

    /** 氷の下へ戻る（もがいていた所に小さな穴をあけて）。 */
    private void dive(ServerLevel level, boolean splash) {
        if (splash) {
            ThinIceService.breakIce(level, new Vec3(getX(), iceY + 0.5, getZ()), 1.0, 100);
            level.sendParticles(ParticleTypes.SPLASH, getX(), iceY + 1.2, getZ(), 30, 0.6, 0.3, 0.6, 0.1);
            playAction("skill_dive");
        }
        setPos(getX(), swimY(), getZ());
        setSubmerged(true);
        state = State.LURK;
        strikeAt = null;
        clearBusy();
    }

    /** 自動テスト用：今の動き（LURK・RISE・SURFACED）。 */
    public String testState() {
        return state.name();
    }

    /** 自動テスト用：すぐに、その場所へ跳び上がる準備をする。 */
    public void testStartRise(Vec3 at) {
        if (level() instanceof ServerLevel level) {
            if (iceY == Integer.MIN_VALUE) {
                findIce(level);
            }
            startRise(level, at);
        }
    }

    /** 自動テスト用：氷の面の高さ。 */
    public int testIceY() {
        return iceY;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("IceY", iceY);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("IceY")) {
            iceY = tag.getInt("IceY");
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COD_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COD_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.4F;
    }
}

package jp.amemaru.lostconnection.enemy.iceforest;

import jp.amemaru.lostconnection.combat.LostService;
import jp.amemaru.lostconnection.core.registry.ModEffects;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.ai.ChargeGoal;
import jp.amemaru.lostconnection.enemy.ai.EnemyMeleeGoal;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 写し身（7階の雑魚。Phase 0-A 9章の7階「プレイヤーの姿を真似る敵。名前が『？？？』」）。11月25日【補完】。
 *
 * <ul>
 *   <li>はじめに近づいた人（{@link #COPY_RANGE} ブロック）の姿を写す（その人のスキン。見た目は
 *       {@link jp.amemaru.lostconnection.client.render.MimicRenderer}）。名前はいつも「？？？」</li>
 *   <li>剣で斬り（slash）、少し離れると踏みこんで突く（charge）。斬られると、ロスト状態「薄れ」（味方の名前が「？？？」に見える）</li>
 *   <li>7階のボス、鏡像獣リフレクタが作る写し身（{@link #bossCopy()}）は、戦う人ひとりずつを写す</li>
 * </ul>
 */
public class Mimic extends AmemaruMob {
    public static final double COPY_RANGE = 24.0;
    private static final EntityDataAccessor<String> DATA_COPY = SynchedEntityData.defineId(Mimic.class, EntityDataSerializers.STRING);

    public Mimic(EntityType<? extends Mimic> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_COPY, "");
    }

    @Override
    public String assetName() {
        return "mimic";
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ChargeGoal(this, SoundEvents.PLAYER_ATTACK_SWEEP));
        goalSelector.addGoal(2, new EnemyMeleeGoal(this, "slash", "attack", TELEGRAPH_WINDUP, 1.3, SoundEvents.PLAYER_ATTACK_STRONG));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected String[] actionNames() {
        return new String[]{"skill_charge"};
    }

    /** 写している人（いなければ null）。 */
    @Nullable
    public UUID copied() {
        String text = entityData.get(DATA_COPY);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** その人の姿を写す。 */
    public void copy(@Nullable UUID player) {
        entityData.set(DATA_COPY, player == null ? "" : player.toString());
    }

    /** 鏡像獣リフレクタが作った写し身か（変種の定義 {@code reflecta_copy}）。 */
    public boolean bossCopy() {
        return variantId() != null && variantId().getPath().equals("reflecta_copy");
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) {
            if (copied() == null && tickCount % 10 == 0) {
                Player near = level.getNearestPlayer(this, COPY_RANGE);
                if (near != null && !near.isSpectator()) {
                    copy(near.getUUID());
                    level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.0, getZ(), 12, 0.3, 0.6, 0.3, 0.03);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, getSoundSource(), 1.0F, 1.6F);
                }
            }
        } else if (isAlive() && random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.WHITE_ASH, getRandomX(0.5), getRandomY(), getRandomZ(0.5), 0.0, 0.01, 0.0);
        }
    }

    @Override
    public void onStruck(ServerPlayer player, double coefficient) {
        EnemyDef def = def();
        double seconds = def == null ? 8.0 : def.behavior("fade_seconds", 8.0);
        LostService.inflict(player, ModEffects.LOST_FADE.get(), (int) Math.round(seconds * 20));
    }

    @Override
    public void playAction(String name) {
        super.playAction(name);
        if ("attack".equals(name) || "skill_charge".equals(name)) {
            swing(InteractionHand.MAIN_HAND, true);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("amemaru_copy", entityData.get(DATA_COPY));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_COPY, tag.getString("amemaru_copy"));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GLASS_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GLASS_BREAK;
    }
}

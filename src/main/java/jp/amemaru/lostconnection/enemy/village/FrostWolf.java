package jp.amemaru.lostconnection.enemy.village;

import jp.amemaru.lostconnection.combat.PlayerColdService;
import jp.amemaru.lostconnection.core.registry.ModEntities;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.KakekibaWolf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 霜狼（6階「凍結した村」の雑魚。Phase 0-A 9章の6階）：白く凍った毛の狼。群れで動き、噛みつきと突進（1階の欠け牙の狼と同じ動き）。
 * 噛まれると凍える（{@link PlayerColdService}。behavior.chill_stacks 段・chill_seconds 秒）。息は白く、足もとに霜がこぼれる【補完】。
 *
 * <p>名のある魔物「霜狼の群れ長」（variant frost_wolf_leader）は、HPが半分を切ると遠吠えで仲間を呼ぶ（behavior.call 匹）。</p>
 */
public class FrostWolf extends KakekibaWolf {
    private boolean called;
    /** 呼んだ仲間（自動テスト用。保存しない）。 */
    private final List<FrostWolf> pack = new ArrayList<>();

    public FrostWolf(EntityType<? extends FrostWolf> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "frost_wolf";
    }

    @Override
    public void onStruck(ServerPlayer player, double coefficient) {
        EnemyDef def = def();
        int stacks = def == null ? 1 : (int) Math.round(def.behavior("chill_stacks", 1.0));
        double seconds = def == null ? 4.0 : def.behavior("chill_seconds", 4.0);
        PlayerColdService.chill(player, stacks, (int) Math.round(seconds * 20));
    }

    @Override
    public void onDamagedBy(@Nullable Entity attacker, double amount) {
        super.onDamagedBy(attacker, amount);
        EnemyDef def = def();
        int count = def == null ? 0 : (int) Math.round(def.behavior("call", 0.0));
        if (count <= 0 || called || getEnemyHp() > getEnemyMaxHp() * 0.5 || !(level() instanceof ServerLevel level)) {
            return;
        }
        called = true;
        playAction("skill_charge");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOLF_HOWL, SoundSource.HOSTILE, 2.0F, 0.7F);
        for (int i = 0; i < count; i++) {
            FrostWolf wolf = ModEntities.FROST_WOLF.get().create(level);
            if (wolf == null) {
                continue;
            }
            double a = Math.PI * 2.0 * i / count + random.nextDouble();
            wolf.moveTo(getX() + Math.cos(a) * 4.0, getY(), getZ() + Math.sin(a) * 4.0, random.nextFloat() * 360.0F, 0.0F);
            wolf.setHome(BlockPos.containing(position()));
            wolf.setTransient(true);
            level.addFreshEntity(wolf);
            pack.add(wolf);
            ServerPlayer target = topThreat();
            if (target != null) {
                wolf.addThreat(target, 1.0);
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 16, 0.4, 0.4, 0.4, 0.03);
        }
    }

    /** 自動テスト用：仲間を呼んだか。 */
    public boolean testCalled() {
        return called;
    }

    /** 自動テスト用：呼んだ仲間。 */
    public List<FrostWolf> testPack() {
        return List.copyOf(pack);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && isAlive() && random.nextInt(5) == 0) {
            level().addParticle(ParticleTypes.SNOWFLAKE, getRandomX(0.6), getY() + 0.2, getRandomZ(0.6), 0.0, 0.01, 0.0);
        }
    }
}

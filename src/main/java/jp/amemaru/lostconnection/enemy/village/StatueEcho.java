package jp.amemaru.lostconnection.enemy.village;

import jp.amemaru.lostconnection.combat.PlayerColdService;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.temple.SilentStatue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 氷像の残響（6階の雑魚。Phase 0-A 9章の6階「村人の氷像の一部が近づくと動く」）：村のあちこちに立つ、凍った村人の氷像と同じ
 * 見た目（顔は白く抜けている）。ふだんは像のまま動かず、behavior.wake_range まで近づく・叩くと目覚める（4階の沈黙の石像と同じ
 * 動き）。拳（fist）と叩きつけ（quake）。叩きつけに当たると凍える。誰もいなくなると、元の場所で像に戻る【補完】。
 *
 * <p>見た目は {@link jp.amemaru.lostconnection.client.render.StatueEchoRenderer}（凍った村人の氷像と同じ形。どの村人かは
 * 生まれたときに決まる）。</p>
 */
public class StatueEcho extends SilentStatue {
    /** 見た目に使う村人（氷像の名前。{@code textures/entity/village/<名前>_statue.png}）。 */
    public static final String[] SKINS = {"man", "woman", "old_man", "child_boy", "child_girl", "merchant", "buyer"};

    public StatueEcho(EntityType<? extends StatueEcho> type, Level level) {
        super(type, level);
    }

    @Override
    public String assetName() {
        return "statue_echo";
    }

    /** どの村人の姿か（ID から決まる。クライアントとサーバーで同じ）。 */
    public String skin() {
        return SKINS[Math.floorMod(getUUID().hashCode(), SKINS.length)];
    }

    @Override
    protected BlockState debris() {
        return Blocks.PACKED_ICE.defaultBlockState();
    }

    @Override
    protected void onQuakeHit(ServerPlayer player) {
        EnemyDef def = def();
        PlayerColdService.chill(player, def == null ? 2 : (int) Math.round(def.behavior("chill_stacks", 2.0)),
                (int) Math.round((def == null ? 5.0 : def.behavior("chill_seconds", 5.0)) * 20));
    }

    @Override
    public void onStruck(ServerPlayer player, double coefficient) {
        if (!dormant()) {
            swing(InteractionHand.MAIN_HAND, true);
        }
    }

    @Override
    public void playAction(String name) {
        super.playAction(name);
        if ("attack".equals(name) || "skill_quake".equals(name)) {
            swing("skill_quake".equals(name) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, true);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return dormant() ? null : SoundEvents.AMETHYST_BLOCK_CHIME;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GLASS_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GLASS_BREAK;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.GLASS_STEP, 0.4F, 0.8F);
    }
}

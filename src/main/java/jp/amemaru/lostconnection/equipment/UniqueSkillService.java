package jp.amemaru.lostconnection.equipment;

import jp.amemaru.lostconnection.core.config.ServerConfig;
import jp.amemaru.lostconnection.job.SkillContext;
import jp.amemaru.lostconnection.job.SkillExecutor;
import jp.amemaru.lostconnection.job.SkillService;
import jp.amemaru.lostconnection.job.SkillTargeting;
import jp.amemaru.lostconnection.job.def.SkillDef;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.SkillCooldown;
import jp.amemaru.lostconnection.core.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * ユニーク武器の固有スキルを使う（11月28日【補完】。{@link UniqueSkillDef}）。
 *
 * <ol>
 *   <li>手に持っている武器に固有スキルがあるか、使える武器か（必要レベル・職業・壊れていないか）を確かめる。</li>
 *   <li>詠唱中でないか・クールダウン・MPを確かめ、狙い（相手・地点）を決める。</li>
 *   <li>MPを使い、クールダウンを始め、職業のスキルと同じ処理（{@link SkillExecutor#deliver}）で効果と演出を出す。</li>
 * </ol>
 * <p>職業のスキルとちがい、連撃（モンク）・連携支援・覚醒の「もう一度」には数えない（武器そのものの技のため）。
 * クールダウンはスキルと同じ所（{@link PlayerData#setCooldown}）に入れ、同期で画面に届く。</p>
 */
public final class UniqueSkillService {
    private UniqueSkillService() {
    }

    /**
     * 固有スキルを使う。
     *
     * @param entityAim クライアントが狙った相手（ないときは -1）
     * @param pointAim  クライアントが狙った地点（ないときは null）
     */
    public static SkillService.Result tryUse(ServerPlayer player, PlayerData data, int entityAim, @Nullable Vec3 pointAim) {
        long now = player.level().getGameTime();
        if (!data.allowSkillRequest(now, ServerConfig.SKILL_REQUESTS_PER_SECOND.get())) {
            return new SkillService.Result(SkillService.Failure.SPAM);
        }
        if (!player.isAlive() || player.isSpectator()) {
            return new SkillService.Result(SkillService.Failure.DEAD);
        }
        if (data.down().isDown()) {
            return fail(player, SkillService.Failure.DOWN);
        }
        if (!data.isStatsReady()) {
            LevelService.recompute(player, data);
            if (!data.isStatsReady()) {
                return new SkillService.Result(SkillService.Failure.NOT_READY);
            }
        }
        ItemStack held = player.getMainHandItem();
        UniqueSkillDef def = UniqueSkills.forStack(held);
        if (def == null) {
            return failText(player, Component.translatable("message.amemaru.unique.no_weapon"), SkillService.Failure.NO_WEAPON);
        }
        EquipmentDef equipment = EquipmentDefs.server().get(held);
        if (equipment != null && !GearService.usable(data, equipment)) {
            return failText(player, Component.translatable("message.amemaru.unique.unusable", held.getHoverName()), SkillService.Failure.NO_WEAPON);
        }
        if (equipment != null && GearData.isBroken(held, equipment)) {
            return failText(player, Component.translatable("message.amemaru.unique.broken", held.getHoverName()), SkillService.Failure.NO_WEAPON);
        }
        if (data.getCasting() != null) {
            return fail(player, SkillService.Failure.CASTING);
        }
        SkillDef skill = def.skill();
        SkillCooldown cooldown = data.getCooldown(skill.id());
        if (cooldown != null && cooldown.endTick() > now) {
            player.displayClientMessage(Component.translatable(SkillService.Failure.COOLDOWN.key(), skill.name(),
                    String.format(Locale.ROOT, "%.1f", cooldown.remaining(now) / 20.0)).withStyle(ChatFormatting.RED), true);
            return new SkillService.Result(SkillService.Failure.COOLDOWN);
        }
        if (data.getMp() + 1.0E-6 < skill.mpCost(1)) {
            return fail(player, SkillService.Failure.NO_MP);
        }
        SkillTargeting.Aim aim = SkillTargeting.resolve(player, skill, 1, entityAim, pointAim);
        if (aim.failure() != null) {
            return fail(player, aim.failure());
        }

        data.setMp(data.getMp() - skill.mpCost(1));
        int ticks = SkillService.cooldownTicks(data, skill, 1, now);
        if (ticks > 0) {
            data.setCooldown(skill.id(), new SkillCooldown(now + ticks, ticks));
        }
        SkillContext ctx = new SkillContext(player, data.getStats(), skill, UniqueSkills.index(def), 1).loadPassives(data);
        // 固有スキルの合図の音（武器が目を覚ます音）を、定義の音に重ねる
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.get("unique.awaken"), SoundSource.PLAYERS,
                0.9F, 1.0F);
        SkillExecutor.deliver(ctx, player, aim);
        GearService.wearWeapon(player, true);
        player.displayClientMessage(Component.translatable("message.amemaru.unique.used", skill.name())
                .withStyle(style -> style.withColor(def.color())), true);
        return new SkillService.Result(null);
    }

    /** 職業のスキルと同じ「使えなかった」の知らせ。 */
    private static SkillService.Result fail(ServerPlayer player, SkillService.Failure failure) {
        if (failure.shown()) {
            player.displayClientMessage(Component.translatable(failure.key()).withStyle(ChatFormatting.RED), true);
            player.playNotifySound(ModSounds.get("skill.fail"), SoundSource.PLAYERS, 0.6F, 1.0F);
        }
        return new SkillService.Result(failure);
    }

    private static SkillService.Result failText(ServerPlayer player, Component text, SkillService.Failure failure) {
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.RED), true);
        player.playNotifySound(ModSounds.get("skill.fail"), SoundSource.PLAYERS, 0.6F, 1.0F);
        return new SkillService.Result(failure);
    }

    /** 手に持っている武器の固有スキルの、クールダウンの残り（tick。なければ 0）。テストで使う。 */
    public static int cooldownRemaining(ServerPlayer player, PlayerData data) {
        UniqueSkillDef def = UniqueSkills.forStack(player.getMainHandItem());
        if (def == null) {
            return 0;
        }
        SkillCooldown cooldown = data.getCooldown(def.skill().id());
        return cooldown == null ? 0 : cooldown.remaining(player.level().getGameTime());
    }
}

package jp.amemaru.lostconnection.job;

import jp.amemaru.lostconnection.core.registry.ModSounds;
import jp.amemaru.lostconnection.item.ResetItem;
import jp.amemaru.lostconnection.player.LevelService;
import jp.amemaru.lostconnection.player.PlayerData;
import jp.amemaru.lostconnection.player.PlayerDataCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * 忘れ水（ステータスの振り分けを戻す）と白紙の誓約書（スキルの習得を戻す）を使う。
 *
 * <p>間違えて使わないよう、1回目は確認だけ出し、3秒以内にもう一度使うと実行する。</p>
 */
public final class ResetService {
    private static final int CONFIRM_TICKS = 60;

    private ResetService() {
    }

    /**
     * アイテムを使う。
     *
     * @return 実行した（アイテムを1つ減らす）か
     */
    public static boolean useItem(ServerPlayer player, ResetItem.Kind kind) {
        PlayerData data = PlayerDataCapability.get(player).orElse(null);
        if (data == null) {
            return false;
        }
        long now = player.level().getGameTime();
        switch (kind) {
            case STATS, SKILLS -> {
                // 11月24日：ステータスもスキルツリーで振るので、忘却の水も白紙の誓約書も、スキルツリーをすべて戻す
                if (!data.hasCombatJob()) {
                    SkillTreeService.tell(player, Component.translatable("message.amemaru.reset.skills.no_job"));
                    return false;
                }
                int spent = SkillTreeService.spentPoints(data);
                if (spent <= 0) {
                    SkillTreeService.tell(player, Component.translatable("message.amemaru.reset.skills.nothing"));
                    return false;
                }
                if (!data.confirm("reset_skills", now, CONFIRM_TICKS)) {
                    player.displayClientMessage(Component.translatable("message.amemaru.reset.skills.confirm", spent)
                            .withStyle(ChatFormatting.YELLOW), true);
                    return false;
                }
                SkillTreeService.resetSkills(player, data);
                player.displayClientMessage(Component.translatable("message.amemaru.reset.skills.done", spent)
                        .withStyle(ChatFormatting.AQUA), false);
            }
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.get("item.reset"),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        player.serverLevel().sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(),
                40, 0.4, 0.8, 0.4, 0.4);
        return true;
    }
}

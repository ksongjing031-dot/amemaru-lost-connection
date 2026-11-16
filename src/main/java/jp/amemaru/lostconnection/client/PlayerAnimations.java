package jp.amemaru.lostconnection.client;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * プレイヤーのモーション（playerAnimator。Phase 0-B 1章）。
 *
 * <p>モーションは {@code assets/amemaru/player_animation/*.json}（GeckoLibの書き出しの形）に置き、アニメーションの名前
 * （例：{@code knight_charge_slash}）で呼ぶ。動かす部位（ボーン）は head・torso・right_arm・left_arm・right_leg・left_leg・body。
 * 書いていない部位は、ふだんの動き（歩く・腕を振る）のままになる。</p>
 */
public final class PlayerAnimations {
    /** プレイヤーごとのモーションの層の名前。 */
    private static final ResourceLocation LAYER = AmemaruMod.id("skill");
    /** ほかのModのモーションより手前に出す。 */
    private static final int PRIORITY = 1000;

    private PlayerAnimations() {
    }

    /** クライアントの準備のときに1回呼ぶ。 */
    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> new ModifierLayer<>());
    }

    /**
     * モーションを始める。
     *
     * @param name アニメーションの名前（空なら何もしない）
     * @return 始めたか（モーションのファイルがないときは false）
     */
    public static boolean play(@Nullable AbstractClientPlayer player, String name) {
        if (player == null || name == null || name.isEmpty()) {
            return false;
        }
        KeyframeAnimation animation = PlayerAnimationRegistry.getAnimation(AmemaruMod.id(name));
        ModifierLayer<IAnimation> layer = layer(player);
        if (animation == null || layer == null) {
            return false;
        }
        KeyframeAnimationPlayer playing = new KeyframeAnimationPlayer(animation);
        if (player == Minecraft.getInstance().player) {
            // 一人称でも、腕の動きが見えるようにする
            playing.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
            playing.setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
        }
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(3, Ease.INOUTSINE), playing);
        return true;
    }

    /** モーションを止める（だんだん元に戻す）。 */
    public static void stop(@Nullable AbstractClientPlayer player) {
        ModifierLayer<IAnimation> layer = player == null ? null : layer(player);
        if (layer != null && layer.isActive()) {
            layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(4, Ease.INOUTSINE), null);
        }
    }

    /** そのモーションのファイルがあるか（素材の確かめ用）。 */
    public static boolean exists(String name) {
        return name != null && !name.isEmpty() && PlayerAnimationRegistry.getAnimation(AmemaruMod.id(name)) != null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static ModifierLayer<IAnimation> layer(AbstractClientPlayer player) {
        try {
            IAnimation animation = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
            return animation instanceof ModifierLayer<?> layer ? (ModifierLayer<IAnimation>) layer : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

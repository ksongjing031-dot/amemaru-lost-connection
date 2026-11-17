package jp.amemaru.lostconnection.item;

import jp.amemaru.lostconnection.client.render.GeoGearRenderers;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * GeckoLibで描く武器（動く・光る部分がある武器。Phase 0-B 12章「武器（動く・光る）」）。
 *
 * <p>見た目のファイルはアイテムのIDの名前で置く：{@code geo/item/<名前>.geo.json}・
 * {@code animations/item/<名前>.animation.json}・{@code textures/item/<名前>.png}（光る部分は
 * {@code textures/item/<名前>_glowmask.png}）。アニメーションは {@code animation.<名前>.idle}（いつも）と、
 * 攻撃・スキルのたびに {@code animation.<名前>.use}（Phase 0-B 12章の名前の決まり）を流す。</p>
 */
public class GeoWeaponItem extends AmemaruWeaponItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private RawAnimation idle;

    public GeoWeaponItem(WeaponKind kind, Properties properties) {
        super(kind, properties);
        // サーバーから「使った」のアニメーションを流すため（まわりの人の画面にも届く）
        GeoItem.registerSyncedAnimatable(this);
    }

    /** 手に持った動く武器の「使った」のアニメーションを流す（攻撃が当たった・スキルを使ったとき。サーバーから）。 */
    public static void playUse(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof GeoWeaponItem item) {
            item.triggerAnim(player, GeoItem.getOrAssignId(stack, player.serverLevel()), "use", "use");
        }
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = GeoGearRenderers.weapon(GeoWeaponItem.this);
                }
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> {
            if (idle == null) {
                idle = RawAnimation.begin().thenLoop("animation." + name() + ".idle");
            }
            return state.setAndContinue(idle);
        }));
        AnimationController<GeoWeaponItem> use = new AnimationController<>(this, "use", 0, state -> PlayState.STOP);
        use.triggerableAnim("use", RawAnimation.begin().thenPlay("animation." + name() + ".use"));
        controllers.add(use);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** 見た目のファイルの名前（アイテムのIDのパス）。 */
    public String name() {
        return gearId() == null ? "unknown" : gearId().getPath();
    }
}

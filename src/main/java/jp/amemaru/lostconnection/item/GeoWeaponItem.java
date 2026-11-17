package jp.amemaru.lostconnection.item;

import jp.amemaru.lostconnection.client.render.GeoGearRenderers;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * GeckoLibで描く武器（動く・光る部分がある武器。Phase 0-B 12章「武器（動く・光る）」）。
 *
 * <p>見た目のファイルはアイテムのIDの名前で置く：{@code geo/item/<名前>.geo.json}・
 * {@code animations/item/<名前>.animation.json}・{@code textures/item/<名前>.png}（光る部分は
 * {@code textures/item/<名前>_glowmask.png}）。アニメーションは {@code animation.<名前>.idle}（いつも）を流す。</p>
 */
public class GeoWeaponItem extends AmemaruWeaponItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private RawAnimation idle;

    public GeoWeaponItem(WeaponKind kind, Properties properties) {
        super(kind, properties);
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

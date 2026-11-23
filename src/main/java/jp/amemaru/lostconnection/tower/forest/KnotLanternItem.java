package jp.amemaru.lostconnection.tower.forest;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 結び目の灯（Phase 8 その1。2階「忘れられた森」のギミック。Phase 0-A 9章「正しい道に『結び目の灯』を置いていかないと
 * 同じ場所に戻される」）。迷いの森の鐘を鳴らすともらえる。正しい出口の灯台に置くと、その区画の道がつながる
 * （{@link LostWoodsService}）。階の外へは持ち出せない（塔の門へ戻ると消える）【補完】。
 */
public class KnotLanternItem extends Item {
    public KnotLanternItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.amemaru.knot_lantern").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}

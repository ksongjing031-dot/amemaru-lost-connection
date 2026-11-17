package jp.amemaru.lostconnection.equipment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * このModの装備（武器・盾・防具）のアイテム。数値は {@link EquipmentDef}（データパック）から読み、
 * 一つひとつの品の抽選の結果・強化・耐久などは {@link GearData}（アイテムのNBT）に持つ。
 */
public interface GearItem {
    /** 装備する場所。 */
    GearSlot gearSlot();

    /** 両手で持つ武器か（持っている間、もう片方の手の盾は効かない）。 */
    default boolean isTwoHanded() {
        return false;
    }

    /** 定義のID（アイテムのIDと同じ）。 */
    @Nullable
    default ResourceLocation gearId() {
        return ForgeRegistries.ITEMS.getKey((Item) this);
    }
}

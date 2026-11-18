package jp.amemaru.lostconnection.workshop;

import jp.amemaru.lostconnection.accessory.AccessoryItem;
import jp.amemaru.lostconnection.equipment.GearItem;
import jp.amemaru.lostconnection.equipment.GearSlot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * 工房の系統（Phase 0-A 9章：武器工房・防具工房・装飾工房。生産職の系統ごとに分かれる）。
 *
 * <ul>
 *   <li>武器工房：武器職人が武器を作る。武器の強化・修理</li>
 *   <li>防具工房：防具職人が防具と盾を作る。防具と盾の強化・修理</li>
 *   <li>装飾工房：装飾師がアクセサリーを作る（厳選はPhase 6）</li>
 * </ul>
 */
public enum WorkshopType {
    WEAPON("weapon"),
    ARMOR("armor"),
    ACCESSORY("accessory");

    private final String id;

    WorkshopType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "workshop.amemaru." + id;
    }

    /** この工房で強化・修理できる品か。 */
    public boolean accepts(ItemStack stack) {
        if (stack.getItem() instanceof GearItem gear) {
            return this == WEAPON ? gear.gearSlot() == GearSlot.WEAPON : this == ARMOR && gear.gearSlot() != GearSlot.WEAPON;
        }
        return this == ACCESSORY && stack.getItem() instanceof AccessoryItem;
    }

    @Nullable
    public static WorkshopType byId(String id) {
        for (WorkshopType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }

    public static WorkshopType byIndex(int index) {
        WorkshopType[] values = values();
        return index >= 0 && index < values.length ? values[index] : WEAPON;
    }
}

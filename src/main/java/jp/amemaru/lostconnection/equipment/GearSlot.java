package jp.amemaru.lostconnection.equipment;

import net.minecraft.world.entity.EquipmentSlot;

import javax.annotation.Nullable;

/**
 * 装備の場所（武器・もう片方の手・頭・胴・脚・足）。アクセサリーの枠は {@code AccessorySlot}。
 */
public enum GearSlot {
    WEAPON("weapon", EquipmentSlot.MAINHAND),
    OFFHAND("offhand", EquipmentSlot.OFFHAND),
    HEAD("head", EquipmentSlot.HEAD),
    CHEST("chest", EquipmentSlot.CHEST),
    LEGS("legs", EquipmentSlot.LEGS),
    FEET("feet", EquipmentSlot.FEET);

    private static final GearSlot[] VALUES = values();

    private final String id;
    private final EquipmentSlot vanilla;

    GearSlot(String id, EquipmentSlot vanilla) {
        this.id = id;
        this.vanilla = vanilla;
    }

    public String id() {
        return id;
    }

    /** バニラの装備の場所。 */
    public EquipmentSlot vanilla() {
        return vanilla;
    }

    /** 言語ファイルのキー（「片手剣」などの種類は武器の種類で出すので、ここは防具の場所の名前）。 */
    public String translationKey() {
        return "gear_slot.amemaru." + id;
    }

    /** 防具の場所か。 */
    public boolean isArmor() {
        return vanilla.getType() == EquipmentSlot.Type.ARMOR;
    }

    @Nullable
    public static GearSlot byId(String id) {
        for (GearSlot slot : VALUES) {
            if (slot.id.equals(id)) {
                return slot;
            }
        }
        return null;
    }

    public static GearSlot byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : WEAPON;
    }

    @Nullable
    public static GearSlot of(EquipmentSlot slot) {
        for (GearSlot gear : VALUES) {
            if (gear.vanilla == slot) {
                return gear;
            }
        }
        return null;
    }
}

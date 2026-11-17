package jp.amemaru.lostconnection.accessory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.equipment.SpecialEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.UUID;

/**
 * アクセサリー（指輪・首飾り・腕輪・お守り・留め具。Phase 0-A 21章）。枠の入れ物は Curios API。
 *
 * <p>メインステータスとサブステータスは、作ったとき・落ちたときに抽選して、NBTに持つ（{@link AccessoryData}）。
 * 品質★4はアイコンがうっすら光り（エンチャントの光沢）、★5は虹色にきらめく（{@code AccessoryDecorator}）。</p>
 *
 * <p>体に表示するアクセサリー（首飾り・腕輪・お守り・留め具）は GeckoLib のモデルで描く：
 * {@code geo/accessory/<名前>.geo.json}・{@code textures/accessory/<名前>.png}。防具と同じボーンの名前
 * （armorBody・armorRightArm など）にパーツを入れると、その体の部分について動く。</p>
 */
public class AccessoryItem extends Item implements ICurioItem, GeoItem {
    private static final UUID SPEED_UUID = UUID.fromString("5a0e7d64-8a0e-4f7c-9a76-2f2b1b0b6e01");

    private final AccessorySlot slot;
    private final boolean visibleOnBody;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private RawAnimation idle;

    public AccessoryItem(AccessorySlot slot, boolean visibleOnBody, Properties properties) {
        super(properties.stacksTo(1));
        this.slot = slot;
        this.visibleOnBody = visibleOnBody && slot.shownOnBody();
    }

    public AccessorySlot slot() {
        return slot;
    }

    /** 体に表示するモデルがあるか。 */
    public boolean visibleOnBody() {
        return visibleOnBody;
    }

    /** 見た目のファイルの名前（アイテムのIDのパス）。 */
    public String assetName() {
        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(this);
        return id == null ? "unknown" : id.getPath();
    }

    // ---------------------------------------------------------------- Curios

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return slotContext.identifier().equals(slot.curiosId());
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext slotContext, ItemStack stack) {
        return new ICurio.SoundInfo(SoundEvents.ARMOR_EQUIP_CHAIN, 1.0F, 1.2F);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        AccessoryService.onChanged(slotContext.entity());
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        AccessoryService.onChanged(slotContext.entity());
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!slotContext.entity().level().isClientSide() && slotContext.entity().tickCount % 20 == 0) {
            AccessoryService.ensureRolled(stack, slotContext.entity().getRandom());
        }
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = HashMultimap.create();
        AccessoryDef def = EffectiveSide.get().isServer() ? AccessoryDefs.get(stack) : null;
        if (def != null) {
            double speed = 0.0;
            for (SpecialEffect effect : def.effects()) {
                if (effect.type() == SpecialEffect.Type.MOVE_SPEED) {
                    speed += effect.percent();
                }
            }
            if (speed > 0) {
                map.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(uuid, "amemaru:accessory_speed",
                        speed / 100.0, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }
        return map;
    }

    @Override
    public List<Component> getSlotsTooltip(List<Component> tooltips, ItemStack stack) {
        // 枠の名前はこのModの説明文で出す
        return List.of();
    }

    @Override
    public List<Component> getAttributesTooltip(List<Component> tooltips, ItemStack stack) {
        return List.of();
    }

    // ---------------------------------------------------------------- アイテム

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotIndex, boolean selected) {
        if (!level.isClientSide() && entity instanceof ServerPlayer) {
            AccessoryService.ensureRolled(stack, level.getRandom());
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        Rarity rarity = AccessoryData.rarity(stack, Rarity.UNCOMMON);
        return rarity.paint(super.getName(stack).copy());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // 品質★4以上はアイコンがうっすら光る（★5は、さらに虹色のきらめきを重ねる）
        return AccessoryData.storedQuality(stack) >= 4 || super.isFoil(stack);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    // ---------------------------------------------------------------- GeckoLib（体に表示するモデル）

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> {
            if (idle == null) {
                idle = RawAnimation.begin().thenLoop("animation." + assetName() + ".idle");
            }
            return state.setAndContinue(idle);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}

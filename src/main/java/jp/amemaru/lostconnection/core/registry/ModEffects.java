package jp.amemaru.lostconnection.core.registry;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.effect.AmemaruEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 状態（MobEffect）の登録（Phase 0-B 4.4「状態異常とロスト状態は、バニラの効果として登録する」）。
 *
 * <p>アイコンは {@code textures/mob_effect/<名前>.png}（18×18）。</p>
 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, AmemaruMod.MOD_ID);

    /** よろめき：足が止まり、大きく遅くなる。 */
    public static final RegistryObject<MobEffect> STAGGER = EFFECTS.register("stagger", () ->
            new AmemaruEffect(MobEffectCategory.HARMFUL, 0xE0B04A, true)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "8d5b2c1e-3f47-4a6b-9c0d-1e2f3a4b5c61", -0.6,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));

    /** 凍え：段階1つにつき10%遅くなる。 */
    public static final RegistryObject<MobEffect> FROST = EFFECTS.register("frost", () ->
            new AmemaruEffect(MobEffectCategory.HARMFUL, 0x9FE6FF, false)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "2c7e4f90-1a3b-4c5d-8e6f-7a8b9c0d1e22", -0.1,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));

    /** 城壁の構え：正面からのダメージが減る。足は遅くなる。 */
    public static final RegistryObject<MobEffect> RAMPART = EFFECTS.register("rampart", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0x6EA8FF, false)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "5a1d3e7c-9b2f-4e8a-a6c4-0d2e4f6a8b13", -0.3,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));

    /** 不屈の誓い：HPが1より下がらない。 */
    public static final RegistryObject<MobEffect> UNYIELDING = EFFECTS.register("unyielding", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0xFFC94D, false));

    /** 盾結び：ナイトに守られている。 */
    public static final RegistryObject<MobEffect> SHIELD_BOND = EFFECTS.register("shield_bond", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0x8FD3FF, false));

    /** 不落の城：受けるダメージが減る。 */
    public static final RegistryObject<MobEffect> FORTRESS = EFFECTS.register("fortress", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0xE8F4FF, false));

    /** 連携支援：次のスキルの効果が上がる。 */
    public static final RegistryObject<MobEffect> LINK_SUPPORT = EFFECTS.register("link_support", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0xFFE066, false));

    /** 詠唱中：足が遅くなる。 */
    public static final RegistryObject<MobEffect> CHANTING = EFFECTS.register("chanting", () ->
            new AmemaruEffect(MobEffectCategory.NEUTRAL, 0xB57CFF, false)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "e3b0c442-98fc-4c14-9afb-f4c8996fb924", -0.5,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));

    private ModEffects() {
    }
}

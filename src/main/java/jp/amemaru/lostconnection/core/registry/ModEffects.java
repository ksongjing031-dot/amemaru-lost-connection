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

    /**
     * 毒よけ（Phase 8 その1。薬屋の解毒薬）：しばらく毒にならない。プレイヤーがかかる毒（2階のポコダケの胞子）は、
     * 敵にかける毒と同じ {@link #POISON} のアイコンを使い、減り方は {@link jp.amemaru.lostconnection.combat.PoisonService}。
     */
    public static final RegistryObject<MobEffect> ANTIDOTE = EFFECTS.register("antidote", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0xD8F0A0, false));

    /** 凍結：凍えが5つ重なると、しばらく動けず、攻撃もできない。 */
    public static final RegistryObject<MobEffect> FROZEN = EFFECTS.register("frozen", () ->
            new AmemaruEffect(MobEffectCategory.HARMFUL, 0xBFF2FF, true).freezing()
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "6f1d9c2a-4b3e-4d7f-9a1c-2e5b8d0f3a47", -1.0,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));

    /** 感電：次の雷鎖が、跳ぶ回数を増やして強く流れる。 */
    public static final RegistryObject<MobEffect> SHOCK = EFFECTS.register("shock", () ->
            new AmemaruEffect(MobEffectCategory.HARMFUL, 0xFFE27A, false));

    /** 星痕：星の印（最大5つ）。星爆で一度に弾ける。 */
    public static final RegistryObject<MobEffect> STAR_BRAND = EFFECTS.register("star_brand", () ->
            new AmemaruEffect(MobEffectCategory.HARMFUL, 0xFFD27A, false));

    /** 防御崩し：防御が下がる（{@code StatusService#ARMOR_BREAK_DEFENSE}）。 */
    public static final RegistryObject<MobEffect> ARMOR_BREAK = EFFECTS.register("armor_break", () ->
            new AmemaruEffect(MobEffectCategory.HARMFUL, 0xFF8C40, false));

    /** 光の帳：ダメージを吸う障壁（量はサーバーのプレイヤーデータ）。 */
    public static final RegistryObject<MobEffect> BARRIER = EFFECTS.register("barrier", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0xFFF2A8, false));

    /** 誓いの円陣：陣の中にいる間、受けるダメージが減る。 */
    public static final RegistryObject<MobEffect> WARD = EFFECTS.register("ward", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0x8FD3FF, false));

    /** 受け流しの構え：正面からの攻撃を無効にして反撃する。 */
    public static final RegistryObject<MobEffect> PARRY = EFFECTS.register("parry", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0xD6F6FF, false));

    /** 溜め：足が少し遅くなる。 */
    public static final RegistryObject<MobEffect> CHARGING = EFFECTS.register("charging", () ->
            new AmemaruEffect(MobEffectCategory.NEUTRAL, 0xFFB46E, false)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "b2e4c6a8-1d3f-4a5b-8c7d-9e0f1a2b3c4d", -0.35,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));

    /** 詠唱中：足が遅くなる。 */
    public static final RegistryObject<MobEffect> CHANTING = EFFECTS.register("chanting", () ->
            new AmemaruEffect(MobEffectCategory.NEUTRAL, 0xB57CFF, false)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "e3b0c442-98fc-4c14-9afb-f4c8996fb924", -0.5,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));

    /**
     * 衰弱（Phase 4）：教会の呼び戻しの儀で戻った後、しばらくステータスが下がる（Phase 0-A 20章：60秒・−10%。
     * 下がる量は {@code DownHandler}）。牛乳では消えない。
     */
    public static final RegistryObject<MobEffect> WEAKENED = EFFECTS.register("weakened", () ->
            new AmemaruEffect(MobEffectCategory.HARMFUL, 0x8C90B0, false) {
                @Override
                public java.util.List<net.minecraft.world.item.ItemStack> getCurativeItems() {
                    return new java.util.ArrayList<>();
                }
            });

    // ---------------------------------------------------------------- Phase 6 その2：敵にかかる状態

    /** 毒：重なった数だけ、毎秒ダメージを受ける（数は {@code StatusService}）。 */
    public static final RegistryObject<MobEffect> POISON = EFFECTS.register("poison", () ->
            new AmemaruEffect(MobEffectCategory.HARMFUL, 0x8CE65A, false));
    /** 標：プレイヤーから受けるダメージが上がる。 */
    public static final RegistryObject<MobEffect> HUNTER_MARK = EFFECTS.register("hunter_mark", () ->
            new AmemaruEffect(MobEffectCategory.HARMFUL, 0x9FD8FF, false));
    /** 縫い止め・絡め糸：その場から動けない（攻撃はできる）。 */
    public static final RegistryObject<MobEffect> ROOTED = EFFECTS.register("rooted", () ->
            new AmemaruEffect(MobEffectCategory.HARMFUL, 0xC8B4FF, true)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "3f0c7a52-9e14-4b6d-8a2f-5c1d7e9b0a36", -1.0,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));
    /** 戒め：攻撃が弱まる（割合は {@code StatusService}）。 */
    public static final RegistryObject<MobEffect> ADMONISHED = EFFECTS.register("admonished", () ->
            new AmemaruEffect(MobEffectCategory.HARMFUL, 0xFFE08A, false));
    /** 煙：攻撃が外れやすい（割合は {@code StatusService}）。 */
    public static final RegistryObject<MobEffect> BLINDED = EFFECTS.register("blinded", () ->
            new AmemaruEffect(MobEffectCategory.HARMFUL, 0x9AA0A8, false));

    // ---------------------------------------------------------------- Phase 6 その2：プレイヤーの強化（アイコンと残り時間）

    /** 加護の灯：防御が上がる。 */
    public static final RegistryObject<MobEffect> BLESSING = EFFECTS.register("blessing", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0xFFE08A, false));
    /** 守り：受けるダメージが減る。 */
    public static final RegistryObject<MobEffect> PROTECTED = EFFECTS.register("protected", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0xF4F0C8, false));
    /** 鷹の眼差し・気功：会心が上がる。 */
    public static final RegistryObject<MobEffect> KEEN = EFFECTS.register("keen", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0xFFD27A, false));
    /** 足が速くなる（強さ1つにつき +5%）。 */
    public static final RegistryObject<MobEffect> SWIFT = EFFECTS.register("swift", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0xB8F0FF, false)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "6b2e9d14-7c3a-4f58-b1e6-2a9c0d4e7f81", 0.05,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));
    /** 百烈・結：次のスキルが強くなる。 */
    public static final RegistryObject<MobEffect> EMPOWERED = EFFECTS.register("empowered", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0xFFB46E, false));
    /** 狙撃の構え：次の矢が強くなる。足は遅くなる。 */
    public static final RegistryObject<MobEffect> AIMING = EFFECTS.register("aiming", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0x9FD8FF, false)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "1d8f3b6a-2e4c-4a7d-9f05-8b3c6e1a2d94", -0.5,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));
    /** 命の糸：つながっている。 */
    public static final RegistryObject<MobEffect> LIFE_THREAD = EFFECTS.register("life_thread", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0x8CF0B4, false));
    /** 身代わりの鈴：倒れるほどの攻撃を一度だけ防ぐ。 */
    public static final RegistryObject<MobEffect> GUARDIAN_BELL = EFFECTS.register("guardian_bell", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0xFFF0A0, false));
    /** 星の環：星が巡っている。 */
    public static final RegistryObject<MobEffect> STAR_RING = EFFECTS.register("star_ring", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0xFFD27A, false));
    /** 影分身：影が共に斬る。 */
    public static final RegistryObject<MobEffect> SHADOW_CLONE = EFFECTS.register("shadow_clone", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0x7A5CC8, false));
    /** 毒塗り：攻撃が毒を重ねる。 */
    public static final RegistryObject<MobEffect> VENOM_COAT = EFFECTS.register("venom_coat", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0x8CE65A, false));
    /** 疾風の弓舞：足が速くなる（強さ1つにつき +5%）。 */
    public static final RegistryObject<MobEffect> BOW_DANCE = EFFECTS.register("bow_dance", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0xB8FFD8, false)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "a4c1e7f2-5b3d-4e69-8c0a-3f7b2d9e1c58", 0.05,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));
    /** 影走り：敵の攻撃をすり抜ける。 */
    public static final RegistryObject<MobEffect> EVADE = EFFECTS.register("evade", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0x5A4A8C, false));
    /** 天地流転：連撃が消えない。 */
    public static final RegistryObject<MobEffect> ENDLESS_FLOW = EFFECTS.register("endless_flow", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0x7FD6FF, false));
    /** 気炎・魔力集中・鬨の声など（11月24日）：しばらく与えるダメージが上がる。 */
    public static final RegistryObject<MobEffect> POWER_UP = EFFECTS.register("power_up", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0xFF8A3D, false));
    /** 余韻など：HPが戻り続ける。 */
    public static final RegistryObject<MobEffect> RENEWAL = EFFECTS.register("renewal", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0x8CF0B4, false));
    /** 潜伏：敵に見失われている。 */
    public static final RegistryObject<MobEffect> HIDDEN = EFFECTS.register("hidden", () ->
            new AmemaruEffect(MobEffectCategory.BENEFICIAL, 0x50506A, false));

    private ModEffects() {
    }
}

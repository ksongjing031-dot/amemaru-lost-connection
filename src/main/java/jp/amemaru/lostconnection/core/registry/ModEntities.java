package jp.amemaru.lostconnection.core.registry;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.enemy.AmemaruMob;
import jp.amemaru.lostconnection.enemy.CrystalBee;
import jp.amemaru.lostconnection.enemy.IshikureSoldier;
import jp.amemaru.lostconnection.enemy.KakekibaWolf;
import jp.amemaru.lostconnection.enemy.Tsunomofu;
import jp.amemaru.lostconnection.entity.CrystalNeedle;
import jp.amemaru.lostconnection.entity.LampBolt;
import jp.amemaru.lostconnection.entity.TrainingDummy;
import jp.amemaru.lostconnection.loot.LootItemEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** エンティティの登録。 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AmemaruMod.MOD_ID);

    /** 訓練人形。 */
    public static final RegistryObject<EntityType<TrainingDummy>> TRAINING_DUMMY = ENTITIES.register("training_dummy", () ->
            EntityType.Builder.<TrainingDummy>of(TrainingDummy::new, MobCategory.MISC)
                    .sized(0.6F, 1.9F)
                    .clientTrackingRange(10)
                    .build(AmemaruMod.id("training_dummy").toString()));

    /** 灯火弾などのスキルの弾。 */
    public static final RegistryObject<EntityType<LampBolt>> LAMP_BOLT = ENTITIES.register("lamp_bolt", () ->
            EntityType.Builder.<LampBolt>of(LampBolt::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(6)
                    .updateInterval(2)
                    .noSave()
                    .build(AmemaruMod.id("lamp_bolt").toString()));

    // ---- 1階「始まりの草原」の敵（Phase 3）

    /** ツノモフ。 */
    public static final RegistryObject<EntityType<Tsunomofu>> TSUNOMOFU = ENTITIES.register("tsunomofu", () ->
            EntityType.Builder.<Tsunomofu>of(Tsunomofu::new, MobCategory.MONSTER)
                    .sized(0.9F, 0.9F)
                    .clientTrackingRange(10)
                    .build(AmemaruMod.id("tsunomofu").toString()));

    /** 石くれ兵。 */
    public static final RegistryObject<EntityType<IshikureSoldier>> ISHIKURE_SOLDIER = ENTITIES.register("ishikure_soldier", () ->
            EntityType.Builder.<IshikureSoldier>of(IshikureSoldier::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.1F)
                    .clientTrackingRange(10)
                    .build(AmemaruMod.id("ishikure_soldier").toString()));

    /** 結晶蜂。 */
    public static final RegistryObject<EntityType<CrystalBee>> CRYSTAL_BEE = ENTITIES.register("crystal_bee", () ->
            EntityType.Builder.<CrystalBee>of(CrystalBee::new, MobCategory.MONSTER)
                    .sized(0.7F, 0.6F)
                    .clientTrackingRange(10)
                    .build(AmemaruMod.id("crystal_bee").toString()));

    /** 欠け牙の狼。 */
    public static final RegistryObject<EntityType<KakekibaWolf>> KAKEKIBA_WOLF = ENTITIES.register("kakekiba_wolf", () ->
            EntityType.Builder.<KakekibaWolf>of(KakekibaWolf::new, MobCategory.MONSTER)
                    .sized(0.8F, 0.95F)
                    .clientTrackingRange(10)
                    .build(AmemaruMod.id("kakekiba_wolf").toString()));

    /** 結晶蜂の針。 */
    public static final RegistryObject<EntityType<CrystalNeedle>> CRYSTAL_NEEDLE = ENTITIES.register("crystal_needle", () ->
            EntityType.Builder.<CrystalNeedle>of(CrystalNeedle::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(6)
                    .updateInterval(2)
                    .noSave()
                    .build(AmemaruMod.id("crystal_needle").toString()));

    /** 倒した人だけが拾えるドロップ（ほかの人には見えない）。 */
    public static final RegistryObject<EntityType<LootItemEntity>> LOOT_ITEM = ENTITIES.register("loot_item", () ->
            EntityType.Builder.<LootItemEntity>of(LootItemEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build(AmemaruMod.id("loot_item").toString()));

    // ---- Phase 5：NPC と、1階のボス

    /** 街のNPC（倒せない、押されない、その場から動かない。Phase 0-B 4.4）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.npc.NpcEntity>> NPC = ENTITIES.register("npc", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.npc.NpcEntity>of(jp.amemaru.lostconnection.npc.NpcEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build(AmemaruMod.id("npc").toString()));

    /** 1階のボス、草原の主ヴェルドホーン（Phase 0-A 9章）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.boss.Veldhorn>> VELDHORN = ENTITIES.register("veldhorn", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.boss.Veldhorn>of(jp.amemaru.lostconnection.enemy.boss.Veldhorn::new,
                            MobCategory.MONSTER)
                    .sized(2.6F, 3.2F)
                    .clientTrackingRange(16)
                    // 突進と大跳躍は速いので、位置を毎 tick 送る（なめらかに見せる。11月22日）
                    .updateInterval(1)
                    .build(AmemaruMod.id("veldhorn").toString()));

    /** 地面の印（ボスの技の当たる所。跳んで降りる所・人の足もと。11月22日）。保存しない。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.entity.GroundMarker>> GROUND_MARKER = ENTITIES.register("ground_marker", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.entity.GroundMarker>of(jp.amemaru.lostconnection.entity.GroundMarker::new, MobCategory.MISC)
                    .sized(0.5F, 0.1F)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .noSave()
                    .fireImmune()
                    .build(AmemaruMod.id("ground_marker").toString()));

    private ModEntities() {
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(TRAINING_DUMMY.get(), TrainingDummy.createAttributes().build());
        event.put(TSUNOMOFU.get(), AmemaruMob.createAttributes().build());
        event.put(ISHIKURE_SOLDIER.get(), AmemaruMob.createAttributes().build());
        event.put(CRYSTAL_BEE.get(), CrystalBee.createBeeAttributes().build());
        event.put(KAKEKIBA_WOLF.get(), AmemaruMob.createAttributes().build());
        event.put(NPC.get(), jp.amemaru.lostconnection.npc.NpcEntity.createAttributes().build());
        event.put(VELDHORN.get(), jp.amemaru.lostconnection.enemy.boss.AmemaruBoss.createBossAttributes().build());
    }
}

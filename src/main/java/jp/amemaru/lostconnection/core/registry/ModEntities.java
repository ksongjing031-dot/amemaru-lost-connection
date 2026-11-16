package jp.amemaru.lostconnection.core.registry;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.entity.LampBolt;
import jp.amemaru.lostconnection.entity.TrainingDummy;
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

    private ModEntities() {
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(TRAINING_DUMMY.get(), TrainingDummy.createAttributes().build());
    }
}

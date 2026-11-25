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

    // ---- Phase 8 その1：2階「忘れられた森」の敵（Phase 0-A 9章）
    /** 迷い灯（道から外れた方へ誘う光の玉）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.forest.MayoiBi>> MAYOI_BI = ENTITIES.register("mayoi_bi", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.forest.MayoiBi>of(jp.amemaru.lostconnection.enemy.forest.MayoiBi::new,
                            MobCategory.MONSTER)
                    .sized(0.6F, 0.6F).clientTrackingRange(10).build(AmemaruMod.id("mayoi_bi").toString()));
    /** 樹皮兵（古代の植樹管理人形）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.forest.Juhihei>> JUHIHEI = ENTITIES.register("juhihei", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.forest.Juhihei>of(jp.amemaru.lostconnection.enemy.forest.Juhihei::new,
                            MobCategory.MONSTER)
                    .sized(1.0F, 2.3F).clientTrackingRange(10).build(AmemaruMod.id("juhihei").toString()));
    /** ポコダケ（毒の胞子を出すキノコ）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.forest.Pokodake>> POKODAKE = ENTITIES.register("pokodake", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.forest.Pokodake>of(jp.amemaru.lostconnection.enemy.forest.Pokodake::new,
                            MobCategory.MONSTER)
                    .sized(0.9F, 1.0F).clientTrackingRange(10).build(AmemaruMod.id("pokodake").toString()));
    /** 葉影ギツネ（素早く、背後を狙う）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.forest.HakageKitsune>> HAKAGE_KITSUNE =
            ENTITIES.register("hakage_kitsune", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.forest.HakageKitsune>of(jp.amemaru.lostconnection.enemy.forest.HakageKitsune::new,
                            MobCategory.MONSTER)
                    .sized(0.7F, 0.8F).clientTrackingRange(10).build(AmemaruMod.id("hakage_kitsune").toString()));

    /** 2階のボス、森番の古樹オールドウォード（Phase 0-A 9章。動かない大木）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.boss.OldWard>> OLD_WARD = ENTITIES.register("old_ward", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.boss.OldWard>of(jp.amemaru.lostconnection.enemy.boss.OldWard::new,
                            MobCategory.MONSTER)
                    .sized(2.6F, 5.4F).clientTrackingRange(16).build(AmemaruMod.id("old_ward").toString()));
    /** 古樹の光る節（ボスの幹の弱点）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.boss.OldWardKnot>> OLD_WARD_KNOT =
            ENTITIES.register("old_ward_knot", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.boss.OldWardKnot>of(jp.amemaru.lostconnection.enemy.boss.OldWardKnot::new,
                            MobCategory.MONSTER)
                    .sized(0.8F, 0.8F).clientTrackingRange(16).updateInterval(1).build(AmemaruMod.id("old_ward_knot").toString()));

    // ---- Phase 8 その2：3階「古代の水路」の敵（Phase 0-A 9章）
    /** 水門ネズミ（群れ）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.waterway.SuimonNezumi>> SUIMON_NEZUMI =
            ENTITIES.register("suimon_nezumi", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.waterway.SuimonNezumi>of(jp.amemaru.lostconnection.enemy.waterway.SuimonNezumi::new,
                            MobCategory.MONSTER)
                    .sized(0.7F, 0.6F).clientTrackingRange(10).build(AmemaruMod.id("suimon_nezumi").toString()));
    /** 澱みスライム（分裂する）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.waterway.YodomiSlime>> YODOMI_SLIME =
            ENTITIES.register("yodomi_slime", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.waterway.YodomiSlime>of(jp.amemaru.lostconnection.enemy.waterway.YodomiSlime::new,
                            MobCategory.MONSTER)
                    .sized(1.3F, 1.2F).clientTrackingRange(10).build(AmemaruMod.id("yodomi_slime").toString()));
    /** 水路掃除機兵（古代の清掃機械。正面の装甲）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.waterway.Soujikihei>> SOUJIKIHEI =
            ENTITIES.register("soujikihei", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.waterway.Soujikihei>of(jp.amemaru.lostconnection.enemy.waterway.Soujikihei::new,
                            MobCategory.MONSTER)
                    .sized(1.2F, 1.9F).clientTrackingRange(10).build(AmemaruMod.id("soujikihei").toString()));
    /** 牙ナマズ（水中から跳ねる）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.waterway.KibaNamazu>> KIBA_NAMAZU =
            ENTITIES.register("kiba_namazu", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.waterway.KibaNamazu>of(jp.amemaru.lostconnection.enemy.waterway.KibaNamazu::new,
                            MobCategory.MONSTER)
                    .sized(1.4F, 0.8F).clientTrackingRange(10).updateInterval(2).build(AmemaruMod.id("kiba_namazu").toString()));
    /** 3階のボス、大水門の番人ハイドラム（Phase 0-A 9章）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.boss.Hydram>> HYDRAM = ENTITIES.register("hydram", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.boss.Hydram>of(jp.amemaru.lostconnection.enemy.boss.Hydram::new,
                            MobCategory.MONSTER)
                    .sized(3.2F, 6.4F).clientTrackingRange(16).updateInterval(2).build(AmemaruMod.id("hydram").toString()));

    // ---- 11月24日：4階「崩壊した神殿」の敵（Phase 0-A 9章）
    /** 沈黙の石像（近づくと動き出す）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.temple.SilentStatue>> SILENT_STATUE =
            ENTITIES.register("silent_statue", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.temple.SilentStatue>of(jp.amemaru.lostconnection.enemy.temple.SilentStatue::new,
                            MobCategory.MONSTER)
                    .sized(1.1F, 2.6F).clientTrackingRange(10).build(AmemaruMod.id("silent_statue").toString()));
    /** 祈り手の残響（仲間を歌で治す）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.temple.PrayerEcho>> PRAYER_ECHO =
            ENTITIES.register("prayer_echo", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.temple.PrayerEcho>of(jp.amemaru.lostconnection.enemy.temple.PrayerEcho::new,
                            MobCategory.MONSTER)
                    .sized(0.8F, 1.9F).clientTrackingRange(10).build(AmemaruMod.id("prayer_echo").toString()));
    /** 結晶甲虫（硬いが魔法に弱い）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.temple.CrystalBeetle>> CRYSTAL_BEETLE =
            ENTITIES.register("crystal_beetle", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.temple.CrystalBeetle>of(jp.amemaru.lostconnection.enemy.temple.CrystalBeetle::new,
                            MobCategory.MONSTER)
                    .sized(1.4F, 1.0F).clientTrackingRange(10).build(AmemaruMod.id("crystal_beetle").toString()));
    /** 欠けた聖獣（神殿の守護獣がロストで欠けたもの）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.temple.BrokenBeast>> BROKEN_BEAST =
            ENTITIES.register("broken_beast", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.temple.BrokenBeast>of(jp.amemaru.lostconnection.enemy.temple.BrokenBeast::new,
                            MobCategory.MONSTER)
                    .sized(1.3F, 1.5F).clientTrackingRange(10).build(AmemaruMod.id("broken_beast").toString()));
    /** 4階のボス、祈りの残響カンターレ（Phase 0-A 9章）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.boss.Cantare>> CANTARE = ENTITIES.register("cantare", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.boss.Cantare>of(jp.amemaru.lostconnection.enemy.boss.Cantare::new,
                            MobCategory.MONSTER)
                    .sized(2.4F, 4.2F).clientTrackingRange(16).updateInterval(2).build(AmemaruMod.id("cantare").toString()));

    // ---- 11月24日：5階「古代守護者の祭壇」の敵（Phase 0-A 9章）
    /** 監視眼ウォッチャー（浮かぶ目。見つかると守護者が強くなる）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.altar.Watcher>> WATCHER = ENTITIES.register("watcher", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.altar.Watcher>of(jp.amemaru.lostconnection.enemy.altar.Watcher::new,
                            MobCategory.MONSTER)
                    .sized(1.0F, 1.0F).clientTrackingRange(10).build(AmemaruMod.id("watcher").toString()));
    /** 防衛兵センチネル（盾持ち）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.altar.Sentinel>> SENTINEL = ENTITIES.register("sentinel", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.altar.Sentinel>of(jp.amemaru.lostconnection.enemy.altar.Sentinel::new,
                            MobCategory.MONSTER)
                    .sized(1.0F, 2.3F).clientTrackingRange(10).build(AmemaruMod.id("sentinel").toString()));
    /** 修復ドローン（守護者を回復する）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.altar.RepairDrone>> REPAIR_DRONE =
            ENTITIES.register("repair_drone", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.altar.RepairDrone>of(jp.amemaru.lostconnection.enemy.altar.RepairDrone::new,
                            MobCategory.MONSTER)
                    .sized(0.7F, 0.6F).clientTrackingRange(10).build(AmemaruMod.id("repair_drone").toString()));
    /** 5階のボス、古代守護者（第1章の章ボス。Phase 0-A 9章・10章）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.boss.AncientGuardian>> ANCIENT_GUARDIAN =
            ENTITIES.register("ancient_guardian", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.boss.AncientGuardian>of(jp.amemaru.lostconnection.enemy.boss.AncientGuardian::new,
                            MobCategory.MONSTER)
                    .sized(2.6F, 5.2F).clientTrackingRange(16).updateInterval(2).build(AmemaruMod.id("ancient_guardian").toString()));

    // ---- 11月25日：6階「凍結した村」の敵（Phase 0-A 9章）
    /** 霜狼（群れで動く。噛まれると凍える）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.village.FrostWolf>> FROST_WOLF =
            ENTITIES.register("frost_wolf", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.village.FrostWolf>of(jp.amemaru.lostconnection.enemy.village.FrostWolf::new,
                            MobCategory.MONSTER)
                    .sized(0.8F, 0.95F).clientTrackingRange(10).build(AmemaruMod.id("frost_wolf").toString()));
    /** 氷像の残響（村人の氷像の一部が、近づくと動く）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.village.StatueEcho>> STATUE_ECHO =
            ENTITIES.register("statue_echo", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.village.StatueEcho>of(jp.amemaru.lostconnection.enemy.village.StatueEcho::new,
                            MobCategory.MONSTER)
                    .sized(0.6F, 1.8F).clientTrackingRange(10).build(AmemaruMod.id("statue_echo").toString()));
    /** 凍て鳥（吹雪を起こす）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.village.FrostBird>> FROST_BIRD =
            ENTITIES.register("frost_bird", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.village.FrostBird>of(jp.amemaru.lostconnection.enemy.village.FrostBird::new,
                            MobCategory.MONSTER)
                    .sized(0.9F, 0.7F).clientTrackingRange(10).build(AmemaruMod.id("frost_bird").toString()));
    /** 霜の欠け熊（大型）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.village.FrostBear>> FROST_BEAR =
            ENTITIES.register("frost_bear", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.village.FrostBear>of(jp.amemaru.lostconnection.enemy.village.FrostBear::new,
                            MobCategory.MONSTER)
                    .sized(1.6F, 1.6F).clientTrackingRange(10).build(AmemaruMod.id("frost_bear").toString()));
    /** 6階のボス、村長の残響オルド（Phase 0-A 9章）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.boss.Ordo>> ORDO = ENTITIES.register("ordo", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.boss.Ordo>of(jp.amemaru.lostconnection.enemy.boss.Ordo::new, MobCategory.MONSTER)
                    .sized(1.3F, 3.2F).clientTrackingRange(16).updateInterval(2).build(AmemaruMod.id("ordo").toString()));

    // ---- 11月25日：7階「氷の森林」の敵（Phase 0-A 9章）
    /** 写し身（プレイヤーの姿を真似る。名前は「？？？」。斬られると「薄れ」）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.iceforest.Mimic>> MIMIC =
            ENTITIES.register("mimic", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.iceforest.Mimic>of(jp.amemaru.lostconnection.enemy.iceforest.Mimic::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F).clientTrackingRange(10).build(AmemaruMod.id("mimic").toString()));
    /** 氷角鹿（角で突き、突進する）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.iceforest.IceStag>> ICE_STAG =
            ENTITIES.register("ice_stag", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.iceforest.IceStag>of(jp.amemaru.lostconnection.enemy.iceforest.IceStag::new, MobCategory.MONSTER)
                    .sized(0.9F, 1.5F).clientTrackingRange(10).build(AmemaruMod.id("ice_stag").toString()));
    /** つらら蝙蝠（つららを撃つ）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.iceforest.IcicleBat>> ICICLE_BAT =
            ENTITIES.register("icicle_bat", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.iceforest.IcicleBat>of(jp.amemaru.lostconnection.enemy.iceforest.IcicleBat::new, MobCategory.MONSTER)
                    .sized(0.7F, 0.6F).clientTrackingRange(10).build(AmemaruMod.id("icicle_bat").toString()));
    /** 吹雪の精（吹雪に打たれると「薄れ」）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.iceforest.BlizzardSpirit>> BLIZZARD_SPIRIT =
            ENTITIES.register("blizzard_spirit", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.iceforest.BlizzardSpirit>of(jp.amemaru.lostconnection.enemy.iceforest.BlizzardSpirit::new, MobCategory.MONSTER)
                    .sized(0.8F, 1.7F).clientTrackingRange(10).build(AmemaruMod.id("blizzard_spirit").toString()));
    /** 7階のボス、鏡像獣リフレクタ（Phase 0-A 9章）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.boss.Reflecta>> REFLECTA =
            ENTITIES.register("reflecta", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.boss.Reflecta>of(jp.amemaru.lostconnection.enemy.boss.Reflecta::new, MobCategory.MONSTER)
                    .sized(2.4F, 2.8F).clientTrackingRange(16).updateInterval(2).build(AmemaruMod.id("reflecta").toString()));

    // ---- 11月25日：8階「沈黙の湖」の敵（Phase 0-A 9章）
    /** 無音の大魚（氷の下から襲う。割れ目が予兆）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.lake.SilentFish>> SILENT_FISH =
            ENTITIES.register("silent_fish", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.lake.SilentFish>of(jp.amemaru.lostconnection.enemy.lake.SilentFish::new, MobCategory.MONSTER)
                    .sized(1.6F, 0.9F).clientTrackingRange(10).build(AmemaruMod.id("silent_fish").toString()));
    /** 声喰い（近くにいると効果音が消える）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.lake.VoiceEater>> VOICE_EATER =
            ENTITIES.register("voice_eater", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.lake.VoiceEater>of(jp.amemaru.lostconnection.enemy.lake.VoiceEater::new, MobCategory.MONSTER)
                    .sized(1.0F, 1.1F).clientTrackingRange(10).build(AmemaruMod.id("voice_eater").toString()));
    /** 凍て鶴（くちばしで突き、翼で吹雪を叩きつける）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.lake.FrostCrane>> FROST_CRANE =
            ENTITIES.register("frost_crane", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.lake.FrostCrane>of(jp.amemaru.lostconnection.enemy.lake.FrostCrane::new, MobCategory.MONSTER)
                    .sized(0.8F, 1.9F).clientTrackingRange(10).build(AmemaruMod.id("frost_crane").toString()));
    /** 氷霧の亡霊（触れられると「断線」）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.lake.FogWraith>> FOG_WRAITH =
            ENTITIES.register("fog_wraith", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.lake.FogWraith>of(jp.amemaru.lostconnection.enemy.lake.FogWraith::new, MobCategory.MONSTER)
                    .sized(0.8F, 1.8F).clientTrackingRange(10).build(AmemaruMod.id("fog_wraith").toString()));
    /** 8階のボス、声なき湖主ムーネ（Phase 0-A 9章）。 */
    public static final RegistryObject<EntityType<jp.amemaru.lostconnection.enemy.boss.Mune>> MUNE =
            ENTITIES.register("mune", () ->
            EntityType.Builder.<jp.amemaru.lostconnection.enemy.boss.Mune>of(jp.amemaru.lostconnection.enemy.boss.Mune::new, MobCategory.MONSTER)
                    .sized(3.2F, 2.0F).clientTrackingRange(16).updateInterval(2).build(AmemaruMod.id("mune").toString()));

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
        event.put(MAYOI_BI.get(), jp.amemaru.lostconnection.enemy.forest.MayoiBi.createLightAttributes().build());
        event.put(JUHIHEI.get(), AmemaruMob.createAttributes().build());
        event.put(POKODAKE.get(), AmemaruMob.createAttributes().build());
        event.put(HAKAGE_KITSUNE.get(), AmemaruMob.createAttributes().build());
        event.put(OLD_WARD.get(), jp.amemaru.lostconnection.enemy.boss.AmemaruBoss.createBossAttributes().build());
        event.put(OLD_WARD_KNOT.get(), AmemaruMob.createAttributes().add(
                net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 1.0).build());
        event.put(SUIMON_NEZUMI.get(), AmemaruMob.createAttributes().build());
        event.put(YODOMI_SLIME.get(), AmemaruMob.createAttributes().build());
        event.put(SOUJIKIHEI.get(), AmemaruMob.createAttributes().add(
                net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 0.7).build());
        event.put(KIBA_NAMAZU.get(), AmemaruMob.createAttributes().build());
        event.put(HYDRAM.get(), jp.amemaru.lostconnection.enemy.boss.AmemaruBoss.createBossAttributes().build());
        // 11月24日：4階
        event.put(SILENT_STATUE.get(), AmemaruMob.createAttributes().add(
                net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 0.9).build());
        event.put(PRAYER_ECHO.get(), jp.amemaru.lostconnection.enemy.temple.PrayerEcho.createEchoAttributes().build());
        event.put(CRYSTAL_BEETLE.get(), AmemaruMob.createAttributes().add(
                net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 0.6).build());
        event.put(BROKEN_BEAST.get(), AmemaruMob.createAttributes().build());
        event.put(CANTARE.get(), jp.amemaru.lostconnection.enemy.boss.AmemaruBoss.createBossAttributes().build());
        // 11月24日：5階
        event.put(WATCHER.get(), jp.amemaru.lostconnection.enemy.altar.Watcher.createWatcherAttributes().build());
        event.put(SENTINEL.get(), AmemaruMob.createAttributes().add(
                net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 0.8).build());
        event.put(REPAIR_DRONE.get(), jp.amemaru.lostconnection.enemy.altar.RepairDrone.createDroneAttributes().build());
        event.put(ANCIENT_GUARDIAN.get(), jp.amemaru.lostconnection.enemy.boss.AmemaruBoss.createBossAttributes().build());
        // 11月25日：6階
        event.put(FROST_WOLF.get(), AmemaruMob.createAttributes().build());
        event.put(STATUE_ECHO.get(), AmemaruMob.createAttributes().add(
                net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 0.9).build());
        event.put(FROST_BIRD.get(), CrystalBee.createBeeAttributes().build());
        event.put(FROST_BEAR.get(), AmemaruMob.createAttributes().add(
                net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 0.7).build());
        event.put(ORDO.get(), jp.amemaru.lostconnection.enemy.boss.AmemaruBoss.createBossAttributes().build());
        // 11月25日：7階
        event.put(MIMIC.get(), AmemaruMob.createAttributes().build());
        event.put(ICE_STAG.get(), AmemaruMob.createAttributes().add(
                net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 0.4).build());
        event.put(ICICLE_BAT.get(), CrystalBee.createBeeAttributes().build());
        event.put(BLIZZARD_SPIRIT.get(), CrystalBee.createBeeAttributes().build());
        event.put(REFLECTA.get(), jp.amemaru.lostconnection.enemy.boss.AmemaruBoss.createBossAttributes().build());
        // 11月25日：8階
        event.put(SILENT_FISH.get(), AmemaruMob.createAttributes().add(
                net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 1.0).build());
        event.put(VOICE_EATER.get(), CrystalBee.createBeeAttributes().build());
        event.put(FROST_CRANE.get(), AmemaruMob.createAttributes().add(
                net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 0.3).build());
        event.put(FOG_WRAITH.get(), CrystalBee.createBeeAttributes().build());
        event.put(MUNE.get(), jp.amemaru.lostconnection.enemy.boss.AmemaruBoss.createBossAttributes().build());
    }
}

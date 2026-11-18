package jp.amemaru.lostconnection.network.s2c;

import jp.amemaru.lostconnection.client.ClientPacketHandler;
import jp.amemaru.lostconnection.enemy.EnemyDef;
import jp.amemaru.lostconnection.enemy.EnemyDefs;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearRules;
import jp.amemaru.lostconnection.job.def.JobDef;
import jp.amemaru.lostconnection.job.def.SkillRegistry;
import jp.amemaru.lostconnection.workshop.ProductionRules;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 装備の定義・装備の決まり・図鑑に要る敵の情報を、クライアントへ配る（ログイン時と /reload の後。Phase 3）。
 *
 * <p>装備の説明文（ステータスの数値・幅・割合）は、品のNBT（抽選の割合）と定義の数値から、クライアントで計算する。</p>
 */
public final class SyncContentS2C {
    private static final int MAX_ENTRIES = 4096;

    /** 生産職の、画面に要る分。 */
    public record ProductionJobInfo(ResourceLocation id, String name, String description, int color, String workshop) {
    }

    private final GearRules rules;
    private final List<EquipmentDef> equipment;
    private final List<EnemyDef.Info> enemies;
    private final ProductionRules production;
    private final List<ProductionJobInfo> productionJobs;

    public SyncContentS2C(GearRules rules, List<EquipmentDef> equipment, List<EnemyDef.Info> enemies, ProductionRules production,
                          List<ProductionJobInfo> productionJobs) {
        this.rules = rules;
        this.equipment = equipment;
        this.enemies = enemies;
        this.production = production;
        this.productionJobs = productionJobs;
    }

    /** サーバーで、今の定義から作る。 */
    public static SyncContentS2C current() {
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.server();
        List<ProductionJobInfo> jobs = new ArrayList<>();
        for (JobDef job : SkillRegistry.get().productionJobs()) {
            jobs.add(new ProductionJobInfo(job.id(), job.name(), job.description(), job.color(), job.workshop()));
        }
        return new SyncContentS2C(snapshot.rules(), new ArrayList<>(snapshot.equipment().values()), EnemyDefs.infos(),
                ProductionRules.current(), jobs);
    }

    public void encode(FriendlyByteBuf buf) {
        production.write(buf);
        buf.writeVarInt(productionJobs.size());
        for (ProductionJobInfo job : productionJobs) {
            buf.writeResourceLocation(job.id());
            buf.writeUtf(job.name());
            buf.writeUtf(job.description());
            buf.writeInt(job.color());
            buf.writeUtf(job.workshop());
        }
        rules.write(buf);
        buf.writeVarInt(equipment.size());
        for (EquipmentDef def : equipment) {
            def.write(buf);
        }
        buf.writeVarInt(enemies.size());
        for (EnemyDef.Info info : enemies) {
            info.write(buf);
        }
    }

    public static SyncContentS2C decode(FriendlyByteBuf buf) {
        ProductionRules production = ProductionRules.read(buf);
        int jobCount = check(buf.readVarInt());
        List<ProductionJobInfo> jobs = new ArrayList<>();
        for (int i = 0; i < jobCount; i++) {
            jobs.add(new ProductionJobInfo(buf.readResourceLocation(), buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readUtf()));
        }
        GearRules rules = GearRules.read(buf);
        int count = check(buf.readVarInt());
        List<EquipmentDef> equipment = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            equipment.add(EquipmentDef.read(buf));
        }
        int enemyCount = check(buf.readVarInt());
        List<EnemyDef.Info> enemies = new ArrayList<>();
        for (int i = 0; i < enemyCount; i++) {
            enemies.add(EnemyDef.Info.read(buf));
        }
        return new SyncContentS2C(rules, equipment, enemies, production, jobs);
    }

    private static int check(int length) {
        if (length < 0 || length > MAX_ENTRIES) {
            throw new IllegalArgumentException("Bad length in SyncContentS2C: " + length);
        }
        return length;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleContent(this));
    }

    public GearRules rules() {
        return rules;
    }

    public List<EquipmentDef> equipment() {
        return equipment;
    }

    public List<EnemyDef.Info> enemies() {
        return enemies;
    }

    public ProductionRules production() {
        return production;
    }

    public List<ProductionJobInfo> productionJobs() {
        return productionJobs;
    }
}

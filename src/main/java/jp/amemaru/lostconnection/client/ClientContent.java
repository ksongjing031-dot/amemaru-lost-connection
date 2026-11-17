package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.network.s2c.SyncContentS2C;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/** サーバーから受け取った生産職の一覧（クライアント専用。工房の画面で使う）。 */
public final class ClientContent {
    private static volatile List<SyncContentS2C.ProductionJobInfo> productionJobs = List.of();

    private ClientContent() {
    }

    public static void setProductionJobs(List<SyncContentS2C.ProductionJobInfo> jobs) {
        productionJobs = List.copyOf(jobs);
    }

    /** その工房（weapon・armor・accessory）の生産職。 */
    @Nullable
    public static SyncContentS2C.ProductionJobInfo jobFor(String workshop) {
        for (SyncContentS2C.ProductionJobInfo job : productionJobs) {
            if (job.workshop().equals(workshop)) {
                return job;
            }
        }
        return null;
    }

    @Nullable
    public static SyncContentS2C.ProductionJobInfo job(@Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        for (SyncContentS2C.ProductionJobInfo job : productionJobs) {
            if (job.id().equals(id)) {
                return job;
            }
        }
        return null;
    }
}

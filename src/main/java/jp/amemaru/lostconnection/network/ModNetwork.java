package jp.amemaru.lostconnection.network;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.network.c2s.AccessoryActionC2S;
import jp.amemaru.lostconnection.network.c2s.AllocateStatC2S;
import jp.amemaru.lostconnection.network.c2s.WorkshopActionC2S;
import jp.amemaru.lostconnection.network.s2c.AccessoryVisibilityS2C;
import jp.amemaru.lostconnection.network.s2c.SpecialMomentS2C;
import jp.amemaru.lostconnection.network.s2c.SyncAdventureS2C;
import jp.amemaru.lostconnection.network.s2c.SyncContentS2C;
import jp.amemaru.lostconnection.network.c2s.JobActionC2S;
import jp.amemaru.lostconnection.network.c2s.LearnSkillC2S;
import jp.amemaru.lostconnection.network.c2s.SetLoadoutC2S;
import jp.amemaru.lostconnection.network.c2s.UseSkillC2S;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.network.s2c.OpenJobScreenS2C;
import jp.amemaru.lostconnection.network.s2c.SkillFxS2C;
import jp.amemaru.lostconnection.network.s2c.SkillResultS2C;
import jp.amemaru.lostconnection.network.s2c.StatusFxS2C;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
import jp.amemaru.lostconnection.network.s2c.SyncPlayerDataS2C;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 通信のチャンネル {@code amemaru:main}（Phase 0-B 5章）。
 *
 * <p>プロトコル番号が違うクライアントは接続できない（Modの版ずれを防ぐ）。パケットの形を変えたら
 * {@link #PROTOCOL_VERSION} を上げる。クライアントが送るのは「何をしたいか」だけで、数値の計算はサーバーで行う。</p>
 */
public final class ModNetwork {
    public static final String PROTOCOL_VERSION = "8";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            AmemaruMod.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static boolean registered;

    private ModNetwork() {
    }

    /** パケットを登録する。番号の順番を変えないこと（足すときは最後に足す）。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        int id = 0;

        // サーバー → クライアント
        CHANNEL.messageBuilder(SyncPlayerDataS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncPlayerDataS2C::encode)
                .decoder(SyncPlayerDataS2C::decode)
                .consumerMainThread(SyncPlayerDataS2C::handle)
                .add();

        // クライアント → サーバー
        CHANNEL.messageBuilder(AllocateStatC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AllocateStatC2S::encode)
                .decoder(AllocateStatC2S::decode)
                .consumerMainThread(AllocateStatC2S::handle)
                .add();

        // ---- Phase 2：職業とスキル
        CHANNEL.messageBuilder(SyncDefinitionsS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncDefinitionsS2C::encode)
                .decoder(SyncDefinitionsS2C::decode)
                .consumerMainThread(SyncDefinitionsS2C::handle)
                .add();
        CHANNEL.messageBuilder(SkillFxS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkillFxS2C::encode)
                .decoder(SkillFxS2C::decode)
                .consumerMainThread(SkillFxS2C::handle)
                .add();
        CHANNEL.messageBuilder(CombatTextS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CombatTextS2C::encode)
                .decoder(CombatTextS2C::decode)
                .consumerMainThread(CombatTextS2C::handle)
                .add();
        CHANNEL.messageBuilder(SkillResultS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkillResultS2C::encode)
                .decoder(SkillResultS2C::decode)
                .consumerMainThread(SkillResultS2C::handle)
                .add();
        CHANNEL.messageBuilder(OpenJobScreenS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenJobScreenS2C::encode)
                .decoder(OpenJobScreenS2C::decode)
                .consumerMainThread(OpenJobScreenS2C::handle)
                .add();
        CHANNEL.messageBuilder(UseSkillC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UseSkillC2S::encode)
                .decoder(UseSkillC2S::decode)
                .consumerMainThread(UseSkillC2S::handle)
                .add();
        CHANNEL.messageBuilder(LearnSkillC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LearnSkillC2S::encode)
                .decoder(LearnSkillC2S::decode)
                .consumerMainThread(LearnSkillC2S::handle)
                .add();
        CHANNEL.messageBuilder(SetLoadoutC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetLoadoutC2S::encode)
                .decoder(SetLoadoutC2S::decode)
                .consumerMainThread(SetLoadoutC2S::handle)
                .add();
        CHANNEL.messageBuilder(JobActionC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(JobActionC2S::encode)
                .decoder(JobActionC2S::decode)
                .consumerMainThread(JobActionC2S::handle)
                .add();
        // Phase 2（スキルの拡張）：状態の見た目
        CHANNEL.messageBuilder(StatusFxS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StatusFxS2C::encode)
                .decoder(StatusFxS2C::decode)
                .consumerMainThread(StatusFxS2C::handle)
                .add();

        // ---- Phase 3：敵・ドロップ・装備・工房・アクセサリー
        CHANNEL.messageBuilder(SyncAdventureS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncAdventureS2C::encode)
                .decoder(SyncAdventureS2C::decode)
                .consumerMainThread(SyncAdventureS2C::handle)
                .add();
        CHANNEL.messageBuilder(SyncContentS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncContentS2C::encode)
                .decoder(SyncContentS2C::decode)
                .consumerMainThread(SyncContentS2C::handle)
                .add();
        CHANNEL.messageBuilder(AccessoryVisibilityS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AccessoryVisibilityS2C::encode)
                .decoder(AccessoryVisibilityS2C::decode)
                .consumerMainThread(AccessoryVisibilityS2C::handle)
                .add();
        CHANNEL.messageBuilder(SpecialMomentS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SpecialMomentS2C::encode)
                .decoder(SpecialMomentS2C::decode)
                .consumerMainThread(SpecialMomentS2C::handle)
                .add();
        CHANNEL.messageBuilder(WorkshopActionC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WorkshopActionC2S::encode)
                .decoder(WorkshopActionC2S::decode)
                .consumerMainThread(WorkshopActionC2S::handle)
                .add();
        CHANNEL.messageBuilder(AccessoryActionC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AccessoryActionC2S::encode)
                .decoder(AccessoryActionC2S::decode)
                .consumerMainThread(AccessoryActionC2S::handle)
                .add();
        // Phase 4：ダウン・パーティ・所持金
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.DownStateS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.DownStateS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.DownStateS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.DownStateS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.PartyStateS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.PartyStateS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.PartyStateS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.PartyStateS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.SyncWalletS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.SyncWalletS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.SyncWalletS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.SyncWalletS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.c2s.PartyActionC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(jp.amemaru.lostconnection.network.c2s.PartyActionC2S::encode)
                .decoder(jp.amemaru.lostconnection.network.c2s.PartyActionC2S::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.c2s.PartyActionC2S::handle)
                .add();
        // ---- Phase 5：進行・物語・NPC・塔
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.WorldStateS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.WorldStateS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.WorldStateS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.WorldStateS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.AnnouncementS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.AnnouncementS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.AnnouncementS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.AnnouncementS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.DialogueS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.DialogueS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.DialogueS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.DialogueS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.SceneS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.SceneS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.SceneS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.SceneS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.ReplayListS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.ReplayListS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.ReplayListS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.ReplayListS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.SyncStoryS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.SyncStoryS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.SyncStoryS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.SyncStoryS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.StoryTextS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.StoryTextS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.StoryTextS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.StoryTextS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.FloorSelectS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.FloorSelectS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.FloorSelectS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.FloorSelectS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.SyncNpcsS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.SyncNpcsS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.SyncNpcsS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.SyncNpcsS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.c2s.DialogueChoiceC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(jp.amemaru.lostconnection.network.c2s.DialogueChoiceC2S::encode)
                .decoder(jp.amemaru.lostconnection.network.c2s.DialogueChoiceC2S::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.c2s.DialogueChoiceC2S::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.c2s.SceneStateC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(jp.amemaru.lostconnection.network.c2s.SceneStateC2S::encode)
                .decoder(jp.amemaru.lostconnection.network.c2s.SceneStateC2S::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.c2s.SceneStateC2S::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.c2s.FloorSelectC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(jp.amemaru.lostconnection.network.c2s.FloorSelectC2S::encode)
                .decoder(jp.amemaru.lostconnection.network.c2s.FloorSelectC2S::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.c2s.FloorSelectC2S::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.c2s.ReplayMemoryC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(jp.amemaru.lostconnection.network.c2s.ReplayMemoryC2S::encode)
                .decoder(jp.amemaru.lostconnection.network.c2s.ReplayMemoryC2S::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.c2s.ReplayMemoryC2S::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.c2s.RetireC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(jp.amemaru.lostconnection.network.c2s.RetireC2S::encode)
                .decoder(jp.amemaru.lostconnection.network.c2s.RetireC2S::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.c2s.RetireC2S::handle)
                .add();
        // Phase 6：経済（ノットの受け渡し）
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.c2s.TransferKnotsC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(jp.amemaru.lostconnection.network.c2s.TransferKnotsC2S::encode)
                .decoder(jp.amemaru.lostconnection.network.c2s.TransferKnotsC2S::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.c2s.TransferKnotsC2S::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.StorageContentsS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.StorageContentsS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.StorageContentsS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.StorageContentsS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.OpenShopS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.OpenShopS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.OpenShopS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.OpenShopS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.c2s.ShopActionC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(jp.amemaru.lostconnection.network.c2s.ShopActionC2S::encode)
                .decoder(jp.amemaru.lostconnection.network.c2s.ShopActionC2S::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.c2s.ShopActionC2S::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.ShopResultS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.ShopResultS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.ShopResultS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.ShopResultS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.OpenMarketS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.OpenMarketS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.OpenMarketS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.OpenMarketS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.MarketPageS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.MarketPageS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.MarketPageS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.MarketPageS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.MarketPriceS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.MarketPriceS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.MarketPriceS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.MarketPriceS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.MarketMineS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.MarketMineS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.MarketMineS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.MarketMineS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.MarketStallsS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.MarketStallsS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.MarketStallsS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.MarketStallsS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.MarketResultS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.MarketResultS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.MarketResultS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.MarketResultS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.c2s.MarketActionC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(jp.amemaru.lostconnection.network.c2s.MarketActionC2S::encode)
                .decoder(jp.amemaru.lostconnection.network.c2s.MarketActionC2S::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.c2s.MarketActionC2S::handle)
                .add();
        // Phase 6 その2：弓の通常の射撃
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.c2s.BasicShotC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(jp.amemaru.lostconnection.network.c2s.BasicShotC2S::encode)
                .decoder(jp.amemaru.lostconnection.network.c2s.BasicShotC2S::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.c2s.BasicShotC2S::handle)
                .add();
        // Phase 6 その3：ギルド（称号・古代記録・親密度・クエスト・縁帳・パーティ募集・冒険日誌・露店の寄付）
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.GuildS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.GuildS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.GuildS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.GuildS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.c2s.GuildActionC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(jp.amemaru.lostconnection.network.c2s.GuildActionC2S::encode)
                .decoder(jp.amemaru.lostconnection.network.c2s.GuildActionC2S::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.c2s.GuildActionC2S::handle)
                .add();
        // Phase 6 その4：厳選と、交流（依頼台・決闘）
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.c2s.AccessoryOperationC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(jp.amemaru.lostconnection.network.c2s.AccessoryOperationC2S::encode)
                .decoder(jp.amemaru.lostconnection.network.c2s.AccessoryOperationC2S::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.c2s.AccessoryOperationC2S::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.RefineQuoteS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.RefineQuoteS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.RefineQuoteS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.RefineQuoteS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.SocialS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.SocialS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.SocialS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.SocialS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.c2s.SocialActionC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(jp.amemaru.lostconnection.network.c2s.SocialActionC2S::encode)
                .decoder(jp.amemaru.lostconnection.network.c2s.SocialActionC2S::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.c2s.SocialActionC2S::handle)
                .add();
        // Phase 7 その1：コネクション量と輝き段階・結び札
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.s2c.ConnectionS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(jp.amemaru.lostconnection.network.s2c.ConnectionS2C::encode)
                .decoder(jp.amemaru.lostconnection.network.s2c.ConnectionS2C::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.s2c.ConnectionS2C::handle)
                .add();
        CHANNEL.messageBuilder(jp.amemaru.lostconnection.network.c2s.ConnectionActionC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(jp.amemaru.lostconnection.network.c2s.ConnectionActionC2S::encode)
                .decoder(jp.amemaru.lostconnection.network.c2s.ConnectionActionC2S::decode)
                .consumerMainThread(jp.amemaru.lostconnection.network.c2s.ConnectionActionC2S::handle)
                .add();
    }

    /** 1人のプレイヤーに送る。 */
    public static void sendTo(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** 本人と、その人が見えているまわりの人に送る。 */
    public static void sendToTrackingAndSelf(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), message);
    }

    /** つながっている全員に送る。 */
    public static void sendToAll(Object message) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), message);
    }

    /** サーバーに送る（クライアントから呼ぶ）。 */
    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }
}

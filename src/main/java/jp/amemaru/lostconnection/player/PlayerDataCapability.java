package jp.amemaru.lostconnection.player;

import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import java.util.Optional;

/**
 * {@link PlayerData} をプレイヤーに付けるCapabilityの入り口。
 */
public final class PlayerDataCapability {
    public static final Capability<PlayerData> PLAYER_DATA = CapabilityManager.get(new CapabilityToken<>() {
    });

    /** プレイヤーに付けるときの名前（保存データのキーにもなる）。 */
    public static final ResourceLocation ID = AmemaruMod.id("player_data");

    private PlayerDataCapability() {
    }

    /** プレイヤーのデータを取り出す。付いていないとき（ほかのModが消したときなど）は空。 */
    public static Optional<PlayerData> get(Player player) {
        return player.getCapability(PLAYER_DATA).resolve();
    }
}

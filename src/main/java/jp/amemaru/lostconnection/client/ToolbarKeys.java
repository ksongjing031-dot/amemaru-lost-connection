package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.core.config.ClientConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * バニラの「ツールバーの保存・読み込み」のキー（初期値 C・X）を、最初の1回だけ外す（Phase 0-A 19章）。
 *
 * <p>外したことはクライアントの設定（toolbarKeysReleased）に覚えておき、自分で割り当て直した人のキーは二度と外さない。
 * もとの初期値のままのときだけ外す。</p>
 */
public final class ToolbarKeys {
    private static boolean checked;

    private ToolbarKeys() {
    }

    /** ゲームの準備ができてから1回だけ呼ぶ。 */
    public static void releaseOnce() {
        if (checked) {
            return;
        }
        checked = true;
        try {
            if (ClientConfig.TOOLBAR_KEYS_RELEASED.get()) {
                return;
            }
        } catch (IllegalStateException notLoaded) {
            checked = false;
            return;
        }
        Options options = Minecraft.getInstance().options;
        boolean changed = release(options.keySaveHotbarActivator) | release(options.keyLoadHotbarActivator);
        if (changed) {
            KeyMapping.resetMapping();
            options.save();
            AmemaruMod.LOGGER.info("[amemaru] Released the vanilla toolbar save/load keys for skill slots");
        }
        ClientConfig.TOOLBAR_KEYS_RELEASED.set(true);
        ClientConfig.TOOLBAR_KEYS_RELEASED.save();
    }

    private static boolean release(KeyMapping mapping) {
        if (mapping.isDefault()) {
            mapping.setKey(InputConstants.UNKNOWN);
            return true;
        }
        return false;
    }
}

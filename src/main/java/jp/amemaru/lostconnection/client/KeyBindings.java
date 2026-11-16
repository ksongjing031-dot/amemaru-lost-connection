package jp.amemaru.lostconnection.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * キーの割り当て。設定の「操作設定」→「AMEMARU」から変えられる。
 *
 * <p>スキルのキー（R・G・V・Z・X・C）はPhase 2で足す。</p>
 */
public final class KeyBindings {
    public static final String CATEGORY = "key.categories.amemaru";

    /** ステータス画面を開く（初期値：K）。 */
    public static final KeyMapping OPEN_STATUS = new KeyMapping(
            "key.amemaru.status",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY);

    private KeyBindings() {
    }
}

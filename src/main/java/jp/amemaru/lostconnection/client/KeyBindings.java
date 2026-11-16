package jp.amemaru.lostconnection.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * キーの割り当て。設定の「操作設定」→「AMEMARU」から変えられる。
 *
 * <p>スキル枠6つの初期値は R・Z・X・C・B・Y。Phase 0-A の案（R・G・V・Z・X・C）のうち、G と V はボイスチャットMod
 * （Simple Voice Chat）の初期のキー（グループの画面・ボイスチャットの画面）と重なるため、B と Y に変えた【提案】。
 * X と C はバニラの「ツールバーの保存・読み込み」を外して使う（{@link ToolbarKeys}）。</p>
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

    /** スキルツリーを開く（初期値：J）。 */
    public static final KeyMapping OPEN_SKILL_TREE = new KeyMapping(
            "key.amemaru.skill_tree",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY);

    private static final int[] SKILL_DEFAULTS = {
            GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_Y};

    /** スキル枠1〜6。 */
    public static final KeyMapping[] SKILLS = new KeyMapping[SKILL_DEFAULTS.length];

    static {
        for (int i = 0; i < SKILLS.length; i++) {
            SKILLS[i] = new KeyMapping(
                    "key.amemaru.skill_" + (i + 1),
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    SKILL_DEFAULTS[i],
                    CATEGORY);
        }
    }

    private KeyBindings() {
    }
}

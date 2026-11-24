package jp.amemaru.lostconnection.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * キーの割り当て。設定の「操作設定」→「AMEMARU」から変えられる。
 *
 * <p>スキル枠6つの初期値は Phase 0-A のとおり R・G・V・Z・X・C（11月17日にご確認）。X と C はバニラの
 * 「ツールバーの保存・読み込み」を外して使う（{@link ToolbarKeys}）。ボイスチャットModの初期のキー（V・G）と重なるときは、
 * ボイスチャットの方のキーを変えて使う。</p>
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

    /** パーティの画面を開く（初期値：U。Phase 4。バニラのPはソーシャル操作の画面なので使わない）。 */
    public static final KeyMapping OPEN_PARTY = new KeyMapping(
            "key.amemaru.party",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            CATEGORY);

    /**
     * ダウン中に押し続けると、教会へ戻る（リタイア。初期値：H。11月22日のご指示）。H はバニラでもこのModでも使っていないキー。
     * スペースなどの動くキーは、ダウンした直後に押したままのことがあるので使わない。
     */
    public static final KeyMapping RETIRE = new KeyMapping(
            "key.amemaru.retire",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY);

    /**
     * 敵をロックする（初期値：B。11月24日のご指示「スキルにターゲットロックオン機能を追加しよう」）。押すたびに右どなりの敵へ移り、
     * 長押しで外す。B はバニラでは使っていない（Ctrl+B のナレーターとは別）。試作版のスキル枠5の初期値だったが、
     * 新しい初期値（R・G・V・Z・X・C）に移したので空いている。
     */
    public static final KeyMapping LOCK_ON = new KeyMapping(
            "key.amemaru.lock_on",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY);

    private static final int[] SKILL_DEFAULTS = {
            GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C};

    /** 試作版（11月17日まで）の初期値。これのままの人は、新しい初期値に移す（{@link ToolbarKeys}）。 */
    static final int[] OLD_SKILL_DEFAULTS = {
            GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_Y};

    static int defaultKey(int slot) {
        return SKILL_DEFAULTS[slot];
    }

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

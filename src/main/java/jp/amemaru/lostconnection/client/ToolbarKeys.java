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
 *
 * <p>あわせて、スキルのキーが試作版の初期値（R・Z・X・C・B・Y）のままの人を、R・G・V・Z・X・C に移す（1回だけ。
 * 自分で変えたキーはそのまま）。</p>
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
            migrateSkillKeys();
            releaseCuriosKey();
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

    /** 試作版の初期値のままのスキルのキーを、新しい初期値に移す。 */
    private static void migrateSkillKeys() {
        if (ClientConfig.SKILL_KEYS_VERSION.get() >= 2) {
            return;
        }
        boolean changed = false;
        for (int slot = 0; slot < KeyBindings.SKILLS.length; slot++) {
            KeyMapping mapping = KeyBindings.SKILLS[slot];
            InputConstants.Key old = InputConstants.Type.KEYSYM.getOrCreate(KeyBindings.OLD_SKILL_DEFAULTS[slot]);
            if (!mapping.isDefault() && mapping.getKey().equals(old)) {
                mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(KeyBindings.defaultKey(slot)));
                changed = true;
            }
        }
        if (changed) {
            KeyMapping.resetMapping();
            Minecraft.getInstance().options.save();
            AmemaruMod.LOGGER.info("[amemaru] Moved the skill keys to the new defaults (R, G, V, Z, X, C)");
        }
        ClientConfig.SKILL_KEYS_VERSION.set(2);
        ClientConfig.SKILL_KEYS_VERSION.save();
    }

    /**
     * Curios の「アクセサリーの枠を開く」キー（初期値G）は、スキルの2つ目のキー（G）と重なるため、最初の1回だけ外す（Phase 3）。
     * このModの枠の画面は、ステータス画面と持ち物の画面のボタンから開く。
     */
    private static void releaseCuriosKey() {
        if (ClientConfig.CURIOS_KEY_RELEASED.get()) {
            return;
        }
        Options options = Minecraft.getInstance().options;
        boolean changed = false;
        for (KeyMapping mapping : options.keyMappings) {
            if ("key.curios.open.desc".equals(mapping.getName()) && mapping.isDefault()) {
                mapping.setKey(InputConstants.UNKNOWN);
                changed = true;
            }
        }
        if (changed) {
            KeyMapping.resetMapping();
            options.save();
            AmemaruMod.LOGGER.info("[amemaru] Released the Curios open key (G) for the skill slots");
        }
        ClientConfig.CURIOS_KEY_RELEASED.set(true);
        ClientConfig.CURIOS_KEY_RELEASED.save();
    }

    private static boolean release(KeyMapping mapping) {
        if (mapping.isDefault()) {
            mapping.setKey(InputConstants.UNKNOWN);
            return true;
        }
        return false;
    }
}

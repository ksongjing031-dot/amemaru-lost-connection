package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.accessory.AccessoryData;
import jp.amemaru.lostconnection.accessory.AccessoryItem;
import jp.amemaru.lostconnection.client.screen.UiStyle;
import jp.amemaru.lostconnection.equipment.GearItems;
import jp.amemaru.lostconnection.equipment.Rarity;
import jp.amemaru.lostconnection.network.s2c.SpecialMomentS2C;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * 本人の画面だけに出す特別な演出（{@code SpecialMomentS2C}）：画面の上の帯（品の絵と名前）、音、体のまわりの光。
 *
 * <ul>
 *   <li>レアドロップ：レアリティの色の帯と、澄んだ音（ドロップの場所には、持ち主にだけ見える光の柱が立つ）</li>
 *   <li>完璧なアクセサリー：虹色の帯と、特別な音・光（Phase 0-A 21章【確定】）</li>
 *   <li>工房：完成・大成功・強化・修理</li>
 * </ul>
 */
public final class SpecialMomentFx {
    public static final IGuiOverlay OVERLAY = SpecialMomentFx::render;
    private static final long DURATION = 3600L;

    private static SpecialMomentS2C.Kind kind = SpecialMomentS2C.Kind.CRAFTED;
    private static ItemStack stack = ItemStack.EMPTY;
    private static long start = -DURATION;

    private SpecialMomentFx() {
    }

    public static void show(SpecialMomentS2C.Kind newKind, ItemStack newStack) {
        kind = newKind;
        stack = newStack;
        start = Util.getMillis();
        Minecraft minecraft = Minecraft.getInstance();
        SoundEvent sound = switch (newKind) {
            case RARE_DROP -> SoundEvents.AMETHYST_BLOCK_RESONATE;
            case PERFECT_ACCESSORY -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case GREAT_SUCCESS -> SoundEvents.PLAYER_LEVELUP;
            case CRAFTED -> SoundEvents.UI_STONECUTTER_TAKE_RESULT;
            case ENHANCED -> SoundEvents.ANVIL_USE;
            case REPAIRED -> SoundEvents.GRINDSTONE_USE;
        };
        float pitch = newKind == SpecialMomentS2C.Kind.RARE_DROP ? 1.6F : 1.0F;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.9F));
        if (newKind == SpecialMomentS2C.Kind.RARE_DROP) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.8F, 0.6F));
        }
        LocalPlayer player = minecraft.player;
        if (player != null) {
            ParticleOptions particle = newKind == SpecialMomentS2C.Kind.PERFECT_ACCESSORY ? ParticleTypes.TOTEM_OF_UNDYING
                    : newKind == SpecialMomentS2C.Kind.RARE_DROP ? ParticleTypes.END_ROD : ParticleTypes.HAPPY_VILLAGER;
            int count = newKind == SpecialMomentS2C.Kind.PERFECT_ACCESSORY ? 60 : 16;
            for (int i = 0; i < count; i++) {
                double angle = i * Math.PI * 2.0 / count;
                player.level().addParticle(particle, player.getX() + Math.cos(angle) * 0.8, player.getY() + 0.2 + (i % 5) * 0.35,
                        player.getZ() + Math.sin(angle) * 0.8, 0.0, 0.05, 0.0);
            }
        }
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        long elapsed = Util.getMillis() - start;
        if (elapsed < 0 || elapsed > DURATION || stack.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float fadeIn = Mth.clamp(elapsed / 250.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((DURATION - elapsed) / 500.0F, 0.0F, 1.0F);
        float alpha = Math.min(fadeIn, fadeOut);
        if (alpha <= 0.02F) {
            return;
        }
        Component title = Component.translatable("moment.amemaru." + kind.name().toLowerCase(java.util.Locale.ROOT));
        Component name = stack.getHoverName();
        int textWidth = Math.max(minecraft.font.width(title), minecraft.font.width(name));
        int width = textWidth + 44;
        int height = 30;
        int x = (screenWidth - width) / 2;
        int y = 18 + Math.round((1.0F - fadeIn) * -12.0F);
        int accent = accent();
        int a = Math.round(alpha * 230.0F) << 24;
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, a | (UiStyle.OUTLINE & 0xFFFFFF));
        graphics.fillGradient(x, y, x + width, y + height, a | 0x131B33, a | 0x0B1022);
        graphics.fill(x, y, x + width, y + 1, a | (accent & 0xFFFFFF));
        graphics.fill(x, y + height - 1, x + width, y + height, a | (accent & 0xFFFFFF));
        graphics.renderItem(stack, x + 8, y + 7);
        int textAlpha = Math.max(4, Math.round(alpha * 255.0F)) << 24;
        graphics.drawString(minecraft.font, title, x + 32, y + 5, textAlpha | (accent & 0xFFFFFF), true);
        graphics.drawString(minecraft.font, name, x + 32, y + 17, textAlpha | 0xFFFFFF, true);
    }

    /** 帯の色（品のレアリティ。完璧なアクセサリーは虹色）。 */
    private static int accent() {
        long now = Util.getMillis();
        if (kind == SpecialMomentS2C.Kind.PERFECT_ACCESSORY) {
            return Rarity.UNIQUE.colorAt(now);
        }
        Rarity rarity = GearItems.rarity(stack);
        if (rarity == null && stack.getItem() instanceof AccessoryItem) {
            rarity = AccessoryData.rarity(stack, Rarity.UNCOMMON);
        }
        if (rarity == null) {
            rarity = kind == SpecialMomentS2C.Kind.RARE_DROP ? Rarity.RARE : Rarity.UNCOMMON;
        }
        return rarity.colorAt(now);
    }
}

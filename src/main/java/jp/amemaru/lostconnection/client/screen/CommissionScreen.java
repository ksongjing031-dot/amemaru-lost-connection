package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.accessory.AccessoryData;
import jp.amemaru.lostconnection.accessory.AccessoryItem;
import jp.amemaru.lostconnection.client.ClientPlayerState;
import jp.amemaru.lostconnection.client.ClientSocial;
import jp.amemaru.lostconnection.client.GearTooltips;
import jp.amemaru.lostconnection.economy.KnotTransfer;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 依頼台の画面（Phase 6 その4。Phase 0-A 21章「装飾師による依頼の厳選」）。装飾工房の作業台の画面の「依頼」から開く。
 *
 * <ul>
 *   <li>受付中：まだ誰も受けていない依頼。装飾師は「受ける」</li>
 *   <li>出した依頼：自分の依頼の様子（結果の記録）。受付中なら「取り下げる」、厳選中なら「ここで止める」</li>
 *   <li>受けた依頼（装飾師）：「1回振り直す」「続けて振り直す」「手放す」</li>
 *   <li>依頼を出す：持ち物のアクセサリーを選び、数値だけ・種類ごと・強化、固定するサブ、目標、回数の上限、手数料を決める。
 *       預ける素材とノットは、サーバーの見積もりで出す</li>
 * </ul>
 */
public class CommissionScreen extends Screen {
    private static final int W = 380;
    private static final int H = 222;
    private static final String[] TABS = {"open", "mine", "working", "create"};
    private static final int TAB_W = 88;
    private static final int LIST_W = 160;
    private static final int ROW = 24;
    private static final int ROWS = 6;
    private static final int GRID = 18;
    private static final int GRID_COLS = 8;
    private static final String[] KINDS = {"VALUES", "TYPES", "ENHANCE"};

    private int left;
    private int top;
    private String tab = "open";
    private int selected;
    private int scroll;
    private int seen = -1;
    // ---- 依頼を出す
    private int pickSlot = -1;
    private String kind = "VALUES";
    private final List<Integer> locks = new ArrayList<>();
    private int targetIndex;
    private int ratio = 90;
    private int stars = 4;
    private int level = 3;
    private int attempts = 10;
    private String fee = "500";
    private EditBox feeBox;
    private long quoteSentAt;
    private String quoteKey = "";

    public CommissionScreen() {
        super(Component.translatable("screen.amemaru.commission.title"));
    }

    // ================================================================ 組み立て

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        seen = ClientSocial.version();
        addRenderableWidget(new CrystalButton(left + W - 20, top + 5, 14, 12, Component.literal("×"), b -> onClose()));
        for (int i = 0; i < TABS.length; i++) {
            String key = TABS[i];
            CrystalButton button = addRenderableWidget(new CrystalButton(left + 12 + i * (TAB_W + 2), top + 20, TAB_W, 14,
                    Component.translatable("screen.amemaru.commission.tab." + key), b -> {
                        tab = key;
                        selected = 0;
                        scroll = 0;
                        rebuildWidgets();
                    }));
            button.active = !key.equals(tab);
        }
        if ("create".equals(tab)) {
            initCreate();
        } else {
            initList();
        }
    }

    private ListTag list() {
        return ClientSocial.commissions().getList(tab, Tag.TAG_COMPOUND);
    }

    private void initList() {
        ListTag list = list();
        selected = Math.max(0, Math.min(list.size() - 1, selected));
        if (list.isEmpty()) {
            return;
        }
        CompoundTag one = list.getCompound(selected);
        int id = one.getInt("id");
        String state = one.getString("state");
        boolean enhance = "ENHANCE".equals(one.getString("kind"));
        int bx = left + LIST_W + 22;
        int by = top + H - 24;
        CompoundTag args = new CompoundTag();
        args.putInt("id", id);
        switch (tab) {
            case "open" -> {
                CrystalButton accept = addRenderableWidget(new CrystalButton(bx, by, 80, 16, Component.translatable("screen.amemaru.commission.accept"),
                        b -> ClientSocial.send("commission_accept", args)));
                accept.active = ClientSocial.commissions().getBoolean("crafter") && !one.getBoolean("own");
            }
            case "mine" -> {
                if ("OPEN".equals(state)) {
                    addRenderableWidget(new CrystalButton(bx, by, 80, 16, Component.translatable("screen.amemaru.commission.cancel"),
                            b -> ClientSocial.send("commission_stop", args)));
                } else if ("WORKING".equals(state)) {
                    addRenderableWidget(new CrystalButton(bx, by, 80, 16, Component.translatable("screen.amemaru.commission.stop"),
                            b -> ClientSocial.send("commission_stop", args)));
                }
            }
            case "working" -> {
                if ("WORKING".equals(state)) {
                    CompoundTag once = args.copy();
                    once.putInt("times", 1);
                    addRenderableWidget(new CrystalButton(bx, by, 76, 16, Component.translatable(enhance
                            ? "screen.amemaru.commission.enhance_once" : "screen.amemaru.commission.reroll_once"),
                            b -> ClientSocial.send("commission_attempt", once)));
                    CompoundTag batch = args.copy();
                    batch.putInt("times", ClientSocial.commissions().getInt("batch"));
                    addRenderableWidget(new CrystalButton(bx + 80, by, 64, 16, Component.translatable("screen.amemaru.commission.batch",
                            ClientSocial.commissions().getInt("batch")), b -> ClientSocial.send("commission_attempt", batch)));
                    addRenderableWidget(new CrystalButton(bx + 148, by, 44, 16, Component.translatable("screen.amemaru.commission.release"),
                            b -> ClientSocial.send("commission_release", args)));
                }
            }
            default -> {
            }
        }
    }

    private void initCreate() {
        int rx = left + LIST_W + 22;
        int y = top + 42;
        for (int i = 0; i < KINDS.length; i++) {
            String target = KINDS[i];
            CrystalButton button = addRenderableWidget(new CrystalButton(rx + i * 62, y, 58, 14,
                    Component.translatable("screen.amemaru.commission.kind." + target.toLowerCase(Locale.ROOT)), b -> {
                        kind = target;
                        targetIndex = 0;
                        rebuildWidgets();
                    }));
            button.active = !target.equals(kind);
        }
        y += 20;
        if (!"ENHANCE".equals(kind)) {
            addRenderableWidget(new CrystalButton(rx + 44, y - 1, 92, 12, targetLabel(), b -> {
                targetIndex = (targetIndex + 1) % Math.max(1, targetOptions().size());
                rebuildWidgets();
            }));
            addRenderableWidget(new CrystalButton(rx + 140, y - 1, 12, 12, Component.literal("−"), b -> adjustTarget(-1)));
            addRenderableWidget(new CrystalButton(rx + 176, y - 1, 12, 12, Component.literal("+"), b -> adjustTarget(1)));
            y += 16;
            addRenderableWidget(new CrystalButton(rx + 140, y - 1, 12, 12, Component.literal("−"), b -> {
                attempts = Math.max(1, attempts - (hasShiftDown() ? 10 : 1));
            }));
            addRenderableWidget(new CrystalButton(rx + 176, y - 1, 12, 12, Component.literal("+"), b -> {
                attempts = Math.min(Math.max(1, ClientSocial.commissions().getInt("maxAttempts")), attempts + (hasShiftDown() ? 10 : 1));
            }));
        } else {
            addRenderableWidget(new CrystalButton(rx + 140, y - 1, 12, 12, Component.literal("−"), b -> level = Math.max(1, level - 1)));
            addRenderableWidget(new CrystalButton(rx + 176, y - 1, 12, 12, Component.literal("+"),
                    b -> level = Math.min(Math.max(1, ClientSocial.commissions().getInt("maxEnhance")), level + 1)));
            y += 16;
        }
        y += 16;
        feeBox = new EditBox(font, rx + 44, y - 2, 70, 13, Component.translatable("screen.amemaru.commission.fee"));
        feeBox.setMaxLength(9);
        feeBox.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        feeBox.setValue(fee);
        feeBox.setResponder(text -> fee = text);
        addRenderableWidget(feeBox);
        addRenderableWidget(new CrystalButton(rx + 118, y - 2, 34, 13, Component.literal("+100"), b -> addFee(100)));
        addRenderableWidget(new CrystalButton(rx + 154, y - 2, 40, 13, Component.literal("+1,000"), b -> addFee(1000)));
        CrystalButton create = addRenderableWidget(new CrystalButton(left + W - 112, top + H - 24, 100, 16,
                Component.translatable("screen.amemaru.commission.create"), b -> {
                    ClientSocial.send("commission_create", formArgs());
                    pickSlot = -1;
                    locks.clear();
                    tab = "mine";
                    rebuildWidgets();
                }));
        create.active = pickSlot >= 0;
    }

    private void addFee(long amount) {
        long now = parse(fee);
        fee = String.valueOf(Math.min(999_999_999L, now + amount));
        if (feeBox != null) {
            feeBox.setValue(fee);
        }
    }

    private static long parse(String text) {
        try {
            return text.isEmpty() ? 0 : Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ================================================================ 依頼の中身

    /** 目標の候補（目標なし・品質・完璧・サブの種類ごと）。数値だけのときは、今あるサブの種類だけ。 */
    private List<String> targetOptions() {
        List<String> options = new ArrayList<>();
        options.add("NONE");
        options.add("QUALITY");
        options.add("PERFECT");
        ItemStack stack = picked();
        if (stack.isEmpty()) {
            return options;
        }
        AccessoryData.StatLine main = AccessoryData.main(stack);
        if ("VALUES".equals(kind)) {
            for (AccessoryData.StatLine sub : AccessoryData.subs(stack)) {
                options.add("STAT:" + sub.type().id());
            }
        } else {
            for (StatType type : StatType.all()) {
                if (main != null && type == main.type()) {
                    continue;
                }
                if (type == StatType.HP || type == StatType.DEFENSE || type == StatType.MP || type == StatType.ATTACK
                        || type == StatType.MAGIC || type == StatType.LOST_RESIST || type == StatType.SPEED || type == StatType.CRIT
                        || type == StatType.CRIT_DAMAGE || type == StatType.HEAL_POWER || type == StatType.COOLDOWN_REDUCTION) {
                    options.add("STAT:" + type.id());
                }
            }
        }
        return options;
    }

    private String targetOption() {
        List<String> options = targetOptions();
        return options.get(Math.max(0, Math.min(options.size() - 1, targetIndex)));
    }

    private Component targetLabel() {
        String option = targetOption();
        if (option.startsWith("STAT:")) {
            StatType type = StatType.byId(option.substring(5)).orElse(StatType.CRIT);
            return Component.translatable(type.translationKey());
        }
        return Component.translatable("screen.amemaru.commission.target." + option.toLowerCase(Locale.ROOT));
    }

    private void adjustTarget(int direction) {
        String option = targetOption();
        if (option.startsWith("STAT:")) {
            ratio = Math.max(50, Math.min(100, ratio + direction * 5));
        } else if ("QUALITY".equals(option)) {
            stars = Math.max(1, Math.min(5, stars + direction));
        }
    }

    private CompoundTag formArgs() {
        CompoundTag args = new CompoundTag();
        args.putInt("slot", pickSlot);
        args.putString("kind", kind);
        args.put("locks", new IntArrayTag(locks.stream().mapToInt(Integer::intValue).toArray()));
        if ("ENHANCE".equals(kind)) {
            args.putString("target", "ENHANCE");
            args.putInt("value", level);
        } else {
            String option = targetOption();
            if (option.startsWith("STAT:")) {
                args.putString("target", "STAT");
                args.putString("stat", option.substring(5));
                args.putInt("value", ratio);
            } else {
                args.putString("target", option);
                args.putInt("value", "QUALITY".equals(option) ? stars : 0);
            }
            args.putInt("attempts", attempts);
        }
        args.putLong("fee", parse(fee));
        return args;
    }

    private ItemStack picked() {
        Minecraft minecraft = Minecraft.getInstance();
        if (pickSlot < 0 || minecraft.player == null || pickSlot >= minecraft.player.getInventory().items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = minecraft.player.getInventory().items.get(pickSlot);
        return stack.getItem() instanceof AccessoryItem && AccessoryData.isRolled(stack) ? stack : ItemStack.EMPTY;
    }

    /** 持ち物の、依頼に出せるアクセサリー（持ち物の枠の番号）。 */
    private static List<Integer> accessorySlots() {
        List<Integer> list = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return list;
        }
        List<ItemStack> items = minecraft.player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.getItem() instanceof AccessoryItem && AccessoryData.isRolled(stack)) {
                list.add(i);
            }
        }
        return list;
    }

    @Override
    public void tick() {
        if (seen != ClientSocial.version() && !"create".equals(tab)) {
            rebuildWidgets();
        } else if (seen != ClientSocial.version()) {
            seen = ClientSocial.version();
        }
        if ("create".equals(tab) && pickSlot >= 0) {
            if (picked().isEmpty()) {
                pickSlot = -1;
                rebuildWidgets();
                return;
            }
            String key = formArgs().toString();
            long now = System.currentTimeMillis();
            if (!key.equals(quoteKey) && now - quoteSentAt > 300) {
                quoteKey = key;
                quoteSentAt = now;
                ClientSocial.send("commission_quote", formArgs());
            }
        }
    }

    // ================================================================ 描画

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + W, top + H);
        graphics.drawString(font, title, left + 12, top + 7, UiStyle.TEXT_HEADER, true);
        CompoundTag data = ClientSocial.commissions();
        Component info = data.getBoolean("crafter")
                ? Component.translatable("screen.amemaru.commission.crafter", data.getInt("discount"))
                : Component.translatable("screen.amemaru.commission.not_crafter");
        graphics.drawString(font, info, left + W - 28 - font.width(info), top + 7, data.getBoolean("crafter") ? UiStyle.TEXT_GOOD : UiStyle.TEXT_DIM, true);
        UiStyle.divider(graphics, left + 10, left + W - 10, top + 37);
        if ("create".equals(tab)) {
            renderCreate(graphics, mouseX, mouseY);
        } else {
            renderList(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderHover(graphics, mouseX, mouseY);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        ListTag list = list();
        int x = left + 12;
        int y = top + 42;
        if (list.isEmpty()) {
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.commission.empty." + tab).getString(), x, y + 2, W - 24,
                    UiStyle.TEXT_DIM, 4);
            return;
        }
        for (int i = scroll; i < Math.min(list.size(), scroll + ROWS); i++) {
            CompoundTag one = list.getCompound(i);
            boolean sel = i == selected;
            UiStyle.card(graphics, x, y, x + LIST_W, y + ROW - 2, stateColor(one.getString("state")), sel);
            ItemStack stack = ItemStack.of(one.getCompound("item"));
            graphics.renderItem(stack, x + 3, y + 3);
            String head = "#" + one.getInt("id") + " " + ("open".equals(tab) || "working".equals(tab) ? one.getString("owner")
                    : stack.getHoverName().getString());
            graphics.drawString(font, UiStyle.clip(font, head, LIST_W - 26), x + 22, y + 3, UiStyle.TEXT, false);
            String sub = Component.translatable("screen.amemaru.commission.row", kindName(one), one.getInt("attempts"), one.getInt("max"),
                    KnotTransfer.format(one.getLong("fee"))).getString();
            graphics.drawString(font, UiStyle.clip(font, sub, LIST_W - 26), x + 22, y + 12, UiStyle.TEXT_DIM, false);
            y += ROW;
        }
        if (list.size() > ROWS) {
            String more = (scroll + 1) + "-" + Math.min(list.size(), scroll + ROWS) + "/" + list.size();
            graphics.drawString(font, more, x, top + H - 14, UiStyle.TEXT_DIM, false);
        }
        renderDetail(graphics, list.getCompound(Math.max(0, Math.min(list.size() - 1, selected))));
    }

    private static int stateColor(String state) {
        return switch (state) {
            case "OPEN" -> 0x8FD3FF;
            case "WORKING" -> 0xFFD866;
            default -> 0x6E7891;
        };
    }

    private Component kindName(CompoundTag one) {
        return Component.translatable("screen.amemaru.commission.kind." + one.getString("kind").toLowerCase(Locale.ROOT));
    }

    private Component targetText(CompoundTag one) {
        String target = one.getString("target");
        return switch (target) {
            case "STAT" -> Component.translatable("screen.amemaru.commission.target_stat",
                    Component.translatable(StatType.byId(one.getString("stat")).orElse(StatType.CRIT).translationKey()), one.getInt("value"));
            case "QUALITY" -> Component.translatable("screen.amemaru.commission.target_quality", one.getInt("value"));
            case "PERFECT" -> Component.translatable("screen.amemaru.commission.target.perfect");
            case "ENHANCE" -> Component.translatable("screen.amemaru.commission.target_enhance", one.getInt("value"));
            default -> Component.translatable("screen.amemaru.commission.target.none");
        };
    }

    private void renderDetail(GuiGraphics graphics, CompoundTag one) {
        int x = left + LIST_W + 22;
        int y = top + 42;
        int w = W - LIST_W - 34;
        ItemStack stack = ItemStack.of(one.getCompound("item"));
        graphics.renderItem(stack, x, y);
        graphics.drawString(font, UiStyle.clip(font, stack.getHoverName().getString(), w - 20), x + 19, y, UiStyle.TEXT, true);
        graphics.drawString(font, Component.translatable("screen.amemaru.refine.quality", GearTooltips.stars(AccessoryData.storedQuality(stack)),
                GearTooltips.percent(AccessoryData.averageRatio(stack)), AccessoryData.enhance(stack)), x + 19, y + 9, UiStyle.TEXT_GOLD, true);
        y += 21;
        for (AccessoryData.StatLine sub : AccessoryData.subs(stack)) {
            String text = Component.translatable(sub.type().translationKey()).getString() + " " + sub.type().format(sub.value()) + " "
                    + GearTooltips.percent(sub.ratio()) + (sub.locked() ? " " + Component.translatable("screen.amemaru.commission.locked").getString() : "");
            graphics.drawString(font, UiStyle.clip(font, text, w), x, y, sub.locked() ? 0xFFFFE08A : 0xFF9FE8E0, false);
            y += 9;
        }
        y += 3;
        String who = Component.translatable("screen.amemaru.commission.who", one.getString("owner"),
                one.getString("crafter").isEmpty() ? "—" : one.getString("crafter")).getString();
        graphics.drawString(font, UiStyle.clip(font, who, w), x, y, UiStyle.TEXT, false);
        y += 10;
        graphics.drawString(font, UiStyle.clip(font, Component.translatable("screen.amemaru.commission.detail", kindName(one), targetText(one),
                one.getInt("attempts"), one.getInt("max")).getString(), w), x, y, UiStyle.TEXT_HEADER, false);
        y += 10;
        String money = Component.translatable("screen.amemaru.commission.money", KnotTransfer.format(one.getLong("fee")),
                KnotTransfer.format(one.getLong("escrow"))).getString();
        if (one.contains("escrow")) {
            graphics.drawString(font, UiStyle.clip(font, money, w), x, y, UiStyle.TEXT_DIM, false);
            y += 10;
        }
        if ("DONE".equals(one.getString("state"))) {
            graphics.drawString(font, Component.translatable("screen.amemaru.commission.ended",
                    Component.translatable("message.amemaru.commission.end." + one.getString("end").toLowerCase(Locale.ROOT))), x, y,
                    UiStyle.TEXT_GOLD, false);
            y += 10;
        }
        ListTag log = one.getList("log", Tag.TAG_STRING);
        int lines = Math.min(log.size(), Math.max(0, (top + H - 30 - y) / 9));
        for (int i = log.size() - lines; i < log.size(); i++) {
            Component line;
            try {
                line = Component.Serializer.fromJson(log.getString(i));
            } catch (RuntimeException e) {
                line = null;
            }
            if (line != null) {
                graphics.drawString(font, UiStyle.clip(font, line.getString(), w), x, y, UiStyle.TEXT_DIM, false);
                y += 9;
            }
        }
    }

    private void renderCreate(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left + 12;
        int y = top + 42;
        graphics.drawString(font, Component.translatable("screen.amemaru.commission.pick"), x, y, UiStyle.TEXT_HEADER, false);
        y += 11;
        List<Integer> slots = accessorySlots();
        if (slots.isEmpty()) {
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.commission.no_accessory").getString(), x, y, LIST_W, UiStyle.TEXT_DIM, 3);
        }
        Minecraft minecraft = Minecraft.getInstance();
        for (int i = 0; i < Math.min(32, slots.size()); i++) {
            int gx = x + (i % GRID_COLS) * GRID;
            int gy = y + (i / GRID_COLS) * GRID;
            int slot = slots.get(i);
            graphics.fill(gx, gy, gx + 17, gy + 17, slot == pickSlot ? 0xFFFFD866 : 0xFF26324D);
            graphics.fill(gx + 1, gy + 1, gx + 16, gy + 16, 0xFF0B1022);
            if (minecraft.player != null) {
                graphics.renderItem(minecraft.player.getInventory().items.get(slot), gx + 1, gy + 1);
            }
        }
        // 選んだ品と固定
        ItemStack stack = picked();
        int sy = top + 132;
        if (!stack.isEmpty()) {
            graphics.drawString(font, UiStyle.clip(font, stack.getHoverName().getString(), LIST_W), x, sy, UiStyle.TEXT, true);
            sy += 11;
            List<AccessoryData.StatLine> subs = AccessoryData.subs(stack);
            boolean enhance = "ENHANCE".equals(kind);
            for (int i = 0; i < subs.size(); i++) {
                AccessoryData.StatLine sub = subs.get(i);
                boolean locked = !enhance && locks.contains(i);
                String text = Component.translatable(sub.type().translationKey()).getString() + " " + sub.type().format(sub.value()) + " "
                        + GearTooltips.percent(sub.ratio());
                graphics.drawString(font, UiStyle.clip(font, text, LIST_W - 16), x, sy, locked ? 0xFFFFE08A : 0xFF9FE8E0, false);
                if (!enhance) {
                    lockBox(graphics, x + LIST_W - 12, sy - 1, locked);
                }
                sy += 10;
            }
        }
        // 右：中身
        int rx = left + LIST_W + 22;
        int ry = top + 62;
        if (!"ENHANCE".equals(kind)) {
            graphics.drawString(font, Component.translatable("screen.amemaru.commission.target"), rx, ry + 1, UiStyle.TEXT_HEADER, false);
            String option = targetOption();
            String value = option.startsWith("STAT:") ? ratio + "%" : "QUALITY".equals(option) ? "★" + stars : "—";
            graphics.drawString(font, value, rx + 158 - font.width(value) / 2 + 6, ry + 1, UiStyle.TEXT_GOLD, false);
            ry += 16;
            graphics.drawString(font, Component.translatable("screen.amemaru.commission.attempts"), rx, ry + 1, UiStyle.TEXT_HEADER, false);
            String count = String.valueOf(attempts);
            graphics.drawString(font, count, rx + 164 - font.width(count) / 2, ry + 1, UiStyle.TEXT_GOLD, false);
        } else {
            graphics.drawString(font, Component.translatable("screen.amemaru.commission.level"), rx, ry + 1, UiStyle.TEXT_HEADER, false);
            String text = "+" + level;
            graphics.drawString(font, text, rx + 164 - font.width(text) / 2, ry + 1, UiStyle.TEXT_GOLD, false);
            ry += 16;
        }
        ry += 16;
        graphics.drawString(font, Component.translatable("screen.amemaru.commission.fee"), rx, ry + 1, UiStyle.TEXT_HEADER, false);
        ry += 16;
        // 見積もり
        CompoundTag quote = ClientSocial.quote();
        int w = W - LIST_W - 34;
        if (stack.isEmpty()) {
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.commission.create_help").getString(), rx, ry, w,
                    UiStyle.TEXT_DIM, 6);
            return;
        }
        if (quote.getInt("slot") != pickSlot) {
            return;
        }
        if (quote.contains("error")) {
            UiStyle.wrapped(graphics, font, Component.translatable(quote.getString("error"), 3).getString(), rx, ry, w, UiStyle.TEXT_BAD, 3);
            return;
        }
        graphics.drawString(font, Component.translatable("screen.amemaru.commission.deposit", quote.getInt("attempts")), rx, ry,
                UiStyle.TEXT_HEADER, false);
        ry += 10;
        for (Tag tag : quote.getList("items", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) tag;
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(one.getString("id")));
            if (item == null) {
                continue;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(rx, ry - 1, 0);
            graphics.pose().scale(0.625F, 0.625F, 1.0F);
            graphics.renderItem(new ItemStack(item), 0, 0);
            graphics.pose().popPose();
            graphics.drawString(font, Component.translatable("screen.amemaru.workshop.cost", new ItemStack(item).getHoverName(), one.getInt("count"),
                    count(item)), rx + 12, ry, count(item) >= one.getInt("count") ? UiStyle.TEXT_GOOD : UiStyle.TEXT_BAD, false);
            ry += 10;
        }
        long total = quote.getLong("knots") + quote.getLong("fee");
        graphics.drawString(font, Component.translatable("screen.amemaru.commission.deposit_knots", KnotTransfer.format(quote.getLong("knots")),
                KnotTransfer.format(quote.getLong("fee")), KnotTransfer.format(total)), rx, ry,
                ClientPlayerState.get().knots() >= total ? UiStyle.TEXT_GOOD : UiStyle.TEXT_BAD, false);
        ry += 10;
        UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.commission.deposit_note").getString(), rx, ry + 2, w,
                UiStyle.TEXT_DIM, 3);
    }

    private static int count(Item item) {
        Minecraft minecraft = Minecraft.getInstance();
        int have = 0;
        if (minecraft.player != null) {
            for (ItemStack stack : minecraft.player.getInventory().items) {
                if (stack.is(item)) {
                    have += stack.getCount();
                }
            }
        }
        return have;
    }

    private static void lockBox(GuiGraphics graphics, int x, int y, boolean locked) {
        graphics.fill(x, y, x + 9, y + 9, locked ? 0xFFFFD866 : 0xFF3A4A6E);
        graphics.fill(x + 1, y + 1, x + 8, y + 8, locked ? 0xFF5E4A18 : 0xFF0B1022);
        if (locked) {
            graphics.fill(x + 2, y + 4, x + 7, y + 8, 0xFFFFD866);
            graphics.fill(x + 3, y + 2, x + 6, y + 3, 0xFFFFD866);
        }
    }

    private void renderHover(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!"create".equals(tab)) {
            ListTag list = list();
            int y = top + 42;
            for (int i = scroll; i < Math.min(list.size(), scroll + ROWS); i++) {
                if (mouseX >= left + 15 && mouseX < left + 31 && mouseY >= y + 3 && mouseY < y + 19) {
                    graphics.renderTooltip(font, ItemStack.of(list.getCompound(i).getCompound("item")), mouseX, mouseY);
                }
                y += ROW;
            }
            return;
        }
        List<Integer> slots = accessorySlots();
        Minecraft minecraft = Minecraft.getInstance();
        for (int i = 0; i < Math.min(32, slots.size()); i++) {
            int gx = left + 12 + (i % GRID_COLS) * GRID;
            int gy = top + 53 + (i / GRID_COLS) * GRID;
            if (mouseX >= gx && mouseX < gx + 17 && mouseY >= gy && mouseY < gy + 17 && minecraft.player != null) {
                graphics.renderTooltip(font, minecraft.player.getInventory().items.get(slots.get(i)), mouseX, mouseY);
            }
        }
    }

    // ================================================================ 操作

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if ("create".equals(tab)) {
                List<Integer> slots = accessorySlots();
                for (int i = 0; i < Math.min(32, slots.size()); i++) {
                    int gx = left + 12 + (i % GRID_COLS) * GRID;
                    int gy = top + 53 + (i / GRID_COLS) * GRID;
                    if (mouseX >= gx && mouseX < gx + 17 && mouseY >= gy && mouseY < gy + 17) {
                        pickSlot = slots.get(i);
                        locks.clear();
                        targetIndex = 0;
                        ItemStack stack = picked();
                        level = Math.min(Math.max(1, ClientSocial.commissions().getInt("maxEnhance")), AccessoryData.enhance(stack) + 3);
                        rebuildWidgets();
                        return true;
                    }
                }
                ItemStack stack = picked();
                if (!stack.isEmpty() && !"ENHANCE".equals(kind)) {
                    int sy = top + 143;
                    int subs = AccessoryData.subs(stack).size();
                    for (int i = 0; i < subs; i++) {
                        int bx = left + 12 + LIST_W - 12;
                        if (mouseX >= bx && mouseX < bx + 9 && mouseY >= sy - 1 && mouseY < sy + 8) {
                            if (locks.contains(i)) {
                                locks.remove(Integer.valueOf(i));
                            } else if (locks.size() < Math.min(3, subs - 1)) {
                                locks.add(i);
                            }
                            return true;
                        }
                        sy += 10;
                    }
                }
            } else {
                ListTag list = list();
                int y = top + 42;
                for (int i = scroll; i < Math.min(list.size(), scroll + ROWS); i++) {
                    if (mouseX >= left + 12 && mouseX < left + 12 + LIST_W && mouseY >= y && mouseY < y + ROW - 2) {
                        selected = i;
                        rebuildWidgets();
                        return true;
                    }
                    y += ROW;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!"create".equals(tab)) {
            int size = list().size();
            if (size > ROWS) {
                scroll = Math.max(0, Math.min(size - ROWS, scroll - (int) Math.signum(delta)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        ClientSocial.send("commission_close");
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** CIの撮影用：タブを選ぶ（依頼を出すなら、持ち物の最初のアクセサリーを選ぶ）。 */
    public void ciTab(String key) {
        tab = key;
        selected = 0;
        if ("create".equals(key)) {
            List<Integer> slots = accessorySlots();
            pickSlot = slots.isEmpty() ? -1 : slots.get(0);
            if (pickSlot >= 0 && AccessoryData.subs(picked()).size() > 1) {
                locks.clear();
                locks.add(0);
            }
            targetIndex = Math.min(3, Math.max(0, targetOptions().size() - 1));
        }
        rebuildWidgets();
    }
}

package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.client.ClientAdventure;
import jp.amemaru.lostconnection.client.ClientContent;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.equipment.GearItem;
import jp.amemaru.lostconnection.equipment.GearRules;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.WorkshopActionC2S;
import jp.amemaru.lostconnection.network.s2c.SyncContentS2C;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.workshop.ModRecipes;
import jp.amemaru.lostconnection.workshop.ProductionRules;
import jp.amemaru.lostconnection.workshop.WorkshopMenu;
import jp.amemaru.lostconnection.workshop.WorkshopRecipe;
import jp.amemaru.lostconnection.workshop.WorkshopService;
import jp.amemaru.lostconnection.workshop.WorkshopType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 工房の画面（武器工房・防具工房・装飾工房）。タブは「作る」「強化」「修理」。
 *
 * <ul>
 *   <li>作る：その工房の生産職のレシピの一覧（左）と、選んだレシピの素材・持っている数・大成功の確率（右）</li>
 *   <li>強化：台に置いた装備の、今の段と次の段のステータス・素材（失敗しない）</li>
 *   <li>修理：台に置いた装備の耐久と、修理の素材（生産職でない人は2倍）</li>
 * </ul>
 *
 * <p>まだその工房の生産職でない人には、職業の説明と「〇〇になる」ボタンを出す。操作はサーバーが確かめてから行う。</p>
 */
public class WorkshopScreen extends AbstractContainerScreen<WorkshopMenu> {
    private static final int ROWS = 4;
    private static final int ROW_HEIGHT = 18;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 50;
    private static final int LIST_W = 80;
    private static final int RIGHT_X = 94;
    private static final int RIGHT_Y = 50;

    private enum Tab {
        CRAFT, ENHANCE, REPAIR
    }

    private Tab tab = Tab.CRAFT;
    private final List<WorkshopRecipe> recipes = new ArrayList<>();
    private int selected;
    private int scroll;
    private Button actionButton;
    private Button joinButton;
    private final List<Button> tabButtons = new ArrayList<>();

    public WorkshopScreen(WorkshopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 262;
        imageHeight = 224;
        inventoryLabelX = WorkshopMenu.INVENTORY_X;
        inventoryLabelY = WorkshopMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        loadRecipes();
        tabButtons.clear();
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab target = tabs[i];
            CrystalButton button = new CrystalButton(leftPos + 8 + i * 50, topPos + 30, 46, 14,
                    Component.translatable("screen.amemaru.workshop.tab." + target.name().toLowerCase(Locale.ROOT)), pressed -> {
                        tab = target;
                        updateButtons();
                    });
            tabButtons.add(addRenderableWidget(button));
        }
        actionButton = addRenderableWidget(new CrystalButton(leftPos + imageWidth - 72, topPos + 112, 64, 16,
                Component.translatable("screen.amemaru.workshop.craft"), pressed -> act()));
        joinButton = addRenderableWidget(new CrystalButton(leftPos + RIGHT_X, topPos + 112, 120, 16,
                Component.translatable("screen.amemaru.workshop.join"), pressed ->
                ModNetwork.sendToServer(WorkshopActionC2S.of(WorkshopActionC2S.Action.JOIN))));
        updateButtons();
    }

    private void loadRecipes() {
        recipes.clear();
        if (Minecraft.getInstance().level == null) {
            return;
        }
        for (WorkshopRecipe recipe : Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(ModRecipes.WORKSHOP_TYPE.get())) {
            if (recipe.workshop() == menu.type()) {
                recipes.add(recipe);
            }
        }
        recipes.sort(Comparator.comparingInt(WorkshopRecipe::mastery).thenComparing(recipe -> recipe.getId().toString()));
        selected = Math.min(selected, Math.max(0, recipes.size() - 1));
    }

    // ---------------------------------------------------------------- 状態

    private boolean isMyJob() {
        SyncContentS2C.ProductionJobInfo job = ClientContent.jobFor(menu.type().id());
        return job != null && job.id().equals(ClientAdventure.productionJob());
    }

    private int mastery() {
        if (!isMyJob()) {
            return 0;
        }
        SyncContentS2C.ProductionJobInfo job = ClientContent.jobFor(menu.type().id());
        return ProductionRules.client().level(ClientAdventure.masteryExp(job.id()), ClientAdventure.chapter());
    }

    private void updateButtons() {
        for (int i = 0; i < tabButtons.size(); i++) {
            tabButtons.get(i).active = Tab.values()[i] != tab;
        }
        boolean job = isMyJob();
        joinButton.visible = !job && (tab == Tab.CRAFT || tab == Tab.ENHANCE);
        if (joinButton.visible) {
            SyncContentS2C.ProductionJobInfo info = ClientContent.jobFor(menu.type().id());
            joinButton.setMessage(Component.translatable("screen.amemaru.workshop.join", info == null ? "?" : info.name()));
        }
        actionButton.visible = !joinButton.visible;
        actionButton.setMessage(Component.translatable("screen.amemaru.workshop." + switch (tab) {
            case CRAFT -> "craft";
            case ENHANCE -> "enhance";
            case REPAIR -> "repair";
        }));
        actionButton.active = switch (tab) {
            case CRAFT -> !recipes.isEmpty() && mastery() >= recipes.get(selected).mastery();
            case ENHANCE, REPAIR -> !menu.workStack().isEmpty();
        };
    }

    private void act() {
        switch (tab) {
            case CRAFT -> {
                if (!recipes.isEmpty()) {
                    ModNetwork.sendToServer(new WorkshopActionC2S(WorkshopActionC2S.Action.CRAFT, recipes.get(selected).getId()));
                }
            }
            case ENHANCE -> ModNetwork.sendToServer(WorkshopActionC2S.of(WorkshopActionC2S.Action.ENHANCE));
            case REPAIR -> ModNetwork.sendToServer(WorkshopActionC2S.of(WorkshopActionC2S.Action.REPAIR));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    // ---------------------------------------------------------------- 描画

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        UiStyle.panel(graphics, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        UiStyle.divider(graphics, leftPos + 8, leftPos + imageWidth - 8, topPos + 46);
        UiStyle.card(graphics, leftPos + RIGHT_X - 2, topPos + RIGHT_Y - 2, leftPos + imageWidth - 6, topPos + 132, 0x8FD3FF, false);
        // 台
        slotFrame(graphics, leftPos + WorkshopMenu.WORK_X - 1, topPos + WorkshopMenu.WORK_Y - 1);
        // 持ち物
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slotFrame(graphics, leftPos + WorkshopMenu.INVENTORY_X - 1 + column * 18, topPos + WorkshopMenu.INVENTORY_Y - 1 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            slotFrame(graphics, leftPos + WorkshopMenu.INVENTORY_X - 1 + column * 18, topPos + WorkshopMenu.INVENTORY_Y + 57);
        }
    }

    private static void slotFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF26324D);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF0B1022);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 7, UiStyle.TEXT_HEADER, true);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiStyle.TEXT_DIM, true);
        graphics.drawString(font, jobLine(), 8, 18, UiStyle.TEXT_GOLD, true);
        graphics.drawString(font, Component.translatable("screen.amemaru.workshop.bench"), 8, WorkshopMenu.WORK_Y + 4,
                UiStyle.TEXT_DIM, true);
        if (menu.workStack().isEmpty()) {
            graphics.drawString(font, "＋", WorkshopMenu.WORK_X + 4, WorkshopMenu.WORK_Y + 4, 0x40FFFFFF, false);
        }
        switch (tab) {
            case CRAFT -> renderCraft(graphics, mouseX - leftPos, mouseY - topPos);
            case ENHANCE -> renderEnhance(graphics);
            case REPAIR -> renderRepair(graphics);
        }
        renderHelp(graphics);
    }

    private Component jobLine() {
        SyncContentS2C.ProductionJobInfo job = ClientContent.jobFor(menu.type().id());
        if (job == null) {
            return Component.translatable("screen.amemaru.workshop.no_job_def");
        }
        if (!isMyJob()) {
            return Component.translatable("screen.amemaru.workshop.not_job", job.name());
        }
        int exp = ClientAdventure.masteryExp(job.id());
        ProductionRules rules = ProductionRules.client();
        int level = mastery();
        int cap = rules.cap(ClientAdventure.chapter());
        if (level >= rules.maxMastery()) {
            return Component.translatable("screen.amemaru.workshop.job_max", job.name(), level);
        }
        return Component.translatable("screen.amemaru.workshop.job", job.name(), level, cap, exp, rules.threshold(level + 1));
    }

    private void renderCraft(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isMyJob()) {
            SyncContentS2C.ProductionJobInfo job = ClientContent.jobFor(menu.type().id());
            String text = job == null ? "" : job.description();
            UiStyle.wrapped(graphics, font, text, RIGHT_X + 2, RIGHT_Y + 2, imageWidth - RIGHT_X - 12, UiStyle.TEXT, 6);
        }
        if (recipes.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.workshop.no_recipes"), LIST_X, LIST_Y, UiStyle.TEXT_DIM, true);
            return;
        }
        int mastery = mastery();
        for (int i = 0; i < ROWS && scroll + i < recipes.size(); i++) {
            int index = scroll + i;
            WorkshopRecipe recipe = recipes.get(index);
            int y = LIST_Y + i * ROW_HEIGHT;
            boolean hover = mouseX >= LIST_X && mouseX < LIST_X + LIST_W && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (index == selected) {
                graphics.fill(LIST_X - 1, y - 1, LIST_X + LIST_W, y + ROW_HEIGHT - 1, 0x608FD3FF);
            } else if (hover) {
                graphics.fill(LIST_X - 1, y - 1, LIST_X + LIST_W, y + ROW_HEIGHT - 1, 0x308FD3FF);
            }
            ItemStack result = recipe.result();
            graphics.renderItem(result, LIST_X, y);
            int color = mastery >= recipe.mastery() ? UiStyle.TEXT : UiStyle.TEXT_BAD;
            graphics.drawString(font, UiStyle.clip(font, result.getHoverName().getString(), LIST_W - 20), LIST_X + 18, y + 4, color, true);
        }
        if (recipes.size() > ROWS) {
            graphics.drawString(font, (scroll + 1) + "-" + Math.min(recipes.size(), scroll + ROWS) + "/" + recipes.size(),
                    LIST_X, LIST_Y + ROWS * ROW_HEIGHT, UiStyle.TEXT_DIM, false);
        }
        if (!isMyJob()) {
            return;
        }
        WorkshopRecipe recipe = recipes.get(selected);
        ItemStack result = recipe.result();
        graphics.renderItem(result, RIGHT_X, RIGHT_Y);
        graphics.drawString(font, result.getHoverName(), RIGHT_X + 20, RIGHT_Y + 4, UiStyle.TEXT, true);
        int y = RIGHT_Y + 20;
        graphics.drawString(font, Component.translatable("screen.amemaru.workshop.need_mastery", recipe.mastery()),
                RIGHT_X, y, mastery >= recipe.mastery() ? UiStyle.TEXT_DIM : UiStyle.TEXT_BAD, true);
        y += 11;
        y = renderCosts(graphics, recipe.ingredients(), RIGHT_X, y);
        double chance = ProductionRules.client().greatChance(mastery, recipe.mastery());
        graphics.drawString(font, Component.translatable("screen.amemaru.workshop.great_chance",
                String.format(Locale.ROOT, "%.0f", chance)), RIGHT_X, Math.max(y + 2, 100), UiStyle.TEXT_GOLD, true);
    }

    private void renderEnhance(GuiGraphics graphics) {
        ItemStack stack = menu.workStack();
        if (stack.isEmpty()) {
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.workshop.put_enhance").getString(),
                    RIGHT_X + 2, RIGHT_Y + 2, imageWidth - RIGHT_X - 12, UiStyle.TEXT_DIM, 5);
            return;
        }
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.client();
        EquipmentDef def = snapshot.get(stack);
        if (def == null || !(stack.getItem() instanceof GearItem)) {
            graphics.drawString(font, Component.translatable("screen.amemaru.workshop.accessory_later"), RIGHT_X, RIGHT_Y,
                    UiStyle.TEXT_BAD, true);
            return;
        }
        GearRules rules = snapshot.rules();
        int enhance = GearData.enhance(stack);
        graphics.renderItem(stack, RIGHT_X, RIGHT_Y);
        graphics.drawString(font, stack.getHoverName(), RIGHT_X + 20, RIGHT_Y + 4, UiStyle.TEXT, true);
        if (enhance >= rules.maxEnhance()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.workshop.max_enhance"), RIGHT_X, RIGHT_Y + 22,
                    UiStyle.TEXT_GOLD, true);
            return;
        }
        ItemStack next = stack.copy();
        GearData.setEnhance(next, enhance + 1);
        int y = RIGHT_Y + 20;
        graphics.drawString(font, Component.translatable("screen.amemaru.workshop.enhance_step", enhance, enhance + 1),
                RIGHT_X, y, UiStyle.TEXT_GOLD, true);
        y += 11;
        for (StatType type : def.stats().keySet()) {
            Component line = Component.translatable("screen.amemaru.workshop.stat_change", Component.translatable(type.translationKey()),
                    type.format(GearData.value(stack, type, def, rules)), type.format(GearData.value(next, type, def, rules)));
            graphics.drawString(font, line, RIGHT_X, y, UiStyle.TEXT_GOOD, true);
            y += 10;
        }
        renderCosts(graphics, WorkshopService.enhanceCost(stack, def, rules), RIGHT_X, y + 2);
    }

    private void renderRepair(GuiGraphics graphics) {
        ItemStack stack = menu.workStack();
        if (stack.isEmpty()) {
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.workshop.put_repair").getString(),
                    RIGHT_X + 2, RIGHT_Y + 2, imageWidth - RIGHT_X - 12, UiStyle.TEXT_DIM, 5);
            return;
        }
        EquipmentDefs.Snapshot snapshot = EquipmentDefs.client();
        EquipmentDef def = snapshot.get(stack);
        if (def == null) {
            return;
        }
        graphics.renderItem(stack, RIGHT_X, RIGHT_Y);
        graphics.drawString(font, stack.getHoverName(), RIGHT_X + 20, RIGHT_Y + 4, UiStyle.TEXT, true);
        if (def.durability() <= 0) {
            graphics.drawString(font, Component.translatable("tooltip.amemaru.unbreakable"), RIGHT_X, RIGHT_Y + 22, UiStyle.TEXT_DIM, true);
            return;
        }
        int durability = GearData.durability(stack, def);
        graphics.drawString(font, Component.translatable("tooltip.amemaru.durability", durability, def.durability()), RIGHT_X,
                RIGHT_Y + 20, durability <= 0 ? UiStyle.TEXT_BAD : UiStyle.TEXT, true);
        boolean crafter = isMyJob();
        double multiplier = crafter ? 1.0 : snapshot.rules().nonCrafterRepair();
        graphics.drawString(font, Component.translatable(crafter ? "screen.amemaru.workshop.repair_crafter"
                        : "screen.amemaru.workshop.repair_other", String.format(Locale.ROOT, "%.0f", multiplier)), RIGHT_X, RIGHT_Y + 31,
                UiStyle.TEXT_DIM, true);
        renderCosts(graphics, WorkshopService.repairCost(stack, def, multiplier), RIGHT_X, RIGHT_Y + 44);
    }

    /** 素材の一覧（アイコン・名前・要る数／持っている数）。 */
    private int renderCosts(GuiGraphics graphics, List<EquipmentDef.Cost> costs, int x, int y) {
        Inventory inventory = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getInventory();
        for (EquipmentDef.Cost cost : costs) {
            Item item = ForgeRegistries.ITEMS.getValue(cost.item());
            if (item == null) {
                continue;
            }
            int have = 0;
            if (inventory != null) {
                for (ItemStack stack : inventory.items) {
                    if (stack.is(item)) {
                        have += stack.getCount();
                    }
                }
            }
            graphics.pose().pushPose();
            graphics.pose().translate(x, y - 1, 0);
            graphics.pose().scale(0.625F, 0.625F, 1.0F);
            graphics.renderItem(new ItemStack(item), 0, 0);
            graphics.pose().popPose();
            Component line = Component.translatable("screen.amemaru.workshop.cost", new ItemStack(item).getHoverName(), cost.count(), have);
            graphics.drawString(font, line, x + 12, y, have >= cost.count() ? UiStyle.TEXT_GOOD : UiStyle.TEXT_BAD, true);
            y += 10;
        }
        return y;
    }

    private void renderHelp(GuiGraphics graphics) {
        String key = switch (tab) {
            case CRAFT -> "screen.amemaru.workshop.help_craft";
            case ENHANCE -> "screen.amemaru.workshop.help_enhance";
            case REPAIR -> "screen.amemaru.workshop.help_repair";
        };
        UiStyle.wrapped(graphics, font, Component.translatable(key, Component.translatable(menu.type().translationKey())).getString(),
                8, WorkshopMenu.WORK_Y + 24, WorkshopMenu.INVENTORY_X - 14, UiStyle.TEXT_DIM, 9);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (tab == Tab.CRAFT) {
            int localX = mouseX - leftPos;
            int localY = mouseY - topPos;
            for (int i = 0; i < ROWS && scroll + i < recipes.size(); i++) {
                int y = LIST_Y + i * ROW_HEIGHT;
                if (localX >= LIST_X && localX < LIST_X + 16 && localY >= y && localY < y + 16) {
                    graphics.renderTooltip(font, recipes.get(scroll + i).result(), mouseX, mouseY);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.CRAFT && button == 0) {
            double localX = mouseX - leftPos;
            double localY = mouseY - topPos;
            for (int i = 0; i < ROWS && scroll + i < recipes.size(); i++) {
                int y = LIST_Y + i * ROW_HEIGHT;
                if (localX >= LIST_X && localX < LIST_X + LIST_W && localY >= y && localY < y + ROW_HEIGHT) {
                    selected = scroll + i;
                    updateButtons();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.CRAFT && recipes.size() > ROWS) {
            scroll = Math.max(0, Math.min(recipes.size() - ROWS, scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** 画面のタブを外から選ぶ（CIの撮影用）。 */
    public void selectTab(int index) {
        tab = Tab.values()[Math.max(0, Math.min(Tab.values().length - 1, index))];
        updateButtons();
    }
}

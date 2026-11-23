package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.accessory.AccessoryData;
import jp.amemaru.lostconnection.accessory.AccessoryItem;
import jp.amemaru.lostconnection.client.ClientAdventure;
import jp.amemaru.lostconnection.client.ClientContent;
import jp.amemaru.lostconnection.client.ClientDefinitions;
import jp.amemaru.lostconnection.client.ClientPlayerState;
import jp.amemaru.lostconnection.client.ClientRefine;
import jp.amemaru.lostconnection.client.GearTooltips;
import jp.amemaru.lostconnection.equipment.EquipmentDef;
import jp.amemaru.lostconnection.equipment.EquipmentDefs;
import jp.amemaru.lostconnection.equipment.GearData;
import jp.amemaru.lostconnection.equipment.GearItem;
import jp.amemaru.lostconnection.equipment.GearRules;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.AccessoryOperationC2S;
import jp.amemaru.lostconnection.network.c2s.SocialActionC2S;
import jp.amemaru.lostconnection.network.c2s.WorkshopActionC2S;
import jp.amemaru.lostconnection.network.s2c.SyncContentS2C;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
import jp.amemaru.lostconnection.player.StatType;
import jp.amemaru.lostconnection.workshop.ModRecipes;
import jp.amemaru.lostconnection.workshop.ProductionRules;
import jp.amemaru.lostconnection.workshop.WorkshopMenu;
import jp.amemaru.lostconnection.workshop.WorkshopRecipe;
import jp.amemaru.lostconnection.workshop.WorkshopService;
import jp.amemaru.lostconnection.workshop.WorkshopType;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 工房の画面（武器工房・防具工房・装飾工房）。
 *
 * <ul>
 *   <li>武器工房・防具工房のタブ：「作る」「強化」「修理」</li>
 *   <li>装飾工房のタブ（Phase 6 その4）：「作る」「厳選」「強化」「分解」「依頼」</li>
 *   <li>作る：その工房の生産職のレシピの一覧（左）と、選んだレシピの素材・持っている数・大成功の確率（右）</li>
 *   <li>強化：台に置いた装備の、今の段と次の段のステータス・素材（失敗しない）。アクセサリーはメイン+5%ずつと、
 *       +3・+6・+9・+12 でサブが増える・伸びる</li>
 *   <li>修理：台に置いた装備の耐久と、修理の素材（生産職でない人は2倍）</li>
 *   <li>厳選：台に置いたアクセサリーのサブを「数値だけ」「種類ごと」振り直す。サブの右の錠で固定する（留め糸）</li>
 *   <li>分解：台に置いたアクセサリーを素材に戻す（2回押して確かめる）</li>
 *   <li>依頼：装飾師への厳選の依頼（依頼台の画面を開く）</li>
 * </ul>
 *
 * <p>まだその工房の生産職でない人には、職業の説明と「〇〇になる」ボタンを出す。操作はサーバーが確かめてから行う。
 * 厳選の費用は、サーバーが計算した見積もり（{@link ClientRefine}）を出す。</p>
 */
public class WorkshopScreen extends AbstractContainerScreen<WorkshopMenu> {
    // 11月23日：画面の並びを見直した（340×234）。上：題・生産職の行・タブ。左の列（幅114）：台 → 作る品の一覧・厳選の
    // ボタンと費用・説明。右：札（品の情報。右下に操作のボタン）→ その下に持ち物。文字が台・持ち物・ボタンと重ならない
    private static final int WIDTH = 340;
    private static final int HEIGHT = 234;
    private static final int ROWS = 4;
    private static final int ROW_HEIGHT = 18;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 74;
    private static final int LIST_W = 114;
    /** 左の列の幅（右の札の横）。札より下（持ち物の左）は INVENTORY_X − 14 まで使える。 */
    private static final int LEFT_W = 114;
    private static final int RIGHT_X = 134;
    private static final int RIGHT_Y = 50;
    /** 右の札の下の端（持ち物の見出しより上）。 */
    private static final int CARD_BOTTOM = 138;
    /** 操作のボタン（札の右下）。 */
    private static final int ACTION_X = WIDTH - 74;
    private static final int ACTION_Y = 120;
    /** 厳選のサブの行（固定の錠の位置）。 */
    private static final int LINES_Y = 82;
    private static final int LOCK_X = WIDTH - 21;
    /** 分解の確かめ（2回目を押せる時間）。 */
    private static final long CONFIRM_MILLIS = 4000L;
    /** 職業の絞り込みのボタン（タブの行の右のはし。武器・防具の工房はタブが3つなので、右があいている）。 */
    private static final int FILTER_W = 112;
    private static final int FILTER_X = WIDTH - 8 - FILTER_W;
    private static final int TAB_Y = 30;

    /**
     * 作る品の一覧の、職業の絞り込み（Phase 8 その2：3階からは職業ごとの防具と武器になる）。null は「すべて」。
     * 初めは自分の職業。押すたびに、次の職業 →…→「すべて」と変わる。選んだものは、画面を閉じても覚えておく。
     * 職業の決まっていない品（アクセサリーなど）は、いつも出す。
     */
    private static ResourceLocation jobFilter;
    private static boolean jobFilterChosen;

    private enum Tab {
        CRAFT, REFINE, ENHANCE, REPAIR, DISMANTLE, COMMISSION
    }

    private final List<Tab> tabs;
    private Tab tab = Tab.CRAFT;
    private final List<WorkshopRecipe> recipes = new ArrayList<>();
    private int selected;
    private int scroll;
    private Button actionButton;
    private Button joinButton;
    private Button valuesButton;
    private Button typesButton;
    private Button commissionButton;
    private Button filterButton;
    /** この工房に、職業の決まった品のレシピがあるか（ないなら、絞り込みのボタンを出さない）。 */
    private boolean hasJobGear;
    private final List<Button> tabButtons = new ArrayList<>();
    private long confirmUntil;
    /** 振り直す前の行（変わった行を光らせる）。 */
    private String lastLines = "";
    private final List<String> beforeLines = new ArrayList<>();
    private long changedAt;

    public WorkshopScreen(WorkshopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
        inventoryLabelX = WorkshopMenu.INVENTORY_X;
        inventoryLabelY = WorkshopMenu.INVENTORY_Y - 11;
        tabs = menu.type() == WorkshopType.ACCESSORY
                ? List.of(Tab.CRAFT, Tab.REFINE, Tab.ENHANCE, Tab.DISMANTLE, Tab.COMMISSION)
                : List.of(Tab.CRAFT, Tab.ENHANCE, Tab.REPAIR);
    }

    private boolean accessory() {
        return menu.type() == WorkshopType.ACCESSORY;
    }

    @Override
    protected void init() {
        super.init();
        if (!jobFilterChosen) {
            jobFilter = currentJob();
        }
        loadRecipes();
        tabButtons.clear();
        for (int i = 0; i < tabs.size(); i++) {
            Tab target = tabs.get(i);
            CrystalButton button = new CrystalButton(leftPos + 8 + i * 64, topPos + TAB_Y, 60, 14,
                    Component.translatable("screen.amemaru.workshop.tab." + target.name().toLowerCase(Locale.ROOT)), pressed -> {
                        tab = target;
                        confirmUntil = 0;
                        updateButtons();
                    });
            tabButtons.add(addRenderableWidget(button));
        }
        actionButton = addRenderableWidget(new CrystalButton(leftPos + ACTION_X, topPos + ACTION_Y, 66, 16,
                Component.translatable("screen.amemaru.workshop.craft"), pressed -> act()));
        joinButton = addRenderableWidget(new CrystalButton(leftPos + RIGHT_X, topPos + ACTION_Y, 120, 16,
                Component.translatable("screen.amemaru.workshop.join"), pressed ->
                ModNetwork.sendToServer(WorkshopActionC2S.of(WorkshopActionC2S.Action.JOIN))));
        valuesButton = addRenderableWidget(new CrystalButton(leftPos + 8, topPos + LIST_Y, LEFT_W, 14,
                Component.translatable("screen.amemaru.refine.values"), pressed ->
                ModNetwork.sendToServer(new AccessoryOperationC2S(AccessoryOperationC2S.Action.REROLL_VALUES, 0))));
        typesButton = addRenderableWidget(new CrystalButton(leftPos + 8, topPos + LIST_Y + 17, LEFT_W, 14,
                Component.translatable("screen.amemaru.refine.types"), pressed ->
                ModNetwork.sendToServer(new AccessoryOperationC2S(AccessoryOperationC2S.Action.REROLL_TYPES, 0))));
        commissionButton = addRenderableWidget(new CrystalButton(leftPos + RIGHT_X + 4, topPos + ACTION_Y, 150, 16,
                Component.translatable("screen.amemaru.commission.open"), pressed -> openCommissionBoard()));
        filterButton = addRenderableWidget(new CrystalButton(leftPos + FILTER_X, topPos + TAB_Y, FILTER_W, 14,
                filterLabel(), pressed -> cycleFilter()));
        filterButton.setTooltip(Tooltip.create(Component.translatable("screen.amemaru.workshop.filter_help")));
        if (accessory()) {
            ModNetwork.sendToServer(new AccessoryOperationC2S(AccessoryOperationC2S.Action.QUOTE, 0));
        }
        updateButtons();
    }

    private CompoundTag posArgs() {
        CompoundTag args = new CompoundTag();
        args.putLong("pos", menu.pos().asLong());
        return args;
    }

    /**
     * 依頼台を開く。先に工房の画面を閉じる（台の上の物は持ち物に戻る。依頼に出す品を持ち物から選べるように）。
     * 閉じる知らせと開く知らせは、この順にサーバーで処理される。
     */
    public void openCommissionBoard() {
        CompoundTag args = posArgs();
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
        }
        ModNetwork.sendToServer(new SocialActionC2S("commission_open", args));
    }

    private void loadRecipes() {
        recipes.clear();
        hasJobGear = false;
        if (Minecraft.getInstance().level == null) {
            return;
        }
        EquipmentDefs.Snapshot gear = EquipmentDefs.client();
        for (WorkshopRecipe recipe : Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(ModRecipes.WORKSHOP_TYPE.get())) {
            if (recipe.workshop() != menu.type()) {
                continue;
            }
            EquipmentDef def = gear.get(recipe.result());
            boolean jobGear = def != null && def.hasJobRequirement();
            hasJobGear |= jobGear;
            if (jobGear && jobFilter != null && !def.jobs().contains(jobFilter)) {
                continue;
            }
            recipes.add(recipe);
        }
        recipes.sort(Comparator.comparingInt(WorkshopRecipe::mastery).thenComparing(recipe -> recipe.getId().toString()));
        selected = Math.min(selected, Math.max(0, recipes.size() - 1));
    }

    /** 絞り込みを次の職業へ（最後の職業の次は「すべて」、その次は最初の職業）。 */
    private void cycleFilter() {
        List<SyncDefinitionsS2C.JobInfo> jobs = ClientDefinitions.get().jobs();
        int at = -1;
        for (int i = 0; i < jobs.size(); i++) {
            if (jobs.get(i).id().equals(jobFilter)) {
                at = i;
            }
        }
        jobFilter = at + 1 >= jobs.size() ? null : jobs.get(at + 1).id();
        jobFilterChosen = true;
        selected = 0;
        scroll = 0;
        loadRecipes();
        filterButton.setMessage(filterLabel());
        updateButtons();
    }

    private static Component filterLabel() {
        return Component.translatable("screen.amemaru.workshop.filter", jobFilter == null
                ? Component.translatable("screen.amemaru.workshop.filter_all") : Component.literal(jobName(jobFilter)));
    }

    private static ResourceLocation currentJob() {
        int index = ClientPlayerState.get().jobIndex();
        SyncDefinitionsS2C.JobInfo job = index < 0 ? null : ClientDefinitions.get().job(index);
        return job == null ? null : job.id();
    }

    private static String jobName(ResourceLocation id) {
        for (SyncDefinitionsS2C.JobInfo job : ClientDefinitions.get().jobs()) {
            if (job.id().equals(id)) {
                return job.name();
            }
        }
        return id.getPath();
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

    /** 台に、抽選済みのアクセサリーがあり、見積もりが届いているか。 */
    private boolean hasAccessory() {
        ItemStack stack = menu.workStack();
        return accessory() && stack.getItem() instanceof AccessoryItem && AccessoryData.isRolled(stack)
                && ClientRefine.quote().getBoolean("has");
    }

    private void updateButtons() {
        for (int i = 0; i < tabButtons.size(); i++) {
            tabButtons.get(i).active = tabs.get(i) != tab;
        }
        boolean job = isMyJob();
        // 装飾工房の強化は、厳選の一つで誰でもできる（Phase 6 その4）。武器・防具の強化は、その系統の生産職だけ
        joinButton.visible = !job && (tab == Tab.CRAFT || (tab == Tab.ENHANCE && !accessory()));
        if (joinButton.visible) {
            SyncContentS2C.ProductionJobInfo info = ClientContent.jobFor(menu.type().id());
            joinButton.setMessage(Component.translatable("screen.amemaru.workshop.join", info == null ? "?" : info.name()));
        }
        boolean actionTab = tab == Tab.CRAFT || tab == Tab.ENHANCE || tab == Tab.REPAIR || tab == Tab.DISMANTLE;
        actionButton.visible = actionTab && !joinButton.visible;
        boolean confirming = tab == Tab.DISMANTLE && Util.getMillis() < confirmUntil;
        actionButton.setMessage(Component.translatable(switch (tab) {
            case CRAFT -> "screen.amemaru.workshop.craft";
            case ENHANCE -> "screen.amemaru.workshop.enhance";
            case REPAIR -> "screen.amemaru.workshop.repair";
            case DISMANTLE -> confirming ? "screen.amemaru.refine.dismantle_confirm" : "screen.amemaru.refine.dismantle";
            default -> "screen.amemaru.workshop.craft";
        }));
        actionButton.active = switch (tab) {
            case CRAFT -> !recipes.isEmpty() && mastery() >= recipes.get(selected).mastery();
            case ENHANCE -> accessory() ? hasAccessory() && ClientRefine.quote().contains("enhance") : !menu.workStack().isEmpty();
            case REPAIR -> !menu.workStack().isEmpty();
            case DISMANTLE -> hasAccessory();
            default -> false;
        };
        boolean refine = tab == Tab.REFINE;
        valuesButton.visible = refine;
        typesButton.visible = refine;
        boolean canReroll = hasAccessory() && ClientRefine.quote().getBoolean("canReroll");
        valuesButton.active = canReroll;
        typesButton.active = canReroll;
        commissionButton.visible = tab == Tab.COMMISSION;
        filterButton.visible = tab == Tab.CRAFT && hasJobGear && 8 + tabs.size() * 64 <= FILTER_X;
    }

    private void act() {
        switch (tab) {
            case CRAFT -> {
                if (!recipes.isEmpty()) {
                    ModNetwork.sendToServer(new WorkshopActionC2S(WorkshopActionC2S.Action.CRAFT, recipes.get(selected).getId()));
                }
            }
            case ENHANCE -> {
                if (accessory()) {
                    ModNetwork.sendToServer(new AccessoryOperationC2S(AccessoryOperationC2S.Action.ENHANCE, 0));
                } else {
                    ModNetwork.sendToServer(WorkshopActionC2S.of(WorkshopActionC2S.Action.ENHANCE));
                }
            }
            case REPAIR -> ModNetwork.sendToServer(WorkshopActionC2S.of(WorkshopActionC2S.Action.REPAIR));
            case DISMANTLE -> {
                // 取り消せないので、2回押して確かめる
                if (Util.getMillis() < confirmUntil) {
                    confirmUntil = 0;
                    ModNetwork.sendToServer(new AccessoryOperationC2S(AccessoryOperationC2S.Action.DISMANTLE, 0));
                } else {
                    confirmUntil = Util.getMillis() + CONFIRM_MILLIS;
                }
            }
            default -> {
            }
        }
        updateButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        trackChanges();
        updateButtons();
    }

    /** 台の品の行が変わったら、変わる前の行を覚えておく（変わった行を少しの間光らせる）。 */
    private void trackChanges() {
        ItemStack stack = menu.workStack();
        String now = accessory() && stack.getItem() instanceof AccessoryItem ? linesKey(stack) : "";
        if (!now.equals(lastLines)) {
            if (!lastLines.isEmpty() && !now.isEmpty() && sameItem(lastLines, now)) {
                beforeLines.clear();
                for (String part : lastLines.split("\\|")) {
                    beforeLines.add(part);
                }
                changedAt = Util.getMillis();
            } else {
                beforeLines.clear();
            }
            lastLines = now;
        }
    }

    private static boolean sameItem(String a, String b) {
        return a.split("\\|").length > 0 && b.split("\\|").length > 0 && a.split("\\|")[0].equals(b.split("\\|")[0]);
    }

    private static String linesKey(ItemStack stack) {
        StringBuilder builder = new StringBuilder(ForgeRegistries.ITEMS.getKey(stack.getItem()) + "#" + AccessoryData.generation(stack));
        for (AccessoryData.StatLine line : AccessoryData.subs(stack)) {
            builder.append('|').append(line.type().id()).append(':').append(line.value());
        }
        return builder.toString();
    }

    /** そのサブの行が、さっきの振り直しで変わったか。 */
    private boolean changed(int index, AccessoryData.StatLine line) {
        if (beforeLines.isEmpty() || Util.getMillis() - changedAt > 1600L) {
            return false;
        }
        int at = index + 1;
        return at >= beforeLines.size() || !beforeLines.get(at).equals(line.type().id() + ":" + line.value());
    }

    // ---------------------------------------------------------------- 描画

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        UiStyle.panel(graphics, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        UiStyle.divider(graphics, leftPos + 8, leftPos + imageWidth - 8, topPos + 46);
        UiStyle.card(graphics, leftPos + RIGHT_X - 2, topPos + RIGHT_Y - 2, leftPos + imageWidth - 6, topPos + CARD_BOTTOM, 0x8FD3FF, false);
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
        int localX = mouseX - leftPos;
        int localY = mouseY - topPos;
        switch (tab) {
            case CRAFT -> renderCraft(graphics, localX, localY);
            case ENHANCE -> {
                if (accessory()) {
                    renderAccessoryEnhance(graphics);
                } else {
                    renderEnhance(graphics);
                }
            }
            case REPAIR -> renderRepair(graphics);
            case REFINE -> renderRefine(graphics, localX, localY);
            case DISMANTLE -> renderDismantle(graphics);
            case COMMISSION -> renderCommissionTab(graphics);
        }
        if (tab != Tab.REFINE) {
            renderHelp(graphics);
        }
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
            Component none = hasJobGear && jobFilter != null
                    ? Component.translatable("screen.amemaru.workshop.no_recipes_job", jobName(jobFilter))
                    : Component.translatable("screen.amemaru.workshop.no_recipes");
            graphics.drawString(font, none, LIST_X, LIST_Y, UiStyle.TEXT_DIM, true);
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
            graphics.drawString(font, Component.translatable("screen.amemaru.workshop.list_page", scroll + 1,
                            Math.min(recipes.size(), scroll + ROWS), recipes.size()),
                    LIST_X, LIST_Y + ROWS * ROW_HEIGHT + 2, UiStyle.TEXT_DIM, false);
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
        int chanceY = Math.max(y + 2, 100);
        graphics.drawString(font, UiStyle.clip(font, Component.translatable("screen.amemaru.workshop.great_chance",
                String.format(Locale.ROOT, "%.0f", chance)).getString(), textWidth(RIGHT_X, chanceY)), RIGHT_X, chanceY, UiStyle.TEXT_GOLD, true);
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
            graphics.drawString(font, Component.translatable("screen.amemaru.workshop.wrong_bench"), RIGHT_X, RIGHT_Y,
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
        // 数値は整数に丸めるため、1段では変わらないこともある。倍率（+4%ずつ）も出して、上がっていることが分かるようにする
        graphics.drawString(font, Component.translatable("screen.amemaru.workshop.enhance_step", enhance, enhance + 1,
                        String.format(Locale.ROOT, "%.0f", enhance * rules.enhancePercent()),
                        String.format(Locale.ROOT, "%.0f", (enhance + 1) * rules.enhancePercent())),
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

    // ---------------------------------------------------------------- 装飾工房（Phase 6 その4）

    /** 台のアクセサリーの名前と品質の行（右の札の上）。置いていなければ案内を出して false。 */
    private boolean accessoryHeader(GuiGraphics graphics, String emptyKey) {
        ItemStack stack = menu.workStack();
        if (!hasAccessory()) {
            String key = stack.isEmpty() ? emptyKey
                    : stack.getItem() instanceof AccessoryItem ? "screen.amemaru.refine.waiting" : "screen.amemaru.refine.not_accessory";
            UiStyle.wrapped(graphics, font, Component.translatable(key).getString(), RIGHT_X + 2, RIGHT_Y + 2,
                    imageWidth - RIGHT_X - 12, UiStyle.TEXT_DIM, 6);
            return false;
        }
        graphics.renderItem(stack, RIGHT_X, RIGHT_Y);
        graphics.drawString(font, UiStyle.clip(font, stack.getHoverName().getString(), imageWidth - RIGHT_X - 30), RIGHT_X + 19,
                RIGHT_Y, UiStyle.TEXT, true);
        int quality = AccessoryData.storedQuality(stack);
        Component info = Component.translatable("screen.amemaru.refine.quality", GearTooltips.stars(quality),
                GearTooltips.percent(AccessoryData.averageRatio(stack)), AccessoryData.enhance(stack));
        graphics.drawString(font, info, RIGHT_X + 19, RIGHT_Y + 10,
                AccessoryData.storedPerfect(stack) ? 0xFFD98CFF : quality >= 4 ? UiStyle.TEXT_GOLD : UiStyle.TEXT_DIM, true);
        return true;
    }

    /** 厳選：メインとサブの行・固定の錠・費用。 */
    private void renderRefine(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean has = accessoryHeader(graphics, "screen.amemaru.refine.put");
        if (!has) {
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.refine.help").getString(), 8, LIST_Y + 36,
                    LEFT_W, UiStyle.TEXT_DIM, 11);
            return;
        }
        ItemStack stack = menu.workStack();
        CompoundTag quote = ClientRefine.quote();
        AccessoryData.StatLine main = AccessoryData.main(stack);
        int y = LINES_Y - 10;
        if (main != null) {
            graphics.drawString(font, lineText(main, true, stack, quote), RIGHT_X, y, 0xFF8FB8FF, true);
        }
        List<AccessoryData.StatLine> subs = AccessoryData.subs(stack);
        for (int i = 0; i < subs.size(); i++) {
            AccessoryData.StatLine line = subs.get(i);
            int ly = LINES_Y + i * 10;
            if (changed(i, line)) {
                graphics.fill(RIGHT_X - 1, ly - 1, LOCK_X - 2, ly + 9, 0x50C07CFF);
            }
            int color = line.locked() ? 0xFFFFE08A : 0xFF9FE8E0;
            graphics.drawString(font, UiStyle.clip(font, lineText(line, false, stack, quote).getString(), LOCK_X - RIGHT_X - 4), RIGHT_X,
                    ly, color, true);
            drawLock(graphics, LOCK_X, ly - 1, line.locked(), mouseX >= LOCK_X && mouseX < LOCK_X + 10 && mouseY >= ly - 1 && mouseY < ly + 9);
        }
        int locks = quote.getInt("locks");
        int maxLocks = quote.getInt("maxLocks");
        Component lockLine = maxLocks <= 0 ? Component.translatable("screen.amemaru.refine.lock_none")
                : Component.translatable("screen.amemaru.refine.lock_count", locks, maxLocks);
        graphics.drawString(font, lockLine, RIGHT_X, LINES_Y + 4 * 10 + 2, UiStyle.TEXT_DIM, true);
        int up = quote.getInt("costUp");
        if (up > 0) {
            Component upLine = Component.translatable("screen.amemaru.refine.cost_up", up);
            graphics.drawString(font, upLine, imageWidth - 10 - font.width(upLine), LINES_Y + 4 * 10 + 2, 0xFFFFB37A, true);
        }
        // 左：割引と費用（数値だけ・種類ごと）。ボタン2つの下から
        int cy = LIST_Y + 36;
        int discount = quote.getInt("discount");
        if (discount > 0) {
            graphics.drawString(font, Component.translatable("screen.amemaru.refine.discount", discount), 8, cy, UiStyle.TEXT_GOOD, true);
            cy += 12;
        }
        cy = costBlock(graphics, "screen.amemaru.refine.cost_values", quote.getCompound("values"), 8, cy);
        costBlock(graphics, "screen.amemaru.refine.cost_types", quote.getCompound("types"), 8, cy + 2);
    }

    /** 1行の文（メイン：種類・値（強化の伸び）・割合。サブ：種類・値・幅・割合・回数分）。 */
    private Component lineText(AccessoryData.StatLine line, boolean isMain, ItemStack stack, CompoundTag quote) {
        StatType type = line.type();
        Component name = Component.translatable(type.translationKey());
        if (isMain) {
            double value = AccessoryData.effectiveMain(stack, line);
            int enhance = AccessoryData.enhance(stack);
            String bonus = enhance > 0 ? String.format(Locale.ROOT, "（+%.0f%%）", enhance * AccessoryData.mainPercent(stack)) : "";
            return Component.translatable("screen.amemaru.refine.main_line", name, type.format(value) + bonus,
                    GearTooltips.percent(line.ratio()));
        }
        String rolls = line.rolls() > 1 ? " ×" + line.rolls() : "";
        return Component.translatable("screen.amemaru.refine.sub_line", name, type.format(line.value()), type.format(line.min()),
                type.format(line.max()), GearTooltips.percent(line.ratio()) + rolls);
    }

    /** 固定の錠（10×10）。 */
    private static void drawLock(GuiGraphics graphics, int x, int y, boolean locked, boolean hover) {
        int frame = hover ? 0xFFBFEFFF : 0xFF3A4A6E;
        graphics.fill(x, y, x + 10, y + 10, frame);
        graphics.fill(x + 1, y + 1, x + 9, y + 9, locked ? 0xFF5E4A18 : 0xFF0B1022);
        int metal = locked ? 0xFFFFD866 : 0xFF6E7C99;
        // つる（開いているときは右に上げる）
        graphics.fill(x + 3, y + 2, x + 7, y + 3, metal);
        graphics.fill(x + 3, y + 2, x + 4, y + 5, metal);
        graphics.fill(locked ? x + 6 : x + 7, locked ? y + 2 : y + 1, locked ? x + 7 : x + 8, y + 5, metal);
        // 胴
        graphics.fill(x + 2, y + 5, x + 8, y + 9, metal);
        graphics.fill(x + 4, y + 6, x + 6, y + 8, locked ? 0xFF5E4A18 : 0xFF0B1022);
    }

    /** 費用のかたまり（見出し・素材（持っている数）・ノット）。左の細い列に出す。 */
    private int costBlock(GuiGraphics graphics, String headerKey, CompoundTag cost, int x, int y) {
        graphics.drawString(font, Component.translatable(headerKey), x, y, UiStyle.TEXT_HEADER, true);
        y += 10;
        y = itemCosts(graphics, cost.getList("items", Tag.TAG_COMPOUND), x, y, true);
        long knots = cost.getLong("knots");
        long have = ClientPlayerState.get().knots();
        graphics.drawString(font, Component.translatable("screen.amemaru.refine.knots", jp.amemaru.lostconnection.economy.KnotTransfer.format(knots)),
                x, y, have >= knots ? UiStyle.TEXT_GOOD : UiStyle.TEXT_BAD, true);
        return y + 10;
    }

    /** 見積もりの素材の一覧（小さなアイコン・×要る数（持っている数））。 */
    private int itemCosts(GuiGraphics graphics, ListTag items, int x, int y, boolean compact) {
        for (Tag tag : items) {
            CompoundTag one = (CompoundTag) tag;
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(one.getString("id")));
            if (item == null) {
                continue;
            }
            int need = one.getInt("count");
            int have = count(item);
            graphics.pose().pushPose();
            graphics.pose().translate(x, y - 1, 0);
            graphics.pose().scale(0.625F, 0.625F, 1.0F);
            graphics.renderItem(new ItemStack(item), 0, 0);
            graphics.pose().popPose();
            Component line = compact ? Component.translatable("screen.amemaru.refine.item_short", need, have)
                    : Component.translatable("screen.amemaru.workshop.cost", new ItemStack(item).getHoverName(), need, have);
            graphics.drawString(font, UiStyle.clip(font, line.getString(), textWidth(x + 12, y)), x + 12, y,
                    have >= need ? UiStyle.TEXT_GOOD : UiStyle.TEXT_BAD, true);
            y += 10;
        }
        return y;
    }

    private static int count(Item item) {
        Inventory inventory = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getInventory();
        int have = 0;
        if (inventory != null) {
            for (ItemStack stack : inventory.items) {
                if (stack.is(item)) {
                    have += stack.getCount();
                }
            }
            for (ItemStack stack : inventory.offhand) {
                if (stack.is(item)) {
                    have += stack.getCount();
                }
            }
        }
        return have;
    }

    /** 強化（アクセサリー）：今の段と次の段・メインの伸び・次にサブが増える段・費用。 */
    private void renderAccessoryEnhance(GuiGraphics graphics) {
        if (!accessoryHeader(graphics, "screen.amemaru.refine.put_enhance")) {
            return;
        }
        ItemStack stack = menu.workStack();
        CompoundTag quote = ClientRefine.quote();
        int enhance = AccessoryData.enhance(stack);
        int max = quote.getInt("maxEnhance");
        int y = RIGHT_Y + 22;
        if (!quote.contains("enhance")) {
            graphics.drawString(font, Component.translatable("screen.amemaru.refine.max_enhance", max), RIGHT_X, y, UiStyle.TEXT_GOLD, true);
            return;
        }
        double percent = quote.getDouble("mainPercent");
        graphics.drawString(font, Component.translatable("screen.amemaru.refine.enhance_step", enhance, enhance + 1,
                String.format(Locale.ROOT, "%.0f", enhance * percent), String.format(Locale.ROOT, "%.0f", (enhance + 1) * percent)),
                RIGHT_X, y, UiStyle.TEXT_GOLD, true);
        y += 10;
        AccessoryData.StatLine main = AccessoryData.main(stack);
        if (main != null) {
            ItemStack next = stack.copy();
            AccessoryData.setEnhance(next, enhance + 1);
            AccessoryData.setMainPercent(next, percent);
            graphics.drawString(font, Component.translatable("screen.amemaru.workshop.stat_change",
                    Component.translatable(main.type().translationKey()),
                    main.type().format(AccessoryData.effectiveMain(stack, main)),
                    main.type().format(AccessoryData.effectiveMain(next, main))), RIGHT_X, y, UiStyle.TEXT_GOOD, true);
            y += 10;
        }
        int milestone = quote.getInt("nextMilestone");
        if (milestone > 0) {
            String key = milestone == enhance + 1
                    ? (AccessoryData.subs(stack).size() < 4 ? "screen.amemaru.refine.milestone_add_now" : "screen.amemaru.refine.milestone_grow_now")
                    : (AccessoryData.subs(stack).size() < 4 ? "screen.amemaru.refine.milestone_add" : "screen.amemaru.refine.milestone_grow");
            graphics.drawString(font, UiStyle.clip(font, Component.translatable(key, milestone).getString(), textWidth(RIGHT_X, y)),
                    RIGHT_X, y, 0xFFD98CFF, true);
            y += 10;
        }
        CompoundTag cost = quote.getCompound("enhance");
        y = itemCosts(graphics, cost.getList("items", Tag.TAG_COMPOUND), RIGHT_X, y + 1, false);
        // ノット（足りなければ赤）。右下の「強化する」と重ならないよう、持っている額は出さない
        long knots = cost.getLong("knots");
        graphics.drawString(font, Component.translatable("screen.amemaru.refine.knots",
                        jp.amemaru.lostconnection.economy.KnotTransfer.format(knots)), RIGHT_X, y,
                ClientPlayerState.get().knots() >= knots ? UiStyle.TEXT_GOOD : UiStyle.TEXT_BAD, true);
    }

    /** 分解：戻る素材と、取り消せないことの注意。 */
    private void renderDismantle(GuiGraphics graphics) {
        if (!accessoryHeader(graphics, "screen.amemaru.refine.put_dismantle")) {
            return;
        }
        CompoundTag quote = ClientRefine.quote();
        int y = RIGHT_Y + 22;
        graphics.drawString(font, Component.translatable("screen.amemaru.refine.yield"), RIGHT_X, y, UiStyle.TEXT_HEADER, true);
        y += 10;
        ListTag yield = quote.getList("yield", Tag.TAG_COMPOUND);
        if (yield.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.refine.yield_none"), RIGHT_X, y, UiStyle.TEXT_DIM, true);
            y += 10;
        }
        for (Tag tag : yield) {
            ItemStack one = ItemStack.of((CompoundTag) tag);
            graphics.pose().pushPose();
            graphics.pose().translate(RIGHT_X, y - 1, 0);
            graphics.pose().scale(0.625F, 0.625F, 1.0F);
            graphics.renderItem(one, 0, 0);
            graphics.pose().popPose();
            graphics.drawString(font, Component.translatable("screen.amemaru.refine.yield_line", one.getHoverName(), one.getCount()),
                    RIGHT_X + 12, y, UiStyle.TEXT_GOOD, true);
            y += 10;
        }
        boolean confirming = Util.getMillis() < confirmUntil;
        UiStyle.wrapped(graphics, font, Component.translatable(confirming ? "screen.amemaru.refine.dismantle_warn_now"
                : "screen.amemaru.refine.dismantle_warn").getString(), RIGHT_X, Math.max(y + 2, 96), ACTION_X - RIGHT_X - 6,
                confirming ? UiStyle.TEXT_BAD : UiStyle.TEXT_DIM, 4);
    }

    /** 依頼：依頼台の説明と、開くボタン（依頼台の画面で、出す・受ける・振り直す）。 */
    private void renderCommissionTab(GuiGraphics graphics) {
        UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.commission.tab_help").getString(), RIGHT_X + 2,
                RIGHT_Y + 2, imageWidth - RIGHT_X - 12, UiStyle.TEXT, 6);
    }

    /** 素材の一覧（アイコン・名前・要る数／持っている数）。 */
    private int renderCosts(GuiGraphics graphics, List<EquipmentDef.Cost> costs, int x, int y) {
        for (EquipmentDef.Cost cost : costs) {
            Item item = ForgeRegistries.ITEMS.getValue(cost.item());
            if (item == null) {
                continue;
            }
            int have = count(item);
            graphics.pose().pushPose();
            graphics.pose().translate(x, y - 1, 0);
            graphics.pose().scale(0.625F, 0.625F, 1.0F);
            graphics.renderItem(new ItemStack(item), 0, 0);
            graphics.pose().popPose();
            Component line = Component.translatable("screen.amemaru.workshop.cost", new ItemStack(item).getHoverName(), cost.count(), have);
            graphics.drawString(font, UiStyle.clip(font, line.getString(), textWidth(x + 12, y)), x + 12, y,
                    have >= cost.count() ? UiStyle.TEXT_GOOD : UiStyle.TEXT_BAD, true);
            y += 10;
        }
        return y;
    }

    private void renderHelp(GuiGraphics graphics) {
        String key = switch (tab) {
            case CRAFT -> "screen.amemaru.workshop.help_craft";
            case ENHANCE -> accessory() ? "screen.amemaru.refine.help_enhance" : "screen.amemaru.workshop.help_enhance";
            case REPAIR -> "screen.amemaru.workshop.help_repair";
            case DISMANTLE -> "screen.amemaru.refine.help_dismantle";
            case COMMISSION -> "screen.amemaru.commission.help";
            case REFINE -> "screen.amemaru.refine.help";
        };
        String text = Component.translatable(key, Component.translatable(menu.type().translationKey())).getString();
        if (tab == Tab.CRAFT) {
            // 作る：一覧（と「1-4/7」）の下。持ち物の左まで使える
            int y = LIST_Y + ROWS * ROW_HEIGHT + 14;
            UiStyle.wrapped(graphics, font, text, 8, y, WorkshopMenu.INVENTORY_X - 14, UiStyle.TEXT_DIM, (HEIGHT - 6 - y) / 10);
        } else {
            UiStyle.wrapped(graphics, font, text, 8, LIST_Y, LEFT_W, UiStyle.TEXT_DIM, (HEIGHT - 6 - LIST_Y) / 10);
        }
    }

    /**
     * 右の札の中で、(x, y) から書ける文字の幅。操作のボタン（札の右下）の高さにかかる行は、ボタンの手前まで。
     * 左の列（x が札より左）は、札の横なら LEFT_W、札より下なら持ち物の左まで。
     */
    private int textWidth(int x, int y) {
        if (x < RIGHT_X - 2) {
            return (y >= CARD_BOTTOM ? WorkshopMenu.INVENTORY_X - 6 : LIST_X + LEFT_W) - x;
        }
        boolean underButton = y + 9 > ACTION_Y && (actionButton.visible || joinButton.visible || commissionButton.visible);
        return (underButton ? ACTION_X - 4 : imageWidth - 10) - x;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int localX = mouseX - leftPos;
        int localY = mouseY - topPos;
        if (tab == Tab.CRAFT) {
            for (int i = 0; i < ROWS && scroll + i < recipes.size(); i++) {
                int y = LIST_Y + i * ROW_HEIGHT;
                if (localX >= LIST_X && localX < LIST_X + 16 && localY >= y && localY < y + 16) {
                    graphics.renderTooltip(font, recipes.get(scroll + i).result(), mouseX, mouseY);
                }
            }
        } else if (tab == Tab.REFINE && hasAccessory()) {
            int subs = AccessoryData.subs(menu.workStack()).size();
            for (int i = 0; i < subs; i++) {
                int ly = LINES_Y + i * 10;
                if (localX >= LOCK_X && localX < LOCK_X + 10 && localY >= ly - 1 && localY < ly + 9) {
                    graphics.renderTooltip(font, Component.translatable("screen.amemaru.refine.lock_tip"), mouseX, mouseY);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        if (tab == Tab.CRAFT && button == 0) {
            for (int i = 0; i < ROWS && scroll + i < recipes.size(); i++) {
                int y = LIST_Y + i * ROW_HEIGHT;
                if (localX >= LIST_X && localX < LIST_X + LIST_W && localY >= y && localY < y + ROW_HEIGHT) {
                    selected = scroll + i;
                    updateButtons();
                    return true;
                }
            }
        }
        if (tab == Tab.REFINE && button == 0 && hasAccessory()) {
            int subs = AccessoryData.subs(menu.workStack()).size();
            for (int i = 0; i < subs; i++) {
                int ly = LINES_Y + i * 10;
                if (localX >= LOCK_X && localX < LOCK_X + 10 && localY >= ly - 1 && localY < ly + 9) {
                    ModNetwork.sendToServer(new AccessoryOperationC2S(AccessoryOperationC2S.Action.TOGGLE_LOCK, i));
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

    @Override
    public void removed() {
        super.removed();
        ClientRefine.clear();
    }

    /** 画面のタブを外から選ぶ（CIの撮影用。番号はその工房のタブの順）。 */
    public void selectTab(int index) {
        tab = tabs.get(Math.max(0, Math.min(tabs.size() - 1, index)));
        updateButtons();
    }

    /** 分解の確かめの状態にする（CIの撮影用）。 */
    public void ciConfirmDismantle() {
        confirmUntil = Util.getMillis() + CONFIRM_MILLIS;
        updateButtons();
    }
}

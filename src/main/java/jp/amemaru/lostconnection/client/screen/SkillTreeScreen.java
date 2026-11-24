package jp.amemaru.lostconnection.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.client.ClientDefinitions;
import jp.amemaru.lostconnection.client.ClientPlayerState;
import jp.amemaru.lostconnection.client.KeyBindings;
import jp.amemaru.lostconnection.client.SkillHud;
import jp.amemaru.lostconnection.client.SkillHudArt;
import jp.amemaru.lostconnection.job.def.LinkType;
import jp.amemaru.lostconnection.job.def.SkillTreeDef;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.SetLoadoutC2S;
import jp.amemaru.lostconnection.network.c2s.TakeTreeNodeC2S;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C.JobInfo;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C.SkillInfo;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C.TreeNodeInfo;
import jp.amemaru.lostconnection.player.StatType;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * スキルツリーの画面（初期値：Jキー。Phase 0-A 19章・S3「職業ごとのスキルツリー（GUI）」）。
 *
 * <p>11月24日に、星座のような形に作り直した（ご指示「ステータスもスキルツリーで一緒に振る形にして、すべてのスキルが取れるではなく
 * ビルドによって変化するようにしたい」。見た目は、いただいた理想の絵に合わせた）。</p>
 *
 * <ul>
 *   <li>左の広い場所に、職業のスキルツリー。まん中の下に職業の核があり、左上と右上へ2つの流派の枝が星座のようにのびる。
 *       金のふちの大きな丸はスキル、小さな丸はステータスの節（印の形と色で、上がるステータスがわかる）、中くらいの丸は
 *       名前のある大きな節。取った節どうしの線は金色に光る。ドラッグで動かし、ホイールで拡大・縮小する（右上に「全体」「核へ」）。</li>
 *   <li>左下に、スキル枠（R・G・V・Z・X・C）。アクティブスキルを選んでから枠をクリックすると入る（右クリックで空にする）。</li>
 *   <li>右に、選んだ節の説明と「取る」ボタン（スキルは段階のつまみ1〜5段で、その段階の数値と効果を見せる）。
 *       「ステータス」のつまみでは、今のステータスと、ツリーで上がった分（金色）を見せる。</li>
 * </ul>
 *
 * <p>節はクリックで選び、ボタン（かダブルクリック）で取る。取れるのは、となりの節が取れている節だけ（核から道をのばす）。
 * 取るのと枠の入れかえはサーバーが確かめてから反映し、結果は同期で返ってくる。</p>
 */
public class SkillTreeScreen extends Screen {
    /** スキルツリーの画面の絵（tools/artgen/make_tree_art.py）。 */
    private static final ResourceLocation ART = AmemaruMod.id("textures/gui/skill_tree.png");
    private static final int ART_W = 128;
    private static final int ART_H = 64;

    // ---- 置き場所
    /** 画面のふちからのすきま（パネルの角の飾りが、はみ出さない分）。 */
    private static final int MARGIN = 6;
    private static final int GAP = 8;
    private static final int PANEL_MIN_W = 160;
    private static final int PANEL_MAX_W = 200;
    private static final int HEADER_TEXT_Y = 9;
    private static final int TABS_Y = 23;
    private static final int TABS_H = 12;
    private static final int DIVIDER_Y = 38;
    private static final int CONTENT_Y = 42;
    private static final int BUTTON_H = 16;
    private static final int DESC_LINES = 2;
    private static final int RANK_TAB_W = 15;
    private static final int RANK_TAB_H = 11;
    private static final int RANK_TAB_STEP = 17;
    private static final int VIEW_BUTTON_W = 30;
    private static final int CLOSE_BUTTON_W = 36;
    private static final int TOP_BUTTON_H = 12;
    private static final int SLOT_STEP = SkillHudArt.HEX_W + 2;
    /** 左下のスキル枠の台の高さ（木の下のふちから）。 */
    private static final int LOADOUT_PLATE_H = 46;

    // ---- 節（ツリーの単位。倍率をかけて画面のピクセルにする）
    private static final float CORE_SIZE = 48.0F;
    private static final float SKILL_SIZE = 36.0F;
    private static final float NOTABLE_SIZE = 28.0F;
    private static final float STAT_SIZE = 20.0F;
    /** 開いたときの倍率（全体が入る大きな画面では、全体）。 */
    private static final float START_ZOOM = 0.55F;
    private static final float MAX_ZOOM = 1.6F;
    private static final float ZOOM_STEP = 1.18F;
    private static final long DOUBLE_CLICK_MS = 350L;

    // ---- 線の色
    private static final int LINK_TAKEN = 0xFFE8C46A;
    private static final int LINK_FOCUS = 0xFFAEE8FF;
    private static final int LINK_REACH = 0xFF5D7FB0;
    private static final int LINK_DARK = 0xFF2A3550;

    // ---- 右のパネルのつまみ
    private static final int TAB_DETAIL = 0;
    private static final int TAB_STATS = 1;

    /** 「ステータス」のつまみに並べる種類。 */
    private static final List<StatType> SHOWN_STATS = List.of(StatType.HP, StatType.MP, StatType.ATTACK, StatType.MAGIC,
            StatType.DEFENSE, StatType.SPEED, StatType.CRIT, StatType.CRIT_DAMAGE, StatType.HEAL_POWER, StatType.COOLDOWN_REDUCTION);

    // ---- 背景の星（木の半分の速さで動く）
    private static final int STAR_COUNT = 150;
    private static final float[] STAR_U = new float[STAR_COUNT];
    private static final float[] STAR_V = new float[STAR_COUNT];
    private static final int[] STAR_ALPHA = new int[STAR_COUNT];
    private static final boolean[] STAR_BIG = new boolean[STAR_COUNT];

    static {
        Random random = new Random(0x5EEDL);
        for (int i = 0; i < STAR_COUNT; i++) {
            STAR_U[i] = random.nextFloat();
            STAR_V[i] = random.nextFloat();
            STAR_ALPHA[i] = 50 + random.nextInt(140);
            STAR_BIG[i] = random.nextInt(9) == 0;
        }
    }

    // ---- 閉じても覚えておく見え方（同じ職業で開き直したとき、続きから）
    private static int rememberedJob = -1;
    private static float rememberedCamX;
    private static float rememberedCamY;
    private static float rememberedZoom;
    @Nullable
    private static String rememberedNode;

    /** 画面の上の、四角い場所。 */
    private record Rect(int x0, int y0, int x1, int y1) {
        boolean contains(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    // ---- 置き場所（init で決める）
    private int canvasX0;
    private int canvasY0;
    private int canvasX1;
    private int canvasY1;
    private int panelX0;
    private int panelY0;
    private int panelX1;
    private int panelY1;
    private int topButtonsLeft;

    // ---- 木（定義から作る、画面用の形）
    @Nullable
    private SyncDefinitionsS2C.TreeInfo tree;
    private int[][] neighbors = new int[0][];
    private final Map<String, Integer> nodeIndex = new HashMap<>();
    private final Map<Integer, Integer> skillNode = new HashMap<>();
    private float treeMinX;
    private float treeMaxX;
    private float treeMinY;
    private float treeMaxY;

    // ---- 見え方
    private float camX;
    private float camY;
    private float zoom = START_ZOOM;
    private float minZoom = 0.2F;
    private boolean viewReady;

    // ---- 操作
    private boolean panning;
    private int pressButton;
    private double pressX;
    private double pressY;
    private float pressCamX;
    private float pressCamY;
    private boolean pressMoved;
    private int pressNode = -1;
    private long lastClickTime;
    private int lastClickNode = -1;
    /**
     * 開いたときのマウスの位置と、そこから動かしたか。動かすまでは、節を光らせず、ツールチップも出さない
     * （開いたとたん、画面のまん中にある節の説明が、右のパネルにかぶさっていたため。11月24日「UIがごちゃついている」）。
     */
    private int openMouseX = Integer.MIN_VALUE;
    private int openMouseY;
    private boolean mouseMoved;

    // ---- 選んでいる節と、右のパネル
    @Nullable
    private String selectedId;
    private int panelTab = TAB_DETAIL;
    /** 右の説明で見せている段階（段階のつまみで切りかえる）。 */
    private int shownRank = 1;
    /** 選んでいるスキルの、前に見たときの段階（覚えたら、見せる段階を進めるため）。 */
    private int lastRank = -1;
    private CrystalButton takeButton;
    private final List<CrystalButton> viewButtons = new ArrayList<>();

    // ---- 描いたときに覚える場所（ツールチップとクリックに使う）
    @Nullable
    private Rect pointsRect;
    @Nullable
    private Rect helpRect;
    @Nullable
    private Rect upperRect;
    @Nullable
    private Rect descriptionRect;
    @Nullable
    private Rect linesRect;
    private final Rect[] tabRects = new Rect[2];
    private int rankTabsX;
    private int rankTabsY;
    private int rankTabsCount;
    /** 効果の行が入りきらなかったか（入りきらなければ、ツールチップで全部を見せる）。 */
    private boolean detailOverflow;

    public SkillTreeScreen() {
        super(Component.translatable("screen.amemaru.skill_tree.title"));
    }

    // ---------------------------------------------------------------- 用意

    @Override
    protected void init() {
        int panelWidth = Mth.clamp(Math.round(width * 0.36F), PANEL_MIN_W, PANEL_MAX_W);
        panelX1 = width - MARGIN;
        panelX0 = panelX1 - panelWidth;
        panelY0 = MARGIN;
        panelY1 = height - MARGIN;
        canvasX0 = MARGIN;
        canvasY0 = MARGIN;
        canvasX1 = panelX0 - GAP;
        canvasY1 = height - MARGIN;

        takeButton = addRenderableWidget(new CrystalButton(panelX0 + 8, panelY1 - 6 - BUTTON_H, panelWidth - 16, BUTTON_H,
                Component.empty(), b -> takeSelected()));
        viewButtons.clear();
        int closeX = canvasX1 - 6 - CLOSE_BUTTON_W;
        addRenderableWidget(new CrystalButton(closeX, canvasY0 + 5, CLOSE_BUTTON_W, TOP_BUTTON_H,
                Component.translatable("screen.amemaru.status.close"), b -> onClose()));
        int coreX = closeX - 3 - VIEW_BUTTON_W;
        viewButtons.add(addRenderableWidget(new CrystalButton(coreX, canvasY0 + 5, VIEW_BUTTON_W, TOP_BUTTON_H,
                Component.translatable("screen.amemaru.skill_tree.view_core"), b -> viewCore())));
        topButtonsLeft = coreX - 3 - VIEW_BUTTON_W;
        viewButtons.add(addRenderableWidget(new CrystalButton(topButtonsLeft, canvasY0 + 5, VIEW_BUTTON_W, TOP_BUTTON_H,
                Component.translatable("screen.amemaru.skill_tree.view_all"), b -> viewAll())));

        if (ensureTree()) {
            if (viewReady) {
                // 窓の大きさが変わった：見え方は続けて、はみ出さないように直す
                updateZoomRange();
                zoom = Mth.clamp(zoom, minZoom, MAX_ZOOM);
                clampCamera();
            } else {
                setupInitialView();
            }
        }
        resetShownRank();
        updateButton();
    }

    /** 今の職業のツリーを用意する（定義が届き直したら作り直し、職業が変わったら見え方も最初から）。 */
    private boolean ensureTree() {
        ClientPlayerState state = ClientPlayerState.get();
        SyncDefinitionsS2C.TreeInfo current = state.hasJob() ? ClientDefinitions.get().tree(state.jobIndex()) : null;
        if (current != tree) {
            boolean jobChanged = tree == null || current == null || tree.jobIndex() != current.jobIndex();
            tree = current;
            rebuild();
            if (jobChanged) {
                selectedId = null;
                viewReady = false;
            }
        }
        return tree != null && !tree.nodes().isEmpty();
    }

    private void rebuild() {
        nodeIndex.clear();
        skillNode.clear();
        if (tree == null) {
            neighbors = new int[0][];
            return;
        }
        List<TreeNodeInfo> nodes = tree.nodes();
        List<List<Integer>> adjacency = new ArrayList<>();
        treeMinX = Float.MAX_VALUE;
        treeMinY = Float.MAX_VALUE;
        treeMaxX = -Float.MAX_VALUE;
        treeMaxY = -Float.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            TreeNodeInfo node = nodes.get(i);
            nodeIndex.put(node.id(), i);
            if (node.skill() >= 0) {
                skillNode.put(node.skill(), i);
            }
            adjacency.add(new ArrayList<>());
            treeMinX = Math.min(treeMinX, node.x());
            treeMaxX = Math.max(treeMaxX, node.x());
            treeMinY = Math.min(treeMinY, node.y());
            treeMaxY = Math.max(treeMaxY, node.y());
        }
        if (nodes.isEmpty()) {
            treeMinX = treeMaxX = treeMinY = treeMaxY = 0.0F;
        }
        int[] links = tree.links();
        for (int k = 0; k + 1 < links.length; k += 2) {
            int a = links[k];
            int b = links[k + 1];
            if (a < 0 || b < 0 || a >= nodes.size() || b >= nodes.size()) {
                continue;
            }
            adjacency.get(a).add(b);
            adjacency.get(b).add(a);
        }
        neighbors = new int[nodes.size()][];
        for (int i = 0; i < nodes.size(); i++) {
            List<Integer> list = adjacency.get(i);
            int[] array = new int[list.size()];
            for (int k = 0; k < array.length; k++) {
                array[k] = list.get(k);
            }
            neighbors[i] = array;
        }
    }

    /** 開いたときの見え方：前に同じ職業で見ていたなら続きから、なければ核のまわり（大きな画面では全体）。 */
    private void setupInitialView() {
        if (tree == null) {
            return;
        }
        updateZoomRange();
        if (rememberedJob == tree.jobIndex() && rememberedZoom > 0.0F) {
            zoom = Mth.clamp(rememberedZoom, minZoom, MAX_ZOOM);
            camX = rememberedCamX;
            camY = rememberedCamY;
            clampCamera();
            if (selectedId == null && rememberedNode != null && nodeIndex.containsKey(rememberedNode)) {
                selectedId = rememberedNode;
            }
        } else if (fitZoom() >= START_ZOOM) {
            viewAll();
        } else {
            viewCore();
        }
        if (selectedId == null || !nodeIndex.containsKey(selectedId)) {
            int core = coreIndex();
            selectedId = core >= 0 ? tree.nodes().get(core).id() : null;
        }
        viewReady = true;
        resetShownRank();
        updateButton();
    }

    @Override
    public void removed() {
        super.removed();
        if (tree != null && viewReady) {
            rememberedJob = tree.jobIndex();
            rememberedCamX = camX;
            rememberedCamY = camY;
            rememberedZoom = zoom;
            rememberedNode = selectedId;
        }
    }

    @Override
    public void tick() {
        super.tick();
        SkillInfo skill = selectedSkill();
        if (skill != null && ClientPlayerState.get().rank(skill.index()) != lastRank) {
            resetShownRank();
        }
        updateButton();
    }

    // ---------------------------------------------------------------- 見え方（動かす・拡大）

    private float centerX() {
        return (canvasX0 + canvasX1) / 2.0F;
    }

    private float centerY() {
        return (canvasY0 + canvasY1) / 2.0F;
    }

    private float screenX(float x) {
        return centerX() + (x - camX) * zoom;
    }

    private float screenY(float y) {
        return centerY() + (y - camY) * zoom;
    }

    /** 全体が、スキル枠の台より上に入る倍率。 */
    private float fitZoom() {
        float w = Math.max(40, canvasX1 - canvasX0 - 24);
        float h = Math.max(40, canvasY1 - canvasY0 - 24 - LOADOUT_PLATE_H);
        float treeW = treeMaxX - treeMinX + CORE_SIZE * 2.0F;
        float treeH = treeMaxY - treeMinY + CORE_SIZE * 2.0F;
        return Math.min(w / treeW, h / treeH);
    }

    private void updateZoomRange() {
        minZoom = Math.max(0.12F, Math.min(START_ZOOM, fitZoom() * 0.9F));
    }

    /** 全体を見る。 */
    private void viewAll() {
        if (tree == null) {
            return;
        }
        updateZoomRange();
        zoom = Mth.clamp(fitZoom(), minZoom, MAX_ZOOM);
        camX = (treeMinX + treeMaxX) / 2.0F;
        // 下のスキル枠の台の分だけ、上にずらす
        camY = (treeMinY + treeMaxY) / 2.0F + (LOADOUT_PLATE_H / 2.0F) / zoom;
        clampCamera();
    }

    /** 核のまわりを見る（核をスキル枠の台の少し上に置く）。 */
    private void viewCore() {
        if (tree == null) {
            return;
        }
        updateZoomRange();
        zoom = Mth.clamp(Math.max(START_ZOOM, fitZoom()), minZoom, MAX_ZOOM);
        int core = coreIndex();
        float coreX = core >= 0 ? tree.nodes().get(core).x() : 0.0F;
        float coreY = core >= 0 ? tree.nodes().get(core).y() : 0.0F;
        float coreScreenY = canvasY1 - LOADOUT_PLATE_H - 30;
        camX = coreX;
        camY = coreY - (coreScreenY - centerY()) / zoom;
        clampCamera();
    }

    /** その節がまん中に見えるように動かす（小さすぎるときは、読める倍率まで寄る）。 */
    private void focusOn(int index) {
        if (tree == null || index < 0 || index >= tree.nodes().size()) {
            return;
        }
        TreeNodeInfo node = tree.nodes().get(index);
        zoom = Mth.clamp(Math.max(zoom, START_ZOOM), minZoom, MAX_ZOOM);
        camX = node.x();
        camY = node.y() + (LOADOUT_PLATE_H / 2.0F) / zoom;
        clampCamera();
    }

    /** 木が見えなくなるほどは、動かせない。 */
    private void clampCamera() {
        float halfW = (canvasX1 - canvasX0) * 0.5F / zoom;
        float halfH = (canvasY1 - canvasY0) * 0.5F / zoom;
        camX = Mth.clamp(camX, treeMinX - halfW * 0.6F, treeMaxX + halfW * 0.6F);
        camY = Mth.clamp(camY, treeMinY - halfH * 0.6F, treeMaxY + halfH * 0.6F);
    }

    /** マウスの下の点を動かさずに、拡大・縮小する。 */
    private void zoomAt(double mouseX, double mouseY, float factor) {
        float newZoom = Mth.clamp(zoom * factor, minZoom, MAX_ZOOM);
        if (Math.abs(newZoom - zoom) < 1.0E-4F) {
            return;
        }
        float treeX = camX + (float) (mouseX - centerX()) / zoom;
        float treeY = camY + (float) (mouseY - centerY()) / zoom;
        zoom = newZoom;
        camX = treeX - (float) (mouseX - centerX()) / zoom;
        camY = treeY - (float) (mouseY - centerY()) / zoom;
        clampCamera();
    }

    // ---------------------------------------------------------------- 節の状態（サーバーと同じ決まり）

    private int coreIndex() {
        if (tree == null) {
            return -1;
        }
        for (int i = 0; i < tree.nodes().size(); i++) {
            if (tree.nodes().get(i).kindEnum() == SkillTreeDef.Kind.CORE) {
                return i;
            }
        }
        return tree.nodes().isEmpty() ? -1 : 0;
    }

    private int selectedIndex() {
        Integer index = selectedId == null ? null : nodeIndex.get(selectedId);
        return index == null ? -1 : index;
    }

    @Nullable
    private TreeNodeInfo selectedNode() {
        int index = selectedIndex();
        return tree == null || index < 0 || index >= tree.nodes().size() ? null : tree.nodes().get(index);
    }

    @Nullable
    private SkillInfo selectedSkill() {
        TreeNodeInfo node = selectedNode();
        return node == null || node.skill() < 0 ? null : ClientDefinitions.get().skill(node.skill());
    }

    /** その節が取れているか（核はいつも。スキルは1段目以上。ステータスの節は取ったもの）。 */
    private static boolean taken(TreeNodeInfo node, ClientPlayerState state) {
        return switch (node.kindEnum()) {
            case CORE -> true;
            case SKILL -> node.skill() >= 0 && state.rank(node.skill()) > 0;
            default -> state.hasTreeNode(node.id());
        };
    }

    /** となりの節のどれかが取れているか。 */
    private boolean connected(int index, ClientPlayerState state) {
        if (tree == null || index < 0 || index >= neighbors.length) {
            return false;
        }
        for (int neighbor : neighbors[index]) {
            if (taken(tree.nodes().get(neighbor), state)) {
                return true;
            }
        }
        return false;
    }

    private static int rankCost(SkillInfo skill, int rank) {
        int[] costs = skill.rankCosts();
        if (costs.length == 0) {
            return 1;
        }
        return costs[Mth.clamp(rank, 1, costs.length) - 1];
    }

    /** 取れない（次の段階を覚えられない）理由。取れるなら null。サーバーの決まりと同じ順に見る。 */
    @Nullable
    private Component blocker(int index, ClientPlayerState state) {
        if (tree == null || index < 0 || index >= tree.nodes().size()) {
            return Component.translatable("message.amemaru.skill.learn.unknown");
        }
        TreeNodeInfo node = tree.nodes().get(index);
        SkillTreeDef.Kind kind = node.kindEnum();
        if (kind == SkillTreeDef.Kind.CORE) {
            return Component.translatable("screen.amemaru.skill_tree.kind.core");
        }
        if (kind == SkillTreeDef.Kind.SKILL) {
            SkillInfo skill = ClientDefinitions.get().skill(node.skill());
            if (skill == null) {
                return Component.translatable("message.amemaru.skill.learn.unknown");
            }
            if (skill.jobIndex() != state.jobIndex()) {
                return Component.translatable("message.amemaru.skill.learn.other_job");
            }
            int rank = state.rank(skill.index());
            int next = rank + 1;
            if (next > skill.maxRank()) {
                return Component.translatable("message.amemaru.skill.learn.max_rank");
            }
            SkillInfo chosen = chosenUltimate(skill, state);
            if (chosen != null) {
                return Component.translatable("message.amemaru.skill.learn.ultimate_chosen", chosen.name());
            }
            if (state.level() < skill.minLevel()) {
                return Component.translatable("message.amemaru.skill.learn.level", skill.minLevel());
            }
            if (rank == 0 && !connected(index, state)) {
                return Component.translatable("message.amemaru.skill.learn.not_connected");
            }
            if ((skill.ultimate() || next >= skill.upperRank()) && !state.promoted()) {
                return Component.translatable("message.amemaru.skill.learn.upper");
            }
            int cost = rankCost(skill, next);
            if (state.skillPoints() < cost) {
                return Component.translatable("message.amemaru.skill.learn.points", cost);
            }
            return null;
        }
        if (state.hasTreeNode(node.id())) {
            return Component.translatable("message.amemaru.skill.learn.taken");
        }
        if (!connected(index, state)) {
            return Component.translatable("message.amemaru.skill.learn.not_connected");
        }
        if (state.skillPoints() < node.cost()) {
            return Component.translatable("message.amemaru.skill.learn.points", node.cost());
        }
        return null;
    }

    /** ボタンとツールチップに入る長さの、取れない理由。 */
    private Component shortBlocker(int index, ClientPlayerState state) {
        TreeNodeInfo node = tree == null || index < 0 || index >= tree.nodes().size() ? null : tree.nodes().get(index);
        if (node == null) {
            return Component.translatable("message.amemaru.skill.learn.unknown");
        }
        if (node.kindEnum() == SkillTreeDef.Kind.SKILL) {
            SkillInfo skill = ClientDefinitions.get().skill(node.skill());
            if (skill == null) {
                return Component.translatable("message.amemaru.skill.learn.unknown");
            }
            if (skill.jobIndex() != state.jobIndex()) {
                return Component.translatable("screen.amemaru.skill_tree.need.other_job");
            }
            int rank = state.rank(skill.index());
            int next = rank + 1;
            if (next > skill.maxRank()) {
                return Component.translatable("screen.amemaru.skill_tree.max");
            }
            if (chosenUltimate(skill, state) != null) {
                return Component.translatable("screen.amemaru.skill_tree.need.ultimate_chosen");
            }
            if (state.level() < skill.minLevel()) {
                return Component.translatable("screen.amemaru.skill_tree.need.level", skill.minLevel());
            }
            if (rank == 0 && !connected(index, state)) {
                return Component.translatable("screen.amemaru.skill_tree.need.connect");
            }
            if ((skill.ultimate() || next >= skill.upperRank()) && !state.promoted()) {
                return Component.translatable("screen.amemaru.skill_tree.need.upper");
            }
            return Component.translatable("screen.amemaru.skill_tree.need.points", rankCost(skill, next));
        }
        if (node.kindEnum() == SkillTreeDef.Kind.CORE) {
            return Component.translatable("screen.amemaru.skill_tree.kind.core");
        }
        if (state.hasTreeNode(node.id())) {
            return Component.translatable("screen.amemaru.skill_tree.need.taken");
        }
        if (!connected(index, state)) {
            return Component.translatable("screen.amemaru.skill_tree.need.connect");
        }
        return Component.translatable("screen.amemaru.skill_tree.need.points", node.cost());
    }

    /** 同じ職業の、もう覚えている「ほかの」奥義（奥義は2つから1つを選ぶ。サーバーと同じ決まり）。なければ null。 */
    @Nullable
    private static SkillInfo chosenUltimate(SkillInfo skill, ClientPlayerState state) {
        if (!skill.ultimate()) {
            return null;
        }
        for (SkillInfo other : ClientDefinitions.get().skillsOf(skill.jobIndex())) {
            if (other.ultimate() && other.index() != skill.index() && state.rank(other.index()) > 0) {
                return other;
            }
        }
        return null;
    }

    /** ステータスの節の、上がる分（数と%）。{@code [ステータスの番号, 値, %か(1/0)]} を並べる。 */
    private static List<float[]> statEntries(TreeNodeInfo node) {
        List<float[]> entries = new ArrayList<>();
        float[] flat = node.flat();
        for (int k = 0; k + 1 < flat.length; k += 2) {
            entries.add(new float[]{flat[k], flat[k + 1], 0.0F});
        }
        float[] percent = node.percent();
        for (int k = 0; k + 1 < percent.length; k += 2) {
            entries.add(new float[]{percent[k], percent[k + 1], 1.0F});
        }
        return entries;
    }

    @Nullable
    private static StatType statOf(float ordinal) {
        int index = Math.round(ordinal);
        StatType[] all = StatType.values();
        return index >= 0 && index < all.length ? all[index] : null;
    }

    @Nullable
    private static StatType firstStat(TreeNodeInfo node) {
        List<float[]> entries = statEntries(node);
        return entries.isEmpty() ? null : statOf(entries.get(0)[0]);
    }

    /** 「HP +24」「防御 +8%」の形。 */
    private static String statText(float[] entry) {
        StatType type = statOf(entry[0]);
        if (type == null) {
            return "?";
        }
        boolean percent = entry[2] > 0.5F || type.isPercent();
        return Component.translatable(type.translationKey()).getString() + " +" + trim(entry[1]) + (percent ? "%" : "");
    }

    /** 節の名前（大きな節は名前、小さな節は「HP+24」のように）。 */
    private static String nodeTitle(TreeNodeInfo node) {
        if (!node.name().isEmpty()) {
            return node.name();
        }
        List<String> parts = new ArrayList<>();
        for (float[] entry : statEntries(node)) {
            parts.add(statText(entry).replace(" ", ""));
        }
        return String.join("・", parts);
    }

    /** ステータスの印（skill_tree.png の1段目の何番目か）。 */
    private static int glyphOf(StatType type) {
        return switch (type) {
            case HP -> 0;
            case MP -> 1;
            case ATTACK -> 2;
            case MAGIC -> 3;
            case DEFENSE -> 4;
            case SPEED -> 5;
            case CRIT, CRIT_DAMAGE -> 6;
            default -> 7;
        };
    }

    private static float baseSize(SkillTreeDef.Kind kind) {
        return switch (kind) {
            case CORE -> CORE_SIZE;
            case SKILL -> SKILL_SIZE;
            case NOTABLE -> NOTABLE_SIZE;
            default -> STAT_SIZE;
        };
    }

    /** 節の、画面での大きさ（ピクセル）。 */
    private int pixelSize(TreeNodeInfo node) {
        int min = node.kindEnum() == SkillTreeDef.Kind.STAT ? 5 : 8;
        return Math.max(min, Math.round(baseSize(node.kindEnum()) * zoom));
    }

    // ---------------------------------------------------------------- 選ぶ・取る

    /** 見せる段階を決め直す：次の段階を覚えられるならそれ、覚えていなければ1段、ほかは今の段階。 */
    private void resetShownRank() {
        SkillInfo skill = selectedSkill();
        if (skill == null) {
            shownRank = 1;
            lastRank = -1;
            return;
        }
        ClientPlayerState state = ClientPlayerState.get();
        int rank = state.rank(skill.index());
        lastRank = rank;
        if (rank <= 0) {
            shownRank = 1;
        } else if (rank < skill.maxRank() && blocker(selectedIndex(), state) == null) {
            shownRank = rank + 1;
        } else {
            shownRank = Math.min(rank, skill.maxRank());
        }
    }

    private void updateButton() {
        if (takeButton == null) {
            return;
        }
        for (CrystalButton button : viewButtons) {
            button.visible = tree != null;
        }
        ClientPlayerState state = ClientPlayerState.get();
        TreeNodeInfo node = selectedNode();
        if (node == null || panelTab != TAB_DETAIL || !state.hasJob() || node.kindEnum() == SkillTreeDef.Kind.CORE) {
            takeButton.visible = false;
            return;
        }
        takeButton.visible = true;
        int index = selectedIndex();
        Component reason = blocker(index, state);
        takeButton.active = reason == null;
        if (reason == null) {
            Component message;
            if (node.kindEnum() == SkillTreeDef.Kind.SKILL) {
                SkillInfo skill = ClientDefinitions.get().skill(node.skill());
                int rank = skill == null ? 0 : state.rank(skill.index());
                int cost = skill == null ? 1 : rankCost(skill, rank + 1);
                message = Component.translatable(rank == 0 ? "screen.amemaru.skill_tree.learn" : "screen.amemaru.skill_tree.rank_up", cost);
            } else {
                message = Component.translatable("screen.amemaru.skill_tree.take", node.cost());
            }
            takeButton.setMessage(message);
            takeButton.setTooltip(null);
        } else {
            // 押せないときは、ボタンに短い理由、ツールチップに詳しい理由を出す
            takeButton.setMessage(shortBlocker(index, state));
            takeButton.setTooltip(Tooltip.create(reason));
        }
    }

    private void selectIndex(int index) {
        if (tree == null || index < 0 || index >= tree.nodes().size()) {
            return;
        }
        selectedId = tree.nodes().get(index).id();
        panelTab = TAB_DETAIL;
        resetShownRank();
        updateButton();
    }

    private void takeSelected() {
        take(selectedIndex());
    }

    private void take(int index) {
        if (tree == null || index < 0 || index >= tree.nodes().size()) {
            return;
        }
        TreeNodeInfo node = tree.nodes().get(index);
        if (node.kindEnum() != SkillTreeDef.Kind.CORE) {
            ModNetwork.sendToServer(new TakeTreeNodeC2S(node.id()));
        }
    }

    /** 木の節をクリックした（選ぶ。同じ節をすぐもう一度なら、取る）。 */
    private void clickNode(int index) {
        if (index < 0) {
            return;
        }
        long now = Util.getMillis();
        boolean doubleClick = index == lastClickNode && now - lastClickTime < DOUBLE_CLICK_MS;
        lastClickNode = index;
        lastClickTime = now;
        if (index != selectedIndex() || panelTab != TAB_DETAIL) {
            selectIndex(index);
        }
        if (doubleClick && blocker(index, ClientPlayerState.get()) == null) {
            take(index);
            lastClickNode = -1;
        }
    }

    /** 選んでいるスキルを変え、木のまん中に見せる（自動の確認でも使う）。 */
    public void select(int skillIndex) {
        if (!ensureTree()) {
            return;
        }
        if (!viewReady) {
            setupInitialView();
        }
        Integer index = skillNode.get(skillIndex);
        if (index != null) {
            selectIndex(index);
            focusOn(index);
        }
    }

    /** 節を名前で選び、木のまん中に見せる（自動の確認で使う）。 */
    public void selectNode(String id) {
        if (!ensureTree()) {
            return;
        }
        if (!viewReady) {
            setupInitialView();
        }
        Integer index = nodeIndex.get(id);
        if (index != null) {
            selectIndex(index);
            focusOn(index);
        }
    }

    /** 右のパネルを「ステータス」のつまみにする（自動の確認で使う）。 */
    public void showStats() {
        panelTab = TAB_STATS;
        updateButton();
    }

    /** 木の全体を見せる（自動の確認で使う）。 */
    public void showWholeTree() {
        if (ensureTree()) {
            if (!viewReady) {
                setupInitialView();
            }
            viewAll();
        }
    }

    // ---------------------------------------------------------------- 当たり

    private boolean inCanvas(double x, double y) {
        return x >= canvasX0 && x < canvasX1 && y >= canvasY0 && y < canvasY1;
    }

    private int loadoutX() {
        return canvasX0 + 9;
    }

    private int loadoutY() {
        return canvasY1 - 6 - SkillHudArt.HEX_H;
    }

    private Rect loadoutPlate() {
        return new Rect(loadoutX() - 5, canvasY1 - LOADOUT_PLATE_H, loadoutX() + 6 * SLOT_STEP + 3, canvasY1);
    }

    private boolean overCanvasUi(double x, double y) {
        if (loadoutPlate().contains(x, y)) {
            return true;
        }
        return y < canvasY0 + 5 + TOP_BUTTON_H + 2 && x >= topButtonsLeft - 2;
    }

    /** マウスの下の節（なければ -1）。 */
    private int nodeAt(double mouseX, double mouseY) {
        if (tree == null || !inCanvas(mouseX, mouseY) || overCanvasUi(mouseX, mouseY) || mouseY < topStripBottom()) {
            return -1;
        }
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        List<TreeNodeInfo> nodes = tree.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            TreeNodeInfo node = nodes.get(i);
            double dx = screenX(node.x()) - mouseX;
            double dy = screenY(node.y()) - mouseY;
            double distance = Math.sqrt(dx * dx + dy * dy);
            double radius = Math.max(5.0, pixelSize(node) / 2.0 + 1.0);
            if (distance <= radius && distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** マウスの下のスキル枠（なければ -1）。 */
    private int slotAt(double mouseX, double mouseY) {
        for (int slot = 0; slot < 6; slot++) {
            int x = loadoutX() + slot * SLOT_STEP;
            int y = loadoutY();
            if (mouseX >= x && mouseX < x + SkillHudArt.HEX_W && mouseY >= y && mouseY < y + SkillHudArt.HEX_H) {
                return slot;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- 描画

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        pointsRect = null;
        helpRect = null;
        upperRect = null;
        descriptionRect = null;
        linesRect = null;
        rankTabsCount = 0;
        tabRects[0] = null;
        tabRects[1] = null;
        ClientPlayerState state = ClientPlayerState.get();
        JobInfo job = ClientDefinitions.get().job(state.jobIndex());
        boolean hasTree = job != null && ensureTree();
        if (hasTree && !viewReady) {
            setupInitialView();
        }
        UiStyle.panel(graphics, canvasX0, canvasY0, canvasX1, canvasY1);
        if (!hasTree) {
            for (CrystalButton button : viewButtons) {
                button.visible = false;
            }
            if (takeButton != null) {
                takeButton.visible = false;
            }
            UiStyle.panel(graphics, panelX0, panelY0, panelX1, panelY1);
            String text = Component.translatable(job == null ? "screen.amemaru.skill_tree.no_job" : "screen.amemaru.skill_tree.no_tree")
                    .getString();
            UiStyle.wrapped(graphics, font, text, canvasX0 + 20, (canvasY0 + canvasY1) / 2 - 15, canvasX1 - canvasX0 - 40,
                    UiStyle.TEXT_DIM, 4);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        noteMouse(mouseX, mouseY);
        int hovered = !mouseMoved || panning && pressMoved ? -1 : nodeAt(mouseX, mouseY);
        graphics.enableScissor(canvasX0, canvasY0, canvasX1, canvasY1);
        renderStars(graphics);
        renderLinks(graphics, state, hovered);
        renderNodes(graphics, state, job, hovered);
        graphics.disableScissor();
        renderTopStrip(graphics);
        renderHelp(graphics);
        int hoveredSlot = renderLoadout(graphics, state, mouseX, mouseY);
        renderPanel(graphics, state, job, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (mouseMoved) {
            renderTooltips(graphics, state, job, mouseX, mouseY, hovered, hoveredSlot);
        }
    }

    /** 開いてからマウスを動かしたかを覚える（{@link #mouseMoved}）。 */
    private void noteMouse(int mouseX, int mouseY) {
        if (mouseMoved) {
            return;
        }
        if (openMouseX == Integer.MIN_VALUE) {
            openMouseX = mouseX;
            openMouseY = mouseY;
        } else if (Math.abs(mouseX - openMouseX) + Math.abs(mouseY - openMouseY) > 2) {
            mouseMoved = true;
        }
    }

    /** 上の帯（使い方と3つのボタン）の下の端。 */
    private int topStripBottom() {
        return canvasY0 + 5 + TOP_BUTTON_H + 4;
    }

    /**
     * 上の帯。木の節が使い方の文字やボタンと重なって読みにくくならないよう、左下のスキル枠の台と同じ暗い帯を敷く
     * （11月24日「UIがごちゃついている」）。帯の下の節は、選べない（{@link #nodeAt}）。
     */
    private void renderTopStrip(GuiGraphics graphics) {
        int bottom = topStripBottom();
        graphics.fill(canvasX0, canvasY0, canvasX1, bottom, 0xC8070B16);
        graphics.fill(canvasX0, bottom, canvasX1, bottom + 1, 0x708FD3FF);
        // 帯で欠けた、木の枠の上の角の飾りを描き直す
        UiStyle.panelTopCorners(graphics, canvasX0, canvasY0, canvasX1);
    }

    /** 背景の星（木の4分の1の速さで流れ、ゆっくりまたたく）。 */
    private void renderStars(GuiGraphics graphics) {
        int w = canvasX1 - canvasX0 - 2;
        int h = canvasY1 - canvasY0 - 2;
        if (w <= 0 || h <= 0) {
            return;
        }
        float offsetX = -camX * zoom * 0.25F;
        float offsetY = -camY * zoom * 0.25F;
        long time = Util.getMillis();
        for (int i = 0; i < STAR_COUNT; i++) {
            int x = canvasX0 + 1 + (int) wrap(STAR_U[i] * w + offsetX, w);
            int y = canvasY0 + 1 + (int) wrap(STAR_V[i] * h + offsetY, h);
            float twinkle = 0.7F + 0.3F * (float) Math.sin(time / 700.0 + i * 1.7);
            int alpha = Mth.clamp((int) (STAR_ALPHA[i] * twinkle), 0, 255);
            graphics.fill(x, y, x + 1, y + 1, (alpha << 24) | 0xDDE8FF);
            if (STAR_BIG[i]) {
                int faint = (alpha / 3) << 24 | 0xDDE8FF;
                graphics.fill(x - 1, y, x, y + 1, faint);
                graphics.fill(x + 1, y, x + 2, y + 1, faint);
                graphics.fill(x, y - 1, x + 1, y, faint);
                graphics.fill(x, y + 1, x + 1, y + 2, faint);
            }
        }
    }

    private static float wrap(float value, float size) {
        float r = value % size;
        return r < 0 ? r + size : r;
    }

    /** 節どうしのつながり（取った節どうしは金色。片方だけ取っていれば青。マウスの乗った節への道は明るく）。 */
    private void renderLinks(GuiGraphics graphics, ClientPlayerState state, int hovered) {
        if (tree == null) {
            return;
        }
        List<TreeNodeInfo> nodes = tree.nodes();
        int[] links = tree.links();
        int focus = hovered >= 0 ? hovered : selectedIndex();
        for (int k = 0; k + 1 < links.length; k += 2) {
            int a = links[k];
            int b = links[k + 1];
            if (a < 0 || b < 0 || a >= nodes.size() || b >= nodes.size()) {
                continue;
            }
            TreeNodeInfo nodeA = nodes.get(a);
            TreeNodeInfo nodeB = nodes.get(b);
            float x1 = screenX(nodeA.x());
            float y1 = screenY(nodeA.y());
            float x2 = screenX(nodeB.x());
            float y2 = screenY(nodeB.y());
            if (Math.max(x1, x2) < canvasX0 || Math.min(x1, x2) > canvasX1 || Math.max(y1, y2) < canvasY0
                    || Math.min(y1, y2) > canvasY1) {
                continue;
            }
            boolean takenA = taken(nodeA, state);
            boolean takenB = taken(nodeB, state);
            int color;
            int thickness;
            if (takenA && takenB) {
                color = LINK_TAKEN;
                thickness = 2;
            } else if ((takenA || takenB) && (a == focus || b == focus)) {
                color = LINK_FOCUS;
                thickness = 2;
            } else if (takenA || takenB) {
                color = LINK_REACH;
                thickness = 1;
            } else {
                color = LINK_DARK;
                thickness = 1;
            }
            line(graphics, x1, y1, x2, y2, thickness, color);
        }
    }

    /** 斜めの線（細い四角を回して描く）。 */
    private static void line(GuiGraphics graphics, float x1, float y1, float x2, float y2, int thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0F) {
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x1, y1, 0.0F);
        pose.mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        int half = thickness / 2;
        graphics.fill(0, -half, Math.round(length), thickness - half, color);
        pose.popPose();
    }

    private void renderNodes(GuiGraphics graphics, ClientPlayerState state, JobInfo job, int hovered) {
        if (tree == null) {
            return;
        }
        List<TreeNodeInfo> nodes = tree.nodes();
        int selectedIndex = selectedIndex();
        float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 2000.0 * Math.PI * 2.0);
        for (int i = 0; i < nodes.size(); i++) {
            TreeNodeInfo node = nodes.get(i);
            int size = pixelSize(node);
            int x = Math.round(screenX(node.x()) - size / 2.0F);
            int y = Math.round(screenY(node.y()) - size / 2.0F);
            if (x + size < canvasX0 - 12 || x > canvasX1 + 12 || y + size < canvasY0 - 12 || y > canvasY1 + 12) {
                continue;
            }
            switch (node.kindEnum()) {
                case CORE -> renderCore(graphics, job, x, y, size, i == selectedIndex, i == hovered);
                case SKILL -> renderSkillNode(graphics, state, i, node, x, y, size, i == selectedIndex, i == hovered, pulse);
                default -> renderStatNode(graphics, state, i, node, x, y, size, i == selectedIndex, i == hovered, pulse);
            }
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void blitArt(GuiGraphics graphics, int x, int y, int w, int h, int u, int v, int uw, int vh) {
        graphics.blit(ART, x, y, w, h, u, v, uw, vh, ART_W, ART_H);
    }

    /** 光の輪（取れる節はゆっくり脈打つ。選んだ節ははっきり）。 */
    private static void glow(GuiGraphics graphics, int x, int y, int size, float scale, float r, float g, float b, float a) {
        int glow = Math.round(size * scale);
        RenderSystem.setShaderColor(r, g, b, a);
        blitArt(graphics, x + (size - glow) / 2, y + (size - glow) / 2, glow, glow, 48, 16, 16, 16);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderCore(GuiGraphics graphics, JobInfo job, int x, int y, int size, boolean selected, boolean hovered) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (selected || hovered) {
            glow(graphics, x, y, size, 1.45F, 1.0F, 0.95F, 0.75F, selected ? 1.0F : 0.6F);
        }
        blitArt(graphics, x, y, size, size, 72, 32, 32, 32);
        int icon = Math.round(size * 0.62F);
        graphics.blit(job.icon(), x + (size - icon) / 2, y + (size - icon) / 2, icon, icon, 0, 0, 64, 64, 64, 64);
    }

    private void renderSkillNode(GuiGraphics graphics, ClientPlayerState state, int index, TreeNodeInfo node, int x, int y, int size,
                                 boolean selected, boolean hovered, float pulse) {
        SkillInfo skill = ClientDefinitions.get().skill(node.skill());
        if (skill == null) {
            return;
        }
        int rank = state.rank(skill.index());
        boolean available = blocker(index, state) == null;
        boolean reachable = rank > 0 || connected(index, state);
        int u;
        if (skill.ultimate()) {
            u = SkillHudArt.U_NODE_ULTIMATE;
        } else if (rank >= skill.maxRank()) {
            u = SkillHudArt.U_NODE_MAX;
        } else if (rank > 0) {
            u = SkillHudArt.U_NODE_LEARNED;
        } else if (available) {
            u = SkillHudArt.U_NODE_AVAILABLE;
        } else {
            u = SkillHudArt.U_NODE_LOCKED;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (available) {
            glow(graphics, x, y, size, 1.5F, 0.6F, 0.85F, 1.0F, 0.3F + 0.45F * pulse);
        }
        if (selected || hovered) {
            int ring = Math.round(size * SkillHudArt.NODE_SELECT / (float) SkillHudArt.NODE);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, selected ? 1.0F : 0.55F);
            graphics.blit(SkillHudArt.TEXTURE, x + (size - ring) / 2, y + (size - ring) / 2, ring, ring, SkillHudArt.U_NODE_SELECT,
                    SkillHudArt.V_NODE, SkillHudArt.NODE_SELECT, SkillHudArt.NODE_SELECT, 256, 256);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        graphics.blit(SkillHudArt.TEXTURE, x, y, size, size, u, SkillHudArt.V_NODE, SkillHudArt.NODE, SkillHudArt.NODE, 256, 256);
        int inset = Math.max(1, Math.round(size * 2.0F / SkillHudArt.NODE));
        if (rank <= 0 && !available) {
            float dim = reachable ? 0.72F : 0.42F;
            RenderSystem.setShaderColor(dim, dim + 0.03F, Math.min(1.0F, dim + 0.12F), 1.0F);
        }
        graphics.blit(skill.icon(), x + inset, y + inset, size - inset * 2, size - inset * 2, 0, 0, 32, 32, 32, 32);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (size >= 18) {
            renderPips(graphics, skill, rank, x, y, size);
            int slot = state.slotOf(skill.index());
            if (slot >= 0) {
                String key = KeyBindings.SKILLS[slot].getTranslatedKeyMessage().getString();
                if (key.length() > 2) {
                    key = key.substring(0, 2);
                }
                PoseStack pose = graphics.pose();
                pose.pushPose();
                pose.translate(x + 1, y + 1, 0.0F);
                pose.scale(0.75F, 0.75F, 1.0F);
                graphics.drawString(font, key, 0, 0, 0xFFFFF2A8, true);
                pose.popPose();
            }
        }
    }

    /** スキルの節の下の、段階の印（上位職からの段階は、橙色）。 */
    private static void renderPips(GuiGraphics graphics, SkillInfo skill, int rank, int x, int y, int size) {
        int max = skill.maxRank();
        if (max <= 1) {
            return;
        }
        int pip = size >= 26 ? 3 : 2;
        int step = pip + 1;
        int width = max * step - 1;
        int px = x + (size - width) / 2;
        int py = y + size - pip - 1;
        graphics.fill(px - 1, py - 1, px + width + 1, py + pip + 1, 0xD0080C16);
        for (int r = 1; r <= max; r++) {
            int cx = px + (r - 1) * step;
            boolean upper = skill.ultimate() || r >= skill.upperRank();
            int color = r <= rank ? (upper ? 0xFFFFB347 : 0xFFFFE08A) : (upper ? 0xFF6B4E1E : 0xFF34405C);
            graphics.fill(cx, py, cx + pip, py + pip, color);
        }
    }

    private void renderStatNode(GuiGraphics graphics, ClientPlayerState state, int index, TreeNodeInfo node, int x, int y, int size,
                                boolean selected, boolean hovered, float pulse) {
        boolean notable = node.kindEnum() == SkillTreeDef.Kind.NOTABLE;
        boolean isTaken = state.hasTreeNode(node.id());
        boolean reachable = isTaken || connected(index, state);
        boolean available = !isTaken && reachable && state.skillPoints() >= node.cost();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (selected) {
            glow(graphics, x, y, size, 1.75F, 1.0F, 0.95F, 0.75F, 1.0F);
        } else if (hovered) {
            glow(graphics, x, y, size, 1.65F, 1.0F, 1.0F, 1.0F, 0.7F);
        } else if (available) {
            glow(graphics, x, y, size, 1.55F, 0.6F, 0.85F, 1.0F, 0.3F + 0.45F * pulse);
        }
        if (notable) {
            blitArt(graphics, x, y, size, size, isTaken ? 48 : reachable ? 24 : 0, 32, 24, 24);
        } else {
            blitArt(graphics, x, y, size, size, isTaken ? 32 : reachable ? 16 : 0, 16, 16, 16);
        }
        StatType type = firstStat(node);
        if (type == null) {
            return;
        }
        int glyph = Math.round(size * (notable ? 0.62F : 0.7F));
        if (glyph < 5) {
            return;
        }
        int color = StatusScreen.statColor(type);
        float f = isTaken ? 1.0F : reachable ? 0.8F : 0.42F;
        RenderSystem.setShaderColor(((color >> 16) & 0xFF) / 255.0F * f, ((color >> 8) & 0xFF) / 255.0F * f, (color & 0xFF) / 255.0F * f,
                1.0F);
        blitArt(graphics, x + (size - glyph) / 2, y + (size - glyph) / 2, glyph, glyph, glyphOf(type) * 16, 0, 16, 16);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 木の左上の、短い使い方（全文はツールチップ）。 */
    private void renderHelp(GuiGraphics graphics) {
        int x = canvasX0 + 7;
        int y = canvasY0 + 7;
        int maxWidth = topButtonsLeft - 6 - x;
        if (maxWidth < 20) {
            return;
        }
        String help = UiStyle.clip(font, Component.translatable("screen.amemaru.skill_tree.help_short").getString(), maxWidth);
        graphics.drawString(font, help, x, y, UiStyle.TEXT_DIM, true);
        helpRect = new Rect(x, y - 1, x + font.width(help), y + 9);
    }

    /** 左下のスキル枠6つ。マウスが乗っている枠の番号を返す（なければ -1）。 */
    private int renderLoadout(GuiGraphics graphics, ClientPlayerState state, int mouseX, int mouseY) {
        Rect plate = loadoutPlate();
        graphics.fill(plate.x0(), plate.y0(), plate.x1(), plate.y1() - 1, 0xC8070B16);
        graphics.fill(plate.x0(), plate.y0(), plate.x1(), plate.y0() + 1, 0x708FD3FF);
        graphics.drawString(font, Component.translatable("screen.amemaru.skill_tree.loadout"), loadoutX(), plate.y0() + 3,
                UiStyle.TEXT_HEADER, false);
        int hovered = -1;
        for (int slot = 0; slot < 6; slot++) {
            int x = loadoutX() + slot * SLOT_STEP;
            int y = loadoutY();
            SkillInfo skill = ClientDefinitions.get().skill(state.loadout(slot));
            // 文字を描くと混ぜ合わせ（半透明）が切れるため、絵を描く前に毎回つけ直す
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.blit(SkillHudArt.TEXTURE, x, y, skill == null ? SkillHudArt.U_HEX_EMPTY : SkillHudArt.U_HEX, SkillHudArt.V_HEX,
                    SkillHudArt.HEX_W, SkillHudArt.HEX_H, 256, 256);
            if (skill != null) {
                // アイコンのふち（2ピクセル）を除いた中だけを、六角の中に描く
                graphics.blit(skill.icon(), x + 4, y + 5, 16, 16, 2, 2, 28, 28, 32, 32);
            }
            // キーの名前は、HUDと同じく枠の右下に小さく
            SkillHud.drawKey(graphics, font, slot, x, y, false);
            if (mouseX >= x && mouseX < x + SkillHudArt.HEX_W && mouseY >= y && mouseY < y + SkillHudArt.HEX_H) {
                hovered = slot;
            }
        }
        return hovered;
    }

    // ---------------------------------------------------------------- 右のパネル

    private void renderPanel(GuiGraphics graphics, ClientPlayerState state, JobInfo job, int mouseX, int mouseY) {
        UiStyle.panel(graphics, panelX0, panelY0, panelX1, panelY1);
        int x = panelX0 + 8;
        int right = panelX1 - 8;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(job.icon(), x - 2, panelY0 + 5, 16, 16, 0, 0, 64, 64, 64, 64);
        // 11月24日：職業の名前（ウィザード・アーチャー）が切れないよう、ここは「ポイント」だけ（くわしくはツールチップ）
        Component points = Component.translatable("screen.amemaru.skill_tree.points", state.skillPoints());
        int pointsX = right - font.width(points);
        graphics.drawString(font, points, pointsX, panelY0 + HEADER_TEXT_Y, state.skillPoints() > 0 ? UiStyle.TEXT_GOLD : UiStyle.TEXT_DIM,
                true);
        pointsRect = new Rect(pointsX - 2, panelY0 + HEADER_TEXT_Y - 2, right + 2, panelY0 + HEADER_TEXT_Y + 10);
        graphics.drawString(font, UiStyle.clip(font, job.name(), pointsX - 6 - (x + 18)), x + 18, panelY0 + HEADER_TEXT_Y,
                0xFF000000 | job.color(), true);

        int tabW = (right - x - 3) / 2;
        for (int t = 0; t < 2; t++) {
            int tx = x + t * (tabW + 3);
            int ty = panelY0 + TABS_Y;
            Rect rect = new Rect(tx, ty, tx + tabW, ty + TABS_H);
            tabRects[t] = rect;
            boolean active = panelTab == t;
            boolean hover = rect.contains(mouseX, mouseY);
            graphics.fill(tx, ty, tx + tabW, ty + TABS_H, active ? 0xFF8FD3FF : hover ? 0xFF5A6F96 : 0xFF2C3A58);
            graphics.fill(tx + 1, ty + 1, tx + tabW - 1, ty + TABS_H - (active ? 0 : 1), active ? 0xFF1E2B4F : 0xFF141C30);
            String label = UiStyle.clip(font, Component.translatable(t == TAB_DETAIL ? "screen.amemaru.skill_tree.tab_detail"
                    : "screen.amemaru.skill_tree.tab_stats").getString(), tabW - 6);
            graphics.drawString(font, label, tx + (tabW - font.width(label)) / 2, ty + 2, active ? UiStyle.TEXT_HEADER : UiStyle.TEXT_DIM,
                    false);
        }
        UiStyle.divider(graphics, x, right, panelY0 + DIVIDER_Y);
        int top = panelY0 + CONTENT_Y;
        if (panelTab == TAB_STATS) {
            renderStats(graphics, state, x, top, right, panelY1 - 6);
            return;
        }
        int bottom = takeButton != null && takeButton.visible ? takeButton.getY() - 3 : panelY1 - 6;
        detailOverflow = false;
        TreeNodeInfo node = selectedNode();
        if (node == null) {
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.skill_tree.select").getString(), x, top + 4, right - x,
                    UiStyle.TEXT_DIM, 3);
            return;
        }
        switch (node.kindEnum()) {
            case CORE -> renderCoreDetail(graphics, state, job, x, top, right, bottom);
            case SKILL -> renderSkillDetail(graphics, state, job, node, x, top, right, bottom);
            default -> renderStatDetail(graphics, state, node, x, top, right, bottom);
        }
    }

    /** 核：職業の説明・上位職・専用ゲージ。 */
    private void renderCoreDetail(GuiGraphics graphics, ClientPlayerState state, JobInfo job, int x, int top, int right, int bottom) {
        int width = right - x;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        blitArt(graphics, x, top, 24, 24, 72, 32, 32, 32);
        graphics.blit(job.icon(), x + 4, top + 4, 16, 16, 0, 0, 64, 64, 64, 64);
        graphics.drawString(font, UiStyle.clip(font, job.name(), width - 28), x + 28, top + 1, 0xFF000000 | job.color(), true);
        graphics.drawString(font, UiStyle.clip(font, Component.translatable("screen.amemaru.skill_tree.kind.core").getString(), width - 28),
                x + 28, top + 13, UiStyle.TEXT_DIM, false);
        int y = top + 30;
        int lines = UiStyle.wrapped(graphics, font, job.description(), x, y, width, UiStyle.TEXT, Math.max(1, Math.min(4, (bottom - y) / 10)));
        y += lines * 10 + 4;
        if (job.hasUpper() && y + 10 <= bottom) {
            Component upper = state.promoted() ? Component.translatable("screen.amemaru.skill_tree.upper_done", job.upperName())
                    : Component.translatable("screen.amemaru.skill_tree.upper_todo", job.upperName(), job.upperMinDay(), job.upperMinLevel());
            int count = UiStyle.wrapped(graphics, font, upper.getString(), x, y, width, state.promoted() ? UiStyle.TEXT_GOLD : 0xC9A860,
                    Math.max(1, Math.min(2, (bottom - y) / 10)));
            upperRect = new Rect(x, y - 1, right, y + count * 10);
            y += count * 10 + 4;
        }
        if (job.hasGauge() && y + 10 <= bottom) {
            String gauge = Component.translatable("screen.amemaru.skill_tree.gauge", job.gaugeName()).getString();
            graphics.drawString(font, UiStyle.clip(font, gauge, width), x, y, 0xFF000000 | job.gaugeColor(), false);
            y += 14;
        }
        if (y + 10 <= bottom) {
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.skill_tree.core_hint").getString(), x, y, width,
                    UiStyle.TEXT_DIM, Math.max(1, (bottom - y) / 10));
        }
    }

    /** スキル：説明・段階のつまみ・その段階の数値と効果。 */
    private void renderSkillDetail(GuiGraphics graphics, ClientPlayerState state, JobInfo job, TreeNodeInfo node, int x, int top, int right,
                                   int bottom) {
        SkillInfo skill = ClientDefinitions.get().skill(node.skill());
        if (skill == null) {
            return;
        }
        int width = right - x;
        int rank = state.rank(skill.index());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(skill.icon(), x, top, 24, 24, 0, 0, 32, 32, 32, 32);
        Component rankText = Component.translatable("screen.amemaru.skill_tree.rank", rank, skill.maxRank());
        int rankX = right - font.width(rankText);
        graphics.drawString(font, UiStyle.clip(font, skill.name(), rankX - 4 - (x + 28)), x + 28, top + 1, UiStyle.TEXT, true);
        graphics.drawString(font, rankText, rankX, top + 1, rank > 0 ? UiStyle.TEXT_GOLD : UiStyle.TEXT_DIM, false);
        String schoolName = skill.schoolIndex() >= 0 && skill.schoolIndex() < job.schools().size() ? job.schools().get(skill.schoolIndex()).name()
                : "";
        String school = skill.ultimate()
                ? (schoolName.isEmpty() ? Component.translatable("screen.amemaru.skill_tree.ultimate").getString()
                : Component.translatable("screen.amemaru.skill_tree.ultimate_of", schoolName).getString())
                : schoolName;
        String kind = Component.translatable(skill.passive() ? "screen.amemaru.skill_tree.passive" : "screen.amemaru.skill_tree.active").getString();
        String link = LinkType.byIndex(skill.link()).label();
        String sub = (school.isEmpty() ? "" : school + "・") + kind
                + (link.isEmpty() ? "" : "・" + Component.translatable("screen.amemaru.skill_tree.link", link).getString());
        if (!skill.comboLabel().isEmpty()) {
            sub += "・【" + skill.comboLabel() + "】";
        }
        graphics.drawString(font, UiStyle.clip(font, sub, width - 28), x + 28, top + 13, UiStyle.TEXT_DIM, false);

        int descY = top + 28;
        UiStyle.wrapped(graphics, font, skill.description(), x, descY, width, UiStyle.TEXT, DESC_LINES);
        if (UiStyle.overflows(font, skill.description(), width, DESC_LINES)) {
            descriptionRect = new Rect(x, descY, right, descY + DESC_LINES * 10);
        }
        int tabsY = descY + DESC_LINES * 10 + 3;
        renderRankTabs(graphics, state, skill, x, tabsY, right);

        // 見せている段階の数値と効果
        int shown = Math.max(1, Math.min(shownRank, skill.maxRank()));
        int y = tabsY + RANK_TAB_H + 4;
        String numbers = numbers(skill, shown);
        if (!numbers.isEmpty()) {
            graphics.drawString(font, UiStyle.clip(font, numbers, width), x, y, UiStyle.TEXT_HEADER, false);
            y += 11;
        }
        int linesTop = y;
        int bullet = font.width("・");
        int maxLines = Math.max(1, (bottom - y) / 10);
        List<String> lines = detailLines(skill, shown);
        int drawn = 0;
        for (int index = 0; index < lines.size() && drawn < maxLines; index++) {
            List<String> parts = UiStyle.wrap(font, lines.get(index), width - bullet);
            for (int i = 0; i < parts.size() && drawn < maxLines; i++) {
                boolean moreAfter = i < parts.size() - 1 || index < lines.size() - 1;
                String text = parts.get(i);
                if (drawn == maxLines - 1 && moreAfter) {
                    // 入りきらない：最後の行を「…」にし、ツールチップで全部を見せる
                    text = UiStyle.clip(font, text + "…", width - bullet);
                    detailOverflow = true;
                }
                if (i == 0) {
                    graphics.drawString(font, "・", x, y, UiStyle.TEXT_DIM, false);
                }
                graphics.drawString(font, text, x + bullet, y, UiStyle.TEXT_DIM, false);
                y += 10;
                drawn++;
            }
        }
        linesRect = new Rect(x, linesTop, right, Math.max(linesTop + 10, y));
    }

    /** 段階のつまみ（1〜5）と、見せている段階の状態（習得済み・次の段階・上位職から・Lvから）。 */
    private void renderRankTabs(GuiGraphics graphics, ClientPlayerState state, SkillInfo skill, int x, int y, int right) {
        int rank = state.rank(skill.index());
        rankTabsX = x;
        rankTabsY = y;
        rankTabsCount = skill.maxRank();
        for (int r = 1; r <= skill.maxRank(); r++) {
            int tx = x + (r - 1) * RANK_TAB_STEP;
            boolean shown = r == shownRank;
            boolean learned = r <= rank;
            boolean upperOnly = (skill.ultimate() || r >= skill.upperRank()) && !state.promoted();
            graphics.fill(tx, y, tx + RANK_TAB_W, y + RANK_TAB_H, shown ? 0xFFFFF2A8 : 0xFF3E5274);
            graphics.fill(tx + 1, y + 1, tx + RANK_TAB_W - 1, y + RANK_TAB_H - 1, learned ? 0xFF24506E : 0xFF141C30);
            String label = Integer.toString(r);
            int color = learned ? 0xFFFFFF : upperOnly ? 0xC9A860 : 0x9AA6BF;
            graphics.drawString(font, label, tx + (RANK_TAB_W - font.width(label) + 1) / 2, y + 2, color, false);
        }
        int shown = Math.max(1, Math.min(shownRank, skill.maxRank()));
        String status;
        int color;
        if (shown <= rank) {
            status = Component.translatable("screen.amemaru.skill_tree.status.learned").getString();
            color = UiStyle.TEXT_HEADER;
        } else if ((skill.ultimate() || shown >= skill.upperRank()) && !state.promoted()) {
            status = Component.translatable("screen.amemaru.skill_tree.status.upper").getString();
            color = 0xC9A860;
        } else if (shown == rank + 1 && state.level() < skill.minLevel()) {
            status = Component.translatable("screen.amemaru.skill_tree.status.level", skill.minLevel()).getString();
            color = UiStyle.TEXT_BAD;
        } else if (shown == rank + 1) {
            status = Component.translatable(rank == 0 ? "screen.amemaru.skill_tree.status.first" : "screen.amemaru.skill_tree.status.next")
                    .getString();
            color = UiStyle.TEXT_GOLD;
        } else {
            status = Component.translatable("screen.amemaru.skill_tree.status.later").getString();
            color = UiStyle.TEXT_DIM;
        }
        int statusX = x + skill.maxRank() * RANK_TAB_STEP + 2;
        graphics.drawString(font, UiStyle.clip(font, status, right - statusX), statusX, y + 2, color, false);
    }

    /** ステータスの節：上がるステータス・取れるか・説明。 */
    private void renderStatDetail(GuiGraphics graphics, ClientPlayerState state, TreeNodeInfo node, int x, int top, int right, int bottom) {
        int width = right - x;
        boolean notable = node.kindEnum() == SkillTreeDef.Kind.NOTABLE;
        int index = selectedIndex();
        boolean isTaken = state.hasTreeNode(node.id());
        boolean reachable = isTaken || connected(index, state);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (notable) {
            blitArt(graphics, x, top, 24, 24, isTaken ? 48 : reachable ? 24 : 0, 32, 24, 24);
        } else {
            blitArt(graphics, x + 2, top + 2, 20, 20, isTaken ? 32 : reachable ? 16 : 0, 16, 16, 16);
        }
        StatType type = firstStat(node);
        if (type != null) {
            int color = StatusScreen.statColor(type);
            RenderSystem.setShaderColor(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F);
            blitArt(graphics, x + 5, top + 5, 14, 14, glyphOf(type) * 16, 0, 16, 16);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        graphics.drawString(font, UiStyle.clip(font, nodeTitle(node), width - 28), x + 28, top + 1, notable ? UiStyle.TEXT_GOLD : UiStyle.TEXT,
                true);
        String kind = Component.translatable(notable ? "screen.amemaru.skill_tree.kind.notable" : "screen.amemaru.skill_tree.kind.stat",
                node.cost()).getString();
        graphics.drawString(font, UiStyle.clip(font, kind, width - 28), x + 28, top + 13, UiStyle.TEXT_DIM, false);
        int y = top + 30;
        for (float[] entry : statEntries(node)) {
            StatType stat = statOf(entry[0]);
            int color = stat == null ? 0xFFFFFFFF : StatusScreen.statColor(stat);
            graphics.fill(x + 1, y + 2, x + 5, y + 6, color);
            graphics.drawString(font, UiStyle.clip(font, statText(entry), width - 8), x + 8, y, UiStyle.TEXT, false);
            y += 11;
        }
        y += 3;
        Component status;
        int color;
        Component reason = blocker(index, state);
        if (isTaken) {
            status = Component.translatable("screen.amemaru.skill_tree.state.taken");
            color = UiStyle.TEXT_GOLD;
        } else if (reason == null) {
            status = Component.translatable("screen.amemaru.skill_tree.state.ready");
            color = UiStyle.TEXT_GOOD;
        } else {
            status = shortBlocker(index, state);
            color = UiStyle.TEXT_BAD;
        }
        graphics.drawString(font, UiStyle.clip(font, status.getString(), width), x, y, color, false);
        y += 14;
        if (y + 10 <= bottom) {
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.skill_tree.stat_hint").getString(), x, y, width,
                    UiStyle.TEXT_DIM, Math.max(1, (bottom - y) / 10));
        }
    }

    /** 「ステータス」のつまみ：今のステータスと、ツリーで上がった分（金色）。 */
    private void renderStats(GuiGraphics graphics, ClientPlayerState state, int x, int top, int right, int bottom) {
        int width = right - x;
        graphics.drawString(font, UiStyle.clip(font, Component.translatable("screen.amemaru.skill_tree.stats_header").getString(), width), x, top,
                UiStyle.TEXT_HEADER, false);
        int count = StatType.values().length;
        double[] flat = new double[count];
        double[] percent = new double[count];
        int statNodes = 0;
        int skills = 0;
        if (tree != null) {
            for (TreeNodeInfo node : tree.nodes()) {
                SkillTreeDef.Kind kind = node.kindEnum();
                if (kind == SkillTreeDef.Kind.SKILL) {
                    if (node.skill() >= 0 && state.rank(node.skill()) > 0) {
                        skills++;
                    }
                    continue;
                }
                if (kind == SkillTreeDef.Kind.CORE || !state.hasTreeNode(node.id())) {
                    continue;
                }
                statNodes++;
                for (float[] entry : statEntries(node)) {
                    StatType type = statOf(entry[0]);
                    if (type == null) {
                        continue;
                    }
                    if (entry[2] > 0.5F) {
                        percent[type.ordinal()] += entry[1];
                    } else {
                        flat[type.ordinal()] += entry[1];
                    }
                }
            }
        }
        // ツリーの分の列の幅（いちばん長いものに合わせる）
        int bonusWidth = 0;
        for (StatType type : SHOWN_STATS) {
            bonusWidth = Math.max(bonusWidth, font.width(bonusText(type, flat[type.ordinal()], percent[type.ordinal()])));
        }
        int valueRight = right - (bonusWidth > 0 ? bonusWidth + 4 : 0);
        int y = top + 13;
        int row = 0;
        for (StatType type : SHOWN_STATS) {
            if (y + 10 > bottom) {
                break;
            }
            if (row % 2 == 0) {
                graphics.fill(x - 2, y - 1, right + 2, y + 10, 0x18FFFFFF);
            }
            int color = StatusScreen.statColor(type);
            graphics.fill(x + 1, y + 2, x + 5, y + 6, color);
            String value = type.format(state.stat(type));
            int valueX = valueRight - font.width(value);
            graphics.drawString(font, UiStyle.clip(font, Component.translatable(type.translationKey()).getString(), valueX - 4 - (x + 8)), x + 8,
                    y, UiStyle.TEXT, false);
            graphics.drawString(font, value, valueX, y, UiStyle.TEXT, false);
            String bonus = bonusText(type, flat[type.ordinal()], percent[type.ordinal()]);
            if (!bonus.isEmpty()) {
                graphics.drawString(font, bonus, right - font.width(bonus), y, UiStyle.TEXT_GOLD, false);
            }
            y += 11;
            row++;
        }
        y += 4;
        if (y + 10 <= bottom) {
            String summary = Component.translatable("screen.amemaru.skill_tree.summary", statNodes, skills).getString();
            graphics.drawString(font, UiStyle.clip(font, summary, width), x, y, UiStyle.TEXT_HEADER, false);
            y += 13;
        }
        if (y + 10 <= bottom) {
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.skill_tree.reset_hint").getString(), x, y, width,
                    UiStyle.TEXT_DIM, Math.max(1, (bottom - y) / 10));
        }
    }

    /** ツリーで上がった分（「+24」「+4%」。両方なら「+24/+4%」。なければ空）。 */
    private static String bonusText(StatType type, double flat, double percent) {
        List<String> parts = new ArrayList<>();
        if (flat > 1.0E-6) {
            parts.add("+" + trim((float) flat) + (type.isPercent() ? "%" : ""));
        }
        if (percent > 1.0E-6) {
            parts.add("+" + trim((float) percent) + "%");
        }
        return String.join("/", parts);
    }

    /** その段階の効果の行（サーバーが作った説明）。 */
    private static List<String> detailLines(SkillInfo skill, int rank) {
        return rank - 1 >= 0 && rank - 1 < skill.details().size() ? skill.details().get(rank - 1) : List.of();
    }

    /** 「MP 12・CD 12秒・詠唱 2秒」の形。 */
    private static String numbers(SkillInfo skill, int rank) {
        if (skill.passive()) {
            return "";
        }
        int r = rank - 1;
        List<String> parts = new ArrayList<>();
        if (r < skill.mpCost().length && skill.mpCost()[r] > 0) {
            parts.add("MP " + skill.mpCost()[r]);
        }
        if (r < skill.gaugeCost().length && skill.gaugeCost()[r] > 0) {
            parts.add(Component.translatable("screen.amemaru.skill_tree.gauge_cost", skill.gaugeCost()[r]).getString());
        }
        if (r < skill.cooldown().length && skill.cooldown()[r] > 0) {
            parts.add("CD " + trim(skill.cooldown()[r]) + "秒");
        }
        if (r < skill.cast().length && skill.cast()[r] > 0) {
            // ナイトの「詠唱」は、剣の構え
            JobInfo job = ClientDefinitions.get().job(skill.jobIndex());
            boolean knight = job != null && job.id().getPath().equals("knight");
            parts.add((knight ? "構え " : "詠唱 ") + trim(skill.cast()[r]) + "秒");
        }
        return String.join("・", parts);
    }

    private static String trim(float value) {
        return Math.abs(value - Math.round(value)) < 0.05F ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    // ---------------------------------------------------------------- ツールチップ

    private void renderTooltips(GuiGraphics graphics, ClientPlayerState state, JobInfo job, int mouseX, int mouseY, int hovered,
                                int hoveredSlot) {
        List<FormattedCharSequence> lines = null;
        SkillInfo current = selectedSkill();
        if (pointsRect != null && pointsRect.contains(mouseX, mouseY)) {
            lines = UiStyle.tooltipLines(font, Component.translatable("screen.amemaru.skill_tree.points_help").getString(), 220);
        } else if (helpRect != null && helpRect.contains(mouseX, mouseY)) {
            lines = UiStyle.tooltipLines(font, Component.translatable("screen.amemaru.skill_tree.help").getString(), 220);
        } else if (upperRect != null && upperRect.contains(mouseX, mouseY) && job.hasUpper()) {
            lines = UiStyle.tooltipLines(font, Component.translatable("screen.amemaru.skill_tree.upper_help", job.upperName(), job.upperMinDay(),
                    job.upperMinLevel()).getString(), 220);
        } else if (current != null && descriptionRect != null && descriptionRect.contains(mouseX, mouseY)) {
            lines = UiStyle.tooltipLines(font, current.description(), 220);
        } else if (current != null && detailOverflow && linesRect != null && linesRect.contains(mouseX, mouseY)) {
            lines = new ArrayList<>();
            for (String line : detailLines(current, shownRank)) {
                lines.addAll(UiStyle.tooltipLines(font, "・" + line, 220));
            }
        } else if (hoveredSlot >= 0) {
            SkillInfo inSlot = ClientDefinitions.get().skill(state.loadout(hoveredSlot));
            lines = new ArrayList<>();
            lines.add(Component.translatable("screen.amemaru.skill_tree.slot", hoveredSlot + 1,
                    KeyBindings.SKILLS[hoveredSlot].getTranslatedKeyMessage()).getVisualOrderText());
            lines.add(Component.literal(inSlot == null ? "―" : inSlot.name()).withStyle(ChatFormatting.AQUA).getVisualOrderText());
            lines.add(Component.translatable("screen.amemaru.skill_tree.slot_help").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        } else if (hovered >= 0) {
            lines = nodeTooltip(hovered, state, job);
        } else if (current != null && rankTabsCount > 0 && mouseY >= rankTabsY && mouseY < rankTabsY + RANK_TAB_H) {
            int r = (int) Math.floor((mouseX - rankTabsX) / (double) RANK_TAB_STEP) + 1;
            if (r >= 1 && r <= rankTabsCount && mouseX - rankTabsX - (r - 1) * RANK_TAB_STEP < RANK_TAB_W) {
                lines = List.of(Component.translatable("screen.amemaru.skill_tree.tab", r).getVisualOrderText());
            }
        }
        if (lines != null && !lines.isEmpty()) {
            setTooltipForNextRenderPass(lines);
        }
    }

    /** 木の節のツリーチップ（名前・段階か上がるステータス・取れるか）。 */
    private List<FormattedCharSequence> nodeTooltip(int index, ClientPlayerState state, JobInfo job) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (tree == null || index < 0 || index >= tree.nodes().size()) {
            return lines;
        }
        TreeNodeInfo node = tree.nodes().get(index);
        SkillTreeDef.Kind kind = node.kindEnum();
        if (kind == SkillTreeDef.Kind.CORE) {
            lines.add(Component.literal(job.name()).withStyle(style -> style.withColor(job.color())).getVisualOrderText());
            lines.add(Component.translatable("screen.amemaru.skill_tree.kind.core").withStyle(ChatFormatting.GRAY).getVisualOrderText());
            return lines;
        }
        if (kind == SkillTreeDef.Kind.SKILL) {
            SkillInfo skill = ClientDefinitions.get().skill(node.skill());
            if (skill == null) {
                return lines;
            }
            int rank = state.rank(skill.index());
            lines.add(Component.literal(skill.name()).withStyle(ChatFormatting.AQUA).getVisualOrderText());
            MutableComponent second = Component.translatable("screen.amemaru.skill_tree.rank", rank, skill.maxRank());
            second.append("・").append(Component.translatable(skill.ultimate() ? "screen.amemaru.skill_tree.ultimate"
                    : skill.passive() ? "screen.amemaru.skill_tree.passive" : "screen.amemaru.skill_tree.active"));
            lines.add(second.withStyle(ChatFormatting.GRAY).getVisualOrderText());
            lines.add(nodeStatus(index, state).getVisualOrderText());
            if (skill.ultimate()) {
                for (FormattedCharSequence line : UiStyle.tooltipLines(font,
                        Component.translatable("screen.amemaru.skill_tree.ultimate_choice").getString(), 200)) {
                    lines.add(line);
                }
            }
        } else {
            lines.add(Component.literal(nodeTitle(node)).withStyle(kind == SkillTreeDef.Kind.NOTABLE ? ChatFormatting.GOLD : ChatFormatting.WHITE)
                    .getVisualOrderText());
            if (!node.name().isEmpty()) {
                for (float[] entry : statEntries(node)) {
                    lines.add(Component.literal("・" + statText(entry)).withStyle(ChatFormatting.GRAY).getVisualOrderText());
                }
            }
            lines.add(nodeStatus(index, state).getVisualOrderText());
        }
        lines.add(Component.translatable("screen.amemaru.skill_tree.tooltip.click").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        return lines;
    }

    /** 節が取れるか（ツールチップの1行）。 */
    private Component nodeStatus(int index, ClientPlayerState state) {
        TreeNodeInfo node = tree == null ? null : tree.nodes().get(index);
        if (node == null) {
            return Component.empty();
        }
        if (node.kindEnum() == SkillTreeDef.Kind.SKILL) {
            SkillInfo skill = ClientDefinitions.get().skill(node.skill());
            if (skill == null) {
                return Component.empty();
            }
            int rank = state.rank(skill.index());
            if (rank >= skill.maxRank()) {
                return Component.translatable("screen.amemaru.skill_tree.max").withStyle(ChatFormatting.GOLD);
            }
            if (blocker(index, state) == null) {
                return Component.translatable(rank == 0 ? "screen.amemaru.skill_tree.tooltip.take" : "screen.amemaru.skill_tree.tooltip.rank_up",
                        rankCost(skill, rank + 1)).withStyle(ChatFormatting.GREEN);
            }
            return shortBlocker(index, state).copy().withStyle(ChatFormatting.RED);
        }
        if (state.hasTreeNode(node.id())) {
            return Component.translatable("screen.amemaru.skill_tree.state.taken").withStyle(ChatFormatting.GOLD);
        }
        if (blocker(index, state) == null) {
            return Component.translatable("screen.amemaru.skill_tree.tooltip.take", node.cost()).withStyle(ChatFormatting.GREEN);
        }
        return shortBlocker(index, state).copy().withStyle(ChatFormatting.RED);
    }

    // ---------------------------------------------------------------- 操作

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 動かさずに押したときも、そこからは節を光らせ、説明を出す
        mouseMoved = true;
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        ClientPlayerState state = ClientPlayerState.get();
        // 右のパネルのつまみ
        for (int t = 0; t < 2; t++) {
            if (tabRects[t] != null && tabRects[t].contains(mouseX, mouseY)) {
                panelTab = t;
                updateButton();
                return true;
            }
        }
        // 段階のつまみ
        SkillInfo current = selectedSkill();
        if (panelTab == TAB_DETAIL && current != null && rankTabsCount > 0 && mouseY >= rankTabsY && mouseY < rankTabsY + RANK_TAB_H) {
            for (int r = 1; r <= rankTabsCount; r++) {
                int tx = rankTabsX + (r - 1) * RANK_TAB_STEP;
                if (mouseX >= tx && mouseX < tx + RANK_TAB_W) {
                    shownRank = r;
                    return true;
                }
            }
        }
        // スキル枠
        int slot = slotAt(mouseX, mouseY);
        if (slot >= 0 && tree != null) {
            if (button == 1) {
                ModNetwork.sendToServer(new SetLoadoutC2S(slot, -1));
            } else if (current != null && !current.passive() && state.rank(current.index()) > 0) {
                ModNetwork.sendToServer(new SetLoadoutC2S(slot, current.index()));
            }
            return true;
        }
        // 木：押したところから、動かすか（ドラッグ）、選ぶか（その場で離す）
        if (tree != null && inCanvas(mouseX, mouseY) && !overCanvasUi(mouseX, mouseY) && (button == 0 || button == 1)) {
            panning = true;
            pressButton = button;
            pressX = mouseX;
            pressY = mouseY;
            pressCamX = camX;
            pressCamY = camY;
            pressMoved = false;
            pressNode = nodeAt(mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panning && button == pressButton) {
            if (!pressMoved && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 3.0) {
                pressMoved = true;
            }
            if (pressMoved) {
                camX = pressCamX - (float) (mouseX - pressX) / zoom;
                camY = pressCamY - (float) (mouseY - pressY) / zoom;
                clampCamera();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (panning && button == pressButton) {
            panning = false;
            if (!pressMoved && button == 0) {
                clickNode(pressNode);
            }
            pressNode = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tree != null && inCanvas(mouseX, mouseY)) {
            zoomAt(mouseX, mouseY, delta > 0 ? ZOOM_STEP : 1.0F / ZOOM_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KeyBindings.OPEN_SKILL_TREE.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

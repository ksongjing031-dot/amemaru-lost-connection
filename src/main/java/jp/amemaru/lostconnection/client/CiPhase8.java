package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.tower.FloorDef;
import jp.amemaru.lostconnection.tower.FloorDefs;
import jp.amemaru.lostconnection.tower.FloorFeatures;
import jp.amemaru.lostconnection.tower.forest.LostWoodsService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 自動の確認の写真（Phase 8 その1：2階「忘れられた森」）の段取り：迷いの森の鐘を鳴らす・出口の方でこだまを聞く・
 * 正しい灯台に結び目の灯を置く・品を手に持つ。
 */
final class CiPhase8 {
    /** p8_bench で置いた作業台。 */
    private static BlockPos bench;

    private CiPhase8() {
    }

    /** その種類の行なら true（first のときだけ動かす）。 */
    static boolean action(Minecraft minecraft, String[] shot, boolean first) {
        String kind = shot[0];
        if (!List.of("p8_bell", "p8_listen", "p8_knot", "p8_hold", "p8_valve", "p8_bench", "p8_bench_open", "p8_charm", "p8_lock",
                "p8_bossfx", "p8_tooltip", "p8_say", "p8_settings", "p8_tips", "p8_eat", "p8_tabs").contains(kind)) {
            return false;
        }
        if (!first) {
            return true;
        }
        switch (kind) {
            // 鐘の南2マスに立って鐘を鳴らす（結び目の灯ももらえる）
            case "p8_bell" -> CiPhase3.onServer(minecraft, player -> withWoods(player, shot[3], (def, woods, level) -> {
                BlockPos bell = def.world(woods.bell());
                player.teleportTo(level, bell.getX() + 0.5, bell.getY(), bell.getZ() + 2.5, 180.0F, 10.0F);
                LostWoodsService.ringBell(player, def.number(), woods.id(), bell);
            }));
            // 間違いの出口の灯台のそば（鐘から十分はなれた所）に立って、こだまの方角を聞く
            case "p8_listen" -> CiPhase3.onServer(minecraft, player -> withWoods(player, shot[3], (def, woods, level) -> {
                int correct = woods.exits().indexOf(woods.correctExit());
                FloorFeatures.Exit other = woods.exits().get((correct + woods.exits().size() - 1) % woods.exits().size());
                BlockPos post = def.world(other.post());
                player.teleportTo(level, post.getX() - 1.5, post.getY(), post.getZ() + 0.5, 180.0F, 0.0F);
            }));
            // 正しい灯台の南に立って、結び目の灯を置く（区画がつながり、灯台が灯る）
            case "p8_knot" -> CiPhase3.onServer(minecraft, player -> withWoods(player, shot[3], (def, woods, level) -> {
                FloorFeatures.Exit exit = woods.correctExit();
                if (exit == null) {
                    return;
                }
                BlockPos post = def.world(exit.post());
                player.teleportTo(level, post.getX() + 0.5, post.getY(), post.getZ() + 3.5, 180.0F, 18.0F);
                LostWoodsService.placeKnot(player, def.number(), woods.id(), woods.exits().indexOf(exit), post);
            }));
            case "p8_hold" -> CiPhase3.onServer(minecraft, player -> CiGear.hold(player, "-".equals(shot[3]) ? null : shot[3], null));
            // 11月25日：ボス戦の曲の確かめ。11月26日から、CIの機械は鳴った音をファイルに録るので、音のエンジンが曲を鳴らしているか
            // （active）と、その時刻（clock。録った音の何秒めか）も書く。録った音に、そのボスの曲が入っているかは tools/ci/boss_audio.py が確かめる
            case "p8_bossfx" -> jp.amemaru.lostconnection.AmemaruMod.LOGGER.info(
                    "[amemaru-ci] boss fx ({}): boss={} track={} active={} retries={} master={} volume={} clock={}", shot[3],
                    BossFx.currentBoss(), BossFx.playingTrack(), BossFx.trackActive(), BossFx.trackRetries(),
                    minecraft.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER),
                    jp.amemaru.lostconnection.core.config.ClientConfig.bossMusicVolume(), System.currentTimeMillis());
            // 11月25日：着ている頭と胴の品の説明文を並べて描く（「一式の効果 今 4/4」を撮る。「-」で閉じる）
            case "p8_tooltip" -> {
                if ("-".equals(shot[3]) || minecraft.player == null) {
                    // 11月25日：何もない画面にすると、写真の途中で自動テストが始まる（6階の写真が止まった原因）。CIの間は何も描かない画面へ
                    minecraft.setScreen(CiScreenshots.screenAfterClose());
                } else {
                    minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    minecraft.setScreen(new CiPhase3.TooltipScreen(List.of(
                            minecraft.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).copy(),
                            minecraft.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).copy())));
                }
            }
            // 11月26日：見た目と音の設定の画面を開く（open）・「曲を試しに聞く」を押したのと同じ（preview。2階のボスの曲）・閉じる（-）
            case "p8_settings" -> {
                if ("-".equals(shot[3])) {
                    // 試しに聞いている曲は止めない（10秒流れて、ひとりでに消える。録った音で確かめる）
                    minecraft.setScreen(CiScreenshots.screenAfterClose());
                } else if ("preview".equals(shot[3])) {
                    boolean started = BossFx.preview("old_ward", 10);
                    jp.amemaru.lostconnection.AmemaruMod.LOGGER.info(
                            "[amemaru-ci] boss fx (preview): boss=old_ward track={} active={} retries=0 master={} volume={} clock={}",
                            started ? "old_ward" : "", BossFx.previewActive(),
                            minecraft.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER),
                            jp.amemaru.lostconnection.core.config.ClientConfig.bossMusicVolume(), System.currentTimeMillis());
                } else {
                    minecraft.setScreen(new jp.amemaru.lostconnection.client.screen.SettingsScreen(CiScreenshots.screenAfterClose()));
                }
            }
            // 11月25日：7階の合言葉の扉の前で、チャットに言葉を打ったのと同じ（サーバーの PassphraseService.say）
            case "p8_say" -> CiPhase3.onServer(minecraft, player -> jp.amemaru.lostconnection.AmemaruMod.LOGGER.info(
                    "[amemaru-ci] passphrase \"{}\": {}", shot[3], jp.amemaru.lostconnection.tower.iceforest.PassphraseService.say(player, shot[3])));
            // 11月29日：品の説明文を2つ並べて描く（「名前,名前」。「-」で閉じる）。キャンディーの説明を撮る
            case "p8_tips" -> {
                if ("-".equals(shot[3])) {
                    minecraft.setScreen(CiScreenshots.screenAfterClose());
                } else {
                    List<net.minecraft.world.item.ItemStack> stacks = new java.util.ArrayList<>();
                    for (String name : shot[3].split(",")) {
                        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                                jp.amemaru.lostconnection.AmemaruMod.id(name));
                        if (item != null) {
                            stacks.add(new net.minecraft.world.item.ItemStack(item));
                        }
                    }
                    minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    minecraft.setScreen(new CiPhase3.TooltipScreen(stacks));
                }
            }
            // 11月29日：キャンディーを食べたのと同じ（サーバーで使う。効き目の印が右上に出る）
            case "p8_eat" -> CiPhase3.onServer(minecraft, player -> {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                        jp.amemaru.lostconnection.AmemaruMod.id(shot[3]));
                jp.amemaru.lostconnection.item.ConsumableDef def = item == null ? null
                        : jp.amemaru.lostconnection.item.ConsumableDefs.get(jp.amemaru.lostconnection.AmemaruMod.id(shot[3]));
                if (def != null) {
                    player.getCooldowns().removeCooldown(item);
                    jp.amemaru.lostconnection.AmemaruMod.LOGGER.info("[amemaru-ci] eat {}: {}", shot[3],
                            jp.amemaru.lostconnection.item.ConsumableService.consume(player, new net.minecraft.world.item.ItemStack(item), def));
                }
            });
            // 11月29日：クリエイティブの6つのタブの中身（先頭の品）を並べた確かめの画面（「-」で閉じる）
            case "p8_tabs" -> minecraft.setScreen("-".equals(shot[3]) ? CiScreenshots.screenAfterClose() : new TabsScreen());
            // 11月24日：ロックオンのキー（B）を1回押したのと同じ（見ている敵をロック。照準の印と名前・距離・HPを撮る）
            case "p8_lock" -> TargetLock.ciPress(minecraft);
            // 11月24日：お守りの枠に付ける（「-」で外す）。5階のユニーク「第七の門鍵」の、体に表示するモデルを撮る
            case "p8_charm" -> CiPhase3.onServer(minecraft, player -> CiPhase3.setCurio(player,
                    jp.amemaru.lostconnection.accessory.AccessorySlot.CHARM,
                    "-".equals(shot[3]) ? net.minecraft.world.item.ItemStack.EMPTY : CiPhase3.accessory(shot[3], 1, 1.0)));
            // Phase 8 その2：3階の水門のハンドルの前（2マス）に立って、回す（「組:0か1」）
            case "p8_valve" -> CiPhase3.onServer(minecraft, player -> turnValve(player, shot[3]));
            // Phase 8 その2：目の前（北へ1ブロック）に工房の作業台（armor・weapon・accessory）を置き、次の行で開く
            case "p8_bench" -> {
                bench = minecraft.player == null ? null : minecraft.player.blockPosition().north(1);
                if (bench != null) {
                    CiScreenshots.runCommand(minecraft, "setblock " + bench.getX() + " " + bench.getY() + " " + bench.getZ()
                            + " amemaru:" + shot[3] + "_workbench[facing=south]");
                }
            }
            case "p8_bench_open" -> {
                if (bench != null) {
                    CiPhase3.useWorkbench(minecraft, bench);
                }
            }
            default -> {
            }
        }
        return true;
    }

    /** 地面の印（{@link jp.amemaru.lostconnection.entity.GroundMarker}）が見えている。 */
    static boolean anyGroundMarker(Minecraft minecraft) {
        if (minecraft.level == null) {
            return false;
        }
        for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof jp.amemaru.lostconnection.entity.GroundMarker) {
                return true;
            }
        }
        return false;
    }

    private static void turnValve(ServerPlayer player, String spec) {
        String[] parts = spec.split(":");
        FloorDef def = FloorDefs.byNumber(3);
        ServerLevel level = FloorDefs.towerLevel(player.server);
        FloorFeatures.Floodgate gate = def == null ? null : def.features().floodgate(parts[0]);
        if (gate == null || level == null || parts.length < 2) {
            return;
        }
        int side = Integer.parseInt(parts[1]);
        BlockPos valve = def.world(gate.valves().get(side));
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(valve);
        net.minecraft.core.Direction facing = state.hasProperty(jp.amemaru.lostconnection.tower.waterway.WaterwayBlocks.FACING)
                ? state.getValue(jp.amemaru.lostconnection.tower.waterway.WaterwayBlocks.FACING) : net.minecraft.core.Direction.SOUTH;
        double x = valve.getX() + 0.5 + facing.getStepX() * 2.0;
        double z = valve.getZ() + 0.5 + facing.getStepZ() * 2.0;
        player.teleportTo(level, x, valve.getY(), z, facing.getOpposite().toYRot(), 20.0F);
        jp.amemaru.lostconnection.tower.waterway.FloodgateService.turn(player, def.number(), gate.id(), side, valve);
    }

    private interface WoodsAction {
        void run(FloorDef def, FloorFeatures.LostWoods woods, ServerLevel level);
    }

    private static void withWoods(ServerPlayer player, String id, WoodsAction action) {
        FloorDef def = FloorDefs.byNumber(2);
        ServerLevel level = FloorDefs.towerLevel(player.server);
        FloorFeatures.LostWoods woods = def == null ? null : def.features().lostWoods(id);
        if (woods != null && level != null) {
            action.run(def, woods, level);
        }
    }

    /** 11月29日：クリエイティブの6つのタブの名前・数と、中身の先頭の品を並べる（CI の写真）。 */
    static final class TabsScreen extends net.minecraft.client.gui.screens.Screen {
        TabsScreen() {
            super(net.minecraft.network.chat.Component.empty());
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xF0101420);
            int y = 4;
            int rows = jp.amemaru.lostconnection.core.registry.ModCreativeTabs.Tab.values().length;
            int step = Math.max(30, (height - 8) / rows);
            for (jp.amemaru.lostconnection.core.registry.ModCreativeTabs.Tab tab : jp.amemaru.lostconnection.core.registry.ModCreativeTabs.Tab.values()) {
                net.minecraft.world.item.CreativeModeTab creative = jp.amemaru.lostconnection.core.registry.ModCreativeTabs.registered(tab).get();
                List<net.minecraft.world.item.Item> items = jp.amemaru.lostconnection.core.registry.ModCreativeTabs.items(tab);
                graphics.renderItem(creative.getIconItem(), 6, y);
                graphics.drawString(font, creative.getDisplayName().getString() + "（" + items.size() + "）", 26, y + 4, 0xFFF2D27A, false);
                int perRow = Math.max(1, (width - 32) / 18);
                for (int i = 0; i < Math.min(perRow, items.size()); i++) {
                    graphics.renderItem(new net.minecraft.world.item.ItemStack(items.get(i)), 26 + i * 18, y + 14);
                }
                y += step;
            }
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }
    }
}

package jp.amemaru.lostconnection.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * このModのどの品にも、持ち物のモデル（{@code assets/amemaru/models/item/<名前>.json}）と、名前（言語ファイル）がある。
 *
 * <p>11月26日：11月25日に足した生産職どうしの部品3つ（補強金具・台座布・結晶細工）は、絵はあるのにモデルが抜けていて、持ち物の枠に
 * 「モデルのない品」の箱が出ていた（CI のクライアントのログの「Unable to load model」で見つけた）。サーバーはモデルを読まないので、
 * jar の中のファイルがあるかを確かめる。
 */
@GameTestHolder(AmemaruMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ItemModelGameTests {
    private static final String EMPTY = "empty3x3x3";

    private ItemModelGameTests() {
    }

    /** 登録したどの品にも、持ち物のモデルと、ja_jp の名前がある。 */
    @GameTest(template = EMPTY)
    public static void everyItemHasAModelAndAName(GameTestHelper helper) {
        JsonObject lang = readLanguage();
        List<String> noModel = new ArrayList<>();
        List<String> noName = new ArrayList<>();
        int count = 0;
        for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> entry : ForgeRegistries.ITEMS.getEntries()) {
            ResourceLocation id = entry.getKey().location();
            if (!AmemaruMod.MOD_ID.equals(id.getNamespace())) {
                continue;
            }
            count++;
            if (!exists("/assets/" + id.getNamespace() + "/models/item/" + id.getPath() + ".json")) {
                noModel.add(id.getPath());
            }
            String key = entry.getValue().getDescriptionId();
            if (!lang.has(key)) {
                noName.add(key);
            }
        }
        if (count < 100) {
            helper.fail("このModの品が少なすぎます（" + count + "）。登録を読めていません");
            return;
        }
        if (!noModel.isEmpty()) {
            helper.fail("持ち物のモデルがない品があります：" + noModel);
            return;
        }
        if (!noName.isEmpty()) {
            helper.fail("名前のない品があります：" + noName);
            return;
        }
        helper.succeed();
    }

    private static boolean exists(String path) {
        try (InputStream stream = ItemModelGameTests.class.getResourceAsStream(path)) {
            return stream != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static JsonObject readLanguage() {
        String path = "/assets/" + AmemaruMod.MOD_ID + "/lang/ja_jp.json";
        try (InputStream stream = ItemModelGameTests.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new GameTestAssertException(path + " が見つかりません");
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            throw new GameTestAssertException(path + " を読めません：" + e.getMessage());
        }
    }
}

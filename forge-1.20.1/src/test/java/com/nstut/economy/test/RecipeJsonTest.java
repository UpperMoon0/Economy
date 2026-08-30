package com.nstut.economy.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeJsonTest {

    private static final List<String> NAMES = List.of("market", "vault", "tank");
    private static final List<String> FOLDERS = List.of("recipe", "recipes");

    @Test
    @DisplayName("Economy recipes use object-form ingredients accepted by every supported parser")
    void recipesUseObjectIngredients() throws Exception {
        for (String folder : FOLDERS) {
            for (String name : NAMES) {
                String resource = "/data/economy/" + folder + "/" + name + ".json";
                try (InputStream in = getClass().getResourceAsStream(resource)) {
                    assertNotNull(in, "Missing recipe resource " + resource);
                    JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                    JsonObject key = json.getAsJsonObject("key");
                    for (var entry : key.entrySet()) {
                        assertTrue(entry.getValue().isJsonObject() && entry.getValue().getAsJsonObject().has("item"),
                                resource + " key '" + entry.getKey() + "' must be an object ingredient with 'item'");
                    }
                    JsonObject result = json.getAsJsonObject("result");
                    assertTrue(result.has("item") || result.has("id"),
                            resource + " result must declare item or id");
                }
            }
        }
    }
}

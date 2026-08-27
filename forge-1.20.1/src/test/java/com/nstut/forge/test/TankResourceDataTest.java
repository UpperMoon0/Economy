package com.nstut.economy.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TankResourceDataTest {
    private static final Path RESOURCES = Path.of(
            System.getProperty("economy.repoRoot"), "common", "src", "main", "resources");

    private static JsonObject json(String relativePath) throws IOException {
        try (var reader = Files.newBufferedReader(RESOURCES.resolve(relativePath))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    @DisplayName("Tank has a shaped crafting recipe")
    void tankRecipeExists() throws IOException {
        JsonObject recipe = json("data/economy/recipes/tank.json");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("economy:tank",
                recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals(3, recipe.getAsJsonArray("pattern").size());
    }

    @Test
    @DisplayName("Tank loot table drops the tank block")
    void tankLootTableExists() throws IOException {
        JsonObject loot = json("data/economy/loot_tables/blocks/tank.json");
        JsonObject entry = loot.getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals("economy:tank", entry.get("name").getAsString());
    }

    @Test
    @DisplayName("Tank is mineable with a pickaxe and requires a stone-tier tool")
    void tankMiningTagsExist() throws IOException {
        assertTagContains("data/minecraft/tags/blocks/mineable/pickaxe.json", "economy:tank");
        assertTagContains("data/minecraft/tags/blocks/needs_stone_tool.json", "economy:tank");
    }

    @Test
    @DisplayName("Tank blockstate and models cover all six facings and inventory rendering")
    void tankModelsExist() throws IOException {
        JsonObject variants = json("assets/economy/blockstates/tank.json")
                .getAsJsonObject("variants");
        for (String facing : new String[]{"up", "down", "north", "south", "east", "west"}) {
            assertTrue(variants.has("facing=" + facing), "Missing facing=" + facing);
        }
        assertEquals("minecraft:block/cube",
                json("assets/economy/models/block/tank.json").get("parent").getAsString());
        assertEquals("economy:block/tank",
                json("assets/economy/models/item/tank.json").get("parent").getAsString());
    }

    private static void assertTagContains(String path, String value) throws IOException {
        JsonArray values = json(path).getAsJsonArray("values");
        assertTrue(values.asList().stream().anyMatch(element -> value.equals(element.getAsString())));
    }
}

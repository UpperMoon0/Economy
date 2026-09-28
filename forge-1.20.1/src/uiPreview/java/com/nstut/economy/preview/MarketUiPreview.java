package com.nstut.economy.preview;

import com.nstut.economy.client.EconomyUiThemeMode;
import com.nstut.economy.client.MarketClientStore;
import com.nstut.economy.client.MarketScreen;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.nstut.economy.api.AccountRef;
import com.nstut.economy.api.MarketIdentity;
import com.nstut.economy.blocks.MarketMenu;
import com.nstut.economy.network.MarketNetwork;
import com.nstut.openui.state.Signal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Development-only renderer exercising production MarketScreen layout and widgets. */
@Mod("economy_ui_preview")
@Mod.EventBusSubscriber(modid = "economy_ui_preview", value = Dist.CLIENT)
public final class MarketUiPreview {
    record Case(String name, String view, String role, boolean visible, boolean team, boolean sell) {}
    record Shot(String file, int width, int height, int guiScale, String theme) {}
    static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID TEAM = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final List<Case> CASES = List.of(
        new Case("browse-personal", "BROWSE", "NONE", false, false, false),
        new Case("browse-team", "BROWSE", "OWNER", true, true, false),
        new Case("treasury-owner", "TEAM_TREASURY", "OWNER", true, true, false),
        new Case("treasury-owner-expanded", "TEAM_TREASURY", "OWNER", true, true, false),
        new Case("treasury-officer", "TEAM_TREASURY", "OFFICER", true, true, false),
        new Case("treasury-member", "TEAM_TREASURY", "MEMBER", true, false, false),
        new Case("treasury-unavailable", "TEAM_TREASURY", "NONE", false, true, false),
        new Case("containers-personal", "CONTAINERS", "OWNER", true, false, false),
        new Case("containers-owner", "CONTAINERS", "OWNER", true, true, false),
        new Case("containers-officer", "CONTAINERS", "OFFICER", true, true, false),
        new Case("orders-personal-selected", "ORDERS", "OWNER", true, false, false),
        new Case("orders-team-selected", "ORDERS", "OWNER", true, true, false),
        new Case("new-personal-buy", "NEW_ORDER", "OWNER", true, false, false),
        new Case("new-personal-sell", "NEW_ORDER", "OWNER", true, false, true),
        new Case("new-team-buy", "NEW_ORDER", "OWNER", true, true, false),
        new Case("new-team-sell", "NEW_ORDER", "OWNER", true, true, true));
    static final List<Shot> shots = new ArrayList<>();
    static Path output;
    static int index, ticks;
    static boolean worldRequested, started, advance, done;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++ticks > 6000) throw new IllegalStateException("UI preview timed out");
            if (!worldRequested && mc.level == null && mc.getOverlay() == null && mc.screen != null && ticks >= 20) {
                worldRequested = true;
                com.nstut.Economy.LOGGER.info("Opening deterministic UI preview world");
                mc.createWorldOpenFlows().loadLevel(mc.screen, "world");
            }
            if (!started && mc.level != null && mc.player != null && mc.getOverlay() == null) {
                started = true;
                output = Path.of(System.getProperty("economy.uiPreview.output"));
                Files.createDirectories(output);
                next(mc);
            } else if (advance) {
                advance = false;
                if (++index == CASES.size() * 4) {
                    StringBuilder html = new StringBuilder("<!doctype html><meta charset='utf-8'><title>Economy UI previews</title><style>body{background:#172020;color:#eee;font:16px sans-serif}main{display:flex;flex-wrap:wrap;gap:16px}figure{margin:8px}img{max-width:100%;image-rendering:pixelated}</style><h1>Economy production Market UI</h1><p>Deterministic fixtures, wide/narrow, dark/light. These images validate layout, not multiplayer authorization.</p><main>");
                    for (Shot shot : shots) html.append("<figure><img src='").append(shot.file()).append("'><figcaption>").append(shot.file()).append("</figcaption></figure>");
                    Files.writeString(output.resolve("index.html"), html.append("</main>").toString());
                    Files.writeString(output.resolve("manifest.json"), new GsonBuilder().setPrettyPrinting().create().toJson(shots));
                    done = true; mc.stop();
                } else next(mc);
            }
        } catch (Exception failure) {
            done = true;
            com.nstut.Economy.LOGGER.error("UI preview failed", failure);
            mc.stop();
        }
    }

    static void next(Minecraft mc) throws Exception {
        int scale = (index / CASES.size()) % 2 == 0 ? 2 : 3;
        mc.setScreen(null);
        mc.options.guiScale().set(scale);
        mc.resizeDisplay();
        Case fixture = CASES.get(index % CASES.size());
        fixture(fixture);
        mc.setScreen(new PreviewScreen(fixture, index < CASES.size() * 2
                ? EconomyUiThemeMode.DARK : EconomyUiThemeMode.LIGHT, scale));
    }

    static void fixture(Case c) {
        MarketClientStore.applySyncItemList(new MarketNetwork.SyncItemListPacket("1234.50", 2,
            List.of(new MarketNetwork.ItemCardData("minecraft:iron_ingot", "Iron Ingot", "12.5", 4, 2.5),
                    new MarketNetwork.ItemCardData("minecraft:diamond", "Diamond", "120", 2, -3.0)),
            "HYBRID", c.visible(), "Upper Moon Builders", "98765.25", c.role(), c.visible(),
            c.role().equals("OWNER") || c.role().equals("OFFICER"), c.role().equals("OWNER"), "OFFICER", "OWNER", c.team() ? "TEAM" : "PLAYER"));
        if (c.team()) {
            MarketClientStore.containerEntries.set(List.of(
                    new MarketNetwork.VaultDetailEntry(15,64,32,"minecraft:overworld",24,54,1536,1,false,"",true,"Team / Upper Moon Builders",c.role().equals("OWNER")),
                    new MarketNetwork.VaultDetailEntry(18,64,32,"minecraft:overworld",8000,16000,8000,2,true,"minecraft:water",true,"Team / Upper Moon Builders",c.role().equals("OWNER"))));
            MarketClientStore.activeOrders.set(List.of(
                    new MarketNetwork.ActiveOrderEntry(TEAM,"minecraft:diamond","Diamond","120",8,16,false,false,1700000000000L,
                            new MarketIdentity(AccountRef.team(TEAM),PLAYER,AccountRef.team(TEAM)))));
        } else {
            MarketClientStore.containerEntries.set(List.of(
                    new MarketNetwork.VaultDetailEntry(12,64,32,"minecraft:overworld",12,54,768,1,false,"",false,"Personal",c.role().equals("OWNER"))));
            MarketClientStore.activeOrders.set(List.of(
                    new MarketNetwork.ActiveOrderEntry(PLAYER,"minecraft:iron_ingot","Iron Ingot","12.5",32,64,true,false,1700000000000L,
                            MarketIdentity.personal(PLAYER))));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void signal(MarketScreen screen, String field, Object value) throws Exception {
        var f = MarketScreen.class.getDeclaredField(field); f.setAccessible(true);
        ((Signal) f.get(screen)).set(value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Object marketView(String name) throws Exception {
        Class<? extends Enum> type = (Class<? extends Enum>) Class.forName("com.nstut.economy.client.MarketScreen$MarketView").asSubclass(Enum.class);
        return Enum.valueOf(type, name);
    }
    static final class PreviewScreen extends MarketScreen {
        final Case fixture;
        final int scale;
        final EconomyUiThemeMode theme;
        int frames;
        boolean captured;
        PreviewScreen(Case fixture, EconomyUiThemeMode theme, int scale) throws Exception {
            super(new MarketMenu(0, Minecraft.getInstance().player.getInventory()), Minecraft.getInstance().player.getInventory(), Component.literal("Market"));
            this.fixture=fixture; this.theme=theme; this.scale=scale;
            themeMode.set(theme);
            var f=MarketScreen.class.getDeclaredField("initialDataRequested"); f.setAccessible(true); f.setBoolean(this,true);
            signal(this,"view",marketView(fixture.view()));
            signal(this,"treasuryAmount","100");
            signal(this,"treasuryInfoExpanded", fixture.name().equals("treasury-owner-expanded"));
            signal(this,"createCommodityId","minecraft:iron_ingot"); signal(this,"createCommodityQuery","Iron Ingot");
            signal(this,"createQty","32"); signal(this,"createPrice","12.5"); signal(this,"createSellMode",fixture.sell());
        }
        @Override public void render(GuiGraphics g,int mx,int my,float pt) {
            g.fill(0,0,width,height,0xFF192329);
            super.render(g,-1,-1,0);
            if (!captured && ++frames>=10) {
                g.flush();
                int padding=8;
                try(NativeImage frame=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                    int left=Math.max(0,(leftPos-padding)*scale), top=Math.max(0,(topPos-padding)*scale);
                    int right=Math.min(frame.getWidth(),(leftPos+imageWidth+padding)*scale);
                    int bottom=Math.min(frame.getHeight(),(topPos+imageHeight+padding)*scale);
                    int w=Math.max(1,right-left), h=Math.max(1,bottom-top);
                    try(NativeImage crop=new NativeImage(w,h,false)) {
                        for(int y=0;y<h;y++) for(int x=0;x<w;x++) crop.setPixelRGBA(x,y,frame.getPixelRGBA(left+x,top+y));
                        String name=fixture.name()+"-"+(scale==2?"wide":"narrow")+"-"+theme.name().toLowerCase(Locale.ROOT)+".png";
                        crop.writeToFile(output.resolve(name));
                        shots.add(new Shot(name,w,h,scale,theme.name()));
                        captured=true;
                        advance=true;
                    }
                } catch(Exception failure) {
                    throw new IllegalStateException("Capture failed",failure);
                }
            }
        }
    }
}
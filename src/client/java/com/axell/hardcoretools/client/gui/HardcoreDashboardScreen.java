package com.axell.hardcoretools.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

import com.axell.hardcoretools.HardcoreToolsMod;
import com.axell.hardcoretools.client.ClientStateSnapshot;
import com.axell.hardcoretools.client.HardcoreToolsClient;
import com.axell.hardcoretools.feature.EnchantFeature;
import com.axell.hardcoretools.feature.ItemUpgraderFeature;
import com.axell.hardcoretools.feature.UncraftFeature;
import com.axell.hardcoretools.network.HardcoreNetworking;
import com.axell.hardcoretools.state.HardcoreStateManager;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public class HardcoreDashboardScreen extends Screen {
	private static final Identifier EMBLEM_TEX = Identifier.of(HardcoreToolsMod.MOD_ID, "textures/gui/emblem.png");
	private static final String MOD_VERSION = "v1.7.5";
	private static final String DEVELOPER_NAME = "xVoid Axell";
	private static boolean infoLangIndonesian = true;

	private record TabDef(String key, String fallback, Identifier icon) {
	}

	private record InfoEntry(
			Identifier icon,
			String title,
			String category,
			int accentColor,
			String descId,
			String descEn
	) {
	}

	private static final List<TabDef> TABS = List.of(
			new TabDef("hc.java.tab.protection", "Protection", icon("immortal")),
			new TabDef("hc.java.tab.vein", "Vein & Tree", icon("veinminer")),
			new TabDef("hc.java.tab.sneak", "Sneak Grow", icon("sneakgrow")),
			new TabDef("hc.java.tab.ore", "Ore Plus+", icon("oreplus")),
			new TabDef("hc.java.tab.enchant", "Enchants", icon("enchant")),
			new TabDef("hc.java.tab.books", "Ench. Books", icon("book")),
			new TabDef("hc.java.tab.repair", "Repair", icon("repair")),
			new TabDef("hc.java.tab.upgrader", "Item Upgrader", icon("upgrader")),
			new TabDef("hc.java.tab.uncraft", "Uncraft", icon("uncraft")),
			new TabDef("hc.java.tab.effects", "Effects", icon("effects")),
			new TabDef("hc.java.tab.xp", "XP Manager", icon("xp")),
			new TabDef("hc.java.tab.teleport", "Teleport", icon("teleport")),
			new TabDef("hc.java.tab.waypoints", "Waypoints", icon("waypoint")),
			new TabDef("hc.java.tab.console", "Console", icon("command")),
			new TabDef("hc.java.tab.info", "Info", icon("info"))
	);

	private static final List<InfoEntry> INFO_ENTRIES = List.of(
			new InfoEntry(icon("immortal"), "Immortal", "Protection", 0xFF23D17B,
					"Kebal terhadap seluruh jenis damage, lapar & napas air selalu penuh, serta penyelamatan otomatis saat jatuh ke Void.",
					"Grants full immunity to all incoming damage, keeps hunger and air full, and automatically rescues you from the Void."),
			new InfoEntry(icon("fullbright"), "Fullbright", "Protection", 0xFFFFB838,
					"Penerangan 100% terang seperti siang hari di gua gelap, Deep Dark, dan Nether tanpa kabut atau partikel Night Vision.",
					"Pure client-side daylight visibility inside dark caves, Deep Dark, and the Nether with zero potion fog or particles."),
			new InfoEntry(icon("totem"), "Auto-Totem, Auto-Refill & Panic Saver", "Protection", 0xFFFFB838,
					"Memicu Totem of Undying langsung dari slot tas mana pun saat terkena serangan mematikan, mengisi ulang offhand otomatis (Auto-Refill), dan memicu Totem lebih awal saat HP kritis (Low-HP Panic Saver).",
					"Pops Totems of Undying directly from any inventory slot on lethal hits, auto-restocks your offhand, and pre-emptively triggers a Totem when HP drops below threshold."),
			new InfoEntry(icon("rbd"), "Return by Death", "Protection", 0xFFC86BFF,
					"Mencegah kematian permanen Hardcore setelah Totem habis lalu memutar balik waktu dunia hingga -60 detik (koreografi 20 detik: karakter rebah & item terpental, kaca layar retak & pecah, lalu dunia monokrom fast-reverse memulihkan blok, mob, langit, posisi, dan isi tas).",
					"Intercepts fatal Hardcore deaths when no Totems remain and rewinds up to 60 seconds of world state over a 20s sequence—restoring broken blocks, mobs, sky time, position, and full inventory."),
			new InfoEntry(icon("veinminer"), "Vein Miner & Tree Capitator", "World & Ore", 0xFF38C9FF,
					"Menghancurkan satu urat bijih sekaligus dengan Pickaxe dan menebang pohon alami utuh dengan Axe tanpa merusak bangunan kayu. Dilengkapi Auto-Collect, Auto-Smelt Ores, dan Instant Leaf Decay pintar.",
					"Breaks connected ore veins in one swing with a Pickaxe and fells natural trees with an Axe while ignoring player builds. Includes Auto-Collect, Auto-Smelt Ores, and smart single-tree Instant Leaf Decay."),
			new InfoEntry(icon("sneakgrow"), "Sneak Grow", "World & Ore", 0xFF23D17B,
					"Jongkok (Shift) di dekat tanaman pertanian, bibit pohon, tebu, atau jamur untuk mempercepat pertumbuhan, lengkap dengan opsi tumbuh instan penuh (Instant Full Maturity) & partikel.",
					"Crouch/sneak near crops, saplings, sugar cane, or fungi to rapidly accelerate growth, with optional Instant Full Maturity and green sparkle particles."),
			new InfoEntry(icon("oreplus"), "Ore Plus+", "World & Ore", 0xFFFF8C38,
					"Menggandakan hasil tambang bijih mentah (x2 hingga x4) dan dapat diatur aktif/nonaktif untuk masing-masing jenis bijih.",
					"Multiplies raw ore drops (x2 to x4) with individual per-ore filters so you control exactly which ores get boosted."),
			new InfoEntry(icon("enchant"), "Enchants & God Stack (Lv. 25)", "Gear & Magic", 0xFFC86BFF,
					"Pasang enchantment langsung ke senjata, alat, atau armor di tas hingga Over-Enchant Level XXV (25), 1-Click Auto Max All Gear, serta God Stack untuk menggabungkan enchant yang normalnya saling bentrok.",
					"Apply enchantments directly to any gear in your bag up to Over-Enchant Level XXV (25), 1-Click Auto Max All Gear, and God Stack to combine mutually exclusive enchantments."),
			new InfoEntry(icon("book"), "Enchanted Books", "Gear & Magic", 0xFFFFB838,
					"Ambil buku enchantment apa pun secara instan mulai dari batas standar Vanilla hingga Over-Enchant Level XXV (25).",
					"Instantly obtain any Enchanted Book from the full registry at either Vanilla max level or custom Over-Enchant levels up to XXV (25)."),
			new InfoEntry(icon("repair"), "Repair Workshop", "Gear & Magic", 0xFF38C9FF,
					"Perbaiki instan durability item di tangan utama, armor yang sedang dipakai, hotbar, atau seluruh isi inventory sekaligus tanpa Anvil.",
					"Instantly restore full durability to your held item, equipped armor, hotbar, or entire inventory with zero Anvil penalty."),
			new InfoEntry(icon("upgrader"), "Item Upgrader (Arcane Altar)", "Gear & Magic", 0xFFE02444,
					"Korbankan item dari tas untuk menempa item target pilihan (otomatis mewariskan enchantment). Jika gagal, kamu tetap mendapat XP dan mengisi Arcane Pity (Soft Pity di 70%, jaminan 100% Jackpot di 100%).",
					"Sacrifice items from your bag to forge any chosen target reward while inheriting enchantments. Failed rolls grant XP and build Arcane Pity (Soft Pity at 70%, guaranteed 100% Jackpot at 100%)."),
			new InfoEntry(icon("uncraft"), "Uncraft & Book Extractor", "Gear & Magic", 0xFF23D17B,
					"Bongkar kembali item hasil crafting menjadi bahan mentahnya dan ekstrak seluruh enchantment menjadi Enchanted Book terpisah (Split Enchanted Books).",
					"Deconstruct crafted items back into their raw ingredients and extract applied enchantments into Enchanted Books (with optional Split Enchanted Books)."),
			new InfoEntry(icon("effects"), "Potion Effects", "Gear & Magic", 0xFFC86BFF,
					"Berikan efek potion positif instan hingga Tier X dengan durasi detik kustom atau Infinite Duration (∞), serta hapus efek buruk sekali klik.",
					"Apply positive status buffs up to Tier X with custom seconds or Infinite Duration (∞), or cure negative effects in a single click."),
			new InfoEntry(icon("xp"), "XP Manager", "Gear & Magic", 0xFF23D17B,
					"Atur level XP ke angka pasti, tambah/kurangi +1 hingga +100 level secara instan, atau reset XP kembali ke 0.",
					"Set your exact XP level, add or subtract +1 to +100 levels instantly, or reset your XP back to zero."),
			new InfoEntry(icon("teleport"), "Teleport & Pinpoint Locator", "Navigation", 0xFF38C9FF,
					"Lacak & teleport aman (Instant Safe Teleport) langsung ke titik inti Biome, Struktur (End Portal Room, End Ship, Ancient City, Trial Vault), Pemain, atau Koordinat X Y Z lintas dimensi + fitur ↩ Back.",
					"Locate and safely warp directly to Biomes, Structure cores (End Portal Room, End Ship, Ancient City, Trial Vault), online players, or exact X Y Z coordinates across dimensions with ↩ Back support."),
			new InfoEntry(icon("waypoint"), "Waypoints", "Navigation", 0xFFFFB838,
					"Simpan lokasi penting dengan nama dan penanda warna di Overworld, Nether, maupun The End untuk teleport kembali kapan saja.",
					"Save custom named and color-coded locations across Overworld, Nether, and The End for one-click cross-dimensional warping."),
			new InfoEntry(icon("command"), "Console, GameMode & Gamerules", "Navigation", 0xFFE02444,
					"Jalankan command meski di dunia Hardcore tanpa cheats, ganti GameMode (Creative, Survival, Hardcore, Spectator, Adventure), atur waktu/cuaca, dan ubah World Gamerules.",
					"Execute commands even in locked Hardcore worlds, switch between 5 GameModes (including Hardcore), control time/weather, and toggle World Gamerules."),
			new InfoEntry(EMBLEM_TEX, "Quick Radial Wheel", "Shortcut", 0xFFFF4264,
					"Tekan tombol R sekali di dalam game (tanpa perlu ditahan) untuk membuka menu roda cepat 8 fitur favorit, lalu Klik Kiri untuk mengaktifkan.",
					"Press R once in-game (single-press toggle, no hold needed) to open the 8-slice Quick Radial Wheel, then Left-Click any slice to trigger.")
	);

	private record NamedEntry(String id, String label) {
	}

	private static final List<NamedEntry> BIOMES = List.of(
			new NamedEntry("minecraft:cherry_grove", "Cherry Grove"),
			new NamedEntry("minecraft:mushroom_fields", "Mushroom Fields"),
			new NamedEntry("minecraft:deep_dark", "Deep Dark"),
			new NamedEntry("minecraft:lush_caves", "Lush Caves"),
			new NamedEntry("minecraft:dripstone_caves", "Dripstone Caves"),
			new NamedEntry("minecraft:meadow", "Meadow"),
			new NamedEntry("minecraft:plains", "Plains"),
			new NamedEntry("minecraft:sunflower_plains", "Sunflower Plains"),
			new NamedEntry("minecraft:flower_forest", "Flower Forest"),
			new NamedEntry("minecraft:dark_forest", "Dark Forest"),
			new NamedEntry("minecraft:jungle", "Jungle"),
			new NamedEntry("minecraft:bamboo_jungle", "Bamboo Jungle"),
			new NamedEntry("minecraft:mangrove_swamp", "Mangrove Swamp"),
			new NamedEntry("minecraft:swamp", "Swamp"),
			new NamedEntry("minecraft:desert", "Desert"),
			new NamedEntry("minecraft:badlands", "Badlands"),
			new NamedEntry("minecraft:ice_spikes", "Ice Spikes"),
			new NamedEntry("minecraft:jagged_peaks", "Jagged Peaks"),
			new NamedEntry("minecraft:warm_ocean", "Warm Ocean"),
			new NamedEntry("minecraft:crimson_forest", "Crimson Forest (Nether)"),
			new NamedEntry("minecraft:warped_forest", "Warped Forest (Nether)"),
			new NamedEntry("minecraft:soul_sand_valley", "Soul Sand Valley (Nether)"),
			new NamedEntry("minecraft:basalt_deltas", "Basalt Deltas (Nether)"),
			new NamedEntry("minecraft:end_highlands", "End Highlands (The End)")
	);

	private static final List<NamedEntry> STRUCTURES = List.of(
			new NamedEntry("minecraft:trial_chambers", "Trial Chambers"),
			new NamedEntry("minecraft:ancient_city", "Ancient City"),
			new NamedEntry("minecraft:stronghold", "Stronghold"),
			new NamedEntry("minecraft:mansion", "Woodland Mansion"),
			new NamedEntry("minecraft:monument", "Ocean Monument"),
			new NamedEntry("minecraft:village_plains", "Plains Village"),
			new NamedEntry("minecraft:village_desert", "Desert Village"),
			new NamedEntry("minecraft:village_taiga", "Taiga Village"),
			new NamedEntry("minecraft:pillager_outpost", "Pillager Outpost"),
			new NamedEntry("minecraft:desert_pyramid", "Desert Pyramid"),
			new NamedEntry("minecraft:jungle_pyramid", "Jungle Temple"),
			new NamedEntry("minecraft:igloo", "Igloo"),
			new NamedEntry("minecraft:swamp_hut", "Witch Hut"),
			new NamedEntry("minecraft:trail_ruins", "Trail Ruins"),
			new NamedEntry("minecraft:shipwreck", "Shipwreck"),
			new NamedEntry("minecraft:buried_treasure", "Buried Treasure"),
			new NamedEntry("minecraft:fortress", "Nether Fortress"),
			new NamedEntry("minecraft:bastion_remnant", "Bastion Remnant"),
			new NamedEntry("minecraft:end_city", "End City")
	);

	private static final List<NamedEntry> EFFECTS = List.of(
			new NamedEntry("minecraft:night_vision", "Night Vision"),
			new NamedEntry("minecraft:speed", "Speed"),
			new NamedEntry("minecraft:haste", "Haste"),
			new NamedEntry("minecraft:strength", "Strength"),
			new NamedEntry("minecraft:regeneration", "Regeneration"),
			new NamedEntry("minecraft:resistance", "Resistance"),
			new NamedEntry("minecraft:fire_resistance", "Fire Resistance"),
			new NamedEntry("minecraft:water_breathing", "Water Breathing"),
			new NamedEntry("minecraft:jump_boost", "Jump Boost"),
			new NamedEntry("minecraft:absorption", "Absorption"),
			new NamedEntry("minecraft:health_boost", "Health Boost"),
			new NamedEntry("minecraft:saturation", "Saturation"),
			new NamedEntry("minecraft:slow_falling", "Slow Falling"),
			new NamedEntry("minecraft:conduit_power", "Conduit Power"),
			new NamedEntry("minecraft:dolphins_grace", "Dolphin's Grace"),
			new NamedEntry("minecraft:hero_of_the_village", "Hero of the Village")
	);

	private static final int[] WAYPOINT_COLORS = {
			0xFFDC1E36, 0xFFFF8822, 0xFFFFCC22, 0xFF23D17B, 0xFF38C9FF, 0xFFA26BFF, 0xFFFF66B8
	};

	private record ClickZone(int x, int y, int w, int h, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private record SliderZone(String id, int x, int y, int w, int h, int min, int max, IntConsumer onPreview, IntConsumer onRelease) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}

		int valueAt(double mx) {
			double clamped = Math.max(0.0, Math.min(1.0, (mx - x) / (double) Math.max(1, w)));
			return min + (int) Math.round(clamped * (max - min));
		}
	}

	private int panelX, panelY, panelW, panelH;
	private int sidebarW = 120;
	private int dockW = 108;
	private int headerH = 34;
	private double sidebarScroll = 0.0;
	private double targetSidebarScroll = 0.0;
	private double contentScroll = 0.0;
	private double targetContentScroll = 0.0;
	private int maxContentScroll = 0;
	private int maxSidebarScroll = 0;
	private float animatedActiveTabRelY = -1.0F;
	private long tabSwitchMs = 0L;
	private long lastUiFrameNs = System.nanoTime();
	private String hoveredCustomTip = "";

	private final List<ClickZone> clickZones = new ArrayList<>();
	private final List<SliderZone> sliderZones = new ArrayList<>();
	private SliderZone activeSlider = null;
	private int pendingSliderValue = 0;

	private TextFieldWidget primaryInput;
	private String searchQuery = "";
	private ItemStack hoveredTooltipStack = ItemStack.EMPTY;

	public HardcoreDashboardScreen() {
		super(Text.literal("Void Sovereign"));
	}

	private static Identifier icon(String name) {
		return Identifier.of(HardcoreToolsMod.MOD_ID, "textures/gui/icons/" + name + ".png");
	}

	@Override
	protected void init() {
		CrimsonTheme.applyTheme(ClientStateSnapshot.INSTANCE.uiTheme);
		this.panelW = Math.max(348, Math.min(592, this.width - 20));
		this.panelH = Math.max(228, Math.min(340, this.height - 18));
		this.sidebarW = (this.panelW >= 510) ? 120 : 108;
		this.dockW = (this.panelW >= 460) ? ((this.panelW >= 540) ? 110 : 92) : 0;
		this.panelX = (this.width - this.panelW) / 2;
		this.panelY = (this.height - this.panelH) / 2;

		int centerW = panelW - sidebarW - dockW;
		int inputX = panelX + sidebarW + 26;
		int inputY = panelY + headerH + 12;
		int inputW = Math.max(80, centerW - 38);

		this.primaryInput = new TextFieldWidget(this.textRenderer, inputX, inputY, inputW, 12, Text.literal("Input"));
		this.primaryInput.setMaxLength(160);
		this.primaryInput.setDrawsBackground(false);
		this.primaryInput.setEditableColor(0xFFFFFFFF);
		this.addSelectableChild(this.primaryInput);
		configureInputForActiveTab();
	}

	private void configureInputForActiveTab() {
		if (this.primaryInput == null) return;
		int tab = ClientStateSnapshot.INSTANCE.activeTab;
		int centerW = panelW - sidebarW - dockW;
		int inputX = panelX + sidebarW + 26;
		int inputY = panelY + headerH + 12;
		int inputW = Math.max(80, centerW - 38);
		this.primaryInput.setX(inputX);
		this.primaryInput.setY(inputY);
		this.primaryInput.setWidth(inputW);

		switch (tab) {
			case 4, 5, 14 -> {
				this.primaryInput.setVisible(true);
				this.primaryInput.setPlaceholder(Text.translatable("hc.java.search").formatted(Formatting.DARK_GRAY));
				this.primaryInput.setText(this.searchQuery);
				this.primaryInput.setChangedListener(val -> {
					this.searchQuery = val;
					this.contentScroll = 0;
					this.targetContentScroll = 0;
				});
			}
			case 7 -> {
				this.primaryInput.setVisible(true);
				this.primaryInput.setPlaceholder(Text.literal("Search any target item...").formatted(Formatting.DARK_GRAY));
				this.primaryInput.setText(this.searchQuery);
				this.primaryInput.setChangedListener(val -> {
					this.searchQuery = val;
				});
			}
			case 10 -> {
				this.primaryInput.setVisible(true);
				this.primaryInput.setChangedListener(val -> {
				});
				this.primaryInput.setPlaceholder(Text.literal("Enter target XP Level...").formatted(Formatting.DARK_GRAY));
				this.primaryInput.setText(String.valueOf(ClientStateSnapshot.INSTANCE.xpLevel));
			}
			case 11 -> {
				if (ClientStateSnapshot.INSTANCE.teleportSubTab == 3) {
					this.primaryInput.setVisible(true);
					this.primaryInput.setChangedListener(val -> {
					});
					this.primaryInput.setPlaceholder(Text.literal("X Y Z (e.g. 0 80 0)").formatted(Formatting.DARK_GRAY));
					ClientStateSnapshot s = ClientStateSnapshot.INSTANCE;
					this.primaryInput.setText(((int) s.posX) + " " + ((int) s.posY) + " " + ((int) s.posZ));
				} else {
					this.primaryInput.setVisible(true);
					this.primaryInput.setPlaceholder(Text.translatable("hc.java.search").formatted(Formatting.DARK_GRAY));
					this.primaryInput.setText(this.searchQuery);
					this.primaryInput.setChangedListener(val -> {
						this.searchQuery = val;
						this.contentScroll = 0;
						this.targetContentScroll = 0;
					});
				}
			}
			case 12 -> {
				this.primaryInput.setVisible(true);
				this.primaryInput.setChangedListener(val -> {
				});
				this.primaryInput.setPlaceholder(Text.literal("New Waypoint Name (e.g. Base)...").formatted(Formatting.DARK_GRAY));
				this.primaryInput.setText("");
			}
			case 13 -> {
				this.primaryInput.setVisible(true);
				this.primaryInput.setChangedListener(val -> {
				});
				this.primaryInput.setPlaceholder(Text.literal("Run command without / or with /...").formatted(Formatting.DARK_GRAY));
				this.primaryInput.setText("");
			}
			default -> {
				this.primaryInput.setVisible(false);
				this.primaryInput.setFocused(false);
			}
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Native 1.21.1 Frosted Gaussian Blur (called once per frame; 100% compatible with Sodium & Iris!)
		if (this.client != null && this.client.world != null) {
			try {
				this.applyBlur(delta);
			} catch (Exception ignored) {
			}
		}
		int topTint = CrimsonTheme.withAlpha(CrimsonTheme.BG_WINDOW, 132);
		int botTint = CrimsonTheme.withAlpha(CrimsonTheme.BG_HEADER_TOP, 168);
		context.fillGradient(0, 0, this.width, this.height, topTint, botTint);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		this.clickZones.clear();
		this.sliderZones.clear();
		this.hoveredTooltipStack = ItemStack.EMPTY;
		this.hoveredCustomTip = "";

		long nowNs = System.nanoTime();
		float uiDt = Math.min(0.05F, Math.max(0.0F, (nowNs - this.lastUiFrameNs) / 1_000_000_000.0F));
		this.lastUiFrameNs = nowNs;

		// Smooth inertia scrolling (60-240 FPS)
		double scrollLerp = 1.0 - Math.exp(-uiDt * 22.0);
		this.targetSidebarScroll = Math.max(0.0, Math.min(this.maxSidebarScroll, this.targetSidebarScroll));
		this.targetContentScroll = Math.max(0.0, Math.min(this.maxContentScroll, this.targetContentScroll));
		this.sidebarScroll += (this.targetSidebarScroll - this.sidebarScroll) * scrollLerp;
		this.contentScroll += (this.targetContentScroll - this.contentScroll) * scrollLerp;

		ClientStateSnapshot s = ClientStateSnapshot.INSTANCE;
		if (CrimsonTheme.getActiveThemeIndex() != s.uiTheme) {
			CrimsonTheme.applyTheme(s.uiTheme);
		}

		if (s.upgraderModalOpen) {
			// Pure distraction-free full-screen Ritual Sanctuary (no background panel clutter or bag-grid spoilers!)
			renderRitualGachaModal(ctx, s, mouseX, mouseY);
			if (!this.hoveredTooltipStack.isEmpty()) {
				ctx.getMatrices().push();
				ctx.getMatrices().translate(0.0F, 0.0F, 420.0F);
				ctx.drawItemTooltip(this.textRenderer, this.hoveredTooltipStack, mouseX, mouseY);
				ctx.getMatrices().pop();
			}
			return;
		}

		CrimsonTheme.drawPanel(ctx, panelX, panelY, panelW, panelH);

		// 1. Modern Frosted Header Bar + 5-Theme Live Color Swatches + Status Pills
		CrimsonTheme.drawRoundedGradient(ctx, panelX + 1, panelY + 1, panelW - 2, headerH - 1, 7.0F,
				CrimsonTheme.BG_HEADER_TOP, CrimsonTheme.BG_HEADER_BOT);
		ctx.fillGradient(panelX + 2, panelY + headerH, panelX + panelW - 2, panelY + headerH + 1,
				CrimsonTheme.withAlpha(CrimsonTheme.BORDER_OUTER, 200),
				CrimsonTheme.withAlpha(CrimsonTheme.BORDER_SUBTLE, 140));

		// HD 64x64 Emblem & Spacious Title
		CrimsonTheme.drawCircle(ctx, panelX + 20, panelY + 17, 11.5F, CrimsonTheme.withAlpha(CrimsonTheme.CRIMSON_BRIGHT, 38));
		ctx.drawTexture(EMBLEM_TEX, panelX + 10, panelY + 7, 20, 20, 0.0F, 0.0F, 64, 64, 64, 64);
		ctx.drawTextWithShadow(this.textRenderer, "VOID SOVEREIGN", panelX + 35, panelY + 8, CrimsonTheme.TEXT_PRIMARY);
		String subLine = MOD_VERSION + " · " + CrimsonTheme.getActivePalette().name();
		ctx.drawTextWithShadow(this.textRenderer, subLine, panelX + 35, panelY + 19, CrimsonTheme.TEXT_MUTED);

		// Close Button (Rightmost)
		int closeX = panelX + panelW - 25;
		int closeY = panelY + 8;
		boolean closeHover = inRect(mouseX, mouseY, closeX, closeY, 18, 18);
		CrimsonTheme.drawSubtleButton(ctx, this.textRenderer, closeX, closeY, 18, 18, "✕", false, closeHover);
		clickZones.add(new ClickZone(closeX, closeY, 18, 18, this::close));

		// 5 Live Color Theme Swatches
		int swatchStep = 14;
		int swatchGroupW = CrimsonTheme.PALETTES.length * swatchStep + 6;
		int swatchStartX = closeX - 8 - swatchGroupW;
		int swatchCy = panelY + 17;
		CrimsonTheme.drawRoundedRect(ctx, swatchStartX - 3, panelY + 9, swatchGroupW, 16, 8.0F,
				CrimsonTheme.withAlpha(CrimsonTheme.BG_INPUT, 210));
		CrimsonTheme.drawRoundedOutline(ctx, swatchStartX - 3, panelY + 9, swatchGroupW, 16, 8.0F, 0.9F, CrimsonTheme.BORDER_SUBTLE);

		for (int ti = 0; ti < CrimsonTheme.PALETTES.length; ti++) {
			final int themeIdx = ti;
			CrimsonTheme.ThemePalette pal = CrimsonTheme.PALETTES[ti];
			float scx = swatchStartX + 7.0F + ti * swatchStep;
			boolean selTheme = (s.uiTheme == ti);
			boolean hovTheme = inRect(mouseX, mouseY, Math.round(scx - 6), swatchCy - 6, 12, 12);
			if (selTheme) {
				CrimsonTheme.drawCircle(ctx, scx, swatchCy, 5.8F, 0xFFFFFFFF);
			} else if (hovTheme) {
				CrimsonTheme.drawCircle(ctx, scx, swatchCy, 5.4F, CrimsonTheme.withAlpha(pal.swatchColor(), 135));
			}
			CrimsonTheme.drawCircle(ctx, scx, swatchCy, selTheme ? 4.2F : (hovTheme ? 4.3F : 3.7F), pal.swatchColor());
			if (hovTheme) {
				this.hoveredCustomTip = "Theme: " + pal.name();
			}
			clickZones.add(new ClickZone(Math.round(scx - 6), swatchCy - 6, 12, 12, () -> {
				s.uiTheme = themeIdx;
				CrimsonTheme.applyTheme(themeIdx);
				sendIntAction("set_ui_theme", themeIdx);
			}));
		}

		// Live Header Status Pills (Collision-Guarded against left title)
		int minChipLeft = panelX + 38 + Math.max(this.textRenderer.getWidth("VOID SOVEREIGN"), this.textRenderer.getWidth(subLine)) + 10;
		int chipRight = swatchStartX - 8;
		int chipY = panelY + 11;

		String totemChip = "Totem: " + s.totemCount;
		int tw = this.textRenderer.getWidth(totemChip) + 10;
		if (chipRight - tw >= minChipLeft) {
			chipRight -= tw;
			CrimsonTheme.drawBadge(ctx, this.textRenderer, chipRight, chipY, totemChip,
					s.autoTotem ? 0x38FFB838 : CrimsonTheme.BG_INPUT,
					s.autoTotem ? CrimsonTheme.GOLD : CrimsonTheme.BORDER_SUBTLE,
					s.autoTotem ? CrimsonTheme.GOLD : CrimsonTheme.TEXT_SECONDARY);
		}

		if (s.returnByDeath) {
			String rbdChip = s.returnByDeathCooldownLeft > 0
					? ("⏪ " + s.returnByDeathCooldownLeft + "s")
					: ("⏪ RBD -" + s.returnByDeathSeconds + "s");
			int rw = this.textRenderer.getWidth(rbdChip) + 10;
			if (chipRight - (rw + 4) >= minChipLeft) {
				chipRight -= (rw + 4);
				CrimsonTheme.drawBadge(ctx, this.textRenderer, chipRight, chipY, rbdChip,
						0x38C86BFF, CrimsonTheme.AMETHYST, CrimsonTheme.AMETHYST);
			}
		}

		if (s.immortal) {
			String immChip = "IMMORTAL";
			int iw = this.textRenderer.getWidth(immChip) + 10;
			if (chipRight - (iw + 4) >= minChipLeft) {
				chipRight -= (iw + 4);
				CrimsonTheme.drawBadge(ctx, this.textRenderer, chipRight, chipY, immChip,
						0x3823D17B, CrimsonTheme.EMERALD, CrimsonTheme.EMERALD);
			}
		}

		if (s.fullbright) {
			String fbChip = "☀ FULLBRIGHT";
			int fw = this.textRenderer.getWidth(fbChip) + 10;
			if (chipRight - (fw + 4) >= minChipLeft) {
				chipRight -= (fw + 4);
				CrimsonTheme.drawBadge(ctx, this.textRenderer, chipRight, chipY, fbChip,
						0x38FFB838, CrimsonTheme.GOLD, CrimsonTheme.GOLD);
			}
		}

		// 2. Categorized Left Glass Sidebar with Smooth Sliding Selection Pill
		int sideX = panelX + 1;
		int sideY = panelY + headerH + 1;
		int sideH = panelH - headerH - 2;
		CrimsonTheme.drawRoundedRect(ctx, sideX + 1, sideY + 1, sidebarW - 1, sideH - 2, 6.0F, CrimsonTheme.BG_SIDEBAR);
		ctx.fill(sideX + sidebarW, sideY + 2, sideX + sidebarW + 1, sideY + sideH - 2, CrimsonTheme.BORDER_SUBTLE);

		// Compute sidebar layout height dynamically including category headers
		int catCount = 0;
		for (int i = 0; i < TABS.size(); i++) {
			if (sidebarCategoryTitle(i) != null) {
				catCount++;
			}
		}
		int totalSideH = TABS.size() * 22 + catCount * 12 + 10;
		this.maxSidebarScroll = Math.max(0, totalSideH - sideH);
		this.sidebarScroll = Math.max(0.0, Math.min(this.maxSidebarScroll, this.sidebarScroll));

		// Precompute relative Y of each tab so our sliding active pill glides smoothly!
		int[] tabRelYs = new int[TABS.size()];
		int cursorRelY = 5;
		for (int i = 0; i < TABS.size(); i++) {
			if (sidebarCategoryTitle(i) != null) {
				cursorRelY += 12;
			}
			tabRelYs[i] = cursorRelY;
			cursorRelY += 22;
		}
		float targetActiveRelY = tabRelYs[Math.max(0, Math.min(TABS.size() - 1, s.activeTab))];
		if (this.animatedActiveTabRelY < 0.0F) {
			this.animatedActiveTabRelY = targetActiveRelY;
		} else {
			this.animatedActiveTabRelY += (targetActiveRelY - this.animatedActiveTabRelY) * (1.0F - (float) Math.exp(-uiDt * 22.0F));
		}

		ctx.enableScissor(sideX, sideY, sideX + sidebarW, sideY + sideH);

		// Draw smooth sliding active pill behind active tab row
		int rowX = sideX + 5;
		int rowW = sidebarW - 10;
		int rowH = 20;
		float animScreenY = sideY + this.animatedActiveTabRelY - (float) this.sidebarScroll;
		CrimsonTheme.drawRoundedGradient(ctx, rowX, animScreenY, rowW, rowH, 5.0F,
				CrimsonTheme.withAlpha(CrimsonTheme.CRIMSON, 110),
				CrimsonTheme.withAlpha(CrimsonTheme.CRIMSON_DARK, 175));
		CrimsonTheme.drawRoundedOutline(ctx, rowX, animScreenY, rowW, rowH, 5.0F, 1.0F, CrimsonTheme.CRIMSON_BRIGHT);
		CrimsonTheme.drawRoundedRect(ctx, rowX + 2.0F, animScreenY + 4.0F, 2.5F, rowH - 8.0F, 1.25F, CrimsonTheme.CRIMSON_BRIGHT);

		for (int i = 0; i < TABS.size(); i++) {
			final int idx = i;
			String cat = sidebarCategoryTitle(i);
			int tabY = sideY + tabRelYs[i] - (int) Math.round(this.sidebarScroll);

			if (cat != null) {
				int catY = tabY - 10;
				ctx.getMatrices().push();
				ctx.getMatrices().translate(rowX + 3.0F, catY + 1.0F, 0.0F);
				ctx.getMatrices().scale(0.72F, 0.72F, 1.0F);
				ctx.drawTextWithShadow(this.textRenderer, cat, 0, 0, CrimsonTheme.TEXT_MUTED);
				ctx.getMatrices().pop();
			}

			TabDef tab = TABS.get(i);
			boolean active = (s.activeTab == i);
			boolean hovered = inRect(mouseX, mouseY, sideX, sideY, sidebarW, sideH) && inRect(mouseX, mouseY, rowX, tabY, rowW, rowH);

			if (!active && hovered) {
				CrimsonTheme.drawRoundedRect(ctx, rowX, tabY, rowW, rowH, 5.0F, CrimsonTheme.BG_CARD_HOVER);
				CrimsonTheme.drawRoundedOutline(ctx, rowX, tabY, rowW, rowH, 5.0F, 0.9F, CrimsonTheme.BORDER_HOVER);
			}

			// Render new 64x64 HD transparent icon cleanly at 15x15
			ctx.drawTexture(tab.icon(), rowX + 6, tabY + 2, 15, 15, 0.0F, 0.0F, 64, 64, 64, 64);
			String label = Text.translatableWithFallback(tab.key(), tab.fallback()).getString();
			ctx.drawTextWithShadow(this.textRenderer, trimToWidth(label, rowW - 31), rowX + 25, tabY + 6,
					active ? CrimsonTheme.TEXT_PRIMARY : (hovered ? CrimsonTheme.TEXT_PRIMARY : CrimsonTheme.TEXT_SECONDARY));

			if (isTabFeatureActive(s, i)) {
				CrimsonTheme.drawCircle(ctx, rowX + rowW - 5.5F, tabY + 10.0F, 2.4F, CrimsonTheme.EMERALD);
			}

			if (tabY + rowH > sideY && tabY < sideY + sideH) {
				int clipY1 = Math.max(sideY, tabY);
				int clipY2 = Math.min(sideY + sideH, tabY + rowH);
				clickZones.add(new ClickZone(rowX, clipY1, rowW, clipY2 - clipY1, () -> {
					if (s.activeTab != idx) {
						s.activeTab = idx;
						this.contentScroll = 0;
						this.targetContentScroll = 0;
						this.searchQuery = "";
						this.tabSwitchMs = System.currentTimeMillis();
						configureInputForActiveTab();
					}
				}));
			}
		}
		ctx.disableScissor();

		// 3. Center Content Workspace (with Custom Rounded Frosted Search/Command Bar)
		int workX = panelX + sidebarW + 2;
		int centerSpanW = panelW - sidebarW - dockW - 4;
		int topInputPad = (this.primaryInput != null && this.primaryInput.isVisible()) ? 28 : 0;
		if (this.primaryInput != null && this.primaryInput.isVisible()) {
			int ibX = workX + 8;
			int ibY = panelY + headerH + 7;
			int ibW = centerSpanW - 18;
			int ibH = 18;
			boolean focused = this.primaryInput.isFocused();
			CrimsonTheme.drawRoundedRect(ctx, ibX, ibY, ibW, ibH, 5.0F, CrimsonTheme.BG_INPUT);
			CrimsonTheme.drawRoundedOutline(ctx, ibX, ibY, ibW, ibH, 5.0F, 1.0F,
					focused ? CrimsonTheme.CRIMSON_BRIGHT : CrimsonTheme.BORDER_SUBTLE);
			String glyph = (s.activeTab == 13) ? ">_" : "⌕";
			ctx.drawTextWithShadow(this.textRenderer, glyph, ibX + 6, ibY + 5,
					focused ? CrimsonTheme.CRIMSON_BRIGHT : CrimsonTheme.TEXT_MUTED);
			this.primaryInput.render(ctx, mouseX, mouseY, delta);
		}

		int workY = panelY + headerH + 2 + topInputPad;
		int workW = centerSpanW;
		int workH = panelH - headerH - 4 - topInputPad;

		// Subtle 130ms slide-up entrance when switching tabs
		float tabSlideOffset = 0.0F;
		long sinceSwitch = System.currentTimeMillis() - this.tabSwitchMs;
		if (sinceSwitch >= 0L && sinceSwitch < 130L) {
			float t = 1.0F - (sinceSwitch / 130.0F);
			tabSlideOffset = t * t * 4.5F;
		}

		ctx.enableScissor(workX, workY, workX + workW, workY + workH);
		int startY = workY + 6 - (int) Math.round(this.contentScroll) + Math.round(tabSlideOffset);
		int endY = renderActiveTabContent(ctx, s, workX + 8, startY, workW - 18, workY, workY + workH, mouseX, mouseY);
		int totalWorkH = (endY - startY) + 12;
		this.maxContentScroll = Math.max(0, totalWorkH - workH);
		if (this.contentScroll > this.maxContentScroll) {
			this.contentScroll = this.maxContentScroll;
		}
		if (this.targetContentScroll > this.maxContentScroll) {
			this.targetContentScroll = this.maxContentScroll;
		}
		ctx.disableScissor();

		// Sleek Rounded Scrollbar Indicator
		if (this.maxContentScroll > 0) {
			int sbX = workX + workW - 6;
			CrimsonTheme.drawRoundedRect(ctx, sbX, workY + 4, 3.0F, workH - 8, 1.5F, CrimsonTheme.withAlpha(CrimsonTheme.BORDER_SUBTLE, 110));
			int trackH = workH - 8;
			int thumbH = Math.max(18, (int) ((workH / (double) totalWorkH) * trackH));
			int thumbY = workY + 4 + (int) ((this.contentScroll / Math.max(1, this.maxContentScroll)) * (trackH - thumbH));
			CrimsonTheme.drawRoundedRect(ctx, sbX, thumbY, 3.0F, thumbH, 1.5F, CrimsonTheme.CRIMSON_BRIGHT);
		}

		// 4. Right Interactive 3D Character Showcase Dock
		if (this.dockW > 0) {
			renderPlayerShowcaseDock(ctx, s, panelX + panelW - dockW - 1, panelY + headerH + 1, dockW, panelH - headerH - 2, mouseX, mouseY);
		}

		// 5. Rounded Toast Notification Banner
		if (System.currentTimeMillis() < s.toastExpireMs && !s.toastText.isEmpty()) {
			int tw2 = Math.min(workW - 12, this.textRenderer.getWidth(s.toastText) + 20);
			int tx = workX + workW - tw2 - 8;
			int ty = panelY + panelH - 23;
			int border = s.toastOk ? CrimsonTheme.EMERALD : CrimsonTheme.CRIMSON_BRIGHT;
			CrimsonTheme.drawRoundedRect(ctx, tx, ty, tw2, 17, 5.0F, CrimsonTheme.withAlpha(CrimsonTheme.BG_WINDOW, 246));
			CrimsonTheme.drawRoundedOutline(ctx, tx, ty, tw2, 17, 5.0F, 1.0F, border);
			CrimsonTheme.drawRoundedRect(ctx, tx + 2.5F, ty + 3.5F, 2.5F, 10.0F, 1.25F, border);
			ctx.drawTextWithShadow(this.textRenderer, trimToWidth(s.toastText, tw2 - 14), tx + 8, ty + 5, CrimsonTheme.TEXT_PRIMARY);
		}

		// 6. Held Item or Swatch Hover Tooltip
		if (!this.hoveredTooltipStack.isEmpty()) {
			ctx.drawItemTooltip(this.textRenderer, this.hoveredTooltipStack, mouseX, mouseY);
		} else if (!this.hoveredCustomTip.isEmpty()) {
			ctx.drawTooltip(this.textRenderer, Text.literal(this.hoveredCustomTip), mouseX, mouseY);
		}
	}

	private static String sidebarCategoryTitle(int tabIdx) {
		return switch (tabIdx) {
			case 0 -> "SURVIVAL";
			case 1 -> "WORLD";
			case 4 -> "GEAR & MAGIC";
			case 11 -> "NAVIGATION";
			case 14 -> "INFO";
			default -> null;
		};
	}

	private void renderPlayerShowcaseDock(DrawContext ctx, ClientStateSnapshot s, int dx, int dy, int dw, int dh, int mx, int my) {
		ctx.fill(dx, dy + 2, dx + 1, dy + dh - 2, CrimsonTheme.BORDER_SUBTLE);
		CrimsonTheme.drawRoundedRect(ctx, dx + 2, dy + 2, dw - 4, dh - 4, 6.0F, CrimsonTheme.BG_SIDEBAR);

		int padX = dx + 6;
		int innerW = dw - 12;
		int curY = dy + 7;

		// 1. World / GameMode Pill Badge at Top of 3D Dock
		String modeStr = switch (s.gameMode == null ? "survival" : s.gameMode) {
			case "hardcore" -> "HARDCORE";
			case "creative" -> "CREATIVE";
			case "spectator" -> "SPECTATOR";
			case "adventure" -> "ADVENTURE";
			default -> "SURVIVAL";
		};
		int modeCol = "hardcore".equals(s.gameMode) ? CrimsonTheme.CRIMSON_BRIGHT
				: ("creative".equals(s.gameMode) ? CrimsonTheme.GOLD : CrimsonTheme.EMERALD);
		int pillW = Math.min(innerW, this.textRenderer.getWidth(modeStr) + 10);
		CrimsonTheme.drawBadge(ctx, this.textRenderer, dx + (dw - pillW) / 2, curY, modeStr,
				CrimsonTheme.withAlpha(modeCol, 42), modeCol, modeCol);
		curY += 15;

		// 2. Interactive 3D Player Model Stage (Skin, Armor, Elytra & Held Items track mouse!)
		int quickBtnH = 18;
		int statsH = 56;
		int stageH = Math.max(76, dh - (curY - dy) - statsH - quickBtnH - 14);
		CrimsonTheme.drawRoundedGradient(ctx, padX, curY, innerW, stageH, 6.0F,
				CrimsonTheme.withAlpha(CrimsonTheme.BG_INPUT, 215),
				CrimsonTheme.withAlpha(CrimsonTheme.BG_CARD, 225));
		CrimsonTheme.drawRoundedOutline(ctx, padX, curY, innerW, stageH, 6.0F, 1.0F, CrimsonTheme.BORDER_SUBTLE);

		// Glowing Pedestal Aura Ring beneath player's feet
		float pedCx = padX + innerW * 0.5F;
		float pedCy = curY + stageH - 14.0F;
		CrimsonTheme.drawRoundedRect(ctx, pedCx - innerW * 0.34F, pedCy, innerW * 0.68F, 4.0F, 2.0F,
				CrimsonTheme.withAlpha(CrimsonTheme.CRIMSON_BRIGHT, 78));

		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			int entityScale = Math.max(22, Math.min(34, (stageH - 18) / 2));
			// Clamp look target slightly so head turn stays natural even when mouse is far away
			float lookX = Math.max(padX - 90.0F, Math.min(padX + innerW + 90.0F, (float) mx));
			float lookY = Math.max(curY - 50.0F, Math.min(curY + stageH + 50.0F, (float) my));
			InventoryScreen.drawEntity(ctx, padX + 2, curY + 2, padX + innerW - 2, curY + stageH - 4,
					entityScale, 0.0625F, lookX, lookY, mc.player);

			String pName = trimToWidth(mc.player.getName().getString(), innerW - 4);
			ctx.drawTextWithShadow(this.textRenderer, pName,
					dx + (dw - this.textRenderer.getWidth(pName)) / 2, curY + stageH + 3, CrimsonTheme.TEXT_PRIMARY);
		}
		curY += stageH + 15;

		// 3. Live Health & XP Mini Vitals Card
		CrimsonTheme.drawRoundedRect(ctx, padX, curY, innerW, 36, 5.0F, CrimsonTheme.BG_CARD);
		CrimsonTheme.drawRoundedOutline(ctx, padX, curY, innerW, 36, 5.0F, 0.9F, CrimsonTheme.BORDER_SUBTLE);

		float maxHp = Math.max(1.0F, s.maxHealth);
		float hpRatio = Math.max(0.0F, Math.min(1.0F, s.health / maxHp));
		String hpTxt = String.format(Locale.ROOT, "❤ %.0f/%.0f", s.health, maxHp);
		ctx.getMatrices().push();
		ctx.getMatrices().translate(padX + 5.0F, curY + 4.0F, 0.0F);
		ctx.getMatrices().scale(0.78F, 0.78F, 1.0F);
		ctx.drawTextWithShadow(this.textRenderer, hpTxt, 0, 0, 0xFFFF6B84);
		ctx.getMatrices().pop();

		int barX = padX + 5;
		int barW = innerW - 10;
		CrimsonTheme.drawRoundedRect(ctx, barX, curY + 13, barW, 4.5F, 2.2F, CrimsonTheme.BG_INPUT);
		if (hpRatio > 0.02F) {
			CrimsonTheme.drawRoundedGradient(ctx, barX, curY + 13, Math.max(4.0F, barW * hpRatio), 4.5F, 2.2F,
					0xFFFF5277, 0xFFD91E48);
		}

		String xpCoord = "✦ Lv." + s.xpLevel + " · Y:" + (int) Math.round(s.posY);
		ctx.getMatrices().push();
		ctx.getMatrices().translate(padX + 5.0F, curY + 23.0F, 0.0F);
		ctx.getMatrices().scale(0.76F, 0.76F, 1.0F);
		ctx.drawTextWithShadow(this.textRenderer, trimToWidth(xpCoord, (int) ((innerW - 8) / 0.76F)), 0, 0, CrimsonTheme.GOLD);
		ctx.getMatrices().pop();

		// 4. Bottom Radial Quick-Wheel Button
		int btnY = dy + dh - quickBtnH - 5;
		boolean qHov = inRect(mx, my, padX, btnY, innerW, quickBtnH);
		String wheelKey = HardcoreToolsClient.getKeyLabel(HardcoreToolsClient.radialWheelKey, "R");
		String wheelBtnTxt = trimToWidth("Wheel [" + wheelKey + "]", innerW - 8);
		CrimsonTheme.drawSubtleButton(ctx, this.textRenderer, padX, btnY, innerW, quickBtnH, wheelBtnTxt, false, qHov);
		if (qHov) {
			this.hoveredCustomTip = "Open Quick Radial Wheel or press [" + wheelKey + "]";
		}
		clickZones.add(new ClickZone(padX, btnY, innerW, quickBtnH, () -> {
			if (this.client != null) {
				this.client.setScreen(new RadialWheelScreen());
			}
		}));
	}

	private int renderActiveTabContent(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		return switch (s.activeTab) {
			case 0 -> renderProtectionTab(ctx, s, x, y, w, clipTop, clipBot, mx, my);
			case 1 -> renderVeinTab(ctx, s, x, y, w, clipTop, clipBot, mx, my);
			case 2 -> renderSneakTab(ctx, s, x, y, w, clipTop, clipBot, mx, my);
			case 3 -> renderOrePlusTab(ctx, s, x, y, w, clipTop, clipBot, mx, my);
			case 4 -> renderEnchantTab(ctx, s, x, y, w, clipTop, clipBot, mx, my);
			case 5 -> renderBooksTab(ctx, s, x, y, w, clipTop, clipBot, mx, my);
			case 6 -> renderRepairTab(ctx, s, x, y, w, clipTop, clipBot, mx, my);
			case 7 -> renderUpgraderTab(ctx, s, x, y, w, clipTop, clipBot, mx, my);
			case 8 -> renderUncraftTab(ctx, s, x, y, w, clipTop, clipBot, mx, my);
			case 9 -> renderEffectsTab(ctx, s, x, y, w, clipTop, clipBot, mx, my);
			case 10 -> renderXpTab(ctx, s, x, y, w, clipTop, clipBot, mx, my);
			case 11 -> renderTeleportTab(ctx, s, x, y, w, clipTop, clipBot, mx, my);
			case 12 -> renderWaypointsTab(ctx, s, x, y, w, clipTop, clipBot, mx, my);
			case 13 -> renderConsoleTab(ctx, s, x, y, w, clipTop, clipBot, mx, my);
			case 14 -> renderInfoTab(ctx, s, x, y, w, clipTop, clipBot, mx, my);
			default -> y;
		};
	}

	// ==================== TAB 0: PROTECTION ====================
	private int renderProtectionTab(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "SURVIVAL SHIELD & VISION");
		y += 18;
		y = drawToggleCard(ctx, x, y, w, "Immortal",
				s.immortal, clipTop, clipBot, mx, my, () -> sendAction("toggle_immortal", new JsonObject()));
		y = drawToggleCard(ctx, x, y, w, "Fullbright",
				s.fullbright, clipTop, clipBot, mx, my, () -> {
					s.fullbright = !s.fullbright;
					JsonObject o = new JsonObject();
					o.addProperty("value", s.fullbright);
					sendAction("toggle_fullbright", o);
				});

		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y + 4, w, "AUTO-TOTEM: " + s.totemCount + "");
		y += 22;
		y = drawToggleCard(ctx, x, y, w, "Auto-Totem",
				s.autoTotem, clipTop, clipBot, mx, my, () -> sendAction("toggle_autototem", new JsonObject()));
		y = drawToggleCard(ctx, x, y, w, "Auto-Refill",
				s.autoTotemOffhand, clipTop, clipBot, mx, my, () -> sendAction("toggle_autototem_offhand", new JsonObject()));
		y = drawToggleCard(ctx, x, y, w, "Low-HP Panic Saver",
				s.panicSaver, clipTop, clipBot, mx, my, () -> sendAction("toggle_panic_saver", new JsonObject()));

		y = drawSliderCard(ctx, "panic_hp", x, y, w, "Panic HP Threshold", (s.panicHpThreshold / 2.0F) + " ❤",
				s.panicHpThreshold, 2, 18, clipTop, clipBot, mx, my,
				v -> s.panicHpThreshold = v,
				v -> sendIntAction("set_panic_hp", v));

		String rbdHdr = s.returnByDeathCooldownLeft > 0
				? ("⏪ RETURN BY DEATH COOLDOWN: " + s.returnByDeathCooldownLeft + "S")
				: "⏪ RETURN BY DEATH";
		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y + 4, w, rbdHdr);
		y += 22;
		y = drawToggleCard(ctx, x, y, w, "Return by Death",
				s.returnByDeath, clipTop, clipBot, mx, my, () -> sendAction("toggle_rbd", new JsonObject()));

		y = drawSliderCard(ctx, "rbd_sec", x, y, w,
				"Rewind Time Window",
				"-" + s.returnByDeathSeconds + "s",
				s.returnByDeathSeconds, 15, 60, clipTop, clipBot, mx, my,
				v -> s.returnByDeathSeconds = v,
				v -> sendIntAction("set_rbd_seconds", v));

		String cdBadge = s.returnByDeathCooldown == 0 ? "Always Ready" : (s.returnByDeathCooldown + "s");
		y = drawSliderCard(ctx, "rbd_cd", x, y, w,
				"Rewind Cooldown",
				cdBadge,
				s.returnByDeathCooldown, 0, 300, clipTop, clipBot, mx, my,
				v -> s.returnByDeathCooldown = v,
				v -> sendIntAction("set_rbd_cooldown", v));
		return y;
	}

	// ==================== TAB 1: VEIN MINER & TREE ====================
	private int renderVeinTab(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "VEIN MINER & TREE CAPITATOR");
		y += 18;
		y = drawToggleCard(ctx, x, y, w, "Vein Miner",
				s.veinMiner, clipTop, clipBot, mx, my, () -> sendAction("toggle_vein", new JsonObject()));
		y = drawToggleCard(ctx, x, y, w, "Tree Capitator",
				s.treeCapitator, clipTop, clipBot, mx, my, () -> sendAction("toggle_tree", new JsonObject()));
		y = drawToggleCard(ctx, x, y, w, "Auto-Collect",
				s.veinAutoCollect, clipTop, clipBot, mx, my, () -> sendAction("toggle_autocollect", new JsonObject()));
		y = drawToggleCard(ctx, x, y, w, "Auto-Smelt Ores",
				s.veinAutoSmelt, clipTop, clipBot, mx, my, () -> sendAction("toggle_autosmelt", new JsonObject()));
		y = drawToggleCard(ctx, x, y, w, "Instant Leaf Decay",
				s.fastLeafDecay, clipTop, clipBot, mx, my, () -> sendAction("toggle_leafdecay", new JsonObject()));

		y = drawSliderCard(ctx, "vein_max", x, y, w, "Max Chain Blocks per Break", s.veinMaxBlocks + " blocks",
				s.veinMaxBlocks, 4, 128, clipTop, clipBot, mx, my,
				v -> s.veinMaxBlocks = v,
				v -> sendIntAction("set_vein_max", v));
		return y;
	}

	// ==================== TAB 2: SNEAK GROW ====================
	private int renderSneakTab(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "SNEAK GROW AURA");
		y += 18;
		y = drawToggleCard(ctx, x, y, w, "Sneak Grow",
				s.sneakGrow, clipTop, clipBot, mx, my, () -> sendAction("toggle_sneakgrow", new JsonObject()));
		y = drawToggleCard(ctx, x, y, w, "Instant Full Maturity",
				s.sneakGrowInstant, clipTop, clipBot, mx, my, () -> sendAction("toggle_sneak_instant", new JsonObject()));
		y = drawToggleCard(ctx, x, y, w, "Sparkle Particles",
				s.sneakGrowParticles, clipTop, clipBot, mx, my, () -> sendAction("toggle_sneak_particles", new JsonObject()));

		y = drawSliderCard(ctx, "sneak_rad", x, y, w, "Aura Radius", s.sneakGrowRadius + " blocks",
				s.sneakGrowRadius, 1, 10, clipTop, clipBot, mx, my,
				v -> s.sneakGrowRadius = v,
				v -> sendIntAction("set_sneak_radius", v));
		y = drawSliderCard(ctx, "sneak_spd", x, y, w, "Pulse Speed Sneak", "Speed " + s.sneakGrowSpeed,
				s.sneakGrowSpeed, 1, 20, clipTop, clipBot, mx, my,
				v -> s.sneakGrowSpeed = v,
				v -> sendIntAction("set_sneak_speed", v));
		return y;
	}

	// ==================== TAB 3: ORE PLUS+ ====================
	private int renderOrePlusTab(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "ORE PLUS+");
		y += 18;
		y = drawToggleCard(ctx, x, y, w, "Ore Plus Boost",
				s.orePlusEnabled, clipTop, clipBot, mx, my, () -> sendAction("toggle_oreplus", new JsonObject()));

		y = drawSliderCard(ctx, "ore_mult", x, y, w, "Ore Yield Multiplier", "x" + s.orePlusMultiplier,
				s.orePlusMultiplier, 2, 4, clipTop, clipBot, mx, my,
				v -> s.orePlusMultiplier = v,
				v -> sendIntAction("set_ore_multiplier", v));

		int half = (w - 6) / 2;
		drawActionBtn(ctx, x, y, half, 18, "Enable All Ores", CrimsonTheme.CRIMSON, clipTop, clipBot, mx, my, () -> {
			JsonObject o = new JsonObject();
			o.addProperty("enable", true);
			sendAction("set_all_ores", o);
		});
		drawActionBtn(ctx, x + half + 6, y, half, 18, "Disable All Ores", 0xFF3A2029, clipTop, clipBot, mx, my, () -> {
			JsonObject o = new JsonObject();
			o.addProperty("enable", false);
			sendAction("set_all_ores", o);
		});
		y += 24;

		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "PER-ORE FILTER");
		y += 16;
		List<String> keys = HardcoreStateManager.ALL_ORE_KEYS;
		for (int i = 0; i < keys.size(); i += 2) {
			for (int col = 0; col < 2 && (i + col) < keys.size(); col++) {
				String key = keys.get(i + col);
				int cx = x + col * (half + 6);
				boolean on = Boolean.TRUE.equals(s.ores.getOrDefault(key, true));
				boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, cx, y, half, 20);
				CrimsonTheme.drawCard(ctx, cx, y, half, 20, hov, on ? CrimsonTheme.CRIMSON : 0);
				ctx.drawTextWithShadow(textRenderer, formatIdLabel(key), cx + 7, y + 6, on ? CrimsonTheme.TEXT_PRIMARY : CrimsonTheme.TEXT_MUTED);
				CrimsonTheme.drawPillSwitch(ctx, cx + half - 34, y + 3, on, hov);
				addClippedClick(cx, y, half, 20, clipTop, clipBot, () -> {
					JsonObject o = new JsonObject();
					o.addProperty("ore", key);
					sendAction("toggle_single_ore", o);
				});
			}
			y += 23;
		}
		return y;
	}

	// ==================== TAB 4: ENCHANTMENTS (NATIVE LV. 25) ====================
	private int renderEnchantTab(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		y = drawTargetEnchantShowcase(ctx, s, x, y, w, clipTop, clipBot, mx, my);

		// Interactive 3D Equipment & Bag Slot Picker (no need to hold items in mainhand!)
		if (!s.gearSlots.isEmpty()) {
			CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "SELECT GEAR — " + s.gearSlots.size() + "");
			y += 15;

			int cellW = 24;
			int cellH = 24;
			int gap = 3;
			int cols = Math.max(1, (w + gap) / (cellW + gap));
			int col = 0;
			int rowY = y;

			for (ClientStateSnapshot.GearSlotItem gi : s.gearSlots) {
				int cx = x + col * (cellW + gap);
				boolean selected = (gi.slot == s.selectedEnchantSlot);
				boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, cx, rowY, cellW, cellH);

				drawModernSlotBox(ctx, cx, rowY, cellW, cellH, selected, hov, CrimsonTheme.GOLD);

				ItemStack slotStack = getClientStackAtSlot(gi.slot);
				if (!slotStack.isEmpty()) {
					ctx.drawItem(slotStack, cx + 4, rowY + 4);
					ctx.drawItemInSlot(this.textRenderer, slotStack, cx + 4, rowY + 4);
					if (hov) {
						this.hoveredTooltipStack = slotStack;
					}
				}
				if (gi.enchantCount > 0) {
					CrimsonTheme.drawCircle(ctx, cx + cellW - 4.0F, rowY + 4.0F, 1.8F, CrimsonTheme.EMERALD);
				}

				final int targetSlot = gi.slot;
				addClippedClick(cx, rowY, cellW, cellH, clipTop, clipBot, () -> {
					s.selectedEnchantSlot = targetSlot;
					JsonObject o = new JsonObject();
					o.addProperty("slot", targetSlot);
					sendAction("select_enchant_slot", o);
				});

				col++;
				if (col >= cols) {
					col = 0;
					rowY += cellH + gap;
				}
			}
			y = (col == 0 ? rowY : rowY + cellH + gap) + 3;
		}

		// Row 1: Selected Item Auto Max (Vanilla vs Over-Enchant Lv. 25) + Clear
		int third = (w - 8) / 3;
		drawActionBtn(ctx, x, y, third, 19, "Auto Max Vanilla", 0xFF2D5B88, clipTop, clipBot, mx, my, () -> {
			JsonObject o = new JsonObject();
			o.addProperty("over25", false);
			o.addProperty("slot", s.selectedEnchantSlot);
			sendAction("enchant_automax", o);
		});
		drawActionBtn(ctx, x + third + 4, y, third, 19, "Auto Max Lv. 25", CrimsonTheme.CRIMSON, clipTop, clipBot, mx, my, () -> {
			JsonObject o = new JsonObject();
			o.addProperty("over25", true);
			o.addProperty("slot", s.selectedEnchantSlot);
			sendAction("enchant_automax", o);
		});
		drawActionBtn(ctx, x + (third + 4) * 2, y, w - (third + 4) * 2, 19, "Clear Item", 0xFF47202B, clipTop, clipBot, mx, my, () -> {
			JsonObject o = new JsonObject();
			o.addProperty("slot", s.selectedEnchantSlot);
			sendAction("enchant_clear", o);
		});
		y += 22;

		// Row 2: 1-Click Batch Auto-Max ALL Gear (Armor + Offhand + Hotbar + Bag!)
		int half = (w - 6) / 2;
		drawActionBtn(ctx, x, y, half, 18, "✦ Max ALL Gear Vanilla", 0xFF245B42, clipTop, clipBot, mx, my, () -> {
			JsonObject o = new JsonObject();
			o.addProperty("over25", false);
			sendAction("enchant_automax_all", o);
		});
		drawActionBtn(ctx, x + half + 6, y, w - half - 6, 18, "✦ Max ALL Gear Lv. 25", 0xFF7B2CBF, clipTop, clipBot, mx, my, () -> {
			JsonObject o = new JsonObject();
			o.addProperty("over25", true);
			sendAction("enchant_automax_all", o);
		});
		y += 22;

		y = drawToggleCard(ctx, x, y, w, "God Stack",
				s.bypassEnchantConflicts, clipTop, clipBot, mx, my, () -> sendAction("toggle_bypass_conflicts", new JsonObject()));

		y = drawSliderCard(ctx, "ench_lv", x, y, w, "Target Enchantment Level (1–25)",
				EnchantFeature.toRoman(s.customEnchantLevel) + " (Lv. " + s.customEnchantLevel + ")",
				s.customEnchantLevel, 1, 25, clipTop, clipBot, mx, my,
				v -> s.customEnchantLevel = v,
				v -> s.customEnchantLevel = v);

		String q = this.searchQuery == null ? "" : this.searchQuery.trim().toLowerCase(Locale.ROOT);
		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "ENCHANTMENT CATALOG (" + s.enchantCatalog.size() + ")");
		y += 16;

		for (ClientStateSnapshot.EnchantItem item : s.enchantCatalog) {
			if (!q.isEmpty() && !item.name.toLowerCase(Locale.ROOT).contains(q) && !item.id.toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, x, y, w, 22);
			int accent = item.currentLevel > item.maxLevel ? CrimsonTheme.AMETHYST : (item.currentLevel > 0 ? CrimsonTheme.EMERALD : (item.fitsHeld ? CrimsonTheme.CRIMSON : 0));
			CrimsonTheme.drawCard(ctx, x, y, w, 22, hov, accent);

			int nameColor = item.curse ? 0xFFFF6666 : (item.fitsHeld ? CrimsonTheme.TEXT_PRIMARY : CrimsonTheme.TEXT_SECONDARY);
			ctx.drawTextWithShadow(textRenderer, trimToWidth(item.name, w - 120), x + 7, y + 4, nameColor);
			String sub = item.maxLevel == 1 ? "Single Level (I)" : ("Vanilla Max " + EnchantFeature.toRoman(item.maxLevel) + " · Over XXV");
			ctx.drawTextWithShadow(textRenderer, sub, x + 7, y + 13, CrimsonTheme.TEXT_MUTED);

			int rightX = x + w - 4;
			if (item.currentLevel > 0) {
				rightX -= 18;
				drawActionBtn(ctx, rightX, y + 3, 16, 16, "✕", 0xFF5A1D28, clipTop, clipBot, mx, my, () -> {
					JsonObject o = new JsonObject();
					o.addProperty("id", item.id);
					o.addProperty("slot", s.selectedEnchantSlot);
					sendAction("enchant_remove", o);
				});
				rightX -= 4;
			}

			int targetLv = item.maxLevel <= 1 ? 1 : s.customEnchantLevel;
			String applyLabel = "+ " + EnchantFeature.toRoman(targetLv);
			int btnW = Math.max(40, textRenderer.getWidth(applyLabel) + 10);
			rightX -= btnW;
			drawActionBtn(ctx, rightX, y + 3, btnW, 16, applyLabel,
					targetLv > item.maxLevel ? 0xFF7B2CBF : CrimsonTheme.CRIMSON, clipTop, clipBot, mx, my, () -> {
						JsonObject o = new JsonObject();
						o.addProperty("id", item.id);
						o.addProperty("level", targetLv);
						o.addProperty("slot", s.selectedEnchantSlot);
						sendAction("enchant_apply", o);
					});

			if (item.currentLevel > 0) {
				String curTag = item.currentRoman;
				int tagW = textRenderer.getWidth(curTag) + 8;
				CrimsonTheme.drawBadge(ctx, textRenderer, rightX - tagW - 4, y + 5, curTag,
						0x33D86BFF, CrimsonTheme.AMETHYST, CrimsonTheme.AMETHYST);
			}

			y += 25;
		}
		return y;
	}

	// ==================== TAB 5: ENCHANTED BOOKS ====================
	private int renderBooksTab(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		y = drawSliderCard(ctx, "book_lv", x, y, w, "Book Enchantment Level (1–25)",
				EnchantFeature.toRoman(s.customBookLevel) + " (Lv. " + s.customBookLevel + ")",
				s.customBookLevel, 1, 25, clipTop, clipBot, mx, my,
				v -> s.customBookLevel = v,
				v -> s.customBookLevel = v);

		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "GIVE ENCHANTED BOOK");
		y += 16;
		String q = this.searchQuery == null ? "" : this.searchQuery.trim().toLowerCase(Locale.ROOT);

		for (ClientStateSnapshot.EnchantItem item : s.enchantCatalog) {
			if (!q.isEmpty() && !item.name.toLowerCase(Locale.ROOT).contains(q) && !item.id.toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, x, y, w, 20);
			CrimsonTheme.drawCard(ctx, x, y, w, 20, hov, CrimsonTheme.GOLD);
			ctx.drawTextWithShadow(textRenderer, trimToWidth(item.name, w - 116), x + 7, y + 6, CrimsonTheme.TEXT_PRIMARY);

			int customLv = item.maxLevel <= 1 ? 1 : s.customBookLevel;
			String giveCustom = "Lv. " + EnchantFeature.toRoman(customLv);
			int bw = Math.max(46, textRenderer.getWidth(giveCustom) + 10);
			drawActionBtn(ctx, x + w - bw - 4, y + 2, bw, 16, giveCustom, CrimsonTheme.CRIMSON, clipTop, clipBot, mx, my, () -> {
				JsonObject o = new JsonObject();
				o.addProperty("id", item.id);
				o.addProperty("level", customLv);
				sendAction("give_book", o);
			});

			if (item.maxLevel > 1) {
				String vanLabel = "Max " + EnchantFeature.toRoman(item.maxLevel);
				int vw = textRenderer.getWidth(vanLabel) + 10;
				drawActionBtn(ctx, x + w - bw - vw - 8, y + 2, vw, 16, vanLabel, 0xFF2D5B88, clipTop, clipBot, mx, my, () -> {
					JsonObject o = new JsonObject();
					o.addProperty("id", item.id);
					o.addProperty("level", item.maxLevel);
					sendAction("give_book", o);
				});
			}
			y += 23;
		}
		return y;
	}

	// ==================== TAB 6: REPAIR WORKSHOP ====================
	private int renderRepairTab(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		y = drawHeldItemShowcase(ctx, s, x, y, w, clipTop, clipBot, mx, my);

		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "INSTANT ANVIL WORKSHOP (Damaged in Bag: " + s.damagedCount + ")");
		y += 18;

		int half = (w - 6) / 2;
		drawActionBtn(ctx, x, y, half, 22, "Repair Held Item", CrimsonTheme.CRIMSON, clipTop, clipBot, mx, my, () -> sendRepair("held"));
		drawActionBtn(ctx, x + half + 6, y, half, 22, "Repair Equipped Armor", 0xFF2D5B88, clipTop, clipBot, mx, my, () -> sendRepair("armor"));
		y += 26;
		drawActionBtn(ctx, x, y, half, 22, "Repair Hotbar", 0xFF2E6B4E, clipTop, clipBot, mx, my, () -> sendRepair("hotbar"));
		drawActionBtn(ctx, x + half + 6, y, half, 22, "Repair Entire Inventory", 0xFF8C4A19, clipTop, clipBot, mx, my, () -> sendRepair("all"));
		return y + 26;
	}

	// ==================== TAB 7: ITEM UPGRADER (ARCANE ALTAR GACHA) ====================
	private int renderUpgraderTab(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		var clientPlayer = MinecraftClient.getInstance().player;
		List<ItemUpgraderFeature.BagOfferSlot> bagOffers = clientPlayer == null
				? List.of()
				: ItemUpgraderFeature.collectPlayerOffers(clientPlayer.getInventory());

		// Auto-resolve selected bag offer (prefer exact slot, then matching item ID if previous stack was consumed)
		ItemUpgraderFeature.BagOfferSlot activeOffer = resolveActiveOffer(bagOffers, s);

		Identifier targetIdent = Identifier.tryParse(s.upgraderTargetId);
		Item targetItem = (targetIdent != null && Registries.ITEM.containsId(targetIdent))
				? Registries.ITEM.get(targetIdent)
				: Items.NETHERITE_INGOT;
		if (!ItemUpgraderFeature.isAllowedTarget(targetItem)) {
			targetItem = Items.NETHERITE_INGOT;
			s.upgraderTargetId = "minecraft:netherite_ingot";
		}
		ItemStack targetPreview = new ItemStack(targetItem, 1);

		int maxOwned = activeOffer == null ? 0 : activeOffer.totalCount();
		if (maxOwned > 0) {
			s.upgraderAmount = Math.max(1, Math.min(maxOwned, s.upgraderAmount));
		} else {
			s.upgraderAmount = 1;
		}

		ItemStack offerSample = activeOffer == null ? ItemStack.EMPTY : activeOffer.sample();
		double baseChance = activeOffer == null ? 0.0 : ItemUpgraderFeature.calculateWinChance(offerSample, s.upgraderAmount, targetItem);
		double pityBonus = ItemUpgraderFeature.calculatePityBonusChance(s.upgraderPity);
		double liveChance = activeOffer == null ? 0.0 : ItemUpgraderFeature.calculateEffectiveWinChance(offerSample, s.upgraderAmount, targetItem, s.upgraderPity);
		double estPityGain = activeOffer == null ? 0.0 : ItemUpgraderFeature.calculateFailPityGain(offerSample, s.upgraderAmount, targetItem);
		boolean fullPityReady = s.upgraderPity >= ItemUpgraderFeature.MAX_PITY;
		boolean softPityActive = !fullPityReady && s.upgraderPity >= ItemUpgraderFeature.SOFT_PITY_THRESHOLD;
		int failXp = activeOffer == null ? 0 : ItemUpgraderFeature.calculateFailXp(offerSample, s.upgraderAmount);
		boolean hasEnchantsToInherit = activeOffer != null && activeOffer.enchantCount() > 0
				&& (targetPreview.isDamageable() || targetPreview.isEnchantable() || targetPreview.isOf(Items.ENCHANTED_BOOK));

		long now = System.currentTimeMillis();
		boolean spinning = now < s.upgraderSpinEndMs;
		processRitualRevealIfReady(s, now);

		// 1. Top Arcane Altar Pedestal Preview Card
		int cardH = 58;
		int accentColor = fullPityReady ? CrimsonTheme.GOLD
				: (spinning ? CrimsonTheme.AMETHYST
						: (s.upgraderHasResult ? (s.upgraderWon ? CrimsonTheme.EMERALD : CrimsonTheme.CRIMSON_BRIGHT) : CrimsonTheme.AMETHYST));
		CrimsonTheme.drawCard(ctx, x, y, w, cardH, false, accentColor);

		// Left Pedestal: Sacrifice Item
		int leftBoxX = x + 8;
		int boxY = y + 6;
		drawModernSlotBox(ctx, leftBoxX, boxY, 28, 28, activeOffer != null, false, CrimsonTheme.GOLD);
		if (!offerSample.isEmpty()) {
			ItemStack displayIn = offerSample.copyWithCount(Math.min(99, s.upgraderAmount));
			ctx.drawItem(displayIn, leftBoxX + 6, boxY + 6);
			ctx.drawItemInSlot(textRenderer, displayIn, leftBoxX + 6, boxY + 6);
			if (inClip(mx, my, clipTop, clipBot) && inRect(mx, my, leftBoxX, boxY, 28, 28)) {
				this.hoveredTooltipStack = offerSample;
			}
		}

		// Right Pedestal: Target Reward Item
		int rightBoxX = x + w - 36;
		drawModernSlotBox(ctx, rightBoxX, boxY, 28, 28, true, false, fullPityReady ? CrimsonTheme.GOLD : CrimsonTheme.EMERALD);
		ctx.drawItem(targetPreview, rightBoxX + 6, boxY + 6);
		if (inClip(mx, my, clipTop, clipBot) && inRect(mx, my, rightBoxX, boxY, 28, 28)) {
			this.hoveredTooltipStack = targetPreview;
		}

		// Center Altar Conduit & Chance Readout
		int midX = leftBoxX + 34;
		int midW = rightBoxX - midX - 6;
		int chanceColor = fullPityReady ? CrimsonTheme.GOLD
				: (liveChance >= 70.0 ? CrimsonTheme.EMERALD : (liveChance >= 30.0 ? CrimsonTheme.GOLD : CrimsonTheme.CRIMSON_BRIGHT));
		String inLabel = activeOffer == null ? "Empty Bag" : (s.upgraderAmount + "x " + offerSample.getName().getString());
		String matchupLine = inLabel + "  ✦➔✦  " + targetPreview.getName().getString();
		ctx.drawTextWithShadow(textRenderer, trimToWidth(matchupLine, midW), midX, y + 6, CrimsonTheme.TEXT_PRIMARY);

		// Mini animated Altar Rune Pulse next to Win Chance
		int pulseAlpha = 155 + (int) (100 * (0.5 + 0.5 * Math.sin(now * 0.006)));
		drawRuneDiamond(ctx, midX + 3, y + 20, 3,
				withAlpha(fullPityReady ? CrimsonTheme.GOLD : CrimsonTheme.AMETHYST, pulseAlpha), CrimsonTheme.GOLD);
		String chanceHeadline;
		if (fullPityReady) {
			chanceHeadline = "☀ Rate: 100.0%";
		} else if (pityBonus > 0.0 && liveChance > baseChance) {
			chanceHeadline = "Rate: " + liveChance + "% (" + baseChance + "% + " + Math.round((liveChance - baseChance) * 10.0) / 10.0 + "% Pity)";
		} else {
			chanceHeadline = "Ritual Rate: " + liveChance + "%";
		}
		ctx.drawTextWithShadow(textRenderer, trimToWidth(chanceHeadline, midW - 10), midX + 10, y + 16, chanceColor);

		String bonusLine = hasEnchantsToInherit
				? "✦ Transfers All Enchantments!"
				: ("If Shattered: +" + failXp + " XP & +" + estPityGain + "% Pity");
		ctx.drawTextWithShadow(textRenderer, trimToWidth(bonusLine, midW), midX, y + 26,
				hasEnchantsToInherit ? CrimsonTheme.EMERALD : CrimsonTheme.TEXT_SECONDARY);

		// Bottom Altar Recap / Hint Strip inside Card
		int pillX = x + 8;
		int pillY = y + 39;
		int pillW = w - 16;
		CrimsonTheme.drawRoundedRect(ctx, pillX, pillY, pillW, 14, 4.0F, CrimsonTheme.BG_INPUT);
		CrimsonTheme.drawRoundedOutline(ctx, pillX, pillY, pillW, 14, 4.0F, 0.9F, CrimsonTheme.BORDER_SUBTLE);
		String footerTxt;
		int footerColor;
		if (s.upgraderHasResult && s.upgraderResultRevealed) {
			if (s.upgraderWon) {
				String tag = s.upgraderPityTriggered ? " [☀ PITY GUARANTEED]" : (" (" + s.upgraderRolled + "% ≤ " + s.upgraderChance + "%)");
				footerTxt = "✦ Last Ritual: Forged " + s.upgraderTargetName + tag;
				footerColor = s.upgraderPityTriggered ? CrimsonTheme.GOLD : CrimsonTheme.EMERALD;
			} else {
				footerTxt = "✖ Shattered: +" + s.upgraderXpRefund + " XP · Pity +" + s.upgraderPityGain + "% (Now " + s.upgraderPityAfter + "%)";
				footerColor = CrimsonTheme.CRIMSON_BRIGHT;
			}
		} else {
			footerTxt = "✦ Arcane Ritual Altar · Failed Rolls Charge Guaranteed Divine Pity";
			footerColor = CrimsonTheme.AMETHYST;
		}
		ctx.drawTextWithShadow(textRenderer, trimToWidth(footerTxt, pillW - 10), pillX + 5, pillY + 3, footerColor);
		y += cardH + 5;

		// 1b. Genshin-Style Arcane Pity Meter ("Divine Guarantee") Card
		int pityCardH = 30;
		int pityAccent = fullPityReady ? CrimsonTheme.GOLD : (softPityActive ? 0xFFFF8C38 : CrimsonTheme.AMETHYST);
		CrimsonTheme.drawCard(ctx, x, y, w, pityCardH, fullPityReady, pityAccent);

		String pityLeftLabel = fullPityReady
				? "☀ DIVINE PITY: 100% READY!"
				: (softPityActive ? ("🔥 SOFT PITY: " + s.upgraderPity + "% / 100%") : ("✦ ARCANE PITY: " + s.upgraderPity + "% / 100%"));
		ctx.drawTextWithShadow(textRenderer, pityLeftLabel, x + 8, y + 5,
				fullPityReady ? CrimsonTheme.GOLD : CrimsonTheme.TEXT_PRIMARY);

		String pityRightTag;
		int pityRightCol;
		if (fullPityReady) {
			pityRightTag = "Guaranteed Jackpot!";
			pityRightCol = CrimsonTheme.GOLD;
		} else if (softPityActive) {
			pityRightTag = "+" + pityBonus + "% Rate Surge · Streak " + s.upgraderFailStreak;
			pityRightCol = 0xFFFFB84D;
		} else if (s.upgraderPity > 0.0) {
			pityRightTag = "+" + pityBonus + "% Luck · Streak " + s.upgraderFailStreak;
			pityRightCol = CrimsonTheme.AMETHYST;
		} else {
			pityRightTag = "Soft Pity @ 70% · Max @ 100%";
			pityRightCol = CrimsonTheme.TEXT_MUTED;
		}
		int prw = textRenderer.getWidth(pityRightTag);
		if (x + w - prw - 8 > x + textRenderer.getWidth(pityLeftLabel) + 14) {
			ctx.drawTextWithShadow(textRenderer, pityRightTag, x + w - prw - 8, y + 5, pityRightCol);
		}

		drawArcanePityBar(ctx, x + 8, y + 17, w - 16, 8, s.upgraderPity, now);
		y += pityCardH + 5;

		// 2. Quantity Slider (when stackable count > 1)
		if (maxOwned > 1) {
			y = drawSliderCard(ctx, "upg_qty", x, y, w,
					"Sacrifice Quantity",
					s.upgraderAmount + " / " + maxOwned + " (" + liveChance + "%)",
					s.upgraderAmount, 1, maxOwned, clipTop, clipBot, mx, my,
					v -> s.upgraderAmount = v,
					v -> s.upgraderAmount = v);
		}

		// 3. Smart 95% Fill + Summon Altar Ritual Action Row
		int smartW = 90;
		int rollW = w - smartW - 6;
		final Item finalTargetItem = targetItem;
		final int finalMaxOwned = maxOwned;
		drawActionBtn(ctx, x, y, smartW, 21, "Smart Max 95%", 0xFF2D4F6E, clipTop, clipBot, mx, my, () -> {
			if (!offerSample.isEmpty() && finalMaxOwned > 0) {
				s.upgraderAmount = ItemUpgraderFeature.calculateSmartAmountForMaxChance(offerSample, finalMaxOwned, finalTargetItem);
			}
		});

		String rollBtnLabel;
		int rollBtnColor;
		if (spinning) {
			rollBtnLabel = "✦ Ritual Channeling...";
			rollBtnColor = 0xFF3E1F47;
		} else if (fullPityReady) {
			rollBtnLabel = "☀ UNLEASH DIVINE PITY 100% WIN!";
			rollBtnColor = 0xFFB87414;
		} else if (softPityActive) {
			rollBtnLabel = "🔥 SUMMON ALTAR " + liveChance + "% · SOFT PITY";
			rollBtnColor = 0xFFA3264E;
		} else {
			rollBtnLabel = "✦ SUMMON ALTAR " + liveChance + "% CHANCE";
			rollBtnColor = 0xFF8C1D58;
		}
		final ItemUpgraderFeature.BagOfferSlot chosenOffer = activeOffer;
		drawActionBtn(ctx, x + smartW + 6, y, rollW, 21, rollBtnLabel, rollBtnColor, clipTop, clipBot, mx, my, () -> {
			triggerRitualRoll(s, chosenOffer, finalTargetItem);
		});
		y += 26;

		// 4. Bag Sacrifice Item Picker (3D Grid)
		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "1. SELECT SACRIFICE ITEM FROM BAG (" + bagOffers.size() + ")");
		y += 15;
		int cell = 22;
		int gap = 3;
		int cols = Math.max(1, (w + gap) / (cell + gap));

		if (bagOffers.isEmpty()) {
			ctx.drawTextWithShadow(textRenderer, "No items found in your inventory.", x + 4, y + 4, CrimsonTheme.TEXT_MUTED);
			y += 18;
		} else {
			int col = 0;
			for (ItemUpgraderFeature.BagOfferSlot bo : bagOffers) {
				int gx = x + col * (cell + gap);
				int gy = y;
				boolean sel = (activeOffer != null && bo.slot() == activeOffer.slot());
				boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, gx, gy, cell, cell);

				drawModernSlotBox(ctx, gx, gy, cell, cell, sel, hov, CrimsonTheme.GOLD);

				ItemStack iconStack = bo.sample().copyWithCount(Math.min(99, bo.totalCount()));
				ctx.drawItem(iconStack, gx + 3, gy + 3);
				if (bo.totalCount() > 1) {
					String cntStr = bo.totalCount() > 99 ? "99+" : String.valueOf(bo.totalCount());
					ctx.getMatrices().push();
					ctx.getMatrices().translate(0.0F, 0.0F, 200.0F);
					ctx.drawTextWithShadow(textRenderer, cntStr, gx + cell - textRenderer.getWidth(cntStr) - 1, gy + cell - 9, 0xFFFFFFFF);
					ctx.getMatrices().pop();
				}
				if (bo.enchantCount() > 0) {
					CrimsonTheme.drawCircle(ctx, gx + cell - 3.5F, gy + 3.5F, 1.7F, CrimsonTheme.EMERALD);
				}
				if (hov) {
					this.hoveredTooltipStack = bo.sample();
				}
				addClippedClick(gx, gy, cell, cell, clipTop, clipBot, () -> {
					s.upgraderSelectedSlot = bo.slot();
					s.upgraderLastSacrificeItemId = Registries.ITEM.getId(bo.sample().getItem()).toString();
					s.upgraderAmount = Math.min(s.upgraderAmount, Math.max(1, bo.totalCount()));
				});

				col++;
				if (col >= cols) {
					col = 0;
					y += cell + gap;
				}
			}
			if (col > 0) {
				y += cell + gap;
			}
			y += 4;
		}

		// 5. Target Reward Item Catalog (Featured + Full Survival Registry Search)
		String q = this.searchQuery == null ? "" : this.searchQuery.trim().toLowerCase(Locale.ROOT);
		List<Item> targetCatalog = buildUpgraderTargetCatalog(q);
		String targetHdr = q.isEmpty()
				? "2. SELECT TARGET REWARD OR TYPE ABOVE TO SEARCH ANY ITEM"
				: ("2. TARGET SEARCH RESULTS " + targetCatalog.size() + "");
		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, targetHdr);
		y += 15;

		int col = 0;
		for (Item item : targetCatalog) {
			Identifier id = Registries.ITEM.getId(item);
			String idStr = id.toString();
			int gx = x + col * (cell + gap);
			int gy = y;
			boolean sel = idStr.equals(s.upgraderTargetId);
			boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, gx, gy, cell, cell);

			drawModernSlotBox(ctx, gx, gy, cell, cell, sel, hov, CrimsonTheme.EMERALD);

			ItemStack st = new ItemStack(item, 1);
			ctx.drawItem(st, gx + 3, gy + 3);
			if (hov) {
				this.hoveredTooltipStack = st;
			}
			addClippedClick(gx, gy, cell, cell, clipTop, clipBot, () -> {
				s.upgraderTargetId = idStr;
			});

			col++;
			if (col >= cols) {
				col = 0;
				y += cell + gap;
			}
		}
		if (col > 0) {
			y += cell + gap;
		}
		return y + 6;
	}

	private static List<Item> buildUpgraderTargetCatalog(String query) {
		List<Item> list = new ArrayList<>();
		if (query.isEmpty()) {
			for (String idStr : ItemUpgraderFeature.FEATURED_TARGETS) {
				Identifier id = Identifier.tryParse(idStr);
				if (id != null && Registries.ITEM.containsId(id)) {
					Item it = Registries.ITEM.get(id);
					if (ItemUpgraderFeature.isAllowedTarget(it)) {
						list.add(it);
					}
				}
			}
			return list;
		}

		for (Item item : Registries.ITEM) {
			if (!ItemUpgraderFeature.isAllowedTarget(item)) continue;
			Identifier id = Registries.ITEM.getId(item);
			String name = item.getName().getString().toLowerCase(Locale.ROOT);
			if (id.getPath().contains(query) || name.contains(query)) {
				list.add(item);
				if (list.size() >= 64) {
					break;
				}
			}
		}
		return list;
	}

	// ==================== ARCANE ALTAR & ENERGY BURST GACHA STAGE ====================
	private static ItemUpgraderFeature.BagOfferSlot resolveActiveOffer(List<ItemUpgraderFeature.BagOfferSlot> bagOffers, ClientStateSnapshot s) {
		for (ItemUpgraderFeature.BagOfferSlot bo : bagOffers) {
			if (bo.slot() == s.upgraderSelectedSlot) {
				return bo;
			}
		}
		if (s.upgraderLastSacrificeItemId != null && !s.upgraderLastSacrificeItemId.isEmpty()) {
			for (ItemUpgraderFeature.BagOfferSlot bo : bagOffers) {
				String idStr = Registries.ITEM.getId(bo.sample().getItem()).toString();
				if (idStr.equals(s.upgraderLastSacrificeItemId)) {
					s.upgraderSelectedSlot = bo.slot();
					return bo;
				}
			}
		}
		if (!bagOffers.isEmpty()) {
			ItemUpgraderFeature.BagOfferSlot first = bagOffers.get(0);
			s.upgraderSelectedSlot = first.slot();
			return first;
		}
		return null;
	}

	private static ItemUpgraderFeature.BagOfferSlot findMatchingRerollOffer(List<ItemUpgraderFeature.BagOfferSlot> bagOffers, ClientStateSnapshot s) {
		if (s.upgraderLastSacrificeItemId == null || s.upgraderLastSacrificeItemId.isEmpty()) {
			return null;
		}
		for (ItemUpgraderFeature.BagOfferSlot bo : bagOffers) {
			if (bo.slot() == s.upgraderSelectedSlot
					&& Registries.ITEM.getId(bo.sample().getItem()).toString().equals(s.upgraderLastSacrificeItemId)) {
				return bo;
			}
		}
		for (ItemUpgraderFeature.BagOfferSlot bo : bagOffers) {
			if (Registries.ITEM.getId(bo.sample().getItem()).toString().equals(s.upgraderLastSacrificeItemId)) {
				return bo;
			}
		}
		return null;
	}

	private void triggerRitualRoll(ClientStateSnapshot s, ItemUpgraderFeature.BagOfferSlot chosenOffer, Item targetItem) {
		long clickNow = System.currentTimeMillis();
		if (clickNow < s.upgraderSpinEndMs) {
			return;
		}
		if (chosenOffer == null || chosenOffer.totalCount() <= 0) {
			s.toastText = "Your inventory has no items to sacrifice!";
			s.toastOk = false;
			s.toastExpireMs = clickNow + 2600L;
			return;
		}
		ItemStack sample = chosenOffer.sample();
		if (sample.getItem() == targetItem && chosenOffer.enchantCount() == 0) {
			s.toastText = "Pick a different Target Reward item below!";
			s.toastOk = false;
			s.toastExpireMs = clickNow + 2600L;
			return;
		}

		int useAmt = Math.max(1, Math.min(chosenOffer.totalCount(), s.upgraderAmount));
		boolean fullPityPull = s.upgraderPity >= ItemUpgraderFeature.MAX_PITY;
		s.upgraderSelectedSlot = chosenOffer.slot();
		s.upgraderAmount = useAmt;
		s.upgraderSacrificedAmount = useAmt;
		s.upgraderLastSacrificeItemId = Registries.ITEM.getId(sample.getItem()).toString();
		s.upgraderSacrificeName = sample.getName().getString();
		s.upgraderSacrificeStack = sample.copyWithCount(1);
		s.upgraderRewardStack = ItemStack.EMPTY;
		s.upgraderStartedWithFullPity = fullPityPull;
		s.upgraderChance = ItemUpgraderFeature.calculateEffectiveWinChance(sample, useAmt, targetItem, s.upgraderPity);
		s.upgraderResultTargetId = s.upgraderTargetId;
		s.upgraderTargetName = new ItemStack(targetItem, 1).getName().getString();

		s.upgraderModalOpen = true;
		s.upgraderSkipRequested = false;
		s.upgraderHasResult = false;
		s.upgraderResultRevealed = false;
		s.upgraderSpinStartMs = clickNow;
		s.upgraderSpinEndMs = clickNow + ClientStateSnapshot.RITUAL_DURATION_MS;
		s.upgraderRevealStartMs = 0L;
		s.upgraderLastTickBucket = -1;

		var sm = MinecraftClient.getInstance().getSoundManager();
		sm.play(PositionedSoundInstance.master(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, fullPityPull ? 0.95F : 0.76F));
		sm.play(PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, fullPityPull ? 1.05F : 0.68F));
		if (fullPityPull) {
			sm.play(PositionedSoundInstance.master(SoundEvents.BLOCK_BEACON_ACTIVATE, 1.15F));
		}

		JsonObject o = new JsonObject();
		o.addProperty("slot", chosenOffer.slot());
		o.addProperty("amount", useAmt);
		o.addProperty("targetId", s.upgraderTargetId);
		sendAction("upgrader_roll", o);
	}

	private static void closeRitualModal(ClientStateSnapshot s) {
		s.upgraderModalOpen = false;
		if (s.upgraderPendingPity >= 0.0) {
			s.upgraderPity = s.upgraderPendingPity;
			s.upgraderPendingPity = -1.0;
		}
		if (s.upgraderPendingFailStreak >= 0) {
			s.upgraderFailStreak = s.upgraderPendingFailStreak;
			s.upgraderPendingFailStreak = -1;
		}
	}

	private static void skipRitualToReveal(ClientStateSnapshot s) {
		long now = System.currentTimeMillis();
		s.upgraderSkipRequested = true;
		if (s.upgraderHasResult) {
			s.upgraderSpinEndMs = now;
		}
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.38F));
	}

	private static void processRitualRevealIfReady(ClientStateSnapshot s, long now) {
		if (now < s.upgraderSpinEndMs || !s.upgraderHasResult || s.upgraderResultRevealed) {
			return;
		}
		s.upgraderResultRevealed = true;
		s.upgraderRevealStartMs = now;

		// Commit buffered server Pity at exact explosion moment (never earlier!)
		s.upgraderPity = s.upgraderPityAfter;
		if (s.upgraderPendingPity >= 0.0) {
			s.upgraderPity = s.upgraderPendingPity;
			s.upgraderPendingPity = -1.0;
		}
		if (s.upgraderPendingFailStreak >= 0) {
			s.upgraderFailStreak = s.upgraderPendingFailStreak;
			s.upgraderPendingFailStreak = -1;
		} else if (s.upgraderWon) {
			s.upgraderFailStreak = 0;
		}

		Identifier resId = Identifier.tryParse(s.upgraderResultTargetId);
		Item rewardItem = (resId != null && Registries.ITEM.containsId(resId))
				? Registries.ITEM.get(resId)
				: Items.NETHERITE_INGOT;
		s.upgraderRewardStack = ItemUpgraderFeature.createRewardPreview(
				s.upgraderSacrificeStack,
				rewardItem,
				s.upgraderInheritedEnchants > 0,
				s.bypassEnchantConflicts
		);

		var sm = MinecraftClient.getInstance().getSoundManager();
		if (s.upgraderWon) {
			sm.play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.12F));
			sm.play(PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 1.25F));
			sm.play(PositionedSoundInstance.master(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 1.18F));
			if (s.upgraderPityTriggered) {
				sm.play(PositionedSoundInstance.master(SoundEvents.ITEM_TOTEM_USE, 1.15F));
			}
			String enchTag = s.upgraderInheritedEnchants > 0 ? (" [+" + s.upgraderInheritedEnchants + " Enchants]") : "";
			String pityTag = s.upgraderPityTriggered ? " [☀ 100% PITY!]" : (" (" + s.upgraderRolled + "% ≤ " + s.upgraderChance + "%)");
			s.toastText = "✦ JACKPOT! Forged " + s.upgraderTargetName + enchTag + pityTag;
			s.toastOk = true;
			s.toastExpireMs = now + 4200L;
		} else {
			sm.play(PositionedSoundInstance.master(SoundEvents.BLOCK_GLASS_BREAK, 0.82F));
			sm.play(PositionedSoundInstance.master(SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.12F));
			sm.play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.95F));
			if (s.upgraderPityAfter >= ItemUpgraderFeature.MAX_PITY) {
				sm.play(PositionedSoundInstance.master(SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.22F));
			}
			s.toastText = "✖ Shattered! +" + s.upgraderXpRefund + " XP · Pity +" + s.upgraderPityGain + "% (" + s.upgraderPityAfter + "%)";
			s.toastOk = false;
			s.toastExpireMs = now + 4200L;
		}
	}

	private void renderRitualGachaModal(DrawContext ctx, ClientStateSnapshot s, int mx, int my) {
		long now = System.currentTimeMillis();
		long elapsedMs = Math.max(0L, now - s.upgraderSpinStartMs);
		boolean spinning = now < s.upgraderSpinEndMs || !s.upgraderHasResult;
		processRitualRevealIfReady(s, now);

		int stageW = Math.min(336, this.width - 20);
		int stageH = Math.min(244, this.height - 14);
		int stageX = (this.width - stageW) / 2;
		int stageY = (this.height - stageH) / 2;

		double chargeT = Math.max(0.0, Math.min(1.0,
				elapsedMs / (double) Math.max(1L, ClientStateSnapshot.RITUAL_DURATION_MS)));
		double revSec = (!spinning && s.upgraderRevealStartMs > 0L)
				? Math.max(0.0, (now - s.upgraderRevealStartMs) / 1000.0)
				: 0.0;

		boolean goldenPityRitual = s.upgraderStartedWithFullPity;

		int hdrH = 38;
		int footerH = 60;
		int arenaTop = stageY + hdrH + 2;
		int arenaBot = stageY + stageH - footerH;
		float cx = stageX + stageW * 0.5F;
		float cy = (arenaTop + arenaBot) * 0.5F;
		float maxR = Math.max(30.0F, Math.min(52.0F, (arenaBot - arenaTop) * 0.5F - 7.0F));

		// 1. Opaque Deep Astral Sanctuary Curtain (veils HUD/inventory completely — zero spoilers or clutter!)
		ctx.fillGradient(0, 0, this.width, this.height, 0xFC05020A, 0xFF0C0412);

		// 2. Stage Window Frame & Spacious Header (Golden Sovereign Aura ONLY when 100% Pity was already active before pull!)
		int frameBorder = spinning
				? (goldenPityRitual ? CrimsonTheme.GOLD : 0xFFB86BFF)
				: (s.upgraderWon ? (s.upgraderPityTriggered ? CrimsonTheme.GOLD : CrimsonTheme.EMERALD) : CrimsonTheme.CRIMSON_BRIGHT);
		ctx.fill(stageX - 4, stageY - 4, stageX + stageW + 4, stageY + stageH + 4, 0x88000000);
		ctx.fill(stageX, stageY, stageX + stageW, stageY + stageH, 0xFA0B0611);
		ctx.drawBorder(stageX, stageY, stageW, stageH, frameBorder);
		ctx.drawBorder(stageX + 2, stageY + 2, stageW - 4, stageH - 4, goldenPityRitual && spinning ? 0x66B88228 : 0x445E2D73);

		int hdrTop = spinning ? (goldenPityRitual ? 0xFF33210A : 0xFF1F0C29) : (s.upgraderWon ? 0xFF0D2B1E : 0xFF2B0B15);
		int hdrBot = spinning ? (goldenPityRitual ? 0xFF171006 : 0xFF100716) : (s.upgraderWon ? 0xFF091611 : 0xFF14070C);
		ctx.fillGradient(stageX + 1, stageY + 1, stageX + stageW - 1, stageY + hdrH, hdrTop, hdrBot);
		ctx.fill(stageX + 1, stageY + hdrH, stageX + stageW - 1, stageY + hdrH + 1, frameBorder);

		String stageTitle;
		int titleColor;
		String stageSub;
		if (spinning) {
			if (goldenPityRitual) {
				stageTitle = "☀ SOVEREIGN PITY INVOCATION ☀";
				titleColor = CrimsonTheme.GOLD;
				stageSub = "100% Divine Guarantee Active · Offering " + s.upgraderSacrificedAmount + "x " + s.upgraderSacrificeName;
			} else {
				stageTitle = "✦ ARCANE ALTAR RITUAL ✦";
				titleColor = CrimsonTheme.TEXT_PRIMARY;
				stageSub = "Offering " + s.upgraderSacrificedAmount + "x " + s.upgraderSacrificeName + "  ·  Rate: " + s.upgraderChance + "%";
			}
		} else if (s.upgraderWon) {
			stageTitle = s.upgraderPityTriggered ? "☀ DIVINE PITY JACKPOT! ☀" : "✦ DIVINE FORGE JACKPOT! ✦";
			titleColor = CrimsonTheme.GOLD;
			stageSub = s.upgraderPityTriggered ? "100% Arcane Pity Consumed · Reset to 0%" : "Sacred Transmutation Complete!";
		} else {
			stageTitle = (s.upgraderPityAfter >= ItemUpgraderFeature.MAX_PITY)
					? "☀ PITY MAXED — NEXT ROLL 100%! ☀"
					: "✖ ALTAR ESSENCE SHATTERED ✖";
			titleColor = (s.upgraderPityAfter >= ItemUpgraderFeature.MAX_PITY) ? CrimsonTheme.GOLD : CrimsonTheme.CRIMSON_BRIGHT;
			stageSub = "Crystal Core Fractured into XP & Arcane Pity";
		}
		drawCenteredText(ctx, stageTitle, stageX + stageW / 2, stageY + 8, titleColor);
		drawCenteredText(ctx, trimToWidth(stageSub, stageW - 24), stageX + stageW / 2, stageY + 22, CrimsonTheme.TEXT_SECONDARY);

		// Divider line above footer
		ctx.fill(stageX + 10, arenaBot, stageX + stageW - 10, arenaBot + 1, 0x445E2D73);

		// 3. Silky-Smooth OpenGL Floating-Point Vector Additive Glow Stage
		beginAdditiveGlow(ctx);
		Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();

		if (spinning) {
			// Gentle, musical 9-step rising Amethyst chime across the 4.0s build-up
			int chimeStep = (int) (chargeT * 9.0);
			if (chimeStep != s.upgraderLastTickBucket && chargeT < 0.93) {
				s.upgraderLastTickBucket = chimeStep;
				float pitch = (goldenPityRitual ? 0.82F : 0.65F) + (float) (chargeT * 1.05F);
				MinecraftClient.getInstance().getSoundManager().play(
						PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, pitch));
			} else if (chargeT >= 0.93 && s.upgraderLastTickBucket != 99) {
				s.upgraderLastTickBucket = 99;
				MinecraftClient.getInstance().getSoundManager().play(
						PositionedSoundInstance.master(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 1.35F));
			}

			double unfold = easeOutCubic(Math.min(1.0, chargeT / 0.28));
			double implode = chargeT <= 0.74 ? 1.0
					: (chargeT < 0.92
							? (1.0 - 0.55 * easeInOutSine((chargeT - 0.74) / 0.18))
							: (0.45 * (1.0 - easeInCubic((chargeT - 0.92) / 0.08))));

			float curOuterR = (float) (maxR * unfold * implode);
			float curHexR = curOuterR * 0.71F;

			double accelPhase = Math.pow(chargeT, 2.35) * 14.5;
			double rotOuter = (elapsedMs * 0.0009) + accelPhase;
			double rotInner = -(elapsedMs * 0.0012) - accelPhase * 1.25;

			int outerRingRgb = goldenPityRitual ? 0xFFD859 : 0xC86BFF;
			int innerRingRgb = goldenPityRitual ? 0xFFA438 : 0x38C9FF;
			int hexRgb = goldenPityRitual ? 0xFFF2AD : 0xFFBC3B;
			int nebulaCenter = goldenPityRitual ? 0x45FFB838 : 0x389B4DFF;
			int nebulaEdge = goldenPityRitual ? 0x003D2205 : 0x00240A40;

			float nebulaR = (float) ((maxR + 24.0F) * unfold * (chargeT > 0.92 ? implode * 2.0 : 1.0));
			if (nebulaR > 2.0F) {
				drawSmoothRadialGlow(mat, cx, cy, nebulaR, nebulaCenter, nebulaEdge);
			}

			if (curOuterR > 3.0F) {
				int ringAlpha = (int) Math.round(195 * unfold);
				drawSmoothGlowRing(mat, cx, cy, curOuterR, 0.85F, 3.8F, withAlpha(outerRingRgb, ringAlpha));
				drawSmoothGlowRing(mat, cx, cy, curHexR, 0.65F, 2.6F, withAlpha(innerRingRgb, (int) (ringAlpha * 0.78)));
				drawSmoothHexagram(mat, cx, cy, curHexR, rotInner, 0.85F, withAlpha(hexRgb, (int) (ringAlpha * 0.85)));

				for (int k = 0; k < 6; k++) {
					double ang = rotOuter + k * (Math.PI / 3.0);
					float x1 = cx + (float) (Math.cos(ang) * (curHexR + 2.0F));
					float y1 = cy + (float) (Math.sin(ang) * (curHexR + 2.0F));
					float x2 = cx + (float) (Math.cos(ang) * (curOuterR - 1.5F));
					float y2 = cy + (float) (Math.sin(ang) * (curOuterR - 1.5F));
					drawSmoothGlowSegment(mat, x1, y1, x2, y2, 0.8F, withAlpha(0xFFD86B, (int) (ringAlpha * 0.8)));
				}
			}

			// 8 Silky Curved Spiral Energy Ribbons (Act II & III: 0.20 .. 0.94)
			if (chargeT > 0.20 && chargeT < 0.94) {
				double p = (chargeT - 0.20) / 0.74;
				float ribbonFade = (float) (p < 0.15 ? (p / 0.15) : (p > 0.85 ? (1.0 - p) / 0.15 : 1.0));
				for (int i = 0; i < 8; i++) {
					double wave = ((p * 2.2) + (i * 0.125)) % 1.0;
					float headR = (float) ((1.0 - easeInQuad(wave)) * (curOuterR + 8.0F) + 2.5F);
					double headAng = (i * (Math.PI * 2.0 / 8.0)) + rotOuter * 1.35 + wave * 2.1;
					int colHead = goldenPityRitual ? 0xFFFFF3B0 : ((i % 2 == 0) ? 0xFFFFDF75 : 0xFFE28BFF);
					int colTail = goldenPityRitual ? 0xFFFF9E2C : ((i % 2 == 0) ? 0xFFD86BFF : 0xFF38C9FF);
					int alpha = (int) Math.round(215 * ribbonFade * Math.sin(wave * Math.PI));
					if (alpha > 8) {
						drawSmoothSpiralComet(mat, cx, cy, headAng, headR, 14.0F, 0.68,
								withAlpha(colHead, alpha), withAlpha(colTail, 0));
					}
				}
			}

			float coreGlowR;
			float starScale;
			if (chargeT < 0.28) {
				coreGlowR = (float) (12.0 + unfold * 6.0);
				starScale = (float) (4.0 * unfold);
			} else if (chargeT < 0.92) {
				double grow = (chargeT - 0.28) / 0.64;
				float pulse = (float) (Math.sin(elapsedMs * (0.008 + grow * 0.018)) * 2.5);
				coreGlowR = (float) (18.0 + grow * 20.0 + pulse);
				starScale = (float) (4.0 + grow * 11.0);
			} else {
				double imp = (chargeT - 0.92) / 0.08;
				coreGlowR = (float) (38.0 * (1.0 - imp * 0.72));
				starScale = (float) (15.0 * (1.0 - imp * 0.55));
			}

			float orbOffX = 0.0F;
			float orbOffY = 0.0F;
			if (chargeT > 0.74 && chargeT < 0.94) {
				float vib = (float) (((chargeT - 0.74) / 0.20) * 1.35);
				orbOffX = (float) (Math.sin(elapsedMs * 0.085) * vib);
				orbOffY = (float) (Math.cos(elapsedMs * 0.105) * vib);
			}

			int coreOuterCol = goldenPityRitual ? 0xDDFFC94D : 0xCCDA8FFF;
			int coreOuterEdge = goldenPityRitual ? 0x00A8550A : 0x006A1FB8;
			int coreInnerEdge = goldenPityRitual ? 0x00FFD859 : 0x00D86BFF;
			drawSmoothRadialGlow(mat, cx + orbOffX, cy + orbOffY, coreGlowR, coreOuterCol, coreOuterEdge);
			drawSmoothRadialGlow(mat, cx + orbOffX, cy + orbOffY, coreGlowR * 0.52F, 0xEEFFF4D2, coreInnerEdge);
			drawCrystalPrismStar(mat, cx + orbOffX, cy + orbOffY, starScale, elapsedMs * 0.0032, 0xFFFFF8E7, coreInnerEdge);

			endAdditiveGlow();

			// 3D Sacrifice Item: Levitates cleanly in Act I (0..0.28), then smoothly spirals & shrinks into Core in Act II (0.28..0.74)
			if (!s.upgraderSacrificeStack.isEmpty() && chargeT < 0.74) {
				float itemScale;
				float tiltDeg;
				float floatY;
				if (chargeT < 0.28) {
					double u = easeOutCubic(chargeT / 0.28);
					itemScale = (float) (0.75 + 1.10 * u);
					tiltDeg = (float) (Math.sin(elapsedMs * 0.004) * 4.0);
					floatY = (float) (-Math.sin(u * Math.PI * 0.5) * 3.0 + Math.sin(elapsedMs * 0.005) * 1.8);
				} else {
					double absorb = easeInOutCubic((chargeT - 0.28) / 0.46);
					itemScale = (float) (1.85 * (1.0 - absorb));
					tiltDeg = (float) (absorb * absorb * 380.0);
					floatY = (float) ((1.0 - absorb) * -2.0);
				}
				if (itemScale > 0.06F) {
					drawScaledItemCentered(ctx, s.upgraderSacrificeStack, cx, cy + floatY, itemScale, tiltDeg);
					ctx.draw();
				}
			}
		} else {
			// ==================== ACT IV: CLIMAX EXPLOSION & REVEAL ====================
			if (s.upgraderWon) {
				double slowRot = now * 0.00075;

				// Smooth Volumetric Emerald-Gold Aura + 12 Rotating Sunburst God-Rays
				drawSmoothRadialGlow(mat, cx, cy, maxR + 28.0F, 0x5523D17B, 0x000A3822);
				drawSmoothRadialGlow(mat, cx, cy, maxR * 0.85F, 0x99FFD859, 0x0023D17B);
				drawSmoothSunburstRays(mat, cx, cy, 12, 6.0F, maxR + 18.0F, 0.115F, slowRot, 0x88FFE074);

				// Crisp Sacred Golden-Emerald Halo Ring
				drawSmoothGlowRing(mat, cx, cy, maxR * 0.92F, 0.85F, 3.6F, 0xBB23D17B);
				drawSmoothHexagram(mat, cx, cy, maxR * 0.66F, -slowRot * 1.15, 0.70F, 0x77FFD859);

				// Expanding Silky Shockwave Ring (0.00s .. 0.78s)
				if (revSec < 0.78) {
					double p = revSec / 0.78;
					float swR = (float) (6.0 + easeOutCubic(p) * (maxR + 46.0));
					int swAlpha = (int) Math.round(240 * Math.pow(1.0 - p, 1.6));
					drawSmoothGlowRing(mat, cx, cy, swR, 1.2F, 6.0F, withAlpha(0xFFE680, swAlpha));
				}

				// 8 Gently Floating Golden-Emerald Starlight Orbs
				for (int i = 0; i < 8; i++) {
					double orbAng = slowRot * 1.8 + i * (Math.PI * 2.0 / 8.0);
					float orbR = (float) (maxR * 0.72 + Math.sin(now * 0.003 + i * 1.4) * 5.5);
					float ox = cx + (float) (Math.cos(orbAng) * orbR);
					float oy = cy + (float) (Math.sin(orbAng) * orbR);
					int c = (i % 2 == 0) ? 0xDDFFE885 : 0xDD4DFFAA;
					drawSmoothRadialGlow(mat, ox, oy, 5.2F, c, 0x00000000);
				}

				endAdditiveGlow();

				// Elastic Spring Pop-Out 2.30x 3D Target Item (0.0x -> 2.56x -> 2.30x over 0.52s)
				float popScale;
				if (revSec < 0.52) {
					double u = revSec / 0.52;
					double c1 = 1.70158;
					double c3 = c1 + 1.0;
					double easeBack = 1.0 + c3 * Math.pow(u - 1.0, 3.0) + c1 * Math.pow(u - 1.0, 2.0);
					popScale = (float) Math.max(0.08, 2.30 * easeBack);
				} else {
					popScale = 2.30F;
				}
				float bobY = (float) (Math.sin(now * 0.0036) * 2.8);
				ItemStack displayReward = s.upgraderRewardStack.isEmpty()
						? new ItemStack(Items.NETHERITE_INGOT)
						: s.upgraderRewardStack;
				drawScaledItemCentered(ctx, displayReward, cx, cy + bobY, popScale, 0.0F);
				ctx.draw();

				if (inRect(mx, my, (int) (cx - 24), (int) (cy - 24), 48, 48)) {
					this.hoveredTooltipStack = displayReward;
				}
			} else {
				// ==================== SHATTERED CRIMSON CORE -> LUMINOUS XP ORBS ====================
				drawSmoothRadialGlow(mat, cx, cy, maxR + 18.0F, 0x48FF2E4C, 0x0029040C);

				// Expanding Crimson Fracture Shockwave (0.00s .. 0.68s)
				if (revSec < 0.68) {
					double p = revSec / 0.68;
					float swR = (float) (6.0 + easeOutCubic(p) * (maxR + 34.0));
					int swAlpha = (int) Math.round(225 * Math.pow(1.0 - p, 1.7));
					drawSmoothGlowRing(mat, cx, cy, swR, 1.0F, 5.0F, withAlpha(0xFF425E, swAlpha));
				}

				// 8 Glowing Crimson-Amber Crystal Polygon Shards flying outward (0.00s .. 1.25s)
				if (revSec < 1.25) {
					double p = revSec / 1.25;
					double ease = 1.0 - Math.pow(1.0 - p, 3.2);
					int shardAlpha = (int) Math.round(235 * (1.0 - p));
					for (int i = 0; i < 8; i++) {
						double baseAng = i * (Math.PI * 2.0 / 8.0) + 0.22;
						float dist = (float) (8.0 + ease * (maxR + 14.0) * (i % 2 == 0 ? 1.05 : 0.78));
						float sx = cx + (float) (Math.cos(baseAng) * dist);
						float sy = cy + (float) (Math.sin(baseAng) * dist + p * p * 12.0);
						double spin = baseAng + p * (i % 2 == 0 ? 2.6 : -2.4);
						drawCrystalShardPolygon(mat, sx, sy, 5.5F * (float) (1.0 - p * 0.35), spin,
								withAlpha(0xFFFF6B52, shardAlpha), withAlpha(0xFFC81D35, shardAlpha / 2));
					}
				}

				// 5 Smooth Volumetric Lime-Gold XP Orbs orbiting gently around the Experience Essence
				double xpFade = Math.min(1.0, revSec / 0.45);
				for (int i = 0; i < 5; i++) {
					double xpAng = now * 0.0022 + i * (Math.PI * 2.0 / 5.0);
					float xpR = (float) (25.0 + Math.sin(now * 0.0045 + i * 1.3) * 3.5);
					float ox = cx + (float) (Math.cos(xpAng) * xpR);
					float oy = cy + (float) (Math.sin(xpAng) * xpR);
					int outerCol = withAlpha(0x7CFF3B, (int) (175 * xpFade));
					int coreCol = withAlpha((i % 2 == 0) ? 0xE6FF85 : 0xFFE066, (int) (245 * xpFade));
					drawSmoothRadialGlow(mat, ox, oy, 8.0F, outerCol, 0x00000000);
					drawSmoothRadialGlow(mat, ox, oy, 3.4F, coreCol, 0x00000000);
				}

				drawSmoothRadialGlow(mat, cx, cy, 20.0F, withAlpha(0x8AFF47, (int) (95 * xpFade)), 0x00000000);
				endAdditiveGlow();

				float xpScale = (float) (1.85 * Math.min(1.0, easeOutCubic(Math.min(1.0, revSec / 0.40))));
				float bobY = (float) (Math.sin(now * 0.0036) * 2.2);
				drawScaledItemCentered(ctx, new ItemStack(Items.EXPERIENCE_BOTTLE), cx, cy + bobY, xpScale, 0.0F);
				ctx.draw();
			}

			// Foremost Circular Radial Energy Bloom right at Explosion (0.00s .. 0.55s) — round sphere, never a box!
			if (revSec < 0.55) {
				beginAdditiveGlow(ctx);
				Matrix4f bloomMat = ctx.getMatrices().peek().getPositionMatrix();
				double fade = Math.pow(1.0 - (revSec / 0.55), 2.2);
				int bloomAlpha = Math.max(0, Math.min(245, (int) Math.round(235 * fade)));
				int bloomRgb = s.upgraderWon ? 0xFFF7C2 : 0xFF425E;
				float bloomR = (float) (maxR * 2.1 * (0.55 + 0.45 * (revSec / 0.55)));
				drawSmoothRadialGlow(bloomMat, cx, cy, bloomR, withAlpha(bloomRgb, bloomAlpha), 0x00000000);
				endAdditiveGlow();
			}
		}

		// 4. Spacious Bottom Status Readout, Animated Pity Meter & Action Controls
		if (spinning) {
			String phaseText;
			if (chargeT < 0.28) {
				phaseText = goldenPityRitual
						? "☀ Awakening Sovereign Golden Circle... ☀"
						: "✦ Awakening the Arcane Circle... ✦";
			} else if (chargeT < 0.74) {
				phaseText = "✦ Absorbing " + s.upgraderSacrificedAmount + "x " + s.upgraderSacrificeName + " into the Crystal Core... ✦";
			} else {
				phaseText = goldenPityRitual
						? "☀ Divine Guarantee Converging — Jackpot Imminent! ☀"
						: "✦ Essence Converging — Fate Imminent... ✦";
			}
			drawCenteredText(ctx, trimToWidth(phaseText, stageW - 24), stageX + stageW / 2, arenaBot + 10, CrimsonTheme.GOLD);

			int skipW = 124;
			int skipH = 17;
			int skipX = stageX + (stageW - skipW) / 2;
			int skipY = stageY + stageH - 23;
			boolean canSkipNow = elapsedMs >= 350L;
			boolean hovSkip = canSkipNow && inRect(mx, my, skipX, skipY, skipW, skipH);
			CrimsonTheme.drawSubtleButton(ctx, this.textRenderer, skipX, skipY, skipW, skipH, "⏭ Skip Ritual [Space]", false, hovSkip);
			if (canSkipNow) {
				clickZones.add(new ClickZone(skipX, skipY, skipW, skipH, () -> skipRitualToReveal(s)));
			}
		} else {
			if (s.upgraderWon) {
				String enchTag = s.upgraderInheritedEnchants > 0 ? ("  ✦ +" + s.upgraderInheritedEnchants + " Enchants") : "";
				String headline = "✦ Obtained: " + s.upgraderTargetName + enchTag;
				String rollLine = s.upgraderPityTriggered
						? "☀ 100% Divine Pity Guarantee Consumed  ·  Pity Reset to 0%"
						: ("Fate Roll: " + s.upgraderRolled + "% ≤ " + s.upgraderChance + "%  ·  Pity Reset (0%)");
				drawCenteredText(ctx, trimToWidth(headline, stageW - 24), stageX + stageW / 2, arenaBot + 6, CrimsonTheme.GOLD);
				drawCenteredText(ctx, trimToWidth(rollLine, stageW - 24), stageX + stageW / 2, arenaBot + 18,
						s.upgraderPityTriggered ? CrimsonTheme.GOLD : CrimsonTheme.EMERALD);
			} else {
				double fillAnim = easeOutCubic(Math.min(1.0, revSec / 0.85));
				double animPity = s.upgraderPityBefore + (s.upgraderPityAfter - s.upgraderPityBefore) * fillAnim;
				boolean hitMaxPity = s.upgraderPityAfter >= ItemUpgraderFeature.MAX_PITY;

				String headline = hitMaxPity
						? ("Essence: +" + s.upgraderXpRefund + " XP  ·  ☀ 100% PITY GUARANTEE READY!")
						: ("Essence: +" + s.upgraderXpRefund + " XP  ·  Arcane Pity +" + s.upgraderPityGain + "% (" + s.upgraderPityAfter + "%)");
				drawCenteredText(ctx, trimToWidth(headline, stageW - 24), stageX + stageW / 2, arenaBot + 4,
						hitMaxPity ? CrimsonTheme.GOLD : CrimsonTheme.TEXT_PRIMARY);

				int barW = stageW - 44;
				int barX = stageX + 22;
				drawArcanePityBar(ctx, barX, arenaBot + 16, barW, 7, animPity, now);

				String rollLine = "Rolled " + s.upgraderRolled + "% > " + s.upgraderChance + "% Needed";
				drawCenteredText(ctx, trimToWidth(rollLine, stageW - 24), stageX + stageW / 2, arenaBot + 25, CrimsonTheme.CRIMSON_BRIGHT);
			}

			var clientPlayer = MinecraftClient.getInstance().player;
			List<ItemUpgraderFeature.BagOfferSlot> liveOffers = clientPlayer == null
					? List.of()
					: ItemUpgraderFeature.collectPlayerOffers(clientPlayer.getInventory());
			ItemUpgraderFeature.BagOfferSlot rerollOffer = findMatchingRerollOffer(liveOffers, s);

			Identifier tIdent = Identifier.tryParse(s.upgraderTargetId);
			Item tItem = (tIdent != null && Registries.ITEM.containsId(tIdent)) ? Registries.ITEM.get(tIdent) : Items.NETHERITE_INGOT;
			boolean canRerollSameItem = rerollOffer != null
					&& rerollOffer.totalCount() > 0
					&& (rerollOffer.sample().getItem() != tItem || rerollOffer.enchantCount() > 0);

			int btnY = stageY + stageH - 22;
			int btnH = 17;
			if (canRerollSameItem) {
				int nextAmt = Math.max(1, Math.min(rerollOffer.totalCount(), s.upgraderAmount));
				double nextChance = ItemUpgraderFeature.calculateEffectiveWinChance(rerollOffer.sample(), nextAmt, tItem, s.upgraderPity);
				boolean nextIsFullPity = s.upgraderPity >= ItemUpgraderFeature.MAX_PITY;
				int btnW = (stageW - 26) / 2;
				int leftBtnX = stageX + 10;
				int rightBtnX = leftBtnX + btnW + 6;

				String rerollLabel = nextIsFullPity
						? "☀ Guaranteed Pity!"
						: ("↻ Roll Again (" + nextChance + "%)");
				int rerollCol = nextIsFullPity ? 0xFFB87414 : 0xFF7D1D58;
				boolean hovReroll = inRect(mx, my, leftBtnX, btnY, btnW, btnH);
				CrimsonTheme.drawButton(ctx, this.textRenderer, leftBtnX, btnY, btnW, btnH,
						trimToWidth(rerollLabel, btnW - 8), rerollCol, hovReroll);
				final ItemUpgraderFeature.BagOfferSlot finalReroll = rerollOffer;
				final Item finalTItem = tItem;
				clickZones.add(new ClickZone(leftBtnX, btnY, btnW, btnH, () -> triggerRitualRoll(s, finalReroll, finalTItem)));

				String closeLabel = s.upgraderWon ? "✦ Claim & Close" : "Close Altar";
				int closeColor = s.upgraderWon ? 0xFF1B7A4C : 0xFF3B202C;
				boolean hovClose = inRect(mx, my, rightBtnX, btnY, btnW, btnH);
				CrimsonTheme.drawButton(ctx, this.textRenderer, rightBtnX, btnY, btnW, btnH, closeLabel, closeColor, hovClose);
				clickZones.add(new ClickZone(rightBtnX, btnY, btnW, btnH, () -> closeRitualModal(s)));
			} else {
				int btnW = Math.min(184, stageW - 26);
				int btnX = stageX + (stageW - btnW) / 2;
				String closeLabel = s.upgraderWon ? "✦ Claim & Continue" : "Continue";
				int closeColor = s.upgraderWon ? 0xFF1B7A4C : CrimsonTheme.CRIMSON;
				boolean hovClose = inRect(mx, my, btnX, btnY, btnW, btnH);
				CrimsonTheme.drawButton(ctx, this.textRenderer, btnX, btnY, btnW, btnH, closeLabel, closeColor, hovClose);
				clickZones.add(new ClickZone(btnX, btnY, btnW, btnH, () -> closeRitualModal(s)));
			}
		}
	}

	private static void drawArcanePityBar(DrawContext ctx, int x, int y, int w, int h, double pityPct, long now) {
		double clamped = Math.max(0.0, Math.min(100.0, pityPct));
		boolean full = clamped >= 100.0;
		boolean soft = !full && clamped >= ItemUpgraderFeature.SOFT_PITY_THRESHOLD;

		int borderCol = full ? CrimsonTheme.GOLD : (soft ? 0xFFFF8C38 : 0xFF4A2559);
		ctx.fill(x, y, x + w, y + h, 0xFF110814);
		ctx.drawBorder(x, y, w, h, borderCol);

		int innerW = Math.max(0, w - 2);
		int fillW = (int) Math.round((clamped / 100.0) * innerW);
		if (fillW > 0) {
			int topCol;
			int botCol;
			if (full) {
				int pulse = (int) (35 * (0.5 + 0.5 * Math.sin(now * 0.008)));
				topCol = (0xFF << 24) | ((255) << 16) | (Math.min(255, 210 + pulse) << 8) | 90;
				botCol = 0xFFD98218;
			} else if (soft) {
				topCol = 0xFFFFA040;
				botCol = 0xFFC8286E;
			} else {
				topCol = 0xFFC86BFF;
				botCol = 0xFF5D2BB8;
			}
			ctx.fillGradient(x + 1, y + 1, x + 1 + fillW, y + h - 1, topCol, botCol);
			int tipX = Math.min(x + innerW, x + fillW);
			ctx.fill(Math.max(x + 1, tipX - 1), y + 1, tipX + 1, y + h - 1, 0xDDFFF6CC);
		}

		int softX = x + 1 + (int) Math.round(innerW * (ItemUpgraderFeature.SOFT_PITY_THRESHOLD / 100.0));
		ctx.fill(softX, y + 1, softX + 1, y + h - 1, full ? 0x88FFFFFF : 0xAAFFBC3B);
	}

	// ==================== SMOOTH OPENGL VECTOR & EASING PRIMITIVES ====================
	private static double easeOutCubic(double t) {
		double clamped = Math.max(0.0, Math.min(1.0, t));
		return 1.0 - Math.pow(1.0 - clamped, 3.0);
	}

	private static double easeInCubic(double t) {
		double clamped = Math.max(0.0, Math.min(1.0, t));
		return clamped * clamped * clamped;
	}

	private static double easeInQuad(double t) {
		double clamped = Math.max(0.0, Math.min(1.0, t));
		return clamped * clamped;
	}

	private static double easeInOutCubic(double t) {
		double c = Math.max(0.0, Math.min(1.0, t));
		return c < 0.5 ? 4.0 * c * c * c : 1.0 - Math.pow(-2.0 * c + 2.0, 3.0) / 2.0;
	}

	private static double easeInOutSine(double t) {
		double c = Math.max(0.0, Math.min(1.0, t));
		return -(Math.cos(Math.PI * c) - 1.0) / 2.0;
	}

	private static void beginAdditiveGlow(DrawContext ctx) {
		ctx.draw();
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
	}

	private static void endAdditiveGlow() {
		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	private static void flushBuffer(BufferBuilder buf) {
		BuiltBuffer built = buf.endNullable();
		if (built != null) {
			BufferRenderer.drawWithGlobalProgram(built);
		}
	}

	private static void drawSmoothRadialGlow(Matrix4f mat, float cx, float cy, float radius, int centerColor, int edgeColor) {
		if (radius <= 0.4F || ((centerColor >>> 24) & 0xFF) == 0) {
			return;
		}
		int segments = 48;
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
		buf.vertex(mat, cx, cy, 0.0F).color(centerColor);
		for (int i = 0; i <= segments; i++) {
			double ang = (i * Math.PI * 2.0) / segments;
			float px = cx + (float) (Math.cos(ang) * radius);
			float py = cy + (float) (Math.sin(ang) * radius);
			buf.vertex(mat, px, py, 0.0F).color(edgeColor);
		}
		flushBuffer(buf);
	}

	private static void drawSmoothGlowRing(Matrix4f mat, float cx, float cy, float radius, float coreHalfW, float featherW, int color) {
		int baseAlpha = (color >>> 24) & 0xFF;
		if (radius <= 0.8F || baseAlpha <= 0) {
			return;
		}
		int segments = 64;
		int softColor = withAlpha(color, Math.max(1, (int) (baseAlpha * 0.42F)));
		int zeroColor = withAlpha(color, 0);

		float rInnerEdge = Math.max(0.2F, radius - coreHalfW - featherW);
		float rOuterEdge = radius + coreHalfW + featherW;

		// Inner feathered glow skirt
		BufferBuilder innerSkirt = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
		for (int i = 0; i <= segments; i++) {
			double ang = (i * Math.PI * 2.0) / segments;
			float cos = (float) Math.cos(ang);
			float sin = (float) Math.sin(ang);
			innerSkirt.vertex(mat, cx + cos * rInnerEdge, cy + sin * rInnerEdge, 0.0F).color(zeroColor);
			innerSkirt.vertex(mat, cx + cos * radius, cy + sin * radius, 0.0F).color(softColor);
		}
		flushBuffer(innerSkirt);

		// Outer feathered glow skirt
		BufferBuilder outerSkirt = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
		for (int i = 0; i <= segments; i++) {
			double ang = (i * Math.PI * 2.0) / segments;
			float cos = (float) Math.cos(ang);
			float sin = (float) Math.sin(ang);
			outerSkirt.vertex(mat, cx + cos * radius, cy + sin * radius, 0.0F).color(softColor);
			outerSkirt.vertex(mat, cx + cos * rOuterEdge, cy + sin * rOuterEdge, 0.0F).color(zeroColor);
		}
		flushBuffer(outerSkirt);

		// Crisp luminous core ring
		float rCoreIn = Math.max(0.2F, radius - coreHalfW);
		float rCoreOut = radius + coreHalfW;
		BufferBuilder core = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
		for (int i = 0; i <= segments; i++) {
			double ang = (i * Math.PI * 2.0) / segments;
			float cos = (float) Math.cos(ang);
			float sin = (float) Math.sin(ang);
			core.vertex(mat, cx + cos * rCoreIn, cy + sin * rCoreIn, 0.0F).color(color);
			core.vertex(mat, cx + cos * rCoreOut, cy + sin * rCoreOut, 0.0F).color(color);
		}
		flushBuffer(core);
	}

	private static void drawSmoothGlowSegment(Matrix4f mat, float x1, float y1, float x2, float y2, float halfWidth, int color) {
		int alpha = (color >>> 24) & 0xFF;
		float dx = x2 - x1;
		float dy = y2 - y1;
		float len = (float) Math.hypot(dx, dy);
		if (len < 0.2F || alpha <= 0) {
			return;
		}
		float nx = -dy / len;
		float ny = dx / len;
		float glowW = halfWidth * 2.8F;
		int glowCol = withAlpha(color, Math.max(1, alpha / 3));

		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		// Soft outer line glow
		buf.vertex(mat, x1 + nx * glowW, y1 + ny * glowW, 0.0F).color(glowCol);
		buf.vertex(mat, x2 + nx * glowW, y2 + ny * glowW, 0.0F).color(glowCol);
		buf.vertex(mat, x2 - nx * glowW, y2 - ny * glowW, 0.0F).color(glowCol);
		buf.vertex(mat, x1 - nx * glowW, y1 - ny * glowW, 0.0F).color(glowCol);
		// Crisp inner filament
		buf.vertex(mat, x1 + nx * halfWidth, y1 + ny * halfWidth, 0.0F).color(color);
		buf.vertex(mat, x2 + nx * halfWidth, y2 + ny * halfWidth, 0.0F).color(color);
		buf.vertex(mat, x2 - nx * halfWidth, y2 - ny * halfWidth, 0.0F).color(color);
		buf.vertex(mat, x1 - nx * halfWidth, y1 - ny * halfWidth, 0.0F).color(color);
		flushBuffer(buf);
	}

	private static void drawSmoothHexagram(Matrix4f mat, float cx, float cy, float radius, double angleOffset, float halfWidth, int color) {
		if (radius <= 2.0F || ((color >>> 24) & 0xFF) == 0) {
			return;
		}
		for (int tri = 0; tri < 2; tri++) {
			double base = angleOffset + tri * (Math.PI / 3.0);
			float[] vx = new float[3];
			float[] vy = new float[3];
			for (int k = 0; k < 3; k++) {
				double ang = base + k * (Math.PI * 2.0 / 3.0);
				vx[k] = cx + (float) (Math.cos(ang) * radius);
				vy[k] = cy + (float) (Math.sin(ang) * radius);
			}
			for (int k = 0; k < 3; k++) {
				int next = (k + 1) % 3;
				drawSmoothGlowSegment(mat, vx[k], vy[k], vx[next], vy[next], halfWidth, color);
				drawSmoothRadialGlow(mat, vx[k], vy[k], 3.2F, color, withAlpha(color, 0));
			}
		}
	}

	private static void drawSmoothSpiralComet(Matrix4f mat, float cx, float cy, double headAngle, float headRadius,
											  float radialSpan, double arcSpan, int headColor, int tailColor) {
		int headAlpha = (headColor >>> 24) & 0xFF;
		if (headAlpha <= 2) {
			return;
		}
		int steps = 14;
		BufferBuilder ribbon = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
		for (int i = 0; i <= steps; i++) {
			float f = i / (float) steps;
			double ang = headAngle - f * arcSpan;
			float r = headRadius + f * radialSpan;
			float px = cx + (float) (Math.cos(ang) * r);
			float py = cy + (float) (Math.sin(ang) * r);

			// Radial normal across spiral tangent for smooth tapered ribbon width
			float nx = (float) Math.cos(ang);
			float ny = (float) Math.sin(ang);
			float w = (1.0F - f) * 2.2F + 0.15F;
			int stepAlpha = (int) Math.round(headAlpha * Math.pow(1.0F - f, 1.6));
			int col = lerpColorRgb(headColor, tailColor, f, stepAlpha);

			ribbon.vertex(mat, px - nx * w, py - ny * w, 0.0F).color(col);
			ribbon.vertex(mat, px + nx * w, py + ny * w, 0.0F).color(col);
		}
		flushBuffer(ribbon);

		float hx = cx + (float) (Math.cos(headAngle) * headRadius);
		float hy = cy + (float) (Math.sin(headAngle) * headRadius);
		drawSmoothRadialGlow(mat, hx, hy, 5.2F, withAlpha(0xFFFFFF, headAlpha), withAlpha(headColor, 0));
	}

	private static void drawSmoothSunburstRays(Matrix4f mat, float cx, float cy, int count, float innerR, float outerR,
											   float halfAngle, double baseRot, int innerColor) {
		int outerCol = withAlpha(innerColor, 0);
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
		for (int i = 0; i < count; i++) {
			double a = baseRot + i * (Math.PI * 2.0 / count);
			float x0 = cx + (float) (Math.cos(a) * innerR);
			float y0 = cy + (float) (Math.sin(a) * innerR);
			float x1 = cx + (float) (Math.cos(a - halfAngle) * outerR);
			float y1 = cy + (float) (Math.sin(a - halfAngle) * outerR);
			float x2 = cx + (float) (Math.cos(a + halfAngle) * outerR);
			float y2 = cy + (float) (Math.sin(a + halfAngle) * outerR);
			buf.vertex(mat, x0, y0, 0.0F).color(innerColor);
			buf.vertex(mat, x1, y1, 0.0F).color(outerCol);
			buf.vertex(mat, x2, y2, 0.0F).color(outerCol);
		}
		flushBuffer(buf);
	}

	private static void drawCrystalPrismStar(Matrix4f mat, float cx, float cy, float size, double rot, int centerColor, int tipColor) {
		if (size <= 0.5F) {
			return;
		}
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
		buf.vertex(mat, cx, cy, 0.0F).color(centerColor);
		int pts = 8;
		for (int i = 0; i <= pts; i++) {
			double ang = rot + i * (Math.PI * 2.0 / pts);
			float r = (i % 2 == 0) ? size : (size * 0.28F);
			buf.vertex(mat, cx + (float) (Math.cos(ang) * r), cy + (float) (Math.sin(ang) * r), 0.0F).color(tipColor);
		}
		flushBuffer(buf);
	}

	private static void drawCrystalShardPolygon(Matrix4f mat, float cx, float cy, float size, double rot, int coreCol, int edgeCol) {
		if (size <= 0.4F || ((coreCol >>> 24) & 0xFF) == 0) {
			return;
		}
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
		buf.vertex(mat, cx, cy, 0.0F).color(coreCol);
		float[] scales = {1.35F, 0.55F, 1.05F, 0.48F, 1.35F};
		double[] offsets = {0.0, 1.35, Math.PI, 4.55, Math.PI * 2.0};
		for (int i = 0; i < scales.length; i++) {
			double a = rot + offsets[i];
			float r = size * scales[i];
			buf.vertex(mat, cx + (float) (Math.cos(a) * r), cy + (float) (Math.sin(a) * r), 0.0F).color(edgeCol);
		}
		flushBuffer(buf);
	}

	private static int lerpColorRgb(int c1, int c2, float t, int alpha) {
		int r1 = (c1 >>> 16) & 0xFF;
		int g1 = (c1 >>> 8) & 0xFF;
		int b1 = c1 & 0xFF;
		int r2 = (c2 >>> 16) & 0xFF;
		int g2 = (c2 >>> 8) & 0xFF;
		int b2 = c2 & 0xFF;
		int r = Math.round(r1 + (r2 - r1) * t);
		int g = Math.round(g1 + (g2 - g1) * t);
		int b = Math.round(b1 + (b2 - b1) * t);
		return (Math.max(0, Math.min(255, alpha)) << 24) | (r << 16) | (g << 8) | b;
	}

	private void drawCenteredText(DrawContext ctx, String text, int centerX, int y, int color) {
		ctx.drawTextWithShadow(this.textRenderer, text, centerX - this.textRenderer.getWidth(text) / 2, y, color);
	}

	private static int withAlpha(int rgbOrArgb, int alpha) {
		int clamped = Math.max(0, Math.min(255, alpha));
		return (clamped << 24) | (rgbOrArgb & 0x00FFFFFF);
	}

	private static void drawRuneDiamond(DrawContext ctx, int cx, int cy, int radius, int coreColor, int glowColor) {
		ctx.fill(cx - radius, cy, cx + radius + 1, cy + 1, glowColor);
		ctx.fill(cx, cy - radius, cx + 1, cy + radius + 1, glowColor);
		if (radius >= 2) {
			ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, coreColor);
		} else {
			ctx.fill(cx, cy, cx + 1, cy + 1, coreColor);
		}
	}

	private static void drawScaledItemCentered(DrawContext ctx, ItemStack stack, float cx, float cy, float scale, float tiltDeg) {
		if (stack == null || stack.isEmpty() || scale <= 0.04F) {
			return;
		}
		ctx.getMatrices().push();
		ctx.getMatrices().translate(cx, cy, 40.0F);
		if (Math.abs(tiltDeg) > 0.01F) {
			ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(tiltDeg));
		}
		ctx.getMatrices().scale(scale, scale, scale);
		ctx.drawItem(stack, -8, -8);
		ctx.getMatrices().pop();
	}

	// ==================== TAB 8: UNCRAFTER & ENCHANT BOOK EXTRACTOR ====================
	private int renderUncraftTab(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		var mc = MinecraftClient.getInstance();
		var clientPlayer = mc.player;
		var clientWorld = mc.world;
		List<UncraftFeature.UncraftCandidate> candidates = (clientPlayer == null || clientWorld == null)
				? List.of()
				: UncraftFeature.collectUncraftableSlots(clientWorld, clientPlayer.getInventory());

		UncraftFeature.UncraftCandidate active = null;
		for (UncraftFeature.UncraftCandidate c : candidates) {
			if (c.slot() == s.uncraftSelectedSlot) {
				active = c;
				break;
			}
		}
		if (active == null && !candidates.isEmpty()) {
			active = candidates.get(0);
			s.uncraftSelectedSlot = active.slot();
		}

		int maxBatches = active == null ? 1 : Math.max(1, active.maxBatches());
		s.uncraftBatches = Math.max(1, Math.min(maxBatches, s.uncraftBatches));

		UncraftFeature.UncraftPlan plan = (active == null || clientPlayer == null || clientWorld == null)
				? new UncraftFeature.UncraftPlan(false, "No craftable or enchanted items found in your inventory.", 0, 0, List.of(), List.of(), false)
				: UncraftFeature.buildPlan(clientWorld, clientPlayer.getInventory(), active.slot(), s.uncraftBatches, s.uncraftSplitBooks);

		// 1. Top Deconstruction & Enchanted Book Preview Card
		List<ItemStack> allOutputs = plan.allOutputs();
		int outCell = 22;
		int outGap = 3;
		int outAreaX = x + 48;
		int outAreaW = Math.max(44, w - 56);
		int outCols = Math.max(1, (outAreaW + outGap) / (outCell + outGap));
		int outRows = allOutputs.isEmpty() ? 1 : ((allOutputs.size() + outCols - 1) / outCols);
		int previewCardH = Math.max(54, 24 + outRows * (outCell + outGap) + 4);

		CrimsonTheme.drawCard(ctx, x, y, w, previewCardH, false, plan.canUncraft() ? CrimsonTheme.EMERALD : CrimsonTheme.BORDER_SUBTLE);
		String cardHdr = active == null
				? "UNCRAFT PREVIEW"
				: ("DECONSTRUCT: " + Math.max(1, plan.requiredInputCount()) + "x " + active.sample().getName().getString());
		ctx.drawTextWithShadow(textRenderer, trimToWidth(cardHdr, w - 14), x + 8, y + 6, CrimsonTheme.GOLD);

		// Source Item 3D Slot
		int srcX = x + 8;
		int srcY = y + 20;
		drawModernSlotBox(ctx, srcX, srcY, 24, 24, active != null, false, CrimsonTheme.GOLD);
		if (active != null) {
			ItemStack inDisp = active.sample().copyWithCount(Math.min(99, Math.max(1, plan.requiredInputCount())));
			ctx.drawItem(inDisp, srcX + 4, srcY + 4);
			ctx.drawItemInSlot(textRenderer, inDisp, srcX + 4, srcY + 4);
			if (inClip(mx, my, clipTop, clipBot) && inRect(mx, my, srcX, srcY, 24, 24)) {
				this.hoveredTooltipStack = active.sample();
			}
		}
		ctx.drawTextWithShadow(textRenderer, "➔", x + 36, srcY + 8, CrimsonTheme.EMERALD);

		// Returned Raw Materials + Enchanted Books 3D Slots
		if (!plan.canUncraft()) {
			ctx.drawTextWithShadow(textRenderer, trimToWidth(plan.reason(), outAreaW), outAreaX, srcY + 8, CrimsonTheme.TEXT_MUTED);
		} else {
			for (int i = 0; i < allOutputs.size(); i++) {
				ItemStack outStack = allOutputs.get(i);
				int c = i % outCols;
				int r = i / outCols;
				int ox = outAreaX + c * (outCell + outGap);
				int oy = srcY + r * (outCell + outGap);
				boolean isBook = outStack.isOf(Items.ENCHANTED_BOOK);
				boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, ox, oy, outCell, outCell);

				drawModernSlotBox(ctx, ox, oy, outCell, outCell, true, hov, isBook ? CrimsonTheme.AMETHYST : CrimsonTheme.EMERALD);
				ctx.drawItem(outStack, ox + 3, oy + 3);
				ctx.drawItemInSlot(textRenderer, outStack, ox + 3, oy + 3);
				if (hov) {
					this.hoveredTooltipStack = outStack;
				}
			}
		}
		y += previewCardH + 5;

		// 2. Split Enchanted Books Toggle
		y = drawToggleCard(ctx, x, y, w, "Split Enchanted Books",
				s.uncraftSplitBooks, clipTop, clipBot, mx, my,
				() -> s.uncraftSplitBooks = !s.uncraftSplitBooks);

		// 3. Batch Count Slider (when stackable craftable items allow >1 batch)
		if (maxBatches > 1 && active != null) {
			int perBatch = Math.max(1, active.requiredPerBatch());
			y = drawSliderCard(ctx, "uncraft_batch", x, y, w,
					"Uncraft Batches (" + perBatch + ")",
					s.uncraftBatches + " batch (" + (s.uncraftBatches * perBatch) + ")",
					s.uncraftBatches, 1, maxBatches, clipTop, clipBot, mx, my,
					v -> s.uncraftBatches = v,
					v -> s.uncraftBatches = v);
		}

		// 4. Primary Uncraft Action Button
		String btnLabel;
		if (!plan.canUncraft()) {
			btnLabel = "Select a Craftable or Enchanted Item Below";
		} else if (!plan.enchantBooks().isEmpty() && plan.disenchantOnlyKeepBase()) {
			btnLabel = "✦ Extract " + plan.enchantBooks().size() + " Enchanted Book";
		} else if (!plan.enchantBooks().isEmpty()) {
			btnLabel = "✦ Uncraft to Raw Materials + " + plan.enchantBooks().size() + " Enchanted Book";
		} else {
			btnLabel = "✦ Uncraft into Raw Materials (" + plan.rawMaterials().size() + ")";
		}
		final UncraftFeature.UncraftCandidate chosen = active;
		drawActionBtn(ctx, x, y, w, 21, btnLabel, plan.canUncraft() ? CrimsonTheme.CRIMSON : 0xFF3A2029, clipTop, clipBot, mx, my, () -> {
			if (chosen == null || !plan.canUncraft()) return;
			JsonObject o = new JsonObject();
			o.addProperty("slot", chosen.slot());
			o.addProperty("batches", s.uncraftBatches);
			o.addProperty("splitBooks", s.uncraftSplitBooks);
			sendAction("uncraft_execute", o);
		});
		y += 26;

		// 5. 3D Uncraftable Gear / Bag Picker Grid
		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "SELECT CRAFTED OR ENCHANTED ITEM (" + candidates.size() + ")");
		y += 15;
		int cell = 22;
		int gap = 3;
		int cols = Math.max(1, (w + gap) / (cell + gap));

		if (candidates.isEmpty()) {
			ctx.drawTextWithShadow(textRenderer, "No craftable or enchanted items in your bag/armor.", x + 4, y + 4, CrimsonTheme.TEXT_MUTED);
			return y + 18;
		}

		int col = 0;
		for (UncraftFeature.UncraftCandidate cand : candidates) {
			int gx = x + col * (cell + gap);
			int gy = y;
			boolean sel = (active != null && cand.slot() == active.slot());
			boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, gx, gy, cell, cell);

			drawModernSlotBox(ctx, gx, gy, cell, cell, sel, hov, CrimsonTheme.GOLD);

			ItemStack iconSt = cand.sample().copyWithCount(Math.min(99, cand.availableCount()));
			ctx.drawItem(iconSt, gx + 3, gy + 3);
			if (cand.availableCount() > 1) {
				String cntStr = cand.availableCount() > 99 ? "99+" : String.valueOf(cand.availableCount());
				ctx.getMatrices().push();
				ctx.getMatrices().translate(0.0F, 0.0F, 200.0F);
				ctx.drawTextWithShadow(textRenderer, cntStr, gx + cell - textRenderer.getWidth(cntStr) - 1, gy + cell - 9, 0xFFFFFFFF);
				ctx.getMatrices().pop();
			} else if (!cand.slotTag().isEmpty()) {
				ctx.getMatrices().push();
				ctx.getMatrices().translate(0.0F, 0.0F, 200.0F);
				ctx.drawTextWithShadow(textRenderer, cand.slotTag(), gx + 2, gy + 2, sel ? CrimsonTheme.GOLD : 0xFFB8A8B0);
				ctx.getMatrices().pop();
			}
			if (cand.enchantCount() > 0) {
				ctx.fill(gx + cell - 4, gy + 2, gx + cell - 2, gy + 4, 0xFFB86BFF);
			}
			if (hov) {
				this.hoveredTooltipStack = cand.sample();
			}
			addClippedClick(gx, gy, cell, cell, clipTop, clipBot, () -> {
				s.uncraftSelectedSlot = cand.slot();
				s.uncraftBatches = 1;
			});

			col++;
			if (col >= cols) {
				col = 0;
				y += cell + gap;
			}
		}
		if (col > 0) {
			y += cell + gap;
		}
		return y + 6;
	}

	// ==================== TAB 9: POTION EFFECTS ====================
	private int renderEffectsTab(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		y = drawToggleCard(ctx, x, y, w, "Fullbright",
				s.fullbright, clipTop, clipBot, mx, my, () -> {
					s.fullbright = !s.fullbright;
					JsonObject o = new JsonObject();
					o.addProperty("value", s.fullbright);
					sendAction("toggle_fullbright", o);
				});
		y = drawToggleCard(ctx, x, y, w, "Infinite Duration (∞)",
				s.effectInfinite, clipTop, clipBot, mx, my, () -> s.effectInfinite = !s.effectInfinite);

		if (!s.effectInfinite) {
			y = drawSliderCard(ctx, "eff_sec", x, y, w, "Effect Duration", s.effectSeconds + "s",
					s.effectSeconds, 30, 1800, clipTop, clipBot, mx, my,
					v -> s.effectSeconds = v, v -> s.effectSeconds = v);
		}
		y = drawSliderCard(ctx, "eff_amp", x, y, w, "Effect Tier / Amplifier",
				"Tier " + EnchantFeature.toRoman(s.effectAmplifier + 1),
				s.effectAmplifier + 1, 1, 10, clipTop, clipBot, mx, my,
				v -> s.effectAmplifier = v - 1, v -> s.effectAmplifier = v - 1);

		int half = (w - 6) / 2;
		drawActionBtn(ctx, x, y, half, 18, "Cure Negative Effects", 0xFF2E6B4E, clipTop, clipBot, mx, my,
				() -> sendAction("effect_clear_negative", new JsonObject()));
		drawActionBtn(ctx, x + half + 6, y, half, 18, "Clear All Effects", 0xFF5A1D28, clipTop, clipBot, mx, my,
				() -> sendAction("effect_clear_all", new JsonObject()));
		y += 24;

		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "POTION BUFF STUDIO");
		y += 16;
		for (int i = 0; i < EFFECTS.size(); i += 2) {
			for (int col = 0; col < 2 && (i + col) < EFFECTS.size(); col++) {
				NamedEntry eff = EFFECTS.get(i + col);
				int cx = x + col * (half + 6);
				drawActionBtn(ctx, cx, y, half, 19, eff.label() + " " + EnchantFeature.toRoman(s.effectAmplifier + 1),
						0xFF2A1822, clipTop, clipBot, mx, my, () -> {
							JsonObject o = new JsonObject();
							o.addProperty("id", eff.id());
							o.addProperty("seconds", s.effectSeconds);
							o.addProperty("amplifier", s.effectAmplifier);
							o.addProperty("infinite", s.effectInfinite);
							sendAction("effect_apply", o);
						});
			}
			y += 22;
		}
		return y;
	}

	// ==================== TAB 8: XP MANAGER ====================
	private int renderXpTab(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		CrimsonTheme.drawCard(ctx, x, y, w, 28, false, CrimsonTheme.EMERALD);
		ctx.drawTextWithShadow(textRenderer, "Current Level: " + s.xpLevel, x + 8, y + 6, CrimsonTheme.EMERALD);
		ctx.drawTextWithShadow(textRenderer, "Total XP Points: " + s.xpTotal, x + 8, y + 16, CrimsonTheme.TEXT_SECONDARY);
		drawActionBtn(ctx, x + w - 88, y + 5, 82, 18, "Set Exact Lv.", CrimsonTheme.CRIMSON, clipTop, clipBot, mx, my, () -> {
			try {
				int lv = Integer.parseInt(this.primaryInput.getText().trim());
				sendXp("set_level", lv);
			} catch (NumberFormatException e) {
				s.toastText = "Enter a valid number in the box above!";
				s.toastOk = false;
				s.toastExpireMs = System.currentTimeMillis() + 2500L;
			}
		});
		y += 34;

		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "QUICK XP ADJUSTMENTS");
		y += 16;
		int third = (w - 8) / 3;
		drawActionBtn(ctx, x, y, third, 20, "+1 Level", 0xFF245B42, clipTop, clipBot, mx, my, () -> sendXp("add_levels", 1));
		drawActionBtn(ctx, x + third + 4, y, third, 20, "+10 Levels", 0xFF245B42, clipTop, clipBot, mx, my, () -> sendXp("add_levels", 10));
		drawActionBtn(ctx, x + (third + 4) * 2, y, w - (third + 4) * 2, 20, "+50 Levels", CrimsonTheme.CRIMSON, clipTop, clipBot, mx, my, () -> sendXp("add_levels", 50));
		y += 24;
		drawActionBtn(ctx, x, y, third, 20, "+100 Levels", 0xFF7B2CBF, clipTop, clipBot, mx, my, () -> sendXp("add_levels", 100));
		drawActionBtn(ctx, x + third + 4, y, third, 20, "-10 Levels", 0xFF47202B, clipTop, clipBot, mx, my, () -> sendXp("add_levels", -10));
		drawActionBtn(ctx, x + (third + 4) * 2, y, w - (third + 4) * 2, 20, "Reset to 0", 0xFF6B1D2A, clipTop, clipBot, mx, my, () -> sendXp("reset", 0));
		return y + 24;
	}

	// ==================== TAB 9: TELEPORT & LOCATOR ====================
	private int renderTeleportTab(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		y = drawBackLocationCard(ctx, s, x, y, w, clipTop, clipBot, mx, my);

		String[] subLabels = {"Biomes", "Structures", "Players", "Coords"};
		int quarter = (w - 9) / 4;
		for (int i = 0; i < 4; i++) {
			final int sub = i;
			int bx = x + i * (quarter + 3);
			boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, bx, y, quarter, 17);
			CrimsonTheme.drawSubtleButton(ctx, textRenderer, bx, y, quarter, 17, subLabels[i], s.teleportSubTab == i, hov);
			addClippedClick(bx, y, quarter, 17, clipTop, clipBot, () -> {
				s.teleportSubTab = sub;
				this.contentScroll = 0;
				configureInputForActiveTab();
			});
		}
		y += 22;

		String q = this.searchQuery == null ? "" : this.searchQuery.trim().toLowerCase(Locale.ROOT);
		if (s.teleportSubTab == 0 || s.teleportSubTab == 1) {
			y = drawToggleCard(ctx, x, y, w, "Instant Safe Teleport",
					s.locatorAutoTp, clipTop, clipBot, mx, my, () -> s.locatorAutoTp = !s.locatorAutoTp);

			List<NamedEntry> list = (s.teleportSubTab == 0) ? BIOMES : STRUCTURES;
			String act = (s.teleportSubTab == 0) ? "tp_biome" : "tp_structure";
			int half = (w - 6) / 2;
			List<NamedEntry> filtered = new ArrayList<>();
			for (NamedEntry e : list) {
				if (q.isEmpty() || e.label().toLowerCase(Locale.ROOT).contains(q) || e.id().contains(q)) {
					filtered.add(e);
				}
			}
			for (int i = 0; i < filtered.size(); i += 2) {
				for (int col = 0; col < 2 && (i + col) < filtered.size(); col++) {
					NamedEntry entry = filtered.get(i + col);
					int cx = x + col * (half + 6);
					drawActionBtn(ctx, cx, y, half, 19, trimToWidth(entry.label(), half - 8), 0xFF281620, clipTop, clipBot, mx, my, () -> {
						JsonObject o = new JsonObject();
						o.addProperty("id", entry.id());
						o.addProperty("autoTp", s.locatorAutoTp);
						sendAction(act, o);
					});
				}
				y += 22;
			}
		} else if (s.teleportSubTab == 2) {
			CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "ONLINE PLAYERS (" + s.onlinePlayers.size() + ")");
			y += 16;
			for (String pName : s.onlinePlayers) {
				if (!q.isEmpty() && !pName.toLowerCase(Locale.ROOT).contains(q)) continue;
				boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, x, y, w, 20);
				CrimsonTheme.drawCard(ctx, x, y, w, 20, hov, CrimsonTheme.CYAN);
				ctx.drawTextWithShadow(textRenderer, pName, x + 8, y + 6, CrimsonTheme.TEXT_PRIMARY);
				drawActionBtn(ctx, x + w - 58, y + 2, 54, 16, "Teleport", CrimsonTheme.CRIMSON, clipTop, clipBot, mx, my, () -> {
					JsonObject o = new JsonObject();
					o.addProperty("target", pName);
					sendAction("tp_player", o);
				});
				y += 23;
			}
		} else {
			CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "TARGET DIMENSION");
			y += 16;
			String[][] dims = {
					{"minecraft:overworld", "Overworld"},
					{"minecraft:the_nether", "The Nether"},
					{"minecraft:the_end", "The End"}
			};
			int third = (w - 8) / 3;
			for (int i = 0; i < dims.length; i++) {
				String dId = dims[i][0];
				int dx = x + i * (third + 4);
				boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, dx, y, third, 18);
				CrimsonTheme.drawSubtleButton(ctx, textRenderer, dx, y, third, 18, dims[i][1], dId.equals(s.selectedCoordDim), hov);
				addClippedClick(dx, y, third, 18, clipTop, clipBot, () -> s.selectedCoordDim = dId);
			}
			y += 24;
			drawActionBtn(ctx, x, y, w, 22, "Teleport to X Y Z Coordinates Above", CrimsonTheme.CRIMSON, clipTop, clipBot, mx, my, () -> {
				String[] parts = this.primaryInput.getText().trim().split("[\\s,]+");
				if (parts.length < 3) {
					s.toastText = "Enter X Y Z separated by spaces (e.g. 120 70 -340)";
					s.toastOk = false;
					s.toastExpireMs = System.currentTimeMillis() + 2800L;
					return;
				}
				try {
					JsonObject o = new JsonObject();
					o.addProperty("dimension", s.selectedCoordDim);
					o.addProperty("x", Double.parseDouble(parts[0]));
					o.addProperty("y", Double.parseDouble(parts[1]));
					o.addProperty("z", Double.parseDouble(parts[2]));
					sendAction("tp_coords", o);
				} catch (NumberFormatException e) {
					s.toastText = "Invalid coordinates!";
					s.toastOk = false;
					s.toastExpireMs = System.currentTimeMillis() + 2600L;
				}
			});
			y += 26;
		}
		return y;
	}

	// ==================== TAB 10: WAYPOINTS ====================
	private int renderWaypointsTab(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		y = drawBackLocationCard(ctx, s, x, y, w, clipTop, clipBot, mx, my);

		CrimsonTheme.drawCard(ctx, x, y, w, 26, false, s.selectedWaypointColor);
		ctx.drawTextWithShadow(textRenderer, "Color:", x + 7, y + 9, CrimsonTheme.TEXT_SECONDARY);
		int swX = x + 42;
		for (int color : WAYPOINT_COLORS) {
			ctx.fill(swX, y + 6, swX + 13, y + 19, color);
			ctx.drawBorder(swX, y + 6, 13, 13, s.selectedWaypointColor == color ? 0xFFFFFFFF : 0xFF1A1015);
			addClippedClick(swX, y + 6, 13, 13, clipTop, clipBot, () -> s.selectedWaypointColor = color);
			swX += 16;
		}
		drawActionBtn(ctx, x + w - 94, y + 4, 90, 18, "+ Save Position", CrimsonTheme.CRIMSON, clipTop, clipBot, mx, my, () -> {
			JsonObject o = new JsonObject();
			o.addProperty("name", this.primaryInput.getText().trim());
			o.addProperty("color", s.selectedWaypointColor);
			sendAction("waypoint_create", o);
			this.primaryInput.setText("");
		});
		y += 32;

		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "SAVED WAYPOINTS (" + s.waypoints.size() + ")");
		y += 16;
		for (ClientStateSnapshot.WaypointItem wp : s.waypoints) {
			boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, x, y, w, 24);
			CrimsonTheme.drawCard(ctx, x, y, w, 24, hov, wp.color);
			ctx.drawTextWithShadow(textRenderer, trimToWidth(wp.name, w - 98), x + 8, y + 4, CrimsonTheme.TEXT_PRIMARY);
			String sub = wp.dimension.replace("minecraft:", "") + " · " + (int) wp.x + ", " + (int) wp.y + ", " + (int) wp.z;
			ctx.drawTextWithShadow(textRenderer, sub, x + 8, y + 14, CrimsonTheme.TEXT_MUTED);

			drawActionBtn(ctx, x + w - 22, y + 4, 18, 16, "✕", 0xFF5A1D28, clipTop, clipBot, mx, my, () -> {
				JsonObject o = new JsonObject();
				o.addProperty("id", wp.id);
				sendAction("waypoint_delete", o);
			});
			drawActionBtn(ctx, x + w - 68, y + 4, 42, 16, "Warp", CrimsonTheme.CRIMSON, clipTop, clipBot, mx, my, () -> {
				JsonObject o = new JsonObject();
				o.addProperty("id", wp.id);
				sendAction("waypoint_tp", o);
			});
			y += 27;
		}
		return y;
	}

	// ==================== TAB 13: COMMAND CONSOLE & GAMEMODE SWITCHER ====================
	private int renderConsoleTab(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		drawActionBtn(ctx, x, y, w, 19, "Execute Command Above!", CrimsonTheme.CRIMSON, clipTop, clipBot, mx, my, () -> {
			JsonObject o = new JsonObject();
			o.addProperty("command", this.primaryInput.getText().trim());
			sendAction("console_run", o);
		});
		y += 25;

		String activeGmLabel = formatIdLabel(s.gameMode == null || s.gameMode.isEmpty() ? "survival" : s.gameMode);
		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "GAMEMODE SWITCHER ACTIVE: " + activeGmLabel.toUpperCase(Locale.ROOT) + "");
		y += 16;

		int third = (w - 8) / 3;
		drawGameModeBtn(ctx, s, x, y, third, 20, "Creative", "creative", 0xFF7B2D9E, clipTop, clipBot, mx, my);
		drawGameModeBtn(ctx, s, x + third + 4, y, third, 20, "Survival", "survival", 0xFF1F7D50, clipTop, clipBot, mx, my);
		drawGameModeBtn(ctx, s, x + (third + 4) * 2, y, w - (third + 4) * 2, 20, "Hardcore", "hardcore", CrimsonTheme.CRIMSON, clipTop, clipBot, mx, my);
		y += 23;

		int half = (w - 6) / 2;
		drawGameModeBtn(ctx, s, x, y, half, 20, "Spectator", "spectator", 0xFF236685, clipTop, clipBot, mx, my);
		drawGameModeBtn(ctx, s, x + half + 6, y, w - half - 6, 20, "Adventure", "adventure", 0xFF8F571B, clipTop, clipBot, mx, my);
		y += 26;

		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "QUICK WORLD ACTIONS");
		y += 16;
		drawActionBtn(ctx, x, y, third, 18, "Time: Day", 0xFF2D5B88, clipTop, clipBot, mx, my, () -> sendQuick("day"));
		drawActionBtn(ctx, x + third + 4, y, third, 18, "Time: Night", 0xFF29224F, clipTop, clipBot, mx, my, () -> sendQuick("night"));
		drawActionBtn(ctx, x + (third + 4) * 2, y, w - (third + 4) * 2, 18, "Heal & Feed", 0xFF245B42, clipTop, clipBot, mx, my, () -> sendQuick("heal_feed"));
		y += 21;
		drawActionBtn(ctx, x, y, third, 18, "Weather: Clear", 0xFF2D5B88, clipTop, clipBot, mx, my, () -> sendQuick("weather_clear"));
		drawActionBtn(ctx, x + third + 4, y, third, 18, "Thunderstorm", 0xFF47335E, clipTop, clipBot, mx, my, () -> sendQuick("weather_thunder"));
		drawActionBtn(ctx, x + (third + 4) * 2, y, w - (third + 4) * 2, 18, "Clear Ground Lag", 0xFF6B2D1D, clipTop, clipBot, mx, my, () -> sendQuick("clear_lag"));
		y += 25;

		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w, "WORLD GAMERULES");
		y += 16;
		String[][] rules = {
				{"keepInventory", "Keep Inventory on Death"},
				{"mobGriefing", "Mob Griefing"},
				{"daylightCycle", "Daylight Cycle"},
				{"weatherCycle", "Weather Cycle"},
				{"immediateRespawn", "Immediate Respawn"},
				{"pvp", "Player vs Player"}
		};
		for (String[] rule : rules) {
			String key = rule[0];
			boolean val = Boolean.TRUE.equals(s.gamerules.getOrDefault(key, false));
			y = drawToggleCard(ctx, x, y, w, rule[1], val, clipTop, clipBot, mx, my, () -> {
				JsonObject o = new JsonObject();
				o.addProperty("rule", key);
				sendAction("console_gamerule", o);
			});
		}
		return y;
	}

	// ==================== TAB 14: INFO, CREDITS & FEATURE GUIDE ====================
	private int renderInfoTab(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		// 1. Hero Mod Version & Developer Credits Card
		int heroH = 54;
		CrimsonTheme.drawCard(ctx, x, y, w, heroH, false, CrimsonTheme.GOLD);
		CrimsonTheme.drawCircle(ctx, x + 22.0F, y + 27.0F, 15.5F, CrimsonTheme.withAlpha(CrimsonTheme.CRIMSON_BRIGHT, 42));
		ctx.drawTexture(EMBLEM_TEX, x + 9, y + 14, 26, 26, 0.0F, 0.0F, 64, 64, 64, 64);

		int infoX = x + 42;
		ctx.drawTextWithShadow(textRenderer, "VOID SOVEREIGN", infoX, y + 7, CrimsonTheme.TEXT_PRIMARY);
		int titleW = textRenderer.getWidth("VOID SOVEREIGN");
		CrimsonTheme.drawBadge(ctx, textRenderer, infoX + titleW + 6, y + 5, MOD_VERSION,
				0x38FFB838, CrimsonTheme.GOLD, CrimsonTheme.GOLD);
		int verW = textRenderer.getWidth(MOD_VERSION) + 10;
		if (infoX + titleW + verW + 76 < x + w) {
			CrimsonTheme.drawBadge(ctx, textRenderer, infoX + titleW + verW + 10, y + 5, "MC 1.21.1 · Fabric",
					CrimsonTheme.BG_INPUT, CrimsonTheme.BORDER_SUBTLE, CrimsonTheme.TEXT_SECONDARY);
		}

		// [ID | EN] Language Toggle Buttons on top-right of Hero Card
		int langBtnW = 24;
		int langBtnH = 15;
		int enX = x + w - langBtnW - 6;
		int idX = enX - langBtnW - 3;
		int langY = y + 6;
		boolean hovId = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, idX, langY, langBtnW, langBtnH);
		boolean hovEn = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, enX, langY, langBtnW, langBtnH);
		CrimsonTheme.drawSubtleButton(ctx, textRenderer, idX, langY, langBtnW, langBtnH, "ID", infoLangIndonesian, hovId);
		CrimsonTheme.drawSubtleButton(ctx, textRenderer, enX, langY, langBtnW, langBtnH, "EN", !infoLangIndonesian, hovEn);
		addClippedClick(idX, langY, langBtnW, langBtnH, clipTop, clipBot, () -> infoLangIndonesian = true);
		addClippedClick(enX, langY, langBtnW, langBtnH, clipTop, clipBot, () -> infoLangIndonesian = false);

		String devLabel = (infoLangIndonesian ? "Developer: " : "Developer: ") + DEVELOPER_NAME;
		ctx.drawTextWithShadow(textRenderer, trimToWidth(devLabel, w - 50), infoX, y + 22, CrimsonTheme.GOLD);
		String repoLine = "GitHub: axellpemula/Hardcore-Tools  ·  MIT License";
		ctx.drawTextWithShadow(textRenderer, trimToWidth(repoLine, w - 50), infoX, y + 35, CrimsonTheme.TEXT_MUTED);
		y += heroH + 6;

		// 2. Quick Keybinds Reference Strip
		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w,
				infoLangIndonesian ? "PINTASAN TOMBOL" : "SHORTCUTS & KEYBINDS");
		y += 15;
		int third = (w - 8) / 3;
		String kMenu = HardcoreToolsClient.getKeyLabel(HardcoreToolsClient.openDashboardKey, "H");
		String kWheel = HardcoreToolsClient.getKeyLabel(HardcoreToolsClient.radialWheelKey, "R");
		String kFb = HardcoreToolsClient.getKeyLabel(HardcoreToolsClient.toggleFullbrightKey, "G");
		drawKeybindInfoChip(ctx, x, y, third, "[" + kMenu + "]", infoLangIndonesian ? "Menu Utama" : "Dashboard");
		drawKeybindInfoChip(ctx, x + third + 4, y, third, "[" + kWheel + "]", "Radial Wheel");
		drawKeybindInfoChip(ctx, x + (third + 4) * 2, y, w - (third + 4) * 2, "[" + kFb + "]", "Fullbright");
		y += 25;

		// 3. Complete Feature Encyclopedia (Searchable!)
		String q = this.searchQuery == null ? "" : this.searchQuery.trim().toLowerCase(Locale.ROOT);
		CrimsonTheme.drawSectionHeader(ctx, textRenderer, x, y, w,
				infoLangIndonesian
						? ("KETERANGAN FITUR (" + INFO_ENTRIES.size() + ")")
						: ("FEATURE DESCRIPTIONS (" + INFO_ENTRIES.size() + ")"));
		y += 16;

		int descMaxW = Math.max(80, w - 18);
		for (InfoEntry entry : INFO_ENTRIES) {
			String desc = infoLangIndonesian ? entry.descId() : entry.descEn();
			if (!q.isEmpty()
					&& !entry.title().toLowerCase(Locale.ROOT).contains(q)
					&& !entry.category().toLowerCase(Locale.ROOT).contains(q)
					&& !desc.toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}

			List<OrderedText> wrapped = textRenderer.wrapLines(Text.literal(desc), descMaxW);
			int cardH = 24 + wrapped.size() * 10 + 5;
			boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, x, y, w, cardH);
			CrimsonTheme.drawCard(ctx, x, y, w, cardH, hov, entry.accentColor());

			// Feature Icon + Title + Category Badge
			ctx.drawTexture(entry.icon(), x + 8, y + 5, 14, 14, 0.0F, 0.0F, 64, 64, 64, 64);
			int catW = textRenderer.getWidth(entry.category()) + 10;
			ctx.drawTextWithShadow(textRenderer, trimToWidth(entry.title(), w - catW - 34), x + 26, y + 8, CrimsonTheme.TEXT_PRIMARY);
			CrimsonTheme.drawBadge(ctx, textRenderer, x + w - catW - 6, y + 5, entry.category(),
					CrimsonTheme.withAlpha(entry.accentColor(), 42), entry.accentColor(), entry.accentColor());

			// Wrapped Feature Description Lines
			int lineY = y + 23;
			for (OrderedText line : wrapped) {
				ctx.drawTextWithShadow(textRenderer, line, x + 9, lineY, CrimsonTheme.TEXT_SECONDARY);
				lineY += 10;
			}
			y += cardH + 4;
		}
		return y;
	}

	private void drawKeybindInfoChip(DrawContext ctx, int x, int y, int w, String keyBadge, String label) {
		CrimsonTheme.drawCard(ctx, x, y, w, 20, false, CrimsonTheme.CRIMSON_DARK);
		int kw = textRenderer.getWidth(keyBadge) + 8;
		CrimsonTheme.drawBadge(ctx, textRenderer, x + 6, y + 3, keyBadge,
				0x38FFB838, CrimsonTheme.GOLD, CrimsonTheme.GOLD);
		ctx.drawTextWithShadow(textRenderer, trimToWidth(label, w - kw - 14), x + kw + 10, y + 6, CrimsonTheme.TEXT_PRIMARY);
	}

	private void drawGameModeBtn(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int h,
								 String label, String modeKey, int activeColor, int clipTop, int clipBot, int mx, int my) {
		boolean active = modeKey.equalsIgnoreCase(s.gameMode);
		boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, x, y, w, h);

		if (active) {
			CrimsonTheme.drawButton(ctx, this.textRenderer, x, y, w, h, trimToWidth("✦ " + label, w - 6), activeColor, hov);
			CrimsonTheme.drawRoundedOutline(ctx, x, y, w, h, 4.5F, 1.1F, CrimsonTheme.GOLD);
		} else {
			int bg = hov ? CrimsonTheme.BG_CARD_HOVER : CrimsonTheme.BG_CARD;
			CrimsonTheme.drawRoundedRect(ctx, x, y, w, h, 4.5F, bg);
			CrimsonTheme.drawRoundedOutline(ctx, x, y, w, h, 4.5F, 1.0F, hov ? activeColor : CrimsonTheme.BORDER_SUBTLE);
			CrimsonTheme.drawRoundedRect(ctx, x + 2.5F, y + 3.5F, 2.5F, h - 7.0F, 1.25F, activeColor);
			String txt = trimToWidth(label, w - 8);
			int tx = x + 3 + (w - 3 - this.textRenderer.getWidth(txt)) / 2;
			int ty = y + (h - 8) / 2;
			ctx.drawTextWithShadow(this.textRenderer, txt, tx, ty, hov ? CrimsonTheme.TEXT_PRIMARY : CrimsonTheme.TEXT_SECONDARY);
		}

		addClippedClick(x, y, w, h, clipTop, clipBot, () -> {
			s.gameMode = modeKey;
			if ("hardcore".equals(modeKey)) {
				s.worldHardcore = true;
				s.hasSyncedWorldHardcore = true;
			} else if ("survival".equals(modeKey)) {
				s.worldHardcore = false;
				s.hasSyncedWorldHardcore = true;
			}
			JsonObject o = new JsonObject();
			o.addProperty("mode", modeKey);
			sendAction("set_gamemode", o);
		});
	}

	// ==================== REUSABLE COMPONENT HELPERS ====================
	private int drawBackLocationCard(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		if (!s.hasBackLocation) {
			return y;
		}
		boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, x, y, w, 24);
		CrimsonTheme.drawCard(ctx, x, y, w, 24, hov, CrimsonTheme.GOLD);
		String title = "↩ Return: " + s.backLabel;
		String coords = s.backDimension.replace("minecraft:", "") + " · " + (int) s.backX + ", " + (int) s.backY + ", " + (int) s.backZ;
		ctx.drawTextWithShadow(textRenderer, trimToWidth(title, w - 84), x + 8, y + 4, CrimsonTheme.GOLD);
		ctx.drawTextWithShadow(textRenderer, trimToWidth(coords, w - 84), x + 8, y + 14, CrimsonTheme.TEXT_SECONDARY);
		drawActionBtn(ctx, x + w - 74, y + 4, 68, 16, "↩ Back", 0xFF8C5319, clipTop, clipBot, mx, my,
				() -> sendAction("tp_back", new JsonObject()));
		return y + 28;
	}

	private static ItemStack getClientStackAtSlot(int slot) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || slot < 0) return ItemStack.EMPTY;
		var inv = mc.player.getInventory();
		if (slot < 36) return inv.main.get(slot);
		if (slot < 40) return inv.armor.get(slot - 36);
		if (slot == 40) return inv.offHand.get(0);
		return ItemStack.EMPTY;
	}

	private static void drawModernSlotBox(DrawContext ctx, int x, int y, int w, int h, boolean selected, boolean hovered, int selectedColor) {
		int bg = selected
				? CrimsonTheme.lerpColor(CrimsonTheme.BG_INPUT, selectedColor, 0.22F)
				: (hovered ? CrimsonTheme.BG_CARD_HOVER : CrimsonTheme.BG_INPUT);
		int border = selected ? selectedColor : (hovered ? CrimsonTheme.CRIMSON_BRIGHT : CrimsonTheme.BORDER_SUBTLE);
		CrimsonTheme.drawRoundedRect(ctx, x, y, w, h, 4.0F, bg);
		CrimsonTheme.drawRoundedOutline(ctx, x, y, w, h, 4.0F, 1.0F, border);
	}

	private int drawTargetEnchantShowcase(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		ItemStack targetStack = getClientStackAtSlot(s.selectedEnchantSlot);
		boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, x, y, w, 28);
		CrimsonTheme.drawCard(ctx, x, y, w, 28, hov, targetStack.isEmpty() ? CrimsonTheme.BORDER_SUBTLE : CrimsonTheme.GOLD);

		drawModernSlotBox(ctx, x + 6, y + 4, 20, 20, !targetStack.isEmpty(), hov, CrimsonTheme.GOLD);
		if (!targetStack.isEmpty()) {
			ctx.drawItem(targetStack, x + 8, y + 6);
			ctx.drawItemInSlot(this.textRenderer, targetStack, x + 8, y + 6);

			int slotBadgeW = textRenderer.getWidth(s.targetSlotLabel) + 10;
			CrimsonTheme.drawBadge(ctx, textRenderer, x + w - slotBadgeW - 6, y + 5, s.targetSlotLabel,
					CrimsonTheme.withAlpha(CrimsonTheme.CRIMSON, 64), CrimsonTheme.CRIMSON_BRIGHT, CrimsonTheme.TEXT_SECONDARY);

			ctx.drawTextWithShadow(textRenderer, trimToWidth(targetStack.getName().getString(), w - slotBadgeW - 46), x + 32, y + 5, CrimsonTheme.GOLD);
			if (targetStack.isDamageable()) {
				int rem = targetStack.getMaxDamage() - targetStack.getDamage();
				ctx.drawTextWithShadow(textRenderer, "Durability: " + rem + " / " + targetStack.getMaxDamage(), x + 32, y + 16, CrimsonTheme.TEXT_SECONDARY);
			} else {
				ctx.drawTextWithShadow(textRenderer, "Selected for Enchantment Studio", x + 32, y + 16, CrimsonTheme.TEXT_MUTED);
			}
			if (hov && inRect(mx, my, x + 6, y + 4, 20, 20)) {
				this.hoveredTooltipStack = targetStack;
			}
		} else {
			ctx.drawTextWithShadow(textRenderer, "Carry any weapon, tool, or armor in your Armor / Bag",
					x + 32, y + 10, CrimsonTheme.TEXT_MUTED);
		}
		return y + 32;
	}

	private int drawHeldItemShowcase(DrawContext ctx, ClientStateSnapshot s, int x, int y, int w, int clipTop, int clipBot, int mx, int my) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ItemStack liveStack = (mc.player != null) ? mc.player.getMainHandStack() : ItemStack.EMPTY;
		boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, x, y, w, 28);
		CrimsonTheme.drawCard(ctx, x, y, w, 28, hov, liveStack.isEmpty() ? CrimsonTheme.BORDER_SUBTLE : CrimsonTheme.GOLD);

		drawModernSlotBox(ctx, x + 6, y + 4, 20, 20, !liveStack.isEmpty(), hov, CrimsonTheme.GOLD);
		if (!liveStack.isEmpty()) {
			ctx.drawItem(liveStack, x + 8, y + 6);
			ctx.drawItemInSlot(this.textRenderer, liveStack, x + 8, y + 6);
			ctx.drawTextWithShadow(textRenderer, trimToWidth(liveStack.getName().getString(), w - 42), x + 32, y + 5, CrimsonTheme.GOLD);
			if (liveStack.isDamageable()) {
				int rem = liveStack.getMaxDamage() - liveStack.getDamage();
				ctx.drawTextWithShadow(textRenderer, "Durability: " + rem + " / " + liveStack.getMaxDamage(), x + 32, y + 16, CrimsonTheme.TEXT_SECONDARY);
			} else {
				ctx.drawTextWithShadow(textRenderer, "Ready for Enchantment Studio", x + 32, y + 16, CrimsonTheme.TEXT_MUTED);
			}
			if (hov && inRect(mx, my, x + 6, y + 4, 20, 20)) {
				this.hoveredTooltipStack = liveStack;
			}
		} else {
			ctx.drawTextWithShadow(textRenderer, Text.translatableWithFallback("hc.java.no_item", "Hold a weapon, tool, or armor in mainhand").getString(),
					x + 32, y + 10, CrimsonTheme.TEXT_MUTED);
		}
		return y + 32;
	}

	private int drawToggleCard(DrawContext ctx, int x, int y, int w, String title, boolean state,
							   int clipTop, int clipBot, int mx, int my, Runnable onClick) {
		int h = 20;
		boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, x, y, w, h);
		CrimsonTheme.drawCard(ctx, x, y, w, h, hov, state ? CrimsonTheme.CRIMSON : 0);
		int titleCol = state ? CrimsonTheme.TEXT_PRIMARY : (hov ? CrimsonTheme.TEXT_PRIMARY : CrimsonTheme.TEXT_SECONDARY);
		ctx.drawTextWithShadow(textRenderer, trimToWidth(title, w - 48), x + 8, y + 6, titleCol);
		CrimsonTheme.drawPillSwitch(ctx, x + w - 36, y + 3, state, hov);
		addClippedClick(x, y, w, h, clipTop, clipBot, onClick);
		return y + h + 3;
	}

	private int drawSliderCard(DrawContext ctx, String id, int x, int y, int w, String label, String badge, int value, int min, int max,
							   int clipTop, int clipBot, int mx, int my, IntConsumer onPreview, IntConsumer onRelease) {
		int h = 28;
		boolean draggingThis = (activeSlider != null && activeSlider.id().equals(id));
		boolean hov = draggingThis || (inClip(mx, my, clipTop, clipBot) && inRect(mx, my, x, y, w, h));
		CrimsonTheme.drawCard(ctx, x, y, w, h, hov, CrimsonTheme.CRIMSON_DARK);
		CrimsonTheme.drawSlider(ctx, textRenderer, x + 8, y + 5, w - 16, label, badge, value, min, max, hov);
		if (y + h > clipTop && y < clipBot) {
			int cy1 = Math.max(clipTop, y);
			int cy2 = Math.min(clipBot, y + h);
			sliderZones.add(new SliderZone(id, x + 8, cy1, w - 16, cy2 - cy1, min, max, onPreview, onRelease));
		}
		return y + h + 4;
	}

	private void drawActionBtn(DrawContext ctx, int x, int y, int w, int h, String label, int color,
							   int clipTop, int clipBot, int mx, int my, Runnable onClick) {
		boolean hov = inClip(mx, my, clipTop, clipBot) && inRect(mx, my, x, y, w, h);
		CrimsonTheme.drawButton(ctx, textRenderer, x, y, w, h, label, color, hov);
		addClippedClick(x, y, w, h, clipTop, clipBot, onClick);
	}

	private void addClippedClick(int x, int y, int w, int h, int clipTop, int clipBot, Runnable action) {
		if (y + h <= clipTop || y >= clipBot) return;
		int cy1 = Math.max(clipTop, y);
		int cy2 = Math.min(clipBot, y + h);
		if (cy2 > cy1) {
			clickZones.add(new ClickZone(x, cy1, w, cy2 - cy1, action));
		}
	}

	// ==================== INPUT & SCROLL EVENTS ====================
	@Override
	public void close() {
		closeRitualModal(ClientStateSnapshot.INSTANCE);
		super.close();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		ClientStateSnapshot s = ClientStateSnapshot.INSTANCE;
		if (s.upgraderModalOpen) {
			if (this.primaryInput != null) {
				this.primaryInput.setFocused(false);
			}
			long now = System.currentTimeMillis();
			boolean spinning = now < s.upgraderSpinEndMs || !s.upgraderHasResult;
			if (button == 0) {
				for (int i = clickZones.size() - 1; i >= 0; i--) {
					ClickZone cz = clickZones.get(i);
					if (cz.contains(mouseX, mouseY)) {
						playClickSound(1.0F);
						cz.action().run();
						return true;
					}
				}
				if (!spinning && (now - s.upgraderRevealStartMs) >= 300L) {
					int stageW = Math.min(336, this.width - 20);
					int stageH = Math.min(244, this.height - 14);
					int stageX = (this.width - stageW) / 2;
					int stageY = (this.height - stageH) / 2;
					if (!inRect(mouseX, mouseY, stageX, stageY, stageW, stageH)) {
						playClickSound(0.95F);
						closeRitualModal(s);
					}
				}
			} else if (button == 1 && !spinning && (now - s.upgraderRevealStartMs) >= 300L) {
				playClickSound(0.95F);
				closeRitualModal(s);
			}
			return true;
		}

		if (this.primaryInput != null && this.primaryInput.isVisible()) {
			int ibX = panelX + sidebarW + 10;
			int ibY = panelY + headerH + 7;
			int ibW = (panelW - sidebarW - dockW - 4) - 18;
			if (this.primaryInput.mouseClicked(mouseX, mouseY, button)) {
				this.setFocused(this.primaryInput);
				return true;
			}
			if (inRect(mouseX, mouseY, ibX, ibY, ibW, 18)) {
				this.primaryInput.setFocused(true);
				this.setFocused(this.primaryInput);
				return true;
			}
			this.primaryInput.setFocused(false);
		}

		if (button == 0) {
			for (SliderZone sz : sliderZones) {
				if (sz.contains(mouseX, mouseY)) {
					this.activeSlider = sz;
					this.pendingSliderValue = sz.valueAt(mouseX);
					sz.onPreview().accept(this.pendingSliderValue);
					playClickSound(1.1F);
					return true;
				}
			}
			for (int i = clickZones.size() - 1; i >= 0; i--) {
				ClickZone cz = clickZones.get(i);
				if (cz.contains(mouseX, mouseY)) {
					playClickSound(1.0F);
					cz.action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (ClientStateSnapshot.INSTANCE.upgraderModalOpen) {
			return true;
		}
		if (button == 0 && this.activeSlider != null) {
			this.pendingSliderValue = this.activeSlider.valueAt(mouseX);
			this.activeSlider.onPreview().accept(this.pendingSliderValue);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && this.activeSlider != null) {
			SliderZone finished = this.activeSlider;
			this.activeSlider = null;
			finished.onRelease().accept(this.pendingSliderValue);
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (ClientStateSnapshot.INSTANCE.upgraderModalOpen) {
			return true;
		}
		double step = verticalAmount * 24.0;
		if (inRect(mouseX, mouseY, panelX, panelY + headerH, sidebarW, panelH - headerH)) {
			this.targetSidebarScroll = Math.max(0.0, Math.min(this.maxSidebarScroll, this.targetSidebarScroll - step));
			return true;
		}
		if (inRect(mouseX, mouseY, panelX + sidebarW, panelY + headerH, panelW - sidebarW - dockW, panelH - headerH)) {
			this.targetContentScroll = Math.max(0.0, Math.min(this.maxContentScroll, this.targetContentScroll - step));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		ClientStateSnapshot s = ClientStateSnapshot.INSTANCE;
		if (s.upgraderModalOpen) {
			if (keyCode == 256 || keyCode == 32 || keyCode == 257 || keyCode == 335) {
				long now = System.currentTimeMillis();
				boolean spinning = now < s.upgraderSpinEndMs || !s.upgraderHasResult;
				if (spinning) {
					if (now - s.upgraderSpinStartMs >= 350L) {
						skipRitualToReveal(s);
					}
				} else if (now - s.upgraderRevealStartMs >= 250L) {
					playClickSound(0.95F);
					closeRitualModal(s);
				}
				return true;
			}
			if (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
				return true;
			}
			return true;
		}

		if (this.primaryInput != null && this.primaryInput.isVisible() && this.primaryInput.isFocused()) {
			if (keyCode == 256) { // ESC
				this.primaryInput.setFocused(false);
				return true;
			}
			if (this.primaryInput.keyPressed(keyCode, scanCode, modifiers)) {
				return true;
			}
			// Prevent E (inventory key) from closing screen while typing
			if (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (ClientStateSnapshot.INSTANCE.upgraderModalOpen) {
			return true;
		}
		if (this.primaryInput != null && this.primaryInput.isVisible() && this.primaryInput.isFocused()) {
			if (this.primaryInput.charTyped(chr, modifiers)) {
				return true;
			}
		}
		return super.charTyped(chr, modifiers);
	}

	// ==================== NETWORKING & UTILS ====================
	private static void sendAction(String action, JsonObject obj) {
		if (ClientPlayNetworking.canSend(HardcoreNetworking.ActionC2SPayload.ID)) {
			ClientPlayNetworking.send(new HardcoreNetworking.ActionC2SPayload(action, obj.toString()));
		}
	}

	private static void sendIntAction(String action, int value) {
		JsonObject o = new JsonObject();
		o.addProperty("value", value);
		sendAction(action, o);
	}

	private static void sendRepair(String mode) {
		JsonObject o = new JsonObject();
		o.addProperty("mode", mode);
		sendAction("repair", o);
	}

	private static void sendXp(String op, int amount) {
		JsonObject o = new JsonObject();
		o.addProperty("op", op);
		o.addProperty("amount", amount);
		sendAction("xp_action", o);
	}

	private static void sendQuick(String cmd) {
		JsonObject o = new JsonObject();
		o.addProperty("cmd", cmd);
		sendAction("console_quick", o);
	}

	private static boolean isTabFeatureActive(ClientStateSnapshot s, int tab) {
		return switch (tab) {
			case 0 -> s.immortal || s.autoTotem || s.fullbright || s.returnByDeath;
			case 1 -> s.veinMiner || s.treeCapitator || s.veinAutoSmelt;
			case 2 -> s.sneakGrow;
			case 3 -> s.orePlusEnabled;
			case 4 -> s.bypassEnchantConflicts;
			default -> false;
		};
	}

	private String trimToWidth(String text, int maxW) {
		if (maxW <= 8 || this.textRenderer.getWidth(text) <= maxW) {
			return text;
		}
		return this.textRenderer.trimToWidth(text, maxW - 6) + "..";
	}

	private static String formatIdLabel(String raw) {
		String[] parts = raw.split("_");
		StringBuilder sb = new StringBuilder();
		for (String p : parts) {
			if (p.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
		}
		return sb.toString();
	}

	private static boolean inClip(double mx, double my, int clipTop, int clipBot) {
		return my >= clipTop && my < clipBot;
	}

	private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static void playClickSound(float pitch) {
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, pitch));
	}
}

package bl4ckscor3.wikiizer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.geforcemods.securitycraft.SecurityCraft;
import net.geforcemods.securitycraft.api.ICustomizable;
import net.geforcemods.securitycraft.api.IExplosive;
import net.geforcemods.securitycraft.api.ILockable;
import net.geforcemods.securitycraft.api.IModuleInventory;
import net.geforcemods.securitycraft.api.IOwnable;
import net.geforcemods.securitycraft.api.IPasswordProtected;
import net.geforcemods.securitycraft.api.IViewActivated;
import net.geforcemods.securitycraft.api.Option;
import net.geforcemods.securitycraft.items.SCManualItem;
import net.geforcemods.securitycraft.misc.ModuleType;
import net.geforcemods.securitycraft.misc.SCManualPage;
import net.geforcemods.securitycraft.util.Utils;
import net.minecraft.DetectedVersion;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.client.gui.widget.ExtendedButton;

public class WikiizerScreen extends Screen {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String SC_VERSION = SecurityCraft.getVersion();
	private static final String MC_VERSION = DetectedVersion.BUILT_IN.getName();
	private static final File OUTPUT_FOLDER = new File(Minecraft.getInstance().gameDirectory, "scwikiizer");
	private static final File RESOURCES_FOLDER = new File(OUTPUT_FOLDER, "resources");
	private static final ResourceLocation CRAFTING_GRID_TEXTURE = new ResourceLocation("scwikiizer", "textures/gui/crafting_grid.png");
	//@formatter:off
	private final List<String> homePage = Util.make(new ArrayList<>(), list -> list.addAll(Arrays.asList(
			"# SecurityCraft Wiki",
			"",
			String.format(
					"This wiki is generated from the mod's ingame manual. While it was created using SecurityCraft %s for Minecraft %s, most of the content applies to the mod in other Minecraft versions, or other versions of SecurityCraft.  ",
					SC_VERSION,
					MC_VERSION),
			"All the information in this wiki can also be found in the ingame SecurityCraft Manual. You can get it by crafting a book together with iron bars. Do note, that it may visualize some information (like recipes) better than this wiki.",
			"",
			"## Pages",
			"")));
	//@formatter:on
	private boolean isRunning = false;
	private int currentPage = 0;
	private Button startStopButton;
	private NonNullList<ItemStack> recipeItems;

	public WikiizerScreen() {
		super(new TextComponent("SecurityCraft Wikiizer"));

		SCManualItem.PAGES.sort((page1, page2) -> {
			String key1 = Utils.localize(page1.item().getDescriptionId()).getString();
			String key2 = Utils.localize(page2.item().getDescriptionId()).getString();

			return key1.compareTo(key2);
		});
	}

	@Override
	protected void init() {
		super.init();

		addRenderableWidget(new ExtendedButton(5, 5, 20, 20, new TextComponent("<-"), b -> Minecraft.getInstance().setScreen(null)));
		startStopButton = addRenderableWidget(new ExtendedButton(30, 5, 50, 20, new TextComponent("Start"), b -> changeRunningStatus(!isRunning)));
		reset();
	}

	@Override
	public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
		renderDirtBackground(0);

		super.render(pose, mouseX, mouseY, partialTick);

		if (isRunning) {
			if (currentPage >= SCManualItem.PAGES.size()) {
				finalizeWiki();
				reset();
				changeRunningStatus(false);
			}
			else {
				SCManualPage page = SCManualItem.PAGES.get(currentPage);

				renderRecipe(page, pose);
				createAndSavePage(page);
				currentPage++;
			}
		}

		drawCenteredString(pose, font, title, width / 2, 15, 0xFFFFFF);
	}

	private void renderRecipe(SCManualPage currentPage, PoseStack pose) {
		populateRecipeField(currentPage.item());
		RenderSystem._setShaderTexture(0, CRAFTING_GRID_TEXTURE);
		blit(pose, 100, 100, 0, 0, 126, 64, 126, 64);

		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(recipeItems.get((i * 3 + j)), 106 + j * 18, 106 + i * 18);
			}
		}

		Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(new ItemStack(currentPage.item()), 200, 124);
	}

	private void populateRecipeField(Item item) {
		recipeItems = NonNullList.withSize(9, ItemStack.EMPTY);

		for (Recipe<?> object : Minecraft.getInstance().level.getRecipeManager().getRecipes()) {
			if (object instanceof ShapedRecipe recipe) {
				if (item == recipe.getResultItem().getItem()) {
					NonNullList<Ingredient> ingredients = recipe.getIngredients();

					for (int i = 0; i < ingredients.size(); i++) {
						ItemStack[] items = ingredients.get(i).getItems();

						if (items.length == 0)
							continue;

						recipeItems.set(getCraftMatrixPosition(i, recipe.getWidth(), recipe.getHeight()), items[0]);
					}

					return;
				}
			}
			else if (object instanceof ShapelessRecipe recipe) {
				if (item == recipe.getResultItem().getItem()) {
					//don't show keycard reset recipes
					if (recipe.getId().getPath().endsWith("_reset"))
						continue;

					NonNullList<Ingredient> ingredients = recipe.getIngredients();

					for (int i = 0; i < ingredients.size(); i++) {
						ItemStack[] items = ingredients.get(i).getItems();

						if (items.length == 0)
							continue;

						recipeItems.set(i, items[0]);
					}

					return;
				}
			}
		}
	}

	private void createAndSavePage(SCManualPage currentPage) {
		TranslatableComponent helpInfo = currentPage.helpInfo();
		boolean reinforcedPage = helpInfo.getKey().equals("help.securitycraft:reinforced.info");
		String title = (reinforcedPage ? Utils.localize("gui.securitycraft:scManual.reinforced") : Utils.localize(currentPage.item().getDescriptionId())).getString();
		String description = helpInfo.getString();
		String recipe;

		if (reinforcedPage)
			recipe = Utils.localize("gui.securitycraft:scManual.recipe.reinforced").getString();
		else {
			recipe = currentPage.hasRecipeDescription() ? Utils.localize("gui.securitycraft:scManual.recipe." + currentPage.item().getRegistryName().getPath()).getString() : null;

			if (recipe == null)
				recipe = "![Recipe](" + takeAndSaveRecipeScreenshot(title) + ")";
		}

		homePage.add("- [[" + title + "|" + title + "]]");

		try {
			List<String> lines = new ArrayList<>();

			//@formatter:off
			lines.addAll(Arrays.asList(
					description,
					"",
					"## Recipe",
					"",
					recipe));
			//@formatter:on

			addExtraInfo(currentPage.item(), lines);
			FileUtils.writeLines(new File(OUTPUT_FOLDER, title + ".md"), lines);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String takeAndSaveRecipeScreenshot(String title) {
		String savePath;

		title = title.toLowerCase().replace(" ", "_");
		savePath = "resources/" + title + ".png";
		ScreenshotUtil.grab(new File(OUTPUT_FOLDER, savePath));
		return savePath;
	}

	private void addExtraInfo(Item item, List<String> lines) {
		if (item instanceof BlockItem blockItem) {
			Block block = blockItem.getBlock();
			List<String> properties = new ArrayList<>();

			if (block instanceof IExplosive)
				properties.add(Utils.localize("gui.securitycraft:scManual.explosiveBlock").getString());

			if (block.defaultBlockState().hasBlockEntity()) {
				BlockEntity be = ((EntityBlock) block).newBlockEntity(BlockPos.ZERO, block.defaultBlockState());

				if (be instanceof IOwnable)
					properties.add(Utils.localize("gui.securitycraft:scManual.ownableBlock").getString());

				if (be instanceof IPasswordProtected)
					properties.add(Utils.localize("gui.securitycraft:scManual.passwordProtectedBlock").getString());

				if (be instanceof IViewActivated)
					properties.add(Utils.localize("gui.securitycraft:scManual.viewActivatedBlock").getString());

				if (be instanceof ILockable)
					properties.add(Utils.localize("gui.securitycraft:scManual.lockable").getString());

				if (be instanceof ICustomizable customizableBe && customizableBe.customOptions() != null && customizableBe.customOptions().length > 0) {
					lines.add("");
					lines.add("## Universal Block Modifier Options");
					lines.add("");

					for (Option<?> option : customizableBe.customOptions()) {
						lines.add(new TextComponent("- ").append(Utils.localize("option" + block.getDescriptionId().substring(5) + "." + option.getName() + ".description")).getString());
					}
				}

				if (be instanceof IModuleInventory moduleInv && moduleInv.acceptedModules() != null && moduleInv.acceptedModules().length > 0) {
					lines.add("");
					lines.add("## Accepted Modules");
					lines.add("");

					for (ModuleType module : moduleInv.acceptedModules()) {
						lines.add(new TextComponent("- ").append(Utils.localize("module" + block.getDescriptionId().substring(5) + "." + module.getItem().getDescriptionId().substring(5).replace("securitycraft.", "") + ".description")).getString());
					}
				}
			}

			if (properties.size() > 0) {
				lines.add("");
				lines.add("## Properties");
				lines.add("");
				lines.addAll(properties.stream().map(e -> "- " + e).toList());
			}
		}
	}

	private void finalizeWiki() {
		try {
			FileUtils.writeLines(new File(OUTPUT_FOLDER, "Home.md"), homePage);
			//@formatter:off
			FileUtils.writeLines(new File(OUTPUT_FOLDER, "_Footer.md"), List.of(
					String.format("Generated at `%s` using [SecurityCraft Wikiizer](https://github.com/bl4ckscor3/SecurityCraft-Wikiizer), with SecurityCraft %s and Minecraft %s.",
							new Date(),
							SC_VERSION,
							MC_VERSION)));
			//@formatter:on
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void reset() {
		currentPage = 0;
		OUTPUT_FOLDER.delete();
		OUTPUT_FOLDER.mkdir();
		RESOURCES_FOLDER.mkdir();
	}

	private void changeRunningStatus(boolean shouldBeRunning) {
		isRunning = shouldBeRunning;
		startStopButton.active = !shouldBeRunning;
	}

	//from JEI
	private int getCraftMatrixPosition(int i, int width, int height) {
		int index;

		if (width == 1) {
			if (height == 3)
				index = (i * 3) + 1;
			else if (height == 2)
				index = (i * 3) + 1;
			else
				index = 4;
		}
		else if (height == 1)
			index = i + 3;
		else if (width == 2) {
			index = i;

			if (i > 1) {
				index++;

				if (i > 3)
					index++;
			}
		}
		else if (height == 2)
			index = i + 3;
		else
			index = i;

		return index;
	}
}

package bl4ckscor3.wikiizer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;

import net.geforcemods.securitycraft.SCContent;
import net.geforcemods.securitycraft.SecurityCraft;
import net.geforcemods.securitycraft.api.ICustomizable;
import net.geforcemods.securitycraft.api.IExplosive;
import net.geforcemods.securitycraft.api.ILockable;
import net.geforcemods.securitycraft.api.IModuleInventory;
import net.geforcemods.securitycraft.api.IOwnable;
import net.geforcemods.securitycraft.api.IPasscodeProtected;
import net.geforcemods.securitycraft.api.IViewActivated;
import net.geforcemods.securitycraft.api.Option;
import net.geforcemods.securitycraft.items.SCManualItem;
import net.geforcemods.securitycraft.misc.ModuleType;
import net.geforcemods.securitycraft.misc.PageGroup;
import net.geforcemods.securitycraft.misc.SCManualPage;
import net.geforcemods.securitycraft.util.Utils;
import net.minecraft.DetectedVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.gui.widget.ExtendedButton;

public class WikiizerScreen extends Screen {
	private static final String SC_VERSION = SecurityCraft.getVersion();
	private static final String MC_VERSION = DetectedVersion.BUILT_IN.getName();
	private static final File OUTPUT_FOLDER = new File(Minecraft.getInstance().gameDirectory, "scwikiizer");
	private static final File RESOURCES_FOLDER = new File(OUTPUT_FOLDER, "resources");
	private static final ResourceLocation CRAFTING_GRID_TEXTURE = new ResourceLocation("scwikiizer", "textures/gui/crafting_grid.png");
	private final List<String> pages = new ArrayList<>();
	private boolean isRunning = false;
	private int previousPageIndex = 0;
	private int currentPageIndex = 0;
	private Button startStopButton;
	private SimpleIngredientDisplay[] displays = new SimpleIngredientDisplay[9];
	private SimpleIngredientDisplay resultDisplay;
	private boolean isCreatingGif = false;
	private int currentGroupItemIndex = 0;
	private List<File> gifImageFiles = new ArrayList<>();

	public WikiizerScreen() {
		super(Component.literal("SecurityCraft Wikiizer"));

		SCManualItem.PAGES.sort((page1, page2) -> {
			String key1 = page1.title().getString();
			String key2 = page2.title().getString();

			return key1.compareTo(key2);
		});
	}

	@Override
	protected void init() {
		super.init();

		addRenderableWidget(new ExtendedButton(5, 5, 20, 20, Component.literal("<-"), b -> Minecraft.getInstance().setScreen(null)));
		startStopButton = addRenderableWidget(new ExtendedButton(30, 5, 50, 20, Component.literal("Start"), b -> changeRunningStatus(!isRunning)));

		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				displays[(i * 3) + j] = new SimpleIngredientDisplay(106 + j * 18, 106 + i * 18);
			}
		}

		resultDisplay = new SimpleIngredientDisplay(200, 124);
		reset();
		refreshOutputFolder();
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);

		if (isRunning) {
			if (currentPageIndex >= SCManualItem.PAGES.size()) {
				finalizeWiki();
				reset();
			}
			else {
				SCManualPage currentPage = SCManualItem.PAGES.get(currentPageIndex);

				renderRecipe(currentPage, guiGraphics, mouseX, mouseY, partialTick);

				if (!currentPage.group().hasRecipeGrid()) {
					createAndSavePage(currentPage);
					currentPageIndex++;
				}
				else if (!isCreatingGif)
					initiateGifCreation(currentPage);

				if (isCreatingGif) {
					createRecipeScreenshot(currentPage, "" + currentGroupItemIndex);

					if (++currentGroupItemIndex >= currentPage.group().getItems().getItems().length) {
						createAndSavePage(currentPage);
						currentPageIndex++;
						isCreatingGif = false;
					}
				}
			}
		}

		guiGraphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
	}

	private void renderRecipe(SCManualPage currentPage, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		if (previousPageIndex != currentPageIndex) {
			if (!currentPage.hasRecipeDescription())
				populateRecipeField(currentPage);

			previousPageIndex = currentPageIndex;
		}

		guiGraphics.blit(CRAFTING_GRID_TEXTURE, 100, 100, 0, 0, 126, 64, 126, 64);

		for (SimpleIngredientDisplay display : displays) {
			display.render(guiGraphics);
		}

		resultDisplay.render(guiGraphics);
	}

	private void populateRecipeField(SCManualPage currentPage) {
		NonNullList<Ingredient> recipeIngredients = NonNullList.withSize(9, Ingredient.EMPTY);
		Item item = currentPage.item();
		PageGroup pageGroup = currentPage.group();
		Level level = Minecraft.getInstance().level;
		RegistryAccess registryAccess = level.registryAccess();

		for (SimpleIngredientDisplay display : displays) {
			display.setIngredient(Ingredient.EMPTY);
		}

		resultDisplay.setIngredient(Ingredient.EMPTY);

		if (pageGroup == PageGroup.NONE) {
			for (RecipeHolder<?> recipeHolder : level.getRecipeManager().getRecipes()) {
				if (recipeHolder.value() instanceof ShapedRecipe recipe) {
					if (recipe.getResultItem(registryAccess).getItem() == item) {
						NonNullList<Ingredient> ingredients = recipe.getIngredients();
						NonNullList<Ingredient> recipeItems = NonNullList.<Ingredient>withSize(9, Ingredient.EMPTY);

						for (int i = 0; i < ingredients.size(); i++) {
							recipeItems.set(getCraftMatrixPosition(i, recipe.getWidth(), recipe.getHeight()), ingredients.get(i));
						}

						recipeIngredients = recipeItems;
						break;
					}
				}
				else if (recipeHolder.value() instanceof ShapelessRecipe recipe && recipe.getResultItem(registryAccess).getItem() == item) {
					//don't show keycard reset recipes
					if (recipeHolder.id().getPath().endsWith("_reset"))
						continue;

					NonNullList<Ingredient> recipeItems = NonNullList.<Ingredient>withSize(recipe.getIngredients().size(), Ingredient.EMPTY);

					for (int i = 0; i < recipeItems.size(); i++) {
						recipeItems.set(i, recipe.getIngredients().get(i));
					}

					recipeIngredients = recipeItems;
					break;
				}
			}
		}
		else if (pageGroup.hasRecipeGrid()) {
			Map<Integer, ItemStack[]> recipeStacks = new HashMap<>();
			List<Item> pageItems = Arrays.stream(pageGroup.getItems().getItems()).map(ItemStack::getItem).toList();
			int stacksLeft = pageItems.size();

			for (int i = 0; i < 9; i++) {
				recipeStacks.put(i, new ItemStack[pageItems.size()]);
			}

			for (RecipeHolder<?> recipeHolder : Minecraft.getInstance().level.getRecipeManager().getRecipes()) {
				if (stacksLeft == 0)
					break;

				if (recipeHolder.value() instanceof ShapedRecipe recipe) {
					if (!recipe.getResultItem(registryAccess).isEmpty() && pageItems.contains(recipe.getResultItem(registryAccess).getItem())) {
						NonNullList<Ingredient> ingredients = recipe.getIngredients();

						for (int i = 0; i < ingredients.size(); i++) {
							ItemStack[] items = ingredients.get(i).getItems();

							if (items.length == 0)
								continue;

							int indexToAddAt = pageItems.indexOf(recipe.getResultItem(registryAccess).getItem());

							//first item needs to suffice since multiple recipes are being cycled through
							recipeStacks.get(getCraftMatrixPosition(i, recipe.getWidth(), recipe.getHeight()))[indexToAddAt] = items[0];
						}

						stacksLeft--;
					}
				}
				else if (recipeHolder.value() instanceof ShapelessRecipe recipe && !recipe.getResultItem(registryAccess).isEmpty() && pageItems.contains(recipe.getResultItem(registryAccess).getItem())) {
					//don't show keycard reset recipes
					if (recipeHolder.id().getPath().endsWith("_reset"))
						continue;

					NonNullList<Ingredient> ingredients = recipe.getIngredients();

					for (int i = 0; i < ingredients.size(); i++) {
						ItemStack[] items = ingredients.get(i).getItems();

						if (items.length == 0)
							continue;

						int indexToAddAt = pageItems.indexOf(recipe.getResultItem(registryAccess).getItem());

						//first item needs to suffice since multiple recipes are being cycled through
						recipeStacks.get(i)[indexToAddAt] = items[0];
					}

					stacksLeft--;
				}
			}

			recipeIngredients = NonNullList.withSize(9, Ingredient.EMPTY);

			for (Entry<Integer, ItemStack[]> entry : recipeStacks.entrySet()) {
				int i = entry.getKey();
				ItemStack[] stackArray = entry.getValue();

				recipeIngredients.set(i, Ingredient.of(Arrays.stream(stackArray).map(s -> s == null ? ItemStack.EMPTY : s)));
			}
		}

		if (!recipeIngredients.isEmpty()) {
			for (int i = 0; i < 3; i++) {
				for (int j = 0; j < 3; j++) {
					int index = (i * 3) + j;

					if (index >= recipeIngredients.size())
						displays[index].setIngredient(Ingredient.EMPTY);
					else
						displays[index].setIngredient(recipeIngredients.get(index));
				}
			}

			if (currentPage.group().getItems().isEmpty())
				resultDisplay.setIngredient(Ingredient.of(currentPage.item()));
			else
				resultDisplay.setIngredient(currentPage.group().getItems());
		}
		else {
			for (SimpleIngredientDisplay display : displays) {
				display.setIngredient(Ingredient.EMPTY);
			}

			resultDisplay.setIngredient(Ingredient.EMPTY);
		}
	}

	private void createAndSavePage(SCManualPage currentPage) {
		PageGroup pageGroup = currentPage.group();
		Item item = currentPage.item();
		Component helpInfo = currentPage.helpInfo();
		String title = currentPage.title().getString();
		String description = helpInfo.getString();
		String recipe;

		if (pageGroup == PageGroup.REINFORCED || item == SCContent.REINFORCED_HOPPER.get().asItem())
			recipe = Utils.localize("gui.securitycraft:scManual.recipe.reinforced").getString();
		else if (currentPage.hasRecipeDescription())
			recipe = Utils.localize("gui.securitycraft:scManual.recipe." + BuiltInRegistries.ITEM.getKey(currentPage.item()).getPath()).getString();
		else if (!isCreatingGif)
			recipe = "![Recipe](" + createRecipeScreenshot(currentPage, title) + ")";
		else
			recipe = "![Recipe](" + saveRecipeGif(title) + ")";

		pages.add("- [[" + title + "|" + title + "]]");

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

	private String createRecipeScreenshot(SCManualPage currentPage, String title) {
		String savePath;
		File fileToSaveTo;

		title = title.toLowerCase().replace(" ", "_");

		if (isCreatingGif)
			savePath = currentPage.group().name().toLowerCase() + "/" + title + ".png";
		else
			savePath = title + ".png";

		fileToSaveTo = new File(RESOURCES_FOLDER, savePath);

		if (isCreatingGif)
			gifImageFiles.add(fileToSaveTo);

		ScreenshotUtil.grabScreenshot(fileToSaveTo);
		return "resources/" + savePath;
	}

	private void initiateGifCreation(SCManualPage currentPage) {
		isCreatingGif = true;
		currentGroupItemIndex = 0;
		gifImageFiles = new ArrayList<>();
		new File(RESOURCES_FOLDER, currentPage.group().name().toLowerCase()).mkdir();
	}

	private String saveRecipeGif(String title) {
		String savePath = title.toLowerCase().replace(" ", "_") + ".gif";
		File frameCache = new File(RESOURCES_FOLDER, savePath);

		ScreenshotUtil.createGif(frameCache, new ArrayList<>(gifImageFiles));
		isCreatingGif = false;
		currentGroupItemIndex = 0;
		gifImageFiles.clear();

		try {
			Files.deleteIfExists(frameCache.toPath());
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		return "resources/" + savePath;
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

				if (be instanceof IPasscodeProtected)
					properties.add(Utils.localize("gui.securitycraft:scManual.passcodeProtectedBlock").getString());

				if (be instanceof IViewActivated)
					properties.add(Utils.localize("gui.securitycraft:scManual.viewActivatedBlock").getString());

				if (be instanceof ILockable)
					properties.add(Utils.localize("gui.securitycraft:scManual.lockable").getString());

				if (be instanceof ICustomizable customizableBe && customizableBe.customOptions() != null && customizableBe.customOptions().length > 0) {
					lines.add("");
					lines.add("## Universal Block Modifier Options");
					lines.add("");

					for (Option<?> option : customizableBe.customOptions()) {
						lines.add(Component.translatable("gui.securitycraft:scManual.option_text", Component.translatable(option.getDescriptionKey(block)), option.getDefaultInfo()).getString());
					}
				}

				if (be instanceof IModuleInventory moduleInv && moduleInv.acceptedModules() != null && moduleInv.acceptedModules().length > 0) {
					lines.add("");
					lines.add("## Accepted Modules");
					lines.add("");

					for (ModuleType module : moduleInv.acceptedModules()) {
						lines.add(Component.literal("- ").append(Utils.localize(moduleInv.getModuleDescriptionId(block.getDescriptionId().substring(6), module))).getString());
					}
				}
			}

			if (!properties.isEmpty()) {
				lines.add("");
				lines.add("## Properties");
				lines.add("");
				lines.addAll(properties.stream().map(e -> "- " + e).toList());
			}
		}
	}

	private void finalizeWiki() {
		try {
			//@formatter:off
			List<String> homePage = new ArrayList<>(Arrays.asList(
					"# SecurityCraft Wiki",
					"",
					String.format(
							"This wiki is generated from the mod's ingame manual. While it was created using SecurityCraft %s for Minecraft %s, most of the content applies to the mod in other Minecraft versions, or other versions of SecurityCraft.  ",
							SC_VERSION,
							MC_VERSION),
					"All the information in this wiki can also be found in the ingame SecurityCraft Manual. You can get it by crafting a book together with iron bars. Do note, that it may visualize some information (like recipes) better than this wiki.",
					"",
					"## Pages",
					""));
			homePage.addAll(pages);
			FileUtils.writeLines(new File(OUTPUT_FOLDER, "Home.md"), homePage);
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
		changeRunningStatus(false);
		currentPageIndex = 0;
		previousPageIndex = -1;
		isCreatingGif = false;
		currentGroupItemIndex = 0;
		gifImageFiles.clear();
		pages.clear();
		resultDisplay.setIngredient(Ingredient.EMPTY);

		for (SimpleIngredientDisplay display : displays) {
			display.setIngredient(Ingredient.EMPTY);
		}

		for (PageGroup group : PageGroup.values()) {
			try {
				FileUtils.deleteDirectory(new File(RESOURCES_FOLDER, group.name().toLowerCase()));
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void changeRunningStatus(boolean shouldBeRunning) {
		if (shouldBeRunning)
			refreshOutputFolder();

		isRunning = shouldBeRunning;
		startStopButton.active = !shouldBeRunning;
	}

	private void refreshOutputFolder() {
		try {
			FileUtils.deleteDirectory(OUTPUT_FOLDER);
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		OUTPUT_FOLDER.mkdir();
		RESOURCES_FOLDER.mkdir();
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

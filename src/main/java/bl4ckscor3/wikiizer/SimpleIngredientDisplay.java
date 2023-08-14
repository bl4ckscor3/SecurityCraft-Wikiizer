package bl4ckscor3.wikiizer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public class SimpleIngredientDisplay {
	protected final int x;
	protected final int y;
	protected ItemStack[] stacks;
	protected int currentRenderingStack = 0;

	public SimpleIngredientDisplay(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public void render(GuiGraphics guiGraphics) {
		if (stacks == null || stacks.length == 0)
			return;

		guiGraphics.renderItem(stacks[currentRenderingStack], x, y);
		changeRenderingStack(1);
	}

	public void setIngredient(Ingredient ingredient) {
		stacks = ingredient.getItems();
		currentRenderingStack = 0;
	}

	public void changeRenderingStack(double direction) {
		currentRenderingStack += Math.signum(direction);

		if (currentRenderingStack < 0)
			currentRenderingStack = stacks.length - 1;
		else if (currentRenderingStack >= stacks.length)
			currentRenderingStack = 0;
	}
}

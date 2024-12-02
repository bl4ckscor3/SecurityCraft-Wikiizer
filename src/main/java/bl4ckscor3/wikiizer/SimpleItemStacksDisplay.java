package bl4ckscor3.wikiizer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public class SimpleItemStacksDisplay {
	protected final int x;
	protected final int y;
	private List<ItemStack> stacks = new ArrayList<>();
	protected int currentRenderingStack = 0;

	public SimpleItemStacksDisplay(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public void render(GuiGraphics guiGraphics) {
		if (stacks.isEmpty() || currentRenderingStack < 0 || currentRenderingStack >= stacks.size())
			return;

		guiGraphics.renderItem(stacks.get(currentRenderingStack), x, y);
		changeRenderingStack(1);
	}

	public void setStacks(List<ItemStack> stacks) {
		if (stacks == null)
			stacks = List.of();

		this.stacks = stacks;
		currentRenderingStack = 0;
	}

	public void changeRenderingStack(double direction) {
		currentRenderingStack += Math.signum(direction);

		if (currentRenderingStack < 0)
			currentRenderingStack = stacks.size() - 1;
		else if (currentRenderingStack >= stacks.size())
			currentRenderingStack = 0;
	}
}

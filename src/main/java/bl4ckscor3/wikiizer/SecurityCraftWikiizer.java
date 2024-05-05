package bl4ckscor3.wikiizer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.widget.ExtendedButton;

@Mod("scwikiizer")
@EventBusSubscriber(modid = "scwikiizer", value = Dist.CLIENT)
public class SecurityCraftWikiizer {
	@SubscribeEvent
	public static void onInitScreen(ScreenEvent.Init.Pre event) {
		if (event.getScreen() instanceof ChatScreen screen)
			screen.addRenderableWidget(new ExtendedButton(5, 5, 20, 20, Component.literal("W"), b -> Minecraft.getInstance().setScreen(new WikiizerScreen())));
	}
}

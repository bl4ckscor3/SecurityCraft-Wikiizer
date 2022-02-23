package bl4ckscor3.wikiizer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent.InitScreenEvent;
import net.minecraftforge.client.gui.widget.ExtendedButton;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@Mod("scwikiizer")
@EventBusSubscriber(modid = "scwikiizer", value = Dist.CLIENT)
public class SecurityCraftWikiizer {
	@SubscribeEvent
	public static void onInitScreen(InitScreenEvent event) {
		if (event.getScreen() instanceof ChatScreen screen)
			screen.addRenderableWidget(new ExtendedButton(5, 5, 20, 20, new TextComponent("W"), b -> Minecraft.getInstance().setScreen(new WikiizerScreen())));
	}
}

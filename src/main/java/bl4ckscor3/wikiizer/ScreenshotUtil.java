package bl4ckscor3.wikiizer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

public class ScreenshotUtil {
	private static final Logger LOGGER = LogManager.getLogger();

	private ScreenshotUtil() {}

	public static void grabScreenshot(File target) {
		RenderTarget framebuffer = Minecraft.getInstance().getMainRenderTarget();
		NativeImage img = new NativeImage(framebuffer.width, framebuffer.height, false);

		//takes a screenshot of the whole game screen
		Screenshot.takeScreenshot(framebuffer, i -> {
			int startX = -1;
			int lastGreenX = -1;
			int startY = -1;
			int lastGreenY = -1;

			img.copyFrom(i);

			//the crafting grid texture has green corners. for one, to determine the area of the crafting grid (which is the only part of the image that is needed),
			//but also so the corners can be removed and the resulting image will be fully transparent in those places
			for (int x = 0; x < img.getWidth(); x++) {
				for (int y = 0; y < img.getHeight(); y++) {
					if (img.getPixel(x, y) == 0xFF00FF00) {
						if (startX == -1 && startY == -1) {
							startX = x;
							startY = y;
						}
						else {
							lastGreenX = x;
							lastGreenY = y;
						}

						img.setPixel(x, y, 0x00000000);
					}
				}
			}

			NativeImage copy = new NativeImage(lastGreenX - startX + 1, lastGreenY - startY + 1, false);

			//copy the area with the crafting grid to a new native image to save it
			for (int x = startX; x <= lastGreenX; x++) {
				for (int y = startY; y <= lastGreenY; y++) {
					copy.setPixel(x - startX, y - startY, img.getPixel(x, y));
				}
			}

			Util.ioPool().execute(() -> {
				try {
					copy.writeToFile(target);
				}
				catch (Exception exception) {
					LOGGER.warn("Couldn't save screenshot", exception);
				}
				finally {
					img.close();
					copy.close();
				}
			});
		});
	}

	public static void createGif(File saveLocation, List<File> gifParts) {
		Util.ioPool().execute(() -> {
			GifSequenceWriter writer = null;

			try (ImageOutputStream output = new FileImageOutputStream(saveLocation)) {
				BufferedImage first = ImageIO.read(gifParts.get(0));

				writer = new GifSequenceWriter(output, first.getType(), 1000, true);

				for (File image : gifParts) {
					writer.writeToSequence(ImageIO.read(image));
				}

				writer.save();
			}
			catch (Exception exception) {
				LOGGER.warn("Couldn't save gif", exception);
			}
		});
	}
}

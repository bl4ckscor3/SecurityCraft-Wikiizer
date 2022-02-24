package bl4ckscor3.wikiizer;

import java.awt.image.RenderedImage;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/**
 * @author https://memorynotfound.com/generate-gif-image-java-delay-infinite-loop-example/
 */
public class GifSequenceWriter {
	protected ImageWriter writer;
	protected ImageWriteParam params;
	protected IIOMetadata metadata;

	public GifSequenceWriter(ImageOutputStream out, int imageType, int delay, boolean loop) throws IOException {
		ImageTypeSpecifier imageTypeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(imageType);

		writer = ImageIO.getImageWritersBySuffix("gif").next();
		params = writer.getDefaultWriteParam();
		metadata = writer.getDefaultImageMetadata(imageTypeSpecifier, params);
		configureRootMetadata(delay, loop);
		writer.setOutput(out);
		writer.prepareWriteSequence(null);
	}

	private void configureRootMetadata(int delay, boolean loop) throws IIOInvalidTreeException {
		String metaFormatName = metadata.getNativeMetadataFormatName();
		IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);
		IIOMetadataNode graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");
		IIOMetadataNode appExtensionsNode = getNode(root, "ApplicationExtensions");
		IIOMetadataNode child = new IIOMetadataNode("ApplicationExtension");
		int loopContinuously = loop ? 0 : 1;

		graphicsControlExtensionNode.setAttribute("disposalMethod", "none");
		graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE");
		graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE");
		graphicsControlExtensionNode.setAttribute("delayTime", Integer.toString(delay / 10));
		graphicsControlExtensionNode.setAttribute("transparentColorIndex", "0");
		child.setAttribute("applicationID", "NETSCAPE");
		child.setAttribute("authenticationCode", "2.0");
		child.setUserObject(new byte[] {
				0x1, (byte) (loopContinuously & 0xFF), (byte) ((loopContinuously >> 8) & 0xFF)
		});
		appExtensionsNode.appendChild(child);
		metadata.setFromTree(metaFormatName, root);
	}

	private static IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
		int nNodes = rootNode.getLength();

		for (int i = 0; i < nNodes; i++) {
			if (rootNode.item(i).getNodeName().equalsIgnoreCase(nodeName))
				return (IIOMetadataNode) rootNode.item(i);
		}

		IIOMetadataNode node = new IIOMetadataNode(nodeName);
		rootNode.appendChild(node);
		return node;
	}

	public void writeToSequence(RenderedImage img) throws IOException {
		writer.writeToSequence(new IIOImage(img, null, metadata), params);
	}

	public void save() throws IOException {
		writer.endWriteSequence();
	}
}
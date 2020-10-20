import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TextArea;
import java.awt.color.ColorSpace;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.File;
import java.io.File;
import java.io.IOException;
import java.lang.IllegalArgumentException;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.AbstractAction;

public class BlurNeighbors {
    private static final int MOVING_AVERAGE = 5;
    
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("program [--output %name_blurneighbors.%ext] "
                               +"[--neigbor-weights 0.2,0.5,1.0,0.5,0.2] "
                               +"input1.jpg input2.jpg ...");
            System.exit(1);
        }

        List<Double> neighborWeights = new ArrayList<>();
        neighborWeights.addAll(Arrays.asList(0.2, 0.5, 1.0, 0.5, 0.2));
        List<Path> images = new ArrayList<>();
        String outputFormat = "%name_blurneighbors.%ext";
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--output")) {
                outputFormat = args[++i];
            } else if (arg.equals("--neighbor-weights")) {
                neighborWeights.clear();
                String[] split = args[++i].split(",");
                if (split.length % 2 != 1) {
                    throw new IllegalArgumentException("There must be an odd number ofneighbor-weights");
                }
                for (String w : split) {
                    neighborWeights.add(Double.valueOf(w));
                }
            } else {
                images.add(Paths.get(arg));
            }
        }
        blurNeighbors(images, neighborWeights, outputFormat);
    }

    private static void blurNeighbors(List<Path> images, List<Double> neighborWeights,
                                      String outputFormat) throws IOException {
        Integer width = null;
        Integer height = null;
        for (Path image : images) {
            BufferedImage img = ImageIO.read(image.toFile());
            if (width == null) {
                width = img.getWidth();
                height = img.getHeight();
            } else {
                if (img.getWidth() != width || img.getHeight() != height) {
                    throw new IllegalArgumentException("Images must have the same size");
                }
            }
        }
        
        Map<Path, SoftReference<BufferedImage>> pathToImageRef = new HashMap<>();
        
        int neighbors = neighborWeights.size() / 2;
        for (int i = 0; i < images.size(); i++) {
            Path image = images.get(i);

            LinkedHashMap<BufferedImage, Double> imagesToWeights = new LinkedHashMap<>();
            for (int j = 0; j < neighborWeights.size(); j++) {
                int offset = j - neighborWeights.size() / 2;
                if (i + offset >= 0 && i + offset < images.size()) {
                    SoftReference<BufferedImage> imageRef = pathToImageRef.get(images.get(offset+i));
                    if (imageRef == null || imageRef.get() == null) {
                        BufferedImage img = new BufferedImage(width, height,
                                                              BufferedImage.TYPE_INT_RGB);
                        Graphics2D g2d = img.createGraphics();
                        g2d.drawImage(ImageIO.read(images.get(offset+i).toFile()), 0, 0, null);
                        g2d.dispose();
                        imageRef = new SoftReference<>(img);
                        pathToImageRef.put(images.get(offset+i), imageRef);
                    }
                    imagesToWeights.put(imageRef.get(), neighborWeights.get(j));
                }
            }
            BufferedImage blurredImage = blur(imagesToWeights);
            String f = image.getFileName().toString();
            String ext = f.substring(f.lastIndexOf(".")+1);
            Path outputFile = Paths.get(getOutputFileName(image, outputFormat));
            ImageIO.write(blurredImage, ext.toUpperCase(), outputFile.toFile());
        }
    }

    private static BufferedImage blur(LinkedHashMap<BufferedImage, Double> imagesToWeights) {
        double weightSum = 0.0;
        for (Double weight : imagesToWeights.values()) {
            weightSum += weight;
        }

        BufferedImage firstInput = imagesToWeights.keySet().iterator().next();
        int width = firstInput.getWidth();
        int height = firstInput.getHeight();

        // fast
        float[] rgbPixels = new float[width * height * 3];
        Arrays.fill(rgbPixels, 0.0f);
        int[] inPixels = new int[width*height];
        for (Entry<BufferedImage, Double> entry : imagesToWeights.entrySet()) {
            double weight = entry.getValue() / weightSum;
            
            BufferedImage img2 = new BufferedImage(width, height,
                                                   BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = img2.createGraphics();
            g2d.drawImage(entry.getKey(), 0, 0, null);
            g2d.dispose();
            img2.getRaster().getDataElements(0, 0, width, height, inPixels);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = inPixels[y*width + x];
                    int r = (pixel >> 16) & 0xFF;
                    int g = (pixel >> 8) & 0xFF;
                    int b = (pixel >> 0) & 0xFF;
                        
                    int i = (y*width + x) * 3;
                    rgbPixels[i+0] += r / 255.0f * weight;
                    rgbPixels[i+1] += g / 255.0f * weight;
                    rgbPixels[i+2] += b / 255.0f * weight;
                }
            }
        }
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] imgPixels = new int[width*height];
        img.getRaster().getDataElements(0, 0, width, height, imgPixels);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (y*width + x) * 3;
                int r = (int) Math.round(Math.min(1.0, rgbPixels[i+0]) * 255.0f);
                int g = (int) Math.round(Math.min(1.0, rgbPixels[i+1]) * 255.0f);
                int b = (int) Math.round(Math.min(1.0, rgbPixels[i+2]) * 255.0f);
                imgPixels[y*width + x] = (r << 16) | (g << 8) | b;
            }
        }
        img.getRaster().setDataElements(0, 0, width, height, imgPixels);
        return img;
    }

    private static String getOutputFileName(Path image, String format) {
        String name = image.getFileName().toString();
        String extension = name.substring(name.lastIndexOf(".")+1);
        String nameWithoutExtension = name.substring(0, name.lastIndexOf("."));
        return format.replace("%name", nameWithoutExtension).replace("%ext", extension);
    }
}

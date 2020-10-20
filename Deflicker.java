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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.AbstractAction;

public class Deflicker {
    private static final int MOVING_AVERAGE = 5;
    
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("program [--output %name_deflicker.%ext] [--gui] [--area x,y w,h] "
                               +"input1.jpg input2.jpg ...");
            System.exit(1);
        }

        boolean gui = false;
        Rectangle area = null;
        List<Path> images = new ArrayList<>();
        String outputFormat = "%name_deflicker.%ext";
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--output")) {
                outputFormat = args[++i];
            } else if (arg.equals("--area")) {
                String[] xy = args[++i].split(",");
                String[] wh = args[++i].split(",");
                area = new Rectangle(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]),
                                     Integer.parseInt(wh[0]), Integer.parseInt(wh[1]));
            } else if (arg.equals("--gui")) {
                gui = true;
            } else {
                images.add(Paths.get(arg));
            }
        }
        if (gui) {
            areaSelectorGui(images, area, outputFormat);
        } else {
            deflicker(images, area, outputFormat);
        }
    }

    private static void areaSelectorGui(List<Path> images, Rectangle area, String outputFormat)
        throws IOException {
        JFrame frame = new JFrame();
        BufferedImage img = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = getG2d(img);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        Dimension imagesSize = null;
        for (Path image : images) {
            BufferedImage imageImg = ImageIO.read(image.toFile());
            if (imagesSize == null) {
                imagesSize = new Dimension(imageImg.getWidth(), imageImg.getHeight());
            } else if (imagesSize.width != imageImg.getWidth()
                       || imagesSize.height != imageImg.getHeight()) {
                System.err.println("Input images not same size");
                System.exit(1);
            }
            g2d.drawImage(imageImg,
                          0, 0, img.getWidth(), img.getHeight(),
                          0, 0,  imageImg.getWidth(), imageImg.getHeight(),
                          null);
        }
        double xMul = (double) img.getWidth() / imagesSize.width;
        double yMul = (double) img.getHeight() / imagesSize.height;

        AtomicReference<Rectangle> areaRef = new AtomicReference<Rectangle>(null);
        if (area != null) {
            areaRef.set(new Rectangle((int) (area.x * xMul), (int) (area.y * yMul),
                                      (int) (area.width * xMul), (int) (area.height * yMul)));
        }
        JLabel imgLabel = new JLabel(new ImageIcon(img)) {
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Rectangle r = areaRef.get();
                    if (r != null) {
                        g.setColor(Color.BLUE);
                        g.drawRect(r.x, r.y, r.width, r.height);
                    }
                }
            };
        MouseAdapter mouseListener = new MouseAdapter() {
                MouseEvent start = null;
                public void mousePressed(MouseEvent e) {
                    start = e;
                }

                public void mouseReleased(MouseEvent e) {
                    start = null;
                }

                public void mouseDragged(MouseEvent e) {
                    if (start != null) {
                        areaRef.set(new Rectangle(Math.min(start.getX(), e.getX()),
                                                  Math.min(start.getY(), e.getY()),
                                                  Math.abs(start.getX() - e.getX()),
                                                  Math.abs(start.getY() - e.getY())));
                    }
                    if (areaRef.get() != null) {
                        imgLabel.repaint();
                    }
                }
            };
        imgLabel.addMouseListener(mouseListener);
        imgLabel.addMouseMotionListener(mouseListener);
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("Draw area with mouse"));
        panel.add(imgLabel);
        panel.add(new JButton(new AbstractAction("Run") {
                public void actionPerformed(ActionEvent e) {
                    frame.dispose();
                    try {
                        Rectangle r = areaRef.get();
                        if (r != null) {
                            r = new Rectangle((int) (r.x / xMul), (int) (r.y / yMul),
                                              (int) (r.width / xMul), (int) (r.height / yMul));
                            System.out.println("--area "+r.x+","+r.y+" "+r.width+","+r.height);
                        }
                        deflicker(images, r, outputFormat);
                    } catch (IOException e1) {
                        throw new IllegalStateException(e1);
                    }
                }
            }));

        frame.add(panel);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setVisible(true);
    }

    private static Graphics2D getG2d(BufferedImage img) {
        Graphics2D g2d = img.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                             RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                             RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
                             RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING,
                             RenderingHints.VALUE_DITHER_ENABLE);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                             RenderingHints.VALUE_RENDER_QUALITY);
        return g2d;
    }


    private static void deflicker(List<Path> images, Rectangle area, String outputFormat)
        throws IOException {
        List<Double> luminances = new ArrayList<>();
        for (Path image : images) {
            BufferedImage img = ImageIO.read(image.toFile());
            luminances.add(luminance(img, area));
        }

        printStats(images, luminances);
        
        for (int i = 0; i < images.size(); i++) {
            double avgLuminance = rollingAverage(luminances, i, MOVING_AVERAGE);
            Path image = images.get(i);
            BufferedImage img = ImageIO.read(image.toFile());
            changeBrightness(img, avgLuminance / luminances.get(i));
            String f = image.getFileName().toString();
            String ext = f.substring(f.lastIndexOf(".")+1);
            Path outputFile = Paths.get(getOutputFileName(image, outputFormat));
            ImageIO.write(img, ext.toUpperCase(), outputFile.toFile());
        }
    }

    private static String getOutputFileName(Path image, String format) {
        String name = image.getFileName().toString();
        String extension = name.substring(name.lastIndexOf(".")+1);
        String nameWithoutExtension = name.substring(0, name.lastIndexOf("."));
        return format.replace("%name", nameWithoutExtension).replace("%ext", extension);
    }

    private static void printStats(List<Path> images, List<Double> luminances) {
        double sum = 0.0;
        double min = 1.0;
        double max = 0.0;
        for (double luminance : luminances) {
            sum += luminance;
            min = Math.min(min, luminance);
            max = Math.max(max, luminance);
        }

        double avg = sum / luminances.size();
        System.out.format("Minimum luminance %1.3f%n", min);
        System.out.format("Average luminance %1.3f%n", avg);
        System.out.format("Maximum luminance %1.3f%n", max);
        System.out.println();
        System.out.format("%5s | %6s | %9s | %6s | %s%n",
                          "lumin", "diff", "avg lumin", "diff", "file");
        for (int i = 0; i < images.size(); i++) {
            System.out.format("%5s | ", String.format("%1.3f", luminances.get(i)));
            double luminDiff = (i == 0 ? Double.NaN : luminances.get(i) - luminances.get(i-1));
            System.out.format("%6s | ", String.format("%1.3f", luminDiff));
            double avgLumin = rollingAverage(luminances, i, MOVING_AVERAGE);
            System.out.format("%9s | ", String.format("%1.3f", avgLumin));
            double avgDiff = (i == 0 ? Double.NaN : rollingAverage(luminances, i, MOVING_AVERAGE)
                              - rollingAverage(luminances, i-1, MOVING_AVERAGE));
            System.out.format("%6s | ", String.format("%1.3f", avgDiff));
            System.out.println(images.get(i));
        }
    }

    private static double rollingAverage(List<Double> doubles, int i, int size) {
        int start = Math.max(0, i - size);
        int end = Math.min(doubles.size(), i + size + 1);
        double sum = 0.0;
        for (int j = start; j < end; j++) {
            sum += doubles.get(j);
        }
        return sum / (end - start);
    }

    private static double luminance(BufferedImage img, Rectangle area) {
        if (area == null) {
            area = new Rectangle(0, 0, img.getWidth(), img.getHeight());
        }
        
        double red = 0.0;
        double green = 0.0;
        double blue = 0.0;
        int amount = 0;
        for (int y = area.y; y < area.y + area.height; y++) {
            for (int x = area.x ; x < area.x + area.width; x++) {
                Color color = new Color(img.getRGB(x, y));
                red += color.getRed() / 255.0;
                green += color.getGreen() / 255.0;
                blue += color.getBlue() / 255.0;
                amount++;
            }
        }
        return 0.299 * (red / amount) + 0.587 * (green / amount) + 0.114 * (blue / amount);
    }

    private static void changeBrightness(BufferedImage img, double multiplier) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0 ; x < img.getWidth(); x++) {
                Color color = brightness(new Color(img.getRGB(x, y)), multiplier);
                img.setRGB(x, y, color.getRGB());
            }
        }
    }

    private static Color brightness(Color color, double multiplier) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), new float[3]);
        hsb[2] = (float) Math.min(1.0f, Math.max(0.0f, hsb[2] * multiplier));
        return Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
    }
}

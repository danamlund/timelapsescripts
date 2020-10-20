import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TextArea;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

public class BlurredBorders {
    private static final int MAX_COLORS_EQUALY = 70;
    private static final Color BORDER_COLOR = Color.BLACK;
    private static final int EXTRA_BORDER = 5;
    private static final double MIN_SIDE_BORDERS_PERCENTAGE = 30.0;
    
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("program input.jpg output.jpg");
            System.exit(1);
        }

        blurBorders(Paths.get(args[0]), Paths.get(args[1]), BORDER_COLOR, EXTRA_BORDER);
    }

    public static void blurBorders(Path file, Path outputFile, Color borderColorIn, int extraBorder)
        throws Exception {
        String f = file.getFileName().toString();
        String pre = f.substring(0, f.lastIndexOf("."));
        String type = f.substring(f.lastIndexOf(".")+1).toUpperCase();
        
        BufferedImage img = ImageIO.read(file.toFile());

        blurBorders(img, borderColorIn, extraBorder);

        ImageIO.write(img, type, outputFile.toFile());
    }
    
    public static void blurBorders(BufferedImage img, Color borderColorIn, int extraBorder)
        throws Exception {
        int borderColor = borderColorIn.getRGB();
        Graphics2D g2d = getG2d(img);

        Path2D path = new Path2D.Double();
        for (int y = 0; y < img.getHeight(); y++) {
            Integer border = null;
            for (int x = 0; x < img.getWidth(); x++) {
                if (equaly(img.getRGB(x, y), borderColor)) {
                    border = x;
                } else {
                    break;
                }
            }
            if (border != null) {
                path.append(new Rectangle(0, y, border + extraBorder, 1), false);
            }
        }
        for (int y = 0; y < img.getHeight(); y++) {
            Integer border = null;
            for (int x = img.getWidth()-1; x >= 0; x--) {
                if (equaly(img.getRGB(x, y), borderColor)) {
                    border = x;
                } else {
                    break;
                }
            }
            if (border != null) {
                path.append(new Rectangle(border - extraBorder, y, img.getWidth(), 1), false);
            }
        }
        for (int x = 0; x < img.getWidth(); x++) {
            Integer border = null;
            for (int y = 0; y < img.getHeight(); y++) {
                if (equaly(img.getRGB(x, y), borderColor)) {
                    border = y;
                } else {
                    break;
                }
            }
            if (border != null) {
                path.append(new Rectangle(x, 0, 1, border + extraBorder), false);
            }
        }
        for (int x = 0; x < img.getWidth(); x++) {
            Integer border = null;
            for (int y = img.getHeight() - 1; y >= 0; y--) {
                if (equaly(img.getRGB(x, y), borderColor)) {
                    border = y;
                } else {
                    break;
                }
            }
            if (border != null) {
                path.append(new Rectangle(x, border - extraBorder, 1, img.getHeight()), false);
            }
        }


        Shape borderShape = new Area(path);
        // g2d.setPaint(new Color(255, 0, 0, 50));
        // g2d.fill(borderShape);
        
        Rectangle resizeRectangle = getImageOnlyRectangle(img, borderColor);
        // g2d.setPaint(new Color(0, 255, 0, 50));
        // g2d.fill(resizeRectangle);

        g2d.setClip(borderShape);
        g2d.drawImage(getBackground(img),
                      0, 0, img.getWidth(), img.getHeight(),
                      resizeRectangle.x, resizeRectangle.y,
                      resizeRectangle.x + resizeRectangle.width,
                      resizeRectangle.y + resizeRectangle.height,
                      null);
    }

    private static Rectangle getImageOnlyRectangle(BufferedImage img, int borderColor) {
        Point northWest = new Point(0, 0);
        Point southEast = new Point(img.getWidth() - 1, img.getHeight() - 1);

        boolean moveNorthWest = true;
        boolean moveNorthEast = true;
        boolean moveSouthEast = true;
        boolean moveSouthWest = true;
        
        int minSize = (int) Math.min(img.getWidth(), img.getHeight()) / 2;
        for (int i = 0; i < minSize; i++) {
            int westBorders = 0;
            int westNonBorders = 0;
            int eastBorders = 0;
            int eastNonBorders = 0;
            for (int y = northWest.y; y <= southEast.y; y++) {
                if (equaly(img.getRGB(northWest.x, y), borderColor)) {
                    westBorders++;
                } else {
                    westNonBorders++;
                }
                if (equaly(img.getRGB(southEast.x, y), borderColor)) {
                    eastBorders++;
                } else {
                    eastNonBorders++;
                }
            }
            int northBorders = 0;
            int northNonBorders = 0;
            int southBorders = 0;
            int southNonBorders = 0;
            for (int x = northWest.x; x <= southEast.x; x++) {
                if (equaly(img.getRGB(x, northWest.y), borderColor)) {
                    northBorders++;
                } else {
                    northNonBorders++;
                }
                if (equaly(img.getRGB(x, southEast.y), borderColor)) {
                    southBorders++;
                } else {
                    southNonBorders++;
                }
            }

            boolean northIsBorder =
                100.0 * northBorders / (northBorders + northNonBorders) > MIN_SIDE_BORDERS_PERCENTAGE;
            boolean southIsBorder =
                100.0 * southBorders / (southBorders + southNonBorders) > MIN_SIDE_BORDERS_PERCENTAGE;
            boolean westIsBorder =
                100.0 * westBorders / (westBorders + westNonBorders) > MIN_SIDE_BORDERS_PERCENTAGE;
            boolean eastIsBorder =
                100.0 * eastBorders / (eastBorders + eastNonBorders) > MIN_SIDE_BORDERS_PERCENTAGE;

            if (northIsBorder) {
                northWest.translate(0, 1);
            }
            if (southIsBorder) {
                southEast.translate(0, -1);
            }
            if (westIsBorder) {
                northWest.translate(1, 0);
            }
            if (eastIsBorder) {
                southEast.translate(-1, 0);
            }

            if (!northIsBorder && !eastIsBorder && !southIsBorder && !westIsBorder) {
                break;
            }
        }

        return new Rectangle(northWest.x, northWest.y,
                             southEast.x - northWest.x, southEast.y - northWest.y);
    }

    public static void expandBorders(Path file) throws Exception {
        int borderColor = Color.BLACK.getRGB();
        int extraBorder = 10;

        final String type;
        final Path outputFile;
        {
            String f = file.toString();
            String pre = f.substring(0, f.lastIndexOf("."));
            type = f.substring(f.lastIndexOf(".")+1).toUpperCase();
            outputFile = Paths.get(pre+"_output."+type.toLowerCase());
        }

        BufferedImage img = ImageIO.read(file.toFile());
        Graphics2D g2d = getG2d(img);

        Path2D path = new Path2D.Double();
        for (int y = 0; y < img.getHeight(); y++) {
            Integer border = null;
            for (int x = 0; x < img.getWidth(); x++) {
                if (equaly(img.getRGB(x, y), borderColor)) {
                    border = x;
                } else {
                    break;
                }
            }
            if (border != null && border + 2*extraBorder < img.getWidth()) {
                int[] rgbs = new int[extraBorder];
                for (int i = 0; i < extraBorder; i++) {
                    rgbs[i] = img.getRGB(border + extraBorder + i, y);
                }
                g2d.setColor(avgColors(rgbs));
                g2d.fill(new Rectangle(0, y, border + extraBorder, 1));
            }
        }
        for (int y = 0; y < img.getHeight(); y++) {
            Integer border = null;
            for (int x = img.getWidth()-1; x >= 0; x--) {
                if (equaly(img.getRGB(x, y), borderColor)) {
                    border = x;
                } else {
                    break;
                }
            }
            if (border != null && border - 2*extraBorder >= 0) {
                int[] rgbs = new int[extraBorder];
                for (int i = 0; i < extraBorder; i++) {
                    rgbs[i] = img.getRGB(border - extraBorder - i, y);
                }
                g2d.setColor(avgColors(rgbs));
                g2d.fill(new Rectangle(border - extraBorder, y, img.getWidth(), 1));
            }
        }
        for (int x = 0; x < img.getWidth(); x++) {
            Integer border = null;
            for (int y = 0; y < img.getHeight(); y++) {
                if (equaly(img.getRGB(x, y), borderColor)) {
                    border = y;
                } else {
                    break;
                }
            }
            if (border != null && border + 2*extraBorder < img.getHeight()) {
                int[] rgbs = new int[extraBorder];
                for (int i = 0; i < extraBorder; i++) {
                    rgbs[i] = img.getRGB(x, border + extraBorder + i);
                }
                g2d.setColor(avgColors(rgbs));
                g2d.fill(new Rectangle(x, 0, 1, border + extraBorder));
            }
        }
        for (int x = 0; x < img.getWidth(); x++) {
            Integer border = null;
            for (int y = img.getHeight() - 1; y >= 0; y--) {
                if (equaly(img.getRGB(x, y), borderColor)) {
                    border = y;
                } else {
                    break;
                }
            }
            if (border != null && border - 2*extraBorder >= 0) {
                int[] rgbs = new int[extraBorder];
                for (int i = 0; i < extraBorder; i++) {
                    rgbs[i] = img.getRGB(x, border - extraBorder - i);
                }
                g2d.setColor(avgColors(rgbs));
                g2d.fill(new Rectangle(x, border - extraBorder, 1, img.getHeight()));
            }
        }

        ImageIO.write(img, type, outputFile.toFile());
    }

    private static Color avgColors(int... rgbs) {
        float red = 0;
        float green = 0;
        float blue = 0;
        for (int rgb : rgbs) {
            Color color = new Color(rgb);
            red += color.getRed()/255.0f;
            green += color.getGreen()/255.0f;
            blue += color.getBlue()/255.0f;
        }
        return new Color(red / rgbs.length, green / rgbs.length, blue / rgbs.length);
    }
    
    private static BufferedImage getBackground(BufferedImage input) {
        return blur2(copy(input));
    }
    
    private static BufferedImage copy(BufferedImage input) {
        BufferedImage img = new BufferedImage(input.getWidth(), input.getHeight(),
                                              BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = getG2d(img);
        g2d.drawImage(input, 0, 0, null);
        g2d.dispose();
        return img;
    }

    private static BufferedImage blur(BufferedImage in) {
        int r = 10;
        BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(),
                                              BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = getG2d(out);

        for (int y = 0; y < in.getHeight(); y++) {
            for (int x = 0; x < in.getWidth(); x++) {
                float red = 0;
                float green = 0;
                float blue = 0;
                int amount = 0;
                for (int by = y-r; by <= y+r; by++) {
                    for (int bx = x-r; bx <= x+r; bx++) {
                        if (bx >= 0 && bx < in.getWidth() && by >= 0 && by < in.getHeight()) {
                            amount++;
                            Color color = new Color(in.getRGB(bx, by));
                            red += color.getRed() / 255.0f;
                            green += color.getGreen() / 255.0f;
                            blue += color.getBlue() / 255.0f;
                        }
                    }
                }
                Color color = new Color((float) red / amount,
                                        (float) green / amount,
                                        (float) blue / amount);
                out.setRGB(x, y, color.getRGB());
            }
        }
        return out;
    }

    private static BufferedImage blur2(BufferedImage in) {
        int r = 10;
        BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(),
                                              BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = getG2d(out);

        for (int y = 0; y < in.getHeight(); y++) {
            float red = 0;
            float green = 0;
            float blue = 0;
            int amount = 0;

            for (int yy = y - r; yy <= y + r; yy++) {
                for (int xx = 0; xx < r; xx++) {
                    if (xx < in.getWidth() && yy >= 0 && yy < in.getHeight()) {
                        amount++;
                        Color color = new Color(in.getRGB(xx, yy));
                        red += color.getRed() / 255.0f;
                        green += color.getGreen() / 255.0f;
                        blue += color.getBlue() / 255.0f;
                    }
                }
            }
            
            for (int x = 0; x < in.getWidth(); x++) {
                int oldx = x - r - 1;
                if (oldx >= 0) {
                    for (int yy = y - r; yy <= y + r; yy++) {
                        if (yy >= 0 && yy < in.getHeight()) {
                            Color color = new Color(in.getRGB(oldx, yy));
                            amount--;
                            red -= color.getRed() / 255.0f;
                            green -= color.getGreen() / 255.0f;
                            blue -= color.getBlue() / 255.0f;
                        }
                    }
                }
                int newx = x + r;
                if (newx < in.getWidth()) {
                    for (int yy = y - r; yy <= y + r; yy++) {
                        if (yy >= 0 && yy < in.getHeight()) {
                            Color color = new Color(in.getRGB(newx, yy));
                            amount++;
                            red += color.getRed() / 255.0f;
                            green += color.getGreen() / 255.0f;
                            blue += color.getBlue() / 255.0f;
                        }
                    }
                }

                Color color = new Color((float) Math.max(0.0, Math.min(1.0, red / amount)),
                                        (float) Math.max(0.0, Math.min(1.0, green / amount)),
                                        (float) Math.max(0.0, Math.min(1.0, blue / amount)));
                out.setRGB(x, y, color.getRGB());
            }
        }
        return out;
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
    
    private static boolean equaly(int aInt, int bInt) {
        Color a = new Color(aInt);
        Color b = new Color(bInt);
        int diff = 0;
        diff += Math.abs(a.getRed() - b.getRed());
        diff += Math.abs(a.getGreen() - b.getGreen());
        diff += Math.abs(a.getBlue() - b.getBlue());
        
        return diff < MAX_COLORS_EQUALY;
    }
}

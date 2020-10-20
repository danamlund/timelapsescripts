import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

public class TimelapseVideo {
    private static final float FONT_SIZE = 24.0f;
    private static final float LINE_WIDTH = 2.0f;
    private final List<Path> inputs;
    private final Path output;
    private final int outWidth;
    private final int outHeight;
    private final String type;
    private final TreeMap<LocalDateTime, Path> dateToPath;
    private final LocalDateTime from;
    private final LocalDateTime to;

    public TimelapseVideo(List<Path> inputs, Path output) throws IOException{
        this.inputs = inputs;
        this.output = output;

        BufferedImage firstImage = ImageIO.read(inputs.get(0).toFile());
        this.outWidth = firstImage.getWidth();
        this.outHeight = firstImage.getHeight();
        String f = inputs.get(0).getFileName().toString();
        this.type = f.substring(f.lastIndexOf(".")+1).toLowerCase();
        
        this.dateToPath = new TreeMap<>();
        for (Path input : inputs) {
            LocalDateTime date = new ExifDateTime(input).dateTime();
            this.dateToPath.put(date, input);
        }
        this.from = dateToPath.firstKey();
        this.to = dateToPath.lastKey();
        System.out.println("## " + this.from + " -> " + this.to);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("./prog [--one-per-day] [--output output/] input1.jpg input2.jpg ...");
            System.exit(1);
        }

        boolean onePerDay = false;
        List<Path> inputs = new ArrayList<>();
        Path output = Paths.get("");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--output")) {
                i++;
                output = Paths.get(args[i]);
            } else if (args[i].equals("--one-per-day")) {
                onePerDay = true;
            } else {
                inputs.add(Paths.get(args[i]));
            }
        }

        new TimelapseVideo(inputs, output).timelapse(onePerDay);
        return;
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
            BufferedImage img2 = entry.getKey();
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

    private static class MyImage {
        public final BufferedImage img;
        public final Graphics2D g2d;
        public MyImage(int width, int height) {
            this.img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            this.g2d = img.createGraphics();

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

            g2d.setFont(g2d.getFont().deriveFont(FONT_SIZE));
            g2d.setStroke(new BasicStroke(LINE_WIDTH));
        }
    }

    public void timelapse(boolean onePerDay) throws IOException {
        Path prevFile = null;
        LocalDateTime prevDate = null;
        int frameNumber = 1;
        for (Entry<LocalDateTime, Path> entry : dateToPath.entrySet()) {
            LocalDateTime date = entry.getKey();
            Path file = entry.getValue();
            if (onePerDay) {
                if (prevDate != null && date.toLocalDate().equals(prevDate.toLocalDate())) {
                    continue;
                }
                if (prevDate != null) {
                    long daysBetween = Duration.between(prevDate, date).toDays() - 1;
                    if (daysBetween >= 1) {
                        BufferedImage prevImg = getRgbImage(prevFile);
                        BufferedImage curImg = getRgbImage(file);
                        for (int i = 0; i < daysBetween; i++) {
                            LinkedHashMap<BufferedImage, Double> imagesToWeights = new LinkedHashMap<>();
                            double weight = (1.0+i) / (1.0+daysBetween);
                            imagesToWeights.put(prevImg, 1.0 - weight);
                            imagesToWeights.put(curImg, weight);
                            BufferedImage middleImg = blur(imagesToWeights);
                            LocalDateTime middleDate = prevDate.plusDays(i+1);
                            makeFrame(frameNumber++, middleImg, middleDate);
                            System.out.println("## "+middleDate+" = "+prevFile+".."+file);
                        }
                    }
                }
            }

            makeFrame(frameNumber++, ImageIO.read(file.toFile()), date);
            System.out.println("## " +date + " = " +file);
            
            prevFile = file;
            prevDate = date;
        }
    }

    private void makeFrame(int frameNumber, BufferedImage img, LocalDateTime date) throws IOException {
        MyImage image = new MyImage(outWidth, outHeight);
        Graphics2D g2d = image.g2d;

        g2d.drawImage(img,
                      0, 0, outWidth, outHeight,
                      0, 0, img.getWidth(), img.getHeight(),
                      null);

        printTimeline(from, to, date,
                      6.0, 200,
                      g2d, 0, outHeight, outWidth, true);

        ImageIO.write(image.img, type,
                      output.resolve(String.format("output%05d."+type, frameNumber)).toFile());
    }
    
    private static BufferedImage getRgbImage(Path file) throws IOException {
         BufferedImage raw = ImageIO.read(file.toFile());
         if (raw.getType() == BufferedImage.TYPE_INT_RGB) {
             return raw;
         }
         BufferedImage img = new BufferedImage(raw.getWidth(), raw.getHeight(),
                                               BufferedImage.TYPE_INT_RGB);
         Graphics2D g2d = img.createGraphics();
         g2d.drawImage(raw, 0, 0, null);
         g2d.dispose();
         return img;
     }

    private static void printTimeline(LocalDateTime from, LocalDateTime to, LocalDateTime lineDate,
                                      double zoomAmount, int zoomWidth,
                                      Graphics2D g2d, int x, int y, int width, boolean aboveY) {
        FontMetrics metrics = g2d.getFontMetrics(g2d.getFont());

        int tickHeight = metrics.getHeight() / 2;
        int textMargin = 2;

        int height = (metrics.getMaxDescent() - 1) * 3 + (metrics.getHeight() - 2) * 3 + tickHeight;
        if (aboveY) {
            y -= height;
        }
        Shape oldClip = g2d.getClip();
        g2d.setClip(x, y, width, height);

        int yearBoxY = y + height - 1;
        int yearY = yearBoxY - 1 - metrics.getMaxDescent();
        int monthBoxY = yearBoxY - 2 - metrics.getHeight();
        int monthY = monthBoxY - 1 - metrics.getMaxDescent();
        int dayBoxY = monthBoxY - 2 - metrics.getHeight();
        int dayY = dayBoxY - 1 - metrics.getMaxDescent();
        int hourBoxY = dayBoxY - 2 - metrics.getHeight();
        int hourY = hourBoxY - 1 - metrics.getMaxDescent();
        int topBoxY = dayBoxY - 2 - metrics.getHeight();

        g2d.drawLine(x, yearBoxY, x + width, yearBoxY);
        g2d.drawLine(x, monthBoxY, x + width, monthBoxY);
        g2d.drawLine(x, dayBoxY, x + width, dayBoxY);
        g2d.drawLine(x, topBoxY, x + width, topBoxY);

        long fromDays = from.getLong(ChronoField.EPOCH_DAY);
        long toDays = to.getLong(ChronoField.EPOCH_DAY);
        long lineDateDays = lineDate.getLong(ChronoField.EPOCH_DAY);
        long totalDays = toDays - fromDays + 1;
        long lineDays = lineDateDays - fromDays;
        double dayRatio = (double) lineDate.get(ChronoField.MILLI_OF_DAY)
            / ((24 * 60 * 60 * 1_000) - 1);
        double lineXDouble = x + (double) width * (lineDays + dayRatio) / totalDays;
        int lineX = (int) Math.round(lineXDouble);
        double zoomX = lineXDouble;
        class M {
            int x(double x) {
                if (x < zoomX - zoomWidth || x > zoomX + zoomWidth) {
                    return (int) Math.round(x);
                } else {
                    double in = ((double) zoomWidth - Math.abs(x - zoomX)) / zoomWidth;
                    double out = 1.0 - Math.pow(in, zoomAmount);
                    if (x < zoomX) {
                        out *= -1.0;
                    }
                    return (int) Math.round(zoomX + out * zoomWidth);
                }
            }
        }
        M m = new M();

        {
            boolean printedDays = false;
            LocalDateTime date = from.minusDays(1);
            LocalDateTime toDay = to.plusDays(1);
            while (date.compareTo(toDay) <= 0) {
                long dateDays = ChronoUnit.DAYS.between(from, date.plusDays(1));
                int dayX = m.x(x + (double) width * dateDays / totalDays);
                long nextDayDays = dateDays + 1;
                int nextDayX = m.x(x + (double) width * nextDayDays / totalDays);

                g2d.drawLine(dayX, dayBoxY, dayX, topBoxY);
                date = date.plusDays(1);
                String str = DateTimeFormatter.ofPattern("d").format(date);
                if (metrics.stringWidth(str) < nextDayX - dayX - textMargin) {
                    int textX = (int) Math.round(dayX
                                                 + ((nextDayX - dayX)/2.0)
                                                 - (metrics.stringWidth(str)/2.0));
                    g2d.drawString(str, textX, dayY);
                    if (textX >= x && textX < width) {
                        printedDays = true;
                    }
                }
            }
            if (!printedDays) {
                String str = DateTimeFormatter.ofPattern("d").format(from);
                int textX = (int) Math.round(x
                                             + (width/2.0)
                                             - (metrics.stringWidth(str)/2.0));
                g2d.drawString(str, textX, dayY);
            }
        }
        {
            boolean printedMonths = false;
            LocalDateTime date = LocalDate.of(from.getYear(), from.getMonth(), 1).minusMonths(1)
                .atStartOfDay();
            LocalDateTime toMonth = LocalDate.of(to.getYear(), to.getMonth(), 1).plusMonths(1)
                .atStartOfDay();
            while (date.compareTo(toMonth) <= 0) {
                long dateDays = ChronoUnit.DAYS.between(from, date.plusDays(1));
                int monthX = m.x(x + (double) width * dateDays / totalDays);
                long nextMonthDays = ChronoUnit.DAYS.between(from, date.plusMonths(1).plusDays(1));
                int nextMonthX = m.x(x + (double) width * nextMonthDays / totalDays);

                g2d.drawLine(monthX, monthBoxY, monthX, dayBoxY);
                String str = DateTimeFormatter.ofPattern("MMM").format(date);
                if (metrics.stringWidth(str) < nextMonthX - monthX - textMargin) {
                    int textX = (int) Math.round(monthX
                                                 + (nextMonthX - monthX) / 2.0
                                                 - metrics.stringWidth(str) / 2.0);
                    g2d.drawString(str, textX, monthY);
                    if (textX >= x && textX < width) {
                        printedMonths = true;
                    }
                }
                date = date.plusMonths(1);
            }
            if (!printedMonths) {
                String str = DateTimeFormatter.ofPattern("MMM").format(from);
                int textX = (int) Math.round(x
                                             + (width/2.0)
                                             - (metrics.stringWidth(str)/2.0));
                g2d.drawString(str, textX, monthY);
            }
        }
        {
            boolean printedYears = false;
            LocalDateTime date = LocalDate.of(from.getYear(), 1, 1).minusYears(1).atStartOfDay();
            LocalDateTime toYear = LocalDate.of(to.getYear(), 1, 1).plusYears(1).atStartOfDay();
            while (date.compareTo(toYear) <= 0) {
                long dateDays = ChronoUnit.DAYS.between(from, date.plusDays(1));
                int yearX = m.x(x + (double) width * dateDays / totalDays);
                long nextYearDays = ChronoUnit.DAYS.between(from, date.plusYears(1).plusDays(1));
                int nextYearX = m.x(x + (double) width * nextYearDays / totalDays);

                g2d.drawLine(yearX, yearBoxY, yearX, monthBoxY);
                String str = DateTimeFormatter.ofPattern("YYYY").format(date);
                if (metrics.stringWidth(str) < nextYearX - yearX - textMargin) {
                    int textX = (int) Math.round(yearX
                                                 + ((nextYearX - yearX)/2.0)
                                                 - (metrics.stringWidth(str)/2.0));
                    g2d.drawString(str, textX, yearY);
                    if (textX >= x && textX < width) {
                        printedYears = true;
                    }
                }
                date = date.plusYears(1);
            }
            if (!printedYears) {
                String str = DateTimeFormatter.ofPattern("YYYY").format(from);
                int textX = (int)Math.round(x
                                            + (width/2.0)
                                            - (metrics.stringWidth(str)/2.0));
                g2d.drawString(str, textX, yearY);
            }
        }

        g2d.drawLine(lineX, topBoxY, lineX, y);
        g2d.setClip(oldClip);
    }

    private static void printTimeline(LocalDate from, LocalDate to, LocalDate lineDate,
                                      double zoomAmount, int zoomWidth,
                                      Graphics2D g2d, int x, int y, int width, boolean aboveY) {
        FontMetrics metrics = g2d.getFontMetrics(g2d.getFont());

        int tickHeight = metrics.getHeight() / 2;
        int textMargin = 2;

        int height = (metrics.getMaxDescent() - 1) * 3 + (metrics.getHeight() - 2) * 3 + tickHeight;
        if (aboveY) {
            y -= height;
        }
        Shape oldClip = g2d.getClip();
        g2d.setClip(x, y, width, height);

        int yearBoxY = y + height - 1;
        int yearY = yearBoxY - 1 - metrics.getMaxDescent();
        int monthBoxY = yearBoxY - 2 - metrics.getHeight();
        int monthY = monthBoxY - 1 - metrics.getMaxDescent();
        int dayBoxY = monthBoxY - 2 - metrics.getHeight();
        int dayY = dayBoxY - 1 - metrics.getMaxDescent();
        int hourBoxY = dayBoxY - 2 - metrics.getHeight();
        int hourY = hourBoxY - 1 - metrics.getMaxDescent();
        int topBoxY = hourBoxY - 2 - metrics.getHeight();

        g2d.drawLine(x, yearBoxY, x + width, yearBoxY);
        g2d.drawLine(x, monthBoxY, x + width, monthBoxY);
        g2d.drawLine(x, dayBoxY, x + width, dayBoxY);
        g2d.drawLine(x, topBoxY, x + width, topBoxY);

        long totalDays = ChronoUnit.DAYS.between(from, to.plusDays(1));

        long lineDays = ChronoUnit.DAYS.between(from, lineDate);
        double lineXDouble = x + (double) width * (lineDays + 0.5) / totalDays;
        int lineX = (int) Math.round(lineXDouble);
        double zoomX = lineXDouble;
        class M {
            int x(double x) {
                if (x < zoomX - zoomWidth || x > zoomX + zoomWidth) {
                    return (int) Math.round(x);
                } else {
                    double in = ((double) zoomWidth - Math.abs(x - zoomX)) / zoomWidth;
                    double out = 1.0 - Math.pow(in, zoomAmount);
                    if (x < zoomX) {
                        out *= -1.0;
                    }
                    return (int) Math.round(zoomX + out * zoomWidth);
                }
            }
        }
        M m = new M();

        {
            boolean printedDays = false;
            LocalDate date = from.minusDays(1);
            LocalDate toDay = to.plusDays(1);
            while (date.compareTo(toDay) <= 0) {
                long dateDays = ChronoUnit.DAYS.between(from, date.plusDays(1));
                int dayX = m.x(x + (double) width * dateDays / totalDays);
                long nextDayDays = dateDays + 1;
                int nextDayX = m.x(x + (double) width * nextDayDays / totalDays);

                g2d.drawLine(dayX, dayBoxY, dayX, topBoxY);
                date = date.plusDays(1);
                String str = DateTimeFormatter.ofPattern("d").format(date);
                if (metrics.stringWidth(str) < nextDayX - dayX - textMargin) {
                    int textX = (int) Math.round(dayX
                                                 + ((nextDayX - dayX)/2.0)
                                                 - (metrics.stringWidth(str)/2.0));
                    g2d.drawString(str, textX, dayY);
                    if (textX >= x && textX < width) {
                        printedDays = true;
                    }
                }
            }
            if (!printedDays) {
                String str = DateTimeFormatter.ofPattern("d").format(from);
                int textX = (int) Math.round(x
                                             + (width/2.0)
                                             - (metrics.stringWidth(str)/2.0));
                g2d.drawString(str, textX, dayY);
            }
        }
        {
            boolean printedMonths = false;
            LocalDate date = LocalDate.of(from.getYear(), from.getMonth(), 1).minusMonths(1);
            LocalDate toMonth = LocalDate.of(to.getYear(), to.getMonth(), 1).plusMonths(1);
            while (date.compareTo(toMonth) <= 0) {
                long dateDays = ChronoUnit.DAYS.between(from, date.plusDays(1));
                int monthX = m.x(x + (double) width * dateDays / totalDays);
                long nextMonthDays = ChronoUnit.DAYS.between(from, date.plusMonths(1).plusDays(1));
                int nextMonthX = m.x(x + (double) width * nextMonthDays / totalDays);

                g2d.drawLine(monthX, monthBoxY, monthX, dayBoxY);
                String str = DateTimeFormatter.ofPattern("MMM").format(date);
                if (metrics.stringWidth(str) < nextMonthX - monthX - textMargin) {
                    int textX = (int) Math.round(monthX
                                                 + (nextMonthX - monthX) / 2.0
                                                 - metrics.stringWidth(str) / 2.0);
                    g2d.drawString(str, textX, monthY);
                    if (textX >= x && textX < width) {
                        printedMonths = true;
                    }
                }
                date = date.plusMonths(1);
            }
            if (!printedMonths) {
                String str = DateTimeFormatter.ofPattern("MMM").format(from);
                int textX = (int) Math.round(x
                                             + (width/2.0)
                                             - (metrics.stringWidth(str)/2.0));
                g2d.drawString(str, textX, monthY);
            }
        }
        {
            boolean printedYears = false;
            LocalDate date = LocalDate.of(from.getYear(), 1, 1).minusYears(1);
            LocalDate toYear = LocalDate.of(to.getYear(), 1, 1).plusYears(1);
            while (date.compareTo(toYear) <= 0) {
                long dateDays = ChronoUnit.DAYS.between(from, date.plusDays(1));
                int yearX = m.x(x + (double) width * dateDays / totalDays);
                long nextYearDays = ChronoUnit.DAYS.between(from, date.plusYears(1).plusDays(1));
                int nextYearX = m.x(x + (double) width * nextYearDays / totalDays);

                g2d.drawLine(yearX, yearBoxY, yearX, monthBoxY);
                String str = DateTimeFormatter.ofPattern("YYYY").format(date);
                if (metrics.stringWidth(str) < nextYearX - yearX - textMargin) {
                    int textX = (int) Math.round(yearX
                                                 + ((nextYearX - yearX)/2.0)
                                                 - (metrics.stringWidth(str)/2.0));
                    g2d.drawString(str, textX, yearY);
                    if (textX >= x && textX < width) {
                        printedYears = true;
                    }
                }
                date = date.plusYears(1);
            }
            if (!printedYears) {
                String str = DateTimeFormatter.ofPattern("YYYY").format(from);
                int textX = (int)Math.round(x
                                            + (width/2.0)
                                            - (metrics.stringWidth(str)/2.0));
                g2d.drawString(str, textX, yearY);
            }
        }

        g2d.drawLine(lineX, topBoxY, lineX, y);
        g2d.setClip(oldClip);
    }

    private static class ExifDateTime {
        private final Path file;

        public static LocalDateTime of(Path file) {
            return new ExifDateTime(file).dateTime();
        }

        public ExifDateTime(Path file) {
            this.file = file;
        }

        public LocalDateTime dateTime() {
            // https://www.media.mit.edu/pia/Research/deepview/exif.html
            try (RandomAccessFile randomAccess = new RandomAccessFile(file.toFile(), "r")) {
                MotorolaReader in = new MotorolaReader(randomAccess);
                if (in.readUnsignedShort() != 0xFFD8) {
                    throw new IllegalArgumentException("Invalid jpg");
                }

                while (true) {
                    if (in.readUnsignedByte() != 0xFF) {
                        throw new IllegalArgumentException("Invald jpg 2");
                    }
                    int marker = in.readUnsignedByte();
                    int length = in.readUnsignedShort();
                    if (marker == 0xE1) {
                        if (!in.readString(4).equals("Exif")) {
                            throw new IllegalArgumentException("Invald exif");
                        }
                        in.skipBytes(2);
                        long exifOffset = in.getFilePointer();
                        String byteOrder = in.readString(2);
                        if (byteOrder.equals("II")) {
                            in = new IntelReader(randomAccess);
                        } else if (!byteOrder.equals("MM")) {
                            throw new IllegalArgumentException("Unknown exif byte order");
                        }
                        in.skipBytes(2);
                        long ifp = in.readUnsignedInt();
                        if (ifp == 0) {
                            throw new IllegalArgumentException("No datetime exif found");
                        }
                        in.seek(exifOffset + ifp);
                        return readIFP(in, exifOffset);
                    } else if (marker == 0xFFD8) { // Start of Image
                        break;
                    } else {
                        in.skipBytes(length - 2);
                    }
                }
                throw new Exception();
            }
            catch (Exception e) {
                throw new IllegalStateException("Exif problem " + file, e);
            }
        }

        private LocalDateTime readIFP(MotorolaReader in, long exifOffset) throws IOException {
            int numberOfEntries = in.readUnsignedShort();
            for (int i = 0; i < numberOfEntries; i++) {
                int tagNumber = in.readUnsignedShort();
                int dataFormat = in.readUnsignedShort();
                long numberOfComponents = in.readUnsignedInt();
                long dataValue = in.readUnsignedInt();
                                
                if (tagNumber == 0x8769) { // SubIFP pointer
                    in.seek(exifOffset + dataValue);
                    return readIFP(in, exifOffset);
                }

                if (tagNumber == 0x9003) { // DateTimeOriginal
                    in.seek(exifOffset + dataValue);
                    String dateString = in.readString((int) numberOfComponents - 1);
                    return toDateTime(dateString);
                }
            }
            long ifp = in.readUnsignedInt();
            if (ifp == 0) {
                throw new IllegalArgumentException("No datetime exif found");
            }
            return readIFP(in, exifOffset);
        }

        private static LocalDateTime toDateTime(String dateString) {
            return LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"));
        }

        private static class MotorolaReader {
            protected final RandomAccessFile in;
            public MotorolaReader(RandomAccessFile in) {
                this.in = in;
            }
            public String readString(int bytes) throws IOException {
                byte[] out = new byte[bytes];
                in.readFully(out);
                return new String(out);
            }
            public long getFilePointer() throws IOException {
                return in.getFilePointer();
            }
            public void seek(long to) throws IOException {
                in.seek(to);
            }
            public void skipBytes(int bytes) throws IOException {
                in.skipBytes(bytes);
            }
            public int readUnsignedByte() throws IOException {
                return in.readUnsignedByte();
            }
            public int readUnsignedShort() throws IOException {
                return in.readUnsignedShort();
            }
            public long readUnsignedInt() throws IOException {
                return Integer.toUnsignedLong(in.readInt());
            }
        }

        private static class IntelReader extends MotorolaReader {
            public IntelReader(RandomAccessFile in) {
                super(in);
            }
            public int readUnsignedShort() throws IOException {
                return Short.toUnsignedInt(Short.reverseBytes(in.readShort()));
            }
            public long readUnsignedInt() throws IOException {
                return Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
            }
        }
    }
}

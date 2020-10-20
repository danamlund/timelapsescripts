import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TextArea;
import java.awt.color.ColorSpace;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.File;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.ref.SoftReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

public class AlignImages {
    public static void main(String[] args) throws Exception {
        String commandOutputFormat = null;
        Data data = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--command")) {
                i++;
                if (i >= args.length) break;
                commandOutputFormat = args[i];
            } else if (args[i].endsWith(".xml")) {
                data = new Data(Paths.get(args[i]));
            } else {
                data = new Data(Arrays.asList(args));
            }
        }
        
        if (args.length == 0 || data == null) {
            System.out.println("AlignImages [--command output/%name_output.%ext] "
                               + "[alignimages.xml] [image001.jpg ...]");
            System.exit(1);
        }
        if (commandOutputFormat != null) {
            for (Path image : data.getImages()) {
                Path output = Paths.get(getOutputFileName(image, commandOutputFormat));
                System.out.println(data.getDistortMethod(image)
                                   .commandOneLine(data, image, output, ""));
            }
        } else {
            new Gui(data);
        }
    }

    private static class Gui {
        private final Data data;
        private Path currentImage = null;
        private Map<Path, SoftReference<Image>> cache = new HashMap<>();
        private Map<Path, SoftReference<Image>> cacheOutputs = new HashMap<>();
        private JPanel imagePanel;
        private JLabel imageName;
        private JLabel imageCount;
        private JPanel rightPanel;
        private JCheckBox setPointCheckBox;
        private JCheckBox showAllPointsCheckBox;
        private JComboBox<Positioner> positionComboBox;
        private JCheckBox isKeyFrameCheckBox;
        private JComboBox<DistortMethod> distortMethodComboBox;
        private JCheckBox showOutput;
        private double scale = 1.0;
        private int centerx = 0;
        private int centery = 0;

        public Gui(Data data) {
            this.data = data;
            JFrame frame = new JFrame("Align Images");
            JPanel panel = new JPanel(new BorderLayout());
            panel.setPreferredSize(new Dimension(1000, 800));
            JPanel topPanel = new JPanel(new BorderLayout());
            panel.add(topPanel, BorderLayout.NORTH);
            imageName = new JLabel();
            topPanel.add(imageName, BorderLayout.WEST);
            imageCount = new JLabel();
            topPanel.add(imageCount, BorderLayout.EAST);
            JPanel buttonsPanel = new JPanel();
            topPanel.add(buttonsPanel, BorderLayout.NORTH);
            JButton saveButton = new JButton("Save");
            buttonsPanel.add(saveButton);
            saveButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        try {
                            data.save();
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                });
            JButton commandButton = new JButton("Generate ImageMagick commands");
            buttonsPanel.add(commandButton);
            commandButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        JDialog dialog = new JDialog(frame, "ImageMagick commands", false);
                        StringBuilder sb = new StringBuilder();
                        for (Path image : data.getImages()) {
                            Path output = Paths.get("output").resolve(
                                getOutputFileName(image, "%name_aligned.%ext"));
                            sb.append(data.getDistortMethod(image)
                                      .commandOneLine(data, image, output, ""));
                            sb.append("\n");
                        }
                        dialog.setContentPane(new JScrollPane(new JTextArea(sb.toString())));
                        dialog.pack();
                        dialog.setSize(new Dimension(800, 400));
                        dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                        dialog.setVisible(true);
                    }
                });
            JButton addImageButton = new JButton("Add images...");
            addImageButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        JFileChooser fileChooser = new JFileChooser();
                        fileChooser.setMultiSelectionEnabled(true);
                        if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                            for (File f : fileChooser.getSelectedFiles()) {
                                data.addImage(f.toPath());
                            }
                            update();
                        }
                    }                    
                });
            buttonsPanel.add(addImageButton);


            rightPanel = new JPanel();
            rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
            panel.add(rightPanel, BorderLayout.EAST);
            ButtonGroup pointsGroup = new ButtonGroup();
            for (AlignPoint point : data.getPoints()) {
                JRadioButton button = new JRadioButton("");
                pointsGroup.add(button);
                rightPanel.add(button);
            }
            pointsGroup.getElements().nextElement().setSelected(true);
            JButton unsetPointButton = new JButton("Backspace: Unset point");
            unsetPointButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        data.setPoint(currentImage, getCurrentPoint().unset());
                        update();
                    }                    
                });
            rightPanel.add(unsetPointButton);
            distortMethodComboBox = new JComboBox<DistortMethod>(DistortMethod.values()) {
                    @Override
                    public Dimension getMaximumSize() {
                        return getPreferredSize();
                    }};
            distortMethodComboBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            distortMethodComboBox.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        data.setDistortMethod(currentImage,
                                              (DistortMethod) distortMethodComboBox.getSelectedItem());
                    }});
            rightPanel.add(new JLabel("M: distort method"));
            rightPanel.add(distortMethodComboBox);
            setPointCheckBox = new JCheckBox("Enter: Update point");
            rightPanel.add(setPointCheckBox);
            showAllPointsCheckBox = new JCheckBox("A: Show all points");
            rightPanel.add(showAllPointsCheckBox);
            positionComboBox = new JComboBox<Positioner>(getPositioners()) {
                    @Override
                    public Dimension getMaximumSize() {
                        return getPreferredSize();
                    }};
            positionComboBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            rightPanel.add(new JLabel("P: Unset point position"));
            rightPanel.add(positionComboBox);
            showOutput = new JCheckBox("Space: Show output");
            rightPanel.add(showOutput);
            showOutput.addItemListener(new ItemListener() {
                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        update();
                    }});
            isKeyFrameCheckBox = new JCheckBox("K: Key frame");
            isKeyFrameCheckBox.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (isKeyFrameCheckBox.isSelected()) {
                            data.setKeyFrame(currentImage);
                        } else {
                            data.setKeyFrame(null);
                        }
                    }});
            rightPanel.add(isKeyFrameCheckBox);
            JButton removeImageButton = new JButton("Delete: Remove image");
            removeImageButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        removeCurrentImage();
                    }                    
                });
            rightPanel.add(removeImageButton);

            rightPanel.add(new JLabel("<html>"
                                      +"up: move screen/point<br>"
                                      +"down: move screen/point<br>"
                                      +"left: move screen/point<br>"
                                      +"right: move screen/point<br>"
                                      +"&gt;: zoom in<br>"
                                      +"&lt;: zoom out<br>"
                                      +"ctrl left: previous image<br>"
                                      +"ctrl right: next image<br>"
                                      +"ctrl up: first image<br>"
                                      +"ctrl down: last image<br>"
                                      +"ctrl M: set distort all<br>"
                                      +"</html>"));
            
            imagePanel = new JPanel() {
                    @Override
                    public void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        g.translate(- (int)(centerx*scale) + getSize().width/2,
                                    - (int)(centery*scale) + getSize().height/2);
                        Graphics2D g2d = (Graphics2D) g;
                        g2d.scale(scale, scale);
                        g.drawImage(getImage(currentImage), 0, 0, null);
                        final List<AlignPoint> imagePoints;
                        if (showOutput.isSelected()) {
                            imagePoints = data.getPoints(data.keyFrame);
                        } else {
                            imagePoints = data.getPoints(currentImage);
                        }
                        for (AlignPoint point : imagePoints) {
                            if (point.isSet) {
                                g.setColor(point.color);
                                g.drawLine(point.x, point.y, point.x, point.y);
                                int pointSize = 20;
                                if (point.shape == 0) {
                                    g.drawOval(point.x - pointSize/2, point.y - pointSize/2,
                                               pointSize, pointSize);
                                } else if (point.shape == 1) {
                                    g.drawRect(point.x - pointSize/2, point.y - pointSize/2,
                                               pointSize, pointSize);
                                }
                            }
                        }
                        if (showAllPointsCheckBox.isSelected()) {
                            for (Path image : data.getImages()) {
                                if (image != currentImage) {
                                    for (AlignPoint point : data.getPoints(image)) {
                                        if (point.isSet) {
                                            g.setColor(point.color);
                                            int s = 10;
                                            g.drawLine(point.x-s, point.y-s, point.x+s, point.y+s);
                                            g.drawLine(point.x-s, point.y+s, point.x+s, point.y-s);
                                        }
                                    }
                                }
                            }
                        }
                    }
                };
            imagePanel.setBackground(Color.BLACK);
            panel.add(imagePanel, BorderLayout.CENTER);

            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(
                new KeyEventDispatcher() {
                    @Override
                    public boolean dispatchKeyEvent(KeyEvent e) {
                        if (e.getID() != KeyEvent.KEY_PRESSED) {
                            return false;
                        }
                        if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow()
                            != frame) {
                            return false;
                        }
                        if (e.isControlDown() && e.getExtendedKeyCode() == KeyEvent.VK_RIGHT) {
                            List<Path> images = data.getImages();
                            if (!images.isEmpty()) {
                                int currentImageIndex = data.getImages().indexOf(currentImage);
                                setCurrentImage(images.get(Math.min(images.size()-1,
                                                                    currentImageIndex+1)));
                                return true;
                            }
                        }
                        if (e.isControlDown() && e.getExtendedKeyCode() == KeyEvent.VK_LEFT) {
                            List<Path> images = data.getImages();
                            if (!images.isEmpty()) {
                                int currentImageIndex = data.getImages().indexOf(currentImage);
                                setCurrentImage(images.get(Math.max(0, currentImageIndex-1)));
                                return true;
                            }
                        }
                        if (e.isControlDown() && e.getExtendedKeyCode() == KeyEvent.VK_UP) {
                            List<Path> images = data.getImages();
                            if (!images.isEmpty()) {
                                setCurrentImage(images.get(0));
                                return true;
                            }
                        }
                        if (e.isControlDown() && e.getExtendedKeyCode() == KeyEvent.VK_DOWN) {
                            List<Path> images = data.getImages();
                            if (!images.isEmpty()) {
                                setCurrentImage(images.get(images.size()-1));
                            }
                            return true;
                        }
                        int minViewSize = Math.min(imagePanel.getSize().width,
                                                   imagePanel.getSize().height);
                        int moveDist = (int) Math.round(0.05 * minViewSize / scale);
                        if (e.isShiftDown()) {
                            moveDist = 1;
                        }
                        if (e.getExtendedKeyCode() == KeyEvent.VK_UP) {
                            zoom(scale, centerx, centery-moveDist);
                            return true;
                        }
                        if (e.getExtendedKeyCode() == KeyEvent.VK_DOWN) {
                            zoom(scale, centerx, centery+moveDist);
                            return true;
                        }
                        if (e.getExtendedKeyCode() == KeyEvent.VK_LEFT) {
                            zoom(scale, centerx-moveDist, centery);
                            return true;
                        }
                        if (e.getExtendedKeyCode() == KeyEvent.VK_RIGHT) {
                            zoom(scale, centerx+moveDist, centery);
                            return true;
                        }
                        if (e.getKeyChar() == '<') {
                            zoom(scale * 0.5, centerx, centery);
                            return true;
                        }
                        if (e.getKeyChar() == '>') {
                            zoom(scale * 2.0, centerx, centery);
                            return true;
                        }
                        if (e.getKeyChar() >= '0' && e.getKeyChar() <= '9') {
                            int n = e.getKeyChar() - '0';
                            if (n == 0) {
                                n = 10;
                            }
                            if (n < rightPanel.getComponentCount()
                                && rightPanel.getComponent(n-1) instanceof JRadioButton) {
                                JRadioButton pointButton = (JRadioButton) rightPanel.getComponent(n-1);
                                pointButton.setSelected(true);
                                return true;
                            }
                        }
                        if (e.getExtendedKeyCode() == KeyEvent.VK_BACK_SPACE) {
                            data.setPoint(currentImage, getCurrentPoint().unset());
                            update();
                            return true;
                        }
                        if (e.getExtendedKeyCode() == KeyEvent.VK_DELETE) {
                            removeCurrentImage();
                            return true;
                        }
                        if (e.getExtendedKeyCode() == KeyEvent.VK_ENTER) {
                            setPointCheckBox.setSelected(!setPointCheckBox.isSelected());
                            zoom(scale, centerx, centery); // set point if not set
                            return true;
                        }
                        if (e.getExtendedKeyCode() == KeyEvent.VK_A) {
                            showAllPointsCheckBox.setSelected(!showAllPointsCheckBox.isSelected());
                            update();
                            return true;
                        }
                        if (e.getExtendedKeyCode() == KeyEvent.VK_K) {
                            if (isKeyFrameCheckBox.isSelected()) {
                                data.setKeyFrame(null);
                            } else {
                                data.setKeyFrame(currentImage);
                            }
                            update();
                            return true;
                        }
                        if (e.getExtendedKeyCode() == KeyEvent.VK_SPACE) {
                            showOutput.setSelected(!showOutput.isSelected());
                            return true;
                        }
                        if (e.getExtendedKeyCode() == KeyEvent.VK_M) {
                            if (e.isControlDown()) {
                                DistortMethod distortMethod = data.getDistortMethod(currentImage);
                                for (Path image : data.getImages()) {
                                    data.setDistortMethod(image, distortMethod);
                                }
                                update();
                            } else {
                                distortMethodComboBox.setSelectedIndex(
                                    (distortMethodComboBox.getSelectedIndex() + 1)
                                    % distortMethodComboBox.getItemCount());
                            }
                            return true;
                        }
                        if (e.getExtendedKeyCode() == KeyEvent.VK_P) {
                            positionComboBox.setSelectedIndex(
                                (positionComboBox.getSelectedIndex() + 1)
                                % positionComboBox.getItemCount());
                            return true;
                        }

                        return false;
                    }
                });

            class ImagePanelMouseListener extends MouseAdapter implements MouseWheelListener {
                Integer movex = null;
                Integer movey = null;
                Integer moveCenterX = null;
                Integer moveCenterY = null;
                int prevX = -1;
                int prevY = -1;
                @Override
                public void mousePressed(MouseEvent e) {
                    if (isMouse3Down(e)) {
                        movex = e.getX();
                        movey = e.getY();
                        moveCenterX = centerx;
                        moveCenterY = centery;
                    } else if (isMouse1Down(e) && setPointCheckBox.isSelected()) {
                        data.setPoint(currentImage, 
                                      getCurrentPoint().with(getImageX(e.getX()), getImageY(e.getY())));
                        update();
                    }
                }
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (movex != null) {
                        zoom(scale,
                             moveCenterX - (e.getX() - movex),
                             moveCenterY - (e.getY() - movey));
                    }
                }
                @Override
                public void mouseReleased(MouseEvent e) {
                    movex = null;
                }
                @Override
                public void mouseMoved(MouseEvent e) {
                    prevX = e.getX();
                    prevY = e.getY();
                }
                private boolean isMouse3Down(MouseEvent e) {
                    return (e.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) > 0;
                }
                private boolean isMouse1Down(MouseEvent e) {
                    return (e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) > 0;
                }
                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    double newScale = scale;
                    for (int i = 0; i < e.getWheelRotation(); i++) {
                        newScale *= 0.5;
                    }
                    for (int i = 0; i > e.getWheelRotation(); i--) {
                        newScale *= 2.0;
                    }
                    if (newScale > scale) {
                        zoomDontSetPoint(newScale, getImageX(prevX), getImageY(prevY));
                    } else {
                        zoomDontSetPoint(newScale, centerx, centery);
                    }
                }
            }
            ImagePanelMouseListener mouseListener = new ImagePanelMouseListener();
            imagePanel.addMouseListener(mouseListener);
            imagePanel.addMouseMotionListener(mouseListener);
            imagePanel.addMouseWheelListener(mouseListener);
            
            setCurrentImage(data.getImages().get(0));
            resetZoom();
            
            frame.add(panel);
            frame.pack();
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setVisible(true);
        }
        private Image getImage(Path path) {
            if (showOutput.isSelected()) {
                SoftReference<Image> cached = cacheOutputs.get(path);
                if (cached == null || cached.get() == null) {
                    Path tmp = null;
                    Image image = null;
                    try {
                        tmp = Files.createTempFile("AlignImagesTmp", ".jpg");
                        List<String> command = data.getDistortMethod(path)
                            .command(data, path, tmp, "");
                        Process p = new ProcessBuilder(command).start();
                        p.getOutputStream().close();
                        String stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()))
                            .lines().collect(Collectors.joining("\n"));
                        String stdout = new BufferedReader(new InputStreamReader(p.getInputStream()))
                            .lines().collect(Collectors.joining("\n"));
                        p.waitFor();
                        if (p.exitValue() == 0) {
                            image = ImageIO.read(tmp.toFile());
                        } else {
                            image = getMessageImage(stderr+"\n"+stdout);
                        }
                    } catch (IOException | InterruptedException e) {
                        image = getMessageImage(e.getMessage());
                    } finally {
                        try {
                            if (tmp != null) {
                                Files.delete(tmp);
                            }
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                    cacheOutputs.put(path, new SoftReference<>(image));
                }
                return cacheOutputs.get(path).get();
            } else {
                cacheOutputs.clear();
                SoftReference<Image> cached = cache.get(path);
                if (cached == null || cached.get() == null) {
                    try {
                        cache.put(path, new SoftReference<>(ImageIO.read(currentImage.toFile())));
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
                return cache.get(path).get();
            }
        }
        private Image getMessageImage(String message) {
            Image image = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_ARGB);
            for (int i = 0; i*100 < message.length(); i++) {
                String line = message.substring(Math.max(i*100, 0),
                                                Math.min((i+1)*100, message.length()));
                image.getGraphics().drawString(line, 30, 30 + (i*30));
            }
            return image;
        }
        private void update() {
            imagePanel.repaint();
            updateStatus();
            List<AlignPoint> points = data.getPoints(currentImage);
            for (int i = 0; i < points.size(); i++) {
                AlignPoint point = points.get(i);
                JRadioButton pointButton = (JRadioButton) rightPanel.getComponent(i);
                pointButton.setText(point.getName((i+1)+": "+point.getText()));
            }
            isKeyFrameCheckBox.setSelected(Objects.equals(data.keyFrame, currentImage));
            distortMethodComboBox.setSelectedItem(data.getDistortMethod(currentImage));
        }
        private void setCurrentImage(Path image) {
            if (image == currentImage) {
                return;
            }
            Path oldImage = currentImage;
            currentImage = image;
            AlignPoint point = getCurrentPoint();
            if (point.isSet) {
                zoomDontSetPoint(scale, point.x, point.y);
            } else {
                ((Positioner)positionComboBox.getSelectedItem()).setCenterXy(oldImage, image);
                if (setPointCheckBox.isSelected()) {
                    data.setPoint(image, point.with(centerx, centery));
                }
            }
            update();
        }
        private AlignPoint getCurrentPoint() {
            for (int i = 0; i < rightPanel.getComponentCount(); i++) {
                JRadioButton pointButton = (JRadioButton) rightPanel.getComponent(i);
                if (pointButton.isSelected()) {
                    return data.getPoints(currentImage).get(i);
                }
            }
            return null;
        }
        private void resetZoom() {
            zoomDontSetPoint(1.0, getImage(currentImage).getWidth(null)/2,
                 getImage(currentImage).getHeight(null)/2);
        }
        private void zoomDontSetPoint(double scale, int x, int y) {
            this.scale = scale;
            centerx = x;
            centery = y;
            imagePanel.repaint();
            updateStatus();
        }
        private void zoom(double scale, int x, int y) {
            zoomDontSetPoint(scale, x, y);
            if (setPointCheckBox.isSelected()) {
                data.setPoint(currentImage, getCurrentPoint().with(x, y));
                update();
            }
        }
        private void updateStatus() {
            imageName.setText(currentImage.getFileName().toString());
            int imageIndex = data.getImages().indexOf(currentImage);
            String status = (imageIndex+1) + " / " + data.getImages().size();
            status += ", center ("+centerx+", "+centery+") scale="+scale;
            imageCount.setText(status);
        }
        private void removeCurrentImage() {
            int index = data.getImages().indexOf(currentImage);
            data.removeImage(currentImage);
            setCurrentImage(data.getImages()
                            .get(Math.min(data.getImages().size()-1, index)));
        }
        private int getImageX(int x) {
            return centerx - imagePanel.getSize().width/2 + x;
        }
        private int getImageY(int y) {
            return centery - imagePanel.getSize().height/2 + y;
        }
        public Positioner[] getPositioners() {
            List<Positioner> positioners = new ArrayList<>();
            positioners.add(new Positioner("At current point") {
                    @Override
                    public void setCenterXy(Path oldImage, Path newImage) {
                        AlignPoint newImagePoint = data.getPoint(newImage, getCurrentPoint());
                        if (newImagePoint.isSet) {
                            zoomDontSetPoint(scale, newImagePoint.x, newImagePoint.y);
                        }}});
            positioners.add(new Positioner("Same position"));
            for (AlignPoint point : data.getPoints()) {
                positioners.add(new RelativeToPointPositioner(point));
            }
            return positioners.toArray(new Positioner[0]);
        }
        private class Positioner {
            private final String name;
            public Positioner(String name) {
                this.name = name;
            }
            public void setCenterXy(Path oldImage, Path newImage) {
            }
            @Override
            public String toString() {
                return name;
            }
        }
        private class RelativeToPointPositioner extends Positioner {
            private AlignPoint point;
            public RelativeToPointPositioner(AlignPoint point) {
                super(point.getName("Relative to point"));
                this.point = point;
            }
            @Override
            public void setCenterXy(Path oldImage, Path newImage) {
                if (oldImage == null) return;
                AlignPoint oldImagePoint = data.getPoint(oldImage, point);
                int relX = centerx - oldImagePoint.x;
                int relY = centery - oldImagePoint.y;
                AlignPoint newImagePoint = data.getPoint(newImage, point);
                if (oldImagePoint.isSet && newImagePoint.isSet) {
                    zoomDontSetPoint(scale, relX + newImagePoint.x, relY + newImagePoint.y);
                }
            }
        }
    }

    private static class AlignPoint {
        public final Color color;
        public final int shape;
        public final int x;
        public final int y;
        public final boolean isSet;

        public AlignPoint(Color color, int shape) {
            this(color, shape, 0, 0, false);
        }
        public AlignPoint(Color color, int shape, int x, int y, boolean isSet) {
            this.color = color;
            this.shape = shape;
            this.x = x;
            this.y = y;
            this.isSet = isSet;
        }
        public AlignPoint with(int x, int y) {
            return new AlignPoint(color, shape, x, y, true);
        }
        public AlignPoint unset() {
            return new AlignPoint(color, shape, x, y, false);
        }
        public String getHtmlColor() {
            return String.format("%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
        }
        public String getText() {
            return isSet ? (x+", "+y) : "not set";
        }
        public String getName(String text) {
            String name = shape == 0 ? "○ " : "□ ";
            name += text;
            return "<html><span bgcolor="+getHtmlColor()+">"+name+"</span></html>";
        }
        @Override
        public boolean equals(Object oo) {
            AlignPoint o = (AlignPoint) oo;
            return color.equals(o.color) && shape == o.shape;
        }
        @Override
        public int hashCode() {
            return color.hashCode() * 13 * shape;
        }
    }
    public static Color fromHtmlColor(String s) {
        return new Color(Integer.parseInt(s.substring(0, 2), 16),
                         Integer.parseInt(s.substring(2, 4), 16),
                         Integer.parseInt(s.substring(4, 6), 16));
    }
    private static enum DistortMethod {
        NONE() {
            public List<String> command(Data data, Path image, Path output, String extra) {
                Dimension keyFrame = data.getKeyFrameDimension();
                return list("convert", image.toString(),
                            extra,
                            "-define", "distort:viewport="+keyFrame.width+"x"+keyFrame.height+"+0+0",
                            "-distort", "SRT", "0", output.toString());
            }},
        AFFINE() {
            public List<String> command(Data data, Path image, Path output, String extra) {
                return defaultDistort("Affine", data, image, output, extra);
            }},
        PERSPECTIVE() {
            public List<String> command(Data data, Path image, Path output, String extra) {
                return defaultDistort("Perspective", data, image, output, extra);
            }},
        BILINEAR_FORWARD() {
            public List<String> command(Data data, Path image, Path output, String extra) {
                return defaultDistort("BilinearForward", data, image, output, extra);
            }},
        SHEPARDS() {
            public List<String> command(Data data, Path image, Path output, String extra) {
                return defaultDistort("Shepards", data, image, output, extra);
            }};

        public abstract List<String> command(Data data, Path image, Path output, String extra);

        private static List<String> defaultDistort(String type, Data data, Path image, Path output,
                                                   String extra) {
            Dimension keyFrame = data.getKeyFrameDimension();
            return list("convert", image.toString(),
                        extra,
                        "-define", "distort:viewport="+keyFrame.width+"x"+keyFrame.height+"+0+0",
                        "-virtual-pixel", "black",
                        "-distort", type, getPoints(data, image),
                        output.toString());
            
        }
        private static List<String> list(String... strings) {
            List<String> output = new ArrayList<>();
            for (String string : strings) {
                if (string != null && !string.isEmpty()) {
                    output.add(string);
                }
            }
            return output;
        }
        public String commandOneLine(Data data, Path image, Path output, String extra) {
            StringBuilder sb = new StringBuilder();
            for (String arg : command(data, image, output, extra)) {
                if (sb.length() >= 1) {
                    sb.append(" ");
                }
                if (arg.contains(" ")) {
                    sb.append("\""+arg+"\"");
                } else {
                    sb.append(arg);
                }
            }
            return sb.toString();
        }

        private static String getPoints(Data data, Path image) {
            List<AlignPoint> keyPoints = data.getPoints(data.keyFrame);
            List<AlignPoint> points = data.getPoints(image);
            StringBuilder fromTosString = new StringBuilder();
            int numberOfPoints = 0;
            for (int i = 0; i < points.size(); i++) {
                AlignPoint keyPoint = keyPoints.get(i);
                AlignPoint point = points.get(i);
                if (keyPoint.isSet && point.isSet) {
                    numberOfPoints++;
                    if (fromTosString.length() >= 1) {
                        fromTosString.append("   ");
                    }
                    fromTosString.append(point.x+","+point.y+" "+keyPoint.x+","+keyPoint.y);
                }
            }
            return fromTosString.toString();
        }
    }
    public static String getOutputFileName(Path image, String format) {
        String name = image.getFileName().toString();
        String extension = name.substring(name.lastIndexOf(".")+1);
        String nameWithoutExtension = name.substring(0, name.lastIndexOf("."));
        return format.replace("%name", nameWithoutExtension).replace("%ext", extension);
    }
    private static class Data {
        final Path file;
        Path keyFrame = null;
        Dimension keyFrameDimension = null;
        double scaling = 1.0;
        List<AlignPoint> points = Arrays.asList(new AlignPoint(fromHtmlColor("ff3333"), 0),
                                                new AlignPoint(Color.GREEN.brighter(), 0),
                                                new AlignPoint(fromHtmlColor("6666ff"), 0),
                                                new AlignPoint(Color.PINK, 0),
                                                new AlignPoint(Color.CYAN, 0),
                                                new AlignPoint(Color.YELLOW, 0),
                                                new AlignPoint(fromHtmlColor("ff3333"), 1),
                                                new AlignPoint(Color.GREEN.brighter(), 1),
                                                new AlignPoint(fromHtmlColor("6666ff"), 1),
                                                new AlignPoint(Color.PINK, 1));
        LinkedHashMap<Path, Set<AlignPoint>> imageToPoints = new LinkedHashMap<>();
        HashMap<Path, DistortMethod> imageToDistortMethod = new HashMap<>();

        public Data(List<String> imagePaths) {
            if (imagePaths.isEmpty()) {
                throw new IllegalArgumentException("No images");
            }
            for (String imagePath : imagePaths) {
                Path image = Paths.get(imagePath);
                if (!Files.exists(image)) {
                    throw new IllegalArgumentException("Image file not found: " + imagePath);
                }
                imageToPoints.put(image, new HashSet<>());
            }
            file = imageToPoints.keySet().iterator().next()
                .toAbsolutePath().getParent().resolve("alignimages.xml");
        }
        
        public Data(Path file) throws IOException {
            this.file = file;
            if (Files.exists(file)) {
                load();
            }
        }

        public void setKeyFrame(Path image) {
            keyFrame = image;
            keyFrameDimension = null;
        }

        public Dimension getKeyFrameDimension() {
            if (keyFrameDimension == null && keyFrame != null) {
                try {
                    BufferedImage image = ImageIO.read(keyFrame.toFile());
                    keyFrameDimension = new Dimension(image.getWidth(), image.getHeight());
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            return keyFrameDimension;
        }
        
        public List<Path> getImages() {
            return new ArrayList<>(imageToPoints.keySet());
        }

        public List<AlignPoint> getPoints() {
            return points;
        }
        public List<AlignPoint> getPoints(Path image) {
            List<AlignPoint> imagePoints = new ArrayList<>();
            for (AlignPoint point : points) {
                if (imageToPoints.get(image).contains(point)) {
                    for (AlignPoint imagePoint : imageToPoints.get(image)) {
                        if (imagePoint.equals(point)) {
                            imagePoints.add(imagePoint);
                            break;
                        }
                    }
                } else {
                    imagePoints.add(point);
                }
            }
            return imagePoints;
        }
        public AlignPoint getPoint(Path image, AlignPoint point) {
            for (AlignPoint imagePoint : imageToPoints.get(image)) {
                if (imagePoint.equals(point)) {
                    return imagePoint;
                }
            }
            for (AlignPoint initialPoint : getPoints()) {
                if (initialPoint.equals(point)) {
                    return initialPoint;
                }
            }
            return null;
        }
        public DistortMethod getDistortMethod(Path image) {
            return imageToDistortMethod.getOrDefault(image, DistortMethod.AFFINE);
        }
        public void setDistortMethod(Path image, DistortMethod distortMethod) {
            imageToDistortMethod.put(image, distortMethod);
        }
        public void removeImage(Path image) {
            imageToPoints.remove(image);
        }
        public void addImage(Path image) {
            imageToPoints.put(image, new HashSet<>());
        }
        public void setPoint(Path image, AlignPoint point) {
            imageToPoints.get(image).remove(point);
            imageToPoints.get(image).add(point);
        }
        
        public void save() throws IOException {
            Path tmpFile = file.toAbsolutePath().getParent().resolve(file.getFileName()+".tmp");
            try (OutputStream fos = Files.newOutputStream(tmpFile)) {
                XMLOutputFactory xmlOutFact = XMLOutputFactory.newInstance();
                XMLStreamWriter writer = xmlOutFact.createXMLStreamWriter(fos);
                writer.writeStartDocument();
                writer.writeCharacters("\n");
                writer.writeStartElement("alignimages");
                if (keyFrame != null) {
                    writer.writeCharacters("\n");
                    writer.writeStartElement("keyframe");
                    writer.writeCharacters(keyFrame.toString());
                    writer.writeEndElement();
                }
                writer.writeCharacters("\n");
                writer.writeStartElement("images");
                for (Path image : getImages()) {
                    writer.writeCharacters("\n");
                    writer.writeStartElement("image");
                    {
                        writer.writeCharacters("\n");
                        writer.writeStartElement("name");
                        writer.writeCharacters(image.toString());
                        writer.writeEndElement();
                        writer.writeCharacters("\n");
                        writer.writeStartElement("distortmethod");
                        writer.writeCharacters(getDistortMethod(image).name());
                        writer.writeEndElement();
                        writer.writeCharacters("\n");
                        writer.writeStartElement("points");
                        {
                            for (AlignPoint point : getPoints(image)) {
                                if (point.isSet) {
                                    writer.writeCharacters("\n");
                                    writer.writeStartElement("point");
                                    {
                                        writer.writeCharacters("\n");
                                        writer.writeStartElement("color");
                                        writer.writeCharacters(point.getHtmlColor());
                                        writer.writeEndElement();
                                        writer.writeCharacters("\n");
                                        writer.writeStartElement("shape");
                                        writer.writeCharacters(String.valueOf(point.shape));
                                        writer.writeEndElement();
                                        writer.writeCharacters("\n");
                                        writer.writeStartElement("x");
                                        writer.writeCharacters(String.valueOf(point.x));
                                        writer.writeEndElement();
                                        writer.writeCharacters("\n");
                                        writer.writeStartElement("y");
                                        writer.writeCharacters(String.valueOf(point.y));
                                        writer.writeEndElement();
                                    }
                                    writer.writeCharacters("\n");
                                    writer.writeEndElement();
                                }
                            }
                        }
                        writer.writeCharacters("\n");
                        writer.writeEndElement();
                    }
                    writer.writeCharacters("\n");
                    writer.writeEndElement();
                }
                writer.writeCharacters("\n");
                writer.writeEndElement();
                writer.writeCharacters("\n");
                writer.writeEndElement();
                writer.writeEndDocument();
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
            Files.move(tmpFile, file,
                       StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }

        public void load() throws IOException {
            try (InputStream fis = Files.newInputStream(file)) {
                XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
                XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(fis);
                Path image = null;
                Set<AlignPoint> points = null;
                Color color = null;
                Integer shape = null;
                Integer x = null;
                Integer y = null;
                DistortMethod distortMethod = null;
                while (reader.hasNext()) {
                    reader.next();
                    if (isStartElement(reader, "keyframe")) {
                        reader.next();
                        setKeyFrame(Paths.get(reader.getText().trim()));
                    } else if (isStartElement(reader, "image")) {
                        image = null;
                    } else if (isStartElement(reader, "name")) {
                        reader.next();
                        image = Paths.get(reader.getText().trim());
                    } else if (isStartElement(reader, "distortmethod")) {
                        reader.next();
                        distortMethod = DistortMethod.valueOf(reader.getText().trim());
                    } else if (isStartElement(reader, "points")) {
                        points = new HashSet<>();
                    } else if (isStartElement(reader, "point")) {
                        color = null;
                        shape = x = y = null;
                    } else if (isStartElement(reader, "color")) {
                        reader.next();
                        color = fromHtmlColor(reader.getText().trim());
                    } else if (isStartElement(reader, "shape")) {
                        reader.next();
                        shape = Integer.parseInt(reader.getText().trim());
                    } else if (isStartElement(reader, "x")) {
                        reader.next();
                        x = Integer.parseInt(reader.getText().trim());
                    } else if (isStartElement(reader, "y")) {
                        reader.next();
                        y = Integer.parseInt(reader.getText().trim());
                    } else if (reader.isEndElement() && reader.getName().toString().equals("point")) {
                        points.add(new AlignPoint(color, shape, x, y, true));
                    } else if (reader.isEndElement() && reader.getName().toString().equals("image")) {
                        imageToPoints.put(image, points);
                        if (distortMethod != null) {
                            imageToDistortMethod.put(image, distortMethod);
                        }
                    } else if (reader.isEndElement() && reader.getName().toString().equals("images")) {
                        return;
                    }
                }
            } catch (XMLStreamException e) {
                throw new IllegalStateException(e);
            }
        }
        private static boolean isStartElement(XMLStreamReader reader, String name) {
            return reader.isStartElement() && reader.getName().toString().equalsIgnoreCase(name);
        }
    }
}

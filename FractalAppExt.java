package assigment;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/* ============================================================
 *  FractalApp: UI shell around a Swing-free FractalGenerator
 * ============================================================ */
public class FractalAppExt {

    enum FractalType { TREE, MANDELBROT, JULIA }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Fractal Playground — Tree / Mandelbrot / Julia");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            FractalPanel canvas = new FractalPanel(1024, 768);
            ControlPanel controls = new ControlPanel(canvas);

            f.setLayout(new BorderLayout());
            f.add(canvas, BorderLayout.CENTER);
            f.add(controls, BorderLayout.SOUTH);

            
            f.pack();
            f.setSize(1000, 720);
            f.setLocationByPlatform(true);
            f.setVisible(true);
        });
    }

    /* ===================== UI: Canvas ===================== */
    static final class FractalPanel extends JPanel {
        private volatile BufferedImage image;                 
        private final java.util.List<FractalGenerator.Line> lines =
                Collections.synchronizedList(new ArrayList<>()); 
        private volatile FractalGenerator.FractalControl current;
        private FractalType type = FractalType.TREE;
        private javax.swing.Timer timer;

        
        private int iterOrDepth = 500;
        private double juliaRe = -0.8, juliaIm = 0.156;
        private double centerX = -0.5, centerY = 0.0, scale = 3.0;
        private double treeAngleDeg = 22.0;

        FractalPanel(int w, int h) {
            setPreferredSize(new Dimension(w, h));
            setBackground(Color.WHITE);

            addComponentListener(new ComponentAdapter() {
                @Override public void componentShown(ComponentEvent e) { repaint(); }
                @Override public void componentResized(ComponentEvent e) { repaint(); }
            });
        }

        void setType(FractalType t) { this.type = t; }
        void setIterOrDepth(int v) { this.iterOrDepth = v; }
        void setJulia(double re, double im) { this.juliaRe = re; this.juliaIm = im; }
        void setView(double cx, double cy, double sc) { this.centerX = cx; this.centerY = cy; this.scale = sc; }
        void setTree(double angleDeg, double scale) { this.treeAngleDeg = angleDeg; this.scale = scale; }

        /** Corrected version — does NOT auto-generate depth=500 trees */
        void generate(Consumer<String> updateStatus) {
            if (current != null) { cancelCurrent(updateStatus); return; }

            final int w = Math.max(1, getWidth());
            final int h = Math.max(1, getHeight());

            int value = Math.max(1, iterOrDepth);

            current = FractalGenerator.generate(
                type,
                w, h,
                value,               
                treeAngleDeg,
                value,               
                centerX, centerY, Math.max(1e-12, scale),
                juliaRe, juliaIm
            );

            
            if (type == FractalType.TREE) {
                image = null;
                lines.clear();
            } else {
                lines.clear();
                image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            }

            timer = new javax.swing.Timer(100, e -> {
                List<Object> polled = FractalGenerator.update(current);

                for (Object o : polled) {
                    if (type == FractalType.TREE) {
                        lines.add((FractalGenerator.Line)o);
                    } else {
                        FractalGenerator.RowInfo ri = (FractalGenerator.RowInfo)o;
                        image.getRaster().setDataElements(0, ri.y, w, 1, ri.row);
                    }
                }

                SwingUtilities.invokeLater(this::repaint);

                if (current.pool.isTerminated()) {
                    SwingUtilities.invokeLater(() -> {
                        updateStatus.accept(current.isCancelled.get() ? "Cancelled." : "Rendered.");
                        ((javax.swing.Timer)e.getSource()).stop();
                        current = null;
                    });
                }
            });
            timer.start();
        }

        void cancelCurrent(Consumer<String> updateStatus) {
            FractalGenerator.FractalControl h = current;
            if (h != null) {
                updateStatus.accept("Cancelling...");
                h.isCancelled.set(true);
            }
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (type == FractalType.TREE) {
                synchronized (lines) {
                    for (FractalGenerator.Line ln : lines) {
                        g2.setColor(new Color(ln.argb, true));
                        g2.drawLine(ln.x1, ln.y1, ln.x2, ln.y2);
                    }
                }
            } else if (image != null) {
                g2.drawImage(image, 0, 0, null);
            }
            g2.dispose();
        }
    }

    /* ===================== UI: Controls ===================== */
    static final class ControlPanel extends JPanel {
        private final JComboBox<FractalType> typeBox = new JComboBox<>(FractalType.values());
        private final JTextField iterDepth = new JTextField("500", 3);
        private final JTextField juliaRe = new JTextField("-0.8", 3);
        private final JTextField juliaIm = new JTextField("0.156", 3);
        private final JTextField angleDeg = new JTextField("22", 2);
        private final JTextField scaleField = new JTextField("3.0", 3);
        private final JTextField centerX = new JTextField("-0.5", 3);
        private final JTextField centerY = new JTextField("0.0", 3);
        private final JButton generateBtn = new JButton("Generate");
        private final JButton cancelBtn = new JButton("Cancel");
        private final JLabel status = new JLabel(" ");

        private final FractalPanel canvas;

        ControlPanel(FractalPanel canvas) {
            this.canvas = canvas;

            setLayout(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 6, 4, 6);
            c.gridy = 0; c.gridx = 0; c.anchor = GridBagConstraints.WEST;

            add(new JLabel("Fractal:"), c); c.gridx++;
            add(typeBox, c); c.gridx++;

            add(new JLabel("Iter/Depth:"), c); c.gridx++;
            add(iterDepth, c); c.gridx++;

            add(new JLabel("Julia c (re, im):"), c); c.gridx++;
            add(juliaRe, c); c.gridx++;
            add(juliaIm, c); c.gridx++;

            add(new JLabel("View scale:"), c); c.gridx++;
            add(scaleField, c); c.gridx++;

            add(new JLabel("Center (x,y):"), c); c.gridx++;
            add(centerX, c); c.gridx++;
            add(centerY, c); c.gridx++;

            c.gridx++; add(generateBtn, c); c.gridx++;
            add(cancelBtn, c);

            c.gridy = 1; c.gridx = 0; c.gridwidth = 12; c.fill = GridBagConstraints.HORIZONTAL;
            add(status, c);

            syncFieldVisibility((FractalType) typeBox.getSelectedItem());

            typeBox.addActionListener(e -> {
                FractalType t = (FractalType) typeBox.getSelectedItem();
                canvas.setType(t);
                syncFieldVisibility(t);
            });

            generateBtn.addActionListener(e -> onGenerate());
            cancelBtn.addActionListener(e -> {
                canvas.cancelCurrent(s -> status.setText(s));
            });
        }

        private void onGenerate() {
            try {
                FractalType t = (FractalType) typeBox.getSelectedItem();
                int v = Integer.parseInt(iterDepth.getText().trim());

                if (t == FractalType.TREE) {
                    canvas.setIterOrDepth(Math.max(1, v));
                    double ang = Double.parseDouble(angleDeg.getText().trim());
                    double sc = Double.parseDouble(scaleField.getText().trim());
                    canvas.setTree(ang, sc);
                } else {
                    canvas.setIterOrDepth(Math.max(10, v));
                    double cx = Double.parseDouble(centerX.getText().trim());
                    double cy = Double.parseDouble(centerY.getText().trim());
                    double sc = Double.parseDouble(scaleField.getText().trim());
                    canvas.setView(cx, cy, Math.max(1e-12, sc));
                    if (t == FractalType.JULIA) {
                        double re = Double.parseDouble(juliaRe.getText().trim());
                        double im = Double.parseDouble(juliaIm.getText().trim());
                        canvas.setJulia(re, im);
                    }
                }

                status.setText("Rendering…");
                canvas.generate(status::setText);

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Please enter valid numeric parameters.",
                        "Input Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void syncFieldVisibility(FractalType t) {
            boolean isTree = (t == FractalType.TREE);
            juliaRe.setEnabled(t == FractalType.JULIA);
            juliaIm.setEnabled(t == FractalType.JULIA);
            angleDeg.setEnabled(isTree);
            centerX.setEnabled(!isTree);
            centerY.setEnabled(!isTree);
            centerX.setText(t == FractalType.MANDELBROT ? "-0.5" : "0.0");
            scaleField.setText(isTree ? "0.8" : "3.0");
            iterDepth.setText(isTree ? "11" : "500");
        }
    }

    /* ============================================================
     *  FractalGenerator — CONCURRENT, UI-FREE FRACTAL ENGINE
     * ============================================================ */
    static final class FractalGenerator {

        private static volatile boolean slowMode = true;

        public record Line(int x1, int y1, int x2, int y2, int argb) {}
        public record RowInfo(int y, int[] row) {}

        public record FractalControl(
                ExecutorService pool,
                AtomicBoolean isCancelled,
                BlockingQueue<Object> queue,
                AtomicInteger tasks
        ) {}


        public static List<Object> update(FractalControl handle) {
            List<Object> drained = new ArrayList<>();
            handle.queue.drainTo(drained);

            if (handle.tasks.get() == 0 && !handle.pool.isShutdown()) {
                handle.pool.shutdown();
            }
            return drained;
        }

        public static FractalControl generate(
                FractalType type,
                int w, int h,
                int depth, double angleDeg,
                int maxIter,
                double centerX, double centerY, double scale,
                double juliaRe, double juliaIm)
        {
            BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
            AtomicBoolean cancelled = new AtomicBoolean(false);
            AtomicInteger tasks = new AtomicInteger(1);

            ExecutorService pool = Executors.newFixedThreadPool(
                    Math.max(1, Runtime.getRuntime().availableProcessors()));

            FractalControl handle = new FractalControl(pool, cancelled, queue, tasks);

            pool.submit(() -> {
                try {
                    if (type == FractalType.TREE) {
                        double length = Math.min(w, h) * 0.18;
                        submitBranch(handle, w/2, (int)(h*0.9),
                                -90.0, length, depth, angleDeg, scale);
                    } else {
                        generateSet(handle, type, w, h, maxIter,
                                centerX, centerY, scale, juliaRe, juliaIm);
                    }
                } finally {
                    if (handle.tasks.decrementAndGet() == 0 && !handle.pool.isShutdown()) {
                        handle.pool.shutdown();
                    }
                }
            });

            return handle;
        }

        private static void submitBranch(
                FractalControl handle,
                int x, int y,
                double angle, double length,
                int height,
                double angleDeg, double scale)
        {
            if (height == 0 || handle.isCancelled.get()) return;

            if (slowMode) {
                try { Thread.sleep(100); } catch (InterruptedException ignore) {}
            }

            int x2 = x + (int)Math.round(Math.cos(Math.toRadians(angle)) * length);
            int y2 = y + (int)Math.round(Math.sin(Math.toRadians(angle)) * length);

            int argb = (height < 5)
                    ? new Color(20, 140, 20).getRGB()
                    : new Color(40, 40, 40).getRGB();

            try { handle.queue.put(new Line(x, y, x2, y2, argb)); }
            catch (InterruptedException ignored) {}

            double nextLen = length * scale;

            handle.tasks.incrementAndGet();
            handle.pool.submit(() -> {
                try {
                    submitBranch(handle, x2, y2,
                            angle - angleDeg, nextLen,
                            height - 1, angleDeg, scale);
                } finally {
                    if (handle.tasks.decrementAndGet() == 0 && !handle.pool.isShutdown())
                        handle.pool.shutdown();
                }
            });

            submitBranch(handle, x2, y2,
                    angle + angleDeg, nextLen,
                    height - 1, angleDeg, scale);
        }

        
        private static void generateSet(
                FractalControl handle,
                FractalType type,
                int w, int h,
                int maxIter,
                double centerX, double centerY,
                double scale,
                double juliaRe, double juliaIm)
        {
            double scaleX = scale;
            double scaleY = scale * h / (double) w;

            double xmin = centerX - scaleX / 2.0;
            double ymin = centerY - scaleY / 2.0;

            for (int y = 0; y < h; y++) {
                final int Y = y;

                handle.tasks.incrementAndGet();

                handle.pool.submit(() -> {
                    try {
                        if (handle.isCancelled.get()) return;

                        int[] row = new int[w];

                        double imag = ymin +
                                (Y / (double)(h - 1)) * scaleY;

                        for (int x = 0; x < w; x++) {
                            if (handle.isCancelled.get()) return;

                            double real = xmin +
                                    (x / (double)(w - 1)) * scaleX;

                            row[x] = switch (type) {
                                case MANDELBROT -> mandelbrot(real, imag, maxIter);
                                case JULIA -> julia(real, imag, juliaRe, juliaIm, maxIter);
                                default -> 0x000000;
                            };
                        }

                        handle.queue.put(new RowInfo(Y, row));

                    } catch (InterruptedException ignore) {
                        return;
                    } finally {
                        if (handle.tasks.decrementAndGet() == 0 && !handle.pool.isShutdown()) {
                            handle.pool.shutdown();
                        }
                    }
                });
            }
        }

        
        private static int mandelbrot(double cr, double ci, int maxIter) {
            double zr = 0, zi = 0;
            double zr2 = 0, zi2 = 0;
            int i = 0;

            while (i < maxIter && (zr2 + zi2) <= 4.0) {
                zi = 2 * zr * zi + ci;
                zr = zr2 - zi2 + cr;
                zr2 = zr * zr;
                zi2 = zi * zi;
                i++;
            }
            return palette(i, maxIter, zr, zi);
        }

        private static int julia(double zr, double zi, double cr, double ci, int maxIter) {
            int i = 0;
            double zr2 = zr * zr, zi2 = zi * zi;

            while (i < maxIter && (zr2 + zi2) <= 4.0) {
                double nzr = zr2 - zi2 + cr;
                double nzi = 2*zr*zi + ci;

                zr = nzr;
                zi = nzi;

                zr2 = zr * zr;
                zi2 = zi * zi;
                i++;
            }
            return palette(i, maxIter, zr, zi);
        }

        private static int palette(int iter, int maxIter, double zr, double zi) {
            if (iter >= maxIter) return 0x000000;

            double mu = iter - Math.log(Math.log(zr*zr + zi*zi)) / Math.log(2);
            double t = Math.min(1.0, Math.max(0.0, mu / maxIter));

            float hue = (float)(0.7 + 10.0 * t);
            return Color.HSBtoRGB(hue, 0.8f, 1.0f);
        }
    }
}
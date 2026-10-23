package org.example;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.example.Enum.Ids;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.ArucoDetector;
import org.opencv.objdetect.DetectorParameters;
import org.opencv.objdetect.Objdetect;

import nu.pattern.OpenCV;
import org.opencv.core.Point;

public class App {
    private JLabel label;
    private JFrame frame;
    private boolean firstFrame = true;
    private static final int port = 4747;
    private static final String phone_ip = "192.168.0.89"; // change these depending on your app; im using droidcam
                                                           // because i can get this information easily

    private ArucoDetector detector;

    public static void main(String[] args) {
        OpenCV.loadLocally();
        App app = new App();
        app.detector = new ArucoDetector(Objdetect.getPredefinedDictionary(Objdetect.DICT_APRILTAG_36h11),
                new DetectorParameters());
        SwingUtilities.invokeLater(app::makeUi); // methodref makes a runnable if it has no args and returns void; this
                                                 // is why its perfect in this situation
        new Thread(app::connect).start();
    }

    private void makeUi() {
        frame = new JFrame("Camera output");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        label = new JLabel("Waiting for camera connection", SwingConstants.CENTER);
        label.setPreferredSize(new java.awt.Dimension(400, 300));
        frame.add(label);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void showFrame(BufferedImage img) {
        SwingUtilities.invokeLater(() -> {
            label.setText("");
            label.setIcon(new ImageIcon(img));
            if (firstFrame) {
                firstFrame = false;
                frame.pack();
                frame.setLocationRelativeTo(null);
            }
        });
    }

    private void setStatus(String text) { // lazy ass!
        SwingUtilities.invokeLater(() -> {
            label.setIcon(null);
            label.setText(text);
        });
    }

    private BufferedImage processFrameForApriltag(byte[] jpg, double fps, String resolution, long uptimeSeconds)
            throws Exception {
        Mat color = Imgcodecs.imdecode(new MatOfByte(jpg), Imgcodecs.IMREAD_COLOR); // a Mat is essentially an image
                                                                                    // container; stored in native
                                                                                    // memory
        if (color.empty()) // color is the picture itself, stores blue, green, red (BGR), jpg is the byte
                           // array from frameBuffer
            return null;

        // HUD
        int height = 45;
        Imgproc.rectangle(color, new Point(0, 0), new Point(color.cols(), height), new Scalar(20, 20, 20), -1);
        String fpsText = String.format("FPS: %.1f", fps);
        Scalar textColor = new Scalar(255, 255, 255);
        int font = Imgproc.FONT_HERSHEY_SIMPLEX;
        double fontScale = .5;
        int hudTextThickness = 1;
        int textYPosition = 28;

        Imgproc.putText(color, fpsText, new Point(20, textYPosition), font, fontScale, textColor, hudTextThickness);
        Imgproc.putText(color, resolution, new Point(color.cols() / 2 - 50, textYPosition), font, fontScale, textColor,
                hudTextThickness);
        Imgproc.putText(color, String.valueOf(uptimeSeconds), new Point(color.cols() - 150, textYPosition), font,
                fontScale, textColor, hudTextThickness);
        Mat gray = new Mat();
        Imgproc.cvtColor(color, gray, Imgproc.COLOR_BGR2GRAY); // grayscale helps detection i think

        ArrayList<Mat> corners = new ArrayList<>();
        Mat ids = new Mat();
        detector.detectMarkers(gray, corners, ids);

        if (!ids.empty()) {
            int thickness = 4; // px?
            for (int i = 0; i < corners.size(); i++) {
                Point[] pts = new Point[4];
                for (int j = 0; j < 4; j++) {
                    double[] xy = corners.get(i).get(0, j);
                    pts[j] = new Point(xy[0], xy[1]);
                }
                for (int j = 0; j < 4; j++) {
                    Imgproc.line(color, pts[j], pts[(j + 1) % 4], new Scalar(0, 255, 0), thickness);
                }

                int id = (int) ids.get(i, 0)[0];
                Imgproc.putText(color, "  ID " + id, pts[0], Imgproc.FONT_HERSHEY_SIMPLEX, // just draw the ID text,
                                                                                           // since
                                                                                           // we cant change certain
                                                                                           // properties of the lines
                                                                                           // without manually doing it
                        0.8, new Scalar(255, 255, 255), 2);
                if (Ids.fromId(id) != Ids.NoSuchAction) {
                    Imgproc.putText(color, "  Action " + Ids.fromId(id), pts[3], Imgproc.FONT_HERSHEY_COMPLEX, 0.8,
                            new Scalar(255, 255, 255), 2);
                } else {
                    Imgproc.putText(color, "  No assigned action " + Ids.fromId(id), pts[3],
                            Imgproc.FONT_HERSHEY_COMPLEX, 0.8,
                            new Scalar(255, 255, 255), 2);
                }
            }
            for (int i = 0; i < ids.rows(); i++) {
                System.out.println("Apriltag id: " + (int) ids.get(i, 0)[0]);
            }
        }

        MatOfByte out = new MatOfByte();
        Imgcodecs.imencode(".jpg", color, out);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(out.toArray()));
        color.release();
        gray.release();
        ids.release();
        out.release();
        for (Mat c : corners)
            c.release();

        return img;
    }

    private void connect() {
        String streamUrl = "http://" + phone_ip + ":" + port + "/video";
        System.out.println(streamUrl);

        while (true) {
            try {
                URL url = new URI(streamUrl).toURL();
                URLConnection connection = url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                try (InputStream in = new BufferedInputStream(connection.getInputStream(), 1 << 16)) {
                    System.out.println("Connected");

                    // metrics
                    long startTime = System.currentTimeMillis();
                    int frameCount = 0;
                    long lastCheckTime = System.currentTimeMillis();
                    double fps = 0.0;
                    String res = "0x0";

                    /*
                     * MJPEG: ascii headers with jpeg frames in between. imageio only
                     * decodes a stream that starts with jpeg headers (FF D8) so decoding is
                     * necessary
                     */
                    ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream(1 << 16);
                    boolean inFrame = false;
                    int prev = -1;
                    int b;

                    while ((b = in.read()) != -1) {
                        if (!inFrame) {
                            if (prev == 0xFF && b == 0xD8) { // FF D8 is the start of a JPEG frame
                                inFrame = true;
                                frameBuffer.reset();
                                frameBuffer.write(0xFF); // re add marker bytes we read past
                                frameBuffer.write(0xD8);
                            }
                        } else {
                            frameBuffer.write(b); // write the bytes that we are currently holding if its not 0xFF or
                                                  // 0xD8
                            if (prev == 0xFF && b == 0xD9) { // FF D9 is the end of the JPEG frame
                                long currentTime = System.currentTimeMillis();

                                long uptimeSeconds = (currentTime - startTime) / 1000;
                                inFrame = false;
                                try {
                                    BufferedImage img = processFrameForApriltag(frameBuffer.toByteArray(), fps, res,
                                            uptimeSeconds);
                                    if (img != null) {
                                        frameCount++;
                                        res = img.getWidth() + "x" + img.getHeight();
                                        long fpsElapsed = currentTime - lastCheckTime;
                                        if (fpsElapsed >= 1000) {
                                            fps = (frameCount * 1000.0) / fpsElapsed;
                                            frameCount = 0;
                                            lastCheckTime = currentTime;
                                        }
                                        showFrame(img);
                                    }
                                } catch (Exception decodeError) {
                                    // empty cuz we should just skip it
                                }
                            }
                        }
                        prev = b;
                    }
                    System.out.println("Stream ended");
                }
            } catch (Exception e) {
                System.out.println("Connection failed (" + e.getClass().getSimpleName()
                        + "), retrying in 2s. Is your app open?");
            }

            setStatus("Waiting for connection...");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException _) {
                return;
            }
        }
    }
}
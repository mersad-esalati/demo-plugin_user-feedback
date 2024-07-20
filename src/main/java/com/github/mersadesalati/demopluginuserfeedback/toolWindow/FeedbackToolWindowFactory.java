package com.github.mersadesalati.demopluginuserfeedback.toolWindow;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;

import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

public class FeedbackToolWindowFactory implements ToolWindowFactory {
    private static final Logger logger = Logger.getInstance(FeedbackToolWindowFactory.class);

    private final OkHttpClient httpClient;

    private int currentImageIndex = 0;
    private final ArrayList<Map<String, Object>> imageList;

    private JPanel panel;
    private JPanel feedbackPanel;
    private JLabel pictureLabel;
    private JLabel welcomeLabel;
    private JButton startButton;

    public FeedbackToolWindowFactory() {
        httpClient = new OkHttpClient();
        imageList = fetchImages();
    }

    // Method to fetch images from the API
    private ArrayList<Map<String, Object>> fetchImages() {
        Request request = new Request.Builder()
                .url("http://mersadesalati.pythonanywhere.com/api/random_images/3")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                assert response.body() != null;
                String jsonResponse = response.body().string();
                Gson gson = new Gson();
                JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);
                JsonArray itemsArray = jsonObject.getAsJsonArray("items");

                Type listType = new TypeToken<ArrayList<Map<String, Object>>>() {}.getType();
                ArrayList<Map<String, Object>> imageList = gson.fromJson(itemsArray, listType);

                logger.info("Fetched images: " + imageList.size());
                return imageList;
            } else {
                logger.error("Failed to fetch images: " + response.message());
            }
        } catch (Exception e) {
            logger.error("Error during API call", e);
        }
        return new ArrayList<>();
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        panel = new JPanel();

        // Add welcome message
        welcomeLabel = new JLabel("Welcome! Please provide your feedback on the images.");
        welcomeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        startButton = new JButton("Start");
        startButton.setHorizontalAlignment(SwingConstants.CENTER);
        startButton.addActionListener(e -> {
            logger.info("Start Button Clicked");
            // Hide the start button and welcome message
            startButton.setVisible(false);
            welcomeLabel.setVisible(false);

            // Display picture
            displayNextImage();
        });

        // Use a panel to hold the welcome message and start button together
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(welcomeLabel, BorderLayout.NORTH);
        topPanel.add(startButton, BorderLayout.SOUTH);

        panel.add(topPanel, BorderLayout.NORTH);

        toolWindow.getContentManager().addContent(
                ContentFactory.getInstance().createContent(panel, "", false)
        );

        // Ensure panel size is set
        panel.setPreferredSize(new Dimension(480, 480));
    }

    private void displayNextImage() {
        if (currentImageIndex < imageList.size()) {
            Map<String, Object> imageData = imageList.get(currentImageIndex);

            String imageUrl = (String) imageData.get("url");
            // Load the image from the URL
            try {
                logger.info("Start Loading Image: " + imageUrl);
                BufferedImage image = loadImageFromURL(imageUrl);

                if (image != null) {
                    logger.info("Image Loaded");
                    // Resize image to match the pane's width
                    int paneWidth = panel.getWidth();
                    int paneHeight = panel.getHeight();
                    if (pictureLabel == null) {
                        pictureLabel = new JLabel();
                        panel.add(pictureLabel, BorderLayout.CENTER);
                    }
                    pictureLabel.setIcon(new ImageIcon(resizeImage(image, paneWidth, paneHeight)));

                    if (feedbackPanel == null) {
                        feedbackPanel = getFeedbackPanel();
                        panel.add(feedbackPanel, BorderLayout.SOUTH);
                    }

                    panel.revalidate();
                    panel.repaint();
                } else {
                    logger.error("Failed Loading Image");
                    JOptionPane.showMessageDialog(panel, "Failed to load image.", "Error", JOptionPane.ERROR_MESSAGE);
                    welcomeLabel.setVisible(true);
                    startButton.setVisible(true);
                }
            } catch (Exception e) {
                logger.error("Error loading image from URL: " + imageUrl, e);
            }
        } else {
            logger.info("No More Images");
            JOptionPane.showMessageDialog(panel, "Finished!! \nNo more images.", "Information", JOptionPane.INFORMATION_MESSAGE);

            resetToStart();
        }
    }

    private @NotNull JPanel getFeedbackPanel() {
        JPanel feedbackPanel = new JPanel();
        feedbackPanel.setLayout(new BorderLayout());

        JLabel sliderLabel = new JLabel("How beautiful is the animal?");
        feedbackPanel.add(sliderLabel, BorderLayout.NORTH);

        JSlider feedbackSlider = new JSlider(0, 10, 5); // Default value is 5
        feedbackSlider.setMajorTickSpacing(1);
        feedbackSlider.setPaintTicks(true);
        feedbackSlider.setPaintLabels(true);
        feedbackPanel.add(feedbackSlider, BorderLayout.CENTER);

        JButton submitButton = new JButton("Submit Feedback");
        submitButton.addActionListener(e1 -> {
            int feedbackScore = feedbackSlider.getValue();
            Map<String, Object> imageData = imageList.get(currentImageIndex);
            String imageId = (String) imageData.get("id");

            JsonObject feedbackJson = new JsonObject();
            feedbackJson.addProperty("image_id", imageId);
            feedbackJson.addProperty("score", feedbackScore);

            RequestBody body = RequestBody.create(
                    feedbackJson.toString(),
                    MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url("http://mersadesalati.pythonanywhere.com/api/submit_score")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    logger.info("Feedback submitted successfully");
                    currentImageIndex++;
                    displayNextImage();
                } else {
                    logger.error("Failed to submit feedback: " + response.message());
                }
            } catch (Exception e) {
                logger.error("Error submitting feedback", e);
            }
        });
        feedbackPanel.add(submitButton, BorderLayout.SOUTH);
        return feedbackPanel;
    }

    private BufferedImage loadImageFromURL(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            return ImageIO.read(url.openStream());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private BufferedImage resizeImage(BufferedImage originalImage, int maxWidth, int maxHeight) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        float aspectRatio = (float) originalWidth / originalHeight;

        int newWidth = originalWidth;
        int newHeight = originalHeight;

        // Check if resizing is needed based on the width
        if (originalWidth > maxWidth) {
            newWidth = maxWidth;
            newHeight = (int) (maxWidth / aspectRatio);
        }

        // Check if resizing is needed based on the height
        if (newHeight > maxHeight) {
            newHeight = maxHeight;
            newWidth = (int) (maxHeight * aspectRatio);
        }

        Image tmp = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        return resizedImage;
    }

    private void saveFeedbackToFile(String feedback) {
        // Get the path to IntelliJ IDEA's log directory
        File logDir = new File(PathManager.getLogPath());
        // Create a file named feedback.txt in the log directory
        File file = new File(logDir, "feedback.txt");

        // Log the path for debugging purposes
        logger.info("Saving feedback to: " + file.getAbsolutePath());
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write(feedback);
            writer.newLine();
        } catch (IOException e) {
            logger.error(e);
            e.printStackTrace();
        }
    }

    private void resetToStart() {
        // Remove the picture label and feedback panel
        panel.remove(pictureLabel);
        panel.remove(feedbackPanel);

        // Reset the picture label and feedback panel
        pictureLabel = null;
        feedbackPanel = null;

        // Reset the image index
        currentImageIndex = 0;

        // Show the welcome label and start button
        welcomeLabel.setVisible(true);
        startButton.setVisible(true);

        // Update the UI
        panel.revalidate();
        panel.repaint();
    }
}

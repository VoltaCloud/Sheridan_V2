package link.locutus.discord.util;

import ai.djl.util.Pair;
import cn.easyproject.easyocr.EasyOCR;
import cn.easyproject.easyocr.ImageType;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.io.PagePriority;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.IntIntPair;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public class ImageUtil {
    public static BufferedImage readImage(String urlAddr) {
        try {
            URL url = new URL(urlAddr);
            BufferedImage image = ImageIO.read(url);
            return image;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final List<String> SUPPORTED_DOMAINS = Arrays.asList(
            "discord.gg",
            "discord.com",
            "discordapp.com",
            "discord.media",
            "discordapp.net",
            "discordcdn.com",
            "discord.dev",
            "discord.new",
            "discord.gift",
            "discordstatus.com",
            "dis.gd",
            "discord.co"
    );

    public static boolean isDiscordImage(String url) {
        try {
            URL parsedUrl = new URL(url);
            String host = parsedUrl.getHost();
            return SUPPORTED_DOMAINS.stream().anyMatch(host::endsWith);
        } catch (Exception e) {
            // Invalid URL
            return false;
        }
    }



    public static String getTextLocal(String imageUrl, ImageType type) {
        String pathStr = Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OCR.TESSERACT_LOCATION;
        if (pathStr == null || pathStr == null) {
            return null;
        }
        EasyOCR ocr = new EasyOCR(pathStr);
        ocr.setTesseractOptions(EasyOCR.OPTION_LANG_ENG);
        File fileTmp = null; // 50MB limit
        try {
            fileTmp = downloadImageWithSizeLimit(imageUrl, 50 * 1024 * 1024);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        File fileAbs = fileTmp.getAbsoluteFile();
        String result = ocr.discernAndAutoCleanImage(fileAbs, type);
        result = result.replace("|", "I");
        fileTmp.delete();
        return result;
    }

    public static File downloadImageWithSizeLimit(String imageUrl, long maxSizeBytes) throws IOException {
        if (!isDiscordImage(imageUrl)) {
            throw new IllegalArgumentException("URL is not from cdn.discordapp.com");
        }

        // Open a connection to the image URL
        URL url = new URL(imageUrl);
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");

        // Verify that the content type is an image
        String contentType = connection.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IOException("Invalid content type: " + contentType);
        }

        // Verify that the content length is within the size limit
        int contentLength = connection.getContentLength();
        if (contentLength > maxSizeBytes) {
            throw new IOException("Image size exceeds limit: " + contentLength + " bytes");
        }

        // Download the image to a temporary file
        InputStream inputStream = connection.getInputStream();
        File tempFile = new File("images/" + UUID.randomUUID().toString() + ".png");
        // create folder
        tempFile.getParentFile().mkdirs();
//        File tempFile = File.createTempFile("image", null);
//        tempFile.deleteOnExit();
        FileOutputStream outputStream = new FileOutputStream(tempFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.close();
        inputStream.close();

        // Verify that the downloaded content is a valid image
        BufferedImage image = ImageIO.read(tempFile);
        if (image == null) {
            throw new IOException("Invalid image content");
        }

        // ensure file exists
        if (!tempFile.exists()) {
            throw new RuntimeException("File not found at " + tempFile.getAbsolutePath());
        }

        return tempFile;
    }

    private static Set<String> englishWordsCache = null;
    private static int countEnglishWords(String text) {
        if (englishWordsCache == null) {
            englishWordsCache = loadEnglishWordList("/ocr/words.txt");
        }
        String[] words = text.split("\\s+");
        int count = 0;
        for (String word : words) {
            if (isValidEnglishWord(word, englishWordsCache)) {
                count++;
            }
        }
        return count;
    }

    private static Set<String> loadEnglishWordList(String filePath) {
        Set<String> englishWords = new HashSet<>();
        String text = FileUtil.readFile(filePath);
        for (String line : text.split("\n")) {
            englishWords.add(line.trim().toLowerCase());
        }
        return englishWords;
    }

    private static boolean isValidEnglishWord(String word, Set<String> englishWords) {
        // Convert the word to lowercase for case-insensitive matching
        word = word.toLowerCase();
        return englishWords.contains(word);
    }

    private static final String OCR_API_URL = "https://api.ocr.space/parse/image"; // OCR API Endpoints

    public static String convertImageUrlToText(String apiKey, boolean isOverlayRequired, String imageUrl, String language) {
        try {
            URL obj = new URL(OCR_API_URL);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            // Add request header
            con.setRequestMethod("POST");
            con.setRequestProperty("User-Agent", "Mozilla/5.0");
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

            JSONObject postDataParams = new JSONObject();
            postDataParams.put("apikey", apiKey);
            postDataParams.put("isOverlayRequired", isOverlayRequired);
            postDataParams.put("url", imageUrl);
            postDataParams.put("language", language);

            // Send post request
            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(getPostDataString(postDataParams));
            wr.flush();
            wr.close();

            // Read the response
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Return the result
            return response.toString();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private static String getPostDataString(JSONObject params) throws Exception {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (String key : params.keySet()) {
            Object value = params.get(key);

            if (first) {
                first = false;
            } else {
                result.append("&");
            }

            result.append(URLEncoder.encode(key, "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(value.toString(), "UTF-8"));
        }
        return result.toString();
    }

    public static String getText(String imageUrl) {
        if (!Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OCR.OCR_SPACE_KEY.isEmpty()) {
            try {
                return getTextAPI(imageUrl);
            } catch (IOException | IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        return getTextLocal(imageUrl, ImageType.CLEAR);
    }

    // Example usage:
    public static String getTextAPI(String imageUrl) throws IOException {
        String endpoint = "https://api.ocr.space/parse/imageurl?apikey=" + Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OCR.OCR_SPACE_KEY + "&isTable=true&OCREngine=2&url=";

        String url = endpoint + imageUrl;
        String jsonStr = FileUtil.readStringFromURL(PagePriority.API_OCR, url);
        System.out.println(jsonStr);
        JSONObject json = new JSONObject(jsonStr);
        // ParsedResults > ParsedText
        String parsedText = null;
        String errorMessage = null;

        if (json.has("ParsedResults")) {
            JSONArray parsedResultsArray = json.getJSONArray("ParsedResults");
            if (parsedResultsArray.length() > 0) {
                JSONObject parsedResults = parsedResultsArray.getJSONObject(0);
                if (parsedResults.has("ParsedText")) {
                    parsedText = parsedResults.getString("ParsedText");
                }
            }
        }

        if (json.has("ErrorMessage")) {
            errorMessage = json.get("ErrorMessage") + "";
        }

        if (parsedText != null) {
            return parsedText;
        } else if (errorMessage != null) {
            throw new IllegalArgumentException(errorMessage);
        } else {
            throw new IllegalArgumentException("Unknown result: Neither ParsedResults > ParsedText nor ErrorMessage found:\n" + jsonStr);
        }
    }

    public static byte[] addWatermark(BufferedImage image, String watermarkText2, Color color, float opacity, Font font, boolean repeat) {
        try {
            String[] words = watermarkText2.replaceAll("\\n", "\n").split("\n");
            Graphics2D g2d = (Graphics2D) image.getGraphics();

            AlphaComposite alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity);
            g2d.setComposite(alphaChannel);
            g2d.setColor(color);

            int fontSize = 1024;
            font = setFontSize(font, fontSize);
            g2d.setFont(font);
            FontMetrics fontMetrics = g2d.getFontMetrics();
            Rectangle2D rect;

            // Word wrap
            StringBuilder currentLine = new StringBuilder(words[0]);
            java.util.List<String> lines = new ArrayList<>();
            for (int i = 1; i < words.length; i++) {
                if (fontMetrics.stringWidth(currentLine + words[i]) < image.getWidth()) {
                    currentLine.append(" ").append(words[i]);
                } else {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(words[i]);
                }
            }
            lines.add(currentLine.toString());

            // Scale font to fit image width and height
            for (String line : lines) {
                while (true) {
                    if (fontSize <= 16) break;
                    font = new Font("Arial", Font.BOLD, fontSize);
                    g2d.setFont(font);
                    fontMetrics = g2d.getFontMetrics();
                    rect = fontMetrics.getStringBounds(line, g2d);
                    if (rect.getWidth() > image.getWidth() || rect.getHeight() * lines.size() > image.getHeight()) {
                        fontSize--;
                    } else {
                        break;
                    }
                }
            }

            // Draw each line of the watermark text
            int lineHeight = g2d.getFontMetrics().getAscent();
            int padding = g2d.getFontMetrics().getHeight() - g2d.getFontMetrics().getAscent();
            int totalTextHeight = lines.size() * lineHeight;
            System.out.println("Line " + lineHeight + " | " + padding);
            int y = (repeat ? 0 : (image.getHeight() - totalTextHeight) / 2) - padding;
            while (y < image.getHeight()) {
                for (String line : lines) {
                    int x = (image.getWidth() - fontMetrics.stringWidth(line)) / 2;
                    g2d.drawString(line, x, y += lineHeight);
                }
                if (!repeat) break;
            }

            // Write image to bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            baos.flush();
            byte[] imageInByte = baos.toByteArray();
            baos.close();

            g2d.dispose();

            return imageInByte;
        } catch (IOException ex) {
            System.err.println(ex);
            throw new RuntimeException(ex);
        }
    }

    public static BufferedImage image(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        BufferedImage image = ImageIO.read(url);
        return image;
    }

    public static Color getAverageColor(BufferedImage image) {
        long sumRed = 0;
        long sumGreen = 0;
        long sumBlue = 0;
        long totalPixels = (long) image.getWidth() * image.getHeight();

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixel = new Color(image.getRGB(x, y));
                sumRed += pixel.getRed();
                sumGreen += pixel.getGreen();
                sumBlue += pixel.getBlue();
            }
        }

        int averageRed = (int) (sumRed / totalPixels);
        int averageGreen = (int) (sumGreen / totalPixels);
        int averageBlue = (int) (sumBlue / totalPixels);

        return new Color(averageRed, averageGreen, averageBlue);
    }

    public static float getLuminance(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return hsb[2];
    }

    public static Color getDefaultWatermarkColor(BufferedImage image) {
        Color color = getAverageColor(image);
        return getLuminance(color) < 0.5 ? Color.LIGHT_GRAY : Color.DARK_GRAY;
    }

    public static Font setFontSize(Font font, int size) {
        return font.deriveFont((float) size);
    }
}

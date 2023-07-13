package link.locutus.discord.gpt;

import ai.djl.util.Platform;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.moderation.Moderation;
import com.theokanning.openai.moderation.ModerationRequest;
import link.locutus.discord.config.Settings;
import link.locutus.discord.gpt.imps.AdaEmbedding;
import link.locutus.discord.gpt.imps.GPTSummarizer;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.math.ArrayUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class GptHandler {
    public final IEmbeddingDatabase embeddingDatabase;
    private final Encoding chatEncoder;
    private final EncodingRegistry registry;
    private final OpenAiService service;
    private final Platform platform;

    public GptHandler() throws SQLException, ClassNotFoundException {
        this.registry = Encodings.newDefaultEncodingRegistry();
        this.service = new OpenAiService(Settings.INSTANCE.OPENAI_API_KEY, Duration.ofSeconds(50));

        this.embeddingDatabase = new AdaEmbedding(registry, service);

        this.chatEncoder = registry.getEncodingForModel(ModelType.GPT_3_5_TURBO);

        this.platform = Platform.detectPlatform("pytorch");
        this.summarizer = new GPTSummarizer(registry, service);
    }

    public OpenAiService getService() {
        return service;
    }

    public double getSimilarity(String a, String b) {
        return ArrayUtil.cosineSimilarity(getEmbedding(a), getEmbedding(b));
    }

    public int getChatTokenSize(String text) {
        return chatEncoder.encode(text).size();
    }

    public void checkThrowModeration(List<Moderation> moderations, String text) {
        for (Moderation result : moderations) {
            if (result.isFlagged()) {
                String message = "Your submission has been flagged as inappropriate:\n" +
                        "```json\n" + result.toString() + "\n```\n" +
                        "The content submitted:\n" +
                        "```json\n" + text.replaceAll("```", "\\`\\`\\`") + "\n```";
                throw new IllegalArgumentException(message);
            }
        }
    }

    private float[] getEmbeddingApi(String text, boolean checkModeration) {
        if (checkModeration) {
            List<Moderation> modResult = checkModeration(text);
            checkThrowModeration(modResult, text);
        }
        return embeddingDatabase.fetchEmbedding(text);
    }

    public float[] getExistingEmbedding(int type, String text) {
        return this.embeddingDatabase.getEmbedding(type, text);
    }

    public float[] getEmbedding(String text) {
        return getEmbedding(-1, null, text, false);
    }

    public float[] getEmbedding(int type, @Nullable String id, String text, boolean saveContent) {
        float[] existing = this.embeddingDatabase.getEmbedding(text);
        if (existing == null) {
            System.out.println("Fetch embedding: " + text);
            existing = getEmbeddingApi(text, type < 0);
            embeddingDatabase.setEmbedding(type, id, text, existing, saveContent);
        }
        return existing;
    }

}

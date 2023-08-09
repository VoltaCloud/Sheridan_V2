package link.locutus.discord.gpt;

import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.ModelNotFoundException;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AttackCursorFactory;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors.VictoryCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.imps.EmbeddingInfo;
import link.locutus.discord.gpt.imps.EmbeddingType;
import link.locutus.discord.gpt.imps.IEmbeddingAdapter;
import link.locutus.discord.gpt.pwembed.PWGPTHandler;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.math.ArrayUtil;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

public class GPTTest {
    public static void main2(String[] args) throws SQLException, ClassNotFoundException, ModelNotFoundException, MalformedModelException, IOException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        GptHandler handler = new GptHandler();

        IEmbeddingDatabase embedding = handler.getEmbeddings();

//        List<ModerationResult> blah = handler.getModerator().moderate("self harm");
//        System.out.println(blah);

//        String response = handler.getText2text().generate("What is your name?");
//        System.out.println(response);
    }

    public static void main(String[] args) throws SQLException, LoginException, InterruptedException, ClassNotFoundException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());

        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        Settings.INSTANCE.WEB.PORT_HTTPS = 0;
        Settings.INSTANCE.WEB.PORT_HTTP = 8000;
        Settings.INSTANCE.WEB.REDIRECT = "http://localhost";
        Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
        Settings.INSTANCE.ENABLED_COMPONENTS.disableTasks();
        Settings.INSTANCE.TASKS.UNLOAD_ATTACKS_AFTER_DAYS = 0;
        Settings.INSTANCE.TASKS.UNLOAD_WARS_AFTER_DAYS = 0;
        Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.EVENTS = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.WEB = false;

        Locutus locutus = Locutus.create();
        locutus.start();

        String input = "A custom sheet for nation info";

        PWGPTHandler pwGpt = locutus.getCommandManager().getV2().getPwgptHandler();
        pwGpt.registerDefaults();
        GptHandler gptHandler = pwGpt.getHandler();
        IEmbeddingDatabase embeddings = gptHandler.getEmbeddings();
        EmbeddingType userInput = EmbeddingType.User_Input;
        EmbeddingSource userInputSrc = pwGpt.getSource(userInput);

        System.out.println("Source " + userInputSrc);
        if (userInputSrc == null) {
            System.exit(0);
        }

        EmbeddingSource commandSource = pwGpt.getSource(EmbeddingType.Command);
        IEmbeddingAdapter<ParametricCallable> cmdAdapter = pwGpt.getAdapter(commandSource);
        Set<ParametricCallable> callables = locutus.getCommandManager().getV2().getCommands().getParametricCallables(f -> f.getPrimaryCommandId().equalsIgnoreCase("nationsheet"));
        ParametricCallable nationSheet = callables.iterator().next();
        long hash = cmdAdapter.getHash(nationSheet);

        System.out.println("--- nation sheet ---");
        System.out.println("Hash: " + hash);
        System.out.println("Text: " + embeddings.getText(hash));
        float[] vector1 = embeddings.getEmbedding(userInputSrc, input, null);
        float[] vector2 = embeddings.getEmbedding(userInputSrc, embeddings.getText(hash), null);

        System.out.println("Similarity " + ArrayUtil.cosineSimilarity(vector1, vector2));

        Set<EmbeddingSource> sources = pwGpt.getSources(null, true);
        sources.remove(userInputSrc);

        System.out.println("Fetching closest for " + input);

        List<EmbeddingInfo> closest = embeddings.getClosest(userInputSrc, input, 100, sources, new BiPredicate<EmbeddingSource, Long>() {
            @Override
            public boolean test(EmbeddingSource embeddingSource, Long hash) {
                return true;
            }
        }, gptHandler::checkModeration);

        for (EmbeddingInfo info : closest) {
            String text = embeddings.getText(info.hash);
            System.out.println(info.source.source_name + " | " + text + " | " + info.distance);
        }
        System.exit(0);
    }
}
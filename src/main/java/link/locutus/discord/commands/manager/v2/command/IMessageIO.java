package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.config.Settings;
import net.dv8tion.jda.api.entities.TextChannel;
import org.json.JSONObject;

import javax.annotation.CheckReturnValue;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public interface IMessageIO {
    public IMessageBuilder getMessage();

    @CheckReturnValue
    public IMessageBuilder create();

    default CompletableFuture<IMessageBuilder> send(String message) {
        return send(create().append(message));
    }

    public CompletableFuture<IMessageBuilder> send(IMessageBuilder builder);

    public IMessageIO update(IMessageBuilder builder, long id);

    public IMessageIO delete(long id);

    @CheckReturnValue
    default IMessageBuilder paginate(String title, JSONObject command, Integer page, int perPage, List<String> results, String footer, boolean inline) {
        IMessageBuilder message = getMessage();
        TextChannel t = null;
        if (message == null || message.getAuthor() == null || message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID) {
            System.out.println("remove:||Create new");
            message = create();
        } else {
            inline = true;
        }
        return message.paginate(title, command, page, perPage, results, footer, inline);
    }

    long getIdLong();
}
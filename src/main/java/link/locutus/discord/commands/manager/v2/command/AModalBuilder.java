package link.locutus.discord.commands.manager.v2.command;

import net.dv8tion.jda.api.interactions.components.text.TextInput;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class AModalBuilder implements IModalBuilder {
    private final IMessageIO parent;
    private long id;
    private String title;
    private List<TextInput> inputs = new ArrayList<>();

    public AModalBuilder(IMessageIO io, String title) {
        this.parent = io;
        this.title = title;
    }

    @Override
    public AModalBuilder setTitle(String title) {
        this.title = title;
        return this;
    }

    public IModalBuilder addInput(TextInput input) {
        inputs.add(input);
        return this;
    }

    public IMessageIO getParent() {
        return parent;
    }

    public CompletableFuture<IModalBuilder> send() {
        return parent.send(this);
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<TextInput> getInputs() {
        return inputs;
    }
}
package me.x1no4ekk.core.command;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Aborts a command and optionally sends a message (by key from messages.yml) to the sender.
 * Stackless: it is control flow, not an error.
 */
public final class CommandFailure extends RuntimeException {

    private final String key;
    private final transient TagResolver[] resolvers;

    public CommandFailure(String key, TagResolver... resolvers) {
        super(key, null, false, false);
        this.key = key;
        this.resolvers = resolvers;
    }

    /**
     * @return message key, or {@code null} if the command already replied
     */
    public String key() {
        return key;
    }

    public TagResolver[] resolvers() {
        return resolvers;
    }
}

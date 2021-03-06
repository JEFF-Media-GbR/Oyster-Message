package com.jeff_media.oyster.message;

import com.jeff_media.oyster.Msg;
import com.jeff_media.oyster.formatters.FormattingPhase;
import com.jeff_media.oyster.formatters.MessageFormatter;
import net.kyori.adventure.platform.bukkit.BukkitComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Represents an immutable message. Messages are defined by YAML files and contain any amount of lines.
 * <p>
 * Example usage:
 * <pre>
 * Message joinMessage = Msg.get("join-message")
 *              .placeholders(event.getPlayer())
 *              .replace("{something}","something else");
 * joinMessage.sendToAll(player -> player.getWorld().equals(event.getPlayer().getWorld());
 * </pre>
 */
public class Message extends AbstractMessage {

    public static final Message EMPTY = new Message(new ArrayList<>());

    private static final MiniMessage MINIMESSAGE = MiniMessage.miniMessage();
    // Don't use System.lineSeparator(). It'd cause problems on Windows and macOS.
    private static final String NEW_LINE = "\n";

    private final List<String> lines;

    public Message(final String line) {
        this(Collections.singletonList(line));
    }

    public Message(final List<String> lines) {
        super();
        this.lines = lines.stream().map(line -> {
            if(line == null) return "";
            for(final MessageFormatter formatter : Msg.getMessageFormatters(FormattingPhase.PARSE)) {
                line = formatter.format(line, null);
            }
            return line;
        }).collect(Collectors.toList());
    }


    @Override
    public Message placeholders(final CommandSender sender) {
        return new Message(lines.stream().map(line -> applyPluginPlaceholders(line, sender)).collect(Collectors.toList()));
    }

    private String applyPluginPlaceholders(String line, final CommandSender sender) {
        for (final MessageFormatter hook : Msg.getMessageFormatters(FormattingPhase.MANUAL)) {
            line = hook.format(line, sender);
        }
        return line;
    }

    @Override
    public Message replace(final String... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("Length of replacement array must be a multiple of two");
        }
        return new Message(lines.stream().map(line -> {
            for (int i = 0; i < placeholders.length; i += 2) {
                line = line.replace(placeholders[0], placeholders[1]);
            }
            return line;
        }).collect(Collectors.toList()));

    }


    public void sendTo(final CommandSender sender) {
        bake().forEach(component -> Msg.audience().sender(sender).sendMessage(component));
    }

    /**
     * Converts this message into a list of line-separated {@link Component}s
     */
    public List<Component> asAdventureComponents() {
        return bake();
    }

    private List<Component> bake() {
        return lines.stream().map(line -> {
            for(final MessageFormatter formatter : Msg.getMessageFormatters(FormattingPhase.BAKE)) {
                line = formatter.format(line, null);
            }
            return MINIMESSAGE.deserialize(line);
        }).collect(Collectors.toList());
    }

    /**
     * Sends an actionbar message to all online players matching the given {@link Predicate}.
     *
     * @see #sendActionbarTo(CommandSender)
     */
    public void sendActionbarTo(final Predicate<? super Player> predicate) {
        players(predicate).forEach(this::sendActionbarTo);
    }

    /**
     * Sends an actionbar message to the given {@link CommandSender}. If this message contains of more than one line, only
     * the first line will be sent. Click and Hover events will be lost.
     */
    public void sendActionbarTo(final CommandSender sender) {
        Msg.audience().sender(sender).sendActionBar(getFirstLineAsAdventureComponent());
    }

    public @NotNull Component getFirstLineAsAdventureComponent() {
        final String firstLine = getFirstLine();
        if (firstLine.isEmpty()) {
            return Component.empty();
        } else {
            return MINIMESSAGE.deserialize(firstLine);
        }
    }

    private @NotNull String getFirstLine() {
        if (lines.isEmpty()) return "";
        return lines.get(0);
    }

    /**
     * Sends an actionbar message to all online players.
     *
     * @see #sendActionbarTo(CommandSender)
     */
    public void sendActionbarToAll() {
        allPlayers().forEach(this::sendActionbarTo);
    }

    /**
     * Converts this message into a legacy-formatted String.
     *
     * @see #asStringList()
     */
    public String asString() {
        return String.join(NEW_LINE, asStringList());
    }

    /**
     * Converts this message into a line-separated list of legacy-formatted Strings. Click and Hover events will be lost.
     */
    public List<String> asStringList() {
        return bake().stream().map(component -> BukkitComponentSerializer.legacy().serialize(component)).collect(Collectors.toList());
    }

    public String asPlainText() {
        return String.join(NEW_LINE, asPlainTextList());
    }

    public List<String> asPlainTextList() {
        return bake().stream().map(component -> PlainTextComponentSerializer.plainText().serialize(component)).collect(Collectors.toList());
    }

    /**
     * Converts this message into a line-separated list of JSON Strings.
     */
    public List<String> asJsonList() {
        return bake().stream().map(component -> BukkitComponentSerializer.gson().serialize(component)).collect(Collectors.toList());
    }

    /**
     * Converts this message into a line-separated list of {@link BaseComponent[]}s
     */
    public List<BaseComponent[]> asBaseComponentsList() {
        return bake().stream().map(component -> BungeeComponentSerializer.get().serialize(component)).collect(Collectors.toList());
    }

    @Override
    public int hashCode() {
        return Objects.hash(lines);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final Message message = (Message) obj;
        return lines.equals(message.lines);
    }

    @Override
    public String toString() {
        return "Message{" + "lines=" + lines + "}";
    }
}

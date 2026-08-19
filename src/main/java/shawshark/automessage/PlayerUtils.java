package me.shawshark.automessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Small helper for sending legacy '&amp;'-coded messages through Adventure
 * instead of the deprecated {@code ChatColor} / raw string APIs.
 */
public final class PlayerUtils {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private PlayerUtils() {
    }

    public static Component colour(String legacyText) {
        return LEGACY.deserialize(legacyText);
    }

    public static void msg(Player player, String msg) {
        msg(player, msg, true);
    }

    public static void msg(Player player, String msg, boolean colour) {
        Component component = colour ? colour(msg) : Component.text(msg);
        if (player == null) {
            System.out.println(msg);
        } else {
            player.sendMessage(component);
        }
    }

    public static void msg(CommandSender sender, String msg) {
        Component component = colour(msg);
        if (sender == null) {
            System.out.println(msg);
        } else {
            sender.sendMessage(component);
        }
    }
}

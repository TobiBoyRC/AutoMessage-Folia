package me.shawshark.automessage;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AutoMessager extends JavaPlugin implements CommandExecutor {

    /** Ordered pool of messages loaded from config.yml. Index-based, no gap-prone key map. */
    private List<String> messages = new ArrayList<>();

    /** Index (0-based) of the next message to send in sequential mode. */
    private int nextMessageIndex = 0;

    /** Last three sent indices, used to avoid immediate repeats in random mode. */
    private final int[] recentHistory = {-1, -1, -1};

    private int timerSeconds = 40;
    private boolean random;
    private boolean useTellRaw;

    /**
     * Folia has no single global tick thread, so a repeating task that is not tied to a
     * specific chunk/entity (like a broadcast to every player) must be scheduled on the
     * GlobalRegionScheduler and tracked via its ScheduledTask handle rather than a
     * BukkitScheduler task id (Folia does not support numeric task ids for this kind of
     * cross-region task).
     */
    private ScheduledTask broadcastTask;

    private static final Random RANDOM = new Random();

    @Override
    public void onEnable() {
        var command = getCommand("automessage");
        if (command != null) {
            command.setExecutor(this);
        }

        getConfig().addDefault("timer", 10);

        List<String> defaultMessages = new ArrayList<>();
        defaultMessages.add("&eThis is default message 1, Please change this in &cconfig.yml");
        defaultMessages.add("&cThis is default message 2, You can change this in &cconfig.yml");
        defaultMessages.add("&fThis is default message 3, You can change this in &cconfig.yml");
        defaultMessages.add("&bThis is default message 4, You can change this in &cconfig.yml");
        defaultMessages.add("&6This is default message 5, You can change this in &cconfig.yml");
        defaultMessages.add("&7This is default message 6, You can change this in &cconfig.yml");

        List<String> tellRawDefaultMessages = new ArrayList<>();
        tellRawDefaultMessages.add("{\"text\":\"[Get more info on TellRaws here: minecraft.tools/en/tellraw.php ]\",\"color\":\"dark_purple\"}");
        tellRawDefaultMessages.add("{\"text\":\"[This is a test message!] \",\"color\":\"aqua\"}");
        tellRawDefaultMessages.add("[\"\",{\"text\":\"Click me to open URL!\",\"color\":\"red\",\"bold\":true,\"underlined\":false,\"clickEvent\":{\"action\":\"open_url\",\"value\":\"https://www.google.com\"}}]");
        tellRawDefaultMessages.add("{\"text\":\"Hover over me for help text!\",\"color\":\"yellow\",\"bold\":true,\"hoverEvent\":{\"action\":\"show_text\",\"value\":{\"text\":\"\",\"extra\":[{\"text\":\"I am a hint!\",\"color\":\"yellow\",\"bold\":true}]}}}");
        tellRawDefaultMessages.add("{\"text\":\"Click me to run help command!\",\"color\":\"yellow\",\"bold\":true,\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/help\"}}");

        getConfig().addDefault("Random", false);
        getConfig().addDefault("UseTellRawMessages", false);
        getConfig().addDefault("Messages", defaultMessages);
        getConfig().addDefault("TellRawMessages", tellRawDefaultMessages);
        getConfig().options().copyDefaults(true);
        saveConfig();

        loadMessages();
    }

    @Override
    public void onDisable() {
        cancelBroadcastTask();
    }

    public void loadMessages() {
        messages.clear();
        nextMessageIndex = 0;
        recentHistory[0] = recentHistory[1] = recentHistory[2] = -1;

        random = getConfig().getBoolean("Random", false);
        useTellRaw = getConfig().getBoolean("UseTellRawMessages", false);
        timerSeconds = getConfig().getInt("timer", 10);

        List<String> source = useTellRaw
                ? getConfig().getStringList("TellRawMessages")
                : getConfig().getStringList("Messages");

        if (source != null) {
            messages.addAll(source);
        }

        scheduleBroadcastTask();
    }

    private void scheduleBroadcastTask() {
        cancelBroadcastTask();

        long delayTicks = 100L;
        long periodTicks = Math.max(1L, (long) timerSeconds * 20L);

        // GlobalRegionScheduler: this task does not belong to any single region because it
        // touches all online players across every region. Running it via a per-region
        // scheduler (or the legacy BukkitScheduler) would throw on Folia.
        broadcastTask = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(this, task -> broadcastNextMessage(), delayTicks, periodTicks);
    }

    private void cancelBroadcastTask() {
        if (broadcastTask != null) {
            broadcastTask.cancel();
            broadcastTask = null;
        }
    }

    private void broadcastNextMessage() {
        if (messages.isEmpty()) {
            return;
        }

        String raw = random ? pickRandomMessage() : pickSequentialMessage();
        if (raw == null) {
            return;
        }

        if (useTellRaw) {
            sendTellRaw(raw);
        } else {
            broadcastLegacy(raw);
        }
    }

    private String pickSequentialMessage() {
        String msg = messages.get(nextMessageIndex);
        nextMessageIndex = (nextMessageIndex + 1) % messages.size();
        return msg;
    }

    /**
     * Picks a random message, avoiding the last three sent, without the original's off-by-one
     * bias (the old getRandom() remapped 0 -> 1, doubling message #1's odds and making the
     * final message unreachable).
     */
    private String pickRandomMessage() {
        if (messages.size() == 1) {
            return messages.get(0);
        }

        int index;
        int attempts = 0;
        do {
            index = RANDOM.nextInt(messages.size());
            attempts++;
            // Safety valve: if history blocks every index (tiny message pools), stop refusing.
        } while (isRecentlyUsed(index) && attempts < 20);

        recentHistory[2] = recentHistory[1];
        recentHistory[1] = recentHistory[0];
        recentHistory[0] = index;

        return messages.get(index);
    }

    private boolean isRecentlyUsed(int index) {
        return index == recentHistory[0] || index == recentHistory[1] || index == recentHistory[2];
    }

    private void broadcastLegacy(String raw) {
        Component component = PlayerUtils.colour(raw);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
        Bukkit.getConsoleSender().sendMessage(component);
    }

    private void sendTellRaw(String json) {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        // Replaces the old "/tellraw @a <json>" dispatchCommand workaround: we now deserialize
        // the same JSON format directly into an Adventure Component and send it via the API,
        // which is both Folia-safe (no command dispatch/thread-context surprises) and faster.
        Component component = GsonComponentSerializer.gson().deserialize(json);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("automessage.reload")) {
            PlayerUtils.msg(sender, "You don't have permissions to use this command");
            return true;
        }

        reloadConfig();
        saveConfig();
        loadMessages();

        PlayerUtils.msg(sender, "&aYou've reloaded the config.yml config file.");
        return true;
    }
}

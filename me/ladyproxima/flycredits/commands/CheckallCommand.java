package me.ladyproxima.flycredits.commands;

import me.ladyproxima.flycredits.FlyCredits;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CheckallCommand implements ICommand {

    String permissions = "flycredits.use";

    public boolean executeCommand(CommandSender sender, String[] args) {
        for (Map.Entry<UUID, HashMap<String, Integer>> playerWorldMap : FlyCredits.watchedPlayers.entrySet()) {
            sender.sendMessage("");
            OfflinePlayer target = Bukkit.getServer().getOfflinePlayer(playerWorldMap.getKey());
            FlyCredits.sendNice(sender, FlyCredits.NAME_COLOR + target.getName() + FlyCredits.MESSAGE_COLOR + ":");
            for (Map.Entry<String, Integer> stringFlyTimeEntry : playerWorldMap.getValue().entrySet()) {
                FlyCredits.sendNice(sender, "Verbleibende Zeit in Welt " + FlyCredits.WORLD_COLOR + stringFlyTimeEntry.getKey() + FlyCredits.MESSAGE_COLOR + ": " + FlyCredits.TIME_COLOR + FlyCredits.secToTime(stringFlyTimeEntry.getValue()) + FlyCredits.MESSAGE_COLOR + ".");
            }
        }
        return true;
    }

    public String requiredPermissions() {
        return permissions;
    }

}

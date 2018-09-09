package me.ladyproxima.flycredits.commands;

import me.ladyproxima.flycredits.FlyCredits;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class CheckCommand implements ICommand {

    String permissions = "flycredits.check";

    public boolean executeCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player) && args.length < 2){
            FlyCredits.sendNice(sender, "Für diesen Befehl musst du ein Spieler sein.");
            return true;
        }
        if (args.length == 2 && !FlyCredits.perms.has(sender, "flycredits.check.others") && !sender.getName().equalsIgnoreCase(args[1])){
            sender.sendMessage(FlyCredits.getConfigStringColored("no_permission_message"));
            return true;
        }

        OfflinePlayer target;
        if (args.length > 1) {
            target = Bukkit.getServer().getOfflinePlayer(args[1]);
        } else {
            target = (Player) sender;
        }

        if (FlyCredits.watchedPlayers.containsKey(target.getUniqueId())) {
            for (Map.Entry<String, Integer> worldTimeLeftMap : FlyCredits.watchedPlayers.get(target.getUniqueId()).entrySet()) {
                FlyCredits.sendNice(sender, "Verbleibende Zeit in Welt " + FlyCredits.WORLD_COLOR + worldTimeLeftMap.getKey() + FlyCredits.MESSAGE_COLOR + ": " + FlyCredits.TIME_COLOR + FlyCredits.secToTime(worldTimeLeftMap.getValue()) + FlyCredits.MESSAGE_COLOR + ".");
            }
        } else {
            FlyCredits.sendNice(sender, "Keine verbleibende Flugzeit mehr.");
        }
        return true;
    }

    public String requiredPermissions() {
        return permissions;
    }

}

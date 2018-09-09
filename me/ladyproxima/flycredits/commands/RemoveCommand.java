package me.ladyproxima.flycredits.commands;

import me.ladyproxima.flycredits.FlyCredits;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.UUID;

public class RemoveCommand implements ICommand {

    String permissions = "flycredits.use";

    public boolean executeCommand(CommandSender sender, String[] args) {
        if (args.length != 4){
            return false;
        }

        OfflinePlayer target = Bukkit.getServer().getOfflinePlayer(args[1]);

        try {

            String time = "pt" + args[2].replaceAll("-", "");
            Duration t = Duration.parse(time);
            int addSeconds = (int) t.getSeconds();
            String world = args[3].toLowerCase();
            removeTime(target.getUniqueId(), world, addSeconds);
            int newTime = FlyCredits.watchedPlayers.get(target.getUniqueId()).containsKey(world) ? FlyCredits.watchedPlayers.get(target.getUniqueId()).get(world) : 0;
            FlyCredits.sendNice(sender, "Neue Flugzeit für Spieler " + FlyCredits.NAME_COLOR + target.getName() + FlyCredits.MESSAGE_COLOR + " in Welt " + FlyCredits.WORLD_COLOR + world + FlyCredits.MESSAGE_COLOR + ": " + FlyCredits.TIME_COLOR + FlyCredits.secToTime(newTime) + FlyCredits.MESSAGE_COLOR + ".");

        } catch (NullPointerException e) {
            FlyCredits.sendNice(sender, "Spieler war noch nie auf dem Server.");

        } catch (DateTimeParseException e) {
            FlyCredits.sendNice(sender, "Bitte Zeitformat einhalten, z.B.: " + FlyCredits.TIME_COLOR + "12h50m20s" + FlyCredits.MESSAGE_COLOR + ".");
        }
        return true;
    }

    public void removeTime(UUID uuid, String world, int sec) {
        int oldTime = FlyCredits.watchedPlayers.get(uuid).get(world);
        int newTime = (oldTime) - sec > 0 ? oldTime - sec : 0;

        FlyCredits.watchedPlayers.get(uuid).put(world, newTime);
        FlyCredits.updateDB(newTime, uuid.toString(), world);
    }

    public String requiredPermissions() {
        return permissions;
    }

}

package me.ladyproxima.flycredits.commands;

import me.ladyproxima.flycredits.FlyCredits;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.UUID;

public class AddCommand implements ICommand {

    String permissions = "flycredits.use";

    public boolean executeCommand(CommandSender sender, String[] args) {
        if (args.length < 4){
            return false;
        }

        OfflinePlayer target = Bukkit.getServer().getOfflinePlayer(args[1]);

        try {

            String time = "pt" + args[2].replaceAll("-", "", -1);
            Duration t = Duration.parse(time);
            int addSeconds = (int) t.getSeconds();
            String world = args[3].toLowerCase();
            addTime(target, world, addSeconds);
            FlyCredits.sendNice(sender, "Spieler " + FlyCredits.NAME_COLOR + target.getName() + FlyCredits.MESSAGE_COLOR + " erfolgreich " +
                    FlyCredits.TIME_COLOR + FlyCredits.secToTime(addSeconds) + FlyCredits.MESSAGE_COLOR + " in Welt " + FlyCredits.WORLD_COLOR + world + FlyCredits.MESSAGE_COLOR +
                    " hinzugefügt. Neue Flugzeit: " +FlyCredits. TIME_COLOR + FlyCredits.secToTime(FlyCredits.watchedPlayers.get(target.getUniqueId()).get(world)) + FlyCredits.MESSAGE_COLOR + ".");

        } catch (NullPointerException e) {
            FlyCredits.sendNice(sender, "Spieler war noch nie auf dem Server.");

        } catch (DateTimeParseException e) {
            FlyCredits.sendNice(sender, "Bitte Zeitformat einhalten, z.B.: " + FlyCredits.TIME_COLOR + "12h50m20s" + FlyCredits.MESSAGE_COLOR + ".");
        }
        return true;
    }

    public void addTime(OfflinePlayer p, String world, int sec) throws NullPointerException {
        UUID uuid = p.getUniqueId();
        if (!FlyCredits.watchedPlayers.containsKey(uuid)) {

            HashMap<String, Integer> hm1 = new HashMap<>();

            hm1.put(world, sec);
            FlyCredits.watchedPlayers.put(uuid, hm1);
            FlyCredits.insertDB(uuid.toString(), world, sec);
        } else {
            if (FlyCredits.watchedPlayers.get(uuid).containsKey(world)) {
                FlyCredits.watchedPlayers.get(uuid).put(world, FlyCredits.watchedPlayers.get(uuid).get(world) + sec);
                FlyCredits. updateDB(FlyCredits.watchedPlayers.get(p.getUniqueId()).get(world), uuid.toString(), world);
            } else {
                FlyCredits.watchedPlayers.get(uuid).put(world, sec);
                FlyCredits.insertDB(uuid.toString(), world, sec);
            }

        }

        //added time, so player will certainly some time left - giving permission
        FlyCredits.perms.playerAdd(world, p, "essentials.fly");
    }

    public String requiredPermissions() {
        return permissions;
    }

}

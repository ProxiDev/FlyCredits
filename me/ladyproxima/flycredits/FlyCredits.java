package me.ladyproxima.flycredits;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

public class FlyCredits extends JavaPlugin implements Listener {


    HashMap<UUID, HashMap<String, Integer>> watchedPlayers = new HashMap<>();
    HashMap<UUID, HashMap<String, Integer>> playerTaskMap = new HashMap<>();

    FileConfiguration config = getConfig();
    Permission perms = null;

    String NAME_COLOR = "";
    String MESSAGE_COLOR = "";
    String WORLD_COLOR = "";
    String TIME_COLOR = "";


    Logger logger;

    Connection connection;

    @Override
    public void onEnable() {
        logger = getLogger();
        logger.info("Enabling FlyCredits...");

        setupConfigVariables();

        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        perms = rsp.getProvider();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("fc").setExecutor(this);

        //Setting up the DB
        setupDB();

        loadFlyCredits();

        logger.info("Enabled FlyCredits!");
    }

    @Override
    public void onDisable() {
        try {
            for (Map.Entry<UUID, HashMap<String, Integer>> uuidHashMapEntry : watchedPlayers.entrySet()) {
                for (Map.Entry<String, Integer> stringIntegerEntry : uuidHashMapEntry.getValue().entrySet()) {
                    UUID uuid = uuidHashMapEntry.getKey();
                    String world = stringIntegerEntry.getKey();
                    int timeLeft = stringIntegerEntry.getValue();
                    updateDB(timeLeft, uuid.toString(), world);
                    if (playerTaskMap.containsKey(uuid) && playerTaskMap.get(uuid).containsKey(world)) {
                        Bukkit.getScheduler().cancelTask(playerTaskMap.get(uuid).get(world));
                    }
                }
            }

            watchedPlayers.clear();
            playerTaskMap.clear();

            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.info("Disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int permLevel = 0;
        if (sender == Bukkit.getConsoleSender()) {
            permLevel = 10;
        } else {
            Player test = getServer().getPlayer(sender.getName());
            if (perms.has(test, "flycredits.check")) permLevel = 1;
            if (perms.has(test, "flycredits.use")) permLevel = 2;
        }

        if (permLevel < 1) {
            sender.sendMessage(getConfigStringColored("no_permission_message"));
            return true;
        }

        String subcommand = args.length > 0 ? args[0].toLowerCase() : "";

        if (args.length == 4 && subcommand.equals("add")) {
            if (permLevel < 2) {
                sender.sendMessage(getConfigStringColored("no_permission_message"));
                return true;
            }

            OfflinePlayer target = getServer().getOfflinePlayer(args[1]);

            try {
                Duration t = Duration.parse("pt" + args[2].replaceAll("-", ""));
                int addSeconds = (int) t.getSeconds();
                addTime(target, args[3].toLowerCase(), addSeconds);
                sendNice(sender, "Spieler " + NAME_COLOR + target.getName() + MESSAGE_COLOR + " erfolgreich " +
                        TIME_COLOR + secToTime(addSeconds) + MESSAGE_COLOR + " in Welt " + WORLD_COLOR + args[3].toLowerCase() + MESSAGE_COLOR +
                        " hinzugefügt. Neue Flugzeit: " + TIME_COLOR + secToTime(watchedPlayers.get(target.getUniqueId()).get(args[3].toLowerCase())) + MESSAGE_COLOR + ".");

            } catch (NullPointerException e) {
                sendNice(sender, "Spieler war noch nie auf dem Server.");

            } catch (Exception e) {
                sendNice(sender, "Bitte Zeitformat einhalten, z.B.: " + TIME_COLOR + "12h50m20s" + MESSAGE_COLOR + ".");
            }

        } else if (args.length == 4 && subcommand.equals("remove")) {
            if (permLevel < 2) {
                sender.sendMessage(getConfigStringColored("no_permission_message"));
                return true;
            }
            OfflinePlayer target = getServer().getOfflinePlayer(args[1]);
            try {

                Duration t = Duration.parse("pt" + args[2].replaceAll("-", ""));
                int addSeconds = (int) t.getSeconds();
                removeTime(target.getUniqueId(), args[3].toLowerCase(), addSeconds);
                int newTime = watchedPlayers.get(target.getUniqueId()).containsKey(args[3].toLowerCase()) ? watchedPlayers.get(target.getUniqueId()).get(args[3].toLowerCase()) : 0;
                sendNice(sender, "Neue Flugzeit für Spieler " + NAME_COLOR + target.getName() + MESSAGE_COLOR + " in Welt " + WORLD_COLOR + args[3].toLowerCase() + MESSAGE_COLOR + ": " + TIME_COLOR + secToTime(newTime) + MESSAGE_COLOR + ".");

            } catch (NullPointerException e) {
                sendNice(sender, "Spieler war noch nie auf dem Server.");

            } catch (Exception e) {
                sendNice(sender, "Bitte Zeitformat einhalten, z.B.: " + TIME_COLOR + "12h50m20s" + MESSAGE_COLOR + ".");
            }
        } else if (args.length > 0 && subcommand.equals("check")) {
            if (permLevel < 1) {
                sender.sendMessage(getConfigStringColored("no_permission_message"));
                return true;
            }
            OfflinePlayer target;
            if (args.length > 1) {
                target = getServer().getOfflinePlayer(args[1]);

            } else if (sender instanceof Player){
                target = (Player) sender;
            } else {
                sendNice(sender, "Für diesen Befehl musst du ein Spieler sein.");
                return true;
            }

            if (watchedPlayers.containsKey(target.getUniqueId())) {
                for (Map.Entry<String, Integer> worldTimeLeftMap : watchedPlayers.get(target.getUniqueId()).entrySet()) {
                    sendNice(sender, "Verbleibende Zeit in Welt " + WORLD_COLOR + worldTimeLeftMap.getKey() + MESSAGE_COLOR + ": " + TIME_COLOR + secToTime(worldTimeLeftMap.getValue()) + MESSAGE_COLOR + ".");
                }
            } else {
                sendNice(sender, "Keine verbleibende Flugzeit mehr.");
            }

        } else if (args.length > 0 && args[0].toLowerCase().equals("checkall")) {
            if (permLevel < 2) {
                sender.sendMessage(getConfigStringColored("no_permission_message"));
                return true;
            }
            for (Map.Entry<UUID, HashMap<String, Integer>> uuidFlyInformationEntry : watchedPlayers.entrySet()) {
                sender.sendMessage("");
                OfflinePlayer target = getServer().getOfflinePlayer(uuidFlyInformationEntry.getKey());
                sendNice(sender, NAME_COLOR + target.getName() + MESSAGE_COLOR + ":");
                for (Map.Entry<String, Integer> stringFlyTimeEntry : uuidFlyInformationEntry.getValue().entrySet()) {
                    sendNice(sender, "Verbleibende Zeit in Welt " + WORLD_COLOR + stringFlyTimeEntry.getKey() + MESSAGE_COLOR + ": " + TIME_COLOR + secToTime(stringFlyTimeEntry.getValue()) + MESSAGE_COLOR + ".");
                }
            }
        } else {

            return false;

        }
        return true;
    }


    @EventHandler
    public void onPlayerChangedWorldEvent(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (watchedPlayers.containsKey(p.getUniqueId())) {
            if (!watchedPlayers.get(p.getUniqueId()).containsKey(p.getWorld().getName().toLowerCase()) || (watchedPlayers.get(p.getUniqueId()).containsKey(p.getWorld().getName().toLowerCase()) && watchedPlayers.get(p.getUniqueId()).get(p.getWorld().getName().toLowerCase()) <= 0)) {
                getServer().dispatchCommand(getServer().getConsoleSender(), "fly " + p.getName() + " disable");
            }
        }
    }

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        for (UUID toWatch : watchedPlayers.keySet()) {

            if (uuid.toString().equals(toWatch.toString())) {
                System.out.println(true);
                watchedPlayers.get(uuid).forEach((world, timeLeft) -> {
                    perms.playerAdd(world, event.getPlayer().getName(), "essentials.fly");
                    startTimer(uuid, world);

                });
            }
            break;

        }
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        System.out.println(uuid);
        if (playerTaskMap.containsKey(uuid)) {
            for (Integer id : playerTaskMap.get(uuid).values()) {
                Bukkit.getScheduler().cancelTask(id);
            }
        }

    }

    public void startTimer(UUID uuid, String world) {
        Player p = getServer().getPlayer(uuid);
        int id = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            try {
                String pWorld = p.getWorld().getName().toLowerCase();

                if (p.isFlying() && pWorld.equals(world)) {
                    //Player is indeed flying in a watched world
                    //removing a second every 20 ticks
                    watchedPlayers.get(uuid).put(pWorld, watchedPlayers.get(uuid).get(pWorld) - 1);

                    if (watchedPlayers.get(uuid).get(pWorld) <= 0) {
                        //Player has no more flytime left, so:
                        watchedPlayers.get(uuid).put(world, 0);
                        perms.playerRemove(world, getServer().getOfflinePlayer(uuid), "essentials.fly"); //removing permissions
                        p.setFlying(false); //disabling flight
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "fly " + p.getName() + " disable"); //disabling /fly
                        sendNice(p, "Flugzeit abgelaufen!");
                        watchedPlayers.get(uuid).remove(world); //removing the world from watched worlds for this player
                        if (watchedPlayers.get(p.getUniqueId()).isEmpty()) {
                            watchedPlayers.remove(p.getUniqueId()); //removing player from watchlist if he's being watched in no more worlds
                        }

                        try { //saving to db
                            PreparedStatement stmt = connection.prepareStatement("delete from FlyCredits where UUID=? and world=?");
                            stmt.setString(1, p.getUniqueId().toString());
                            stmt.setString(2, world);
                            stmt.execute();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        //cancelling this task
                        Bukkit.getScheduler().cancelTask(playerTaskMap.get(uuid).get(world));
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 40, 20);
        HashMap<String, Integer> hm = new HashMap<>();
        hm.put(world, id);
        playerTaskMap.put(uuid, hm);
    }

    public void addTime(OfflinePlayer p, String world, int sec) throws NullPointerException {
        UUID uuid = p.getUniqueId();
        if (!watchedPlayers.containsKey(uuid)) {

            HashMap<String, Integer> hm1 = new HashMap<>();

            hm1.put(world, sec);
            watchedPlayers.put(uuid, hm1);
            insertDB(uuid.toString(), world, sec);
        } else {
            if (watchedPlayers.get(uuid).containsKey(world)) {
                watchedPlayers.get(uuid).put(world, watchedPlayers.get(uuid).get(world) + sec);
                updateDB(watchedPlayers.get(p.getUniqueId()).get(world), uuid.toString(), world);
            } else {
                watchedPlayers.get(uuid).put(world, sec);
                insertDB(uuid.toString(), world, sec);
            }

        }

        //added time, so player will certainly some time left - giving permission
        perms.playerAdd(world, p, "essentials.fly");

        if (p.isOnline()) {
            startTimer(p.getUniqueId(), world);
        }
    }

    public void removeTime(UUID uuid, String world, int sec) {
        int oldTime = watchedPlayers.get(uuid).get(world);
        int newTime = (oldTime) - sec > 0 ? oldTime - sec : 0;

        watchedPlayers.get(uuid).put(world, newTime);
        updateDB(newTime, uuid.toString(), world);
    }

    public void updateDB(int timeLeft, String uuid, String world) {
        try {
            PreparedStatement stmt = connection.prepareStatement("update FlyCredits set timeleft = ? where uuid = ? and world = ?;");
            stmt.setInt(1, timeLeft);
            stmt.setString(2, uuid);
            stmt.setString(3, world);
            stmt.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void insertDB(String uuid, String world, int timeLeft) {
        try {
            PreparedStatement stmt = connection.prepareStatement("insert into FlyCredits (UUID, world, timeleft) values (?, ?, ?);");
            stmt.setString(1, uuid);
            stmt.setString(2, world);
            stmt.setInt(3, timeLeft);
            stmt.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setupDB() {
        try {
            Class.forName("com.mysql.jdbc.Driver");

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.err.println("jdbc driver unavailable!");
            return;
        }
        try {
            connection = DriverManager.getConnection("jdbc:mysql://" + config.getString("url"), config.getString("username"), config.getString("password"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        String sql = "CREATE TABLE IF NOT EXISTS FlyCredits(UUID varchar(64), world varchar(64), timeleft INTEGER);";
        try {
            PreparedStatement stmt = connection.prepareStatement(sql);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setupConfigVariables() {
        saveDefaultConfig();
        TIME_COLOR = getConfigStringColored("time_color");
        NAME_COLOR = getConfigStringColored("name_color");
        WORLD_COLOR = getConfigStringColored("world_color");
        MESSAGE_COLOR = getConfigStringColored("message_color");
    }

    public void loadFlyCredits() {
        try {
            PreparedStatement stmt = connection.prepareStatement("select * from FlyCredits group by UUID;");
            ResultSet results = stmt.executeQuery();

            Map<UUID, List<Data>> groups = new HashMap<UUID, List<Data>>();
            while (results.next()) {
                UUID col1 = UUID.fromString(results.getString("UUID"));
                String col2 = results.getString("world");
                int col3 = results.getInt("timeleft");
                List<Data> group = groups.get(col1);
                if (group == null) {
                    group = new ArrayList<>();
                    groups.put(col1, group);
                }
                group.add(new Data(col1, col2, col3));
            }

            for (UUID uuid : groups.keySet()) {
                for (Data data : groups.get(uuid)) {
                    HashMap<String, Integer> hm = new HashMap<>();
                    hm.put(data.world, data.timeLeft);
                    watchedPlayers.put(uuid, hm);
                }
            }
            logger.info("Loaded FlyCredits from Database!");


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class Data {
        public Data(UUID uuid, String world, int timeLeft) {
            this.uuid = uuid;
            this.world = world;
            this.timeLeft = timeLeft;
        }

        UUID uuid;
        String world;
        int timeLeft;
    }

    public String secToTime(int seconds) {

        final int MINUTES_IN_AN_HOUR = 60;
        final int SECONDS_IN_A_MINUTE = 60;

        int minutes = seconds / SECONDS_IN_A_MINUTE;
        seconds -= minutes * SECONDS_IN_A_MINUTE;

        int hours = minutes / MINUTES_IN_AN_HOUR;
        minutes -= hours * MINUTES_IN_AN_HOUR;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours + "h ");
        }
        if (minutes > 0) {
            sb.append(minutes + "m ");
        }
        sb.append(seconds + "sec");

        return sb.toString();
    }

    public void sendNice(Player target, String message) {
        target.sendMessage(getConfigStringColored("prefix") + getConfigStringColored("message_color") + message);
    }

    public void sendNice(CommandSender target, String message) {
        if (target == Bukkit.getConsoleSender()) {
            logger.info(message.replaceAll("§.{1}", ""));
        } else {
            target.sendMessage(getConfigStringColored("prefix") + getConfigStringColored("message_color") + message);
        }

    }


    public String getConfigStringColored(String conf) {
        return ChatColor.translateAlternateColorCodes('&', config.getString(conf));
    }

}

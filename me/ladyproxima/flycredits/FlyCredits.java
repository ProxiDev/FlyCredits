package me.ladyproxima.flycredits;

import me.ladyproxima.flycredits.commands.*;
import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

public class FlyCredits extends JavaPlugin implements Listener, CommandExecutor {

    public static HashMap<UUID, HashMap<String, Integer>> watchedPlayers = new HashMap<>();
    public static ArrayList<UUID> activePlayers = new ArrayList<>();

    static int timerID;

    static HashMap<String, ICommand> commands = new HashMap<>();

    static FileConfiguration config;
    public static Permission perms = null;

    public static String NAME_COLOR = "";
    public static String MESSAGE_COLOR = "";
    public static String WORLD_COLOR = "";
    public static String TIME_COLOR = "";

    static Logger logger;

    static Connection connection;

    @Override
    public void onEnable() {
        config = getConfig();
        logger = getLogger();
        logger.info("Enabling FlyCredits...");

        setupConfigVariables();

        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        perms = rsp.getProvider();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("fc").setExecutor(this);

        commands.put("add", new AddCommand());
        commands.put("remove", new RemoveCommand());
        commands.put("check", new CheckCommand());
        commands.put("checkall", new CheckallCommand());

        //Setting up the DB
        setupDB();

        loadFlyCredits();

        timerID = startTimer();

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
                }
            }

            watchedPlayers.clear();
            Bukkit.getScheduler().cancelTask(timerID);

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
        String subcommand = args.length > 0 ? args[0].toLowerCase() : "";

        if (!sufficientPermissions(subcommand, sender)){
            sender.sendMessage(getConfigStringColored("no_permission_message"));
            return true;
        }

        switch (subcommand){
            case "add":
                return commands.get("add").executeCommand(sender, args);
            case "remove":
                return commands.get("remove").executeCommand(sender, args);
            case "check":
                return commands.get("check").executeCommand(sender, args);
            case "checkall":
                return commands.get("checkall").executeCommand(sender, args);
        }

        return false;
    }

    @EventHandler
    public void onPlayerToggleFlightEvent(PlayerToggleFlightEvent e){
        Player p = e.getPlayer();
        if (e.isFlying() && watchedPlayers.containsKey(p.getUniqueId()) && watchedPlayers.get(p.getUniqueId()).containsKey(p.getWorld().getName()) && !activePlayers.contains(p.getUniqueId())){
            activePlayers.add(p.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerChangedWorldEvent(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (watchedPlayers.containsKey(p.getUniqueId()) && !perms.has(p, "flycredits.bypass")){
            p.setAllowFlight(false);
            if (activePlayers.contains(p.getUniqueId())) activePlayers.remove(p.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        if (activePlayers.contains(uuid)) activePlayers.remove(uuid);
    }

    public int startTimer() {
        //Player p = getServer().getPlayer(uuid);
        return Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            try {
                ArrayList<UUID> _activePlayers = new ArrayList<>(activePlayers);
                for (UUID activeUUID : activePlayers) {
                    Player currentPlayer = getServer().getPlayer(activeUUID);
                    String currentWorld = currentPlayer.getWorld().getName().toLowerCase();

                    if (currentPlayer.isFlying() && watchedPlayers.get(activeUUID).containsKey(currentWorld)) {
                        //Player is indeed flying in a watched world
                        //removing a second every 20 ticks
                        watchedPlayers.get(activeUUID).put(currentWorld, watchedPlayers.get(activeUUID).get(currentWorld) - 1);

                        if (watchedPlayers.get(activeUUID).get(currentWorld) <= 0) {
                            //Player has no more flytime left, so:
                            noMoreTimeLeft(activeUUID, currentPlayer, currentWorld);

                            //removing player from activePlayers
                            _activePlayers.remove(activeUUID);
                        }
                    }
                }

                activePlayers = _activePlayers;

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 40, 20);
    }

    public void noMoreTimeLeft(UUID activeUUID, Player currentPlayer, String currentWorld){
        watchedPlayers.get(activeUUID).put(currentWorld, 0);
        perms.playerRemove(currentWorld, getServer().getOfflinePlayer(activeUUID), "essentials.fly"); //removing permissions
        currentPlayer.setFlying(false); //disabling flight
        currentPlayer.setAllowFlight(false);
        sendNice(currentPlayer, "Flugzeit abgelaufen!");
        watchedPlayers.get(activeUUID).remove(currentWorld); //removing the world from watched worlds for this player
        if (watchedPlayers.get(activeUUID).isEmpty()) {
            watchedPlayers.remove(activeUUID); //removing player from watchlist if he's being watched in no more worlds
        }

        try { //saving to db
            PreparedStatement stmt = connection.prepareStatement("delete from FlyCredits where UUID=? and world=?");
            stmt.setString(1, activeUUID.toString());
            stmt.setString(2, currentWorld);
            stmt.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void updateDB(int timeLeft, String uuid, String world) {
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

    public static void insertDB(String uuid, String world, int timeLeft) {
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

    public static String secToTime(int seconds) {

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

    public static void sendNice(Player target, String message) {
        target.sendMessage(getConfigStringColored("prefix") + getConfigStringColored("message_color") + message);
    }

    public static void sendNice(CommandSender target, String message) {
        if (target == Bukkit.getConsoleSender()) {
            logger.info(message.replaceAll("§.{1}", ""));
        } else {
            target.sendMessage(getConfigStringColored("prefix") + getConfigStringColored("message_color") + message);
        }

    }

    public static String getConfigStringColored(String conf) {
        return ChatColor.translateAlternateColorCodes('&', config.getString(conf));
    }

    public static boolean sufficientPermissions(String subcommand, CommandSender sender){
        if (sender instanceof ConsoleCommandSender) return true;

        Player p = (Player) sender;
        return perms.has(p, commands.get(subcommand).requiredPermissions());
    }

}

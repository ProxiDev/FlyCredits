package me.ladyproxima.flycredits;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;


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

    Logger logger;

    static Connection connection;

    @Override
    public void onEnable() {
        logger = getLogger();
        logger.info("Enabling FlyCredits!");

        config.addDefault("username", "DB_user");
        config.addDefault("password", "DB_password");
        config.addDefault("url", "DB_url (e.g. localhost:3306/DataBaseName)");
        config.addDefault("no_permission_message", "'&cDu hast dazu keine Berechtigungen.'");
        config.addDefault("prefix","'&f[&6FlyCredits&f]&b '");
        config.options().copyDefaults(true);
        saveConfig();

        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        perms = rsp.getProvider();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("fc").setExecutor(this);

        //Setting up the DB
        try {
            Class.forName("com.mysql.jdbc.Driver");

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.err.println("jdbc driver unavailable!");
            return;
        }
        try {
            connection = DriverManager.getConnection("jdbc:mysql://"+config.getString("url"),config.getString("username"),config.getString("password"));
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

        loadFlyCredits();

        logger.info("Loaded FlyCredits!");
    }

    @Override
    public void onDisable() {


        try {

            for (Map.Entry<UUID, HashMap<String, Integer>> uuidHashMapEntry : watchedPlayers.entrySet()) {
                for (Map.Entry<String, Integer> stringIntegerEntry : uuidHashMapEntry.getValue().entrySet()) {
                    UUID uuid = uuidHashMapEntry.getKey();
                    String world = stringIntegerEntry.getKey();
                    int timeLeft = stringIntegerEntry.getValue();
                    PreparedStatement stmt = connection.prepareStatement("update FlyCredits set timeleft = ? where uuid = ? and world = ?;");
                    stmt.setInt(1, timeLeft);
                    stmt.setString(2, uuid.toString());
                    stmt.setString(3, world);
                    stmt.execute();

                }
            }


            if (connection!=null && !connection.isClosed()){

                connection.close();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int permlevel = 0;
        if (sender == Bukkit.getConsoleSender()){
            permlevel = 10;
        }else{
            Player test = (Player) sender;
            if(perms.has(test,"flycredits.check")) permlevel = 1;
            if(perms.has(test,"flycredits.use")) permlevel = 2;
        }

        if(permlevel<1){
            sender.sendMessage(getConfigMessage("no_permission_message"));
            return true;
        }

        String subcommand = args.length>0 ? args[0].toLowerCase() : "";

        if (args.length == 4 && subcommand.equals("add")){
            if(permlevel <2){
                sender.sendMessage(getConfigMessage("no_permission_message"));
                return true;
            }

            Player p = getServer().getPlayer(args[1]);
            try{
                if (p.isOnline()){

                    Duration t = Duration.parse("pt"+args[2]);
                    addTime(p, (int)t.getSeconds(), args[3].toLowerCase());
                    sendNice((Player)sender,"Zeit erfolgreich hinzugefügt.");

                }

            }catch (NullPointerException e){
                sendNice((Player)sender,"Spieler nicht online.");

            }catch (Exception e){
                sendNice((Player)sender,"Bitte Zeitformat einhalten, z.B.: 12h50m20s.");
            }

        } /*else if (args.length > 0 && args[0].toLowerCase().equals("get")){
            if(permlevel <2){
                sender.sendMessage(getConfigMessage("no_permission_message"));
                return true;
            }
            for (Map.Entry<UUID, HashMap<String, Integer>> uuidFlyInformationEntry : watchedPlayers.entrySet()) {
                sendNice((Player)sender,uuidFlyInformationEntry.getKey().toString());
                for (Map.Entry<String, Integer> stringFlyTimeEntry : uuidFlyInformationEntry.getValue().entrySet()) {
                    sendNice((Player)sender,stringFlyTimeEntry.getKey());
                    sendNice((Player)sender,"timeleft: "+stringFlyTimeEntry.getValue());

                }
                sender.sendMessage("");
            }
        }*/ else if (args.length == 4 && subcommand.equals("remove")){
            if(permlevel <2){
                sender.sendMessage(getConfigMessage("no_permission_message"));
                return true;
            }
            Player p = getServer().getPlayer(args[1]);
            try{
                if (p.isOnline()){

                    Duration t = Duration.parse("pt"+args[2]);
                    removeTime(p.getUniqueId(), args[3].toLowerCase(), (int)t.getSeconds());
                    sendNice((Player)sender,"Zeit erfolgreich entfernt.");

                }

            }catch (NullPointerException e){
                sendNice((Player)sender,"Spieler nicht online.");

            }catch (Exception e){
                sendNice((Player)sender,"Bitte Zeitformat einhalten, z.B.: 12h50m20s.");
            }
        }else if(args.length > 0 && subcommand.equals("check")){
            if(permlevel <1){
                sender.sendMessage(getConfigMessage("no_permission_message"));
                return true;
            }
            Player p;
            if(args.length>1){
                p = getServer().getPlayer(args[1]);
            } else {
                p = (Player)sender;
            }

            if(watchedPlayers.containsKey(p.getUniqueId())){
                for (Map.Entry<String, Integer> duration : watchedPlayers.get(p.getUniqueId()).entrySet()) {
                    sendNice((Player)sender,"Verbleibende Zeit in Welt "+duration.getKey()+": "+duration.getValue()+" Sekunden.");
                }
            }else{
                sendNice((Player)sender,"Keine verbleibende Flugzeit mehr.");
            }
        }
        else{

            return false;

        }
        return true;
    }


    @EventHandler
    public void onPlayerChangedWorldEvent(PlayerChangedWorldEvent e){
        Player p = e.getPlayer();
        if (watchedPlayers.containsKey(p.getUniqueId())){
            if (!watchedPlayers.get(p.getUniqueId()).containsKey(p.getWorld().getName().toLowerCase()) || (watchedPlayers.get(p.getUniqueId()).containsKey(p.getWorld().getName().toLowerCase()) && watchedPlayers.get(p.getUniqueId()).get(p.getWorld().getName().toLowerCase()) <= 0)){
                getServer().dispatchCommand(getServer().getConsoleSender(), "fly "+p.getName()+" disable");
            }
        }
    }

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent event){
        UUID uuid = event.getPlayer().getUniqueId();
        for (UUID toWatch : watchedPlayers.keySet()) {

            if(uuid.toString().equals(toWatch.toString())){
                System.out.println(true);
                watchedPlayers.get(uuid).forEach((world, timeLeft) -> {
                    perms.playerAdd(world,event.getPlayer().getName(),"essentials.fly");
                    startTimer(uuid, world);

                });
            }
            break;

        }
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event){
        UUID uuid = event.getPlayer().getUniqueId();
        System.out.println(uuid);
        if(playerTaskMap.containsKey(uuid)){
            for (Integer id : playerTaskMap.get(uuid).values()) {
                Bukkit.getScheduler().cancelTask(id);
            }
        }

    }

    public void startTimer(UUID uuid, String world){
        Player p = getServer().getPlayer(uuid);
        int id = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            try{
                String pWorld = p.getWorld().getName().toLowerCase();

                if (p.isFlying() && pWorld.equals(world)){
                    //Player is indeed flying in a watched world
                    //removing a second every 20 ticks
                    watchedPlayers.get(uuid).put(pWorld, watchedPlayers.get(uuid).get(pWorld)-1);

                    if (watchedPlayers.get(uuid).get(pWorld) <= 0){
                        //Player has no more flytime left, so:
                        watchedPlayers.get(uuid).put(world, 0);
                        perms.playerRemove(p.getWorld(),p.getName(),"essentials.fly"); //removing permissions
                        p.setFlying(false); //disabling flight
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "fly "+p.getName()+ " disable"); //disabling /fly
                        sendNice(p, "Flugzeit abgelaufen!");
                        watchedPlayers.get(uuid).remove(world); //removing the world from watched worlds for this player
                        if (watchedPlayers.get(p.getUniqueId()).isEmpty()){
                            watchedPlayers.remove(p.getUniqueId()); //removing player from watchlist if he's being watched in no more worlds
                        }

                        try{ //saving to db
                            PreparedStatement stmt = connection.prepareStatement("delete from FlyCredits where UUID=? and world=?");
                            stmt.setString(1, p.getUniqueId().toString());
                            stmt.setString(2, world);
                            stmt.execute();
                        }catch (Exception e){
                            e.printStackTrace();
                        }

                        //cancelling this task
                        Bukkit.getScheduler().cancelTask(playerTaskMap.get(uuid).get(world));
                    }
                }

            }catch (Exception e){
                e.printStackTrace();
            }
        }, 40, 20);
        HashMap<String, Integer> hm = new HashMap<>();
        hm.put(world, id);
        playerTaskMap.put(uuid, hm);
    }

    public void addTime(Player p, int sec, String world){
        UUID uuid = p.getUniqueId();
        if (!watchedPlayers.containsKey(uuid)){

            HashMap<String, Integer> hm1 = new HashMap<>();

            hm1.put(world, sec);
            watchedPlayers.put(uuid, hm1);
            insertDB(uuid.toString(), world, sec);
        }else{
            if(watchedPlayers.get(uuid).containsKey(world)){
                watchedPlayers.get(uuid).put(world, watchedPlayers.get(uuid).get(world)+sec);
                updateDB(watchedPlayers.get(p.getUniqueId()).get(world), uuid.toString(), world);
            }else{
                watchedPlayers.get(uuid).put(world, sec);
                insertDB(uuid.toString(), world, sec);
            }

        }

        //added time, so player will certainly some time left - giving permission
        perms.playerAdd(p.getWorld(),p.getName(),"essentials.fly");
        startTimer(p.getUniqueId(), world);
    }

    public void removeTime(UUID uuid, String world, int sec){
        int oldTime = watchedPlayers.get(uuid).get(world);
        int newTime = oldTime-sec > 0 ? oldTime-sec : 0;

        watchedPlayers.get(uuid).put(world, newTime);
        try{
            PreparedStatement stmt = connection.prepareStatement("update FlyCredits set timeleft = ? where uuid = ? and world = ?;");
            stmt.setInt(1, (int)(long)watchedPlayers.get(uuid).get(world));
            stmt.setString(2, uuid.toString());
            stmt.setString(3, world);
            stmt.execute();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void updateDB(int timeLeft, String uuid, String world){
        try{
            PreparedStatement stmt = connection.prepareStatement("update FlyCredits set timeleft = ? where uuid = ? and world = ?;");
            stmt.setInt(1, timeLeft);
            stmt.setString(2, uuid);
            stmt.setString(3, world);
            stmt.execute();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void insertDB(String uuid, String world, int timeLeft){
        try{
            PreparedStatement stmt = connection.prepareStatement("insert into FlyCredits (UUID, world, timeleft) values (?, ?, ?);");
            stmt.setString(1, uuid);
            stmt.setString(2, world);
            stmt.setInt(3, timeLeft);
            stmt.execute();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void loadFlyCredits(){
        try{
            PreparedStatement stmt = connection.prepareStatement("select * from FlyCredits group by UUID;");
            ResultSet results = stmt.executeQuery();

            Map<UUID, List<Data>> groups = new HashMap<UUID, List<Data>>();
            while(results.next()){
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



        }catch (Exception e){
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

    public void sendNice(Player target, String message){
        target.sendMessage(getConfigMessage("prefix")+message);
    }

    public String getConfigMessage(String conf){
        return ChatColor.translateAlternateColorCodes('&', config.getString(conf));
    }

}

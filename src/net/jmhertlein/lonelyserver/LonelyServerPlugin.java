/*
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.jmhertlein.lonelyserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 *
 * @author joshua
 */
public class LonelyServerPlugin extends JavaPlugin {

    private static final String configFile = "config.yml";
    private static ChatColor color;
    private static long timeThresholdHours;
    private Player mostRecentLogoffPlayer;
    private long mostRecentLogoffTime;
    private List<String> messages;
    private Random rand;

    @Override
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(new LogListener(), this);
        rand = new Random();
        loadConfig();
    }
    @Override
    public void onDisable(){
    	rand = null; 
    }

    private class LogListener implements Listener {
        @EventHandler
        public void onPlayerLogoff(PlayerQuitEvent e) {
            mostRecentLogoffPlayer = e.getPlayer();
            mostRecentLogoffTime = System.currentTimeMillis();
        }

        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent e) {
            if (    Bukkit.getServer().getOnlinePlayers().size() == 1 
                    && mostRecentLogoffPlayer != null 
                    && getHoursSinceLastLogoff() < timeThresholdHours
                    ) {
                e.getPlayer().sendMessage(color + getLoginMessage(e.getPlayer()));
                getLogger().info(e.getPlayer().getName() + " logged in alone, and was notified that the last player only logged off " + getFormattedTimeSinceLastLogoff() + " ago.");
            }
        }
    }

    private long getMinutesSinceLastLogoff() {
        long timeSpan = System.currentTimeMillis() - mostRecentLogoffTime;

        timeSpan /= 1000;
        timeSpan /= 60;

        return timeSpan;
    }

    private long getHoursSinceLastLogoff() {
        return getMinutesSinceLastLogoff() / 60;
    }
    
    /**
     * @return The time since the last player logged off in the form: "d days, h hours, m minutes", or "less than a minute" if less than 60 seconds have passed
     */
    private String getFormattedTimeSinceLastLogoff() {
        long mins = getMinutesSinceLastLogoff();
        long hours, days;
        
        hours = mins/60;
        mins %= 60;
        days = hours/24;
        hours %= 24;
        
        return (days > 0 ? days + " days, " : "") + (hours > 0 ? hours + " hours, " : "") + (mins > 0 ? mins + " minutes" : (days == 0 && hours == 0 ? "less than a minute" : ""));
    }

    private String getLoginMessage(Player loginPlayer) {
        String msg = messages.get(rand.nextInt(messages.size()));
        msg = msg.replaceAll("$MINS", (new Long(getMinutesSinceLastLogoff())).toString());
        msg = msg.replace("$TIME", getFormattedTimeSinceLastLogoff());
        msg = msg.replace("$LASTPLAYER", mostRecentLogoffPlayer.getName());
        msg = msg.replace("$CURPLAYER", loginPlayer.getName());

        return msg;
    }

    private void loadConfig() {
        File sourceDir = getDataFolder();

        if (!sourceDir.exists())
            sourceDir.mkdir();

        FileConfiguration config = new YamlConfiguration();
        try {
            getLogger().info("Config loaded.");
            config.load(new File(sourceDir, configFile));
            color = ChatColor.valueOf(config.getString("chatColor"));
            timeThresholdHours = config.getLong("timeThresholdHours");
            messages = config.getStringList("messages");
            if (messages.isEmpty()){
            	//if the list is empty, something is wrong with the configuration, so lets try not to panic...
            	if (config.isString("message")){
            		getLogger().info("Found old message format, converting to StringList...");
            		messages.add(config.getString("message"));
            		config.set("message",null);//remove old message setting, use list now
                    config.set("timeThresholdHours", timeThresholdHours);
                    config.set("messages", messages);
                    config.set("chatColor", color.name());
                    persistConfig(config);
            	}
            	else{
            		getLogger().severe("No messages found (bad yml formatting?) loaded default");
            		messages.add("The last player only logged off $TIME ago.");
            	}
            }
            
        } catch (FileNotFoundException ex) {
            //print license info on first run
            printLicenseInfo();
            
            //load defaults into RAM
            color = ChatColor.DARK_AQUA;
            messages = new ArrayList<String>();
            messages.add("The last player only logged off $TIME ago."); 
            timeThresholdHours = 6;

            //set defaults in the config
            config.set("timeThresholdHours", timeThresholdHours);
            config.set("messages", messages);
            config.set("chatColor", color.name());
            
            //write config to file
            persistConfig(config);
        } catch (IOException | InvalidConfigurationException ex) {
            config.set("chatColor", color.toString());
            getLogger().log(Level.SEVERE, "Error loading config; probably bad markup in the file?");
        }
        getLogger().info(String.format("Configuration: loaded %d messages", messages.size()));
    }

    private void printLicenseInfo() {
        getLogger().info("LonelyServer is free software. For more information, see http://www.gnu.org/licenses/quick-guide-gplv3.html and http://www.gnu.org/licenses/gpl.txt");
        getLogger().info("LonelyServer's source code is available as per its license here: https://github.com/jmhertlein/LonelyServer");
    }

    private void persistConfig(FileConfiguration config) {
        try {
            config.save(new File(getDataFolder(), configFile));
            getLogger().info("Default config written.");
        } catch (IOException ex1) {
            getLogger().severe("Error writing default config");
        }
    }
}

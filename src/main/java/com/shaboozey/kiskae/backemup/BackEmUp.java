package com.shaboozey.kiskae.backemup;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import org.apache.commons.lang.Validate;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public class BackEmUp extends JavaPlugin {

    private final static String EXAMPLE_HEADER =
            "Please provide a list of the worlds to backup every day at midnight in the following format:\n\n"
            + "worlds: [omicron,test2]\n"
            + "backup-path: backups OR backup-path: /home/minecraft/backups\n"
            + "interval: 1 (will run every 1 hour)\n"
            + "delete-backups: 30 (delete backups after 30 days, -1 to disable)";
    private final Timer timer = new Timer("BackEmUp");
    private final BackupTask task;
    private final List<World> worlds = new ArrayList<>();
    private File backupFolder = null;
    private int hours = 12;
    private int deleteBackups = 30;

    public BackEmUp() {
        task = new BackupTask(this);
    }

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) { //Check if there is no config
            getConfig().options().header(EXAMPLE_HEADER).copyHeader();
            getConfig().addDefault("backup-path", "backups");
            getConfig().addDefault("interval", 24);
            getConfig().addDefault("delete-backups", 30);
            getConfig().addDefault("worlds", Collections.EMPTY_LIST);
            getConfig().options().copyDefaults(true);
            saveConfig();
            getLogger().log(Level.WARNING, "First run of BackEmUp, generated a config file, please fill in the details.");
            return;
        } else {
            getLogger().log(Level.INFO, "The following worlds will be backed up:");
            for (String w : getConfig().getStringList("worlds")) {
                if (getServer().getWorld(w) != null) {
                    getLogger().log(Level.INFO, String.format("- %s", w));
                    worlds.add(getServer().getWorld(w));
                } else {
                    getLogger().log(Level.WARNING, String.format("! World '%s' not found!", w));
                }
            }
        }

        Path get = Paths.get(getConfig().getString("backup-path", "backups"));
        get = get.normalize().toAbsolutePath();
        getLogger().log(Level.INFO, String.format("Backups will be saved to '%s'", get.toString()));
        backupFolder = get.toFile();
        
        if (backupFolder.exists() && !backupFolder.isDirectory()) {
            getLogger().log(Level.WARNING, "Invalid backup folder, plugin will terminate.");
            return;
        }
        
        backupFolder.mkdirs();

        removeOldBackups();

        hours = getConfig().getInt("interval", 24);
        Validate.isTrue((hours > 0), "Cannot save on negative or zero interval.");
        deleteBackups = getConfig().getInt("delete-backups", 30);

        getLogger().log(Level.INFO, (deleteBackups > 0) ? String.format("Deleting backups after %d days.", deleteBackups) : "Not deleting backups.");

        Date nextrun;
        if (getConfig().getLong("lastBackup", 0L) == 0) {
            //First run, start at midnight and find the next run.
            nextrun = BEUUtil.findNextInstance(BEUUtil.timeLastMidnight(), hours);
        } else {
            nextrun = BEUUtil.findNextInstance(new Date(getConfig().getLong("lastBackup")), hours);
        }
        //Should run every X hours.
        timer.scheduleAtFixedRate(task, nextrun, 1000L * 60L * 60L * hours);
        getLogger().log(Level.INFO, String.format(
                "Next backup will happen at %s",
                new SimpleDateFormat("HH:mm, MMM d").format(nextrun)));
    }

    public List<World> getBackupWorlds() {
        return worlds;
    }

    public int getInterval() {
        return hours;
    }

    public File getBackupFolder() {
        return backupFolder;
    }

    protected void removeOldBackups() {
        for (File f : backupFolder.listFiles()) {
            if (deleteBackups > 0 && f.getName().endsWith(".tar.gz")) {
                //Check date.
                if (((int) Math.floor((System.currentTimeMillis() - f.lastModified()) / 1000L / 60L / 60L / 24L)) >= deleteBackups) {
                    //These are older than specified.
                    getLogger().log(Level.INFO, String.format("Deleting old backup '%s'", f.getName()));
                    f.delete();
                }
            }
        }
    }
}

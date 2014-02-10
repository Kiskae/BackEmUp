package com.shaboozey.backemup;

import com.google.common.collect.Lists;
import com.shaboozey.backemup.util.DateUtil;
import com.shaboozey.backemup.util.Waitable;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class BackEmUp extends JavaPlugin {

    private final static String EXAMPLE_HEADER =
            "Please provide a list of the worlds to backup every day at midnight in the following format:\n\n"
                    + "worlds: [omicron,test2]\n"
                    + "backup-path: backups OR backup-path: /home/minecraft/backups\n"
                    + "interval: 1 (will run every 1 hour)\n"
                    + "delete-backups: 30 (delete backups after 30 days, -1 to disable)";

    private final Timer timer = new Timer("BackEmUp");
    private final BackupTask task;
    private final List<String> worlds = Lists.newArrayList();
    private File backupFolder = null;
    private int hours = 12;
    private int deleteBackups = 30;

    public BackEmUp() {
        this.task = new BackupTask(this);
    }

    @Override
    public void onDisable() {
        this.task.cancel();
        this.timer.purge();
    }

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) { //Check if there is no config
            this.getConfig().options().header(EXAMPLE_HEADER).copyHeader();
            this.getConfig().addDefault("backup-path", "backups");
            this.getConfig().addDefault("interval", 24);
            this.getConfig().addDefault("delete-backups", 30);
            this.getConfig().addDefault("worlds", Collections.EMPTY_LIST);
            this.getConfig().options().copyDefaults(true);
            this.saveConfig();

            this.getLogger().log(Level.WARNING, "First run of BackEmUp, generated a config file, please fill in the details.");
            return;
        }

        final Path get = Paths.get(getConfig().getString("backup-path", "backups")).normalize().toAbsolutePath();
        this.getLogger().log(Level.INFO, String.format("Backups will be saved to '%s'", get.toString()));
        this.backupFolder = get.toFile();

        if (this.backupFolder.exists() && !this.backupFolder.isDirectory()) {
            this.getLogger().log(Level.WARNING, "Invalid backup folder, plugin will terminate.");
            return;
        }

        this.backupFolder.mkdirs();

        this.hours = getConfig().getInt("interval", 24);
        Validate.isTrue((this.hours > 0), "Cannot save on negative or zero interval.");
        this.deleteBackups = getConfig().getInt("delete-backups", 30);

        this.task.removeOldBackups();

        this.getLogger().log(Level.INFO,
                (this.deleteBackups > 0) ?
                        String.format("Deleting backups after %d days.", this.deleteBackups) :
                        "Not deleting backups."
        );

        final Date nextrun;
        final long lastBackup = getConfig().getLong("lastBackup", 0L);
        if (lastBackup == 0) {
            //First run, start at midnight and find the next run.
            nextrun = DateUtil.findNextTime(DateUtil.timeLastMidnight(), hours);
        } else {
            nextrun = DateUtil.findNextTime(new Date(lastBackup), hours);
        }

        //Wait until everything is fully loaded to load the worlds/begin backing up
        this.getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            @Override
            public void run() {
                getLogger().log(Level.INFO, "The following worlds will be backed up:");
                for (final String worldName : getConfig().getStringList("worlds")) {
                    if (getServer().getWorld(worldName) != null) {
                        getLogger().log(Level.INFO, String.format("- %s", worldName));
                    } else {
                        getLogger().log(Level.WARNING, String.format("! World '%s' not found!", worldName));
                    }
                    BackEmUp.this.worlds.add(worldName);
                }

                //Should run every getInterval() hours.
                BackEmUp.this.timer.scheduleAtFixedRate(task, nextrun, TimeUnit.HOURS.toMillis(getInterval()));

                getLogger().log(Level.INFO, String.format(
                        "Next backup will happen at %s",
                        new SimpleDateFormat("HH:mm, MMM d").format(nextrun)));
            }
        });
    }

    public <T> T runWaitable(final Waitable<T> waitable) throws ExecutionException, InterruptedException {
        this.getServer().getScheduler().scheduleSyncDelayedTask(this, waitable);
        return waitable.get();
    }

    public List<World> getBackupWorlds() {
        final List<World> worlds = Lists.newLinkedList();
        for (final String worldName : this.worlds) {
            final World world = Bukkit.getWorld(worldName);
            if (world != null) {
                worlds.add(world);
            }
        }
        return worlds;
    }

    public int getMaxBackupsAge() {
        return this.deleteBackups;
    }

    public int getInterval() {
        return this.hours;
    }

    public File getBackupFolder() {
        return this.backupFolder;
    }
}

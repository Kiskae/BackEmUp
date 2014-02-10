package com.shaboozey.backemup;

import com.shaboozey.backemup.util.Waitable;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.bukkit.World;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

public class BackupTask extends TimerTask {

    private final BackEmUp plugin;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmm");

    public BackupTask(final BackEmUp plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        //Timestamp the backups
        final String dateStr = sdf.format(new Date());


        final List<World> backupWorlds;
        try {
            backupWorlds = this.plugin.runWaitable(new Waitable<List<World>>() {
                @Override
                protected List<World> evaluate() {
                    return plugin.getBackupWorlds();
                }
            });
        } catch (ExecutionException e) {
            e.printStackTrace();
            return;
        } catch (InterruptedException e) {
            return;
        }

        //First, lets go through each world and disable saving.
        for (final World w : backupWorlds) {
            try {
                this.plugin.runWaitable(new Waitable<Boolean>() {
                    @Override
                    protected Boolean evaluate() {
                        w.setAutoSave(false);
                        w.save();

                        return Boolean.TRUE;
                    }
                });
            } catch (ExecutionException | InterruptedException e) {
                this.plugin.getLogger().log(Level.WARNING, "Failure to disable autosave/save", e);
                continue;
            }

            try {
                final String fileName = String.format("%s-%s.tar.gz", w.getName(), dateStr);
                this.plugin.getLogger().log(Level.INFO, String.format("Saving world '%s' to '%s'", w.getName(), fileName));

                this.backup(w, fileName);
            } catch (IOException ex) {
                this.plugin.getLogger().log(Level.WARNING, String.format("Error saving %s", w.getName()), ex);
            }

            this.plugin.getLogger().log(Level.INFO, String.format("Done saving %s", w.getName()));

            try {
                this.plugin.runWaitable(new Waitable<Boolean>() {
                    @Override
                    protected Boolean evaluate() {
                        w.setAutoSave(true);

                        return Boolean.TRUE;
                    }
                });
            } catch (ExecutionException | InterruptedException e) {
                this.plugin.getLogger().log(Level.WARNING, "Failure to re-enable save", e);
            }
        }

        this.plugin.getConfig().set("lastBackup", scheduledExecutionTime() + 1000L * 60L * 60L * plugin.getInterval());
        this.plugin.saveConfig();

        this.removeOldBackups();
    }

    private void backup(final World w, final String fileName) throws IOException {
        final File f = new File(plugin.getBackupFolder(), fileName);

        if (!f.createNewFile()) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to create backup, {} already exists!", fileName);
            return;
        }

        try (TarArchiveOutputStream out = new TarArchiveOutputStream(
                new GZIPOutputStream(
                        new BufferedOutputStream(
                                new FileOutputStream(f))))) {
            store(w.getWorldFolder(), "", out);
        }
    }

    private void store(final File dir, final String path, final TarArchiveOutputStream out) throws IOException {
        final File[] files = dir.listFiles();
        if (files == null) return; //If dir isn't a dir, it returns NULL

        for (final File f : files) {
            final TarArchiveEntry entry = new TarArchiveEntry(f, path + f.getName());
            out.putArchiveEntry(entry);
            if (f.isDirectory()) {
                out.closeArchiveEntry();
                this.store(f, path + f.getName() + "/", out);
            } else {
                try (FileInputStream in = new FileInputStream(f)) {
                    IOUtils.copy(in, out);
                }
                out.closeArchiveEntry();
            }
        }
    }

    public void removeOldBackups() {
        if (this.plugin.getMaxBackupsAge() <= 0) return; //No backups deleting

        final File[] files = this.plugin.getBackupFolder().listFiles();
        if (files == null) {
            //Not a directory
            return;
        }

        for (final File f : files) {
            if (f.getName().endsWith(".tar.gz")) {
                //Check date.
                if (TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - f.lastModified()) >=
                        this.plugin.getMaxBackupsAge()) {
                    //These are older than specified.
                    this.plugin.getLogger().log(Level.INFO, String.format("Deleting old backup '%s'", f.getName()));
                    f.delete();
                }
            }
        }
    }
}

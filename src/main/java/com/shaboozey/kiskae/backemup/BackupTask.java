package com.shaboozey.kiskae.backemup;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.bukkit.World;

/**
 *
 * @author Kiskae
 */
public class BackupTask extends TimerTask {
    
    private final BackEmUp beu;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmm");
    
    public BackupTask(BackEmUp beu) {
        this.beu = beu;
    }
    
    @Override
    public void run() {
        //First, lets go through each world and disable saving.
        String date = sdf.format(new Date());
        for (World w : beu.getBackupWorlds()) {
            w.setAutoSave(false);
            w.save();
            
            try {
                String fileName = String.format("%s-%s.tar.gz", w.getName(), date);
                beu.getLogger().log(Level.INFO, String.format("Saving world '%s' to '%s'", w.getName(), fileName));
                
                backup(w, fileName);
            } catch (IOException ex) {
                beu.getLogger().log(Level.WARNING, String.format("Error saving %s", w.getName()), ex);
            }
            
            beu.getLogger().log(Level.INFO, String.format("Done saving %s", w.getName()));
            
            w.setAutoSave(true);
        }
        beu.getConfig().set("lastBackup", scheduledExecutionTime() + 1000L * 60L * 60L * beu.getInterval());
        beu.saveConfig();
        beu.removeOldBackups();
    }
    
    private void backup(World w, String fileName) throws IOException {
        File f = new File(beu.getBackupFolder(), fileName);
        f.createNewFile();
        
        try (TarArchiveOutputStream out = new TarArchiveOutputStream(
                                            new GZIPOutputStream(
                                            new BufferedOutputStream(
                                            new FileOutputStream(f))))) {
            store(w.getWorldFolder(), "", out);
        }
    }
    
    private void store(File file, String path, TarArchiveOutputStream out) throws IOException {
        for (File f : file.listFiles()) {
            if (f.isDirectory()) {
                TarArchiveEntry entry = new TarArchiveEntry(f, path + f.getName());
                out.putArchiveEntry(entry);
                out.closeArchiveEntry();
                store(f, path + f.getName() + "/", out);
            } else {
                TarArchiveEntry entry = new TarArchiveEntry(f, path + f.getName());
                out.putArchiveEntry(entry);
                try (FileInputStream in = new FileInputStream(f)) {
                    IOUtils.copy(in, out);
                }
                out.closeArchiveEntry();
            }
        }
    }
}

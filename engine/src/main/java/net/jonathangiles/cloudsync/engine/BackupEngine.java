package net.jonathangiles.cloudsync.engine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.List;

import com.google.inject.Guice;
import com.google.inject.Injector;
import net.jonathangiles.cloudsync.engine.cloud.CloudStore;
import net.jonathangiles.cloudsync.engine.db.DataStore;
import net.jonathangiles.cloudsync.engine.model.Backup;
import net.jonathangiles.cloudsync.engine.model.LocalRecord;
import net.jonathangiles.cloudsync.engine.util.Task;
import net.jonathangiles.cloudsync.engine.util.TaskQueue;
import net.jonathangiles.cloudsync.engine.util.WatchDir;
import net.jonathangiles.cloudsync.engine.util.config.Config;

import javax.inject.Inject;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class BackupEngine {
    // local data store
    private final DataStore localDataStore;

    // cloud store
    private final CloudStore cloudStore;

    // task queue
    private final TaskQueue taskQueue;

    public static void main(String[] args) {
        Injector injector = Guice.createInjector(new BackupEngineModule());
        BackupEngine backupEngine = injector.getInstance(BackupEngine.class);
        backupEngine.start();
    }

    @Inject
    private BackupEngine(DataStore dataStore, CloudStore cloudStore, TaskQueue taskQueue) {
        this.localDataStore = dataStore;
        this.cloudStore = cloudStore;
        this.taskQueue = taskQueue;
    }

    private void start() {
        // load backup model
        List<Backup> backupList = localDataStore.getBackupList();

        // for each backup, we need to ensure that there is a corresponding container in the cloud, and
        // because the app is just booting up, we need to do a full consistency check on each backup
        backupList.parallelStream().forEach(backup -> {
            // make a backup container on Azure Storage if it doesn't currently exist
            validateBackupContainerExists(backup);

            // run a startup consistency check to make sure we are consistent between local filesystem, local database,
            // and remote storage
            runConsistencyCheck(backup);

            // start up the folder watcher to watch for changes at runtime
            startFolderWatcher(backup);
        });
    }

    private void validateBackupContainerExists(Backup backup) {
        cloudStore.createContainer(backup);
    }

    /*
     * Checks the consistency of a backup against the local database for that backup:
     *  1) Are there files in the local database that are not visible on the filesystem? Remove from cloud!
     *  2) Are there files in the file system that are not in the local database? Upload to cloud!
     *  3) Do properties (file size, last modified, etc) of any file differ from the local database? Replace file in cloud!
     */
    private void runConsistencyCheck(Backup backup) {
        System.out.println("Performing consistency check for backup '" + backup.getBackupName() + "' in directory " + backup.getRootDirectoryString());

        // check 1 - remove files from Cloud Storage which no longer exist on the file system
        localDataStore
                .getBackupRecords(backup)
                .parallel()
                .filter(record -> !Files.exists(record.getPath()))
                .forEach(record -> removeFile(backup, record));

        // checks 2 and 3 - looking for local file system changes that have not been uploaded yet
        try {
            Files.find(backup.getRootDirectory(), 10000, (path, attributes) -> attributes.isRegularFile())
                    .parallel()
                    .forEach(p -> checkFile(backup, p));
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Consistency check for backup '" + backup.getBackupName() + "' in directory " + backup.getRootDirectoryString() + " is now complete");
    }

    private void startFolderWatcher(Backup backup) {
        try {
            new WatchDir(backup.getRootDirectory(), true, (watchEvent, path) -> {
                WatchEvent.Kind eventKind = watchEvent.kind();
                if (eventKind == ENTRY_CREATE || eventKind == ENTRY_MODIFY) {
                    checkFile(backup, path);
                } else if (eventKind == ENTRY_DELETE) {
                    // delete file from cloud storage / local DB
                    removeFile(backup, path);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkFile(Backup backup, Path p) {
        LocalRecord localRecord = localDataStore.getLocalRecord(backup, p);
        if (localRecord == null) {
            // we don't know about this file - we should add it to our upload list!
            uploadNewFile(backup, p);
        } else {
            // we do know of this file, but we must ensure that the file system version matches
            // what we have in our database
            if (!localRecord.matches(p)) {
                // what we have recorded does not match with what the file is reporting,
                // so we must delete the old file on Azure and replace it with this file
                replaceFile(backup, p, localRecord);
            }
        }
    }

    private void uploadNewFile(Backup backup, Path p) {
        taskQueue.send(Task.create(Task.Type.UPLOAD_FILE, backup, p, () -> {
            // on success, create new LocalRecord
            LocalRecord.create(backup, p).ifPresent(record -> localDataStore.updateLocalRecord(backup, record));
        }));
    }

    private void replaceFile(Backup backup, Path p, LocalRecord localRecord) {
        taskQueue.send(Task.create(Task.Type.REPLACE_FILE, backup, p, () -> {
            // on success, update existing LocalRecord
            localRecord.update(backup, p);
            localDataStore.updateLocalRecord(backup, localRecord);
        }));
    }

    private void removeFile(Backup backup, Path p) {
        LocalRecord localRecord = localDataStore.getLocalRecord(backup, p);
        if (localRecord == null) {
            // something odd - we are trying to remove a file from the cloud that isn't in the local data store
            // TODO handle this appropriately
        } else {
            removeFile(backup, localRecord);
        }
    }

    /*
     * We have a LocalRecord for a file that no longer exists on the file system. We should
     * therefore remove the LocalRecord from the local data store, and also from Azure Storage.
     */
    private void removeFile(Backup backup, LocalRecord localRecord) {
        taskQueue.send(Task.create(Task.Type.DELETE_FILE, backup, localRecord.getPath(), () -> {
            localDataStore.deleteLocalRecord(localRecord);
        }));
    }
}
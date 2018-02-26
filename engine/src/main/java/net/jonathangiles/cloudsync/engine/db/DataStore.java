package net.jonathangiles.cloudsync.engine.db;

import net.jonathangiles.cloudsync.engine.model.Backup;
import net.jonathangiles.cloudsync.engine.model.LocalRecord;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public interface DataStore {

    List<Backup> getBackupList();

    LocalRecord getLocalRecord(Backup backup, Path p);

    void updateLocalRecord(Backup backup, LocalRecord record);

    Stream<LocalRecord> getBackupRecords(Backup backup);

    void deleteLocalRecord(LocalRecord record);
}

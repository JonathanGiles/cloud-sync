package net.jonathangiles.cloudsync.engine.cloud;

import net.jonathangiles.cloudsync.engine.model.Backup;

import java.nio.file.Path;

public interface CloudStore {

    void createContainer(Backup backup);

    void uploadFile(Backup backup, Path p, Runnable onSuccess);

    void removeFile(Backup backup, Path p, Runnable onSuccess);
}

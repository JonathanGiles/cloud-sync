package net.jonathangiles.cloudsync.engine.util;

import lombok.Getter;
import net.jonathangiles.cloudsync.engine.model.Backup;

import java.nio.file.Path;

@Getter
public class Task {

    public enum Type {
        UPLOAD_FILE,
        REPLACE_FILE,
        DELETE_FILE
    }

    public enum Status {
        QUEUED,
        IN_PROGRESS,
        COMPLETE
    }

    public static Task create(Type type, Backup backup, Path p, Runnable r) {
        Task t = new Task();
        t.type = type;
        t.backup = backup;
        t.path = p;
        t.runnable = r;
        return t;
    }

    private Type type;
    private Backup backup;
    private Path path;
    private Runnable runnable;

    // TODO update the task status as it occurs, in an observable way
    private Status status = Status.QUEUED;

    private Task() { }
}

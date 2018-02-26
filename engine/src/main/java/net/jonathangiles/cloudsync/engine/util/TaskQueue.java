package net.jonathangiles.cloudsync.engine.util;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import net.jonathangiles.cloudsync.engine.BackupEngine;
import net.jonathangiles.cloudsync.engine.cloud.CloudStore;

import javax.inject.Singleton;

/**
 * Rather than have the {@link BackupEngine} push directly into the
 * implementation of {@link CloudStore}, we instead have this
 * intermediate task queue to push tasks related to file changes to the cloud. This enables better client-side reporting
 * when we know the size and contents of the pending tasks.
 */
@Singleton
public class TaskQueue {

    private final Subject<Task> bus = PublishSubject.create();

    public void send(Task task) {
        bus.onNext(task);
    }

    public Observable<Task> toObserverable() {
        return bus;
    }
}

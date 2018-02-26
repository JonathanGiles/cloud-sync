package net.jonathangiles.cloudsync.engine.cloud.azure;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import net.jonathangiles.cloudsync.engine.cloud.CloudStore;
import net.jonathangiles.cloudsync.engine.model.Backup;
import net.jonathangiles.cloudsync.engine.util.Task;
import net.jonathangiles.cloudsync.engine.util.TaskQueue;
import net.jonathangiles.cloudsync.engine.util.config.Config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
public class AzureCloudStore implements CloudStore {

    private static final String BACKUP_CONTAINER_KEY = "BACKUP_CONTAINER";

    // azure
    private final CloudBlobClient serviceClient;

    // subscribe to task events
    private final TaskQueue taskQueue;

    private final ExecutorService azureExecutor = Executors.newFixedThreadPool(5);

    @Inject
    public AzureCloudStore(TaskQueue taskQueue, Config config) {
        this.taskQueue = taskQueue;
        this.taskQueue.toObserverable().subscribe(this::process);

        CloudBlobClient _serviceClient = null;
        try {
            // init connection to Azure
            String connectString =
                    "DefaultEndpointsProtocol=https;"
                    + "AccountName=" + config.getAzureAccountName() + ";"
                    + "AccountKey=" + config.getAzureAccountKey();

            CloudStorageAccount account = CloudStorageAccount.parse(connectString);
            _serviceClient = account.createCloudBlobClient();
        } catch (Exception e) {
            System.out.print("Exception encountered: ");
            System.out.println(e.getMessage());
            System.exit(-1);
        }
        serviceClient = _serviceClient;
    }

    @Override
    public void createContainer(Backup backup) {
        try {
            // Container name must be lower case and should obviously be unique
            CloudBlobContainer container = serviceClient.getContainerReference(backup.getBackupName());
            container.createIfNotExists();
            backup.getRuntimeProperties().put(BACKUP_CONTAINER_KEY, container);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            System.exit(-1);
        } catch (StorageException storageException) {
            System.out.print("StorageException encountered for backup: ");
            System.out.println(storageException.getMessage());
            System.exit(-1);
        }
    }

    @Override
    public void uploadFile(Backup backup, Path p, Runnable onSuccess) {
        azureExecutor.submit(() -> {
            try {
                System.out.println("Uploading file " + p);
                CloudBlockBlob blob = getContainer(backup).getBlockBlobReference(p.toString());
                blob.upload(Files.newInputStream(p), Files.size(p));
                if (onSuccess != null) {
                    onSuccess.run();
                }
                System.out.println("Uploading complete for " + p);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void removeFile(Backup backup, Path p, Runnable onSuccess) {
        azureExecutor.submit(() -> {
            try {
                System.out.println("Deleting file " + p);
                CloudBlockBlob blob = getContainer(backup).getBlockBlobReference(p.toString());
                blob.delete();
                if (onSuccess != null) {
                    onSuccess.run();
                }
                System.out.println("Deleting complete for " + p);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void process(Task task) {
        switch (task.getType()) {
            case UPLOAD_FILE:
            case REPLACE_FILE: uploadFile(task.getBackup(), task.getPath(), task.getRunnable()); break;
            case DELETE_FILE: removeFile(task.getBackup(), task.getPath(), task.getRunnable()); break;
        }
    }

    private CloudBlobContainer getContainer(Backup backup) {
        return backup.getRuntimeProperty(BACKUP_CONTAINER_KEY, CloudBlobContainer.class);
    }
}

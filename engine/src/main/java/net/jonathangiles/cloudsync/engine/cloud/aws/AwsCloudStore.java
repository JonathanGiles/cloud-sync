package net.jonathangiles.cloudsync.engine.cloud.aws;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import lombok.extern.slf4j.Slf4j;
import net.jonathangiles.cloudsync.engine.cloud.CloudStore;
import net.jonathangiles.cloudsync.engine.model.Backup;
import net.jonathangiles.cloudsync.engine.util.Task;
import net.jonathangiles.cloudsync.engine.util.TaskQueue;
import net.jonathangiles.cloudsync.engine.util.config.Config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
@Slf4j
public class AwsCloudStore implements CloudStore {
	private static final String BACKUP_CONTAINER_KEY = "BACKUP_CONTAINER";
	
	// Subscribe to task events
	private final TaskQueue taskQueue;
	// Amazon S3 client
	private final AmazonS3 s3Client;
	// Amazon S3 region
	private final Regions region;
	// Amazon S3 bucket name. The bucket name must be unique across S3.
	private String bucket;
	
	private final ExecutorService awsExecutor = Executors.newFixedThreadPool(5);
	
	@Inject
	public AwsCloudStore(TaskQueue taskQueue, Config config) {
		this.taskQueue = taskQueue;
		this.taskQueue.toObserverable().subscribe(this::process);
		this.region = Regions.fromName(config.getAwsRegion());
		
		BasicAWSCredentials awsCredentials = new BasicAWSCredentials(config.getAwsAccessKey(), config.getAwsSecretKey());
		this.s3Client = AmazonS3ClientBuilder.standard()
				.withRegion(config.getAwsRegion())
				.withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
				.withRegion(region)
				.build();
		
		this.bucket = config.getAwsBucket();
	}
	
	@Override
	public void createContainer(Backup backup) {
		if(s3Client.doesBucketExistV2(bucket) == false) {
			bucket = s3Client
					.createBucket(bucket)
					.getName();
		}
		
		backup.getRuntimeProperties().put(BACKUP_CONTAINER_KEY, bucket);
	}
	
	@Override
	public void uploadFile(Backup backup, Path path, Runnable onSuccess) {
		log.debug("Uploading file: '{}'", path.toString());
		awsExecutor.submit(() -> {
			String fileKey = getFileKey(backup, path);
			s3Client.putObject(bucket, fileKey, path.toFile());
			
			log.debug("'{}' uploaded with key '{}'", path.toString(), fileKey);
			if(onSuccess != null) {
				onSuccess.run();
			}
		});
	}
	
	@Override
	public void removeFile(Backup backup, Path p, Runnable onSuccess) {
		String fileKey = getFileKey(backup, p);
		s3Client.deleteObject(bucket, fileKey);
		
		log.debug("Deleted file: '{}' with key '{}'", p.toString(), fileKey);
	}
	
	private void process(Task task) {
		switch (task.getType()) {
			case UPLOAD_FILE:
				uploadFile(task.getBackup(), task.getPath(), task.getRunnable());
				break;
			case REPLACE_FILE:
				uploadFile(task.getBackup(), task.getPath(), task.getRunnable());
				break;
			case DELETE_FILE:
				removeFile(task.getBackup(), task.getPath(), task.getRunnable());
				break;
		}
	}
	
	/**
	 * Generate a file key for S3. The file key is the path to the file from the sync folder.
	 * The following file path 'F:\cloud-sync\folder1\subfolder1\fileSubFolder1.txt' will have
	 * the following key 'subfolder1/fileSubFolder1.txt'. S3 will construct this path, creating a folder named
	 * 'subfolder1' and placing there the file 'fileSubFolder1.txt'.
	 * When the file upload is completed, the file will have the following path in S3 (which is also it's key)
	 * 'bucketName/subfolder1/fileSubFolder1.txt'
	 * @param backup
	 * @param path
	 * @return a {@link String} representing the file key
	 */
	private String getFileKey(Backup backup, Path path) {
		// Parameter path => F:\cloud-sync\folder1\subfolder1\fileSubFolder1.txt
		// basePath can be 'F:\cloud-sync' or 'F:\cloud-sync\' - ending with separator or not
		String basePath = backup.getRootDirectory().getParent().toString();
		if(basePath.endsWith(File.separator)) {
			// Remove the separator to avoid PatternSyntaxException with "\"
			basePath = basePath.substring(0, basePath.length() - 1);
		}
		
		// basePath => F:\cloud-sync
		// path => F:\cloud-sync\folder1\subfolder1\fileSubFolder1.txt
		// fileKey will be => folder1\subfolder1\fileSubFolder1.txt
		String fileKey = path.toString().replace(basePath, "");
		
		// In case fileKey starts with separator, remove it
		if(fileKey.startsWith(File.separator)) {
			// Remove the file separator from the beginning of the key
			fileKey = fileKey.substring(1, fileKey.length());
		}
		
		// Amazon S3 uses "/" as file separator so in case we are using "\" replace it with "/"
		fileKey = fileKey.replace("\\", "/");
		log.debug("File '{}' has fileKey => '{}'", path.toString(), fileKey);
		
		return fileKey;
	}
}

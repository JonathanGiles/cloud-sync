package net.jonathangiles.cloudsync.engine.db.jpa;

import net.jonathangiles.cloudsync.engine.db.DataStore;
import net.jonathangiles.cloudsync.engine.model.Backup;
import net.jonathangiles.cloudsync.engine.model.LocalRecord;
import net.jonathangiles.cloudsync.engine.util.config.Config;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

@Singleton
public class JPADataStore implements DataStore {

    private static final String PERSISTENCE_UNIT_NAME = "backupDB";
    private EntityManagerFactory factory;
    private EntityManager entityManager;

    private final Config config;

    private ExecutorService dbThread = Executors.newSingleThreadExecutor();

    @Inject
    JPADataStore(Config config) {
        this.config = config;

        factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
        entityManager = factory.createEntityManager();

        init();
    }

    private void init() {
        // get the configured backup sets from the local Config file
        // and the backups that have previously been instantiated and loaded into the local DB
        Query q = entityManager.createQuery("select b from Backup b");
        List<Backup> backupsInDB = q.getResultList();

        // now we reconcile...
        // Anything in the config file that is not in the DB, we create in the DB
        // Anything in the DB that is not in the config file, we delete from the DB

        // step one: see if there is a backup in the DB for a given config file, and if not, add one
        config.getBackupConfig().forEach(backupConfig -> {
            boolean exists = backupsInDB.stream().anyMatch(backup -> Config.BackupConfig.match(backupConfig, backup));
            if (!exists) {
                transact(() -> {
                    Backup backup = new Backup(backupConfig.getName(), Paths.get(backupConfig.getRoot()));
                    entityManager.persist(backup);
                });
            }
        });

        // step two: see if there are backups in the DB that don't exist in the config, and remove them
        backupsInDB.forEach(backup -> {
            boolean noMatch = config.getBackupConfig().noneMatch(backupConfig -> Config.BackupConfig.match(backupConfig, backup));
            if (noMatch) {
                transact(() -> entityManager.remove(backup));
            }
        });
    }

    public List<Backup> getBackupList() {
        return entityManager.createQuery("select b from Backup b").getResultList();
    }

    @Override
    public LocalRecord getLocalRecord(Backup backup, Path p) {
        // the same path might be in multiple backup sets - we should ensure we are looking at the right one
        try {
            return (LocalRecord) entityManager
                    .createQuery("select r from LocalRecord r where r.filePath = :filePath and r.backup = :backup")
                    .setParameter("filePath", p.toString())
                    .setParameter("backup", backup)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public void updateLocalRecord(Backup backup, LocalRecord record) {
        transact(() -> {
            final boolean added = backup.addRecord(record);
            if (added) {
                entityManager.persist(backup);
            }
        });
    }

    @Override
    public void deleteLocalRecord(LocalRecord record) {
        transact(() -> {
            record.getBackup().removeRecord(record);
            entityManager.persist(record.getBackup());
            entityManager.remove(record);
        });
    }

    @Override
    public Stream<LocalRecord> getBackupRecords(Backup backup) {
        return entityManager.createQuery("select r from LocalRecord r where r.backup = :backup", LocalRecord.class)
                .setParameter("backup", backup)
                .getResultStream();
    }

    private void transact(Runnable r) {
        dbThread.submit(() -> {
            entityManager.getTransaction().begin();
            r.run();
            entityManager.getTransaction().commit();
        });
    }
}

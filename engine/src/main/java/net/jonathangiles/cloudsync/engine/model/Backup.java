package net.jonathangiles.cloudsync.engine.model;

import lombok.Data;

import javax.persistence.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

@Entity
@Data
@Table(name="backup")
public class Backup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String backupName;

    @Transient
    private Path rootDirectory;
    private String rootDirectoryString;

    @OneToMany(mappedBy = "backup", cascade = CascadeType.ALL)
    private Collection<LocalRecord> records;

    @Transient
    private final Map<String, Object> runtimeProperties;

    public Backup() {
        records = new TreeSet<>();
        runtimeProperties = new HashMap<>();
    }

    public Backup(String name, Path rootDirectory) {
        this();
        setBackupName(name);
        setRootDirectory(rootDirectory);
    }

    public Path getRootDirectory() {
        if (rootDirectory == null && rootDirectoryString != null) {
            setRootDirectory(Paths.get(rootDirectoryString));
        }
        return rootDirectory;
    }

    public void setRootDirectory(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
        this.rootDirectoryString = rootDirectory != null ? rootDirectory.toString() : null;
    }

    public boolean addRecord(LocalRecord record) {
        return records.add(record);
    }

    public void removeRecord(LocalRecord record) {
        records.remove(record);
    }

    public <T> T getRuntimeProperty(String key, Class<T> cls) {
        return cls.cast(runtimeProperties.get(key));
    }

    @Override
    public String toString() {
        return "Backup{" +
                "id=" + id +
                ", backupName='" + backupName + '\'' +
                ", rootDirectory=" + rootDirectory +
                '}';
    }
}

package net.jonathangiles.cloudsync.engine.model;

import lombok.Data;

import javax.persistence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Entity
@Data
@Table(name = "record")
public class LocalRecord implements Comparable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Backup backup;

    private String filePath;
    private long lastModifiedTime;
    private long size;

    protected LocalRecord() { }

    public static Optional<LocalRecord> create(Backup backup, Path p) {
        try {
            LocalRecord r = new LocalRecord();
            configure(r, backup, p);
            return Optional.of(r);
        } catch (IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public void update(Backup backup, Path p) {
        try {
            configure(this, backup, p);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean matches(Path p) {
        try {
            return p != null &&
                    p.toString().equals(filePath) &&
                    Files.getLastModifiedTime(p).toMillis() == lastModifiedTime &&
                    Files.size(p) == size;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void configure(LocalRecord r, Backup backup, Path p) throws IOException {
        r.backup = backup;
        r.filePath = p.toString();
        r.lastModifiedTime = Files.getLastModifiedTime(p).toMillis();
        r.size = Files.size(p);
    }

    public Path getPath() {
        return Paths.get(filePath);
    }

    @Override
    public int compareTo(Object o) {
        return filePath.compareTo(((LocalRecord)o).filePath);
    }
}

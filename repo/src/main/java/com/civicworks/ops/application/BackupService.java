package com.civicworks.ops.application;

import com.civicworks.ops.domain.BackupRun;
import com.civicworks.ops.infra.BackupRunRepository;
import com.civicworks.platform.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final BackupRunRepository backupRepo;
    private final AuditService auditService;
    private final String backupDir;

    public BackupService(BackupRunRepository backupRepo, AuditService auditService,
                         @Value("${civicworks.backup.dir:/tmp/civicworks-backups}") String backupDir) {
        this.backupRepo = backupRepo;
        this.auditService = auditService;
        this.backupDir = backupDir;
    }

    public BackupRun performBackup() {
        Instant started = Instant.now();
        // pg_dump --format=custom produces a compressed binary archive (not a
        // gzipped SQL stream). The .dump extension matches the actual format
        // and matches PostgreSQL convention for restore via pg_restore.
        String fileName = "civicworks_backup_" + LocalDate.now() + ".dump";
        String filePath = backupDir + "/" + fileName;

        BackupRun run = new BackupRun();
        run.setFilePath(filePath);
        run.setStartedAt(started);

        try {
            File dir = new File(backupDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Execute pg_dump
            ProcessBuilder pb = new ProcessBuilder(
                    "pg_dump", "-h", "db", "-U", "civicworks", "-d", "civicworks",
                    "--format=custom", "-f", filePath
            );
            pb.environment().put("PGPASSWORD", System.getenv("SPRING_DATASOURCE_PASSWORD"));
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                File backupFile = new File(filePath);
                run.setFileSize(backupFile.length());
                run.setChecksum(computeChecksum(backupFile));
                run.setStatus("COMPLETED");
            } else {
                run.setStatus("FAILED");
            }
        } catch (Exception e) {
            log.error("Backup failed", e);
            run.setStatus("FAILED");
        }

        run.setCompletedAt(Instant.now());
        BackupRun saved = backupRepo.save(run);
        auditService.log(null, "SYSTEM", "BACKUP_RUN", "backup",
                saved.getId().toString(), "status=" + run.getStatus());
        return saved;
    }

    private String computeChecksum(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "error-computing-checksum";
        }
    }
}

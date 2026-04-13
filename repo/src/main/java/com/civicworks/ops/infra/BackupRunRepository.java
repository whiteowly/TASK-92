package com.civicworks.ops.infra;

import com.civicworks.ops.domain.BackupRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupRunRepository extends JpaRepository<BackupRun, Long> {
}

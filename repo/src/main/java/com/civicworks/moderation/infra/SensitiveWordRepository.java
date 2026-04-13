package com.civicworks.moderation.infra;

import com.civicworks.moderation.domain.SensitiveWord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SensitiveWordRepository extends JpaRepository<SensitiveWord, Long> {
    List<SensitiveWord> findByActiveTrue();
}

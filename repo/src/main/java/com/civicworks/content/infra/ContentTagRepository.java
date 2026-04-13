package com.civicworks.content.infra;

import com.civicworks.content.domain.ContentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ContentTagRepository extends JpaRepository<ContentTag, Long> {
    Optional<ContentTag> findByName(String name);
}

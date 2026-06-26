package com.paperagent.repository;

import com.paperagent.entity.WritingVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WritingVersionRepository extends JpaRepository<WritingVersion, Long> {
    List<WritingVersion> findAllByOrderByCreatedAtDesc();
}

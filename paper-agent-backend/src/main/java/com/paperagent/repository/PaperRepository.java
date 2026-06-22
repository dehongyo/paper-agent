package com.paperagent.repository;

import com.paperagent.entity.Paper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PaperRepository extends JpaRepository<Paper, Long> {

    List<Paper> findAllByOrderByCreatedAtDesc();

    List<Paper> findByStatus(Paper.PaperStatus status);

    List<Paper> findByIdIn(List<Long> ids);

    @Query("""
        SELECT p FROM Paper p
        WHERE (:status IS NULL OR p.status = :status)
          AND (:query IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(COALESCE(p.authors, '')) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(COALESCE(p.summary, '')) LIKE LOWER(CONCAT('%', :query, '%')))
          AND (:tag IS NULL OR LOWER(COALESCE(p.tags, '')) LIKE LOWER(CONCAT('%', :tag, '%')))
        ORDER BY p.createdAt DESC
        """)
    List<Paper> search(
            @Param("query") String query,
            @Param("status") Paper.PaperStatus status,
            @Param("tag") String tag
    );
}

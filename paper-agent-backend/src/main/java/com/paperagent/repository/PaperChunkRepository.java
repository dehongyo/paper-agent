package com.paperagent.repository;

import com.paperagent.entity.PaperChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaperChunkRepository extends JpaRepository<PaperChunk, Long> {

    List<PaperChunk> findByPaperIdOrderByChunkIndex(Long paperId);

    void deleteByPaperId(Long paperId);
}

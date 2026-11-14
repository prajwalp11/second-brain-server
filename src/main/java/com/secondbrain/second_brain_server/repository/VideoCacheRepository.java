package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.VideoCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VideoCacheRepository extends JpaRepository<VideoCache, UUID> {
    Optional<VideoCache> findByQueryKey(String queryKey);
}

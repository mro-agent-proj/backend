package com.skala.mro.domain.request.repository;

import com.skala.mro.domain.request.entity.RequestStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestStatusHistoryRepository extends JpaRepository<RequestStatusHistory, Long> {

    List<RequestStatusHistory> findByRequestIdOrderByIdAsc(UUID requestId);
}

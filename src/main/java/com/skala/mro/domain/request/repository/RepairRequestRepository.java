package com.skala.mro.domain.request.repository;

import com.skala.mro.domain.request.entity.RepairRequest;
import com.skala.mro.domain.request.entity.RequestStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RepairRequestRepository extends JpaRepository<RepairRequest, UUID>,
        JpaSpecificationExecutor<RepairRequest> {

    List<RepairRequest> findByStatusInAndCreatedAtBefore(Collection<RequestStatus> statuses, Instant before);
}

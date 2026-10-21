package com.skala.mro.domain.request.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.mro.domain.agent.dto.RunRequestedEvent;
import com.skala.mro.domain.agent.repository.AgentAssessmentRepository;
import com.skala.mro.domain.master.entity.Aircraft;
import com.skala.mro.domain.request.dto.AssessmentItem;
import com.skala.mro.domain.request.dto.CreateRepairRequest;
import com.skala.mro.domain.request.dto.HistoryItem;
import com.skala.mro.domain.request.dto.RepairRequestResponse;
import com.skala.mro.domain.request.dto.RequestSearch;
import com.skala.mro.domain.request.dto.TraceResponse;
import com.skala.mro.domain.request.entity.Actor;
import com.skala.mro.domain.request.entity.RepairRequest;
import com.skala.mro.domain.request.repository.RepairRequestRepository;
import com.skala.mro.domain.request.repository.RequestStatusHistoryRepository;
import com.skala.mro.global.common.PageResponse;
import com.skala.mro.global.security.AuthUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RepairRequestService {

    private final RepairRequestRepository requestRepository;
    private final RequestStatusHistoryRepository historyRepository;
    private final AgentAssessmentRepository assessmentRepository;
    private final IntakeValidator intakeValidator;
    private final RequestAccessPolicy accessPolicy;
    private final RequestWorkflow workflow;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /** 접수 확인을 통과하면 저장하고, 커밋 후 AI 서버에 실행을 요청합니다 (AgentDispatcher). */
    @Transactional
    public RepairRequestResponse create(AuthUser reporter, CreateRepairRequest command) {
        Aircraft aircraft = intakeValidator.validate(command, reporter);
        RepairRequest request = workflow.submit(
                RepairRequest.create(reporter.id(), aircraft, command.symptomText().trim()),
                Actor.user(reporter.id()));
        eventPublisher.publishEvent(new RunRequestedEvent(request.getId()));
        return RepairRequestResponse.from(request);
    }

    public PageResponse<RepairRequestResponse> list(AuthUser user, RequestSearch search, Pageable pageable) {
        Specification<RepairRequest> spec = accessPolicy.visibleTo(user);
        if (search.mine()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("reporterId"), user.id()));
        }
        if (search.status() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), search.status()));
        }
        if (search.department() != null && !search.department().isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("assignedDepartment"), search.department()));
        }
        if (search.from() != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), search.from()));
        }
        if (search.to() != null) {
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("createdAt"), search.to()));
        }
        return PageResponse.of(requestRepository.findAll(spec, pageable), RepairRequestResponse::from);
    }

    public RepairRequestResponse get(AuthUser user, UUID requestId) {
        return RepairRequestResponse.from(accessPolicy.loadVisible(user, requestId));
    }

    public TraceResponse trace(AuthUser user, UUID requestId) {
        RepairRequest request = accessPolicy.loadVisible(user, requestId);
        return new TraceResponse(
                RepairRequestResponse.from(request),
                assessmentRepository.findByRequestIdOrderByIdAsc(requestId).stream()
                        .map(a -> new AssessmentItem(a.getStage(), a.getAttempt(), readJson(a.getOutput()),
                                a.getCreatedAt()))
                        .toList(),
                historyRepository.findByRequestIdOrderByIdAsc(requestId).stream().map(HistoryItem::from).toList());
    }

    private JsonNode readJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return objectMapper.getNodeFactory().textNode(json);
        }
    }
}

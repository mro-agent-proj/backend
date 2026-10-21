package com.skala.mro.domain.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.mro.domain.agent.dto.AgentEventMessage;
import com.skala.mro.domain.agent.entity.AgentAssessment;
import com.skala.mro.domain.agent.entity.AgentEvent;
import com.skala.mro.domain.agent.repository.AgentAssessmentRepository;
import com.skala.mro.domain.agent.repository.AgentEventRepository;
import com.skala.mro.domain.master.repository.DepartmentRepository;
import com.skala.mro.domain.request.entity.Actor;
import com.skala.mro.domain.request.entity.RepairRequest;
import com.skala.mro.domain.request.entity.RequestStatus;
import com.skala.mro.domain.request.entity.ReviewReason;
import com.skala.mro.domain.request.repository.RepairRequestRepository;
import com.skala.mro.domain.request.service.RequestWorkflow;
import com.skala.mro.global.error.ApiException;
import com.skala.mro.global.error.ErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 서버 이벤트(mro.agent.events)를 요청 상태에 반영합니다.
 * AI 서버는 "판단"만 보내고, 배정 실행(부서·근거 검증 → ASSIGNED)은 여기서 Spring이 합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentEventService {

    public enum Result {
        APPLIED, DUPLICATE, IGNORED
    }

    private final RepairRequestRepository requestRepository;
    private final AgentEventRepository eventRepository;
    private final AgentAssessmentRepository assessmentRepository;
    private final DepartmentRepository departmentRepository;
    private final EvidenceValidator evidenceValidator;
    private final RequestWorkflow workflow;
    private final ObjectMapper objectMapper;

    @Transactional
    public Result handle(AgentEventMessage event) {
        if (event.eventId() == null || event.requestId() == null || event.type() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "event_id, request_id, type은 필수입니다.");
        }
        UUID requestId = event.requestId();
        RepairRequest request = requestRepository.findById(requestId).orElseThrow(ApiException::notFound);
        if (eventRepository.existsById(event.eventId())) {
            return Result.DUPLICATE;
        }
        JsonNode payload = event.payload() == null ? objectMapper.createObjectNode() : event.payload();
        eventRepository.save(new AgentEvent(event, toJson(payload)));

        if (!request.getStatus().isInProgress()) {
            // 시간 초과 등으로 이미 사람 검토로 넘어간 뒤 늦게 도착한 이벤트
            log.warn("진행 중이 아닌 요청의 AI 이벤트 무시: request_id={}, status={}, type={}",
                    requestId, request.getStatus(), event.type());
            return Result.IGNORED;
        }

        switch (event.type()) {
            case STAGE_STARTED -> {
                RequestStatus stage = stageOf(payload);
                if (request.getStatus() != stage) {
                    workflow.transition(request, stage, Actor.AI, "attempt " + payload.path("attempt").asInt(1));
                }
            }
            case STAGE_RESULT -> {
                RequestStatus stage = stageOf(payload);
                assessmentRepository.save(new AgentAssessment(requestId, stage.name(),
                        payload.path("attempt").asInt(1), toJson(payload.get("output"))));
            }
            case COMPLETED -> complete(request, payload);
            case FAILED -> workflow.sendToHumanReview(request, ReviewReason.SYSTEM_ERROR, Actor.AI,
                    "AI 서버 오류: " + payload.path("error").asText(""));
        }
        return Result.APPLIED;
    }

    /**
     * 실행 요청이 AI 서버에 전달되지 못했을 때 (발행 실패, 실행 요청 DLQ 도착).
     * 아직 AI 처리 중인 요청만 사람 검토(SYSTEM_ERROR)로 넘깁니다.
     */
    @Transactional
    public void failRun(UUID requestId, String note) {
        requestRepository.findById(requestId)
                .filter(request -> request.getStatus().isInProgress())
                .ifPresent(request -> workflow.sendToHumanReview(request, ReviewReason.SYSTEM_ERROR, Actor.SYSTEM,
                        note));
    }

    private void complete(RepairRequest request, JsonNode payload) {
        assessmentRepository.save(new AgentAssessment(request.getId(), AgentAssessment.FINAL_STAGE, 0,
                toJson(payload)));
        String summary = payload.path("summary").asText(null);
        String outcome = requiredText(payload, "outcome");

        switch (outcome) {
            case "ASSIGN" -> {
                String department = payload.path("department").asText("");
                if (!departmentRepository.existsByCodeAndActiveTrue(department)) {
                    workflow.updateSummary(request, summary);
                    workflow.sendToHumanReview(request, ReviewReason.VALIDATION_FAILED, Actor.AI,
                            "AI가 제안한 부서 코드가 유효하지 않습니다: " + department);
                    return;
                }
                List<String> invalid = evidenceValidator.findProblems(request.getAircraftModel(),
                        payload.get("evidence"));
                if (!invalid.isEmpty()) {
                    workflow.updateSummary(request, summary);
                    workflow.sendToHumanReview(request, ReviewReason.VALIDATION_FAILED, Actor.AI,
                            "근거 검증 실패: " + String.join(", ", invalid));
                    return;
                }
                workflow.assign(request, department, Actor.AI, summary);
            }
            case "HUMAN_REVIEW" -> {
                // AI가 완료 전에 근거·범위를 재검증하다 실패하면 VALIDATION_FAILED를 보냅니다 (팀 논의 C03).
                ReviewReason reason = switch (payload.path("review_reason").asText()) {
                    case "RETRY_LIMIT" -> ReviewReason.RETRY_LIMIT;
                    case "VALIDATION_FAILED" -> ReviewReason.VALIDATION_FAILED;
                    default -> ReviewReason.AGENT_NEEDS_HUMAN;
                };
                workflow.updateSummary(request, summary);
                workflow.sendToHumanReview(request, reason, Actor.AI, summary);
            }
            default -> throw new ApiException(ErrorCode.VALIDATION_ERROR, "알 수 없는 outcome입니다: " + outcome);
        }
    }

    private static RequestStatus stageOf(JsonNode payload) {
        String stage = requiredText(payload, "stage");
        return switch (stage) {
            case "CLASSIFYING" -> RequestStatus.CLASSIFYING;
            case "INVESTIGATING" -> RequestStatus.INVESTIGATING;
            case "REVIEWING" -> RequestStatus.REVIEWING;
            default -> throw new ApiException(ErrorCode.VALIDATION_ERROR, "알 수 없는 stage입니다: " + stage);
        };
    }

    private static String requiredText(JsonNode payload, String field) {
        String value = payload.path(field).asText("");
        if (value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "payload.%s 값이 필요합니다.".formatted(field));
        }
        return value;
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}

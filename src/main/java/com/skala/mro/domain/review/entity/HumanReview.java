package com.skala.mro.domain.review.entity;

import com.skala.mro.domain.request.entity.ReviewReason;
import com.skala.mro.global.error.ApiException;
import com.skala.mro.global.error.ErrorCode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "human_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HumanReview {

    @Id
    private UUID id;

    private UUID requestId;

    @Enumerated(EnumType.STRING)
    private ReviewReason reason;

    @Enumerated(EnumType.STRING)
    private ReviewStatus status;

    private String note;

    @Enumerated(EnumType.STRING)
    private ReviewAction resolution;

    private String departmentCode;

    private String resolutionNote;

    private UUID resolvedBy;

    private Instant createdAt;

    private Instant resolvedAt;

    @Version
    private Long version;

    public static HumanReview open(UUID requestId, ReviewReason reason, String note) {
        HumanReview review = new HumanReview();
        review.id = UUID.randomUUID();
        review.requestId = requestId;
        review.reason = reason;
        review.status = ReviewStatus.OPEN;
        review.note = note;
        review.createdAt = Instant.now();
        return review;
    }

    public void resolve(ReviewAction action, String departmentCode, String note, UUID adminId) {
        if (status != ReviewStatus.OPEN) {
            throw new ApiException(ErrorCode.INVALID_STATE, "이미 처리된 검토입니다.");
        }
        this.status = ReviewStatus.RESOLVED;
        this.resolution = action;
        this.departmentCode = departmentCode;
        this.resolutionNote = note;
        this.resolvedBy = adminId;
        this.resolvedAt = Instant.now();
    }
}

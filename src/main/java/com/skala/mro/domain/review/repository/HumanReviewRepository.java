package com.skala.mro.domain.review.repository;

import com.skala.mro.domain.review.entity.HumanReview;
import com.skala.mro.domain.review.entity.ReviewStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HumanReviewRepository extends JpaRepository<HumanReview, UUID> {

    Page<HumanReview> findByStatus(ReviewStatus status, Pageable pageable);
}

package com.austinharlan.trading_dashboard.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface FinanceCategoryRepository extends JpaRepository<FinanceCategoryEntity, String> {
  List<FinanceCategoryEntity> findAllByUserIdOrderBySortOrderAscLabelAsc(Long userId);

  Optional<FinanceCategoryEntity> findByUserIdAndSlug(Long userId, String slug);

  boolean existsByUserIdAndSlug(Long userId, String slug);

  @Transactional
  long deleteByUserIdAndSlug(Long userId, String slug);

  @Transactional
  void deleteAllByUserId(Long userId);
}

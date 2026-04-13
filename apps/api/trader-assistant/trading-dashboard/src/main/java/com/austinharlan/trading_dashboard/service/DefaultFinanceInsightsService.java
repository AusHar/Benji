package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.config.UserContext;
import com.austinharlan.trading_dashboard.finance.FinanceCategoryRecord;
import com.austinharlan.trading_dashboard.finance.FinanceSummaryData;
import com.austinharlan.trading_dashboard.finance.FinanceTransactionRecord;
import com.austinharlan.trading_dashboard.persistence.FinanceCategoryEntity;
import com.austinharlan.trading_dashboard.persistence.FinanceCategoryRepository;
import com.austinharlan.trading_dashboard.persistence.FinanceTransactionEntity;
import com.austinharlan.trading_dashboard.persistence.FinanceTransactionRepository;
import com.austinharlan.trading_dashboard.persistence.UserEntity;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultFinanceInsightsService implements FinanceInsightsService {
  private static final int DEFAULT_LIMIT = 100;
  private static final int MAX_LIMIT = 500;

  private static final List<FinanceCategoryRecord> DEFAULT_CATEGORIES =
      List.of(
          new FinanceCategoryRecord("groceries", "Groceries"),
          new FinanceCategoryRecord("dining", "Dining"),
          new FinanceCategoryRecord("housing", "Housing"),
          new FinanceCategoryRecord("utilities", "Utilities"),
          new FinanceCategoryRecord("transport", "Transport"),
          new FinanceCategoryRecord("entertainment", "Entertainment"),
          new FinanceCategoryRecord("shopping", "Shopping"),
          new FinanceCategoryRecord("health", "Health"),
          new FinanceCategoryRecord("subscriptions", "Subscriptions"),
          new FinanceCategoryRecord("gambling", "Gambling"),
          new FinanceCategoryRecord("investing", "Investing"),
          new FinanceCategoryRecord("video_games", "Video Games"),
          new FinanceCategoryRecord("income", "Income"),
          new FinanceCategoryRecord("other", "Other"));

  private final FinanceTransactionRepository transactionRepository;
  private final FinanceCategoryRepository categoryRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  @Autowired
  public DefaultFinanceInsightsService(
      FinanceTransactionRepository transactionRepository,
      FinanceCategoryRepository categoryRepository,
      UserRepository userRepository) {
    this(transactionRepository, categoryRepository, userRepository, Clock.systemUTC());
  }

  public DefaultFinanceInsightsService(
      FinanceTransactionRepository transactionRepository,
      FinanceCategoryRepository categoryRepository,
      UserRepository userRepository,
      Clock clock) {
    this.transactionRepository = transactionRepository;
    this.categoryRepository = categoryRepository;
    this.userRepository = userRepository;
    this.clock = clock;
  }

  @Override
  public FinanceSummaryData getSummary() {
    long userId = UserContext.current().userId();
    Instant now = Instant.now(clock);
    YearMonth currentMonth = YearMonth.now(clock);
    LocalDate today = LocalDate.now(clock);
    Instant startOfMonth = currentMonth.atDay(1).atStartOfDay(clock.getZone()).toInstant();
    Instant startOfNextMonth =
        currentMonth.plusMonths(1).atDay(1).atStartOfDay(clock.getZone()).toInstant();

    List<FinanceTransactionRecord> monthTransactions =
        transactionRepository
            .findWithinRangeByUserId(userId, startOfMonth, startOfNextMonth)
            .stream()
            .map(this::toRecord)
            .toList();

    BigDecimal monthToDate =
        monthTransactions.stream()
            .map(FinanceTransactionRecord::amount)
            .map(this::safeAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

    BigDecimal averageDaily =
        today.getDayOfMonth() > 0
            ? monthToDate.divide(BigDecimal.valueOf(today.getDayOfMonth()), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    BigDecimal projectedMonthEnd =
        averageDaily
            .multiply(BigDecimal.valueOf(currentMonth.lengthOfMonth()))
            .setScale(2, RoundingMode.HALF_UP);

    return new FinanceSummaryData(monthToDate, averageDaily, projectedMonthEnd, now);
  }

  @Override
  public List<FinanceTransactionRecord> listTransactions(Integer limit, String category) {
    long userId = UserContext.current().userId();
    Pageable pageable = resolvePageable(limit);
    if (StringUtils.hasText(category)) {
      String normalizedCategory = category.trim();
      return transactionRepository
          .findByUserIdAndCategoryIgnoreCaseOrderByPostedAtDesc(
              userId, normalizedCategory, pageable)
          .stream()
          .map(this::toRecord)
          .toList();
    }

    return transactionRepository.findAllByUserIdOrderByPostedAtDesc(userId, pageable).stream()
        .map(this::toRecord)
        .toList();
  }

  private BigDecimal safeAmount(BigDecimal amount) {
    return amount != null ? amount : BigDecimal.ZERO;
  }

  private Pageable resolvePageable(Integer limit) {
    int effectiveLimit = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    return PageRequest.of(0, effectiveLimit, Sort.by(Sort.Direction.DESC, "postedAt"));
  }

  private FinanceTransactionRecord toRecord(FinanceTransactionEntity entity) {
    return new FinanceTransactionRecord(
        entity.getId(),
        entity.getPostedAt(),
        entity.getDescription(),
        entity.getAmount(),
        entity.getCategory(),
        entity.getNotes());
  }

  @Override
  public FinanceTransactionRecord createTransaction(
      Instant postedAt, String description, BigDecimal amount, String category, String notes) {
    long userId = UserContext.current().userId();
    Instant resolvedPostedAt = postedAt != null ? postedAt : Instant.now(clock);
    String trimmedDescription = description == null ? "" : description.trim();
    if (trimmedDescription.isEmpty()) {
      throw new IllegalArgumentException("Description is required.");
    }
    if (amount == null) {
      throw new IllegalArgumentException("Amount is required.");
    }
    String slug = slugify(category);
    if (slug.isEmpty()) {
      throw new IllegalArgumentException("Category is required.");
    }
    if (!categoryRepository.existsByUserIdAndSlug(userId, slug)) {
      throw new IllegalArgumentException("Unknown category: " + slug);
    }
    String trimmedNotes = (notes == null || notes.isBlank()) ? null : notes.trim();

    FinanceTransactionEntity entity =
        new FinanceTransactionEntity(
            userId,
            resolvedPostedAt,
            trimmedDescription,
            amount.setScale(2, RoundingMode.HALF_UP),
            slug,
            trimmedNotes);
    FinanceTransactionEntity saved = transactionRepository.save(entity);
    return toRecord(saved);
  }

  @Override
  @Transactional
  public List<FinanceCategoryRecord> listCategories() {
    long userId = UserContext.current().userId();
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalStateException("Current user not found: " + userId));

    if (!user.isCategorySeeded()) {
      seedDefaults(userId);
      user.setCategorySeeded(true);
      userRepository.save(user);
    }

    return categoryRepository.findAllByUserIdOrderBySortOrderAscLabelAsc(userId).stream()
        .map(e -> new FinanceCategoryRecord(e.getSlug(), e.getLabel()))
        .toList();
  }

  @Override
  public FinanceCategoryRecord createCategory(String label) {
    long userId = UserContext.current().userId();
    String trimmedLabel = label == null ? "" : label.trim();
    if (trimmedLabel.isEmpty()) {
      throw new IllegalArgumentException("Category label is required.");
    }
    if (trimmedLabel.length() > 64) {
      throw new IllegalArgumentException("Category label must be 64 characters or fewer.");
    }
    String slug = slugify(trimmedLabel);
    if (slug.isEmpty()) {
      throw new IllegalArgumentException(
          "Category label must contain at least one letter or digit.");
    }
    if (categoryRepository.existsByUserIdAndSlug(userId, slug)) {
      throw new IllegalArgumentException("Category already exists: " + trimmedLabel);
    }
    FinanceCategoryEntity saved =
        categoryRepository.save(new FinanceCategoryEntity(userId, slug, trimmedLabel, 0));
    return new FinanceCategoryRecord(saved.getSlug(), saved.getLabel());
  }

  @Override
  public boolean deleteCategory(String slug) {
    long userId = UserContext.current().userId();
    String normalized = slugify(slug);
    if (normalized.isEmpty()) {
      return false;
    }
    return categoryRepository.deleteByUserIdAndSlug(userId, normalized) > 0;
  }

  private void seedDefaults(long userId) {
    int order = 1;
    for (FinanceCategoryRecord def : DEFAULT_CATEGORIES) {
      if (!categoryRepository.existsByUserIdAndSlug(userId, def.slug())) {
        categoryRepository.save(new FinanceCategoryEntity(userId, def.slug(), def.label(), order));
      }
      order++;
    }
  }

  private static String slugify(String value) {
    if (value == null) {
      return "";
    }
    return value
        .toLowerCase(Locale.ROOT)
        .trim()
        .replaceAll("[^a-z0-9]+", "_")
        .replaceAll("^_|_$", "");
  }
}

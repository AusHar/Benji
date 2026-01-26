package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.persistence.PortfolioPositionEntity;
import com.austinharlan.trading_dashboard.persistence.PortfolioPositionRepository;
import com.austinharlan.trading_dashboard.portfolio.PortfolioHolding;
import com.austinharlan.trading_dashboard.portfolio.PortfolioSnapshot;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DefaultPortfolioService implements PortfolioService {
  private static final Comparator<PortfolioHolding> HOLDING_COMPARATOR =
      Comparator.comparing(PortfolioHolding::ticker, String.CASE_INSENSITIVE_ORDER);

  private final PortfolioPositionRepository repository;

  @Override
  public List<PortfolioHolding> listHoldings() {
    return repository.findAll().stream().map(this::toHolding).sorted(HOLDING_COMPARATOR).toList();
  }

  @Override
  public Optional<PortfolioSnapshot> summarize() {
    List<PortfolioHolding> holdings = listHoldings();
    if (holdings.isEmpty()) {
      return Optional.empty();
    }

    BigDecimal totalQuantity =
        holdings.stream().map(PortfolioHolding::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalCostBasis =
        holdings.stream().map(PortfolioHolding::costBasis).reduce(BigDecimal.ZERO, BigDecimal::add);

    return Optional.of(new PortfolioSnapshot(holdings.size(), totalQuantity, totalCostBasis));
  }

  @Override
  public Optional<PortfolioHolding> findByTicker(String ticker) {
    if (!StringUtils.hasText(ticker)) {
      return Optional.empty();
    }
    return repository.findByTickerIgnoreCase(ticker.trim()).map(this::toHolding);
  }

  @Override
  @Transactional
  public PortfolioHolding create(String ticker, BigDecimal quantity, BigDecimal costBasis) {
    String normalizedTicker = ticker.trim().toUpperCase();
    PortfolioPositionEntity entity =
        new PortfolioPositionEntity(normalizedTicker, quantity, costBasis);
    PortfolioPositionEntity saved = repository.save(entity);
    return toHolding(saved);
  }

  @Override
  @Transactional
  public Optional<PortfolioHolding> update(
      String ticker, BigDecimal quantity, BigDecimal costBasis) {
    if (!StringUtils.hasText(ticker)) {
      return Optional.empty();
    }

    return repository
        .findByTickerIgnoreCase(ticker.trim())
        .map(
            entity -> {
              if (quantity != null) {
                entity.setQty(quantity);
              }
              if (costBasis != null) {
                entity.setBasis(costBasis);
              }
              return toHolding(repository.save(entity));
            });
  }

  @Override
  @Transactional
  public boolean delete(String ticker) {
    if (!StringUtils.hasText(ticker)) {
      return false;
    }
    if (repository.existsByTickerIgnoreCase(ticker.trim())) {
      repository.deleteByTickerIgnoreCase(ticker.trim());
      return true;
    }
    return false;
  }

  @Override
  public boolean existsByTicker(String ticker) {
    if (!StringUtils.hasText(ticker)) {
      return false;
    }
    return repository.existsByTickerIgnoreCase(ticker.trim());
  }

  private PortfolioHolding toHolding(PortfolioPositionEntity entity) {
    return new PortfolioHolding(entity.getTicker(), entity.getQty(), entity.getBasis());
  }
}

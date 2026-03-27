package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.config.UserContext;
import com.austinharlan.trading_dashboard.persistence.PortfolioPositionEntity;
import com.austinharlan.trading_dashboard.persistence.PortfolioPositionRepository;
import com.austinharlan.trading_dashboard.portfolio.PortfolioHolding;
import com.austinharlan.trading_dashboard.portfolio.PortfolioPositionNotFoundException;
import com.austinharlan.trading_dashboard.portfolio.PortfolioSnapshot;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultPortfolioService implements PortfolioService {
  private static final Comparator<PortfolioHolding> HOLDING_COMPARATOR =
      Comparator.comparing(PortfolioHolding::ticker, String.CASE_INSENSITIVE_ORDER);

  private final PortfolioPositionRepository repository;

  @Override
  public List<PortfolioHolding> listHoldings() {
    long userId = UserContext.current().userId();
    return repository.findAllByUserId(userId).stream()
        .map(this::toHolding)
        .sorted(HOLDING_COMPARATOR)
        .toList();
  }

  @Override
  public Optional<PortfolioSnapshot> summarize() {
    long userId = UserContext.current().userId();
    List<PortfolioHolding> holdings =
        repository.findAllByUserId(userId).stream()
            .map(this::toHolding)
            .sorted(HOLDING_COMPARATOR)
            .toList();
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
  @Transactional
  public PortfolioHolding addHolding(String ticker, BigDecimal quantity, BigDecimal pricePerShare) {
    long userId = UserContext.current().userId();
    BigDecimal totalBasis = quantity.multiply(pricePerShare);
    PortfolioPositionEntity entity =
        repository
            .findByUserIdAndTicker(userId, ticker)
            .map(
                existing -> {
                  existing.setQty(quantity);
                  existing.setBasis(totalBasis);
                  return existing;
                })
            .orElseGet(
                () ->
                    new PortfolioPositionEntity(userId, ticker, BigDecimal.ZERO, BigDecimal.ZERO));
    entity.setQty(quantity);
    entity.setBasis(totalBasis);
    return toHolding(repository.save(entity));
  }

  @Override
  @Transactional
  public void deleteHolding(String ticker) {
    long userId = UserContext.current().userId();
    repository
        .findByUserIdAndTicker(userId, ticker)
        .orElseThrow(() -> new PortfolioPositionNotFoundException(ticker));
    repository.deleteByUserIdAndTicker(userId, ticker);
  }

  private PortfolioHolding toHolding(PortfolioPositionEntity entity) {
    return new PortfolioHolding(entity.getTicker(), entity.getQty(), entity.getBasis());
  }
}

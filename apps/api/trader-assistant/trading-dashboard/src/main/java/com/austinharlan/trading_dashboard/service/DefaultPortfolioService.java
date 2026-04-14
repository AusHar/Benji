package com.austinharlan.trading_dashboard.service;

import com.austinharlan.trading_dashboard.config.UserContext;
import com.austinharlan.trading_dashboard.persistence.PortfolioPositionEntity;
import com.austinharlan.trading_dashboard.persistence.PortfolioPositionRepository;
import com.austinharlan.trading_dashboard.portfolio.PortfolioHolding;
import com.austinharlan.trading_dashboard.portfolio.PortfolioPositionNotFoundException;
import com.austinharlan.trading_dashboard.portfolio.PortfolioSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
            .orElseGet(() -> new PortfolioPositionEntity(userId, ticker, quantity, totalBasis));
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

  @Override
  @Transactional
  public void applyTrade(
      String ticker, String side, String assetType, BigDecimal quantity, BigDecimal pricePerShare) {
    if (!"EQUITY".equals(assetType) || (!"BUY".equals(side) && !"SELL".equals(side))) {
      return;
    }
    long userId = UserContext.current().userId();
    if ("BUY".equals(side)) {
      BigDecimal addedBasis = quantity.multiply(pricePerShare);
      repository
          .findByUserIdAndTicker(userId, ticker)
          .ifPresentOrElse(
              pos -> {
                pos.setQty(pos.getQty().add(quantity));
                pos.setBasis(pos.getBasis().add(addedBasis));
                repository.save(pos);
              },
              () ->
                  repository.save(
                      new PortfolioPositionEntity(userId, ticker, quantity, addedBasis)));
    } else {
      repository
          .findByUserIdAndTicker(userId, ticker)
          .ifPresent(
              pos -> {
                BigDecimal newQty = pos.getQty().subtract(quantity);
                if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
                  repository.deleteByUserIdAndTicker(userId, ticker);
                } else {
                  BigDecimal ratio = newQty.divide(pos.getQty(), 10, RoundingMode.HALF_UP);
                  pos.setQty(newQty);
                  pos.setBasis(pos.getBasis().multiply(ratio).setScale(6, RoundingMode.HALF_UP));
                  repository.save(pos);
                }
              });
    }
  }

  private PortfolioHolding toHolding(PortfolioPositionEntity entity) {
    return new PortfolioHolding(entity.getTicker(), entity.getQty(), entity.getBasis());
  }
}

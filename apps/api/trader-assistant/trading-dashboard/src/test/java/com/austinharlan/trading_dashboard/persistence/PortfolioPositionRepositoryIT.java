package com.austinharlan.trading_dashboard.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.testsupport.DatabaseIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PortfolioPositionRepositoryIT extends DatabaseIntegrationTest {

  @Autowired private PortfolioPositionRepository repository;

  @AfterEach
  void cleanDatabase() {
    repository.deleteAll();
  }

  @Test
  void flywayMigrationsRunOnStartup() {
    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void repositorySupportsCrudOperations() {
    PortfolioPositionEntity created =
        new PortfolioPositionEntity(
            "AAPL", new BigDecimal("1.000000"), new BigDecimal("175.500000"));

    PortfolioPositionEntity saved = repository.save(created);
    assertThat(saved.getId()).isNotNull();

    saved.setQty(new BigDecimal("2.000000"));
    saved.setBasis(new BigDecimal("351.000000"));
    PortfolioPositionEntity updated = repository.save(saved);

    assertThat(updated.getQty()).isEqualByComparingTo("2.000000");
    assertThat(updated.getBasis()).isEqualByComparingTo("351.000000");

    assertThat(repository.findByTicker("AAPL")).isPresent();

    repository.delete(updated);

    assertThat(repository.existsById(saved.getId())).isFalse();
  }
}

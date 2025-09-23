package com.austinharlan.trading_dashboard.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PortfolioPositionRepositoryIT {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("trading")
          .withUsername("trader")
          .withPassword("trader");

  @DynamicPropertySource
  private static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    registry.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
  }

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

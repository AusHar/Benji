package com.austinharlan.trading_dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.austinharlan.trading_dashboard.persistence.FinanceTransactionRepository;
import com.austinharlan.trading_dashboard.persistence.UserEntity;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoServiceH2IT {

  @Autowired DemoService demoService;
  @Autowired UserRepository userRepository;
  @Autowired FinanceTransactionRepository financeRepository;

  @Test
  void resetDemoData_seedsSpendingTransactionsOnH2() {
    UserEntity demoUser =
        userRepository
            .findByApiKey("demo")
            .orElseGet(() -> userRepository.save(new UserEntity("demo", "Demo User", false, true)));

    demoService.resetDemoData();

    assertThat(
            financeRepository.findAllByUserIdOrderByPostedAtDesc(
                demoUser.getId(), Pageable.unpaged()))
        .anySatisfy(transaction -> assertThat(transaction.getAmount()).isNegative());
  }
}

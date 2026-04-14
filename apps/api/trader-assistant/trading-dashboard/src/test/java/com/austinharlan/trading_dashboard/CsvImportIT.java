package com.austinharlan.trading_dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.austinharlan.trading_dashboard.persistence.FinanceTransactionRepository;
import com.austinharlan.trading_dashboard.persistence.TradeRepository;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import com.austinharlan.trading_dashboard.testsupport.DatabaseIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CsvImportIT extends DatabaseIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired TradeRepository tradeRepository;
  @Autowired FinanceTransactionRepository financeTransactionRepository;
  @Autowired UserRepository userRepository;

  private long userId;

  // Path relative to the service root (apps/api/trader-assistant/trading-dashboard)
  private static final Path INDIVIDUAL_CSV =
      Path.of("../../../docs/Example Statement CSV/Individual Jan1-Apr13.csv");

  @BeforeEach
  void setUp() {
    userId = userRepository.findByApiKey(TEST_API_KEY).orElseThrow().getId();
    tradeRepository.deleteAllByUserId(userId);
    financeTransactionRepository.deleteAllByUserId(userId);
  }

  @AfterEach
  void cleanup() {
    tradeRepository.deleteAllByUserId(userId);
    financeTransactionRepository.deleteAllByUserId(userId);
  }

  @Test
  void preview_returns_correct_summary_counts() throws Exception {
    byte[] csv = Files.readAllBytes(INDIVIDUAL_CSV);
    MockMultipartFile file = new MockMultipartFile("file", "Individual.csv", "text/csv", csv);

    mockMvc
        .perform(
            multipart("/api/import/csv/preview")
                .file(file)
                .param("account", "INDIVIDUAL")
                .header("X-API-KEY", TEST_API_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.errors").value(0))
        .andExpect(jsonPath("$.summary.tradesToImport").isNumber())
        .andExpect(jsonPath("$.summary.cashEventsToImport").isNumber())
        .andExpect(jsonPath("$.rows").isArray());
  }

  @Test
  void confirm_inserts_trades_and_cash_events() throws Exception {
    byte[] csv = Files.readAllBytes(INDIVIDUAL_CSV);
    MockMultipartFile file = new MockMultipartFile("file", "Individual.csv", "text/csv", csv);

    mockMvc
        .perform(
            multipart("/api/import/csv/confirm")
                .file(file)
                .param("account", "INDIVIDUAL")
                .header("X-API-KEY", TEST_API_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errors").isEmpty());

    long tradeCount =
        tradeRepository.findAllByUserIdOrderByTradeDateDescCreatedAtDesc(userId).stream()
            .filter(t -> "INDIVIDUAL".equals(t.getAccount()))
            .count();
    assertThat(tradeCount).isGreaterThan(0);
  }

  @Test
  void confirm_reimport_same_file_produces_all_duplicates() throws Exception {
    byte[] csv = Files.readAllBytes(INDIVIDUAL_CSV);

    // First import
    mockMvc.perform(
        multipart("/api/import/csv/confirm")
            .file(new MockMultipartFile("file", "Individual.csv", "text/csv", csv))
            .param("account", "INDIVIDUAL")
            .header("X-API-KEY", TEST_API_KEY));

    long countAfterFirst =
        tradeRepository.findAllByUserIdOrderByTradeDateDescCreatedAtDesc(userId).size();

    // Second import — should produce zero new rows
    mockMvc
        .perform(
            multipart("/api/import/csv/confirm")
                .file(new MockMultipartFile("file", "Individual.csv", "text/csv", csv))
                .param("account", "INDIVIDUAL")
                .header("X-API-KEY", TEST_API_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tradesImported").value(0))
        .andExpect(jsonPath("$.cashEventsImported").value(0));

    long countAfterSecond =
        tradeRepository.findAllByUserIdOrderByTradeDateDescCreatedAtDesc(userId).size();
    assertThat(countAfterSecond).isEqualTo(countAfterFirst);
  }

  @Test
  void confirm_demo_user_returns_403() throws Exception {
    mockMvc
        .perform(
            multipart("/api/import/csv/confirm")
                .file(new MockMultipartFile("file", "f.csv", "text/csv", new byte[0]))
                .param("account", "INDIVIDUAL")
                .header("X-API-KEY", "demo"))
        .andExpect(status().isForbidden());
  }
}

package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.marketdata.Quote;
import com.austinharlan.trading_dashboard.service.QuoteService;
import com.austinharlan.tradingdashboard.api.QuotesApi;
import com.austinharlan.tradingdashboard.dto.QuoteResponse;
import com.austinharlan.tradingdashboard.dto.QuotesIndex;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class QuoteController implements QuotesApi {
  private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Za-z0-9.]{1,12}$");
  private final QuoteService quoteService;

  @Override
  public ResponseEntity<QuotesIndex> getQuotesIndex() {
    QuotesIndex index =
        new QuotesIndex()
            .message("Quote service ready.")
            .endpoints(List.of("/api/quotes/{symbol}"));
    return ResponseEntity.ok(index);
  }

  @Override
  public ResponseEntity<QuoteResponse> getQuote(String symbol) {
    String normalizedSymbol = normalize(symbol);
    Quote quote = quoteService.getCached(normalizedSymbol);
    QuoteResponse response =
        new QuoteResponse()
            .symbol(quote.symbol())
            .price(quote.price().doubleValue())
            .currency("USD")
            .asOf(OffsetDateTime.ofInstant(quote.timestamp(), ZoneOffset.UTC));
    return ResponseEntity.ok(response);
  }

  private String normalize(String symbol) {
    if (symbol == null) {
      throw new InvalidTickerException("Ticker symbol is required");
    }

    String candidate = symbol.trim();
    if (candidate.isEmpty()) {
      throw new InvalidTickerException("Ticker symbol must not be blank");
    }

    if (!SYMBOL_PATTERN.matcher(candidate).matches()) {
      throw new InvalidTickerException("Ticker symbol must be 1-12 characters (A-Z, 0-9, .)");
    }

    return candidate.toUpperCase(Locale.US);
  }
}

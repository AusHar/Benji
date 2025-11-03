package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.marketdata.Quote;
import com.austinharlan.trading_dashboard.service.QuoteService;
import com.austinharlan.tradingdashboard.api.QuotesApi;
import com.austinharlan.tradingdashboard.dto.QuoteResponse;
import com.austinharlan.tradingdashboard.dto.QuotesIndex;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class QuoteController implements QuotesApi {
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
    Quote quote = quoteService.getCached(symbol);
    QuoteResponse response =
        new QuoteResponse()
            .symbol(quote.symbol())
            .price(quote.price().doubleValue())
            .currency("USD")
            .asOf(OffsetDateTime.ofInstant(quote.timestamp(), ZoneOffset.UTC));
    return ResponseEntity.ok(response);
  }
}

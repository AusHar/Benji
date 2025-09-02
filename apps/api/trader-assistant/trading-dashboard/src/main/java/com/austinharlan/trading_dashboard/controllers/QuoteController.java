package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.marketdata.Quote;
import com.austinharlan.trading_dashboard.service.QuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class QuoteController {
  private final QuoteService quoteService;

  @GetMapping("/{symbol}")
  public Quote get(@PathVariable String symbol) {
    return quoteService.getCached(symbol);
  }
}

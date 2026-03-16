package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.marketdata.CompanyOverview;
import com.austinharlan.trading_dashboard.marketdata.DailyBar;
import com.austinharlan.trading_dashboard.marketdata.MarketDataQuotaTracker;
import com.austinharlan.trading_dashboard.marketdata.Quote;
import com.austinharlan.trading_dashboard.service.QuoteService;
import com.austinharlan.tradingdashboard.api.QuotesApi;
import com.austinharlan.tradingdashboard.dto.CompanyOverviewResponse;
import com.austinharlan.tradingdashboard.dto.DailyBarDto;
import com.austinharlan.tradingdashboard.dto.MarketDataQuota;
import com.austinharlan.tradingdashboard.dto.NewsArticle;
import com.austinharlan.tradingdashboard.dto.NewsResponse;
import com.austinharlan.tradingdashboard.dto.PriceHistoryResponse;
import com.austinharlan.tradingdashboard.dto.QuoteResponse;
import com.austinharlan.tradingdashboard.dto.QuotesIndex;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuoteController implements QuotesApi {
  private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Za-z0-9.]{1,12}$");
  private final QuoteService quoteService;
  private final Optional<MarketDataQuotaTracker> quotaTracker;

  public QuoteController(QuoteService quoteService, Optional<MarketDataQuotaTracker> quotaTracker) {
    this.quoteService = quoteService;
    this.quotaTracker = quotaTracker;
  }

  @Override
  public ResponseEntity<QuotesIndex> getQuotesIndex() {
    QuotesIndex index =
        new QuotesIndex()
            .message("Quote service ready.")
            .endpoints(
                List.of(
                    "/api/quotes/{symbol}",
                    "/api/quotes/{symbol}/overview",
                    "/api/quotes/{symbol}/history",
                    "/api/quotes/{symbol}/news",
                    "/api/marketdata/quota"));
    return ResponseEntity.ok(index);
  }

  @Override
  public ResponseEntity<MarketDataQuota> getMarketDataQuota() {
    int used = quotaTracker.map(MarketDataQuotaTracker::getUsed).orElse(0);
    int limit =
        quotaTracker
            .map(MarketDataQuotaTracker::getDailyLimit)
            .orElse(MarketDataQuotaTracker.DAILY_LIMIT);
    return ResponseEntity.ok(new MarketDataQuota().used(used).dailyLimit(limit));
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

  @Override
  public ResponseEntity<CompanyOverviewResponse> getQuoteOverview(String symbol) {
    String normalizedSymbol = normalize(symbol);
    CompanyOverview overview = quoteService.getCachedOverview(normalizedSymbol);
    CompanyOverviewResponse response =
        new CompanyOverviewResponse()
            .symbol(overview.symbol())
            .name(overview.name())
            .sector(overview.sector())
            .industry(overview.industry())
            .marketCap(toDouble(overview.marketCap()))
            .peRatio(toDouble(overview.pe()))
            .eps(toDouble(overview.eps()))
            .dividendYield(toDouble(overview.dividendYield()))
            .beta(toDouble(overview.beta()))
            .fiftyTwoWeekHigh(toDouble(overview.fiftyTwoWeekHigh()))
            .fiftyTwoWeekLow(toDouble(overview.fiftyTwoWeekLow()));
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<PriceHistoryResponse> getQuoteHistory(String symbol) {
    String normalizedSymbol = normalize(symbol);
    List<DailyBar> bars = quoteService.getCachedHistory(normalizedSymbol);
    List<DailyBarDto> dtos =
        bars.stream()
            .map(
                b ->
                    new DailyBarDto()
                        .date(b.date())
                        .open(b.open().doubleValue())
                        .high(b.high().doubleValue())
                        .low(b.low().doubleValue())
                        .close(b.close().doubleValue())
                        .volume(b.volume()))
            .toList();
    PriceHistoryResponse response = new PriceHistoryResponse().symbol(normalizedSymbol).bars(dtos);
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<NewsResponse> getQuoteNews(String symbol) {
    String normalizedSymbol = normalize(symbol);
    List<com.austinharlan.trading_dashboard.marketdata.NewsArticle> articles =
        quoteService.getCachedNews(normalizedSymbol);
    if (articles.isEmpty()) {
      return ResponseEntity.noContent().build();
    }
    List<NewsArticle> dtos =
        articles.stream()
            .map(
                a ->
                    new NewsArticle()
                        .id(a.id())
                        .headline(a.headline())
                        .summary(a.summary())
                        .source(a.source())
                        .url(a.url())
                        .image(a.image())
                        .publishedAt(OffsetDateTime.ofInstant(a.publishedAt(), ZoneOffset.UTC)))
            .toList();
    return ResponseEntity.ok(new NewsResponse().symbol(normalizedSymbol).articles(dtos));
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

  private static Double toDouble(java.math.BigDecimal value) {
    return value != null ? value.doubleValue() : null;
  }
}

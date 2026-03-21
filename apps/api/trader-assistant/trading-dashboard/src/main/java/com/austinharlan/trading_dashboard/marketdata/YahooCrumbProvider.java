package com.austinharlan.trading_dashboard.marketdata;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev")
public class YahooCrumbProvider {
  private static final Logger log = LoggerFactory.getLogger(YahooCrumbProvider.class);
  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

  private final String baseUrl;
  private final HttpClient httpClient;
  private volatile String cookie;
  private volatile String crumb;

  public YahooCrumbProvider(
      @Value("${trading.marketdata.query2-base-url:https://query2.finance.yahoo.com}")
          String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  public String getCrumb() {
    String cached = crumb;
    if (cached != null) {
      return cached;
    }
    synchronized (this) {
      cached = crumb;
      if (cached != null) {
        return cached;
      }
      refresh();
      return crumb;
    }
  }

  public String getCookie() {
    if (cookie == null) {
      getCrumb();
    }
    return cookie;
  }

  public void invalidate() {
    synchronized (this) {
      crumb = null;
      cookie = null;
    }
  }

  private void refresh() {
    try {
      String sessionCookie = fetchCookie();
      String freshCrumb = fetchCrumb(sessionCookie);
      this.cookie = sessionCookie;
      this.crumb = freshCrumb;
      log.info("Yahoo Finance crumb refreshed successfully");
    } catch (MarketDataClientException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new MarketDataClientException("Failed to refresh Yahoo Finance crumb", ex);
    }
  }

  private String fetchCookie() {
    try {
      // In production, baseUrl is https://query2.finance.yahoo.com and we need to
      // hit https://fc.yahoo.com for the cookie. In tests, baseUrl is localhost
      // and the replace is a no-op, so cookie+crumb requests both go to MockWebServer.
      String cookieUrl = baseUrl.replace("query2.finance.yahoo.com", "fc.yahoo.com");
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(cookieUrl + "/"))
              .header("User-Agent", USER_AGENT)
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      return response.headers().allValues("set-cookie").stream()
          .filter(c -> c.startsWith("A3=") || c.startsWith("A1="))
          .findFirst()
          .map(c -> c.contains(";") ? c.substring(0, c.indexOf(';')) : c)
          .orElseThrow(
              () ->
                  new MarketDataClientException(
                      "No session cookie in Yahoo response (status "
                          + response.statusCode()
                          + ")"));
    } catch (IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new MarketDataClientException("Failed to fetch Yahoo session cookie", ex);
    }
  }

  private String fetchCrumb(String sessionCookie) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + "/v1/test/getcrumb"))
              .header("User-Agent", USER_AGENT)
              .header("Cookie", sessionCookie)
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new MarketDataClientException(
            "Yahoo crumb fetch failed with status " + response.statusCode());
      }

      String body = response.body();
      if (body == null || body.isBlank()) {
        throw new MarketDataClientException("Yahoo crumb response was empty");
      }
      return body.trim();
    } catch (IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new MarketDataClientException("Failed to fetch Yahoo crumb", ex);
    }
  }
}

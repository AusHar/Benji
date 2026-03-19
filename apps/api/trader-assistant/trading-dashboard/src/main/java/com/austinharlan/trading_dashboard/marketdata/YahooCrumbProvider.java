package com.austinharlan.trading_dashboard.marketdata;

import com.austinharlan.trading_dashboard.config.MarketDataProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component
@Profile("!dev")
public class YahooCrumbProvider {

  private static final Logger log = LoggerFactory.getLogger(YahooCrumbProvider.class);
  private static final String USER_AGENT = "Mozilla/5.0 (compatible; trading-dashboard/1.0)";

  private final WebClient homeClient;
  private final WebClient query2Client;
  private final Duration readTimeout;
  private final AtomicReference<String> crumbCache = new AtomicReference<>();

  public YahooCrumbProvider(WebClient.Builder builder, MarketDataProperties properties) {
    this.readTimeout = properties.getReadTimeout();

    HttpClient httpClient =
        HttpClient.create()
            .option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                (int) properties.getConnectTimeout().toMillis())
            .responseTimeout(properties.getReadTimeout())
            .doOnConnected(
                conn ->
                    conn.addHandlerLast(
                            new ReadTimeoutHandler(
                                properties.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(
                            new WriteTimeoutHandler(
                                properties.getWriteTimeout().toMillis(), TimeUnit.MILLISECONDS)));

    ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

    this.homeClient =
        builder
            .baseUrl(normalizeUrl(properties.getYahooRssBaseUrl()))
            .clientConnector(connector)
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
            .build();

    this.query2Client =
        WebClient.builder()
            .baseUrl(normalizeUrl(properties.getQuery2BaseUrl()))
            .clientConnector(connector)
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .build();
  }

  public String getCrumb() {
    String cached = crumbCache.get();
    if (cached != null) return cached;
    return fetchCrumb();
  }

  public void invalidateCrumb() {
    crumbCache.set(null);
  }

  private synchronized String fetchCrumb() {
    String cached = crumbCache.get();
    if (cached != null) return cached;

    String cookie =
        homeClient
            .get()
            .uri("/")
            .exchangeToMono(
                response -> {
                  String setCookie =
                      response.headers().asHttpHeaders().getFirst(HttpHeaders.SET_COOKIE);
                  return Mono.justOrEmpty(setCookie).defaultIfEmpty("");
                })
            .doOnError(ex -> log.warn("Failed to fetch Yahoo session cookie: {}", ex.getMessage()))
            .block(readTimeout);

    String crumb =
        query2Client
            .get()
            .uri("/v1/test/getcrumb")
            .header(HttpHeaders.COOKIE, cookie != null ? cookie : "")
            .retrieve()
            .bodyToMono(String.class)
            .doOnError(ex -> log.warn("Failed to fetch Yahoo crumb: {}", ex.getMessage()))
            .block(readTimeout);

    log.debug("Fetched fresh Yahoo crumb");
    crumbCache.set(crumb);
    return crumb;
  }

  private static String normalizeUrl(String url) {
    if (url == null) throw new MarketDataClientException("URL must not be null");
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}

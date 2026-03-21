package com.austinharlan.trading_dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class DailyImageService {
  private static final Logger log = LoggerFactory.getLogger(DailyImageService.class);
  private static final String REDDIT_URL =
      "https://www.reddit.com/r/milf/hot.json?limit=100&raw_json=1";
  private static final Pattern IMAGE_EXT = Pattern.compile("\\.(jpg|jpeg|png|webp|gif)(\\?.*)?$");

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public DailyImageService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  public record DailyImage(String url, String title, String author) {}

  @Cacheable("daily-image")
  public DailyImage getImageOfTheDay() {
    List<DailyImage> candidates = fetchImagePosts();
    if (candidates.isEmpty()) {
      return null;
    }
    int dayIndex = LocalDate.now().getDayOfYear() % candidates.size();
    return candidates.get(dayIndex);
  }

  private List<DailyImage> fetchImagePosts() {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(REDDIT_URL))
              .header("User-Agent", "Benji/1.0 (trading-dashboard)")
              .timeout(Duration.ofSeconds(15))
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        log.warn("Reddit returned status {}", response.statusCode());
        return List.of();
      }

      JsonNode root = objectMapper.readTree(response.body());
      JsonNode children = root.path("data").path("children");

      List<DailyImage> images = new ArrayList<>();
      for (JsonNode child : children) {
        JsonNode data = child.path("data");
        if (data.path("over_18").asBoolean(false)) {
          String url = data.path("url").asText("");
          String title = data.path("title").asText("Untitled");
          String author = data.path("author").asText("");
          if (isImageUrl(url)) {
            images.add(new DailyImage(url, title, author));
          }
        }
      }
      log.info("Fetched {} image candidates from Reddit", images.size());
      return images;
    } catch (Exception e) {
      log.error("Failed to fetch Reddit images", e);
      return List.of();
    }
  }

  private boolean isImageUrl(String url) {
    return url != null
        && (url.contains("i.redd.it")
            || url.contains("i.imgur.com")
            || IMAGE_EXT.matcher(url).find());
  }
}

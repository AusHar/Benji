package com.austinharlan.trading_dashboard.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.austinharlan.trading_dashboard.persistence.UserEntity;
import com.austinharlan.trading_dashboard.persistence.UserRepository;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class ApiKeyAuthFilterTest {

  private static final String VALID_API_KEY = "test-api-key-12345";
  private static final String HEADER_NAME = "X-API-KEY";

  private ApiSecurityProperties properties;
  private UserRepository userRepository;
  private ApiKeyAuthFilter filter;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    properties = new ApiSecurityProperties();
    properties.setKey(VALID_API_KEY);
    properties.setHeaderName(HEADER_NAME);
    userRepository = mock(UserRepository.class);
    filter = new ApiKeyAuthFilter(properties, userRepository);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private UserEntity testUser() {
    UserEntity user = new UserEntity("test-api-key-12345", "Test User", true, false);
    try {
      var idField = UserEntity.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(user, 1L);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
    return user;
  }

  @Test
  void allowsRequestWithValidApiKey() throws Exception {
    when(userRepository.findByApiKey(VALID_API_KEY)).thenReturn(Optional.of(testUser()));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HEADER_NAME, VALID_API_KEY);
    request.setRequestURI("/api/quotes/AAPL");

    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void rejectsRequestWithMissingApiKey() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/quotes/AAPL");

    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    verifyNoInteractions(chain);
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    assertThat(response.getContentAsString()).contains("invalid_api_key");
  }

  @Test
  void rejectsRequestWithInvalidApiKey() throws Exception {
    when(userRepository.findByApiKey("wrong-api-key")).thenReturn(Optional.empty());

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HEADER_NAME, "wrong-api-key");
    request.setRequestURI("/api/quotes/AAPL");

    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    verifyNoInteractions(chain);
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(response.getContentAsString()).contains("invalid_api_key");
  }

  @Test
  void rejectsRequestWithEmptyApiKey() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HEADER_NAME, "");
    request.setRequestURI("/api/quotes/AAPL");

    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    verifyNoInteractions(chain);
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  void rejectsRequestWithBlankApiKey() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HEADER_NAME, "   ");
    request.setRequestURI("/api/quotes/AAPL");

    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    verifyNoInteractions(chain);
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  void skipsFilterForActuatorEndpoints() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/actuator/health");

    assertThat(filter.shouldNotFilter(request)).isTrue();
  }

  @Test
  void doesNotSkipFilterForApiEndpoints() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/quotes/AAPL");

    assertThat(filter.shouldNotFilter(request)).isFalse();
  }

  @Test
  void clearsSecurityContextAfterProcessing() throws Exception {
    when(userRepository.findByApiKey(VALID_API_KEY)).thenReturn(Optional.of(testUser()));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HEADER_NAME, VALID_API_KEY);
    request.setRequestURI("/api/quotes/AAPL");

    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void setsUserContextOnValidRequest() throws Exception {
    when(userRepository.findByApiKey(VALID_API_KEY)).thenReturn(Optional.of(testUser()));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HEADER_NAME, VALID_API_KEY);
    request.setRequestURI("/api/quotes/AAPL");

    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain =
        (req, res) -> {
          // During filter chain execution, UserContext should be set
          assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
          assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
              .isInstanceOf(UserContext.class);
          UserContext ctx =
              (UserContext) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
          assertThat(ctx.displayName()).isEqualTo("Test User");
        };

    filter.doFilterInternal(request, response, chain);
  }
}

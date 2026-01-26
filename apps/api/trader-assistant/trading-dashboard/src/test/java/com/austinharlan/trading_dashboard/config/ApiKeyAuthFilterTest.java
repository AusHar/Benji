package com.austinharlan.trading_dashboard.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
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
  private ApiKeyAuthFilter filter;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    properties = new ApiSecurityProperties();
    properties.setKey(VALID_API_KEY);
    properties.setHeaderName(HEADER_NAME);
    filter = new ApiKeyAuthFilter(properties);
  }

  @Test
  void allowsRequestWithValidApiKey() throws Exception {
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
  void skipsFilterWhenApiKeyNotConfigured() throws Exception {
    ApiSecurityProperties disabledProperties = new ApiSecurityProperties();
    ApiKeyAuthFilter disabledFilter = new ApiKeyAuthFilter(disabledProperties);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/quotes/AAPL");

    assertThat(disabledFilter.shouldNotFilter(request)).isTrue();
  }

  @Test
  void doesNotSkipFilterForApiEndpoints() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/quotes/AAPL");

    assertThat(filter.shouldNotFilter(request)).isFalse();
  }

  @Test
  void clearsSecurityContextAfterProcessing() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HEADER_NAME, VALID_API_KEY);
    request.setRequestURI("/api/quotes/AAPL");

    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void usesCustomHeaderName() throws Exception {
    ApiSecurityProperties customProperties = new ApiSecurityProperties();
    customProperties.setKey(VALID_API_KEY);
    customProperties.setHeaderName("Authorization");
    ApiKeyAuthFilter customFilter = new ApiKeyAuthFilter(customProperties);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", VALID_API_KEY);
    request.setRequestURI("/api/quotes/AAPL");

    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    customFilter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
  }
}

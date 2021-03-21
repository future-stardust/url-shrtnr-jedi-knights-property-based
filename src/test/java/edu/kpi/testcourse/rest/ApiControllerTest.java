package edu.kpi.testcourse.rest;

import static edu.kpi.testcourse.rest.MockAuthenticationProvider.LOGIN;
import static edu.kpi.testcourse.rest.MockAuthenticationProvider.PASSWORD;
import static edu.kpi.testcourse.rest.MockAuthenticationProvider.USERNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.kpi.testcourse.bigtable.Alias;
import edu.kpi.testcourse.bigtable.AliasDao;
import edu.kpi.testcourse.rest.dto.UrlCreateResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.security.authentication.AuthenticationProvider;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.jwt.render.BearerAccessRefreshToken;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@MicronautTest
class ApiControllerTest {

  private static final Logger log = LoggerFactory.getLogger(ApiControllerTest.class);

  @Inject
  @Client("/")
  private RxHttpClient client;

  @Inject
  private EmbeddedServer server;

  @Inject
  private AuthenticationProvider authenticationProvider;

  @Inject
  private AliasDao aliasDao;

  @MockBean(AuthenticationProvider.class)
  public AuthenticationProvider authenticationProvider() {
    return new MockAuthenticationProvider();
  }

  @MockBean(AliasDao.class)
  public AliasDao aliasDao() {
    return mock(AliasDao.class);
  }

  @Test
  void createShortenUrlWhenUniqueAliasProvided() {
    String accessToken = authorize();

    String url = "test";
    String alias = "test_alias";
    Map<String, Object> saveAliasRequestBody = Map.of(
      "url", url,
      "alias", alias
    );
    var expectedSaveParam = new Alias(alias, url, USERNAME);
    var expectedResult = new UrlCreateResponse(alias, url, USERNAME);
    var saveAliasRequest = HttpRequest.POST("/urls/shorten", saveAliasRequestBody)
      .bearerAuth(accessToken);

    HttpResponse<UrlCreateResponse> result = client.toBlocking()
      .exchange(saveAliasRequest, UrlCreateResponse.class);

    assertThat(result).extracting(HttpResponse::status)
      .isEqualTo(HttpStatus.OK);
    assertThat(result.body()).isEqualTo(expectedResult);

    verify(aliasDao).get(alias);
    verify(aliasDao).add(alias, expectedSaveParam);
  }

  @Test
  void createShortenUrlWhenProvidedAliasNotUnique() {
    String accessToken = authorize();

    String url = "test";
    String alias = "test_alias";
    Map<String, Object> saveAliasRequestBody = Map.of(
      "url", url,
      "alias", alias
    );
    var expectedResult = "Alias is not unique!";
    var saveAliasRequest = HttpRequest.POST("/urls/shorten", saveAliasRequestBody)
      .bearerAuth(accessToken);

    when(aliasDao.get(alias))
      .thenReturn(mock(Alias.class));

    HttpResponse<?> result = assertThrows(
      HttpClientResponseException.class,
      () -> client.toBlocking()
        .exchange(saveAliasRequest, String.class)
    )
      .getResponse();

    assertThat(result).extracting(HttpResponse::status)
      .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(result.body()).isEqualTo(expectedResult);

    verify(aliasDao).get(alias);
    verify(aliasDao, never()).add(anyString(), any());
  }

  @Test
  void createShortenUrlWhenNoAlias() {
    String accessToken = authorize();

    String url = "test";
    Map<String, Object> saveAliasRequestBody = Map.of(
      "url", url
    );

    var saveAliasRequest = HttpRequest.POST("/urls/shorten", saveAliasRequestBody)
      .bearerAuth(accessToken);

    HttpResponse<UrlCreateResponse> result = client.toBlocking()
      .exchange(saveAliasRequest, UrlCreateResponse.class);

    assertThat(result).extracting(HttpResponse::status)
      .isEqualTo(HttpStatus.OK);
    assertThat(result.body())
      .isNotNull()
      .satisfies(resultBody -> {
        assertThat(resultBody.getUrl()).isEqualTo(url);
        assertThat(resultBody.getUsername()).isEqualTo(USERNAME);
        assertThat(resultBody.getShorten()).isNotBlank();
      });

    ArgumentCaptor<Alias> aliasCaptor = ArgumentCaptor.forClass(Alias.class);
    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
    verify(aliasDao, atLeastOnce()).get(anyString());
    verify(aliasDao).add(stringCaptor.capture(), aliasCaptor.capture());

    String alias = stringCaptor.getValue();
    assertThat(aliasCaptor.getValue())
      .isNotNull()
      .satisfies(aliasObject -> {
        assertThat(stringCaptor.getValue())
          .isNotBlank()
          .isEqualTo(aliasObject.getShorten());
        assertThat(aliasObject.getUrl()).isEqualTo(url);
        assertThat(aliasObject.getUsername()).isEqualTo(USERNAME);
      });
  }

  @Test
  @Disabled("Disabled because there is no functionality to prevent infinite loop")
  void createShortenUrlWhenNoAliasAndCannotCreateUnique() {
    String accessToken = authorize();

    String url = "test";
    Map<String, Object> saveAliasRequestBody = Map.of(
      "url", url
    );

    var saveAliasRequest = HttpRequest.POST("/urls/shorten", saveAliasRequestBody)
      .bearerAuth(accessToken);

    when(aliasDao.get(anyString()))
      .thenReturn(mock(Alias.class));

    assertTimeoutPreemptively(Duration.of(5, ChronoUnit.SECONDS), () -> client.toBlocking()
      .exchange(saveAliasRequest, UrlCreateResponse.class));

    verify(aliasDao, atLeastOnce()).get(anyString());
    verify(aliasDao, never()).add(anyString(), any());
  }

  private String authorize() {
    log.info("Try to authorize");
    var credentials = new UsernamePasswordCredentials(LOGIN, PASSWORD);
    var request = HttpRequest
      .POST("/signin", credentials);

    String accessToken = client.toBlocking()
      .retrieve(request, BearerAccessRefreshToken.class)
      .getAccessToken();

    log.info("Retrieve auth token '{}'", accessToken);

    return accessToken;
  }
}

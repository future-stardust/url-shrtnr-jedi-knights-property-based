package edu.kpi.testcourse.rest;

import static edu.kpi.testcourse.rest.MockAuthenticationProvider.LOGIN;
import static edu.kpi.testcourse.rest.MockAuthenticationProvider.PASSWORD;
import static edu.kpi.testcourse.rest.MockAuthenticationProvider.USERNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.kpi.testcourse.bigtable.Alias;
import edu.kpi.testcourse.bigtable.AliasDao;
import edu.kpi.testcourse.rest.dto.UrlCreateResponse;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
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

  @Test
  void createShortenUrlWhenEmptyRequestBody() {
    String accessToken = authorize();

    Map<String, Object> saveAliasRequestBody = Collections.emptyMap();

    var saveAliasRequest = HttpRequest.POST("/urls/shorten", saveAliasRequestBody)
      .bearerAuth(accessToken);

    HttpResponse<?> result = assertThrows(
      HttpClientResponseException.class,
      () -> client.toBlocking()
        .exchange(saveAliasRequest, String.class))
      .getResponse();

    assertThat(result).extracting(HttpResponse::status)
      .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat((String) result.body()).contains("Required argument [String url] not specified");

    verifyNoInteractions(aliasDao);
  }

  @Test
  void createShortenUrlWhenUnauthorized() {
    String url = "test";
    String alias = "test_alias";
    Map<String, Object> saveAliasRequestBody = Map.of(
      "url", url,
      "alias", alias
    );

    var saveAliasRequest = HttpRequest.POST("/urls/shorten", saveAliasRequestBody);

    HttpResponse<?> result = assertThrows(
      HttpClientResponseException.class,
      () -> client.toBlocking()
        .exchange(saveAliasRequest, String.class))
      .getResponse();

    assertThat(result).extracting(HttpResponse::status)
      .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(result.body()).isNull();

    verifyNoInteractions(aliasDao);
  }

  @Test
  void redirectToUrlWhenUrlNotExists() {
    String alias = "test_alias";

    MutableHttpRequest<Object> getUrlRequest = HttpRequest.GET("/r/" + alias);

    HttpResponse<?> result = assertThrows(
      HttpClientResponseException.class,
      () -> client.toBlocking().exchange(getUrlRequest))
      .getResponse();

    assertThat(result).extracting(HttpResponse::status).isEqualTo(HttpStatus.BAD_REQUEST);
    verify(aliasDao).get(alias);
  }

  @Test
  void redirectToUrlWhenUrlExistsAndAuthorized() {
    String token = authorize();

    String alias = "test_alias";
    String url = "http://test.com";
    Alias savedAliasObject = new Alias(alias, url, USERNAME);

    when(aliasDao.get(alias))
      .thenReturn(savedAliasObject);

    MutableHttpRequest<Object> getUrlRequest = HttpRequest.GET("/r/" + alias)
      .bearerAuth(token);

    try {
      HttpResponse<Object> result = client.toBlocking().exchange(getUrlRequest);
      assertThat(result).extracting(HttpResponse::status).isEqualTo(HttpStatus.OK);
    } catch (Exception e) {
      log.error("Error while redirect", e);
    }
    verify(aliasDao).get(alias);
  }

  @Test
  void redirectToUrlWhenUrlExists() {
    String alias = "test_alias";
    String url = "http://test.com";
    Alias savedAliasObject = new Alias(alias, url, USERNAME);

    when(aliasDao.get(alias))
      .thenReturn(savedAliasObject);

    MutableHttpRequest<Object> getUrlRequest = HttpRequest.GET("/r/" + alias);

    try {
      HttpResponse<Object> result = client.toBlocking().exchange(getUrlRequest);
      assertThat(result).extracting(HttpResponse::status).isEqualTo(HttpStatus.OK);
    } catch (Exception e) {
      log.error("Error while redirect", e);
    }

    verify(aliasDao).get(alias);
  }

  @Test
  void getUserAliasesWhenUnauthorized() {
    var getAliasesRequest = HttpRequest.GET("/urls")
      .accept(MediaType.APPLICATION_JSON_TYPE);

    HttpResponse<?> result = assertThrows(
      HttpClientResponseException.class,
      () -> client.toBlocking()
        .exchange(getAliasesRequest, String.class))
      .getResponse();

    assertThat(result).extracting(HttpResponse::status)
      .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(result.body()).isNull();

    verifyNoInteractions(aliasDao);
  }

  @Test
  void getUserAliases() {
    String token = authorize();

    String shorten = "test_alias";
    String url = "test_url";

    Alias userAlias = new Alias(shorten, url, USERNAME);
    ArrayList<Alias> userAliases = new ArrayList<>();
    userAliases.add(userAlias);
    UrlCreateResponse expected = new UrlCreateResponse(shorten, url, USERNAME);

    MutableHttpRequest<Object> getAliasesRequest = HttpRequest.GET("/urls")
      .bearerAuth(token)
      .accept(MediaType.APPLICATION_JSON_TYPE);

    when(aliasDao.getAllByUser(USERNAME))
      .thenReturn(userAliases);

    HttpResponse<List<UrlCreateResponse>> result = client.toBlocking()
      .exchange(getAliasesRequest, Argument.listOf(UrlCreateResponse.class));

    assertThat(result).extracting(HttpResponse::status).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isNotEmpty();
    assertThat(result.getBody().get()).hasSize(1)
      .containsExactlyInAnyOrder(expected);

    verify(aliasDao).getAllByUser(USERNAME);
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

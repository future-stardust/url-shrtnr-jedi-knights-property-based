package edu.kpi.testcourse.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.SourceDSL.strings;

import edu.kpi.testcourse.rest.dto.UrlCreateResponse;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.jwt.render.BearerAccessRefreshToken;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//To run this, please, startup application and if needed change url
public class IntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(ApiControllerTest.class);

  private static final String APPLICATION_URL = "http://localhost:8080";
  private static final int SLEEP_TIME_MS = 10000;

  private final RxHttpClient client = RxHttpClient.create(new URL(APPLICATION_URL));

  public IntegrationTest() throws MalformedURLException {
  }

  @Test
  @Disabled("Comment to run integration test")
  public void createUrlFlowTest() {
    qt().withTestingTime(2, TimeUnit.MINUTES).forAll(strings().allPossible().ofLengthBetween(1, 10))
      .check(url -> {
        createUrl(url);
        return true;
      });
  }


  private void createUrl(String url) {
    //Sign up
    String username = UUID.randomUUID().toString();
    var credentials = new UsernamePasswordCredentials(
      username,
      "test_password"
    );
    var signUpRequest =
      HttpRequest.POST("/signup", credentials);
    client.toBlocking().exchange(signUpRequest);
    log.info("Create user with username '{}'", username);
    //Sign in
    var signInRequest = HttpRequest
      .POST("/signin", credentials);

    String token = client.toBlocking()
      .retrieve(signInRequest, BearerAccessRefreshToken.class)
      .getAccessToken();
    log.info("Authorize user with username'{}'", username);
    //Create url
    Map<String, Object> saveAliasRequestBody = Map.of(
      "url", url
    );

    var saveAliasRequest = HttpRequest.POST("/urls/shorten", saveAliasRequestBody)
      .bearerAuth(token);

    UrlCreateResponse creationResult = client.toBlocking()
      .exchange(saveAliasRequest, UrlCreateResponse.class).body();
    log.info("Create url '{}', creation result '{}'", url, creationResult);
    //get all user aliases
    MutableHttpRequest<Object> getAliasesRequest = HttpRequest.GET("/urls")
      .bearerAuth(token)
      .accept(MediaType.APPLICATION_JSON_TYPE);

    List<UrlCreateResponse> getAllResult = client.toBlocking()
      .exchange(getAliasesRequest, Argument.listOf(UrlCreateResponse.class)).body();

    log.info("Retrieve all url for user '{}', result '{}'", username, getAllResult);
    assertThat(getAllResult).contains(creationResult);
    assertThat(creationResult).extracting(UrlCreateResponse::getUrl).isEqualTo(url);
    assertThat(creationResult).extracting(UrlCreateResponse::getUsername).isEqualTo(username);

    try {
      log.info("Sleep for " + SLEEP_TIME_MS + "ms");
      Thread.sleep(SLEEP_TIME_MS);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

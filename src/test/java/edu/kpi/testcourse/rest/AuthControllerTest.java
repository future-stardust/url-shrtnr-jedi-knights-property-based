package edu.kpi.testcourse.rest;

import edu.kpi.testcourse.bigtable.TokenDao;
import edu.kpi.testcourse.bigtable.UserDao;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.security.authentication.AuthenticationProvider;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.jwt.render.BearerAccessRefreshToken;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static edu.kpi.testcourse.rest.MockAuthenticationProvider.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@MicronautTest
public class AuthControllerTest {
  @Inject
  @Client("/")
  private RxHttpClient client;

  @Inject
  private UserDao userDao;

  @MockBean(UserDao.class)
  public UserDao userDao() {
    return mock(UserDao.class);
  }

  @Inject
  private TokenDao tokenDao;

  @MockBean(TokenDao.class)
  public TokenDao tokenDao() {
    return mock(TokenDao.class);
  }

  @Inject
  private AuthenticationProvider authenticationProvider;

  @MockBean(AuthenticationProvider.class)
  public AuthenticationProvider authenticationProvider() {
    return new MockAuthenticationProvider();
  }

  private final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(
    USERNAME,
    PASSWORD
  );

  @Test
  void testSignUp() {
    var signUpRequest =
      HttpRequest.POST("/signup", credentials);

    var result = client.toBlocking().exchange(signUpRequest);

    assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);

    verify(userDao).get(credentials.getUsername());
  }

  @Test
  void testSignUpWhenUserAlreadyExist() {
    var expectedResult = "{\"message\":\"User already exists\"}";

    var signUpRequest = HttpRequest.POST("/signup", credentials);

    when(userDao.get(credentials.getUsername()))
      .thenReturn(credentials.getUsername());

    var result = assertThrows(
      HttpClientResponseException.class,
      () -> client.toBlocking()
        .exchange(signUpRequest, String.class)
    ).getResponse();

    assertThat(result.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(result.body()).isEqualTo(expectedResult);

    verify(userDao).get(credentials.getUsername());
  }

  @Test
  void testSignOutWhenUnauthorized() {
    var signOut = HttpRequest.GET("/signout");

    var result = assertThrows(
      HttpClientResponseException.class,
      () -> client.toBlocking()
        .exchange(signOut, String.class))
      .getResponse();

    assertThat(result.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(result.body()).isNull();
  }

  @Test
  void testSignOut() {
    var credentials = new UsernamePasswordCredentials(LOGIN, PASSWORD);
    var signIn = HttpRequest
      .POST("/signin", credentials);

    String token = client.toBlocking()
      .retrieve(signIn, BearerAccessRefreshToken.class)
      .getAccessToken();

    var signOut = HttpRequest.GET("/signout").bearerAuth(token);

    var result = client.toBlocking().exchange(signOut);

    assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
    verify(tokenDao).add(USERNAME, token);
  }

  @Test
  void testSignIn() {
    var signIn = client.toBlocking().exchange(
      HttpRequest.POST("/signin", credentials),
      BearerAccessRefreshToken.class
    );

    BearerAccessRefreshToken token = signIn.getBody().orElseThrow();
    assertThat(token.getUsername()).isEqualTo(credentials.getUsername());
  }

  @Test
  void testSignInUnknownUser() {
    UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(
      "UnknownUser",
      PASSWORD
    );
    var request = HttpRequest
      .POST("/signin", credentials);

    var result = assertThrows(
      HttpClientResponseException.class,
      () -> client.toBlocking().exchange(
        request,
        BearerAccessRefreshToken.class
      ))
      .getResponse();
    assertThat(result.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}

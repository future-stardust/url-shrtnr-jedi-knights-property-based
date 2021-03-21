package edu.kpi.testcourse.rest;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationFailed;
import io.micronaut.security.authentication.AuthenticationProvider;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.UserDetails;
import io.reactivex.Flowable;
import java.util.Collections;
import javax.inject.Singleton;
import org.reactivestreams.Publisher;

public class MockAuthenticationProvider implements AuthenticationProvider {

  public static final String LOGIN = "user";
  public static final String PASSWORD = "password";
  public static final String USERNAME = "user";

  @Override
  public Publisher<AuthenticationResponse> authenticate(
    @Nullable HttpRequest<?> httpRequest,
    AuthenticationRequest<?, ?> authenticationRequest
  ) {
    if (LOGIN.equals(authenticationRequest.getIdentity())
      && PASSWORD.equals(authenticationRequest.getSecret())
    ) {
      return Flowable.just(new UserDetails(USERNAME, Collections.emptyList()));
    }
    return Flowable.just(new AuthenticationFailed());
  }
}

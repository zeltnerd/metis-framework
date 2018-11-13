package eu.europeana.metis.authentication.rest.client;


import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import eu.europeana.metis.authentication.user.MetisUser;
import eu.europeana.metis.exception.UserUnauthorizedException;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.BeforeAll;
import software.betamax.ConfigurationBuilder;
import software.betamax.TapeMode;
import software.betamax.junit.Betamax;
import software.betamax.junit.RecorderRule;

/**
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2018-01-02
 */
public class TestAuthenticationClient {

  @Rule
  public RecorderRule recorder = new RecorderRule(
      new ConfigurationBuilder().defaultMode(TapeMode.READ_ONLY).build());
  private static AuthenticationClient authenticationClient;

  @BeforeClass
  @BeforeAll
  public static void beforeClass() {
    authenticationClient = new AuthenticationClient(
        "http://localhost:8080/metis-authentication-rest-test");
  }

  @Betamax(tape = "testGetUserByAccessTokenInHeader")
  @Test
  public void testGetUserByAccessTokenInHeader() throws Exception {
    MetisUser userByAccessTokenInHeader = authenticationClient
        .getUserByAccessTokenInHeader("Bearer vq6V1YJIOfLC0pSTeb1plANiopyVlwrx");
    assertNotNull(userByAccessTokenInHeader);
  }

  @Betamax(tape = "testGetUserByAccessTokenInHeaderHttpClientErrorException")
  @Test
  public void testGetUserByAccessTokenInHeaderHttpClientErrorException() {
    assertThrows(UserUnauthorizedException.class,
        () -> authenticationClient.getUserByAccessTokenInHeader("Bearer OUwbCoeELS28sF"));
  }
}

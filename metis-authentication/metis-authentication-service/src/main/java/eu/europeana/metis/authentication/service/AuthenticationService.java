package eu.europeana.metis.authentication.service;

import com.fasterxml.jackson.databind.JsonNode;
import eu.europeana.metis.authentication.dao.PsqlMetisUserDao;
import eu.europeana.metis.authentication.dao.ZohoAccessClientDao;
import eu.europeana.metis.authentication.exceptions.BadContentException;
import eu.europeana.metis.authentication.exceptions.NoOrganizationFoundException;
import eu.europeana.metis.authentication.exceptions.NoUserFoundException;
import eu.europeana.metis.authentication.exceptions.UserAlreadyExistsException;
import eu.europeana.metis.authentication.user.AccountRole;
import eu.europeana.metis.authentication.user.MetisUser;
import eu.europeana.metis.authentication.user.MetisUserAccessToken;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2017-10-27
 */
@Service
public class AuthenticationService {

  private ZohoAccessClientDao zohoAccessClientDao;
  private PsqlMetisUserDao psqlMetisUserDao;

  @Autowired
  public AuthenticationService(
      ZohoAccessClientDao zohoAccessClientDao,
      PsqlMetisUserDao psqlMetisUserDao) {
    this.zohoAccessClientDao = zohoAccessClientDao;
    this.psqlMetisUserDao = psqlMetisUserDao;
  }

  public void registerUser(String email, String password)
      throws BadContentException, NoUserFoundException, NoOrganizationFoundException, UserAlreadyExistsException {

    MetisUser storedMetisUser = psqlMetisUserDao.getMetisUserByEmail(email);
    if (storedMetisUser != null) {
      throw new UserAlreadyExistsException(
          String.format("User with email: %s already exists", email));
    }

    MetisUser metisUser = constructMetisUserFromZoho(email);
    byte[] salt = getSalt();
    String hashedPassword = generatePasswordHashing(salt, password);
    metisUser.setSalt(salt);
    metisUser.setPassword(hashedPassword);

    psqlMetisUserDao.createMetisUser(metisUser);
  }

  public void updateUserFromZoho(String email)
      throws BadContentException, NoUserFoundException, NoOrganizationFoundException {
    MetisUser storedMetisUser = psqlMetisUserDao.getMetisUserByEmail(email);
    if (storedMetisUser == null) {
      throw new NoUserFoundException(
          String.format("User with email: %s does not exist", email));
    }

    MetisUser metisUser = constructMetisUserFromZoho(email);
    metisUser.setSalt(storedMetisUser.getSalt());
    metisUser.setPassword(storedMetisUser.getPassword());
    if (storedMetisUser.getAccountRole() == AccountRole.METIS_ADMIN) {
      metisUser.setAccountRole(AccountRole.METIS_ADMIN);
    }

    psqlMetisUserDao.updateMetisUser(metisUser);
  }

  private MetisUser constructMetisUserFromZoho(String email)
      throws BadContentException, NoOrganizationFoundException, NoUserFoundException {
    //Get user from zoho
    JsonNode userByEmailJsonNode;
    try {
      userByEmailJsonNode = zohoAccessClientDao.getUserByEmail(email);
    } catch (IOException e) {
      throw new BadContentException(
          String.format("Cannot retrieve user with email %s, from Zoho", email), e);
    }
    if (userByEmailJsonNode == null) {
      throw new NoUserFoundException("User was not found in Zoho");
    }

    //Construct User
    MetisUser metisUser;
    try {
      metisUser = new MetisUser(userByEmailJsonNode);
    } catch (ParseException e) {
      throw new BadContentException("Bad content while constructing metisUser");
    }
    if (StringUtils.isEmpty(metisUser.getOrganizationName())) {
      throw new NoOrganizationFoundException("User is not related to an organization");
    }

    //Get Organization Id related to user
    JsonNode organizationJsonNode;
    try {
      organizationJsonNode = zohoAccessClientDao
          .getOrganizationByOrganizationName(metisUser.getOrganizationName());
    } catch (IOException e) {
      throw new BadContentException(
          String.format("Cannot retrieve organization with orgnaization name %s, from Zoho",
              metisUser.getOrganizationName()), e);
    }
    metisUser.setOrganizationIdFromJsonNode(organizationJsonNode);
    return metisUser;
  }

  private String generatePasswordHashing(byte[] salt, String password) throws BadContentException {
    MessageDigest sha256;
    try {
      sha256 = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new BadContentException("Hashing algorithm not found", e);
    }
    sha256.update(salt);
    byte[] passwordHash = sha256.digest(password.getBytes());
    StringBuilder stringHashedPassword = new StringBuilder();
    for (byte aPasswordHash : passwordHash) {
      stringHashedPassword
          .append(Integer.toString((aPasswordHash & 0xff) + 0x100, 16).substring(1));
    }

    return stringHashedPassword.toString();
  }

  private byte[] getSalt() {
    final Random r = new SecureRandom();
    byte[] salt = new byte[32];
    r.nextBytes(salt);
    return salt;
  }

  private boolean isPasswordValid(MetisUser metisUser, String passwordToTry)
      throws BadContentException {
    String hashedPasswordToTry = generatePasswordHashing(metisUser.getSalt(), passwordToTry);
    return hashedPasswordToTry.equals(metisUser.getPassword());
  }

  public String[] validateAuthorizationHeader(String authorization) throws BadContentException {
    if (StringUtils.isEmpty(authorization)) {
      throw new BadContentException("Authorization header was empty");
    }
    String[] credentials = decodeHeader(authorization);
    String email = credentials[0];
    String password = credentials[1];
    if (StringUtils.isEmpty(email) || StringUtils.isEmpty(password)) {
      throw new BadContentException("Username or password not provided");
    }
    return credentials;
  }

  private String[] decodeHeader(String authorization) {
    if (authorization != null && authorization.startsWith("Basic")) {
      // Authorization: Basic base64credentials
      String base64Credentials = authorization.substring("Basic" .length()).trim();
      String credentials = new String(Base64.getDecoder().decode(base64Credentials),
          Charset.forName("UTF-8"));
      // credentials = username:password
      return credentials.split(":", 2);
    }
    return null;
  }

  public MetisUser loginUser(String email, String password) throws BadContentException {
    MetisUser storedMetisUser = psqlMetisUserDao.getMetisUserByEmail(email);
    if (storedMetisUser == null || !isPasswordValid(storedMetisUser, password)) {
      throw new BadContentException("Wrong credentials");
    }

    if (storedMetisUser.getMetisUserAccessToken() != null) {
      psqlMetisUserDao.updateAccessTokenTimestamp(email);
      return storedMetisUser;
    }
    MetisUserAccessToken metisUserAccessToken = new MetisUserAccessToken(email,
        generateAccessToken(), new Date());
    psqlMetisUserDao.createUserAccessToken(metisUserAccessToken);
    storedMetisUser.setMetisUserAccessToken(metisUserAccessToken);
    return storedMetisUser;
  }

  public void updateUserPassword(String email, String password, String newPassword)
      throws BadContentException {
    MetisUser storedMetisUser = psqlMetisUserDao.getMetisUserByEmail(email);
    if (storedMetisUser == null || !isPasswordValid(storedMetisUser, password)) {
      throw new BadContentException("Wrong credentials");
    }

    byte[] salt = getSalt();
    String hashedPassword = generatePasswordHashing(salt, newPassword);
    storedMetisUser.setSalt(salt);
    storedMetisUser.setPassword(hashedPassword);

    if (storedMetisUser.getMetisUserAccessToken() != null) {
      psqlMetisUserDao.updateAccessTokenTimestamp(email);
    } else {
      MetisUserAccessToken metisUserAccessToken = new MetisUserAccessToken(email,
          generateAccessToken(), new Date());
      psqlMetisUserDao.createUserAccessToken(metisUserAccessToken);
      storedMetisUser.setMetisUserAccessToken(metisUserAccessToken);
    }
    psqlMetisUserDao.updateMetisUser(storedMetisUser);
  }

  public void updateUserMakeAdmin(String userEmailToMakeAdmin) throws BadContentException {
    if (psqlMetisUserDao.getMetisUserByEmail(userEmailToMakeAdmin) == null)
      throw new BadContentException(String.format("User with email %s does not exist", userEmailToMakeAdmin));
    psqlMetisUserDao.updateMetisUserToMakeAdmin(userEmailToMakeAdmin);
  }

  public boolean isUserAdmin(String email, String password) throws BadContentException {
    MetisUser storedMetisUser = psqlMetisUserDao.getMetisUserByEmail(email);
    if (storedMetisUser == null || !isPasswordValid(storedMetisUser, password)) {
      throw new BadContentException("Wrong credentials");
    }
    return storedMetisUser.getAccountRole() == AccountRole.METIS_ADMIN;
  }

  public boolean hasPermissionToRequestUserUpdate(String email, String password,
      String userEmailToUpdate)
      throws BadContentException {
    MetisUser storedMetisUser = psqlMetisUserDao.getMetisUserByEmail(email);
    if (storedMetisUser == null || !isPasswordValid(storedMetisUser, password)) {
      throw new BadContentException("Wrong credentials");
    }
    MetisUser storedMetisUserToUpdate = psqlMetisUserDao.getMetisUserByEmail(userEmailToUpdate);
    return storedMetisUser.getAccountRole() == AccountRole.METIS_ADMIN || (
        storedMetisUser.getAccountRole() == AccountRole.EUROPEANA_DATA_OFFICER
            && storedMetisUserToUpdate.getAccountRole() != AccountRole.METIS_ADMIN);
  }

  private String generateAccessToken() {
    final String characterBasket = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    final SecureRandom rnd = new SecureRandom();
    final int accessTokenLength = 32;
    StringBuilder sb = new StringBuilder(accessTokenLength);
    for (int i = 0; i < accessTokenLength; i++) {
      sb.append(characterBasket.charAt(rnd.nextInt(characterBasket.length())));
    }
    return sb.toString();
  }

  public void expireAccessTokens() {
    Date now = new Date();
    psqlMetisUserDao.expireAccessTokens(now);
  }

  public void deleteUser(String email) {
    psqlMetisUserDao.deleteMetisUser(email);
  }
}

package app.coronawarn.datadonation.services.ppac.ios;

import app.coronawarn.datadonation.common.config.UrlConstants;
import app.coronawarn.datadonation.common.persistence.domain.ApiTokenData;
import app.coronawarn.datadonation.common.persistence.domain.DeviceToken;
import app.coronawarn.datadonation.common.persistence.repository.ApiTokenRepository;
import app.coronawarn.datadonation.common.persistence.repository.DeviceTokenRepository;
import app.coronawarn.datadonation.common.protocols.internal.ppdd.EDUSOneTimePasswordRequestIOS;
import app.coronawarn.datadonation.common.protocols.internal.ppdd.PPADataRequestIOS;
import app.coronawarn.datadonation.common.utils.TimeUtils;
import app.coronawarn.datadonation.services.ppac.commons.web.DataSubmissionResponse;
import app.coronawarn.datadonation.services.ppac.config.PpacConfiguration;
import app.coronawarn.datadonation.services.ppac.ios.client.IosDeviceApiClient;
import app.coronawarn.datadonation.services.ppac.ios.client.domain.PerDeviceDataQueryRequest;
import app.coronawarn.datadonation.services.ppac.ios.client.domain.PerDeviceDataResponse;
import app.coronawarn.datadonation.services.ppac.ios.client.domain.PerDeviceDataUpdateRequest;
import app.coronawarn.datadonation.services.ppac.ios.testdata.TestData;
import app.coronawarn.datadonation.services.ppac.ios.verification.JwtProvider;
import app.coronawarn.datadonation.services.ppac.ios.verification.PpacIosScenarioRepository;
import app.coronawarn.datadonation.services.ppac.logging.PpacErrorCode;
import feign.FeignException;
import feign.Request;
import feign.Request.Body;
import feign.Request.HttpMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Optional;

import static app.coronawarn.datadonation.common.utils.TimeUtils.*;
import static app.coronawarn.datadonation.services.ppac.ios.testdata.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IosAuthenticationIntegrationTest {

  private static final String IOS_SERVICE_URL = UrlConstants.IOS + UrlConstants.DATA;
  private static final String IOS_SURVEY_URL = UrlConstants.IOS + UrlConstants.OTP;
  private static final OffsetDateTime OFFSET_DATE_TIME = OffsetDateTime.parse("2021-10-01T10:00:00+01:00");

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Autowired
  private DeviceTokenRepository deviceTokenRepository;

  @Autowired
  private PpacConfiguration configuration;

  @MockBean
  private IosDeviceApiClient iosDeviceApiClient;

  @MockBean
  private JwtProvider jwtProvider;

  @SpyBean
  PpacIosScenarioRepository scenarioRepository;

  @Autowired
  ApiTokenRepository apiTokenRepository;

  @BeforeEach
  void clearDatabase() {
    deviceTokenRepository.deleteAll();
    apiTokenRepository.deleteAll();
    when(jwtProvider.generateJwt()).thenReturn("jwt");
  }

  @Test
  void testSubmitData_successfulSubmission_shouldFailAfterSecondSurvey() {
    final String deviceToken = buildBase64String(this.configuration.getIos().getMinDeviceTokenLength() + 1);
    final String deviceTokenForSurvey = buildBase64String(this.configuration.getIos().getMinDeviceTokenLength() + 2);
    final String apiToken = buildUuid();
    final String otp = buildUuid();
    final OffsetDateTime now = OffsetDateTime.now();
    final PerDeviceDataResponse perDeviceDataResponse = buildIosDeviceData(now.minusMonths(1), true);
    final PPADataRequestIOS submissionPayloadIos = buildPPADataRequestIosPayload(apiToken, deviceToken, false);
    final EDUSOneTimePasswordRequestIOS edusOneTimePasswordRequestIOS = TestData
        .buildEdusOneTimePasswordPayload(apiToken, deviceTokenForSurvey, otp);

    when(iosDeviceApiClient.queryDeviceData(any(), any()))
        .thenReturn(ResponseEntity.ok(jsonify(perDeviceDataResponse)));

    final ResponseEntity<DataSubmissionResponse> responseEntity = postSubmission(submissionPayloadIos, testRestTemplate,
        IOS_SERVICE_URL, false);
    final ResponseEntity<DataSubmissionResponse> surveyResponseEntity = postSurvey(edusOneTimePasswordRequestIOS,
        testRestTemplate,
        IOS_SURVEY_URL, false);
    verify(scenarioRepository, times(1)).saveForPpa(any());
    verify(scenarioRepository, times(1)).updateForEdus(any());

    final DeviceToken newDeviceToken = buildDeviceToken(submissionPayloadIos.getAuthentication().getDeviceToken());
    final DeviceToken newSurveyDeviceToken = buildDeviceToken(
        edusOneTimePasswordRequestIOS.getAuthentication().getDeviceToken());

    final Optional<DeviceToken> byDeviceTokenHash = deviceTokenRepository
        .findByDeviceTokenHash(newDeviceToken.getDeviceTokenHash());
    final Optional<DeviceToken> surveyDeviceToken = deviceTokenRepository
        .findByDeviceTokenHash(newSurveyDeviceToken.getDeviceTokenHash());
    final Optional<ApiTokenData> apiTokenOptional = apiTokenRepository.findById(apiToken);

    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(surveyResponseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(byDeviceTokenHash).isPresent();
    assertThat(surveyDeviceToken).isPresent();
    assertThat(apiTokenOptional).isPresent();
    assertThat(apiTokenOptional.get().getApiToken()).isEqualTo(apiToken);
    assertThat(apiTokenOptional.get().getLastUsedEdus()).isPresent();
    assertThat(apiTokenOptional.get().getLastUsedPpac()).isPresent();

    final String secondDeviceTokenForSurvey = buildBase64String(
        this.configuration.getIos().getMinDeviceTokenLength() + 3);
    final EDUSOneTimePasswordRequestIOS secondEdusOneTimePasswordRequestIOS = TestData
        .buildEdusOneTimePasswordPayload(apiToken, secondDeviceTokenForSurvey, otp);

    final ResponseEntity<DataSubmissionResponse> errorResponse = postSurvey(secondEdusOneTimePasswordRequestIOS,
        testRestTemplate,
        IOS_SURVEY_URL, false);
    assertThat(errorResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(errorResponse.getBody()).isNotNull();
    assertThat(errorResponse.getBody().getErrorCode()).isEqualTo(PpacErrorCode.API_TOKEN_QUOTA_EXCEEDED);
  }

  @Test
  void testSubmitData_authenticateExistingApiToken_successfulPpac() {
    final String deviceToken = buildBase64String(this.configuration.getIos().getMinDeviceTokenLength() + 1);
    final String apiToken = buildUuid();
    final OffsetDateTime now = OffsetDateTime.now();
    final PerDeviceDataResponse perDeviceDataResponse = buildIosDeviceData(now.minusMonths(1), true);
    final PPADataRequestIOS submissionPayloadIos = buildPPADataRequestIosPayload(apiToken, deviceToken, false);
    apiTokenRepository.insert(apiToken, getLastDayOfMonthForNow(), getEpochSecondsForNow(), null, null);

    when(iosDeviceApiClient.queryDeviceData(any(), any()))
        .thenReturn(ResponseEntity.ok(jsonify(perDeviceDataResponse)));

    final ResponseEntity<DataSubmissionResponse> responseEntity = postSubmission(submissionPayloadIos, testRestTemplate,
        IOS_SERVICE_URL, false);
    final DeviceToken newDeviceToken = buildDeviceToken(submissionPayloadIos.getAuthentication().getDeviceToken());
    final Optional<DeviceToken> byDeviceTokenHash = deviceTokenRepository
        .findByDeviceTokenHash(newDeviceToken.getDeviceTokenHash());

    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(byDeviceTokenHash).isPresent();
  }

  @Test
  void testSubmitData_invalidPayload() {
    PPADataRequestIOS submissionPayloadIos = buildInvalidPPADataRequestIosPayload();
    ResponseEntity<DataSubmissionResponse> response = postSubmission(submissionPayloadIos, testRestTemplate,
        IOS_SERVICE_URL, false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody()).isInstanceOf(DataSubmissionResponse.class);
    assertThat(response.getBody().getErrorCode()).isEqualTo(PpacErrorCode.DEVICE_TOKEN_SYNTAX_ERROR);
  }

  @Test
  void testSubmitData_storeDeviceTokenHash_uniqueKeyViolation() {
    // given
    // Per Device Data that was updated last month
    String deviceToken = buildBase64String(this.configuration.getIos().getMinDeviceTokenLength() + 1);
    String apiToken = buildUuid();
    OffsetDateTime now = OffsetDateTime.now();
    PerDeviceDataResponse data = buildIosDeviceData(now.minusMonths(1), true);
    // And a valid payload
    PPADataRequestIOS submissionPayloadIos = buildPPADataRequestIosPayload(apiToken, deviceToken, false);
    // And an already existing device token
    DeviceToken newDeviceToken = buildDeviceToken(submissionPayloadIos.getAuthentication().getDeviceToken());
    deviceTokenRepository.save(newDeviceToken);

    // when the device api returns per-device data
    when(iosDeviceApiClient.queryDeviceData(anyString(), any())).thenReturn(ResponseEntity.ok(jsonify(data)));
    when(iosDeviceApiClient.updatePerDeviceData(anyString(), any())).thenReturn(ResponseEntity.ok().build());
    // And a new payload is sent to the server
    ResponseEntity<DataSubmissionResponse> response = postSubmission(submissionPayloadIos, testRestTemplate,
        IOS_SERVICE_URL, false);

    // then
    // The request fails because the device token already exists in the device token hash table
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody()).isInstanceOf(DataSubmissionResponse.class);
    assertThat(response.getBody().getErrorCode()).isEqualTo(PpacErrorCode.DEVICE_TOKEN_REDEEMED);
  }

  @Test
  void testSubmitData_errorUpdatingPerDevicedata_rollback() {
    // Have no API Token YET
    // and a submission that corresponds to per-device data that was last updated last month.
    // Per-Device Data should be updated and a new API Token should be created with expiration set to end of the current month.
    // After exception is thrown the db insertion should be rollbacked. So no API Token will be found.
    OffsetDateTime now = OffsetDateTime.now();
    String deviceToken = buildBase64String(this.configuration.getIos().getMinDeviceTokenLength() + 1);
    String apiToken = buildUuid();
    PerDeviceDataResponse data = buildIosDeviceData(now.minusMonths(1), true);
    PPADataRequestIOS submissionPayloadIos = buildPPADataRequestIosPayload(apiToken, deviceToken, false);

    // when
    when(iosDeviceApiClient.queryDeviceData(anyString(), any())).thenReturn(ResponseEntity.ok(jsonify(data)));
    doThrow(FeignException.class).when(iosDeviceApiClient)
        .updatePerDeviceData(anyString(), any());
    postSubmission(submissionPayloadIos, testRestTemplate, IOS_SERVICE_URL, false);

    // then
    Optional<ApiTokenData> optionalApiToken = apiTokenRepository.findById(apiToken);
    assertFalse(optionalApiToken.isPresent());
  }

  @Test
  void testSubmitData_updatePerDeviceData() {
    // Have no API Token YET
    // and a submission that correspond to per-device data that was last updated last month
    // Per-Device Data should be updated and a new API Token should be created with expiration set to end of the current month.
    OffsetDateTime now = OffsetDateTime.now();
    String deviceToken = buildBase64String(this.configuration.getIos().getMinDeviceTokenLength() + 1);
    String apiToken = buildUuid();
    PerDeviceDataResponse data = buildIosDeviceData(now.minusMonths(1), true);
    PPADataRequestIOS submissionPayloadIos = buildPPADataRequestIosPayload(apiToken, deviceToken, false);
    ArgumentCaptor<PerDeviceDataUpdateRequest> deviceTokenArgumentCaptor = ArgumentCaptor
        .forClass(PerDeviceDataUpdateRequest.class);
    ArgumentCaptor<PerDeviceDataQueryRequest> queryRequestArgumentCaptor = ArgumentCaptor
        .forClass(PerDeviceDataQueryRequest.class);

    // when
    when(iosDeviceApiClient.queryDeviceData(anyString(), queryRequestArgumentCaptor.capture()))
        .thenReturn(ResponseEntity.ok(jsonify(data)));
    when(iosDeviceApiClient.updatePerDeviceData(anyString(), deviceTokenArgumentCaptor.capture()))
        .thenReturn(ResponseEntity.ok().build());
    final ResponseEntity<DataSubmissionResponse> response = postSubmission(submissionPayloadIos, testRestTemplate,
        IOS_SERVICE_URL, false);

    // then
    Optional<ApiTokenData> optionalApiToken = apiTokenRepository.findById(apiToken);
    Optional<DeviceToken> deviceTokenOptional = deviceTokenRepository
        .findByDeviceTokenHash(
            buildDeviceToken(submissionPayloadIos.getAuthentication().getDeviceToken()).getDeviceTokenHash());
    assertThat(deviceTokenOptional).isPresent();
    assertThat(optionalApiToken).isPresent();

    final Long expirationDate = optionalApiToken.get().getExpirationDate();
    final LocalDate localDateFor = TimeUtils.getLocalDateFor(expirationDate);
    assertThat(localDateFor)
        .isEqualTo(OffsetDateTime.now().with(TemporalAdjusters.lastDayOfMonth()).toLocalDate());

    assertThat(deviceTokenArgumentCaptor.getValue().isBit0()).isFalse();
    assertThat(deviceTokenArgumentCaptor.getValue().isBit1()).isFalse();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getBody()).isNull();
  }

  @Test
  void testSubmitData_apiTokenAlreadyUsed() {
    // Toy ios device data that has last update NOW - this will be compared against current server time
    // so this means that someone altered the per device data already this month with an api token.

    // given
    String deviceToken = buildBase64String(this.configuration.getIos().getMinDeviceTokenLength() + 1);
    String apiToken = buildUuid();
    PerDeviceDataResponse data = buildIosDeviceData(OffsetDateTime.now(), true);
    PPADataRequestIOS submissionPayloadIos = buildPPADataRequestIosPayload(apiToken, deviceToken, false);

    // when
    when(iosDeviceApiClient.queryDeviceData(anyString(), any())).thenReturn(ResponseEntity.ok(jsonify(data)));
    ResponseEntity<DataSubmissionResponse> response = postSubmission(submissionPayloadIos, testRestTemplate,
        IOS_SERVICE_URL, false);

    // then
    Optional<ApiTokenData> optionalApiToken = apiTokenRepository.findById(apiToken);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(optionalApiToken).isEmpty();
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody()).isInstanceOf(DataSubmissionResponse.class);
    assertThat(response.getBody().getErrorCode()).isEqualTo(PpacErrorCode.API_TOKEN_ALREADY_ISSUED);
  }

  @Test
  void testSubmitData_apiTokenExpired() {
    // Existing API Token that expired LAST month is compared against current timestamp
    // submission will fail because the API Token expired last month.

    // given
    String deviceToken = buildBase64String(this.configuration.getIos().getMinDeviceTokenLength() + 1);
    String apiToken = buildUuid();
    OffsetDateTime now = OffsetDateTime.now();
    Long expirationDate = getLastDayOfMonthFor(now.minusMonths(1));
    long timestamp = getEpochSecondFor(now);

    apiTokenRepository.insert(apiToken, expirationDate, expirationDate, timestamp, timestamp);
    PerDeviceDataResponse data = buildIosDeviceData(OFFSET_DATE_TIME, true);
    PPADataRequestIOS submissionPayloadIos = buildPPADataRequestIosPayload(apiToken, deviceToken, false);

    // when
    when(iosDeviceApiClient.queryDeviceData(anyString(), any())).thenReturn(ResponseEntity.ok(jsonify(data)));
    ResponseEntity<DataSubmissionResponse> response = postSubmission(submissionPayloadIos, testRestTemplate,
        IOS_SERVICE_URL, false);

    // then

    Optional<ApiTokenData> apiTokenOptional = apiTokenRepository.findById(apiToken);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(apiTokenOptional).isPresent();
    assertThat(apiTokenOptional.get().getExpirationDate()).isEqualTo(expirationDate);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody()).isInstanceOf(DataSubmissionResponse.class);
    assertThat(response.getBody().getErrorCode()).isEqualTo(PpacErrorCode.API_TOKEN_EXPIRED);
  }

  @Test
  void testSubmitData_failRetrievingPerDeviceData_invalidDeviceToken() {
    // given
    String deviceToken = buildBase64String(this.configuration.getIos().getMinDeviceTokenLength() + 1);
    String apiToken = buildUuid();
    final FeignException feignException = buildFakeException();

    // when
    when(iosDeviceApiClient.queryDeviceData(anyString(), any())).thenThrow(feignException);
    PPADataRequestIOS submissionPayloadIos = buildPPADataRequestIosPayload(apiToken, deviceToken, false);
    ResponseEntity<DataSubmissionResponse> response = postSubmission(submissionPayloadIos, testRestTemplate,
        IOS_SERVICE_URL, false);
    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody()).isInstanceOf(DataSubmissionResponse.class);
    assertThat(response.getBody().getErrorCode()).isEqualTo(PpacErrorCode.DEVICE_TOKEN_INVALID);
  }

  @Test
  void testSubmitData_failRetrievingPerDeviceData_internalServerError() {
    // Querying the apple device api returns a statuscode that is not 400 nor 200

    // given
    String deviceToken = buildBase64String(this.configuration.getIos().getMinDeviceTokenLength() + 1);
    String apiToken = buildUuid();
    PPADataRequestIOS submissionPayloadIos = buildPPADataRequestIosPayload(apiToken, deviceToken, false);

    // when
    when(iosDeviceApiClient.queryDeviceData(anyString(), any())).thenThrow(FeignException.class);
    ResponseEntity<DataSubmissionResponse> response = postSubmission(submissionPayloadIos, testRestTemplate,
        IOS_SERVICE_URL, false);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNull();
  }

  @Test
  void testSubmitData_invalidPerDeviceData() {
    // Toy data contains invalid values for bot0 and bit1 (both have state 1)

    // given
    String deviceToken = buildBase64String(this.configuration.getIos().getMinDeviceTokenLength() + 1);
    String apiToken = buildUuid();
    PerDeviceDataResponse data = buildIosDeviceData(OFFSET_DATE_TIME, false);
    PPADataRequestIOS submissionPayloadIos = buildPPADataRequestIosPayload(apiToken, deviceToken, false);

    // when
    when(iosDeviceApiClient.queryDeviceData(anyString(), any())).thenReturn(ResponseEntity.ok(jsonify(data)));
    when(iosDeviceApiClient.updatePerDeviceData(anyString(), any())).thenReturn(ResponseEntity.ok().build());
    ResponseEntity<DataSubmissionResponse> response = postSubmission(submissionPayloadIos, testRestTemplate,
        IOS_SERVICE_URL, false);

    // when
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody()).isInstanceOf(DataSubmissionResponse.class);
    assertThat(response.getBody().getErrorCode()).isEqualTo(PpacErrorCode.DEVICE_BLOCKED);
  }

  private FeignException.BadRequest buildFakeException() {
    return new FeignException.BadRequest("Bad Device Token", buildFakeFeignRequest(), null, null);
  }

  private Request buildFakeFeignRequest() {
    return Request.create(HttpMethod.POST, "", new HashMap<>(), Body.create(""), null);
  }
}

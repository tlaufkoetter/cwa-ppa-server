package app.coronawarn.datadonation.services.retention.runner;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;

import app.coronawarn.datadonation.common.persistence.repository.ApiTokenRepository;
import app.coronawarn.datadonation.common.persistence.repository.DeviceTokenRepository;
import app.coronawarn.datadonation.common.persistence.repository.ElsOneTimePasswordRepository;
import app.coronawarn.datadonation.common.persistence.repository.OneTimePasswordRepository;
import app.coronawarn.datadonation.common.persistence.repository.metrics.ClientMetadataRepository;
import app.coronawarn.datadonation.common.persistence.repository.metrics.ExposureRiskMetadataRepository;
import app.coronawarn.datadonation.common.persistence.repository.metrics.ExposureWindowRepository;
import app.coronawarn.datadonation.common.persistence.repository.metrics.ExposureWindowTestResultsRepository;
import app.coronawarn.datadonation.common.persistence.repository.metrics.ExposureWindowsAtTestRegistrationRepository;
import app.coronawarn.datadonation.common.persistence.repository.metrics.KeySubmissionMetadataWithClientMetadataRepository;
import app.coronawarn.datadonation.common.persistence.repository.metrics.KeySubmissionMetadataWithUserMetadataRepository;
import app.coronawarn.datadonation.common.persistence.repository.metrics.ScanInstanceRepository;
import app.coronawarn.datadonation.common.persistence.repository.metrics.ScanInstancesAtTestRegistrationRepository;
import app.coronawarn.datadonation.common.persistence.repository.metrics.SummarizedExposureWindowsWithUserMetadataRepository;
import app.coronawarn.datadonation.common.persistence.repository.metrics.TestResultMetadataRepository;
import app.coronawarn.datadonation.common.persistence.repository.metrics.UserMetadataRepository;
import app.coronawarn.datadonation.common.persistence.repository.ppac.android.SaltRepository;
import app.coronawarn.datadonation.services.retention.Application;
import app.coronawarn.datadonation.services.retention.config.RetentionConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class RetentionPolicy implements ApplicationRunner {

  static long subtractRetentionPeriodFromNowToEpochMilli(final TemporalUnit temporalUnit,
      final Integer retentionPeriod) {
    return Instant.now().truncatedTo(temporalUnit).minus(retentionPeriod, temporalUnit).toEpochMilli();
  }

  static long subtractRetentionPeriodFromNowToSeconds(final TemporalUnit temporalUnit, final Integer retentionPeriod) {
    return Instant.now().truncatedTo(temporalUnit).minus(retentionPeriod, temporalUnit).getEpochSecond();
  }

  /**
   * Calculates the date to be used for the deletion.
   *
   * @param retentionDays how many days back in time you want to travel?
   * @return TODAY - retentionDays
   */
  static LocalDate threshold(final Integer retentionDays) {
    return Instant.now().atOffset(ZoneOffset.UTC).toLocalDate().minusDays(retentionDays);
  }

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final ExposureRiskMetadataRepository exposureRiskMetadataRepository;
  private final ExposureWindowRepository exposureWindowRepository;
  private final ScanInstanceRepository scanInstanceRepository;
  private final KeySubmissionMetadataWithClientMetadataRepository keySubmissionMetadataWithClientMetadataRepository;
  private final KeySubmissionMetadataWithUserMetadataRepository keySubmissionMetadataWithUserMetadataRepository;
  private final TestResultMetadataRepository testResultMetadataRepository;
  private final DeviceTokenRepository deviceTokenRepository;
  private final OneTimePasswordRepository oneTimePasswordRepository;
  private final ElsOneTimePasswordRepository elsOneTimePasswordRepository;
  private final RetentionConfiguration retentionConfiguration;
  private final ApplicationContext appContext;
  private final SaltRepository saltRepository;
  private final ApiTokenRepository apiTokenRepository;
  private final ClientMetadataRepository clientMetadataRepository;
  private final UserMetadataRepository userMetadataRepository;
  private final SummarizedExposureWindowsWithUserMetadataRepository summarizedExposureWindowsWithUserMetadataRepo;
  private final ExposureWindowTestResultsRepository exposureWindowTestResultsRepository;
  private final ScanInstancesAtTestRegistrationRepository scanInstancesAtTestRegistrationRepository;
  private final ExposureWindowsAtTestRegistrationRepository exposureWindowsAtTestRegistrationRepository;

  /**
   * Creates a new {@link RetentionPolicy}.
   */
  @Autowired
  public RetentionPolicy(final ApiTokenRepository apiTokenRepository,
      final ExposureRiskMetadataRepository exposureRiskMetadataRepository,
      final ExposureWindowRepository exposureWindowRepository, final ScanInstanceRepository scanInstanceRepository,
      final KeySubmissionMetadataWithClientMetadataRepository keySubmissionMetadataWithClientMetadataRepository,
      final KeySubmissionMetadataWithUserMetadataRepository keySubmissionMetadataWithUserMetadataRepository,
      final TestResultMetadataRepository testResultMetadataRepository,
      final DeviceTokenRepository deviceTokenRepository, final OneTimePasswordRepository oneTimePasswordRepository,
      final ElsOneTimePasswordRepository elsOneTimePasswordRepository,
      final RetentionConfiguration retentionConfiguration, final ApplicationContext appContext,
      final SaltRepository saltRepository, final ClientMetadataRepository clientMetadataRepository,
      final UserMetadataRepository userMetadataRepository,
      final SummarizedExposureWindowsWithUserMetadataRepository summarizedExposureWindowsWithUserMetadataRepo,
      final ExposureWindowTestResultsRepository exposureWindowTestResultsRepository,
      final ScanInstancesAtTestRegistrationRepository scanInstancesAtTestRegistrationRepository,
      final ExposureWindowsAtTestRegistrationRepository exposureWindowsAtTestRegistrationRepository) {
    this.exposureRiskMetadataRepository = exposureRiskMetadataRepository;
    this.scanInstanceRepository = scanInstanceRepository;
    this.exposureWindowRepository = exposureWindowRepository;
    this.keySubmissionMetadataWithClientMetadataRepository = keySubmissionMetadataWithClientMetadataRepository;
    this.keySubmissionMetadataWithUserMetadataRepository = keySubmissionMetadataWithUserMetadataRepository;
    this.testResultMetadataRepository = testResultMetadataRepository;
    this.deviceTokenRepository = deviceTokenRepository;
    this.oneTimePasswordRepository = oneTimePasswordRepository;
    this.elsOneTimePasswordRepository = elsOneTimePasswordRepository;
    this.retentionConfiguration = retentionConfiguration;
    this.appContext = appContext;
    this.saltRepository = saltRepository;
    this.apiTokenRepository = apiTokenRepository;
    this.clientMetadataRepository = clientMetadataRepository;
    this.userMetadataRepository = userMetadataRepository;
    this.summarizedExposureWindowsWithUserMetadataRepo = summarizedExposureWindowsWithUserMetadataRepo;
    this.exposureWindowTestResultsRepository = exposureWindowTestResultsRepository;
    this.scanInstancesAtTestRegistrationRepository = scanInstancesAtTestRegistrationRepository;
    this.exposureWindowsAtTestRegistrationRepository = exposureWindowsAtTestRegistrationRepository;
  }

  private void deleteClientMetadata() {
    final LocalDate date = threshold(retentionConfiguration.getClientMetadataRetentionDays());
    logDeletionInDays(clientMetadataRepository.countOlderThan(date),
        retentionConfiguration.getClientMetadataRetentionDays(), "client metadata");
    clientMetadataRepository.deleteOlderThan(date);
  }

  private void deleteKeySubmissionMetadataWithClient() {
    final LocalDate date = threshold(retentionConfiguration.getKeyMetadataWithClientRetentionDays());
    logDeletionInDays(keySubmissionMetadataWithClientMetadataRepository.countOlderThan(date),
        retentionConfiguration.getKeyMetadataWithClientRetentionDays(), "key submission metadata with client");
    keySubmissionMetadataWithClientMetadataRepository.deleteOlderThan(date);
  }

  private void deleteKeySubmissionMetadataWithUser() {
    final LocalDate date = threshold(retentionConfiguration.getKeyMetadataWithUserRetentionDays());
    logDeletionInDays(keySubmissionMetadataWithUserMetadataRepository.countOlderThan(date),
        retentionConfiguration.getKeyMetadataWithUserRetentionDays(), "key submission metadata with user");
    keySubmissionMetadataWithUserMetadataRepository.deleteOlderThan(date);
  }

  private void deleteOutdatedApiTokens() {
    final long apiTokenThreshold = subtractRetentionPeriodFromNowToSeconds(DAYS,
        retentionConfiguration.getApiTokenRetentionDays());
    logDeletionInDays(apiTokenRepository.countOlderThan(apiTokenThreshold),
        retentionConfiguration.getApiTokenRetentionDays(), "API tokens");
    apiTokenRepository.deleteOlderThan(apiTokenThreshold);
  }

  private void deleteOutdatedDeviceTokens() {
    final long deviceTokenThreshold = subtractRetentionPeriodFromNowToEpochMilli(HOURS,
        retentionConfiguration.getDeviceTokenRetentionHours());
    logDeletionInHours(deviceTokenRepository.countOlderThan(deviceTokenThreshold),
        retentionConfiguration.getDeviceTokenRetentionHours(), "device tokens");
    deviceTokenRepository.deleteOlderThan(deviceTokenThreshold);
  }

  private void deleteOutdatedElsTokens() {
    final long elsOtpThreshold = subtractRetentionPeriodFromNowToSeconds(DAYS,
        retentionConfiguration.getElsOtpRetentionDays());
    logDeletionInDays(elsOneTimePasswordRepository.countOlderThan(elsOtpThreshold),
        retentionConfiguration.getElsOtpRetentionDays(), "els-verify tokens");
    elsOneTimePasswordRepository.deleteOlderThan(elsOtpThreshold);
  }

  private void deleteOutdatedExposureRiskMetadata() {
    final LocalDate date = threshold(retentionConfiguration.getExposureRiskMetadataRetentionDays());
    logDeletionInDays(exposureRiskMetadataRepository.countOlderThan(date),
        retentionConfiguration.getExposureRiskMetadataRetentionDays(), "exposure risk metadata");
    exposureRiskMetadataRepository.deleteOlderThan(date);
  }

  private void deleteOutdatedExposureWindows() {
    final LocalDate date = threshold(retentionConfiguration.getExposureWindowRetentionDays());
    logDeletionInDays(exposureWindowRepository.countOlderThan(date),
        retentionConfiguration.getExposureWindowRetentionDays(), "exposure windows");
    exposureWindowRepository.deleteOlderThan(date);
  }

  private void deleteOutdatedOneTimePasswords() {
    final long otpThreshold = subtractRetentionPeriodFromNowToSeconds(DAYS,
        retentionConfiguration.getOtpRetentionDays());
    logDeletionInDays(oneTimePasswordRepository.countOlderThan(otpThreshold),
        retentionConfiguration.getOtpRetentionDays(), "one time passwords");
    oneTimePasswordRepository.deleteOlderThan(otpThreshold);
  }

  private void deleteOutdatedSalt() {
    final long saltThreshold = subtractRetentionPeriodFromNowToEpochMilli(HOURS,
        retentionConfiguration.getSaltRetentionHours());
    logDeletionInHours(saltRepository.countOlderThan(saltThreshold),
        retentionConfiguration.getSaltRetentionHours(), "salts");
    saltRepository.deleteOlderThan(saltThreshold);
  }

  private void deleteOutdatedScanInstance() {
    final LocalDate date = threshold(retentionConfiguration.getExposureWindowRetentionDays());
    logDeletionInDays(scanInstanceRepository.countOlderThan(date),
        retentionConfiguration.getExposureWindowRetentionDays(), "scan instance");
    scanInstanceRepository.deleteOlderThan(date);
  }

  private void deleteTestResultsMetadata() {
    final LocalDate date = threshold(retentionConfiguration.getTestResultMetadataRetentionDays());
    logDeletionInDays(testResultMetadataRepository.countOlderThan(date),
        retentionConfiguration.getTestResultMetadataRetentionDays(), "test results metadata");
    testResultMetadataRepository.deleteOlderThan(date);
  }

  private void deleteUserMetaData() {
    final LocalDate date = threshold(retentionConfiguration.getUserMetadataRetentionDays());
    logDeletionInDays(userMetadataRepository.countOlderThan(date),
        retentionConfiguration.getUserMetadataRetentionDays(), "user metadata");
    userMetadataRepository.deleteOlderThan(date);
  }

  private void deleteSummarizedExposureWindowsWithUserMetadata() {
    final LocalDate date = threshold(retentionConfiguration.getSummarizedExposureWindowRetentionDays());
    logDeletionInDays(summarizedExposureWindowsWithUserMetadataRepo.countOlderThan(date),
        retentionConfiguration.getSummarizedExposureWindowRetentionDays(), "summarized exposure windows");
    summarizedExposureWindowsWithUserMetadataRepo.deleteOlderThan(date);
  }

  private void deleteExposureWindowsTestResult() {
    final LocalDate date = threshold(retentionConfiguration.getExposureWindowTestResultRetentionDays());
    logDeletionInDays(exposureWindowTestResultsRepository.countOlderThan(date),
        retentionConfiguration.getExposureWindowTestResultRetentionDays(), "exposure window test result");
    exposureWindowTestResultsRepository.deleteOlderThan(date);
  }

  private void deleteExposureWindowAtTestRegistration() {
    final LocalDate date = threshold(retentionConfiguration.getExposureWindowAtTestRegistrationRetentionDays());
    logDeletionInDays(exposureWindowsAtTestRegistrationRepository.countOlderThan(date),
        retentionConfiguration.getExposureWindowAtTestRegistrationRetentionDays(),
        "exposure window at test registration");
    exposureWindowsAtTestRegistrationRepository.deleteOlderThan(date);
  }

  private void deleteScanInstanceAtTestRegistration() {
    final LocalDate date = threshold(retentionConfiguration.getScanInstanceAtTestRegistrationRetentionDays());
    logDeletionInDays(scanInstancesAtTestRegistrationRepository.countOlderThan(date),
        retentionConfiguration.getScanInstanceAtTestRegistrationRetentionDays(),
        "scan instance at test registration");
    scanInstancesAtTestRegistrationRepository.deleteOlderThan(date);
  }

  private void logDeletionInDays(final int dataAmount, final int retentionDays, final String dataName) {
    logger.info("Deleting {} {} that are older than {} day(s) ago.", dataAmount, dataName, retentionDays);
  }

  private void logDeletionInHours(final int dataAmount, final int retentionHours, final String dataName) {
    logger.info("Deleting {} {} that are older than {} hour(s) ago.", dataAmount, dataName, retentionHours);
  }

  @Override
  public void run(final ApplicationArguments args) {
    try {
      deleteClientMetadata();
      deleteKeySubmissionMetadataWithClient();
      deleteKeySubmissionMetadataWithUser();
      deleteOutdatedApiTokens();
      deleteOutdatedDeviceTokens();
      deleteOutdatedElsTokens();
      deleteOutdatedExposureRiskMetadata();
      deleteOutdatedOneTimePasswords();
      deleteOutdatedSalt();
      deleteTestResultsMetadata();
      deleteUserMetaData();
      deleteOutdatedScanInstance();
      deleteOutdatedExposureWindows();
      deleteSummarizedExposureWindowsWithUserMetadata();
      deleteExposureWindowsTestResult();
      deleteExposureWindowAtTestRegistration();
      deleteScanInstanceAtTestRegistration();
    } catch (final Exception e) {
      logger.error("Apply of retention policy failed.", e);
      Application.killApplication(appContext);
    }
  }
}

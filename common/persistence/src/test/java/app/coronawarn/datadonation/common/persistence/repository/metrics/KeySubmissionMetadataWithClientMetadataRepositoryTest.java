package app.coronawarn.datadonation.common.persistence.repository.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.coronawarn.datadonation.common.persistence.domain.metrics.KeySubmissionMetadataWithClientMetadata;
import app.coronawarn.datadonation.common.persistence.domain.metrics.TechnicalMetadata;
import app.coronawarn.datadonation.common.persistence.domain.metrics.embeddable.ClientMetadataDetails;
import app.coronawarn.datadonation.common.persistence.domain.metrics.embeddable.CwaVersionMetadata;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;

@DataJdbcTest
class KeySubmissionMetadataWithClientMetadataRepositoryTest {

  @Autowired
  private KeySubmissionMetadataWithClientMetadataRepository keySubmissionMetadataClientMetadataRepository;

  @AfterEach
  void tearDown() {
    keySubmissionMetadataClientMetadataRepository.deleteAll();
  }

  @Test
  void keySubmissionWithClientMetadataShouldBePersistedCorrectly() {
    LocalDate justADate = LocalDate.now(ZoneId.of("UTC"));
    CwaVersionMetadata cwaVersionMetadata = new CwaVersionMetadata(1, 1, 1);
    ClientMetadataDetails clientMetadata = new ClientMetadataDetails(cwaVersionMetadata, "abc", 2, 2, 3, 1l, 2l);
    TechnicalMetadata technicalMetadata = new TechnicalMetadata(justADate, true, false, true, false);
    KeySubmissionMetadataWithClientMetadata keySubmissionMetadata = new KeySubmissionMetadataWithClientMetadata(null,
        true, false, true, false, true, 1, false, clientMetadata, technicalMetadata);

    keySubmissionMetadataClientMetadataRepository.save(keySubmissionMetadata);

    KeySubmissionMetadataWithClientMetadata loadedEntity = keySubmissionMetadataClientMetadataRepository.findAll()
        .iterator().next();
    assertEquals(loadedEntity.getAdvancedConsentGiven(), keySubmissionMetadata.getAdvancedConsentGiven());
    assertEquals(loadedEntity.getLastSubmissionFlowScreen(), keySubmissionMetadata.getLastSubmissionFlowScreen());
    assertEquals(loadedEntity.getSubmitted(), keySubmissionMetadata.getSubmitted());

    assertEquals(loadedEntity.getSubmittedAfterCancel(), keySubmissionMetadata.getSubmittedAfterCancel());
    assertEquals(loadedEntity.getSubmittedAfterSymptomFlow(), keySubmissionMetadata.getSubmittedAfterSymptomFlow());
    assertEquals(loadedEntity.getSubmittedInBackground(), keySubmissionMetadata.getSubmittedInBackground());
    assertEquals(loadedEntity.getSubmittedWithCheckIns(), keySubmissionMetadata.getSubmittedWithCheckIns());

    assertEquals(loadedEntity.getTechnicalMetadata(), keySubmissionMetadata.getTechnicalMetadata());
    assertEquals(loadedEntity.getClientMetadata(), keySubmissionMetadata.getClientMetadata());
    assertNotNull(loadedEntity.getId());
  }
}

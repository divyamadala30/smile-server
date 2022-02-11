package org.mskcc.cmo.metadb.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mskcc.cmo.metadb.model.MetadbPatient;
import org.mskcc.cmo.metadb.model.MetadbRequest;
import org.mskcc.cmo.metadb.model.MetadbSample;
import org.mskcc.cmo.metadb.model.SampleMetadata;
import org.mskcc.cmo.metadb.persistence.neo4j.MetadbPatientRepository;
import org.mskcc.cmo.metadb.persistence.neo4j.MetadbRequestRepository;
import org.mskcc.cmo.metadb.persistence.neo4j.MetadbSampleRepository;
import org.mskcc.cmo.metadb.service.util.RequestDataFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 *
 * @author ochoaa
 */
@Testcontainers
@DataNeo4jTest
@Import(MockDataUtils.class)
public class CorrectCmoPatientIdHandlerTest {
    @Autowired
    private MockDataUtils mockDataUtils;

    @Autowired
    private MetadbRequestService requestService;

    @Autowired
    private MetadbSampleService sampleService;

    @Autowired
    private MetadbPatientService patientService;

    private final Map<String, String> UPDATED_CMO_SAMPLE_LABELS = initNewCmoSampleLabelsExistingPatient();

    @Container
    private static final Neo4jContainer databaseServer = new Neo4jContainer<>()
            .withEnv("NEO4J_dbms_security_procedures_unrestricted", "apoc.*,algo.*");

    @TestConfiguration
    static class Config {
        @Bean
        public org.neo4j.ogm.config.Configuration configuration() {
            return new org.neo4j.ogm.config.Configuration.Builder()
                    .uri(databaseServer.getBoltUrl())
                    .credentials("neo4j", databaseServer.getAdminPassword())
                    .build();
        }
    }

    private final MetadbRequestRepository requestRepository;
    private final MetadbSampleRepository sampleRepository;
    private final MetadbPatientRepository patientRepository;

    @Autowired
    public CorrectCmoPatientIdHandlerTest(MetadbRequestRepository requestRepository,
            MetadbSampleRepository sampleRepository, MetadbPatientRepository patientRepository) {
        this.requestRepository = requestRepository;
        this.sampleRepository = sampleRepository;
        this.patientRepository = patientRepository;
    }


    /**
     * Persists the Mock Request data to the test database.
     * @throws Exception
     */
    @Autowired
    public void initializeMockDatabase() throws Exception {
        // mock request id: REQUESTBEFOREUPDATE_A
        MockJsonTestData requestDataPreSwap = mockDataUtils.mockedRequestJsonDataMap
                .get("mockIncomingRequestPreCmoPatientSwap");
        MetadbRequest requestPreSwap = RequestDataFactory.buildNewLimsRequestFromJson(requestDataPreSwap.getJsonString());
        requestService.saveRequest(requestPreSwap);

        // mock request id: REQUESTAFTERUPDATE_A
        MockJsonTestData requestDataPostSwap = mockDataUtils.mockedRequestJsonDataMap
                .get("mockIncomingRequestPostCmoPatientSwap");
        MetadbRequest requestPostSwap = RequestDataFactory.buildNewLimsRequestFromJson(requestDataPostSwap.getJsonString());
        requestService.saveRequest(requestPostSwap);
    }


    /**
     * Tests sample fetch before patient swap and after the patient id swap in the
     * event that the patient already exists by the new id.
     *
     * <p>Request: REQUESTBEFOREUPDATE_A
     *  Patient: C-CMOPTA1 / Samples: C-CMOPTA1-X001-d, C-CMOPTA1-N001-d
     *  Patient: C-CMOPTB1 / Samples: C-CMOPTB1-X001-d, C-CMOPTB1-N001-d
     *
     * <p>Request: REQUESTAFTERUPDATE_A
     *  Patient: C-CMOPTY1 / Samples: C-CMOPTY1-X001-d, C-CMOPTY1-N001-d
     *  Patient: C-CMOPTX1 / Samples: C-CMOPTX1-X001-d, C-CMOPTX1-N001-d
     *
     * <p>Patient ID swap: C-CMOPTA1 swapped to C-CMOPTY1
     *  - patient will have 4 samples linked to it after swap
     *
     * <p>Request: REQUESTAFTERUPDATE_A
     *  Patient: C-CMOPTY1 / Samples: C-CMOPTY1-X001-d, C-CMOPTY1-N001-d, C-CMOPTY1-X002-d, C-CMOPTY1-N002-d
     *  Patient: C-CMOPTX1 / Samples: C-CMOPTX1-X001-d, C-CMOPTX1-N001-d
     */
    @Test
    public void testPatientIdSwapWithExistingPatient() throws Exception {
        String requestIdPreSwap = "REQUESTBEFOREUPDATE_A";
        String requestIdPostSwap = "REQUESTAFTERUPDATE_A";

        // testing patient id swap from C-CMOPTA1 --> C-CMOPTY1
        String oldCmoPatientId = "C-CMOPTA1";
        String newCmoPatientId = "C-CMOPTY1";
        List<MetadbSample> samplesByOldCmoPatient =
                                sampleService.getSamplesByCmoPatientId(oldCmoPatientId);
        Assertions.assertThat(samplesByOldCmoPatient.size())
                .isEqualTo(2);

        List<MetadbSample> samplesByNewCmoPatient = sampleService.getSamplesByCmoPatientId(newCmoPatientId);
        Assertions.assertThat(samplesByNewCmoPatient.size())
                .isEqualTo(2);

        MetadbPatient newPatient = patientService.getPatientByCmoPatientId(newCmoPatientId);
        Assertions.assertThat(newPatient).isNotNull();

        for (MetadbSample sample : samplesByOldCmoPatient) {
            SampleMetadata latestMetadata = sample.getLatestSampleMetadata();
            latestMetadata.setCmoPatientId(newCmoPatientId);
            if (sample.getSampleCategory().equals("research")) {

                String newCmoSampleLabel = UPDATED_CMO_SAMPLE_LABELS.get(latestMetadata.getCmoSampleName());

                System.out.println("\n\n\nUpdating CMO label and patient ID for sample: " + latestMetadata.getCmoSampleName() + ", " + sample.getPatient().getCmoPatientId().getValue() + " --> " + newCmoSampleLabel + ", " + newCmoPatientId);

                latestMetadata.setCmoSampleName(newCmoSampleLabel);
                sample.setPatient(newPatient);
                sample.updateSampleMetadata(latestMetadata);
                sampleService.saveMetadbSample(sample);
            }
        }

        // double checking that the swap happened..
        MetadbRequest requestPreSwap = requestService.getMetadbRequestById(requestIdPreSwap);
        for (MetadbSample sample : requestPreSwap.getMetaDbSampleList()) {
            System.out.println("\n\n\n\n Sample ID:" + sample.getLatestSampleMetadata().getCmoSampleName() + ", Request ID: " + requestIdPreSwap + ", CMO Patient ID: " + sample.getPatient().getCmoPatientId().getValue());
        }
        System.out.println("\n\n\n\n");

        List<MetadbSample> samplesByNewCmoPatientAfterSwap = sampleService.getSamplesByCmoPatientId(newCmoPatientId);
        Assertions.assertThat(samplesByNewCmoPatientAfterSwap.size())
                .isEqualTo(4);
        // sample size should be 4 but is actually coming out to 3... need to look into this further
    }

    private Map<String, String> initNewCmoSampleLabelsExistingPatient() {
        Map<String, String> map = new HashMap<>();
        map.put("C-CMOPTA1-X001-d", "C-CMOPTY1-X002-d");
        map.put("C-CMOPTA1-N001-d", "C-CMOPTY1-N002-d");
        return map;
    }
}

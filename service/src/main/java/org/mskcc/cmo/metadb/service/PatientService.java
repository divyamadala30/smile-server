package org.mskcc.cmo.metadb.service;

import java.util.UUID;
import org.mskcc.cmo.metadb.model.MetaDbPatient;

public interface PatientService {
    UUID savePatientMetadata(MetaDbPatient metaDbPatient);
    MetaDbPatient findPatientByCmoPatientId(String cmoPatientId);
    UUID findPatientIdBySample(UUID metaDbSampleUuid);
}
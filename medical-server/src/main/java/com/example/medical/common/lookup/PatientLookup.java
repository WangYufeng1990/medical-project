package com.example.medical.common.lookup;

import java.util.Optional;

/**
 * A patient's display fields, for modules that render a patient name or check a
 * patient's allergies but own no patient record.
 * <p>
 * {@code AppointmentService}, {@code BillService}, {@code ChatService},
 * {@code PrescriptionService} and {@code CdsService} used to read
 * {@code PatientRepository} directly, which made those modules depend on the
 * patient module's persistence — and, since the patient module reads their
 * records in turn, closed three import cycles.
 * <p>
 * Deliberately <b>scope-free and audit-free</b>: this answers display questions,
 * and {@code PatientService.getById} is neither. It enforces the doctor scope
 * (a name that resolved before would start answering 403) and writes an audit
 * row through {@code @Auditable(phiAccess = true)} (one row per row of every
 * list it renders).
 */
public interface PatientLookup {

    /** The patient's name; empty when there is no such patient, or none recorded. */
    Optional<String> displayName(Long patientId);

    /** The patient's recorded allergies; empty when there is no such patient, or none recorded. */
    Optional<String> allergies(Long patientId);
}

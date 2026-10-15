package com.example.medical.common.lookup;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * The clinical inputs an eCQM measure reads, for the quality module.
 * <p>
 * {@code QualityMeasureService} used to walk {@code patientRepository.findAll()}
 * and read the patient module's entities directly — the last cross-module read
 * of its kind, and one that also cost an observation query per patient per LOINC.
 * The measures' logic (which patients count, what the thresholds are, how a rate
 * is derived) stays with the quality module; the data behind it is answered here.
 * <p>
 * Deliberately <b>scope-free and audit-free</b>: the daily
 * {@code QualityScheduler} runs with no authenticated user at all, so anything
 * this reader inherited from a login would break that path.
 * <p>
 * Two things are passed through as the measures read them today, rather than
 * improved: the condition text (the measures match words in free text, while the
 * {@code problem} table holds codes that cover a different set of patients — so
 * switching would move the counters) and the value as a string (the measures
 * parse it themselves, and an unparseable value means "not on record").
 */
public interface ClinicalMeasureReader {

    /** One page of the patients a measure must consider, carrying only what a measure reads. */
    Page<MeasurePatientRow> patientPage(Pageable pageable);

    /** The most recent observation per LOINC for each of these patients; absent when there is none. */
    Map<Long, Map<String, LatestObservation>> latestObservations(Collection<Long> patientIds,
                                                                 Collection<String> loincs);

    /**
     * @param ageYears      negative when the date of birth is unknown, which every measure reads as out of range
     * @param conditionText the recorded history, free text
     */
    record MeasurePatientRow(long patientId, int ageYears, String sexAtBirth,
                             String patientStatus, String conditionText) {
    }

    /** @param effectiveDate null when the observation carries no date, as the row itself may */
    record LatestObservation(String value, LocalDateTime effectiveDate) {
    }
}

package com.example.medical.module.export.controller;

import com.example.medical.common.audit.Auditable;
import com.example.medical.module.billing.entity.Bill;
import com.example.medical.module.billing.repository.BillRepository;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.util.CsvUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
public class ExportController {

    private final PatientRepository patientRepository;
    private final BillRepository billRepository;

    @GetMapping("/patients")
    @Auditable(module = "export", action = "EXPORT_PATIENTS")
    public ResponseEntity<String> exportPatients() {
        List<Patient> patients = patientRepository.findAll(
                Sort.by(Sort.Direction.DESC, "createTime"));

        List<String> headers = List.of("MRN", "Name", "DOB", "Sex", "Gender Identity",
                "Race", "Ethnicity", "Language", "Phone", "Email",
                "Address Line 1", "City", "State", "ZIP",
                "Insurance Payer", "Medical History", "Allergies", "Created");
        List<List<String>> rows = new ArrayList<>();
        for (Patient p : patients) {
            rows.add(List.of(
                    p.getMrn() != null ? p.getMrn() : "",
                    p.getName(),
                    p.getDateOfBirth() != null ? p.getDateOfBirth().toString() : "",
                    p.getSexAtBirth() != null ? p.getSexAtBirth() : "",
                    p.getGenderIdentity() != null ? p.getGenderIdentity() : "",
                    p.getRace() != null ? p.getRace() : "",
                    p.getEthnicity() != null ? p.getEthnicity() : "",
                    p.getPreferredLanguage() != null ? p.getPreferredLanguage() : "",
                    p.getPhoneMobile() != null ? p.getPhoneMobile() : "",
                    p.getEmail() != null ? p.getEmail() : "",
                    p.getAddressLine1() != null ? p.getAddressLine1() : "",
                    p.getCity() != null ? p.getCity() : "",
                    p.getState() != null ? p.getState() : "",
                    p.getZipCode() != null ? p.getZipCode() : "",
                    p.getInsurancePayer() != null ? p.getInsurancePayer() : "",
                    p.getMedicalHistory() != null ? p.getMedicalHistory() : "",
                    p.getAllergies() != null ? p.getAllergies() : "",
                    p.getCreateTime() != null ? p.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE) : ""));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=patients.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(CsvUtil.toCsv(headers, rows));
    }

    @GetMapping("/bills")
    @Auditable(module = "export", action = "EXPORT_BILLS")
    public ResponseEntity<String> exportBills() {
        List<Bill> bills = billRepository.findAll(
                Sort.by(Sort.Direction.DESC, "createTime"));

        List<String> headers = List.of("ID", "Patient ID", "Prescription ID", "Appointment ID",
                "Bill Type", "Claim Status", "Total Charge", "Insurance Adj", "Insurance Pay",
                "Patient Resp", "Patient Paid", "Copay",
                "CPT Codes", "ICD-10 Codes", "POS", "Payer", "Claim #",
                "Filing Date", "Adjud Date", "Pay Time", "Payment Method", "Created");
        List<List<String>> rows = new ArrayList<>();
        for (Bill b : bills) {
            rows.add(List.of(
                    b.getId().toString(),
                    b.getPatientId().toString(),
                    b.getPrescriptionId() != null ? b.getPrescriptionId().toString() : "",
                    b.getAppointmentId() != null ? b.getAppointmentId().toString() : "",
                    b.getBillType() != null ? b.getBillType() : "",
                    b.getClaimStatus() != null ? b.getClaimStatus() : "",
                    b.getTotalCharge() != null ? b.getTotalCharge().toString() : "",
                    b.getInsuranceAdjustment() != null ? b.getInsuranceAdjustment().toString() : "",
                    b.getInsurancePayment() != null ? b.getInsurancePayment().toString() : "",
                    b.getPatientResponsibility() != null ? b.getPatientResponsibility().toString() : "",
                    b.getPatientPaidAmount() != null ? b.getPatientPaidAmount().toString() : "",
                    b.getCopayAmount() != null ? b.getCopayAmount().toString() : "",
                    b.getCptCodes() != null ? b.getCptCodes() : "",
                    b.getIcd10Codes() != null ? b.getIcd10Codes() : "",
                    b.getPlaceOfServiceCode() != null ? b.getPlaceOfServiceCode() : "",
                    b.getInsurancePayerName() != null ? b.getInsurancePayerName() : "",
                    b.getInsuranceClaimNumber() != null ? "****" + b.getInsuranceClaimNumber().substring(Math.max(0, b.getInsuranceClaimNumber().length() - 4)) : "",
                    b.getClaimFilingDate() != null ? b.getClaimFilingDate().toString() : "",
                    b.getAdjudicationDate() != null ? b.getAdjudicationDate().toString() : "",
                    b.getPayTime() != null ? b.getPayTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "",
                    b.getPaymentMethod() != null ? b.getPaymentMethod() : "",
                    b.getCreateTime() != null ? b.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE) : ""));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bills.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(CsvUtil.toCsv(headers, rows));
    }

}

package com.example.medical.module.export.controller;

import com.example.medical.common.audit.Auditable;
import com.example.medical.module.appointment.repository.AppointmentRepository;
import com.example.medical.module.billing.entity.Bill;
import com.example.medical.module.billing.repository.BillRepository;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.module.prescription.repository.PrescriptionRepository;
import com.example.medical.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
public class ExportController {

    private static final int EXPORT_PAGE_SIZE = 500;

    private final PatientRepository patientRepository;
    private final BillRepository billRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;

    @GetMapping("/patients")
    @Auditable(module = "export", action = "EXPORT_PATIENTS", phiAccess = true)
    public ResponseEntity<StreamingResponseBody> exportPatients() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        LoginUser user = auth != null ? (LoginUser) auth.getPrincipal() : null;
        Set<Long> scopedPatientIds = resolveExportScope(user);

        StreamingResponseBody stream = outputStream -> {
            Writer writer = new OutputStreamWriter(outputStream);
            writer.write("MRN,Name,DOB,Sex,Gender,Race,Ethnicity,Language,Phone,Email," +
                    "Address,City,State,ZIP,Insurance,MedicalHistory,Allergies,Created\n");

            int page = 0;
            boolean hasMore;
            do {
                var patients = patientRepository.findAll(
                        org.springframework.data.domain.PageRequest.of(page++, EXPORT_PAGE_SIZE,
                                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createTime")));
                for (Patient p : patients.getContent()) {
                    if (scopedPatientIds != null && !scopedPatientIds.contains(p.getId())) continue;
                    writer.write(String.join(",",
                            csv(p.getMrn()), csv(p.getName()),
                            csv(p.getDateOfBirth() != null ? p.getDateOfBirth().toString() : ""),
                            csv(p.getSexAtBirth()), csv(p.getGenderIdentity()),
                            csv(p.getRace()), csv(p.getEthnicity()),
                            csv(p.getPreferredLanguage()),
                            csv(p.getPhoneMobile() != null ? maskLast4(p.getPhoneMobile()) : ""),
                            csv(p.getEmail() != null ? maskEmail(p.getEmail()) : ""),
                            csv(p.getAddressLine1()), csv(p.getCity()),
                            csv(p.getState()), csv(p.getZipCode()),
                            csv(p.getInsurancePayer()),
                            csv(p.getMedicalHistory()),
                            csv(p.getAllergies()),
                            csv(p.getCreateTime() != null
                                    ? p.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE) : "")));
                    writer.write("\n");
                }
                hasMore = patients.hasNext();
            } while (hasMore);
            writer.flush();
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=patients.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(stream);
    }

    @GetMapping("/bills")
    @Auditable(module = "export", action = "EXPORT_BILLS", phiAccess = true)
    public ResponseEntity<StreamingResponseBody> exportBills() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        LoginUser user = auth != null ? (LoginUser) auth.getPrincipal() : null;
        Set<Long> scopedPatientIds = resolveExportScope(user);

        StreamingResponseBody stream = outputStream -> {
            Writer writer = new OutputStreamWriter(outputStream);
            writer.write("ID,PatientID,Type,Status,TotalCharge,InsAdj,InsPay," +
                    "PatientResp,PatientPaid,Copay,CPT,ICD10,POS,Payer,Claim#,FilingDate,PayTime,Method,Created\n");

            int page = 0;
            boolean hasMore;
            do {
                var bills = billRepository.findAll(
                        org.springframework.data.domain.PageRequest.of(page++, EXPORT_PAGE_SIZE,
                                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createTime")));
                for (Bill b : bills.getContent()) {
                    if (scopedPatientIds != null && !scopedPatientIds.contains(b.getPatientId())) continue;
                    writer.write(String.join(",",
                            csv(b.getId()), csv(b.getPatientId()),
                            csv(b.getBillType()), csv(b.getClaimStatus()),
                            csv(b.getTotalCharge()), csv(b.getInsuranceAdjustment()),
                            csv(b.getInsurancePayment()), csv(b.getPatientResponsibility()),
                            csv(b.getPatientPaidAmount()), csv(b.getCopayAmount()),
                            csv(b.getCptCodes()), csv(b.getIcd10Codes()),
                            csv(b.getPlaceOfServiceCode()), csv(b.getInsurancePayerName()),
                            csv(b.getInsuranceClaimNumber() != null
                                    ? "****" + b.getInsuranceClaimNumber().substring(
                                            Math.max(0, b.getInsuranceClaimNumber().length() - 4)) : ""),
                            csv(b.getClaimFilingDate()),
                            csv(b.getPayTime() != null
                                    ? b.getPayTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : ""),
                            csv(b.getPaymentMethod()),
                            csv(b.getCreateTime() != null
                                    ? b.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE) : "")));
                    writer.write("\n");
                }
                hasMore = bills.hasNext();
            } while (hasMore);
            writer.flush();
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bills.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(stream);
    }

    private Set<Long> resolveExportScope(LoginUser user) {
        if (user == null) return null;
        var authorities = user.getAuthorities();
        boolean isAdmin = authorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return null;

        Set<Long> ids = new HashSet<>();
        ids.addAll(appointmentRepository.findDistinctPatientIdsByDoctor(user.getUserId()));
        ids.addAll(prescriptionRepository.findDistinctPatientIdsByDoctor(user.getUserId()));
        return ids;
    }

    private static String csv(Object value) {
        if (value == null) return "";
        String s = value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String maskLast4(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.length() <= 4) return "****";
        return "****" + value.substring(value.length() - 4);
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "";
        int at = email.indexOf('@');
        if (at <= 0) return "****";
        return email.charAt(0) + "***" + email.substring(at);
    }
}

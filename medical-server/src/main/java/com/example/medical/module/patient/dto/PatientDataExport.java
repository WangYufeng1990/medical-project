package com.example.medical.module.patient.dto;

import com.example.medical.module.appointment.entity.Appointment;
import com.example.medical.module.billing.entity.Bill;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.prescription.entity.Prescription;
import com.example.medical.module.prescription.entity.PrescriptionItem;
import lombok.Data;

import java.util.List;

@Data
public class PatientDataExport {

    private PatientVO demographics;
    private List<AppointmentSummary> appointments;
    private List<PrescriptionSummary> prescriptions;
    private List<BillSummary> bills;
    private String exportDate;
    private String dataUseNotice;

    public static PatientDataExport of(Patient patient,
                                        List<Appointment> appointments,
                                        List<Prescription> prescriptions,
                                        List<PrescriptionItem> allItems,
                                        List<Bill> bills) {
        PatientDataExport export = new PatientDataExport();
        export.demographics = PatientVO.fromEntity(patient);
        export.appointments = appointments.stream().map(AppointmentSummary::from).toList();
        export.prescriptions = prescriptions.stream()
                .map(p -> PrescriptionSummary.from(p,
                        allItems.stream()
                                .filter(i -> i.getPrescriptionId().equals(p.getId()))
                                .toList()))
                .toList();
        export.bills = bills.stream().map(BillSummary::from).toList();
        export.exportDate = java.time.LocalDateTime.now().toString();
        export.dataUseNotice = "This data is provided pursuant to HIPAA 45 CFR 164.524. "
                + "You have the right to access your protected health information.";
        return export;
    }

    @Data
    public static class AppointmentSummary {
        private Long id;
        private String appointmentTime;
        private String visitType;
        private String chiefComplaint;
        private String department;
        private String description;
        private String status;

        static AppointmentSummary from(Appointment a) {
            AppointmentSummary s = new AppointmentSummary();
            s.id = a.getId();
            s.appointmentTime = a.getAppointmentTime() != null ? a.getAppointmentTime().toString() : null;
            s.visitType = a.getVisitType();
            s.chiefComplaint = a.getChiefComplaint();
            s.department = a.getDepartment();
            s.description = a.getDescription();
            s.status = a.getStatus() != null ? a.getStatus().toString() : null;
            return s;
        }
    }

    @Data
    public static class PrescriptionSummary {
        private Long id;
        private String diagnosis;
        private String icd10Codes;
        private String prescriptionDate;
        private String rxStatus;
        private List<ItemSummary> items;

        static PrescriptionSummary from(Prescription p, List<PrescriptionItem> items) {
            PrescriptionSummary s = new PrescriptionSummary();
            s.id = p.getId();
            s.diagnosis = p.getDiagnosis();
            s.icd10Codes = p.getIcd10Codes();
            s.prescriptionDate = p.getPrescriptionDate() != null ? p.getPrescriptionDate().toString() : null;
            s.rxStatus = p.getRxStatus();
            s.items = items.stream().map(ItemSummary::from).toList();
            return s;
        }
    }

    @Data
    public static class ItemSummary {
        private String drugName;
        private String dosage;
        private String frequency;
        private String sig;
        private Integer daysSupply;
        private Integer refills;

        static ItemSummary from(PrescriptionItem i) {
            ItemSummary s = new ItemSummary();
            s.drugName = i.getDrugName();
            s.dosage = i.getDosage();
            s.frequency = i.getFrequency();
            s.sig = i.getSig();
            s.daysSupply = i.getDaysSupply();
            s.refills = i.getRefills();
            return s;
        }
    }

    @Data
    public static class BillSummary {
        private Long id;
        private String billType;
        private String claimStatus;
        private String totalCharge;
        private String patientResponsibility;
        private String cptCodes;
        private String icd10Codes;
        private String insurancePayerName;

        static BillSummary from(Bill b) {
            BillSummary s = new BillSummary();
            s.id = b.getId();
            s.billType = b.getBillType();
            s.claimStatus = b.getClaimStatus();
            s.totalCharge = b.getTotalCharge() != null ? b.getTotalCharge().toString() : null;
            s.patientResponsibility = b.getPatientResponsibility() != null
                    ? b.getPatientResponsibility().toString() : null;
            s.cptCodes = b.getCptCodes();
            s.icd10Codes = b.getIcd10Codes();
            s.insurancePayerName = b.getInsurancePayerName();
            return s;
        }
    }
}

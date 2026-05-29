package com.example.medical.common.config;

import com.example.medical.module.appointment.entity.Appointment;
import com.example.medical.module.appointment.repository.AppointmentRepository;
import com.example.medical.module.billing.entity.Bill;
import com.example.medical.module.billing.repository.BillRepository;
import com.example.medical.module.chat.entity.Message;
import com.example.medical.module.chat.repository.MessageRepository;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.entity.PatientAuth;
import com.example.medical.module.patient.repository.PatientAuthRepository;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.module.prescription.entity.Prescription;
import com.example.medical.module.prescription.entity.PrescriptionItem;
import com.example.medical.module.prescription.repository.PrescriptionItemRepository;
import com.example.medical.module.prescription.repository.PrescriptionRepository;
import com.example.medical.module.system.entity.SysMenu;
import com.example.medical.module.system.entity.SysRole;
import com.example.medical.module.system.entity.SysUser;
import com.example.medical.module.system.repository.SysMenuRepository;
import com.example.medical.module.system.repository.SysRoleRepository;
import com.example.medical.module.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysUserRepository sysUserRepository;
    private final SysRoleRepository sysRoleRepository;
    private final SysMenuRepository sysMenuRepository;
    private final PatientRepository patientRepository;
    private final PatientAuthRepository patientAuthRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final BillRepository billRepository;
    private final MessageRepository messageRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        if (sysUserRepository.count() > 0) {
            return;
        }

        log.info("Initializing seed data...");

        seedUsers();
        seedRolesAndMenus();
        seedPatients();
        seedPatientAuth();
        seedAppointments();
        seedPrescriptions();
        seedBills();
        seedMessages();

        log.info("Seed data initialized (admin/admin123, doctor1/doctor123, patient1/patient123 using BCrypt)");
    }

    private void seedUsers() {
        SysUser admin = new SysUser();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setRealName("Administrator");
        admin.setPhone("312-555-0001");
        admin.setEmail("admin@medical.com");
        admin.setGender(1);
        admin.setStatus(1);
        sysUserRepository.save(admin);

        SysUser doctor = new SysUser();
        doctor.setId(2L);
        doctor.setUsername("doctor1");
        doctor.setPassword(passwordEncoder.encode("doctor123"));
        doctor.setRealName("Dr. Sarah Mitchell");
        doctor.setPhone("312-555-0002");
        doctor.setEmail("sarah.mitchell@medical.com");
        doctor.setGender(0);
        doctor.setStatus(1);
        doctor.setNpi("1234567890");
        doctor.setStateLicenseNumber("036.140000");
        doctor.setLicenseState("IL");
        doctor.setDeaNumber("SM1234567");
        doctor.setTaxonomyCode("207Q00000X");
        doctor.setCredentials("MD");
        doctor.setSpecialty("Family Medicine");
        sysUserRepository.save(doctor);
    }

    private void seedRolesAndMenus() {
        SysRole adminRole = new SysRole();
        adminRole.setId(1L);
        adminRole.setRoleName("Admin");
        adminRole.setRoleCode("ADMIN");
        adminRole.setDescription("System administrator");
        adminRole.setStatus(1);
        sysRoleRepository.save(adminRole);

        SysRole doctorRole = new SysRole();
        doctorRole.setId(2L);
        doctorRole.setRoleName("Doctor");
        doctorRole.setRoleCode("DOCTOR");
        doctorRole.setDescription("Doctor");
        doctorRole.setStatus(1);
        sysRoleRepository.save(doctorRole);

        SysRole patientRole = new SysRole();
        patientRole.setId(3L);
        patientRole.setRoleName("Patient");
        patientRole.setRoleCode("PATIENT");
        patientRole.setDescription("Patient");
        patientRole.setStatus(1);
        sysRoleRepository.save(patientRole);

        // Join tables — no JPA entities, must use JDBC
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?,?)", 1L, 1L);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?,?)", 2L, 2L);

        Object[][] menuDefs = {
                {1L, 0L, "Dashboard", "/dashboard", "dashboard/index", "Odometer", "MENU", null, 1, 1},
                {2L, 0L, "System", "/system", null, "Setting", "DIRECTORY", null, 10, 1},
                {3L, 2L, "Users", "/system/users", "system/users/index", "User", "MENU", "system:user:list", 11, 1},
                {4L, 2L, "Roles", "/system/roles", "system/roles/index", "Avatar", "MENU", "system:role:list", 12, 1},
                {5L, 2L, "Menus", "/system/menus", "system/menus/index", "Menu", "MENU", "system:menu:list", 13, 1},
                {10L, 0L, "Patients", "/patients", "patients/index", "UserFilled", "MENU", "patient:list", 20, 1},
                {11L, 0L, "Appointments", "/appointments", "appointments/index", "Calendar", "MENU", "appointment:list", 30, 1},
                {12L, 0L, "Prescriptions", "/prescriptions", "prescriptions/index", "Document", "MENU", "prescription:list", 40, 1},
                {13L, 0L, "Billing", "/billing", "billing/index", "Money", "MENU", "billing:list", 50, 1},
        };
        for (Object[] m : menuDefs) {
            SysMenu menu = new SysMenu();
            menu.setId((Long) m[0]);
            menu.setParentId((Long) m[1]);
            menu.setMenuName((String) m[2]);
            menu.setPath((String) m[3]);
            menu.setComponent((String) m[4]);
            menu.setIcon((String) m[5]);
            menu.setType((String) m[6]);
            menu.setPermission((String) m[7]);
            menu.setSort((Integer) m[8]);
            menu.setStatus((Integer) m[9]);
            sysMenuRepository.save(menu);
        }

        // Role-menu assignments — join table, must use JDBC
        for (long mid : new long[]{1, 2, 3, 4, 5, 10, 11, 12, 13}) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?,?)", 1L, mid);
        }
        for (long mid : new long[]{1, 10, 11, 12, 13}) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?,?)", 2L, mid);
        }
    }

    private void seedPatients() {
        Patient p1 = buildPatient(100L, "MRN-10001", "123-45-6789",
                "James Anderson", "1998-02-14", "M", "Male",
                "White", "Not Hispanic or Latino", "en", "Single",
                "312-555-0101", null, "james.anderson@email.com",
                "1400 S Lake Shore Dr", null, "Chicago", "IL", "60605",
                "Mary Anderson", "312-555-0102", "Spouse",
                "Blue Cross Blue Shield", "BCBS-7890123", "GRP-88421",
                "Hypertension diagnosed 2024-03; Type 2 Diabetes diagnosed 2025-01",
                "Penicillin; Shellfish");
        patientRepository.save(p1);

        Patient p2 = buildPatient(101L, "MRN-10002", "987-65-4321",
                "Maria Garcia", "1991-08-23", "F", "Female",
                "White", "Hispanic or Latino", "es", "Married",
                "312-555-0201", "312-555-0202", "maria.garcia@email.com",
                "200 E Randolph St", null, "Chicago", "IL", "60601",
                "Carlos Garcia", "312-555-0203", "Spouse",
                "Aetna", "AET-4567890", "GRP-99234",
                "Iron-deficiency anemia diagnosed 2023; Seasonal allergic asthma",
                "Dust mites; Pollen");
        patientRepository.save(p2);

        Patient p3 = buildPatient(102L, "MRN-10003", null,
                "Robert Chen", "1981-05-07", "M", "Male",
                "Asian", "Not Hispanic or Latino", "en", "Divorced",
                "312-555-0301", null, "robert.chen@email.com",
                "233 S Wacker Dr", null, "Chicago", "IL", "60606",
                "Linda Chen", "312-555-0302", "Sister",
                "UnitedHealthcare", "UHC-3456789", "GRP-55109",
                "Lumbar disc herniation 2022; Hyperlipidemia diagnosed 2024",
                null);
        patientRepository.save(p3);
    }

    private Patient buildPatient(Long id, String mrn, String ssn,
                                  String name, String dob, String sex, String genderId,
                                  String race, String ethnicity, String lang, String marital,
                                  String phoneMobile, String phoneHome, String email,
                                  String addr1, String addr2, String city, String state, String zip,
                                  String emergName, String emergPhone, String emergRel,
                                  String payer, String memberId, String groupNum,
                                  String history, String allergies) {
        Patient p = new Patient();
        p.setId(id);
        p.setMrn(mrn);
        p.setSsn(ssn);
        p.setName(name);
        p.setDateOfBirth(LocalDate.parse(dob));
        p.setSexAtBirth(sex);
        p.setGenderIdentity(genderId);
        p.setRace(race);
        p.setEthnicity(ethnicity);
        p.setPreferredLanguage(lang);
        p.setMaritalStatus(marital);
        p.setPatientStatus("active");
        p.setPrimaryCareProvider("Dr. Sarah Mitchell");
        p.setPhoneMobile(phoneMobile);
        p.setPhoneHome(phoneHome);
        p.setEmail(email);
        p.setAddressLine1(addr1);
        p.setAddressLine2(addr2);
        p.setCity(city);
        p.setState(state);
        p.setZipCode(zip);
        p.setEmergencyContactName(emergName);
        p.setEmergencyContactPhone(emergPhone);
        p.setEmergencyContactRelation(emergRel);
        p.setInsurancePayer(payer);
        p.setInsuranceMemberId(memberId);
        p.setInsuranceGroupNumber(groupNum);
        p.setMedicalHistory(history);
        p.setAllergies(allergies);
        return p;
    }

    private void seedPatientAuth() {
        patientAuthRepository.save(buildPatientAuth(1L, 100L, "patient1", "patient123"));
        patientAuthRepository.save(buildPatientAuth(2L, 101L, "patient2", "patient123"));
        patientAuthRepository.save(buildPatientAuth(3L, 102L, "patient3", "patient123"));
    }

    private PatientAuth buildPatientAuth(Long id, Long patientId, String username, String rawPassword) {
        PatientAuth auth = new PatientAuth();
        auth.setId(id);
        auth.setPatientId(patientId);
        auth.setUsername(username);
        auth.setPassword(passwordEncoder.encode(rawPassword));
        auth.setStatus(1);
        auth.setFailedAttempts(0);
        return auth;
    }

    private void seedAppointments() {
        Appointment a1 = buildAppointment(200L, 100L, 2L,
                "2026-05-15T09:00:00", 3, "FOLLOW_UP",
                "Routine blood pressure check", "Cardiology", 30, "99213",
                "Follow-up for hypertension",
                "Blood pressure 130/85, stable. Continue current medication.");
        appointmentRepository.save(a1);

        Appointment a2 = buildAppointment(201L, 100L, 2L,
                "2026-05-20T10:30:00", 3, "URGENT_CARE",
                "Fever and sore throat x3 days", "Family Medicine", 30, "99203",
                "Fever and sore throat",
                "Diagnosed with upper respiratory infection. Prescribed antibiotics.");
        appointmentRepository.save(a2);

        Appointment a3 = buildAppointment(202L, 100L, 2L,
                "2026-05-28T14:00:00", 0, "ANNUAL_PHYSICAL",
                "Annual diabetes checkup and wellness exam", "Family Medicine", 45, "99214",
                "Annual diabetes checkup", null);
        appointmentRepository.save(a3);

        Appointment a4 = buildAppointment(203L, 100L, 2L,
                "2026-06-15T08:30:00", 0, "FOLLOW_UP",
                "Blood sugar recheck post medication adjustment", "Family Medicine", 30, "99213",
                "Blood sugar recheck",
                "Patient needs to fast for 8h before test");
        appointmentRepository.save(a4);

        Appointment a5 = buildAppointment(204L, 101L, 2L,
                "2026-05-22T11:00:00", 3, "CONSULTATION",
                "Persistent allergy symptoms, suspected environmental triggers",
                "Allergy & Immunology", 45, "99244",
                "Allergy consultation",
                "Skin prick test positive for dust mites and pollen. Prescribed antihistamines.");
        appointmentRepository.save(a5);
    }

    private Appointment buildAppointment(Long id, Long patientId, Long doctorId,
                                          String time, Integer status, String visitType,
                                          String complaint, String dept, Integer duration,
                                          String cpt, String desc, String notes) {
        Appointment a = new Appointment();
        a.setId(id);
        a.setPatientId(patientId);
        a.setDoctorId(doctorId);
        a.setAppointmentTime(LocalDateTime.parse(time));
        a.setStatus(status);
        a.setVisitType(visitType);
        a.setChiefComplaint(complaint);
        a.setDepartment(dept);
        a.setDuration(duration);
        a.setCptCode(cpt);
        a.setDescription(desc);
        a.setNotes(notes);
        return a;
    }

    private void seedPrescriptions() {
        Prescription rx1 = buildPrescription(300L, 100L, 2L,
                "Upper respiratory infection", "J06.9", "2026-05-20",
                "MEDICATION", "completed", "Walgreens #1234", "312-555-0400");
        prescriptionRepository.save(rx1);

        prescriptionItemRepository.save(buildRxItem(400L, 300L,
                "Amoxicillin", "65862-0017-01", "308191", "500mg", "500mg",
                "PO", "TID", "Take one capsule three times daily with food",
                7, 7, 21, 0, 0, 0.85));
        prescriptionItemRepository.save(buildRxItem(401L, 300L,
                "Ibuprofen", "49035-0323-50", "5640", "200mg", "200mg",
                "PO", "BID", "Take one tablet twice daily as needed for pain",
                3, 3, 6, 0, 0, 0.50));

        Prescription rx2 = buildPrescription(301L, 100L, 2L,
                "Allergic rhinitis; Type 2 diabetes mellitus", "J30.9;E11.9", "2026-05-28",
                "MEDICATION", "active", "Walgreens #1234", "312-555-0400");
        prescriptionRepository.save(rx2);

        prescriptionItemRepository.save(buildRxItem(402L, 301L,
                "Cetirizine", "55111-0183-01", "23642", "10mg", "10mg",
                "PO", "QD", "Take one tablet daily for allergy symptoms",
                14, 14, 14, 0, 0, 1.20));
        prescriptionItemRepository.save(buildRxItem(403L, 301L,
                "Metformin HCl", "65862-0109-01", "6809", "850mg", "850mg",
                "PO", "BID", "Take one tablet twice daily with meals",
                30, 30, 60, 2, 0, 0.35));

        Prescription rx3 = buildPrescription(302L, 101L, 2L,
                "Seasonal allergic asthma", "J45.30", "2026-05-22",
                "MEDICATION", "active", "CVS Pharmacy #5678", "312-555-0500");
        prescriptionRepository.save(rx3);

        prescriptionItemRepository.save(buildRxItem(404L, 302L,
                "Montelukast", "00006-1715-31", "64479", "5mg", "5mg",
                "PO", "QD", "Take one tablet daily at bedtime for asthma prevention",
                30, 30, 30, 2, 0, 2.50));
        prescriptionItemRepository.save(buildRxItem(405L, 302L,
                "Albuterol HFA Inhaler", "59310-0579-22", "435", "100mcg", "100mcg",
                "INH", "PRN", "Inhale 1-2 puffs every 4-6 hours as needed for wheezing or shortness of breath",
                30, 30, 1, 0, 1, 25.00));
    }

    private Prescription buildPrescription(Long id, Long patientId, Long doctorId,
                                            String diagnosis, String icd10, String date,
                                            String type, String status,
                                            String pharmacy, String pharmacyPhone) {
        Prescription p = new Prescription();
        p.setId(id);
        p.setPatientId(patientId);
        p.setDoctorId(doctorId);
        p.setDiagnosis(diagnosis);
        p.setIcd10Codes(icd10);
        p.setPrescriptionDate(LocalDate.parse(date));
        p.setPrescriptionType(type);
        p.setRxStatus(status);
        p.setPrescriberNpi("1234567890");
        p.setDeaNumber("SM1234567");
        p.setPharmacyName(pharmacy);
        p.setPharmacyPhone(pharmacyPhone);
        return p;
    }

    private PrescriptionItem buildRxItem(Long id, Long rxId, String drugName,
                                          String ndc, String rxnorm, String spec, String dosage,
                                          String route, String freq, String sig,
                                          int duration, int daysSupply, int quantity,
                                          int refills, int daw, double price) {
        PrescriptionItem item = new PrescriptionItem();
        item.setId(id);
        item.setPrescriptionId(rxId);
        item.setDrugName(drugName);
        item.setNdcCode(ndc);
        item.setRxnormCode(rxnorm);
        item.setSpecification(spec);
        item.setDosage(dosage);
        item.setRoute(route);
        item.setFrequency(freq);
        item.setSig(sig);
        item.setDuration(duration);
        item.setDaysSupply(daysSupply);
        item.setQuantity(quantity);
        item.setRefills(refills);
        item.setDaw(daw);
        item.setUnitPrice(BigDecimal.valueOf(price));
        return item;
    }

    private void seedBills() {
        Bill b1 = new Bill();
        b1.setId(500L);
        b1.setPatientId(100L);
        b1.setPrescriptionId(300L);
        b1.setAppointmentId(201L);
        b1.setBillType("PROFESSIONAL");
        b1.setClaimStatus("PAID");
        b1.setTotalCharge(BigDecimal.valueOf(20.85));
        b1.setInsuranceAdjustment(BigDecimal.valueOf(15.85));
        b1.setInsurancePayment(BigDecimal.valueOf(5.00));
        b1.setPatientResponsibility(BigDecimal.ZERO);
        b1.setPatientPaidAmount(BigDecimal.ZERO);
        b1.setCopayAmount(BigDecimal.ZERO);
        b1.setCptCodes("99203");
        b1.setIcd10Codes("J06.9");
        b1.setPlaceOfServiceCode("11");
        b1.setBillingProviderNpi("1234567890");
        b1.setRenderingProviderNpi("1234567890");
        b1.setInsurancePayerName("Blue Cross Blue Shield");
        b1.setInsuranceClaimNumber("BCBS-CLM-80001");
        b1.setClaimFilingDate(LocalDate.parse("2026-05-21"));
        b1.setAdjudicationDate(LocalDate.parse("2026-05-23"));
        b1.setPayTime(LocalDateTime.parse("2026-05-23T15:30:00"));
        b1.setPaymentMethod("CREDIT_CARD");
        b1.setReceiptNumber("RCPT-10001");
        billRepository.save(b1);

        Bill b2 = new Bill();
        b2.setId(501L);
        b2.setPatientId(100L);
        b2.setPrescriptionId(301L);
        b2.setAppointmentId(202L);
        b2.setBillType("PROFESSIONAL");
        b2.setClaimStatus("PENDING");
        b2.setTotalCharge(BigDecimal.valueOf(37.80));
        b2.setInsuranceAdjustment(BigDecimal.ZERO);
        b2.setInsurancePayment(BigDecimal.ZERO);
        b2.setPatientResponsibility(BigDecimal.valueOf(37.80));
        b2.setPatientPaidAmount(BigDecimal.ZERO);
        b2.setCopayAmount(BigDecimal.ZERO);
        b2.setCptCodes("99214");
        b2.setIcd10Codes("J30.9;E11.9");
        b2.setPlaceOfServiceCode("11");
        b2.setBillingProviderNpi("1234567890");
        b2.setRenderingProviderNpi("1234567890");
        b2.setInsurancePayerName("Blue Cross Blue Shield");
        b2.setClaimFilingDate(LocalDate.parse("2026-05-29"));
        billRepository.save(b2);

        Bill b3 = new Bill();
        b3.setId(502L);
        b3.setPatientId(101L);
        b3.setPrescriptionId(302L);
        b3.setAppointmentId(204L);
        b3.setBillType("PROFESSIONAL");
        b3.setClaimStatus("PAID");
        b3.setTotalCharge(BigDecimal.valueOf(100.00));
        b3.setInsuranceAdjustment(BigDecimal.valueOf(72.50));
        b3.setInsurancePayment(BigDecimal.valueOf(22.50));
        b3.setPatientResponsibility(BigDecimal.valueOf(5.00));
        b3.setPatientPaidAmount(BigDecimal.valueOf(5.00));
        b3.setCopayAmount(BigDecimal.ZERO);
        b3.setCptCodes("99244");
        b3.setIcd10Codes("J45.30");
        b3.setPlaceOfServiceCode("11");
        b3.setBillingProviderNpi("1234567890");
        b3.setRenderingProviderNpi("1234567890");
        b3.setInsurancePayerName("Aetna");
        b3.setInsuranceClaimNumber("AET-CLM-45678");
        b3.setClaimFilingDate(LocalDate.parse("2026-05-23"));
        b3.setAdjudicationDate(LocalDate.parse("2026-05-25"));
        b3.setPayTime(LocalDateTime.parse("2026-05-25T16:00:00"));
        b3.setPaymentMethod("HSA");
        b3.setReceiptNumber("RCPT-10002");
        billRepository.save(b3);
    }

    private void seedMessages() {
        saveMessage(600L, 100L, 2L,
                "Hello Dr. Mitchell, my blood pressure has been a bit high the past few days. Should I be concerned?", 1);
        saveMessage(601L, 2L, 100L,
                "What are your readings? Have you been taking your medication regularly?", 1);
        saveMessage(602L, 100L, 2L,
                "Around 145/90 in the morning. Yes, I'm taking the medication on time.", 1);
        saveMessage(603L, 2L, 100L,
                "That's mildly elevated. Let's keep monitoring. Cut down on salt and come in for a checkup if it stays above 140/90 for three more days.", 1);
        saveMessage(604L, 100L, 2L,
                "OK, I'll watch my salt intake. Thank you, doctor.", 1);
        saveMessage(605L, 2L, 100L,
                "You're welcome. Also, please fast for 8 hours before the June 15 blood sugar test.", 0);
        saveMessage(606L, 101L, 2L,
                "Dr. Mitchell, my allergy symptoms have gotten much better after using the inhaler you prescribed.", 1);
        saveMessage(607L, 2L, 101L,
                "That's good to hear, Maria. Keep using it as prescribed. Let me know if symptoms return.", 0);
    }

    private void saveMessage(Long id, Long senderId, Long receiverId, String content, int isRead) {
        Message msg = new Message();
        msg.setId(id);
        msg.setSenderId(senderId);
        msg.setReceiverId(receiverId);
        msg.setContent(content);
        msg.setIsRead(isRead);
        messageRepository.save(msg);
    }
}

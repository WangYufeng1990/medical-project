package com.example.medical.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
@Profile({"dev", "h2"})
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class);
        if (count != null && count > 0) {
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
        seedObservations();
        seedLoincCatalog();
        seedPharmacies();
        seedQualityMeasures();
        seedCds();

        seedAllergies();
        seedVitalSigns();
        seedProblems();
        seedImmunizations();
        seedReferrals();
        seedCharges();
        seedCarePlans();
        seedPriorAuths();
        seedFormularyEntries();
        log.info("Seed data initialized (admin, doctor1, patient1 — all BCrypt hashed)");
    }

    private void seedUsers() {
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password, real_name, phone, email, gender, status, npi, " +
                "state_license_number, license_state, dea_number, taxonomy_code, credentials, specialty, " +
                "failed_attempts, locked_until, password_changed_at, create_time, update_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                1L, "admin", passwordEncoder.encode("admin123"), "Administrator",
                AesCryptoUtil.encrypt("312-555-0001"), AesCryptoUtil.encrypt("admin@medical.com"), 1, 1, null, null, null, null, null, null, null,
                0, null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );

        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password, real_name, phone, email, gender, status, npi, " +
                "state_license_number, license_state, dea_number, taxonomy_code, credentials, specialty, " +
                "failed_attempts, locked_until, password_changed_at, create_time, update_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                2L, "doctor1", passwordEncoder.encode("doctor123"), "Dr. Sarah Mitchell",
                AesCryptoUtil.encrypt("312-555-0002"), AesCryptoUtil.encrypt("sarah.mitchell@medical.com"), 0, 1,
                "1234567890", AesCryptoUtil.encrypt("036.140000"), "IL", AesCryptoUtil.encrypt("SM1234567"),
                "207Q00000X", "MD", "Family Medicine",
                0, null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private void seedRolesAndMenus() {
        jdbcTemplate.update(
                "INSERT INTO sys_role (id, role_name, role_code, description, status, create_time, update_time) VALUES (?,?,?,?,?,?,?)",
                1L, "Admin", "ADMIN", "System administrator", 1, LocalDateTime.now(), LocalDateTime.now());
        jdbcTemplate.update(
                "INSERT INTO sys_role (id, role_name, role_code, description, status, create_time, update_time) VALUES (?,?,?,?,?,?,?)",
                2L, "Doctor", "DOCTOR", "Doctor", 1, LocalDateTime.now(), LocalDateTime.now());
        jdbcTemplate.update(
                "INSERT INTO sys_role (id, role_name, role_code, description, status, create_time, update_time) VALUES (?,?,?,?,?,?,?)",
                3L, "Patient", "PATIENT", "Patient", 1, LocalDateTime.now(), LocalDateTime.now());

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
            jdbcTemplate.update(
                    "INSERT INTO sys_menu (id, parent_id, menu_name, path, component, icon, type, permission, sort, status, create_time, update_time) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9],
                    LocalDateTime.now(), LocalDateTime.now());
        }

        for (long mid : new long[]{1, 2, 3, 4, 5, 10, 11, 12, 13}) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?,?)", 1L, mid);
        }
        for (long mid : new long[]{1, 10, 11, 12, 13}) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?,?)", 2L, mid);
        }
    }

    private void seedPatients() {
        String patientSql = "INSERT INTO patient (id, mrn, ssn, name, date_of_birth, sex_at_birth, gender_identity, " +
                "race, ethnicity, preferred_language, marital_status, patient_status, primary_care_provider, " +
                "phone_mobile, phone_home, email, address_line1, address_line2, city, state, zip_code, " +
                "emergency_contact_name, emergency_contact_phone, emergency_contact_relation, " +
                "insurance_payer, insurance_member_id, insurance_group_number, medical_history, allergies, " +
                "create_time, update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(patientSql,
                100L, "MRN-10001", AesCryptoUtil.encrypt("123-45-6789"), AesCryptoUtil.encrypt("James Anderson"),
                AesCryptoUtil.encrypt("1998-02-14"), "M", "Male",
                "White", "Not Hispanic or Latino", "en", "Single",
                "active", AesCryptoUtil.encrypt("Dr. Sarah Mitchell"),
                AesCryptoUtil.encrypt("312-555-0101"), null, AesCryptoUtil.encrypt("james.anderson@email.com"),
                AesCryptoUtil.encrypt("1400 S Lake Shore Dr"), null,
                AesCryptoUtil.encrypt("Chicago"), AesCryptoUtil.encrypt("IL"), AesCryptoUtil.encrypt("60605"),
                AesCryptoUtil.encrypt("Mary Anderson"), AesCryptoUtil.encrypt("312-555-0102"), "Spouse",
                AesCryptoUtil.encrypt("Blue Cross Blue Shield"), AesCryptoUtil.encrypt("BCBS-7890123"),
                AesCryptoUtil.encrypt("GRP-88421"),
                AesCryptoUtil.encrypt("Hypertension diagnosed 2024-03; Type 2 Diabetes diagnosed 2025-01"),
                AesCryptoUtil.encrypt("Penicillin; Shellfish"), now, now);

        jdbcTemplate.update(patientSql,
                101L, "MRN-10002", AesCryptoUtil.encrypt("987-65-4321"), AesCryptoUtil.encrypt("Maria Garcia"),
                AesCryptoUtil.encrypt("1991-08-23"), "F", "Female",
                "White", "Hispanic or Latino", "es", "Married",
                "active", AesCryptoUtil.encrypt("Dr. Sarah Mitchell"),
                AesCryptoUtil.encrypt("312-555-0201"), AesCryptoUtil.encrypt("312-555-0202"),
                AesCryptoUtil.encrypt("maria.garcia@email.com"),
                AesCryptoUtil.encrypt("200 E Randolph St"), null,
                AesCryptoUtil.encrypt("Chicago"), AesCryptoUtil.encrypt("IL"), AesCryptoUtil.encrypt("60601"),
                AesCryptoUtil.encrypt("Carlos Garcia"), AesCryptoUtil.encrypt("312-555-0203"), "Spouse",
                AesCryptoUtil.encrypt("Aetna"), AesCryptoUtil.encrypt("AET-4567890"),
                AesCryptoUtil.encrypt("GRP-99234"),
                AesCryptoUtil.encrypt("Iron-deficiency anemia diagnosed 2023; Seasonal allergic asthma"),
                AesCryptoUtil.encrypt("Dust mites; Pollen"), now, now);

        jdbcTemplate.update(patientSql,
                102L, "MRN-10003", null, AesCryptoUtil.encrypt("Robert Chen"),
                AesCryptoUtil.encrypt("1981-05-07"), "M", "Male",
                "Asian", "Not Hispanic or Latino", "en", "Divorced",
                "active", AesCryptoUtil.encrypt("Dr. Sarah Mitchell"),
                AesCryptoUtil.encrypt("312-555-0301"), null, AesCryptoUtil.encrypt("robert.chen@email.com"),
                AesCryptoUtil.encrypt("233 S Wacker Dr"), null,
                AesCryptoUtil.encrypt("Chicago"), AesCryptoUtil.encrypt("IL"), AesCryptoUtil.encrypt("60606"),
                AesCryptoUtil.encrypt("Linda Chen"), AesCryptoUtil.encrypt("312-555-0302"), "Sister",
                AesCryptoUtil.encrypt("UnitedHealthcare"), AesCryptoUtil.encrypt("UHC-3456789"),
                AesCryptoUtil.encrypt("GRP-55109"),
                AesCryptoUtil.encrypt("Lumbar disc herniation 2022; Hyperlipidemia diagnosed 2024"),
                null, now, now);

        // Patient 103 — Female 71yo for CMS125 breast cancer screening
        jdbcTemplate.update(patientSql,
                103L, "MRN-10004", AesCryptoUtil.encrypt("456-78-9012"), AesCryptoUtil.encrypt("Patricia Williams"),
                AesCryptoUtil.encrypt("1955-03-12"), "F", "Female",
                "Black or African American", "Not Hispanic or Latino", "en", "Widowed",
                "active", AesCryptoUtil.encrypt("Dr. Sarah Mitchell"),
                AesCryptoUtil.encrypt("312-555-0401"), null, AesCryptoUtil.encrypt("patricia.williams@email.com"),
                AesCryptoUtil.encrypt("500 N Michigan Ave"), null,
                AesCryptoUtil.encrypt("Chicago"), AesCryptoUtil.encrypt("IL"), AesCryptoUtil.encrypt("60611"),
                AesCryptoUtil.encrypt("David Williams"), AesCryptoUtil.encrypt("312-555-0402"), "Son",
                AesCryptoUtil.encrypt("Medicare"), AesCryptoUtil.encrypt("MCR-9012345"),
                AesCryptoUtil.encrypt("GRP-33901"),
                AesCryptoUtil.encrypt("Hypertension diagnosed 2018; Type 2 Diabetes diagnosed 2015; Osteoarthritis diagnosed 2019"),
                AesCryptoUtil.encrypt("Sulfa drugs"), now, now);
    }

    private void seedPatientAuth() {
        String sql = "INSERT INTO patient_auth (id, patient_id, username, password, status, " +
                     "failed_attempts, locked_until, password_changed_at, create_time, update_time) " +
                     "VALUES (?,?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(sql, 1L, 100L, "patient1", passwordEncoder.encode("patient123"), 1, 0, null, null, now, now);
        jdbcTemplate.update(sql, 2L, 101L, "patient2", passwordEncoder.encode("patient123"), 1, 0, null, null, now, now);
        jdbcTemplate.update(sql, 3L, 102L, "patient3", passwordEncoder.encode("patient123"), 1, 0, null, null, now, now);
        jdbcTemplate.update(sql, 4L, 103L, "patient4", passwordEncoder.encode("patient123"), 1, 0, null, null, now, now);
    }

    private void seedAppointments() {
        String sql = "INSERT INTO appointment (id, patient_id, doctor_id, appointment_time, status, visit_type, " +
                "chief_complaint, department, duration, cpt_code, icd10_codes, description, notes, create_time, update_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(sql,
                200L, 100L, 2L, LocalDateTime.of(2026, 5, 15, 9, 0), 3, "FOLLOW_UP",
                AesCryptoUtil.encrypt("Routine blood pressure check"), "Cardiology", 30, "99213", "I10",
                AesCryptoUtil.encrypt("Follow-up for hypertension"),
                AesCryptoUtil.encrypt("Blood pressure 130/85, stable. Continue current medication."), now, now);

        jdbcTemplate.update(sql,
                201L, 100L, 2L, LocalDateTime.of(2026, 5, 20, 10, 30), 3, "URGENT_CARE",
                AesCryptoUtil.encrypt("Fever and sore throat x3 days"), "Family Medicine", 30, "99203", "J06.9",
                AesCryptoUtil.encrypt("Fever and sore throat"),
                AesCryptoUtil.encrypt("Diagnosed with upper respiratory infection. Prescribed antibiotics."), now, now);

        jdbcTemplate.update(sql,
                202L, 100L, 2L, LocalDateTime.of(2026, 5, 28, 14, 0), 0, "ANNUAL_PHYSICAL",
                AesCryptoUtil.encrypt("Annual diabetes checkup and wellness exam"), "Family Medicine", 45, "99214", "E11.9;Z00.00",
                AesCryptoUtil.encrypt("Annual diabetes checkup"), null, now, now);

        jdbcTemplate.update(sql,
                203L, 100L, 2L, LocalDateTime.of(2026, 6, 15, 8, 30), 0, "FOLLOW_UP",
                AesCryptoUtil.encrypt("Blood sugar recheck post medication adjustment"), "Family Medicine", 30, "99213", "E11.9",
                AesCryptoUtil.encrypt("Blood sugar recheck"),
                AesCryptoUtil.encrypt("Patient needs to fast for 8h before test"), now, now);

        jdbcTemplate.update(sql,
                204L, 101L, 2L, LocalDateTime.of(2026, 5, 22, 11, 0), 3, "CONSULTATION",
                AesCryptoUtil.encrypt("Persistent allergy symptoms, suspected environmental triggers"),
                "Allergy & Immunology", 45, "99244", "J45.30",
                AesCryptoUtil.encrypt("Allergy consultation"),
                AesCryptoUtil.encrypt("Skin prick test positive for dust mites and pollen. Prescribed antihistamines."), now, now);
    }

    private void seedPrescriptions() {
        String rxSql = "INSERT INTO prescription (id, patient_id, doctor_id, diagnosis, icd10_codes, " +
                "prescription_date, prescription_type, rx_status, prescriber_npi, dea_number, " +
                "pharmacy_name, pharmacy_phone, create_time, update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        String itemSql = "INSERT INTO prescription_item (id, prescription_id, drug_name, ndc_code, rxnorm_code, " +
                "specification, dosage, route, frequency, sig, duration, days_supply, quantity, refills, daw, " +
                "unit_price, create_time, update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();

        // Prescription 300
        jdbcTemplate.update(rxSql,
                300L, 100L, 2L, AesCryptoUtil.encrypt("Upper respiratory infection"),
                AesCryptoUtil.encrypt("J06.9"),
                LocalDate.of(2026, 5, 20), "MEDICATION", "completed",
                "1234567890", AesCryptoUtil.encrypt("SM1234567"),
                AesCryptoUtil.encrypt("Walgreens #1234"), AesCryptoUtil.encrypt("312-555-0400"), now, now);

        jdbcTemplate.update(itemSql,
                400L, 300L, AesCryptoUtil.encrypt("Amoxicillin"), "65862-0017-01", "308191", "500mg",
                AesCryptoUtil.encrypt("500mg"),
                "PO", "TID", AesCryptoUtil.encrypt("Take one capsule three times daily with food"),
                7, 7, 21, 0, 0, 0.85, now, now);

        jdbcTemplate.update(itemSql,
                401L, 300L, AesCryptoUtil.encrypt("Ibuprofen"), "49035-0323-50", "5640", "200mg",
                AesCryptoUtil.encrypt("200mg"),
                "PO", "BID", AesCryptoUtil.encrypt("Take one tablet twice daily as needed for pain"),
                3, 3, 6, 0, 0, 0.50, now, now);

        // Prescription 301
        jdbcTemplate.update(rxSql,
                301L, 100L, 2L, AesCryptoUtil.encrypt("Allergic rhinitis; Type 2 diabetes mellitus"),
                AesCryptoUtil.encrypt("J30.9;E11.9"),
                LocalDate.of(2026, 5, 28), "MEDICATION", "active",
                "1234567890", AesCryptoUtil.encrypt("SM1234567"),
                AesCryptoUtil.encrypt("Walgreens #1234"), AesCryptoUtil.encrypt("312-555-0400"), now, now);

        jdbcTemplate.update(itemSql,
                402L, 301L, AesCryptoUtil.encrypt("Cetirizine"), "55111-0183-01", "23642", "10mg",
                AesCryptoUtil.encrypt("10mg"),
                "PO", "QD", AesCryptoUtil.encrypt("Take one tablet daily for allergy symptoms"),
                14, 14, 14, 0, 0, 1.20, now, now);

        jdbcTemplate.update(itemSql,
                403L, 301L, AesCryptoUtil.encrypt("Metformin HCl"), "65862-0109-01", "6809", "850mg",
                AesCryptoUtil.encrypt("850mg"),
                "PO", "BID", AesCryptoUtil.encrypt("Take one tablet twice daily with meals"),
                30, 30, 60, 2, 0, 0.35, now, now);

        // Prescription 302
        jdbcTemplate.update(rxSql,
                302L, 101L, 2L, AesCryptoUtil.encrypt("Seasonal allergic asthma"),
                AesCryptoUtil.encrypt("J45.30"),
                LocalDate.of(2026, 5, 22), "MEDICATION", "active",
                "1234567890", AesCryptoUtil.encrypt("SM1234567"),
                AesCryptoUtil.encrypt("CVS Pharmacy #5678"), AesCryptoUtil.encrypt("312-555-0500"), now, now);

        jdbcTemplate.update(itemSql,
                404L, 302L, AesCryptoUtil.encrypt("Montelukast"), "00006-1715-31", "64479", "5mg",
                AesCryptoUtil.encrypt("5mg"),
                "PO", "QD", AesCryptoUtil.encrypt("Take one tablet daily at bedtime for asthma prevention"),
                30, 30, 30, 2, 0, 2.50, now, now);

        jdbcTemplate.update(itemSql,
                405L, 302L, AesCryptoUtil.encrypt("Albuterol HFA Inhaler"), "59310-0579-22", "435", "100mcg",
                AesCryptoUtil.encrypt("100mcg"),
                "INH", "PRN", AesCryptoUtil.encrypt("Inhale 1-2 puffs every 4-6 hours as needed for wheezing or shortness of breath"),
                30, 30, 1, 0, 1, 25.00, now, now);
    }

    private void seedBills() {
        String sql = "INSERT INTO bill (id, patient_id, prescription_id, appointment_id, bill_type, claim_status, " +
                "total_charge, insurance_adjustment, insurance_payment, patient_responsibility, patient_paid_amount, " +
                "copay_amount, cpt_codes, icd10_codes, place_of_service_code, billing_provider_npi, " +
                "rendering_provider_npi, insurance_payer_name, insurance_claim_number, claim_filing_date, " +
                "adjudication_date, pay_time, payment_method, receipt_number, create_time, update_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(sql,
                500L, 100L, 300L, 201L, "PROFESSIONAL", "PAID",
                20.85, 15.85, 5.00, 0.00, 0.00, 0.00,
                "99203", "J06.9", "11", "1234567890",
                "1234567890", "Blue Cross Blue Shield", AesCryptoUtil.encrypt("BCBS-CLM-80001"),
                LocalDate.of(2026, 5, 21), LocalDate.of(2026, 5, 23),
                LocalDateTime.of(2026, 5, 23, 15, 30), "CREDIT_CARD", "RCPT-10001", now, now);

        jdbcTemplate.update(sql,
                501L, 100L, 301L, 202L, "PROFESSIONAL", "PENDING",
                37.80, 0.00, 0.00, 37.80, 0.00, 0.00,
                "99214", "J30.9;E11.9", "11", "1234567890",
                "1234567890", "Blue Cross Blue Shield", null,
                LocalDate.of(2026, 5, 29), null, null, null, null, now, now);

        jdbcTemplate.update(sql,
                502L, 101L, 302L, 204L, "PROFESSIONAL", "PAID",
                100.00, 72.50, 22.50, 5.00, 5.00, 0.00,
                "99244", "J45.30", "11", "1234567890",
                "1234567890", "Aetna", AesCryptoUtil.encrypt("AET-CLM-45678"),
                LocalDate.of(2026, 5, 23), LocalDate.of(2026, 5, 25),
                LocalDateTime.of(2026, 5, 25, 16, 0), "HSA", "RCPT-10002", now, now);
    }

    private void seedMessages() {
        String sql = "INSERT INTO message (id, sender_id, sender_type, receiver_id, receiver_type, content, is_read, create_time, update_time) " +
                     "VALUES (?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(sql, 600L, 100L, "PATIENT", 2L, "STAFF",
                AesCryptoUtil.encrypt("Hello Dr. Mitchell, my blood pressure has been a bit high the past few days. Should I be concerned?"), 1, now, now);
        jdbcTemplate.update(sql, 601L, 2L, "STAFF", 100L, "PATIENT",
                AesCryptoUtil.encrypt("What are your readings? Have you been taking your medication regularly?"), 1, now, now);
        jdbcTemplate.update(sql, 602L, 100L, "PATIENT", 2L, "STAFF",
                AesCryptoUtil.encrypt("Around 145/90 in the morning. Yes, I'm taking the medication on time."), 1, now, now);
        jdbcTemplate.update(sql, 603L, 2L, "STAFF", 100L, "PATIENT",
                AesCryptoUtil.encrypt("That's mildly elevated. Let's keep monitoring. Cut down on salt and come in for a checkup if it stays above 140/90 for three more days."), 1, now, now);
        jdbcTemplate.update(sql, 604L, 100L, "PATIENT", 2L, "STAFF",
                AesCryptoUtil.encrypt("OK, I'll watch my salt intake. Thank you, doctor."), 1, now, now);
        jdbcTemplate.update(sql, 605L, 2L, "STAFF", 100L, "PATIENT",
                AesCryptoUtil.encrypt("You're welcome. Also, please fast for 8 hours before the June 15 blood sugar test."), 0, now, now);
        jdbcTemplate.update(sql, 606L, 101L, "PATIENT", 2L, "STAFF",
                AesCryptoUtil.encrypt("Dr. Mitchell, my allergy symptoms have gotten much better after using the inhaler you prescribed."), 1, now, now);
        jdbcTemplate.update(sql, 607L, 2L, "STAFF", 101L, "PATIENT",
                AesCryptoUtil.encrypt("That's good to hear, Maria. Keep using it as prescribed. Let me know if symptoms return."), 0, now, now);
    }

    private void seedObservations() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM observation", Integer.class);
        if (count != null && count > 0) return;

        String sql = "INSERT INTO observation (patient_id, loinc_code, loinc_display, obs_value, unit, " +
                "reference_range, abnormal_flag, status, effective_date, create_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime collectionDate = LocalDateTime.of(2026, 5, 28, 8, 30);

        // === Patient 100: Historical trend data (4 dates: Feb → May 2026) ===
        // Feb 15 — poorly controlled diabetes + hypertension
        LocalDateTime d1 = LocalDateTime.of(2026, 2, 15, 9, 0);
        jdbcTemplate.update(sql, 100L, "4548-4", "HbA1c", "8.9", "%", "<5.7", "H", "final", d1, now);
        jdbcTemplate.update(sql, 100L, "2345-7", "Glucose", "155", "mg/dL", "70-99", "H", "final", d1, now);
        jdbcTemplate.update(sql, 100L, "8480-6", "Systolic BP", "146", "mmHg", "<140", "H", "final", d1, now);
        jdbcTemplate.update(sql, 100L, "8462-4", "Diastolic BP", "92", "mmHg", "<90", "H", "final", d1, now);
        jdbcTemplate.update(sql, 100L, "6690-2", "WBC", "8.1", "10*3/uL", "4.0-11.0", "N", "final", d1, now);
        jdbcTemplate.update(sql, 100L, "789-8", "RBC", "4.5", "10*6/uL", "4.5-5.9", "N", "final", d1, now);
        jdbcTemplate.update(sql, 100L, "718-7", "HGB", "13.8", "g/dL", "13.5-17.5", "N", "final", d1, now);

        // Mar 20 — improving after medication adjustment
        LocalDateTime d2 = LocalDateTime.of(2026, 3, 20, 9, 0);
        jdbcTemplate.update(sql, 100L, "4548-4", "HbA1c", "8.3", "%", "<5.7", "H", "final", d2, now);
        jdbcTemplate.update(sql, 100L, "2345-7", "Glucose", "142", "mg/dL", "70-99", "H", "final", d2, now);
        jdbcTemplate.update(sql, 100L, "8480-6", "Systolic BP", "138", "mmHg", "<140", "N", "final", d2, now);
        jdbcTemplate.update(sql, 100L, "8462-4", "Diastolic BP", "88", "mmHg", "<90", "N", "final", d2, now);
        jdbcTemplate.update(sql, 100L, "6690-2", "WBC", "7.5", "10*3/uL", "4.0-11.0", "N", "final", d2, now);
        jdbcTemplate.update(sql, 100L, "789-8", "RBC", "4.7", "10*6/uL", "4.5-5.9", "N", "final", d2, now);
        jdbcTemplate.update(sql, 100L, "718-7", "HGB", "14.0", "g/dL", "13.5-17.5", "N", "final", d2, now);

        // Apr 25 — continued improvement
        LocalDateTime d3 = LocalDateTime.of(2026, 4, 25, 9, 0);
        jdbcTemplate.update(sql, 100L, "4548-4", "HbA1c", "8.1", "%", "<5.7", "H", "final", d3, now);
        jdbcTemplate.update(sql, 100L, "2345-7", "Glucose", "140", "mg/dL", "70-99", "H", "final", d3, now);
        jdbcTemplate.update(sql, 100L, "8480-6", "Systolic BP", "135", "mmHg", "<140", "N", "final", d3, now);
        jdbcTemplate.update(sql, 100L, "8462-4", "Diastolic BP", "86", "mmHg", "<90", "N", "final", d3, now);
        jdbcTemplate.update(sql, 100L, "6690-2", "WBC", "7.0", "10*3/uL", "4.0-11.0", "N", "final", d3, now);
        jdbcTemplate.update(sql, 100L, "789-8", "RBC", "4.9", "10*6/uL", "4.5-5.9", "N", "final", d3, now);
        jdbcTemplate.update(sql, 100L, "718-7", "HGB", "14.2", "g/dL", "13.5-17.5", "N", "final", d3, now);

        // May 28 — near target (existing visit)
        jdbcTemplate.update(sql, 100L, "4548-4", "HbA1c", "7.8", "%", "<5.7", "H", "final", collectionDate, now);
        jdbcTemplate.update(sql, 100L, "2345-7", "Glucose", "135", "mg/dL", "70-99", "H", "final", collectionDate, now);
        jdbcTemplate.update(sql, 100L, "8480-6", "Systolic BP", "132", "mmHg", "<140", "N", "final", collectionDate, now);
        jdbcTemplate.update(sql, 100L, "8462-4", "Diastolic BP", "85", "mmHg", "<90", "N", "final", collectionDate, now);
        jdbcTemplate.update(sql, 100L, "6690-2", "WBC", "7.2", "10*3/uL", "4.0-11.0", "N", "final", collectionDate, now);
        jdbcTemplate.update(sql, 100L, "789-8", "RBC", "4.8", "10*6/uL", "4.5-5.9", "N", "final", collectionDate, now);
        jdbcTemplate.update(sql, 100L, "718-7", "HGB", "14.1", "g/dL", "13.5-17.5", "N", "final", collectionDate, now);
        jdbcTemplate.update(sql, 100L, "4544-3", "HCT", "42.5", "%", "38.0-50.0", "N", "final", collectionDate, now);
        jdbcTemplate.update(sql, 100L, "777-3", "PLT", "245", "10*3/uL", "150-400", "N", "final", collectionDate, now);

        // === Patient 103: Historical trend data (4 dates: Mar → Jun 2026) ===
        LocalDateTime p3d1 = LocalDateTime.of(2026, 3, 1, 10, 0);
        jdbcTemplate.update(sql, 103L, "4548-4", "HbA1c", "9.2", "%", "<5.7", "H", "final", p3d1, now);
        jdbcTemplate.update(sql, 103L, "2345-7", "Glucose", "180", "mg/dL", "70-99", "H", "final", p3d1, now);
        jdbcTemplate.update(sql, 103L, "8480-6", "Systolic BP", "152", "mmHg", "<140", "H", "final", p3d1, now);
        jdbcTemplate.update(sql, 103L, "8462-4", "Diastolic BP", "95", "mmHg", "<90", "H", "final", p3d1, now);

        LocalDateTime p3d2 = LocalDateTime.of(2026, 4, 1, 10, 0);
        jdbcTemplate.update(sql, 103L, "4548-4", "HbA1c", "8.8", "%", "<5.7", "H", "final", p3d2, now);
        jdbcTemplate.update(sql, 103L, "2345-7", "Glucose", "165", "mg/dL", "70-99", "H", "final", p3d2, now);
        jdbcTemplate.update(sql, 103L, "8480-6", "Systolic BP", "148", "mmHg", "<140", "H", "final", p3d2, now);
        jdbcTemplate.update(sql, 103L, "8462-4", "Diastolic BP", "93", "mmHg", "<90", "H", "final", p3d2, now);

        LocalDateTime p3d3 = LocalDateTime.of(2026, 5, 1, 10, 0);
        jdbcTemplate.update(sql, 103L, "4548-4", "HbA1c", "8.6", "%", "<5.7", "H", "final", p3d3, now);
        jdbcTemplate.update(sql, 103L, "2345-7", "Glucose", "155", "mg/dL", "70-99", "H", "final", p3d3, now);
        jdbcTemplate.update(sql, 103L, "8480-6", "Systolic BP", "146", "mmHg", "<140", "H", "final", p3d3, now);
        jdbcTemplate.update(sql, 103L, "8462-4", "Diastolic BP", "91", "mmHg", "<90", "H", "final", p3d3, now);

        LocalDateTime p3d4 = LocalDateTime.of(2026, 6, 1, 10, 0);
        jdbcTemplate.update(sql, 103L, "4548-4", "HbA1c", "8.5", "%", "<5.7", "H", "final", p3d4, now);
        jdbcTemplate.update(sql, 103L, "8480-6", "Systolic BP", "145", "mmHg", "<140", "H", "final", p3d4, now);
        jdbcTemplate.update(sql, 103L, "8462-4", "Diastolic BP", "92", "mmHg", "<90", "H", "final", p3d4, now);

        // Mammogram for female patient 103 (CMS125 numerator)
        LocalDateTime mammoDate = LocalDateTime.of(2026, 3, 15, 9, 0);
        jdbcTemplate.update(sql, 103L, "24606-6", "Mammogram", "Screening mammogram completed", null,
                null, "N", "final", mammoDate, now);

        // === Patient 101: 4 dates × 7 tests (28 results) — pagination dataset (>20/page) ===
        // HbA1c improves across visits: 7.8 → 7.5 → 7.2 → 6.9 (downward trend)
        LocalDateTime[] p101visits = {
                LocalDateTime.of(2026, 6, 10, 8, 30), LocalDateTime.of(2026, 6, 24, 8, 30),
                LocalDateTime.of(2026, 7, 8, 8, 30), LocalDateTime.of(2026, 7, 22, 8, 30),
        };
        String[][] p101rows = {
                {"7.8", "128", "130", "82", "7.1", "4.8", "14.3"},
                {"7.5", "121", "128", "80", "6.9", "4.8", "14.4"},
                {"7.2", "115", "126", "79", "6.6", "4.9", "14.2"},
                {"6.9", "108", "124", "78", "6.4", "4.9", "14.5"},
        };
        String[] p101glucoseFlag = {"H", "H", "N", "N"};
        String[] p101codes = {"4548-4", "2345-7", "8480-6", "8462-4", "6690-2", "789-8", "718-7"};
        String[] p101displays = {"HbA1c", "Glucose", "Systolic BP", "Diastolic BP", "WBC", "RBC", "HGB"};
        String[] p101units = {"%", "mg/dL", "mmHg", "mmHg", "10*3/uL", "10*6/uL", "g/dL"};
        String[] p101ranges = {"<5.7", "70-99", "<140", "<90", "4.0-11.0", "4.5-5.9", "13.5-17.5"};
        for (int v = 0; v < p101rows.length; v++) {
            for (int c = 0; c < p101codes.length; c++) {
                String flag = c == 0 ? "H" : c == 1 ? p101glucoseFlag[v] : "N";
                jdbcTemplate.update(sql, 101L, p101codes[c], p101displays[c], p101rows[v][c],
                        p101units[c], p101ranges[c], flag, "final", p101visits[v], now);
            }
        }

        log.info("Observation seed data: 70 results (4-date trends for p100/p101/p103 + mammogram)");
    }

    private void seedLoincCatalog() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM loinc_catalog", Integer.class);
        if (count != null && count > 0) return;

        String sql = "INSERT INTO loinc_catalog (loinc_code, display, unit, ref_range_low, " +
                "ref_range_high, panel_parent_code) VALUES (?,?,?,?,?,?)";

        // CBC (Complete Blood Count) panel — 8 codes
        jdbcTemplate.update(sql, "6690-2", "WBC", "10*3/uL", "4.0", "11.0", "CBC");
        jdbcTemplate.update(sql, "789-8", "RBC", "10*6/uL", "4.5", "5.9", "CBC");
        jdbcTemplate.update(sql, "718-7", "HGB", "g/dL", "13.5", "17.5", "CBC");
        jdbcTemplate.update(sql, "4544-3", "HCT", "%", "38.0", "50.0", "CBC");
        jdbcTemplate.update(sql, "787-2", "MCV", "fL", "80", "100", "CBC");
        jdbcTemplate.update(sql, "785-6", "MCH", "pg", "27", "33", "CBC");
        jdbcTemplate.update(sql, "786-4", "MCHC", "g/dL", "32", "36", "CBC");
        jdbcTemplate.update(sql, "777-3", "PLT", "10*3/uL", "150", "400", "CBC");

        // BMP (Basic Metabolic Panel) — 8 codes
        jdbcTemplate.update(sql, "2345-7", "Glucose", "mg/dL", "70", "99", "BMP");
        jdbcTemplate.update(sql, "3094-0", "BUN", "mg/dL", "7", "20", "BMP");
        jdbcTemplate.update(sql, "2160-0", "Creatinine", "mg/dL", "0.6", "1.2", "BMP");
        jdbcTemplate.update(sql, "2951-2", "Sodium", "mmol/L", "135", "145", "BMP");
        jdbcTemplate.update(sql, "2823-3", "Potassium", "mmol/L", "3.5", "5.1", "BMP");
        jdbcTemplate.update(sql, "2075-0", "Chloride", "mmol/L", "98", "107", "BMP");
        jdbcTemplate.update(sql, "2028-9", "CO2", "mmol/L", "22", "29", "BMP");
        jdbcTemplate.update(sql, "17861-6", "Calcium", "mg/dL", "8.5", "10.5", "BMP");

        // Lipid Panel — 4 codes
        jdbcTemplate.update(sql, "2093-3", "Cholesterol Total", "mg/dL", null, "200", "LIPID");
        jdbcTemplate.update(sql, "2085-9", "HDL Cholesterol", "mg/dL", "40", null, "LIPID");
        jdbcTemplate.update(sql, "2089-1", "LDL Cholesterol", "mg/dL", null, "130", "LIPID");
        jdbcTemplate.update(sql, "2571-8", "Triglycerides", "mg/dL", null, "150", "LIPID");

        // Diabetes monitoring
        jdbcTemplate.update(sql, "4548-4", "HbA1c", "%", null, "5.7", "DIABETES");

        // Thyroid
        jdbcTemplate.update(sql, "3016-3", "TSH", "mIU/L", "0.4", "4.0", "THYROID");

        // Urinalysis dipstick — 8 codes
        jdbcTemplate.update(sql, "25428-4", "Glucose (Urine)", null, null, null, "UA");
        jdbcTemplate.update(sql, "5770-3", "Bilirubin (Urine)", null, null, null, "UA");
        jdbcTemplate.update(sql, "5797-6", "Ketones (Urine)", null, null, null, "UA");
        jdbcTemplate.update(sql, "5811-5", "Specific Gravity (Urine)", null, "1.005", "1.030", "UA");
        jdbcTemplate.update(sql, "5794-3", "Blood (Urine)", null, null, null, "UA");
        jdbcTemplate.update(sql, "5803-4", "pH (Urine)", null, "5.0", "8.0", "UA");
        jdbcTemplate.update(sql, "5804-2", "Protein (Urine)", null, null, null, "UA");
        jdbcTemplate.update(sql, "5802-6", "Nitrite (Urine)", null, null, null, "UA");

        log.info("LOINC catalog seed data: 29 common lab codes (CBC/BMP/LIPID/DIABETES/THYROID/UA)");
    }

    private void seedPharmacies() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pharmacy_directory", Integer.class);
        if (count != null && count > 0) return;

        String sql = "INSERT INTO pharmacy_directory (npi, name, address_line1, city, state, " +
                "zip_code, phone, supports_epcs) VALUES (?,?,?,?,?,?,?,?)";

        jdbcTemplate.update(sql, "9876543210", "Walgreens #1234",
                "1200 N Clark St", "Chicago", "IL", "60610", "312-555-0400", 1);
        jdbcTemplate.update(sql, "9876543211", "CVS Pharmacy #5678",
                "233 S Wacker Dr", "Chicago", "IL", "60606", "312-555-0500", 1);
        jdbcTemplate.update(sql, "9876543212", "Walmart Pharmacy #9012",
                "4626 W Diversey Ave", "Chicago", "IL", "60639", "773-555-0600", 0);
        jdbcTemplate.update(sql, "9876543213", "Jewel-Osco Pharmacy",
                "1340 S Canal St", "Chicago", "IL", "60607", "312-555-0700", 0);
        jdbcTemplate.update(sql, "9876543214", "Rush University Pharmacy",
                "1653 W Congress Pkwy", "Chicago", "IL", "60612", "312-555-0800", 1);

        log.info("Pharmacy directory seed data: 5 Chicago pharmacies (3 EPCS-capable)");
    }

    private void seedQualityMeasures() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quality_measure", Integer.class);
        if (count != null && count > 0) return;

        String sql = "INSERT INTO quality_measure (cms_id, title, description, " +
                "denominator_query, numerator_query, exclusion_query, report_period_months) " +
                "VALUES (?,?,?,?,?,?,?)";

        jdbcTemplate.update(sql, "CMS122v11", "HbA1c Poor Control (>9%)",
                "Percentage of diabetic patients 18-75 whose most recent HbA1c > 9.0%",
                "SELECT COUNT(DISTINCT p.id) FROM patient p " +
                "WHERE p.is_deleted = 0 AND (LOWER(p.medical_history) LIKE '%diabetes%' OR LOWER(p.medical_history) LIKE '%type 2%')",
                "SELECT COUNT(DISTINCT p.id) FROM patient p " +
                "JOIN observation o ON o.patient_id = p.id AND o.loinc_code = '4548-4' " +
                "WHERE p.is_deleted = 0 AND (LOWER(p.medical_history) LIKE '%diabetes%' OR LOWER(p.medical_history) LIKE '%type 2%') " +
                "AND CAST(o.obs_value AS DOUBLE) <= 9.0",
                null, 12);

        jdbcTemplate.update(sql, "CMS125v11", "Breast Cancer Screening",
                "Percentage of women 50-74 who had a mammogram in the last 27 months",
                "SELECT COUNT(*) FROM patient p " +
                "WHERE p.is_deleted = 0 AND p.sex_at_birth = 'F' " +
                "AND TIMESTAMPDIFF(YEAR, p.date_of_birth, CURRENT_DATE) BETWEEN 50 AND 74",
                "SELECT COUNT(*) FROM patient p " +
                "JOIN observation o ON o.patient_id = p.id AND o.loinc_code = '24606-6' " +
                "WHERE p.is_deleted = 0 AND p.sex_at_birth = 'F' " +
                "AND TIMESTAMPDIFF(YEAR, p.date_of_birth, CURRENT_DATE) BETWEEN 50 AND 74",
                "SELECT COUNT(*) FROM patient p " +
                "WHERE p.is_deleted = 0 AND p.sex_at_birth = 'F' " +
                "AND TIMESTAMPDIFF(YEAR, p.date_of_birth, CURRENT_DATE) BETWEEN 50 AND 74 " +
                "AND p.patient_status = 'deceased'",
                12);

        jdbcTemplate.update(sql, "CMS165v11", "Controlling High Blood Pressure",
                "Percentage of hypertensive patients 18-85 whose most recent BP < 140/90",
                "SELECT COUNT(DISTINCT p.id) FROM patient p " +
                "WHERE p.is_deleted = 0 AND LOWER(p.medical_history) LIKE '%hypertension%'",
                "SELECT COUNT(DISTINCT p.id) FROM patient p " +
                "JOIN observation os ON os.patient_id = p.id AND os.loinc_code = '8480-6' " +
                "JOIN observation od ON od.patient_id = p.id AND od.loinc_code = '8462-4' " +
                "WHERE p.is_deleted = 0 AND LOWER(p.medical_history) LIKE '%hypertension%' " +
                "AND CAST(os.obs_value AS DOUBLE) < 140 AND CAST(od.obs_value AS DOUBLE) < 90",
                null, 12);

        log.info("Quality measure seed data: 3 CMS eCQM definitions (122/125/165)");
    }

    private void seedAllergies() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM allergy_entry", Integer.class);
        if (count != null && count > 0) return;

        String sql = "INSERT INTO allergy_entry (patient_id, allergen, reaction, severity, recorded_by, create_time) " +
                     "VALUES (?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(sql, 100L, AesCryptoUtil.encrypt("Penicillin"), AesCryptoUtil.encrypt("Anaphylaxis"), "SEVERE", 2L, now);
        jdbcTemplate.update(sql, 100L, AesCryptoUtil.encrypt("Shellfish"), AesCryptoUtil.encrypt("Hives, swelling"), "MODERATE", 2L, now);
        jdbcTemplate.update(sql, 101L, AesCryptoUtil.encrypt("Dust mites"), AesCryptoUtil.encrypt("Rhinitis, asthma exacerbation"), "MODERATE", 2L, now);
        jdbcTemplate.update(sql, 101L, AesCryptoUtil.encrypt("Pollen"), AesCryptoUtil.encrypt("Seasonal rhinitis"), "MILD", 2L, now);

        log.info("Allergy seed data: 4 allergy entries for patients 100 and 101");
    }

    private void seedCds() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM drug_interaction", Integer.class);
        if (count != null && count > 0) return;

        String diSql = "INSERT INTO drug_interaction (drug_a_rxnorm, drug_b_rxnorm, severity, " +
                "description, mechanism, recommendation) VALUES (?,?,?,?,?,?)";

        jdbcTemplate.update(diSql, "6809", "5640", "moderate",
                "NSAIDs may reduce antihyperglycemic effect and increase risk of lactic acidosis with Metformin",
                "NSAIDs inhibit organic cation transporters, reducing renal clearance of Metformin",
                "Monitor blood glucose closely. Consider acetaminophen as alternative analgesic.");

        jdbcTemplate.update(diSql, "308191", "5640", "minor",
                "No clinically significant interaction expected between Amoxicillin and Ibuprofen",
                null, "No intervention required.");

        jdbcTemplate.update(diSql, "6809", "308191", "minor",
                "No clinically significant interaction expected between Metformin and Amoxicillin",
                null, "No intervention required.");

        jdbcTemplate.update(diSql, "435", "6809", "minor",
                "Albuterol may cause mild hyperglycemia; monitor blood glucose in diabetic patients",
                "Beta-2 agonists stimulate hepatic glycogenolysis",
                "Monitor blood glucose. Interaction is generally not clinically significant.");

        jdbcTemplate.update(diSql, "6809", "23642", "minor",
                "No clinically significant interaction expected between Metformin and Cetirizine",
                null, "No intervention required.");

        jdbcTemplate.update(diSql, "5640", "435", "minor",
                "NSAIDs may slightly reduce bronchodilator response to beta-agonists",
                "Prostaglandin inhibition may reduce beta-receptor sensitivity",
                "Monitor for reduced bronchodilator efficacy. Interaction is generally mild.");

        jdbcTemplate.update(diSql, "6809", "64479", "minor",
                "No clinically significant interaction expected between Metformin and Montelukast",
                null, "No intervention required.");

        String acSql = "INSERT INTO drug_allergy_class (drug_rxnorm_code, allergy_class, " +
                "cross_reactive_codes) VALUES (?,?,?)";

        jdbcTemplate.update(acSql, "308191", "Penicillin", "308191,308189,308192");
        jdbcTemplate.update(acSql, "5640", "NSAIDs", "5640,5636,5641");
        jdbcTemplate.update(acSql, "23642", "Antihistamines", null);
        jdbcTemplate.update(acSql, "435", "Beta-Agonists", null);
        jdbcTemplate.update(acSql, "6809", "Biguanides", null);
        jdbcTemplate.update(acSql, "64479", "Leukotriene Modifiers", null);
    }

    private void seedVitalSigns() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vital_sign", Integer.class);
        if (count != null && count > 0) return;

        String sql = "INSERT INTO vital_sign (patient_id, recorded_by, recorded_at, systolic_bp, diastolic_bp, " +
                "heart_rate, temperature, respiratory_rate, oxygen_saturation, height_cm, weight_kg, bmi, notes, create_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(sql, 100L, 2L, LocalDateTime.of(2026, 5, 15, 9, 15),
                128, 82, 72, 36.6, 16, 98, 175.3, 85.0, 27.6,
                AesCryptoUtil.encrypt("Routine checkup; BP slightly elevated"), now);
        jdbcTemplate.update(sql, 100L, 2L, LocalDateTime.of(2026, 7, 10, 10, 0),
                134, 86, 76, 36.8, 18, 97, 175.3, 84.5, 27.4,
                AesCryptoUtil.encrypt("Follow-up; BP trending up, counseled on diet"), now);
        jdbcTemplate.update(sql, 101L, 2L, LocalDateTime.of(2026, 5, 23, 11, 0),
                118, 76, 68, 36.5, 14, 99, 162.0, 58.0, 22.1,
                AesCryptoUtil.encrypt("Annual physical; all vitals within normal range"), now);
        jdbcTemplate.update(sql, 103L, 2L, LocalDateTime.of(2026, 5, 20, 10, 30),
                142, 90, 80, 36.9, 20, 95, 165.0, 92.0, 33.8,
                AesCryptoUtil.encrypt("Hypertension follow-up; BP elevated, adjust meds"), now);
    }

    private void seedProblems() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM problem", Integer.class);
        if (count != null && count > 0) return;

        String sql = "INSERT INTO problem (patient_id, snomed_code, snomed_display, icd10_code, " +
                "onset_date, status, severity, recorded_by, notes, create_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(sql, 100L, "38341003", "Essential hypertension", "I10",
                LocalDate.of(2024, 3, 1), "ACTIVE", "MODERATE", 2L,
                AesCryptoUtil.encrypt("Diagnosed during routine physical; lifestyle modifications recommended"), now);
        jdbcTemplate.update(sql, 100L, "44054006", "Type 2 diabetes mellitus", "E11.9",
                LocalDate.of(2025, 1, 15), "ACTIVE", "MILD", 2L,
                AesCryptoUtil.encrypt("HbA1c 7.1%; managed with Metformin 500mg BID"), now);
        jdbcTemplate.update(sql, 101L, "195967001", "Iron deficiency anemia", "D50.9",
                LocalDate.of(2023, 6, 1), "ACTIVE", "MILD", 2L,
                AesCryptoUtil.encrypt("Responding to oral iron supplementation"), now);
        jdbcTemplate.update(sql, 101L, "195949008", "Seasonal allergic asthma", "J45.30",
                LocalDate.of(2022, 4, 1), "ACTIVE", "MODERATE", 2L,
                AesCryptoUtil.encrypt("Triggered by dust mites and pollen; managed with Albuterol PRN"), now);
        jdbcTemplate.update(sql, 102L, "202796002", "Lumbar disc herniation", "M51.26",
                LocalDate.of(2022, 8, 1), "ACTIVE", "MODERATE", 2L,
                AesCryptoUtil.encrypt("L4-L5 herniation; physical therapy ongoing"), now);
        jdbcTemplate.update(sql, 102L, "55822004", "Hyperlipidemia", "E78.5",
                LocalDate.of(2024, 3, 1), "ACTIVE", "MILD", 2L,
                AesCryptoUtil.encrypt("LDL elevated; managed with atorvastatin 10mg daily"), now);
        jdbcTemplate.update(sql, 103L, "38341003", "Essential hypertension", "I10",
                LocalDate.of(2018, 2, 1), "ACTIVE", "SEVERE", 2L,
                AesCryptoUtil.encrypt("Long-standing hypertension; on combination therapy"), now);
        jdbcTemplate.update(sql, 103L, "44054006", "Type 2 diabetes mellitus", "E11.9",
                LocalDate.of(2015, 6, 1), "ACTIVE", "MODERATE", 2L,
                AesCryptoUtil.encrypt("HbA1c 8.2%; insulin-dependent"), now);
    }

    private void seedImmunizations() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM immunization", Integer.class);
        if (count != null && count > 0) return;

        String sql = "INSERT INTO immunization (patient_id, vaccine_name, cvx_code, administration_date, " +
                "lot_number, manufacturer, dose_number, site, route, status, administered_by, notes, create_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(sql, 100L, "Influenza (seasonal)", "141",
                LocalDate.of(2025, 10, 15), "FL2025-001", "Sanofi Pasteur",
                "Annual", "left arm", "intramuscular", "completed", 2L,
                AesCryptoUtil.encrypt("2025-2026 season quadrivalent"), now);
        jdbcTemplate.update(sql, 100L, "COVID-19 (mRNA)", "208",
                LocalDate.of(2025, 9, 1), "CV2025-089", "Pfizer-BioNTech",
                "Booster", "left arm", "intramuscular", "completed", 2L,
                AesCryptoUtil.encrypt("2025 updated formulation"), now);
        jdbcTemplate.update(sql, 100L, "Tdap", "115",
                LocalDate.of(2023, 6, 10), "TD2023-045", "GlaxoSmithKline",
                "Booster", "right arm", "intramuscular", "completed", 2L, null, now);
        jdbcTemplate.update(sql, 101L, "Influenza (seasonal)", "141",
                LocalDate.of(2025, 10, 20), "FL2025-002", "Sanofi Pasteur",
                "Annual", "left arm", "intramuscular", "completed", 2L,
                AesCryptoUtil.encrypt("2025-2026 season quadrivalent"), now);
        jdbcTemplate.update(sql, 101L, "COVID-19 (mRNA)", "208",
                LocalDate.of(2025, 9, 15), "CV2025-092", "Moderna",
                "Booster", "right arm", "intramuscular", "completed", 2L,
                AesCryptoUtil.encrypt("2025 updated formulation"), now);
        jdbcTemplate.update(sql, 101L, "Shingles (recombinant)", "187",
                LocalDate.of(2024, 3, 1), "SH2024-011", "GlaxoSmithKline",
                "1st dose", "left arm", "intramuscular", "completed", 2L,
                AesCryptoUtil.encrypt("Shingrix dose 1 of 2"), now);
        jdbcTemplate.update(sql, 103L, "Influenza (seasonal)", "141",
                LocalDate.of(2025, 11, 5), "FL2025-015", "Sanofi Pasteur",
                "Annual", "left arm", "intramuscular", "completed", 2L,
                AesCryptoUtil.encrypt("High-dose for 65+"), now);
        jdbcTemplate.update(sql, 103L, "Pneumococcal conjugate (PCV20)", "215",
                LocalDate.of(2024, 1, 15), "PC2024-003", "Pfizer",
                null, "right arm", "intramuscular", "completed", 2L,
                AesCryptoUtil.encrypt("Prevnar 20"), now);
        jdbcTemplate.update(sql, 103L, "COVID-19 (mRNA)", "208",
                LocalDate.of(2025, 9, 10), "CV2025-101", "Pfizer-BioNTech",
                "Booster", "left arm", "intramuscular", "completed", 2L,
                AesCryptoUtil.encrypt("2025 updated formulation"), now);
    }

    private void seedReferrals() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM referral", Integer.class);
        if (count != null && count > 0) return;

        String sql = "INSERT INTO referral (patient_id, referring_doctor_id, specialist_name, specialist_npi, " +
                "specialty, diagnosis, reason, urgency, status, referral_date, appointment_date, notes, create_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(sql, 100L, 2L, "Dr. Emily Chen", "9876543210",
                "Ophthalmology", AesCryptoUtil.encrypt("Diabetic retinopathy screening"),
                AesCryptoUtil.encrypt("Annual eye exam for diabetic patient"),
                "ROUTINE", "SCHEDULED", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 15),
                AesCryptoUtil.encrypt("Patient scheduled at Northwestern Ophthalmology"), now);
        jdbcTemplate.update(sql, 100L, 2L, "Dr. Robert Park", "8765432109",
                "Cardiology", AesCryptoUtil.encrypt("Essential hypertension"),
                AesCryptoUtil.encrypt("BP trending up despite medication; evaluate for secondary causes"),
                "ROUTINE", "PENDING", LocalDate.of(2026, 7, 10), null,
                AesCryptoUtil.encrypt("Referral faxed 7/10; awaiting scheduling"), now);
        jdbcTemplate.update(sql, 102L, 2L, "Dr. Lisa Zhang", "7654321098",
                "Orthopedic Surgery", AesCryptoUtil.encrypt("Lumbar disc herniation"),
                AesCryptoUtil.encrypt("Persistent radiculopathy; surgical consultation"),
                "URGENT", "PENDING", LocalDate.of(2026, 6, 28), null,
                AesCryptoUtil.encrypt("MRI shows L4-L5 herniation with nerve root compression"), now);
        jdbcTemplate.update(sql, 103L, 2L, "Dr. James Miller", "6543210987",
                "Endocrinology", AesCryptoUtil.encrypt("Type 2 diabetes mellitus"),
                AesCryptoUtil.encrypt("HbA1c 8.2% despite insulin; optimize regimen"),
                "ROUTINE", "COMPLETED", LocalDate.of(2026, 4, 15), LocalDate.of(2026, 5, 20),
                AesCryptoUtil.encrypt("Insulin adjusted; follow-up in 3 months"), now);
    }

    private void seedCharges() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM charge", Integer.class);
        if (count != null && count > 0) return;

        String sql = "INSERT INTO charge (patient_id, appointment_id, doctor_id, cpt_codes, icd10_codes, " +
                "units, charge_amount, visit_type, status, notes, create_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(sql, 100L, 201L, 2L, "99213", "I10;E11.9",
                1, 20.85, "FOLLOW_UP", "DRAFT",
                AesCryptoUtil.encrypt("Hypertension + diabetes follow-up; captured from appointment #201"), now);
        jdbcTemplate.update(sql, 101L, 204L, 2L, "99214", "J45.30",
                1, 100.00, "FOLLOW_UP", "DRAFT",
                AesCryptoUtil.encrypt("Asthma follow-up with pulmonary function assessment"), now);
    }

    private void seedCarePlans() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM care_plan", Integer.class);
        if (count != null && count > 0) return;

        String sql = "INSERT INTO care_plan (patient_id, title, goal, interventions, start_date, target_date, status, created_by, notes, create_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(sql, 100L, "Hypertension Management", AesCryptoUtil.encrypt("Maintain BP < 130/80 mmHg"),
                AesCryptoUtil.encrypt("Daily BP monitoring; Low-sodium diet (<2g/day); Lisinopril 10mg daily; Walking 30min 5x/week"),
                LocalDate.of(2024, 3, 1), LocalDate.of(2026, 12, 31), "ACTIVE", 2L,
                AesCryptoUtil.encrypt("BP improving; continue current regimen"), now);
        jdbcTemplate.update(sql, 100L, "Diabetes Type 2 Management", AesCryptoUtil.encrypt("HbA1c < 7.0%"),
                AesCryptoUtil.encrypt("Blood glucose monitoring BID; Metformin 500mg BID; Quarterly HbA1c; Annual eye exam; Foot exam at each visit"),
                LocalDate.of(2025, 1, 15), LocalDate.of(2026, 12, 31), "ACTIVE", 2L,
                AesCryptoUtil.encrypt("HbA1c 7.1% at last check; titrating Metformin"), now);
        jdbcTemplate.update(sql, 103L, "Diabetes Type 2 Management", AesCryptoUtil.encrypt("HbA1c < 7.5%"),
                AesCryptoUtil.encrypt("Insulin therapy as prescribed; Carb counting; Weekly glucose log review; Podiatry referral"),
                LocalDate.of(2020, 6, 1), LocalDate.of(2026, 6, 1), "COMPLETED", 2L,
                AesCryptoUtil.encrypt("Transitioned to new regimen; new plan created"), now);
    }

    private void seedPriorAuths() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM prior_auth", Integer.class);
        if (count != null && count > 0) return;

        String sql = "INSERT INTO prior_auth (patient_id, auth_type, item_name, item_code, insurance_payer, status, requested_at, requested_by, notes, create_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(sql, 100L, "MEDICATION", "Lisinopril 20mg", "314076", "Blue Cross Blue Shield",
                "PENDING", LocalDate.of(2026, 7, 10), 2L,
                AesCryptoUtil.encrypt("Dose increase from 10mg requested"), now);
        jdbcTemplate.update(sql, 102L, "PROCEDURE", "Lumbar MRI", "72148", "UnitedHealthcare",
                "PENDING", LocalDate.of(2026, 7, 5), 2L,
                AesCryptoUtil.encrypt("Follow-up imaging for disc herniation; conservative treatment x6 months completed"), now);
    }

    private void seedFormularyEntries() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM formulary_entry", Integer.class);
        if (count != null && count > 0) return;

        String sql = "INSERT INTO formulary_entry (rxnorm_code, drug_name, insurance_payer, tier, prior_auth_required, step_therapy_required, alternatives) " +
                "VALUES (?,?,?,?,?,?,?)";
        jdbcTemplate.update(sql, "6809", "Metformin HCl", "Blue Cross Blue Shield", "1", 0, 0, null);
        jdbcTemplate.update(sql, "308191", "Amoxicillin", "Blue Cross Blue Shield", "1", 0, 0, null);
        jdbcTemplate.update(sql, "5640", "Ibuprofen", "Blue Cross Blue Shield", "1", 0, 0, null);
        jdbcTemplate.update(sql, "435", "Albuterol", "Blue Cross Blue Shield", "2", 0, 0, "Levalbuterol");
        jdbcTemplate.update(sql, "314076", "Lisinopril", "Blue Cross Blue Shield", "1", 0, 0, null);
        jdbcTemplate.update(sql, "23642", "Cetirizine", "Blue Cross Blue Shield", "2", 0, 1, "Loratadine;Fexofenadine");
        jdbcTemplate.update(sql, "6809", "Metformin HCl", "Aetna", "1", 0, 0, null);
        jdbcTemplate.update(sql, "314076", "Lisinopril", "Aetna", "2", 0, 0, null);
        jdbcTemplate.update(sql, "435", "Albuterol", "UnitedHealthcare", "1", 0, 0, null);
        jdbcTemplate.update(sql, "64479", "Montelukast", "UnitedHealthcare", "3", 1, 1, "Zafirlukast");
    }
}

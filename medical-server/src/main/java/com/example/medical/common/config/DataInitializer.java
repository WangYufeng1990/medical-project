package com.example.medical.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
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

        log.info("Seed data initialized (admin/admin123, doctor1/doctor123, patient1/patient123 using BCrypt)");
    }

    private void seedUsers() {
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password, real_name, phone, email, gender, status, npi, " +
                "state_license_number, license_state, dea_number, taxonomy_code, credentials, specialty, create_time, update_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                1L, "admin", passwordEncoder.encode("admin123"), "Administrator",
                AesCryptoUtil.encrypt("312-555-0001"), "admin@medical.com", 1, 1, null, null, null, null, null, null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );

        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password, real_name, phone, email, gender, status, npi, " +
                "state_license_number, license_state, dea_number, taxonomy_code, credentials, specialty, create_time, update_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                2L, "doctor1", passwordEncoder.encode("doctor123"), "Dr. Sarah Mitchell",
                AesCryptoUtil.encrypt("312-555-0002"), "sarah.mitchell@medical.com", 0, 1,
                "1234567890", AesCryptoUtil.encrypt("036.140000"), "IL", AesCryptoUtil.encrypt("SM1234567"),
                "207Q00000X", "MD", "Family Medicine",
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
                LocalDate.of(1998, 2, 14), "M", "Male",
                "White", "Not Hispanic or Latino", "en", "Single",
                "active", "Dr. Sarah Mitchell",
                AesCryptoUtil.encrypt("312-555-0101"), null, "james.anderson@email.com",
                "1400 S Lake Shore Dr", null, "Chicago", "IL", "60605",
                "Mary Anderson", "312-555-0102", "Spouse",
                "Blue Cross Blue Shield", AesCryptoUtil.encrypt("BCBS-7890123"), "GRP-88421",
                "Hypertension diagnosed 2024-03; Type 2 Diabetes diagnosed 2025-01",
                "Penicillin; Shellfish", now, now);

        jdbcTemplate.update(patientSql,
                101L, "MRN-10002", AesCryptoUtil.encrypt("987-65-4321"), AesCryptoUtil.encrypt("Maria Garcia"),
                LocalDate.of(1991, 8, 23), "F", "Female",
                "White", "Hispanic or Latino", "es", "Married",
                "active", "Dr. Sarah Mitchell",
                AesCryptoUtil.encrypt("312-555-0201"), AesCryptoUtil.encrypt("312-555-0202"), "maria.garcia@email.com",
                "200 E Randolph St", null, "Chicago", "IL", "60601",
                "Carlos Garcia", "312-555-0203", "Spouse",
                "Aetna", AesCryptoUtil.encrypt("AET-4567890"), "GRP-99234",
                "Iron-deficiency anemia diagnosed 2023; Seasonal allergic asthma",
                "Dust mites; Pollen", now, now);

        jdbcTemplate.update(patientSql,
                102L, "MRN-10003", null, AesCryptoUtil.encrypt("Robert Chen"),
                LocalDate.of(1981, 5, 7), "M", "Male",
                "Asian", "Not Hispanic or Latino", "en", "Divorced",
                "active", "Dr. Sarah Mitchell",
                AesCryptoUtil.encrypt("312-555-0301"), null, "robert.chen@email.com",
                "233 S Wacker Dr", null, "Chicago", "IL", "60606",
                "Linda Chen", "312-555-0302", "Sister",
                "UnitedHealthcare", AesCryptoUtil.encrypt("UHC-3456789"), "GRP-55109",
                "Lumbar disc herniation 2022; Hyperlipidemia diagnosed 2024",
                null, now, now);
    }

    private void seedPatientAuth() {
        String sql = "INSERT INTO patient_auth (id, patient_id, username, password, status, failed_attempts, create_time, update_time) " +
                     "VALUES (?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(sql, 1L, 100L, "patient1", passwordEncoder.encode("patient123"), 1, 0, now, now);
        jdbcTemplate.update(sql, 2L, 101L, "patient2", passwordEncoder.encode("patient123"), 1, 0, now, now);
        jdbcTemplate.update(sql, 3L, 102L, "patient3", passwordEncoder.encode("patient123"), 1, 0, now, now);
    }

    private void seedAppointments() {
        String sql = "INSERT INTO appointment (id, patient_id, doctor_id, appointment_time, status, visit_type, " +
                "chief_complaint, department, duration, cpt_code, description, notes, create_time, update_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(sql,
                200L, 100L, 2L, LocalDateTime.of(2026, 5, 15, 9, 0), 3, "FOLLOW_UP",
                "Routine blood pressure check", "Cardiology", 30, "99213",
                "Follow-up for hypertension",
                "Blood pressure 130/85, stable. Continue current medication.", now, now);

        jdbcTemplate.update(sql,
                201L, 100L, 2L, LocalDateTime.of(2026, 5, 20, 10, 30), 3, "URGENT_CARE",
                "Fever and sore throat x3 days", "Family Medicine", 30, "99203",
                "Fever and sore throat",
                "Diagnosed with upper respiratory infection. Prescribed antibiotics.", now, now);

        jdbcTemplate.update(sql,
                202L, 100L, 2L, LocalDateTime.of(2026, 5, 28, 14, 0), 0, "ANNUAL_PHYSICAL",
                "Annual diabetes checkup and wellness exam", "Family Medicine", 45, "99214",
                "Annual diabetes checkup", null, now, now);

        jdbcTemplate.update(sql,
                203L, 100L, 2L, LocalDateTime.of(2026, 6, 15, 8, 30), 0, "FOLLOW_UP",
                "Blood sugar recheck post medication adjustment", "Family Medicine", 30, "99213",
                "Blood sugar recheck",
                "Patient needs to fast for 8h before test", now, now);

        jdbcTemplate.update(sql,
                204L, 101L, 2L, LocalDateTime.of(2026, 5, 22, 11, 0), 3, "CONSULTATION",
                "Persistent allergy symptoms, suspected environmental triggers",
                "Allergy & Immunology", 45, "99244",
                "Allergy consultation",
                "Skin prick test positive for dust mites and pollen. Prescribed antihistamines.", now, now);
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
                300L, 100L, 2L, "Upper respiratory infection", "J06.9",
                LocalDate.of(2026, 5, 20), "MEDICATION", "completed",
                "1234567890", AesCryptoUtil.encrypt("SM1234567"), "Walgreens #1234", "312-555-0400", now, now);

        jdbcTemplate.update(itemSql,
                400L, 300L, "Amoxicillin", "65862-0017-01", "308191", "500mg", "500mg",
                "PO", "TID", "Take one capsule three times daily with food",
                7, 7, 21, 0, 0, 0.85, now, now);

        jdbcTemplate.update(itemSql,
                401L, 300L, "Ibuprofen", "49035-0323-50", "5640", "200mg", "200mg",
                "PO", "BID", "Take one tablet twice daily as needed for pain",
                3, 3, 6, 0, 0, 0.50, now, now);

        // Prescription 301
        jdbcTemplate.update(rxSql,
                301L, 100L, 2L, "Allergic rhinitis; Type 2 diabetes mellitus", "J30.9;E11.9",
                LocalDate.of(2026, 5, 28), "MEDICATION", "active",
                "1234567890", AesCryptoUtil.encrypt("SM1234567"), "Walgreens #1234", "312-555-0400", now, now);

        jdbcTemplate.update(itemSql,
                402L, 301L, "Cetirizine", "55111-0183-01", "23642", "10mg", "10mg",
                "PO", "QD", "Take one tablet daily for allergy symptoms",
                14, 14, 14, 0, 0, 1.20, now, now);

        jdbcTemplate.update(itemSql,
                403L, 301L, "Metformin HCl", "65862-0109-01", "6809", "850mg", "850mg",
                "PO", "BID", "Take one tablet twice daily with meals",
                30, 30, 60, 2, 0, 0.35, now, now);

        // Prescription 302
        jdbcTemplate.update(rxSql,
                302L, 101L, 2L, "Seasonal allergic asthma", "J45.30",
                LocalDate.of(2026, 5, 22), "MEDICATION", "active",
                "1234567890", AesCryptoUtil.encrypt("SM1234567"), "CVS Pharmacy #5678", "312-555-0500", now, now);

        jdbcTemplate.update(itemSql,
                404L, 302L, "Montelukast", "00006-1715-31", "64479", "5mg", "5mg",
                "PO", "QD", "Take one tablet daily at bedtime for asthma prevention",
                30, 30, 30, 2, 0, 2.50, now, now);

        jdbcTemplate.update(itemSql,
                405L, 302L, "Albuterol HFA Inhaler", "59310-0579-22", "435", "100mcg", "100mcg",
                "INH", "PRN", "Inhale 1-2 puffs every 4-6 hours as needed for wheezing or shortness of breath",
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
        String sql = "INSERT INTO message (id, sender_id, receiver_id, content, is_read, create_time, update_time) " +
                     "VALUES (?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(sql, 600L, 100L, 2L,
                AesCryptoUtil.encrypt("Hello Dr. Mitchell, my blood pressure has been a bit high the past few days. Should I be concerned?"), 1, now, now);
        jdbcTemplate.update(sql, 601L, 2L, 100L,
                AesCryptoUtil.encrypt("What are your readings? Have you been taking your medication regularly?"), 1, now, now);
        jdbcTemplate.update(sql, 602L, 100L, 2L,
                AesCryptoUtil.encrypt("Around 145/90 in the morning. Yes, I'm taking the medication on time."), 1, now, now);
        jdbcTemplate.update(sql, 603L, 2L, 100L,
                AesCryptoUtil.encrypt("That's mildly elevated. Let's keep monitoring. Cut down on salt and come in for a checkup if it stays above 140/90 for three more days."), 1, now, now);
        jdbcTemplate.update(sql, 604L, 100L, 2L,
                AesCryptoUtil.encrypt("OK, I'll watch my salt intake. Thank you, doctor."), 1, now, now);
        jdbcTemplate.update(sql, 605L, 2L, 100L,
                AesCryptoUtil.encrypt("You're welcome. Also, please fast for 8 hours before the June 15 blood sugar test."), 0, now, now);
        jdbcTemplate.update(sql, 606L, 101L, 2L,
                AesCryptoUtil.encrypt("Dr. Mitchell, my allergy symptoms have gotten much better after using the inhaler you prescribed."), 1, now, now);
        jdbcTemplate.update(sql, 607L, 2L, 101L,
                AesCryptoUtil.encrypt("That's good to hear, Maria. Keep using it as prescribed. Let me know if symptoms return."), 0, now, now);
    }
}

-- Medical Management System - Schema & Seed Data

CREATE DATABASE IF NOT EXISTS medical_dev
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE medical_dev;

-- ============================================================
-- System Tables
-- ============================================================

CREATE TABLE sys_user (
    id BIGINT NOT NULL,
    username VARCHAR(200) NOT NULL,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(50) DEFAULT NULL,
    phone VARCHAR(200) DEFAULT NULL,
    email VARCHAR(100) DEFAULT NULL,
    gender TINYINT DEFAULT 0, -- 0: unknown, 1: male, 2: female
    status TINYINT DEFAULT 1,  -- 0: disabled, 1: enabled
    avatar VARCHAR(255) DEFAULT NULL,
    npi VARCHAR(10) DEFAULT NULL,
    state_license_number VARCHAR(200) DEFAULT NULL, -- AES encrypted
    license_state CHAR(2) DEFAULT NULL,
    dea_number VARCHAR(200) DEFAULT NULL,           -- AES encrypted
    taxonomy_code VARCHAR(10) DEFAULT NULL,
    credentials VARCHAR(20) DEFAULT NULL,
    specialty VARCHAR(100) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_npi (npi)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role (
    id BIGINT NOT NULL,
    role_name VARCHAR(200) NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    description VARCHAR(200) DEFAULT NULL,
    status TINYINT DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_menu (
    id BIGINT NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    menu_name VARCHAR(200) NOT NULL,
    path VARCHAR(200) DEFAULT NULL,
    component VARCHAR(200) DEFAULT NULL,
    icon VARCHAR(50) DEFAULT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'MENU', -- DIRECTORY, MENU, BUTTON
    permission VARCHAR(100) DEFAULT NULL,
    sort INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Business Tables
-- ============================================================

CREATE TABLE patient (
    id BIGINT NOT NULL,
    mrn VARCHAR(50) DEFAULT NULL,
    ssn VARCHAR(200) DEFAULT NULL,      -- AES encrypted
    name VARCHAR(200) NOT NULL,
    date_of_birth DATE DEFAULT NULL,
    sex_at_birth CHAR(1) DEFAULT NULL,
    gender_identity VARCHAR(50) DEFAULT NULL,
    race VARCHAR(100) DEFAULT NULL,
    ethnicity VARCHAR(50) DEFAULT NULL,
    preferred_language VARCHAR(10) DEFAULT NULL,
    marital_status VARCHAR(20) DEFAULT NULL,
    patient_status VARCHAR(20) DEFAULT 'active',
    primary_care_provider VARCHAR(100) DEFAULT NULL,
    phone_mobile VARCHAR(200) DEFAULT NULL, -- AES encrypted
    phone_home VARCHAR(200) DEFAULT NULL,   -- AES encrypted
    phone_work VARCHAR(20) DEFAULT NULL,
    email VARCHAR(100) DEFAULT NULL,
    address_line1 VARCHAR(100) DEFAULT NULL,
    address_line2 VARCHAR(100) DEFAULT NULL,
    city VARCHAR(50) DEFAULT NULL,
    state CHAR(2) DEFAULT NULL,
    zip_code VARCHAR(10) DEFAULT NULL,
    emergency_contact_name VARCHAR(100) DEFAULT NULL,
    emergency_contact_phone VARCHAR(200) DEFAULT NULL,
    emergency_contact_relation VARCHAR(50) DEFAULT NULL,
    insurance_payer VARCHAR(100) DEFAULT NULL,
    insurance_member_id VARCHAR(200) DEFAULT NULL, -- AES encrypted
    insurance_group_number VARCHAR(50) DEFAULT NULL,
    medical_history TEXT DEFAULT NULL,
    allergies VARCHAR(500) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mrn (mrn)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE appointment (
    id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    appointment_time DATETIME NOT NULL,
    status INT DEFAULT 0,  -- 0:scheduled, 1:arrived, 2:cancelled, 3:completed, 4:no-show, 5:rescheduled, 6:in-progress
    visit_type VARCHAR(30) DEFAULT NULL,
    chief_complaint VARCHAR(500) DEFAULT NULL,
    department VARCHAR(50) DEFAULT NULL,
    duration INT DEFAULT NULL,
    cpt_code VARCHAR(10) DEFAULT NULL,
    check_in_time DATETIME DEFAULT NULL,
    check_out_time DATETIME DEFAULT NULL,
    description VARCHAR(500) DEFAULT NULL,
    notes TEXT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_appointment_patient_id (patient_id),
    INDEX idx_appointment_doctor_id (doctor_id),
    INDEX idx_appointment_time (appointment_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE prescription (
    id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    diagnosis VARCHAR(500) NOT NULL,
    icd10_codes VARCHAR(500) DEFAULT NULL,
    prescription_date DATE NOT NULL,
    prescription_type VARCHAR(20) DEFAULT 'MEDICATION',
    rx_status VARCHAR(20) DEFAULT 'active',
    prescriber_npi VARCHAR(10) DEFAULT NULL,
    dea_number VARCHAR(200) DEFAULT NULL,   -- AES encrypted
    controlled_schedule VARCHAR(5) DEFAULT NULL,
    pharmacy_name VARCHAR(100) DEFAULT NULL,
    pharmacy_phone VARCHAR(200) DEFAULT NULL,
    pharmacy_npi VARCHAR(10) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_prescription_patient_id (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE prescription_item (
    id BIGINT NOT NULL,
    prescription_id BIGINT NOT NULL,
    drug_name VARCHAR(100) NOT NULL,
    ndc_code VARCHAR(20) DEFAULT NULL,
    rxnorm_code VARCHAR(20) DEFAULT NULL,
    specification VARCHAR(50) DEFAULT NULL,
    dosage VARCHAR(50) NOT NULL,
    route VARCHAR(10) DEFAULT NULL,
    frequency VARCHAR(50) NOT NULL,
    sig VARCHAR(200) DEFAULT NULL,
    duration INT DEFAULT 0,         -- days
    days_supply INT DEFAULT NULL,
    quantity INT DEFAULT 1,
    refills INT DEFAULT 0,
    daw INT DEFAULT 0,              -- 0: substitution allowed, 1: dispense as written
    unit_price DECIMAL(10,2) DEFAULT 0.00,
    notes VARCHAR(200) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_prescription_id (prescription_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bill (
    id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    prescription_id BIGINT DEFAULT NULL,
    appointment_id BIGINT DEFAULT NULL,
    bill_type VARCHAR(20) DEFAULT 'PROFESSIONAL',
    claim_status VARCHAR(20) DEFAULT 'DRAFT',
    total_charge DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    insurance_adjustment DECIMAL(10,2) DEFAULT 0.00,
    insurance_payment DECIMAL(10,2) DEFAULT 0.00,
    patient_responsibility DECIMAL(10,2) DEFAULT 0.00,
    patient_paid_amount DECIMAL(10,2) DEFAULT 0.00,
    copay_amount DECIMAL(10,2) DEFAULT 0.00,
    cpt_codes VARCHAR(200) DEFAULT NULL,
    icd10_codes VARCHAR(500) DEFAULT NULL,
    place_of_service_code VARCHAR(5) DEFAULT NULL,
    billing_provider_npi VARCHAR(10) DEFAULT NULL,
    rendering_provider_npi VARCHAR(10) DEFAULT NULL,
    insurance_payer_name VARCHAR(100) DEFAULT NULL,
    insurance_claim_number VARCHAR(200) DEFAULT NULL, -- AES encrypted
    prior_authorization_number VARCHAR(50) DEFAULT NULL,
    claim_filing_date DATE DEFAULT NULL,
    adjudication_date DATE DEFAULT NULL,
    pay_time DATETIME DEFAULT NULL,
    payment_method VARCHAR(20) DEFAULT NULL,
    receipt_number VARCHAR(50) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_bill_patient_id (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE patient_auth (
    id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    username VARCHAR(200) NOT NULL,
    password VARCHAR(255) NOT NULL,
    status TINYINT DEFAULT 1,
    last_login_time DATETIME DEFAULT NULL,
    failed_attempts INT DEFAULT 0,
    locked_until DATETIME DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_patient_auth_username (username),
    UNIQUE KEY uk_patient_auth_patient_id (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Seed Data
-- ============================================================

-- Admin user (password: admin123, BCrypt encoded)
INSERT INTO sys_user (id, username, password, real_name, phone, email, gender, status, npi, state_license_number, license_state, dea_number, taxonomy_code, credentials, specialty)
VALUES (1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Administrator', '312-555-0001', 'admin@medical.com', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL);

-- Doctor user (password: doctor123)
INSERT INTO sys_user (id, username, password, real_name, phone, email, gender, status, npi, state_license_number, license_state, dea_number, taxonomy_code, credentials, specialty)
VALUES (2, 'doctor1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Dr. Sarah Mitchell', '312-555-0002', 'sarah.mitchell@medical.com', 0, 1, '1234567890', '036.140000', 'IL', 'SM1234567', '207Q00000X', 'MD', 'Family Medicine');

-- Roles
INSERT INTO sys_role (id, role_name, role_code, description, status) VALUES (1, 'Admin', 'ADMIN', 'System administrator', 1);
INSERT INTO sys_role (id, role_name, role_code, description, status) VALUES (2, 'Doctor', 'DOCTOR', 'Doctor', 1);

-- User-Role assignments
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
INSERT INTO sys_user_role (user_id, role_id) VALUES (2, 2);

-- Menus
INSERT INTO sys_menu (id, parent_id, menu_name, path, component, icon, type, permission, sort, status) VALUES
(1, 0, 'Dashboard', '/dashboard', 'dashboard/index', 'Odometer', 'MENU', NULL, 1, 1),
(2, 0, 'System', '/system', NULL, 'Setting', 'DIRECTORY', NULL, 10, 1),
(3, 2, 'Users', '/system/users', 'system/users/index', 'User', 'MENU', 'system:user:list', 11, 1),
(4, 2, 'Roles', '/system/roles', 'system/roles/index', 'Avatar', 'MENU', 'system:role:list', 12, 1),
(5, 2, 'Menus', '/system/menus', 'system/menus/index', 'Menu', 'MENU', 'system:menu:list', 13, 1),
(10, 0, 'Patients', '/patients', 'patients/index', 'UserFilled', 'MENU', 'patient:list', 20, 1),
(11, 0, 'Appointments', '/appointments', 'appointments/index', 'Calendar', 'MENU', 'appointment:list', 30, 1),
(12, 0, 'Prescriptions', '/prescriptions', 'prescriptions/index', 'Document', 'MENU', 'prescription:list', 40, 1),
(13, 0, 'Billing', '/billing', 'billing/index', 'Money', 'MENU', 'billing:list', 50, 1);

-- Role-Menu assignments (ADMIN sees everything)
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 10), (1, 11), (1, 12), (1, 13);
-- DOCTOR sees Dashboard, Patients, Appointments, Prescriptions, Billing
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (2, 1), (2, 10), (2, 11), (2, 12), (2, 13);

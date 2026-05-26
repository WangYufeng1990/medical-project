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
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(50) DEFAULT NULL,
    phone VARCHAR(20) DEFAULT NULL,
    email VARCHAR(100) DEFAULT NULL,
    gender TINYINT DEFAULT 0, -- 0: unknown, 1: male, 2: female
    status TINYINT DEFAULT 1,  -- 0: disabled, 1: enabled
    avatar VARCHAR(255) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role (
    id BIGINT NOT NULL,
    role_name VARCHAR(50) NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    description VARCHAR(200) DEFAULT NULL,
    status TINYINT DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_menu (
    id BIGINT NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    menu_name VARCHAR(50) NOT NULL,
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
    name VARCHAR(50) NOT NULL,
    gender TINYINT DEFAULT 0,
    age INT DEFAULT 0,
    id_card VARCHAR(200) DEFAULT NULL,  -- AES encrypted
    phone VARCHAR(200) DEFAULT NULL,    -- AES encrypted
    address VARCHAR(200) DEFAULT NULL,
    medical_history TEXT DEFAULT NULL,
    allergies VARCHAR(500) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE appointment (
    id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    appointment_time DATETIME NOT NULL,
    status INT DEFAULT 0,  -- 0: pending, 1: confirmed, 2: cancelled, 3: completed
    description VARCHAR(500) DEFAULT NULL,
    notes TEXT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_patient_id (patient_id),
    INDEX idx_doctor_id (doctor_id),
    INDEX idx_appointment_time (appointment_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE prescription (
    id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    diagnosis VARCHAR(500) NOT NULL,
    prescription_date DATE NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_patient_id (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE prescription_item (
    id BIGINT NOT NULL,
    prescription_id BIGINT NOT NULL,
    drug_name VARCHAR(100) NOT NULL,
    specification VARCHAR(50) DEFAULT NULL,
    dosage VARCHAR(50) NOT NULL,
    frequency VARCHAR(50) NOT NULL,
    duration INT DEFAULT 0,         -- days
    quantity INT DEFAULT 1,
    unit_price DECIMAL(10,2) DEFAULT 0.00,
    notes VARCHAR(200) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_prescription_id (prescription_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bill (
    id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    prescription_id BIGINT DEFAULT NULL,
    amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    paid_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status INT DEFAULT 0,  -- 0: unpaid, 1: paid, 2: refunded
    pay_time DATETIME DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_patient_id (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Seed Data
-- ============================================================

-- Admin user (password: admin123, BCrypt encoded)
INSERT INTO sys_user (id, username, password, real_name, phone, email, gender, status)
VALUES (1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Administrator', '13800000000', 'admin@medical.com', 1, 1);

-- Doctor user (password: doctor123)
INSERT INTO sys_user (id, username, password, real_name, phone, email, gender, status)
VALUES (2, 'doctor1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Dr. Zhang Wei', '13800000001', 'zhangwei@medical.com', 1, 1);

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

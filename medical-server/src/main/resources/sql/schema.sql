CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(200) NOT NULL,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(50) DEFAULT NULL,
    phone VARCHAR(200) DEFAULT NULL,
    email VARCHAR(300) DEFAULT NULL,
    gender TINYINT DEFAULT 0,
    status TINYINT DEFAULT 1,
    avatar VARCHAR(255) DEFAULT NULL,
    npi VARCHAR(10) DEFAULT NULL,
    state_license_number VARCHAR(200) DEFAULT NULL,
    license_state CHAR(2) DEFAULT NULL,
    dea_number VARCHAR(200) DEFAULT NULL,
    taxonomy_code VARCHAR(10) DEFAULT NULL,
    credentials VARCHAR(20) DEFAULT NULL,
    specialty VARCHAR(100) DEFAULT NULL,
    failed_attempts INT DEFAULT 0,
    locked_until TIMESTAMP NULL DEFAULT NULL,
    password_changed_at TIMESTAMP NULL DEFAULT NULL,
    force_logout_after TIMESTAMP NULL DEFAULT NULL,
    last_login_time TIMESTAMP NULL DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_npi (npi)
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_name VARCHAR(200) NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    description VARCHAR(200) DEFAULT NULL,
    status TINYINT DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
);

CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NOT NULL DEFAULT 0,
    menu_name VARCHAR(200) NOT NULL,
    path VARCHAR(200) DEFAULT NULL,
    component VARCHAR(200) DEFAULT NULL,
    icon VARCHAR(50) DEFAULT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'MENU',
    permission VARCHAR(100) DEFAULT NULL,
    sort INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);

CREATE TABLE IF NOT EXISTS patient (
    id BIGINT NOT NULL AUTO_INCREMENT,
    mrn VARCHAR(50) DEFAULT NULL,
    ssn VARCHAR(200) DEFAULT NULL,
    name VARCHAR(200) NOT NULL,
    date_of_birth VARCHAR(100) DEFAULT NULL,
    sex_at_birth CHAR(1) DEFAULT NULL,
    gender_identity VARCHAR(50) DEFAULT NULL,
    race VARCHAR(100) DEFAULT NULL,
    ethnicity VARCHAR(50) DEFAULT NULL,
    preferred_language VARCHAR(10) DEFAULT NULL,
    marital_status VARCHAR(20) DEFAULT NULL,
    patient_status VARCHAR(20) DEFAULT 'active',
    primary_care_provider VARCHAR(200) DEFAULT NULL,
    phone_mobile VARCHAR(200) DEFAULT NULL,
    phone_home VARCHAR(200) DEFAULT NULL,
    phone_work VARCHAR(200) DEFAULT NULL,
    email VARCHAR(300) DEFAULT NULL,
    address_line1 VARCHAR(200) DEFAULT NULL,
    address_line2 VARCHAR(200) DEFAULT NULL,
    city VARCHAR(200) DEFAULT NULL,
    state VARCHAR(200) DEFAULT NULL,
    zip_code VARCHAR(200) DEFAULT NULL,
    emergency_contact_name VARCHAR(200) DEFAULT NULL,
    emergency_contact_phone VARCHAR(200) DEFAULT NULL,
    emergency_contact_relation VARCHAR(50) DEFAULT NULL,
    insurance_payer VARCHAR(200) DEFAULT NULL,
    insurance_member_id VARCHAR(200) DEFAULT NULL,
    insurance_group_number VARCHAR(200) DEFAULT NULL,
    medical_history VARCHAR(4000) DEFAULT NULL,
    allergies VARCHAR(2000) DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mrn (mrn)
);

CREATE TABLE IF NOT EXISTS appointment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    appointment_time TIMESTAMP NOT NULL,
    status INT DEFAULT 0,
    visit_type VARCHAR(30) DEFAULT NULL,
    chief_complaint VARCHAR(500) DEFAULT NULL,
    department VARCHAR(50) DEFAULT NULL,
    duration INT DEFAULT NULL,
    cpt_code VARCHAR(10) DEFAULT NULL,
    icd10_codes VARCHAR(200) DEFAULT NULL,
    check_in_time TIMESTAMP DEFAULT NULL,
    check_out_time TIMESTAMP DEFAULT NULL,
    description VARCHAR(500) DEFAULT NULL,
    notes TEXT DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_appointment_patient_id (patient_id),
    INDEX idx_appointment_doctor_id (doctor_id),
    INDEX idx_appointment_time (appointment_time)
);

CREATE TABLE IF NOT EXISTS prescription (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    diagnosis VARCHAR(500) NOT NULL,
    icd10_codes VARCHAR(500) DEFAULT NULL,
    prescription_date DATE NOT NULL,
    prescription_type VARCHAR(20) DEFAULT 'MEDICATION',
    rx_status VARCHAR(20) DEFAULT 'active',
    prescriber_npi VARCHAR(10) DEFAULT NULL,
    dea_number VARCHAR(200) DEFAULT NULL,
    controlled_schedule VARCHAR(5) DEFAULT NULL,
    pharmacy_name VARCHAR(100) DEFAULT NULL,
    pharmacy_phone VARCHAR(200) DEFAULT NULL,
    pharmacy_npi VARCHAR(10) DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_prescription_patient_id (patient_id)
);

CREATE TABLE IF NOT EXISTS prescription_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    prescription_id BIGINT NOT NULL,
    drug_name VARCHAR(100) NOT NULL,
    ndc_code VARCHAR(20) DEFAULT NULL,
    rxnorm_code VARCHAR(20) DEFAULT NULL,
    specification VARCHAR(50) DEFAULT NULL,
    dosage VARCHAR(50) NOT NULL,
    route VARCHAR(10) DEFAULT NULL,
    frequency VARCHAR(50) NOT NULL,
    sig VARCHAR(200) DEFAULT NULL,
    duration INT DEFAULT 0,
    days_supply INT DEFAULT NULL,
    quantity INT DEFAULT 1,
    refills INT DEFAULT 0,
    daw INT DEFAULT 0,
    unit_price DECIMAL(10,2) DEFAULT 0.00,
    notes VARCHAR(200) DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_prescription_id (prescription_id)
);

CREATE TABLE IF NOT EXISTS bill (
    id BIGINT NOT NULL AUTO_INCREMENT,
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
    insurance_claim_number VARCHAR(200) DEFAULT NULL,
    prior_authorization_number VARCHAR(50) DEFAULT NULL,
    claim_filing_date DATE DEFAULT NULL,
    adjudication_date DATE DEFAULT NULL,
    pay_time TIMESTAMP DEFAULT NULL,
    payment_method VARCHAR(20) DEFAULT NULL,
    receipt_number VARCHAR(50) DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_bill_patient_id (patient_id)
);

CREATE TABLE IF NOT EXISTS message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sender_id BIGINT NOT NULL,
    sender_type VARCHAR(10) NOT NULL,
    receiver_id BIGINT NOT NULL,
    receiver_type VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    is_read TINYINT DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT DEFAULT NULL,
    username VARCHAR(50) DEFAULT NULL,
    patient_id BIGINT DEFAULT NULL,
    module VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_id VARCHAR(100) DEFAULT NULL,
    detail VARCHAR(500) DEFAULT NULL,
    ip VARCHAR(50) DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_hash VARCHAR(64) DEFAULT NULL,
    archived TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time),
    INDEX idx_target_id (target_id),
    INDEX idx_archived (archived)
);

CREATE TABLE IF NOT EXISTS patient_auth (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    username VARCHAR(200) NOT NULL,
    password VARCHAR(255) NOT NULL,
    status TINYINT DEFAULT 1,
    last_login_time TIMESTAMP DEFAULT NULL,
    failed_attempts INT DEFAULT 0,
    locked_until TIMESTAMP DEFAULT NULL,
    password_changed_at TIMESTAMP NULL DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_patient_auth_username (username),
    UNIQUE KEY uk_patient_auth_patient_id (patient_id)
);

CREATE TABLE IF NOT EXISTS password_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_type VARCHAR(10) NOT NULL,
    user_id BIGINT NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    changed_at TIMESTAMP NULL DEFAULT NULL,
    is_deleted TINYINT DEFAULT 0,
    version INT DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_user_type_id (user_type, user_id)
);

CREATE TABLE IF NOT EXISTS consent (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    consent_type VARCHAR(30) NOT NULL,
    scope VARCHAR(100) DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    policy_uri VARCHAR(255) DEFAULT NULL,
    provision_period_start DATE DEFAULT NULL,
    provision_period_end DATE DEFAULT NULL,
    granted_by BIGINT DEFAULT NULL,
    consent_date DATE NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_consent_patient_id (patient_id)
);

CREATE TABLE IF NOT EXISTS emergency_access (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    accessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL DEFAULT NULL,
    audited TINYINT NOT NULL DEFAULT 0,
    reviewed_by BIGINT DEFAULT NULL,
    reviewed_at TIMESTAMP NULL DEFAULT NULL,
    is_deleted TINYINT DEFAULT 0,
    version INT DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_emergency_user_id (user_id),
    INDEX idx_emergency_patient_id (patient_id)
);

CREATE TABLE IF NOT EXISTS key_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(20) NOT NULL,
    key_version VARCHAR(10) DEFAULT NULL,
    changed_by VARCHAR(50) DEFAULT NULL,
    detail VARCHAR(500) DEFAULT NULL,
    event_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_key_audit_time (event_time)
);

CREATE TABLE IF NOT EXISTS drug_interaction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    drug_a_rxnorm VARCHAR(20) NOT NULL,
    drug_b_rxnorm VARCHAR(20) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    description VARCHAR(500) NOT NULL,
    mechanism VARCHAR(200) DEFAULT NULL,
    recommendation VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_drug_pair (drug_a_rxnorm, drug_b_rxnorm)
);

CREATE TABLE IF NOT EXISTS drug_allergy_class (
    id BIGINT NOT NULL AUTO_INCREMENT,
    drug_rxnorm_code VARCHAR(20) NOT NULL,
    allergy_class VARCHAR(100) NOT NULL,
    cross_reactive_codes VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_drug_allergy_code (drug_rxnorm_code)
);

CREATE TABLE IF NOT EXISTS cds_override (
    id BIGINT NOT NULL AUTO_INCREMENT,
    prescription_id BIGINT NOT NULL,
    warning_type VARCHAR(30) NOT NULL,
    severity VARCHAR(20) DEFAULT NULL,
    drugs_involved VARCHAR(200) DEFAULT NULL,
    override_reason VARCHAR(500) NOT NULL,
    overridden_by BIGINT NOT NULL,
    overridden_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    version INT DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_override_prescription (prescription_id)
);

CREATE TABLE IF NOT EXISTS quality_measure (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cms_id VARCHAR(20) UNIQUE NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(500) DEFAULT NULL,
    denominator_query VARCHAR(1000) DEFAULT NULL,
    numerator_query VARCHAR(1000) DEFAULT NULL,
    exclusion_query VARCHAR(1000) DEFAULT NULL,
    report_period_months INT DEFAULT 12,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS quality_result (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cms_id VARCHAR(20) NOT NULL,
    denominator BIGINT NOT NULL DEFAULT 0,
    exclusions BIGINT NOT NULL DEFAULT 0,
    eligible_denominator BIGINT NOT NULL DEFAULT 0,
    numerator BIGINT NOT NULL DEFAULT 0,
    performance_rate DOUBLE NOT NULL DEFAULT 0,
    performance_target VARCHAR(200) DEFAULT NULL,
    report_period_months INT DEFAULT 12,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_qr_cms_id (cms_id),
    INDEX idx_qr_calculated_at (calculated_at)
);

CREATE TABLE IF NOT EXISTS pharmacy_directory (
    id BIGINT NOT NULL AUTO_INCREMENT,
    npi VARCHAR(10) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    address_line1 VARCHAR(100) DEFAULT NULL,
    city VARCHAR(50) DEFAULT NULL,
    state CHAR(2) DEFAULT NULL,
    zip_code VARCHAR(10) DEFAULT NULL,
    phone VARCHAR(200) DEFAULT NULL,
    supports_epcs TINYINT DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS loinc_catalog (
    id BIGINT NOT NULL AUTO_INCREMENT,
    loinc_code VARCHAR(20) UNIQUE NOT NULL,
    display VARCHAR(200) NOT NULL,
    unit VARCHAR(20) DEFAULT NULL,
    ref_range_low VARCHAR(20) DEFAULT NULL,
    ref_range_high VARCHAR(20) DEFAULT NULL,
    panel_parent_code VARCHAR(20) DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS observation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    loinc_code VARCHAR(20) NOT NULL,
    loinc_display VARCHAR(200) DEFAULT NULL,
    obs_value VARCHAR(50) DEFAULT NULL,
    unit VARCHAR(20) DEFAULT NULL,
    reference_range VARCHAR(50) DEFAULT NULL,
    abnormal_flag CHAR(1) DEFAULT NULL,
    status VARCHAR(20) DEFAULT 'final',
    source_message_id VARCHAR(100) DEFAULT NULL,
    effective_date TIMESTAMP NULL DEFAULT NULL,
    is_deleted TINYINT DEFAULT 0,
    version INT DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_obs_patient_loinc (patient_id, loinc_code),
    UNIQUE KEY uk_source_message (source_message_id)
);

CREATE TABLE IF NOT EXISTS medical_history_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    description VARCHAR(500) NOT NULL,
    recorded_by BIGINT DEFAULT NULL,
    is_deleted TINYINT DEFAULT 0,
    version INT DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_mh_patient_id (patient_id)
);

CREATE TABLE IF NOT EXISTS allergy_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    allergen VARCHAR(200) NOT NULL,
    reaction VARCHAR(200) DEFAULT NULL,
    severity VARCHAR(20) DEFAULT NULL,
    recorded_by BIGINT DEFAULT NULL,
    status VARCHAR(20) DEFAULT 'active',
    resolved_by BIGINT DEFAULT NULL,
    resolved_at TIMESTAMP NULL DEFAULT NULL,
    is_deleted TINYINT DEFAULT 0,
    version INT DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_ae_patient_id (patient_id)
);

CREATE TABLE IF NOT EXISTS vital_sign (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    recorded_by BIGINT DEFAULT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    systolic_bp INT DEFAULT NULL,
    diastolic_bp INT DEFAULT NULL,
    heart_rate INT DEFAULT NULL,
    temperature DECIMAL(4,1) DEFAULT NULL,
    respiratory_rate INT DEFAULT NULL,
    oxygen_saturation INT DEFAULT NULL,
    height_cm DECIMAL(5,1) DEFAULT NULL,
    weight_kg DECIMAL(5,1) DEFAULT NULL,
    bmi DECIMAL(4,1) DEFAULT NULL,
    notes VARCHAR(500) DEFAULT NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_vs_patient_id (patient_id)
);

CREATE TABLE IF NOT EXISTS care_plan (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    goal VARCHAR(200) DEFAULT NULL,
    interventions VARCHAR(1000) DEFAULT NULL,
    start_date DATE DEFAULT NULL,
    target_date DATE DEFAULT NULL,
    completed_date DATE DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT DEFAULT NULL,
    notes VARCHAR(500) DEFAULT NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_cp_patient_id (patient_id)
);

CREATE TABLE IF NOT EXISTS prior_auth (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    auth_type VARCHAR(20) DEFAULT NULL,
    item_name VARCHAR(200) DEFAULT NULL,
    item_code VARCHAR(20) DEFAULT NULL,
    insurance_payer VARCHAR(200) DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at DATE NOT NULL,
    resolved_at DATE DEFAULT NULL,
    auth_number VARCHAR(50) DEFAULT NULL,
    requested_by BIGINT DEFAULT NULL,
    notes VARCHAR(500) DEFAULT NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_pa_patient_id (patient_id)
);

CREATE TABLE IF NOT EXISTS formulary_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rxnorm_code VARCHAR(20) NOT NULL,
    drug_name VARCHAR(200) NOT NULL,
    insurance_payer VARCHAR(200) NOT NULL,
    tier VARCHAR(20) NOT NULL,
    prior_auth_required TINYINT DEFAULT 0,
    step_therapy_required TINYINT DEFAULT 0,
    alternatives VARCHAR(200) DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_fe_rxnorm_payer (rxnorm_code, insurance_payer)
);

CREATE TABLE IF NOT EXISTS referral (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    referring_doctor_id BIGINT NOT NULL,
    specialist_name VARCHAR(200) NOT NULL,
    specialist_npi VARCHAR(10) DEFAULT NULL,
    specialty VARCHAR(100) DEFAULT NULL,
    diagnosis VARCHAR(200) DEFAULT NULL,
    reason VARCHAR(500) DEFAULT NULL,
    urgency VARCHAR(20) DEFAULT 'ROUTINE',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    referral_date DATE DEFAULT NULL,
    appointment_date DATE DEFAULT NULL,
    completion_date DATE DEFAULT NULL,
    notes VARCHAR(500) DEFAULT NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_ref_patient_id (patient_id)
);

CREATE TABLE IF NOT EXISTS charge (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    appointment_id BIGINT DEFAULT NULL,
    doctor_id BIGINT DEFAULT NULL,
    cpt_codes VARCHAR(200) DEFAULT NULL,
    icd10_codes VARCHAR(200) DEFAULT NULL,
    units INT DEFAULT 1,
    charge_amount DECIMAL(10,2) DEFAULT NULL,
    visit_type VARCHAR(30) DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    bill_id BIGINT DEFAULT NULL,
    notes VARCHAR(500) DEFAULT NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_chg_patient_id (patient_id)
);

CREATE TABLE IF NOT EXISTS refill_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    prescription_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by BIGINT DEFAULT NULL,
    reviewed_at TIMESTAMP NULL DEFAULT NULL,
    reason VARCHAR(500) DEFAULT NULL,
    review_notes VARCHAR(500) DEFAULT NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_rr_patient_id (patient_id),
    INDEX idx_rr_status (status)
);

CREATE TABLE IF NOT EXISTS immunization (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    vaccine_name VARCHAR(200) NOT NULL,
    cvx_code VARCHAR(10) DEFAULT NULL,
    administration_date DATE DEFAULT NULL,
    lot_number VARCHAR(50) DEFAULT NULL,
    manufacturer VARCHAR(100) DEFAULT NULL,
    dose_number VARCHAR(20) DEFAULT NULL,
    site VARCHAR(50) DEFAULT NULL,
    route VARCHAR(50) DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'completed',
    administered_by BIGINT DEFAULT NULL,
    notes VARCHAR(500) DEFAULT NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_imm_patient_id (patient_id)
);

CREATE TABLE IF NOT EXISTS problem (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    snomed_code VARCHAR(20) DEFAULT NULL,
    snomed_display VARCHAR(200) DEFAULT NULL,
    icd10_code VARCHAR(10) DEFAULT NULL,
    onset_date DATE DEFAULT NULL,
    resolution_date DATE DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    severity VARCHAR(10) DEFAULT NULL,
    recorded_by BIGINT DEFAULT NULL,
    notes VARCHAR(500) DEFAULT NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_prob_patient_id (patient_id)
);

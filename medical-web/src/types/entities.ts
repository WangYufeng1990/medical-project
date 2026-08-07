// Backend VO / frontend form shapes per module. VO types mirror backend
// responses (numeric ids, dates); Form types mirror frontend form state
// (string inputs), which views send directly as create/update payloads.

import type { PageQuery } from './common'

// ── Patient ────────────────────────────────────────────────────────────────
export interface PatientVO {
  id: number
  mrn: string
  name: string
  ssn?: string
  dateOfBirth?: string
  sexAtBirth?: string
  genderIdentity?: string
  race?: string
  ethnicity?: string
  preferredLanguage?: string
  maritalStatus?: string
  phoneMobile?: string
  phoneHome?: string
  phoneWork?: string
  email?: string
  addressLine1?: string
  addressLine2?: string
  city?: string
  state?: string
  zipCode?: string
  emergencyContactName?: string
  emergencyContactPhone?: string
  emergencyContactRelation?: string
  insurancePayer?: string
  insuranceMemberId?: string
  insuranceGroupNumber?: string
  primaryCareProvider?: string
  patientStatus?: string
  medicalHistory?: string
  allergies?: string
  createTime?: string
}

export interface PatientForm {
  name: string
  mrn: string
  ssn: string
  dateOfBirth: string
  sexAtBirth: string
  genderIdentity: string
  race: string
  ethnicity: string
  preferredLanguage: string
  maritalStatus: string
  phoneMobile: string
  phoneHome: string
  phoneWork: string
  email: string
  addressLine1: string
  addressLine2: string
  city: string
  state: string
  zipCode: string
  emergencyContactName: string
  emergencyContactPhone: string
  emergencyContactRelation: string
  insurancePayer: string
  insuranceMemberId: string
  insuranceGroupNumber: string
  primaryCareProvider: string
  patientStatus: string
}

export interface MedicalHistoryEntry {
  id: number
  description: string
  recordedBy?: number
  createTime?: string
}

export interface AllergyEntry {
  id: number
  allergen: string
  reaction?: string | null
  severity?: string | null
  status?: string
  resolvedAt?: string
  resolvedBy?: number
}

// ── Appointment ────────────────────────────────────────────────────────────
export interface AppointmentVO {
  id: number
  patientId: number
  patientName: string
  doctorId: number
  doctorName?: string
  appointmentTime: string
  visitType?: string
  chiefComplaint?: string
  department?: string
  duration?: number
  cptCode?: string
  description?: string
  status: number
  icd10Codes?: string
  createTime?: string
}

export interface AppointmentForm {
  patientId: string
  doctorId: string
  appointmentTime: string
  visitType: string
  chiefComplaint: string
  department: string
  duration: number
  cptCode: string
  description: string
  status: number
}

export interface AppointmentQuery extends PageQuery {
  patientId?: number
  status?: number
  doctorId?: number
}

export interface AppointmentConflict {
  id: number
  appointmentTime: string
  patientName: string
}

// ── Billing ────────────────────────────────────────────────────────────────
export interface BillVO {
  id: number
  patientId: number
  patientName: string
  billType?: string
  claimStatus?: string
  totalCharge?: number
  insuranceAdjustment?: number
  insurancePayment?: number
  patientResponsibility?: number
  patientPaidAmount?: number
  copayAmount?: number
  cptCodes?: string
  icd10Codes?: string
  placeOfServiceCode?: string
  insurancePayerName?: string
  insuranceClaimNumber?: string
  claimFilingDate?: string
  payTime?: string
  paymentMethod?: string
  createTime?: string
}

export interface BillForm {
  patientId: string
  totalCharge: string
  billType: string
  cptCodes: string
  icd10Codes: string
  insurancePayerName: string
  copayAmount: string
}

export interface AdjudicateForm {
  insurancePayment: string
  adjustment: string
  claimNumber: string
  adjudicationDate: string
}

export interface PayForm {
  paymentAmount: string
  paymentMethod: string
}

export interface BillQuery extends PageQuery {
  patientId?: number
}

export interface BillCreatePayload {
  patientId: number
  totalCharge?: number
  billType?: string
  cptCodes?: string
  icd10Codes?: string
  insurancePayerName?: string
  copayAmount?: number
}

// ── Prescription ───────────────────────────────────────────────────────────
export interface PrescriptionItem {
  id?: number
  drugName: string
  rxnormCode: string
  dosage?: string
  frequency?: string
  duration?: string
  quantity?: string
  refills?: string
  notes?: string
}

export interface PrescriptionVO {
  id: number
  patientId: number
  patientName: string
  doctorId?: number
  doctorName?: string
  diagnosis?: string
  icd10Codes?: string
  prescriptionDate?: string
  prescriptionType?: string
  rxStatus?: string
  items: PrescriptionItem[]
  createTime?: string
}

export interface PrescriptionItemForm {
  drugName: string
  rxnormCode: string
  dosage: string
  frequency: string
  duration: string
  quantity: string
  refills: string
  notes: string
}

export interface PrescriptionForm {
  patientId: string
  doctorId: string
  diagnosis: string
  icd10Codes: string
  prescriptionDate: string
  prescriptionType: string
  rxStatus: string
  items: PrescriptionItemForm[]
}

export interface PrescriptionQuery extends PageQuery {
  patientId?: number
  rxStatus?: string
}

export interface RefillRequestVO {
  id: number
  prescriptionId: number
  patientName?: string
  patientId?: number
  doctorName?: string
  diagnosis?: string
  icd10Codes?: string
  rxStatus?: string
  reason?: string
  requestedAt?: string
  status?: string
}

// ── Charge / Superbill ─────────────────────────────────────────────────────
export interface ChargeVO {
  id: number
  patientId: number
  patientName?: string
  appointmentId?: number
  appointmentTime?: string
  cptCodes?: string
  icd10Codes?: string
  visitType?: string
  chargeAmount?: number
  status?: string
  billId?: number
  notes?: string
  createTime?: string
}

export interface ChargeForm {
  patientId: string
  appointmentId: string
  cptCodes: string
  icd10Codes: string
  chargeAmount: string
  visitType: string
  notes: string
}

export interface ChargeCreatePayload {
  patientId: number
  appointmentId?: number
  cptCodes?: string
  icd10Codes?: string
  chargeAmount?: number
  visitType?: string
  notes?: string
}

// ── Referral ───────────────────────────────────────────────────────────────
export interface ReferralVO {
  id: number
  patientId: number
  patientName?: string
  specialistName?: string
  specialistNpi?: string
  specialty?: string
  diagnosis?: string
  reason?: string
  urgency?: string
  referralDate?: string
  appointmentDate?: string
  status?: string
  notes?: string
  createTime?: string
}

export interface ReferralForm {
  patientId: string
  specialistName: string
  specialistNpi: string
  specialty: string
  diagnosis: string
  reason: string
  urgency: string
  notes: string
}

// ── Prior Auth ─────────────────────────────────────────────────────────────
export interface PriorAuthVO {
  id: number
  patientId: number
  patientName?: string
  authType?: string
  itemName?: string
  itemCode?: string
  insurancePayer?: string
  requestedAt?: string
  authNumber?: string
  status?: string
  notes?: string
}

export interface PriorAuthForm {
  patientId: string
  authType: string
  itemName: string
  itemCode: string
  insurancePayer: string
  notes: string
}

// ── Clinical sub-resources (patient detail) ────────────────────────────────
export interface ProblemVO {
  id: number
  patientId?: number
  snomedCode?: string | null
  snomedDisplay: string
  icd10Code?: string | null
  onsetDate?: string
  severity?: string
  status?: string
  resolutionDate?: string
  notes?: string | null
}

export interface ProblemForm {
  snomedCode: string
  snomedDisplay: string
  icd10Code: string
  onsetDate: string
  severity: string
  notes: string
}

export interface ProblemCreatePayload {
  snomedCode?: string | null
  snomedDisplay: string
  icd10Code?: string | null
  onsetDate?: string
  severity?: string
  notes?: string | null
  status?: string
  resolutionDate?: string
}

export interface ImmunizationVO {
  id: number
  patientId?: number
  vaccineName: string
  cvxCode?: string | null
  administrationDate?: string
  doseNumber?: string | null
  lotNumber?: string | null
  manufacturer?: string | null
  site?: string | null
  route?: string | null
  status?: string
  notes?: string | null
}

export interface ImmunizationForm {
  vaccineName: string
  cvxCode: string
  administrationDate: string
  doseNumber: string
  lotNumber: string
  manufacturer: string
  site: string
  route: string
  notes: string
}

export interface ImmunizationCreatePayload {
  vaccineName: string
  cvxCode?: string | null
  administrationDate?: string
  doseNumber?: string | null
  lotNumber?: string | null
  manufacturer?: string | null
  site?: string | null
  route?: string | null
  notes?: string | null
}

export interface VitalSignVO {
  id: number
  patientId?: number
  systolicBp?: number | null
  diastolicBp?: number | null
  heartRate?: number | null
  temperature?: number | null
  respiratoryRate?: number | null
  oxygenSaturation?: number | null
  heightCm?: number | null
  weightKg?: number | null
  bmi?: number | null
  notes?: string | null
  recordedAt?: string
  createTime?: string
}

export interface VitalSignForm {
  systolicBp: string
  diastolicBp: string
  heartRate: string
  temperature: string
  respiratoryRate: string
  oxygenSaturation: string
  heightCm: string
  weightKg: string
  bmi: string
  notes: string
}

export interface VitalSignCreatePayload {
  systolicBp?: number | null
  diastolicBp?: number | null
  heartRate?: number | null
  temperature?: number | null
  respiratoryRate?: number | null
  oxygenSaturation?: number | null
  heightCm?: number | null
  weightKg?: number | null
  bmi?: number | null
  notes?: string | null
}

export interface CarePlanVO {
  id: number
  patientId?: number
  title: string
  goal?: string
  interventions?: string
  targetDate?: string
  startDate?: string
  status?: string
  notes?: string
}

export interface CarePlanForm {
  title: string
  goal: string
  interventions: string
  targetDate: string
  notes: string
}

export interface CarePlanCreatePayload {
  title: string
  goal?: string | null
  interventions?: string | null
  targetDate?: string | null
  notes?: string | null
}

// ── Lab / LOINC ────────────────────────────────────────────────────────────
export interface ObservationVO {
  id: number
  patientId?: number
  loincCode: string
  loincDisplay?: string
  obsValue?: string
  unit?: string
  referenceRange?: string
  abnormalFlag?: string
  effectiveDate?: string
  createTime?: string
}

export interface ObservationQuery extends PageQuery {
  loinc?: string
}

export interface LoincEntry {
  id: number
  loincCode: string
  display: string
  unit?: string
  refRangeLow?: string
  refRangeHigh?: string
  panelParentCode?: string
}

// ── System ─────────────────────────────────────────────────────────────────
export interface SysUserVO {
  id: number
  username: string
  realName?: string
  phone?: string
  email?: string
  gender?: number
  npi?: string
  stateLicenseNumber?: string
  licenseState?: string
  deaNumber?: string
  taxonomyCode?: string
  credentials?: string
  specialty?: string
  roleIds?: number[]
  roles?: string[]
  status?: number
  failedAttempts?: number
  lockedUntil?: string
  lastLoginTime?: string
  createTime?: string
}

export interface SysUserForm {
  username: string
  password: string
  realName: string
  phone: string
  email: string
  gender: number
  status: number
  npi: string
  stateLicenseNumber: string
  licenseState: string
  deaNumber: string
  taxonomyCode: string
  credentials: string
  specialty: string
}

export interface UserQuery extends PageQuery {
  keyword?: string
  roleId?: number
}

export interface RoleVO {
  id: number
  roleName: string
  roleCode: string
  description?: string
  status?: number
  createTime?: string
}

export interface RoleForm {
  roleName: string
  roleCode: string
  description: string
  status: number
}

export interface MenuVO {
  id: number
  menuName: string
  path?: string
  permission?: string
  type?: string
  module?: string
  parentId?: number
  sort?: number
  children?: MenuVO[]
}

export interface MenuForm {
  menuName: string
  path: string
  permission?: string
  type?: string
  module?: string
  parentId?: number
  sort?: number
}

export interface DoctorVO {
  id: number
  username: string
  realName?: string
}

export interface StaffProfileVO {
  username: string
  realName?: string
  phone?: string
  email?: string
  gender?: string
  npi?: string
  licenseState?: string
  taxonomyCode?: string
  credentials?: string
  specialty?: string
}

export interface StaffProfileForm {
  realName: string
  phone: string
  email: string
  gender: string
  npi: string
  licenseState: string
  taxonomyCode: string
  credentials: string
  specialty: string
}

export interface ChangePasswordForm {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export interface PatientProfileVO {
  name: string
  mrn: string
  dateOfBirth?: string
  sexAtBirth?: string
  phoneMobile?: string
  phoneHome?: string
  phoneWork?: string
  email?: string
  addressLine1?: string
  addressLine2?: string
  city?: string
  state?: string
  zipCode?: string
  emergencyContactName?: string
  emergencyContactPhone?: string
  emergencyContactRelation?: string
  insurancePayer?: string
  allergies?: string
}

// ── Consent / Emergency / Audit ────────────────────────────────────────────
export interface ConsentVO {
  id: number
  patientId?: number
  consentType: string
  scope?: string
  status?: string
  consentDate?: string
  createTime?: string
}

export interface EmergencyAccessVO {
  id: number
  userId?: number
  patientId: number
  reason: string
  accessedAt?: string
  expiresAt?: string
  audited?: number
  reviewedBy?: number
  reviewedAt?: string
}

export interface EmergencyAccessResultVO {
  token: string
  expiresIn: number
  patientId: number
}

export interface AuditLogVO {
  id: number
  username?: string
  userId?: number
  patientId?: number
  module: string
  action: string
  target?: string
  targetId?: number
  detail?: string
  ip?: string
  createTime: string
}

export interface AuditDistinctValues {
  modules: string[]
  actions: string[]
}

export interface AuditQuery extends PageQuery {
  module?: string
  action?: string
  userId?: number
  patientId?: number
  fromDate?: string
  toDate?: string
}

// ── Dashboard / Quality / Pharmacy / Key rotation ──────────────────────────
export interface DashboardStats {
  totalPatients: number
  todayAppointments: number
  pendingBills: number
  monthlyPrescriptions: number
}

export interface QualityMeasureVO {
  id: number
  cmsId: string
  title: string
  description?: string
  reportPeriodMonths?: number
  performanceTarget?: number
}

export interface QualityResultVO {
  id?: number
  cmsId: string
  title?: string
  reportPeriodMonths?: number
  denominator: number
  eligibleDenominator: number
  exclusions: number
  numerator: number
  performanceRate: number
  performanceTarget?: number
  calculatedAt?: string
}

export interface PharmacyVO {
  id: number
  name: string
  npi?: string
  addressLine1?: string
  city?: string
  state?: string
  zip?: string
  zipCode?: string
  phone?: string
  supportsEpcs?: boolean
}

export interface PharmacyQuery extends PageQuery {
  zip?: string
  distance?: number
}

export interface KeyHistoryEntry {
  id: number
  keyVersion: number
  eventType: string
  eventTime: string
  detail?: string
}

export interface KeyRotationStatusVO {
  rotationActive: boolean
  running?: boolean
  complete?: boolean
  remainingByTable?: Record<string, number>
}

export interface DisclosureVO {
  id: number
  module: string
  action: string
  detail?: string
  createTime: string
}

export interface FormularyEntry {
  id: number
  rxnormCode: string
  insurancePayer: string
  name?: string
  tier?: string
  status?: string
}

// ── CDS / Chat / Auth ──────────────────────────────────────────────────────
export interface CdsItem {
  rxnormCode: string
  drugName: string
}

export interface CdsCheckRequest {
  patientId: number
  items: CdsItem[]
}

export interface CdsCheckResult {
  passed: boolean
  warnings: CdsWarning[]
}

export interface DrugLookupResult {
  rxnormCode: string
  drugName: string
}

export interface CdsWarning {
  type: string
  severity: string
  drugsInvolved: string
  description: string
  recommendation: string
  message?: string
}

export interface PrescriptionCreatePayload {
  patientId: number
  doctorId: number
  diagnosis?: string
  icd10Codes?: string
  prescriptionDate?: string
  prescriptionType?: string
  rxStatus?: string
  items: {
    drugName: string
    rxnormCode: string
    dosage?: string
    frequency?: string
    duration?: number | null
    quantity?: number | null
    refills?: number | null
    notes?: string
  }[]
}

export interface SseTicketVO {
  ticket: string
  expiresIn: number
}

export interface MessageVO {
  id: number
  senderId: number
  receiverId: number
  content: string
  isRead?: boolean
  createTime: string
}

export interface ConversationVO {
  partnerId: number
  partnerName: string
  lastMessage?: string
  lastMessageTime?: string
  unreadCount?: number
}

export interface LoginResponse {
  token?: string
  accessToken?: string
  refreshToken?: string
  expiresIn?: number
  userId?: number
  username?: string
  realName?: string
  roles?: string[]
  permissions?: string[]
  user?: { id: number; username: string; realName?: string }
}

export interface PatientLoginResponse {
  token: string
  refreshToken?: string
  patientId: number
  name: string
  username: string
}

export const APPOINTMENT_STATUS: Record<number, string> = {
  0: 'Scheduled',
  1: 'Arrived',
  2: 'Cancelled',
  3: 'Completed',
  4: 'No-Show',
  5: 'Rescheduled',
  6: 'In Progress',
}

export const VISIT_TYPES = [
  'NEW_PATIENT', 'FOLLOW_UP', 'ANNUAL_PHYSICAL',
  'URGENT_CARE', 'CONSULTATION'
]

export const SEX_OPTIONS = ['M', 'F', 'U']

export const RACE_OPTIONS = [
  'White', 'Black or African American', 'Asian',
  'American Indian or Alaska Native',
  'Native Hawaiian or Other Pacific Islander', 'Other'
]

export const ETHNICITY_OPTIONS = ['Hispanic or Latino', 'Not Hispanic or Latino']

export const MARITAL_STATUS_OPTIONS = ['Single', 'Married', 'Divorced', 'Widowed']

export const LANGUAGE_OPTIONS = ['en', 'es', 'zh']

export const EMERGENCY_RELATION_OPTIONS = ['Spouse', 'Parent', 'Child', 'Sibling', 'Other']

export const TERMINAL_APPOINTMENT_STATUSES = [2, 3, 4]
export const CONSENT_TYPE_LABELS: Record<string, string> = {
  TREATMENT: 'Treatment Consent',
  RESEARCH: 'Research Participation',
  DATA_SHARING: 'Data Sharing',
  MARKETING: 'Marketing Communications',
}

export const PAGE_SIZE = 10

export const BILL_STATUS_COLOR: Record<string, string> = {
  DRAFT: '#909399', SUBMITTED: '#409EFF', PENDING: '#E6A23C', PAID: '#67C23A', DENIED: '#F56C6C',
}

export const APPOINTMENT_STATUS_COLOR: Record<number, string> = {
  0: '#409EFF', 1: '#67C23A', 2: '#909399', 3: '#67C23A', 4: '#E6A23C', 5: '#E6A23C', 6: '#409EFF',
}

export const CONSENT_TYPES = ['TREATMENT', 'RESEARCH', 'DATA_SHARING', 'MARKETING']

export const CONSENT_STATUS = ['active', 'revoked']

export const CONSENT_STATUS_COLOR: Record<string, string> = {
  active: '#67C23A',
  revoked: '#909399',
}

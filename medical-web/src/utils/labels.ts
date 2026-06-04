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

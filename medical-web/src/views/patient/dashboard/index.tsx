import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import patientRequest from '../../../api/patientRequest'
import styles from '../../dashboard/style.module.css'

const overviewCards = [
  { label: 'Appointments', key: 'apt', path: '/patient/appointments', color: '#10b981' },
  { label: 'Prescriptions', key: 'rx', path: '/patient/prescriptions', color: '#ef4444' },
  { label: 'Bills', key: 'bill', path: '/patient/bills', color: '#f59e0b' },
]

const healthCards = [
  { label: 'Vital Signs', path: '/patient/vitals', icon: '🫀', color: '#10b981' },
  { label: 'Problem List', path: '/patient/problems', icon: '📋', color: '#6366f1' },
  { label: 'Immunizations', path: '/patient/immunizations', icon: '💉', color: '#8b5cf6' },
  { label: 'Lab Results', path: '/patient/lab', icon: '🧪', color: '#06b6d4' },
  { label: 'Care Plans', path: '/patient/care-plans', icon: '📝', color: '#64748b' },
]

const accountCards = [
  { label: 'Referrals', path: '/patient/referrals', icon: '🏥', color: '#3b82f6' },
  { label: 'Prior Auths', path: '/patient/prior-auths', icon: '📄', color: '#f59e0b' },
  { label: 'Consent', path: '/patient/consent', icon: '✍️', color: '#10b981' },
  { label: 'Disclosures', path: '/patient/disclosures', icon: '🛡', color: '#64748b' },
]

export default function PatientDashboard() {
  const navigate = useNavigate()
  const info = JSON.parse(localStorage.getItem('patientInfo') || '{}')

  const fetchCount = (url: string) => patientRequest.get(url).then(r => r)
  const { data: aptData } = useQuery({ queryKey: ['me', 'apt-count'], queryFn: () => fetchCount('/patient/me/appointments?page=1&size=1') })
  const { data: rxData } = useQuery({ queryKey: ['me', 'rx-count'], queryFn: () => fetchCount('/patient/me/prescriptions?page=1&size=1') })
  const { data: billData } = useQuery({ queryKey: ['me', 'bill-count'], queryFn: () => fetchCount('/patient/me/bills?page=1&size=1') })
  const counts: any = { apt: aptData?.total ?? 0, rx: rxData?.total ?? 0, bill: billData?.total ?? 0 }

  return (<div>
    <h2 className={styles.welcome}>Welcome, {info.name || 'Patient'}</h2>
    <p className={styles.welcomeSub}>Your health at a glance</p>

    <div className={styles.sectionTitle} style={{ marginTop: 24 }}>Overview</div>
    <div className={styles.patientGrid}>
      {overviewCards.map(c => (
        <div key={c.label} className={styles.patientCard} onClick={() => navigate(c.path)} style={{ borderTopColor: c.color }}>
          <div className={styles.label}>{c.label}</div>
          <div className={styles.patientStatValue}>{counts[c.key]}</div>
        </div>
      ))}
    </div>

    <div className={styles.sectionTitle}>Health Records</div>
    <div className={styles.patientGrid}>
      {healthCards.map(c => (
        <div key={c.label} className={styles.patientFeatureCard} onClick={() => navigate(c.path)} style={{ borderLeftColor: c.color }}>
          <span className={styles.patientFeatureIcon}>{c.icon}</span>
          <span className={styles.patientFeatureLabel}>{c.label}</span>
        </div>
      ))}
    </div>

    <div className={styles.sectionTitle}>Account</div>
    <div className={styles.patientGrid}>
      {accountCards.map(c => (
        <div key={c.label} className={styles.patientFeatureCard} onClick={() => navigate(c.path)} style={{ borderLeftColor: c.color }}>
          <span className={styles.patientFeatureIcon}>{c.icon}</span>
          <span className={styles.patientFeatureLabel}>{c.label}</span>
        </div>
      ))}
    </div>
  </div>)
}

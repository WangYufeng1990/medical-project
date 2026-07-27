import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getDashboardStats } from '../../api/dashboard'
import { hasAnyRole } from '../../utils/auth'
import styles from './style.module.css'

const statCards = [
  { label: 'Total Patients', key: 'totalPatients', path: '/patients', color: '#6366f1' },
  { label: 'Appointments Today', key: 'todayAppointments', path: '/appointments', color: '#10b981' },
  { label: 'Pending Bills', key: 'pendingBills', path: '/billing', color: '#f59e0b' },
  { label: 'Monthly Rx', key: 'monthlyPrescriptions', path: '/prescriptions', color: '#ef4444' },
]

const clinicalCards = [
  { label: 'Vital Signs', path: '/lab', icon: '🫀', color: '#10b981' },
  { label: 'Problem List', path: '/patients', icon: '📋', color: '#6366f1' },
  { label: 'Immunizations', path: '/patients', icon: '💉', color: '#8b5cf6' },
  { label: 'Care Plans', path: '/patients', icon: '📝', color: '#06b6d4' },
  { label: 'LOINC Catalog', path: '/loinc', icon: '🔬', color: '#64748b' },
]

const workflowCards = [
  { label: 'Referrals', path: '/referrals', icon: '🏥', color: '#3b82f6' },
  { label: 'Prior Auths', path: '/prior-auths', icon: '📄', color: '#f59e0b' },
  { label: 'Superbill', path: '/billing', icon: '🧾', color: '#10b981' },
  { label: 'Refill Requests', path: '/prescriptions', icon: '💊', color: '#ef4444' },
]

const adminCards = [
  { label: 'Emergency Access', path: '/emergency', icon: '🚨', color: '#ef4444', roles: ['ADMIN'] },
  { label: 'eCQM Quality', path: '/system/quality', icon: '📈', color: '#8b5cf6', roles: ['ADMIN'] },
  { label: 'Key Management', path: '/system/keys', icon: '🔐', color: '#f59e0b', roles: ['ADMIN'] },
  { label: 'Audit Logs', path: '/audit-logs', icon: '🛡', color: '#64748b', roles: ['ADMIN'] },
]

function FeatureCard({ c }: { c: any }) {
  const navigate = useNavigate()
  return (
    <div className={`${styles.card} ${styles.featureCard}`} onClick={() => navigate(c.path)} style={{ borderLeftColor: c.color }}>
      <div className={styles.featureIcon}>{c.icon}</div>
      <div className={styles.featureLabel}>{c.label}</div>
    </div>
  )
}

function FeatureSection({ title, cards }: { title: string; cards: any[] }) {
  return (
    <>
      <div className={styles.sectionTitle}>{title}</div>
      <div className={styles.grid}>
        {cards.filter(c => !c.roles || hasAnyRole(c.roles)).map(c => (
          <FeatureCard key={c.label} c={c} />
        ))}
      </div>
    </>
  )
}

export default function Dashboard() {
  const navigate = useNavigate()
  const { data: stats } = useQuery({
    queryKey: ['dashboard', 'stats'],
    queryFn: () => getDashboardStats(),
  })

  return (
    <div>
      <h2 style={{ fontSize: 24, fontWeight: 700, color: '#111827', marginBottom: 24 }}>Dashboard</h2>

      <div className={styles.sectionTitle}>Overview</div>
      <div className={styles.grid}>
        {statCards.map((c, i) => (
          <div key={c.label} className={`${styles.card} ${styles.statCard}`} onClick={() => navigate(c.path)} style={{ borderLeftColor: c.color }}>
            <div className={styles.label}>{c.label}</div>
            <div className={styles.value}>{stats ? (stats as any)[c.key] ?? 0 : 0}</div>
          </div>
        ))}
      </div>

      <FeatureSection title="Clinical" cards={clinicalCards} />
      <FeatureSection title="Workflow" cards={workflowCards} />
      <FeatureSection title="Administration" cards={adminCards} />
    </div>
  )
}

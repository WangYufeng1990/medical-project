import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getDashboardStats } from '../../api/dashboard'
import { hasAnyRole } from '../../utils/auth'
import styles from './style.module.css'

const statCards = [
  { label: 'Total Patients', key: 'totalPatients', path: '/patients', color: '#409EFF' },
  { label: 'Today Appointments', key: 'todayAppointments', path: '/appointments', color: '#67C23A' },
  { label: 'Pending Bills', key: 'pendingBills', path: '/billing', color: '#E6A23C' },
  { label: 'Monthly Rx', key: 'monthlyPrescriptions', path: '/prescriptions', color: '#F56C6C' },
]

const clinicalCards = [
  { label: 'Vital Signs', path: '/lab', icon: '🫀' },
  { label: 'Problem List', path: '/patients', icon: '📋' },
  { label: 'Immunizations', path: '/patients', icon: '💉' },
  { label: 'Care Plans', path: '/patients', icon: '📝' },
  { label: 'LOINC Catalog', path: '/loinc', icon: '🔬' },
]

const workflowCards = [
  { label: 'Referrals', path: '/referrals', icon: '🏥' },
  { label: 'Prior Auths', path: '/prior-auths', icon: '📄' },
  { label: 'Superbill', path: '/billing', icon: '🧾' },
  { label: 'Refill Requests', path: '/prescriptions', icon: '💊' },
]

const adminCards = [
  { label: 'Emergency Access', path: '/emergency', icon: '🚨', roles: ['ADMIN'] },
  { label: 'eCQM Quality', path: '/system/quality', icon: '📈', roles: ['ADMIN'] },
  { label: 'Key Management', path: '/system/keys', icon: '🔐', roles: ['ADMIN'] },
  { label: 'Audit Logs', path: '/audit-logs', icon: '🛡', roles: ['ADMIN'] },
]

export default function Dashboard() {
  const navigate = useNavigate()

  const { data: stats } = useQuery({
    queryKey: ['dashboard', 'stats'],
    queryFn: () => getDashboardStats(),
  })

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Dashboard</h2>

      <h3 style={{ fontSize: 14, color: '#606266', marginBottom: 12 }}>Overview</h3>
      <div className={styles.grid}>
        {statCards.map((c, i) => (
          <div key={c.label} className={styles.card} onClick={() => navigate(c.path)}
            style={{ borderTopColor: c.color }}>
            <div className={styles.label}>{c.label}</div>
            <div className={styles.value}>{stats ? (stats as any)[c.key] ?? 0 : 0}</div>
          </div>
        ))}
      </div>

      <h3 style={{ fontSize: 14, color: '#606266', margin: '24px 0 12px' }}>Clinical</h3>
      <div className={styles.grid}>
        {clinicalCards.map(c => (
          <div key={c.label} className={styles.card} onClick={() => navigate(c.path)}
            style={{ borderTopColor: '#67C23A', cursor: 'pointer' }}>
            <div className={styles.label}>{c.icon} {c.label}</div>
          </div>
        ))}
      </div>

      <h3 style={{ fontSize: 14, color: '#606266', margin: '24px 0 12px' }}>Workflow</h3>
      <div className={styles.grid}>
        {workflowCards.map(c => (
          <div key={c.label} className={styles.card} onClick={() => navigate(c.path)}
            style={{ borderTopColor: '#409EFF', cursor: 'pointer' }}>
            <div className={styles.label}>{c.icon} {c.label}</div>
          </div>
        ))}
      </div>

      <h3 style={{ fontSize: 14, color: '#606266', margin: '24px 0 12px' }}>Administration</h3>
      <div className={styles.grid}>
        {adminCards.filter(c => hasAnyRole(c.roles)).map(c => (
          <div key={c.label} className={styles.card} onClick={() => navigate(c.path)}
            style={{ borderTopColor: '#E6A23C', cursor: 'pointer' }}>
            <div className={styles.label}>{c.icon} {c.label}</div>
          </div>
        ))}
      </div>
    </div>
  )
}

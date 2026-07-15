import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getDashboardStats } from '../../api/dashboard'
import styles from './style.module.css'

const cards = [
  { label: 'Total Patients', path: '/patients', color: '#409EFF' },
  { label: 'Today Appointments', path: '/appointments', color: '#67C23A' },
  { label: 'Pending Bills', path: '/billing', color: '#E6A23C' },
  { label: 'Monthly Prescriptions', path: '/prescriptions', color: '#F56C6C' },
]

export default function Dashboard() {
  const navigate = useNavigate()

  const { data: stats } = useQuery({
    queryKey: ['dashboard', 'stats'],
    queryFn: () => getDashboardStats(),
  })

  const values: (number | string)[] = stats
    ? [stats.totalPatients, stats.todayAppointments, stats.pendingBills, stats.monthlyPrescriptions]
    : [0, 0, 0, 0]

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Dashboard</h2>
      <div className={styles.grid}>
        {cards.map((c, i) => (
          <div key={c.label} className={styles.card} onClick={() => navigate(c.path)}
            style={{ borderTopColor: c.color }}>
            <div className={styles.label}>{c.label}</div>
            <div className={styles.value}>{values[i]}</div>
          </div>
        ))}
      </div>
    </div>
  )
}

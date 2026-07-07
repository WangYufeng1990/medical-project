import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
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
  const [values, setValues] = useState<(number | string)[]>([0, 0, 0, 0])

  useEffect(() => {
    getDashboardStats().then(stats => {
      setValues([stats.totalPatients, stats.todayAppointments, stats.scheduledAppointments, stats.monthlyPrescriptions])
    }).catch(() => {})
  }, [])

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

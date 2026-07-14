import { useState, useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import patientRequest from '../../../api/patientRequest'

const cards = [
  { label: 'Appointments', path: '/patient/appointments', color: '#67C23A' },
  { label: 'Prescriptions', path: '/patient/prescriptions', color: '#F56C6C' },
  { label: 'Bills', path: '/patient/bills', color: '#E6A23C' },
]

export default function PatientDashboard() {
  const navigate = useNavigate()
  const location = useLocation()
  const [counts, setCounts] = useState<number[]>([0, 0, 0])
  const info = JSON.parse(localStorage.getItem('patientInfo') || '{}')

  useEffect(() => {
    Promise.all([
      patientRequest.get('/patient/me/appointments?page=1&size=1'),
      patientRequest.get('/patient/me/prescriptions?page=1&size=1'),
      patientRequest.get('/patient/me/bills?page=1&size=1'),
    ]).then(([a, p, b]) => {
      setCounts([a.data.data.total, p.data.data.total, b.data.data.total])
    }).catch(() => {})
  }, [location])

  return (<div>
    <h2>Welcome, {info.name || 'Patient'}</h2>
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 16, marginTop: 20 }}>
      {cards.map((c, i) => (
        <div key={c.label} onClick={() => navigate(c.path)}
          style={{ background: '#fff', padding: 20, borderRadius: 8, cursor: 'pointer', borderTop: `3px solid ${c.color}` }}>
          <div style={{ fontSize: 14, color: '#909399' }}>{c.label}</div>
          <div style={{ fontSize: 28, fontWeight: 700 }}>{counts[i]}</div>
        </div>
      ))}
    </div>
  </div>)
}

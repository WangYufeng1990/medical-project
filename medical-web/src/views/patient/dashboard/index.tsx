import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import patientRequest from '../../../api/patientRequest'

const cards = [
  { label: 'Appointments', path: '/patient/appointments', color: '#67C23A' },
  { label: 'Prescriptions', path: '/patient/prescriptions', color: '#F56C6C' },
  { label: 'Bills', path: '/patient/bills', color: '#E6A23C' },
]

export default function PatientDashboard() {
  const navigate = useNavigate()
  const info = JSON.parse(localStorage.getItem('patientInfo') || '{}')

  const fetchCount = (url: string) => patientRequest.get(url).then(r => r.data.data)

  const { data: aptData } = useQuery({
    queryKey: ['me', 'appointments', 'list', { page: 1, size: 1 }],
    queryFn: () => fetchCount('/patient/me/appointments?page=1&size=1'),
  })
  const { data: rxData } = useQuery({
    queryKey: ['me', 'prescriptions', 'list', { page: 1, size: 1 }],
    queryFn: () => fetchCount('/patient/me/prescriptions?page=1&size=1'),
  })
  const { data: billData } = useQuery({
    queryKey: ['me', 'bills', 'list', { page: 1, size: 1 }],
    queryFn: () => fetchCount('/patient/me/bills?page=1&size=1'),
  })

  const counts = [aptData?.total ?? 0, rxData?.total ?? 0, billData?.total ?? 0]

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

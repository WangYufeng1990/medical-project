import { Outlet, useNavigate, useLocation, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import patientRequest from '../../../api/patientRequest'
import { useIdleTimeout } from '../../../utils/useIdleTimeout'

const items = [
  { path: '/patient/dashboard', label: 'Dashboard' },
  { path: '/patient/profile', label: 'Profile' },
  { path: '/patient/appointments', label: 'Appointments' },
  { path: '/patient/prescriptions', label: 'Prescriptions' },
  { path: '/patient/lab', label: 'Lab Results' },
  { path: '/patient/bills', label: 'Bills' },
  { path: '/patient/chat', label: 'Messages' },
]

export default function PatientLayout() {
  const navigate = useNavigate()
  const loc = useLocation()
  const cachedInfo = JSON.parse(localStorage.getItem('patientInfo') || '{}')
  const { data: profile } = useQuery({
    queryKey: ['me', 'profile'],
    queryFn: () => patientRequest.get('/patient/me').then(r => r),
    staleTime: 60_000,
  })
  const info = profile || cachedInfo

  const handleLogout = () => { localStorage.removeItem('patientToken'); localStorage.removeItem('patientRefreshToken'); localStorage.removeItem('patientInfo'); navigate('/patient/login') }
  useIdleTimeout(handleLogout)

  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      <aside style={{ width: 200, background: '#1f2937', color: '#fff', padding: 20 }}>
        <h3 style={{ marginBottom: 16 }}>{info.name || 'Patient'}</h3>
        {items.map(i => (
          <Link key={i.path} to={i.path} style={{ padding: '8px 0', cursor: 'pointer', color: loc.pathname === i.path ? '#409EFF' : '#d1d5db', textDecoration: 'none', display: 'block' }}
            >{i.label}</Link>
        ))}
        <div style={{ marginTop: 24, padding: '8px 0', color: '#93c5fd', cursor: 'pointer', fontSize: 14 }}
          onClick={async () => {
            try {
              const res = await patientRequest.get('/patient/me/export')
              const exportData = res
              const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' })
              const url = URL.createObjectURL(blob)
              const date = new Date().toISOString().slice(0, 10)
              const a = document.createElement('a'); a.href = url; a.download = `health-data-${date}.json`; a.click()
              URL.revokeObjectURL(url)
            } catch { alert('Export failed') }
          }}>📥 Export My Data</div>
        <div style={{ marginTop: 8, color: '#fca5a5', cursor: 'pointer' }} onClick={async () => { try { await patientRequest.post('/patient/logout') } catch {}; localStorage.removeItem('patientToken'); localStorage.removeItem('patientRefreshToken'); localStorage.removeItem('patientInfo'); navigate('/patient/login') }}>
          Logout</div>
      </aside>
      <main style={{ flex: 1, padding: 24, background: '#f5f7fa' }}><Outlet /></main>
    </div>
  )
}

import { Outlet, useNavigate, useLocation } from 'react-router-dom'

const items = [
  { path: '/patient/dashboard', label: 'Dashboard' },
  { path: '/patient/profile', label: 'Profile' },
  { path: '/patient/appointments', label: 'Appointments' },
  { path: '/patient/prescriptions', label: 'Prescriptions' },
  { path: '/patient/bills', label: 'Bills' },
]

export default function PatientLayout() {
  const navigate = useNavigate()
  const loc = useLocation()
  const info = JSON.parse(localStorage.getItem('patientInfo') || '{}')

  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      <aside style={{ width: 200, background: '#1f2937', color: '#fff', padding: 20 }}>
        <h3 style={{ marginBottom: 16 }}>{info.name || 'Patient'}</h3>
        {items.map(i => (
          <div key={i.path} style={{ padding: '8px 0', cursor: 'pointer', color: loc.pathname === i.path ? '#409EFF' : '#d1d5db' }}
            onClick={() => navigate(i.path)}>{i.label}</div>
        ))}
        <div style={{ marginTop: 24, color: '#fca5a5', cursor: 'pointer' }} onClick={() => { localStorage.removeItem('patientToken'); navigate('/patient/login') }}>
          Logout</div>
      </aside>
      <main style={{ flex: 1, padding: 24, background: '#f5f7fa' }}><Outlet /></main>
    </div>
  )
}

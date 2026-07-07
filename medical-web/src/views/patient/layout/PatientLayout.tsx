import { Outlet, useNavigate, useLocation } from 'react-router-dom'

const items = [
  { path: '/patient/dashboard', label: 'Dashboard' },
  { path: '/patient/profile', label: 'Profile' },
  { path: '/patient/appointments', label: 'Appointments' },
  { path: '/patient/prescriptions', label: 'Prescriptions' },
  { path: '/patient/bills', label: 'Bills' },
  { path: '/patient/chat', label: 'Messages' },
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
        <div style={{ marginTop: 24, padding: '8px 0', color: '#93c5fd', cursor: 'pointer', fontSize: 14 }}
          onClick={async () => {
            const token = localStorage.getItem('patientToken')
            try {
              const res = await fetch('/api/v1/patient/me/export', { headers: { Authorization: `Bearer ${token}` } })
              const blob = await res.blob()
              const url = URL.createObjectURL(blob)
              const a = document.createElement('a'); a.href = url; a.download = 'my-health-data.json'; a.click()
              URL.revokeObjectURL(url)
            } catch { alert('Export failed') }
          }}>📥 Export My Data</div>
        <div style={{ marginTop: 8, color: '#fca5a5', cursor: 'pointer' }} onClick={() => { localStorage.removeItem('patientToken'); navigate('/patient/login') }}>
          Logout</div>
      </aside>
      <main style={{ flex: 1, padding: 24, background: '#f5f7fa' }}><Outlet /></main>
    </div>
  )
}

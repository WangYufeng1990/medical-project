import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import patientRequest from '../../../api/patientRequest'

const overviewCards = [
  { label: 'Appointments', key: 'apt', path: '/patient/appointments', color: '#67C23A' },
  { label: 'Prescriptions', key: 'rx', path: '/patient/prescriptions', color: '#F56C6C' },
  { label: 'Bills', key: 'bill', path: '/patient/bills', color: '#E6A23C' },
]

const healthCards = [
  { label: '🫀 Vital Signs', path: '/patient/vitals' },
  { label: '📋 Problem List', path: '/patient/problems' },
  { label: '💉 Immunizations', path: '/patient/immunizations' },
  { label: '🧪 Lab Results', path: '/patient/lab' },
  { label: '📝 Care Plans', path: '/patient/care-plans' },
]

const accountCards = [
  { label: '🏥 Referrals', path: '/patient/referrals' },
  { label: '📄 Prior Auths', path: '/patient/prior-auths' },
  { label: '✍️ Consent', path: '/patient/consent' },
  { label: '🛡 Disclosures', path: '/patient/disclosures' },
]

export default function PatientDashboard() {
  const navigate = useNavigate()
  const info = JSON.parse(localStorage.getItem('patientInfo') || '{}')

  const fetchCount = (url: string) => patientRequest.get(url).then(r => r.data.data)
  const { data: aptData } = useQuery({ queryKey: ['me', 'apt-count'], queryFn: () => fetchCount('/patient/me/appointments?page=1&size=1') })
  const { data: rxData } = useQuery({ queryKey: ['me', 'rx-count'], queryFn: () => fetchCount('/patient/me/prescriptions?page=1&size=1') })
  const { data: billData } = useQuery({ queryKey: ['me', 'bill-count'], queryFn: () => fetchCount('/patient/me/bills?page=1&size=1') })
  const counts: any = { apt: aptData?.total ?? 0, rx: rxData?.total ?? 0, bill: billData?.total ?? 0 }

  return (<div>
    <h2 style={{ marginBottom: 20 }}>Welcome, {info.name || 'Patient'}</h2>

    <h3 style={{ fontSize: 14, color: '#606266', marginBottom: 12 }}>Overview</h3>
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 16, marginBottom: 24 }}>
      {overviewCards.map(c => (
        <div key={c.label} onClick={() => navigate(c.path)}
          style={{ background: '#fff', padding: 20, borderRadius: 8, cursor: 'pointer', borderTop: `3px solid ${c.color}` }}>
          <div style={{ fontSize: 14, color: '#909399', marginBottom: 8 }}>{c.label}</div>
          <div style={{ fontSize: 28, fontWeight: 700 }}>{counts[c.key]}</div>
        </div>
      ))}
    </div>

    <h3 style={{ fontSize: 14, color: '#606266', margin: '24px 0 12px' }}>Health Records</h3>
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 16, marginBottom: 24 }}>
      {healthCards.map(c => (
        <div key={c.label} onClick={() => navigate(c.path)}
          style={{ background: '#fff', padding: 18, borderRadius: 8, cursor: 'pointer', borderTop: '3px solid #67C23A' }}>
          <div style={{ fontSize: 14, color: '#606266' }}>{c.label}</div>
        </div>
      ))}
    </div>

    <h3 style={{ fontSize: 14, color: '#606266', margin: '24px 0 12px' }}>Account</h3>
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 16 }}>
      {accountCards.map(c => (
        <div key={c.label} onClick={() => navigate(c.path)}
          style={{ background: '#fff', padding: 18, borderRadius: 8, cursor: 'pointer', borderTop: '3px solid #409EFF' }}>
          <div style={{ fontSize: 14, color: '#606266' }}>{c.label}</div>
        </div>
      ))}
    </div>
  </div>)
}

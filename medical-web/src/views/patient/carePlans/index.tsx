import { useQuery } from '@tanstack/react-query'
import patientRequest from '../../../api/patientRequest'
import styles from '../../shared.module.css'

export default function PatientCarePlans() {
  const { data, isLoading } = useQuery({
    queryKey: ['me', 'care-plans'],
    queryFn: () => patientRequest.get('/patient/me/care-plans').then(r => r ?? []),
  })
  if (isLoading) return <p style={{ color: '#909399' }}>Loading...</p>
  return (<div>
    <h2 style={{ marginBottom: 20 }}>Care Plans</h2>
    {(!data || data.length === 0) && <p style={{ color: '#909399' }}>No care plans.</p>}
    <table className={styles.table}>
      <thead><tr><th>Plan</th><th>Goal</th><th>Interventions</th><th>Period</th><th>Status</th></tr></thead>
      <tbody>{(data ?? []).map((cp: any) => (
        <tr key={cp.id} style={{ opacity: cp.status === 'COMPLETED' ? 0.5 : 1 }}>
          <td style={{ textDecoration: cp.status === 'COMPLETED' ? 'line-through' : 'none' }}>{cp.title}</td>
          <td>{cp.goal || '-'}</td>
          <td style={{ maxWidth: 300, fontSize: 12 }}>{cp.interventions || '-'}</td>
          <td style={{ fontSize: 11 }}>{cp.startDate}{cp.targetDate ? ` → ${cp.targetDate}` : ''}</td>
          <td><span style={{ color: cp.status === 'ACTIVE' ? '#67C23A' : '#909399', fontWeight: 600, fontSize: 12 }}>{cp.status}</span></td>
        </tr>
      ))}</tbody>
    </table>
  </div>)
}

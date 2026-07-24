import { useQuery } from '@tanstack/react-query'
import patientRequest from '../../../api/patientRequest'
import styles from '../../shared.module.css'

const URGENCY_COLOR: any = { ROUTINE: '#67C23A', URGENT: '#E6A23C', STAT: '#F56C6C' }
const STATUS_COLOR: any = { PENDING: '#E6A23C', SCHEDULED: '#409EFF', COMPLETED: '#67C23A', CLOSED: '#909399' }

export default function PatientReferrals() {
  const { data, isLoading } = useQuery({
    queryKey: ['me', 'referrals'],
    queryFn: () => patientRequest.get('/patient/me/referrals').then(r => r.data.data ?? []),
  })

  if (isLoading) return <p style={{ color: '#909399' }}>Loading...</p>

  return (<div>
    <h2 style={{ marginBottom: 20 }}>My Referrals</h2>
    {(!data || data.length === 0) && <p style={{ color: '#909399' }}>No referrals.</p>}
    <table className={styles.table}>
      <thead><tr><th>Date</th><th>Specialist</th><th>Specialty</th><th>Diagnosis</th><th>Urgency</th><th>Status</th><th>Appointment</th></tr></thead>
      <tbody>{(data ?? []).map(r => (
        <tr key={r.id}>
          <td>{r.referralDate}</td><td>{r.specialistName}</td><td>{r.specialty || '-'}</td><td>{r.diagnosis || r.reason || '-'}</td>
          <td><span style={{ color: URGENCY_COLOR[r.urgency] || '#909399', fontWeight: 600 }}>{r.urgency}</span></td>
          <td><span style={{ color: STATUS_COLOR[r.status] || '#909399', fontWeight: 600 }}>{r.status}</span></td>
          <td>{r.appointmentDate ?? '-'}</td>
        </tr>
      ))}</tbody>
    </table>
  </div>)
}

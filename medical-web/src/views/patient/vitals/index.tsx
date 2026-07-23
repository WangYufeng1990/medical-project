import { useQuery } from '@tanstack/react-query'
import patientRequest from '../../../api/patientRequest'
import styles from '../../shared.module.css'

export default function PatientVitals() {
  const { data: vitals, isLoading } = useQuery({
    queryKey: ['me', 'vitals'],
    queryFn: () => patientRequest.get('/patient/me/vitals').then(r => r.data.data ?? []),
  })

  if (isLoading) return <p style={{ color: '#909399' }}>Loading...</p>

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Vital Signs</h2>
      {(!vitals || vitals.length === 0) && <p style={{ color: '#909399' }}>No vital signs recorded.</p>}
      <table className={styles.table}>
        <thead><tr><th>Date</th><th>BP</th><th>HR</th><th>Temp</th><th>RR</th><th>O₂</th><th>BMI</th><th>Notes</th></tr></thead>
        <tbody>
          {(vitals ?? []).map((v: any) => (
            <tr key={v.id}>
              <td>{v.recordedAt?.substring(0, 16)}</td>
              <td>{v.systolicBp != null ? `${v.systolicBp}/${v.diastolicBp}` : '-'}</td>
              <td>{v.heartRate ?? '-'}</td>
              <td>{v.temperature != null ? `${v.temperature}°C` : '-'}</td>
              <td>{v.respiratoryRate ?? '-'}</td>
              <td>{v.oxygenSaturation != null ? `${v.oxygenSaturation}%` : '-'}</td>
              <td>{v.bmi ?? '-'}</td>
              <td style={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis' }}>{v.notes ?? ''}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

import { useQuery } from '@tanstack/react-query'
import patientRequest from '../../../api/patientRequest'
import styles from '../../shared.module.css'

export default function PatientProblems() {
  const { data: problems, isLoading } = useQuery({
    queryKey: ['me', 'problems'],
    queryFn: () => patientRequest.get('/patient/me/problems').then(r => r.data.data ?? []),
  })

  if (isLoading) return <p style={{ color: '#909399' }}>Loading...</p>

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Problem List</h2>
      {(!problems || problems.length === 0) && <p style={{ color: '#909399' }}>No active problems.</p>}
      <table className={styles.table}>
        <thead><tr><th>Diagnosis</th><th>SNOMED</th><th>ICD-10</th><th>Severity</th><th>Status</th><th>Onset</th><th>Resolved</th></tr></thead>
        <tbody>
          {(problems ?? []).map((p: any) => (
            <tr key={p.id} style={{ opacity: p.status === 'RESOLVED' ? 0.5 : 1 }}>
              <td style={{ textDecoration: p.status === 'RESOLVED' ? 'line-through' : 'none' }}>{p.snomedDisplay || 'Unspecified'}</td>
              <td style={{ fontSize: 11, color: '#909399' }}>{p.snomedCode || '-'}</td>
              <td style={{ fontSize: 11, color: '#909399' }}>{p.icd10Code || '-'}</td>
              <td><span style={{ color: p.severity === 'SEVERE' ? '#F56C6C' : p.severity === 'MODERATE' ? '#E6A23C' : '#67C23A', fontWeight: 600, fontSize: 12 }}>{p.severity}</span></td>
              <td><span style={{ color: p.status === 'ACTIVE' ? '#67C23A' : '#909399', fontWeight: 600, fontSize: 12 }}>{p.status}</span></td>
              <td>{p.onsetDate ?? '-'}</td>
              <td>{p.resolutionDate ?? '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

import { useQuery } from '@tanstack/react-query'
import patientRequest from '../../../api/patientRequest'
import styles from '../../shared.module.css'

const STATUS_COLOR: any = { completed: '#67C23A', refused: '#F56C6C', contraindicated: '#E6A23C' }

export default function PatientImmunizations() {
  const { data, isLoading } = useQuery({
    queryKey: ['me', 'immunizations'],
    queryFn: () => patientRequest.get('/patient/me/immunizations').then(r => r ?? []),
  })

  if (isLoading) return <p style={{ color: '#909399' }}>Loading...</p>

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Immunizations</h2>
      {(!data || data.length === 0) && <p style={{ color: '#909399' }}>No immunizations recorded.</p>}
      <table className={styles.table}>
        <thead><tr><th>Vaccine</th><th>CVX</th><th>Date</th><th>Dose</th><th>Site</th><th>Lot #</th><th>Manufacturer</th><th>Status</th></tr></thead>
        <tbody>
          {(data ?? []).map((r: any) => (
            <tr key={r.id}>
              <td>{r.vaccineName}</td>
              <td style={{ fontSize: 11, color: '#909399' }}>{r.cvxCode || '-'}</td>
              <td>{r.administrationDate ?? '-'}</td>
              <td>{r.doseNumber || '-'}</td>
              <td>{r.site || '-'} {r.route ? `(${r.route})` : ''}</td>
              <td style={{ fontSize: 11 }}>{r.lotNumber || '-'}</td>
              <td style={{ fontSize: 11 }}>{r.manufacturer || '-'}</td>
              <td><span style={{ color: STATUS_COLOR[r.status] || '#909399', fontWeight: 600, fontSize: 12 }}>{r.status}</span></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

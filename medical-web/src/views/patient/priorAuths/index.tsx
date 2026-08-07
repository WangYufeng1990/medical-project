import { useQuery } from '@tanstack/react-query'
import { http } from '../../../api/patientRequest'
import { PriorAuthVO } from '../../../types/entities'
import styles from '../../shared.module.css'

const STATUS_COLOR: Record<string, string> = { PENDING: '#E6A23C', APPROVED: '#67C23A', DENIED: '#F56C6C' }

export default function PatientPriorAuths() {
  const { data, isLoading } = useQuery({
    queryKey: ['me', 'prior-auths'],
    queryFn: () => http.get<PriorAuthVO[]>('/patient/me/prior-auths'),
  })
  if (isLoading) return <p style={{ color: '#909399' }}>Loading...</p>
  return (<div>
    <h2 style={{ marginBottom: 20 }}>Prior Authorizations</h2>
    {(!data || data.length === 0) && <p style={{ color: '#909399' }}>No prior authorizations.</p>}
    <table className={styles.table}>
      <thead><tr><th>Type</th><th>Item</th><th>Insurance</th><th>Status</th><th>Requested</th><th>Auth #</th></tr></thead>
      <tbody>{(data ?? []).map(pa => (
        <tr key={pa.id}><td>{pa.authType}</td><td>{pa.itemName}</td><td>{pa.insurancePayer || '-'}</td>
          <td><span style={{ color: STATUS_COLOR[pa.status || ''] || '#909399', fontWeight: 600 }}>{pa.status}</span></td>
          <td>{pa.requestedAt}</td><td>{pa.authNumber || '-'}</td>
        </tr>
      ))}</tbody>
    </table>
  </div>)
}

import { useQuery } from '@tanstack/react-query'
import patientRequest from '../../../api/patientRequest'
import { CONSENT_STATUS_COLOR } from '../../../utils/labels'
import styles from '../../shared.module.css'

export default function PatientConsent() {
  const { data, isLoading } = useQuery({
    queryKey: ['me', 'consent'],
    queryFn: () => patientRequest.get('/patient/me/consent').then(r => r ?? []),
  })
  const list = data ?? []

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>My Consents</h2>
      {isLoading ? (
        <div style={{ color: '#909399', padding: 20 }}>Loading...</div>
      ) : (
        <table className={styles.table}>
          <thead><tr><th>ID</th><th>Type</th><th>Scope</th><th>Status</th><th>Signed At</th></tr></thead>
          <tbody>
            {list.map(c => (
              <tr key={c.id}>
                <td>{c.id}</td>
                <td>{c.consentType}</td>
                <td>{c.scope}</td>
                <td><span style={{ color: CONSENT_STATUS_COLOR[c.status] ?? '#909399', fontWeight: 600 }}>{c.status}</span></td>
                <td>{c.consentDate ?? c.createTime}</td>
              </tr>
            ))}
            {list.length === 0 && (
              <tr><td colSpan={5} style={{ textAlign: 'center', color: '#909399', padding: 20 }}>No consent records</td></tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  )
}

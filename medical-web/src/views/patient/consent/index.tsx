import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { http } from '../../../api/patientRequest'
import { ConsentVO } from '../../../types/entities'
import { useConfirm } from '../../../utils/ConfirmDialog'
import { CONSENT_STATUS_COLOR, CONSENT_TYPE_LABELS } from '../../../utils/labels'
import styles from '../../shared.module.css'

export default function PatientConsent() {
  const queryClient = useQueryClient()
  const { confirm } = useConfirm()

  const { data, isLoading } = useQuery({
    queryKey: ['me', 'consent'],
    queryFn: () => http.get<ConsentVO[]>('/patient/me/consent'),
  })
  const list = data ?? []

  const revokeMutation = useMutation({
    mutationFn: (id: number) => http.put(`/patient/me/consent/${id}/revoke`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'consent'] }),
    onError: (err: Error) => alert(err?.message || 'Revoke failed'),
  })

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>My Consents</h2>
      {isLoading ? (
        <div style={{ color: '#909399', padding: 20 }}>Loading...</div>
      ) : (
        <table className={styles.table}>
          <thead><tr><th>ID</th><th>Type</th><th>Scope</th><th>Status</th><th>Signed At</th><th></th></tr></thead>
          <tbody>
            {list.map(c => (
              <tr key={c.id}>
                <td>{c.id}</td>
                <td>{CONSENT_TYPE_LABELS[c.consentType] || c.consentType}</td>
                <td>{c.scope}</td>
                <td><span style={{ color: CONSENT_STATUS_COLOR[c.status ?? ''] ?? '#909399', fontWeight: 600 }}>{c.status}</span></td>
                <td>{c.consentDate ?? c.createTime}</td>
                <td>
                  {c.status === 'active' && (
                    <button className={styles.btnSmDanger} disabled={revokeMutation.isPending}
                      onClick={async () => { if (await confirm('Revoke this consent?')) revokeMutation.mutate(c.id) }}>
                      Revoke
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {list.length === 0 && (
              <tr><td colSpan={6} style={{ textAlign: 'center', color: '#909399', padding: 20 }}>No consent records</td></tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  )
}

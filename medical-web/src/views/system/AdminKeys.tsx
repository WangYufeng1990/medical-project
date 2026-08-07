import { useState, FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getKeyHistory, getRotationStatus, rotateKey } from '../../api/key'
import { KeyHistoryEntry } from '../../types/entities'
import styles from '../shared.module.css'

export default function AdminKeys() {
  const queryClient = useQueryClient()
  const [showRotate, setShowRotate] = useState(false)
  const [rotateForm, setRotateForm] = useState({ newKey: '', oldKey: '' })

  const { data: history, isLoading } = useQuery({
    queryKey: ['keys', 'history'],
    queryFn: () => getKeyHistory().then(r => r ?? []),
  })

  const { data: status } = useQuery({
    queryKey: ['keys', 'status'],
    queryFn: () => getRotationStatus(),
  })

  const rotateMutation = useMutation({
    mutationFn: (data: { newKey: string; oldKey: string }) => rotateKey(data),
    onSuccess: () => { setShowRotate(false); queryClient.invalidateQueries({ queryKey: ['keys'] }) },
    onError: () => alert('Key rotation failed. Check that keys are correct.'),
  })

  const handleRotate = (e: FormEvent) => {
    e.preventDefault()
    if (rotateForm.newKey && rotateForm.oldKey) rotateMutation.mutate(rotateForm)
  }

  if (isLoading) return <p style={{ color: '#909399' }}>Loading...</p>
  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Key Management</h2>

      <div style={{ display: 'flex', gap: 12, marginBottom: 20, flexWrap: 'wrap' }}>
        <button className={styles.btnPrimary} onClick={() => { setRotateForm({ newKey: '', oldKey: '' }); setShowRotate(true) }}
          disabled={!!(status?.running)}>
          {status?.running ? 'Rotation in progress...' : 'Rotate Key'}
        </button>
      </div>

      {status && (
        <div style={{ marginBottom: 20, padding: 12, background: status.rotationActive ? '#fef0f0' : '#f0f9eb', borderRadius: 8, border: `1px solid ${status.rotationActive ? '#fde2e2' : '#e1f3d8'}` }}>
          <span style={{ fontWeight: 600, color: status.rotationActive ? '#f56c6c' : '#67c23a' }}>
            {status.rotationActive ? '⚠ Rotation Active' : '✓ Single-Key Mode'}
          </span>
          {status.running && <span style={{ marginLeft: 12, color: '#e6a23c' }}>Job running...</span>}
          {status.complete && <span style={{ marginLeft: 12, color: '#67c23a' }}>Migration complete</span>}
          {status.remainingByTable && Object.keys(status.remainingByTable).length > 0 && (
            <div style={{ marginTop: 8, fontSize: 13, color: '#606266' }}>
              Remaining legacy rows:
              {Object.entries(status.remainingByTable).map(([table, count]) => (
                <span key={table} style={{ marginLeft: 12 }}>{table}: <strong>{count as number}</strong></span>
              ))}
            </div>
          )}
        </div>
      )}

      <h3 style={{ marginBottom: 12 }}>Key Lifecycle History</h3>
      <table className={styles.table}>
        <thead><tr><th>ID</th><th>Event</th><th>Version</th><th>Detail</th><th>Time</th></tr></thead>
        <tbody>{(history ?? []).map((r: KeyHistoryEntry) => (
          <tr key={r.id}>
            <td>{r.id}</td>
            <td><span style={{ color: r.eventType === 'KEY_ROTATION' ? '#e6a23c' : '#67c23a', fontWeight: 600 }}>{r.eventType}</span></td>
            <td>{r.keyVersion}</td>
            <td style={{ maxWidth: 300, overflow: 'hidden', textOverflow: 'ellipsis' }}>{r.detail}</td>
            <td>{r.eventTime}</td>
          </tr>
        ))}</tbody>
      </table>

      {showRotate && <div className={styles.modalOverlay} onClick={() => setShowRotate(false)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>Rotate Encryption Key</h3>
        <form onSubmit={handleRotate} className={styles.formGrid}>
          <div className={styles.formGroup}><label>Old Key (current)</label><input type="password" value={rotateForm.oldKey} onChange={e => setRotateForm({ ...rotateForm, oldKey: e.target.value })} placeholder="Current AES key" /></div>
          <div className={styles.formGroup}><label>New Key</label><input type="password" value={rotateForm.newKey} onChange={e => setRotateForm({ ...rotateForm, newKey: e.target.value })} placeholder="New AES key (base64, 32 bytes)" /></div>
          <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => setShowRotate(false)}>Cancel</button><button type="submit" className={styles.btnPrimary} disabled={rotateMutation.isPending || !rotateForm.newKey || !rotateForm.oldKey}>Rotate</button></div>
        </form>
      </div></div>}
    </div>
  )
}

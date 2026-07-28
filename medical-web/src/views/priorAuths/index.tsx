import { useState, FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getPriorAuths, createPriorAuth, updatePriorAuth } from '../../api/priorAuth'
import { getPatientPage } from '../../api/patient'
import { PAGE_SIZE } from '../../utils/labels'
import styles from '../shared.module.css'

const STATUS_COLOR: any = { PENDING: '#E6A23C', APPROVED: '#67C23A', DENIED: '#F56C6C' }
const emptyForm: any = { patientId: '', authType: 'MEDICATION', itemName: '', itemCode: '', insurancePayer: '', notes: '' }

export default function PriorAuths() {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ ...emptyForm })

  const onError = (err: any) => alert(err?.message || 'Operation failed')

  const { data: pageData, isLoading } = useQuery({
    queryKey: ['priorAuths', 'list', { page, size: PAGE_SIZE }],
    queryFn: () => getPriorAuths({ page, size: PAGE_SIZE }),
  })
  const data = pageData?.records ?? []
  const total = pageData?.total ?? 0

  const { data: patients } = useQuery({
    queryKey: ['patients', 'all'],
    queryFn: () => getPatientPage({ page: 1, size: 999 }).then(r => r.records ?? []),
  })

  const createMutation = useMutation({
    mutationFn: (d: any) => createPriorAuth(d),
    onSuccess: () => { setShowForm(false); queryClient.invalidateQueries({ queryKey: ['priorAuths'] }) },
    onError,
  })

  const updateMutation = useMutation({
    mutationFn: (params: { id: number; data: any }) => updatePriorAuth(params.id, params.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['priorAuths'] }),
    onError,
  })

  const handleCreate = (e: FormEvent) => { e.preventDefault(); createMutation.mutate({ ...form, patientId: Number(form.patientId) }) }

  if (isLoading) return <p style={{ color: '#909399' }}>Loading...</p>
  return (<div>
    <h2 style={{ marginBottom: 20 }}>Prior Authorizations</h2>
    <button className={styles.btnPrimary} onClick={() => { setForm({ ...emptyForm }); setShowForm(true) }} style={{ marginBottom: 16 }}>+ New Prior Auth</button>
    <table className={styles.table}>
      <thead><tr><th>ID</th><th>Patient</th><th>Type</th><th>Item</th><th>Insurance</th><th>Status</th><th>Requested</th><th></th></tr></thead>
      <tbody>{data.map(r => (
        <tr key={r.id}>
          <td>{r.id}</td><td>{r.patientId}</td><td>{r.authType}</td><td>{r.itemName}{r.itemCode ? ` (${r.itemCode})` : ''}</td>
          <td>{r.insurancePayer || '-'}</td>
          <td><span style={{ color: STATUS_COLOR[r.status] || '#909399', fontWeight: 600 }}>{r.status}</span></td>
          <td>{r.requestedAt}</td>
          <td>
            {r.status === 'PENDING' && (
              <>
                <button className={styles.btnSm} onClick={() => { const n = prompt('Auth number:'); if (n) updateMutation.mutate({ id: r.id, data: { status: 'APPROVED', authNumber: n, resolvedAt: new Date().toISOString().slice(0, 10) } }) }}>Approve</button>
                <button className={styles.btnSmDanger} onClick={() => updateMutation.mutate({ id: r.id, data: { status: 'DENIED', resolvedAt: new Date().toISOString().slice(0, 10) } })}>Deny</button>
              </>
            )}
            {r.status === 'APPROVED' && <span style={{ fontSize: 11, color: '#909399' }}>Auth #{r.authNumber}</span>}
          </td>
        </tr>
      ))}</tbody>
    </table>
    <div className={styles.pagination}><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>

    {showForm && <div className={styles.modalOverlay} onClick={() => setShowForm(false)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>New Prior Authorization</h3>
      <form onSubmit={handleCreate} className={styles.formGrid}>
        <div className={styles.formGroup}><label>Patient</label><select value={form.patientId} onChange={e => setForm({ ...form, patientId: e.target.value })}><option value="">-- Select --</option>{(patients ?? []).map((p: any) => <option key={p.id} value={p.id}>{p.name}</option>)}</select></div>
        <div className={styles.formGroup}><label>Type</label><select value={form.authType} onChange={e => setForm({ ...form, authType: e.target.value })}><option value="MEDICATION">Medication</option><option value="PROCEDURE">Procedure</option><option value="IMAGING">Imaging</option></select></div>
        <div className={styles.formGroup}><label>Item Name</label><input value={form.itemName} onChange={e => setForm({ ...form, itemName: e.target.value })} placeholder="e.g. Lisinopril 20mg" /></div>
        <div className={styles.formGroup}><label>Item Code</label><input value={form.itemCode} onChange={e => setForm({ ...form, itemCode: e.target.value })} placeholder="e.g. 314076" /></div>
        <div className={styles.formGroup}><label>Insurance Payer</label><input value={form.insurancePayer} onChange={e => setForm({ ...form, insurancePayer: e.target.value })} /></div>
        <div className={styles.formGroup} style={{ gridColumn: 'span 2' }}><label>Notes</label><input value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} /></div>
        <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => setShowForm(false)}>Cancel</button><button type="submit" className={styles.btnPrimary}>Save</button></div>
      </form>
    </div></div>}
  </div>)
}

import { useState, FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getChargePage, createCharge, convertCharge } from '../../api/charge'
import { getPatientPage } from '../../api/patient'
import { PAGE_SIZE } from '../../utils/labels'
import styles from '../shared.module.css'

const emptyForm: any = { patientId: '', appointmentId: '', cptCodes: '', icd10Codes: '', chargeAmount: '', visitType: 'FOLLOW_UP', notes: '' }

export default function Charges() {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ ...emptyForm })

  const { data: pageData } = useQuery({
    queryKey: ['charges', 'list', { page, size: PAGE_SIZE }],
    queryFn: () => getChargePage({ page, size: PAGE_SIZE }),
  })
  const data = pageData?.records ?? []
  const total = pageData?.total ?? 0

  const { data: patients } = useQuery({
    queryKey: ['patients', 'all'],
    queryFn: () => getPatientPage({ page: 1, size: 999 }).then(r => r.records ?? []),
  })

  const createMutation = useMutation({
    mutationFn: (d: any) => createCharge(d),
    onSuccess: () => { setShowForm(false); queryClient.invalidateQueries({ queryKey: ['charges'] }) },
  })

  const convertMutation = useMutation({
    mutationFn: (id: number) => convertCharge(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['charges', 'billing'] }),
  })

  const handleCreate = (e: FormEvent) => {
    e.preventDefault()
    createMutation.mutate({
      ...form,
      patientId: Number(form.patientId),
      appointmentId: form.appointmentId ? Number(form.appointmentId) : undefined,
      chargeAmount: form.chargeAmount !== '' ? Number(form.chargeAmount) : undefined,
    })
  }

  return (<div>
    <h2 style={{ marginBottom: 20 }}>Charge Capture (Superbill)</h2>
    <button className={styles.btnPrimary} onClick={() => { setForm({ ...emptyForm }); setShowForm(true) }} style={{ marginBottom: 16 }}>+ New Charge</button>
    <table className={styles.table}>
      <thead><tr><th>ID</th><th>Patient</th><th>Appt</th><th>CPT</th><th>ICD-10</th><th>Amount</th><th>Status</th><th></th></tr></thead>
      <tbody>{data.map(r => (
        <tr key={r.id}>
          <td>{r.id}</td><td>{r.patientId}</td><td>{r.appointmentId ?? '-'}</td><td>{r.cptCodes || '-'}</td><td>{r.icd10Codes || '-'}</td>
          <td>{r.chargeAmount != null ? `$${r.chargeAmount}` : '-'}</td>
          <td><span style={{ color: r.status === 'DRAFT' ? '#E6A23C' : '#67C23A', fontWeight: 600 }}>{r.status}</span></td>
          <td>
            {r.status === 'DRAFT' && (
              <button className={styles.btnSm} disabled={convertMutation.isPending} onClick={() => { if (confirm('Convert to Bill?')) convertMutation.mutate(r.id) }}>
                Convert to Bill
              </button>
            )}
            {r.status === 'BILLED' && <span style={{ fontSize: 11, color: '#909399' }}>Bill #{r.billId}</span>}
          </td>
        </tr>
      ))}</tbody>
    </table>
    <div className={styles.pagination}><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>

    {showForm && <div className={styles.modalOverlay} onClick={() => setShowForm(false)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>New Charge (Superbill)</h3>
      <form onSubmit={handleCreate} className={styles.formGrid}>
        <div className={styles.formGroup}><label>Patient</label><select value={form.patientId} onChange={e => setForm({ ...form, patientId: e.target.value })}><option value="">-- Select --</option>{(patients ?? []).map((p: any) => <option key={p.id} value={p.id}>{p.name}</option>)}</select></div>
        <div className={styles.formGroup}><label>Appointment ID</label><input value={form.appointmentId} onChange={e => setForm({ ...form, appointmentId: e.target.value })} /></div>
        <div className={styles.formGroup}><label>CPT Codes</label><input value={form.cptCodes} onChange={e => setForm({ ...form, cptCodes: e.target.value })} placeholder="e.g. 99213" /></div>
        <div className={styles.formGroup}><label>ICD-10 Codes</label><input value={form.icd10Codes} onChange={e => setForm({ ...form, icd10Codes: e.target.value })} placeholder="e.g. I10" /></div>
        <div className={styles.formGroup}><label>Charge Amount</label><input type="number" step="0.01" value={form.chargeAmount} onChange={e => setForm({ ...form, chargeAmount: e.target.value })} /></div>
        <div className={styles.formGroup}><label>Visit Type</label><select value={form.visitType} onChange={e => setForm({ ...form, visitType: e.target.value })}><option value="FOLLOW_UP">Follow Up</option><option value="NEW_PATIENT">New Patient</option><option value="ANNUAL_PHYSICAL">Annual Physical</option><option value="PROCEDURE">Procedure</option></select></div>
        <div className={styles.formGroup} style={{ gridColumn: 'span 2' }}><label>Notes</label><input value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} /></div>
        <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => setShowForm(false)}>Cancel</button><button type="submit" className={styles.btnPrimary}>Save</button></div>
      </form>
    </div></div>}
  </div>)
}

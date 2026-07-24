import { useState, FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getReferralPage, getPatientReferrals, createReferral, updateReferral } from '../../api/referral'
import { getPatientPage } from '../../api/patient'
import { PAGE_SIZE } from '../../utils/labels'
import styles from '../shared.module.css'

const URGENCY_COLOR: any = { ROUTINE: '#67C23A', URGENT: '#E6A23C', STAT: '#F56C6C' }
const STATUS_COLOR: any = { PENDING: '#E6A23C', SCHEDULED: '#409EFF', COMPLETED: '#67C23A', CLOSED: '#909399' }
const emptyForm: any = { patientId: '', specialistName: '', specialistNpi: '', specialty: '', diagnosis: '', reason: '', urgency: 'ROUTINE', notes: '' }

export default function Referrals() {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ ...emptyForm })

  const { data: pageData } = useQuery({
    queryKey: ['referrals', 'list', { page, size: PAGE_SIZE }],
    queryFn: () => getReferralPage({ page, size: PAGE_SIZE }),
  })
  const data = pageData?.records ?? []
  const total = pageData?.total ?? 0

  const { data: patients } = useQuery({
    queryKey: ['patients', 'all'],
    queryFn: () => getPatientPage({ page: 1, size: 999 }).then(r => r.records ?? []),
  })

  const createMutation = useMutation({
    mutationFn: (d: any) => createReferral(d),
    onSuccess: () => { setShowForm(false); queryClient.invalidateQueries({ queryKey: ['referrals'] }) },
  })

  const updateMutation = useMutation({
    mutationFn: (params: { id: number; data: any }) => updateReferral(params.id, params.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['referrals'] }),
  })

  const handleCreate = (e: FormEvent) => { e.preventDefault(); createMutation.mutate({ ...form, patientId: Number(form.patientId) }) }

  return (<div>
    <h2 style={{ marginBottom: 20 }}>Referrals</h2>
    <button className={styles.btnPrimary} onClick={() => { setForm({ ...emptyForm }); setShowForm(true) }} style={{ marginBottom: 16 }}>+ New Referral</button>
    <table className={styles.table}>
      <thead><tr><th>ID</th><th>Patient</th><th>Specialist</th><th>Specialty</th><th>Urgency</th><th>Status</th><th>Date</th><th></th></tr></thead>
      <tbody>{data.map(r => (
        <tr key={r.id}>
          <td>{r.id}</td><td>{r.patientId}</td><td>{r.specialistName}</td><td>{r.specialty || '-'}</td>
          <td><span style={{ color: URGENCY_COLOR[r.urgency] || '#909399', fontWeight: 600 }}>{r.urgency}</span></td>
          <td><span style={{ color: STATUS_COLOR[r.status] || '#909399', fontWeight: 600 }}>{r.status}</span></td>
          <td>{r.referralDate}</td>
          <td>
            {r.status === 'PENDING' && <button className={styles.btnSm} onClick={() => updateMutation.mutate({ id: r.id, data: { status: 'SCHEDULED' } })}>Schedule</button>}
            {r.status === 'SCHEDULED' && <button className={styles.btnSm} onClick={() => updateMutation.mutate({ id: r.id, data: { status: 'COMPLETED' } })}>Complete</button>}
            {(r.status === 'SCHEDULED' || r.status === 'COMPLETED') && <button className={styles.btnSm} onClick={() => updateMutation.mutate({ id: r.id, data: { status: 'CLOSED' } })}>Close</button>}
          </td>
        </tr>
      ))}</tbody>
    </table>
    <div className={styles.pagination}><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>

    {showForm && <div className={styles.modalOverlay} onClick={() => setShowForm(false)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>New Referral</h3>
      <form onSubmit={handleCreate} className={styles.formGrid}>
        <div className={styles.formGroup}><label>Patient</label><select value={form.patientId} onChange={e => setForm({ ...form, patientId: e.target.value })}><option value="">-- Select --</option>{(patients ?? []).map((p: any) => <option key={p.id} value={p.id}>{p.name}</option>)}</select></div>
        <div className={styles.formGroup}><label>Specialist Name</label><input value={form.specialistName} onChange={e => setForm({ ...form, specialistName: e.target.value })} /></div>
        <div className={styles.formGroup}><label>NPI</label><input value={form.specialistNpi} onChange={e => setForm({ ...form, specialistNpi: e.target.value })} /></div>
        <div className={styles.formGroup}><label>Specialty</label><input value={form.specialty} onChange={e => setForm({ ...form, specialty: e.target.value })} /></div>
        <div className={styles.formGroup} style={{ gridColumn: 'span 2' }}><label>Diagnosis</label><input value={form.diagnosis} onChange={e => setForm({ ...form, diagnosis: e.target.value })} /></div>
        <div className={styles.formGroup} style={{ gridColumn: 'span 2' }}><label>Reason</label><input value={form.reason} onChange={e => setForm({ ...form, reason: e.target.value })} /></div>
        <div className={styles.formGroup}><label>Urgency</label><select value={form.urgency} onChange={e => setForm({ ...form, urgency: e.target.value })}><option value="ROUTINE">Routine</option><option value="URGENT">Urgent</option><option value="STAT">STAT</option></select></div>
        <div className={styles.formGroup}><label>Notes</label><input value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} /></div>
        <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => setShowForm(false)}>Cancel</button><button type="submit" className={styles.btnPrimary}>Save</button></div>
      </form>
    </div></div>}
  </div>)
}

import { useState, FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getAppointmentPage, getAppointmentById, createAppointment, updateAppointment, deleteAppointment, getAppointmentConflicts } from '../../api/appointment'
import { getPatientPage } from '../../api/patient'
import { getDoctors } from '../../api/user'
import { AppointmentForm, AppointmentVO } from '../../types/entities'
import { APPOINTMENT_STATUS, VISIT_TYPES, PAGE_SIZE, APPOINTMENT_STATUS_COLOR, TERMINAL_APPOINTMENT_STATUSES } from '../../utils/labels'
import { useConfirm } from '../../utils/ConfirmDialog'
import styles from '../shared.module.css'

const emptyForm: AppointmentForm = { patientId: '', doctorId: '', appointmentTime: '', visitType: 'FOLLOW_UP', chiefComplaint: '', department: '', duration: 30, cptCode: '', description: '', icd10Codes: '', notes: '', status: 0 }

export default function Appointments() {
  const queryClient = useQueryClient()
  const { confirm } = useConfirm()
  const [page, setPage] = useState(1)
  const [showForm, setShowForm] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [viewOnly, setViewOnly] = useState(false)
  const [form, setForm] = useState<AppointmentForm>({ ...emptyForm })
  const [formError, setFormError] = useState('')

  const { data: pageData } = useQuery({
    queryKey: ['appointments', 'list', { page, size: PAGE_SIZE }],
    queryFn: () => getAppointmentPage({ page, size: PAGE_SIZE }),
  })
  const data = pageData?.records ?? []

  const { data: patients } = useQuery({
    queryKey: ['patients', 'all'],
    queryFn: () => getPatientPage({ page: 1, size: 200 }).then(r => r.records ?? []),
  })

  const { data: doctors } = useQuery({
    queryKey: ['users', 'all'],
    queryFn: () => getDoctors().then(r => r ?? []),
  })

  const { data: conflicts } = useQuery({
    queryKey: ['appointments', 'conflicts', { doctorId: form.doctorId, time: form.appointmentTime, editId }],
    queryFn: () => getAppointmentConflicts({ doctorId: Number(form.doctorId), time: form.appointmentTime, excludeId: editId ?? undefined }),
    enabled: !!form.doctorId && !!form.appointmentTime && !viewOnly,
  })
  const total = pageData?.total ?? 0

  const saveMutation = useMutation({
    mutationFn: (params: { id?: number; data: AppointmentForm }) =>
      params.id != null ? updateAppointment(params.id, params.data) : createAppointment(params.data),
    onSuccess: () => {
      setShowForm(false)
      queryClient.invalidateQueries({ queryKey: ['appointments'] })
    },
    onError: (err: Error) => setFormError(err?.message || 'Save failed'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteAppointment(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['appointments'] }),
  })

  const openForm = async (row?: AppointmentVO, viewOnlyParam: boolean = false) => {
    setViewOnly(viewOnlyParam)
    if (row) {
      setEditId(row.id)
      const d = await getAppointmentById(row.id)
      setForm({
        patientId: String(d.patientId ?? ''),
        doctorId: String(d.doctorId ?? ''),
        appointmentTime: d.appointmentTime ?? '',
        visitType: d.visitType ?? '',
        chiefComplaint: d.chiefComplaint ?? '',
        department: d.department ?? '',
        duration: d.duration ?? 30,
        cptCode: d.cptCode ?? '',
        description: d.description ?? '',
        icd10Codes: d.icd10Codes ?? '',
        notes: d.notes ?? '',
        status: d.status,
      })
    }
    else { setEditId(null); setForm({ ...emptyForm }) }
    setShowForm(true)
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!form.patientId) { setFormError('Patient is required'); return }
    if (!form.doctorId) { setFormError('Doctor is required'); return }
    if (!form.appointmentTime) { setFormError('Appointment time is required'); return }
    setFormError('')
    saveMutation.mutate(editId != null ? { id: editId, data: form } : { data: form })
  }

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Appointments</h2>
      <button className={styles.btnPrimary} onClick={() => openForm()} style={{ marginBottom: 16 }}>+ Add</button>
      <table className={styles.table}>
        <thead><tr><th>ID</th><th>Patient</th><th>Doctor</th><th>Time</th><th>Duration</th><th>Type</th><th>Status</th><th></th></tr></thead>
        <tbody>{data.map(r => (
          <tr key={r.id} className={styles.clickableRow} onClick={() => openForm(r, TERMINAL_APPOINTMENT_STATUSES.includes(r.status))}>
            <td>{r.id}</td><td>{r.patientName}</td><td>{r.doctorName}</td><td>{r.appointmentTime}</td><td>{r.duration}m</td><td>{r.visitType}</td>
            <td><span style={{ color: APPOINTMENT_STATUS_COLOR[r.status] ?? '#909399', fontWeight: 600 }}>{APPOINTMENT_STATUS[r.status] ?? r.status}</span></td>
            <td onClick={e => e.stopPropagation()}>{TERMINAL_APPOINTMENT_STATUSES.includes(r.status) ? null : <button className={styles.btnSm} onClick={() => openForm(r)}>Edit</button>}
              {TERMINAL_APPOINTMENT_STATUSES.includes(r.status) ? null : <button className={styles.btnSmDanger} onClick={async () => { if (await confirm('Delete?')) deleteMutation.mutate(r.id) }}>Del</button>}</td></tr>
        ))}</tbody>
      </table>
      <div className={styles.pagination}><span>Total: {total}</span><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>
      {showForm && <div className={styles.modalOverlay} onClick={() => setShowForm(false)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>{editId ? (viewOnly ? 'View' : 'Edit') : 'Add'} Appointment</h3>
        <form onSubmit={handleSubmit} className={styles.formGrid}>
          <div className={styles.formGroup}><label>Patient</label>
            <select disabled={viewOnly} value={form.patientId} onChange={e => setForm({ ...form, patientId: e.target.value })}>
              <option value="">-- Select --</option>
              {(patients ?? []).map(p => <option key={p.id} value={p.id}>{p.name} (MRN: {p.mrn})</option>)}
            </select></div>
          <div className={styles.formGroup}><label>Doctor</label>
            <select disabled={viewOnly} value={form.doctorId} onChange={e => setForm({ ...form, doctorId: e.target.value })}>
              <option value="">-- Select --</option>
              {(doctors ?? []).map(d => <option key={d.id} value={d.id}>{d.realName || d.username}</option>)}
            </select></div>
          <div className={styles.formGroup}><label>Time</label><input disabled={viewOnly} type="datetime-local" value={form.appointmentTime} min={new Date().toISOString().slice(0, 16)} onChange={e => setForm({ ...form, appointmentTime: e.target.value })} /></div>
          <div className={styles.formGroup}><label>Visit Type</label>
            <select disabled={viewOnly} value={form.visitType} onChange={e => setForm({ ...form, visitType: e.target.value })}>
              {VISIT_TYPES.map(v => <option key={v} value={v}>{v.replace(/_/g,' ')}</option>)}</select></div>
          <div className={styles.formGroup}><label>Department</label><input disabled={viewOnly} value={form.department} onChange={e => setForm({ ...form, department: e.target.value })} /></div>
          <div className={styles.formGroup}><label>CPT Code</label><input disabled={viewOnly} value={form.cptCode} onChange={e => setForm({ ...form, cptCode: e.target.value })} /></div>
          <div className={styles.formGroup}><label>Duration (min)</label><input disabled={viewOnly} type="number" value={form.duration} onChange={e => setForm({ ...form, duration: Number(e.target.value) })} /></div>
          <div className={styles.formGroup} style={{ gridColumn: 'span 2' }}><label>Chief Complaint</label><input disabled={viewOnly} value={form.chiefComplaint} onChange={e => setForm({ ...form, chiefComplaint: e.target.value })} /></div>
          <div className={styles.formGroup} style={{ gridColumn: 'span 2' }}><label>ICD-10 Codes</label><input disabled={viewOnly} value={form.icd10Codes} onChange={e => setForm({ ...form, icd10Codes: e.target.value })} /></div>
          <div className={styles.formGroup} style={{ gridColumn: 'span 2' }}><label>Description</label><input disabled={viewOnly} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></div>
          <div className={styles.formGroup} style={{ gridColumn: 'span 2' }}><label>Notes</label><textarea rows={2} disabled={viewOnly} style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13, resize: 'vertical' }} value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} /></div>
          {editId && <div className={styles.formGroup}><label>Status</label>
            <select disabled={viewOnly} value={form.status} onChange={e => setForm({ ...form, status: Number(e.target.value) })}>
              {Object.entries(APPOINTMENT_STATUS).map(([k,v]) => <option key={k} value={k}>{v}</option>)}</select></div>}
          {!viewOnly && conflicts && conflicts.length > 0 && (
            <div style={{ gridColumn: 'span 2', background: '#fef0f0', border: '1px solid #fbc4c4', color: '#F56C6C', fontSize: 12, padding: '8px 10px', borderRadius: 4 }}>
              ⚠️ Doctor already has {conflicts.length} appointment(s) within 30 minutes:{' '}
              {conflicts.map(c => `${c.appointmentTime} (${c.patientName})`).join('; ')}
            </div>
          )}
          {formError && <div style={{ gridColumn: 'span 2', color: '#F56C6C', fontSize: 12 }}>{formError}</div>}
          <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => { setShowForm(false); setFormError('') }}>Cancel</button>{!viewOnly && <button type="submit" className={styles.btnPrimary} disabled={saveMutation.isPending || (conflicts?.length ?? 0) > 0}>Save</button>}</div></form></div></div>}
    </div>
  )
}

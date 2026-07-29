import { useState, FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getAppointmentPage, getAppointmentById, createAppointment, updateAppointment, deleteAppointment } from '../../api/appointment'
import { getPatientPage } from '../../api/patient'
import { getDoctors } from '../../api/user'
import { APPOINTMENT_STATUS, VISIT_TYPES, PAGE_SIZE, APPOINTMENT_STATUS_COLOR } from '../../utils/labels'
import styles from '../shared.module.css'

const emptyForm: any = { patientId: '', doctorId: '', appointmentTime: '', visitType: 'FOLLOW_UP', chiefComplaint: '', department: '', duration: 30, cptCode: '', description: '', status: 0 }

export default function Appointments() {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [showForm, setShowForm] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [viewOnly, setViewOnly] = useState(false)
  const [form, setForm] = useState({ ...emptyForm })

  const { data: pageData } = useQuery({
    queryKey: ['appointments', 'list', { page, size: PAGE_SIZE }],
    queryFn: () => getAppointmentPage({ page, size: PAGE_SIZE }),
  })
  const data = pageData?.records ?? []

  const { data: patients } = useQuery({
    queryKey: ['patients', 'all'],
    queryFn: () => getPatientPage({ page: 1, size: 999 }).then(r => r.records ?? []),
  })

  const { data: doctors } = useQuery({
    queryKey: ['users', 'all'],
    queryFn: () => getDoctors().then(r => r ?? []),
  })
  const total = pageData?.total ?? 0

  const saveMutation = useMutation({
    mutationFn: (params: { id?: number; data: any }) =>
      params.id != null ? updateAppointment(params.id, params.data) : createAppointment(params.data),
    onSuccess: () => {
      setShowForm(false)
      queryClient.invalidateQueries({ queryKey: ['appointments'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteAppointment(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['appointments'] }),
  })

  const openForm = async (row?: any, viewOnlyParam: boolean = false) => {
    setViewOnly(viewOnlyParam)
    if (row) { setEditId(row.id); setForm({ ...emptyForm, ...(await getAppointmentById(row.id)) }) }
    else { setEditId(null); setForm({ ...emptyForm }) }
    setShowForm(true)
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    saveMutation.mutate(editId != null ? { id: editId, data: form } : { data: form })
  }

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Appointments</h2>
      <button className={styles.btnPrimary} onClick={() => openForm()} style={{ marginBottom: 16 }}>+ Add</button>
      <table className={styles.table}>
        <thead><tr><th>ID</th><th>Patient</th><th>Doctor</th><th>Time</th><th>Duration</th><th>Type</th><th>Status</th><th></th></tr></thead>
        <tbody>{data.map(r => (
          <tr key={r.id} className={styles.clickableRow} onClick={() => openForm(r, [2, 3, 4].includes(r.status))}>
            <td>{r.id}</td><td>{r.patientName}</td><td>{r.doctorName}</td><td>{r.appointmentTime}</td><td>{r.duration}m</td><td>{r.visitType}</td>
            <td><span style={{ color: APPOINTMENT_STATUS_COLOR[r.status] ?? '#909399', fontWeight: 600 }}>{APPOINTMENT_STATUS[r.status] ?? r.status}</span></td>
            <td onClick={e => e.stopPropagation()}>{[2, 3, 4].includes(r.status) ? null : <button className={styles.btnSm} onClick={() => openForm(r)}>Edit</button>}
              {[2, 3, 4].includes(r.status) ? null : <button className={styles.btnSmDanger} onClick={() => { if (confirm('Delete?')) deleteMutation.mutate(r.id) }}>Del</button>}</td></tr>
        ))}</tbody>
      </table>
      <div className={styles.pagination}><span>Total: {total}</span><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>
      {showForm && <div className={styles.modalOverlay} onClick={() => setShowForm(false)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>{editId ? (viewOnly ? 'View' : 'Edit') : 'Add'} Appointment</h3>
        <form onSubmit={handleSubmit} className={styles.formGrid}>
          <div className={styles.formGroup}><label>Patient</label>
            <select disabled={viewOnly} value={form.patientId} onChange={e => setForm({ ...form, patientId: e.target.value })}>
              <option value="">-- Select --</option>
              {(patients ?? []).map((p: any) => <option key={p.id} value={p.id}>{p.name} (MRN: {p.mrn})</option>)}
            </select></div>
          <div className={styles.formGroup}><label>Doctor</label>
            <select disabled={viewOnly} value={form.doctorId} onChange={e => setForm({ ...form, doctorId: e.target.value })}>
              <option value="">-- Select --</option>
              {(doctors ?? []).map((d: any) => <option key={d.id} value={d.id}>{d.realName || d.username}</option>)}
            </select></div>
          <div className={styles.formGroup}><label>Time</label><input disabled={viewOnly} type="datetime-local" value={form.appointmentTime} min={new Date().toISOString().slice(0, 16)} onChange={e => setForm({ ...form, appointmentTime: e.target.value })} /></div>
          <div className={styles.formGroup}><label>Visit Type</label>
            <select disabled={viewOnly} value={form.visitType} onChange={e => setForm({ ...form, visitType: e.target.value })}>
              {VISIT_TYPES.map(v => <option key={v} value={v}>{v.replace(/_/g,' ')}</option>)}</select></div>
          <div className={styles.formGroup}><label>Department</label><input disabled={viewOnly} value={form.department} onChange={e => setForm({ ...form, department: e.target.value })} /></div>
          <div className={styles.formGroup}><label>CPT Code</label><input disabled={viewOnly} value={form.cptCode} onChange={e => setForm({ ...form, cptCode: e.target.value })} /></div>
          <div className={styles.formGroup}><label>Duration (min)</label><input disabled={viewOnly} type="number" value={form.duration} onChange={e => setForm({ ...form, duration: Number(e.target.value) })} /></div>
          <div className={styles.formGroup} style={{ gridColumn: 'span 2' }}><label>Chief Complaint</label><input disabled={viewOnly} value={form.chiefComplaint} onChange={e => setForm({ ...form, chiefComplaint: e.target.value })} /></div>
          <div className={styles.formGroup} style={{ gridColumn: 'span 2' }}><label>Description</label><input disabled={viewOnly} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></div>
          {editId && <div className={styles.formGroup}><label>Status</label>
            <select disabled={viewOnly} value={form.status} onChange={e => setForm({ ...form, status: Number(e.target.value) })}>
              {Object.entries(APPOINTMENT_STATUS).map(([k,v]) => <option key={k} value={k}>{v}</option>)}</select></div>}
          <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => setShowForm(false)}>Cancel</button>{!viewOnly && <button type="submit" className={styles.btnPrimary} disabled={saveMutation.isPending}>Save</button>}</div></form></div></div>}
    </div>
  )
}

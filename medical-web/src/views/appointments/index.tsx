import { useState, useEffect, FormEvent } from 'react'
import { getAppointmentPage, getAppointmentById, createAppointment, updateAppointment, deleteAppointment } from '../../api/appointment'
import { APPOINTMENT_STATUS, VISIT_TYPES, PAGE_SIZE, APPOINTMENT_STATUS_COLOR } from '../../utils/labels'
import styles from '../shared.module.css'

const emptyForm: any = { patientId: '', doctorId: '', appointmentTime: '', visitType: 'FOLLOW_UP', chiefComplaint: '', department: '', duration: 30, cptCode: '', description: '', status: 0 }

export default function Appointments() {
  const [data, setData] = useState<any[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [showForm, setShowForm] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [form, setForm] = useState({ ...emptyForm })

  useEffect(() => { getAppointmentPage({ page, size: PAGE_SIZE }).then(r => { setData(r.records); setTotal(r.total) }) }, [page])

  const openForm = async (row?: any) => {
    if (row) { setEditId(row.id); setForm({ ...emptyForm, ...(await getAppointmentById(row.id)) }) }
    else { setEditId(null); setForm({ ...emptyForm }) }
    setShowForm(true)
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    editId ? await updateAppointment(editId, form) : await createAppointment(form)
    setShowForm(false); getAppointmentPage({ page, size: PAGE_SIZE }).then(r => { setData(r.records); setTotal(r.total) })
  }

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Appointments</h2>
      <button className={styles.btnPrimary} onClick={() => openForm()} style={{ marginBottom: 16 }}>+ Add</button>
      <table className={styles.table}>
        <thead><tr><th>ID</th><th>Patient</th><th>Doctor</th><th>Time</th><th>Duration</th><th>Type</th><th>Status</th><th></th></tr></thead>
        <tbody>{data.map(r => (
          <tr key={r.id}><td>{r.id}</td><td>{r.patientName}</td><td>{r.doctorName}</td><td>{r.appointmentTime}</td><td>{r.duration}m</td><td>{r.visitType}</td>
            <td><span style={{ color: APPOINTMENT_STATUS_COLOR[r.status] ?? '#909399', fontWeight: 600 }}>{APPOINTMENT_STATUS[r.status] ?? r.status}</span></td>
            <td><button className={styles.btnSm} onClick={() => openForm(r)}>Edit</button>
              <button className={styles.btnSmDanger} onClick={async () => { if (confirm('Delete?')) { await deleteAppointment(r.id); setData(d => d.filter(x => x.id !== r.id)) } }}>Del</button></td></tr>
        ))}</tbody>
      </table>
      <div className={styles.pagination}><span>Total: {total}</span><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>
      {showForm && <div className={styles.modalOverlay} onClick={() => setShowForm(false)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>{editId ? 'Edit' : 'Add'} Appointment</h3>
        <form onSubmit={handleSubmit} className={styles.formGrid}>
          <div className={styles.formGroup}><label>Patient ID</label><input value={form.patientId} onChange={e => setForm({ ...form, patientId: e.target.value })} /></div>
          <div className={styles.formGroup}><label>Doctor ID</label><input value={form.doctorId} onChange={e => setForm({ ...form, doctorId: e.target.value })} /></div>
          <div className={styles.formGroup}><label>Time</label><input type="datetime-local" value={form.appointmentTime} onChange={e => setForm({ ...form, appointmentTime: e.target.value })} /></div>
          <div className={styles.formGroup}><label>Visit Type</label>
            <select value={form.visitType} onChange={e => setForm({ ...form, visitType: e.target.value })}>
              {VISIT_TYPES.map(v => <option key={v} value={v}>{v.replace(/_/g,' ')}</option>)}</select></div>
          <div className={styles.formGroup}><label>Department</label><input value={form.department} onChange={e => setForm({ ...form, department: e.target.value })} /></div>
          <div className={styles.formGroup}><label>CPT Code</label><input value={form.cptCode} onChange={e => setForm({ ...form, cptCode: e.target.value })} /></div>
          <div className={styles.formGroup}><label>Duration (min)</label><input type="number" value={form.duration} onChange={e => setForm({ ...form, duration: Number(e.target.value) })} /></div>
          <div className={styles.formGroup} style={{ gridColumn: 'span 2' }}><label>Chief Complaint</label><input value={form.chiefComplaint} onChange={e => setForm({ ...form, chiefComplaint: e.target.value })} /></div>
          <div className={styles.formGroup} style={{ gridColumn: 'span 2' }}><label>Description</label><input value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></div>
          {editId && <div className={styles.formGroup}><label>Status</label>
            <select value={form.status} onChange={e => setForm({ ...form, status: Number(e.target.value) })}>
              {Object.entries(APPOINTMENT_STATUS).map(([k,v]) => <option key={k} value={k}>{v}</option>)}</select></div>}
          <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => setShowForm(false)}>Cancel</button><button type="submit" className={styles.btnPrimary}>Save</button></div></form></div></div>}
    </div>
  )
}

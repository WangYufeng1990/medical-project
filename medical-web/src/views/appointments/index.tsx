import { useState, useEffect, FormEvent } from 'react'
import { getAppointmentPage, getAppointmentById, createAppointment, updateAppointment, deleteAppointment } from '../../api/appointment'
import styles from '../shared.module.css'

const emptyForm: any = { patientId: '', doctorId: '', appointmentTime: '', visitType: 'FOLLOW_UP', chiefComplaint: '', department: '', duration: 30, cptCode: '', description: '', status: 0 }

export default function Appointments() {
  const [data, setData] = useState<any[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [showForm, setShowForm] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [form, setForm] = useState({ ...emptyForm })

  useEffect(() => { getAppointmentPage({ page, size: 10 }).then(r => { setData(r.records); setTotal(r.total) }) }, [page])

  const openForm = async (row?: any) => {
    if (row) { setEditId(row.id); setForm({ ...emptyForm, ...(await getAppointmentById(row.id)) }) }
    else { setEditId(null); setForm({ ...emptyForm }) }
    setShowForm(true)
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    editId ? await updateAppointment(editId, form) : await createAppointment(form)
    setShowForm(false); getAppointmentPage({ page, size: 10 }).then(r => { setData(r.records); setTotal(r.total) })
  }

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Appointments</h2>
      <button className={styles.btnPrimary} onClick={() => openForm()} style={{ marginBottom: 16 }}>+ Add</button>
      <table className={styles.table}>
        <thead><tr><th>ID</th><th>Patient</th><th>Doctor</th><th>Time</th><th>Type</th><th>Status</th><th></th></tr></thead>
        <tbody>{data.map(r => (
          <tr key={r.id}><td>{r.id}</td><td>{r.patientName}</td><td>{r.doctorName}</td><td>{r.appointmentTime}</td><td>{r.visitType}</td><td>{r.status}</td>
            <td><button className={styles.btnSm} onClick={() => openForm(r)}>Edit</button>
              <button className={styles.btnSmDanger} onClick={async () => { if (confirm('Delete?')) { await deleteAppointment(r.id); setData(d => d.filter(x => x.id !== r.id)) } }}>Del</button></td></tr>
        ))}</tbody>
      </table>
      <div className={styles.pagination}><span>Total: {total}</span><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*10>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>
      {showForm && <div className={styles.modalOverlay} onClick={() => setShowForm(false)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>{editId ? 'Edit' : 'Add'} Appointment</h3>
        <form onSubmit={handleSubmit} className={styles.formGrid}>
          {['patientId','doctorId','appointmentTime','visitType','chiefComplaint','department','duration','cptCode','description'].map(f => (
            <div key={f} className={styles.formGroup}><label>{f}</label><input value={form[f] || ''} onChange={e => setForm({ ...form, [f]: e.target.value })} /></div>))}
          <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => setShowForm(false)}>Cancel</button><button type="submit" className={styles.btnPrimary}>Save</button></div></form></div></div>}
    </div>
  )
}

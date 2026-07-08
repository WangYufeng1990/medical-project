import { useState, useEffect, FormEvent } from 'react'
import axios from 'axios'
import { APPOINTMENT_STATUS, VISIT_TYPES, PAGE_SIZE, APPOINTMENT_STATUS_COLOR } from '../../../utils/labels'
import styles from '../../shared.module.css'

const emptyForm: any = { doctorId: '', appointmentTime: '', visitType: 'FOLLOW_UP', chiefComplaint: '', department: '' }

export default function PatientAppointments() {
  const [data, setData] = useState<any[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ ...emptyForm })
  const [cancelling, setCancelling] = useState<number | null>(null)
  const headers = { Authorization: `Bearer ${localStorage.getItem('patientToken')}` }

  const fetchAppointments = (p?: number) => axios.get(`/api/v1/patient/me/appointments?page=${p ?? page}&size=${PAGE_SIZE}`, { headers }).then(r => { setData(r.data.data.records); setTotal(r.data.data.total) })

  useEffect(() => { fetchAppointments() }, [page])

  const canCancel = (s: number) => s !== 2 && s !== 3

  const handleCancel = async (id: number) => {
    setCancelling(id)
    try {
      await axios.put(`/api/v1/patient/me/appointments/${id}/cancel`, {}, { headers })
      fetchAppointments(1)
      setPage(1)
    } catch (err: any) {
      alert(err?.response?.data?.message || 'Cancel failed')
    } finally {
      setCancelling(null)
    }
  }

  const handleBook = async (e: FormEvent) => {
    e.preventDefault()
    try {
      await axios.post('/api/v1/patient/me/appointments', {
        doctorId: Number(form.doctorId),
        appointmentTime: form.appointmentTime,
        visitType: form.visitType,
        chiefComplaint: form.chiefComplaint,
        department: form.department
      }, { headers })
      setShowForm(false)
      setForm({ ...emptyForm })
      fetchAppointments(1)
      setPage(1)
    } catch (err: any) {
      alert(err?.response?.data?.message || 'Booking failed')
    }
  }

  return (<div>
    <h2 style={{ marginBottom: 20 }}>My Appointments
      <button className={styles.btnPrimary} style={{ marginLeft: 16 }} onClick={() => setShowForm(true)}>+ Book</button>
    </h2>
    <table className={styles.table}>
      <thead><tr><th>Date</th><th>Doctor</th><th>Duration</th><th>Visit Type</th><th>Department</th><th>Status</th><th></th></tr></thead>
      <tbody>{data.map(r => (
        <tr key={r.id}>
          <td>{r.appointmentTime}</td><td>{r.doctorName}</td><td>{r.duration}m</td><td>{r.visitType}</td><td>{r.department}</td>
          <td><span style={{ color: APPOINTMENT_STATUS_COLOR[r.status] ?? '#909399', fontWeight: 600 }}>{APPOINTMENT_STATUS[r.status] ?? r.status}</span></td>
          <td>
            {canCancel(r.status) && (
              <button className={styles.btnSmDanger} disabled={cancelling === r.id}
                onClick={() => { if (confirm('Cancel this appointment?')) handleCancel(r.id) }}>
                {cancelling === r.id ? '...' : 'Cancel'}
              </button>
            )}
          </td>
        </tr>
      ))}</tbody>
    </table>
    <div className={styles.pagination}><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>

    {showForm && <div className={styles.modalOverlay} onClick={() => setShowForm(false)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>Book Appointment</h3>
      <form onSubmit={handleBook} className={styles.formGrid}>
        <div className={styles.formGroup}><label>Doctor ID</label><input value={form.doctorId} onChange={e => setForm({ ...form, doctorId: e.target.value })} placeholder="e.g. 2" /></div>
        <div className={styles.formGroup}><label>Date & Time</label><input type="datetime-local" value={form.appointmentTime} onChange={e => setForm({ ...form, appointmentTime: e.target.value })} /></div>
        <div className={styles.formGroup}>
          <label>Visit Type</label>
          <select value={form.visitType} onChange={e => setForm({ ...form, visitType: e.target.value })}>
            {VISIT_TYPES.map(v => <option key={v} value={v}>{v.replace(/_/g, ' ')}</option>)}
          </select>
        </div>
        <div className={styles.formGroup}><label>Department</label><input value={form.department} onChange={e => setForm({ ...form, department: e.target.value })} /></div>
        <div className={styles.formGroup} style={{ gridColumn: 'span 2' }}><label>Chief Complaint</label><input value={form.chiefComplaint} onChange={e => setForm({ ...form, chiefComplaint: e.target.value })} /></div>
        <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => setShowForm(false)}>Cancel</button><button type="submit" className={styles.btnPrimary}>Book</button></div>
      </form>
    </div></div>}
  </div>)
}

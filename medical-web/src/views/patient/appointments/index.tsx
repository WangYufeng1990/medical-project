import { useState, useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import patientRequest from '../../../api/patientRequest'
import { APPOINTMENT_STATUS, PAGE_SIZE, APPOINTMENT_STATUS_COLOR } from '../../../utils/labels'
import styles from '../../shared.module.css'

export default function PatientAppointments() {
  const location = useLocation()
  const [data, setData] = useState<any[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [cancelling, setCancelling] = useState<number | null>(null)

  const fetchAppointments = (p?: number) => patientRequest.get(`/patient/me/appointments?page=${p ?? page}&size=${PAGE_SIZE}`).then(r => { setData(r.data.data.records); setTotal(r.data.data.total) })

  useEffect(() => { fetchAppointments() }, [page, location])

  const canCancel = (s: number) => s !== 2 && s !== 3

  const handleCancel = async (id: number) => {
    setCancelling(id)
    try {
      await patientRequest.put(`/patient/me/appointments/${id}/cancel`)
      fetchAppointments(1)
      setPage(1)
    } catch (err: any) {
      alert(err?.response?.data?.message || 'Cancel failed')
    } finally {
      setCancelling(null)
    }
  }

  return (<div>
    <h2 style={{ marginBottom: 20 }}>My Appointments</h2>
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
  </div>)
}

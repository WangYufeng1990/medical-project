import { useState, useEffect } from 'react'
import axios from 'axios'
import { APPOINTMENT_STATUS } from '../../../utils/labels'
import styles from '../../shared.module.css'

export default function PatientAppointments() {
  const [data, setData] = useState<any[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const headers = { Authorization: `Bearer ${localStorage.getItem('patientToken')}` }

  useEffect(() => { axios.get(`/api/v1/patient/me/appointments?page=${page}&size=10`, { headers }).then(r => { setData(r.data.data.records); setTotal(r.data.data.total) }) }, [page])

  const statusColor = (s: number) => ['#409EFF','#67C23A','#909399','#67C23A','#E6A23C','#E6A23C','#409EFF'][s] || '#909399'

  return (<div>
    <h2>My Appointments</h2>
    <table className={styles.table}><thead><tr><th>Date</th><th>Doctor</th><th>Visit Type</th><th>Department</th><th>Status</th></tr></thead>
      <tbody>{data.map(r => (<tr key={r.id}><td>{r.appointmentTime}</td><td>{r.doctorName}</td><td>{r.visitType}</td><td>{r.department}</td>
        <td><span style={{ color: statusColor(r.status), fontWeight: 600 }}>{APPOINTMENT_STATUS[r.status] ?? r.status}</span></td></tr>))}</tbody></table>
    <div className={styles.pagination}><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*10>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>
  </div>)
}

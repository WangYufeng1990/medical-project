import { useState, useEffect } from 'react'
import axios from 'axios'
import { PAGE_SIZE } from '../../../utils/labels'
import styles from '../../shared.module.css'

export default function PatientPrescriptions() {
  const [data, setData] = useState<any[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const headers = { Authorization: `Bearer ${localStorage.getItem('patientToken')}` }

  useEffect(() => { axios.get(`/api/v1/patient/me/prescriptions?page=${page}&size=${PAGE_SIZE}`, { headers }).then(r => { setData(r.data.data.records); setTotal(r.data.data.total) }) }, [page])

  return (<div>
    <h2>My Prescriptions</h2>
    <table className={styles.table}><thead><tr><th>Date</th><th>Doctor</th><th>Diagnosis</th><th>ICD-10</th><th>Status</th></tr></thead>
      <tbody>{data.map(r => (<tr key={r.id}><td>{r.prescriptionDate}</td><td>{r.doctorName}</td><td>{r.diagnosis}</td><td>{r.icd10Codes}</td><td>{r.rxStatus}</td></tr>))}</tbody></table>
    <div className={styles.pagination}><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>
  </div>)
}

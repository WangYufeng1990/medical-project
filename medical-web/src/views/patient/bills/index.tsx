import { useState, useEffect } from 'react'
import axios from 'axios'
import styles from '../../shared.module.css'

export default function PatientBills() {
  const [data, setData] = useState<any[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const headers = { Authorization: `Bearer ${localStorage.getItem('patientToken')}` }

  useEffect(() => { axios.get(`/api/v1/patient/me/bills?page=${page}&size=10`, { headers }).then(r => { setData(r.data.data.records); setTotal(r.data.data.total) }) }, [page])

  return (<div>
    <h2>My Bills</h2>
    <table className={styles.table}><thead><tr><th>ID</th><th>Type</th><th>Status</th><th>Total</th><th>Patient Resp</th></tr></thead>
      <tbody>{data.map(r => (<tr key={r.id}><td>{r.id}</td><td>{r.billType}</td><td>{r.claimStatus}</td><td>{r.totalCharge}</td><td>{r.patientResponsibility}</td></tr>))}</tbody></table>
    <div className={styles.pagination}><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*10>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>
  </div>)
}

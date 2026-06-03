import { useState, useEffect } from 'react'
import { getPrescriptionPage, getPrescriptionById, createPrescription, updatePrescription, deletePrescription } from '../../api/prescription'
import styles from '../shared.module.css'

export default function Prescriptions() {
  const [data, setData] = useState<any[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)

  useEffect(() => { getPrescriptionPage({ page, size: 10 }).then(r => { setData(r.records); setTotal(r.total) }) }, [page])

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Prescriptions</h2>
      <table className={styles.table}>
        <thead><tr><th>ID</th><th>Patient</th><th>Doctor</th><th>Diagnosis</th><th>ICD-10</th><th>Date</th><th>Status</th><th></th></tr></thead>
        <tbody>{data.map(r => (
          <tr key={r.id}><td>{r.id}</td><td>{r.patientName}</td><td>{r.doctorName}</td><td>{r.diagnosis}</td><td>{r.icd10Codes}</td><td>{r.prescriptionDate}</td><td>{r.rxStatus}</td>
            <td><button className={styles.btnSmDanger} onClick={async () => { if (confirm('Delete?')) { await deletePrescription(r.id); setData(d => d.filter(x => x.id !== r.id)) } }}>Del</button></td></tr>
        ))}</tbody>
      </table>
      <div className={styles.pagination}><span>Total: {total}</span><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*10>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>
    </div>
  )
}

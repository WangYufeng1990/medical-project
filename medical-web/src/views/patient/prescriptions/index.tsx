import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import patientRequest from '../../../api/patientRequest'
import { PAGE_SIZE } from '../../../utils/labels'
import styles from '../../shared.module.css'

export default function PatientPrescriptions() {
  const [page, setPage] = useState(1)

  const { data: pageData } = useQuery({
    queryKey: ['me', 'prescriptions', 'list', { page, size: PAGE_SIZE }],
    queryFn: () => patientRequest.get(`/patient/me/prescriptions?page=${page}&size=${PAGE_SIZE}`).then(r => r.data.data),
  })
  const data = pageData?.records ?? []
  const total = pageData?.total ?? 0

  return (<div>
    <h2>My Prescriptions</h2>
    <table className={styles.table}><thead><tr><th>Date</th><th>Doctor</th><th>Diagnosis</th><th>ICD-10</th><th>Status</th></tr></thead>
      <tbody>{data.map(r => (<tr key={r.id}><td>{r.prescriptionDate}</td><td>{r.doctorName}</td><td>{r.diagnosis}</td><td>{r.icd10Codes}</td><td>{r.rxStatus}</td></tr>))}</tbody></table>
    <div className={styles.pagination}><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>
  </div>)
}

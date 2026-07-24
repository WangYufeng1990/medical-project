import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import patientRequest from '../../../api/patientRequest'
import { PAGE_SIZE } from '../../../utils/labels'
import styles from '../../shared.module.css'

export default function PatientDisclosures() {
  const [page, setPage] = useState(1)

  const { data: pageData, isLoading } = useQuery({
    queryKey: ['me', 'disclosures', { page, size: PAGE_SIZE }],
    queryFn: () => patientRequest.get(`/patient/me/disclosures?page=${page}&size=${PAGE_SIZE}`).then(r => r.data.data),
  })
  const data = pageData?.records ?? []
  const total = pageData?.total ?? 0

  if (isLoading) return <p style={{ color: '#909399' }}>Loading...</p>

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Accounting of Disclosures</h2>
      <p style={{ fontSize: 13, color: '#909399', marginBottom: 16 }}>
        HIPAA §164.528 — A record of staff who have accessed your health information.
      </p>
      {data.length === 0 && <p style={{ color: '#909399' }}>No disclosures recorded.</p>}
      <table className={styles.table}>
        <thead><tr><th>Date</th><th>Module</th><th>Action</th><th>Detail</th></tr></thead>
        <tbody>{data.map((r: any) => (
          <tr key={r.id}>
            <td>{r.createTime?.substring(0, 16)}</td>
            <td style={{ fontWeight: 600 }}>{r.module}</td>
            <td>{r.action}</td>
            <td style={{ maxWidth: 300, overflow: 'hidden', textOverflow: 'ellipsis', fontSize: 12 }}>{r.detail ?? ''}</td>
          </tr>
        ))}</tbody>
      </table>
      <div className={styles.pagination}>
        <button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button>
        <span>Page {page}</span>
        <button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button>
      </div>
    </div>
  )
}

import { useState, useEffect } from 'react'
import { getBillPage, deleteBill } from '../../api/bill'
import styles from '../shared.module.css'

export default function Billing() {
  const [data, setData] = useState<any[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)

  useEffect(() => { getBillPage({ page, size: 10 }).then(r => { setData(r.records); setTotal(r.total) }) }, [page])

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Billing</h2>
      <table className={styles.table}>
        <thead><tr><th>ID</th><th>Patient</th><th>Bill Type</th><th>Status</th><th>Total</th><th>Ins Pay</th><th>Patient Resp</th><th></th></tr></thead>
        <tbody>{data.map(r => (
          <tr key={r.id}><td>{r.id}</td><td>{r.patientName}</td><td>{r.billType}</td><td>{r.claimStatus}</td><td>{r.totalCharge}</td><td>{r.insurancePayment}</td><td>{r.patientResponsibility}</td>
            <td><button className={styles.btnSmDanger} onClick={async () => { if (confirm('Delete?')) { await deleteBill(r.id); setData(d => d.filter(x => x.id !== r.id)) } }}>Del</button></td></tr>
        ))}</tbody>
      </table>
      <div className={styles.pagination}><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*10>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>
    </div>
  )
}

import { useState, useEffect, FormEvent } from 'react'
import axios from 'axios'
import { PAGE_SIZE, BILL_STATUS_COLOR } from '../../../utils/labels'
import styles from '../../shared.module.css'

export default function PatientBills() {
  const [data, setData] = useState<any[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [payId, setPayId] = useState<number | null>(null)
  const [payForm, setPayForm] = useState({ paymentAmount: '', paymentMethod: 'CREDIT_CARD' })
  const headers = { Authorization: `Bearer ${localStorage.getItem('patientToken')}` }

  const refresh = () => axios.get(`/api/v1/patient/me/bills?page=${page}&size=${PAGE_SIZE}`, { headers }).then(r => { setData(r.data.data.records); setTotal(r.data.data.total) })

  useEffect(() => { refresh() }, [page])

  const openPay = (bill: any) => {
    setPayId(bill.id)
    const amount = bill.patientResponsibility != null ? bill.patientResponsibility : (bill.totalCharge || '')
    setPayForm({ paymentAmount: amount, paymentMethod: 'CREDIT_CARD' })
  }

  const handlePay = async (e: FormEvent) => {
    e.preventDefault()
    if (!payId) return
    await axios.put(`/api/v1/patient/me/bills/${payId}/pay`, { paymentAmount: Number(payForm.paymentAmount), paymentMethod: payForm.paymentMethod }, { headers })
    setPayId(null)
    refresh()
  }

  return (<div>
    <h2 style={{ marginBottom: 20 }}>My Bills</h2>
    <table className={styles.table}>
      <thead><tr><th>ID</th><th>Type</th><th>Status</th><th>Total</th><th>Ins Paid</th><th>I Owe</th><th></th></tr></thead>
      <tbody>{data.map(r => (
        <tr key={r.id}>
          <td>{r.id}</td><td>{r.billType}</td>
          <td><span style={{ color: BILL_STATUS_COLOR[r.claimStatus] || '#909399', fontWeight: 600 }}>{r.claimStatus}</span></td>
          <td>${r.totalCharge}</td><td>${r.insurancePayment}</td><td>${r.patientResponsibility}</td>
          <td>
            {(r.claimStatus === 'PENDING' || r.claimStatus === 'DRAFT') && <button className={styles.btnPrimary} onClick={() => openPay(r)}>Pay Now</button>}
          </td>
        </tr>
      ))}</tbody>
    </table>
    <div className={styles.pagination}><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>

    {payId && <div className={styles.modalOverlay} onClick={() => setPayId(null)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>Pay Bill #{payId}</h3>
      <form onSubmit={handlePay} className={styles.formGrid}>
        <div className={styles.formGroup}><label>Amount</label><input type="number" step="0.01" value={payForm.paymentAmount} onChange={e => setPayForm({ ...payForm, paymentAmount: e.target.value })} /></div>
        <div className={styles.formGroup}>
          <label>Payment Method</label>
          <select value={payForm.paymentMethod} onChange={e => setPayForm({ ...payForm, paymentMethod: e.target.value })}>
            <option value="CREDIT_CARD">Credit Card</option><option value="HSA">HSA/FSA</option><option value="CHECK">Check</option><option value="ONLINE">Online Payment</option>
          </select>
        </div>
        <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => setPayId(null)}>Cancel</button><button type="submit" className={styles.btnPrimary}>Pay</button></div>
      </form>
    </div></div>}
  </div>)
}

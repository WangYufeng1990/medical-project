import { useState, FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { http } from '../../../api/patientRequest'
import { PageResult } from '../../../types/common'
import { BillVO, PayForm } from '../../../types/entities'
import { PAGE_SIZE, BILL_STATUS_COLOR } from '../../../utils/labels'
import styles from '../../shared.module.css'

export default function PatientBills() {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [payId, setPayId] = useState<number | null>(null)
  const [payForm, setPayForm] = useState({ paymentAmount: '', paymentMethod: 'CREDIT_CARD' })

  const { data: pageData } = useQuery({
    queryKey: ['me', 'bills', 'list', { page, size: PAGE_SIZE }],
    queryFn: () => http.get<PageResult<BillVO>>(`/patient/me/bills?page=${page}&size=${PAGE_SIZE}`),
  })
  const data = pageData?.records ?? []
  const total = pageData?.total ?? 0

  const payMutation = useMutation({
    mutationFn: (params: { id: number; data: { paymentAmount: number; paymentMethod: string } }) => http.put(`/patient/me/bills/${params.id}/pay`, params.data),
    onSuccess: () => {
      setPayId(null)
      queryClient.invalidateQueries({ queryKey: ['me', 'bills'] })
    },
  })

  const openPay = (bill: BillVO) => {
    setPayId(bill.id)
    const amount = bill.patientResponsibility != null ? bill.patientResponsibility : (bill.totalCharge != null ? bill.totalCharge : '')
    setPayForm({ paymentAmount: String(amount), paymentMethod: 'CREDIT_CARD' })
  }

  const [payError, setPayError] = useState('')

  const handlePay = (e: FormEvent) => {
    e.preventDefault()
    if (!payId) return
    const amount = Number(payForm.paymentAmount)
    if (isNaN(amount) || amount <= 0) { setPayError('Amount must be a positive number'); return }
    const bill = data.find(b => b.id === payId)
    const max = bill?.patientResponsibility ?? bill?.totalCharge ?? Infinity
    if (amount > max) { setPayError(`Amount cannot exceed $${max}`); return }
    setPayError('')
    payMutation.mutate({ id: payId, data: { paymentAmount: amount, paymentMethod: payForm.paymentMethod } })
  }

  return (<div>
    <h2 style={{ marginBottom: 20 }}>My Bills</h2>
    <table className={styles.table}>
      <thead><tr><th>ID</th><th>Type</th><th>Status</th><th>Total</th><th>Ins Paid</th><th>I Owe</th><th></th></tr></thead>
      <tbody>{data.map(r => (
        <tr key={r.id}>
          <td>{r.id}</td><td>{r.billType}</td>
          <td><span style={{ color: BILL_STATUS_COLOR[r.claimStatus || ''] || '#909399', fontWeight: 600 }}>{r.claimStatus}</span></td>
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
        {payError && <div style={{ gridColumn: 'span 2', color: '#F56C6C', fontSize: 12 }}>{payError}</div>}
        <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => { setPayId(null); setPayError('') }}>Cancel</button><button type="submit" className={styles.btnPrimary}>Pay</button></div>
      </form>
    </div></div>}
  </div>)
}

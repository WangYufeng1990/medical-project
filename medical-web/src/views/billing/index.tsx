import { useState, useEffect, FormEvent } from 'react'
import { useLocation } from 'react-router-dom'
import { getBillPage, createBill, submitBill, adjudicateBill, payBill, denyBill, deleteBill } from '../../api/bill'
import { getPatientPage } from '../../api/patient'
import { PAGE_SIZE, BILL_STATUS_COLOR } from '../../utils/labels'
import styles from '../shared.module.css'

const emptyForm: any = { patientId: '', totalCharge: '', billType: 'PROFESSIONAL', cptCodes: '', icd10Codes: '', insurancePayerName: '', copayAmount: '' }

export default function Billing() {
  const [data, setData] = useState<any[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ ...emptyForm })
  const [patients, setPatients] = useState<any[]>([])

  const [adjudicateId, setAdjudicateId] = useState<number | null>(null)
  const [adjForm, setAdjForm] = useState({ insurancePayment: '', adjustment: '0', claimNumber: '', adjudicationDate: '' })

  const location = useLocation()
  useEffect(() => { getBillPage({ page, size: PAGE_SIZE }).then(r => { setData(r.records); setTotal(r.total) }) }, [page, location])
  useEffect(() => { getPatientPage({ page: 1, size: 999 }).then(r => setPatients(r.records || [])) }, [])

  const refresh = () => getBillPage({ page, size: PAGE_SIZE }).then(r => { setData(r.records); setTotal(r.total) })

  const handleCreate = async (e: FormEvent) => {
    e.preventDefault()
    await createBill({ ...form, patientId: Number(form.patientId), totalCharge: Number(form.totalCharge), copayAmount: form.copayAmount !== '' ? Number(form.copayAmount) : undefined })
    setShowForm(false); refresh()
  }

  const handleAdjudicate = async (e: FormEvent) => {
    e.preventDefault()
    if (!adjudicateId) return
    await adjudicateBill(adjudicateId, { insurancePayment: Number(adjForm.insurancePayment), adjustment: adjForm.adjustment ? Number(adjForm.adjustment) : 0, claimNumber: adjForm.claimNumber || undefined, adjudicationDate: adjForm.adjudicationDate || undefined })
    setAdjudicateId(null); refresh()
  }

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Billing</h2>
      <button className={styles.btnPrimary} onClick={() => { setForm({ ...emptyForm }); setShowForm(true) }} style={{ marginBottom: 16 }}>+ Create Bill</button>
      <table className={styles.table}>
        <thead><tr><th>ID</th><th>Patient</th><th>Type</th><th>Status</th><th>Total</th><th>Ins Pay</th><th>Patient Resp</th><th></th></tr></thead>
        <tbody>{data.map(r => (
          <tr key={r.id} className={styles.clickableRow}>
            <td>{r.id}</td><td>{r.patientName}</td><td>{r.billType}</td>
            <td><span style={{ color: BILL_STATUS_COLOR[r.claimStatus] || '#909399', fontWeight: 600 }}>{r.claimStatus}</span></td>
            <td>{r.totalCharge}</td><td>{r.insurancePayment}</td><td>{r.patientResponsibility}</td>
            <td onClick={e => e.stopPropagation()}>
              {r.claimStatus === 'DRAFT' && <button className={styles.btnSm} onClick={async () => { if (confirm('Submit claim?')) { await submitBill(r.id); refresh() } }}>Submit</button>}
              {r.claimStatus === 'SUBMITTED' && <button className={styles.btnSm} onClick={() => { setAdjudicateId(r.id); setAdjForm({ insurancePayment: '', adjustment: '0', claimNumber: '', adjudicationDate: '' }) }}>Adjudicate</button>}
              {r.claimStatus === 'PENDING' && <button className={styles.btnSm} onClick={async () => { const pmt = prompt('Payment amount:'); const pmtMethod = prompt('Payment method (CASH/CARD/CHECK):'); if (pmt && pmtMethod) { await payBill(r.id, { paymentAmount: Number(pmt), paymentMethod: pmtMethod }); refresh() } }}>Pay</button>}
              {r.claimStatus === 'PENDING' && <button className={styles.btnSmDanger} onClick={async () => { const reason = prompt('Denial reason:'); if (reason) { await denyBill(r.id, reason); refresh() } }}>Deny</button>}
              <button className={styles.btnSmDanger} onClick={async () => { if (confirm('Delete?')) { await deleteBill(r.id); setData(d => d.filter(x => x.id !== r.id)) } }}>Del</button>
            </td></tr>
        ))}</tbody>
      </table>
      <div className={styles.pagination}><span>Total: {total}</span><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>

      {showForm && <div className={styles.modalOverlay} onClick={() => setShowForm(false)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>Create Bill</h3>
        <form onSubmit={handleCreate} className={styles.formGrid}>
          <div className={styles.formGroup}>
            <label>Patient</label>
            <select value={form.patientId} onChange={e => setForm({ ...form, patientId: e.target.value })}>
              <option value="">-- Select --</option>
              {patients.map((p: any) => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
          </div>
          <div className={styles.formGroup}><label>Total Charge</label><input type="number" step="0.01" value={form.totalCharge} onChange={e => setForm({ ...form, totalCharge: e.target.value })} /></div>
          <div className={styles.formGroup}>
            <label>Bill Type</label>
            <select value={form.billType} onChange={e => setForm({ ...form, billType: e.target.value })}>
              <option value="PROFESSIONAL">Professional</option><option value="INSTITUTIONAL">Institutional</option><option value="DENTAL">Dental</option><option value="PHARMACY">Pharmacy</option>
            </select>
          </div>
          <div className={styles.formGroup}><label>CPT Codes</label><input value={form.cptCodes} onChange={e => setForm({ ...form, cptCodes: e.target.value })} placeholder="e.g. 99213,97110" /></div>
          <div className={styles.formGroup}><label>ICD-10 Codes</label><input value={form.icd10Codes} onChange={e => setForm({ ...form, icd10Codes: e.target.value })} placeholder="e.g. E11.9,I10" /></div>
          <div className={styles.formGroup}><label>Insurance Payer</label><input value={form.insurancePayerName} onChange={e => setForm({ ...form, insurancePayerName: e.target.value })} /></div>
          <div className={styles.formGroup}><label>Copay</label><input type="number" step="0.01" value={form.copayAmount} onChange={e => setForm({ ...form, copayAmount: e.target.value })} /></div>
          <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => setShowForm(false)}>Cancel</button><button type="submit" className={styles.btnPrimary}>Save</button></div>
        </form>
      </div></div>}

      {adjudicateId && <div className={styles.modalOverlay} onClick={() => setAdjudicateId(null)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>Adjudicate Bill #{adjudicateId}</h3>
        <form onSubmit={handleAdjudicate} className={styles.formGrid}>
          <div className={styles.formGroup}><label>Insurance Payment</label><input type="number" step="0.01" value={adjForm.insurancePayment} onChange={e => setAdjForm({ ...adjForm, insurancePayment: e.target.value })} /></div>
          <div className={styles.formGroup}><label>Adjustment</label><input type="number" step="0.01" value={adjForm.adjustment} onChange={e => setAdjForm({ ...adjForm, adjustment: e.target.value })} /></div>
          <div className={styles.formGroup}><label>Claim Number</label><input value={adjForm.claimNumber} onChange={e => setAdjForm({ ...adjForm, claimNumber: e.target.value })} /></div>
          <div className={styles.formGroup}><label>Adjudication Date</label><input type="date" value={adjForm.adjudicationDate} onChange={e => setAdjForm({ ...adjForm, adjudicationDate: e.target.value })} /></div>
          <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => setAdjudicateId(null)}>Cancel</button><button type="submit" className={styles.btnPrimary}>Save</button></div>
        </form>
      </div></div>}
    </div>
  )
}

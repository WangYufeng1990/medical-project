import { useState, FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getBillPage, createBill, submitBill, adjudicateBill, payBill, denyBill, deleteBill } from '../../api/bill'
import { getPatientPage } from '../../api/patient'
import { PAGE_SIZE, BILL_STATUS_COLOR } from '../../utils/labels'
import styles from '../shared.module.css'

const emptyForm: any = { patientId: '', totalCharge: '', billType: 'PROFESSIONAL', cptCodes: '', icd10Codes: '', insurancePayerName: '', copayAmount: '' }

export default function Billing() {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ ...emptyForm })
  const [adjudicateId, setAdjudicateId] = useState<number | null>(null)
  const [adjForm, setAdjForm] = useState({ insurancePayment: '', adjustment: '0', claimNumber: '', adjudicationDate: '' })
  const [payBillId, setPayBillId] = useState<number | null>(null)
  const [payForm, setPayForm] = useState({ paymentAmount: '', paymentMethod: 'CASH' })
  const [denyBillId, setDenyBillId] = useState<number | null>(null)
  const [denyReason, setDenyReason] = useState('')

  const { data: pageData } = useQuery({
    queryKey: ['billing', 'list', { page, size: PAGE_SIZE }],
    queryFn: () => getBillPage({ page, size: PAGE_SIZE }),
  })
  const data = pageData?.records ?? []
  const total = pageData?.total ?? 0

  const { data: patients } = useQuery({
    queryKey: ['patients', 'all'],
    queryFn: () => getPatientPage({ page: 1, size: 999 }).then(r => r.records ?? []),
  })

  const createMutation = useMutation({
    mutationFn: (data: any) => createBill(data),
    onSuccess: () => { setShowForm(false); queryClient.invalidateQueries({ queryKey: ['billing'] }) },
  })

  const submitMutation = useMutation({
    mutationFn: (id: number) => submitBill(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['billing'] }),
  })

  const adjudicateMutation = useMutation({
    mutationFn: (params: { id: number; data: any }) => adjudicateBill(params.id, params.data),
    onSuccess: () => { setAdjudicateId(null); queryClient.invalidateQueries({ queryKey: ['billing'] }) },
  })

  const payMutation = useMutation({
    mutationFn: (params: { id: number; data: any }) => payBill(params.id, params.data),
    onSuccess: () => { setPayBillId(null); queryClient.invalidateQueries({ queryKey: ['billing'] }) },
  })

  const denyMutation = useMutation({
    mutationFn: (params: { id: number; reason: string }) => denyBill(params.id, params.reason),
    onSuccess: () => { setDenyBillId(null); queryClient.invalidateQueries({ queryKey: ['billing'] }) },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteBill(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['billing'] }),
  })

  const handleCreate = (e: FormEvent) => {
    e.preventDefault()
    createMutation.mutate({ ...form, patientId: Number(form.patientId), totalCharge: Number(form.totalCharge), copayAmount: form.copayAmount !== '' ? Number(form.copayAmount) : undefined })
  }

  const handleAdjudicate = (e: FormEvent) => {
    e.preventDefault()
    if (!adjudicateId) return
    adjudicateMutation.mutate({ id: adjudicateId, data: { insurancePayment: Number(adjForm.insurancePayment), adjustment: adjForm.adjustment ? Number(adjForm.adjustment) : 0, claimNumber: adjForm.claimNumber || undefined, adjudicationDate: adjForm.adjudicationDate || undefined } })
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
              {r.claimStatus === 'DRAFT' && <button className={styles.btnSm} onClick={() => { if (confirm('Submit claim?')) submitMutation.mutate(r.id) }}>Submit</button>}
              {r.claimStatus === 'SUBMITTED' && <button className={styles.btnSm} onClick={() => { setAdjudicateId(r.id); setAdjForm({ insurancePayment: '', adjustment: '0', claimNumber: '', adjudicationDate: '' }) }}>Adjudicate</button>}
              {r.claimStatus === 'PENDING' && <button className={styles.btnSm} onClick={() => { setPayBillId(r.id); setPayForm({ paymentAmount: '', paymentMethod: 'CASH' }) }}>Pay</button>}
              {r.claimStatus === 'PENDING' && <button className={styles.btnSmDanger} onClick={() => { setDenyBillId(r.id); setDenyReason('') }}>Deny</button>}
              <button className={styles.btnSmDanger} onClick={() => { if (confirm('Delete?')) deleteMutation.mutate(r.id) }}>Del</button>
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
              {(patients ?? []).map((p: any) => <option key={p.id} value={p.id}>{p.name}</option>)}
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

      {payBillId && <div className={styles.modalOverlay} onClick={() => setPayBillId(null)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>Pay Bill #{payBillId}</h3>
        <form onSubmit={e => { e.preventDefault(); if (payForm.paymentAmount) payMutation.mutate({ id: payBillId, data: { paymentAmount: Number(payForm.paymentAmount), paymentMethod: payForm.paymentMethod }}) }} className={styles.formGrid}>
          <div className={styles.formGroup}><label>Amount</label><input type="number" step="0.01" value={payForm.paymentAmount} onChange={e => setPayForm({ ...payForm, paymentAmount: e.target.value })} /></div>
          <div className={styles.formGroup}>
            <label>Method</label>
            <select value={payForm.paymentMethod} onChange={e => setPayForm({ ...payForm, paymentMethod: e.target.value })}>
              <option value="CASH">Cash</option><option value="CARD">Card</option><option value="CHECK">Check</option>
            </select>
          </div>
          <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => setPayBillId(null)}>Cancel</button><button type="submit" className={styles.btnPrimary} disabled={payMutation.isPending || !payForm.paymentAmount}>Pay</button></div>
        </form>
      </div></div>}

      {denyBillId && <div className={styles.modalOverlay} onClick={() => setDenyBillId(null)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>Deny Bill #{denyBillId}</h3>
        <form onSubmit={e => { e.preventDefault(); if (denyReason.trim()) denyMutation.mutate({ id: denyBillId, reason: denyReason.trim() }) }} className={styles.formGrid}>
          <div className={styles.formGroup} style={{ gridColumn: 'span 2' }}><label>Denial Reason</label><input value={denyReason} onChange={e => setDenyReason(e.target.value)} placeholder="e.g. Not medically necessary" /></div>
          <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => setDenyBillId(null)}>Cancel</button><button type="submit" className={styles.btnSmDanger} disabled={denyMutation.isPending || !denyReason.trim()}>Deny</button></div>
        </form>
      </div></div>}
    </div>
  )
}

import { useState, FormEvent } from 'react'
import { useConfirm } from '../../utils/ConfirmDialog'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getBillPage, createBill, submitBill, adjudicateBill, payBill, denyBill, deleteBill } from '../../api/bill'
import { getPatientPage } from '../../api/patient'
import { getAppointmentPage } from '../../api/appointment'
import { getChargePage, convertCharge } from '../../api/charge'
import { PAGE_SIZE, BILL_STATUS_COLOR } from '../../utils/labels'
import styles from '../shared.module.css'

const FEE_SCHEDULE: any = { '99203': 100, '99213': 90, '99214': 130, '99244': 200, 'NEW_PATIENT': 120, 'FOLLOW_UP': 90, 'ANNUAL_PHYSICAL': 150, 'PROCEDURE': 250 }

const emptyForm: any = { patientId: '', totalCharge: '', billType: 'PROFESSIONAL', cptCodes: '', icd10Codes: '', insurancePayerName: '', copayAmount: '' }

export default function Billing() {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [filterPatientId, setFilterPatientId] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ ...emptyForm })
  const [linkedApptId, setLinkedApptId] = useState('')
  const [adjudicateId, setAdjudicateId] = useState<number | null>(null)
  const [adjForm, setAdjForm] = useState({ insurancePayment: '', adjustment: '0', claimNumber: '', adjudicationDate: '' })
  const [payBillId, setPayBillId] = useState<number | null>(null)
  const [payForm, setPayForm] = useState({ paymentAmount: '', paymentMethod: 'CASH' })
  const [denyBillId, setDenyBillId] = useState<number | null>(null)
  const [denyReason, setDenyReason] = useState('')
  const [formError, setFormError] = useState('')
  const { confirm } = useConfirm()

  const { data: pageData } = useQuery({
    queryKey: ['billing', 'list', { page, size: PAGE_SIZE, patientId: filterPatientId ? Number(filterPatientId) : undefined }],
    queryFn: () => getBillPage({ page, size: PAGE_SIZE, ...(filterPatientId ? { patientId: Number(filterPatientId) } : {}) }),
  })
  const data = pageData?.records ?? []
  const total = pageData?.total ?? 0

  const { data: patients } = useQuery({
    queryKey: ['patients', 'all'],
    queryFn: () => getPatientPage({ page: 1, size: 999 }).then(r => r.records ?? []),
  })

  const { data: appointments } = useQuery({
    queryKey: ['appointments', 'forBill', form.patientId],
    queryFn: () => form.patientId ? getAppointmentPage({ patientId: Number(form.patientId), size: 100 }) : Promise.resolve({ records: [] }),
    enabled: !!form.patientId && showForm,
  })
  const apptList = (appointments as any)?.records ?? []

  const { data: charges } = useQuery({
    queryKey: ['charges', 'list', { size: 50 }],
    queryFn: () => getChargePage({ size: 50 }).then(r => r.records ?? []),
  })
  const draftCharges = (charges ?? []).filter((c: any) => c.status === 'DRAFT')

  const selectAppointment = (apptId: string) => {
    setLinkedApptId(apptId)
    const appt = apptList.find((a: any) => String(a.id) === apptId)
    if (!appt) return
    const cpt = appt.cptCode || ''
    const icd = appt.icd10Codes || appt.chiefComplaint || ''
    const visit = appt.visitType || 'FOLLOW_UP'
    const suggestedFee = FEE_SCHEDULE[cpt] || FEE_SCHEDULE[visit] || 90
    setForm(prev => ({ ...prev, cptCodes: cpt, icd10Codes: icd, billType: visit === 'ANNUAL_PHYSICAL' ? 'PROFESSIONAL' : 'PROFESSIONAL', totalCharge: String(suggestedFee) }))
  }

  const openCreateForm = () => {
    setLinkedApptId('')
    setForm({ ...emptyForm })
    setShowForm(true)
  }

  const createMutation = useMutation({
    mutationFn: (d: any) => createBill(d),
    onSuccess: () => { setShowForm(false); queryClient.invalidateQueries({ queryKey: ['billing'] }) },
  })

  const convertMutation = useMutation({
    mutationFn: (id: number) => convertCharge(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['charges'] }); queryClient.invalidateQueries({ queryKey: ['billing'] }) },
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
    if (!form.patientId) { setFormError('Patient is required'); return }
    if (!form.totalCharge || Number(form.totalCharge) <= 0) { setFormError('Total charge must be a positive number'); return }
    setFormError('')
    createMutation.mutate({
      ...form,
      patientId: Number(form.patientId),
      totalCharge: form.totalCharge !== '' ? Number(form.totalCharge) : undefined,
      copayAmount: form.copayAmount !== '' ? Number(form.copayAmount) : undefined,
    })
  }

  const handleAdjudicate = (e: FormEvent) => {
    e.preventDefault()
    if (!adjudicateId) return
    adjudicateMutation.mutate({ id: adjudicateId, data: { insurancePayment: Number(adjForm.insurancePayment), adjustment: adjForm.adjustment ? Number(adjForm.adjustment) : 0, claimNumber: adjForm.claimNumber || undefined, adjudicationDate: adjForm.adjudicationDate || undefined } })
  }

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Billing</h2>

      {draftCharges.length > 0 && (
        <div style={{ marginBottom: 20, padding: 16, background: '#f0f9eb', borderRadius: 8, border: '1px solid #e1f3d8' }}>
          <h3 style={{ margin: '0 0 8px 0', color: '#67C23A' }}>Draft Charges — Ready to Convert ({draftCharges.length})</h3>
          <table className={styles.table} style={{ background: '#fff' }}>
            <thead><tr><th>ID</th><th>Patient</th><th>Appt</th><th>CPT</th><th>Amount</th><th></th></tr></thead>
            <tbody>{draftCharges.map((c: any) => (
              <tr key={c.id}>
                <td>{c.id}</td><td>{(patients ?? []).find((p: any) => p.id === c.patientId)?.name ?? `#${c.patientId}`}</td><td>{c.appointmentId ?? '-'}</td><td>{c.cptCodes || '-'}</td><td>${c.chargeAmount}</td>
                <td><button className={styles.btnSm} disabled={convertMutation.isPending} onClick={async () => { if (await confirm('Convert this charge to a bill?')) convertMutation.mutate(c.id) }}>Convert to Bill</button></td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}

      <div style={{ display: 'flex', gap: 12, marginBottom: 16, alignItems: 'center' }}>
        <button className={styles.btnPrimary} onClick={openCreateForm}>+ Create Bill</button>
        <select value={filterPatientId} onChange={e => { setFilterPatientId(e.target.value); setPage(1) }} style={{ padding: '6px 12px', borderRadius: 6, border: '1px solid #d9d9d9' }}>
          <option value="">All Patients</option>
          {(patients ?? []).map((p: any) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
      </div>

      <table className={styles.table}>
        <thead><tr><th>ID</th><th>Patient</th><th>Type</th><th>Status</th><th>Total</th><th>Ins Pay</th><th>Patient Resp</th><th>Claim#</th><th></th></tr></thead>
        <tbody>{data.map(r => (
          <tr key={r.id} className={styles.clickableRow}>
            <td>{r.id}</td><td>{r.patientName}</td><td>{r.billType}</td>
            <td><span style={{ color: BILL_STATUS_COLOR[r.claimStatus] || '#909399', fontWeight: 600 }}>{r.claimStatus}</span></td>
            <td>{r.totalCharge}</td><td>{r.insurancePayment}</td><td>{r.patientResponsibility}</td>
            <td style={{ fontSize: 11 }}>{r.insuranceClaimNumber || '-'}</td>
            <td onClick={e => e.stopPropagation()}>
              {r.claimStatus === 'DRAFT' && <button className={styles.btnSm} onClick={async () => { if (await confirm('Submit claim?')) submitMutation.mutate(r.id) }}>Submit</button>}
              {r.claimStatus === 'SUBMITTED' && <button className={styles.btnSm} onClick={() => { setAdjudicateId(r.id); setAdjForm({ insurancePayment: '', adjustment: '0', claimNumber: '', adjudicationDate: '' }) }}>Adjudicate</button>}
              {r.claimStatus === 'PENDING' && <button className={styles.btnSm} onClick={() => { setPayBillId(r.id); setPayForm({ paymentAmount: '', paymentMethod: 'CASH' }) }}>Pay</button>}
              {r.claimStatus === 'PENDING' && <button className={styles.btnSmDanger} onClick={() => { setDenyBillId(r.id); setDenyReason('') }}>Deny</button>}
              <button className={styles.btnSmDanger} onClick={async () => { if (await confirm('Delete?')) deleteMutation.mutate(r.id) }}>Del</button>
            </td></tr>
        ))}</tbody>
      </table>
      <div className={styles.pagination}><span>Total: {total}</span><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>

      {showForm && <div className={styles.modalOverlay} onClick={() => setShowForm(false)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>Create Bill</h3>
        <form onSubmit={handleCreate} className={styles.formGrid}>
          <div className={styles.formGroup}>
            <label>Patient</label>
            <select value={form.patientId} onChange={e => { setLinkedApptId(''); setForm({ ...emptyForm, patientId: e.target.value }) }}>
              <option value="">-- Select --</option>
              {(patients ?? []).map((p: any) => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
          </div>
          <div className={styles.formGroup}>
            <label>Link to Appointment</label>
            <select value={linkedApptId} onChange={e => selectAppointment(e.target.value)} style={{ width: '100%' }}>
              <option value="">-- Optional auto-fill --</option>
              {apptList.filter((a: any) => a.status !== 2 && a.status !== 4).map((a: any) => <option key={a.id} value={a.id}>#{a.id} — {a.appointmentTime?.substring(0, 16)} {a.visitType}</option>)}
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
          {formError && <div style={{ gridColumn: 'span 2', color: '#F56C6C', fontSize: 12 }}>{formError}</div>}
          <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => { setShowForm(false); setFormError('') }}>Cancel</button><button type="submit" className={styles.btnPrimary}>Save</button></div>
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
          <div className={styles.formGroup}><label>Method</label><select value={payForm.paymentMethod} onChange={e => setPayForm({ ...payForm, paymentMethod: e.target.value })}><option value="CASH">Cash</option><option value="CARD">Card</option><option value="CHECK">Check</option></select></div>
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

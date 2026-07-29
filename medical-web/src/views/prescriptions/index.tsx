import { useState, FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getPrescriptionPage, getPrescriptionById, createPrescription, deletePrescription, transmitPrescription, cancelPrescription } from '../../api/prescription'
import { getPatientPage } from '../../api/patient'
import { getDoctors } from '../../api/user'
import { getPharmacies } from '../../api/pharmacy'
import { checkCds, lookupDrug } from '../../api/cds'
import CdsWarningModal from './CdsWarningModal'
import { getPendingRefillRequests, approveRefillRequest, denyRefillRequest } from '../../api/refill'
import { PAGE_SIZE } from '../../utils/labels'
import { useConfirm } from '../../utils/ConfirmDialog'
import styles from '../shared.module.css'

const emptyItem = { drugName: '', rxnormCode: '', dosage: '', frequency: '', duration: '', quantity: '', refills: '', notes: '' }
const emptyForm: any = { patientId: '', doctorId: '', diagnosis: '', icd10Codes: '', prescriptionDate: '', prescriptionType: 'MEDICATION', rxStatus: 'active', items: [{ ...emptyItem }] }

export default function Prescriptions() {
  const queryClient = useQueryClient()
  const { confirm } = useConfirm()
  const [page, setPage] = useState(1)
  const [showForm, setShowForm] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [form, setForm] = useState({ ...emptyForm })
  const [transmitId, setTransmitId] = useState<number | null>(null)
  const [pharmacies, setPharmacies] = useState<any[]>([])
  const [selectedPharmacy, setSelectedPharmacy] = useState('')
  const [cdsWarnings, setCdsWarnings] = useState<any[]>([])
  const [showCdsModal, setShowCdsModal] = useState(false)
  const [pendingCdsPayload, setPendingCdsPayload] = useState<any>(null)

  const { data: refillRequests } = useQuery({
    queryKey: ['prescriptions', 'refill-requests'],
    queryFn: () => getPendingRefillRequests().then(r => r ?? []),
  })

  const approveRefill = useMutation({
    mutationFn: (id: number) => approveRefillRequest(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['prescriptions', 'refill-requests'] }),
  })

  const denyRefill = useMutation({
    mutationFn: (params: { id: number; notes?: string }) => denyRefillRequest(params.id, params.notes),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['prescriptions', 'refill-requests'] }),
  })

  const { data: pageData } = useQuery({
    queryKey: ['prescriptions', 'list', { page, size: PAGE_SIZE }],
    queryFn: () => getPrescriptionPage({ page, size: PAGE_SIZE }),
  })
  const data = pageData?.records ?? []
  const total = pageData?.total ?? 0

  const { data: patients } = useQuery({
    queryKey: ['patients', 'all'],
    queryFn: () => getPatientPage({ page: 1, size: 999 }).then(r => r.records ?? []),
  })

  const { data: doctors } = useQuery({
    queryKey: ['users', 'all'],
    queryFn: () => getDoctors().then(r => r ?? []),
  })

  const saveMutation = useMutation({
    mutationFn: (payload: any) => createPrescription(payload),
    onSuccess: () => {
      setShowForm(false)
      queryClient.invalidateQueries({ queryKey: ['prescriptions'] })
    },
  })

  const transmitMutation = useMutation({
    mutationFn: (params: { id: number; pharmacyId: number }) => transmitPrescription(params.id, params.pharmacyId),
    onSuccess: () => {
      setTransmitId(null)
      queryClient.invalidateQueries({ queryKey: ['prescriptions'] })
    },
  })

  const cancelMutation = useMutation({
    mutationFn: (id: number) => cancelPrescription(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['prescriptions'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deletePrescription(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['prescriptions'] }),
  })

  const openTransmit = async (id: number) => {
    setTransmitId(id)
    setSelectedPharmacy('')
    const ph = await getPharmacies()
    setPharmacies(ph || [])
  }

  const handleTransmit = () => {
    if (!transmitId || !selectedPharmacy) return
    transmitMutation.mutate({ id: transmitId, pharmacyId: Number(selectedPharmacy) })
  }

  const openForm = async (row?: any) => {
    if (row) {
      setEditId(row.id)
      const detail = await getPrescriptionById(row.id)
      setForm({
        patientId: detail.patientId ?? '',
        doctorId: detail.doctorId ?? '',
        diagnosis: detail.diagnosis ?? '',
        icd10Codes: detail.icd10Codes ?? '',
        prescriptionDate: detail.prescriptionDate ?? '',
        prescriptionType: detail.prescriptionType ?? 'MEDICATION',
        rxStatus: detail.rxStatus ?? 'active',
        items: detail.items?.length ? detail.items.map((i: any) => ({
          drugName: i.drugName ?? '', rxnormCode: i.rxnormCode ?? '', dosage: i.dosage ?? '',
          frequency: i.frequency ?? '', duration: i.duration ?? '', quantity: i.quantity ?? '',
          refills: i.refills ?? '', notes: i.notes ?? ''
        })) : [{ ...emptyItem }]
      })
    } else {
      setEditId(null)
      setForm({ ...emptyForm })
    }
    setShowForm(true)
  }

  const doSave = (payload: any) => {
    saveMutation.mutate(payload)
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const payload = {
      ...form,
      patientId: Number(form.patientId),
      doctorId: Number(form.doctorId),
      prescriptionDate: form.prescriptionDate || undefined,
      items: form.items.filter((it: any) => it.drugName).map((it: any) => ({
        ...it, duration: it.duration !== '' ? Number(it.duration) : null, quantity: it.quantity !== '' ? Number(it.quantity) : null, refills: it.refills !== '' ? Number(it.refills) : null
      }))
    }
    try {
      const cdsItems = form.items.filter((it: any) => it.drugName).map((it: any) => ({
        rxnormCode: it.rxnormCode || '', drugName: it.drugName
      }))
      const cdsResult = await checkCds({ patientId: payload.patientId, items: cdsItems })
      if (cdsResult.passed === true) {
        doSave(payload)
      } else {
        setPendingCdsPayload(payload)
        setCdsWarnings(cdsResult.warnings ?? [])
        setShowCdsModal(true)
      }
    } catch {
      alert('CDS check unavailable — cannot verify drug safety. Please try again.')
    }
  }

  const handleOverrideSave = () => {
    if (!pendingCdsPayload) return
    setShowCdsModal(false)
    setCdsWarnings([])
    setPendingCdsPayload(null)
    doSave(pendingCdsPayload)
  }

  const addItem = () => setForm({ ...form, items: [...form.items, { ...emptyItem }] })
  const removeItem = (idx: number) => setForm({ ...form, items: form.items.filter((_: any, i: number) => i !== idx) })
  const updateItem = (idx: number, field: string, value: string) => {
    setForm(prev => {
      const items = [...prev.items]
      items[idx] = { ...items[idx], [field]: value }
      return { ...prev, items }
    })
  }

  const handleRxnormChange = (idx: number, code: string) => {
    setForm(prev => {
      const items = [...prev.items]
      items[idx] = { ...items[idx], rxnormCode: code }
      return { ...prev, items }
    })
    if (!code || !code.trim()) return
    lookupDrug(code.trim()).then(result => {
      if (result.drugName) {
        setForm(prev => {
          if (prev.items[idx]?.rxnormCode !== code.trim()) return prev
          const items = [...prev.items]
          items[idx] = { ...items[idx], drugName: result.drugName }
          return { ...prev, items }
        })
      }
    }).catch(() => {})
  }

  return (
    <div>
      {refillRequests && refillRequests.length > 0 && (
        <div style={{ marginBottom: 20, padding: 16, background: '#fef0f0', borderRadius: 8, border: '1px solid #fde2e2' }}>
          <h3 style={{ margin: '0 0 12px 0', color: '#E6A23C' }}>Pending Refill Requests ({refillRequests.length})</h3>
          <table className={styles.table} style={{ background: '#fff' }}>
            <thead><tr><th>ID</th><th>Patient</th><th>Prescription</th><th>Reason</th><th>Requested</th><th></th></tr></thead>
            <tbody>{refillRequests.map((r: any) => (
              <tr key={r.id}>
                <td>{r.id}</td>
                <td>{((patients ?? []) as any[]).find((p: any) => p.id === r.patientId)?.name ?? `#${r.patientId}`}</td>
                <td>{r.prescriptionId}</td>
                <td>{r.reason || '-'}</td>
                <td>{r.requestedAt?.substring(0, 16)}</td>
                <td style={{ display: 'flex', gap: 4 }}>
                  <button className={styles.btnSm} disabled={approveRefill.isPending} onClick={() => approveRefill.mutate(r.id)}>Approve</button>
                  <button className={styles.btnSmDanger} disabled={denyRefill.isPending} onClick={() => {
                    const notes = prompt('Denial reason (optional):')
                    denyRefill.mutate({ id: r.id, notes: notes || undefined })
                  }}>Deny</button>
                </td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
      <h2 style={{ marginBottom: 20 }}>Prescriptions</h2>
      <button className={styles.btnPrimary} onClick={() => openForm()} style={{ marginBottom: 16 }}>+ Add Prescription</button>
      <table className={styles.table}>
        <thead><tr><th>ID</th><th>Patient</th><th>Doctor</th><th>Diagnosis</th><th>ICD-10</th><th>Date</th><th>Status</th><th></th></tr></thead>
        <tbody>{data.map(r => (
          <tr key={r.id} className={styles.clickableRow}>
            <td>{r.id}</td><td>{r.patientName}</td><td>{r.doctorName}</td><td>{r.diagnosis}</td><td>{r.icd10Codes}</td><td>{r.prescriptionDate}</td><td>{r.rxStatus}</td>
            <td onClick={e => e.stopPropagation()}>
              {r.rxStatus === 'active' && <button className={styles.btnSm} onClick={() => openTransmit(r.id)}>Transmit</button>}
              {r.rxStatus === 'active' && <button className={styles.btnSmDanger} onClick={async () => { if (await confirm('Cancel prescription?')) cancelMutation.mutate(r.id) }}>Cancel</button>}
              {!['transmitted', 'dispensed', 'cancelled'].includes(r.rxStatus) && <button className={styles.btnSmDanger} onClick={async () => { if (await confirm('Delete?')) deleteMutation.mutate(r.id) }}>Del</button>}
            </td></tr>
        ))}</tbody>
      </table>
      <div className={styles.pagination}><span>Total: {total}</span><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>

      {showForm && <div className={styles.modalOverlay} onClick={() => setShowForm(false)}><div className={styles.modal} onClick={e => e.stopPropagation()} style={{ maxWidth: 900 }}>
        <h3>{editId ? 'Edit' : 'Add'} Prescription</h3>
        <form onSubmit={handleSubmit}>
          <div className={styles.formGrid}>
            <div className={styles.formGroup}>
              <label>Patient</label>
              <select value={form.patientId} onChange={e => setForm({ ...form, patientId: e.target.value })}>
                <option value="">-- Select Patient --</option>
                {(patients ?? []).map((p: any) => <option key={p.id} value={p.id}>{p.name} (ID:{p.id})</option>)}
              </select>
            </div>
            <div className={styles.formGroup}><label>Doctor</label>
              <select value={form.doctorId} onChange={e => setForm({ ...form, doctorId: e.target.value })}>
                <option value="">-- Select --</option>
                {(doctors ?? []).map((d: any) => <option key={d.id} value={d.id}>{d.realName || d.username}</option>)}
              </select></div>
            <div className={styles.formGroup}><label>Diagnosis</label><input value={form.diagnosis} onChange={e => setForm({ ...form, diagnosis: e.target.value })} /></div>
            <div className={styles.formGroup}><label>ICD-10 Codes</label><input value={form.icd10Codes} onChange={e => setForm({ ...form, icd10Codes: e.target.value })} placeholder="e.g. E11.9,I10" /></div>
            <div className={styles.formGroup}><label>Date</label><input type="date" value={form.prescriptionDate} onChange={e => setForm({ ...form, prescriptionDate: e.target.value })} /></div>
            <div className={styles.formGroup}>
              <label>Type</label>
              <select value={form.prescriptionType} onChange={e => setForm({ ...form, prescriptionType: e.target.value })}>
                <option value="MEDICATION">Medication</option><option value="CONTROLLED">Controlled</option><option value="COMPOUND">Compound</option>
              </select>
            </div>
            {editId && <div className={styles.formGroup}>
              <label>Status</label>
              <select value={form.rxStatus} onChange={e => setForm({ ...form, rxStatus: e.target.value })}>
                <option value="active">Active</option><option value="transmitted">Transmitted</option><option value="dispensed">Dispensed</option><option value="cancelled">Cancelled</option>
              </select>
            </div>}
          </div>

          <h4 style={{ marginTop: 20, marginBottom: 8 }}>Items</h4>
          {form.items.map((it: any, idx: number) => (
            <div key={idx} style={{ display: 'grid', gridTemplateColumns: '1fr 100px 1fr 1fr 80px 80px 80px 60px', gap: 8, marginBottom: 8, alignItems: 'end' }}>
              <div className={styles.formGroup}><label>Drug</label><input value={it.drugName} onChange={e => updateItem(idx, 'drugName', e.target.value)} placeholder="Drug name" /></div>
              <div className={styles.formGroup}><label>RxNorm</label><input value={it.rxnormCode} onChange={e => handleRxnormChange(idx, e.target.value)} placeholder="e.g. 6809" /></div>
              <div className={styles.formGroup}><label>Dosage</label><input value={it.dosage} onChange={e => updateItem(idx, 'dosage', e.target.value)} placeholder="e.g. 500mg" /></div>
              <div className={styles.formGroup}><label>Frequency</label><input value={it.frequency} onChange={e => updateItem(idx, 'frequency', e.target.value)} placeholder="e.g. BID" /></div>
              <div className={styles.formGroup}><label>Duration</label><input value={it.duration} onChange={e => updateItem(idx, 'duration', e.target.value)} placeholder="days" /></div>
              <div className={styles.formGroup}><label>Qty</label><input value={it.quantity} onChange={e => updateItem(idx, 'quantity', e.target.value)} placeholder="30" /></div>
              <div className={styles.formGroup}><label>Refills</label><input value={it.refills} onChange={e => updateItem(idx, 'refills', e.target.value)} placeholder="0" /></div>
              <div style={{ display: 'flex', alignItems: 'center', paddingBottom: 2 }}>
                {form.items.length > 1 && <button type="button" className={styles.btnSmDanger} style={{ margin: 0 }} onClick={() => removeItem(idx)}>✕</button>}
              </div>
            </div>
          ))}
          <button type="button" className={styles.btnSm} onClick={addItem} style={{ marginBottom: 16 }}>+ Add Item</button>

          <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => setShowForm(false)}>Cancel</button><button type="submit" className={styles.btnPrimary}>Save</button></div>
        </form>
      </div></div>}

      {transmitId && <div className={styles.modalOverlay} onClick={() => setTransmitId(null)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>Transmit Prescription #{transmitId}</h3>
        <div className={styles.formGroup} style={{ marginBottom: 16 }}>
          <label>Pharmacy</label>
          <select value={selectedPharmacy} onChange={e => setSelectedPharmacy(e.target.value)}>
            <option value="">-- Select Pharmacy --</option>
            {pharmacies.map((p: any) => <option key={p.id} value={p.id}>{p.name} — {p.city}, {p.state} {p.zip}</option>)}
          </select>
        </div>
        <div className={styles.formActions}><button className={styles.btnSm} onClick={() => setTransmitId(null)}>Cancel</button><button className={styles.btnPrimary} onClick={handleTransmit} disabled={!selectedPharmacy}>Transmit (NCPDP SCRIPT)</button></div>
      </div></div>}

      {showCdsModal && (
        <CdsWarningModal
          warnings={cdsWarnings}
          onOverride={handleOverrideSave}
          onCancel={() => { setShowCdsModal(false); setCdsWarnings([]); setPendingCdsPayload(null) }}
        />
      )}
    </div>
  )
}

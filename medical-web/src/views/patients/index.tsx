import { useState, FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { getPatientPage, getPatientById, createPatient, updatePatient, deletePatient, getPatientHistory, addPatientHistory, getPatientAllergies, addPatientAllergy, resolvePatientAllergy } from '../../api/patient'
import { getConsents, createConsent, revokeConsent } from '../../api/consent'
import { initiateEmergencyAccess } from '../../api/emergency'
import { getVitalSigns, createVitalSign } from '../../api/vitalSign'
import { getProblems, createProblem, updateProblem } from '../../api/problem'
import { getImmunizations, createImmunization } from '../../api/immunization'
import { getCarePlans, createCarePlan, updateCarePlan } from '../../api/carePlan'
import { PAGE_SIZE, CONSENT_TYPES, CONSENT_STATUS_COLOR } from '../../utils/labels'
import styles from '../shared.module.css'

const emptyForm: any = { name: '', mrn: '', ssn: '', dateOfBirth: '', sexAtBirth: 'M', genderIdentity: '', race: '', ethnicity: '', preferredLanguage: 'en', maritalStatus: '', phoneMobile: '', phoneHome: '', phoneWork: '', email: '', addressLine1: '', addressLine2: '', city: '', state: '', zipCode: '', emergencyContactName: '', emergencyContactPhone: '', emergencyContactRelation: '', insurancePayer: '', insuranceMemberId: '', insuranceGroupNumber: '', primaryCareProvider: '', medicalHistory: '', allergies: '', patientStatus: 'active' }

export default function Patients() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [showForm, setShowForm] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [viewOnly, setViewOnly] = useState(false)
  const [form, setForm] = useState({ ...emptyForm })
  const [consentPatient, setConsentPatient] = useState<{ id: number; name: string } | null>(null)
  const [consents, setConsents] = useState<any[]>([])
  const [newConsentType, setNewConsentType] = useState('TREATMENT')
  const [newConsentScope, setNewConsentScope] = useState('')
  const [creating, setCreating] = useState(false)
  const [emergencyPatientId, setEmergencyPatientId] = useState<number | null>(null)
  const [emergencyReason, setEmergencyReason] = useState('')
  const [emergencySubmitting, setEmergencySubmitting] = useState(false)
  const [emergencyPrescriptions, setEmergencyPrescriptions] = useState<any[] | null>(null)
  const [historyEntries, setHistoryEntries] = useState<any[]>([])
  const [allergyEntries, setAllergyEntries] = useState<any[]>([])
  const [newHistoryDesc, setNewHistoryDesc] = useState('')
  const [newAllergy, setNewAllergy] = useState({ allergen: '', reaction: '', severity: 'MODERATE' })
  const [vitalSigns, setVitalSigns] = useState<any[]>([])
  const [problems, setProblems] = useState<any[]>([])
  const [newVital, setNewVital] = useState({ systolicBp: '', diastolicBp: '', heartRate: '', temperature: '', respiratoryRate: '', oxygenSaturation: '', heightCm: '', weightKg: '', bmi: '', notes: '' })
  const [newProblem, setNewProblem] = useState({ snomedCode: '', snomedDisplay: '', icd10Code: '', onsetDate: '', severity: 'MODERATE', notes: '' })
  const [immunizations, setImmunizations] = useState<any[]>([])
  const [newImmunization, setNewImmunization] = useState({ vaccineName: '', cvxCode: '', administrationDate: '', doseNumber: '', lotNumber: '', manufacturer: '', site: '', route: '', notes: '' })
  const [carePlans, setCarePlans] = useState<any[]>([])
  const [newCarePlan, setNewCarePlan] = useState({ title: '', goal: '', interventions: '', targetDate: '', notes: '' })

  const { data: pageData, isLoading } = useQuery({
    queryKey: ['patients', 'list', { page, size: PAGE_SIZE }],
    queryFn: () => getPatientPage({ page, size: PAGE_SIZE }),
  })
  const data = pageData?.records ?? []
  const total = pageData?.total ?? 0

  const onError = () => alert('Operation failed. Please try again.')

  const saveMutation = useMutation({
    mutationFn: (params: { id?: number; data: any }) =>
      params.id != null ? updatePatient(params.id, params.data) : createPatient(params.data),
    onSuccess: () => {
      setShowForm(false)
      queryClient.invalidateQueries({ queryKey: ['patients'] })
    },
    onError,
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deletePatient(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['patients'] }),
    onError,
  })

  const openForm = async (row?: any, viewOnlyParam: boolean = false) => {
    setViewOnly(viewOnlyParam)
    setEmergencyPrescriptions(null)
    setHistoryEntries([])
    setAllergyEntries([])
    setNewHistoryDesc('')
    setNewAllergy({ allergen: '', reaction: '', severity: 'MODERATE' })
    if (row) {
      setEditId(row.id)
      const emToken = sessionStorage.getItem('emergencyToken')
      const emPid = sessionStorage.getItem('emergencyPatientId')
      if (emToken && emPid && String(row.id) === emPid) {
        const [patientRes, rxRes] = await Promise.all([
          axios.get(`/api/v1/patients/${row.id}`, { headers: { Authorization: `Bearer ${emToken}` } }),
          axios.get(`/api/v1/prescriptions/by-patient/${row.id}`, { headers: { Authorization: `Bearer ${emToken}` } })
        ])
        setForm({ ...emptyForm, ...patientRes.data.data })
        setEmergencyPrescriptions(rxRes.data.data || [])
        sessionStorage.removeItem('emergencyToken')
        sessionStorage.removeItem('emergencyPatientId')
      } else {
        const d = await getPatientById(row.id)
        setForm({ ...emptyForm, ...d })
        try { setHistoryEntries(await getPatientHistory(row.id) || []) } catch { setHistoryEntries([]) }
        try { setAllergyEntries(await getPatientAllergies(row.id) || []) } catch { setAllergyEntries([]) }
        try { setVitalSigns((await getVitalSigns(row.id, { page: 1, size: 100 }))?.records ?? []) } catch { setVitalSigns([]) }
        try { setProblems((await getProblems(row.id, { page: 1, size: 100 }))?.records ?? []) } catch { setProblems([]) }
        try { setImmunizations((await getImmunizations(row.id, { page: 1, size: 100 }))?.records ?? []) } catch { setImmunizations([]) }
        try { setCarePlans((await getCarePlans(row.id, { page: 1, size: 100 }))?.records ?? []) } catch { setCarePlans([]) }
      }
    } else {
      setEditId(null); setForm({ ...emptyForm })
    }
    setShowForm(true)
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    saveMutation.mutate(editId != null ? { id: editId, data: form } : { data: form })
  }

  const handleDelete = (id: number) => {
    if (!confirm('Delete this patient?')) return
    deleteMutation.mutate(id)
  }

  const maskPhone = (p: string) => p ? '****' + p.slice(-4) : ''
  const maskEmail = (e: string) => { const at = e?.indexOf('@'); return at > 0 ? e[0] + '***' + e.slice(at) : '' }

  const openConsent = async (row: any) => {
    setConsentPatient({ id: row.id, name: row.name })
    setNewConsentType('TREATMENT')
    setNewConsentScope('')
    try {
      const r = await getConsents(row.id)
      setConsents(r)
    } catch {
      setConsents([])
    }
  }

  const handleCreateConsent = async () => {
    if (!consentPatient) return
    setCreating(true)
    try {
      await createConsent({ patientId: consentPatient.id, consentType: newConsentType, scope: newConsentScope })
      const r = await getConsents(consentPatient.id)
      setConsents(r)
      setNewConsentType('TREATMENT')
      setNewConsentScope('')
    } catch (err: any) {
      alert(err?.response?.data?.message || 'Failed to create consent')
    } finally {
      setCreating(false)
    }
  }

  const openEmergencyPrompt = (patientId: number) => {
    setEmergencyPatientId(patientId)
    setEmergencyReason('')
  }

  const handleInitiateEmergency = async () => {
    if (emergencyPatientId == null || !emergencyReason.trim()) return
    setEmergencySubmitting(true)
    try {
      const r = await initiateEmergencyAccess(emergencyPatientId, emergencyReason)
      sessionStorage.setItem('emergencyToken', r.token)
      sessionStorage.setItem('emergencyPatientId', String(r.patientId))
      const row = data.find(p => p.id === emergencyPatientId)
      setEmergencyPatientId(null)
      setEmergencyReason('')
      setEmergencySubmitting(false)
      if (row) openForm(row)
    } catch (err: any) {
      alert(err?.response?.data?.message || 'Failed to initiate emergency access')
      setEmergencySubmitting(false)
    }
  }

  const handleRevokeConsent = async (id: number) => {
    if (!confirm('Revoke this consent?')) return
    try {
      await revokeConsent(id)
      if (consentPatient) {
        const r = await getConsents(consentPatient.id)
        setConsents(r)
      }
    } catch (err: any) {
      alert(err?.response?.data?.message || 'Failed to revoke consent')
    }
  }

  if (isLoading) return <p style={{ color: '#909399' }}>Loading...</p>

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Patients</h2>
      <button className={styles.btnPrimary} onClick={() => openForm()} style={{ marginBottom: 16 }}>+ Add Patient</button>

      <table className={styles.table}>
        <thead><tr>
          <th>ID</th><th>MRN</th><th>Name</th><th>DOB</th><th>Phone</th><th>Email</th><th></th>
        </tr></thead>
        <tbody>
          {data.map(row => (
            <tr key={row.id} className={styles.clickableRow} onClick={() => openForm(row, true)}>
              <td>{row.id}</td><td>{row.mrn}</td><td>{row.name}</td><td>{row.dateOfBirth}</td>
              <td>{maskPhone(row.phoneMobile)}</td><td>{maskEmail(row.email)}</td>
              <td onClick={e => e.stopPropagation()}>
                <button className={styles.btnSm} onClick={() => navigate(`/chat?partnerId=${row.id}&partnerName=${encodeURIComponent(row.name)}`)}>Msg</button>
                <button className={styles.btnSm} onClick={() => openForm(row)}>Edit</button>
                <button className={styles.btnSm} onClick={() => openConsent(row)}>Consent</button>
                <button className={styles.btnSm} onClick={() => openEmergencyPrompt(row.id)}>Break Glass</button>
                <button className={styles.btnSmDanger} onClick={() => handleDelete(row.id)}>Del</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className={styles.pagination}>
        <span>Total: {total}</span>
        <button disabled={page <= 1} onClick={() => setPage(p => p - 1)}>Prev</button>
        <span>Page {page}</span>
        <button disabled={page * PAGE_SIZE >= total} onClick={() => setPage(p => p + 1)}>Next</button>
      </div>

      {consentPatient && (
        <div style={{ marginTop: 24, background: '#fff', borderRadius: 6, padding: 20, boxShadow: '0 1px 6px rgba(0,0,0,.06)' }}>
          <h3 style={{ marginBottom: 16 }}>Consents — {consentPatient.name}</h3>
          <table className={styles.table}>
            <thead><tr><th>ID</th><th>Type</th><th>Scope</th><th>Status</th><th>Signed At</th><th></th></tr></thead>
            <tbody>
              {consents.map(c => (
                <tr key={c.id}>
                  <td>{c.id}</td><td>{c.consentType}</td><td>{c.scope}</td>
                  <td><span style={{ color: CONSENT_STATUS_COLOR[c.status] ?? '#909399', fontWeight: 600 }}>{c.status}</span></td>
                  <td>{c.consentDate ?? c.createTime}</td>
                  <td>
                    <button className={styles.btnSmDanger} disabled={c.status !== 'active'}
                      onClick={() => handleRevokeConsent(c.id)}>Revoke</button>
                  </td>
                </tr>
              ))}
              {consents.length === 0 && (
                <tr><td colSpan={6} style={{ textAlign: 'center', color: '#909399', padding: 20 }}>No consents</td></tr>
              )}
            </tbody>
          </table>
          <div style={{ marginTop: 16, display: 'flex', gap: 12, alignItems: 'flex-end' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <label style={{ fontSize: 12, color: '#909399' }}>Type</label>
              <select value={newConsentType} onChange={e => setNewConsentType(e.target.value)}
                style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13 }}>
                {CONSENT_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <label style={{ fontSize: 12, color: '#909399' }}>Scope</label>
              <input value={newConsentScope} onChange={e => setNewConsentScope(e.target.value)}
                style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13 }} />
            </div>
            <button className={styles.btnPrimary} disabled={creating || !newConsentScope.trim()}
              onClick={handleCreateConsent}>{creating ? '...' : 'Create Consent'}</button>
            <button className={styles.btnSm} onClick={() => setConsentPatient(null)}>Close</button>
          </div>
        </div>
      )}

      {emergencyPatientId != null && (
        <div className={styles.modalOverlay} onClick={() => setEmergencyPatientId(null)}>
          <div className={styles.modal} onClick={e => e.stopPropagation()} style={{ maxWidth: 500 }}>
            <h3>Emergency Access (Break Glass)</h3>
            <p style={{ fontSize: 13, color: '#909399', marginBottom: 16 }}>
              This grants temporary access to patient #{emergencyPatientId}'s full record. All access is audited. Enter the clinical reason for this emergency access.
            </p>
            <div className={styles.formGroup}>
              <label>Reason</label>
              <textarea value={emergencyReason} onChange={e => setEmergencyReason(e.target.value)}
                style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13, minHeight: 80, resize: 'vertical' }} />
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button className={styles.btnSm} onClick={() => setEmergencyPatientId(null)}>Cancel</button>
              <button className={styles.btnPrimary} disabled={emergencySubmitting || !emergencyReason.trim()}
                onClick={handleInitiateEmergency}>{emergencySubmitting ? 'Accessing...' : 'Access Patient Record'}</button>
            </div>
          </div>
        </div>
      )}

      {showForm && (
        <div className={styles.modalOverlay} onClick={() => setShowForm(false)}>
          <div className={styles.modal} onClick={e => e.stopPropagation()}>
            <h3>{editId ? (viewOnly ? 'View Patient' : 'Edit Patient') : 'Add Patient'}</h3>
            <form onSubmit={handleSubmit} className={styles.formGrid}>
              {['name','mrn','ssn','dateOfBirth','sexAtBirth'].map(f => (
                <div key={f} className={styles.formGroup}>
                  <label>{f}</label>
                  <input value={form[f] ?? ''} disabled={!!editId || viewOnly} style={{ background: (editId || viewOnly) ? '#f5f7fa' : undefined }} onChange={e => setForm({ ...form, [f]: e.target.value })} />
                </div>
              ))}
              {['genderIdentity','race','ethnicity','preferredLanguage','maritalStatus',
                'phoneMobile','phoneHome','phoneWork','email','addressLine1','addressLine2','city','state','zipCode',
                'emergencyContactName','emergencyContactPhone','emergencyContactRelation',
                'insurancePayer','insuranceMemberId','insuranceGroupNumber','primaryCareProvider'].map(f => (
                <div key={f} className={styles.formGroup}>
                  <label>{f}</label>
                  <input value={form[f] ?? ''} disabled={viewOnly} onChange={e => setForm({ ...form, [f]: e.target.value })} />
                </div>
              ))}
              {emergencyPrescriptions != null && (
                <div style={{ gridColumn: 'span 2', borderTop: '1px solid #f56c6c', paddingTop: 16, marginTop: 8 }}>
                  <h4 style={{ color: '#f56c6c', marginBottom: 12 }}>Emergency Access — Active Prescriptions</h4>
                  {emergencyPrescriptions.length === 0 ? (
                    <p style={{ fontSize: 13, color: '#909399' }}>No active prescriptions found.</p>
                  ) : (
                    emergencyPrescriptions.map((rx: any) => (
                      <div key={rx.id} style={{ marginBottom: 12, padding: '10px 14px', border: '1px solid #ebeef5', borderRadius: 6, background: '#fafafa' }}>
                        <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 4 }}>
                          {rx.diagnosis} <span style={{ color: '#909399', fontWeight: 400 }}>({rx.icd10Codes || 'N/A'})</span>
                          <span style={{ marginLeft: 12, fontSize: 12, color: rx.rxStatus === 'active' ? '#67C23A' : '#E6A23C' }}>{rx.rxStatus}</span>
                        </div>
                        <div style={{ fontSize: 12, color: '#606266' }}>
                          Prescribed by {rx.doctorName} on {rx.prescriptionDate}
                        </div>
                        {rx.items && (
                          <table style={{ width: '100%', marginTop: 8, fontSize: 12, borderCollapse: 'collapse' }}>
                            <thead>
                              <tr style={{ background: '#f5f7fa' }}>
                                <th style={{ padding: '4px 8px', textAlign: 'left', borderBottom: '1px solid #ebeef5' }}>Drug</th>
                                <th style={{ padding: '4px 8px', textAlign: 'left', borderBottom: '1px solid #ebeef5' }}>Dosage</th>
                                <th style={{ padding: '4px 8px', textAlign: 'left', borderBottom: '1px solid #ebeef5' }}>Frequency</th>
                                <th style={{ padding: '4px 8px', textAlign: 'left', borderBottom: '1px solid #ebeef5' }}>Duration</th>
                                <th style={{ padding: '4px 8px', textAlign: 'left', borderBottom: '1px solid #ebeef5' }}>Qty</th>
                              </tr>
                            </thead>
                            <tbody>
                              {rx.items.map((item: any) => (
                                <tr key={item.id}>
                                  <td style={{ padding: '4px 8px', borderBottom: '1px solid #ebeef5' }}>{item.drugName}</td>
                                  <td style={{ padding: '4px 8px', borderBottom: '1px solid #ebeef5' }}>{item.dosage}</td>
                                  <td style={{ padding: '4px 8px', borderBottom: '1px solid #ebeef5' }}>{item.frequency}</td>
                                  <td style={{ padding: '4px 8px', borderBottom: '1px solid #ebeef5' }}>{item.duration}d</td>
                                  <td style={{ padding: '4px 8px', borderBottom: '1px solid #ebeef5' }}>{item.quantity}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        )}
                      </div>
                    ))
                  )}
                </div>
              )}
              {editId && (
                <>
                  <div style={{ gridColumn: 'span 2', borderTop: '1px solid #ebeef5', paddingTop: 16, marginTop: 8 }}>
                    <h4 style={{ marginBottom: 8 }}>Medical History</h4>
                    {historyEntries.length === 0 && <p style={{ fontSize: 12, color: '#909399', marginBottom: 8 }}>No history entries.</p>}
                    {historyEntries.map((e: any) => (
                      <div key={e.id} style={{ fontSize: 12, color: '#606266', marginBottom: 4, padding: '4px 8px', background: '#fafafa', borderRadius: 4 }}>
                        {e.description}
                        <span style={{ color: '#909399', marginLeft: 8 }}>— {e.createTime ? e.createTime.substring(0, 10) : ''}</span>
                      </div>
                    ))}
                    <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                      <input value={newHistoryDesc} onChange={e => setNewHistoryDesc(e.target.value)} placeholder="Add history entry..."
                        style={{ flex: 1, padding: '4px 8px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 12 }} />
                      <button type="button" className={styles.btnSm} disabled={!newHistoryDesc.trim()} onClick={async () => {
                        if (!editId || !newHistoryDesc.trim()) return
                        await addPatientHistory(editId, newHistoryDesc.trim())
                        setHistoryEntries(await getPatientHistory(editId))
                        setNewHistoryDesc('')
                      }}>Add</button>
                    </div>
                  </div>
                  <div style={{ gridColumn: 'span 2', borderTop: '1px solid #ebeef5', paddingTop: 16, marginTop: 8 }}>
                    <h4 style={{ marginBottom: 8 }}>Allergies</h4>
                    {allergyEntries.length === 0 && <p style={{ fontSize: 12, color: '#909399', marginBottom: 8 }}>No known allergies.</p>}
                    {allergyEntries.map((e: any) => {
                      const resolved = e.status === 'resolved'
                      return (
                        <div key={e.id} style={{ fontSize: 12, color: resolved ? '#909399' : '#606266', marginBottom: 4, padding: '4px 8px', background: resolved ? '#f5f5f5' : '#fafafa', borderRadius: 4, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                          <span>
                            <strong style={{ textDecoration: resolved ? 'line-through' : 'none' }}>{e.allergen}</strong>
                            {e.reaction && <span style={{ color: resolved ? '#909399' : '#E6A23C' }}> — {e.reaction}</span>}
                            {e.severity && <span style={{ color: resolved ? '#909399' : (e.severity === 'SEVERE' ? '#F56C6C' : '#E6A23C'), marginLeft: 8 }}>[{e.severity}]</span>}
                            {resolved && e.resolvedAt && <span style={{ marginLeft: 8, fontStyle: 'italic' }}>resolved {e.resolvedAt.substring(0, 10)}</span>}
                          </span>
                          {!resolved && <button type="button" className={styles.btnSmDanger} onClick={async () => {
                            if (!editId || !confirm('Resolve this allergy? This records that the patient no longer has this allergy.')) return
                            await resolvePatientAllergy(editId, e.id)
                            setAllergyEntries(await getPatientAllergies(editId))
                          }}>Resolve</button>}
                        </div>
                      )
                    })}
                    <div style={{ display: 'flex', gap: 8, marginTop: 8, alignItems: 'end' }}>
                      <div className={styles.formGroup} style={{ flex: 1 }}><label style={{ fontSize: 11 }}>Allergen</label><input value={newAllergy.allergen} onChange={e => setNewAllergy({ ...newAllergy, allergen: e.target.value })} placeholder="e.g. Penicillin" style={{ padding: '4px 8px', fontSize: 12 }} /></div>
                      <div className={styles.formGroup} style={{ flex: 1 }}><label style={{ fontSize: 11 }}>Reaction</label><input value={newAllergy.reaction} onChange={e => setNewAllergy({ ...newAllergy, reaction: e.target.value })} placeholder="e.g. Anaphylaxis" style={{ padding: '4px 8px', fontSize: 12 }} /></div>
                      <div className={styles.formGroup} style={{ width: 100 }}><label style={{ fontSize: 11 }}>Severity</label>
                        <select value={newAllergy.severity} onChange={e => setNewAllergy({ ...newAllergy, severity: e.target.value })} style={{ padding: '4px 6px', fontSize: 12 }}>
                          <option value="MILD">Mild</option><option value="MODERATE">Moderate</option><option value="SEVERE">Severe</option>
                        </select>
                      </div>
                      <button type="button" className={styles.btnSm} disabled={!newAllergy.allergen.trim()} onClick={async () => {
                        if (!editId || !newAllergy.allergen.trim()) return
                        await addPatientAllergy(editId, { allergen: newAllergy.allergen.trim(), reaction: newAllergy.reaction.trim() || null, severity: newAllergy.severity })
                        setAllergyEntries(await getPatientAllergies(editId))
                        setNewAllergy({ allergen: '', reaction: '', severity: 'MODERATE' })
                      }}>+</button>
                    </div>
                  </div>
                </>
              )}
              {editId && (
                <>
                  <div style={{ gridColumn: 'span 2', borderTop: '1px solid #ebeef5', paddingTop: 16, marginTop: 8 }}>
                    <h4 style={{ marginBottom: 8 }}>Vital Signs</h4>
                    {vitalSigns.length === 0 && <p style={{ fontSize: 12, color: '#909399', marginBottom: 8 }}>No vital signs recorded.</p>}
                    {vitalSigns.map((v: any) => (
                      <div key={v.id} style={{ fontSize: 12, color: '#606266', marginBottom: 4, padding: '4px 8px', background: '#fafafa', borderRadius: 4 }}>
                        <span style={{ fontWeight: 600 }}>{v.recordedAt?.substring(0, 10)}</span>
                        {v.systolicBp != null && <span style={{ marginLeft: 12 }}>BP: <strong>{v.systolicBp}/{v.diastolicBp}</strong></span>}
                        {v.heartRate != null && <span style={{ marginLeft: 12 }}>HR: <strong>{v.heartRate}</strong></span>}
                        {v.temperature != null && <span style={{ marginLeft: 12 }}>Temp: <strong>{v.temperature}°C</strong></span>}
                        {v.respiratoryRate != null && <span style={{ marginLeft: 12 }}>RR: <strong>{v.respiratoryRate}</strong></span>}
                        {v.oxygenSaturation != null && <span style={{ marginLeft: 12 }}>O₂: <strong>{v.oxygenSaturation}%</strong></span>}
                        {v.bmi != null && <span style={{ marginLeft: 12 }}>BMI: <strong>{v.bmi}</strong></span>}
                        {v.notes && <div style={{ color: '#909399', marginTop: 2 }}>{v.notes}</div>}
                      </div>
                    ))}
                    <div style={{ display: 'flex', gap: 6, marginTop: 8, flexWrap: 'wrap', alignItems: 'end' }}>
                      <input value={newVital.systolicBp} onChange={e => setNewVital({ ...newVital, systolicBp: e.target.value })} placeholder="SBP" style={{ width: 54, padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }} />
                      <input value={newVital.diastolicBp} onChange={e => setNewVital({ ...newVital, diastolicBp: e.target.value })} placeholder="DBP" style={{ width: 54, padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }} />
                      <input value={newVital.heartRate} onChange={e => setNewVital({ ...newVital, heartRate: e.target.value })} placeholder="HR" style={{ width: 48, padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }} />
                      <input value={newVital.temperature} onChange={e => setNewVital({ ...newVital, temperature: e.target.value })} placeholder="Temp" style={{ width: 54, padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }} />
                      <input value={newVital.oxygenSaturation} onChange={e => setNewVital({ ...newVital, oxygenSaturation: e.target.value })} placeholder="O2%" style={{ width: 48, padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }} />
                      <input value={newVital.notes} onChange={e => setNewVital({ ...newVital, notes: e.target.value })} placeholder="Notes" style={{ width: 120, padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }} />
                      <button type="button" className={styles.btnSm} disabled={!newVital.systolicBp && !newVital.diastolicBp && !newVital.heartRate && !newVital.temperature} onClick={async () => {
                        if (!editId) return
                        await createVitalSign(editId, {
                          systolicBp: newVital.systolicBp !== '' ? Number(newVital.systolicBp) : null,
                          diastolicBp: newVital.diastolicBp !== '' ? Number(newVital.diastolicBp) : null,
                          heartRate: newVital.heartRate !== '' ? Number(newVital.heartRate) : null,
                          temperature: newVital.temperature !== '' ? Number(newVital.temperature) : null,
                          oxygenSaturation: newVital.oxygenSaturation !== '' ? Number(newVital.oxygenSaturation) : null,
                          notes: newVital.notes || null,
                        })
                        setVitalSigns((await getVitalSigns(editId, { page: 1, size: 100 }))?.records ?? [])
                        setNewVital({ systolicBp: '', diastolicBp: '', heartRate: '', temperature: '', respiratoryRate: '', oxygenSaturation: '', heightCm: '', weightKg: '', bmi: '', notes: '' })
                      }}>+ Add</button>
                    </div>
                  </div>
                  <div style={{ gridColumn: 'span 2', borderTop: '1px solid #ebeef5', paddingTop: 16, marginTop: 8 }}>
                    <h4 style={{ marginBottom: 8 }}>Problem List</h4>
                    {problems.length === 0 && <p style={{ fontSize: 12, color: '#909399', marginBottom: 8 }}>No active problems.</p>}
                    {problems.map((p: any) => (
                      <div key={p.id} style={{ fontSize: 12, color: p.status === 'RESOLVED' ? '#909399' : '#606266', marginBottom: 4, padding: '4px 8px', background: p.status === 'RESOLVED' ? '#f5f5f5' : '#fafafa', borderRadius: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span>
                          <strong style={{ textDecoration: p.status === 'RESOLVED' ? 'line-through' : 'none' }}>{p.snomedDisplay || 'Unspecified'}</strong>
                          {p.snomedCode && <span style={{ color: '#909399', marginLeft: 8, fontSize: 10 }}>({p.snomedCode})</span>}
                          {p.icd10Code && <span style={{ color: '#909399', marginLeft: 4, fontSize: 10 }}>[{p.icd10Code}]</span>}
                          <span style={{ color: p.severity === 'SEVERE' ? '#F56C6C' : p.severity === 'MODERATE' ? '#E6A23C' : '#67C23A', marginLeft: 8, fontSize: 10, fontWeight: 600 }}>[{p.severity}]</span>
                          <span style={{ marginLeft: 8, color: p.status === 'ACTIVE' ? '#67C23A' : '#909399', fontSize: 10 }}>{p.status}</span>
                          {p.onsetDate && <span style={{ marginLeft: 8, fontSize: 10, color: '#909399' }}>since {p.onsetDate}</span>}
                        </span>
                        <div style={{ display: 'flex', gap: 4 }}>
                          {p.status === 'ACTIVE' && (
                            <button type="button" className={styles.btnSm} onClick={async () => {
                              if (!editId || !confirm('Resolve this problem?')) return
                              await updateProblem(editId, p.id, { status: 'RESOLVED', resolutionDate: new Date().toISOString().slice(0, 10) })
                              setProblems((await getProblems(editId, { page: 1, size: 100 }))?.records ?? [])
                            }}>Resolve</button>
                          )}
                        </div>
                      </div>
                    ))}
                    <div style={{ display: 'flex', gap: 6, marginTop: 8, flexWrap: 'wrap', alignItems: 'end' }}>
                      <input value={newProblem.snomedDisplay} onChange={e => setNewProblem({ ...newProblem, snomedDisplay: e.target.value })} placeholder="Diagnosis name" style={{ width: 180, padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }} />
                      <input value={newProblem.snomedCode} onChange={e => setNewProblem({ ...newProblem, snomedCode: e.target.value })} placeholder="SNOMED" style={{ width: 80, padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }} />
                      <input value={newProblem.icd10Code} onChange={e => setNewProblem({ ...newProblem, icd10Code: e.target.value })} placeholder="ICD-10" style={{ width: 70, padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }} />
                      <select value={newProblem.severity} onChange={e => setNewProblem({ ...newProblem, severity: e.target.value })} style={{ padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }}>
                        <option value="MILD">Mild</option><option value="MODERATE">Moderate</option><option value="SEVERE">Severe</option>
                      </select>
                      <button type="button" className={styles.btnSm} disabled={!newProblem.snomedDisplay.trim()} onClick={async () => {
                        if (!editId || !newProblem.snomedDisplay.trim()) return
                        await createProblem(editId, {
                          snomedDisplay: newProblem.snomedDisplay.trim(),
                          snomedCode: newProblem.snomedCode.trim() || null,
                          icd10Code: newProblem.icd10Code.trim() || null,
                          severity: newProblem.severity,
                          onsetDate: newProblem.onsetDate || new Date().toISOString().slice(0, 10),
                          notes: newProblem.notes || null,
                        })
                        setProblems((await getProblems(editId, { page: 1, size: 100 }))?.records ?? [])
                        setNewProblem({ snomedCode: '', snomedDisplay: '', icd10Code: '', onsetDate: '', severity: 'MODERATE', notes: '' })
                      }}>+ Add</button>
                    </div>
                  </div>
                </>
              )}
              {editId && (
                <div style={{ gridColumn: 'span 2', borderTop: '1px solid #ebeef5', paddingTop: 16, marginTop: 8 }}>
                  <h4 style={{ marginBottom: 8 }}>Immunizations</h4>
                  {immunizations.length === 0 && <p style={{ fontSize: 12, color: '#909399', marginBottom: 8 }}>No immunizations recorded.</p>}
                  {immunizations.map((r: any) => (
                    <div key={r.id} style={{ fontSize: 12, color: '#606266', marginBottom: 4, padding: '4px 8px', background: '#fafafa', borderRadius: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span>
                        <strong>{r.vaccineName}</strong>
                        {r.cvxCode && <span style={{ color: '#909399', marginLeft: 8, fontSize: 10 }}>CVX:{r.cvxCode}</span>}
                        {r.doseNumber && <span style={{ color: '#409EFF', marginLeft: 8, fontSize: 10 }}>[{r.doseNumber}]</span>}
                        <span style={{ marginLeft: 8, color: '#909399', fontSize: 10 }}>{r.administrationDate}</span>
                        {r.lotNumber && <span style={{ marginLeft: 8, fontSize: 10 }}>Lot:{r.lotNumber}</span>}
                      </span>
                      <span style={{ color: r.status === 'completed' ? '#67C23A' : r.status === 'refused' ? '#F56C6C' : '#E6A23C', fontWeight: 600, fontSize: 10 }}>{r.status}</span>
                    </div>
                  ))}
                  <div style={{ display: 'flex', gap: 6, marginTop: 8, flexWrap: 'wrap', alignItems: 'end' }}>
                    <input value={newImmunization.vaccineName} onChange={e => setNewImmunization({ ...newImmunization, vaccineName: e.target.value })} placeholder="Vaccine name" style={{ width: 160, padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }} />
                    <input value={newImmunization.cvxCode} onChange={e => setNewImmunization({ ...newImmunization, cvxCode: e.target.value })} placeholder="CVX" style={{ width: 60, padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }} />
                    <input value={newImmunization.administrationDate} type="date" onChange={e => setNewImmunization({ ...newImmunization, administrationDate: e.target.value })} style={{ width: 110, padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }} />
                    <input value={newImmunization.doseNumber} onChange={e => setNewImmunization({ ...newImmunization, doseNumber: e.target.value })} placeholder="Dose #" style={{ width: 64, padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }} />
                    <input value={newImmunization.lotNumber} onChange={e => setNewImmunization({ ...newImmunization, lotNumber: e.target.value })} placeholder="Lot" style={{ width: 80, padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }} />
                    <button type="button" className={styles.btnSm} disabled={!newImmunization.vaccineName.trim()} onClick={async () => {
                      if (!editId || !newImmunization.vaccineName.trim()) return
                      await createImmunization(editId, {
                        vaccineName: newImmunization.vaccineName.trim(),
                        cvxCode: newImmunization.cvxCode.trim() || null,
                        administrationDate: newImmunization.administrationDate || new Date().toISOString().slice(0, 10),
                        doseNumber: newImmunization.doseNumber.trim() || null,
                        lotNumber: newImmunization.lotNumber.trim() || null,
                        manufacturer: newImmunization.manufacturer.trim() || null,
                        site: newImmunization.site.trim() || null,
                        route: newImmunization.route.trim() || null,
                        notes: newImmunization.notes.trim() || null,
                      })
                      setImmunizations((await getImmunizations(editId, { page: 1, size: 100 }))?.records ?? [])
                      setNewImmunization({ vaccineName: '', cvxCode: '', administrationDate: '', doseNumber: '', lotNumber: '', manufacturer: '', site: '', route: '', notes: '' })
                    }}>+ Add</button>
                  </div>
                </div>
              )}
              {editId && (
                <div style={{ gridColumn: 'span 2', borderTop: '1px solid #ebeef5', paddingTop: 16, marginTop: 8 }}>
                  <h4 style={{ marginBottom: 8 }}>Care Plans</h4>
                  {carePlans.length === 0 && <p style={{ fontSize: 12, color: '#909399', marginBottom: 8 }}>No care plans.</p>}
                  {carePlans.map((cp: any) => (
                    <div key={cp.id} style={{ fontSize: 12, color: cp.status === 'COMPLETED' ? '#909399' : '#606266', marginBottom: 4, padding: '4px 8px', background: cp.status === 'COMPLETED' ? '#f5f5f5' : '#fafafa', borderRadius: 4 }}>
                      <strong style={{ textDecoration: cp.status === 'COMPLETED' ? 'line-through' : 'none' }}>{cp.title}</strong>
                      <span style={{ marginLeft: 8, color: cp.status === 'ACTIVE' ? '#67C23A' : '#909399', fontSize: 10, fontWeight: 600 }}>[{cp.status}]</span>
                      {cp.goal && <div style={{ color: '#409EFF', fontSize: 11 }}>Goal: {cp.goal}</div>}
                      {cp.interventions && <div style={{ color: '#909399', fontSize: 11, marginTop: 2 }}>{cp.interventions}</div>}
                      {cp.startDate && <span style={{ color: '#909399', fontSize: 10 }}>{cp.startDate}{cp.targetDate ? ` → ${cp.targetDate}` : ''}</span>}
                    </div>
                  ))}
                  <div style={{ display: 'flex', gap: 6, marginTop: 8, flexWrap: 'wrap', alignItems: 'end' }}>
                    <input value={newCarePlan.title} onChange={e => setNewCarePlan({ ...newCarePlan, title: e.target.value })} placeholder="Title" style={{ width: 140, padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }} />
                    <input value={newCarePlan.goal} onChange={e => setNewCarePlan({ ...newCarePlan, goal: e.target.value })} placeholder="Goal" style={{ width: 160, padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }} />
                    <input value={newCarePlan.interventions} onChange={e => setNewCarePlan({ ...newCarePlan, interventions: e.target.value })} placeholder="Interventions" style={{ width: 180, padding: '4px 6px', fontSize: 11, border: '1px solid #dcdfe6', borderRadius: 4 }} />
                    <button type="button" className={styles.btnSm} disabled={!newCarePlan.title.trim()} onClick={async () => {
                      if (!editId || !newCarePlan.title.trim()) return
                      await createCarePlan(editId, { title: newCarePlan.title.trim(), goal: newCarePlan.goal.trim() || null, interventions: newCarePlan.interventions.trim() || null, targetDate: newCarePlan.targetDate || null, notes: newCarePlan.notes || null })
                      setCarePlans((await getCarePlans(editId, { page: 1, size: 100 }))?.records ?? [])
                      setNewCarePlan({ title: '', goal: '', interventions: '', targetDate: '', notes: '' })
                    }}>+ Add</button>
                  </div>
                </div>
              )}
              <div className={styles.formActions}>
                <button type="button" className={styles.btnSm} onClick={() => setShowForm(false)}>Cancel</button>
                {!viewOnly && <button type="submit" className={styles.btnPrimary}>{editId ? 'Update' : 'Create'}</button>}
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

import { useState, useEffect, FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import { getPatientPage, getPatientById, createPatient, updatePatient, deletePatient } from '../../api/patient'
import { getConsents, createConsent, revokeConsent } from '../../api/consent'
import { initiateEmergencyAccess } from '../../api/emergency'
import { PAGE_SIZE, CONSENT_TYPES, CONSENT_STATUS_COLOR } from '../../utils/labels'
import styles from '../shared.module.css'

const emptyForm: any = { name: '', mrn: '', ssn: '', dateOfBirth: '', sexAtBirth: 'M', genderIdentity: '', race: '', ethnicity: '', preferredLanguage: 'en', maritalStatus: '', phoneMobile: '', phoneHome: '', phoneWork: '', email: '', addressLine1: '', addressLine2: '', city: '', state: '', zipCode: '', emergencyContactName: '', emergencyContactPhone: '', emergencyContactRelation: '', insurancePayer: '', insuranceMemberId: '', insuranceGroupNumber: '', primaryCareProvider: '', medicalHistory: '', allergies: '', patientStatus: 'active' }

export default function Patients() {
  const navigate = useNavigate()
  const [data, setData] = useState<any[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [showForm, setShowForm] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
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

  const fetchData = async () => {
    setLoading(true)
    try { const r = await getPatientPage({ page, size: PAGE_SIZE }); setData(r.records); setTotal(r.total) } finally { setLoading(false) }
  }
  useEffect(() => { fetchData() }, [page])

  const openForm = async (row?: any) => {
    setEmergencyPrescriptions(null)
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
        const rxs = rxRes.data.data || []
        setEmergencyPrescriptions(rxs)
        sessionStorage.removeItem('emergencyToken')
        sessionStorage.removeItem('emergencyPatientId')
      } else {
        const d = await getPatientById(row.id)
        setForm({ ...emptyForm, ...d })
      }
    } else {
      setEditId(null); setForm({ ...emptyForm })
    }
    setShowForm(true)
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    editId ? await updatePatient(editId, form) : await createPatient(form)
    setShowForm(false); fetchData()
  }

  const handleDelete = async (id: number) => {
    if (!confirm('Delete this patient?')) return
    await deletePatient(id); fetchData()
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
    setEmergencyResult(null)
  }

  const handleInitiateEmergency = async () => {
    if (emergencyPatientId == null || !emergencyReason.trim()) return
    setEmergencySubmitting(true)
    try {
      const r = await initiateEmergencyAccess(emergencyPatientId, emergencyReason)
      sessionStorage.setItem('emergencyToken', r.token)
      sessionStorage.setItem('emergencyPatientId', String(r.patientId))
      setEmergencyPatientId(null)
      setEmergencyReason('')
      const row = data.find(p => p.id === emergencyPatientId)
      if (row) {
        setEmergencySubmitting(false)
        openForm(row)
      }
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
            <tr key={row.id}>
              <td>{row.id}</td><td>{row.mrn}</td><td>{row.name}</td><td>{row.dateOfBirth}</td>
              <td>{maskPhone(row.phoneMobile)}</td><td>{maskEmail(row.email)}</td>
              <td>
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
            <h3>{editId ? 'Edit Patient' : 'Add Patient'}</h3>
            <form onSubmit={handleSubmit} className={styles.formGrid}>
              {['name','mrn','ssn','dateOfBirth','sexAtBirth','medicalHistory','allergies'].map(f => (
                <div key={f} className={styles.formGroup}>
                  <label>{f}</label>
                  <input value={form[f] ?? ''} disabled={!!editId} style={{ background: editId ? '#f5f7fa' : undefined }} onChange={e => setForm({ ...form, [f]: e.target.value })} />
                </div>
              ))}
              {['genderIdentity','race','ethnicity','preferredLanguage','maritalStatus',
                'phoneMobile','phoneHome','phoneWork','email','addressLine1','addressLine2','city','state','zipCode',
                'emergencyContactName','emergencyContactPhone','emergencyContactRelation',
                'insurancePayer','insuranceMemberId','insuranceGroupNumber','primaryCareProvider'].map(f => (
                <div key={f} className={styles.formGroup}>
                  <label>{f}</label>
                  <input value={form[f] ?? ''} onChange={e => setForm({ ...form, [f]: e.target.value })} />
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
              <div className={styles.formActions}>
                <button type="button" className={styles.btnSm} onClick={() => setShowForm(false)}>Cancel</button>
                <button type="submit" className={styles.btnPrimary}>{editId ? 'Update' : 'Create'}</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

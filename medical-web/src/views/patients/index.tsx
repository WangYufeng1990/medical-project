import { useState, useEffect, FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { getPatientPage, getPatientById, createPatient, updatePatient, deletePatient } from '../../api/patient'
import { getConsents, createConsent, revokeConsent } from '../../api/consent'
import { PAGE_SIZE, CONSENT_TYPES, CONSENT_STATUS_COLOR } from '../../utils/labels'
import { getUserRoles } from '../../utils/auth'
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
  const isAdmin = getUserRoles().includes('ADMIN')
  const [consentPatient, setConsentPatient] = useState<{ id: number; name: string } | null>(null)
  const [consents, setConsents] = useState<any[]>([])
  const [newConsentType, setNewConsentType] = useState('TREATMENT')
  const [newConsentScope, setNewConsentScope] = useState('')
  const [creating, setCreating] = useState(false)

  const fetchData = async () => {
    setLoading(true)
    try { const r = await getPatientPage({ page, size: PAGE_SIZE }); setData(r.records); setTotal(r.total) } finally { setLoading(false) }
  }
  useEffect(() => { fetchData() }, [page])

  const openForm = async (row?: any) => {
    if (row) { setEditId(row.id); const d = await getPatientById(row.id); setForm({ ...emptyForm, ...d }) }
    else { setEditId(null); setForm({ ...emptyForm }) }
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
                {isAdmin && <button className={styles.btnSm} onClick={() => openConsent(row)}>Consent</button>}
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

      {showForm && (
        <div className={styles.modalOverlay} onClick={() => setShowForm(false)}>
          <div className={styles.modal} onClick={e => e.stopPropagation()}>
            <h3>{editId ? 'Edit Patient' : 'Add Patient'}</h3>
            <form onSubmit={handleSubmit} className={styles.formGrid}>
              {['name','mrn','ssn','dateOfBirth','sexAtBirth','genderIdentity','race','ethnicity','preferredLanguage','maritalStatus',
                'phoneMobile','phoneHome','phoneWork','email','addressLine1','addressLine2','city','state','zipCode',
                'emergencyContactName','emergencyContactPhone','emergencyContactRelation',
                'insurancePayer','insuranceMemberId','insuranceGroupNumber','primaryCareProvider','medicalHistory','allergies'].map(f => (
                <div key={f} className={styles.formGroup}>
                  <label>{f}</label>
                  <input value={form[f] ?? ''} onChange={e => setForm({ ...form, [f]: e.target.value })} />
                </div>
              ))}
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

import { useState, useEffect, FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { getPatientPage, getPatientById, createPatient, updatePatient, deletePatient } from '../../api/patient'
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

  const fetchData = async () => {
    setLoading(true)
    try { const r = await getPatientPage({ page, size: 10 }); setData(r.records); setTotal(r.total) } finally { setLoading(false) }
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
        <button disabled={page * 10 >= total} onClick={() => setPage(p => p + 1)}>Next</button>
      </div>

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
                  <input value={form[f] || ''} onChange={e => setForm({ ...form, [f]: e.target.value })} />
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

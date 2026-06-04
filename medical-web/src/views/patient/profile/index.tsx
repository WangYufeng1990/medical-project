import { useState, useEffect, FormEvent } from 'react'
import axios from 'axios'
import styles from '../../shared.module.css'

const FIELDS = [
  { key: 'name', label: 'Name', readonly: true, note: 'Contact staff to update legal name' },
  { key: 'mrn', label: 'MRN', readonly: true },
  { key: 'dateOfBirth', label: 'Date of Birth', readonly: true },
  { key: 'sexAtBirth', label: 'Sex at Birth', readonly: true },
  { key: 'phoneMobile', label: 'Phone (Mobile)' },
  { key: 'phoneHome', label: 'Phone (Home)' },
  { key: 'phoneWork', label: 'Phone (Work)' },
  { key: 'email', label: 'Email' },
  { key: 'addressLine1', label: 'Address Line 1' },
  { key: 'addressLine2', label: 'Address Line 2' },
  { key: 'city', label: 'City' },
  { key: 'state', label: 'State' },
  { key: 'zipCode', label: 'ZIP Code' },
  { key: 'emergencyContactName', label: 'Emergency Contact' },
  { key: 'emergencyContactPhone', label: 'Emergency Phone' },
  { key: 'emergencyContactRelation', label: 'Emergency Relation' },
  { key: 'insurancePayer', label: 'Insurance Payer', readonly: true },
  { key: 'allergies', label: 'Allergies', readonly: true },
]

export default function PatientProfile() {
  const [profile, setProfile] = useState<any>({})
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<any>({})
  const headers = { Authorization: `Bearer ${localStorage.getItem('patientToken')}` }

  useEffect(() => {
    axios.get('/api/v1/patient/me', { headers }).then(r => {
      setProfile(r.data.data)
      setForm(r.data.data)
    })
  }, [])

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    await axios.put('/api/v1/patient/me', form, { headers })
    setProfile({ ...form })
    setEditing(false)
    alert('Profile updated')
  }

  return (<div>
    <h2 style={{ marginBottom: 20 }}>My Profile
      {!editing && <button className={styles.btnPrimary} style={{ marginLeft: 16 }} onClick={() => setEditing(true)}>Edit</button>}
    </h2>
    <div style={{ background: '#fff', padding: 24, borderRadius: 8, maxWidth: 700, marginTop: 16 }}>
      {editing ? (
        <form onSubmit={handleSubmit} className={styles.formGrid}>
          {FIELDS.map(f => (
            <div key={f.key} className={styles.formGroup}>
              <label>{f.label}</label>
              <input value={form[f.key] || ''} disabled={f.readonly}
                onChange={e => setForm({ ...form, [f.key]: e.target.value })} />
              {f.note && <span style={{ fontSize: 11, color: '#e6a23c' }}>{f.note}</span>}
            </div>
          ))}
          <div className={styles.formActions}>
            <button type="button" className={styles.btnSm} onClick={() => { setEditing(false); setForm(profile) }}>Cancel</button>
            <button type="submit" className={styles.btnPrimary}>Save</button>
          </div>
        </form>
      ) : (
        FIELDS.map(f => (
          <div key={f.key} style={{ padding: '6px 0', borderBottom: '1px solid #f5f5f5' }}>
            <span style={{ color: '#909399', fontSize: 12 }}>{f.label}</span><br />
            <span>{profile[f.key] || '-'}</span>
          </div>
        ))
      )}
    </div>
  </div>)
}

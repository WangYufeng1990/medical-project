import { useState, useEffect, FormEvent } from 'react'
import patientRequest from '../../../api/patientRequest'
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
  const [showPwd, setShowPwd] = useState(false)
  const [pwdForm, setPwdForm] = useState({ oldPassword: '', newPassword: '' })

  useEffect(() => {
    patientRequest.get('/patient/me').then(r => {
      setProfile(r.data.data)
      setForm(r.data.data)
    })
  }, [])

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    await patientRequest.put('/patient/me', form)
    setProfile({ ...form })
    setEditing(false)
    alert('Profile updated')
  }

  const handlePasswordChange = async (e: FormEvent) => {
    e.preventDefault()
    try {
      await patientRequest.put('/patient/me/password', pwdForm)
      setShowPwd(false)
      setPwdForm({ oldPassword: '', newPassword: '' })
      alert('Password changed')
    } catch (err: any) {
      alert(err?.response?.data?.message || 'Password change failed')
    }
  }

  return (<div>
    <h2 style={{ marginBottom: 20 }}>My Profile
      {!editing && <button className={styles.btnPrimary} style={{ marginLeft: 16 }} onClick={() => setEditing(true)}>Edit</button>}
      {!editing && <button className={styles.btnSm} style={{ marginLeft: 8 }} onClick={() => setShowPwd(!showPwd)}>Change Password</button>}
    </h2>
    <div style={{ background: '#fff', padding: 24, borderRadius: 8, maxWidth: 700, marginTop: 16 }}>
      {editing ? (
        <form onSubmit={handleSubmit} className={styles.formGrid}>
          {FIELDS.map(f => (
            <div key={f.key} className={styles.formGroup}>
              <label>{f.label}</label>
              <input value={form[f.key] ?? ''} disabled={f.readonly}
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
            <span>{profile[f.key] ?? '-'}</span>
          </div>
        ))
      )}
    </div>

    {showPwd && <div style={{ background: '#fff', padding: 24, borderRadius: 8, maxWidth: 500, marginTop: 16 }}>
      <h3 style={{ marginBottom: 16 }}>Change Password</h3>
      <form onSubmit={handlePasswordChange} className={styles.formGrid}>
        <div className={styles.formGroup}><label>Current Password</label><input type="password" value={pwdForm.oldPassword} onChange={e => setPwdForm({ ...pwdForm, oldPassword: e.target.value })} /></div>
        <div className={styles.formGroup}><label>New Password</label><input type="password" value={pwdForm.newPassword} onChange={e => setPwdForm({ ...pwdForm, newPassword: e.target.value })} /></div>
        <div className={styles.formActions}>
          <button type="button" className={styles.btnSm} onClick={() => setShowPwd(false)}>Cancel</button>
          <button type="submit" className={styles.btnPrimary}>Save</button>
        </div>
      </form>
    </div>}
  </div>)
}

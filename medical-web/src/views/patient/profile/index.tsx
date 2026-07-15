import { useState, useEffect, FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
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
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<any>({})
  const [showPwd, setShowPwd] = useState(false)
  const [pwdForm, setPwdForm] = useState({ oldPassword: '', newPassword: '' })

  const { data: profile } = useQuery({
    queryKey: ['me', 'profile'],
    queryFn: () => patientRequest.get('/patient/me').then(r => r.data.data),
  })

  useEffect(() => {
    if (profile) setForm(profile)
  }, [profile])

  const updateMutation = useMutation({
    mutationFn: (data: any) => patientRequest.put('/patient/me', data),
    onSuccess: () => {
      setEditing(false)
      queryClient.invalidateQueries({ queryKey: ['me', 'profile'] })
      alert('Profile updated')
    },
  })

  const pwdMutation = useMutation({
    mutationFn: (data: any) => patientRequest.put('/patient/me/password', data),
    onSuccess: () => {
      setShowPwd(false)
      setPwdForm({ oldPassword: '', newPassword: '' })
      queryClient.invalidateQueries({ queryKey: ['me', 'profile'] })
      alert('Password changed')
    },
  })

  return (<div>
    <h2 style={{ marginBottom: 20 }}>My Profile
      {!editing && <button className={styles.btnPrimary} style={{ marginLeft: 16 }} onClick={() => setEditing(true)}>Edit</button>}
      {!editing && <button className={styles.btnSm} style={{ marginLeft: 8 }} onClick={() => setShowPwd(!showPwd)}>Change Password</button>}
    </h2>
    <div style={{ background: '#fff', padding: 24, borderRadius: 8, maxWidth: 700, marginTop: 16 }}>
      {editing ? (
        <form onSubmit={e => { e.preventDefault(); updateMutation.mutate(form) }} className={styles.formGrid}>
          {FIELDS.map(f => (
            <div key={f.key} className={styles.formGroup}>
              <label>{f.label}</label>
              <input value={form[f.key] ?? ''} disabled={f.readonly}
                onChange={e => setForm({ ...form, [f.key]: e.target.value })} />
              {f.note && <span style={{ fontSize: 11, color: '#e6a23c' }}>{f.note}</span>}
            </div>
          ))}
          <div className={styles.formActions}>
            <button type="button" className={styles.btnSm} onClick={() => { setEditing(false); setForm(profile ?? {}) }}>Cancel</button>
            <button type="submit" className={styles.btnPrimary}>Save</button>
          </div>
        </form>
      ) : (
        FIELDS.map(f => (
          <div key={f.key} style={{ padding: '6px 0', borderBottom: '1px solid #f5f5f5' }}>
            <span style={{ color: '#909399', fontSize: 12 }}>{f.label}</span><br />
            <span>{profile?.[f.key] ?? '-'}</span>
          </div>
        ))
      )}
    </div>

    {showPwd && <div style={{ background: '#fff', padding: 24, borderRadius: 8, maxWidth: 500, marginTop: 16 }}>
      <h3 style={{ marginBottom: 16 }}>Change Password</h3>
      <form onSubmit={e => { e.preventDefault(); pwdMutation.mutate(pwdForm) }} className={styles.formGrid}>
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

import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getProfile, updateProfile, changePassword } from '../../api/user'
import { StaffProfileVO, StaffProfileForm, ChangePasswordForm } from '../../types/entities'
import styles from '../shared.module.css'

const PROFILE_FIELDS = ['realName','phone','email','gender','npi','licenseState','taxonomyCode','credentials','specialty'] as const

export default function Profile() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<StaffProfileForm>({
    realName: '', phone: '', email: '', gender: '', npi: '', licenseState: '', taxonomyCode: '', credentials: '', specialty: '',
  })
  const [pwdForm, setPwdForm] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' })
  const [showPwd, setShowPwd] = useState(false)
  const [pwdError, setPwdError] = useState('')

  const { data: profile } = useQuery({
    queryKey: ['profile'],
    queryFn: () => getProfile(),
  })

  useEffect(() => {
    if (profile) setForm({
      realName: profile.realName ?? '', phone: profile.phone ?? '', email: profile.email ?? '', gender: profile.gender ?? '',
      npi: profile.npi ?? '', licenseState: profile.licenseState ?? '', taxonomyCode: profile.taxonomyCode ?? '',
      credentials: profile.credentials ?? '', specialty: profile.specialty ?? '',
    })
  }, [profile])

  const updateMutation = useMutation({
    mutationFn: (data: StaffProfileForm) => updateProfile(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile'] })
      alert('Updated')
    },
    onError: (err: Error) => alert(err?.message || 'Update failed'),
  })

  const CREDENTIAL_FIELDS: (keyof StaffProfileForm)[] = ['npi', 'licenseState', 'taxonomyCode', 'credentials', 'specialty']

  const handleUpdate = async () => {
    // Professional credential changes require the current password on the
    // backend — collect it up front instead of failing silently (Review III H5).
    const credentialsChanged = CREDENTIAL_FIELDS.some(f => (form[f] ?? '') !== (profile?.[f as keyof StaffProfileVO] ?? ''))
    let currentPassword: string | undefined
    if (credentialsChanged) {
      currentPassword = prompt('Enter your current password to update professional credentials:') || undefined
      if (!currentPassword) return
    }
    const payload: StaffProfileForm = {
      ...form,
      gender: form.gender === '' ? null : Number(form.gender),
      ...(currentPassword ? { currentPassword } : {}),
    }
    updateMutation.mutate(payload)
  }

  const pwdMutation = useMutation({
    mutationFn: (data: ChangePasswordForm) => changePassword(data),
    onSuccess: () => {
      setShowPwd(false)
      setPwdForm({ oldPassword: '', newPassword: '', confirmPassword: '' })
      setPwdError('')
      alert('Password changed')
    },
    onError: (err: Error) => alert(err?.message || 'Password change failed'),
  })

  const handlePwdSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (pwdForm.newPassword !== pwdForm.confirmPassword) { setPwdError('Passwords do not match'); return }
    if (pwdForm.newPassword.length < 8) { setPwdError('Password must be at least 8 characters'); return }
    setPwdError('')
    pwdMutation.mutate(pwdForm)
  }

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Profile</h2>
      <div style={{ maxWidth: 500, background: '#fff', padding: 24, borderRadius: 8 }}>
        <div className={styles.formGrid}>
          {PROFILE_FIELDS.map(f => (
            <div key={f} className={styles.formGroup}>
              <label>{f}</label>
              <input value={form[f] ?? ''} onChange={e => setForm(prev => ({ ...prev, [f]: e.target.value }) as StaffProfileForm)} />
            </div>
          ))}
        </div>
        <button className={styles.btnPrimary} onClick={handleUpdate} style={{ marginTop: 16 }}>Update Profile</button>
        <button className={styles.btnSm} onClick={() => setShowPwd(true)} style={{ marginLeft: 8, marginTop: 16 }}>Change Password</button>
      </div>

      {showPwd && <div className={styles.modalOverlay} onClick={() => { setShowPwd(false); setPwdError('') }}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>Change Password</h3>
        <form onSubmit={handlePwdSubmit} className={styles.formGrid}>
          <div className={styles.formGroup}><label>Current Password</label><input type="password" value={pwdForm.oldPassword} onChange={e => setPwdForm({ ...pwdForm, oldPassword: e.target.value })} autoFocus /></div>
          <div className={styles.formGroup}><label>New Password</label><input type="password" value={pwdForm.newPassword} onChange={e => setPwdForm({ ...pwdForm, newPassword: e.target.value })} /></div>
          <div className={styles.formGroup}><label>Confirm New Password</label><input type="password" value={pwdForm.confirmPassword} onChange={e => setPwdForm({ ...pwdForm, confirmPassword: e.target.value })} /></div>
          {pwdError && <div style={{ gridColumn: 'span 2', color: '#F56C6C', fontSize: 12 }}>{pwdError}</div>}
          <div className={styles.formActions}>
            <button type="button" className={styles.btnSm} onClick={() => { setShowPwd(false); setPwdError('') }}>Cancel</button>
            <button type="submit" className={styles.btnPrimary} disabled={pwdMutation.isPending || !pwdForm.oldPassword || !pwdForm.newPassword || !pwdForm.confirmPassword}>Save</button>
          </div>
        </form>
      </div></div>}
    </div>
  )
}

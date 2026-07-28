import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getProfile, updateProfile, changePassword } from '../../api/user'
import styles from '../shared.module.css'

export default function Profile() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<any>({})
  const [pwdForm, setPwdForm] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' })
  const [showPwd, setShowPwd] = useState(false)
  const [pwdError, setPwdError] = useState('')

  const { data: profile } = useQuery({
    queryKey: ['profile'],
    queryFn: () => getProfile(),
  })

  useEffect(() => {
    if (profile) setForm(profile)
  }, [profile])

  const updateMutation = useMutation({
    mutationFn: (data: any) => updateProfile(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile'] })
      alert('Updated')
    },
  })

  const pwdMutation = useMutation({
    mutationFn: (data: any) => changePassword(data),
    onSuccess: () => {
      setShowPwd(false)
      setPwdForm({ oldPassword: '', newPassword: '', confirmPassword: '' })
      setPwdError('')
      alert('Password changed')
    },
    onError: (err: any) => alert(err?.message || 'Password change failed'),
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
          {['realName','phone','email','gender','npi','licenseState','taxonomyCode','credentials','specialty'].map(f => (
            <div key={f} className={styles.formGroup}>
              <label>{f}</label>
              <input value={form[f] ?? ''} onChange={e => setForm({ ...form, [f]: e.target.value })} />
            </div>
          ))}
        </div>
        <button className={styles.btnPrimary} onClick={() => updateMutation.mutate(form)} style={{ marginTop: 16 }}>Update Profile</button>
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

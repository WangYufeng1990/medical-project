import { useState, useEffect } from 'react'
import { getProfile, updateProfile, changePassword } from '../../api/user'
import styles from '../shared.module.css'

export default function Profile() {
  const [profile, setProfile] = useState<any>({})
  const [pwdForm, setPwdForm] = useState({ oldPassword: '', newPassword: '' })
  const [showPwd, setShowPwd] = useState(false)

  useEffect(() => { getProfile().then(setProfile) }, [])

  const handleUpdate = async () => {
    await updateProfile(profile)
    alert('Updated')
  }

  const handleChangePwd = async () => {
    await changePassword(pwdForm)
    alert('Password changed')
    setShowPwd(false)
  }

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>Profile</h2>
      <div style={{ maxWidth: 500, background: '#fff', padding: 24, borderRadius: 8 }}>
        <div className={styles.formGrid}>
          {['realName','phone','email','gender','npi','licenseState','taxonomyCode','credentials','specialty'].map(f => (
            <div key={f} className={styles.formGroup}>
              <label>{f}</label>
              <input value={profile[f] || ''} onChange={e => setProfile({ ...profile, [f]: e.target.value })} />
            </div>
          ))}
        </div>
        <button className={styles.btnPrimary} onClick={handleUpdate} style={{ marginTop: 16 }}>Update Profile</button>
        <button className={styles.btnSm} onClick={() => setShowPwd(!showPwd)} style={{ marginLeft: 8, marginTop: 16 }}>Change Password</button>

        {showPwd && (
          <div style={{ marginTop: 16, border: '1px solid #ebeef5', padding: 16, borderRadius: 4 }}>
            <div className={styles.formGroup} style={{ marginBottom: 8 }}><label>Old Password</label>
              <input type="password" value={pwdForm.oldPassword} onChange={e => setPwdForm({ ...pwdForm, oldPassword: e.target.value })} /></div>
            <div className={styles.formGroup} style={{ marginBottom: 8 }}><label>New Password</label>
              <input type="password" value={pwdForm.newPassword} onChange={e => setPwdForm({ ...pwdForm, newPassword: e.target.value })} /></div>
            <button className={styles.btnPrimary} onClick={handleChangePwd}>Change Password</button>
          </div>
        )}
      </div>
    </div>
  )
}

import { useState, useEffect } from 'react'
import axios from 'axios'
import styles from '../../shared.module.css'

export default function PatientProfile() {
  const [profile, setProfile] = useState<any>({})
  const headers = { Authorization: `Bearer ${localStorage.getItem('patientToken')}` }

  useEffect(() => { axios.get('/api/v1/patient/me', { headers }).then(r => setProfile(r.data.data)) }, [])

  return (<div>
    <h2>My Profile</h2>
    <div style={{ background: '#fff', padding: 24, borderRadius: 8, maxWidth: 600, marginTop: 16 }}>
      {['name','mrn','dateOfBirth','sexAtBirth','phoneMobile','email','addressLine1','city','state','zipCode','insurancePayer','allergies'].map(f => (
        <div key={f} style={{ padding: '6px 0', borderBottom: '1px solid #f5f5f5' }}>
          <span style={{ color: '#909399', fontSize: 12 }}>{f}</span><br />
          <span>{profile[f] || '-'}</span>
        </div>
      ))}
    </div>
  </div>)
}

import { Routes, Route, Navigate } from 'react-router-dom'
import { getUserRoles } from './utils/auth'
import { tokenStore } from './utils/auth'
import Login from './views/login'
import StaffLayout from './layout/StaffLayout'
import Dashboard from './views/dashboard'
import Patients from './views/patients'
import Appointments from './views/appointments'
import Prescriptions from './views/prescriptions'
import Billing from './views/billing'
import Profile from './views/profile'
import Chat from './views/chat'
import Users from './views/system/users'
import Roles from './views/system/roles'
import Menus from './views/system/menus'
import EmergencyAudit from './views/system/EmergencyAudit'
import AuditLogs from './views/system/AuditLogs'
import PatientLogin from './views/patient/login'
import PatientForgotPassword from './views/patient/forgotPassword'
import PatientLayout from './views/patient/layout/PatientLayout'
import PatientDashboard from './views/patient/dashboard'
import PatientProfile from './views/patient/profile'
import PatientAppointments from './views/patient/appointments'
import PatientPrescriptions from './views/patient/prescriptions'
import PatientBills from './views/patient/bills'
import PatientChat from './views/patient/chat'
import PatientConsent from './views/patient/consent'
import PatientVitals from './views/patient/vitals'
import PatientProblems from './views/patient/problems'
import PatientImmunizations from './views/patient/immunizations'
import PatientDisclosures from './views/patient/disclosures'
import PatientReferrals from './views/patient/referrals'
import PatientCarePlans from './views/patient/carePlans'
import PatientPriorAuths from './views/patient/priorAuths'
import Referrals from './views/referrals'
import PriorAuths from './views/priorAuths'
import Charges from './views/charges'

import LabResults from './views/lab/LabResults'
import LoincCatalog from './views/lab/LoincCatalog'
import PatientLab from './views/patient/lab'
import QualityMeasures from './views/system/QualityMeasures'
import AdminKeys from './views/system/AdminKeys'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<AuthGuard><StaffLayout /></AuthGuard>}>
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="patients" element={<Patients />} />
        <Route path="appointments" element={<Appointments />} />
        <Route path="prescriptions" element={<Prescriptions />} />
        <Route path="chat" element={<Chat />} />
        <Route path="billing" element={<Billing />} />
        <Route path="charges" element={<Charges />} />
        <Route path="profile" element={<Profile />} />
        <Route path="emergency" element={<AdminGuard><EmergencyAudit /></AdminGuard>} />
        <Route path="audit-logs" element={<AdminGuard><AuditLogs /></AdminGuard>} />
        <Route path="system/users" element={<AdminGuard><Users /></AdminGuard>} />
        <Route path="lab" element={<LabResults />} />
        <Route path="loinc" element={<LoincCatalog />} />
        <Route path="system/roles" element={<AdminGuard><Roles /></AdminGuard>} />
        <Route path="system/menus" element={<AdminGuard><Menus /></AdminGuard>} />
        <Route path="system/quality" element={<AdminGuard><QualityMeasures /></AdminGuard>} />
        <Route path="system/keys" element={<AdminGuard><AdminKeys /></AdminGuard>} />
        <Route path="referrals" element={<Referrals />} />
        <Route path="prior-auths" element={<PriorAuths />} />
      </Route>
      <Route path="/patient/login" element={<PatientLogin />} />
      <Route path="/patient/forgot-password" element={<PatientForgotPassword />} />
      <Route path="/patient" element={<PatientAuthGuard><PatientLayout /></PatientAuthGuard>}>
        <Route index element={<Navigate to="/patient/dashboard" replace />} />
        <Route path="dashboard" element={<PatientDashboard />} />
        <Route path="profile" element={<PatientProfile />} />
        <Route path="appointments" element={<PatientAppointments />} />
        <Route path="prescriptions" element={<PatientPrescriptions />} />
        <Route path="bills" element={<PatientBills />} />
        <Route path="chat" element={<PatientChat />} />
        <Route path="lab" element={<PatientLab />} />
        <Route path="consent" element={<PatientConsent />} />
        <Route path="vitals" element={<PatientVitals />} />
        <Route path="problems" element={<PatientProblems />} />
        <Route path="immunizations" element={<PatientImmunizations />} />
        <Route path="disclosures" element={<PatientDisclosures />} />
        <Route path="referrals" element={<PatientReferrals />} />
        <Route path="care-plans" element={<PatientCarePlans />} />
        <Route path="prior-auths" element={<PatientPriorAuths />} />
      </Route>
    </Routes>
  )
}

function AuthGuard({ children }: { children: React.ReactNode }) {
  const token = tokenStore.get('token')
  if (!token) return <Navigate to="/login" replace />
  return <>{children}</>
}

function AdminGuard({ children }: { children: React.ReactNode }) {
  const token = tokenStore.get('token')
  if (!token) return <Navigate to="/login" replace />
  const roles = getUserRoles()
  if (!roles.includes('ADMIN')) {
    return <div style={{ padding: 48, textAlign: 'center' }}>
      <h2 style={{ color: '#f56c6c' }}>Access Denied</h2>
      <p style={{ color: '#909399', marginTop: 8 }}>Admin role required to access this page.</p>
    </div>
  }
  return <>{children}</>
}

function PatientAuthGuard({ children }: { children: React.ReactNode }) {
  const token = tokenStore.get('patientToken')
  if (!token) return <Navigate to="/patient/login" replace />
  return <>{children}</>
}

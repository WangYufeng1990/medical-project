import { Routes, Route, Navigate } from 'react-router-dom'
import Login from './views/login'
import StaffLayout from './layout/StaffLayout'
import Dashboard from './views/dashboard'
import Patients from './views/patients'
import Appointments from './views/appointments'
import Prescriptions from './views/prescriptions'
import Billing from './views/billing'
import Profile from './views/profile'
import Users from './views/system/users'
import Roles from './views/system/roles'
import Menus from './views/system/menus'
import PatientLogin from './views/patient/login'
import PatientLayout from './views/patient/layout/PatientLayout'
import PatientDashboard from './views/patient/dashboard'
import PatientProfile from './views/patient/profile'
import PatientAppointments from './views/patient/appointments'
import PatientPrescriptions from './views/patient/prescriptions'
import PatientBills from './views/patient/bills'

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
        <Route path="billing" element={<Billing />} />
        <Route path="profile" element={<Profile />} />
        <Route path="system/users" element={<Users />} />
        <Route path="system/roles" element={<Roles />} />
        <Route path="system/menus" element={<Menus />} />
      </Route>
      <Route path="/patient/login" element={<PatientLogin />} />
      <Route path="/patient" element={<PatientAuthGuard><PatientLayout /></PatientAuthGuard>}>
        <Route index element={<Navigate to="/patient/dashboard" replace />} />
        <Route path="dashboard" element={<PatientDashboard />} />
        <Route path="profile" element={<PatientProfile />} />
        <Route path="appointments" element={<PatientAppointments />} />
        <Route path="prescriptions" element={<PatientPrescriptions />} />
        <Route path="bills" element={<PatientBills />} />
      </Route>
    </Routes>
  )
}

function AuthGuard({ children }: { children: React.ReactNode }) {
  const token = localStorage.getItem('token')
  if (!token) return <Navigate to="/login" replace />
  return <>{children}</>
}

function PatientAuthGuard({ children }: { children: React.ReactNode }) {
  const token = localStorage.getItem('patientToken')
  if (!token) return <Navigate to="/patient/login" replace />
  return <>{children}</>
}

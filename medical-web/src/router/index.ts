import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/index.vue'),
    meta: { noAuth: true }
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/dashboard/index.vue') },
      { path: 'system/users', name: 'Users', component: () => import('../views/system/users/index.vue') },
      { path: 'system/roles', name: 'Roles', component: () => import('../views/system/roles/index.vue') },
      { path: 'system/menus', name: 'Menus', component: () => import('../views/system/menus/index.vue') },
      { path: 'patients', name: 'Patients', component: () => import('../views/patients/index.vue') },
      { path: 'appointments', name: 'Appointments', component: () => import('../views/appointments/index.vue') },
      { path: 'prescriptions', name: 'Prescriptions', component: () => import('../views/prescriptions/index.vue') },
      { path: 'billing', name: 'Billing', component: () => import('../views/billing/index.vue') },
      { path: 'profile', name: 'Profile', component: () => import('../views/profile/index.vue') }
    ]
  },
  {
    path: '/patient/login',
    name: 'PatientLogin',
    component: () => import('../views/patient/login.vue'),
    meta: { noAuth: true }
  },
  {
    path: '/patient',
    component: () => import('../views/patient/layout/PatientLayout.vue'),
    redirect: '/patient/dashboard',
    meta: { patientAuth: true },
    children: [
      { path: 'dashboard', name: 'PatientDashboard', component: () => import('../views/patient/dashboard/index.vue') },
      { path: 'profile', name: 'PatientProfile', component: () => import('../views/patient/profile/index.vue') },
      { path: 'appointments', name: 'PatientAppointments', component: () => import('../views/patient/appointments/index.vue') },
      { path: 'prescriptions', name: 'PatientPrescriptions', component: () => import('../views/patient/prescriptions/index.vue') },
      { path: 'bills', name: 'PatientBills', component: () => import('../views/patient/bills/index.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.meta.noAuth) {
    next()
  } else if (to.meta.patientAuth) {
    const patientToken = localStorage.getItem('patientToken')
    if (!patientToken) {
      next('/patient/login')
    } else {
      next()
    }
  } else {
    const token = localStorage.getItem('token')
    if (!token) {
      next('/login')
    } else {
      next()
    }
  }
})

export default router

import { defineStore } from 'pinia'
import { ref } from 'vue'
import { patientLogin } from '../api/patientAuth'

export const usePatientStore = defineStore('patient', () => {
  const token = ref(localStorage.getItem('patientToken') || '')
  const patientInfo = ref(JSON.parse(localStorage.getItem('patientInfo') || '{}'))

  async function login(credentials) {
    const data = await patientLogin(credentials)
    token.value = data.token
    patientInfo.value = {
      patientId: data.patientId,
      name: data.name,
      username: data.username
    }
    localStorage.setItem('patientToken', data.token)
    localStorage.setItem('patientInfo', JSON.stringify(patientInfo.value))
  }

  function logout() {
    token.value = ''
    patientInfo.value = {}
    localStorage.removeItem('patientToken')
    localStorage.removeItem('patientInfo')
  }

  return { token, patientInfo, login, logout }
})

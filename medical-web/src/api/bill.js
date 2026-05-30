import request from './request'

export function getBillPage(params) {
  return request.get('/bills', { params })
}

export function getBillById(id) {
  return request.get(`/bills/${id}`)
}

export function createBill(data) {
  return request.post('/bills', data)
}

export function payBill(id, data) {
  return request.put(`/bills/${id}/pay`, data)
}

export function deleteBill(id) {
  return request.delete(`/bills/${id}`)
}

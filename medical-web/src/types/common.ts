// Backend envelope shapes — mirrors Result.java / PageResult.java.
export interface Result<T> {
  code: number
  message: string
  data?: T
}

export interface PageResult<T> {
  total: number
  size: number
  current: number
  records: T[]
}

export interface PageQuery {
  page?: number
  size?: number
}

export interface IdName {
  id: number
  name: string
}

export interface JwtPayload {
  uid?: number
  jti?: string
  exp?: number
  iat?: number
  scp?: string[]
  groups?: string[]
  roles?: string[]
}

import { isAxiosError } from 'axios'
import { api } from '@/modules/api'
import {
  TankobonApiError,
  type ErrorResponse,
  type EntityResponse
} from '@/types/tankobon-response'
import type { ClaimStatus, ClaimAdmin } from '@/types/tankobon-claim'
import type { UserEntity } from '@/types/tankobon-user'

export type ClaimResponse = EntityResponse<UserEntity>

export async function getClaimStatus(): Promise<ClaimStatus> {
  try {
    const { data } = await api.get<ClaimStatus>('claim')

    return data
  } catch (e) {
    if (isAxiosError<ErrorResponse>(e) && e.response?.data) {
      throw new TankobonApiError(e.response.data)
    }

    throw e
  }
}

export async function claimAdmin(admin: ClaimAdmin): Promise<UserEntity> {
  try {
    const { data } = await api.post<ClaimResponse>('claim', admin)

    return data
  } catch (e) {
    if (isAxiosError<ErrorResponse>(e) && e.response?.data) {
      throw new TankobonApiError(e.response.data)
    }

    throw e
  }
}
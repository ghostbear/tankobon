import { useQuery, type UseQueryOptions } from '@tanstack/vue-query'
import type { TankobonApiError } from '@/types/tankobon-response'
import { MaybeRef } from '@vueuse/core'
import { Includes } from '@/types/tankobon-entity'
import { getOneSeries } from '@/services/tankobon-series'
import { SeriesEntity } from '@/types/tankobon-series'

interface UseSeriesQueryOptions<S = SeriesEntity> extends UseQueryOptions<SeriesEntity, ErrorResponse, S> {
  seriesId: MaybeRef<string>,
  includes?: MaybeRef<Includes>,
}

type ErrorResponse = TankobonApiError | Error

export default function useSeriesQuery<S = SeriesEntity>(
  options: UseSeriesQueryOptions<S>
) {
  return useQuery<SeriesEntity, ErrorResponse, S>({
    queryKey: ['series', { id: options.seriesId }],
    queryFn: async () => {
      return await getOneSeries({
        seriesId: unref(options.seriesId),
        includes: unref(options.includes),
      })
    },
    ...options,
  })
}
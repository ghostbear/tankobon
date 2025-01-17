<script lang="ts" setup>
import { ColumnSort, createColumnHelper, PaginationState, SortingState } from '@tanstack/vue-table'
import { EllipsisHorizontalIcon } from '@heroicons/vue/20/solid'
import BasicCheckbox from '@/components/form/BasicCheckbox.vue'
import Button from '@/components/form/Button.vue'
import { PersonEntity, PersonSort } from '@/types/tankobon-person'
import { Sort } from '@/types/tankobon-api'

export interface PeopleTableProps {
  libraryId: string,
  search?: string,
}

const props = withDefaults(defineProps<PeopleTableProps>(), {
  search: undefined
})
const { libraryId, search } = toRefs(props)
const notificator = useToaster()

const defaultSorting: ColumnSort = { id: 'name', desc: false }
const pagination = ref<PaginationState>({ pageIndex: 0, pageSize: 20 })
const sorting = ref<SortingState>([defaultSorting])
const rowSelection = ref<Record<string, boolean>>({})

const { data: people, isLoading } = useLibraryPeopleQuery({
  libraryId,
  search,
  page: computed(() => pagination.value.pageIndex),
  size: computed(() => pagination.value.pageSize),
  sort: computed<Sort<PersonSort>[]>(() => {
    return sorting.value.map((sort) => ({
      property: sort.id as PersonSort,
      direction: sort.desc ? 'desc' : 'asc',
    }))
  }),
  enabled: computed(() => libraryId.value !== undefined),
  keepPreviousData: true,
  onError: async (error) => {
    await notificator.failure({
      title: t('people.fetch-failure'),
      body: error.message,
    })
  }
})
const { t, locale } = useI18n()
const columnHelper = createColumnHelper<PersonEntity>()

const columns = [
  columnHelper.display({
    id: 'select',
    header: ({ table }) => h(BasicCheckbox, {
      disabled: table.getRowModel().rows.length === 0,
      checked: table.getIsAllRowsSelected(),
      indeterminate: table.getIsSomeRowsSelected(),
      onChange: table.getToggleAllRowsSelectedHandler(),
    }),
    cell: ({ row }) => h('div', { class: 'flex items-center w-full h-full' }, [
      h(BasicCheckbox, {
        checked: row.getIsSelected(),
        disabled: !row.getCanSelect(),
        indeterminate: row.getIsSomeSelected(),
        onChange: row.getToggleSelectedHandler(),
      })
    ]),
    meta: {
      headerClass: 'w-12',
      cellClass: 'align-middle',
    }
  }),
  columnHelper.accessor('attributes.name', {
    id: 'name',
    header: () => t('common-fields.name'),
    cell: (info) => info.getValue(),
  }),
  columnHelper.accessor('attributes.description', {
    id: 'description',
    enableSorting: false,
    header: () => t('common-fields.description'),
    cell: (info) => h('div', { class: 'line-clamp-2', innerText: info.getValue() }),
  }),
  columnHelper.display({
    id: 'actions',
    header: () => null,
    cell: ({ row }) => h(
      Button,
      {
        kind: 'ghost-alt',
        isRouterLink: true,
        class: 'w-10 h-10',
        to: { name: 'people-id', params: { id: row.original.id } },
      },
      {
        default: () => [
          h('span', { class: 'sr-only', text: () => t('common-actions.view-details') }),
          h(EllipsisHorizontalIcon, { class: 'w-5 h-5' }),
        ]
      }
    ),
    meta: {
      headerClass: 'w-12',
    }
  }),
]
</script>

<template>
  <Table
    :data="people?.data"
    :columns="columns"
    :page-count="people?.pagination?.totalPages"
    :items-count="people?.pagination?.totalElements"
    :loading="isLoading"
    v-model:pagination="pagination"
    v-model:row-selection="rowSelection"
    v-model:sorting="sorting"
  >
    <template #empty>
      <slot name="empty" />
    </template>
  </Table>
</template>

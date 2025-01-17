<script lang="ts" setup>
import type { PersonCreation } from '@/types/tankobon-person'
import { MagnifyingGlassIcon, PlusIcon } from '@heroicons/vue/20/solid'
import { AcademicCapIcon } from '@heroicons/vue/24/outline'
import { MagnifyingGlassIcon as MagnifyingGlassIconOutline } from '@heroicons/vue/24/outline'

const { t } = useI18n()
const router = useRouter()
const notificator = useToaster()

const showCreateDialog = ref(false)
const search = ref('')
const searchTerm = refDebounced(search, 500)

const { mutate } = useCreatePersonMutation()

const libraryStore = useLibraryStore()
const library = computed(() => libraryStore.library!)

function handleCreatePerson(person: PersonCreation) {
  mutate(person, {
    onSuccess: async ({ id }) => {
      notificator.success({ title: t('people.created-with-success') })
      await router.push({ name: 'people-id', params: { id } })
    },
    onError: async (error) => {
      await notificator.failure({
        title: t('people.created-with-failure'),
        body: error.message,
      })
    }
  })
}
</script>

<route lang="yaml">
  meta:
    layout: dashboard
</route>

<template>
  <div>
    <Header
      class="mb-3 md:mb-0"
      :title="$t('entities.people')"
    >
      <template #actions>
        <Button
          kind="primary"
          @click="showCreateDialog = true"
        >
          <PlusIcon class="w-5 h-5" />
          <span>{{ $t('people.new') }}</span>
        </Button>
      </template>
    </Header>

    <div class="max-w-7xl mx-auto p-4 sm:p-6">
      <TableControls v-if="library">
        <div>
          <label class="sr-only" for="search-person">
            {{ $t('people.search') }}
          </label>
          <BasicTextInput
            id="search-person"
            class="w-48"
            v-model="search"
            size="small"
            type="search"
            :placeholder="$t('common-placeholders.search')"
          >
            <template #left-icon>
              <MagnifyingGlassIcon class="w-4 h-4" />
            </template>
          </BasicTextInput>
        </div>
      </TableControls>

      <PeopleTable
        v-if="library"
        class="mt-4 sm:mt-6"
        :library-id="library.id"
        :search="searchTerm"
      >
        <template #empty>
          <EmptyState
            :icon="searchTerm.length > 0 ? MagnifyingGlassIconOutline : AcademicCapIcon"
            :title="$t('people.empty-header')"
            :description="
              searchTerm.length > 0
                ? $t('people.empty-search-description', [searchTerm])
                : $t('people.empty-description')
            "
          >
            <template #actions v-if="searchTerm.length === 0">
              <Button
                kind="primary"
                @click="showCreateDialog = true"
              >
                <PlusIcon class="w-5 h-5" />
                <span>{{ $t('people.new') }}</span>
              </Button>
            </template>
          </EmptyState>
        </template>
      </PeopleTable>
    </div>

    <PersonCreateDialog
      v-if="library"
      :library-id="library.id"
      :is-open="showCreateDialog"
      @submit="handleCreatePerson"
      @close="showCreateDialog = false"
    />
  </div>
</template>
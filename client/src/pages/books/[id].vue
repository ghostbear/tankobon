<script lang="ts" setup>
import { PencilIcon, TrashIcon } from '@heroicons/vue/24/solid'
import { PublisherUpdate } from '@/types/tankobon-publisher'
import { getRelationship } from '@/utils/api';

const { t } = useI18n()
const router = useRouter()
const bookId = useRouteParams<string | undefined>('id', undefined)
const notificator = useToaster()

// const { mutate: deletePublisher, isLoading: isDeleting, isSuccess: isDeleted } = useDeletePublisherMutation()
// const { mutate: editPublisher, isLoading: isEditing } = useUpdatePublisherMutation()

const { data: book, isLoading } = useBookQuery({
  bookId: bookId as Ref<string>,
  includes: [
    'publisher',
    'collection',
    'contributor',
    'series',
    'store',
    'tag',
    'library',
    'cover_art',
  ],
  enabled: computed(() => !!bookId.value), //&& !isDeleting.value && !isDeleted.value),
  onError: async (error) => {
    await notificator.failure({
      title: t('books.fetch-one-failure'),
      body: error.message,
    })
  }
})

const library = computed(() => getRelationship(book.value, 'LIBRARY'))

const showBookInfo = computed(() => {
  return !isLoading.value && !!book.value
})

// function handleDelete() {
//   deletePublisher(publisherId.value!, {
//     onSuccess: async () => {
//       notificator.success({ title: t('publishers.deleted-with-success') })
//       await router.replace({ name: 'publishers' })
//     },
//     onError: async (error) => {
//       await notificator.failure({
//         title: t('publishers.deleted-with-failure'),
//         body: error.message,
//       })
//     }
//   })
// }

// const showEditDialog = ref(false)

// function handleEditPublisher(publisher: PublisherUpdate) {
//   editPublisher(publisher, {
//     onSuccess: async () => {
//       await notificator.success({ title: t('publishers.edited-with-success') })
//     },
//     onError: async (error) => {
//       await notificator.failure({
//         title: t('publishers.edited-with-failure'),
//         body: error.message,
//       })
//     }
//   })
// }
</script>

<template>
  <div
    :class="[
      'bg-white dark:bg-gray-950 motion-safe:transition-colors',
      'duration-300 ease-in-out -mt-16 relative'
    ]"
  >
    <div class="absolute inset-x-0 top-0">
      <BookBanner :loading="!showBookInfo" :book="book" />
    </div>
  
    <div class="max-w-7xl mx-auto px-4 sm:px-6 z-10 pt-20 pb-6 relative">
      <div class="book-grid">
        <BookCover
          class="book-cover"
          :loading="!showBookInfo"
          :book="book"
        />

        <BookTitle
          class="book-title"
          :loading="!showBookInfo"
          :book="book"
        />

        <BookButtons
          class="book-buttons pt-1.5"
          :loading="!showBookInfo"
          :book="book"
          :editing="false"
        />
      </div>
    </div>
  </div>
</template>

<route lang="yaml">
  meta:
    layout: dashboard
    transparentNavbar: true
</route>

<style lang="postcss">
.book-grid {
  display: grid;
  gap: 1rem;
  grid-template-areas:
    'art title'
    'buttons buttons'
    'synopsis synopsis'
    'attributes attributes'
    'notes notes'
    'tabs tabs';
  grid-template-columns: 6rem 1fr;

  @media (min-width: theme('screens.sm')) {
    gap: 1.5rem;
    grid-template-areas:
      'art title'
      'art buttons'
      'art padding'
      'attributes synopsis'
      'attributes tags'
      'attributes notes';
    grid-template-columns: 14rem 1fr;
  }

  @media (min-width: theme('screens.2xl')) {
    gap: 1.5rem;
    grid-template-areas:
      'art title title'
      'art buttons buttons'
      'art padding padding'
      'attributes synopsis right'
      'attributes tags right'
      'attributes notes right';
    grid-template-columns: 14rem 1fr 22rem;
  }

  .book-cover {
    grid-area: art / art / art / art;
  }

  .book-buttons {
    grid-area: buttons / buttons / buttons / buttons;
  }

  .book-title {
    grid-area: title / title / title / title;
  }

  .book-synopsis {
    grid-area: synopsis / synopsis / synopsis / synopsis;
  }

  .book-attributes {
    grid-area: attributes / attributes / attributes / attributes;
  }

  .book-right {
    grid-area: right / right / right / right;
  }
}
</style>
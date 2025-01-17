package io.github.alessandrojean.tankobon.infrastructure.jooq

import io.github.alessandrojean.tankobon.domain.model.Book
import io.github.alessandrojean.tankobon.domain.model.BookContributor
import io.github.alessandrojean.tankobon.domain.model.BookLibraryChangedException
import io.github.alessandrojean.tankobon.domain.model.BookSearch
import io.github.alessandrojean.tankobon.domain.model.ContributorRole
import io.github.alessandrojean.tankobon.domain.model.Dimensions
import io.github.alessandrojean.tankobon.domain.model.LibraryItem
import io.github.alessandrojean.tankobon.domain.model.Person
import io.github.alessandrojean.tankobon.domain.model.Publisher
import io.github.alessandrojean.tankobon.domain.model.RelationIdDoesNotExistException
import io.github.alessandrojean.tankobon.domain.model.RelationIsNotFromSameLibraryException
import io.github.alessandrojean.tankobon.domain.model.Tag
import io.github.alessandrojean.tankobon.domain.model.TankobonUser
import io.github.alessandrojean.tankobon.domain.model.UserDoesNotHaveAccessException
import io.github.alessandrojean.tankobon.infrastructure.datasource.SqliteUdfDataSource
import io.github.alessandrojean.tankobon.infrastructure.image.BookCoverLifecycle
import io.github.alessandrojean.tankobon.infrastructure.importer.removeDashes
import io.github.alessandrojean.tankobon.infrastructure.importer.toIsbn10
import io.github.alessandrojean.tankobon.infrastructure.search.LuceneEntity
import io.github.alessandrojean.tankobon.infrastructure.search.LuceneHelper
import io.github.alessandrojean.tankobon.interfaces.api.persistence.BookDtoRepository
import io.github.alessandrojean.tankobon.interfaces.api.rest.dto.BookCreationDto
import io.github.alessandrojean.tankobon.interfaces.api.rest.dto.BookEntityDto
import io.github.alessandrojean.tankobon.interfaces.api.rest.dto.BookUpdateDto
import io.github.alessandrojean.tankobon.interfaces.api.rest.dto.RelationDto
import io.github.alessandrojean.tankobon.interfaces.api.rest.dto.RelationshipType
import io.github.alessandrojean.tankobon.interfaces.api.rest.dto.toAttributesDto
import io.github.alessandrojean.tankobon.jooq.tables.records.BookRecord
import org.javamoney.moneta.FastMoney
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import io.github.alessandrojean.tankobon.jooq.Tables.BOOK as TableBook
import io.github.alessandrojean.tankobon.jooq.Tables.BOOK_CONTRIBUTOR as TableBookContributor
import io.github.alessandrojean.tankobon.jooq.Tables.BOOK_PUBLISHER as TableBookPublisher
import io.github.alessandrojean.tankobon.jooq.Tables.BOOK_TAG as TableBookTag
import io.github.alessandrojean.tankobon.jooq.Tables.COLLECTION as TableCollection

@Component
class BookDtoDao(
  private val dsl: DSLContext,
  private val userDao: TankobonUserDao,
  private val libraryDao: LibraryDao,
  private val bookDao: BookDao,
  private val collectionDao: CollectionDao,
  private val storeDao: StoreDao,
  private val seriesDao: SeriesDao,
  private val tagDao: TagDao,
  private val contributorRoleDao: ContributorRoleDao,
  private val personDao: PersonDao,
  private val publisherDao: PublisherDao,
  private val bookContributorDao: BookContributorDao,
  private val bookCoverLifecycle: BookCoverLifecycle,
  private val luceneHelper: LuceneHelper,
  private val transactionTemplate: TransactionTemplate,
  @Value("#{@tankobonProperties.database.batchChunkSize}") private val batchSize: Int,
) : BookDtoRepository {

  private val sorts = mapOf(
    "title" to TableBook.TITLE.collate(SqliteUdfDataSource.collationUnicode3),
    "createdAt" to TableBook.CREATED_AT,
    "modifiedAt" to TableBook.MODIFIED_AT,
    "boughtAt" to TableBook.BOUGHT_AT,
    "billedAt" to TableBook.BILLED_AT,
    "arrivedAt" to TableBook.ARRIVED_AT,
    "number" to TableBook.NUMBER,
    "pageCount" to TableBook.PAGE_COUNT,
  )

  override fun findByIdOrNull(bookId: String): BookEntityDto? = bookDao.findByIdOrNull(bookId)?.toDto()

  override fun findAll(search: BookSearch, pageable: Pageable): Page<BookEntityDto> {
    val bookIds = luceneHelper.searchEntitiesIds(search.searchTerm, LuceneEntity.Book)

    val searchCondition = when {
      bookIds == null -> DSL.noCondition()
      bookIds.isEmpty() -> DSL.falseCondition()
      else -> TableBook.ID.`in`(dsl.selectTempStrings())
    }

    val conditions = search.toCondition()
      .and(searchCondition)

    val (bookIdsPaged, pagingBySearch) = when {
      bookIds.isNullOrEmpty() -> emptyList<String>() to false
      pageable.isPaged -> {
        bookIds.drop(pageable.pageSize * pageable.pageNumber).take(pageable.pageSize) to true
      }
      else -> bookIds to false
    }

    val orderBy = pageable.sort.mapNotNull {
      if (it.property == "relevance" && !bookIds.isNullOrEmpty()) {
        TableBook.ID.sortByValues(bookIdsPaged, it.isAscending)
      } else {
        it.toSortField(sorts)
      }
    }

    val (count, books) = transactionTemplate.execute {
      if (!bookIds.isNullOrEmpty()) {
        dsl.insertTempStrings(batchSize, bookIds)
      }

      val count = dsl.fetchCount(
        dsl.select(TableBook.ID)
          .from(TableBook)
          .leftJoin(TableCollection)
          .on(TableCollection.ID.eq(TableBook.COLLECTION_ID))
          .where(conditions)
          .groupBy(TableBook.ID)
      )

      if (pagingBySearch) {
        dsl.insertTempStrings(batchSize, bookIdsPaged)
      }

      val books = dsl
        .select(
          *TableBook.fields(),
          *TableCollection.fields(),
        )
        .from(TableBook)
        .leftJoin(TableCollection)
        .on(TableCollection.ID.eq(TableBook.COLLECTION_ID))
        .where(conditions)
        .orderBy(orderBy)
        .apply {
          if (!pagingBySearch && pageable.isPaged) {
            limit(pageable.pageSize)
              .offset(pageable.offset)
          }
        }
        .fetchInto(TableBook)
        .map { it.toDomain() }

      count to books.toDto()
    }!!

    val pageSort = if (orderBy.isNotEmpty()) pageable.sort else Sort.unsorted()

    return PageImpl(
      books,
      if (pageable.isPaged) {
        PageRequest.of(pageable.pageNumber, pageable.pageSize, pageSort)
      } else {
        PageRequest.of(0, maxOf(count, 20), pageSort)
      },
      count.toLong(),
    )
  }

  override fun findAllByIsbnInLibraries(isbn: String, librariesIds: Collection<String>): Collection<BookEntityDto> {
    return dsl.select(*TableBook.fields())
      .from(TableBook)
      .leftJoin(TableCollection)
      .on(TableCollection.ID.eq(TableBook.COLLECTION_ID))
      .where(TableCollection.LIBRARY_ID.`in`(librariesIds))
      .and(
        TableBook.CODE.eq(isbn.removeDashes())
          .or(TableBook.CODE.eq(isbn.toIsbn10()))
      )
      .fetchInto(TableBook)
      .map { it.toDomain() }
      .toDto()
  }

  @Transactional
  @Throws(
    RelationIdDoesNotExistException::class,
    RelationIsNotFromSameLibraryException::class,
    UserDoesNotHaveAccessException::class,
  )
  override fun insert(book: BookCreationDto, user: TankobonUser): String {
    val relations = findRelations(book)
    val contributors = findContributors(book)
    val librariesIds = (relations + contributors.flatMap { it.toList() })
      .map { it.libraryId }
      .distinct()
    val library = libraryDao.findById(librariesIds.first())

    if (librariesIds.size > 1) {
      throw RelationIsNotFromSameLibraryException("All relations must be from the same library")
    }

    if (!user.canAccessLibrary(library)) {
      throw UserDoesNotHaveAccessException("The user does not have access to the library")
    }

    val bookDomain = book.toDomain()

    bookDao.insert(bookDomain)
    insertBookPublishers(bookDomain.id, relations.filterIsInstance<Publisher>())
    insertBookTags(bookDomain.id, relations.filterIsInstance<Tag>())
    insertBookContributors(bookDomain.id, contributors)

    return bookDomain.id
  }

  @Transactional
  @Throws(
    RelationIdDoesNotExistException::class,
    RelationIsNotFromSameLibraryException::class,
    UserDoesNotHaveAccessException::class,
  )
  override fun update(bookId: String, book: BookUpdateDto, user: TankobonUser) {
    val existingLibraryId = bookDao.getLibraryIdOrNull(bookId)
    val relations = findRelations(book)
    val contributors = findContributors(book)
    val librariesIds = (relations + contributors.flatMap { it.toList() })
      .map { it.libraryId }
      .distinct()
    val library = libraryDao.findById(librariesIds.first())

    if (librariesIds.size > 1) {
      throw RelationIsNotFromSameLibraryException("All relations must be from the same library")
    }

    if (library.id != existingLibraryId) {
      throw BookLibraryChangedException("Can't change the library of an existing book")
    }

    if (!user.canAccessLibrary(library)) {
      throw UserDoesNotHaveAccessException("The user does not have access to the library")
    }

    val bookDomain = book.toDomain()

    bookDao.update(bookDomain)
    insertBookPublishers(bookId, relations.filterIsInstance<Publisher>())
    insertBookTags(bookId, relations.filterIsInstance<Tag>())
    insertBookContributors(bookId, contributors)
  }

  private fun insertBookPublishers(bookId: String, publishers: List<Publisher>) {
    dsl.deleteFrom(TableBookPublisher)
      .where(TableBookPublisher.BOOK_ID.eq(bookId))

    dsl.insertInto(TableBookPublisher)
      .columns(TableBookPublisher.BOOK_ID, TableBookPublisher.PUBLISHER_ID)
      .apply { publishers.forEach { publisher -> values(bookId, publisher.id) } }
      .execute()
  }

  private fun insertBookTags(bookId: String, tags: List<Tag>) {
    dsl.deleteFrom(TableBookTag)
      .where(TableBookTag.BOOK_ID.eq(bookId))

    dsl.insertInto(TableBookTag)
      .columns(TableBookTag.BOOK_ID, TableBookTag.TAG_ID)
      .apply { tags.forEach { tag -> values(bookId, tag.id) } }
      .execute()
  }

  private fun insertBookContributors(bookId: String, contributors: List<Pair<Person, ContributorRole>>) {
    bookContributorDao.deleteAllByBookId(bookId)
    bookContributorDao.insert(
      contributors.map {
        BookContributor(
          bookId = bookId,
          personId = it.first.id,
          roleId = it.second.id,
        )
      }
    )
  }

  @Throws(RelationIdDoesNotExistException::class)
  private fun findRelations(book: BookCreationDto): List<LibraryItem> {
    val collection = collectionDao.findByIdOrNull(book.collection)
      ?: throw RelationIdDoesNotExistException("Collection not found")
    val series = book.series?.let {
      seriesDao.findByIdOrNull(it) ?: throw RelationIdDoesNotExistException("Series not found")
    }
    val store = book.store?.let {
      storeDao.findByIdOrNull(it) ?: throw RelationIdDoesNotExistException("Store not found")
    }
    val publishers = publisherDao.findAllByIds(book.publishers)
    val tags = book.tags?.let { tagDao.findAllByIds(it) }

    if (publishers.size != book.publishers.size) {
      throw RelationIdDoesNotExistException("Publisher not found")
    }

    if (tags.orEmpty().size != book.tags.orEmpty().size) {
      throw RelationIdDoesNotExistException("Tag not found")
    }

    val relations = listOf(
      listOfNotNull(collection),
      listOfNotNull(series),
      listOfNotNull(store),
      publishers,
      tags.orEmpty(),
    )

    return relations.flatten()
  }

  @Throws(
    RelationIdDoesNotExistException::class,
    RelationIsNotFromSameLibraryException::class,
    UserDoesNotHaveAccessException::class,
  )
  fun findContributors(book: BookCreationDto): List<Pair<Person, ContributorRole>> {
    val rolesIds = book.contributors.map { it.role }.distinct()
    val peopleIds = book.contributors.map { it.person }.distinct()

    val roles = contributorRoleDao.findAllByIds(rolesIds)
    val people = personDao.findAllByIds(peopleIds)

    if (roles.size != rolesIds.size || people.size != peopleIds.size) {
      throw RelationIdDoesNotExistException("Role or person not found")
    }

    val rolesMap = roles.associateBy { it.id }
    val peopleMap = people.associateBy { it.id }

    return book.contributors.map { peopleMap[it.person]!! to rolesMap[it.role]!! }
  }

  private fun BookCreationDto.toDomain() = Book(
    code = code,
    title = title,
    paidPrice = paidPrice,
    labelPrice = labelPrice,
    dimensions = Dimensions(dimensions.widthCm, dimensions.heightCm),
    collectionId = collection,
    storeId = store,
    seriesId = series,
    barcode = barcode,
    isInLibrary = isInLibrary,
    number = number,
    pageCount = pageCount,
    synopsis = synopsis,
    notes = notes,
    boughtAt = boughtAt,
    billedAt = billedAt,
    arrivedAt = arrivedAt,
  )

  private fun Book.toDto(): BookEntityDto {
    val publishers = dsl.select(TableBookPublisher.PUBLISHER_ID)
      .from(TableBookPublisher)
      .where(TableBookPublisher.BOOK_ID.eq(id))
      .fetch { RelationDto(it.get(TableBookPublisher.PUBLISHER_ID), RelationshipType.PUBLISHER) }

    val tags = dsl.select(TableBookTag.TAG_ID)
      .from(TableBookTag)
      .where(TableBookTag.BOOK_ID.eq(id))
      .fetch { RelationDto(it.get(TableBookTag.TAG_ID), RelationshipType.TAG) }

    val contributors = dsl
      .select(TableBookContributor.ID)
      .from(TableBookContributor)
      .where(TableBookContributor.BOOK_ID.eq(id))
      .fetch { RelationDto(it.get(TableBookContributor.ID), RelationshipType.CONTRIBUTOR) }

    val library = RelationDto(bookDao.getLibraryIdOrNull(id)!!, RelationshipType.LIBRARY)

    val cover = RelationDto(id, RelationshipType.COVER_ART)
      .takeIf { bookCoverLifecycle.hasCover(id) }

    return toDto(
      library = library,
      cover = cover,
      publishers = publishers.toTypedArray(),
      tags = tags.toTypedArray(),
      contributors = contributors.toTypedArray(),
    )
  }

  private fun List<Book>.toDto(): List<BookEntityDto> {
    val bookIds = map(Book::id)

    lateinit var publishers: Map<String, List<RelationDto>>
    lateinit var tags: Map<String, List<RelationDto>>
    lateinit var contributors: Map<String, List<RelationDto>>
    lateinit var libraries: Map<String, RelationDto>
    lateinit var covers: Map<String, RelationDto>

    transactionTemplate.executeWithoutResult {
      dsl.insertTempStrings(batchSize, bookIds)

      publishers = dsl.select(*TableBookPublisher.fields())
        .from(TableBookPublisher)
        .where(TableBookPublisher.BOOK_ID.`in`(dsl.selectTempStrings()))
        .groupBy({ it.get(TableBookPublisher.BOOK_ID) }) { record ->
          RelationDto(record.get(TableBookPublisher.PUBLISHER_ID), RelationshipType.PUBLISHER)
        }

      tags = dsl.select(*TableBookTag.fields())
        .from(TableBookTag)
        .where(TableBookTag.BOOK_ID.`in`(dsl.selectTempStrings()))
        .groupBy({ it.get(TableBookTag.BOOK_ID) }) { record ->
          RelationDto(record.get(TableBookTag.TAG_ID), RelationshipType.TAG)
        }

      contributors = dsl
        .select(*TableBookContributor.fields())
        .from(TableBookContributor)
        .where(TableBookContributor.BOOK_ID.`in`(dsl.selectTempStrings()))
        .groupBy({ it.get(TableBookContributor.BOOK_ID) }) { record ->
          RelationDto(record.get(TableBookContributor.ID), RelationshipType.CONTRIBUTOR)
        }

      libraries = dsl
        .select(TableCollection.LIBRARY_ID, TableBook.ID)
        .from(TableBook)
        .leftJoin(TableCollection)
        .on(TableCollection.ID.eq(TableBook.COLLECTION_ID))
        .where(TableBook.ID.`in`(dsl.selectTempStrings()))
        .fetch()
        .associate { it[TableBook.ID] to RelationDto(it[TableCollection.LIBRARY_ID], RelationshipType.LIBRARY) }

      covers = bookIds
        .associateWith { bookCoverLifecycle.hasCover(it) }
        .filterValues { it }
        .mapValues { RelationDto(it.key, RelationshipType.COVER_ART) }
    }

    return map { book ->
      book.toDto(
        library = libraries[book.id]!!,
        cover = covers[book.id],
        publishers = publishers[book.id].orEmpty().toTypedArray(),
        tags = tags[book.id].orEmpty().toTypedArray(),
        contributors = contributors[book.id].orEmpty().toTypedArray(),
      )
    }
  }

  private fun BookSearch.toCondition(): Condition {
    var c: Condition = DSL.noCondition()

    if (!seriesIds.isNullOrEmpty()) c = c.and(TableBook.SERIES_ID.`in`(seriesIds))

    if (!userId.isNullOrEmpty()) {
      val user = userDao.findByIdOrNull(userId)!!
      val librariesIdsUserHasAccess = libraryDao.getAllowedToViewLibrariesIds(userId)
      val filteredLibrariesIds = libraryIds.orEmpty()
        .filter { it in librariesIdsUserHasAccess }

      val libraryCondition = when {
        user.isAdmin && !libraryIds.isNullOrEmpty() ->
          TableCollection.LIBRARY_ID.`in`(libraryIds)

        user.isAdmin -> DSL.noCondition()
        !libraryIds.isNullOrEmpty() ->
          TableCollection.LIBRARY_ID.inOrNoCondition(filteredLibrariesIds)

        else -> DSL.noCondition()
      }

      c = c.and(libraryCondition)
    }

    return c
  }

  private fun Book.toDto(
    library: RelationDto,
    cover: RelationDto?,
    publishers: Array<RelationDto>,
    tags: Array<RelationDto>,
    contributors: Array<RelationDto>,
  ) = BookEntityDto(
    id = id,
    attributes = toAttributesDto(),
    relationships = listOfNotNull(
      RelationDto(collectionId, RelationshipType.COLLECTION),
      seriesId?.let { RelationDto(it, RelationshipType.SERIES) },
      storeId?.let { RelationDto(it, RelationshipType.STORE) },
      library,
      cover,
      *publishers,
      *contributors,
      *tags,
    )
  )

  private fun BookRecord.toDomain() = Book(
    code = code,
    title = title,
    paidPrice = FastMoney.of(paidPriceValue, paidPriceCurrency),
    labelPrice = FastMoney.of(labelPriceValue, labelPriceCurrency),
    dimensions = Dimensions(
      widthCm = dimensionWidthCm,
      heightCm = dimensionHeightCm,
    ),
    id = id,
    collectionId = collectionId,
    storeId = storeId,
    seriesId = seriesId,
    barcode = barcode,
    isInLibrary = isInLibrary,
    number = number,
    pageCount = pageCount,
    synopsis = synopsis,
    notes = notes,
    boughtAt = boughtAt?.toCurrentTimeZone(),
    billedAt = billedAt?.toCurrentTimeZone(),
    arrivedAt = arrivedAt?.toCurrentTimeZone(),
    createdAt = createdAt.toCurrentTimeZone(),
    modifiedAt = modifiedAt.toCurrentTimeZone(),
  )

}
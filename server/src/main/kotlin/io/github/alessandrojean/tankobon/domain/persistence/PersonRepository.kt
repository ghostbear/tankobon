package io.github.alessandrojean.tankobon.domain.persistence

import io.github.alessandrojean.tankobon.domain.model.Person
import io.github.alessandrojean.tankobon.domain.model.PersonSearch
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface PersonRepository {
  fun findById(personId: String): Person
  fun findByIdOrNull(personId: String): Person?
  fun findByNameInLibraryOrNull(name: String, libraryId: String): Person?
  fun findByNamesInLibraryOrNull(names: Collection<String>, libraryId: String): Collection<Person>
  fun findByLibraryId(libraryId: String): Collection<Person>

  fun findAll(): Collection<Person>
  fun findAll(search: PersonSearch, pageable: Pageable): Page<Person>
  fun findAllByIds(personIds: Collection<String>): Collection<Person>

  fun existsByNameInLibrary(name: String, libraryId: String): Boolean

  fun getLibraryIdOrNull(personId: String): String?

  fun insert(person: Person)
  fun update(person: Person)

  fun delete(personId: String)
  fun deleteAll()

  fun count(): Long
}

package io.github.alessandrojean.tankobon.interfaces.api.rest

import io.github.alessandrojean.tankobon.domain.model.IdDoesNotExistException
import io.github.alessandrojean.tankobon.domain.model.Person
import io.github.alessandrojean.tankobon.domain.model.PersonSearch
import io.github.alessandrojean.tankobon.domain.model.RelationIdDoesNotExistException
import io.github.alessandrojean.tankobon.domain.model.UserDoesNotHaveAccessException
import io.github.alessandrojean.tankobon.domain.persistence.LibraryRepository
import io.github.alessandrojean.tankobon.domain.persistence.PersonRepository
import io.github.alessandrojean.tankobon.domain.service.PersonLifecycle
import io.github.alessandrojean.tankobon.domain.service.ReferenceExpansion
import io.github.alessandrojean.tankobon.infrastructure.security.TankobonPrincipal
import io.github.alessandrojean.tankobon.interfaces.api.rest.dto.PersonCreationDto
import io.github.alessandrojean.tankobon.interfaces.api.rest.dto.PersonEntityDto
import io.github.alessandrojean.tankobon.interfaces.api.rest.dto.PersonUpdateDto
import io.github.alessandrojean.tankobon.interfaces.api.rest.dto.RelationshipType
import io.github.alessandrojean.tankobon.interfaces.api.rest.dto.SuccessEntityResponseDto
import io.github.alessandrojean.tankobon.interfaces.api.rest.dto.SuccessPaginatedCollectionResponseDto
import io.github.alessandrojean.tankobon.interfaces.api.rest.dto.toDto
import io.github.alessandrojean.tankobon.interfaces.api.rest.dto.toPaginationDto
import io.github.alessandrojean.tankobon.interfaces.api.rest.dto.toSuccessCollectionResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.hibernate.validator.constraints.UUID
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("api", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Person", description = "Operations regarding people")
class PersonController(
  private val libraryRepository: LibraryRepository,
  private val personRepository: PersonRepository,
  private val personLifecycle: PersonLifecycle,
  private val referenceExpansion: ReferenceExpansion,
) {

  @GetMapping("v1/people")
  @Operation(summary = "Get all people", security = [SecurityRequirement(name = "Basic Auth")])
  fun getAllPeople(
    @AuthenticationPrincipal principal: TankobonPrincipal,
    @RequestParam(name = "search", required = false) searchTerm: String? = null,
    @RequestParam(name = "libraries", required = false)
    @ArraySchema(schema = Schema(format = "uuid"))
    libraryIds: Set<@UUID(version = [4]) String>? = null,
    @RequestParam(required = false, defaultValue = "") includes: Set<RelationshipType> = emptySet(),
    @Parameter(hidden = true) page: Pageable,
  ): SuccessPaginatedCollectionResponseDto<PersonEntityDto> {
    val peoplePage = personRepository.findAll(
      search = PersonSearch(
        libraryIds = libraryIds,
        searchTerm = searchTerm,
        userId = principal.user.id,
      ),
      pageable = page,
    )

    val people = referenceExpansion.expand(
      entities = peoplePage.content.map { it.toDto() },
      relationsToExpand = includes,
    )

    return SuccessPaginatedCollectionResponseDto(people, peoplePage.toPaginationDto())
  }

  @GetMapping("v1/libraries/{libraryId}/people")
  @Operation(summary = "Get all people from a library", security = [SecurityRequirement(name = "Basic Auth")])
  fun getAllPeopleByLibrary(
    @AuthenticationPrincipal principal: TankobonPrincipal,
    @RequestParam(name = "search", required = false) searchTerm: String? = null,
    @PathVariable @UUID(version = [4]) @Schema(format = "uuid") libraryId: String,
    @Parameter(hidden = true) page: Pageable,
  ): SuccessPaginatedCollectionResponseDto<PersonEntityDto> {
    val library = libraryRepository.findByIdOrNull(libraryId)
      ?: throw IdDoesNotExistException("Library not found")

    if (!principal.user.canAccessLibrary(library)) {
      throw UserDoesNotHaveAccessException()
    }

    val people = personRepository.findAll(
      search = PersonSearch(
        libraryIds = listOf(library.id),
        searchTerm = searchTerm,
        userId = principal.user.id,
      ),
      pageable = page,
    )

    return people.toSuccessCollectionResponseDto { it.toDto() }
  }

  @GetMapping("v1/people/{personId}")
  @Operation(summary = "Get a person by its id", security = [SecurityRequirement(name = "Basic Auth")])
  fun getOnePerson(
    @AuthenticationPrincipal principal: TankobonPrincipal,
    @PathVariable @UUID(version = [4]) @Schema(format = "uuid") personId: String,
    @RequestParam(required = false, defaultValue = "") includes: Set<RelationshipType> = emptySet(),
  ): SuccessEntityResponseDto<PersonEntityDto> {
    val person = personRepository.findByIdOrNull(personId)
      ?: throw IdDoesNotExistException("Person not found")

    val library = libraryRepository.findById(person.libraryId)

    if (!principal.user.canAccessLibrary(library)) {
      throw UserDoesNotHaveAccessException()
    }

    val expanded = referenceExpansion.expand(
      entity = person.toDto(),
      relationsToExpand = includes,
    )

    return SuccessEntityResponseDto(expanded)
  }

  @PostMapping("v1/people")
  @Operation(summary = "Create a new person", security = [SecurityRequirement(name = "Basic Auth")])
  fun addOnePerson(
    @AuthenticationPrincipal principal: TankobonPrincipal,
    @Valid @RequestBody
    person: PersonCreationDto,
  ): SuccessEntityResponseDto<PersonEntityDto> {
    val library = libraryRepository.findByIdOrNull(person.library)
      ?: throw RelationIdDoesNotExistException("Library not found")

    if (!principal.user.canAccessLibrary(library)) {
      throw UserDoesNotHaveAccessException()
    }

    val created = personLifecycle.addPerson(
      Person(
        name = person.name,
        description = person.description,
        libraryId = person.library,
      )
    )

    return SuccessEntityResponseDto(created.toDto())
  }

  @DeleteMapping("v1/people/{personId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a person by its id", security = [SecurityRequirement(name = "Basic Auth")])
  fun deleteOnePerson(
    @AuthenticationPrincipal principal: TankobonPrincipal,
    @PathVariable @UUID(version = [4]) @Schema(format = "uuid") personId: String
  ) {
    val existing = personRepository.findByIdOrNull(personId)
      ?: throw IdDoesNotExistException("Person not found")

    val library = libraryRepository.findById(existing.libraryId)

    if (!principal.user.canAccessLibrary(library)) {
      throw UserDoesNotHaveAccessException()
    }

    personLifecycle.deletePerson(existing)
  }

  @PutMapping("v1/people/{personId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Modify a person by its id", security = [SecurityRequirement(name = "Basic Auth")])
  fun updateOnePerson(
    @AuthenticationPrincipal principal: TankobonPrincipal,
    @PathVariable @UUID(version = [4]) @Schema(format = "uuid") personId: String,
    @Valid @RequestBody
    person: PersonUpdateDto
  ) {
    val existing = personRepository.findByIdOrNull(personId)
      ?: throw IdDoesNotExistException("Person not found")

    val library = libraryRepository.findById(existing.libraryId)

    if (!principal.user.canAccessLibrary(library)) {
      throw UserDoesNotHaveAccessException()
    }

    val toUpdate = existing.copy(
      name = person.name,
      description = person.description,
    )

    personLifecycle.updatePerson(toUpdate)
  }
}

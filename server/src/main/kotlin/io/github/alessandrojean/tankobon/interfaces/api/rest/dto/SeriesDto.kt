package io.github.alessandrojean.tankobon.interfaces.api.rest.dto

import io.github.alessandrojean.tankobon.domain.model.Series
import io.github.alessandrojean.tankobon.domain.model.SeriesAlternativeName
import io.github.alessandrojean.tankobon.domain.model.SeriesType
import io.github.alessandrojean.tankobon.infrastructure.jooq.toUtcTimeZone
import io.github.alessandrojean.tankobon.infrastructure.validation.Bcp47
import io.github.alessandrojean.tankobon.infrastructure.validation.NullOrBcp47
import io.github.alessandrojean.tankobon.infrastructure.validation.NullOrNotBlank
import io.github.alessandrojean.tankobon.infrastructure.validation.UrlMultipleHosts
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.hibernate.validator.constraints.URL
import org.hibernate.validator.constraints.UUID
import org.hibernate.validator.constraints.UniqueElements
import java.time.LocalDateTime

data class SeriesEntityDto(
  override val id: String,
  override val attributes: EntityAttributesDto,
  override var relationships: List<RelationDto<ReferenceExpansionSeries>>? = null,
) : EntityDto<ReferenceExpansionSeries> {
  @Schema(type = "string", allowableValues = ["SERIES"])
  override val type = EntityType.SERIES
}

data class SeriesAttributesDto(
  val name: String,
  val description: String,
  val type: SeriesType?,
  val alternativeNames: List<SeriesAlternativeNameDto>,
  val lastNumber: String?,
  val originalLanguage: String?,
  val links: SeriesLinksDto,
  val createdAt: LocalDateTime?,
  val modifiedAt: LocalDateTime?
) : EntityAttributesDto()

data class SeriesAlternativeNameDto(
  @get:NotBlank
  val name: String,
  @get:Bcp47
  @get:Schema(format = "bcp-47")
  val language: String,
)

data class SeriesLinksDto(
  @get:NullOrNotBlank
  @get:URL
  val website: String? = null,
  @get:NullOrNotBlank
  @get:UrlMultipleHosts(allowedHosts = ["myanimelist.net"])
  val myAnimeList: String? = null,
  @get:NullOrNotBlank
  @get:UrlMultipleHosts(allowedHosts = ["kitsu.io"])
  val kitsu: String? = null,
  @get:NullOrNotBlank
  @get:UrlMultipleHosts(allowedHosts = ["anilist.co"])
  val aniList: String? = null,
  @get:NullOrNotBlank
  @get:UrlMultipleHosts(allowedHosts = ["mangaupdates.com"])
  val mangaUpdates: String? = null,
  @get:NullOrNotBlank
  @get:UrlMultipleHosts(allowedHosts = ["guiadosquadrinhos.com"])
  val guiaDosQuadrinhos: String? = null,
  @get:NullOrNotBlank
  @get:UrlMultipleHosts(allowedHosts = ["twitter.com", "mobile.twitter.com"])
  val twitter: String? = null,
  @get:NullOrNotBlank
  @get:UrlMultipleHosts(allowedHosts = ["instagram.com"])
  val instagram: String? = null,
)

enum class ReferenceExpansionSeries : ReferenceExpansionEnum {
  LIBRARY,
  SERIES_COVER,
}

fun Series.toDto(libraryAttributes: LibraryAttributesDto? = null) = SeriesEntityDto(
  id = id,
  attributes = toAttributesDto(alternativeNames),
  relationships = listOf(
    RelationDto(
      id = libraryId,
      type = ReferenceExpansionSeries.LIBRARY,
      attributes = libraryAttributes,
    )
  )
)

fun SeriesAlternativeName.toDto() = SeriesAlternativeNameDto(name, language)

fun Series.toAttributesDto(alternativeNames: List<SeriesAlternativeName>) = SeriesAttributesDto(
  name = name,
  description = description,
  type = type,
  alternativeNames = alternativeNames.map(SeriesAlternativeName::toDto),
  lastNumber = lastNumber,
  originalLanguage = originalLanguage,
  links = SeriesLinksDto(
    website = links.website,
    myAnimeList = links.myAnimeList,
    kitsu = links.kitsu,
    aniList = links.aniList,
    twitter = links.twitter,
    instagram = links.instagram,
  ),
  createdAt = createdAt.toUtcTimeZone(),
  modifiedAt = modifiedAt.toUtcTimeZone(),
)

data class SeriesCreationDto(
  @get:NotBlank
  val name: String,
  @get:NotNull
  val description: String,
  val type: SeriesType?,
  @get:NotNull
  @get:UniqueElements
  val alternativeNames: List<@NotNull SeriesAlternativeNameDto>,
  @get:NullOrNotBlank
  val lastNumber: String?,
  @get:NullOrBcp47
  @get:Schema(format = "bcp-47")
  val originalLanguage: String?,
  @get:NotNull
  val links: SeriesLinksDto,
  @get:UUID(version = [4])
  @get:Schema(format = "uuid")
  val library: String,
)

data class SeriesUpdateDto(
  @get:NotBlank
  val name: String,
  @get:NotNull
  val description: String,
  val type: SeriesType?,
  @get:NotNull
  @get:UniqueElements
  val alternativeNames: List<@NotNull SeriesAlternativeNameDto>,
  @get:NullOrNotBlank
  val lastNumber: String?,
  @get:NullOrBcp47
  @get:Schema(format = "bcp-47")
  val originalLanguage: String?,
  @get:NotNull
  val links: SeriesLinksDto,
)

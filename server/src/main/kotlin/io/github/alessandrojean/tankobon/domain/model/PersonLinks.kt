package io.github.alessandrojean.tankobon.domain.model

import java.io.Serializable

data class PersonLinks(
  val website: String? = null,
  val twitter: String? = null,
  val instagram: String? = null,
  val facebook: String? = null,
  val pixiv: String? = null,
  val deviantArt: String? = null,
  val youTube: String? = null,
) : Serializable

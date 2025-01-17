package io.github.alessandrojean.tankobon.domain.model

import org.javamoney.moneta.FastMoney
import java.util.UUID
import javax.money.MonetaryAmount

fun makeUuid(): String = UUID.randomUUID().toString()

fun makeLibrary(
  name: String = "default",
  description: String = "Default",
  id: String = makeUuid(),
  ownerId: String = "0",
): Library = Library(
  name = name,
  description = description,
  id = id,
  ownerId = ownerId,
)

fun makeBook(
  code: String = makeUuid(),
  title: String = "default",
  paidPrice: MonetaryAmount = FastMoney.of(10.99f, "USD"),
  labelPrice: MonetaryAmount = FastMoney.of(9.99f, "USD"),
  dimensions: Dimensions = Dimensions(widthCm = 13.2f, heightCm = 20f),
  id: String = makeUuid(),
  collectionId: String = "0",
): Book = Book(
  code = code,
  title = title,
  paidPrice = paidPrice,
  labelPrice = labelPrice,
  dimensions = dimensions,
  id = id,
  collectionId = collectionId,
)

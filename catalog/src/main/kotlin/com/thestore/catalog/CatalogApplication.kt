package com.thestore.catalog

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.support.beans
import org.springframework.data.repository.kotlin.CoroutineSortingRepository
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.reactive.function.server.*


interface ProductRepository :  CoroutineSortingRepository<Product, Long>

class ProductHandler(private val repository: ProductRepository) {

  private val logger = LoggerFactory.getLogger(ProductHandler::class.java)

  suspend fun products(req: ServerRequest): ServerResponse {
    val products = repository.findAll()
    return ServerResponse.ok().bodyAndAwait(products)
  }

  suspend fun product(req: ServerRequest): ServerResponse {
    val idString = req.pathVariable("id")
    val id = idString.toLongOrNull() ?: return ServerResponse.badRequest().bodyValueAndAwait("$idString is not a valid ID")
    val result = fetch(id)
    return ServerResponse.ok().bodyValueAndAwait(result)
  }

  suspend fun fetch(id:Long): Result<Product> {
    val product = repository.findById(id)
    return if (product == null) Result.failure(IllegalArgumentException("Product $id not found"))
    else Result.success(product)
  }

}


val beans = beans {
  bean {
    var handlers = ProductHandler(ref())
    coRouter {
      GET("/products")(handlers::products)
      GET("/products/{id}")(handlers::product)
    }
  }
}

@SpringBootApplication
class CatalogApplication

fun main(args: Array<String>) {
	runApplication<CatalogApplication>(*args) {
    addInitializers(beans)
  }
}

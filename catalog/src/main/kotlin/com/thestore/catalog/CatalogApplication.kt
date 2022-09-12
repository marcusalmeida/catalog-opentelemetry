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

class ProductHandler(private val repository: ProductRepository, private val client: WebClient) {

  private val logger = LoggerFactory.getLogger(ProductHandler::class.java)

  suspend fun products(req: ServerRequest): ServerResponse {
    printHeader(req)
    val products = repository.findAll().map {
      val price = client.get().uri("/${it.id}").retrieve().bodyToMono<Price>().awaitSingle()
      it .withPrice(price)
    }
    return ServerResponse.ok().bodyAndAwait(products)
  }

  suspend fun product(req: ServerRequest): ServerResponse {
    printHeader(req)
    val idString = req.pathVariable("id")
    val id = idString.toLongOrNull() ?: return ServerResponse.badRequest().bodyValueAndAwait("$idString is not a valid ID")
    val result = fetch(id)
    return result.fold(
        {
          val price = client.get().uri("/$id").retrieve().bodyToMono<Price>().awaitSingle()
          ServerResponse.ok().bodyValueAndAwait(it.withPrice(price))
        },
        { ServerResponse.notFound().buildAndAwait() }
    )
    return ServerResponse.ok().bodyValueAndAwait(result)
  }

  @WithSpan("ProductHandler.fetch")
  suspend fun fetch(@SpanAttribute("id") id:Long): Result<Product> {
    val product = repository.findById(id)
    return if (product == null) Result.failure(IllegalArgumentException("Product $id not found"))
    else Result.success(product)
  }
 
  private fun printHeader(req: ServerRequest) {
    req.headers().firstHeader("traceparent")?.let {
      logger.info("traceparent: $it")
    }
  }

  private fun Product.withPrice(price: Price) = PricedProduct(id, name, description, price.price)
}


val beans = beans {
  bean {
    var properties = ref<AppProperties>()
    var client = WebClient.builder().baseUrl(properties.endpoint).build()
    var handlers = ProductHandler(ref(), client)
    coRouter {
      GET("/products")(handlers::products)
      GET("/products/{id}")(handlers::product)
    }
  }
}

@SpringBootApplication
@EnableConfigurationProperties(value = [AppProperties::class])
class CatalogApplication

fun main(args: Array<String>) {
	runApplication<CatalogApplication>(*args) {
    addInitializers(beans)
  }
}

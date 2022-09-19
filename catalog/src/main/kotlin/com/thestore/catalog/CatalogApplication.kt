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

class ProductHandler(private val repository: ProductRepository, private val pricingClient: WebClient, private val ratingClient: WebClient) {

  private val logger = LoggerFactory.getLogger(ProductHandler::class.java)

  suspend fun products(req: ServerRequest): ServerResponse {
    printHeader(req)
    val products = repository.findAll().map {
      val price = pricingClient.get().uri("/${it.id}").retrieve().bodyToMono<Price>().awaitSingle()
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
          val price = pricingClient.get().uri("/$id").retrieve().bodyToMono<Price>().awaitSingle()
          val rating  = ratingClient.get().uri("/$id").retrieve().bodyToMono<Rating>().awaitSingle()
          ServerResponse.ok().bodyValueAndAwait(it.withPriceAndRating(price, rating))
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
  private fun Product.withPriceAndRating(price: Price, rating: Rating) = CompleteProduct(id, name, description, price.price, rating.value)
}


val beans = beans {
  bean {
    var pricingProperties = ref<PricingAppProperties>()
    var pricingClient = WebClient.builder().baseUrl(pricingProperties.endpoint).build()
    var ratingProperties = ref<RatingAppProperties>()
    var ratingClient = WebClient.builder().baseUrl(ratingProperties.endpoint).build()
    var handlers = ProductHandler(ref(), pricingClient, ratingClient)
    coRouter {
      GET("/products")(handlers::products)
      GET("/products/{id}")(handlers::product)
    }
  }
}

@SpringBootApplication
@EnableConfigurationProperties(value = [PricingAppProperties::class, RatingAppProperties::class])
class CatalogApplication

fun main(args: Array<String>) {
	runApplication<CatalogApplication>(*args) {
    addInitializers(beans)
  }
}

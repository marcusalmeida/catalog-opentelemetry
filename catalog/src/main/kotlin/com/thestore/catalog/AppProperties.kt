package com.thestore.catalog 

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "app.pricing")
data class PricingAppProperties(val endpoint: String)

@ConstructorBinding
@ConfigurationProperties(prefix = "app.rating")
data class RatingAppProperties(val endpoint: String)

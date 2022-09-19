package com.thestore.catalog

import org.springframework.data.annotation.Id


data class Product(@Id val id: Long, val name: String, val description: String)

data class Price(val price: Float)

data class Rating(val value: Int)

data class PricedProduct(val id: Long, val name: String, val description: String, val price: Float)
data class CompleteProduct(val id: Long, val name: String, val description: String, val price: Float, val rating: Int)

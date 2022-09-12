package com.thestore.catalog

import org.springframework.data.annotation.Id


data class Product(@Id val id: Long, val name: String, val description: String)

data class Price(val price: Float)

data class PriceProduct(val id: Long, val name: String, val description: String, val price: Float)

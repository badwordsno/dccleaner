package com.dccleaner.app.model

data class AggregateResult(
    val status: Boolean,
    val data: String,
    val aggregateData: AggregateData?
)

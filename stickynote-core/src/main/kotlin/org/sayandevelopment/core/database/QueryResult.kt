package org.sayandevelopment.core.database

import java.sql.ResultSet

data class QueryResult(
    val statusCode: Query.StatusCode,
    val result: ResultSet?
)
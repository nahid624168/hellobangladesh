package com.exambase.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.Connection

class Database private constructor(private val dataSource: HikariDataSource) {

    fun migrate() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    fun ping(): Boolean = runCatching {
        dataSource.connection.use { c -> c.createStatement().use { it.execute("SELECT 1") } }
    }.isSuccess

    fun <T> withConnection(block: (Connection) -> T): T = dataSource.connection.use(block)

    fun <T> transaction(block: (Connection) -> T): T = dataSource.connection.use { c ->
        c.autoCommit = false
        try {
            block(c).also { c.commit() }
        } catch (e: Throwable) {
            c.rollback()
            throw e
        }
    }

    fun close() = dataSource.close()

    companion object {
        fun connectOrNull(config: AppConfig): Database? {
            val url = config.dbUrl ?: return null
            val hikari = HikariConfig().apply {
                jdbcUrl = url
                username = config.dbUser
                password = config.dbPassword
                maximumPoolSize = 10
                isAutoCommit = true
            }
            return Database(HikariDataSource(hikari))
        }
    }
}

package org.acme

import io.quarkus.amazon.lambda.runtime.AmazonLambdaApi.API_BASE_PATH_TEST
import io.quarkus.test.junit.QuarkusTest
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.eclipse.microprofile.config.ConfigProvider
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

@QuarkusTest
class LambdaTest {
    @Test
    fun shouldRunLambda(){
        Given {
            body("{\"property\":\"value\"}")
        } When {
            post(API_BASE_PATH_TEST)
        } Then {
            statusCode(200)
        }
        val messages = snsTestClient.getMessagesWithAttributes(1, 10, 10)
        assertThat(messages, hasSize(1))
        println(messages.first())
    }

    companion object {
        lateinit var snsTestClient: SnsTestClient

        @JvmStatic
        @BeforeAll
        fun init() {
            val testTopicName = ConfigProvider.getConfig().getConfigValue("topic-arn").rawValue.substringAfterLast(":")
            snsTestClient = SnsTestClient(testTopicName)
            snsTestClient.create()
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            snsTestClient.delete()
        }
    }
}
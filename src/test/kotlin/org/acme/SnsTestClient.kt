package org.acme

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.eclipse.microprofile.config.ConfigProvider
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sqs.model.Message
import java.net.URI
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.locks.ReentrantLock

class SnsTestClient(
    private val topicName: String,
    delayMessagesInSeconds: Int = 2
) {
    private val snsClient: SnsClient = initializeSNSClient()
    private val sqsClient: SqsTestClient = SqsTestClient(topicName, delayMessagesInSeconds)

    private var topicArn: String? = null
    private val lock: ReentrantLock = ReentrantLock()

    companion object {
        const val TIMEOUT = 10L
        const val SNS_ENDPOINT = "quarkus.sns.endpoint-override"
        const val AWS_REGION = "quarkus.sns.aws.region"
        const val ACCESS_KEY_ID = "quarkus.sns.aws.credentials.static-provider.access-key-id"
        const val SECRET_ACCESS_KEY = "quarkus.sns.aws.credentials.static-provider.secret-access-key"
    }

    fun create() {
        withLock(lock) { this.topicArn = createAndSubscribeTopic(topicName).topicArn }
    }

    fun delete() {
        withLock(lock) {
            topicArn?.let {
                snsClient.deleteTopic { it.topicArn(topicArn) }
                sqsClient.delete()
                topicArn = null
            }
        }
    }

    fun getMessagesWithAttributes(
        maxMessageNumber: Int,
        waitTimeSeconds: Int,
        visibilityTimeout: Int
    ): List<SnsMessageWithAttributes> {
        val queueUrl = sqsClient.getQueueUrl(topicName)
        val receivedMessages = sqsClient.receiveMessages(queueUrl, maxMessageNumber, waitTimeSeconds, visibilityTimeout)

        sqsClient.deleteMessages(receivedMessages)

        return receivedMessages
            .map { mapMessageToSnsMessageWithAttributes(it) }
            .asReversed()
    }

    private fun mapMessageToSnsMessageWithAttributes(
        message: Message
    ): SnsMessageWithAttributes {
        return ObjectMapper().readTree(message.body())
            .let {
                SnsMessageWithAttributes(
                    message = it["Message"].asText(),
                    attributes = it["MessageAttributes"]?.let { node -> sortSnsMessageAttributes(node) }
                )
            }
    }

    private fun sortSnsMessageAttributes(
        attributes: JsonNode
    ): String {
        attributes.fields().forEach { (_, value) ->
            if (value["Type"]?.asText() == "String.Array") {
                val valueText = value["Value"]?.asText() ?: error("Unsupported value type=$value for String.Array in=$attributes")
                ObjectMapper().readTree(valueText)
                    .mapNotNull { it.asText() }
                    .sorted()
                    .let(ObjectMapper()::writeValueAsString)
                    .let { (value as ObjectNode).put("Value", it) }
            }
        }
        return attributes.toString()
    }

    private fun initializeSNSClient(): SnsClient {
        return SnsClient.builder()
            .httpClient(UrlConnectionHttpClient.builder().build())
            .endpointOverride(URI.create(getConfigValue(SNS_ENDPOINT)))
            .region(Region.of(getConfigValue(AWS_REGION)))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(getConfigValue(ACCESS_KEY_ID), getConfigValue(SECRET_ACCESS_KEY))
                )
            )
            .build()
    }

    private fun createAndSubscribeTopic(
        topicName: String
    ): CreateTopicResponse {
        val topicArn = createTopic(topicName)
        val queueArn = createAndSubscribeQueue(topicArn, topicName)

        return CreateTopicResponse(
            topicArn = topicArn,
            queueArn = queueArn
        )
    }

    private fun createTopic(
        topicName: String
    ): String {
        return snsClient.createTopic { it.name(topicName) }.topicArn()
    }

    private fun createAndSubscribeQueue(
        topicArn: String,
        queueName: String
    ): String {
        sqsClient.create()
        val queueArn = sqsClient.getQueueArn(queueName)

        snsClient.subscribe {
            it.topicArn(topicArn)
                .endpoint(queueArn)
                .protocol("sqs")
        }

        return queueArn!!
    }

    private data class CreateTopicResponse(

        val topicArn: String,

        val queueArn: String
    )

    private fun getConfigValue(
        propertyName: String
    ): String {
        return ConfigProvider.getConfig().getConfigValue(propertyName).rawValue
    }

    private fun withLock(
        lock: ReentrantLock,
        action: () -> Unit
    ) {
        try {
            lock.tryLock(TIMEOUT, SECONDS)
            action()
        } finally {
            lock.unlock()
        }
    }
}




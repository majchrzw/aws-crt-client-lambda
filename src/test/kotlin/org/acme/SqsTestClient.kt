package org.acme

import org.acme.SnsTestClient.Companion.TIMEOUT
import org.eclipse.microprofile.config.ConfigProvider
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import software.amazon.awssdk.services.sqs.model.QueueAttributeName.DELAY_SECONDS
import software.amazon.awssdk.services.sqs.model.QueueAttributeName.QUEUE_ARN
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import java.net.URI
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.locks.ReentrantLock

class SqsTestClient(
    private val queueName: String,
    private val delayMessagesInSeconds: Int = 2
) {
    private val sqsClient: SqsClient = initializeSQSClient()

    private val lock: ReentrantLock = ReentrantLock()
    private val queueAttributes: Map<QueueAttributeName, String> = mapOf(
        DELAY_SECONDS to delayMessagesInSeconds.toString()
    )

    companion object {
        const val SQS_ENDPOINT = "quarkus.sqs.endpoint-override"
        const val AWS_REGION = "quarkus.sns.aws.region"
        const val ACCESS_KEY_ID = "quarkus.sns.aws.credentials.static-provider.access-key-id"
        const val SECRET_ACCESS_KEY = "quarkus.sns.aws.credentials.static-provider.secret-access-key"
    }

    fun create() {
        withLock(lock) { createQueue(queueName) }
    }

    fun delete() {
        withLock(lock) {
            runCatching { sqsClient.deleteQueue { it.queueUrl(getQueueUrl(queueName)) } }
        }
    }

    fun getMessagesWithAttributes(
        maxMessageNumber: Int,
        waitTimeSeconds: Int,
        visibilityTimeout: Int
    ): List<SqsMessageWithAttributes> {
        val queueUrl = getQueueUrl(queueName)
        val receivedMessages = receiveMessages(queueUrl, maxMessageNumber, waitTimeSeconds, visibilityTimeout)

        deleteMessages(receivedMessages)

        return receivedMessages
            .map { mapMessageToSqsMessageWithAttributes(it) }
            .asReversed()
    }

    fun receiveMessages(
        queueUrl: String,
        maxMessageNumber: Int,
        waitTimeSeconds: Int,
        visibilityTimeout: Int
    ): List<Message> {
        return sqsClient.receiveMessage {
            it.queueUrl(queueUrl)
                .maxNumberOfMessages(maxMessageNumber)
                .waitTimeSeconds(waitTimeSeconds)
                .visibilityTimeout(visibilityTimeout)
                .messageAttributeNames("All")
        }
            .messages()
    }

    fun getQueueArn(
        queueName: String
    ): String? {
        return sqsClient.getQueueAttributes {
            it.queueUrl(queueName).attributeNames(QUEUE_ARN)
        }.attributes()[QUEUE_ARN]
    }

    fun getQueueUrl(
        queueName: String
    ): String {
        return sqsClient.getQueueUrl { it.queueName(queueName) }.queueUrl()
    }

    fun deleteMessages(
        messages: List<Message>
    ) {
        messages.forEach { message ->
            sqsClient.deleteMessage { it.queueUrl(queueName).receiptHandle(message.receiptHandle()) }
        }
    }

    fun sendMessage(
        messageBody: String,
        messageAttributes: Map<String, MessageAttributeValue>
    ): SendMessageResponse {
        val queueUrl = getQueueUrl(queueName)
        return sqsClient.sendMessage {
            it.queueUrl(queueUrl).messageBody(messageBody).messageAttributes(messageAttributes)
        }
    }

    private fun queueExists(
        queueName: String
    ): Boolean {
        return sqsClient.listQueues {
            it.queueNamePrefix(queueName)
        }.queueUrls().any { it.endsWith("/$queueName") }
    }

    private fun mapMessageToSqsMessageWithAttributes(
        message: Message
    ): SqsMessageWithAttributes {
        return SqsMessageWithAttributes(
            message = message.body(),
            attributes = message.messageAttributes().toSortedMap()
        )
    }

    private fun initializeSQSClient(): SqsClient {
        return SqsClient.builder()
            .httpClient(UrlConnectionHttpClient.builder().build())
            .endpointOverride(URI.create(getConfigValue(SQS_ENDPOINT)))
            .region(Region.of(getConfigValue(AWS_REGION)))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(getConfigValue(ACCESS_KEY_ID), getConfigValue(SECRET_ACCESS_KEY))
                )
            )
            .build()
    }

    private fun createQueue(
        queueName: String
    ) {
        if (queueExists(queueName)) {
            sqsClient.setQueueAttributes {
                it.queueUrl(getQueueUrl(queueName))
                    .attributes(queueAttributes)
            }
        } else {
            sqsClient.createQueue {
                it.queueName(queueName)
                it.attributes(queueAttributes)
            }
        }
    }

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
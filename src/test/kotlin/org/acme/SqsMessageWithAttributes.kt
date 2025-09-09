package org.acme

import software.amazon.awssdk.services.sqs.model.MessageAttributeValue

data class SqsMessageWithAttributes(

    val message: String,

    val attributes: Map<String, MessageAttributeValue>
)

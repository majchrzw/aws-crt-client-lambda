package org.acme

import com.amazonaws.services.lambda.runtime.Context
import jakarta.inject.Named
import com.amazonaws.services.lambda.runtime.RequestHandler
import io.smallrye.mutiny.Uni
import org.eclipse.microprofile.config.ConfigProvider
import software.amazon.awssdk.services.sns.SnsAsyncClient

@Named("TestLambda")
class Lambda(
    private val client: SnsAsyncClient,
) : RequestHandler<Any, String> {
    override fun handleRequest(request: Any, context: Context): String {
        val arn = ConfigProvider.getConfig().getConfigValue("topic-arn").rawValue
        return Uni.createFrom().completionStage {
            client.publish {
                it.topicArn(arn)
                it.message("message body")
            }
        }
            .map { "OK" }
            .await()
            .indefinitely()
    }
}
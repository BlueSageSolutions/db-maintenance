package aws

import groovy.json.JsonSlurper
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse
import java.util.Base64

class AWSSecretManger {
    SecretsManagerClient client

    AWSSecretManger() {
        client = SecretsManagerClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build()
    }

    Map getSecret(String id) {
        GetSecretValueRequest request = GetSecretValueRequest.builder().secretId(id).build()
        GetSecretValueResponse response = client.getSecretValue(request)
        def json = new JsonSlurper()

        if (response.secretString()) {
            return (Map) json.parseText(response.secretString())
        } else {
            def decoded = new String(Base64.decoder.decode(response.secretBinary().asByteBuffer().array()))
            return (Map) json.parseText(decoded)
        }
    }
}

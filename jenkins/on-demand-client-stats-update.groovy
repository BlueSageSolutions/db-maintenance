pipeline {
    agent any

    parameters {
        choice(name: 'CLIENT', choices: [
            'DCU', 'HB Retail', 'HB Wholesale', 'HB Wholesale Replica',
            'KIND Retail', 'KIND Retail Replica', 'KIND Wholesale', 'Lendage',
            'MFM', 'MFM Replica', 'MFM Correspondent', 'MIG', 'NASB',
            'PHL', 'PRIME', 'SEQ', 'Westerra', 'SCU', 'FCM', 'Chevron'
        ], description: 'Choose the client for stats update')

        string(name: 'SLACK_WEBHOOK', defaultValue: '', description: 'Slack webhook URL (optional)')
    }

    environment {
        SLACK_WEBHOOK_URL = "${params.SLACK_WEBHOOK}"
        AWS_REGION = "us-east-1"
    }

    stages {
        stage('Verify Setup') {
            steps {
                script {
                    if (!params.CLIENT) {
                        error("Client selection is required")
                    }
                    if (!fileExists('update_table_stats.py')) {
                        error("update_table_stats.py not found in workspace root")
                    }
                }
            }
        }

        stage('Run Stats Update') {
            steps {
                script {
                    def clientCredentials = [
                        'MFM': [
                            secretArn: 'arn:aws:secretsmanager:us-east-1:751218182449:secret:MFM_Retail_DB-0ATExQ',
                            host: 'mfm-prod.cluster-cpewz1juqrts.us-east-1.rds.amazonaws.com'
                        ],
                        'MFM Replica': [
                            secretArn: 'arn:aws:secretsmanager:us-east-1:751218182449:secret:MFM_Retail_DB-0ATExQ',
                            host: 'mfm-prod.cluster-ro-cpewz1juqrts.us-east-1.rds.amazonaws.com'
                        ],
                        // Add other clients with their respective secret ARNs
                    ]

                    def config = clientCredentials[params.CLIENT]
                    if (!config) {
                        error("Configuration not found for client: ${params.CLIENT}")
                    }

                    // Get credentials from AWS Secrets Manager securely
                    def secretJson = sh(
                        script: """
                            aws secretsmanager get-secret-value \
                              --secret-id ${config.secretArn} \
                              --region ${AWS_REGION} \
                              --query SecretString \
                              --output text
                        """,
                        returnStdout: true
                    ).trim()

                    // Parse the JSON secret
                    def secret = readJSON text: secretJson
                    
                    // Mask sensitive values in logs
                    wrap([$class: 'MaskPasswordsBuildWrapper']) {
                        env.DB_PASSWORD = secret.password
                        env.DB_USER = secret.username
                    }

                    docker.image('python:3.11-slim').inside('-u 0:0') {
                        withEnv([
                            "CLIENT=${params.CLIENT}",
                            "DB_HOST=${config.host}",
                            "DB_USER=${secret.username}",
                            "DB_PASSWORD=${secret.password}"
                        ]) {
                            sh '''
                                # This script block ensures password isn't exposed in command line
                                pip install --no-cache-dir --progress-bar off pymysql requests
                                python3 update_table_stats.py \
                                    --client "$CLIENT" \
                                    --host "$DB_HOST" \
                                    --user "$DB_USER" \
                                    --password "$DB_PASSWORD"
                            '''
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                // Clean up environment variables containing secrets
                env.DB_PASSWORD = null
                env.DB_USER = null
                echo "Cleaning up workspace..."
            }
        }
        success {
            script {
                echo "✅ Stats update for ${params.CLIENT} completed successfully."
                if (env.SLACK_WEBHOOK_URL) {
                    slackSend(color: "good", message: "✅ Stats update for ${params.CLIENT} completed successfully.")
                }
            }
        }
        failure {
            script {
                echo "❌ Stats update for ${params.CLIENT} failed."
                if (env.SLACK_WEBHOOK_URL) {
                    slackSend(color: "danger", message: "❌ Stats update for ${params.CLIENT} failed.")
                }
            }
        }
    }
}
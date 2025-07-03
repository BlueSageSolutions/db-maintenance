pipeline {
    agent any

    parameters {
        password(name: 'MFM_DB_PASS', defaultValue: '', description: 'Password for MFM')
        password(name: 'PHL_DB_PASS', defaultValue: '', description: 'Password for PHL')
        password(name: 'SEQ_DB_PASS', defaultValue: '', description: 'Password for SEQ')
        password(name: 'SLACK_SECRET', defaultValue: '', description: 'Slack webhook URL')
    }

    environment {
        SLACK_WEBHOOK_URL = params.SLACK_SECRET
    }

    triggers {
        cron('H 3 * * *') // Daily at 3 AM
    }

    stages {
        stage('Checkout') {
            steps {
                git credentialsId: 'bluesage github',
                    url: 'https://github.com/BlueSageSolutions/db-maintenance.git',
                    branch: 'main'
            }
        }

        stage('Install Dependencies') {
            steps {
                sh 'pip install --upgrade pymysql requests'
            }
        }

        stage('Run Stats Update for All Clients') {
            steps {
                script {
                    def clients = [
                        [
                            name: 'MFM',
                            host: 'mfm-prod.cluster-cpewz1juqrts.us-east-1.rds.amazonaws.com',
                            user: 'mfm_prod_admin',
                            pass: params.MFM_DB_PASS
                        ],
                        [
                            name: 'PHL',
                            host: 'phl-prod.cluster-cwi6aysujgze.us-east-1.rds.amazonaws.com',
                            user: 'phl_prod_admin',
                            pass: params.PHL_DB_PASS
                        ],
                        [
                            name: 'SEQ',
                            host: 'prd-seq-cluster.cluster-cm711hj0bd1j.us-east-1.rds.amazonaws.com',
                            user: 'prd_seq_admin',
                            pass: params.SEQ_DB_PASS
                        ]
                    ]

                    clients.each { client ->
                        echo "🔍 Checking stats process for ${client.name}"

                        sh """
                        python update_table_stats.py \
                          --client "${client.name}" \
                          --host "${client.host}" \
                          --user "${client.user}" \
                          --password "${client.pass}" \
                          --all
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            echo "✅ Scheduled stats update completed for all clients."
        }
        failure {
            echo "❌ Scheduled stats update failed."
        }
    }
}

pipeline {
    agent any

    parameters {
        choice(name: 'CLIENT', choices: [
            'MFM',
            'PHL',
            'SEQ'
        ], description: 'Select a client to update stats for')

        password(name: 'MFM_DB_PASS', defaultValue: '', description: 'Password for MFM')
        password(name: 'PHL_DB_PASS', defaultValue: '', description: 'Password for PHL')
        password(name: 'SEQ_DB_PASS', defaultValue: '', description: 'Password for SEQ')

        password(name: 'SLACK_SECRET', defaultValue: '', description: 'Slack webhook URL')
    }

    environment {
        SLACK_WEBHOOK_URL = params.SLACK_SECRET
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

        stage('Map Client Config') {
            steps {
                script {
                    def config = [
                        'MFM': [
                            host: 'mfm-prod.cluster-cpewz1juqrts.us-east-1.rds.amazonaws.com',
                            user: 'mfm_prod_admin',
                            pass: params.MFM_DB_PASS
                        ],
                        'PHL': [
                            host: 'phl-prod.cluster-cwi6aysujgze.us-east-1.rds.amazonaws.com',
                            user: 'phl_prod_admin',
                            pass: params.PHL_DB_PASS
                        ],
                        'SEQ': [
                            host: 'prd-seq-cluster.cluster-cm711hj0bd1j.us-east-1.rds.amazonaws.com',
                            user: 'prd_seq_admin',
                            pass: params.SEQ_DB_PASS
                        ]
                    ]

                    if (!config.containsKey(params.CLIENT)) {
                        error("Unknown client: ${params.CLIENT}")
                    }

                    def selected = config[params.CLIENT]
                    env.CLIENT_HOST = selected.host
                    env.CLIENT_USER = selected.user
                    env.CLIENT_PASS = selected.pass
                }
            }
        }

        stage('Run Stats Update') {
            steps {
                sh '''
                echo "Running ANALYZE TABLE for $CLIENT"
                python update_table_stats.py \
                    --client "$CLIENT" \
                    --host "$CLIENT_HOST" \
                    --user "$CLIENT_USER" \
                    --password "$CLIENT_PASS"
                '''
            }
        }
    }

    post {
        success {
            echo "✅ Stats update for ${params.CLIENT} succeeded."
        }
        failure {
            echo "❌ Stats update for ${params.CLIENT} failed."
        }
    }
}

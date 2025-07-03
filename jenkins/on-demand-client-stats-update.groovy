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
                        'DCU': [credentialId: 'DCU_DB_PASS', host: 'dcu-prod.cluster-cdv1igygg71v.us-east-1.rds.amazonaws.com', user: 'dcu_prod_admin'],
                        'HB Retail': [credentialId: 'HB_DB_PASS', host: 'hb-prod.cluster-cbt5jbn4bezy.us-east-1.rds.amazonaws.com', user: 'hb_prod_admin'],
                        'HB Wholesale': [credentialId: 'HB_DB_PASS', host: 'hb-prod.cluster-cbt5jbn4bezy.us-east-1.rds.amazonaws.com', user: 'hb_prod_admin'],
                        'HB Wholesale Replica': [credentialId: 'HB_DB_PASS', host: 'hb-prod.cluster-ro-cbt5jbn4bezy.us-east-1.rds.amazonaws.com', user: 'hb_prod_admin'],
                        'KIND Retail': [credentialId: 'KIND_DB_PASS', host: 'kindretail-prod.cluster-cyflzub0ncfc.us-east-1.rds.amazonaws.com', user: 'kindretail_admin'],
                        'KIND Retail Replica': [credentialId: 'KIND_DB_PASS', host: 'kindretail-prod.cluster-ro-cyflzub0ncfc.us-east-1.rds.amazonaws.com', user: 'kindretail_admin'],
                        'KIND Wholesale': [credentialId: 'KIND_DB_PASS', host: 'kind-prod.cluster-cklccemav8xr.us-east-1.rds.amazonaws.com', user: 'kind_prod_admin'],
                        'Lendage': [credentialId: 'LENDAGE_DB_PASS', host: 'lendage-prod.cluster-clnsi6frxbws.us-east-1.rds.amazonaws.com', user: 'lend_prod_admin'],
                        'MFM': [credentialId: 'MFM_DB_PASS', host: 'mfm-prod.cluster-cpewz1juqrts.us-east-1.rds.amazonaws.com', user: 'mfm_prod_admin'],
                        'MFM Replica': [credentialId: 'MFM_DB_PASS', host: 'mfm-prod.cluster-ro-cpewz1juqrts.us-east-1.rds.amazonaws.com', user: 'mfm_prod_admin'],
                        'MFM Correspondent': [credentialId: 'MFM_DB_PASS', host: 'mfmcorr-prod.cluster-cphgt2qz1ifr.us-east-1.rds.amazonaws.com', user: 'mfmcorr_prod_adm'],
                        'MIG': [credentialId: 'MIG_DB_PASS', host: 'mig-prod.cluster-c6fysriq3ynz.us-east-1.rds.amazonaws.com', user: 'mig_prod_admin'],
                        'NASB': [credentialId: 'NASB_DB_PASS', host: 'nasb-prod.cluster-c2qozf0lefhk.us-east-1.rds.amazonaws.com', user: 'nasb_prod_admin'],
                        'PHL': [credentialId: 'PHL_DB_PASS', host: 'phl-prod.cluster-cwi6aysujgze.us-east-1.rds.amazonaws.com', user: 'phl_prod_admin'],
                        'PRIME': [credentialId: 'PRIME_DB_PASS', host: 'prime-prod.cluster-cp1iltlyjptb.us-east-1.rds.amazonaws.com', user: 'prime_prod_admin'],
                        'SEQ': [credentialId: 'SEQ_DB_PASS', host: 'prd-seq-cluster.cluster-cm711hj0bd1j.us-east-1.rds.amazonaws.com', user: 'prd_seq_admin'],
                        'Westerra': [credentialId: 'WESTERRA_DB_PASS', host: 'westerra-prod.cluster-cz17s45vhtc5.us-east-1.rds.amazonaws.com', user: 'west_prod_admin'],
                        'SCU': [credentialId: 'SCU_DB_PASS', host: 'scu-prod.cluster-cibmycatlytx.us-east-1.rds.amazonaws.com', user: 'scu_prod_admin'],
                        'FCM': [credentialId: 'FCM_DB_PASS', host: 'fcm-prod.cluster-cpk5blsugybe.us-east-1.rds.amazonaws.com', user: 'fcm_prod_admin'],
                        'Chevron': [credentialId: 'CHEVRON_DB_PASS', host: 'cfcu-prod.cluster-cfghoc9qnkn4.us-east-1.rds.amazonaws.com', user: 'cfcu_prod_admin']
                    ]

                    def config = clientCredentials[params.CLIENT]
                    
                    withCredentials([string(credentialsId: config.credentialId, variable: 'DB_PASSWORD')]) {
                        docker.image('python:3.11-slim').inside('-u 0:0') {
                            sh '''
                                pip install --no-cache-dir --progress-bar off pymysql requests
                                python3 update_table_stats.py \
                                    --client ${CLIENT} \
                                    --host ''' + config.host + ''' \
                                    --user ''' + config.user + ''' \
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
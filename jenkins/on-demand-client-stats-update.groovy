pipeline {
    agent any

    parameters {
        choice(name: 'CLIENT', choices: [
            'DCU', 'HB Retail', 'HB Wholesale', 'HB Wholesale Replica',
            'KIND Retail', 'KIND Retail Replica', 'KIND Wholesale', 'Lendage',
            'MFM', 'MFM Replica', 'MFM Correspondent', 'MIG', 'NASB',
            'PHL', 'PRIME', 'SEQ', 'Westerra', 'SCU', 'FCM', 'Chevron'
        ], description: 'Choose the client for stats update')

        password(name: 'DCU_DB_PASS', defaultValue: '', description: 'DCU DB Password')
        password(name: 'HB_DB_PASS', defaultValue: '', description: 'HB Retail/Wholesale DB Password')
        password(name: 'KIND_DB_PASS', defaultValue: '', description: 'KIND Retail/Wholesale DB Password')
        password(name: 'LENDAGE_DB_PASS', defaultValue: '', description: 'Lendage DB Password')
        password(name: 'MFM_DB_PASS', defaultValue: '', description: 'MFM DB Password')
        password(name: 'MIG_DB_PASS', defaultValue: '', description: 'MIG DB Password')
        password(name: 'NASB_DB_PASS', defaultValue: '', description: 'NASB DB Password')
        password(name: 'PHL_DB_PASS', defaultValue: '', description: 'PHL DB Password')
        password(name: 'PRIME_DB_PASS', defaultValue: '', description: 'PRIME DB Password')
        password(name: 'SEQ_DB_PASS', defaultValue: '', description: 'SEQ DB Password')
        password(name: 'WESTERRA_DB_PASS', defaultValue: '', description: 'Westerra DB Password')
        password(name: 'SCU_DB_PASS', defaultValue: '', description: 'SCU DB Password')
        password(name: 'FCM_DB_PASS', defaultValue: '', description: 'FCM DB Password')
        password(name: 'CHEVRON_DB_PASS', defaultValue: '', description: 'Chevron DB Password')

        password(name: 'SLACK_SECRET', defaultValue: '', description: 'Slack webhook URL')
    }

    environment {
        SLACK_WEBHOOK_URL = "${params.SLACK_SECRET}"
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

        stage('Checkout') {
            steps {
                git(
                    url: 'https://github.com/BlueSageSolutions/db-maintenance.git',
                    credentialsId: 'a88a93e30c147197b3f311bb3030fd85d7e097cd' // Use your actual credential ID
                )
            }
        }

        stage('Run Stats Update') {
            steps {
                script {
                    def config = [
                        'DCU'               : [host: 'dcu-prod.cluster-cdv1igygg71v.us-east-1.rds.amazonaws.com', user: 'dcu_prod_admin', pass: params.DCU_DB_PASS],
                        'HB Retail'         : [host: 'hb-prod.cluster-cbt5jbn4bezy.us-east-1.rds.amazonaws.com', user: 'hb_prod_admin', pass: params.HB_DB_PASS],
                        'HB Wholesale'      : [host: 'hb-prod.cluster-cbt5jbn4bezy.us-east-1.rds.amazonaws.com', user: 'hb_prod_admin', pass: params.HB_DB_PASS],
                        'HB Wholesale Replica': [host: 'hb-prod.cluster-ro-cbt5jbn4bezy.us-east-1.rds.amazonaws.com', user: 'hb_prod_admin', pass: params.HB_DB_PASS],
                        'KIND Retail'       : [host: 'kindretail-prod.cluster-cyflzub0ncfc.us-east-1.rds.amazonaws.com', user: 'kindretail_admin', pass: params.KIND_DB_PASS],
                        'KIND Retail Replica': [host: 'kindretail-prod.cluster-ro-cyflzub0ncfc.us-east-1.rds.amazonaws.com', user: 'kindretail_admin', pass: params.KIND_DB_PASS],
                        'KIND Wholesale'    : [host: 'kind-prod.cluster-cklccemav8xr.us-east-1.rds.amazonaws.com', user: 'kind_prod_admin', pass: params.KIND_DB_PASS],
                        'Lendage'           : [host: 'lendage-prod.cluster-clnsi6frxbws.us-east-1.rds.amazonaws.com', user: 'lend_prod_admin', pass: params.LENDAGE_DB_PASS],
                        'MFM'               : [host: 'mfm-prod.cluster-cpewz1juqrts.us-east-1.rds.amazonaws.com', user: 'mfm_prod_admin', pass: params.MFM_DB_PASS],
                        'MFM Replica'       : [host: 'mfm-prod.cluster-ro-cpewz1juqrts.us-east-1.rds.amazonaws.com', user: 'mfm_prod_admin', pass: params.MFM_DB_PASS],
                        'MFM Correspondent' : [host: 'mfmcorr-prod.cluster-cphgt2qz1ifr.us-east-1.rds.amazonaws.com', user: 'mfmcorr_prod_adm', pass: params.MFM_DB_PASS],
                        'MIG'               : [host: 'mig-prod.cluster-c6fysriq3ynz.us-east-1.rds.amazonaws.com', user: 'mig_prod_admin', pass: params.MIG_DB_PASS],
                        'NASB'              : [host: 'nasb-prod.cluster-c2qozf0lefhk.us-east-1.rds.amazonaws.com', user: 'nasb_prod_admin', pass: params.NASB_DB_PASS],
                        'PHL'               : [host: 'phl-prod.cluster-cwi6aysujgze.us-east-1.rds.amazonaws.com', user: 'phl_prod_admin', pass: params.PHL_DB_PASS],
                        'PRIME'             : [host: 'prime-prod.cluster-cp1iltlyjptb.us-east-1.rds.amazonaws.com', user: 'prime_prod_admin', pass: params.PRIME_DB_PASS],
                        'SEQ'               : [host: 'prd-seq-cluster.cluster-cm711hj0bd1j.us-east-1.rds.amazonaws.com', user: 'prd_seq_admin', pass: params.SEQ_DB_PASS],
                        'Westerra'          : [host: 'westerra-prod.cluster-cz17s45vhtc5.us-east-1.rds.amazonaws.com', user: 'west_prod_admin', pass: params.WESTERRA_DB_PASS],
                        'SCU'               : [host: 'scu-prod.cluster-cibmycatlytx.us-east-1.rds.amazonaws.com', user: 'scu_prod_admin', pass: params.SCU_DB_PASS],
                        'FCM'               : [host: 'fcm-prod.cluster-cpk5blsugybe.us-east-1.rds.amazonaws.com', user: 'fcm_prod_admin', pass: params.FCM_DB_PASS],
                        'Chevron'           : [host: 'cfcu-prod.cluster-cfghoc9qnkn4.us-east-1.rds.amazonaws.com', user: 'cfcu_prod_admin', pass: params.CHEVRON_DB_PASS]
                    ]

                    def clientConfig = config[params.CLIENT]
                    
                    docker.image('python:3.11-slim').inside('-u 0:0') {
                        sh """
                            pip install --no-cache-dir --progress-bar off pymysql requests
                            python3 update_table_stats.py "${clientConfig.host}" "${clientConfig.user}" "${clientConfig.pass}"
                        """
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
                // Uncomment to add Slack notification
                // slackSend(color: "good", message: "✅ Stats update for ${params.CLIENT} completed successfully.")
            }
        }
        failure {
            script {
                echo "❌ Stats update for ${params.CLIENT} failed."
                // Uncomment to add Slack notification
                // slackSend(color: "danger", message: "❌ Stats update for ${params.CLIENT} failed.")
            }
        }
    }
}
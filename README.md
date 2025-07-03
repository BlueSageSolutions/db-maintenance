# 🔧 MySQL Table Statistics Updater

A Python-based automation tool that connects to multiple Amazon RDS MySQL databases, updates table statistics across all non-system schemas, and sends a summary alert to Slack.

---

## 📌 Features

- ✅ Connects securely to multiple RDS instances
- ✅ Automatically runs `ANALYZE NO_WRITE_TO_BINLOG` on all tables
- ✅ Skips system schemas (`information_schema`, `mysql`, etc.)
- ✅ Sends Slack notification on success/failure
- ✅ Designed for automation via **Jenkins**

---

## 🚀 Usage

### 🔧 Requirements

- Python 3.7+
- `pymysql`
- `requests`

Install dependencies:

```bash
pip install -r requirements.txt
````

> Or manually:

```bash
pip install pymysql requests
```

### 📄 Running the Script

```bash
export SLACK_WEBHOOK_URL="https://hooks.slack.com/services/your-webhook-url"
python update_table_stats.py
```

---

## 🛠️ Configuration

The script reads a list of client database connections from the `CLIENTS` list:

```python
CLIENTS = [
    ("MFM", "mfm-prod.cluster-xyz.us-east-1.rds.amazonaws.com", "user", "password"),
    ...
]
```

You can update or extend this list to include more MySQL-compatible RDS hosts.

The Slack channel and webhook are controlled via:

```python
SLACK_WEBHOOK_URL = os.getenv("SLACK_WEBHOOK_URL")
SLACK_CHANNEL = "#prod-stats-update-alert"
```

---

## ⚙️ Jenkins Integration

1. **Create a Jenkins pipeline** (or Freestyle job)
2. **Check out this repo** during the job
3. Use the following steps in your pipeline:

```groovy
pipeline {
    agent any

    environment {
        SLACK_WEBHOOK_URL = credentials('slack-webhook-secret-id')
    }

    stages {
        stage('Install Requirements') {
            steps {
                sh 'pip install --upgrade pymysql requests'
            }
        }

        stage('Run Stats Update') {
            steps {
                sh 'python update_table_stats.py'
            }
        }
    }
}
```

> Replace `'slack-webhook-secret-id'` with your Jenkins credentials ID.

---

## 🔐 Security Best Practices

* Never hardcode database passwords in code.
* Use environment variables or Jenkins Credentials to inject secrets.
* Use IAM-auth or Secrets Manager for production RDS if possible.

---

## 📣 Slack Notifications

The script sends updates like:

* ✅ `Updated stats for 128 tables`
* ⚠️ `No stats updated`
* ❌ Errors during connection or analysis

---

## 🧾 License

MIT License

---

## 🤝 Contributing

Feel free to submit PRs to support:

* PostgreSQL
* CLI flags for selective clients
* Logging improvements

---

## 👨‍💻 Author

**Jamaurice Holt** — [@jamauriceholt](https://github.com/jamaurice)

